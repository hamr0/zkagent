package com.tananaev.passportreader

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * S67-POC FIX (2026-09-06, device evidence) — pins [DroppedOutcomeRelay]'s
 * exactly-once relay contract: the ONE thing that closes the "a refused
 * handoff never reached the screen" bug (a stale instance's
 * [MainActivity.showBlockingNotice]/[MainActivity.showBlockingOutcomeDialog]
 * call silently failing/dropping the message instead of a sibling instance
 * ever showing it). `@After` resets the object's state between tests —
 * this IS process-wide singleton state by design (see its class doc), so
 * tests must not leak into each other.
 */
class DroppedOutcomeRelayTest {

    @After
    fun clearAnyPendingMessage() {
        DroppedOutcomeRelay.consume()
    }

    @Test
    fun `nothing pending by default`() {
        assertNull(DroppedOutcomeRelay.consume())
    }

    @Test
    fun `a stashed message is returned by consume`() {
        DroppedOutcomeRelay.stash("This site asked for over 21, but it first asked for over 18 — refused.")
        assertEquals(
            "This site asked for over 21, but it first asked for over 18 — refused.",
            DroppedOutcomeRelay.consume(),
        )
    }

    @Test
    fun `consume clears the message so it is shown exactly once`() {
        DroppedOutcomeRelay.stash("Handoff refused: origin mismatch")
        DroppedOutcomeRelay.consume()
        assertNull(DroppedOutcomeRelay.consume())
    }

    @Test
    fun `a later stash overwrites an unconsumed earlier one`() {
        DroppedOutcomeRelay.stash("first refusal")
        DroppedOutcomeRelay.stash("second refusal")
        assertEquals("second refusal", DroppedOutcomeRelay.consume())
        assertNull(DroppedOutcomeRelay.consume())
    }
}
