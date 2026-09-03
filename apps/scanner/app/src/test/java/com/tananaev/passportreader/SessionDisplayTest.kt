package com.tananaev.passportreader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * D58 step 4 (findings #9, #14; Q40) — [SessionDisplay.render] is the
 * extracted, pure decision behind `handoffStatus.text`/
 * `lockButton.isEnabled`+`.text`/`modeStatusView.text` — `MainActivity`'s
 * real views are themselves stubs under this module's
 * `unitTests.isReturnDefaultValues = true`, so the decision is pinned
 * here, same pattern as [PaneVisibilityTest]/[MintGateTest]/
 * [HandoffAdmissionTest]/[MintPromptTextTest].
 *
 * `lockedModeWins` covers finding #14's OWN class of defect one level up
 * (a locked session's display must never be overwritten by a foreign
 * verification outcome) and unblocks D58 step 3's guard-removal question —
 * see [SessionDisplay]'s class doc.
 */
class SessionDisplayTest {

    // ---------------------------------------------------------- unlocked

    @Test
    fun `no handoff pending renders the default mode, blank handoff status, enabled button, default label`() {
        val p = SessionDisplay.render(locked = null, handoff = SessionDisplay.HandoffState.None)
        assertEquals("Mode: A — anonymous (no site request pending)", p.modeStatusText)
        assertEquals("", p.handoffStatusText)
        assertTrue(p.lockButtonEnabled)
        assertEquals(SessionDisplay.LockButtonLabel.SCAN, p.lockButtonLabel)
    }

    @Test
    fun `verifying disables the lock button and shows the verifying copy on both lines`() {
        val p = SessionDisplay.render(locked = null, handoff = SessionDisplay.HandoffState.Verifying)
        assertEquals("Mode: verifying the site's request…", p.modeStatusText)
        assertEquals("Handoff request received — verifying signature and origin…", p.handoffStatusText)
        assertFalse(p.lockButtonEnabled)
        assertEquals(SessionDisplay.LockButtonLabel.SCAN, p.lockButtonLabel)
    }

    @Test
    fun `verified tier A renders the anonymous mode line and enables the lock button`() {
        val p = SessionDisplay.render(locked = null, handoff = SessionDisplay.HandoffState.Verified("example.com", "A"))
        assertEquals("Mode: A — anonymous", p.modeStatusText)
        assertTrue(p.handoffStatusText.contains("example.com"))
        assertTrue(p.handoffStatusText.contains("requested tier: A"))
        assertTrue(p.lockButtonEnabled)
    }

    @Test
    fun `verified tier B renders the recognisable mode line`() {
        val p = SessionDisplay.render(locked = null, handoff = SessionDisplay.HandoffState.Verified("example.com", "B"))
        assertEquals("Mode: B — recognisable to this site", p.modeStatusText)
    }

    @Test
    fun `verified with an unmapped tier renders the pending mode line, not a guess`() {
        val p = SessionDisplay.render(locked = null, handoff = SessionDisplay.HandoffState.Verified("example.com", "C"))
        assertEquals("Mode: pending — tap Lock & scan to see the outcome", p.modeStatusText)
        assertTrue(p.handoffStatusText.contains("requested tier: C"))
    }

    @Test
    fun `verified with a null tier shows the absent marker, not a blank or a guess`() {
        val p = SessionDisplay.render(locked = null, handoff = SessionDisplay.HandoffState.Verified("example.com", null))
        assertTrue(p.handoffStatusText.contains("requested tier: <absent>"))
    }

    @Test
    fun `refused reverts the mode line to default and disables the lock button`() {
        val p = SessionDisplay.render(locked = null, handoff = SessionDisplay.HandoffState.Refused("origin mismatch"))
        assertEquals("Mode: A — anonymous (no site request pending)", p.modeStatusText)
        assertTrue(p.handoffStatusText.contains("origin mismatch"))
        assertTrue(p.handoffStatusText.startsWith("Handoff refused"))
        assertFalse(p.lockButtonEnabled)
    }

    // ------------------------------------------------------------ locked

    @Test
    fun `locked mode A shows the locked banner, disables the button, and blanks the handoff line`() {
        val p = SessionDisplay.render(locked = SessionDisplay.LockedMode.A, handoff = SessionDisplay.HandoffState.None)
        assertEquals("Locked: mode A — tap your document now", p.modeStatusText)
        assertEquals("", p.handoffStatusText)
        assertFalse(p.lockButtonEnabled)
        assertEquals(SessionDisplay.LockButtonLabel.TAP_AND_SCAN, p.lockButtonLabel)
    }

    @Test
    fun `locked mode B shows the locked banner`() {
        val p = SessionDisplay.render(locked = SessionDisplay.LockedMode.B, handoff = SessionDisplay.HandoffState.None)
        assertEquals("Locked: mode B — tap your document now", p.modeStatusText)
    }

    /**
     * Finding #14 / D58 step 3 guard-removal trace: an ADMITTED foreign
     * handoff's verification resolving AFTER the legitimate session has
     * since locked must not be able to overwrite the locked banner with
     * "verified"/"refused" text — [locked] must win regardless of
     * [handoff]. This is the exact defect class finding #14 named
     * ("a stale projection changed what the user did next") one level up:
     * a NON-stale but FOREIGN projection must not land on a locked screen
     * either.
     */
    @Test
    fun `locked always wins over a foreign Verified outcome arriving after lock`() {
        val p = SessionDisplay.render(
            locked = SessionDisplay.LockedMode.B,
            handoff = SessionDisplay.HandoffState.Verified("attacker.example", "A"),
        )
        assertEquals("Locked: mode B — tap your document now", p.modeStatusText)
        assertFalse("must not mention the foreign origin", p.handoffStatusText.contains("attacker.example"))
        assertEquals("", p.handoffStatusText)
        assertFalse(p.lockButtonEnabled)
        assertEquals(SessionDisplay.LockButtonLabel.TAP_AND_SCAN, p.lockButtonLabel)
    }

    @Test
    fun `locked always wins over a foreign Refused outcome arriving after lock`() {
        val p = SessionDisplay.render(
            locked = SessionDisplay.LockedMode.A,
            handoff = SessionDisplay.HandoffState.Refused("some foreign refusal reason"),
        )
        assertEquals("Locked: mode A — tap your document now", p.modeStatusText)
        assertFalse(p.handoffStatusText.contains("some foreign refusal reason"))
        assertFalse(p.lockButtonEnabled)
    }

    /**
     * Finding #14's OWN reproduction, reconstructed directly: after a mint
     * consumes the session (`pendingHandoff`/`verifiedRequest` both go
     * null, `lockedMode` clears too), the projection derived from that
     * exact post-consumption state must show neither the stale "verified"
     * text nor any other non-default handoff copy.
     */
    @Test
    fun `post-consumption state renders the same as a fresh session, not the stale verified text`() {
        val fresh = SessionDisplay.render(locked = null, handoff = SessionDisplay.HandoffState.None)
        val postMint = SessionDisplay.render(locked = null, handoff = SessionDisplay.HandoffState.None)
        assertEquals(fresh, postMint)
        assertEquals("", postMint.handoffStatusText)
        assertFalse(postMint.handoffStatusText.contains("verified"))
        assertFalse(postMint.handoffStatusText.contains("waiting"))
    }

    // ------------------------------------------- item 20 (D68): button verb

    @Test
    fun `no handoff pending renders the SCAN verb`() {
        val p = SessionDisplay.render(locked = null, handoff = SessionDisplay.HandoffState.None)
        assertEquals(SessionDisplay.LockButtonLabel.SCAN, p.lockButtonLabel)
    }

    @Test
    fun `verifying (not yet verified) renders SCAN, not VERIFY`() {
        val p = SessionDisplay.render(locked = null, handoff = SessionDisplay.HandoffState.Verifying)
        assertEquals(SessionDisplay.LockButtonLabel.SCAN, p.lockButtonLabel)
    }

    @Test
    fun `a verified handoff renders the VERIFY verb, unlocked`() {
        val p = SessionDisplay.render(locked = null, handoff = SessionDisplay.HandoffState.Verified("example.com", "B"))
        assertEquals(SessionDisplay.LockButtonLabel.VERIFY, p.lockButtonLabel)
    }

    @Test
    fun `a REFUSED handoff renders SCAN, not VERIFY`() {
        val p = SessionDisplay.render(locked = null, handoff = SessionDisplay.HandoffState.Refused("origin mismatch"))
        assertEquals(SessionDisplay.LockButtonLabel.SCAN, p.lockButtonLabel)
    }

    @Test
    fun `a bare local lock (handoffDrivenLock=false) renders TAP_AND_SCAN`() {
        val p = SessionDisplay.render(locked = SessionDisplay.LockedMode.A, handoff = SessionDisplay.HandoffState.None, handoffDrivenLock = false)
        assertEquals(SessionDisplay.LockButtonLabel.TAP_AND_SCAN, p.lockButtonLabel)
    }

    @Test
    fun `a handoff-driven lock renders TAP_AND_VERIFY`() {
        val p = SessionDisplay.render(locked = SessionDisplay.LockedMode.B, handoff = SessionDisplay.HandoffState.None, handoffDrivenLock = true)
        assertEquals(SessionDisplay.LockButtonLabel.TAP_AND_VERIFY, p.lockButtonLabel)
    }

    /**
     * Extends the existing "locked always wins" defence (finding #14/D58
     * step 3) to the new verb: [handoffDrivenLock] is this lock's OWN
     * frozen snapshot (`authorizedHandoff != null`), not the live [handoff]
     * argument — a foreign handoff's async verification resolving AFTER a
     * bare (non-handoff) lock must not flip the verb to VERIFY, exactly as
     * it must not resurrect the foreign origin text. Omitting
     * `handoffDrivenLock` (default `false`) is itself the regression guard:
     * every pre-item-20 call site keeps this behaviour with no code change.
     */
    @Test
    fun `a foreign Verified outcome arriving after a bare lock does not flip the verb to VERIFY`() {
        val p = SessionDisplay.render(
            locked = SessionDisplay.LockedMode.B,
            handoff = SessionDisplay.HandoffState.Verified("attacker.example", "A"),
        )
        assertEquals(SessionDisplay.LockButtonLabel.TAP_AND_SCAN, p.lockButtonLabel)
    }

    @Test
    fun `a genuinely handoff-driven lock keeps the VERIFY verb even if a later outcome for it is Refused`() {
        val p = SessionDisplay.render(
            locked = SessionDisplay.LockedMode.B,
            handoff = SessionDisplay.HandoffState.Refused("some later refusal"),
            handoffDrivenLock = true,
        )
        assertEquals(SessionDisplay.LockButtonLabel.TAP_AND_VERIFY, p.lockButtonLabel)
    }
}
