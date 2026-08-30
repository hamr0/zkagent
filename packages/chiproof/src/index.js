// SPDX-License-Identifier: Apache-2.0
/**
 * chiproof public surface (M1 buckets B1 + B2 + B3).
 *
 * `createVerifier(config)` validates config loudly at boot and returns
 * `{ issueChallenge, verify }`. `verify()` is the B2 core: presentation shape,
 * challenge liveness + single use, tier negotiation, threshold match (D11),
 * zktag / chip_auth presence rules, FR10 trust list, then the evidence slot (B3,
 * `evidence.js`) with plugs registered from `config.evidence.plugs`.
 */
export { cannotCheck, realNo, yes } from './verdict.js';
export { canonicalize, sha256 } from './canonical.js';
export { issueChallenge, verifyChallenge, spendNonce } from './challenge.js';
export { InMemoryNonceStore } from './stores/memory.js';
export { EvidenceRegistry, assertPlug, routeEvidence } from './evidence.js';
export { signedReceipt, receiptMessage } from './plugs/signed-receipt.js';
export { zkPassport, subscopeFromNonce, scopeField, paramCommitment } from './plugs/zk-passport.js';

import { cannotCheck, realNo, yes } from './verdict.js';
import {
  issueChallenge as issueChallengeImpl, verifyChallenge, spendNonce,
} from './challenge.js';
import { InMemoryNonceStore } from './stores/memory.js';
import { EvidenceRegistry, routeEvidence } from './evidence.js';

const SPEC = 'zkagent/1';
const TIER_ORDER = Object.freeze({ A: 0, B: 1, C: 2 });

function tierRank(tier) {
  return Object.prototype.hasOwnProperty.call(TIER_ORDER, tier) ? TIER_ORDER[tier] : undefined;
}

function isPlainObject(v) {
  return v !== null && typeof v === 'object' && !Array.isArray(v);
}

/**
 * Validate a verifier config and construct the verifier. Fails LOUD at
 * construction on a config shape that would silently weaken the verifier —
 * booting with `InMemoryNonceStore` outside tests is exactly that shape:
 * replay defence that looks like it works but does not survive a restart or a
 * second replica (mirrors 8een's `startGate` fail-closed construction-time
 * checks, `src/gate.js:372-404`).
 *
 * @param {{
 *   stores: {nonce: import('./challenge.js').NonceStore},
 *   challengeSecret: Buffer|Uint8Array|string,
 *   threshold?: number, tiers?: {max: 'A'|'B'|'C'},
 *   trustedChallengeIssuers?: {pubkey: unknown, key_id: string, maxTier: 'A'|'B'|'C'}[],
 *   trustedClients?: {name?: string, package: string, certDigest: string, specVersion?: string}[],
 *   evidence?: {require?: string[], accept?: string[], plugs?: Record<string, object>},
 *   scopeDomain: string, masterlistRoot?: string,
 *   allowInMemoryStore?: boolean,
 * }} config
 * @returns {{issueChallenge: (opts: object) => object,
 *   verify: (presentation: unknown, ctx?: {now?: number, clientIdentity?: object}) => Promise<object>}}
 */
