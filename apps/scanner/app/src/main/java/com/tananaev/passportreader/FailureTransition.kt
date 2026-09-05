package com.tananaev.passportreader

import net.sf.scuba.smartcards.CardServiceException

/**
 * §6.2 item 15 (D43)'s state-transition rule, extracted to a pure function
 * the same way [MintGate] extracted item 3's mint gate — so the mapping has
 * a unit test independent of the Android dialog/UI plumbing around it, and
 * [MainActivity] has exactly one place that computes it.
 *
 * THREE buckets (2026-09 real-device fix — a hand tremor should not cost a
 * full retype):
 *  1. **Access-establishment failure** — a genuine access DENIAL: an
 *     [org.jmrtd.AccessDeniedException] (the real-device case: "Mutual
 *     authentication failed", the documented `SW 0x6300`->`0x6985`
 *     condition — this IS the exception JMRTD raises for a rejected
 *     PACE/BAC key) anywhere in the cause chain — keeps MRZ+mode, so a
 *     mistyped key is a retry, not a full re-lock+retype (F3).
 *  2. **Transient chip-communication failure** — the document moved or the
 *     NFC link dropped mid-read (the real-device case:
 *     `CardServiceException("Tag was lost")` surfacing wrapped in
 *     `IOException("Unexpected exception")` from JMRTD's `readBinary`).
 *     The entered MRZ details were CORRECT — only the physical connection
 *     broke — so this bucket keeps MRZ+mode too, for a DIFFERENT reason
 *     than bucket 1: the user holds the document still and taps again, no
 *     re-entry.
 *  3. **Everything else (including an unrecognised exception)** — resets.
 *
 * **Classification is by EXCEPTION EVIDENCE, never by which code path was
 * executing** (2026-09, second real-device fix — a real bug: the access-
 * establishment code path used to catch ANY exception unconditionally and
 * label it bucket 1, so a tag-loss occurring DURING PACE/BAC — before
 * access ever completed — was misreported as "check your details" when
 * the true cause was a card slip. [org.jmrtd.AccessDeniedException] IS-A
 * [CardServiceException] in the JMRTD/scuba class hierarchy, so the two
 * classifiers can both be evaluated against the SAME exception regardless
 * of where in the read it was thrown; [classify] is the single place that
 * resolves which bucket wins.
 *
 * **Precedence, explicit and single-sourced here (2026-09):** transient
 * wins over access-establishment when both could match the same
 * exception. In practice the two real-device markers ("tag was lost" vs.
 * "mutual authentication failed") never both match one message, but the
 * ordering itself — not an incidental non-overlap — is what [classify]
 * guarantees, and [FailureTransitionTest] pins it directly rather than
 * relying on the messages never colliding.
 */
object FailureTransition {
    /** The outcome of classifying one failure exception — see the class
     * doc for what each bucket means and the state-transition it implies. */
    enum class Classification { TRANSIENT_CHIP_COMMUNICATION, ACCESS_ESTABLISHMENT, UNCLASSIFIED }

    /** The ONE place a failure exception is resolved to a bucket.
     * TRANSIENT is checked FIRST — see the class doc's precedence note —
     * then ACCESS_ESTABLISHMENT; anything matching neither is
     * [Classification.UNCLASSIFIED] (bucket 3, reset), never a guess
     * toward either "keep" bucket. */
    fun classify(throwable: Throwable?): Classification = when {
        isTransientChipCommunicationFailure(throwable) -> Classification.TRANSIENT_CHIP_COMMUNICATION
        isAccessEstablishmentFailure(throwable) -> Classification.ACCESS_ESTABLISHMENT
        else -> Classification.UNCLASSIFIED
    }

    /** Whether [classification] keeps MRZ+mode (buckets 1 and 2) or resets
     * (bucket 3, [Classification.UNCLASSIFIED]) — state transitions are
     * UNCHANGED by the 2026-09 classification-order fix: both "keep"
     * buckets still behave identically here, only WHICH bucket a given
     * exception lands in (and therefore which message the user sees)
     * changed. */
    fun keepsMrzAndMode(classification: Classification): Boolean = classification != Classification.UNCLASSIFIED

    /** Legacy two-boolean form, kept for the many call sites (mint-path
     * refusals, session-expiry, tier refusals) that already know their own
     * bucket without ever running [classify] — they are not read-failure
     * exceptions being classified, they are refusals that always resolve
     * to bucket 1 or bucket 3 directly. Equivalent to
     * `keepsMrzAndMode(classify(...))` for a real classified exception,
     * never called with both true from [classify]'s own output (the two
     * booleans there are mutually exclusive by construction). */
    fun keepsMrzAndMode(isAccessEstablishmentFailure: Boolean, isTransientChipCommunicationFailure: Boolean): Boolean =
        isAccessEstablishmentFailure || isTransientChipCommunicationFailure

