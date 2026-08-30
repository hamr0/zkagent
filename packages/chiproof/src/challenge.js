// SPDX-License-Identifier: Apache-2.0
/**
 * The signed, self-authenticating challenge (PRD D20; M1 spec §5 bucket B1).
 *
 * Nonce pattern ported from 8een (https://github.com/hamr0/8een `src/challenge.js`,
 * lines 34-100 `issueChallenge`/`inspectChallenge` and 166-199 `applySingleUse`,
 * Apache-2.0), with the D20 seal amendment (owner-approved 2026-08-30): the
 * nonce is self-authenticating — `random || issued_at || HMAC(secret,
 * random || issued_at || canonical({tier, verbs, threshold, max_scan_age,
 * expires_at}))` — so "did we mint this, and is it internally consistent?" is
 * a recomputation over the PRESENTED fields, not a lookup. An unsigned tier
 * A/B challenge is therefore tamper-evident, not merely recognisable: editing
 * any field after minting breaks the tag. A forged or tampered nonce is
 * rejected by `verifyChallenge` without ever touching the nonce store. The ONE
 * piece of state this module cannot avoid — "has this nonce been spent?" — lives
 * in the adopter's store (`spendNonce`, mirroring 8een's `applySingleUse`), never
 * here: zkagent is stateless (D3, NO-GO #1).
 *
 * Unlike 8een's binary CBOR transcript, the challenge here is a JSON object with
 * its own `issued_at`/`expires_at` fields (D20). The nonce still encodes
 * `issued_at` and HMACs it, so `verifyChallenge` can catch a challenge whose
 * `issued_at` field was edited independently of the nonce that vouches for it —
 * the same "our own frame, recomputed" guarantee 8een gets from binding the
 * expiry into the CBOR transcript.
 *
 * `issueChallenge`/`verifyChallenge`/`spendNonce` never throw on untrusted input
 * (challenge objects, presented nonces, store failures); they classify it into a
 * verdict instead, extending the §3 `ok`/`allowed` invariant (see `verdict.js`)
 * to a local `ok`/`valid` shape, matching the evidence-plug contract each later
 * bucket must also honour (M1 spec §4). `issueChallenge` (like 8een's) DOES throw
 * on bad *configuration* (a missing/weak secret, a non-positive ttl) — a config
 * error should fail loud at the boundary, not be smuggled into an attacker-shaped
 * "unrecognized" result.
 */

import { randomBytes, createHmac, timingSafeEqual, sign as edSign, verify as edVerify } from 'node:crypto';
import { canonicalize } from './canonical.js';

const RANDOM_LEN = 16;
const ISSUED_AT_LEN = 8; // uint64 BE, ms since epoch
const TAG_LEN = 32; // HMAC-SHA256
const PAYLOAD_LEN = RANDOM_LEN + ISSUED_AT_LEN; // 24
const NONCE_BYTES_LEN = PAYLOAD_LEN + TAG_LEN; // 56

/** A/B/C ordered so a numeric ceiling comparison is possible (D19/D20). */
const TIER_ORDER = Object.freeze({ A: 0, B: 1, C: 2 });

function tierRank(tier) {
  return Object.prototype.hasOwnProperty.call(TIER_ORDER, tier) ? TIER_ORDER[tier] : undefined;
}

function assertSecret(secret) {
  // A short or absent secret is a config error that silently weakens the whole
  // gate; fail LOUD at the boundary rather than mint forgeable nonces.
  if (!(secret instanceof Uint8Array) && typeof secret !== 'string') {
    throw new TypeError('challenge secret must be a Buffer/Uint8Array or string');
  }
  if (secret.length < 16) {
    throw new TypeError('challenge secret must be at least 16 bytes of entropy');
  }
}

function hmac(secret, payload) {
  return createHmac('sha256', secret).update(payload).digest();
}

