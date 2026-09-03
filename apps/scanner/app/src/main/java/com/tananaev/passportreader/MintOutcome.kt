package com.tananaev.passportreader

import org.json.JSONObject

/**
 * The `{ok, allowed, reason}` shape a `direct_post` response body MAY carry
 * per the PRD §3 invariant (see [HandoffClient.postDirectPost]'s doc) —
 * best-effort, value-free parsing. Returns `null` when the body doesn't
 * carry the invariant shape at all (e.g. `spikes/m2-handoff/server.mjs`
 * today only echoes `{accepted:true}` — the real verdict lives behind a
 * separate poll endpoint that endpoint, not this app, reads), so a caller
 * MUST treat `null` the same as "unknown", never as a refusal.
 *
 * §3 invariant preserved on read, not just on write: `ok == false` is never
 * paired with a non-null `allowed` (an unreadable verdict is `allowed:null`,
 * never `allowed:false`) — this parser reads `allowed` as given rather than
 * re-deriving it, so an invariant-violating body (should never happen from
 * a correct verifier) surfaces AS GIVEN rather than being silently
 * corrected.
 */
data class DirectPostVerdict(val ok: Boolean?, val allowed: Boolean?, val reason: String?) {
    companion object {
        fun parse(body: String): DirectPostVerdict? =
            try {
                val obj = JSONObject(body)
                if (!obj.has("allowed")) {
                    null
                } else {
                    DirectPostVerdict(
                        ok = if (obj.has("ok") && !obj.isNull("ok")) obj.getBoolean("ok") else null,
                        allowed = if (obj.isNull("allowed")) null else obj.getBoolean("allowed"),
                        reason = if (obj.has("reason") && !obj.isNull("reason")) obj.getString("reason") else null,
                    )
                }
            } catch (e: Exception) {
                null
            }
    }
}

/**
 * Q36/D66 item 3 — classifies a COMPLETED (HTTP 2xx) `direct_post` into
 * what actually happened, distinguishing an honest, expected refusal (this
 * device signed `over_threshold:false`, and the verifier's own
 * `allowed:false` agrees) from any other kind of verifier-side refusal —
 * so the two are never both reported as "the site rejected this response"
 * plumbing failure. Extracted as a pure decision with its own unit test,
 * the same precedent as [MintGate]/[MintConfirmation]/[FailureTransition]:
 * neither [ReportLog], [SessionDisplay], nor [MintConfirmation] previously
 * owned this specific classification or its wording, so it gets its own
 * narrowly-scoped object rather than being bolted onto one of them.
 */
object MintOutcome {

    sealed class Outcome {
        /** No `allowed:false` signal in the body (verdict unknown, or the
         * site accepted) — today's default HTTP-2xx-is-accepted behaviour,
         * unchanged. */
        object AcceptedOrUnknown : Outcome()

        /** The verifier said `allowed:false`, and this device's OWN signed
         * claim already said `over_threshold:false` — an honest, expected
         * refusal, not a plumbing failure. */
        object HonestUnderThreshold : Outcome()

        /** The verifier said `allowed:false` for some other reason — this
         * device's claim said `over_threshold:true`, or the verifier
         * itself could not check (`ok:false`, `allowed:null` is NOT this
         * case — see [classify]). */
        data class RefusedOtherReason(val reason: String?) : Outcome()
    }

    fun classify(verdict: DirectPostVerdict?, claimedOverThreshold: Boolean): Outcome {
        if (verdict?.allowed != false) return Outcome.AcceptedOrUnknown
        return if (!claimedOverThreshold) Outcome.HonestUnderThreshold else Outcome.RefusedOtherReason(verdict.reason)
    }

    /** Plain-language, value-free — no numbers about the person; a
     * threshold value would be fine but isn't needed for this wording. */
    const val UNDER_THRESHOLD_DIALOG_MESSAGE = "The site's age threshold was not met."

    /** The SAME string both the blocking dialog (via
     * [UNDER_THRESHOLD_DIALOG_MESSAGE], worded slightly differently for its
     * own context) and the [ReportLog] entry's plain-language `result`
     * field are built from — see [MainActivity.mintAndMaybeHandoff]'s D66
     * block. Returns `null` for [Outcome.AcceptedOrUnknown] since that case
     * keeps its existing, unchanged report text. */
    fun reportResult(outcome: Outcome): String? = when (outcome) {
        Outcome.AcceptedOrUnknown -> null
        Outcome.HonestUnderThreshold -> "Verified — the site's age threshold was not met"
        is Outcome.RefusedOtherReason -> "Signed OK, but the site refused the response" +
            (outcome.reason?.let { " ($it)" } ?: "")
    }
}
