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
 * **CLOSED 2026-09-01 (D34/D37, §6.2 item 14)**: this file used to decode a
 * compact-JWS request object WITHOUT verifying its signature (see git
 * history for the prior "ESCALATION" doc). That gap is closed — this file
 * is now transport-only (GET the bytes, POST the presentation) and does NOT
 * decode or trust anything about the request object's contents. Origin
 * consistency and JWS verification both happen in [RequestTrust] BEFORE
 * [MainActivity] ever reads a field out of a fetched request; see
 * [RequestTrust]'s class doc for the origin rule and key resolution, and
 * [fetchRequestRaw] below, which returns the raw response body untouched.
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

    /** Raw HTTP outcome of the `request_uri` GET — body untouched, not even
     * parsed as JSON/JWS. Verification ([RequestTrust.verifyRequestObject])
     * and origin binding ([RequestTrust.originOf]) happen on this raw body
     * BEFORE anything in it is trusted (D34/D37, §6.2 item 14). */
    data class RawFetch(val body: String, val httpStatus: Int)

    /** Thrown with the HTTP status attached so a caller can log it explicitly
     * — a total no-op (never fetched) must never look the same in logcat as
     * "fetched, got a 4xx/5xx". */
    class HandoffHttpException(val httpStatus: Int, message: String) : Exception(message)

    /** GET request_uri (request by reference). Transport only — see class doc. */
    fun fetchRequestRaw(requestUri: String): RawFetch {
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
        return RawFetch(body, status)
    }

    /** Builds the `zkagent/1` presentation object per D19/D27/D30/item-9.
     * D38: `data` gains `pubkey` alongside `key_id`/`sig` — see
     * [EvidenceSigner.EvidenceItem]'s doc. */
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
            data.put("pubkey", e.pubkeyBase64)
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
