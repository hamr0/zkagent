package com.tananaev.passportreader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Q36/D66 item 3 — MintOutcome.classify tells an honest under-threshold
 * refusal apart from any other verifier-side refusal, and DirectPostVerdict
 * .parse is best-effort against the PRD §3 {ok, allowed, reason} shape. */
class MintOutcomeTest {

    // ---- DirectPostVerdict.parse -----------------------------------------

    @Test
    fun `parses a well-formed verdict body`() {
        val v = DirectPostVerdict.parse("""{"ok":true,"allowed":false,"reason":"under_threshold"}""")
        assertEquals(DirectPostVerdict(ok = true, allowed = false, reason = "under_threshold"), v)
    }

    @Test
    fun `body with no allowed field is unknown, not a refusal`() {
        assertNull(DirectPostVerdict.parse("""{"accepted":true}"""))
    }

    @Test
    fun `malformed json is unknown`() {
        assertNull(DirectPostVerdict.parse("not json"))
    }

    @Test
    fun `ok false pairs with allowed null per the sec 3 invariant, read as given`() {
        val v = DirectPostVerdict.parse("""{"ok":false,"allowed":null,"reason":"verifier_error"}""")
        assertEquals(DirectPostVerdict(ok = false, allowed = null, reason = "verifier_error"), v)
    }

    // ---- MintOutcome.classify -----------------------------------------------

    @Test
    fun `no verdict body is AcceptedOrUnknown`() {
        assertEquals(MintOutcome.Outcome.AcceptedOrUnknown, MintOutcome.classify(null, claimedOverThreshold = true))
    }

    @Test
    fun `allowed true is AcceptedOrUnknown regardless of the claim`() {
        val v = DirectPostVerdict(ok = true, allowed = true, reason = null)
        assertEquals(MintOutcome.Outcome.AcceptedOrUnknown, MintOutcome.classify(v, claimedOverThreshold = false))
    }

    @Test
    fun `allowed null (ok false, could not check) is AcceptedOrUnknown, not a refusal`() {
        val v = DirectPostVerdict(ok = false, allowed = null, reason = "verifier_error")
        assertEquals(MintOutcome.Outcome.AcceptedOrUnknown, MintOutcome.classify(v, claimedOverThreshold = true))
    }

    @Test
    fun `allowed false with an honest under-threshold claim is HonestUnderThreshold`() {
        val v = DirectPostVerdict(ok = true, allowed = false, reason = "under_threshold")
        assertEquals(MintOutcome.Outcome.HonestUnderThreshold, MintOutcome.classify(v, claimedOverThreshold = false))
    }

    @Test
    fun `allowed false with an over-threshold claim is RefusedOtherReason, carrying the reason`() {
        val v = DirectPostVerdict(ok = true, allowed = false, reason = "masterlist_miss")
        val outcome = MintOutcome.classify(v, claimedOverThreshold = true)
        assertEquals(MintOutcome.Outcome.RefusedOtherReason("masterlist_miss"), outcome)
    }

    // ---- reportResult text --------------------------------------------------

    @Test
    fun `reportResult is null for AcceptedOrUnknown (existing text unchanged)`() {
        assertNull(MintOutcome.reportResult(MintOutcome.Outcome.AcceptedOrUnknown))
    }

    @Test
    fun `reportResult for HonestUnderThreshold names no numbers about the person`() {
        val text = MintOutcome.reportResult(MintOutcome.Outcome.HonestUnderThreshold)!!
        assertTrue(text.contains("age threshold was not met"))
        assertTrue(Regex("\\d").containsMatchIn(text).not())
    }

    @Test
    fun `reportResult for RefusedOtherReason includes the reason when present`() {
        val text = MintOutcome.reportResult(MintOutcome.Outcome.RefusedOtherReason("masterlist_miss"))!!
        assertTrue(text.contains("masterlist_miss"))
    }

    @Test
    fun `reportResult for RefusedOtherReason with no reason is still non-null`() {
        val text = MintOutcome.reportResult(MintOutcome.Outcome.RefusedOtherReason(null))!!
        assertTrue(text.isNotBlank())
    }
}
