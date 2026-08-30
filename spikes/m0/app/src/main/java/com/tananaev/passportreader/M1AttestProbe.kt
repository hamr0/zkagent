package com.tananaev.passportreader

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.security.keystore.StrongBoxUnavailableException
import android.util.Log
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.SecureRandom
import java.security.cert.Certificate
import java.security.cert.X509Certificate

/**
 * M1 POC instrumentation — riskiest assumption: "hardware key attestation on
 * the owner's Pixel 6a is capturable and parseable" (PRD M1 row).
 *
 * THROWAWAY. Not shipped, not graduated (AGENT_RULES: never ship the POC).
 *
 * Generates an attested EC keypair in AndroidKeyStore under two configurations
 * (StrongBox and TEE), twice each, and dumps the full certificate chain to
 * logcat as PEM so a separate Node spike can parse and diff the chains
 * (linkability test: do two chains from the same device/config share anything
 * beyond the leaf attestation extension?).
 */
object M1AttestProbe {

    private const val TAG = "M1Attest"
    private const val KEYSTORE = "AndroidKeyStore"
    private const val BEGIN = "===== M1 ATTEST REPORT BEGIN ====="
    private const val END = "===== M1 ATTEST REPORT END ====="

    data class ConfigResult(
        val config: String,
        val run: Int,
        val alias: String,
        val challengeHex: String,
        val strongBoxUnavailable: Boolean,
        val error: String?,
        val keyGenMs: Long,
        val chainLength: Int,
    )

    /** Runs both configs, twice each, and logs one delimited report. Deletes aliases after. */
    fun runAndReport() {
        val lines = mutableListOf<String>()
        lines += BEGIN

        val results = mutableListOf<ConfigResult>()
        // (strongBox flag, config label)
        val configs = listOf(true to "strongbox", false to "tee")

        for ((strongBox, label) in configs) {
            for (run in 1..2) {
                val alias = "m1-attest-$label-run$run"
                val (result, pemLines) = generateAndDump(alias, label, run, strongBox)
                results += result
                lines += "--- config=$label run=$run summary ---"
                lines += "challenge_hex: ${result.challengeHex}"
                lines += "strongbox_unavailable: ${result.strongBoxUnavailable}"
                lines += "error: ${result.error ?: "none"}"
                lines += "keygen_ms: ${result.keyGenMs}"
                lines += "chain_length: ${result.chainLength}"
                lines.addAll(pemLines)
            }
        }

        lines += "===== SUMMARY ====="
        for (r in results) {
            lines += "config=${r.config} run=${r.run} strongbox_unavailable=${r.strongBoxUnavailable} " +
                "error=${r.error ?: "none"} keygen_ms=${r.keyGenMs} chain_length=${r.chainLength}"
        }
        lines += END

        for (line in lines) Log.i(TAG, line)
    }

    /**
     * Generates one attested keypair, dumps its chain as PEM lines, deletes the
     * alias, and returns the summary plus the PEM lines to embed in the report.
     */
    private fun generateAndDump(
        alias: String,
        configLabel: String,
        run: Int,
        strongBox: Boolean,
    ): Pair<ConfigResult, List<String>> {
        val challenge = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val challengeHex = challenge.joinToString("") { "%02x".format(it) }

        var strongBoxUnavailable = false
        var error: String? = null
        var keyGenMs = 0L
        var chainLength = 0
        val pemLines = mutableListOf<String>()

        try {
            val specBuilder = KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_SIGN)
                .setAlgorithmParameterSpec(java.security.spec.ECGenParameterSpec("secp256r1"))
                .setDigests(KeyProperties.DIGEST_SHA256)
                .setAttestationChallenge(challenge)
            if (strongBox) {
                specBuilder.setIsStrongBoxBacked(true)
            }
            val spec = specBuilder.build()

            val t0 = System.nanoTime()
            val kpg = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, KEYSTORE)
            kpg.initialize(spec)
            kpg.generateKeyPair()
            keyGenMs = (System.nanoTime() - t0) / 1_000_000

            val keyStore = KeyStore.getInstance(KEYSTORE)
            keyStore.load(null)
            val chain: Array<Certificate> = keyStore.getCertificateChain(alias) ?: emptyArray()
            chainLength = chain.size

            for ((i, cert) in chain.withIndex()) {
                pemLines += "--- config=$configLabel run=$run cert=$i ---"
                if (cert is X509Certificate) {
                    pemLines += "subject: ${cert.subjectX500Principal}"
                    pemLines += "issuer: ${cert.issuerX500Principal}"
                    pemLines += "serial: ${cert.serialNumber}"
                    pemLines += "notBefore: ${cert.notBefore}"
                    pemLines += "notAfter: ${cert.notAfter}"
                }
                pemLines.addAll(toPemLines(cert))
            }
        } catch (e: StrongBoxUnavailableException) {
            strongBoxUnavailable = true
            error = "${e.javaClass.simpleName}: ${e.message}"
        } catch (e: Exception) {
            error = "${e.javaClass.simpleName}: ${e.message}"
        } finally {
            try {
                val keyStore = KeyStore.getInstance(KEYSTORE)
                keyStore.load(null)
                if (keyStore.containsAlias(alias)) keyStore.deleteEntry(alias)
            } catch (e: Exception) {
                Log.w(TAG, "cleanup failed for $alias: ${e.javaClass.simpleName}")
            }
        }

        val result = ConfigResult(
            config = configLabel,
            run = run,
            alias = alias,
            challengeHex = challengeHex,
            strongBoxUnavailable = strongBoxUnavailable,
            error = error,
            keyGenMs = keyGenMs,
            chainLength = chainLength,
        )
        return result to pemLines
    }

    /** PEM-encodes a certificate, one Log-safe line (<=64 chars) per body line. */
    private fun toPemLines(cert: Certificate): List<String> {
        val b64 = android.util.Base64.encodeToString(
            cert.encoded,
            android.util.Base64.NO_WRAP,
        )
        val out = mutableListOf<String>()
        out += "-----BEGIN CERTIFICATE-----"
        var i = 0
        while (i < b64.length) {
            out += b64.substring(i, minOf(i + 64, b64.length))
            i += 64
        }
        out += "-----END CERTIFICATE-----"
        return out
    }
}
