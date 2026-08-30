// SPDX-License-Identifier: Apache-2.0
/**
 * `signed-receipt/1` (M1 spec §5 B3): an Ed25519 signature by a pinned key over
 * `sha256(sha256(canonical(claim)) ‖ nonceBytes ‖ utf8(scopeDomain))` (ruling 2026-08-30).
 *
 *   item.data = { key_id, sig }   (sig base64)
 *
 * Bindings: `claim` is hashed into the message; `nonce` is the challenge
 * nonce's raw bytes (base64url-decoded); `scope` is the verifier's own
 * `scopeDomain` as UTF-8 — so a receipt minted for one challenge, one claim
 * and one scope fails for every other combination, explicitly.
 *
 * Linkability 'signer': the key_id says WHICH signer vouched, which links
 * presentations across sessions — never allowed at tier A.
 */
import { createHash, verify as edVerify } from 'node:crypto';
import { canonicalize } from '../canonical.js';

/** The exact bytes a signer must sign. Exported so a client can produce them. */
export function receiptMessage(claim, nonce, scopeDomain) {
  const claimHash = createHash('sha256').update(canonicalize(claim), 'utf8').digest();
  return createHash('sha256')
    .update(Buffer.concat([claimHash, Buffer.from(nonce, 'base64url'), Buffer.from(scopeDomain, 'utf8')]))
    .digest();
}

/**
 * @param {{keys: {key_id: string, pubkey: unknown}[]}} opts adopter-supplied signer keys
 */
export function signedReceipt({ keys } = {}) {
  if (!Array.isArray(keys) || keys.length === 0) {
    throw new TypeError('signed-receipt/1: registration needs a non-empty keys list [{ key_id, pubkey }]');
  }
  const byId = new Map();
  for (const k of keys) {
    if (!k || typeof k.key_id !== 'string' || k.key_id.length === 0 || k.pubkey == null) {
      throw new TypeError('signed-receipt/1: every key needs a non-empty key_id and a pubkey');
    }
    if (byId.has(k.key_id)) throw new TypeError(`signed-receipt/1: duplicate key_id ${k.key_id}`);
    byId.set(k.key_id, k.pubkey);
  }

  return Object.freeze({
    binds: Object.freeze({ nonce: true, claim: true, scope: true }),
    linkability: 'signer',
    tierCeiling: 'C',
    verify(item, ctx) {
      const data = item?.data;
      if (!data || typeof data !== 'object') return { ok: true, valid: false, reason: 'receipt_malformed' };
      const { key_id: keyId, sig } = data;
      if (typeof keyId !== 'string' || typeof sig !== 'string' || sig.length === 0) {
        return { ok: true, valid: false, reason: 'receipt_malformed' };
      }
      const pubkey = byId.get(keyId);
      if (!pubkey) return { ok: true, valid: false, reason: 'receipt_unknown_key' };
      if (typeof ctx?.scopeDomain !== 'string' || ctx.scopeDomain.length === 0) {
        return { ok: false, valid: null, reason: 'scope_domain_unconfigured' };
      }
      let good;
      try {
        good = edVerify(null, receiptMessage(ctx.claim, ctx.nonce, ctx.scopeDomain), pubkey, Buffer.from(sig, 'base64'));
      } catch {
        return { ok: true, valid: false, reason: 'receipt_malformed' };
      }
      return good
        ? { ok: true, valid: true, reason: 'receipt_verified' }
        : { ok: true, valid: false, reason: 'receipt_signature_invalid' };
    },
  });
}
