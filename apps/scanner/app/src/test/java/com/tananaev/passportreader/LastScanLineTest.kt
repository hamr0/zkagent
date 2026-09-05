package com.tananaev.passportreader

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * FIX (owner, 2026-09-05) + owner refinement (same day) — the "Last scan:
 * ..." one-line summary that replaces `report_view` below the Scan/Verify
 * button. Independent truth table for [LastScanLine.render]/[LastScanLine.isStale]
 * — every expected string here is typed out by hand, never derived from
 * [LastScanLine]'s own constants, so a broken mapping cannot pass by
 * agreeing with itself.
 */
class LastScanLineTest {

    @Test
    fun `no entry yet renders the fixed none text`() {
        assertEquals("Last scan: none", LastScanLine.render(null))
    }

    @Test
    fun `delivered tier A - a claim was checked and the site accepted it`() {
        val entry = LastScanLine.Entry(origin = "state.gov", reason = LastScanLine.Reason.DELIVERED, threshold = 18, overThreshold = true)
        assertEquals("Last scan: state.gov, over 18: true, delivered ✓", LastScanLine.render(entry))
    }

    @Test
    fun `delivered tier B - same shape as tier A, different origin`() {
        val entry = LastScanLine.Entry(origin = "127.0.0.1:8787", reason = LastScanLine.Reason.DELIVERED, threshold = 21, overThreshold = true)
        assertEquals("Last scan: 127.0.0.1:8787, over 21: true, delivered ✓", LastScanLine.render(entry))
    }

    @Test
    fun `refused under threshold - an honest false answer, not delivered`() {
        val entry = LastScanLine.Entry(origin = "state.gov", reason = LastScanLine.Reason.REFUSED, threshold = 21, overThreshold = false)
        assertEquals("Last scan: state.gov, over 21: false, refused ✗", LastScanLine.render(entry))
    }

    @Test
    fun `verifier refused - the claim was true but the site refused it anyway`() {
        val entry = LastScanLine.Entry(origin = "state.gov", reason = LastScanLine.Reason.REFUSED, threshold = 18, overThreshold = true)
        assertEquals("Last scan: state.gov, over 18: true, refused ✗", LastScanLine.render(entry))
    }

    @Test
    fun `not sent - a claim existed but nothing reached the site`() {
        val entry = LastScanLine.Entry(origin = "state.gov", reason = LastScanLine.Reason.NOT_SENT, threshold = 18, overThreshold = true)
        assertEquals("Last scan: state.gov, over 18: true, not sent ✗", LastScanLine.render(entry))
    }

    @Test
    fun `chip read failed - no claim was ever built, the chip itself could not be read`() {
        val entry = LastScanLine.Entry(origin = "state.gov", reason = LastScanLine.Reason.READ_FAILED)
        assertEquals("Last scan: state.gov, read failed ✗", LastScanLine.render(entry))
    }

    @Test
    fun `local scan pass - no site, chip check passed`() {
        val entry = LastScanLine.Entry(origin = "Local scan (no site)", reason = LastScanLine.Reason.CHIP_CHECK_PASSED)
        assertEquals("Last scan: Local scan (no site), chip check passed ✓", LastScanLine.render(entry))
    }

    @Test
    fun `local scan fail - no site, chip check failed`() {
        val entry = LastScanLine.Entry(origin = "Local scan (no site)", reason = LastScanLine.Reason.CHIP_CHECK_FAILED)
        assertEquals("Last scan: Local scan (no site), chip check failed ✗", LastScanLine.render(entry))
    }

    @Test
    fun `generic fallback - a refusal before any read was attempted`() {
        val entry = LastScanLine.Entry(origin = "state.gov", reason = LastScanLine.Reason.REFUSED)
        assertEquals("Last scan: state.gov, refused ✗", LastScanLine.render(entry))
    }

    @Test
    fun `a threshold entry always shows the over-N segment even when the reason alone would also render`() {
        val entry = LastScanLine.Entry(origin = "state.gov", reason = LastScanLine.Reason.DELIVERED, threshold = 18, overThreshold = true)
        assertEquals("Last scan: state.gov, over 18: true, delivered ✓", LastScanLine.render(entry))
    }

    // --- Reason -> glyph truth table: only DELIVERED and CHIP_CHECK_PASSED
    // are a checkmark; every other reason is an X. Owner's own rule: the
    // checkmark can never mean "the site approved you", only "it got
    // there" / "the chip read cleanly" — see LastScanLine's class doc.

