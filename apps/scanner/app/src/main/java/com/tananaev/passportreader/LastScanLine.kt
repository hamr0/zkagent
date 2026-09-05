package com.tananaev.passportreader

import org.json.JSONObject

/**
 * FIX (owner, 2026-09-05, verbatim): "remove it from scan, you can keep
 * summary of last scan below scan button to know, just the last one. last
 * scan: state.gov, > 18: true + checkmark".
 *
 * Owner refinement (2026-09-05, same day, approved):
 * 1. **Wording** — a bare "✓" read as "the site approved you", which this
 *    app can never actually know (the two-answers rule: the site's own
 *    verdict shows on the site's own page, never here — see the customer
 *    guide's "Two answers, two places" section). [Reason.word] now always
 *    pairs the glyph with a one-word reason — "delivered"/"refused"/"not
 *    sent"/"read failed"/"chip check passed"/"chip check failed" — so the
 *    checkmark can only ever mean "this left the device and the site
 *    accepted the presentation", never "you passed".
 * 2. **Staleness** — this line persists across restarts (same file as the
 *    Log tab), so a user opening a NEW site's link could still see an OLD
 *    scan's checkmark and mistake it for this session's own result. [isStale]
 *    is the pure guard: [ReportLog] stamps every "Last scan" entry with the
 *    `handoffGeneration` counter in effect when it was produced, and bumps
 *    that counter each time a NEW av:// intent's handoff is captured (see
 *    `MainActivity.handleIncomingIntent`'s own "pendingHandoff captured from
 *    av:// intent" log line) — a plain monotonic counter, never a
 *    timestamp. An entry whose stamped generation is behind the CURRENT
 *    counter is stale and renders as [NONE_TEXT] until this session
 *    produces its own SCAN-channel entry (which stamps the then-current
 *    generation, so it is never stale against itself).
 *
 * Pure decision + (de)serialization for the ONE-LINE "Last scan: ..."
 * summary shown directly under the Scan/Verify button, replacing the full
 * `report_view` block that used to live there — the Log and Diagnostics
 * tabs remain the only full-detail readers (see [ReportLog]'s class doc).
 *
 * [Entry] is the value-free per-scan fact this line needs: a number (the
 * age threshold this device checked against) and a boolean (its own
 * `over_threshold` answer), plus [Reason] — never MRZ/PII (D46), same
 * value-free discipline as [ReportLog.DisclosureSummary].
 *
 * `MainActivity` is the only place an [Entry] is ever built — at each
 * `emitReport` call site that has better data than [ReportLog.append]'s own
 * generic default ([Reason.REFUSED], used by every SCAN-channel call site
 * that does not pass one explicitly — every one of those is, in fact, some
 * kind of refusal before any read/mint could complete). This object only
 * decides how to RENDER whatever it is given, and how to (de)serialize it
 * for [ReportLogStore]/`MainActivity.onSaveInstanceState` — same "pure
 * logic in a pure class" split [ReportLog]/[ReportLogStore] themselves
 * already use.
 */
object LastScanLine {

    /** The one-word reason paired with [threshold]/[overThreshold] (or
     * alone, when neither is set — see [render]'s precedence doc). Only
     * [DELIVERED] and [CHIP_CHECK_PASSED] render a "✓"; every other value
     * renders a "✗" — see [glyph]. */
    enum class Reason {
        /** HTTP 2xx, the presentation was accepted — mirrors
         * [DeliveryVerdictLine.accepted]'s own "delivered, not the site's
         * verdict" framing. */
        DELIVERED,

        /** The site refused the presentation — an honest under-threshold
         * refusal, any other `allowed:false` reason, or a non-2xx rejected
         * response. All three are "a claim was disclosed, the site said
         * no" from this line's point of view; see [ReportLog.Outcome]'s
         * doc for the same FAIL classification these share. */
        REFUSED,

        /** A claim was built, but nothing ever reached the site (no
         * `response_uri`, or the `direct_post` call itself failed). */
        NOT_SENT,

        /** The chip read itself threw (PACE/BAC/tag-loss/etc.) — no claim
         * was ever built. Distinct from [CHIP_CHECK_FAILED]: the chip could
         * not even be read, versus a completed read whose integrity check
         * came back negative. */
        READ_FAILED,

        /** No claim was ever built (mode A with nothing to mint, or a real
         * masterlist "no"), but the chip read itself completed cleanly
         * ([M0Probe.Verdict.ok]). */
        CHIP_CHECK_PASSED,

        /** Same shape as [CHIP_CHECK_PASSED], but the read's own integrity
         * check came back negative. */
        CHIP_CHECK_FAILED,
    }

