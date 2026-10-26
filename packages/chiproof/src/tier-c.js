// SPDX-License-Identifier: Apache-2.0
/**
 * Tier C — "prove specific attributes" (milestones.md §6.9.2 items 14 and 8/9, D90, D95, D97, D109, D110).
 *
 * The SITE's side only. chiproof has no knowledge of the holder app's pin list (the app refuses a site that is not
 * on its build's list; the site learns nothing). What lives here is the same fixed vocabulary and the same fixed
 * boundary lists the app enforces holder-side, so a dishonest scanner build is not the only place they are checked.
 *
 * WIRE (final; replaces the demo's POC-local `zkagent-poc-tierc/0`):
 *
 *  - the challenge is a normal D20 challenge (signed at tier C) with `tier:'C'`, `threshold:null` and a `predicates`
 *    array. The signature covers `predicates`, so the app and the verifier both read one tamper-evident list.
 *    A predicate is one of
 *      `{name:'expiry_beyond', months:3|6|12}`
 *      `{name:'dob_at_least', age:15|16|18|21|60|65}`
 *      `{name:'nationality_is', code:'NLD'}`                        (1 to 3 letters A-Z)
 *      `{name:'nationality_not_in', codes:['CUB','RUS']}`           (1 to 10 such codes)
 *      `{name:'name_match', given_names:'..', surname:'..'}`        (two labelled fields, D109(k))
 *      `{name:'document_number_is', number:'..'}`
 *  - the answer is `{spec:'zkagent/1', tier:'C', challenge, answers:[{name, [months|age], result:boolean}, ...]}`:
 *    one entry per predicate, in the challenge's order, echoing the name and the fixed boundary (months, age) and
 *    nothing else. A site-supplied name, number or code is never echoed (an extra key is refused).
 *  - a refusal is the standard OpenID4VP error response on the same `direct_post`: `error=access_denied` plus
 *    `state`, the same body for every reason (D110). See [verifyTierCRefusal].
 *
 * `allowed:true` on an answers verdict means only "a well-formed answer to exactly what the challenge asked
 * arrived". The booleans inside `answers` ARE the answers, and a `false` among them is a legitimate answer, not a
 * failure. A relying party must read `answers`, never treat `allowed` as "every question was yes".
 *
 * Nothing here throws on untrusted input (D4): `ok:false` forces `allowed:null`, a real "no" is `ok:true,
 * allowed:false`. The verdict factories are the only way a verdict is built.
 */

import { cannotCheck, realNo } from './verdict.js';

/** Item 9 / D97: the fixed expiry buckets, in calendar months. */
export const TIER_C_EXPIRY_MONTHS = Object.freeze([3, 6, 12]);
/** Item 9 / D97: the fixed date-of-birth ages, identical to D74's list. */
export const TIER_C_DOB_AGES = Object.freeze([15, 16, 18, 21, 60, 65]);
/** Item 8(d) / D102: an exclusion set names at most this many countries. */
export const TIER_C_MAX_EXCLUSION_SET = 10;

const CODE = /^[A-Za-z]{1,3}$/;
const isCode = (c) => typeof c === 'string' && CODE.test(c.trim());
const normCode = (c) => c.trim().toUpperCase();
/** Non-blank once the MRZ filler `<`, spaces and hyphens are removed. */
const nonBlank = (s) => typeof s === 'string' && s.replace(/[<\s-]/g, '').length > 0;
/** @param {unknown} v @returns {v is Record<string, any>} */
const isObj = (v) => v !== null && typeof v === 'object' && !Array.isArray(v);

/** Keys a predicate may carry, by name. Anything else is refused (nothing smuggled in a request). */
const PREDICATE_KEYS = Object.freeze({
  expiry_beyond: ['name', 'months'],
  dob_at_least: ['name', 'age'],
  nationality_is: ['name', 'code'],
  nationality_not_in: ['name', 'codes'],
  name_match: ['name', 'given_names', 'surname'],
  document_number_is: ['name', 'number'],
});

function onlyKeys(obj, allowed) {
  const keys = Object.keys(obj);
  return keys.length === allowed.length && keys.every((k) => allowed.includes(k));
}

/**
 * Validate (and normalise: codes trimmed and upper-cased) a list of tier-C predicates against the fixed
 * vocabulary and boundary lists. Never throws.
 *
 * @param {unknown} input
 * @returns {{ok: true, predicates: import('./types.js').TierCPredicate[]} | {ok: false, reason: string}}
 */