    @Test
    fun `only DELIVERED and CHIP_CHECK_PASSED render a checkmark`() {
        val checkmarkReasons = setOf(LastScanLine.Reason.DELIVERED, LastScanLine.Reason.CHIP_CHECK_PASSED)
        for (reason in LastScanLine.Reason.values()) {
            val rendered = LastScanLine.render(LastScanLine.Entry(origin = "x", reason = reason))
            val expectGlyph = if (reason in checkmarkReasons) "✓" else "✗"
            assertTrue("$reason should render $expectGlyph, got: $rendered", rendered.endsWith(expectGlyph))
        }
    }

    // --- Staleness (owner refinement 2) ---

    @Test
    fun `isStale is false when the entry's generation matches the current one`() {
        assertFalse(LastScanLine.isStale(entryGeneration = 3, currentHandoffGeneration = 3))
    }

    @Test
    fun `isStale is false when no handoff has been captured since (both at the default 0)`() {
        assertFalse(LastScanLine.isStale(entryGeneration = 0, currentHandoffGeneration = 0))
    }

    @Test
    fun `isStale is true when a newer handoff has been captured since the entry was produced`() {
        assertTrue(LastScanLine.isStale(entryGeneration = 1, currentHandoffGeneration = 2))
    }

    @Test
    fun `an old entry plus a new pending handoff renders as none`() {
        val entry = LastScanLine.Entry(origin = "state.gov", reason = LastScanLine.Reason.DELIVERED, threshold = 18, overThreshold = true)
        assertEquals("Last scan: none", LastScanLine.render(entry, entryGeneration = 1, currentHandoffGeneration = 2))
    }

    @Test
    fun `an entry produced by the current handoff generation still renders`() {
        val entry = LastScanLine.Entry(origin = "state.gov", reason = LastScanLine.Reason.DELIVERED, threshold = 18, overThreshold = true)
        assertEquals("Last scan: state.gov, over 18: true, delivered ✓", LastScanLine.render(entry, entryGeneration = 2, currentHandoffGeneration = 2))
    }

    @Test
    fun `no handoff pending (generation never advanced) still renders`() {
        val entry = LastScanLine.Entry(origin = "Local scan (no site)", reason = LastScanLine.Reason.CHIP_CHECK_PASSED)
        assertEquals("Last scan: Local scan (no site), chip check passed ✓", LastScanLine.render(entry, entryGeneration = 0, currentHandoffGeneration = 0))
    }

    // --- JSON round-trip (ReportLogStore's nested shape) ---

    @Test
    fun `toJsonObject and fromJsonObject round-trip a full entry byte-identically`() {
        val original = LastScanLine.Entry(origin = "state.gov", reason = LastScanLine.Reason.DELIVERED, threshold = 18, overThreshold = true)
        val restored = LastScanLine.fromJsonObject(LastScanLine.toJsonObject(original))
        assertEquals(original, restored)
    }

    @Test
    fun `toJsonObject and fromJsonObject round-trip every Reason value`() {
        for (reason in LastScanLine.Reason.values()) {
            val original = LastScanLine.Entry(origin = "x", reason = reason)
            val restored = LastScanLine.fromJsonObject(LastScanLine.toJsonObject(original))
            assertEquals(original, restored)
        }
    }

    @Test
    fun `toJsonObject returns null for a null entry, fromJsonObject returns null for a null object`() {
        assertNull(LastScanLine.toJsonObject(null))
        assertNull(LastScanLine.fromJsonObject(null))
    }

    @Test
    fun `fromJsonObject returns null on a malformed object, never throws or guesses`() {
        val malformed = JSONObject().apply { put("reason", "DELIVERED") } // missing required "origin"
        assertNull(LastScanLine.fromJsonObject(malformed))
    }

    @Test
    fun `fromJsonObject returns null on an unrecognised reason name, never throws or guesses`() {
        val malformed = JSONObject().apply { put("origin", "x"); put("reason", "SOMETHING_NEW") }
        assertNull(LastScanLine.fromJsonObject(malformed))
    }

    @Test
    fun `toJson and fromJson (Bundle string shape) round-trip the same as the JSONObject pair`() {
        val original = LastScanLine.Entry(origin = "state.gov", reason = LastScanLine.Reason.DELIVERED, threshold = 18, overThreshold = true)
        val restored = LastScanLine.fromJson(LastScanLine.toJson(original))
        assertEquals(original, restored)
    }

    @Test
    fun `fromJson returns null for null or blank input`() {
        assertNull(LastScanLine.fromJson(null))
        assertNull(LastScanLine.fromJson(""))
    }
}
