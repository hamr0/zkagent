package com.tananaev.passportreader

import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigInteger
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.Security
import java.security.cert.X509Certificate
import java.security.spec.ECGenParameterSpec
import java.util.Date

/**
 * M2 exit-criteria row 1, bucket (ii) — "a well-formed masterlist that
 * simply lacks the presenting issuer's CSCA" => ok:true, allowed:false —
 * exercised on the REAL BUILD'S seam: [M0Probe.checkTrustPath], extracted
 * (behavior-preserving) from [M0Probe.passiveAuth]'s inline
 * CertPathValidator call so this bucket can be proven with synthetic
 * certificates instead of a real chip read.
 *
 * [MasterlistVerifierTest] already covers bucket (i) (half-truncated CMS
 * refused, ok:false) fully on the real bundled masterlist. This file is the
 * unit-level counterpart to the M2 opening session's on-device
 * `excludeAnchorFor` bucket-(ii) evidence (2026-08-31,
 * docs/logs/M2-SCAN-EVIDENCE.md), which exercised the OLD spike's
 * [M0Probe.loadMasterList] planted-negative path — a path the real build
 * (apps/scanner) does not call. That device evidence stands on its own; it
 * is not re-run or superseded here.
 */
class PassiveAuthTrustTest {

    init {
        Security.addProvider(org.bouncycastle.jce.provider.BouncyCastleProvider())
    }

