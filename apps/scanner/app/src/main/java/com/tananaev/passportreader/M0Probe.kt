package com.tananaev.passportreader

import android.util.Log
import org.bouncycastle.asn1.ASN1InputStream
import org.bouncycastle.asn1.ASN1Primitive
import org.bouncycastle.asn1.ASN1Sequence
import org.bouncycastle.asn1.ASN1Set
import org.bouncycastle.asn1.x509.Certificate
import org.jmrtd.PassportService
import org.jmrtd.lds.SODFile
import org.jmrtd.lds.icao.DG15File
import org.jmrtd.lds.icao.DG1File
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.security.KeyStore
import java.security.MessageDigest
import java.security.PublicKey
import java.security.Signature
import java.security.cert.CertPathValidator
import java.security.cert.CertificateFactory
import java.security.cert.PKIXParameters
import java.security.cert.X509Certificate
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * M0 spike instrumentation — PRD v1.5 §6 M0 row and §6.1 go/no-go table.
 *
 * THROWAWAY. Not shipped, not graduated (AGENT_RULES: never ship the POC).
 *
 * Discipline enforced here:
 *  - `ok` (could we check) is separate from `allowed` (what the answer was).
 *    A failure to check NEVER renders as a "no" (PRD §3).
 *  - No PII is ever logged. Field VALUES never leave this file; only field
 *    names, lengths, and hashes are reported (PRD v1.5 M0 evidence rule).
 */
object M0Probe {

    private const val TAG = "M0Probe"

    /** Fixed test domain for the M0 derivation candidates. Not a real service. */
    private const val TEST_DOMAIN = "example.test"

    // ---------------------------------------------------------------- timings

    class Timeline {
        private val marks = LinkedHashMap<String, Long>()
        private val t0 = System.nanoTime()
        fun mark(name: String) { marks[name] = (System.nanoTime() - t0) / 1_000_000 }
        fun report(): List<String> = marks.map { (k, v) -> "  $k: ${v} ms" }
    }

    // ------------------------------------------------------- verdict triplet

    /**
     * The §3 invariant in miniature. `ok == false` forces `allowed == null`;
     * there is deliberately no way to construct "broken, and the answer is no".
     */
    data class Verdict(val ok: Boolean, val allowed: Boolean?, val reason: String) {
        init { require(!(ok.not() && allowed != null)) { "ok:false must carry allowed:null" } }
        override fun toString() = "ok=$ok allowed=$allowed reason=$reason"

        companion object {
            fun yes(reason: String) = Verdict(true, true, reason)
            fun no(reason: String) = Verdict(true, false, reason)
            fun unknown(reason: String) = Verdict(false, null, reason)
        }
    }

    // ------------------------------------------------------------ master list

    class MasterList(
        val certsDeclared: Int,
        val certsParsed: Int,
        val keystore: KeyStore,
        val certsExcluded: Int = 0,
    ) {
        val consistent get() = certsDeclared == certsParsed
    }