/** The sealed fields, in canonical form; throws on uncanonicalizable input. */
function sealedFields({ tier, verbs, threshold, max_scan_age: maxScanAge, expires_at: expiresAt }) {
  return Buffer.from(canonicalize({
    tier, verbs, threshold, max_scan_age: maxScanAge, expires_at: expiresAt,
  }), 'utf8');
}

function mintNonce(secret, issuedAt, fields) {
  const random = randomBytes(RANDOM_LEN);
  const issuedAtBuf = Buffer.alloc(ISSUED_AT_LEN);
  issuedAtBuf.writeBigUInt64BE(BigInt(issuedAt));
  const payload = Buffer.concat([random, issuedAtBuf]);
  const tag = hmac(secret, Buffer.concat([payload, sealedFields(fields)]));
  return Buffer.concat([payload, tag]).toString('base64url');
}

/**
 * Recompute whether `nonce` is one we minted with `secret` OVER THESE FIELDS,
 * and read back the `issued_at` it encodes. Never throws — untrusted input in,
 * a classification out.
 *
 * @param {unknown} nonce
 * @param {Buffer|Uint8Array|string} secret
 * @param {object} fields the presented challenge (its sealed fields are read)
 * @returns {{recognized: boolean, issuedAt?: number}}
 */
function inspectNonce(nonce, secret, fields) {
  try {
    assertSecret(secret);
    if (typeof nonce !== 'string' || nonce.length === 0) return { recognized: false };
    const buf = Buffer.from(nonce, 'base64url');
    if (buf.length !== NONCE_BYTES_LEN) return { recognized: false };
    const payload = buf.subarray(0, PAYLOAD_LEN);
    const tag = buf.subarray(PAYLOAD_LEN);
    const expect = hmac(secret, Buffer.concat([payload, sealedFields(fields)]));
    if (tag.length !== expect.length || !timingSafeEqual(tag, expect)) return { recognized: false };
    const issuedAt = Number(payload.readBigUInt64BE(RANDOM_LEN));
    return { recognized: true, issuedAt };
  } catch {
    // A malformed secret or any surprise in untrusted-input handling is "not a
    // nonce we can vouch for" — never a thrown exception.
    return { recognized: false };
  }
}

/** Sign/verify Ed25519 over the canonical JSON of the challenge, `sig` excluded. */
function signableBytes(challenge) {
  const { sig, ...rest } = challenge;
  return Buffer.from(canonicalize(rest), 'utf8');
}

function signChallenge(privateKey, challengeWithoutSig) {
  return edSign(null, signableBytes(challengeWithoutSig), privateKey).toString('base64');
}

function verifySignature(publicKey, challengeWithoutSig, sigB64) {
  try {
    if (typeof sigB64 !== 'string' || sigB64.length === 0) return false;
    const sig = Buffer.from(sigB64, 'base64');
    return edVerify(null, signableBytes(challengeWithoutSig), publicKey, sig);
  } catch {
    return false;
  }
}

/**
 * Mint a fresh, self-authenticating challenge (D20).
 *
 * @param {{
 *   tier: 'A'|'B'|'C', verbs?: string[], threshold: number,
 *   max_scan_age?: number|null, ttlMs: number,
 *   issuer?: {privateKey: unknown, key_id: string}|null,
 *   challengeSecret: Buffer|Uint8Array|string, now?: number,
 * }} opts
 *   `challengeSecret` is the HMAC key for the self-authenticating nonce — stable across
 *   restarts and shared across every replica that later calls `verifyChallenge`
 *   (a per-process secret would reject a sibling's nonces). Not listed among the
 *   challenge's own fields in the M1 spec's one-line signature, but required to
 *   mint the HMAC nonce it also specifies — added here as a config input,
 *   mirroring how `verifyChallenge` receives it. `issuer` is omitted (or `null`)
 *   for an unsigned challenge, valid at tiers A/B only (D20).
 * @returns {{nonce: string, tier: string, verbs: string[], threshold: number,
 *   max_scan_age: number|null, issued_at: number, expires_at: number,
 *   key_id?: string, sig?: string}}
 */
