// apps/demo/tierc.mjs — the demo's small tier-C helpers (milestones.md §6.9.2 item 15, D109(g)8, D110).
//
// The tier-C verdict core is chiproof (item 14): challenge issuance, answers verification and the generic-refusal
// verdict all live there. What is left here is demo-only: the loopback gate, the value-free log line, the
// raw-predicates device-test hook, and the adb argument lists the CLI helper uses. This file replaced the opening
// POC's `tierc-poc.mjs` (its POC-local `zkagent-poc-tierc/0` wire and verifier are gone).
//
// Nothing in this file logs or returns a site-supplied name, number or country code.

import { sign as edSign } from 'node:crypto';
import { canonicalize, tierCPredicateLabel } from 'chiproof';

/** What a tier-C request asks when the caller names no predicate (the CLI helper's default). */
export const DEFAULT_PREDICATES = Object.freeze([{ name: 'expiry_beyond', months: 3 }]);

const LOOPBACK_HOSTNAMES = new Set(['127.0.0.1', 'localhost', '[::1]']);
const LOOPBACK_ADDRESSES = new Set(['127.0.0.1', '::1', '::ffff:127.0.0.1']);

/** D109(g)8: the tier-C demo is loopback only (`http://127.0.0.1:8787`, the dev signing key). True only when BOTH the
 * Host header names a loopback host AND the TCP peer is a loopback address, so neither a LAN-bound listener nor a
 * forged Host header opens it. */
export function isLoopbackRequest(hostHeader, remoteAddress) {
  try {
    const hostname = new URL(`http://${hostHeader}`).hostname;
    return LOOPBACK_HOSTNAMES.has(hostname) && LOOPBACK_ADDRESSES.has(String(remoteAddress));
  } catch {
    return false;
  }
}

/** Value-free log fragment: predicate names, fixed boundaries and the boolean answers, or the refusal marker. */
export function describeVerdictForLog(verdict) {
  if (verdict && verdict.refused === true) return 'refused=true';
  if (!verdict || !Array.isArray(verdict.answers)) return 'answered=none';
  return `answered=${verdict.answers.map((a) => `${tierCPredicateLabel(a)}=${a.result}`).join(',')}`;
}

/** DEVICE-TEST HOOK ONLY (gated by TIERC_POC_ALLOW_RAW=1 in server.mjs): re-signs a tier-C challenge over predicates
 * exactly as given, so an off-list boundary can reach the app and its whole-request refusal (and, since D110, the
 * generic `access_denied` it sends back) can be device-checked. chiproof itself never mints such a challenge. */
export function resignWithRawPredicates(challenge, rawPredicates, privateKey) {
  const { sig: _old, ...rest } = challenge;
  const next = { ...rest, predicates: rawPredicates };
  return { ...next, sig: edSign(null, Buffer.from(canonicalize(next), 'utf8'), privateKey).toString('base64') };
}

export const SCANNER_PACKAGE = 'com.zkagent.scanner';

/** `adb` argument lists for the owner's scripted launch. adb joins its arguments into ONE string for the device shell, so
 * a bare `&` in the av:// link would be eaten as a background operator; the link is therefore wrapped in literal single
 * quotes INSIDE the argument. Force-stop comes first: a stale recents task can resurface an old intent as if it were
 * fresh (findings.md #24). */
export function adbForceStopArgs() {
  return ['shell', 'am', 'force-stop', SCANNER_PACKAGE];
}
export function adbLaunchArgs(avLink) {
  return ['shell', 'am', 'start', '-a', 'android.intent.action.VIEW', '-d', `'${avLink}'`, '-p', SCANNER_PACKAGE];
}
