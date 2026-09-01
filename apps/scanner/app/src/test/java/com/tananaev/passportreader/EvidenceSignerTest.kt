package com.tananaev.passportreader

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.util.Base64

/**
 * §6.2 item 9 — the signed-message byte layout, checked against KNOWN
 * VECTORS produced independently by
 * `packages/chiproof/src/plugs/attester-sig.js` (`sigEd25519Message` /
 * `sigP256Message`), the shipped reference implementation of both
 * `sig-ed25519/1` and `sig-p256/1` — NOT re-derived from this app's own code:
 *
 *   node --input-type=module -e "
 *     import { sigEd25519Message, sigP256Message } from
 *       '/…/packages/chiproof/src/plugs/attester-sig.js';
 *     const claim = { over_threshold: true, threshold: 18 };
 *     const nonce = 'AAECAwQFBgcICQoLDA0ODw';
 *     console.log(Buffer.from(sigEd25519Message(claim, nonce, 'example.test', 'deadbeefcafebabe')).toString('hex'));
 *     console.log(Buffer.from(sigP256Message(claim, nonce, 'example.test', 'deadbeefcafebabe')).toString('hex'));
 *   "
 *
 * If either test ever fails after an edit to EvidenceSigner.kt or
 * Canonical.kt, that is a real interop break with chiproof's shipped plug —
 * re-derive the vector from attester-sig.js, do not just update the
 * constant. (This session's first EvidenceSigner.kt DID diverge from the
 * p256 layout — see the class doc there for what the defect was and how
 * this test would have caught it.)
 */
class EvidenceSignerTest {

    private val claim = mapOf("over_threshold" to true, "threshold" to 18)
    private val nonce = "AAECAwQFBgcICQoLDA0ODw" // base64url, 16 bytes 0x00..0x0f
    private val scopeDomain = "example.test"
    private val zktag = "deadbeefcafebabe"

    @Test
    fun `sig-ed25519 slash 1 message matches chiproof's known vector (sha256 of the preimage)`() {
        val message = EvidenceSigner.sigEd25519Message(claim, nonce, scopeDomain, zktag)
        val hex = message.joinToString("") { "%02x".format(it) }
        assertEquals("a380290c1aadd110971952f6aadd66ec37e57a6a1c1b0884a605d28c25d95f84", hex)
        assertEquals("Ed25519 message must be exactly one sha256 digest", 32, message.size)
    }

    @Test
    fun `sig-p256 slash 1 message matches chiproof's known vector (the RAW, unhashed preimage)`() {
        val message = EvidenceSigner.sigP256Message(claim, nonce, scopeDomain, zktag)
        val hex = message.joinToString("") { "%02x".format(it) }
        assertEquals(
            "7369672d703235362f310aa3e55d4a870a3aa1384d149c950bfca38ee4c2cfd4a32d11163ac0298100d713000102030405060708090a0b0c0d0e0f6578616d706c652e7465737464656164626565666361666562616265",
            hex,
        )
        // NOT 32 bytes — this is the load-bearing distinction from the
        // ed25519 message: SHA256withECDSA hashes this itself.
        assert(message.size != 32) { "sig-p256/1 message must be the raw preimage, not a digest" }
    }

    @Test
    fun `messageFor routes P256_HARDWARE to the raw-preimage layout, not the ed25519 digest layout`() {
        val viaAlgorithm = EvidenceSigner.messageFor(DeviceKey.Algorithm.P256_HARDWARE, claim, nonce, scopeDomain, zktag)
        val direct = EvidenceSigner.sigP256Message(claim, nonce, scopeDomain, zktag)
        assertEquals(direct.joinToString(",") { it.toString() }, viaAlgorithm.joinToString(",") { it.toString() })
    }

    @Test
    fun `messageFor routes ED25519_HARDWARE to the sha256-digest layout`() {
        val viaAlgorithm = EvidenceSigner.messageFor(DeviceKey.Algorithm.ED25519_HARDWARE, claim, nonce, scopeDomain, zktag)
        val direct = EvidenceSigner.sigEd25519Message(claim, nonce, scopeDomain, zktag)
        assertEquals(direct.joinToString(",") { it.toString() }, viaAlgorithm.joinToString(",") { it.toString() })
    }

    @Test
    fun `different zktag changes both messages (zktag binding, chiproof 0-3-0 Gap 1)`() {
        val e1 = EvidenceSigner.sigEd25519Message(claim, nonce, scopeDomain, "tag-one")
        val e2 = EvidenceSigner.sigEd25519Message(claim, nonce, scopeDomain, "tag-two")
        assert(!e1.contentEquals(e2)) { "ed25519 message must change when zktag changes" }

        val p1 = EvidenceSigner.sigP256Message(claim, nonce, scopeDomain, "tag-one")
        val p2 = EvidenceSigner.sigP256Message(claim, nonce, scopeDomain, "tag-two")
        assert(!p1.contentEquals(p2)) { "p256 message must change when zktag changes" }
    }

