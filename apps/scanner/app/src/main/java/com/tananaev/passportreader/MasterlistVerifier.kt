package com.tananaev.passportreader

import android.util.Log
import org.bouncycastle.asn1.ASN1InputStream
import org.bouncycastle.asn1.ASN1Primitive
import org.bouncycastle.asn1.ASN1Sequence
import org.bouncycastle.asn1.ASN1Set
import org.bouncycastle.asn1.x509.Certificate as BcCertificate
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cms.CMSException
import org.bouncycastle.cms.CMSSignedData
import org.bouncycastle.cms.SignerInformation
import org.bouncycastle.cms.jcajce.JcaSimpleSignerInfoVerifierBuilder
import org.bouncycastle.cert.X509CertificateHolder
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.security.KeyStore
import java.security.cert.X509Certificate

/**
 * §6.2 item 7 — the masterlist bundled as a full BSI CMS SignedData
 * (`DE_ML_*.ml`), verified (CMS signature + signer chain,
 * "CSCA Master List Signer" <- "csca-germany") BEFORE the integrity check,
 * with the owner's two-bucket rule applied on top:
 *
 *   - CMS signature/chain failure, truncated/half-loaded, certs-parsed !=
 *     certs-declared, or unparsable  =>  ok:false (integrity failure)
 *   - well-formed, CMS-verified list that simply lacks the presenting
 *     issuer's CSCA  =>  ok:true, allowed:false (handled by the caller's
 *     passive-auth step, once this class has produced a trusted [KeyStore])
 *
 * The trust anchor for "csca-germany" is a BYTE-PINNED asset
 * (`assets/csca-germany-root.der`), not merely a name/DN match inside the
 * CMS file's own embedded certificates — trusting a same-file "csca-germany"-
 * named certificate with no external pin would let a corrupted/forged
 * masterlist vouch for itself. Provenance of the pinned root: extracted from
 * a `DE_ML_*.ml` capture whose CMS signature independently verified
 * (`openssl cms -verify -noverify`, self-consistency only) during the M2
 * opening session (`docs/logs/M2-SCAN-EVIDENCE.md` "escalations" — BSI
 * masterlist ZIP provenance was checked via headless browser against
 * bsi.bund.de). **This file does NOT re-run that external provenance check**
 * — see the conformance report's escalation: the pin is carried forward
 * from that session's evidence, not independently re-verified here.
 */
object MasterlistVerifier {

    private const val TAG = "MasterlistVerifier"
    private const val SIGNER_LEAF_CN = "CSCA Master List Signer"
    private const val ANCHOR_CN = "csca-germany"

    /** Mirrors M0Probe.MasterList's shape (declared/parsed/consistent) — kept
     * as a distinct type because this one additionally carries the CMS
     * integrity verdict, never conflated with the eContent parse counts. */
    class MasterList(
        val certsDeclared: Int,
        val certsParsed: Int,
        val keystore: KeyStore,
        val certsExcluded: Int = 0,
    ) {
        val consistent get() = certsDeclared == certsParsed
    }

    /** ok:false => the two-bucket "integrity failure" branch (never allowed).
     * ok:true carries a usable [MasterList] whether or not the presenting
     * issuer turns out to be in it — that "no" is decided downstream by
     * passive auth's own CertPathValidator step, not by this class. */
    sealed class LoadResult {
        data class Failure(val reason: String) : LoadResult()
        data class Success(val masterList: MasterList) : LoadResult()
    }

