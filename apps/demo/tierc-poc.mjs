// apps/demo/tierc-poc.mjs — M3b OPENING POC (milestones.md §6.9.1, D109(f)): the demo verifier's side of the
// minimal tier-C round trip that criteria 3 and 4 need an "answered end to end" positive control for.
//
// WHAT THIS IS NOT. It is not chiproof and not the §6.9.2 item 14 wire format (item 14 is unbuilt). The request
// carries `zkagent.predicates`, the answer is `zkagent-poc-tierc/0`, and the verdict below is this demo's own:
// a POC-local choice made so the gate criteria have something real to deliver, NOT a ruling on how chiproof will
// carry tier C. `ok:false` forces `allowed:null`; a real "no" is `ok:true, allowed:false` (the invariant every
// verdict in this project keeps); nothing here throws.
//
// WHAT IT NEVER HANDLES. No document value, no name, no number, no zktag: the POC request names an expiry
// bucket, the POC answer carries a name / bucket / boolean triple. Nothing in this module logs a tested value
// because there is none.

import { randomBytes } from 'node:crypto';

export const POC_SPEC = 'zkagent-poc-tierc/0';
/** Item 9 / D97: the fixed expiry buckets, in calendar months. */
export const EXPIRY_BUCKET_MONTHS = Object.freeze([3, 6, 12]);
const DEFAULT_PREDICATES = Object.freeze([{ name: 'expiry_beyond', months: 3 }]);

/** Validates the predicates a caller wants to ask. Returns {ok:true, predicates} or {ok:false, reason}. */
export function validatePredicates(input) {
  const list = input === undefined ? DEFAULT_PREDICATES : input;
  if (!Array.isArray(list) || list.length === 0) return { ok: false, reason: 'predicates_invalid' };
  const out = [];
  for (const p of list) {
    if (p === null || typeof p !== 'object' || p.name !== 'expiry_beyond') return { ok: false, reason: 'predicate_not_in_poc' };
    if (!Number.isInteger(p.months) || !EXPIRY_BUCKET_MONTHS.includes(p.months)) return { ok: false, reason: 'boundary_off_list' };
    out.push({ name: 'expiry_beyond', months: p.months });
  }
  return { ok: true, predicates: out };
}

/** The POC challenge: a nonce and an expiry. Deliberately no `threshold` (tier C asks no age question here, so the
 * scanner's per-site age lock must not be touched by it) and no chiproof seal. */
export function issueTierCChallenge({ ttlMs, now = Date.now() }) {
  return { nonce: randomBytes(18).toString('base64url'), tier: 'C', issued_at: now, expires_at: now + ttlMs };
}

/** The `zkagent` member of the signed request object for a tier-C transaction. */
export function buildTierCRequestMember({ predicates, challenge }) {
  return { spec: POC_SPEC, tier: 'C', predicates, challenge };
}

const realNo = (reason) => ({ ok: true, allowed: false, reason });
const cannotCheck = (reason) => ({ ok: false, allowed: null, reason });

/**
 * Verifies a tier-C POC presentation against the transaction that asked for it.
 * `tx` = { challenge, predicates }. Never throws.
 *
 * `allowed:true` here means only "a well-formed answer to exactly what was asked arrived"; the booleans inside
 * `answers` are the answers and a `false` among them is a legitimate answer, not a failure.
 */
export function verifyTierCPoc(presentation, tx, now = Date.now()) {
  try {
    if (typeof now !== 'number' || !Number.isFinite(now)) return cannotCheck('clock_unavailable');
    if (presentation === null || typeof presentation !== 'object' || Array.isArray(presentation)) return realNo('presentation_malformed');
    if (presentation.spec !== POC_SPEC) return realNo('unsupported_spec');
    if (presentation.tier !== 'C') return realNo('tier_mismatch');
    const ch = presentation.challenge;
    if (ch === null || typeof ch !== 'object' || ch.nonce !== tx.challenge.nonce) return realNo('state_challenge_mismatch');
    if (now > tx.challenge.expires_at) return realNo('challenge_expired');
    const answers = presentation.answers;
    if (!Array.isArray(answers) || answers.length !== tx.predicates.length) return realNo('answers_do_not_match_request');
    const seen = new Set();
    for (const a of answers) {
      if (a === null || typeof a !== 'object') return realNo('answers_do_not_match_request');
      if (typeof a.result !== 'boolean') return realNo('answer_not_boolean');
      const asked = tx.predicates.find((p) => p.name === a.name && p.months === a.months);
      if (!asked || seen.has(`${a.name}:${a.months}`)) return realNo('answers_do_not_match_request');
      seen.add(`${a.name}:${a.months}`);
    }
    return {
      ok: true, allowed: true, tier: 'C', reason: 'answers-received',
      answers: answers.map((a) => ({ name: a.name, months: a.months, result: a.result })),
    };
  } catch {
    return cannotCheck('verifier_internal_error');
  }
}

/** Value-free log fragment: predicate names, boundaries and the boolean answers, nothing else. */
export function describeVerdictForLog(verdict) {
  if (!verdict || !Array.isArray(verdict.answers)) return 'answered=none';
  return `answered=${verdict.answers.map((a) => `${a.name}:${a.months}=${a.result}`).join(',')}`;
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
