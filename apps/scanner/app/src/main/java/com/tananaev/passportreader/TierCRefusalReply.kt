package com.tananaev.passportreader

import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * D110 (2026-10-26, owner) — the ONE generic refusal a tier-C refusal sends the site, as pure, unit-tested logic.
 *
 * **What is sent.** The standard OpenID4VP error response over the same `direct_post` channel the answer would have
 * used: a form body of exactly `error=access_denied`, plus `state` when the request carried one. The body is the same
 * for EVERY refusal reason: [formBody] takes no reason, so a reason cannot reach the wire (D92, D109(g)5: the reasons
 * show in the app only).
 *
 * **When it is sent.** Only for a request that has passed the app's own gates, and never before them: [plan] needs a
 * [RequestTrust.VerifiedRequest] (the request object's JWS verified, D34/D37; there is no way to build one from an
 * unverified body), the operator switch ON, and the request's host on the build's `tier_c_verifiers` pin list. The
 * holder's "Allow additional verifications" switch is deliberately NOT an input: OFF passes these gates, so the
 * site is told "refused" (not why). Tier A and B refusals never come here.
 *
 * **Failure.** [send] never throws and never retries: one attempt, the status or the exception class is returned for
 * the caller to log, and the holder sees the same in-app refusal either way. A `409 already_responded` is just a status.
 */
object TierCRefusalReply {

    /** Per-phase (connect, read) timeout of the refusal post the dialog waits for: worst case about twice this, so the
     * holder is never left waiting more than ~6 s. One attempt, never retried (D110 4c). */
    const val DIALOG_TIMEOUT_MS = 3_000

    /** The only error code ever sent. */
    const val ERROR = "access_denied"

    /** Where to post: the verified request's own `response_uri` (same origin as the request, checked by
     * [RequestTrust.verifyHandoff]) and its `state`, if any. */
    data class Plan(val responseUri: String, val state: String?)

    /**
     * The refusal to send, or null when none may be sent. Null for: no verified request, the operator switch off, a
     * host that is not on [pinned] (exact hostname), a request without a `response_uri`, or a `response_uri` whose
     * origin is not the verified request's origin (defence in depth: [RequestTrust] already refuses that).
     */
    fun plan(verified: RequestTrust.VerifiedRequest?, operatorEnabled: Boolean, pinned: Set<String>): Plan? {
        if (verified == null) return null
        if (!operatorEnabled) return null
        val host = ThresholdPolicy.hostnameOf(verified.origin) ?: return null
        if (!OperatorPolicy.isVerifierAllowed(host, pinned)) return null
        val responseUri = verified.json.optString("response_uri", "").ifEmpty { null } ?: return null
        if (RequestTrust.originOf(responseUri) != verified.origin) return null
        val state = if (verified.json.has("state")) verified.json.optString("state", "").ifEmpty { null } else null
        return Plan(responseUri, state)
    }

    /** The exact form body: `error=access_denied`, plus `&state=<url-encoded>` when there is a state. Nothing else. */
    fun formBody(state: String?): String =
        if (state == null) "error=$ERROR" else "error=$ERROR&state=${URLEncoder.encode(state, "UTF-8")}"

    sealed class Outcome {
        /** The site's server answered; [httpStatus] is logged, never interpreted (409 and 5xx change nothing). */
        data class Sent(val httpStatus: Int) : Outcome()
        /** No answer (no network, timeout, ...): [label] is the exception class only. Never retried. */
        data class Failed(val label: String) : Outcome()
    }

    /** One POST attempt through [transport] (default: a plain HTTP form post). Never throws, never retries. */
    fun send(plan: Plan, transport: (String, String) -> Int = ::postForm): Outcome =
        try {
            Outcome.Sent(transport(plan.responseUri, formBody(plan.state)))
        } catch (e: Exception) {
            Outcome.Failed(e.javaClass.simpleName)
        }

    /** Plain `application/x-www-form-urlencoded` POST; returns the status and drains the body. Pure JVM, so the unit
     * tests run it against a real local HTTP server. [timeoutMs] applies to connect and to read (default 10 s, as the answer path; the refusal dialog passes [DIALOG_TIMEOUT_MS]). */
    fun postForm(uri: String, body: String, timeoutMs: Int = 10_000): Int {
        val conn = (URL(uri).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            instanceFollowRedirects = false // finding #52: a 3xx is a status, not a reason to re-send as a body-less GET
            connectTimeout = timeoutMs
            readTimeout = timeoutMs
            setRequestProperty("content-type", "application/x-www-form-urlencoded")
        }
        try {
            conn.outputStream.use { it.write(body.toByteArray(StandardCharsets.UTF_8)) }
            val status = conn.responseCode
            (if (status < 400) conn.inputStream else conn.errorStream)?.use { it.readBytes() }
            return status
        } finally {
            conn.disconnect()
        }
    }
}
