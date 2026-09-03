package com.tananaev.passportreader

/**
 * Finding #20 (`.claude/remember/findings.md`), device-found 2026-09-03:
 * `MainActivity.lockModeAndArm`'s two early-exit guards (incomplete MRZ
 * fields; a pending handoff whose request is still being verified) each
 * showed a Snackbar/return with NO paired log line — a real "Verify" tap
 * that hit either guard looked, on device, identical to nothing happening
 * at all. [evaluate] is that decision extracted to a pure predicate — same
 * pattern as [PaneVisibility.choosePane] / [HandoffAdmission] — so the
 * three-way branch is pinned independently of `EditText`/`Snackbar`/`Log`
 * plumbing, which is unassertable under this module's
 * `unitTests.isReturnDefaultValues = true`.
 *
 * Field-PRESENCE booleans only, by construction — never the field values
 * themselves (this project never logs document fields).
 */
object LockPrecondition {

    sealed class Result {
        /** At least one of the three MRZ-adjacent fields is empty. Each
         * flag is a presence bit only — never the field's value. */
        data class Incomplete(
            val docPresent: Boolean,
            val dobPresent: Boolean,
            val expPresent: Boolean,
        ) : Result()

        /** A handoff is pending but its request has not finished
         * verifying yet — a transient, retry-soon condition. */
        object Verifying : Result()

        /** All fields are present and (if a handoff is pending) its
         * request is already verified — safe to proceed to the existing
         * tier/expiry checks in `lockModeAndArm`. */
        object Ready : Result()
    }

    fun evaluate(
        docPresent: Boolean,
        dobPresent: Boolean,
        expPresent: Boolean,
        handoffPending: Boolean,
        requestVerified: Boolean,
    ): Result {
        if (!docPresent || !dobPresent || !expPresent) {
            return Result.Incomplete(docPresent, dobPresent, expPresent)
        }
        if (handoffPending && !requestVerified) {
            return Result.Verifying
        }
        return Result.Ready
    }
}