export function issueChallenge({
  tier, verbs = [], threshold, max_scan_age = null, ttlMs, issuer = null, challengeSecret, now = Date.now(),
}) {
  if (tierRank(tier) === undefined) {
    throw new TypeError(`issueChallenge: tier must be 'A', 'B', or 'C', got ${JSON.stringify(tier)}`);
  }
  if (typeof threshold !== 'number' || !Number.isFinite(threshold)) {
    throw new TypeError(`issueChallenge: threshold must be a finite number, got ${threshold}`);
  }
  if (typeof ttlMs !== 'number' || !Number.isFinite(ttlMs) || ttlMs <= 0) {
    throw new TypeError(`issueChallenge: ttlMs must be a positive finite number, got ${ttlMs}`);
  }
  if (typeof now !== 'number' || !Number.isFinite(now)) {
    throw new TypeError(`issueChallenge: now must be a finite number, got ${now}`);
  }
  assertSecret(challengeSecret);

  const issuedAt = Math.floor(now);
  const expiresAt = issuedAt + Math.floor(ttlMs);

  /** @type {any} */
  const challenge = {
    tier,
    verbs: Array.isArray(verbs) ? verbs : [],
    threshold,
    max_scan_age,
    issued_at: issuedAt,
    expires_at: expiresAt,
  };
  // Seal AFTER the fields are final: the tag covers every one of them.
  challenge.nonce = mintNonce(challengeSecret, issuedAt, challenge);

  if (issuer) {
    const { privateKey, key_id: keyId } = issuer;
    if (typeof keyId !== 'string' || keyId.length === 0) {
      throw new TypeError('issueChallenge: issuer.key_id must be a non-empty string');
    }
    if (privateKey == null) {
      throw new TypeError('issueChallenge: issuer.privateKey is required to sign');
    }
    challenge.key_id = keyId;
    challenge.sig = signChallenge(privateKey, challenge);
  }

  return challenge;
}

/**
 * Classify a presented challenge: is it one we minted, is it still live, and — if
 * signed, or required to be at this tier — does the signature check out against a
 * pinned issuer? Never throws.
 *
 * @param {unknown} challenge
 * @param {{now?: number, challengeSecret: Buffer|Uint8Array|string,
 *   trustedChallengeIssuers?: {pubkey: unknown, key_id: string, maxTier: 'A'|'B'|'C'}[],
 *   skewMs?: number}} opts
 *   `skewMs` (default 5 min) is how far `issued_at` may sit in the future before
 *   it is refused rather than merely "not yet" — a clock-skew allowance, not a
 *   grant of trust.
 * @returns {{ok: boolean, valid: boolean|null, reason: string}}
 */
