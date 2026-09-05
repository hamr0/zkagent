package com.tananaev.passportreader

import net.sf.scuba.smartcards.CardServiceException
import org.jmrtd.AccessDeniedException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/**
 * §6.2 item 15 (D43)'s three-bucket state-transition rule, pinned the same
 * way [MintGateTest] pins item 3's gate. Bucket 1 (access-establishment)
 * and bucket 2 (transient chip-communication failure) both keep MRZ+mode;
 * everything else (bucket 3) resets via the same `keepMrzAndMode` branch,
 * never a second policy.
 *
 * 2026-09, second round: this suite is what let a real bug through —
 * `keepsMrzAndMode`'s MAPPING was correct (both buckets keep MRZ+mode) but
 * nothing here pinned WHICH bucket a given exception resolves to, which is
 * exactly what determined the message the user saw. The `classify`/
 * `isAccessEstablishmentFailure` tests below close that gap.
 */
class FailureTransitionTest {

    // ------------------------------------------------------- keepsMrzAndMode (booleans)

    @Test
    fun `bucket 1 - access-establishment failure keeps MRZ and mode`() {
        assertTrue(FailureTransition.keepsMrzAndMode(isAccessEstablishmentFailure = true, isTransientChipCommunicationFailure = false))
    }

    @Test
    fun `bucket 2 - transient chip-communication failure keeps MRZ and mode`() {
        assertTrue(FailureTransition.keepsMrzAndMode(isAccessEstablishmentFailure = false, isTransientChipCommunicationFailure = true))
    }

    @Test
    fun `bucket 3 - neither bucket applies, resets the session`() {
        assertFalse(FailureTransition.keepsMrzAndMode(isAccessEstablishmentFailure = false, isTransientChipCommunicationFailure = false))
    }

    @Test
    fun `both buckets true (defensive) still keeps MRZ and mode`() {
        assertTrue(FailureTransition.keepsMrzAndMode(isAccessEstablishmentFailure = true, isTransientChipCommunicationFailure = true))
    }

    // ------------------------------------------------------- keepsLockedMode
    //
    // Owner device fix (2026-09-05) — "wrong details entry still doesn't
    // reset to re-enter". Truth table over all four boolean combinations
    // (only three are reachable from a real classification, matching
    // keepsMrzAndMode's own defensive-fourth-case precedent above): only
    // TRANSIENT keeps the lock; ACCESS_ESTABLISHMENT now releases it (the
    // decoupling this fix introduces) so a corrected field can be
    // re-locked via a fresh Scan/Verify tap.

    @Test
    fun `bucket 1 - access-establishment failure RELEASES the lock (the fix)`() {
        assertFalse(FailureTransition.keepsLockedMode(isAccessEstablishmentFailure = true, isTransientChipCommunicationFailure = false))
    }

    @Test
    fun `bucket 2 - transient chip-communication failure KEEPS the lock (unchanged)`() {
        assertTrue(FailureTransition.keepsLockedMode(isAccessEstablishmentFailure = false, isTransientChipCommunicationFailure = true))
    }

    @Test
    fun `bucket 3 - neither bucket applies, lock is already released`() {
        assertFalse(FailureTransition.keepsLockedMode(isAccessEstablishmentFailure = false, isTransientChipCommunicationFailure = false))
    }

    @Test
    fun `both buckets true (defensive) still keeps the lock - transient wins`() {
        assertTrue(FailureTransition.keepsLockedMode(isAccessEstablishmentFailure = true, isTransientChipCommunicationFailure = true))
    }

    @Test
    fun `keepsLockedMode(Classification) - TRANSIENT_CHIP_COMMUNICATION keeps the lock`() {
        assertTrue(FailureTransition.keepsLockedMode(FailureTransition.Classification.TRANSIENT_CHIP_COMMUNICATION))
    }

    @Test
    fun `keepsLockedMode(Classification) - ACCESS_ESTABLISHMENT releases the lock`() {
        assertFalse(FailureTransition.keepsLockedMode(FailureTransition.Classification.ACCESS_ESTABLISHMENT))
    }

    @Test
    fun `keepsLockedMode(Classification) - UNCLASSIFIED releases the lock`() {
        assertFalse(FailureTransition.keepsLockedMode(FailureTransition.Classification.UNCLASSIFIED))
    }

