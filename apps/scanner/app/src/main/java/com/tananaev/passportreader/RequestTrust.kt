package com.tananaev.passportreader

import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.math.BigInteger
import java.net.HttpURLConnection
import java.net.URI
import java.net.URISyntaxException
import java.net.URL
import java.nio.charset.StandardCharsets
import java.security.AlgorithmParameters
import java.security.KeyFactory
import java.security.PublicKey
import java.security.Security
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.security.spec.ECParameterSpec
import java.security.spec.ECPoint
import java.security.spec.ECPublicKeySpec
import java.util.Base64

/**
 * §6.2 item 14 (D34/D37) — request-object trust, closing the escalation
 * recorded in [HandoffClient]'s class doc ("this client does NOT pin a
 * request-signer key"). Two MUSTs, in order:
 *
 *  1. **Origin consistency (D37).** `client_id`, `request_uri`, and the
 *     request object's own `response_uri` MUST all resolve to the same
 *     origin (scheme+host+port; path differences are fine). A mismatch is a
 *     refusal — this is the `av://`-hijack half of Q29/D37: a hijacked or
 *     relayed link can only ever route the answer back to the origin that
 *     issued the request.
 *  2. **ES256 JWS verification (D34).** The request object MUST be a
 *     compact JWS, `alg` MUST be exactly `ES256`, and it MUST verify against
 *     a key resolved for the request's origin. NOTHING in the payload
 *     (nonce, `response_uri`, `state`, tier, `evidence_required`) is trusted
 *     until this passes.
 *
 * Trust anchor is the origin itself (D37 — "verifier is not our issue"),
 * not an authority-bound allow-list:
 *   - **production path**: the verifier's EC public key is fetched over TLS
 *     from `https://<origin>/.well-known/zkagent-verifier`. Shape: a single
 *     JWK object (`{"kty":"EC","crv":"P-256","x":"...","y":"...","kid":"..."}`,
 *     chosen as the simplest shape that says everything needed) OR a
 *     standard JWKS (`{"keys":[ <JWK>, ... ]}`, for interop with JWKS
 *     tooling) — the first EC/P-256 entry is used; `kid` is matched against
 *     the JWS header's `kid` when both are present. `spikes/m2-handoff`
 *     does not serve this path yet, so this 404s there by design — this
 *     path is exercised only by an origin that is NOT the M2 spike host.
 *   - **M2 spike exception (D37)**: `spikes/m2-handoff` runs on plain
 *     `http://127.0.0.1` (no TLS, nothing at the well-known path). ONE
 *     build-time pinned DEV public key stands in, labelled dev-only, and is
 *     accepted ONLY when the origin's scheme is `http` and its host is
 *     `127.0.0.1` or `localhost` — never for any other origin, so this
 *     exception can never leak into what a real deployment would trust. The
 *     public key below is extracted from
 *     `spikes/m2-handoff/dev-request-signer-key.mjs` (`publicKeyPem`, kid
 *     `dev-request-signer-1`) — PUBLIC half only, no private material enters
 *     this app.
 *
 * "No production trust store yet" stands as a disclosure until a real TLS
 * origin exists to test the well-known path against (D34/Q29/D37).
 *
 * Uses `java.net.URI` / `java.util.Base64` / stdlib JCA only — no new
 * dependency, and deliberately NOT `android.net.Uri` / `android.util.Base64`
 * so the pure parts of this file (origin comparison, DER<->raw signature
 * conversion, JWS parsing) are exercisable by a plain JVM unit test, same
 * discipline as [Canonical] / [MasterlistVerifier]. The provider-shadowing
 * hazard SpongyCastle creates for JCA `Signature` resolution ([DeviceKey]'s
 * `resolveByAttempt`, F1) applies here too — [resolveVerifierByAttempt]
 * mirrors that provider-walk rather than trusting the default provider.
 */
object RequestTrust {

    private const val TAG = "RequestTrust"

    private val B64U_DECODER = Base64.getUrlDecoder()

    // ---------------------------------------------------------- origin (D37)

