package com.tananaev.passportreader

import org.json.JSONObject

/**
 * Device finding (owner, 2026-09-05, logcat 00:27:40): a non-2xx
 * `direct_post` response — `"handoff: direct_post http_status=409 ->
 * {\"error\":\"already_responded\"}"` — was reported `verdict: PASS (bare
 * presentation sent)`. Root cause: both `MainActivity.presentBareA` (mode A)
 * and `MainActivity.mintAndMaybeHandoff` (mode B) appended a HARDCODED
 * `"\nverdict: PASS (...)"` line to the raw report text unconditionally,
 * after the `try` block that classifies delivery into `DeliveryResult` —
 * the classification already existed and was already correct for the
 * PASS/FAIL glyph (`ReportLog.Outcome`) and the structured `summary` block's
 * `result` line, but the raw report/log TEXT and the D43 terminal dialog
 * both still said "sent" regardless of what the verifier actually did with
 * it. `DeliveryResult.Rejected` (the non-2xx branch) additionally had NO
 * blocking dialog at all — a refusal was silent.
 *
 * [classify] is the extracted, pure status/body -> outcome decision for a
 * NON-2xx `direct_post` response specifically — same pattern as
 * [MintOutcome]/[FailureTransition]/every other pure decision object in
 * this package. `MainActivity`'s `DeliveryResult.Rejected` branch (both
 * mode-A and mode-B call sites) is its only real caller. A 2xx response
 * never reaches this function in practice — that case is already
 * classified by [DirectPostVerdict]/[MintOutcome] via the verifier's OWN
 * `{ok, allowed, reason}` body, which this class does not duplicate.
 * [Outcome.Sent] exists purely so this class's own truth table is complete
 * and testable end to end (owner's own spec: "2xx -> sent/pass"), not
 * because any call site feeds it a 2xx status today.
 */
object VerifierRefusal {

    sealed class Outcome {
        /** 2xx — the verifier received it. Not this class's real job (see
         * class doc) — [reportLine]/[dialogMessage] both return `null` for
         * it, since neither call site asks this class anything for a 2xx
         * status. */
        object Sent : Outcome()

        /** HTTP 409 with a body carrying `{"error":"already_responded"}` —
         * the SAME `response_uri` was already POSTed to once before (a
         * stale/reused link, a double-tap, or a retry after an earlier
         * Rejected outcome). Distinct from a generic refusal: this is not
         * "the verifier said no to this claim," it is "this exact
         * presentation opportunity is spent" — so it gets its own plain
         * wording, matching `MainActivity.SESSION_EXPIRED_MESSAGE`'s
         * register (same shape of problem: a one-shot link reused). */
        object AlreadyUsed : Outcome()

        /** Any other non-2xx. [label] is the body's top-level `error`
         * field when present and a non-empty string, else `"HTTP
         * <status>"` — never a guess, never the raw body dumped verbatim
         * into a user-facing message. */
        data class Refused(val label: String) : Outcome()
    }

    /**
     * @param httpStatus the raw `direct_post` HTTP status
     *   ([HandoffClient.DirectPostResult.httpStatus]).
     * @param body the raw response body
     *   ([HandoffClient.DirectPostResult.body]) — parsed ONLY for a
     *   top-level string `error` field via [errorFieldOf]; never trusted
     *   beyond that one field, never re-interpreted as a `{ok, allowed,
     *   reason}` verdict (that stays [DirectPostVerdict]'s job, for the
     *   2xx case only).
     */
    fun classify(httpStatus: Int, body: String): Outcome {
        if (httpStatus in 200..299) return Outcome.Sent
        val error = errorFieldOf(body)
        return if (httpStatus == 409 && error == "already_responded") {
            Outcome.AlreadyUsed
        } else {
            Outcome.Refused(error ?: "HTTP $httpStatus")
        }
    }

    /** Extracts a top-level string `error` field from [body], or `null` for
     * anything else — non-JSON body, JSON without that key, a non-string/
     * empty value. Never throws, same discipline as
     * [DirectPostVerdict.parse]. */
    fun errorFieldOf(body: String): String? = try {
        JSONObject(body).optString("error", "").ifEmpty { null }
    } catch (e: Exception) {
        null
    }

    /** The raw report/log "verdict:" line this finding's fix replaces the
     * hardcoded PASS text with, for a NON-2xx delivery. `null` for
     * [Outcome.Sent] — no call site asks this class for one; the existing
     * "verdict: PASS (...)" text is unchanged for an actually-2xx
     * delivery. */
    fun reportLine(outcome: Outcome): String? = when (outcome) {
        Outcome.Sent -> null
        Outcome.AlreadyUsed -> "verdict: REFUSED — verifier: link already used (HTTP 409, already_responded)"
        is Outcome.Refused -> "verdict: REFUSED — verifier: ${outcome.label}"
    }

    /** The D43 blocking terminal-outcome dialog's message for a NON-2xx
     * delivery — previously no dialog fired at all for this case. `null`
     * for [Outcome.Sent]: this class adds no dialog for an actual 2xx
     * response; the existing Accepted/RefusedHonestUnderThreshold dialog
     * branches are untouched by this fix. */
    fun dialogMessage(outcome: Outcome): String? = when (outcome) {
        Outcome.Sent -> null
        Outcome.AlreadyUsed -> ALREADY_USED_MESSAGE
        is Outcome.Refused -> "Verifier refused: ${outcome.label}"
    }

    /** Same register as `MainActivity.SESSION_EXPIRED_MESSAGE`
     * ("Verification session expired — reopen the link from the site."),
     * owner-specified verbatim. */
    const val ALREADY_USED_MESSAGE = "This link was already used — reopen the link from the site."
}
