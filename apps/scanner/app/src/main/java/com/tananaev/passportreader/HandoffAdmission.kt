package com.tananaev.passportreader

/**
 * Finding #10 (`.claude/remember/findings.md`) — MITIGATION for the
 * induced-handoff race: an `av://` intent arriving while a session is
 * locked, or while a chip read is literally in flight, previously
 * overwrote `pendingHandoff`/`verifiedRequest` with no guard at all (the
 * NFC tag-intent branch immediately below it in
 * `MainActivity.handleIncomingIntent` already checks `lockedMode`).
 * [mayAdmitInboundHandoff] is that guard's decision, extracted to a pure
 * predicate — same pattern as [PaneVisibility.choosePane] / [MintGate.mayMint]
 * — so the truth table is pinned independently of `Intent`/`Log`/dialog
 * plumbing, unassertable under this module's
 * `unitTests.isReturnDefaultValues = true`.
 *
 * THIS WAS NOT THE FIX for findings #2/#3 — it closed the timing window
 * described above, but was always a point mitigation, not the ownership
 * fix. D58 step 3 landed that structural fix: `AuthorizedHandoff`, an
 * immutable snapshot of the verified request taken at lock time
 * (`MainActivity.lockModeAndArm`) and threaded through the read/mint
 * pipeline as a parameter from there on — see that class's doc. The
 * MINT-CORRECTNESS half of finding #10 (evidence signed against a
 * superseded request) is now closed by construction: the pipeline never
 * re-reads `pendingHandoff`/`verifiedRequest` after lock time, so an
 * admitted `av://` intent overwriting those fields mid-read or
 * mid-biometric-prompt cannot change what an in-flight mint signs.
 *
 * D58 step 3 KEPT this guard rather than removing it, per that step's own
 * "if removal is not safe yet, keep it and report why" rule — tracing what
 * an ADMITTED foreign intent does mid-session (with the guard hypothetically
 * removed) found a SEPARATE, still-live regression this guard alone
 * prevented AT THAT TIME: `applyHandoffVerificationOutcome` (the async
 * verify callback `beginHandoffVerification` schedules) called
 * `refreshModeStatus()` and set `lockButton.isEnabled = true`
 * UNCONDITIONALLY on a successful verify — `refreshModeStatus`'s own doc
 * said it must "NEVER [be] called while [lockedMode] is set," but nothing
 * in code enforced that; only this guard refusing the intent before
 * `beginHandoffVerification` ever ran kept that invariant.
 *
 * **D58 step 4 status update**: that regression is now closed BY
 * CONSTRUCTION, independent of this guard — see [SessionDisplay]'s class
 * doc. `applyHandoffVerificationOutcome` and `beginHandoffVerification` now
 * both go through `MainActivity.refreshSessionDisplay`/`applySessionDisplay`,
 * which read `lockedMode` FRESH at the instant each actually runs and give
 * it absolute precedence over whatever handoff-verification outcome is
 * being rendered. An admitted foreign intent's verification resolving
 * after the legitimate session has since locked can no longer write
 * anything but the SAME locked-banner projection every other call site
 * already renders — this holds EVEN IF this guard were removed, because
 * the projection layer, not this admission guard, is now what prevents the
 * display corruption. **This guard is still NOT removed by D58 step 4** —
 * see that step's own evidence doc for the full re-derived trace and the
 * coder's recommendation, which is for the owner to decide (removal needs
 * a device test this step could not run). What this guard's continued
 * presence still buys, independent of the display question above: it
 * keeps a foreign handoff from overwriting `pendingHandoff`/`verifiedRequest`
 * (the mutable fields) at all while a session is locked or reading — a
 * narrower, still-real concern than the display one, and the reason D58
 * step 4's own recommendation is conditional rather than unconditional.
 */
object HandoffAdmission {
    /**
     * @param sessionLocked `MainActivity.lockedMode != null`.
     * @param readInProgress `MainActivity.readInProgress` — a chip read is
     *   literally in flight (inside `ReadTask`'s background thread).
     * @return `false` (refuse) while either is true; `true` (admit)
     *   otherwise — matching the NFC tag-intent branch's existing
     *   `lockedMode == null` guard, plus the `readInProgress` check that
     *   branch was already missing (see finding #6, a separate open item
     *   not in this change's scope).
     */
    fun mayAdmitInboundHandoff(sessionLocked: Boolean, readInProgress: Boolean): Boolean =
        !sessionLocked && !readInProgress

    /**
     * Finding #6 (`.claude/remember/findings.md`) FIX — the NFC tag-intent
     * branch of `MainActivity.handleIncomingIntent` checked `lockedMode !=
     * null` and non-empty MRZ fields, but never `paneState.readInProgress`,
     * so a tag intent arriving while a read was already in flight was not
     * excluded. This is that branch's admission decision, extracted to a
     * pure predicate — same pattern as [mayAdmitInboundHandoff].
     *
     * Deliberately kept in this object rather than a new one: both
     * predicates gate the same two source fields (`lockedMode != null`,
     * `paneState.readInProgress`) for the two intent branches of the same
     * function, so grouping them keeps that shared vocabulary in one place.
     * Its polarity is the OPPOSITE of [mayAdmitInboundHandoff]'s
     * `sessionLocked` term, though: a tag read REQUIRES a locked session —
     * `mode`/`snapshot` come from it — so [mayAdmitInboundHandoff]'s
     * predicate is the wrong one to reuse here (that function's own doc
     * flagged this branch as a separate, then-open item).
     *
     * @param sessionLocked `MainActivity.lockedMode != null`.
     * @param readInProgress `MainActivity.paneState.readInProgress` — a chip
     *   read is literally in flight (inside `ReadTask`'s background thread).
     * @return `true` (admit — start the read) only when the session is
     *   locked AND no read is already in progress; `false` (refuse)
     *   otherwise.
     */
    fun mayStartTagRead(sessionLocked: Boolean, readInProgress: Boolean): Boolean =
        sessionLocked && !readInProgress
}
