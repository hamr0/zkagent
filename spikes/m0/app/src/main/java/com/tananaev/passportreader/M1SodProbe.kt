package com.tananaev.passportreader

import android.content.Context
import android.util.Log
import org.bouncycastle.asn1.ASN1ObjectIdentifier
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo
import org.bouncycastle.asn1.x9.ECNamedCurveTable
import org.jmrtd.lds.SODFile
import org.jmrtd.lds.icao.DG1File
import java.io.File
import java.security.PublicKey
import java.security.interfaces.RSAPublicKey
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * M1 ZK-fixture capture — a future ZK-proof spike (spikes/m1-zk, not built
 * yet) needs the RAW bytes of DG1 and EF.SOD as private inputs, plus a
 * PII-free record of the SOD's cryptographic parameters. M0 logged hashes
 * only; this writes the raw bytes M0 never saved.
 *
 * THROWAWAY. Not shipped, not graduated (AGENT_RULES: never ship the POC).
 *
 * PII discipline: the .dg1/.sod files ARE PII-bearing (DG1 = MRZ) and are
 * written only to the app's private external files dir — never logged,
 * pulled only into a gitignored fixtures/real/ path, never committed. The
 * .sod.txt / logcat report carries only algorithm names, key sizes, the
 * CSCA issuer DN (not PII — it identifies the issuing authority, not the
 * holder), the validity window, the DG coverage list, and byte lengths —
 * never MRZ content, names, numbers, or DOB.
 */
object M1SodProbe {

    private const val TAG = "M1Sod"
    private const val BEGIN = "===== M1 SOD REPORT BEGIN ====="
    private const val END = "===== M1 SOD REPORT END ====="

    /**
     * Writes `<docType>-<ts>.dg1`, `.sod`, and `.sod.txt` to [context]'s
     * private external files dir, and logs the `.sod.txt` content to logcat
     * between delimited markers.
     */
    fun saveFixturesAndReport(
        context: Context,
        dg1Encoded: ByteArray,
        sodEncoded: ByteArray,
        dg1File: DG1File,
        sodFile: SODFile,
    ) {
        val report = buildReport(sodFile, dg1Encoded.size, sodEncoded.size)

        val dir = context.getExternalFilesDir(null)
        if (dir == null) {
            Log.w(TAG, "M1Sod: external files dir unavailable — fixtures not saved")
        } else {
            val docType = docTypeOf(dg1File)
            val ts = SimpleDateFormat("yyyyMMddHHmmss", Locale.US).format(Date())
            val baseName = "$docType-$ts"
            try {
                File(dir, "$baseName.dg1").writeBytes(dg1Encoded)
                File(dir, "$baseName.sod").writeBytes(sodEncoded)
                File(dir, "$baseName.sod.txt").writeText(report)
                Log.i(TAG, "M1Sod stage: fixtures written to ${dir.absolutePath}/$baseName.{dg1,sod,sod.txt}")
            } catch (e: Exception) {
                Log.w(TAG, "M1Sod: fixture write failed: ${e.javaClass.simpleName}: ${e.message}")
            }
        }

        val lines = mutableListOf(BEGIN)
        lines.addAll(report.trimEnd('\n').lines())
        lines += END
        for (line in lines) Log.i(TAG, line)
    }

    /** ICAO 9303 document code: first letter 'P' = passport, 'I' = ID card. */
    private fun docTypeOf(dg1File: DG1File): String =
        when (dg1File.mrzInfo.documentCode?.trim()?.trimEnd('<')?.firstOrNull()?.uppercaseChar()) {
            'P' -> "passport"
            'I' -> "id"
            else -> "unknown"
        }

    private fun buildReport(sodFile: SODFile, dg1Len: Int, sodLen: Int): String {
        val sb = StringBuilder()
        sb.append("digest_algorithm (data-group hashes): ").append(safe { sodFile.digestAlgorithm }).append('\n')
        sb.append("signer_info_digest_algorithm: ").append(safe { sodFile.signerInfoDigestAlgorithm }).append('\n')
        sb.append("digest_encryption_algorithm (SignerInfo signature alg): ")
            .append(safe { sodFile.digestEncryptionAlgorithm }).append('\n')

        val ds = try { sodFile.docSigningCertificate } catch (e: Exception) { null }
        if (ds == null) {
            sb.append("ds_certificate: unavailable\n")
        } else {
            sb.append("ds_public_key_algorithm: ").append(ds.publicKey.algorithm).append('\n')
            sb.append("ds_public_key_size: ").append(keySizeOf(ds.publicKey)).append('\n')
            sb.append("ds_signature_algorithm: ").append(ds.sigAlgName).append('\n')
            sb.append("ds_issuer_dn (CSCA, not PII): ").append(ds.issuerX500Principal).append('\n')
            sb.append("ds_valid_from: ").append(ds.notBefore).append('\n')
            sb.append("ds_valid_to: ").append(ds.notAfter).append('\n')
        }

        sb.append("data_groups_covered: ")
            .append(safe { sodFile.dataGroupHashes.keys.sorted().joinToString(", ") { "DG$it" } })
            .append('\n')
        sb.append("dg1_byte_length: ").append(dg1Len).append('\n')
        sb.append("sod_byte_length: ").append(sodLen).append('\n')
        return sb.toString()
    }

    /** RSA modulus bits, or the EC curve name resolved via the ASN.1 SubjectPublicKeyInfo. */
    private fun keySizeOf(pub: PublicKey): String = try {
        when (pub) {
            is RSAPublicKey -> "RSA-${pub.modulus.bitLength()}"
            else -> {
                val spki = SubjectPublicKeyInfo.getInstance(pub.encoded)
                val curveOid = ASN1ObjectIdentifier.getInstance(spki.algorithm.parameters)
                val name = ECNamedCurveTable.getName(curveOid)
                "EC-${name ?: curveOid.id}"
            }
        }
    } catch (e: Exception) {
        "unknown (${e.javaClass.simpleName})"
    }

    private fun safe(f: () -> String): String =
        try { f() } catch (e: Exception) { "unavailable (${e.javaClass.simpleName})" }
}