    // ------------------------------------- pending handoff survives (bucket 1)
    //
    // The regression this fix must not introduce: releasing the lock on an
    // access-establishment failure must NOT cost the pending/verified
    // handoff. `MainActivity.showBlockingOutcomeDialog`'s OK handler nulls
    // `pendingHandoff`/`verifiedRequest`/`authorizedHandoff` ONLY when
    // `keepsMrzAndMode` is false — this pins, side by side, that bucket 1
    // still answers `keepsMrzAndMode = true` (handoff survives) at the
    // EXACT SAME TIME `keepsLockedMode` now answers `false` (lock releases)
    // — the two decisions the fix deliberately decoupled.
    @Test
    fun `bucket 1 keeps the handoff (keepsMrzAndMode) while releasing the lock (keepsLockedMode)`() {
        val classification = FailureTransition.Classification.ACCESS_ESTABLISHMENT
        assertTrue("pending handoff must survive an access-establishment failure", FailureTransition.keepsMrzAndMode(classification))
        assertFalse("the lock must release so a corrected field can be re-locked", FailureTransition.keepsLockedMode(classification))
    }

    @Test
    fun `bucket 2 keeps BOTH the handoff and the lock, unchanged by this fix`() {
        val classification = FailureTransition.Classification.TRANSIENT_CHIP_COMMUNICATION
        assertTrue(FailureTransition.keepsMrzAndMode(classification))
        assertTrue(FailureTransition.keepsLockedMode(classification))
    }

    // ------------------------------------------------- keepsMrzAndMode (Classification)

    @Test
    fun `keepsMrzAndMode(Classification) - TRANSIENT_CHIP_COMMUNICATION keeps MRZ and mode`() {
        assertTrue(FailureTransition.keepsMrzAndMode(FailureTransition.Classification.TRANSIENT_CHIP_COMMUNICATION))
    }

    @Test
    fun `keepsMrzAndMode(Classification) - ACCESS_ESTABLISHMENT keeps MRZ and mode`() {
        assertTrue(FailureTransition.keepsMrzAndMode(FailureTransition.Classification.ACCESS_ESTABLISHMENT))
    }

    @Test
    fun `keepsMrzAndMode(Classification) - UNCLASSIFIED resets`() {
        assertFalse(FailureTransition.keepsMrzAndMode(FailureTransition.Classification.UNCLASSIFIED))
    }

    // ---------------------------------------- isTransientChipCommunicationFailure

    @Test
    fun `real-device case - CardServiceException Tag was lost, wrapped in IOException, is transient`() {
        val cardServiceException = CardServiceException("Tag was lost")
        val wrapped = IOException("Unexpected exception", cardServiceException)
        assertTrue(FailureTransition.isTransientChipCommunicationFailure(wrapped))
    }

    @Test
    fun `a bare unwrapped CardServiceException with the tag-lost message is transient`() {
        assertTrue(FailureTransition.isTransientChipCommunicationFailure(CardServiceException("Tag was lost")))
    }

    @Test
    fun `matching is case-insensitive`() {
        assertTrue(FailureTransition.isTransientChipCommunicationFailure(CardServiceException("TAG WAS LOST")))
        assertTrue(FailureTransition.isTransientChipCommunicationFailure(CardServiceException("tag was lost during read")))
    }

    @Test
    fun `a CardServiceException with a DIFFERENT message is NOT classified as transient`() {
        // e.g. a real protocol/security error — must fall through to reset,
        // never guessed toward "keep" just because the type matches.
        assertFalse(FailureTransition.isTransientChipCommunicationFailure(CardServiceException("Security status not satisfied")))
    }

    @Test
    fun `a non-CardServiceException anywhere in the chain is NOT classified as transient`() {
        assertFalse(FailureTransition.isTransientChipCommunicationFailure(IOException("Tag was lost")))
        assertFalse(FailureTransition.isTransientChipCommunicationFailure(RuntimeException("Tag was lost")))
    }

    @Test
    fun `null throwable cannot be classified - falls through to reset`() {
        assertFalse(FailureTransition.isTransientChipCommunicationFailure(null))
    }