    /**
     * @param cmsBytes the full raw `DE_ML_*.ml` file (CMS SignedData) —
     *   NOT the bare eContent. A truncated/half file is expected to fail
     *   CMS parsing outright, which is itself the integrity refusal.
     * @param pinnedRootDer the byte-pinned "csca-germany" trust anchor.
     * @param excludeAnchorFor planted negative (ii), same contract as
     *   M0Probe.loadMasterList: drop the CSCA that actually anchors this
     *   document's signer, so the two-bucket rule's "well-formed but
     *   lacking the issuer" branch can be exercised on demand.
     */
    fun load(
        cmsBytes: ByteArray,
        pinnedRootDer: ByteArray,
        excludeAnchorFor: X509Certificate? = null,
    ): LoadResult {
        // ---- step 1: CMS parse + signature + signer-chain verification ----
        val cms: CMSSignedData
        try {
            cms = CMSSignedData(cmsBytes)
        } catch (e: Exception) {
            return LoadResult.Failure("CMS parse failed: ${e.javaClass.simpleName}: ${e.message}")
        }

        val pinnedRoot = try {
            parseCert(pinnedRootDer)
        } catch (e: Exception) {
            return LoadResult.Failure("pinned csca-germany root asset unparsable: ${e.javaClass.simpleName}: ${e.message}")
        }

        val embeddedCerts: List<X509Certificate> = try {
            val converter = JcaX509CertificateConverter().setProvider(bcProvider)
            cms.certificates.getMatches(null).map { converter.getCertificate(it) }
        } catch (e: Exception) {
            return LoadResult.Failure("CMS certificate store unreadable: ${e.javaClass.simpleName}: ${e.message}")
        }
        if (embeddedCerts.isEmpty()) return LoadResult.Failure("CMS carries no certificates")

        val signerInfos: Collection<SignerInformation> = cms.signerInfos.signers
        if (signerInfos.isEmpty()) return LoadResult.Failure("CMS carries no signerInfo")

        // Find the signer cert (by CN, then confirm it actually verifies the
        // signerInfo — a name match alone proves nothing).
        val signerCert = embeddedCerts.firstOrNull { cnOf(it) == SIGNER_LEAF_CN }
            ?: return LoadResult.Failure("no certificate named \"$SIGNER_LEAF_CN\" in the CMS store")

        var signatureVerified = false
        for (signer in signerInfos) {
            try {
                // PROVIDER INSTANCE, not a name lookup — see parseCert's note;
                // the app never globally registers anything named "BC".
                val verifier = JcaSimpleSignerInfoVerifierBuilder().setProvider(bcProvider).build(signerCert)
                if (signer.verify(verifier)) {
                    signatureVerified = true
                    break
                }
            } catch (e: CMSException) {
                Log.w(TAG, "signerInfo verify attempt failed: ${e.javaClass.simpleName}: ${e.message}")
            } catch (e: Exception) {
                Log.w(TAG, "signerInfo verify attempt failed (non-CMS exception): ${e.javaClass.simpleName}: ${e.message}")
            }
        }
        if (!signatureVerified) return LoadResult.Failure("CMS signature did not verify against \"$SIGNER_LEAF_CN\"")

        // Signer chain: "CSCA Master List Signer" <- "csca-germany", and the
        // "csca-germany" cert in THIS FILE must byte-match the pinned root —
        // not merely share its name (that would let a forged file vouch for
        // itself with a same-named-but-different-keyed impostor).
        if (signerCert.issuerX500Principal != pinnedRoot.subjectX500Principal) {
            return LoadResult.Failure(
                "signer issuer DN does not match the pinned csca-germany root " +
                    "(signer issuer=${signerCert.issuerX500Principal}, pinned subject=${pinnedRoot.subjectX500Principal})",
            )
        }
        try {
            signerCert.verify(pinnedRoot.publicKey)
        } catch (e: Exception) {
            return LoadResult.Failure(
                "\"$SIGNER_LEAF_CN\" signature does not verify against the pinned csca-germany root: " +
                    "${e.javaClass.simpleName}: ${e.message}",
            )
        }
        try {
            signerCert.checkValidity()
            pinnedRoot.checkValidity()
        } catch (e: Exception) {
            return LoadResult.Failure("signer or anchor certificate not valid today: ${e.javaClass.simpleName}: ${e.message}")
        }
        Log.i(
            TAG,
            "CMS integrity OK: signature verified against \"$SIGNER_LEAF_CN\", " +
                "chained to the PINNED \"$ANCHOR_CN\" root (byte match, not name match)",
        )

        // ---- step 2: parse the eContent as CscaMasterList (declared vs parsed) ----
        val eContentBytes: ByteArray = try {
            cms.signedContent?.content as? ByteArray
                ?: return LoadResult.Failure("CMS eContent is detached or missing — cannot check declared-vs-parsed")
        } catch (e: Exception) {
            return LoadResult.Failure("CMS eContent unreadable: ${e.javaClass.simpleName}: ${e.message}")
        }

        val parsed = try {
            parseCscaMasterList(ByteArrayInputStream(eContentBytes), excludeAnchorFor)
        } catch (e: Exception) {
            return LoadResult.Failure("CscaMasterList eContent parse failed: ${e.javaClass.simpleName}: ${e.message}")
        }

        if (!parsed.consistent) {
            return LoadResult.Failure(
                "master list half-loaded: declared=${parsed.certsDeclared} parsed=${parsed.certsParsed}",
            )
        }

        return LoadResult.Success(parsed)
    }

