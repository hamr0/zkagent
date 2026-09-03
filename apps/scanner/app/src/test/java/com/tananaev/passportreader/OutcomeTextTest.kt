package com.tananaev.passportreader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * §6.2 item 25 (D71b, 2026-09-03) — [OutcomeText.withModeSentence] is the
 * pure decision behind [MainActivity.showBlockingOutcomeDialog]'s new
 * mode sentence; `AlertDialog` is a stub under this module's
 * `unitTests.isReturnDefaultValues = true`, so the decision is pinned
 * here, same pattern as [SessionDisplayTest]/[MintGateTest]/
 * [HandoffAdmissionTest].
 */
class OutcomeTextTest {

    @Test
    fun `mode A appends the exact anonymous sentence`() {
        val result = OutcomeText.withModeSentence("Read OK — nothing was sent.", SessionDisplay.LockedMode.A)
        assertEquals("Read OK — nothing was sent. This scan was Mode A, anonymous.", result)
    }

    @Test
    fun `mode B appends the exact recognisable sentence`() {
        val result = OutcomeText.withModeSentence("Mint confirmed.", SessionDisplay.LockedMode.B)
        assertEquals("Mint confirmed. This scan was Mode B, recognisable to this site.", result)
    }

    @Test
    fun `no mode locked appends nothing — message returned byte-identical`() {
        val message = "Incomplete MRZ fields — fill in document number, date of birth, and expiry date."
        val result = OutcomeText.withModeSentence(message, null)
        assertEquals(message, result)
    }

    @Test
    fun `the appended sentence uses SessionDisplay's own mode-label wording, never a separately-typed literal`() {
        val a = OutcomeText.withModeSentence("x", SessionDisplay.LockedMode.A)
        val b = OutcomeText.withModeSentence("x", SessionDisplay.LockedMode.B)
        assertTrue(a.endsWith(SessionDisplay.modeLabel(SessionDisplay.LockedMode.A) + "."))
        assertTrue(b.endsWith(SessionDisplay.modeLabel(SessionDisplay.LockedMode.B) + "."))
    }

    @Test
    fun `the dialog message body is unchanged apart from the appended sentence`() {
        val message = "Handoff refused: signature mismatch"
        val withMode = OutcomeText.withModeSentence(message, SessionDisplay.LockedMode.B)
        assertTrue("original message body must appear verbatim, unmodified, at the start", withMode.startsWith(message))
        assertEquals(message, withMode.removeSuffix(" This scan was Mode B, recognisable to this site."))
    }
}
