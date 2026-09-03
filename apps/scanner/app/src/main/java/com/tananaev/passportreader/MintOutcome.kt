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
 * Q36/D66 item 3, FIX pass (Q36 follow-up, 2026-09-03) — classifies a
 * COMPLETED (HTTP 2xx — the caller only reaches [classify] on 2xx, see
 * [MainActivity.mintAndMaybeHandoff]'s doc) `direct_post` into what
 * actually happened, distinguishing an honest, expected refusal (this
 * device signed `over_threshold:false`) from any other kind of
 * verifier-side refusal — so the two are never both reported as "the site
 * rejected this response" plumbing failure.
 *
 * **Driven by this device's OWN computed answer, not the response body.**
 * The original D66 cut read the body's `allowed:false` as the trigger and
 * `claimedOverThreshold` only as a tie-breaker underneath it — which meant
 * an under-threshold holder against `spikes/m2-handoff` (whose
 * `direct_post` echoes only `{accepted:true}`, no `allowed` key at all —
 * [DirectPostVerdict.parse] returns `null`) always landed on
 * [AcceptedOrUnknown] and saw the generic success dialog. WRONG: this
 * device already knows, from its OWN signed claim, that the holder is
 * under threshold — it does not need the verifier to confirm that back.
 * [classify] now reads [claimedOverThreshold] FIRST: `false` is always
 * [HonestUnderThreshold], with or without a body verdict. A body verdict,
 * when present, may only make the outcome STRICTER than what
 * [claimedOverThreshold] alone would give — never override an honest
 * `false` back into a false [AcceptedOrUnknown] success. Concretely: for
 * `claimedOverThreshold == true`, an explicit body `allowed:false` still
 * demotes to [RefusedOtherReason] (some OTHER reason the verifier refused
 * a holder this device believed was over threshold); there is no body
 * value that can promote a `claimedOverThreshold == false` claim back up.
 * Extracted as a pure decision with its own unit test, the same precedent
 * as [MintGate]/[MintConfirmation]/[FailureTransition].
 */
object MintOutcome {

    sealed class Outcome {
        /** This device's own claim was `over_threshold:true`, and the body
         * carries no `allowed:false` to demote it (verdict unknown, or the
         * site accepted) — today's default HTTP-2xx-is-accepted behaviour,
         * unchanged. */
        object AcceptedOrUnknown : Outcome()

        /** This device's OWN signed claim already said
         * `over_threshold:false` — an honest, expected refusal, not a
         * plumbing failure. Driven by [classify]'s `claimedOverThreshold`
         * parameter alone; the body verdict is irrelevant to reaching this
         * case (see class doc — a body can only ever make the outcome
         * STRICTER, and there is nothing stricter than this). */
        object HonestUnderThreshold : Outcome()

        /** This device's claim said `over_threshold:true`, but the body
         * explicitly carries `allowed:false` for some OTHER reason (the
         * verifier itself could not check — `ok:false` — is folded in here
         * too, since [DirectPostVerdict]'s own §3-invariant read never
         * pairs `ok:false` with `allowed:true`). */
        data class RefusedOtherReason(val reason: String?) : Outcome()
    }

    fun classify(verdict: DirectPostVerdict?, claimedOverThreshold: Boolean): Outcome {
        // This device's own answer decides first — a body verdict can only
        // make things stricter from here (true -> RefusedOtherReason), never
        // override false back into a false AcceptedOrUnknown. See class doc.
        if (!claimedOverThreshold) return Outcome.HonestUnderThreshold
        return if (verdict?.allowed == false) Outcome.RefusedOtherReason(verdict.reason) else Outcome.AcceptedOrUnknown
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
