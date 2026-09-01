package com.tananaev.passportreader

/**
 * §6.2 item 3's mint gate, extracted to a pure function so the
 * "mode A (and a masterlist real-no / integrity failure) NEVER mints"
 * invariant has a unit test independent of the Android UI plumbing around
 * it, and so [MainActivity.continueAfterRead] has exactly one place that
 * computes this boolean — never re-derive the expression inline elsewhere.
 *
 * ROOT CAUSE NOTE (2026-08-31 real-device runs, second/third tap): this
 * boolean being `false` was never the bug. The bug was that the branch
 * MainActivity took when this is `false` set `reportView.text` directly
 * without ever calling `Log.i` — so a real, correctly-computed "gate not
 * met" verdict rendered on screen while logcat stayed silent, which is
 * exactly what run 2 (mode A, always false here) and run 3 (mode B, false
 * here because passive auth did not return ok:true/allowed:true that time)
 * both did. See MainActivity's `emitReport` doc.
 */
object MintGate {
    fun mayMint(modeIsB: Boolean, verdict: M0Probe.Verdict): Boolean =
        modeIsB && verdict.ok && verdict.allowed == true
}
