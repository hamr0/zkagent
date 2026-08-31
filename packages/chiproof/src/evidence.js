// SPDX-License-Identifier: Apache-2.0
/**
 * Evidence slot (M1 spec §4, PRD D24): a registry of plugs and the routing that
 * decides which presented evidence items are checked, by whom, and how a plug's
 * answer maps onto the §3 verdict invariant.
 *
 * A plug is `{ binds: {nonce, claim, scope, zktag?}, linkability, tierCeiling, verify }`:
 *   - `binds` — nonce/claim/scope must each be `true`, or registration throws.
 *     Evidence that cannot be tied to THIS challenge, THIS claim and THIS scope
 *     is replayable by construction, so it is refused at boot, not at verify.
 *     `binds.zktag` is optional (default false): `true` declares the evidence
 *     is additionally tied to the presented zktag (`ctx.zktag`) — such a plug
 *     cannot run where no zktag exists (tier A), so `tierCeiling: 'A'` with
 *     `binds.zktag: true` is refused at registration, and a zktag-binding
 *     item on a tier-A presentation is "could not check", never a "no".
 *   - `linkability` — 'none' | 'signer' | 'device'. Tier A (anonymous,
 *     unlinkable) refuses any item whose plug is not 'none'.
 *   - `tierCeiling` — the highest tier this evidence may support.
 *   - `verify(item, ctx)` → `{ ok, valid, reason, expiresAt? }` (may be async).
 *
 * Fault isolation: a plug that throws, rejects, or returns garbage is mapped to
 * `ok:false` — a broken plug is not evidence about a person.
 */
import { cannotCheck, realNo } from './verdict.js';
import { canonicalize } from './canonical.js';

const LINKABILITY = new Set(['none', 'signer', 'device']);
const TIER_ORDER = Object.freeze({ A: 0, B: 1, C: 2 });

function tierRank(tier) {
  return Object.prototype.hasOwnProperty.call(TIER_ORDER, tier) ? TIER_ORDER[tier] : undefined;
}

function isPlainObject(v) {
  return v !== null && typeof v === 'object' && !Array.isArray(v);
}

/** Validate a plug's declaration. Throws a TypeError describing the first defect. */
export function assertPlug(type, plug) {
  if (typeof type !== 'string' || type.length === 0) {
    throw new TypeError('registerPlug: type must be a non-empty string like "signed-receipt/1"');
  }
  if (!isPlainObject(plug)) throw new TypeError(`registerPlug(${type}): plug must be an object`);
  const b = plug.binds;
  for (const k of ['nonce', 'claim', 'scope']) {
    if (!isPlainObject(b) || b[k] !== true) {
      throw new TypeError(`registerPlug(${type}): plug must declare binds.${k} === true — evidence that does not bind the ${k} is replayable`);
    }
  }
  if (!LINKABILITY.has(plug.linkability)) {
    throw new TypeError(`registerPlug(${type}): linkability must be 'none', 'signer' or 'device'`);
  }
  if (b.zktag !== undefined && typeof b.zktag !== 'boolean') {
    throw new TypeError(`registerPlug(${type}): binds.zktag must be a boolean when declared`);
  }
  if (tierRank(plug.tierCeiling) === undefined) {
    throw new TypeError(`registerPlug(${type}): tierCeiling must be 'A', 'B' or 'C'`);
  }
  if (b.zktag === true && plug.tierCeiling === 'A') {
    throw new TypeError(`registerPlug(${type}): binds.zktag === true is impossible with tierCeiling 'A' — tier A never carries a zktag (D21), so this plug could never run`);
  }
  if (typeof plug.verify !== 'function') {
    throw new TypeError(`registerPlug(${type}): plug.verify must be a function`);
  }
}

/**
 * Normalize `evidence.require` (plain array = instance-global, or a per-tier
 * `{A?, B?, C?}` object) into a frozen `{A, B, C}` of frozen arrays. Throws a
 * TypeError on any other shape — this is boot-time config validation.
 *
 * @param {unknown} raw  `config.evidence.require` (`undefined` = bare everywhere)
 * @returns {Required<import('./types.js').RequireByTier>}
 */
export function normalizeRequire(raw) {
  /** @type {(v: unknown, label: string) => string[]} */
  const asList = (v, label) => {
    if (!Array.isArray(v) || v.some((t) => typeof t !== 'string')) {
      throw new TypeError(`createVerifier: config.evidence.${label} must be an array of registry-key strings`);
    }
    return /** @type {string[]} */ (Object.freeze([...v]));
  };
  /** @type {string[]} */
  const none = /** @type {any} */ (Object.freeze([]));
  if (raw === undefined) {
    return Object.freeze({ A: none, B: none, C: none });
  }
  if (Array.isArray(raw)) {
    const all = asList(raw, 'require');
    return Object.freeze({ A: all, B: all, C: all });
  }
  if (isPlainObject(raw)) {
    const byTier = /** @type {{A?: unknown, B?: unknown, C?: unknown}} */ (raw);
    for (const k of Object.keys(byTier)) {
      if (tierRank(k) === undefined) {
        throw new TypeError(`createVerifier: config.evidence.require per-tier keys must be 'A', 'B' or 'C', got ${JSON.stringify(k)}`);
      }
    }
    return Object.freeze({
      A: byTier.A === undefined ? none : asList(byTier.A, 'require.A'),
      B: byTier.B === undefined ? none : asList(byTier.B, 'require.B'),
      C: byTier.C === undefined ? none : asList(byTier.C, 'require.C'),
    });
  }
  throw new TypeError('createVerifier: config.evidence.require must be an array (all tiers) or a per-tier object {A?, B?, C?}');
}