export function createVerifier(config) {
  if (!isPlainObject(config)) {
    throw new TypeError('createVerifier: config must be an object');
  }
  const nonceStore = config.stores?.nonce;
  if (!nonceStore || typeof nonceStore.setIfAbsent !== 'function') {
    throw new TypeError('createVerifier: config.stores.nonce must implement setIfAbsent(key, ttlMs)');
  }
  if (nonceStore instanceof InMemoryNonceStore) {
    const allowed = config.allowInMemoryStore === true || process.env.NODE_ENV === 'test';
    if (!allowed) {
      throw new TypeError(
        'createVerifier refuses to boot with InMemoryNonceStore outside tests: it does '
        + 'not survive a restart or a second replica, so replay defence would silently '
        + 'fail behind more than one process. Pass a real atomic store, or set '
        + 'allowInMemoryStore:true to override deliberately.',
      );
    }
  }
  if (config.challengeSecret == null) {
    throw new TypeError('createVerifier: config.challengeSecret (the challenge HMAC key) is required');
  }

  const threshold = config.threshold === undefined ? 18 : config.threshold;
  if (!Number.isInteger(threshold)) {
    throw new TypeError(`createVerifier: config.threshold must be an integer, got ${threshold}`);
  }
  // Fail-closed default: with no `tiers.max` configured, only bare tier A is
  // honoured — an adopter must opt IN to B/C, never fall into them.
  const maxTier = config.tiers?.max === undefined ? 'A' : config.tiers.max;
  if (tierRank(maxTier) === undefined) {
    throw new TypeError(`createVerifier: config.tiers.max must be 'A', 'B', or 'C', got ${JSON.stringify(maxTier)}`);
  }
  const trustedChallengeIssuers = config.trustedChallengeIssuers ?? [];
  if (!Array.isArray(trustedChallengeIssuers)) {
    throw new TypeError('createVerifier: config.trustedChallengeIssuers must be an array');
  }
  if (typeof config.scopeDomain !== 'string' || config.scopeDomain.length === 0) {
    throw new TypeError('createVerifier: config.scopeDomain (this verifier\'s own scope, bound into evidence) is required');
  }
  const trustedClients = config.trustedClients ?? [];
  if (!Array.isArray(trustedClients)) {
    throw new TypeError('createVerifier: config.trustedClients must be an array');
  }

  // --- evidence slot (§4): plugs registered at boot, lists must name them ---
  const ev = config.evidence ?? {};
  if (!isPlainObject(ev)) throw new TypeError('createVerifier: config.evidence must be an object');
  const registry = new EvidenceRegistry();
  const plugs = ev.plugs ?? {};
  if (!isPlainObject(plugs)) throw new TypeError('createVerifier: config.evidence.plugs must be an object');
  for (const [type, plug] of Object.entries(plugs)) registry.registerPlug(type, plug); // throws on a bad plug
  const listOf = (name) => {
    const list = ev[name] ?? [];
    if (!Array.isArray(list)) throw new TypeError(`createVerifier: config.evidence.${name} must be an array`);
    for (const t of list) {
      if (!registry.has(t)) throw new TypeError(`createVerifier: config.evidence.${name} names "${t}" but no such plug is registered`);
    }
    return Object.freeze([...list]);
  };
  const slot = Object.freeze({ registry, require: listOf('require'), accept: listOf('accept') });

  const settled = Object.freeze({
    challengeSecret: config.challengeSecret, threshold, maxTier, trustedChallengeIssuers, trustedClients, nonceStore,
    slot, scopeDomain: config.scopeDomain, masterlistRoot: config.masterlistRoot,
  });

  return Object.freeze({
    issueChallenge: (opts = {}) => {
      // Ruling 2026-08-30: the verifier serves ONE threshold. A caller asking
      // for another is a config error -- fail loud, never mint a challenge
      // verify() would then refuse.
      if (opts.threshold !== undefined && opts.threshold !== settled.threshold) {
        throw new TypeError(`issueChallenge: threshold ${opts.threshold} differs from config.threshold ${settled.threshold}`);
      }
      return issueChallengeImpl({ ...opts, threshold: settled.threshold, challengeSecret: settled.challengeSecret });
    },
    verify: (presentation, ctx) => verify(settled, presentation, ctx),
  });
}

/**
 * The B2 check pipeline, in this order (each step refuses before the next runs):
 * spec → shape → challenge (ours + live + signature) → spend nonce → tier →
 * threshold → zktag / chip_auth → trust list → evidence slot → yes.
 *
 * Never throws. Every verdict is built through the verdict.js factories, so
 * the §3 invariant (`ok:false ⇒ allowed:null`) cannot be violated from here.
 */
async function verify(settled, presentation, ctx) {
  try {
    return await verifyInner(settled, presentation, ctx);
  } catch {
    // An unexpected internal failure is not evidence about anyone: fail
    // closed as "could not check", never as a "no".
    return cannotCheck('verifier_internal_error');
  }
}

