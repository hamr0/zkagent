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
 *   item.data = { key_id, pubkey?, sig }   (sig base64; the P-256 signature
 *   is DER-encoded ECDSA — Node's `crypto.verify` default and what Android
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
 * sites would break tier A's cross-site bar, D22/FR9). D38 (2026-09-01) makes
 * the mode-B key PER-ORIGIN on the device (`DeviceKey`/`EvidenceSigner` on the
 * scanner side), so a presentation now MAY carry `data.pubkey` (the
 * SubjectPublicKeyInfo DER, base64) alongside `key_id`. `key_id` is always
 * independently recomputed from `pubkey` via the identical function the
 * scanner uses (`EvidenceSigner.keyIdFor`:
 * `sha256(publicKeyDer).hex().slice(0, 16)`, mirrored here as `keyIdFor`) and
 * a mismatch is refused (`sig_key_id_mismatch`) — the verifier never trusts a
 * caller-declared `key_id` on its own once a pubkey is carried.
 *
 * Key resolution order, per presentation:
 *   1. Operator-pinned (`keys` at registration) — unchanged 0.4.0 behaviour;
 *      an item without `pubkey` is only ever accepted this way.
 *   2. Else, if an `attesterStore` is configured (D38) and the item carries
 *      `pubkey`: look up the binding for `(ctx.scopeDomain, ctx.zktag)`
 *      (D37 scope). A binding pins the (device, origin) pair on first sight;
 *      a different pubkey for the same binding is `attester_key_mismatch`,
 *      never silently re-bound (that would let a relay hijack an already-
 *      recognised zktag under its own key). An unbound (scope, zktag) is
 *      "first sight": the carried pubkey verifies the signature, and ONLY ON
 *      SUCCESS is it bound — surfaced as the non-fatal
 *      `attester_bound_first_sight` note in the plug result's `warnings`
 *      array (the existing channel — see `evidence.js`'s `warnings`
 *      pass-through — no parallel field).
 *   3. Neither applies (unpinned, no store configured, or no `pubkey`
 *      carried): `sig_unknown_key`, same as pre-D38.
 *
 * `sig-p256/1` is a CANDIDATE plug name under a pending decision number
 * (`Dn`, PRD §6.2 item 11 amendment, 2026-08-31) — required by item 1's
 * device-capability finding that Ed25519 is unavailable as an AndroidKeyStore
 * key on the Pixel 6a (`docs/logs/M2-SESSION-POC.md` F2), not yet an
 * owner-numbered decision the way `sig-ed25519/1` (D30) is.
 */
import { createHash, createPublicKey, verify as cryptoVerify } from 'node:crypto';
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

/**
 * D38: the key-id function a carried `pubkey` (SubjectPublicKeyInfo DER) is
 * checked against. MUST stay byte-identical to the scanner's
 * `EvidenceSigner.keyIdFor` (Kotlin, `apps/scanner/.../EvidenceSigner.kt`):
 * lowercase-hex `sha256(publicKeyDer)`, truncated to 16 hex chars (8 bytes).
 * Exported so a client/test can produce the exact id.
 *
 * @param {Buffer} publicKeyDer
 * @returns {string}
 */
export function keyIdFor(publicKeyDer) {
  return createHash('sha256').update(publicKeyDer).digest('hex').slice(0, 16);
}

/**
 * @param {string} pluginType @param {unknown} keys @param {boolean} hasStore
 * whether an `attesterStore` was also supplied — an empty/absent `keys` list
 * is only a registration error when there is no store to fall back on.
 */
function validateKeys(pluginType, keys, hasStore) {
  const list = keys === undefined ? [] : keys;
  if (!Array.isArray(list) || (list.length === 0 && !hasStore)) {
    throw new TypeError(`${pluginType}: registration needs a non-empty keys list [{ key_id, pubkey }], an attesterStore (D38), or both`);
  }
  const byId = new Map();
  for (const k of list) {
    if (!k || typeof k.key_id !== 'string' || k.key_id.length === 0 || k.pubkey == null) {
      throw new TypeError(`${pluginType}: every key needs a non-empty key_id and a pubkey`);
    }
    if (byId.has(k.key_id)) throw new TypeError(`${pluginType}: duplicate key_id ${k.key_id}`);
    byId.set(k.key_id, k.pubkey);
  }
  return byId;
}

/**
 * @param {string} pluginType
 * @param {unknown} store
 * @returns {import('../types.js').AttesterStore|undefined}
 */
function validateAttesterStore(pluginType, store) {
  if (store === undefined) return undefined;
  const s = /** @type {{get?: unknown, bind?: unknown}} */ (store);
  if (!s || typeof s.get !== 'function' || typeof s.bind !== 'function') {
    throw new TypeError(`${pluginType}: attesterStore must implement { get({scope,zktag}), bind({scope,zktag,key_id,pubkey}) } (D38)`);
  }
  return /** @type {import('../types.js').AttesterStore} */ (store);
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
  /**
   * @param {{keys?: {key_id: string, pubkey: unknown}[], attesterStore?: import('../types.js').AttesterStore}} [opts]
   * `keys`: operator-pinned attester keys (unchanged 0.4.0 behaviour).
   * `attesterStore` (D38): trust-on-first-sight store for per-origin device
   * keys that arrive carrying their own `pubkey` — checked only for a
   * `key_id` not found in `keys`.
   */
  return function attesterSig({ keys, attesterStore } = {}) {
    const store = validateAttesterStore(pluginType, attesterStore);
    const byId = validateKeys(pluginType, keys, store !== undefined);
    return Object.freeze({
      binds: Object.freeze({
        nonce: true, claim: true, scope: true, zktag: true,
      }),
      linkability: 'signer',
      tierCeiling: 'B',
      async verify(item, ctx) {
        try {
          const data = item?.data;
          if (!data || typeof data !== 'object') return { ok: true, valid: false, reason: 'sig_malformed' };
          const { key_id: keyId, sig, pubkey: pubkeyB64 } = data;
          if (typeof keyId !== 'string' || typeof sig !== 'string' || sig.length === 0) {
            return { ok: true, valid: false, reason: 'sig_malformed' };
          }
          if (pubkeyB64 !== undefined && (typeof pubkeyB64 !== 'string' || pubkeyB64.length === 0)) {
            return { ok: true, valid: false, reason: 'sig_malformed' };
          }
          // D38: a carried pubkey is only ever trusted once it's shown to
          // hash to the key_id the item itself claims — never the reverse.
          /** @type {Buffer|undefined} */
          let carriedDer;
          if (pubkeyB64 !== undefined) {
            try {
              carriedDer = Buffer.from(pubkeyB64, 'base64');
            } catch {
              return { ok: true, valid: false, reason: 'sig_malformed' };
            }
            if (carriedDer.length === 0) return { ok: true, valid: false, reason: 'sig_malformed' };
            if (keyIdFor(carriedDer) !== keyId) {
              return { ok: true, valid: false, reason: 'sig_key_id_mismatch' };
            }
          }
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

          /** @type {import('node:crypto').KeyLike} the key actually handed to cryptoVerify */
          let verifyKey;
          /** @type {{scope: string, zktag: string, key_id: string, pubkey: Buffer}|null} bind after a successful first-sight verify */
          let bindAfterVerify = null;
          // Captured only inside the branch where `store` is known defined,
          // so the later `bindAfterVerify` use doesn't need TS to re-derive
          // that from `store`'s outer-scope type.
          /** @type {import('../types.js').AttesterStore|undefined} */
          let storeForBind;

          const pinned = byId.get(keyId);
          if (pinned !== undefined) {
            // Operator-pinned takes precedence over anything carried in the
            // item — unchanged 0.4.0 behaviour, D38 adds nothing here.
            verifyKey = pinned;
          } else if (carriedDer !== undefined && store !== undefined) {
            storeForBind = store;
            let binding;
            try {
              binding = await store.get({ scope: ctx.scopeDomain, zktag: ctx.zktag });
            } catch {
              return { ok: false, valid: null, reason: 'attester_store_unreachable' };
            }
            if (binding !== undefined) {
              if (
                !binding || typeof binding.key_id !== 'string' || !Buffer.isBuffer(binding.pubkey)
                || binding.key_id !== keyId || !binding.pubkey.equals(carriedDer)
              ) {
                // A DIFFERENT key for a (scope, zktag) already bound to
                // someone else — never silently re-bound (D38): that would
                // let a relay hijack a recognised zktag under its own key.
                return { ok: true, valid: false, reason: 'attester_key_mismatch' };
              }
              try {
                verifyKey = createPublicKey({ key: carriedDer, format: 'der', type: 'spki' });
              } catch {
                return { ok: true, valid: false, reason: 'sig_malformed' };
              }
            } else {
              // First sight for this (scope, zktag): verify with the carried
              // key, bind ONLY on a successful signature (never before).
              try {
                verifyKey = createPublicKey({ key: carriedDer, format: 'der', type: 'spki' });
              } catch {
                return { ok: true, valid: false, reason: 'sig_malformed' };
              }
              bindAfterVerify = {
                scope: ctx.scopeDomain, zktag: ctx.zktag, key_id: keyId, pubkey: carriedDer,
              };
            }
          } else {
            // Not pinned, and either no pubkey was carried or no store is
            // configured to make use of one — same as pre-D38.
            return { ok: true, valid: false, reason: 'sig_unknown_key' };
          }

          const sigBytes = Buffer.from(sig, 'base64');
          if (sigBytes.length === 0) return { ok: true, valid: false, reason: 'sig_malformed' };
          const message = messageOf(ctx.claim, ctx.nonce, ctx.scopeDomain, ctx.zktag);
          let good;
          try {
            good = cryptoVerify(verifyAlgorithm, message, verifyKey, sigBytes);
          } catch {
            return { ok: true, valid: false, reason: 'sig_malformed' };
          }
          if (!good) return { ok: true, valid: false, reason: 'sig_invalid' };

          if (bindAfterVerify && storeForBind) {
            try {
              await storeForBind.bind(bindAfterVerify);
            } catch {
              return { ok: false, valid: null, reason: 'attester_store_unreachable' };
            }
            return {
              ok: true, valid: true, reason: 'sig_verified', warnings: ['attester_bound_first_sight'],
            };
          }
          return { ok: true, valid: true, reason: 'sig_verified' };
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
