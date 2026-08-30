// SPDX-License-Identifier: Apache-2.0
/**
 * `signed-receipt/1` (M1 spec §5 B3): an Ed25519 signature by a pinned key over
 * `sha256(canonical(claim) ‖ nonceBytes)`.
 *
 *   item.data = { key_id, sig }   (sig base64)
 *
 * Bindings: `claim` is hashed into the message directly; `nonce` is the
 * challenge nonce's raw bytes (base64url-decoded) appended to that hash, so a
 * receipt minted for one challenge fails for every other one. `scope` is bound
 * transitively: the nonce is minted by ONE verifier under ONE challengeSecret,
 * so a receipt over that nonce cannot be presented to a verifier of another
 * scope (see the B3 report — this reading is flagged for owner confirmation).
 *
 * Linkability 'signer': the key_id says WHICH signer vouched, which links
 * presentations across sessions — never allowed at tier A.
 */
import { createHash, verify as edVerify } from 'node:crypto';
import { canonicalize } from '../canonical.js';

/** The exact bytes a signer must sign. Exported so a client can produce them. */
export function receiptMessage(claim, nonce) {
  const claimHash = createHash('sha256').update(canonicalize(claim), 'utf8').digest();
  return createHash('sha256').update(Buffer.concat([claimHash, Buffer.from(nonce, 'base64url')])).digest();
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
      let good;
      try {
        good = edVerify(null, receiptMessage(ctx.claim, ctx.nonce), pubkey, Buffer.from(sig, 'base64'));
      } catch {
        return { ok: true, valid: false, reason: 'receipt_malformed' };
      }
      return good
        ? { ok: true, valid: true, reason: 'receipt_verified' }
        : { ok: true, valid: false, reason: 'receipt_signature_invalid' };
    },
  });
}