    /** Normalizes a URL string to `scheme://host:port` (default port filled
     * in for http/https), or null if it isn't a parseable absolute URL with
     * a scheme and host. Path/query are deliberately not part of the
     * comparison — D37 only binds scheme+host+port. */
    fun originOf(urlString: String): String? {
        return try {
            val uri = URI(urlString)
            val scheme = uri.scheme?.lowercase() ?: return null
            val host = uri.host?.lowercase() ?: return null
            val port = if (uri.port != -1) uri.port else defaultPortFor(scheme) ?: return null
            "$scheme://$host:$port"
        } catch (e: URISyntaxException) {
            null
        }
    }

    private fun defaultPortFor(scheme: String): Int? = when (scheme) {
        "https" -> 443
        "http" -> 80
        else -> null
    }

    // -------------------------------------------------------------- keys

    data class ResolvedKey(val publicKey: PublicKey, val kid: String?, val isDev: Boolean)

    private const val DEV_KEY_KID = "dev-request-signer-1"

    // Extracted from spikes/m2-handoff/dev-request-signer-key.mjs's
    // publicKeyPem (SPKI DER, uncompressed EC point 0x04||X||Y) via:
    //   openssl ec -pubin -in <pem> -text -noout
    // PUBLIC key only. Never accepted outside http://127.0.0.1|localhost.
    private const val DEV_KEY_X_B64U = "C0-YHKxkZ8_Fffp7q17BPd1KRuwxNue4bvANNKBMv0Q"
    private const val DEV_KEY_Y_B64U = "tXB_f0WJ6bf48Ax6ijOZXPCI3xOZlBA7gW5kK0XdZts"

    private val devKey: PublicKey? by lazy {
        buildEcP256PublicKey(DEV_KEY_X_B64U, DEV_KEY_Y_B64U)
    }

    /** Resolves the trusted request-signer key for [origin] ("scheme://host:port",
     * from [originOf]). Returns null if no key can be resolved — the caller
     * MUST treat that as a refusal, never a warn-and-continue. */
    fun resolveVerifierKey(origin: String): ResolvedKey? {
        val parsed = try {
            URI(origin)
        } catch (e: URISyntaxException) {
            return null
        }
        val scheme = parsed.scheme
        val host = parsed.host
        return if (scheme == "http" && (host == "127.0.0.1" || host == "localhost")) {
            val key = devKey
            if (key == null) {
                Log.e(TAG, "resolveVerifierKey: dev key failed to build — see prior log for the JCA cause")
                return null
            }
            Log.w(TAG, "resolveVerifierKey: using build-time pinned DEV request-signer key for $origin — NOT a production trust store (D37 M2-scope exception, dev-only)")
            ResolvedKey(key, DEV_KEY_KID, isDev = true)
        } else {
            fetchWellKnownKey(origin)
        }
    }