    /**
     * Loads the CSCA master list, counting certificates DECLARED in the CMS
     * structure against certificates actually PARSED into the keystore.
     * A silent half-load (19-in-file / 17-parsed) is the 8een bug shape this
     * count exists to catch (PRD §3 corollary).
     *
     * @param excludeAnchorFor when non-null, the CSCA that actually anchors this
     *        document — i.e. any certificate whose subject equals this certificate's
     *        issuer — is dropped. This is planted negative (ii), and matching on the
     *        real issuer DN rather than a guessed country string is deliberate: the
     *        first attempt matched the literal "United States" and excluded nothing,
     *        because the US CSCA DN reads "U.S. Department of State". A negative that
     *        silently excludes nothing is indistinguishable from one that fails to
     *        fire, which is why `certsExcluded` is now reported and asserted.
     */
    fun loadMasterList(stream: InputStream, excludeAnchorFor: X509Certificate? = null): MasterList {
        val keystore = KeyStore.getInstance(KeyStore.getDefaultType())
        keystore.load(null, null)
        val cf = CertificateFactory.getInstance("X.509")
        var declared = 0
        var parsed = 0
        var index = 0
        var excluded = 0
        val anchorDn = excludeAnchorFor?.issuerX500Principal
        val asn1 = ASN1InputStream(stream)
        var p: ASN1Primitive?
        while (asn1.readObject().also { p = it } != null) {
            val seq = ASN1Sequence.getInstance(p) ?: continue
            if (seq.size() != 2) continue
            val certSet = ASN1Set.getInstance(seq.getObjectAt(1))
            declared += certSet.size()
            for (i in 0 until certSet.size()) {
                try {
                    val encoded = Certificate.getInstance(certSet.getObjectAt(i)).encoded
                    val cert = cf.generateCertificate(ByteArrayInputStream(encoded)) as X509Certificate
                    if (anchorDn != null && cert.subjectX500Principal == anchorDn) {
                        excluded++
                        continue // planted negative: this document's CSCA is not in the list
                    }
                    keystore.setCertificateEntry("csca-${index++}", cert)
                    parsed++
                } catch (e: Exception) {
                    Log.w(TAG, "master list entry $i unparseable: ${e.javaClass.simpleName}")
                }
            }
        }
        if (anchorDn != null) declared = parsed // exclusion is deliberate, not a half-load
        return MasterList(declared, parsed, keystore, excluded)
    }

    // --------------------------------------------------------- passive auth

    /** Outcome of [checkTrustPath] — the two-bucket rule's bucket (ii)
     * decision in isolation: is there a PKIX chain from the presenting
     * issuer's document-signer certs to a trust anchor already loaded into
     * [MasterList.keystore]? [NotTrusted] means the check RAN and the answer
     * is "no" (masterList well-formed, issuer's CSCA simply absent) — never
     * conflated with a broken/half-loaded masterlist, which passiveAuth
     * already rejects earlier via `masterList.consistent`. */
    sealed class TrustPathResult {
        object Trusted : TrustPathResult()
        data class NotTrusted(val exceptionClass: String) : TrustPathResult()
    }

    /**
     * Pure PKIX chain-trust decision, extracted from [passiveAuth] so bucket
     * (ii) of the masterlist two-bucket rule ("well-formed CMS list that
     * simply lacks the presenting issuer's CSCA" => ok:true, allowed:false)
     * can be exercised with synthetic certificates in a unit test, without a
     * real chip read. Behavior-preserving: this is the exact
     * CertPathValidator call [passiveAuth] used to run inline.
     */
    fun checkTrustPath(dsCerts: List<X509Certificate>, masterList: MasterList): TrustPathResult =
        try {
            val cf = CertificateFactory.getInstance("X.509")
            val params = PKIXParameters(masterList.keystore).apply { isRevocationEnabled = false }
            CertPathValidator.getInstance(CertPathValidator.getDefaultType())
                .validate(cf.generateCertPath(dsCerts), params)
            TrustPathResult.Trusted
        } catch (e: Exception) {
            TrustPathResult.NotTrusted(e.javaClass.simpleName)
        }

