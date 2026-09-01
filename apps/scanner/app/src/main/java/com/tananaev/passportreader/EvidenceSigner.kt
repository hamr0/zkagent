package com.tananaev.passportreader

import java.security.MessageDigest
import java.security.Signature
import java.util.Base64

/**
 * §6.2 item 9 — the D30 attester-key evidence signature, matching
 * `packages/chiproof/src/plugs/attester-sig.js` (the shipped `sig-ed25519/1`
 * / `sig-p256/1` plug family — **read, not edited**: item 11 forbids any
 * change to chiproof beyond the one permitted P-256-plug addition, which a
 * parallel session is making; this file only consumes the published byte
 * layout).
 *
 * ONE shared preimage, domain-separated by the literal plug-type string:
 *
 *   preimage = utf8(pluginType + "\n")
 *            || sha256(canonicalize(claim))
 *            || base64urlDecode(nonce)
 *            || utf8(scopeDomain)
 *            || utf8(zktag)
 *
 * The two algorithms differ ONLY in where sha256 is applied — forced by each
 * platform's own signing primitive, not chosen:
 *   - `sig-ed25519/1`: Ed25519 has no prehash step (Node/JCA) — signed
 *     message = sha256(preimage).
 *   - `sig-p256/1`: `SHA256withECDSA` (Android Keystore) / Node's
 *     `crypto.sign('sha256', …)` each hash their own input — signed
 *     message = preimage, UNHASHED.
 *
 * **This superseded an earlier, WRONG implementation in this file** that
 * applied `sha256(preimage)` uniformly to both algorithms — that would have
 * silently double-hashed the P-256 case (SHA256withECDSA hashing an
 * already-hashed 32-byte input) and produced a signature no real
 * `sig-p256/1` verifier would accept. Caught by reading the actual shipped
 * plug (`attester-sig.js`) once the parallel chiproof session published it,
 * not by construction — flagged in the conformance report as a real defect
 * this session found and fixed in itself, not merely a risk it noted.
 *
 * Known vectors, cross-checked against `attester-sig.js` directly (see
 * `EvidenceSignerTest`): for
 * `claim={over_threshold:true,threshold:18}`, `nonce="AAECAwQFBgcICQoLDA0ODw"`,
 * `scopeDomain="example.test"`, `zktag="deadbeefcafebabe"` —
 *   sigEd25519Message = a380290c1aadd110971952f6aadd66ec37e57a6a1c1b0884a605d28c25d95f84
 *   sigP256Message    = 7369672d703235362f310a…(raw preimage, 89 bytes, not a digest)
 *
 * `sig-p256/1` itself remains a CANDIDATE plug (`Dn` pending, item 1/11) —
 * the byte layout is real and shipped in chiproof's source tree, but the
 * decision number and any owner veto on the tier/linkability choices are
 * still open (see attester-sig.js's own doc comment).
 */
object EvidenceSigner {

    private fun claimHash(claimJson: Map<String, Any?>): ByteArray =
        sha256(Canonical.canonicalize(claimJson).toByteArray(Charsets.UTF_8))

    private fun preimage(pluginType: String, claimJson: Map<String, Any?>, nonceBase64Url: String, scopeDomain: String, zktag: String): ByteArray {
        val nonceBytes = Base64.getUrlDecoder().decode(nonceBase64Url)
        val out = java.io.ByteArrayOutputStream()
        out.write("$pluginType\n".toByteArray(Charsets.UTF_8))
        out.write(claimHash(claimJson))
        out.write(nonceBytes)
        out.write(scopeDomain.toByteArray(Charsets.UTF_8))
        out.write(zktag.toByteArray(Charsets.UTF_8))
        return out.toByteArray()
    }

    /** `sig-ed25519/1`'s signed message: sha256(preimage). */
    fun sigEd25519Message(claimJson: Map<String, Any?>, nonceBase64Url: String, scopeDomain: String, zktag: String): ByteArray =
        sha256(preimage("sig-ed25519/1", claimJson, nonceBase64Url, scopeDomain, zktag))

    /** `sig-p256/1`'s signed message: the RAW preimage — SHA256withECDSA hashes it itself. */
    fun sigP256Message(claimJson: Map<String, Any?>, nonceBase64Url: String, scopeDomain: String, zktag: String): ByteArray =
        preimage("sig-p256/1", claimJson, nonceBase64Url, scopeDomain, zktag)

    /** Picks the correct message-builder for [algorithm] — the ONE call site
     * that must stay in sync with [DeviceKey.Algorithm]. */
    fun messageFor(algorithm: DeviceKey.Algorithm, claimJson: Map<String, Any?>, nonceBase64Url: String, scopeDomain: String, zktag: String): ByteArray =
        when (algorithm) {
            DeviceKey.Algorithm.P256_HARDWARE -> sigP256Message(claimJson, nonceBase64Url, scopeDomain, zktag)
            DeviceKey.Algorithm.ED25519_HARDWARE, DeviceKey.Algorithm.ED25519_SOFTWARE ->
                sigEd25519Message(claimJson, nonceBase64Url, scopeDomain, zktag)
        }

    private fun sha256(bytes: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(bytes)

    /** One evidence-slot item (D24 shape, amended D38): `{ type, version,
     * data: { key_id, pubkey, sig } }`. D38: `pubkey` (base64 of the
     * SubjectPublicKeyInfo/X.509 DER, the SAME bytes [keyIdFor] hashes) now
     * travels WITH the presentation, so a verifier's attester-key store can
     * bind key->zktag on first sight (`attester_bound_first_sight`) instead
     * of needing a pre-registered key list — the verifier MUST recompute
     * `key_id` from `pubkey` with the identical function and refuse on a
     * mismatch, never trust a claimed `key_id` alone (PRD §10 D38). */
    data class EvidenceItem(val type: String, val version: Int, val keyId: String, val pubkeyBase64: String, val sigBase64: String)

    /**
     * Signs the ALGORITHM-APPROPRIATE message (via [messageFor]) with the
     * already-biometric/device-credential-authorized [signature] — its JCA
     * algorithm (`Ed25519` or `SHA256withECDSA`) was already fixed at
     * `initSign()` time by [DeviceKey.initSignature], which is exactly what
     * makes "sign this message" produce the right per-algorithm result: a
     * `Signature` bound to `SHA256withECDSA` hashes [message] itself (correct
     * for the RAW p256 preimage); one bound to `Ed25519` does not (correct
     * for the pre-hashed ed25519 message).
     *
     * D38: takes `publicKeyDer` (the SubjectPublicKeyInfo/X.509 DER for the
     * key `signature` is bound to — [DeviceKey.currentPublicKeyDer]'s
     * output) instead of a pre-computed `keyId` — `key_id` is derived HERE,
     * from these same bytes, via [keyIdFor], so `key_id` and `pubkey` can
     * never drift apart the way two independently-computed values could.
     */
    fun sign(signature: Signature, message: ByteArray, algorithm: DeviceKey.Algorithm, publicKeyDer: ByteArray): EvidenceItem {
        signature.update(message)
        val raw = signature.sign()
        val type = algorithm.evidenceType // "sig-ed25519" (hw or sw) | "sig-p256"
        return EvidenceItem(
            type = type,
            version = 1,
            keyId = keyIdFor(publicKeyDer),
            pubkeyBase64 = Base64.getEncoder().encodeToString(publicKeyDer),
            sigBase64 = Base64.getEncoder().encodeToString(raw),
        )
    }

    fun keyIdFor(publicKeyDer: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(publicKeyDer).joinToString("") { "%02x".format(it) }.take(16)
}
