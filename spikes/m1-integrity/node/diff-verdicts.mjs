#!/usr/bin/env node
// Field-by-field diff between two or more decoded Play Integrity verdicts
// (output of decode-tokens.mjs). Feeds the same question as
// spikes/m1-attest/node/diff-chains.mjs but for Play Integrity standard
// tokens instead of key-attestation chains: does the payload contain
// anything stable across sites that could act as a device identifier?
//
// This script does NOT judge — it lists what's identical and what differs.
// Fields identical across the two *sites* are called out separately as
// "cross-site stable" candidates. requestDetails.requestHash and
// requestDetails.timestampMillis are expected to differ (fresh per request)
// and are excluded from the candidate list, but still reported under
// "differs" like everything else.
//
// Usage:
//   node diff-verdicts.mjs <decoded-a.json> <decoded-b.json> [more.json ...]
//
// With exactly two files, prints identical/differs/crossSiteStable. With
// more than two, diffs the first pair and reports crossSiteStable across
// ALL of them (fields identical in every file given).

import { readFileSync } from 'node:fs';
import { basename } from 'node:path';

// Matched by suffix, not exact key, since Google wraps the verdict fields
// under a top-level `tokenPayloadExternal` that isn't part of the field's
// conceptual name (so both `requestDetails.requestHash` and
// `tokenPayloadExternal.requestDetails.requestHash` match).
const EXPECTED_TO_DIFFER_SUFFIXES = [
  'requestDetails.requestHash',
  'requestDetails.timestampMillis',
];

function isExpectedToDiffer(key) {
  return EXPECTED_TO_DIFFER_SUFFIXES.some((s) => key === s || key.endsWith(`.${s}`));
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

function loadFlat(path) {
  const parsed = JSON.parse(readFileSync(path, 'utf8'));
  return flatten(parsed, '', {});
}

function diffPair(flatA, flatB, labelA, labelB) {
  const keys = new Set([...Object.keys(flatA), ...Object.keys(flatB)]);
  const identical = [];
  const differs = [];

  for (const key of [...keys].sort()) {
    const va = flatA[key];
    const vb = flatB[key];
    const same = JSON.stringify(va) === JSON.stringify(vb);
    if (same) {
      identical.push({ field: key, value: va });
    } else {
      differs.push({ field: key, [labelA]: va, [labelB]: vb, expectedToDiffer: isExpectedToDiffer(key) });
    }
  }

  return { identical, differs };
}

function crossFileStable(allFlats, labels) {
  const keys = new Set();
  for (const flat of allFlats) for (const k of Object.keys(flat)) keys.add(k);
  const stable = [];
  for (const key of [...keys].sort()) {
    if (isExpectedToDiffer(key)) continue;
    const values = allFlats.map((f) => JSON.stringify(f[key]));
    const allSame = values.every((v) => v === values[0]) && values[0] !== undefined;
    if (allSame) stable.push({ field: key, value: allFlats[0][key], presentIn: labels });
  }
  return stable;
}

function main() {
  const args = process.argv.slice(2);
  if (args.length < 2) {
    console.error('Usage: node diff-verdicts.mjs <decoded-a.json> <decoded-b.json> [more.json ...]');
    process.exit(2);
  }

  const labels = args.map((p) => basename(p));
  const flats = args.map(loadFlat);

  const { identical, differs } = diffPair(flats[0], flats[1], labels[0], labels[1]);
  const crossSiteStable = crossFileStable(flats, labels);

  console.log(JSON.stringify({
    labelA: labels[0],
    labelB: labels[1],
    filesCompared: labels,
    identical,
    differs,
    crossSiteStable,
  }, null, 2));
}

main();
