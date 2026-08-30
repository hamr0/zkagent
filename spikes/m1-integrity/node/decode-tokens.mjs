#!/usr/bin/env node
// Decodes raw Play Integrity **standard** tokens captured by
// spikes/m0/capture-integrity.sh (see M1IntegrityProbe.kt for the on-device
// side) by calling Google's decodeIntegrityToken API directly.
//
// THROWAWAY POC. Not shipped, not graduated (AGENT_RULES: never ship the
// POC). Node >=20 stdlib only, zero npm dependencies — that is the riskiest
// assumption this spike exists to test (can we do OAuth2 JWT-bearer auth +
// the decode call with nothing but node:crypto and fetch?).
//
// Usage:
//   M1_INTEGRITY_SA_KEY=/path/to/sa-key.json node decode-tokens.mjs <capture.txt> [packageName]
//
// Auth: service-account JSON key file, path only from the M1_INTEGRITY_SA_KEY
// env var. The key file itself never enters the repo and its contents are
// never printed. See README.md for how to create the service account.
//
// Capture file format (produced by spikes/m0/capture-integrity.sh via
// M1IntegrityProbe.kt), delimited logcat text:
//   ===== M1 INTEGRITY REPORT BEGIN =====
//   --- site=<site> run=<n> summary ---
//   request_hash_b64: <b64>
//   request_ms: <n>
//   token_length: <n>
//   token_sha256: <b64>
//   error: none|<ExceptionClass>: <message>
//   --- site=<site> run=<n> raw_token (still-encrypted JWE) ---
//   token_chunk site=<site> run=<n> idx=<n>: <<=64-char chunk>
//   ... (repeated, idx ascending)
//   ===== SUMMARY =====
//   site=<site> run=<n> request_ms=<n> token_length=<n> token_sha256=<b64> error=<none|...>
//   ===== M1 INTEGRITY REPORT END =====

