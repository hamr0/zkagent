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
// bucket, the POC answer carries a name / boundary / boolean triple. Nothing in this module logs a tested value:
// the name, number and codes a request carries are only ever signed into the request, never logged or echoed.

import { randomBytes } from 'node:crypto';

export const POC_SPEC = 'zkagent-poc-tierc/0';
/** Item 9 / D97: the fixed expiry buckets, in calendar months. */
export const EXPIRY_BUCKET_MONTHS = Object.freeze([3, 6, 12]);
/** Item 9 / D97: the fixed date-of-birth ages, identical to D74's list. */
export const DOB_AGES = Object.freeze([15, 16, 18, 21, 60, 65]);
/** Item 8(d) / D102: an exclusion set may name at most this many countries. */
export const MAX_EXCLUSION_SET = 10;
const DEFAULT_PREDICATES = Object.freeze([{ name: 'expiry_beyond', months: 3 }]);

const isCode = (c) => typeof c === 'string' && /^[A-Za-z]{1,3}$/.test(c.trim());
const nonBlank = (s) => typeof s === 'string' && s.replace(/[<\s-]/g, '').length > 0;

/** Validates the predicates a caller wants to ask: all five item-8 kinds, POC-local wire. This mirrors what the scanner
 * enforces holder-side (site-side enforcement is the point of item 14, which replaces this with chiproof in chunk 2).
 * Returns {ok:true, predicates} or {ok:false, reason}. Nothing here logs a value. */
export function validatePredicates(input) {
  const list = input === undefined ? DEFAULT_PREDICATES : input;
  if (!Array.isArray(list) || list.length === 0) return { ok: false, reason: 'predicates_invalid' };
  const out = [];
  for (const p of list) {
    if (p === null || typeof p !== 'object') return { ok: false, reason: 'predicate_not_in_poc' };
    switch (p.name) {
      case 'expiry_beyond':
        if (!Number.isInteger(p.months) || !EXPIRY_BUCKET_MONTHS.includes(p.months)) return { ok: false, reason: 'boundary_off_list' };
        out.push({ name: 'expiry_beyond', months: p.months });
        break;
      case 'dob_at_least':
        if (!Number.isInteger(p.age) || !DOB_AGES.includes(p.age)) return { ok: false, reason: 'boundary_off_list' };
        out.push({ name: 'dob_at_least', age: p.age });
        break;
      case 'nationality_is':
        if (!isCode(p.code)) return { ok: false, reason: 'code_invalid' };
        out.push({ name: 'nationality_is', code: p.code.trim().toUpperCase() });
        break;
      case 'nationality_not_in':
        if (!Array.isArray(p.codes) || p.codes.length === 0 || p.codes.length > MAX_EXCLUSION_SET) return { ok: false, reason: 'exclusion_set_size' };
        if (!p.codes.every(isCode)) return { ok: false, reason: 'code_invalid' };
        out.push({ name: 'nationality_not_in', codes: p.codes.map((c) => c.trim().toUpperCase()) });
        break;
      case 'name_match':
        if (!nonBlank(p.given_names) || !nonBlank(p.surname)) return { ok: false, reason: 'name_fields_invalid' };
        out.push({ name: 'name_match', given_names: p.given_names, surname: p.surname });
        break;
      case 'document_number_is':
        if (!nonBlank(p.number)) return { ok: false, reason: 'number_invalid' };
        out.push({ name: 'document_number_is', number: p.number });
        break;
      default:
        return { ok: false, reason: 'predicate_not_in_poc' };
    }
  }
  return { ok: true, predicates: out };
}

/** The fixed boundary an answer must echo, or undefined for kinds with none. */
const boundaryOf = (p) => (p.name === 'expiry_beyond' ? p.months : p.name === 'dob_at_least' ? p.age : undefined);

/** Value-free label of one predicate for logs: its name and fixed boundary, never a name, number or code. */
export function predicateLabel(p) {
  const b = boundaryOf(p);
  return b === undefined ? p.name : `${p.name}:${b}`;
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
    // One answer per predicate, in request order: the name and the fixed boundary (months / age) must echo what was asked.
    for (let i = 0; i < answers.length; i += 1) {
      const a = answers[i];
      const asked = tx.predicates[i];
      if (a === null || typeof a !== 'object') return realNo('answers_do_not_match_request');
      if (typeof a.result !== 'boolean') return realNo('answer_not_boolean');
      if (a.name !== asked.name || a.months !== asked.months || a.age !== asked.age) return realNo('answers_do_not_match_request');
    }
    return {
      ok: true, allowed: true, tier: 'C', reason: 'answers-received',
      answers: answers.map((a, i) => {
        const o = { name: a.name };
        const b = boundaryOf(tx.predicates[i]);
        if (tx.predicates[i].name === 'expiry_beyond') o.months = b;
        if (tx.predicates[i].name === 'dob_at_least') o.age = b;
        o.result = a.result;
        return o;
      }),
    };
  } catch {
    return cannotCheck('verifier_internal_error');
  }
}

/** Value-free log fragment: predicate names, fixed boundaries and the boolean answers, nothing else. */
export function describeVerdictForLog(verdict) {
  if (!verdict || !Array.isArray(verdict.answers)) return 'answered=none';
  return `answered=${verdict.answers.map((a) => `${predicateLabel(a)}=${a.result}`).join(',')}`;
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
