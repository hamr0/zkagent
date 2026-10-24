package com.tananaev.passportreader

/**
 * Finding #23 (D87) — the pure accept/reject decision for an inbound
 * `av://` (or https app-link) VIEW intent, extracted from
 * [HandoffClient.parseAvLink] so it is JVM-unit-testable (no `android.*`
 * types; this module's unit tests run under `isReturnDefaultValues=true`
 * with no Robolectric, where `Uri` is a non-functional stub).
 *
 * SCOPE, stated plainly: the RULES live here; the URI PARSING (scheme, host,
 * percent/plus-decoding of query values, opaque-URI handling) stays in
 * `android.net.Uri`, reached through the lookups below. It is deliberately
 * NOT reimplemented in pure code: `Uri`'s lenient parser could not be
 * reproduced bit-for-bit here, and an approximation could silently change what
 * the app accepts. The host and query lookups are lazy lambdas so evaluation
 * order and short-circuiting are exactly the pre-extraction behaviour (e.g.
 * `Uri.host` is only read when the scheme is `av`, `getQueryParameter` only
 * after the scheme/host checks pass).
 */
object AvLinkRules {

    sealed class Decision {
        data class Accepted(val clientId: String?, val requestUri: String) : Decision()
        data class Malformed(val reason: Reason) : Decision()
    }

    enum class Reason { WRONG_SCHEME, WRONG_HOST, MISSING_REQUEST_URI }

    /**
     * @param scheme the link's scheme, as `Uri.getScheme()` returns it
     * @param host lazily reads the link's host (`Uri.getHost()`); only invoked for scheme `av`
     * @param queryParam lazily reads one decoded query parameter (`Uri.getQueryParameter`)
     */
    fun decide(scheme: String?, host: () -> String?, queryParam: (String) -> String?): Decision {
        if (scheme != "av" && scheme != "https") return Decision.Malformed(Reason.WRONG_SCHEME)
        if (scheme == "av" && host() != "authorize") return Decision.Malformed(Reason.WRONG_HOST)
        val requestUri = queryParam("request_uri") ?: return Decision.Malformed(Reason.MISSING_REQUEST_URI)
        return Decision.Accepted(clientId = queryParam("client_id"), requestUri = requestUri)
    }
}
