#!/usr/bin/env node
// apps/demo/scripts/tierc-poc-request.mjs — M3b opening POC helper (milestones.md §6.9.1, criteria 3 and 4).
//
// Creates ONE transaction on the running demo verifier and prints the av:// link ready for
// `adb shell am start`, then (with --wait) polls until the app answers or the wait runs out and prints the
// verdict. The Host used to reach the verifier decides the request's origin (the verifier derives its links from
// the Host header), so `--host localhost` gives a request whose origin is http://localhost:8787.
//
//   node scripts/tierc-poc-request.mjs --tier C --host 127.0.0.1 --months 3 [--launch] [--wait 90]
//   node scripts/tierc-poc-request.mjs --tier A --host 127.0.0.1 [--launch] [--wait 90]
//
// --launch force-stops the scanner and then starts it on the new link with the whole link single-quoted for the device
// shell (adb eats an unquoted `&`), printing the exact adb commands it ran. Set ADB to use a different adb binary.
//
// Chunk 1 (M3b): one request can carry ALL five predicate kinds. Flags (each optional, any combination):
//   --months 3|6|12            expiry bucket
//   --age 15|16|18|21|60|65    date-of-birth range (repeat as a comma list "18,21" to ask two ages in one request)
//   --nationality-is CODE      nationality confirmation (ISO alpha-3 or an ICAO special code such as D)
//   --nationality-not-in A,B   nationality exclusion (at most 10 codes)
//   --given-names "..." --surname "..."   name match (BOTH are required together)
//   --doc-number "..."         document-number match
// With none of them a tier-C request asks --months 3. The name, number and codes are signed into the request for the
// app and are NEVER printed by this script or logged by the verifier. Pass them as environment variables
// (TIERC_GIVEN_NAMES, TIERC_SURNAME, TIERC_DOC_NUMBER) to keep them out of the process list and shell history.
// --raw '<json array>'      send these predicates UNVALIDATED (the verifier must run with TIERC_POC_ALLOW_RAW=1); this is
//                            how the app's whole-request refusal of an off-list boundary is device-checked.
// --ttl SECONDS sets the request lifetime (default 120, the verifier clamps at 600) for human-paced device checks.
//
// Prints transaction ids, link, status, ok/allowed/reason, predicate names and boolean answers. It never prints a
// document value, a name or a number.

const args = process.argv.slice(2);
function opt(name, dflt) {
  const i = args.indexOf(`--${name}`);
  return i >= 0 && i + 1 < args.length ? args[i + 1] : dflt;
}
const tier = opt('tier', 'C').toUpperCase();
const host = opt('host', '127.0.0.1');
const port = opt('port', '8787');
const waitSec = Number(opt('wait', '0'));
const ttlSec = Number(opt('ttl', '0'));
if (!['A', 'B', 'C'].includes(tier)) { console.error('--tier must be A, B or C'); process.exit(2); }

import { execFileSync } from 'node:child_process';
import { adbForceStopArgs, adbLaunchArgs, predicateLabel } from '../tierc-poc.mjs';

const base = `http://${host}:${port}`;

/** Builds the tier-C predicate list from the flags; returns null if a flag combination is incomplete. */
function buildPredicates(get, env) {
  const preds = [];
  const months = get('months');
  if (months !== undefined) preds.push({ name: 'expiry_beyond', months: Number(months) });
  const age = get('age');
  if (age !== undefined) for (const a of age.split(',')) preds.push({ name: 'dob_at_least', age: Number(a) });
  const natIs = get('nationality-is');
  if (natIs !== undefined) preds.push({ name: 'nationality_is', code: natIs });
  const natNot = get('nationality-not-in');
  if (natNot !== undefined) preds.push({ name: 'nationality_not_in', codes: natNot.split(',') });
  const given = get('given-names') ?? env.TIERC_GIVEN_NAMES;
  const surname = get('surname') ?? env.TIERC_SURNAME;
  if (given !== undefined || surname !== undefined) {
    if (given === undefined || surname === undefined) return null;
    preds.push({ name: 'name_match', given_names: given, surname });
  }
  const num = get('doc-number') ?? env.TIERC_DOC_NUMBER;
  if (num !== undefined) preds.push({ name: 'document_number_is', number: num });
  return preds.length > 0 ? preds : [{ name: 'expiry_beyond', months: 3 }];
}
const rawArg = opt('raw', undefined);
const predicates = tier === 'C' ? (rawArg !== undefined ? JSON.parse(rawArg) : buildPredicates((n) => opt(n, undefined), process.env)) : [];
if (predicates === null) { console.error('--given-names and --surname must be given together'); process.exit(2); }
const body = tier === 'C' ? (rawArg !== undefined ? { mode: 'C', rawPredicates: predicates } : { mode: 'C', predicates }) : { mode: tier };
if (ttlSec > 0) body.ttlMs = ttlSec * 1000;
const res = await fetch(`${base}/ui/presentations`, {
  method: 'POST', headers: { 'content-type': 'application/json' }, body: JSON.stringify(body),
});
if (res.status !== 201) { console.error(`create failed: HTTP ${res.status}`); process.exit(1); }
const tx = await res.json();
console.log(`transaction=${tx.transactionId} tier=${tier} origin_host=${host}${tier === 'C' ? ` asks=${predicates.map((p) => predicateLabel(p)).join(',')}${rawArg !== undefined ? ' (raw, unvalidated)' : ''}` : ''}${ttlSec > 0 ? ` ttl_s=${ttlSec}` : ''}`);
console.log('adb_am_start_arg (quote it, the & is eaten by adb shell otherwise):');
console.log(`'${tx.app_link_av}'`);

if (args.includes('--launch')) {
  const adb = process.env.ADB ?? 'adb';
  for (const a of [adbForceStopArgs(), adbLaunchArgs(tx.app_link_av)]) {
    console.log(`$ ${adb} ${a.join(' ')}`);
    execFileSync(adb, a, { stdio: 'inherit' });
  }
}

if (waitSec > 0) {
  const deadline = Date.now() + waitSec * 1000;
  for (;;) {
    const r = await fetch(`${base}/ui/presentations/${tx.transactionId}`);
    const s = await r.json();
    if (s.status === 'done') {
      const v = s.verdict ?? {};
      const answered = Array.isArray(v.answers) ? v.answers.map((a) => `${predicateLabel(a)}=${a.result}`).join(',') : 'none';
      console.log(`RESULT status=done ok=${v.ok} allowed=${v.allowed} reason=${v.reason} answered=${answered} presentation_nonempty=${s.presentation !== null && s.presentation !== undefined}`);
      process.exit(0);
    }
    if (Date.now() > deadline) { console.log('RESULT status=pending (nothing reached the verifier within the wait)'); process.exit(0); }
    await new Promise((r2) => setTimeout(r2, 1000));
  }
}