export function verifyChallenge(challenge, {
  now = Date.now(), challengeSecret, trustedChallengeIssuers = [], skewMs = 300_000,
} = {}) {
  if (!challenge || typeof challenge !== 'object') {
    return { ok: true, valid: false, reason: 'challenge_malformed' };
  }
  const { nonce, tier, issued_at: issuedAt, expires_at: expiresAt, key_id: keyId, sig } = challenge;

  if (tierRank(tier) === undefined) {
    return { ok: true, valid: false, reason: 'challenge_malformed' };
  }
  if (typeof issuedAt !== 'number' || !Number.isFinite(issuedAt)
    || typeof expiresAt !== 'number' || !Number.isFinite(expiresAt)) {
    return { ok: true, valid: false, reason: 'challenge_malformed' };
  }
  if (typeof now !== 'number' || !Number.isFinite(now)) {
    return { ok: false, valid: null, reason: 'clock_unavailable' };
  }

  // The nonce must be ours over the PRESENTED fields (tier, verbs, threshold,
  // max_scan_age, expires_at), and its encoded issued_at must match the field
  // the caller is presenting -- any edit after minting is `nonce_forged`. This
  // runs BEFORE any store is touched (there is none here) and before
  // expiry/signature checks, so a forged nonce is refused as cheaply as possible.
  const nonceCheck = inspectNonce(nonce, challengeSecret, challenge);
  if (!nonceCheck.recognized || nonceCheck.issuedAt !== issuedAt) {
    return { ok: true, valid: false, reason: 'nonce_forged' };
  }

  if (issuedAt > now + skewMs) {
    return { ok: true, valid: false, reason: 'challenge_issued_in_future' };
  }
  if (now > expiresAt) {
    return { ok: true, valid: false, reason: 'challenge_expired' };
  }

  // Signature rules (D20): a signed challenge is always checked against the
  // pinned issuer list, whatever its tier. An UNSIGNED challenge is accepted at
  // tiers A/B (nothing to protect the tier does not already protect) and refused
  // at tier C.
  if (typeof sig === 'string' && sig.length > 0) {
    if (typeof keyId !== 'string' || keyId.length === 0) {
      return { ok: true, valid: false, reason: 'signature_missing_key_id' };
    }
    const issuerEntry = trustedChallengeIssuers.find((i) => i && i.key_id === keyId);
    if (!issuerEntry) {
      return { ok: true, valid: false, reason: 'signature_unknown_issuer' };
    }
    const ceilingRank = tierRank(issuerEntry.maxTier);
    if (ceilingRank === undefined || tierRank(tier) > ceilingRank) {
      return { ok: true, valid: false, reason: 'tier_exceeds_issuer_ceiling' };
    }
    if (!verifySignature(issuerEntry.pubkey, challenge, sig)) {
      return { ok: true, valid: false, reason: 'signature_invalid' };
    }
  } else if (tier === 'C') {
    return { ok: true, valid: false, reason: 'signature_required_at_tier_c' };
  }

  return { ok: true, valid: true, reason: 'challenge_valid' };
}

/**
 * Spend a challenge's nonce exactly once, through the adopter's atomic store.
 * Mirrors 8een's `applySingleUse` (src/challenge.js:166-199): the ONE piece of
 * state this module needs lives in the adopter's `NonceStore`, never here.
 *
 * @typedef {{setIfAbsent(key: string, ttlMs: number): Promise<boolean>}} NonceStore
 *   An atomic "SET NX PX"-shaped store (e.g. Redis `SET key NX PX ttl`).
 *   `setIfAbsent` returns `true` on first use (fresh) and `false` if the key was
 *   already present (a replay).
 *
 * @param {{nonce?: unknown, expires_at?: unknown}} challenge
 * @param {NonceStore} store
 * @param {{now?: number}} [opts]
 * @returns {Promise<{ok: boolean, valid: boolean|null, reason: string}>}
 */
export async function spendNonce(challenge, store, { now = Date.now() } = {}) {
  if (!challenge || typeof challenge.nonce !== 'string' || challenge.nonce.length === 0) {
    return { ok: true, valid: false, reason: 'challenge_malformed' };
  }
  if (!store || typeof store.setIfAbsent !== 'function') {
    return { ok: false, valid: null, reason: 'nonce_store_misconfigured' };
  }
  const expiresAt = Number(challenge.expires_at);
  const ttlMs = Number.isFinite(expiresAt) ? Math.max(0, expiresAt - now) : 0;

  let fresh;
  try {
    fresh = await store.setIfAbsent(challenge.nonce, ttlMs);
  } catch {
    // A store we cannot reach is not evidence about anyone -- fail closed, never
    // a "no" (the §3 invariant extended to the store boundary, D3/NO-GO #1).
    return { ok: false, valid: null, reason: 'nonce_store_unreachable' };
  }
  if (!fresh) {
    return { ok: true, valid: false, reason: 'nonce_replayed' };
  }
  return { ok: true, valid: true, reason: 'nonce_fresh' };
}
