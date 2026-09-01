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
 *  1. **Access-establishment failure** (PACE/BAC `SW 0x6300`->`0x6985`, F3)
 *     — keeps MRZ+mode.
 *  2. **Transient chip-communication failure** — the document moved or the
 *     NFC link dropped mid-read, AFTER access was already established (the
 *     real-device case: `CardServiceException("Tag was lost")` surfacing
 *     wrapped in `IOException("Unexpected exception")` from JMRTD's
 *     `readBinary`). The entered MRZ details were CORRECT — only the
 *     physical connection broke — so this bucket keeps MRZ+mode too, for
 *     the same reason as bucket 1: the user holds the document still and
 *     taps again, no re-entry.
 *  3. **Everything else** — resets.
 *
 * [keepsMrzAndMode] is the single place that decides which bucket a
 * failure falls into; [isTransientChipCommunicationFailure] is the single,
 * CONSERVATIVE classifier that feeds bucket 2 — see its own doc for why a
 * wrong "keep" is worse than a wrong "reset".
 */
object FailureTransition {
    fun keepsMrzAndMode(isAccessEstablishmentFailure: Boolean, isTransientChipCommunicationFailure: Boolean): Boolean =
        isAccessEstablishmentFailure || isTransientChipCommunicationFailure

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
     * (the existing reset bucket), never a guess toward "keep".
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

    // The one real-device-observed marker (2026-09 Pixel 6a run) — not a
    // speculative expansion to cover other wordings ("connection lost",
    // "link dropped", etc.) that have not actually been seen.
    private const val TAG_LOST_MARKER = "tag was lost"

    // Guards against a pathological/cyclic cause chain; JMRTD's real wrap
    // depth here is 1-2.
    private const val MAX_CAUSE_CHAIN_DEPTH = 12
}