    // ------------------------------------------------- D38: pubkey in EvidenceItem

    /** Real (non-Keystore) P-256 keypair via the plain JVM "EC" provider —
     * SHA256withECDSA over the RAW (unhashed) p256 preimage, matching
     * [DeviceKey]'s device-key signing discipline (see class doc above:
     * "SHA256withECDSA... hashes its own input"). This is a pure-JVM
     * substitute for an AndroidKeyStore key, sufficient to exercise
     * [EvidenceSigner.sign]'s D38 pubkey/key_id wiring without a device. */
    private fun freshP256KeyPair() = KeyPairGenerator.getInstance("EC").apply {
        initialize(ECGenParameterSpec("secp256r1"))
    }.generateKeyPair()

    @Test
    fun `sign - D38 EvidenceItem carries pubkey, and key_id is recomputed from it (never trusted separately)`() {
        val keyPair = freshP256KeyPair()
        val pubDer = keyPair.public.encoded
        val message = EvidenceSigner.sigP256Message(claim, nonce, scopeDomain, zktag)
        val signature = Signature.getInstance("SHA256withECDSA").apply {
            initSign(keyPair.private)
            update(message)
        }

        val item = EvidenceSigner.sign(signature, message, DeviceKey.Algorithm.P256_HARDWARE, pubDer)

        assertEquals("sig-p256/1", "${item.type}/${item.version}")
        assertEquals(EvidenceSigner.keyIdFor(pubDer), item.keyId)
        assertEquals(Base64.getEncoder().encodeToString(pubDer), item.pubkeyBase64)

        // A verifier recomputing key_id from the transmitted pubkey (D38's
        // whole point — never trust a claimed key_id alone) must land on
        // the SAME value item.keyId already carries.
        val decodedPubDer = Base64.getDecoder().decode(item.pubkeyBase64)
        assertEquals(item.keyId, EvidenceSigner.keyIdFor(decodedPubDer))
    }

    @Test
    fun `sign - a different key produces a different key_id and pubkey (no cross-key collision)`() {
        val kp1 = freshP256KeyPair()
        val kp2 = freshP256KeyPair()
        val message = EvidenceSigner.sigP256Message(claim, nonce, scopeDomain, zktag)

        val sig1 = Signature.getInstance("SHA256withECDSA").apply { initSign(kp1.private); update(message) }
        val item1 = EvidenceSigner.sign(sig1, message, DeviceKey.Algorithm.P256_HARDWARE, kp1.public.encoded)

        val sig2 = Signature.getInstance("SHA256withECDSA").apply { initSign(kp2.private); update(message) }
        val item2 = EvidenceSigner.sign(sig2, message, DeviceKey.Algorithm.P256_HARDWARE, kp2.public.encoded)

        assertTrue(item1.keyId != item2.keyId)
        assertTrue(item1.pubkeyBase64 != item2.pubkeyBase64)
    }

    @Test
    fun `HandoffClient buildPresentation - item serialization JSON carries pubkey alongside key_id and sig`() {
        val keyPair = freshP256KeyPair()
        val pubDer = keyPair.public.encoded
        val message = EvidenceSigner.sigP256Message(claim, nonce, scopeDomain, zktag)
        val signature = Signature.getInstance("SHA256withECDSA").apply {
            initSign(keyPair.private)
            update(message)
        }
        val item = EvidenceSigner.sign(signature, message, DeviceKey.Algorithm.P256_HARDWARE, pubDer)

        val presentation = HandoffClient.buildPresentation(
            tier = "B",
            claim = claim,
            challenge = JSONObject().put("nonce", nonce),
            zktag = zktag,
            evidence = listOf(item),
        )

        val data = presentation.getJSONArray("evidence").getJSONObject(0).getJSONObject("data")
        assertEquals(item.keyId, data.getString("key_id"))
        assertEquals(item.pubkeyBase64, data.getString("pubkey"))
        assertEquals(item.sigBase64, data.getString("sig"))

        // A verifier's exact D38 contract: recompute key_id from the
        // serialized pubkey and compare, never trust the serialized key_id
        // in isolation.
        val recomputedKeyId = EvidenceSigner.keyIdFor(Base64.getDecoder().decode(data.getString("pubkey")))
        assertEquals(data.getString("key_id"), recomputedKeyId)
        assertNotNull(data.getString("pubkey"))
    }
}
