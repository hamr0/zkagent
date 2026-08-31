// spikes/m2-handoff/sig-ed25519-plug.mjs — SPIKE implementation of the FR12
// `sig-ed25519/1` evidence type (D30: default mode-B evidence delivery).
//
// Built via chiproof's EXISTING evidence-plug extension contract — chiproof is
// consumed as-is, nothing in the library is patched. Shape mirrors the shipped
// `signed-receipt/1` plug, with tier ceiling 'B' per FR12/D30.
//
// PROPOSED byte layout (for the owner to confirm — FR12 says the exact layout
// is an M2 implementation detail, fixed when the plug is built; this spike is
// that first build, so this is a CANDIDATE, not a settled spec):
//
//   message = sha256( utf8("sig-ed25519/1\n")            // domain separation
//                     || sha256(canonicalize(claim))      // claim binding
//                     || base64urlDecode(nonce)           // challenge binding
//                     || utf8(scopeDomain) )              // scope binding
//   evidence item = { type: 'sig-ed25519', version: 1, data: { key_id, sig } }
//   sig = base64( Ed25519(privateKey, message) )
//
// NOTE vs D30's literal wording ("nonce + scope"): the claim hash is ALSO
// bound, because chiproof's plug contract refuses at registration any plug
// that does not declare binds.claim === true (evidence not tied to the claim
// is replayable across claims). Flagged for owner confirmation.
//
// The zktag is NOT bound: chiproof's PlugCtx does not expose the presented
// zktag to plugs, so no plug can bind it with chiproof as-is. Flagged.

import { createHash, verify as edVerify } from 'node:crypto';
import { canonicalize } from 'chiproof';

export const SIG_ED25519_KEY = 'sig-ed25519/1';

/** The exact bytes the attester must sign. Exported so the client can produce them. */
export function sigMessage(claim, nonce, scopeDomain) {
  const claimHash = createHash('sha256').update(canonicalize(claim), 'utf8').digest();
  return createHash('sha256')
    .update(Buffer.from('sig-ed25519/1\n', 'utf8'))
    .update(claimHash)
    .update(Buffer.from(nonce, 'base64url'))
    .update(Buffer.from(scopeDomain, 'utf8'))
    .digest();
}

/**
 * Plug factory. `keys`: operator-pinned attester pubkeys [{ key_id, pubkey }].
 * Contract (chiproof M1 spec §4): verify() never throws; "could not check"
 * => ok:false (=> allowed:null upstream); "checked and failed" => valid:false
 * (=> allowed:false upstream).
 */
export function sigEd25519({ keys } = {}) {
  if (!Array.isArray(keys) || keys.length === 0) {
    throw new TypeError('sig-ed25519/1: registration needs a non-empty keys list [{ key_id, pubkey }]');
  }
  const byId = new Map();
  for (const k of keys) {
    if (!k || typeof k.key_id !== 'string' || k.key_id.length === 0 || k.pubkey == null) {
      throw new TypeError('sig-ed25519/1: every key needs a non-empty key_id and a pubkey');
    }
    if (byId.has(k.key_id)) throw new TypeError(`sig-ed25519/1: duplicate key_id ${k.key_id}`);
    byId.set(k.key_id, k.pubkey);
  }

  return Object.freeze({
    binds: Object.freeze({ nonce: true, claim: true, scope: true }),
    linkability: 'signer',   // attester pubkey is stable per attester (FR12/D30)
    tierCeiling: 'B',        // orchestrator-recommended ceiling; owner may veto
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
        if (typeof ctx?.scopeDomain !== 'string' || ctx.scopeDomain.length === 0) {
          // Our own config is broken — could not check, never a "no".
          return { ok: false, valid: null, reason: 'scope_domain_unconfigured' };
        }
        let good;
        try {
          good = edVerify(null, sigMessage(ctx.claim, ctx.nonce, ctx.scopeDomain), pubkey, Buffer.from(sig, 'base64'));
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
