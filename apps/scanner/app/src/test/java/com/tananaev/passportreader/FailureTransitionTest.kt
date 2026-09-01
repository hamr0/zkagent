package com.tananaev.passportreader

import net.sf.scuba.smartcards.CardServiceException
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
 */
class FailureTransitionTest {

    // ------------------------------------------------------- keepsMrzAndMode

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
}