export class EvidenceRegistry {
  #plugs = new Map();

  registerPlug(type, plug) {
    assertPlug(type, plug);
    if (this.#plugs.has(type)) throw new TypeError(`registerPlug(${type}): already registered`);
    this.#plugs.set(type, plug);
    return this;
  }

  has(type) { return this.#plugs.has(type); }

  get(type) { return this.#plugs.get(type); }
}

/** The registry key for a presented item: `${type}/${version}` (§4 naming). */
export function itemKey(item) {
  if (!isPlainObject(item) || typeof item.type !== 'string') return null;
  const v = item.version;
  if (typeof v !== 'string' && !Number.isInteger(v)) return null;
  return `${item.type}/${v}`;
}

/**
 * Route presented evidence through the registry. Returns `{ verified, warnings }`
 * (the registry keys actually checked, in presentation order, and any plug
 * warnings) when every applicable
 * item verified and every required type was present; otherwise a verdict built
 * through the verdict.js factories.
 *
 * `slot.require` may be the 0.2.0 plain array (same list at every tier) or the
 * normalized per-tier `{A, B, C}` object `createVerifier` builds — both work,
 * so direct callers of this export keep their 0.2.0 semantics unchanged.
 *
 * @param {{registry: EvidenceRegistry, require: string[]|import('./types.js').RequireByTier, accept: string[], maxItems: number, maxItemBytes: number}} slot
 * @param {unknown[]} items  presentation.evidence (already known to be an array)
 * @param {'A'|'B'|'C'} tier presented tier
 * @param {import('./types.js').PlugCtx} ctx  plug ctx per §4
 */
export async function routeEvidence(slot, items, tier, ctx) {
  const presentedRank = tierRank(tier);
  const required = Array.isArray(slot.require) ? slot.require : (slot.require?.[tier] ?? []);
  const checked = new Set(required.concat(slot.accept));
  const seen = new Set();
  const keys = new Set();
  const toVerify = [];

  // Bounds (F4): the slot limits its own untrusted input BEFORE any plug runs,
  // so a presentation cannot buy N plug verifications for the price of one.
  if (items.length > slot.maxItems) return realNo('evidence_too_many');

  for (const item of items) {
    if (!isPlainObject(item)) return realNo('evidence_item_malformed');
    const key = itemKey(item);
    if (key === null) return realNo('evidence_item_malformed');
    let size;
    try { size = Buffer.byteLength(canonicalize(item), 'utf8'); } catch { return realNo('evidence_item_malformed'); }
    if (size > slot.maxItemBytes) return realNo('evidence_too_large');
    if (keys.has(key)) return realNo('evidence_duplicate');
    keys.add(key);
    const plug = slot.registry.get(key);
    if (!plug) continue; // unknown types are ignored (§4)
    if (tier === 'A' && plug.linkability !== 'none') return realNo('evidence_forbidden_at_tier_a');
    if (presentedRank > tierRank(plug.tierCeiling)) return realNo('evidence_tier_exceeds_plug_ceiling');
    if (!checked.has(key)) continue; // registered but neither required nor accepted here
    // A zktag-binding plug on a presentation with no zktag (tier A) cannot be
    // evaluated at all: not evidence about a person, so "could not check",
    // never a "no" (§3 invariant).
    if (plug.binds.zktag === true && (ctx.zktag === undefined || ctx.zktag === null)) {
      return cannotCheck('evidence_zktag_unavailable');
    }
    seen.add(key);
    toVerify.push([key, plug, item]);
  }

  for (const req of required) {
    if (!seen.has(req)) return realNo('evidence_required_missing');
  }

  const warnings = [];
  for (const [key, plug, item] of toVerify) {
    let result;
    try {
      result = await plug.verify(item, ctx);
    } catch {
      return cannotCheck('evidence_plug_failed');
    }
    if (!isPlainObject(result) || typeof result.ok !== 'boolean') return cannotCheck('evidence_plug_failed');
    if (result.ok === false) return cannotCheck(typeof result.reason === 'string' ? result.reason : 'evidence_plug_failed');
    if (result.valid !== true) return realNo(typeof result.reason === 'string' ? result.reason : `evidence_invalid:${key}`);
    // Ruling 2026-08-30 (§3 expiry precedence): a plug may date its own
    // evidence; the earlier of challenge expiry and evidence expiry wins.
    if (result.expiresAt !== undefined) {
      if (typeof result.expiresAt !== 'number' || !Number.isFinite(result.expiresAt)) return cannotCheck('evidence_plug_failed');
      if (ctx.now > result.expiresAt) return realNo('evidence_expired');
    }
    if (Array.isArray(result.warnings)) {
      for (const w of result.warnings) if (typeof w === 'string') warnings.push(w);
    }
  }
  return { verified: toVerify.map(([key]) => key), warnings };
}