    private fun cnOf(cert: X509Certificate): String? {
        val dn = cert.subjectX500Principal.name
        // RFC2253 name — find the CN= component. BouncyCastle/JDK ordering
        // varies; scan defensively rather than assume a fixed position.
        return dn.split(",").map { it.trim() }
            .firstOrNull { it.startsWith("CN=", ignoreCase = true) }
            ?.substringAfter("=")
            ?.let { unescapeDn(it) }
    }

    private fun unescapeDn(s: String): String = s.replace("\\,", ",").trim()

    /** ICAO 9303 CSCA certs commonly use EXPLICIT EC domain parameters, which
     * the JDK's default "SUN" CertificateFactory refuses to parse
     * ("Only named ECParameters supported"). Passing the BC PROVIDER
     * INSTANCE (not a name lookup) avoids both that restriction and any
     * NoSuchProviderException — the app never globally registers a provider
     * named "BC" (only SpongyCastle as "SC" — see MainApplication / F1). */
    private val bcProvider = org.bouncycastle.jce.provider.BouncyCastleProvider()

    private fun parseCert(der: ByteArray): X509Certificate =
        JcaX509CertificateConverter().setProvider(bcProvider).getCertificate(X509CertificateHolder(der))

    /**
     * `CscaMasterList ::= SEQUENCE { version INTEGER, certList SET OF
     * Certificate }` — the ICAO 9303 structure carried as the CMS eContent.
     * Same declared-vs-parsed discipline as M0Probe.loadMasterList (a silent
     * half-load must never look "consistent" by construction).
     */
    private fun parseCscaMasterList(
        stream: InputStream,
        excludeAnchorFor: X509Certificate?,
    ): MasterList {
        val keystore = KeyStore.getInstance(KeyStore.getDefaultType())
        keystore.load(null, null)
        var declared = 0
        var parsed = 0
        var index = 0
        var excluded = 0
        val anchorDn = excludeAnchorFor?.issuerX500Principal
        val asn1 = ASN1InputStream(stream)
        val top: ASN1Primitive = asn1.readObject() ?: throw IllegalStateException("empty eContent")
        val seq = ASN1Sequence.getInstance(top)
        require(seq.size() == 2) { "CscaMasterList: expected SEQUENCE{version, certList}, got size ${seq.size()}" }
        val certSet = ASN1Set.getInstance(seq.getObjectAt(1))
        declared = certSet.size()
        for (i in 0 until certSet.size()) {
            try {
                val encoded = BcCertificate.getInstance(certSet.getObjectAt(i)).encoded
                val cert = parseCert(encoded)
                if (anchorDn != null && cert.subjectX500Principal == anchorDn) {
                    excluded++
                    continue
                }
                keystore.setCertificateEntry("csca-${index++}", cert)
                parsed++
            } catch (e: Exception) {
                Log.w(TAG, "master list entry $i unparseable: ${e.javaClass.simpleName}")
            }
        }
        if (anchorDn != null) declared = parsed // exclusion is deliberate, not a half-load
        return MasterList(declared, parsed, keystore, excluded)
    }
}
