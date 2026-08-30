package com.tananaev.passportreader

import android.content.Context
import android.util.Base64
import android.util.Log
import com.google.android.play.core.integrity.IntegrityManagerFactory
import com.google.android.play.core.integrity.StandardIntegrityManager
import com.google.android.play.core.integrity.StandardIntegrityManager.PrepareIntegrityTokenRequest
import com.google.android.play.core.integrity.StandardIntegrityManager.StandardIntegrityTokenRequest
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * M1 POC instrumentation — Q23 option (3): does a Play Integrity token contain
 * anything stable across sites/requests that could act as a device identifier?
 * Requirement under test: "nothing in the payload is stable across sites".
 *
 * THROWAWAY. Not shipped, not graduated (AGENT_RULES: never ship the POC).
 *
 * This probe only REQUESTS tokens and dumps the raw, still-encrypted JWE/JWS
 * compact serialization to logcat — it does not decode them (decoding needs a
 * server-side call or Play-Console-issued keys per Q23 Part 1 findings, and is
 * out of scope for the on-device POC). Analysis of whether the encrypted blobs
 * or their surrounding metadata are stable happens in a separate offline step.
 *
 * Simulates two "sites" via two distinct nonces (classic) / requestHashes
 * (standard), two requests per site, for four tokens total per request type.
 *
 * cloudProjectNumber is read from local.properties key
 * `m1.integrity.cloudProjectNumber` (gitignored) so no project id enters
 * source. If absent, logs a message and exits without calling the API.
 */
object M1IntegrityProbe {

    private const val TAG = "M1Integrity"
    private const val BEGIN = "===== M1 INTEGRITY REPORT BEGIN ====="
    private const val END = "===== M1 INTEGRITY REPORT END ====="
    private const val REQUEST_TIMEOUT_SECONDS = 60L

    private data class TokenResult(
        val site: String,
        val run: Int,
        val nonceOrHashB64: String,
        val requestMs: Long,
        val tokenLength: Int,
        val tokenSha256: String,
        val error: String?,
    )

    fun runAndReport(context: Context) {
        val lines = mutableListOf<String>()
        lines += BEGIN

        val cloudProjectNumber = readCloudProjectNumber(context)
        if (cloudProjectNumber == null) {
            lines += "no project number configured"
            lines += END
            for (line in lines) Log.i(TAG, line)
            return
        }

        val results = mutableListOf<TokenResult>()

        // Two simulated "sites", two requests each, using standard requests
        // (requestHash) since that is the API Google recommends for frequent
        // checks and does not require Play-Console-issued keys on device.
        val sites = listOf("site-a", "site-b")

        try {
            val provider = prepareProvider(context, cloudProjectNumber)
            for (site in sites) {
                for (run in 1..2) {
                    val siteSalt = ByteArray(32).also { SecureRandom().nextBytes(it) }
                    val requestHash = sha256Base64(siteSalt)
                    val (result, rawToken) = requestStandardToken(provider, site, run, requestHash)
                    results += result
                    lines += "--- site=$site run=$run summary ---"
                    lines += "request_hash_b64: ${result.nonceOrHashB64}"
                    lines += "request_ms: ${result.requestMs}"
                    lines += "token_length: ${result.tokenLength}"
                    lines += "token_sha256: ${result.tokenSha256}"
                    lines += "error: ${result.error ?: "none"}"
                    if (rawToken != null) {
                        lines += "--- site=$site run=$run raw_token (still-encrypted JWE) ---"
                        lines.addAll(chunkToken(site, run, rawToken))
                    }
                }
            }
        } catch (e: Exception) {
            lines += "prepare_error: ${e.javaClass.simpleName}: ${e.message}"
        }

        lines += "===== SUMMARY ====="
        for (r in results) {
            lines += "site=${r.site} run=${r.run} request_ms=${r.requestMs} " +
                "token_length=${r.tokenLength} token_sha256=${r.tokenSha256} " +
                "error=${r.error ?: "none"}"
        }
        lines += END

        for (line in lines) Log.i(TAG, line)
    }

    /** Reads m1.integrity.cloudProjectNumber from local.properties, if present. */
    private fun readCloudProjectNumber(context: Context): Long? {
        return try {
            // local.properties is not packaged into the APK by default; the
            // build reads it at build time via BuildConfig instead of at
            // runtime file access, so this probe expects the value to have
            // been injected as a BuildConfig field. See build.gradle.kts.
            val value = BuildConfig.M1_INTEGRITY_CLOUD_PROJECT_NUMBER
            if (value.isBlank()) null else value.toLongOrNull()
        } catch (e: Exception) {
            Log.w(TAG, "failed to read cloud project number: ${e.javaClass.simpleName}")
            null
        }
    }

    private fun prepareProvider(
        context: Context,
        cloudProjectNumber: Long,
    ): StandardIntegrityManager.StandardIntegrityTokenProvider {
        val manager = IntegrityManagerFactory.createStandard(context)
        val latch = CountDownLatch(1)
        var provider: StandardIntegrityManager.StandardIntegrityTokenProvider? = null
        var error: Exception? = null

        manager.prepareIntegrityToken(
            PrepareIntegrityTokenRequest.builder()
                .setCloudProjectNumber(cloudProjectNumber)
                .build()
        ).addOnSuccessListener {
            provider = it
            latch.countDown()
        }.addOnFailureListener {
            error = it
            latch.countDown()
        }

        latch.await(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        error?.let { throw it }
        return provider ?: throw IllegalStateException("prepareIntegrityToken timed out")
    }

    /** Splits a raw token into <=64-char logcat-safe lines, prefixed for reassembly. */
    private fun chunkToken(site: String, run: Int, token: String): List<String> {
        val out = mutableListOf<String>()
        var i = 0
        var chunk = 0
        while (i < token.length) {
            val end = minOf(i + 64, token.length)
            out += "token_chunk site=$site run=$run idx=$chunk: ${token.substring(i, end)}"
            i = end
            chunk++
        }
        return out
    }

    private fun requestStandardToken(
        provider: StandardIntegrityManager.StandardIntegrityTokenProvider,
        site: String,
        run: Int,
        requestHash: String,
    ): Pair<TokenResult, String?> {
        val latch = CountDownLatch(1)
        var token: String? = null
        var error: Exception? = null
        val t0 = System.nanoTime()

        provider.request(
            StandardIntegrityTokenRequest.builder()
                .setRequestHash(requestHash)
                .build()
        ).addOnSuccessListener {
            token = it.token()
            latch.countDown()
        }.addOnFailureListener {
            error = it
            latch.countDown()
        }

        latch.await(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        val requestMs = (System.nanoTime() - t0) / 1_000_000

        val t = token
        return if (t != null) {
            TokenResult(
                site = site,
                run = run,
                nonceOrHashB64 = requestHash,
                requestMs = requestMs,
                tokenLength = t.length,
                tokenSha256 = sha256Base64(t.toByteArray(Charsets.UTF_8)),
                error = null,
            ) to t
        } else {
            TokenResult(
                site = site,
                run = run,
                nonceOrHashB64 = requestHash,
                requestMs = requestMs,
                tokenLength = 0,
                tokenSha256 = "",
                error = error?.let { "${it.javaClass.simpleName}: ${it.message}" } ?: "unknown",
            ) to null
        }
    }

    private fun sha256Base64(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return Base64.encodeToString(digest, Base64.NO_WRAP or Base64.URL_SAFE)
    }
}