async function verifyInner(settled, presentation, ctx) {
  const now = ctx?.now === undefined ? Date.now() : ctx.now;
  if (typeof now !== 'number' || !Number.isFinite(now)) {
    return cannotCheck('clock_unavailable');
  }

  // --- spec ---------------------------------------------------------------
  if (!isPlainObject(presentation)) return realNo('presentation_malformed');
  if (presentation.spec !== SPEC) return realNo('unsupported_spec');

  // --- shape --------------------------------------------------------------
  const {
    tier, claim, challenge, zktag, chip_auth: chipAuth, evidence,
  } = presentation;
  if (tierRank(tier) === undefined) return realNo('tier_invalid');
  if (!isPlainObject(challenge)) return realNo('presentation_malformed');
  if (!isPlainObject(claim)
    || Object.keys(claim).length !== 2
    || typeof claim.over_threshold !== 'boolean'
    || !Number.isInteger(claim.threshold)) {
    return realNo('claim_malformed');
  }
  if (evidence !== undefined && !Array.isArray(evidence)) return realNo('evidence_malformed');
  if (!Number.isInteger(challenge.threshold)) return realNo('challenge_malformed');

  // --- challenge: ours, live, signed where required -----------------------
  const ch = verifyChallenge(challenge, {
    now, challengeSecret: settled.challengeSecret, trustedChallengeIssuers: settled.trustedChallengeIssuers,
  });
  if (!ch.ok) return cannotCheck(ch.reason);
  if (!ch.valid) return realNo(ch.reason);

  // --- single use: one presentation per challenge, whatever the outcome ---
  const spend = await spendNonce(challenge, settled.nonceStore, { now });
  if (!spend.ok) return cannotCheck(spend.reason);
  if (!spend.valid) return realNo(spend.reason);

  // --- tier: refuse any mismatch, never downgrade -------------------------
  const presented = tierRank(tier);
  const requested = tierRank(challenge.tier);
  if (presented > requested) return realNo('tier_exceeds_requested');
  if (presented < requested) return realNo('tier_below_requested');
  if (presented > tierRank(settled.maxTier)) return realNo('tier_exceeds_max');

  // --- threshold (D11): proof for another threshold is not "close enough" -
  if (claim.threshold !== challenge.threshold || claim.threshold !== settled.threshold) {
    return realNo('threshold_mismatch');
  }
  if (claim.over_threshold !== true) return realNo('under_threshold');

  // --- zktag / chip_auth presence rules (D21) -----------------------------
  if (tier === 'A') {
    if (zktag !== undefined) return realNo('zktag_forbidden_at_tier_a');
    if (chipAuth !== undefined) return realNo('chip_auth_forbidden_at_tier_a');
  } else {
    if (typeof zktag !== 'string' || zktag.length === 0) return realNo('zktag_required');
    if (chipAuth !== undefined && !isPlainObject(chipAuth)) return realNo('chip_auth_malformed');
  }

  // --- FR10 trust list: identity comes from ctx, never self-declared ------
  const identity = ctx?.clientIdentity;
  if (identity !== undefined && settled.trustedClients.length > 0) {
    if (!isPlainObject(identity)
      || typeof identity.package !== 'string' || typeof identity.certDigest !== 'string') {
      return realNo('client_untrusted');
    }
    const match = settled.trustedClients.some((c) => c
      && c.package === identity.package
      && c.certDigest === identity.certDigest
      && (c.specVersion === undefined || identity.specVersion === undefined
        || c.specVersion === identity.specVersion));
    if (!match) return realNo('client_untrusted');
  }

  // --- evidence slot (§4, D24) --------------------------------------------
  const plugCtx = Object.freeze({
    nonce: challenge.nonce, claim, tier, scopeDomain: settled.scopeDomain,
    masterlistRoot: settled.masterlistRoot, trustedClients: settled.trustedClients, now,
  });
  const routed = await routeEvidence(settled.slot, evidence ?? [], tier, plugCtx);
  if (routed.ok !== undefined) return routed; // a verdict: refused or could not check

  const reason = settled.slot.require.length === 0 ? 'no-evidence-required' : 'evidence-verified';
  return tier === 'A'
    ? yes({ tier, reason, evidence: routed.verified })
    : yes({ tier, zktag, reason, evidence: routed.verified });
}
