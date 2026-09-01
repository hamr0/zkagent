package com.tananaev.passportreader

import android.net.Uri
import android.util.Base64
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

/**
 * §6.2 item 8 — av:// app-link + direct_post handoff, primary path.
 * Mirrors the shape captured in `docs/logs/M2-CAPTURE.md` Finding 1 and
 * exercised by `spikes/m2-handoff` (`server.mjs` / `scripts/fake-wallet.mjs`):
 *
 *   app-link carries request_uri  ->  GET request_uri (request by reference)
 *   ->  build the zkagent/1 presentation  ->  POST response_uri (direct_post,
 *   form-encoded: state + vp_token, vp_token = base64url(JSON))
 *
 * **ESCALATION**: the reference verifier signs its request objects (JAR,
 * RFC 9101, ES256) and the fake wallet verifies that signature against a
 * PINNED request-signer key before trusting the challenge inside (D20:
 * unsigned challenges are only accepted at tiers A/B, and even then the
 * nonce HMAC seals the fields). This client does NOT pin a request-signer
 * key — there is no owner-approved key/config surface for
 * `trustedChallengeIssuers` in this build (D20 names the shape;
 * §6.2 does not specify where an app build gets one). It reads the request
 * body as-is: if it looks like a compact JWS (three base64url segments), it
 * decodes the payload WITHOUT verifying the signature and logs a loud
 * warning; if it is plain JSON, it uses it directly (D20's explicit
 * allowance for unsigned tier-A/B challenges). This is a real gap flagged
 * for the owner, not a silent downgrade — see README.md / the conformance
 * report.
 */
object HandoffClient {

    private const val TAG = "HandoffClient"

    /** Captured ONCE, immutably, at the moment the av:// intent or a scanned/
     * pasted QR payload is received — never re-derived from a mutable UI
     * control later (same discipline as §6.2 item 4's mode capture). */
    data class PendingHandoff(val clientId: String?, val requestUri: String)

    /** Parses an `av://authorize?client_id=...&request_uri=...` (or an https
     * app-link using the same query shape) Uri into a [PendingHandoff], or
     * null if it isn't one. */
    fun parseAvLink(uri: Uri): PendingHandoff? {
        if (uri.scheme != "av" && !(uri.scheme == "https")) return null
        if (uri.scheme == "av" && uri.host != "authorize") return null
        val requestUri = uri.getQueryParameter("request_uri") ?: run {
            // 2026-09-01 bug: an `adb shell am start` with an unquoted `&`
            // truncated the intent's query string before this ever ran, and
            // the app said nothing — this line is the fix for that silence.
            Log.i(TAG, "parseAvLink: dropped — intent uri has no (or a malformed) request_uri query param: $uri")
            return null
        }
        return PendingHandoff(clientId = uri.getQueryParameter("client_id"), requestUri = requestUri)
    }

    /** Accepts a raw pasted/scanned string that is either a full av:// link
     * or a bare request_uri (https URL) — the manual-fallback / QR-decode
     * entry point. */
    fun parsePastedText(text: String): PendingHandoff? {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return null
        return try {
            val uri = Uri.parse(trimmed)
            if (uri.scheme == "av" || uri.scheme == "https") {
                parseAvLink(uri) ?: if (uri.scheme == "https") PendingHandoff(null, trimmed) else null
            } else null
        } catch (e: Exception) {
            null
        }
    }

    data class FetchedRequest(val json: JSONObject, val wasSigned: Boolean, val signatureVerified: Boolean, val httpStatus: Int)

    /** Thrown with the HTTP status attached so a caller can log it explicitly
     * — a total no-op (never fetched) must never look the same in logcat as
     * "fetched, got a 4xx/5xx". */
    class HandoffHttpException(val httpStatus: Int, message: String) : Exception(message)

    /** GET request_uri (request by reference). See class doc ESCALATION —
     * a JWS body is decoded but its signature is NOT verified in this build. */
    fun fetchRequest(requestUri: String): FetchedRequest {
        val conn = (URL(requestUri).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10_000
            readTimeout = 10_000
        }
        val status = conn.responseCode
        if (status !in 200..299) {
            val errBody = runCatching { conn.errorStream?.bufferedReader(StandardCharsets.UTF_8)?.use { it.readText() } }.getOrNull()
            throw HandoffHttpException(status, "GET $requestUri -> HTTP $status${if (errBody != null) ": $errBody" else ""}")
        }
        val body = conn.inputStream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
        val parts = body.trim().split(".")
        return if (parts.size == 3 && body.trim().startsWith("ey")) {
            Log.w(TAG, "request object is a compact JWS — signature NOT verified (no pinned request-signer key in this build, see HandoffClient class doc ESCALATION)")
            val payload = String(Base64.decode(parts[1], Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP), StandardCharsets.UTF_8)
            FetchedRequest(JSONObject(payload), wasSigned = true, signatureVerified = false, httpStatus = status)
        } else {
            FetchedRequest(JSONObject(body), wasSigned = false, signatureVerified = false, httpStatus = status)
        }
    }

    /** Builds the `zkagent/1` presentation object per D19/D27/D30/item-9. */
    fun buildPresentation(
        tier: String,
        claim: Map<String, Any?>,
        challenge: JSONObject,
        zktag: String?,
        evidence: List<EvidenceSigner.EvidenceItem>,
    ): JSONObject {
        val obj = JSONObject()
        obj.put("spec", "zkagent/1")
        obj.put("tier", tier)
        val claimObj = JSONObject()
        for ((k, v) in claim) claimObj.put(k, v)
        obj.put("claim", claimObj)
        obj.put("challenge", challenge)
        if (zktag != null) obj.put("zktag", zktag)
        val evArr = JSONArray()
        for (e in evidence) {
            val item = JSONObject()
            item.put("type", e.type)
            item.put("version", e.version)
            val data = JSONObject()
            data.put("key_id", e.keyId)
            data.put("sig", e.sigBase64)
            item.put("data", data)
            evArr.put(item)
        }
        obj.put("evidence", evArr)
        return obj
    }

    /** Raw HTTP outcome of the `direct_post` — status ALWAYS carried alongside
     * the body so a caller can log/render "HTTP 200, body: {...}" and never
     * conflate a 4xx/5xx verdict-shaped error body with a real verdict. */
    data class DirectPostResult(val httpStatus: Int, val body: String)

    /** POST response_uri, form-encoded per OpenID4VP `direct_post`. Returns
     * the raw HTTP status + response body (verdict JSON, `{ok, allowed,
     * reason}` shaped — PRD §3 invariant; this client does not interpret it,
     * just relays it to the caller for display/logging). */
    fun postDirectPost(responseUri: String, state: String?, presentation: JSONObject): DirectPostResult {
        val vpToken = Base64.encodeToString(
            presentation.toString().toByteArray(StandardCharsets.UTF_8),
            Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP,
        )
        val form = buildString {
            if (state != null) {
                append("state=").append(Uri.encode(state)).append("&")
            }
            append("vp_token=").append(Uri.encode(vpToken))
        }
        val conn = (URL(responseUri).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 10_000
            readTimeout = 10_000
            setRequestProperty("content-type", "application/x-www-form-urlencoded")
        }
        conn.outputStream.use { it.write(form.toByteArray(StandardCharsets.UTF_8)) }
        val status = conn.responseCode
        val stream = if (status in 200..299) conn.inputStream else conn.errorStream
        val body = stream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
        return DirectPostResult(status, body)
    }
}