export function validateTierCPredicates(input) {
  if (!Array.isArray(input) || input.length === 0) return { ok: false, reason: 'predicates_invalid' };
  const out = [];
  for (const p of input) {
    if (!isObj(p)) return { ok: false, reason: 'predicate_malformed' };
    const allowed = Object.prototype.hasOwnProperty.call(PREDICATE_KEYS, p.name) ? PREDICATE_KEYS[p.name] : undefined;
    if (allowed === undefined) return { ok: false, reason: 'predicate_not_in_vocabulary' };
    if (!onlyKeys(p, allowed)) return { ok: false, reason: 'predicate_malformed' };
    switch (p.name) {
      case 'expiry_beyond':
        if (!Number.isInteger(p.months) || !TIER_C_EXPIRY_MONTHS.includes(p.months)) return { ok: false, reason: 'boundary_off_list' };
        out.push({ name: p.name, months: p.months });
        break;
      case 'dob_at_least':
        if (!Number.isInteger(p.age) || !TIER_C_DOB_AGES.includes(p.age)) return { ok: false, reason: 'boundary_off_list' };
        out.push({ name: p.name, age: p.age });
        break;
      case 'nationality_is':
        if (!isCode(p.code)) return { ok: false, reason: 'code_invalid' };
        out.push({ name: p.name, code: normCode(p.code) });
        break;
      case 'nationality_not_in':
        if (!Array.isArray(p.codes) || p.codes.length === 0 || p.codes.length > TIER_C_MAX_EXCLUSION_SET) return { ok: false, reason: 'exclusion_set_size' };
        if (!p.codes.every(isCode)) return { ok: false, reason: 'code_invalid' };
        out.push({ name: p.name, codes: p.codes.map(normCode) });
        break;
      case 'name_match':
        if (!nonBlank(p.given_names) || !nonBlank(p.surname)) return { ok: false, reason: 'name_fields_invalid' };
        out.push({ name: p.name, given_names: p.given_names, surname: p.surname });
        break;
      default: // document_number_is
        if (!nonBlank(p.number)) return { ok: false, reason: 'number_invalid' };
        out.push({ name: p.name, number: p.number });
        break;
    }
  }
  return { ok: true, predicates: /** @type {import('./types.js').TierCPredicate[]} */ (out) };
}

/** The fixed boundary an answer must echo for this predicate, as `{months}` / `{age}` / `{}`. */
function boundaryOf(p) {
  if (p.name === 'expiry_beyond') return { months: p.months };
  if (p.name === 'dob_at_least') return { age: p.age };
  return {};
}

/**
 * Value-free label of one predicate or answer for logs and pages: its name and fixed boundary, never a name, a
 * number or a country code.
 *
 * @param {{name: string, months?: number, age?: number}} p
 * @returns {string}
 */
export function tierCPredicateLabel(p) {
  if (!isObj(p)) return 'unknown';
  if (Number.isInteger(p.months)) return `${p.name}:${p.months}`;
  if (Number.isInteger(p.age)) return `${p.name}:${p.age}`;
  return String(p.name);
}

/**
 * Check a presentation's `answers` against the predicates the (already verified) challenge asked: one answer per
 * predicate, in order, the name and fixed boundary echoed, `result` a boolean, no other key. Returns the sanitised
 * answers, so only name, boundary and boolean ever leave this function. Never throws.
 *
 * @param {unknown} predicates the challenge's predicates (validated)
 * @param {unknown} answers
 * @returns {{ok: true, answers: import('./types.js').TierCAnswer[]} | {ok: false, reason: string}}
 */
export function checkTierCAnswers(predicates, answers) {
  const v = validateTierCPredicates(predicates);
  if (!v.ok) return { ok: false, reason: 'challenge_predicates_invalid' };
  if (!Array.isArray(answers) || answers.length !== v.predicates.length) return { ok: false, reason: 'answers_do_not_match_request' };
  const out = [];
  for (let i = 0; i < answers.length; i += 1) {
    const a = answers[i];
    const asked = v.predicates[i];
    if (!isObj(a)) return { ok: false, reason: 'answers_do_not_match_request' };
    const boundary = boundaryOf(asked);
    if (!onlyKeys(a, ['name', ...Object.keys(boundary), 'result'])) return { ok: false, reason: 'answers_do_not_match_request' };
    if (a.name !== asked.name) return { ok: false, reason: 'answers_do_not_match_request' };
    for (const [k, val] of Object.entries(boundary)) {
      if (a[k] !== val) return { ok: false, reason: 'answers_do_not_match_request' };
    }
    if (typeof a.result !== 'boolean') return { ok: false, reason: 'answer_not_boolean' };
    out.push({ name: asked.name, ...boundary, result: a.result });
  }
  return { ok: true, answers: out };
}

/**
 * Verify the holder app's generic refusal (D110): the OpenID4VP error response the app posts to `response_uri`
 * when it refuses a tier-C request. `response` is the already-parsed form (or JSON) body, `{error, state?}`.
 *
 * - `error === 'access_denied'` is a distinct, explicit refusal: `ok:true, allowed:false, refused:true`. It is a
 *   real "no", never a pass, and it carries no reason (the app sends the same body for every cause; any
 *   `error_description` is ignored, so nothing a site reads here can differ by cause).
 * - any other `error`, a missing `error`, or a malformed body is "could not check" (`ok:false, allowed:null`):
 *   an answer we cannot interpret says nothing about the holder.
 *
 * The caller matches `state` to its own transaction (chiproof keeps no transaction state, D3); this function only
 * classifies the response. Never throws.
 *
 * @param {unknown} response
 * @returns {{ok: false, allowed: null, reason: string} | {ok: true, allowed: false, reason: string, tier: 'C', refused: true}}
 */
export function verifyTierCRefusal(response) {
  try {
    if (!isObj(response)) return cannotCheck('refusal_malformed');
    const err = response.error;
    if (typeof err !== 'string' || err.length === 0) return cannotCheck('refusal_malformed');
    if (err !== 'access_denied') return cannotCheck('response_error_unrecognized');
    return { ...realNo('refused_by_holder'), tier: /** @type {const} */ ('C'), refused: /** @type {const} */ (true) };
  } catch {
    return cannotCheck('verifier_internal_error');
  }
}