    private fun Reason.word(): String = when (this) {
        Reason.DELIVERED -> "delivered"
        Reason.REFUSED -> "refused"
        Reason.NOT_SENT -> "not sent"
        Reason.READ_FAILED -> "read failed"
        Reason.CHIP_CHECK_PASSED -> "chip check passed"
        Reason.CHIP_CHECK_FAILED -> "chip check failed"
    }

    private fun Reason.glyph(): String = if (this == Reason.DELIVERED || this == Reason.CHIP_CHECK_PASSED) "✓" else "✗"

    /**
     * @param origin the same value-free site title [ReportLog.DisclosureSummary.site]
     *   already carries — a verified request's host:port, or the fixed
     *   "Local scan (no site)" label.
     * @param reason see [Reason]'s own doc.
     * @param threshold the age threshold this device checked against, or
     *   null when no claim was ever built for this scan (a refusal before
     *   any read, a local scan, a masterlist real-no, or a failed read).
     * @param overThreshold this device's own `over_threshold` answer for
     *   [threshold], or null alongside it.
     */
    data class Entry(
        val origin: String,
        val reason: Reason,
        val threshold: Int? = null,
        val overThreshold: Boolean? = null,
    )

    /** No scan has completed yet this session (or since "Clear log"), or
     * the last one is [isStale] against a newer handoff — [render]'s output
     * for both cases. */
    const val NONE_TEXT = "Last scan: none"

    /** Whether an entry stamped with [entryGeneration] must be hidden
     * because a NEWER av:// handoff has been captured since — see this
     * object's class doc, refinement 2. A plain monotonic counter
     * comparison, never a timestamp: [entryGeneration] falling behind
     * [currentHandoffGeneration] is the only staleness signal. */
    fun isStale(entryGeneration: Int, currentHandoffGeneration: Int): Boolean =
        entryGeneration < currentHandoffGeneration

    /** The one-line "Last scan: ..." string for [entry], or [NONE_TEXT] if
     * [entry] is null or [isStale]. Pure — never touches a View.
     *
     * @param entryGeneration the generation [ReportLog] stamped on [entry]
     *   at append time (meaningless when [entry] is null).
     * @param currentHandoffGeneration the CURRENT generation counter —
     *   see [isStale]'s doc.
     *
     * Precedence for a non-stale [entry] (checked in this order): a
     * THRESHOLD entry (a claim was actually built and checked) shows "over
     * N: true|false, <reason>"; otherwise the generic fallback shows just
     * the origin and <reason>. */
    fun render(entry: Entry?, entryGeneration: Int = 0, currentHandoffGeneration: Int = 0): String {
        if (entry == null || isStale(entryGeneration, currentHandoffGeneration)) return NONE_TEXT
        val reasonPart = "${entry.reason.word()} ${entry.reason.glyph()}"
        return if (entry.threshold != null && entry.overThreshold != null) {
            "Last scan: ${entry.origin}, over ${entry.threshold}: ${entry.overThreshold}, $reasonPart"
        } else {
            "Last scan: ${entry.origin}, $reasonPart"
        }
    }

    /** Serializes [entry] to a [JSONObject], or null for a null [entry] —
     * the nested shape [ReportLogStore] embeds in its own document. Pure —
     * never touches disk. */
    fun toJsonObject(entry: Entry?): JSONObject? {
        if (entry == null) return null
        val root = JSONObject()
        root.put("origin", entry.origin)
        root.put("reason", entry.reason.name)
        root.put("threshold", entry.threshold ?: JSONObject.NULL)
        root.put("over_threshold", entry.overThreshold ?: JSONObject.NULL)
        return root
    }

    /** Parses [root] into an [Entry] — null for a null [root] (including
     * every OLD persisted log file, written before this field existed,
     * where the enclosing key is simply absent — [ReportLogStore.fromJson]
     * passes the result of `optJSONObject`, which is already null in that
     * case) or a malformed/incomplete/unrecognised one. Never throws, never
     * guesses at a partial value — same discipline as
     * [ReportLogStore.fromJson]. */
    fun fromJsonObject(root: JSONObject?): Entry? {
        if (root == null) return null
        return try {
            Entry(
                origin = root.getString("origin"),
                reason = Reason.valueOf(root.getString("reason")),
                threshold = if (root.isNull("threshold")) null else root.getInt("threshold"),
                overThreshold = if (root.isNull("over_threshold")) null else root.getBoolean("over_threshold"),
            )
        } catch (e: Exception) {
            null
        }
    }

    /** String-shaped sibling of [toJsonObject]/[fromJsonObject] — Bundle
     * has no native nested-object type, so `MainActivity.onSaveInstanceState`
     * persists this as one String extra instead. */
    fun toJson(entry: Entry?): String? = toJsonObject(entry)?.toString()

    fun fromJson(json: String?): Entry? {
        if (json.isNullOrEmpty()) return null
        return try {
            fromJsonObject(JSONObject(json))
        } catch (e: Exception) {
            null
        }
    }
}
