// SPDX-License-Identifier: Apache-2.0
/**
 * Evidence slot (M1 spec §4, PRD D24): a registry of plugs and the routing that
 * decides which presented evidence items are checked, by whom, and how a plug's
 * answer maps onto the §3 verdict invariant.
 *
 * A plug is `{ binds: {nonce, claim, scope}, linkability, tierCeiling, verify }`:
 *   - `binds` — every one of the three must be `true`, or registration throws.
 *     Evidence that cannot be tied to THIS challenge, THIS claim and THIS scope
 *     is replayable by construction, so it is refused at boot, not at verify.
 *   - `linkability` — 'none' | 'signer' | 'device'. Tier A (anonymous,
 *     unlinkable) refuses any item whose plug is not 'none'.
 *   - `tierCeiling` — the highest tier this evidence may support.
 *   - `verify(item, ctx)` → `{ ok, valid, reason, expiresAt? }` (may be async).
 *
 * Fault isolation: a plug that throws, rejects, or returns garbage is mapped to
 * `ok:false` — a broken plug is not evidence about a person.
 */
import { cannotCheck, realNo } from './verdict.js';

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
  if (tierRank(plug.tierCeiling) === undefined) {
    throw new TypeError(`registerPlug(${type}): tierCeiling must be 'A', 'B' or 'C'`);
  }
  if (typeof plug.verify !== 'function') {
    throw new TypeError(`registerPlug(${type}): plug.verify must be a function`);
  }
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
 * Route presented evidence through the registry. Returns `{ verified }` (the
 * registry keys actually checked, in presentation order) when every applicable
 * item verified and every required type was present; otherwise a verdict built
 * through the verdict.js factories.
 *
 * @param {{registry: EvidenceRegistry, require: string[], accept: string[]}} slot
 * @param {unknown[]} items  presentation.evidence (already known to be an array)
 * @param {'A'|'B'|'C'} tier presented tier
 * @param {object} ctx       plug ctx per §4: { nonce, claim, tier, scopeDomain, masterlistRoot, trustedClients, now }
 */
export async function routeEvidence(slot, items, tier, ctx) {
  const presentedRank = tierRank(tier);
  const checked = new Set(slot.require.concat(slot.accept));
  const seen = new Set();
  const toVerify = [];

  for (const item of items) {
    if (!isPlainObject(item)) return realNo('evidence_item_malformed');
    const key = itemKey(item);
    if (key === null) return realNo('evidence_item_malformed');
    const plug = slot.registry.get(key);
    if (!plug) continue; // unknown types are ignored (§4)
    if (tier === 'A' && plug.linkability !== 'none') return realNo('evidence_forbidden_at_tier_a');
    if (presentedRank > tierRank(plug.tierCeiling)) return realNo('evidence_tier_exceeds_plug_ceiling');
    if (!checked.has(key)) continue; // registered but neither required nor accepted here
    seen.add(key);
    toVerify.push([key, plug, item]);
  }

  for (const req of slot.require) {
    if (!seen.has(req)) return realNo('evidence_required_missing');
  }

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
  }
  return { verified: toVerify.map(([key]) => key) };
}
