package com.tananaev.passportreader

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Findings.md #22 — a truth table over every `DeliveryResult` variant's
 * raw report/log "verdict:" line, pinning [DeliveryVerdictLine] as the
 * single place that derives it. Each expectation here is what
 * `MainActivity`'s hardcoded `else -> "verdict: PASS (...)"` fallback got
 * wrong before this fix for every variant but `Accepted`/`Rejected` — see
 * findings.md #22 for the reasoning that these previously all printed
 * "PASS" regardless of what actually happened.
 */
class DeliveryVerdictLineTest {

    @Test
    fun `accepted names what was sent`() {
        assertEquals("verdict: PASS (bare presentation sent)", DeliveryVerdictLine.accepted("bare presentation sent"))
        assertEquals("verdict: PASS (minted)", DeliveryVerdictLine.accepted("minted"))
    }

    @Test
    fun `refusedHonestUnderThreshold is REFUSED, not PASS`() {
        assertEquals("verdict: REFUSED — under threshold, nothing sent", DeliveryVerdictLine.refusedHonestUnderThreshold())
    }

    @Test
    fun `refusedOtherReason names the verifier's reason`() {
        assertEquals("verdict: REFUSED — some_reason, nothing sent", DeliveryVerdictLine.refusedOtherReason("some_reason"))
    }

    @Test
    fun `refusedOtherReason falls back to plain text when the reason is null`() {
        assertEquals("verdict: REFUSED — no reason given, nothing sent", DeliveryVerdictLine.refusedOtherReason(null))
    }

    @Test
    fun `rejected delegates to VerifierRefusal for a generic non-2xx refusal`() {
        assertEquals("verdict: REFUSED — verifier: HTTP 500", DeliveryVerdictLine.rejected(500, "internal error"))
    }

    @Test
    fun `rejected delegates to VerifierRefusal for the 409 already_responded case`() {
        assertEquals(
            "verdict: REFUSED — verifier: link already used (HTTP 409, already_responded)",
            DeliveryVerdictLine.rejected(409, "{\"error\":\"already_responded\"}"),
        )
    }

    @Test
    fun `noResponseUri is NOT SENT, not PASS`() {
        assertEquals("verdict: NOT SENT — request had no response_uri", DeliveryVerdictLine.noResponseUri())
    }

    @Test
    fun `transportFailed is NOT SENT and names the exception label, not PASS`() {
        assertEquals("verdict: NOT SENT — network error: IOException: timeout", DeliveryVerdictLine.transportFailed("IOException: timeout"))
    }
}
