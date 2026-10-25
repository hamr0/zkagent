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
// Prints transaction ids, link, status, ok/allowed/reason, predicate names and boolean answers. It never prints a
// document value because none exists in this POC's request or answer.

const args = process.argv.slice(2);
function opt(name, dflt) {
  const i = args.indexOf(`--${name}`);
  return i >= 0 && i + 1 < args.length ? args[i + 1] : dflt;
}
const tier = opt('tier', 'C').toUpperCase();
const host = opt('host', '127.0.0.1');
const port = opt('port', '8787');
const months = Number(opt('months', '3'));
const waitSec = Number(opt('wait', '0'));
if (!['A', 'B', 'C'].includes(tier)) { console.error('--tier must be A, B or C'); process.exit(2); }

import { execFileSync } from 'node:child_process';
import { adbForceStopArgs, adbLaunchArgs } from '../tierc-poc.mjs';

const base = `http://${host}:${port}`;
const body = tier === 'C'
  ? { mode: 'C', predicates: [{ name: 'expiry_beyond', months }] }
  : { mode: tier };
const res = await fetch(`${base}/ui/presentations`, {
  method: 'POST', headers: { 'content-type': 'application/json' }, body: JSON.stringify(body),
});
if (res.status !== 201) { console.error(`create failed: HTTP ${res.status} ${await res.text()}`); process.exit(1); }
const tx = await res.json();
console.log(`transaction=${tx.transactionId} tier=${tier} origin_host=${host}`);
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
      const answered = Array.isArray(v.answers) ? v.answers.map((a) => `${a.name}:${a.months}=${a.result}`).join(',') : 'none';
      console.log(`RESULT status=done ok=${v.ok} allowed=${v.allowed} reason=${v.reason} answered=${answered} presentation_nonempty=${s.presentation !== null && s.presentation !== undefined}`);
      process.exit(0);
    }
    if (Date.now() > deadline) { console.log('RESULT status=pending (nothing reached the verifier within the wait)'); process.exit(0); }
    await new Promise((r2) => setTimeout(r2, 1000));
  }
}
