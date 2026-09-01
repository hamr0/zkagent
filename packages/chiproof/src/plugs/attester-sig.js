// SPDX-License-Identifier: Apache-2.0
/**
 * `sig-ed25519/1` and `sig-p256/1` — the D30 attester-key evidence plug family
 * (PRD §6.2 items 1/9/11, FR12, D30; the P-256 variant is a 2026-08-31
 * amendment). An attester-held device key (the app's own Android Keystore
 * key — never fed into zktag derivation, §6.2 item 1) signs the challenge
 * binding: claim + nonce + scope + the PRESENTED zktag.
 *
 * ONE shared preimage, domain-separated by the literal plug-type string, so a
 * signature minted for one algorithm can never verify as the other:
 *
 *   preimage = utf8(PLUG_TYPE + "\n")
 *            ‖ sha256(canonical(claim))
 *            ‖ base64urlDecode(nonce)
 *            ‖ utf8(scopeDomain)
 *            ‖ utf8(zktag)
 *
 * The two algorithms differ only in WHERE sha256 is applied, and that
 * difference is forced by each platform's own signing primitive, not chosen:
 *   - `sig-ed25519/1`: Ed25519 has no prehash step in the Node/JCA APIs — the
 *     signer must be handed a digest. Signed message = sha256(preimage).
 *   - `sig-p256/1`: Android Keystore's `SHA256withECDSA` (and Node's
 *     `crypto.sign('sha256', ...)`) hashes its own input — the signer must be
 *     handed the raw preimage. Signed message = preimage.
 *   Applying sha256 in each algorithm's own native place is what keeps ONE
 *   preimage definition true on both sides. This reading of PRD §6.2 item 9's
 *   layout for the P-256 case is orchestrator-recommended, NOT an owner
 *   decision — flag for owner veto.
 *
 *   item.data = { key_id, sig }   (sig base64; the P-256 signature is
 *   DER-encoded ECDSA — Node's `crypto.verify` default and what Android
 *   Keystore produces by default; no IEEE-P1363/raw r‖s support is added —
 *   nothing built so far needs it, and a silent-fallback decoder would be a
 *   second way to smuggle a valid-looking signature past this plug)
 *
 * Which algorithm a presentation used is identified by the plug type itself
 * (item 9) — there is no in-band `alg` field the verifier trusts.
 *
 * Bindings: nonce/claim/scope/zktag are all bound (`binds.zktag: true`) — a
 * signature minted for one zktag, nonce, claim, scope, OR algorithm fails for
 * every other combination.
 *
 * Linkability 'signer', tier ceiling 'B' (D30; the ceiling is
 * orchestrator-recommended, owner may veto — a signer key stable across
 * sites would break tier A's cross-site bar, D22/FR9).
 *
 * `sig-p256/1` is a CANDIDATE plug name under a pending decision number
 * (`Dn`, PRD §6.2 item 11 amendment, 2026-08-31) — required by item 1's
 * device-capability finding that Ed25519 is unavailable as an AndroidKeyStore
 * key on the Pixel 6a (`docs/logs/M2-SESSION-POC.md` F2), not yet an
 * owner-numbered decision the way `sig-ed25519/1` (D30) is.
 */
import { createHash, verify as cryptoVerify } from 'node:crypto';
import { canonicalize } from '../canonical.js';

/** The shared preimage, before either algorithm's own hashing step. */
function preimage(pluginType, claim, nonce, scopeDomain, zktag) {
  const claimHash = createHash('sha256').update(canonicalize(claim), 'utf8').digest();
  return Buffer.concat([
    Buffer.from(`${pluginType}\n`, 'utf8'),
    claimHash,
    Buffer.from(nonce, 'base64url'),
    Buffer.from(scopeDomain, 'utf8'),
    Buffer.from(zktag, 'utf8'),
  ]);
}

/**
 * `sig-ed25519/1`'s signed message: `sha256(preimage)`. Ed25519 has no
 * prehash step in Node's/the JCA's APIs, so the signer is handed the digest.
 * Exported so a client can produce the exact bytes.
 */
export function sigEd25519Message(claim, nonce, scopeDomain, zktag) {
  return createHash('sha256').update(preimage('sig-ed25519/1', claim, nonce, scopeDomain, zktag)).digest();
}