    /**
     * Owner device fix (2026-09-05, "wrong details entry still doesn't
     * reset to re-enter"): [keepsMrzAndMode] alone used to ALSO govern
     * whether `MainActivity.lockedMode` (and therefore the Scan/Verify
     * button's enabled state, [SessionDisplay.render]'s `locked` branch)
     * stayed set across a kept retry. That was correct for bucket 2
     * (transient chip-communication failure): the typed details were
     * RIGHT, only the physical NFC link dropped, so the user just holds
     * the document against the phone again with no field edit and no
     * button re-press — the session must stay armed exactly as it was.
     * It was WRONG for bucket 1 (access-establishment failure): the BAC/
     * PACE key DERIVED FROM THE CURRENT FIELDS was rejected, so holding
     * the document again with the SAME fields re-derives the SAME wrong
     * key and fails identically. The owner's ask is to let the user edit
     * the field(s) then tap Scan/Verify again — which requires
     * `lockedMode` to actually clear so a fresh `lockModeAndArm()` call
     * does not immediately no-op on its `if (lockedMode != null) return`
     * guard. This is DELIBERATELY decoupled from [keepsMrzAndMode]: bucket
     * 1 now keeps the typed MRZ text (unchanged — a mistyped key is a
     * correction, not a full retype) while releasing the lock (changed);
     * bucket 2 keeps both (unchanged); bucket 3 keeps neither (unchanged).
     * This function governs ONLY the lock/arm state — it has no opinion on
     * `pendingHandoff`/`verifiedRequest`/`authorizedHandoff`, which
     * [keepsMrzAndMode] (via `MainActivity.showBlockingOutcomeDialog`'s OK
     * handler) still independently decides whether to null — a released
     * lock does not have to mean a lost verified handoff: a fresh
     * `lockModeAndArm()` call re-derives the SAME pending/verified handoff
     * (still not nulled) into a new lock, never a re-fetch. */
    fun keepsLockedMode(classification: Classification): Boolean = classification == Classification.TRANSIENT_CHIP_COMMUNICATION

    /** Legacy two-boolean form of [keepsLockedMode], mirroring
     * [keepsMrzAndMode]'s own two-boolean overload for the same reason: a
     * call site that already knows its own bucket without ever running
     * [classify]. Only [isTransientChipCommunicationFailure] keeps the
     * lock — an access-establishment failure (or any other reset-shaped
     * refusal) always releases it, per this function's own doc above. */
    fun keepsLockedMode(isAccessEstablishmentFailure: Boolean, isTransientChipCommunicationFailure: Boolean): Boolean =
        isTransientChipCommunicationFailure

    /**
     * Walks [throwable]'s cause chain looking for a [CardServiceException]
     * whose message indicates the tag/connection was lost mid-read.
     *
     * CONSERVATIVE by design: a wrong "keep" is worse than a wrong "reset"
     * — it would leave stale document-adjacent state on screen the user
     * did not expect a retry to preserve. So this function only returns
     * true for the ONE evidenced condition: a [CardServiceException]
     * anywhere in the cause chain whose message contains "tag was lost"
     * (case-insensitive — the exact, real-device-observed string is
     * `"Tag was lost"`). Nothing else is classified as transient; anything
     * this function cannot confidently recognise falls through to false
     * (the existing reset bucket), never a guess toward "keep". UNCHANGED
     * by the 2026-09 ordering fix — still exactly this, just no longer
     * gated behind `!accessFailure` at the call site.
     */
    fun isTransientChipCommunicationFailure(throwable: Throwable?): Boolean {
        var cause: Throwable? = throwable
        var depth = 0
        while (cause != null && depth < MAX_CAUSE_CHAIN_DEPTH) {
            if (cause is CardServiceException && (cause.message ?: "").contains(TAG_LOST_MARKER, ignoreCase = true)) {
                return true
            }
            cause = cause.cause
            depth++
        }
        return false
    }

    /**
     * Walks [throwable]'s cause chain looking for an
     * [org.jmrtd.AccessDeniedException] — the exception JMRTD actually
     * raises for a rejected PACE or BAC key (the documented
     * `SW 0x6300`->`0x6985` condition; the real-device case: "Mutual
     * authentication failed"). 2026-09: this REPLACES the old
     * "any exception during the access-establishment code path" rule —
     * that rule mislabelled a mid-PACE/BAC tag-loss as an access failure.
     * Same conservative discipline as [isTransientChipCommunicationFailure]:
     * anything this function cannot confidently recognise as a genuine
     * denial falls through to false (bucket 3, reset), never a guess
     * toward "keep".
     */
    fun isAccessEstablishmentFailure(throwable: Throwable?): Boolean {
        var cause: Throwable? = throwable
        var depth = 0
        while (cause != null && depth < MAX_CAUSE_CHAIN_DEPTH) {
            if (cause is org.jmrtd.AccessDeniedException) return true
            cause = cause.cause
            depth++
        }
        return false
    }

    // The one real-device-observed marker (2026-09 Pixel 6a run) — not a
    // speculative expansion to cover other wordings ("connection lost",
    // "link dropped", etc.) that have not actually been seen.
    private const val TAG_LOST_MARKER = "tag was lost"

    // Guards against a pathological/cyclic cause chain; JMRTD's real wrap
    // depth here is 1-2.
    private const val MAX_CAUSE_CHAIN_DEPTH = 12
}