import { readFileSync, writeFileSync, mkdirSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { createSign } from 'node:crypto';

const DEFAULT_PACKAGE_NAME = 'com.tananaev.passportreader';
const SCOPE = 'https://www.googleapis.com/auth/playintegrity';
const OUT_DIR = join(dirname(fileURLToPath(import.meta.url)), '..', 'fixtures', 'real');

function base64url(input) {
  const buf = Buffer.isBuffer(input) ? input : Buffer.from(input, 'utf8');
  return buf.toString('base64').replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
}

/** Parses the delimited capture file into per (site, run) token records. */
export function parseCapture(text) {
  const summaries = new Map(); // key `${site}#${run}` -> {site, run, fields}
  const chunks = new Map(); // key `${site}#${run}` -> array indexed by idx
  let packageName = null;

  const lines = text.split('\n');
  let mode = null; // 'summary' | 'token' | null
  let ctx = null;

  for (const rawLine of lines) {
    // capture-integrity.sh tees raw `adb logcat` output, so each line carries
    // a logcat prefix ("MM-DD HH:MM:SS.mmm PID TID LEVEL TAG: "). Strip it
    // if present; a bare delimited file (no logcat prefix) still matches.
    const prefixed = rawLine.match(/^\S+\s+\S+\s+\d+\s+\d+\s+\w\s+M1Integrity:\s?(.*)$/);
    const line = prefixed ? prefixed[1] : rawLine;
    let m;
    if ((m = line.match(/^--- site=(\S+) run=(\d+) summary ---\s*$/))) {
      ctx = { site: m[1], run: Number(m[2]) };
      mode = 'summary';
      const key = `${ctx.site}#${ctx.run}`;
      if (!summaries.has(key)) summaries.set(key, { site: ctx.site, run: ctx.run, fields: {} });
      continue;
    }
    if ((m = line.match(/^--- site=(\S+) run=(\d+) raw_token .*---\s*$/))) {
      ctx = { site: m[1], run: Number(m[2]) };
      mode = 'token';
      continue;
    }
    if (line.startsWith('===== SUMMARY =====') || line.startsWith('===== M1 INTEGRITY REPORT')) {
      mode = null;
      ctx = null;
      continue;
    }
    if ((m = line.match(/^package_name:\s*(\S+)\s*$/))) {
      packageName = m[1];
      continue;
    }
    if (mode === 'summary' && ctx && (m = line.match(/^(\w+):\s?(.*)$/))) {
      const key = `${ctx.site}#${ctx.run}`;
      summaries.get(key).fields[m[1]] = m[2];
      continue;
    }
    if (mode === 'token' && ctx && (m = line.match(/^token_chunk site=(\S+) run=(\d+) idx=(\d+): (.*)$/))) {
      const key = `${m[1]}#${Number(m[2])}`;
      if (!chunks.has(key)) chunks.set(key, []);
      chunks.get(key)[Number(m[3])] = m[4];
      continue;
    }
  }

  const records = [];
  for (const [key, summary] of summaries) {
    const chunkArr = chunks.get(key) || [];
    const token = chunkArr.length ? chunkArr.join('') : null;
    records.push({
      site: summary.site,
      run: summary.run,
      requestHashB64: summary.fields.request_hash_b64 ?? null,
      requestMs: summary.fields.request_ms ? Number(summary.fields.request_ms) : null,
      tokenLength: summary.fields.token_length ? Number(summary.fields.token_length) : null,
      tokenSha256: summary.fields.token_sha256 ?? null,
      error: summary.fields.error && summary.fields.error !== 'none' ? summary.fields.error : null,
      token,
    });
  }
  records.sort((a, b) => (a.site < b.site ? -1 : a.site > b.site ? 1 : a.run - b.run));
  return { records, packageName };
}

/** Builds an unsigned-then-signed RS256 JWT for the OAuth2 JWT-bearer grant. */
export function createSignedJwt(serviceAccount, { now = Math.floor(Date.now() / 1000) } = {}) {
  const header = { alg: 'RS256', typ: 'JWT' };
  const claims = {
    iss: serviceAccount.client_email,
    scope: SCOPE,
    aud: serviceAccount.token_uri,
    iat: now,
    exp: now + 3600,
  };
  const signingInput = `${base64url(JSON.stringify(header))}.${base64url(JSON.stringify(claims))}`;
  const signer = createSign('RSA-SHA256');
  signer.update(signingInput);
  signer.end();
  const signature = base64url(signer.sign(serviceAccount.private_key));
  return { jwt: `${signingInput}.${signature}`, header, claims };
}

/** Exchanges a signed JWT for an OAuth2 access token at token_uri. */
export async function exchangeForAccessToken(tokenUri, jwt) {
  const body = new URLSearchParams({
    grant_type: 'urn:ietf:params:oauth:grant-type:jwt-bearer',
    assertion: jwt,
  });
  const res = await fetch(tokenUri, {
    method: 'POST',
    headers: { 'content-type': 'application/x-www-form-urlencoded' },
    body: body.toString(),
  });
  const text = await res.text();
  if (!res.ok) {
    throw new Error(`token exchange failed: HTTP ${res.status} ${res.statusText} — ${text}`);
  }
  const parsed = JSON.parse(text);
  if (!parsed.access_token) {
    throw new Error(`token exchange response had no access_token: ${text}`);
  }
  return parsed.access_token;
}

/** Calls decodeIntegrityToken for one raw token. */
export async function decodeIntegrityToken(packageName, accessToken, integrityToken) {
  const url = `https://playintegrity.googleapis.com/v1/${encodeURIComponent(packageName)}:decodeIntegrityToken`;
  const res = await fetch(url, {
    method: 'POST',
    headers: {
      'content-type': 'application/json',
      authorization: `Bearer ${accessToken}`,
    },
    body: JSON.stringify({ integrity_token: integrityToken }),
  });
  const text = await res.text();
  if (!res.ok) {
    throw new Error(`decodeIntegrityToken failed: HTTP ${res.status} ${res.statusText} — ${text}`);
  }
  return JSON.parse(text);
}

async function main() {
  const [captureArg, packageNameArg] = process.argv.slice(2);
  if (!captureArg) {
    console.error('Usage: M1_INTEGRITY_SA_KEY=/path/to/sa-key.json node decode-tokens.mjs <capture.txt> [packageName]');
    process.exit(2);
  }

  const saKeyPath = process.env.M1_INTEGRITY_SA_KEY;
  if (!saKeyPath) {
    console.error('M1_INTEGRITY_SA_KEY is not set — point it at a Google service-account JSON key file with the Play Integrity API role (see README.md), then re-run.');
    process.exit(1);
  }

  let serviceAccount;
  try {
    serviceAccount = JSON.parse(readFileSync(saKeyPath, 'utf8'));
  } catch (e) {
    console.error(`failed to read/parse M1_INTEGRITY_SA_KEY (${saKeyPath}): ${e.message}`);
    process.exit(1);
  }
  for (const field of ['client_email', 'private_key', 'token_uri']) {
    if (!serviceAccount[field]) {
      console.error(`service-account key file is missing required field "${field}"`);
      process.exit(1);
    }
  }

  const captureText = readFileSync(captureArg, 'utf8');
  const { records, packageName: capturePackageName } = parseCapture(captureText);
  const packageName = packageNameArg || capturePackageName || DEFAULT_PACKAGE_NAME;
  console.error(`package name: ${packageName}${capturePackageName ? ' (from capture)' : packageNameArg ? ' (from arg)' : ' (default)'}`);

  const decodable = records.filter((r) => r.token);
  if (decodable.length === 0) {
    console.error('no tokens with a reassembled raw_token block found in capture file');
    process.exit(1);
  }
  console.error(`found ${records.length} record(s), ${decodable.length} with a token to decode`);

  const { jwt, header, claims } = createSignedJwt(serviceAccount);
  console.error('JWT header:', JSON.stringify(header));
  console.error('JWT claims:', JSON.stringify(claims));

  const accessToken = await exchangeForAccessToken(serviceAccount.token_uri, jwt);

  mkdirSync(OUT_DIR, { recursive: true });

  for (const record of decodable) {
    const verdict = await decodeIntegrityToken(packageName, accessToken, record.token);
    const outPath = join(OUT_DIR, `decoded-${record.site}-${record.run}.json`);
    writeFileSync(outPath, JSON.stringify(verdict, null, 2));
    console.log(`--- ${record.site} run ${record.run} -> ${outPath} ---`);
    console.log(JSON.stringify(verdict, null, 2));
  }
}

const isMain = process.argv[1] === fileURLToPath(import.meta.url);
if (isMain) {
  main().catch((e) => {
    console.error(`error: ${e.message}`);
    process.exit(1);
  });
}
