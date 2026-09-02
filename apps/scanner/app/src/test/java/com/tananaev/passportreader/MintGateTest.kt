package com.tananaev.passportreader

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * §6.2 item 3's mint gate — the single source of truth extracted into
 * [MintGate]. This is the exact boolean expression that was computing
 * correctly all along on the real-device 2026-08-31 runs; the actual bug
 * (`MainActivity.continueAfterRead`'s `!mayMint` branch never logging) is
 * covered by inspection/emitReport, not by a test — this test only pins the
 * gate's own truth table so it can never silently drift.
 */
class MintGateTest {

    private fun verdict(ok: Boolean, allowed: Boolean?, reason: String = "test") = M0Probe.Verdict(ok, allowed, reason)

    @Test
    fun `mode B with ok true allowed true MAY mint`() {
        assertTrue(MintGate.mayMint(modeIsB = true, verdict = verdict(true, true)))
    }

    @Test
    fun `mode A NEVER mints, even with ok true allowed true`() {
        assertFalse(MintGate.mayMint(modeIsB = false, verdict = verdict(true, true)))
    }

    @Test
    fun `mode B with a real masterlist no does not mint`() {
        assertFalse(MintGate.mayMint(modeIsB = true, verdict = verdict(true, false)))
    }

    @Test
    fun `mode B with an integrity failure (ok false, allowed null) does not mint`() {
        assertFalse(MintGate.mayMint(modeIsB = true, verdict = verdict(false, null)))
    }

    @Test
    fun `mode A with an integrity failure does not mint`() {
        assertFalse(MintGate.mayMint(modeIsB = false, verdict = verdict(false, null)))
    }
}
