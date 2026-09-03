package com.tananaev.passportreader

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Finding #20 (`.claude/remember/findings.md`) — pins
 * [LockPrecondition.evaluate]'s three-way branch independently of
 * `MainActivity`'s own view/log plumbing. Expectations below are derived
 * from the predicate's own stated contract (Incomplete wins over
 * Verifying, which wins over Ready), never copied from the production
 * implementation's constants.
 */
class LockPreconditionTest {

    @Test
    fun `all fields present, no handoff pending is Ready`() {
        assertEquals(
            LockPrecondition.Result.Ready,
            LockPrecondition.evaluate(
                docPresent = true, dobPresent = true, expPresent = true,
                handoffPending = false, requestVerified = false,
            ),
        )
    }

    @Test
    fun `all fields present, handoff pending and already verified is Ready`() {
        assertEquals(
            LockPrecondition.Result.Ready,
            LockPrecondition.evaluate(
                docPresent = true, dobPresent = true, expPresent = true,
                handoffPending = true, requestVerified = true,
            ),
        )
    }

    @Test
    fun `all fields present, handoff pending but not yet verified is Verifying`() {
        assertEquals(
            LockPrecondition.Result.Verifying,
            LockPrecondition.evaluate(
                docPresent = true, dobPresent = true, expPresent = true,
                handoffPending = true, requestVerified = false,
            ),
        )
    }

    @Test
    fun `missing document number alone is Incomplete with only that flag false`() {
        assertEquals(
            LockPrecondition.Result.Incomplete(docPresent = false, dobPresent = true, expPresent = true),
            LockPrecondition.evaluate(
                docPresent = false, dobPresent = true, expPresent = true,
                handoffPending = false, requestVerified = false,
            ),
        )
    }

    @Test
    fun `missing date of birth alone is Incomplete with only that flag false`() {
        assertEquals(
            LockPrecondition.Result.Incomplete(docPresent = true, dobPresent = false, expPresent = true),
            LockPrecondition.evaluate(
                docPresent = true, dobPresent = false, expPresent = true,
                handoffPending = false, requestVerified = false,
            ),
        )
    }

    @Test
    fun `missing expiry date alone is Incomplete with only that flag false`() {
        assertEquals(
            LockPrecondition.Result.Incomplete(docPresent = true, dobPresent = true, expPresent = false),
            LockPrecondition.evaluate(
                docPresent = true, dobPresent = true, expPresent = false,
                handoffPending = false, requestVerified = false,
            ),
        )
    }

    @Test
    fun `all fields missing is Incomplete with all three flags false`() {
        assertEquals(
            LockPrecondition.Result.Incomplete(docPresent = false, dobPresent = false, expPresent = false),
            LockPrecondition.evaluate(
                docPresent = false, dobPresent = false, expPresent = false,
                handoffPending = true, requestVerified = true,
            ),
        )
    }

    @Test
    fun `incomplete fields win over a pending unverified handoff`() {
        assertEquals(
            LockPrecondition.Result.Incomplete(docPresent = false, dobPresent = true, expPresent = true),
            LockPrecondition.evaluate(
                docPresent = false, dobPresent = true, expPresent = true,
                handoffPending = true, requestVerified = false,
            ),
        )
    }
}
