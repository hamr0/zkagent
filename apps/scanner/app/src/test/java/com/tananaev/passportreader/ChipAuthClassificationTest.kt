package com.tananaev.passportreader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * finding #8 (`docs/logs/M2-OWNERSHIP-AUDIT-2026-09-02.md`): the D51/D53
 * three-state chip-authenticity distinction lived entirely inline in
 * [MainActivity.ReadTask.doInBackground] with zero unit coverage. This
 * pins:
 *  1. the DG14-read decision ([ChipAuthClassification.fromDg14]) — absent
 *     vs. present-but-empty vs. present-and-verified vs. present-and-failed,
 *  2. the CA/AA combine rule ([ChipAuthClassification.combine]) — the full
 *     3x3 truth table, each cell written out by hand from the D51 rule
 *     ("either mechanism VERIFIED wins; else either FAILED beats
 *     NOT_SUPPORTED; combined is NOT_SUPPORTED only if BOTH are") — never
 *     derived by calling the code under test or by reading a shared
 *     constant,
 *  3. the D51/D53 rule that NOT_SUPPORTED must never render as "false" —
 *     [ChipAuthClassification.label] and [ChipAuthClassification.technical]
 *     must not contain "false" or "Not verified" for NOT_SUPPORTED, while
 *     FAILED's DOES contain "Not verified",
 *  4. all three statuses map to three distinct label strings.
 */
class ChipAuthClassificationTest {

    // ------------------------------------------------------------ fromDg14

    @Test
    fun `DG14 unreadable (absent) is NOT_SUPPORTED`() {
        assertEquals(
            M0Probe.ChipAuthStatus.NOT_SUPPORTED,
            ChipAuthClassification.fromDg14(dg14Readable = false, caInfosPresent = true, challengeSucceeded = true)
        )
    }

    @Test
    fun `DG14 readable but no ChipAuthenticationPublicKeyInfo entries is NOT_SUPPORTED`() {
        assertEquals(
            M0Probe.ChipAuthStatus.NOT_SUPPORTED,
            ChipAuthClassification.fromDg14(dg14Readable = true, caInfosPresent = false, challengeSucceeded = true)
        )
    }

    @Test
    fun `DG14 readable, CA infos present, doEACCA succeeds is VERIFIED`() {
        assertEquals(
            M0Probe.ChipAuthStatus.VERIFIED,
            ChipAuthClassification.fromDg14(dg14Readable = true, caInfosPresent = true, challengeSucceeded = true)
        )
    }

    @Test
    fun `DG14 readable, CA infos present, doEACCA throws is FAILED`() {
        assertEquals(
            M0Probe.ChipAuthStatus.FAILED,
            ChipAuthClassification.fromDg14(dg14Readable = true, caInfosPresent = true, challengeSucceeded = false)
        )
    }

    // -------------------------------------------------------------- combine

    private val V = M0Probe.ChipAuthStatus.VERIFIED
    private val N = M0Probe.ChipAuthStatus.NOT_SUPPORTED
    private val F = M0Probe.ChipAuthStatus.FAILED

    @Test fun `combine VERIFIED VERIFIED is VERIFIED`() { assertEquals(V, ChipAuthClassification.combine(V, V)) }
    @Test fun `combine VERIFIED NOT_SUPPORTED is VERIFIED`() { assertEquals(V, ChipAuthClassification.combine(V, N)) }
    @Test fun `combine VERIFIED FAILED is VERIFIED`() { assertEquals(V, ChipAuthClassification.combine(V, F)) }
    @Test fun `combine NOT_SUPPORTED VERIFIED is VERIFIED`() { assertEquals(V, ChipAuthClassification.combine(N, V)) }
    @Test fun `combine NOT_SUPPORTED NOT_SUPPORTED is NOT_SUPPORTED`() { assertEquals(N, ChipAuthClassification.combine(N, N)) }
    @Test fun `combine NOT_SUPPORTED FAILED is FAILED`() { assertEquals(F, ChipAuthClassification.combine(N, F)) }
    @Test fun `combine FAILED VERIFIED is VERIFIED`() { assertEquals(V, ChipAuthClassification.combine(F, V)) }
    @Test fun `combine FAILED NOT_SUPPORTED is FAILED`() { assertEquals(F, ChipAuthClassification.combine(F, N)) }
    @Test fun `combine FAILED FAILED is FAILED`() { assertEquals(F, ChipAuthClassification.combine(F, F)) }

    // ---------------------------------------------------- label / technical

    @Test
    fun `NOT_SUPPORTED label never contains false or Not verified`() {
        val label = ChipAuthClassification.label(M0Probe.ChipAuthStatus.NOT_SUPPORTED)
        assertFalse(label.contains("false", ignoreCase = true))
        assertFalse(label.contains("Not verified"))
    }

    @Test
    fun `NOT_SUPPORTED technical never contains false or Not verified`() {
        val technical = ChipAuthClassification.technical(M0Probe.ChipAuthStatus.NOT_SUPPORTED)
        assertFalse(technical.contains("false", ignoreCase = true))
        assertFalse(technical.contains("Not verified"))
    }

    @Test
    fun `FAILED label DOES contain Not verified`() {
        assertTrue(ChipAuthClassification.label(M0Probe.ChipAuthStatus.FAILED).contains("Not verified"))
    }

    @Test
    fun `label strings are byte-identical to the owner-approved D53 text`() {
        assertEquals("Verified — this document's chip proved it is genuine", ChipAuthClassification.label(M0Probe.ChipAuthStatus.VERIFIED))
        assertEquals("Not supported — this document has no chip authenticity check", ChipAuthClassification.label(M0Probe.ChipAuthStatus.NOT_SUPPORTED))
        assertEquals("Not verified — the chip check did not pass", ChipAuthClassification.label(M0Probe.ChipAuthStatus.FAILED))
    }

    @Test
    fun `technical strings are byte-identical to the existing baseReport values`() {
        assertEquals("passed", ChipAuthClassification.technical(M0Probe.ChipAuthStatus.VERIFIED))
        assertEquals("absent", ChipAuthClassification.technical(M0Probe.ChipAuthStatus.NOT_SUPPORTED))
        assertEquals("failed", ChipAuthClassification.technical(M0Probe.ChipAuthStatus.FAILED))
    }

    @Test
    fun `all three statuses map to three distinct label strings`() {
        val labels = M0Probe.ChipAuthStatus.entries.map { ChipAuthClassification.label(it) }
        assertEquals(labels.size, labels.toSet().size)
    }

    @Test
    fun `all three statuses map to three distinct technical strings`() {
        val technicals = M0Probe.ChipAuthStatus.entries.map { ChipAuthClassification.technical(it) }
        assertNotEquals(technicals[0], technicals[1])
        assertNotEquals(technicals[1], technicals[2])
        assertNotEquals(technicals[0], technicals[2])
    }
}
