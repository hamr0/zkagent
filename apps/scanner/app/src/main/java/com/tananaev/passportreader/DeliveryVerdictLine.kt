package com.tananaev.passportreader

/**
 * Findings.md #22 (opened by the VerifierRefusal fix's own author while
 * touching the exact line): the raw report/log `"verdict:"` line in both
 * `MainActivity.presentBareA` (mode A) and `MainActivity.mintAndMaybeHandoff`
 * (mode B) was HARDCODED to `"verdict: PASS (...)"` for every
 * `DeliveryResult` branch except `Rejected` (which [VerifierRefusal] already
 * corrected) — so an honest under-threshold refusal, an other-reason
 * refusal, a request with no `response_uri`, or a transport failure all
 * still printed "PASS" in the raw text even though the structured
 * `summary.result` line and `ReportLog.Outcome` glyph both already computed
 * the correct outcome for those branches independently.
 *
 * This object is the single place that turns a delivery outcome into the
 * raw report's `"verdict:"` line, for EVERY branch — same "split the
 * decision from the machinery" discipline as [VerifierRefusal]/
 * [MintOutcome]/[MintConfirmation]/[FailureTransition]. It takes primitives
 * extracted from `MainActivity`'s private `DeliveryResult`, never that type
 * itself (that type is private to `MainActivity`; every other pure decision
 * object in this package follows the same pattern for the same reason).
 *
 * [rejected] delegates to [VerifierRefusal] rather than duplicating its
 * classification — that object remains the one place a non-2xx
 * status/body pair is turned into an outcome.
 */
object DeliveryVerdictLine {

    /** HTTP 2xx and the verifier's own body did not refuse the claim (or
     * carried no verdict at all) — parameterized on what was attempted so
     * mode A and mode B keep their own distinct text ("bare presentation
     * sent" / "minted"). Owner decision, 2026-09-05 (findings.md #22's own
     * follow-up): "PASS" replaced with "DELIVERED" — the site's OWN
     * verdict on the claim's truth is never known to this app, only
     * whether the request reached it; "what they need to learn is if
     * their AV request was delivered". This was kept as a single line
     * specifically so this wording change is exactly the one-line change
     * it turned out to be. */
    fun accepted(whatWasSent: String): String = "verdict: DELIVERED ($whatWasSent)"

    /** This device's OWN claim already said `over_threshold:false` — an
     * honest, expected refusal (Q36/D66 item 3), not a plumbing failure.
     * Matches [MintOutcome.UNDER_THRESHOLD_DIALOG_MESSAGE]'s register: the
     * claim really was sent, only the site's answer to it was "no". */
    fun refusedHonestUnderThreshold(): String = "verdict: REFUSED — under threshold, nothing sent"

    /** HTTP 2xx, but the verifier's own body carried `allowed:false` for
     * some OTHER reason. [reason] is [MintOutcome.Outcome.RefusedOtherReason.reason]
     * — the verifier's own `reason` field, verbatim, when present. */
    fun refusedOtherReason(reason: String?): String =
        "verdict: REFUSED — ${reason ?: "no reason given"}, nothing sent"

    /** Non-2xx `direct_post` response — delegates to [VerifierRefusal],
     * the one place that classifies a raw status/body pair, rather than
     * re-deriving the same decision here. */
    fun rejected(httpStatus: Int, body: String): String =
        VerifierRefusal.reportLine(VerifierRefusal.classify(httpStatus, body))
            ?: "verdict: REFUSED — verifier: HTTP $httpStatus"

    /** The request object carried no top-level `response_uri` — nothing
     * ever left this device to be accepted or refused. */
    fun noResponseUri(): String = "verdict: NOT SENT — request had no response_uri"

    /** The `direct_post` call itself threw (network/other exception) —
     * [label] is the same `"${e.javaClass.simpleName}: ${e.message}"` text
     * already written to the `handoff: FAILED ...` log/report line right
     * above this one at both call sites, never re-derived. */
    fun transportFailed(label: String): String = "verdict: NOT SENT — network error: $label"
}
