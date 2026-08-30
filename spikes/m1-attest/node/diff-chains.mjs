#!/usr/bin/env node
// Field-by-field diff between two attestation chains captured from the SAME
// device (two fresh key-attestation runs). Feeds PRD risk #8: the
// attestation payload itself may be a device identifier. This script does
// NOT judge — it lists what's identical and what differs, and separately
// flags identical fields that are *plausibly device-unique* (a
// "linkability candidate") for a human to reason about.
//
// Usage:
//   node diff-chains.mjs <chainA.pem> <chainB.pem>
//   node diff-chains.mjs <capture.txt>        (delimited multi-run capture file;
//                                               diffs the first two runs found)
//
// Capture file format (as produced by the parallel Pixel-capture agent):
//   --- config=<name> run=<n> cert=<n> ---
//   -----BEGIN CERTIFICATE-----
//   ...
//   -----END CERTIFICATE-----
//   (repeated; cert=0 is the leaf of that run)

import { readFileSync } from 'node:fs';
import { decodeChainFromPem } from './decode-chain.mjs';

// Fields we know or suspect are plausibly device-unique (not just fresh-per-key),
// based on the PRD's own risk #8 list. This is a fixed watchlist, not a judgment —
// the script still reports every identical field; these are just called out.
const LINKABILITY_WATCHLIST = new Set([
  'chain.1.serialNumber', 'chain.2.serialNumber', 'chain.3.serialNumber',
  'chain.1.validFrom', 'chain.2.validFrom', 'chain.3.validFrom',
  'extension.uniqueId',
  'extension.hardwareEnforced.rootOfTrust.verifiedBootKey',
  'extension.hardwareEnforced.rootOfTrust.verifiedBootHash',
  'extension.hardwareEnforced.osPatchLevel',
  'extension.hardwareEnforced.vendorPatchLevel',
  'extension.hardwareEnforced.bootPatchLevel',
  'extension.hardwareEnforced.osVersion',
]);

function parseCaptureFile(text) {
  const runs = new Map(); // key: `${config}#${run}` -> array of PEM blocks in cert order
  const lines = text.split('\n');
  let current = null;
  let buf = [];
  const flush = () => {
    if (current) {
      const key = `${current.config}#${current.run}`;
      if (!runs.has(key)) runs.set(key, []);
      runs.get(key)[current.cert] = buf.join('\n');
    }
    buf = [];
  };
  for (const line of lines) {
    const m = line.match(/^---\s*config=(\S+)\s+run=(\d+)\s+cert=(\d+)\s*---/);
    if (m) {
      flush();
      current = { config: m[1], run: Number(m[2]), cert: Number(m[3]) };
    } else if (current) {
      buf.push(line);
    }
  }
  flush();
  return runs;
}

function flatten(obj, prefix, out) {
  if (obj === null || obj === undefined) {
    out[prefix] = obj;
  } else if (Array.isArray(obj)) {
    obj.forEach((v, i) => flatten(v, `${prefix}.${i}`, out));
  } else if (typeof obj === 'object') {
    for (const [k, v] of Object.entries(obj)) flatten(v, prefix ? `${prefix}.${k}` : k, out);
  } else {
    out[prefix] = obj;
  }
  return out;
}

function diffDecoded(a, b, labelA, labelB) {
  const flatA = flatten({ chain: a.chain, extension: a.extension }, '', {});
  const flatB = flatten({ chain: b.chain, extension: b.extension }, '', {});
  const keys = new Set([...Object.keys(flatA), ...Object.keys(flatB)]);

  const identical = [];
  const differs = [];
  const linkabilityCandidates = [];

  for (const key of [...keys].sort()) {
    const va = flatA[key];
    const vb = flatB[key];
    const same = JSON.stringify(va) === JSON.stringify(vb);
    if (same) {
      identical.push({ field: key, value: va });
      if (LINKABILITY_WATCHLIST.has(key)) linkabilityCandidates.push({ field: key, value: va });
    } else {
      differs.push({ field: key, [labelA]: va, [labelB]: vb });
    }
  }

  return { identical, differs, linkabilityCandidates };
}

function main() {
  const args = process.argv.slice(2);
  let decodedA, decodedB, labelA, labelB;

  if (args.length === 1) {
    const text = readFileSync(args[0], 'utf8');
    const runs = parseCaptureFile(text);
    const runKeys = [...runs.keys()];
    if (runKeys.length < 2) {
      console.error(`Capture file has ${runKeys.length} run(s); need at least 2 to diff.`);
      process.exit(2);
    }
    const [keyA, keyB] = runKeys;
    labelA = keyA;
    labelB = keyB;
    decodedA = decodeChainFromPem(runs.get(keyA).join('\n'));
    decodedB = decodeChainFromPem(runs.get(keyB).join('\n'));
  } else if (args.length === 2) {
    labelA = args[0];
    labelB = args[1];
    decodedA = decodeChainFromPem(readFileSync(args[0], 'utf8'));
    decodedB = decodeChainFromPem(readFileSync(args[1], 'utf8'));
  } else {
    console.error('Usage: node diff-chains.mjs <chainA.pem> <chainB.pem>');
    console.error('   or: node diff-chains.mjs <capture.txt>   (diffs the first two runs found)');
    process.exit(2);
  }

  const result = diffDecoded(decodedA, decodedB, labelA, labelB);
  console.log(JSON.stringify({ labelA, labelB, ...result }, null, 2));
}

main();
