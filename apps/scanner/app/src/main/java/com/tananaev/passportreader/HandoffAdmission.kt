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
 * THIS IS NOT THE FIX. It closes the timing window described above, but it
 * is a point mitigation, not the ownership fix — the structural fix
 * (recorded in the finding, mitigation option (b)) is to snapshot the
 * VERIFIED request into an immutable value at lock time (`SessionState`)
 * and thread it through the read/mint path as a parameter, instead of
 * re-reading mutable fields at all. That is the ownership-refactor's job,
 * not this change's. Once it lands, the race this predicate refuses should
 * no longer be reachable, and this guard should become redundant and be
 * removed — do not treat its presence as evidence the underlying ownership
 * issue (findings #2/#3: `verifiedRequest`/`pendingHandoff` non-`@Volatile`,
 * cross-thread, multi-writer) is resolved.
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
