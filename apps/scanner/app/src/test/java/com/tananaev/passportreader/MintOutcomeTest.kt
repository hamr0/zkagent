package com.tananaev.passportreader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Q36/D66 item 3, FIX pass (Q36 follow-up, 2026-09-03) — MintOutcome.classify
 * tells an honest under-threshold refusal apart from any other verifier-side
 * refusal, driven by THIS DEVICE'S OWN `claimedOverThreshold` answer (a body
 * verdict may only make the outcome stricter, never override an honest
 * `false` back to success — see [MintOutcome]'s class doc), and
 * DirectPostVerdict.parse is best-effort against the PRD §3
 * {ok, allowed, reason} shape. */
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
    fun `no verdict body, own claim over-threshold, is AcceptedOrUnknown`() {
        assertEquals(MintOutcome.Outcome.AcceptedOrUnknown, MintOutcome.classify(null, claimedOverThreshold = true))
    }

    // ---- Q36 follow-up FIX: own answer decides, body only ever tightens ----

    /** "accepted + own-false → under" — the exact `spikes/m2-handoff` shape
     * (`{accepted:true}`, no `allowed` key at all — [DirectPostVerdict.parse]
     * returns `null`): this device's own `over_threshold:false` claim alone
     * must produce the honest refusal, with no body signal at all. This is
     * the reproduction of the bug this FIX closes. */
    @Test
    fun `accepted with no verdict body and own claim under-threshold is HonestUnderThreshold`() {
        assertEquals(MintOutcome.Outcome.HonestUnderThreshold, MintOutcome.classify(null, claimedOverThreshold = false))
    }

    /** "accepted + own-true → success" — no body verdict, own claim over
     * threshold: today's default behaviour, unchanged. */
    @Test
    fun `accepted with no verdict body and own claim over-threshold is AcceptedOrUnknown`() {
        assertEquals(MintOutcome.Outcome.AcceptedOrUnknown, MintOutcome.classify(null, claimedOverThreshold = true))
    }

    /** A body `allowed:true` must NOT override an honest own-false claim
     * back to success — the body can only ever make the outcome STRICTER,
     * never looser. */
    @Test
    fun `body allowed true does not override an honest own-false claim`() {
        val v = DirectPostVerdict(ok = true, allowed = true, reason = null)
        assertEquals(MintOutcome.Outcome.HonestUnderThreshold, MintOutcome.classify(v, claimedOverThreshold = false))
    }

    @Test
    fun `body allowed false with an honest own-false claim is still HonestUnderThreshold, not double-refused`() {
        val v = DirectPostVerdict(ok = true, allowed = false, reason = "under_threshold")
        assertEquals(MintOutcome.Outcome.HonestUnderThreshold, MintOutcome.classify(v, claimedOverThreshold = false))
    }

    @Test
    fun `allowed null (ok false, could not check), own claim over-threshold, is AcceptedOrUnknown, not a refusal`() {
        val v = DirectPostVerdict(ok = false, allowed = null, reason = "verifier_error")
        assertEquals(MintOutcome.Outcome.AcceptedOrUnknown, MintOutcome.classify(v, claimedOverThreshold = true))
    }

    /** "accepted + own-true + body allowed:false → refused-other" — the
     * body verdict DEMOTING an own-true claim is exactly the "stricter
     * only" direction the fix still allows. */
    @Test
    fun `body allowed false with an over-threshold claim demotes to RefusedOtherReason, carrying the reason`() {
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
