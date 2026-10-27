package com.tananaev.passportreader

/**
 * D115 (milestones.md §6.9.2 item 25) — the always-visible status strip under
 * VERIFY, as a PURE projection of data that already exists: the newest
 * persisted "last scan" entry ([ReportLog.lastScanInfo], with its existing
 * staleness guard) rendered by [LastScanLine.render]. No new state, no second
 * writer: [MainActivity.applyLastScanLine] calls [of] from the same four
 * sites it always did.
 *
 * [Kind] picks the strip's colour; the [Status.text] always carries its own
 * glyph (check, cross, dash), so colour is never the only signal.
 *
 * Owner rule (D115): green means it worked, red means it didn't, grey only
 * for "No scans yet.". Mapping from [LastScanLine.Reason]:
 * - DELIVERED, CHIP_CHECK_PASSED -> [Kind.SUCCESS] (teal, check). A clean local
 *   read ("Read OK — nothing was sent.") worked, so it is the same teal as a
 *   delivery.
 * - REFUSED, NOT_SENT, READ_FAILED, CHIP_CHECK_FAILED, REQUEST_REFUSED -> [Kind.FAILURE]
 *   (red, cross: something was attempted and did not work)
 * - no entry, or a stale one -> [Kind.NONE] (grey, dash, "No scans yet.")
 */
object LastScanStatus {

    enum class Kind { SUCCESS, FAILURE, NONE }

    data class Status(val kind: Kind, val text: String)

    /** Shown when there is no scan, or the stored one is stale. */
    const val NONE_TEXT = "– No scans yet."

    fun of(entry: LastScanLine.Entry?, entryGeneration: Int = 0, currentHandoffGeneration: Int = 0): Status {
        if (entry == null || LastScanLine.isStale(entryGeneration, currentHandoffGeneration)) {
            return Status(Kind.NONE, NONE_TEXT)
        }
        val kind = when (entry.reason) {
            LastScanLine.Reason.DELIVERED,
            LastScanLine.Reason.CHIP_CHECK_PASSED -> Kind.SUCCESS
            LastScanLine.Reason.REFUSED,
            LastScanLine.Reason.NOT_SENT,
            LastScanLine.Reason.READ_FAILED,
            LastScanLine.Reason.CHIP_CHECK_FAILED,
            LastScanLine.Reason.REQUEST_REFUSED -> Kind.FAILURE
        }
        return Status(kind, LastScanLine.render(entry, entryGeneration, currentHandoffGeneration))
    }
}