/**
 * `sig-p256/1`'s signed message: the raw preimage. `SHA256withECDSA` (Android
 * Keystore) and `crypto.sign('sha256', ...)` (Node) each hash their own
 * input, so the signer is handed the preimage unhashed. Exported so a client
 * can produce the exact bytes.
 */
export function sigP256Message(claim, nonce, scopeDomain, zktag) {
  return preimage('sig-p256/1', claim, nonce, scopeDomain, zktag);
}

/** @param {string} pluginType @param {unknown} keys */
function validateKeys(pluginType, keys) {
  if (!Array.isArray(keys) || keys.length === 0) {
    throw new TypeError(`${pluginType}: registration needs a non-empty keys list [{ key_id, pubkey }]`);
  }
  const byId = new Map();
  for (const k of keys) {
    if (!k || typeof k.key_id !== 'string' || k.key_id.length === 0 || k.pubkey == null) {
      throw new TypeError(`${pluginType}: every key needs a non-empty key_id and a pubkey`);
    }
    if (byId.has(k.key_id)) throw new TypeError(`${pluginType}: duplicate key_id ${k.key_id}`);
    byId.set(k.key_id, k.pubkey);
  }
  return byId;
}

/**
 * Shared plug factory: identical routing/contract logic for both algorithms —
 * `sigEd25519` and `sigP256` differ only in `messageOf` (which of the two
 * exported message-builders above) and `verifyAlgorithm` (the `crypto.verify`
 * algorithm argument), so the byte layout cannot drift between them.
 *
 * @param {{pluginType: string, messageOf: Function, verifyAlgorithm: string|null}} spec
 */
function makeAttesterSigPlug({ pluginType, messageOf, verifyAlgorithm }) {
  /** @param {{keys?: {key_id: string, pubkey: unknown}[]}} [opts] adopter-supplied attester keys */
  return function attesterSig({ keys } = {}) {
    const byId = validateKeys(pluginType, keys);
    return Object.freeze({
      binds: Object.freeze({
        nonce: true, claim: true, scope: true, zktag: true,
      }),
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
          if (typeof ctx?.scopeDomain !== 'string' || ctx.scopeDomain.length === 0) {
            // Our own config is broken — could not check, never a "no".
            return { ok: false, valid: null, reason: 'scope_domain_unconfigured' };
          }
          if (typeof ctx?.zktag !== 'string' || ctx.zktag.length === 0) {
            // binds.zktag:true means the router guarantees ctx.zktag is a
            // presented string before verify() runs (tier A is refused
            // upstream as evidence_zktag_unavailable). A broken caller is
            // our problem, never evidence about a person.
            return { ok: false, valid: null, reason: 'zktag_unavailable_to_plug' };
          }
          const sigBytes = Buffer.from(sig, 'base64');
          if (sigBytes.length === 0) return { ok: true, valid: false, reason: 'sig_malformed' };
          const message = messageOf(ctx.claim, ctx.nonce, ctx.scopeDomain, ctx.zktag);
          let good;
          try {
            good = cryptoVerify(verifyAlgorithm, message, pubkey, sigBytes);
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
  };
}

/** `sig-ed25519/1` — the D30 reference default evidence delivery for mode-B presentations. */
export const sigEd25519 = makeAttesterSigPlug({
  pluginType: 'sig-ed25519/1',
  messageOf: sigEd25519Message,
  // Ed25519: node:crypto's `algorithm` must be null/undefined — the message
  // is signed/verified as-is (no internal hashing), matching signed-receipt.js.
  verifyAlgorithm: null,
});

/**
 * `sig-p256/1` — candidate name, candidate decision (`Dn` pending, PRD §6.2
 * item 11 amendment 2026-08-31). Permitted because Ed25519 is unavailable as
 * an AndroidKeyStore key on the Pixel 6a, at either security level, by either
 * entry point (`docs/logs/M2-SESSION-POC.md` F2).
 */
export const sigP256 = makeAttesterSigPlug({
  pluginType: 'sig-p256/1',
  messageOf: sigP256Message,
  // ECDSA-P256-with-SHA256: node:crypto hashes the message internally;
  // signature is DER-encoded (the default dsaEncoding for EC keys).
  verifyAlgorithm: 'sha256',
});
