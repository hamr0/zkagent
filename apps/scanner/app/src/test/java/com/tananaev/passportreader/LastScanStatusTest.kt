package com.tananaev.passportreader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * D115 (item 25) — the last-scan strip's status is a pure projection of the newest persisted entry. Every expected
 * kind and string is typed out by hand (not read from [LastScanStatus] or [LastScanLine] constants).
 */
class LastScanStatusTest {

    private fun entry(reason: LastScanLine.Reason, origin: String = "state.gov") =
        LastScanLine.Entry(origin = origin, reason = reason, threshold = 18, overThreshold = true)

    @Test
    fun `no entry is NONE with the grey dash text`() {
        assertEquals(LastScanStatus.Status(LastScanStatus.Kind.NONE, "– No scans yet."), LastScanStatus.of(null))
    }

    @Test
    fun `a stale entry (an older handoff generation) is NONE too`() {
        val s = LastScanStatus.of(entry(LastScanLine.Reason.DELIVERED), entryGeneration = 1, currentHandoffGeneration = 2)
        assertEquals(LastScanStatus.Kind.NONE, s.kind)
        assertEquals("– No scans yet.", s.text)
    }

    @Test
    fun `delivered is DELIVERED and keeps the existing line text with its check mark`() {
        val s = LastScanStatus.of(entry(LastScanLine.Reason.DELIVERED))
        assertEquals(LastScanStatus.Kind.SUCCESS, s.kind)
        assertEquals("Last scan: state.gov, over 18: true, delivered ✓", s.text)
    }

    @Test
    fun `refused, not sent, read failed and chip check failed are all REFUSED (red) with a cross`() {
        val expectedWord = mapOf(
            LastScanLine.Reason.REFUSED to "refused",
            LastScanLine.Reason.NOT_SENT to "not sent",
            LastScanLine.Reason.READ_FAILED to "read failed",
            LastScanLine.Reason.CHIP_CHECK_FAILED to "chip check failed",
        )
        for ((reason, word) in expectedWord) {
            val s = LastScanStatus.of(LastScanLine.Entry(origin = "state.gov", reason = reason))
            assertEquals("$reason", LastScanStatus.Kind.FAILURE, s.kind)
            assertEquals("Last scan: state.gov, $word ✗", s.text)
        }
    }

    @Test
    fun `a clean read where nothing was sent is SUCCESS (same teal as delivered), never failure or none`() {
        val s = LastScanStatus.of(LastScanLine.Entry(origin = "Local scan (no site)", reason = LastScanLine.Reason.CHIP_CHECK_PASSED))
        assertEquals(LastScanStatus.Kind.SUCCESS, s.kind)
        assertEquals("Last scan: Local scan (no site), chip check passed \u2713", s.text)
        assertNotEquals(LastScanStatus.Kind.FAILURE, s.kind)
        assertNotEquals(LastScanStatus.Kind.NONE, s.kind)
    }

    @Test
    fun `grey is only for none - every reason maps to success or failure`() {
        val expected = mapOf(
            LastScanLine.Reason.DELIVERED to LastScanStatus.Kind.SUCCESS,
            LastScanLine.Reason.CHIP_CHECK_PASSED to LastScanStatus.Kind.SUCCESS,
            LastScanLine.Reason.REFUSED to LastScanStatus.Kind.FAILURE,
            LastScanLine.Reason.NOT_SENT to LastScanStatus.Kind.FAILURE,
            LastScanLine.Reason.READ_FAILED to LastScanStatus.Kind.FAILURE,
            LastScanLine.Reason.CHIP_CHECK_FAILED to LastScanStatus.Kind.FAILURE,
            LastScanLine.Reason.REQUEST_REFUSED to LastScanStatus.Kind.FAILURE,
        )
        assertEquals(7, expected.size)
        assertEquals(LastScanLine.Reason.values().size, expected.size)
        for ((reason, kind) in expected) assertEquals("$reason", kind, LastScanStatus.of(LastScanLine.Entry("x", reason)).kind)
    }

    @Test
    fun `a refused website request is FAILURE with the exact owner text`() {
        val s = LastScanStatus.of(LastScanLine.requestRefused())
        assertEquals(LastScanStatus.Kind.FAILURE, s.kind)
        assertEquals("Last: website request refused \u2717", s.text)
    }

    @Test
    fun `negative control - the three kinds are all reached by the seven reasons plus none, so no kind is dead`() {
        val reached = LastScanLine.Reason.values().map { LastScanStatus.of(LastScanLine.Entry("x", it)).kind }.toMutableSet()
        reached += LastScanStatus.of(null).kind
        assertEquals(3, reached.size)
    }

    @Test
    fun `every status text carries a glyph so colour is never the only signal`() {
        val glyphs = listOf("✓", "✗", "–")
        val all = LastScanLine.Reason.values().map { LastScanStatus.of(LastScanLine.Entry("x", it)).text } + LastScanStatus.of(null).text
        for (t in all) assertEquals(t, true, glyphs.any { t.contains(it) })
    }
}