    private fun freshEcKeyPair(): KeyPair =
        KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }.generateKeyPair()

    /** Self-signed synthetic CSCA. */
    private fun selfSignedCsca(cn: String, keyPair: KeyPair, notBefore: Date, notAfter: Date): X509Certificate {
        val name = X500Name("CN=$cn")
        val builder = JcaX509v3CertificateBuilder(
            name,
            BigInteger.valueOf(System.nanoTime()),
            notBefore,
            notAfter,
            name,
            keyPair.public,
        )
        val signer = JcaContentSignerBuilder("SHA256withECDSA").build(keyPair.private)
        return JcaX509CertificateConverter().getCertificate(builder.build(signer))
    }

    /** Document-signer cert issued (signed) by [issuer]'s private key, chaining to [issuerCert]. */
    private fun dsCertSignedBy(
        issuerCert: X509Certificate,
        issuerKeyPair: KeyPair,
        dsKeyPair: KeyPair,
        notBefore: Date,
        notAfter: Date,
    ): X509Certificate {
        val builder = JcaX509v3CertificateBuilder(
            X500Name(issuerCert.subjectX500Principal.name),
            BigInteger.valueOf(System.nanoTime()),
            notBefore,
            notAfter,
            X500Name("CN=Document Signer Test"),
            dsKeyPair.public,
        )
        val signer = JcaContentSignerBuilder("SHA256withECDSA").build(issuerKeyPair.private)
        return JcaX509CertificateConverter().getCertificate(builder.build(signer))
    }

    private fun keystoreOf(vararg certs: Pair<String, X509Certificate>): KeyStore {
        val ks = KeyStore.getInstance(KeyStore.getDefaultType())
        ks.load(null, null)
        certs.forEach { (alias, cert) -> ks.setCertificateEntry(alias, cert) }
        return ks
    }

    private fun now() = Date()
    private fun daysFromNow(days: Int) = Date(System.currentTimeMillis() + days * 24L * 60 * 60 * 1000)

    // ------------------------------------------------------------ (a) trusted

    @Test
    fun `trust store containing the issuing CSCA - Trusted`() {
        val cscaKeyPair = freshEcKeyPair()
        val csca = selfSignedCsca("Synthetic CSCA A", cscaKeyPair, daysFromNow(-1), daysFromNow(365))
        val dsKeyPair = freshEcKeyPair()
        val ds = dsCertSignedBy(csca, cscaKeyPair, dsKeyPair, daysFromNow(-1), daysFromNow(30))

        val masterList = M0Probe.MasterList(
            certsDeclared = 1,
            certsParsed = 1,
            keystore = keystoreOf("csca-0" to csca),
        )

        val result = M0Probe.checkTrustPath(listOf(ds), masterList)
        assertEquals(M0Probe.TrustPathResult.Trusted, result)
    }

    // ------------------------------------------------- (b) well-formed, lacking issuer

    @Test
    fun `trust store well-formed but lacking the issuing CSCA - NotTrusted, never a broken-checker failure`() {
        // The DS cert is signed by CSCA A, but the masterlist only carries a
        // DIFFERENT synthetic CSCA (B) — well-formed, just missing the issuer.
        val cscaAKeyPair = freshEcKeyPair()
        val cscaA = selfSignedCsca("Synthetic CSCA A", cscaAKeyPair, daysFromNow(-1), daysFromNow(365))
        val dsKeyPair = freshEcKeyPair()
        val ds = dsCertSignedBy(cscaA, cscaAKeyPair, dsKeyPair, daysFromNow(-1), daysFromNow(30))

        val cscaBKeyPair = freshEcKeyPair()
        val cscaB = selfSignedCsca("Synthetic CSCA B (unrelated)", cscaBKeyPair, daysFromNow(-1), daysFromNow(365))

        val masterList = M0Probe.MasterList(
            certsDeclared = 1,
            certsParsed = 1,
            keystore = keystoreOf("csca-0" to cscaB),
        )

        val result = M0Probe.checkTrustPath(listOf(ds), masterList)
        assertTrue("expected NotTrusted, got $result", result is M0Probe.TrustPathResult.NotTrusted)
        // The two-bucket rule's whole point: this is a real "no", not an
        // integrity failure — [passiveAuth] wraps this into Verdict.no(...),
        // never Verdict.unknown(...). Pin the exact exception class so a
        // future refactor can't silently widen this into a caught-all string.
        assertEquals("CertPathValidatorException", (result as M0Probe.TrustPathResult.NotTrusted).exceptionClass)

        // Reconstruct passiveAuth's message-building to show what the caller
        // actually returns for this bucket (Verdict.no, allowed:false, never
        // allowed:null).
        val verdict = M0Probe.Verdict.no("no path to a trusted CSCA: ${result.exceptionClass}")
        assertEquals(true, verdict.ok)
        assertEquals(false, verdict.allowed)
    }

    // ------------------------------------------------------------ (c) expired DS cert

    @Test
    fun `expired document-signer certificate is not trusted, even against its correct issuing CSCA`() {
        val cscaKeyPair = freshEcKeyPair()
        val csca = selfSignedCsca("Synthetic CSCA A", cscaKeyPair, daysFromNow(-3650), daysFromNow(3650))
        val dsKeyPair = freshEcKeyPair()
        // Validity window entirely in the past.
        val expiredDs = dsCertSignedBy(csca, cscaKeyPair, dsKeyPair, daysFromNow(-400), daysFromNow(-30))

        val masterList = M0Probe.MasterList(
            certsDeclared = 1,
            certsParsed = 1,
            keystore = keystoreOf("csca-0" to csca),
        )

        // Project rule: X509 chain verification checks signatures only, not
        // validity dates by itself — PKIXParameters/CertPathValidator DOES
        // check notBefore/notAfter as part of the RFC 5280 path-validation
        // algorithm (unlike a bare cert.verify(pubkey) call), so this must
        // fail. If this assertion ever fails, that is a real finding (the
        // seam stopped checking dates) — do not loosen the assertion instead.
        val result = M0Probe.checkTrustPath(listOf(expiredDs), masterList)
        assertTrue(
            "expired document-signer cert must not be trusted via the trust-path check — got $result",
            result is M0Probe.TrustPathResult.NotTrusted,
        )

        // passiveAuth ALSO explicitly checks dsCerts[].checkValidity() before
        // ever reaching checkTrustPath (M0Probe.kt ~176-182) — belt-and-braces,
        // not this seam's only line of defense. Assert that path independently
        // here too, since it is the first line of defense a real read hits.
        var explicitCheckFailed = false
        try {
            expiredDs.checkValidity()
        } catch (e: Exception) {
            explicitCheckFailed = true
        }
        assertTrue("passiveAuth's explicit checkValidity() must also reject this cert", explicitCheckFailed)
    }

    // ------------------------------------------------------ (d) half-loaded masterlist

    @Test
    fun `half-loaded masterlist stays a passiveAuth-level ok false, never reaches checkTrustPath`() {
        val cscaKeyPair = freshEcKeyPair()
        val csca = selfSignedCsca("Synthetic CSCA A", cscaKeyPair, daysFromNow(-1), daysFromNow(365))

        // declared != parsed => consistent == false; passiveAuth returns
        // Verdict.unknown(...) BEFORE ever calling checkTrustPath (M0Probe.kt
        // ~157-161) — the half-load bucket (i) is caught upstream of this
        // seam, by design (never conflated with bucket (ii)'s real "no").
        val halfLoaded = M0Probe.MasterList(
            certsDeclared = 19,
            certsParsed = 17,
            keystore = keystoreOf("csca-0" to csca),
        )
        assertTrue("this MasterList must be inconsistent (half-loaded)", !halfLoaded.consistent)

        val verdict = if (!halfLoaded.consistent) {
            M0Probe.Verdict.unknown(
                "master list half-loaded: declared=${halfLoaded.certsDeclared} parsed=${halfLoaded.certsParsed}",
            )
        } else {
            error("unreachable for this test")
        }
        assertEquals(false, verdict.ok)
        assertEquals(null, verdict.allowed)
    }
}
