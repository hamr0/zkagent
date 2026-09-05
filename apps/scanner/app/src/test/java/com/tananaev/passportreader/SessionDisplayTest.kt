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

    // ---------------------------------------------- §6.5 S2 (D74): question line

    @Test
    fun `no handoff pending shows the Local scan question line`() {
        val p = SessionDisplay.render(locked = null, handoff = SessionDisplay.HandoffState.None)
        assertEquals(MintPromptText.NO_HANDOFF_FALLBACK, p.questionText)
    }

    @Test
    fun `verifying (not yet verified) shows the Local scan question line, not a guess`() {
        val p = SessionDisplay.render(locked = null, handoff = SessionDisplay.HandoffState.Verifying)
        assertEquals(MintPromptText.NO_HANDOFF_FALLBACK, p.questionText)
    }

    @Test
    fun `refused shows the Local scan question line`() {
        val p = SessionDisplay.render(locked = null, handoff = SessionDisplay.HandoffState.Refused("origin mismatch"))
        assertEquals(MintPromptText.NO_HANDOFF_FALLBACK, p.questionText)
    }

    @Test
    fun `a verified tier A request shows the exact D74 worked example, unlocked`() {
        val p = SessionDisplay.render(locked = null, handoff = SessionDisplay.HandoffState.Verified("example.com", "A", threshold = 18))
        assertEquals("This website asks if you are over 18", p.questionText)
    }

    @Test
    fun `a verified tier B request appends the recognition sentence`() {
        val p = SessionDisplay.render(locked = null, handoff = SessionDisplay.HandoffState.Verified("example.com", "B", threshold = 21))
        assertEquals("This website asks if you are over 21, and may recognise you again on this site", p.questionText)
    }

    @Test
    fun `a verified request with no readable threshold does not print a guessed number`() {
        val p = SessionDisplay.render(locked = null, handoff = SessionDisplay.HandoffState.Verified("example.com", "A", threshold = null))
        assertFalse("must not contain a literal null", p.questionText.contains("null"))
        assertTrue(p.questionText.startsWith("This website asks if you are over"))
    }

    @Test
    fun `a bare local lock shows the Local scan question line, locked`() {
        val p = SessionDisplay.render(locked = SessionDisplay.LockedMode.A, handoff = SessionDisplay.HandoffState.None)
        assertEquals(MintPromptText.NO_HANDOFF_FALLBACK, p.questionText)
    }

    @Test
    fun `a handoff-driven lock shows the locked request's own question, from the frozen snapshot argument`() {
        val p = SessionDisplay.render(
            locked = SessionDisplay.LockedMode.B,
            handoff = SessionDisplay.HandoffState.None,
            handoffDrivenLock = true,
            lockedQuestionHandoff = SessionDisplay.HandoffState.Verified("example.com", "B", threshold = 18),
        )
        assertEquals("This website asks if you are over 18, and may recognise you again on this site", p.questionText)
    }

    /**
     * The question-line analogue of "locked always wins" (see the mode/verb
     * cases above): once locked, a foreign handoff's live [handoff] argument
     * changing must not change the displayed question — only
     * [lockedQuestionHandoff] (the caller's own frozen [AuthorizedHandoff]
     * snapshot) may.
     */
    @Test
    fun `a foreign Verified outcome arriving after a bare lock does not change the locked question line`() {
        val p = SessionDisplay.render(
            locked = SessionDisplay.LockedMode.A,
            handoff = SessionDisplay.HandoffState.Verified("attacker.example", "B", threshold = 60),
        )
        assertEquals(MintPromptText.NO_HANDOFF_FALLBACK, p.questionText)
    }

    // ------------------------------------------- §6.5 S3 (D75): paste button

    @Test
    fun `paste button is enabled when unlocked and no read is in flight`() {
        val p = SessionDisplay.render(locked = null, handoff = SessionDisplay.HandoffState.None, readInProgress = false)
        assertTrue(p.pasteButtonEnabled)
        assertEquals(HandoffAdmission.mayAdmitInboundHandoff(sessionLocked = false, readInProgress = false), p.pasteButtonEnabled)
    }

    @Test
    fun `paste button is dimmed while locked, even with no read in flight yet`() {
        val p = SessionDisplay.render(locked = SessionDisplay.LockedMode.A, handoff = SessionDisplay.HandoffState.None, readInProgress = false)
        assertFalse(p.pasteButtonEnabled)
        assertEquals(HandoffAdmission.mayAdmitInboundHandoff(sessionLocked = true, readInProgress = false), p.pasteButtonEnabled)
    }

    @Test
    fun `paste button is dimmed while a read is in flight, even if unlocked`() {
        val p = SessionDisplay.render(locked = null, handoff = SessionDisplay.HandoffState.None, readInProgress = true)
        assertFalse(p.pasteButtonEnabled)
        assertEquals(HandoffAdmission.mayAdmitInboundHandoff(sessionLocked = false, readInProgress = true), p.pasteButtonEnabled)
    }

    @Test
    fun `paste button is dimmed while both locked and reading`() {
        val p = SessionDisplay.render(locked = SessionDisplay.LockedMode.B, handoff = SessionDisplay.HandoffState.Verified("example.com", "B"), readInProgress = true)
        assertFalse(p.pasteButtonEnabled)
        assertEquals(HandoffAdmission.mayAdmitInboundHandoff(sessionLocked = true, readInProgress = true), p.pasteButtonEnabled)
    }

    // ------------------------- §6.5 S3 (D75) owner correction, 2026-09-05:
    // pending, un-applied paste text drives the main button's verb

    @Test
    fun `bare unlocked state with pending paste text renders APPLY_PASTE, not SCAN`() {
        val p = SessionDisplay.render(locked = null, handoff = SessionDisplay.HandoffState.None, pasteTextPending = true)
        assertEquals(SessionDisplay.LockButtonLabel.APPLY_PASTE, p.lockButtonLabel)
    }

    @Test
    fun `bare unlocked state with no pending paste text still renders SCAN`() {
        val p = SessionDisplay.render(locked = null, handoff = SessionDisplay.HandoffState.None, pasteTextPending = false)
        assertEquals(SessionDisplay.LockButtonLabel.SCAN, p.lockButtonLabel)
    }

    /**
     * "Locked always wins" (see the mode/verb/question-line cases above)
     * extends to [pasteTextPending] too: a locked session must render its
     * own locked verb regardless of what the (irrelevant, off-screen once
     * locked) paste field happens to hold.
     */
    @Test
    fun `a locked session ignores pasteTextPending and renders its own locked verb`() {
        val bareLock = SessionDisplay.render(locked = SessionDisplay.LockedMode.A, handoff = SessionDisplay.HandoffState.None, pasteTextPending = true)
        assertEquals(SessionDisplay.LockButtonLabel.TAP_AND_SCAN, bareLock.lockButtonLabel)

        val handoffDrivenLock = SessionDisplay.render(
            locked = SessionDisplay.LockedMode.B,
            handoff = SessionDisplay.HandoffState.None,
            handoffDrivenLock = true,
            pasteTextPending = true,
        )
        assertEquals(SessionDisplay.LockButtonLabel.TAP_AND_VERIFY, handoffDrivenLock.lockButtonLabel)
    }

    @Test
    fun `an already-verified handoff takes precedence over pending paste text`() {
        val p = SessionDisplay.render(
            locked = null,
            handoff = SessionDisplay.HandoffState.Verified("example.com", "A", threshold = 18),
            pasteTextPending = true,
        )
        assertEquals(SessionDisplay.LockButtonLabel.VERIFY, p.lockButtonLabel)
    }

    @Test
    fun `a handoff still verifying takes precedence over pending paste text`() {
        val p = SessionDisplay.render(locked = null, handoff = SessionDisplay.HandoffState.Verifying, pasteTextPending = true)
        assertEquals(SessionDisplay.LockButtonLabel.SCAN, p.lockButtonLabel)
    }

    // -------- Device round 4 investigation ("button is dimmed, not released")
    //
    // FailureTransition.classify(exception) for a mistyped-details read
    // failure resolves ACCESS_ESTABLISHMENT -> keepsMrzAndMode(true) ->
    // wipeSession(keepMrzAndMode = true) is a no-op -> lockedMode stays
    // non-null. This is a KEEP transition, not RESET: the session stays
    // locked BY DESIGN (Q40/D67 — armed for another NFC tap, not a second
    // button press), so a disabled lockButton here is correct, not a bug.
    // These two tests pin that both halves of the transition already
    // behave correctly and are UNAFFECTED by this session's new
    // pasteTextPending/readInProgress inputs (compared against 2b7d9ae,
    // the pre-this-session projection: the locked branch's
    // `lockButtonEnabled = false` line is identical there — unconditional,
    // never reading either new parameter).

    @Test
    fun `a KEEP transition (session stays locked after a failed read) keeps the lock button disabled by design`() {
        // pasteTextPending/readInProgress deliberately set to values that
        // WOULD flip other projections' fields, to prove neither can flip
        // this one while locked.
        val p = SessionDisplay.render(
            locked = SessionDisplay.LockedMode.A,
            handoff = SessionDisplay.HandoffState.None,
            pasteTextPending = true,
            readInProgress = true,
        )
        assertFalse("a KEEP transition's disabled button is by design (Q40/D67) — armed for an NFC tap, not a press", p.lockButtonEnabled)
    }

    @Test
    fun `a RESET transition (session unlocks after an unclassified failure) re-enables the lock button`() {
        val p = SessionDisplay.render(locked = null, handoff = SessionDisplay.HandoffState.None)
        assertTrue(p.lockButtonEnabled)
    }
}
