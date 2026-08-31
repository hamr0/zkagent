// SPDX-License-Identifier: Apache-2.0
// Test-only REFERENCE zktag-binding Ed25519 plug (0.3.0, Gap 1). NEVER shipped:
// the FR12 `sig-ed25519/1` byte layout is an M2 implementation detail still
// awaiting owner confirmation (see spikes/m2-handoff), so this fixture exists
// only to exercise the `binds.zktag` contract end-to-end — an attester-held
// Ed25519 key signs claim + nonce + scope + THE PRESENTED ZKTAG, so evidence
// bound to one zktag must die under another (the zktag-swap attack).
import { createHash, verify as edVerify } from 'node:crypto';
import { canonicalize } from '../../src/canonical.js';

export const ZKTAG_SIG_KEY = 'test-sig-ed25519-zktag/1';

/**
 * The exact bytes the attester signs. Exported so tests can build evidence.
 * Layout (test-only): sha256( domain-sep || sha256(claim) || nonce || scope || zktag ).
 */
export function zktagSigMessage(claim, nonce, scopeDomain, zktag) {
  const claimHash = createHash('sha256').update(canonicalize(claim), 'utf8').digest();
  return createHash('sha256')
    .update(Buffer.from('test-sig-ed25519-zktag/1\n', 'utf8'))
    .update(claimHash)
    .update(Buffer.from(nonce, 'utf8'))
    .update(Buffer.from(scopeDomain, 'utf8'))
    .update(Buffer.from(zktag, 'utf8'))
    .digest();
}

/**
 * Plug factory. `keys`: pinned attester pubkeys [{ key_id, pubkey }].
 * Declares binds.zktag === true — the router guarantees ctx.zktag is a string
 * before verify() runs (tier A yields `evidence_zktag_unavailable` upstream).
 */
export function sigEd25519Zktag({ keys }) {
  if (!Array.isArray(keys) || keys.length === 0) {
    throw new TypeError('test-sig-ed25519-zktag/1: registration needs a non-empty keys list');
  }
  const byId = new Map(keys.map((k) => [k.key_id, k.pubkey]));
  return Object.freeze({
    binds: Object.freeze({ nonce: true, claim: true, scope: true, zktag: true }),
    linkability: 'signer',
    tierCeiling: 'B',
    verify(item, ctx) {
      try {
        const data = item?.data;
        if (!data || typeof data !== 'object') return { ok: true, valid: false, reason: 'sig_malformed' };
        const { key_id: keyId, sig } = data;
        if (typeof keyId !== 'string' || typeof sig !== 'string' || sig.length === 0) {
          return { ok: true, valid: false, reason: 'sig_malformed' };
        }
        const pubkey = byId.get(keyId);
        if (!pubkey) return { ok: true, valid: false, reason: 'sig_unknown_key' };
        if (typeof ctx?.zktag !== 'string' || ctx.zktag.length === 0) {
          // Defence in depth: the router refuses this earlier; a broken caller
          // is our problem, not evidence about a person.
          return { ok: false, valid: null, reason: 'zktag_unavailable_to_plug' };
        }
        let good;
        try {
          good = edVerify(
            null,
            zktagSigMessage(ctx.claim, ctx.nonce, ctx.scopeDomain, ctx.zktag),
            pubkey,
            Buffer.from(sig, 'base64'),
          );
        } catch {
          return { ok: true, valid: false, reason: 'sig_malformed' };
        }
        return good
          ? { ok: true, valid: true, reason: 'sig_verified' }
          : { ok: true, valid: false, reason: 'sig_invalid' };
      } catch {
        return { ok: false, valid: null, reason: 'sig_plug_internal_error' };
      }
    },
  });
}