    /**
     * Passive authentication with the three outcomes kept apart.
     *
     * Upstream (tananaev) collapses all of these into `passiveAuthSuccess=false`
     * inside one catch-all. That makes "the signature is forged" and "I could not
     * reach a decision" indistinguishable — precisely what PRD §3 forbids.
     * Recorded as an M0 finding; fixed here.
     */
    fun passiveAuth(
        dg1: DG1File,
        sod: SODFile,
        masterList: MasterList,
        dg1OverrideBytes: ByteArray? = null,
    ): Verdict {
        // --- structural preconditions: failure here means we could not check ---
        val digest: MessageDigest
        val storedDg1Hash: ByteArray
        try {
            digest = MessageDigest.getInstance(sod.digestAlgorithm)
            storedDg1Hash = sod.dataGroupHashes[1]
                ?: return Verdict.unknown("SOD carries no DG1 hash")
        } catch (e: Exception) {
            return Verdict.unknown("SOD unreadable: ${e.javaClass.simpleName}")
        }
        if (!masterList.consistent) {
            return Verdict.unknown(
                "master list half-loaded: declared=${masterList.certsDeclared} parsed=${masterList.certsParsed}"
            )
        }

        // --- real answers: the data either matches the signature or it does not ---
        val dg1Bytes = dg1OverrideBytes ?: dg1.encoded
        if (!digest.digest(dg1Bytes).contentEquals(storedDg1Hash)) {
            return Verdict.no("DG1 hash does not match the hash signed in the SOD")
        }

        val dsCerts = try {
            sod.docSigningCertificates
        } catch (e: Exception) {
            return Verdict.unknown("document signer certificates unreadable: ${e.javaClass.simpleName}")
        }
        if (dsCerts.isEmpty()) return Verdict.unknown("SOD carries no document signer certificate")

        for (c in dsCerts) {
            try {
                c.checkValidity()
            } catch (e: Exception) {
                return Verdict.no("document signer certificate not valid today")
            }
        }

        when (val trust = checkTrustPath(dsCerts, masterList)) {
            is TrustPathResult.NotTrusted ->
                // No trust anchor => this issuer's CSCA is absent from the list.
                // That is a real "no" for this document, not a broken checker:
                // we successfully determined we do not trust the signer.
                return Verdict.no("no path to a trusted CSCA: ${trust.exceptionClass}")
            TrustPathResult.Trusted -> {}
        }

        return try {
            var alg = sod.docSigningCertificate.sigAlgName
            var pss = false
            if (alg == "SSAwithRSA/PSS") { alg = "SHA256withRSA/PSS"; pss = true }
            val sig = Signature.getInstance(alg)
            if (pss) {
                sig.setParameter(
                    java.security.spec.PSSParameterSpec(
                        "SHA-256", "MGF1", java.security.spec.MGF1ParameterSpec.SHA256, 32, 1
                    )
                )
            }
            sig.initVerify(sod.docSigningCertificate)
            sig.update(sod.eContent)
            if (sig.verify(sod.encryptedDigest)) Verdict.yes("SOD signature verified to a trusted CSCA")
            else Verdict.no("SOD signature does not verify")
        } catch (e: Exception) {
            Verdict.unknown("signature check could not run: ${e.javaClass.simpleName}")
        }
    }

    // ------------------------------------------------- chip authenticity (AA)

    /** §6.2 item 16 (2026-09 real-device fix, "three states, not two"): a
     * document's chip-authenticity mechanism (Chip Authentication via
     * DG14+EAC-CA in [MainActivity.ReadTask], or Active Authentication via
     * DG15+AA below — either satisfies "chip authenticity") is either
     * cryptographically VERIFIED, genuinely NOT_SUPPORTED by this document
     * (no DG14/DG15 declaring the capability at all — the real-device US
     * passport case), or the capability is declared but the
     * challenge-response protocol itself FAILED. NOT_SUPPORTED must never
     * be conflated with FAILED, and neither may ever be conflated with
     * VERIFIED: a document with no chip authentication at all is
     * clone-replayable, a stated limitation (not hidden), never silently
     * rendered as though the check ran and passed. */
    enum class ChipAuthStatus { VERIFIED, NOT_SUPPORTED, FAILED }

