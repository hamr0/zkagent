package com.tananaev.passportreader

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Finding #10 (`.claude/remember/findings.md`) — an `av://` intent arriving
 * mid-session (during an already-locked read, or while a chip read is
 * literally in flight) overwrote `pendingHandoff`/`verifiedRequest` with no
 * guard, letting an attacker's origin ride the user's real biometric
 * authorization and chip read. [HandoffAdmission.mayAdmitInboundHandoff] is
 * that guard's decision, extracted to a pure predicate — same pattern as
 * [PaneVisibility.choosePane] / [MintGate.mayMint] — so the truth table is
 * pinned independently of `Intent`/`Log` plumbing, which is unassertable
 * under this module's `unitTests.isReturnDefaultValues = true`.
 *
 * THIS IS A MITIGATION, NOT THE FIX: the ownership-refactor's `SessionState`
 * snapshot (taken at lock time) is the structural fix for finding #10; once
 * it lands, the timing window this predicate closes should not exist any
 * more, and this guard becomes redundant.
 */
class HandoffAdmissionTest {

    @Test
    fun `admits an inbound handoff when nothing is locked and no read is in flight`() {
        assertTrue(HandoffAdmission.mayAdmitInboundHandoff(sessionLocked = false, readInProgress = false))
    }

    @Test
    fun `refuses while the session is locked, even with no read in flight yet`() {
        assertFalse(HandoffAdmission.mayAdmitInboundHandoff(sessionLocked = true, readInProgress = false))
    }

    @Test
    fun `refuses while a read is in progress, even if lockedMode were somehow null`() {
        assertFalse(HandoffAdmission.mayAdmitInboundHandoff(sessionLocked = false, readInProgress = true))
    }

    @Test
    fun `refuses when both locked and reading`() {
        assertFalse(HandoffAdmission.mayAdmitInboundHandoff(sessionLocked = true, readInProgress = true))
    }
}