    @Test
    fun `a CardServiceException with a null message cannot be classified - falls through to reset`() {
        assertFalse(FailureTransition.isTransientChipCommunicationFailure(CardServiceException(null)))
    }

    @Test
    fun `the marker is found several levels deep in the cause chain`() {
        val root = CardServiceException("Tag was lost")
        val middle = RuntimeException("intermediate wrap", root)
        val outer = IOException("Unexpected exception", middle)
        assertTrue(FailureTransition.isTransientChipCommunicationFailure(outer))
    }

    @Test
    fun `an unrelated exception with no CardServiceException anywhere cannot be classified - falls through to reset`() {
        val chain = IOException("Unexpected exception", RuntimeException("some other cause"))
        assertFalse(FailureTransition.isTransientChipCommunicationFailure(chain))
    }

    // ------------------------------------------------- isAccessEstablishmentFailure

    @Test
    fun `real-device case - AccessDeniedException Mutual authentication failed is an access-establishment failure`() {
        val e = AccessDeniedException("Mutual authentication failed", 0x6985)
        assertTrue(FailureTransition.isAccessEstablishmentFailure(e))
    }

    @Test
    fun `AccessDeniedException is recognised even wrapped in another exception`() {
        val wrapped = IOException("Unexpected exception", AccessDeniedException("Mutual authentication failed", 0x6985))
        assertTrue(FailureTransition.isAccessEstablishmentFailure(wrapped))
    }

    @Test
    fun `a plain CardServiceException (not AccessDeniedException) is NOT an access-establishment failure`() {
        // AccessDeniedException IS-A CardServiceException in the JMRTD/scuba
        // hierarchy — this pins that the base type alone does not qualify,
        // only the specific subtype JMRTD actually raises for a rejected key.
        assertFalse(FailureTransition.isAccessEstablishmentFailure(CardServiceException("Tag was lost")))
    }

    @Test
    fun `null throwable is not an access-establishment failure`() {
        assertFalse(FailureTransition.isAccessEstablishmentFailure(null))
    }

    @Test
    fun `an unrelated exception is not an access-establishment failure`() {
        assertFalse(FailureTransition.isAccessEstablishmentFailure(IOException("Unexpected exception", RuntimeException("some other cause"))))
    }

    // --------------------------------------------------------------- classify

    // THE BUG this round fixed: a tag-loss occurring DURING access
    // establishment (before PACE/BAC completes) must classify as
    // TRANSIENT, never as ACCESS_ESTABLISHMENT — classification is by
    // exception evidence, never by which code path was executing.
    @Test
    fun `a tag-loss during access establishment classifies as TRANSIENT, not access-establishment`() {
        val e = IOException("Unexpected exception", CardServiceException("Tag was lost"))
        assertEquals(FailureTransition.Classification.TRANSIENT_CHIP_COMMUNICATION, FailureTransition.classify(e))
    }

    @Test
    fun `a real AccessDeniedException (mutual-auth failure) classifies as ACCESS_ESTABLISHMENT`() {
        val e = AccessDeniedException("Mutual authentication failed", 0x6985)
        assertEquals(FailureTransition.Classification.ACCESS_ESTABLISHMENT, FailureTransition.classify(e))
    }

    @Test
    fun `precedence - an exception matching both markers classifies as TRANSIENT`() {
        // A CardServiceException subtype (AccessDeniedException) whose
        // message ALSO happens to contain the tag-lost marker — contrived,
        // but exactly what the precedence rule must resolve deterministically.
        val e = AccessDeniedException("Tag was lost", 0x6985)
        assertEquals(FailureTransition.Classification.TRANSIENT_CHIP_COMMUNICATION, FailureTransition.classify(e))
    }

    @Test
    fun `an unrecognised exception still falls to UNCLASSIFIED (reset)`() {
        val e = IOException("Unexpected exception", RuntimeException("some other cause"))
        assertEquals(FailureTransition.Classification.UNCLASSIFIED, FailureTransition.classify(e))
    }

    @Test
    fun `null throwable classifies as UNCLASSIFIED (reset)`() {
        assertEquals(FailureTransition.Classification.UNCLASSIFIED, FailureTransition.classify(null))
    }
}
