#!/usr/bin/env node
// Splits a raw logcat capture of the M1 Pixel attestation spike into 4
// leaf-first PEM chain files (strongbox run1/run2, tee run1/run2).
//
// Input format: one logcat line per record, e.g.
//   08-29 22:58:44.792 29673 29929 I M1Attest: --- config=strongbox run=1 cert=0 ---
// The logcat prefix (date, pid, tid, level, tag) is stripped; inside each
// `--- config=<c> run=<r> cert=<n> ---` block we take subject/issuer/serial/
// notBefore/notAfter metadata lines (dropped here — not needed for the PEM)
// followed by a PEM body, which may or may not already carry its own
// BEGIN/END CERTIFICATE markers; we normalize to always emit them once.
//
// Usage: node extract-real-capture.mjs <capture.txt> <output-dir>
// Writes: <output-dir>/strongbox-run1.pem, strongbox-run2.pem, tee-run1.pem, tee-run2.pem

import { readFileSync, writeFileSync, mkdirSync } from 'node:fs';

const LOGCAT_PREFIX = /^\d{2}-\d{2}\s+\d{2}:\d{2}:\d{2}\.\d{3}\s+\d+\s+\d+\s+\S\s+\S+:\s?/;
const BLOCK_HEADER = /^---\s*config=(\S+)\s+run=(\d+)\s+cert=(\d+)\s*---/;

function stripPrefix(line) {
  return line.replace(LOGCAT_PREFIX, '');
}

function main() {
  const [inputPath, outDir] = process.argv.slice(2);
  if (!inputPath || !outDir) {
    console.error('Usage: node extract-real-capture.mjs <capture.txt> <output-dir>');
    process.exit(2);
  }
  const lines = readFileSync(inputPath, 'utf8').split('\n').map(stripPrefix);

  // key: `${config}#run${run}` -> array of base64 body strings, indexed by cert number
  const chains = new Map();
  let current = null; // { key, certIndex }
  let inCertBody = false;
  let body = [];

  const flushCert = () => {
    if (current && body.length) {
      const key = current.key;
      if (!chains.has(key)) chains.set(key, []);
      chains.get(key)[current.certIndex] = body.join('');
    }
    body = [];
    inCertBody = false;
  };

  for (const raw of lines) {
    const line = raw.replace(/\r$/, '');
    const headerMatch = line.match(BLOCK_HEADER);
    if (headerMatch) {
      flushCert();
      const [, config, run, certStr] = headerMatch;
      current = { key: `${config}-run${run}`, certIndex: Number(certStr) };
      continue;
    }
    if (/^---\s*config=\S+\s+run=\d+\s+summary\s*---/.test(line) || /^=====/.test(line)) {
      flushCert();
      current = null;
      continue;
    }
    if (!current) continue;
    if (line.includes('BEGIN CERTIFICATE') || line.includes('END CERTIFICATE')) {
      inCertBody = line.includes('BEGIN CERTIFICATE');
      continue;
    }
    // Skip metadata lines (subject/issuer/serial/notBefore/notAfter); only collect base64 body.
    if (/^(subject|issuer|serial|notBefore|notAfter):/.test(line)) continue;
    const trimmed = line.trim();
    if (trimmed) body.push(trimmed);
  }
  flushCert();

  mkdirSync(outDir, { recursive: true });
  const written = [];
  for (const [key, certBodies] of chains) {
    const pem = certBodies
      .map((b64) => `-----BEGIN CERTIFICATE-----\n${b64.match(/.{1,64}/g).join('\n')}\n-----END CERTIFICATE-----`)
      .join('\n') + '\n';
    const outPath = `${outDir}/${key}.pem`;
    writeFileSync(outPath, pem);
    written.push({ key, certCount: certBodies.length, outPath });
  }
  console.log(JSON.stringify(written, null, 2));
}

main();
