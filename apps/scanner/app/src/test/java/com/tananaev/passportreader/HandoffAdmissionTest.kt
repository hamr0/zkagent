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
 * THIS WAS A MITIGATION, NOT THE FIX: D58 step 3 landed the ownership
 * refactor's `AuthorizedHandoff` snapshot (taken at lock time), which
 * closes the MINT-CORRECTNESS half of finding #10 by construction — see
 * that class's doc. This guard is KEPT anyway (a deliberate D58 step 3
 * decision, not an oversight): tracing what an admitted foreign intent
 * does mid-session found a separate, still-live UI-projection regression
 * (`applyHandoffVerificationOutcome` stomping the locked session's mode
 * display) that only this guard prevented AT THAT TIME — see
 * [HandoffAdmission]'s doc for the full trace and its D58 step 4 status
 * update (that regression is now closed independently, by
 * [SessionDisplay]'s locked-wins-unconditionally rule, so this guard's
 * continued presence is no longer load-bearing for THAT specific concern —
 * see [SessionDisplayTest]'s locked-always-wins cases for the pure-logic
 * coverage of that closure).
 *
 * D58 step 4 (finding #15) reuses this SAME predicate, tested exhaustively
 * below, at a SECOND call site — `MainActivity.applyPendingHandoffText`
 * (the QR-scan/manual-paste handoff path), which previously had no
 * admission gate at all. No new predicate logic was introduced for that
 * fix (the truth table below already covers every input this second call
 * site can supply); `MainActivity` itself has no direct unit-test coverage
 * in this module (it is Android-bound, not instantiable under
 * `unitTests.isReturnDefaultValues = true`) — the wiring at that call site
 * is verified by source read (this step's own survey) and is left for a
 * device run to confirm end-to-end, same as finding #10's own device
 * verification.
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
