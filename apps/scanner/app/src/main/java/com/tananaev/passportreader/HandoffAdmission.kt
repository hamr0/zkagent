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
 * prevents: `applyHandoffVerificationOutcome` (the async verify callback
 * `beginHandoffVerification` schedules) calls `refreshModeStatus()` and sets
 * `lockButton.isEnabled = true` UNCONDITIONALLY on a successful verify —
 * `refreshModeStatus`'s own doc says it must "NEVER [be] called while
 * [lockedMode] is set," but nothing in code enforces that; only THIS guard
 * refusing the intent before `beginHandoffVerification` ever runs today
 * keeps that invariant. Removing this guard would let an admitted foreign
 * intent (mid-lock) stomp the "Locked: mode X — tap your document now"
 * banner with the attacker's "verifying…"/tier text and cosmetically
 * re-enable the lock button (inert — `lockModeAndArm`'s own `lockedMode !=
 * null` early return still no-ops a second tap) while the LOCKED session's
 * evidence remains completely uncorrupted (the snapshot already handles
 * that). Fixing THAT display defect is `modeStatusView`/`lockButton
 * .isEnabled` territory — explicitly out of D58 step 3's scope (that step's
 * MUST NOT list) — so this guard stays until whichever step owns that
 * projection cluster (D58 step 4, session projections) either fixes
 * `applyHandoffVerificationOutcome` to respect `lockedMode` itself, or
 * confirms some other reason removal is safe. Do not remove this guard on
 * the strength of the snapshot alone.
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
}
