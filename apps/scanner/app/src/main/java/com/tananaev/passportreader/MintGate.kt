package com.tananaev.passportreader

/**
 * §6.2 item 3's mint/present gate, extracted to a pure function so the
 * "a read that fails the same ok/allowed integrity check NEVER mints or
 * presents" invariant has a unit test independent of the Android UI
 * plumbing around it, and so [MainActivity.continueAfterRead] has exactly
 * one place that computes this, never re-derive the expression inline
 * elsewhere.
 *
 * ROOT CAUSE NOTE (2026-08-31 real-device runs, second/third tap): this
 * boolean being `false` was never the bug. The bug was that the branch
 * MainActivity took when this is `false` set `reportView.text` directly
 * without ever calling `Log.i` — so a real, correctly-computed "gate not
 * met" verdict rendered on screen while logcat stayed silent, which is
 * exactly what run 2 (mode A, always false here) and run 3 (mode B, false
 * here because passive auth did not return ok:true/allowed:true that time)
 * both did. See MainActivity's `emitReport` doc.
 *
 * FIX (finding #21, 2026-09-03): the gate used to answer one question —
 * "may mode B mint?" — with EVERY mode A outcome, successful read or not,
 * folded into the same `false`. That collapsed two structurally different
 * "not minting" cases into one: mode A never mints a device key/zktag (by
 * design, D27), but a mode A holder who DID pass the same read-integrity
 * check mode B already gates on (`verdict.ok && verdict.allowed == true`)
 * still has an item-9 MUST left to meet — present a bare tier-A claim
 * (`evidence: []`, no zktag, no device key) to whatever site is waiting.
 * [actionFor] now returns which of the three outcomes applies, so
 * [MainActivity.continueAfterRead] can no longer conflate "mode A, nothing
 * to do" with "mode A, present bare" the way the old single boolean did.
 * [mayMint] is kept, unchanged in meaning, for the exact "may mode B mint"
 * question — every existing caller/test of it keeps working verbatim.
 */
object MintGate {

    /** The gate's three outcomes — see class doc. */
    sealed class Action {
        /** Mode B, read passed the integrity check (`ok && allowed ==
         * true`): derive zktag, sign evidence, mint/handoff. */
        object MintB : Action()

        /** Mode A, read passed the SAME integrity check: no zktag, no
         * device key, no biometric prompt — present a bare tier-A claim
         * instead (item 9, D27). */
        object PresentBareA : Action()

        /** Read did NOT pass the integrity check (`!ok`, or a real
         * masterlist `allowed == false`) — nothing is minted or presented,
         * whatever the mode. */
        object None : Action()
    }

    fun actionFor(modeIsB: Boolean, verdict: M0Probe.Verdict): Action {
        val readOk = verdict.ok && verdict.allowed == true
        return when {
            !readOk -> Action.None
            modeIsB -> Action.MintB
            else -> Action.PresentBareA
        }
    }

    /** "May mode B mint?" — equivalent to
     * `actionFor(modeIsB, verdict) == Action.MintB`. Kept as its own
     * function (rather than inlined at call sites) because it is the exact
     * question every pre-#21 caller/test already asked. */
    fun mayMint(modeIsB: Boolean, verdict: M0Probe.Verdict): Boolean =
        actionFor(modeIsB, verdict) == Action.MintB
}