    private fun fetchWellKnownKey(origin: String): ResolvedKey? {
        val url = "$origin/.well-known/zkagent-verifier"
        return try {
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 10_000
                readTimeout = 10_000
            }
            val status = conn.responseCode
            if (status !in 200..299) {
                Log.w(TAG, "fetchWellKnownKey: GET $url -> HTTP $status")
                return null
            }
            val body = (conn.inputStream ?: return null).bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
            val root = JSONObject(body)
            val jwk = if (root.has("keys")) {
                val keys = root.optJSONArray("keys")
                if (keys == null || keys.length() == 0) {
                    Log.w(TAG, "fetchWellKnownKey: JWKS at $url has no keys")
                    return null
                }
                keys.getJSONObject(0)
            } else {
                root
            }
            parseEcJwk(jwk)
        } catch (e: Exception) {
            Log.w(TAG, "fetchWellKnownKey: GET $url failed: ${e.javaClass.simpleName}: ${e.message}")
            null
        }
    }

    private fun parseEcJwk(jwk: JSONObject): ResolvedKey? {
        if (jwk.optString("kty") != "EC" || jwk.optString("crv") != "P-256") {
            Log.w(TAG, "parseEcJwk: not an EC/P-256 JWK (kty=${jwk.optString("kty")} crv=${jwk.optString("crv")})")
            return null
        }
        val x = jwk.optString("x", "")
        val y = jwk.optString("y", "")
        if (x.isEmpty() || y.isEmpty()) {
            Log.w(TAG, "parseEcJwk: JWK missing x/y")
            return null
        }
        val pub = buildEcP256PublicKey(x, y) ?: return null
        val kid = jwk.optString("kid", "").ifEmpty { null }
        return ResolvedKey(pub, kid, isDev = false)
    }

    private fun buildEcP256PublicKey(xB64u: String, yB64u: String): PublicKey? {
        return try {
            val x = BigInteger(1, B64U_DECODER.decode(xB64u))
            val y = BigInteger(1, B64U_DECODER.decode(yB64u))
            val point = ECPoint(x, y)
            for (provider in Security.getProviders()) {
                if (provider.getService("KeyFactory", "EC") == null) continue
                try {
                    val params = AlgorithmParameters.getInstance("EC", provider)
                    params.init(ECGenParameterSpec("secp256r1"))
                    val ecParams = params.getParameterSpec(ECParameterSpec::class.java)
                    val kf = KeyFactory.getInstance("EC", provider)
                    return kf.generatePublic(ECPublicKeySpec(point, ecParams))
                } catch (e: Throwable) {
                    Log.i(TAG, "buildEcP256PublicKey: provider '${provider.name}' failed: ${e.javaClass.simpleName}")
                }
            }
            Log.e(TAG, "buildEcP256PublicKey: no provider could build the EC public key")
            null
        } catch (e: Exception) {
            Log.e(TAG, "buildEcP256PublicKey: failed: ${e.javaClass.simpleName}: ${e.message}")
            null
        }
    }

    // ------------------------------------------------------- JWS verify (D34)

    data class VerifyOutcome(val ok: Boolean, val payload: JSONObject?, val reason: String?)

    /** Verifies a compact ES256 JWS against [key]. NEVER trusts anything
     * about [compactJws] before the signature check passes — `alg` is
     * pinned to exactly `ES256` (an attacker-chosen `none`/`HS256` is
     * refused), and `kid` is matched when both the header and [key] carry
     * one. Mirrors `spikes/m2-handoff/jws.mjs`'s `verifyJws` contract
     * (never throws; `{ok:false, reason}` on any failure). */
    fun verifyRequestObject(compactJws: String, key: ResolvedKey): VerifyOutcome {
        val parts = compactJws.trim().split(".")
        if (parts.size != 3) return VerifyOutcome(false, null, "not_compact_jws")
        val (h, p, s) = parts
        return try {
            val header = JSONObject(String(B64U_DECODER.decode(h), StandardCharsets.UTF_8))
            if (header.optString("alg") != "ES256") {
                return VerifyOutcome(false, null, "alg_mismatch:${header.optString("alg", "<absent>")}")
            }
            val headerKid = header.optString("kid", "").ifEmpty { null }
            if (key.kid != null && headerKid != null && key.kid != headerKid) {
                return VerifyOutcome(false, null, "kid_mismatch:header=$headerKid resolved=${key.kid}")
            }
            val sigRaw = B64U_DECODER.decode(s)
            if (sigRaw.size != 64) return VerifyOutcome(false, null, "signature_malformed")
            val sigDer = rawToDer(sigRaw)
            val verifier = resolveVerifierByAttempt("SHA256withECDSA", key.publicKey)
                ?: return VerifyOutcome(false, null, "no_provider_for_verify")
            verifier.update("$h.$p".toByteArray(StandardCharsets.UTF_8))
            if (!verifier.verify(sigDer)) return VerifyOutcome(false, null, "signature_invalid")
            val payload = JSONObject(String(B64U_DECODER.decode(p), StandardCharsets.UTF_8))
            VerifyOutcome(true, payload, null)
        } catch (e: Exception) {
            VerifyOutcome(false, null, "jws_malformed:${e.javaClass.simpleName}")
        }
    }

    /** [DeviceKey.resolveByAttempt]'s pattern (F1's SpongyCastle-shadowing
     * fix), for verify instead of sign: resolve the provider by ATTEMPT
     * (does `initVerify` actually succeed?), never by name/priority. */
    private fun resolveVerifierByAttempt(algorithm: String, publicKey: PublicKey): Signature? {
        for (provider in Security.getProviders()) {
            if (provider.getService("Signature", algorithm) == null) continue
            try {
                val sig = Signature.getInstance(algorithm, provider)
                try {
                    sig.initVerify(publicKey)
                    return sig
                } catch (e: Throwable) {
                    Log.i(TAG, "resolveVerifierByAttempt: provider '${provider.name}' rejected key: ${e.javaClass.simpleName}")
                }
            } catch (e: Throwable) {
                Log.i(TAG, "resolveVerifierByAttempt: provider '${provider.name}' getInstance failed: ${e.javaClass.simpleName}")
            }
        }
        return null
    }

    // ------------------------------------------------ raw(r||s) <-> DER ECDSA

    /** JWS ES256 signatures are raw `R || S` (64 bytes, RFC 7518 §3.4);
     * `Signature.verify` for `SHA256withECDSA` expects the ASN.1 DER
     * `ECDSA-Sig-Value` encoding. Pure/stateless — no JCA involved, so this
     * (and [derInteger]/[derLength]) is exercisable head-on by a JVM test. */
    fun rawToDer(raw: ByteArray): ByteArray {
        require(raw.size == 64) { "expected a 64-byte raw r||s ES256 signature, got ${raw.size}" }
        val body = derInteger(raw.copyOfRange(0, 32)) + derInteger(raw.copyOfRange(32, 64))
        return byteArrayOf(0x30) + derLength(body.size) + body
    }

    private fun derInteger(unsignedBigEndian: ByteArray): ByteArray {
        var bytes = unsignedBigEndian
        var i = 0
        while (i < bytes.size - 1 && bytes[i] == 0.toByte()) i++
        bytes = bytes.copyOfRange(i, bytes.size)
        val content = if ((bytes[0].toInt() and 0x80) != 0) byteArrayOf(0) + bytes else bytes
        return byteArrayOf(0x02) + derLength(content.size) + content
    }

    private fun derLength(len: Int): ByteArray {
        if (len < 0x80) return byteArrayOf(len.toByte())
        var l = len
        val out = ArrayList<Byte>()
        while (l > 0) {
            out.add(0, (l and 0xFF).toByte())
            l = l ushr 8
        }
        return byteArrayOf((0x80 or out.size).toByte()) + out.toByteArray()
    }

    // -------------------------------------------------------- tier (item 13)

    /** Extracts the raw `zkagent.tier` string from a VERIFIED request
     * payload, or null if absent/empty. Pure parsing only — validity
     * (A/B/C vs. anything else) is judged by the caller
     * ([MainActivity.tierOutcomeFor], §6.2 item 13's absent/invalid ->
     * fail-loud, tier-C -> not-supported-in-this-build distinction). Kept
     * here (not duplicated in MainActivity) so it is exercisable by a plain
     * JVM unit test without an Activity. */
    fun tierOf(json: JSONObject): String? =
        json.optJSONObject("zkagent")?.optString("tier")?.takeIf { it.isNotEmpty() }

    // ------------------------------------------------- evidence_required (D31)

    /** Log-only, value-free parse of the request object's
     * `zkagent.evidence_required` shape (D31 any-of groups, e.g.
     * `[["sig-ed25519/1","sig-p256/1"]]` -> `any-of[sig-ed25519/1,sig-p256/1]`)
     * — for the `evidence_required:` report line only. NEVER a behavioural
     * input: which plug the device actually offers is decided solely by
     * [DeviceKey]'s own preference order (D36) — this function does not
     * feed that decision, it only describes what a verifier said it would
     * accept, for the human reading the report. Absent/empty/malformed ->
     * `"absent"`, never a thrown exception. */
    fun describeEvidenceRequired(requestObject: JSONObject): String {
        val arr = requestObject.optJSONObject("zkagent")?.optJSONArray("evidence_required")
        if (arr == null || arr.length() == 0) return "absent"
        val groups = (0 until arr.length()).map { i ->
            when (val item = arr.opt(i)) {
                is JSONArray -> "any-of[" + (0 until item.length()).joinToString(",") { j -> item.optString(j) } + "]"
                is String -> item
                else -> item?.toString() ?: "?"
            }
        }
        return groups.joinToString(",")
    }

    // ----------------------------------------------------------- outcome

    data class VerifiedRequest(val json: JSONObject, val origin: String)

    sealed class Outcome {
        data class Verified(val request: VerifiedRequest) : Outcome()
        data class Refused(val reason: String) : Outcome()
    }
}