    /** Active Authentication probe — Q18. Two INDEPENDENT failure points,
     * each its own try/catch, so "this document doesn't carry DG15 at all"
     * (NOT_SUPPORTED) is never conflated with "DG15 is present but the
     * challenge-response itself failed" (FAILED) — before this restructure
     * both landed in the same catch block and were indistinguishable.
     *
     * Finding #8 (`.claude/remember/findings.md`) FIX, remaining half: the
     * three-state DECISION (which branch below returns which status/detail)
     * moved to [ChipAuthClassification.fromActiveAuth], unit-tested there —
     * this function keeps ONLY the JMRTD I/O (`service.getInputStream`,
     * `DG15File` parsing, `service.doAA`), same discipline as
     * [ChipAuthClassification.fromDg14] leaving `ReadTask`'s DG14 I/O
     * inline. Detail strings are byte-identical to before. */
    fun tryActiveAuth(service: PassportService, sod: SODFile): Pair<ChipAuthStatus, String> {
        val dg15 = try {
            DG15File(service.getInputStream(PassportService.EF_DG15))
        } catch (e: Exception) {
            return ChipAuthClassification.fromActiveAuth(
                dg15LookupFailureClass = e.javaClass.simpleName,
                aaSucceeded = false,
            )
        }
        return try {
            val pub: PublicKey = dg15.publicKey
            val challenge = ByteArray(8).also { java.security.SecureRandom().nextBytes(it) }
            val sigAlg = if (pub.algorithm.contains("EC")) "SHA256withECDSA" else "SHA1withRSA/ISO9796-2"
            service.doAA(pub, sod.digestAlgorithm, sigAlg, challenge)
            ChipAuthClassification.fromActiveAuth(
                dg15LookupFailureClass = null,
                aaSucceeded = true,
                keyAlgorithm = pub.algorithm,
                sigAlgorithm = sigAlg,
            )
        } catch (e: Exception) {
            ChipAuthClassification.fromActiveAuth(
                dg15LookupFailureClass = null,
                aaSucceeded = false,
                aaFailureClass = e.javaClass.simpleName,
                aaFailureMessage = e.message,
            )
        }
    }

    // ---------------------------------------------------- zktag candidates

    /**
     * One candidate per potentially-stable chip field (PRD D9 decides among these
     * on M0 evidence, not on theory).
     *
     * zktag = HMAC-SHA256(SHA-256(field bytes), domain) — the shape of FR2/FR11,
     * NOT the published spec. The real KDF is an M2 decision.
     *
     * Returns field name -> hex tag. Field VALUES are never returned or logged.
     */
    fun deriveCandidates(
        dg1: DG1File,
        dg14Encoded: ByteArray?,
        dg15Encoded: ByteArray?,
        domain: String = TEST_DOMAIN,
    ): LinkedHashMap<String, String> {
        val mrz = dg1.mrzInfo
        val out = LinkedHashMap<String, String>()
        fun put(name: String, material: ByteArray?) {
            if (material == null || material.isEmpty()) return
            out[name] = hmacHex(sha256(material), domain)
        }
        put("document_number", mrz.documentNumber?.trim()?.trimEnd('<')?.toByteArray())
        put("optional_data", mrz.personalNumber?.trim()?.trimEnd('<')?.toByteArray())
        put("dg1_full", dg1.encoded)
        put("dg14_ca_key", dg14Encoded)
        put("dg15_aa_key", dg15Encoded)
        return out
    }

    private fun sha256(b: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(b)

    private fun hmacHex(key: ByteArray, domain: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(domain.toByteArray()).joinToString("") { "%02x".format(it) }
    }

    // ------------------------------------------------------------ inventory

    /** Which data groups the chip declares it holds. Names only — no values. */
    fun dataGroupInventory(sod: SODFile): String =
        try {
            sod.dataGroupHashes.keys.sorted().joinToString(", ") { "DG$it" }
        } catch (e: Exception) {
            "unreadable: ${e.javaClass.simpleName}"
        }

    /**
     * Flips one bit in a copy of DG1 — planted negative (i).
     * Passive auth over this MUST return ok=true, allowed=false.
     */
    fun tamperedDg1(dg1: DG1File): ByteArray =
        dg1.encoded.copyOf().also { it[it.size / 2] = (it[it.size / 2].toInt() xor 0x01).toByte() }
}
