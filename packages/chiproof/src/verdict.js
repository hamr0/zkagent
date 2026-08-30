// SPDX-License-Identifier: Apache-2.0
/**
 * Verdict — the ok/allowed invariant (PRD §3, adapted from 8een's ok/over_threshold
 * pattern: https://github.com/hamr0/8een `src/verdict.js:80-92`, the
 * `answered()`/`unanswerable()` pair, Apache-2.0).
 *
 *   `ok` says whether the checker managed to check at all.
 *   `allowed` says what the answer was, and only means anything when `ok` is true.
 *
 * `ok:false` MUST force `allowed:null` — never `false`. A broken verifier that
 * says "no" is indistinguishable from a working one: it would turn away every
 * legitimate holder while looking healthy. This module is the one place the
 * invariant is enforced, so nothing downstream can build the forbidden
 * `{ok:false, allowed:false}` shape — the three factories below are the only way
 * to produce a verdict, and none of them can construct it.
 *
 * Pure. Never throws, whatever it is handed.
 */

/**
 * A verdict meaning "we could not get a trustworthy answer": `ok:false`, and
 * therefore `allowed:null`, never `false`.
 *
 * @param {string} [reason]
 * @returns {{ok: false, allowed: null, reason: string}}
 */
export function cannotCheck(reason) {
  return { ok: false, allowed: null, reason: normalizeReason(reason) };
}

/**
 * A real "no": we got an answer, and the answer was negative.
 *
 * @param {string} [reason]
 * @returns {{ok: true, allowed: false, reason: string}}
 */
export function realNo(reason) {
  return { ok: true, allowed: false, reason: normalizeReason(reason) };
}

/**
 * A real "yes": we got an answer, and the answer was positive. `extra` merges in
 * additional fields (e.g. `tier`, `zktag`, `reason`) — spread BEFORE `ok`/`allowed`
 * are set, so nothing in `extra` can ever override them.
 *
 * @param {Record<string, unknown>} [extra]
 * @returns {{ok: true, allowed: true} & Record<string, unknown>}
 */
export function yes(extra) {
  const safeExtra = extra && typeof extra === 'object' ? extra : {};
  return { ...safeExtra, ok: true, allowed: true };
}

/** A reason is always a non-empty string; anything else becomes a fixed fallback. */
function normalizeReason(reason) {
  return typeof reason === 'string' && reason.length > 0 ? reason : 'unspecified';
}
