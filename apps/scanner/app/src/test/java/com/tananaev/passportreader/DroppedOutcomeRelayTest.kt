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

    /**
     * S67-POC FIX round 2 (2026-09-06, duplicate-notice hazard) — pins
     * [DroppedOutcomeRelay.clear]'s exactly-once-across-the-pair contract:
     * a dying sibling's stash must not resurface once a live instance has
     * already shown its own dialog for the same run and called [clear].
     * Expected values here are written independently of the production
     * code's own behaviour, not copied from it.
     */
    @Test
    fun `clear discards a pending message without returning it`() {
        DroppedOutcomeRelay.stash("Handoff refused: origin mismatch")
        DroppedOutcomeRelay.clear()
        assertNull(DroppedOutcomeRelay.consume())
    }

    @Test
    fun `clear is a no-op when nothing is pending`() {
        DroppedOutcomeRelay.clear()
        assertNull(DroppedOutcomeRelay.consume())
    }

    @Test
    fun `a sibling's stash after this instance already cleared is not discarded retroactively`() {
        // Models the real ordering: this (live) instance shows its own
        // dialog and clears first, THEN the dying sibling's late landing
        // stashes its own copy of the same conclusion. clear() only
        // discards what is pending AT THE TIME it runs — a message stashed
        // afterward is a separate event and must still surface once, on
        // the next resume, exactly per the base "shown exactly once, never
        // lost" contract.
        DroppedOutcomeRelay.clear()
        DroppedOutcomeRelay.stash("Handoff refused: origin mismatch")
        assertEquals("Handoff refused: origin mismatch", DroppedOutcomeRelay.consume())
    }
}
