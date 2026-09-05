package com.tananaev.passportreader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Device finding (owner, 2026-09-05, logcat 00:27:40) — pins
 * [VerifierRefusal.classify]'s status/body -> outcome truth table, plus
 * [reportLine]/[dialogMessage]'s exact wording per outcome. See that
 * object's class doc for the full root cause: a hardcoded "verdict: PASS"
 * text and a missing D43 dialog both ignored the `direct_post` HTTP status
 * once the request had merely been SENT.
 */
class VerifierRefusalTest {

    // ---------------------------------------------------------- classify

    @Test
    fun `200 classifies as Sent`() {
        assertEquals(VerifierRefusal.Outcome.Sent, VerifierRefusal.classify(200, "{\"ok\":true}"))
    }

    @Test
    fun `299 (top of the 2xx range) classifies as Sent`() {
        assertEquals(VerifierRefusal.Outcome.Sent, VerifierRefusal.classify(299, ""))
    }

    @Test
    fun `199 (just below the 2xx range) does NOT classify as Sent`() {
        assertTrue(VerifierRefusal.classify(199, "") !is VerifierRefusal.Outcome.Sent)
    }

    @Test
    fun `409 with error already_responded classifies as AlreadyUsed`() {
        // The exact owner device evidence: "handoff: direct_post
        // http_status=409 -> {\"error\":\"already_responded\"}"
        assertEquals(VerifierRefusal.Outcome.AlreadyUsed, VerifierRefusal.classify(409, "{\"error\":\"already_responded\"}"))
    }

    @Test
    fun `409 with a DIFFERENT error field is a generic Refused, not AlreadyUsed`() {
        val outcome = VerifierRefusal.classify(409, "{\"error\":\"conflict\"}")
        assertEquals(VerifierRefusal.Outcome.Refused("conflict"), outcome)
    }

    @Test
    fun `409 with no parseable error field falls back to the HTTP status label`() {
        val outcome = VerifierRefusal.classify(409, "not json")
        assertEquals(VerifierRefusal.Outcome.Refused("HTTP 409"), outcome)
    }

    @Test
    fun `already_responded at a DIFFERENT status is a generic Refused, not AlreadyUsed`() {
        // The (status == 409 AND error == already_responded) pair is what
        // must match — neither alone is sufficient.
        val outcome = VerifierRefusal.classify(400, "{\"error\":\"already_responded\"}")
        assertEquals(VerifierRefusal.Outcome.Refused("already_responded"), outcome)
    }

    @Test
    fun `other 4xx with an error field is Refused, labelled from that field`() {
        assertEquals(VerifierRefusal.Outcome.Refused("invalid_request"), VerifierRefusal.classify(400, "{\"error\":\"invalid_request\"}"))
    }

    @Test
    fun `5xx with no error field is Refused, labelled by HTTP status`() {
        assertEquals(VerifierRefusal.Outcome.Refused("HTTP 500"), VerifierRefusal.classify(500, "internal error"))
    }

    @Test
    fun `an error field that is an empty string falls back to the HTTP status label`() {
        assertEquals(VerifierRefusal.Outcome.Refused("HTTP 403"), VerifierRefusal.classify(403, "{\"error\":\"\"}"))
    }

    // -------------------------------------------------------- errorFieldOf

    @Test
    fun `errorFieldOf extracts a top-level string error field`() {
        assertEquals("already_responded", VerifierRefusal.errorFieldOf("{\"error\":\"already_responded\"}"))
    }

    @Test
    fun `errorFieldOf returns null for non-JSON body`() {
        assertNull(VerifierRefusal.errorFieldOf("not json at all"))
    }

    @Test
    fun `errorFieldOf returns null for an empty body`() {
        assertNull(VerifierRefusal.errorFieldOf(""))
    }

    @Test
    fun `errorFieldOf returns null when the error key is absent`() {
        assertNull(VerifierRefusal.errorFieldOf("{\"ok\":false}"))
    }

    // ------------------------------------------------------- reportLine

    @Test
    fun `reportLine is null for Sent — no call site asks for one`() {
        assertNull(VerifierRefusal.reportLine(VerifierRefusal.Outcome.Sent))
    }

    @Test
    fun `reportLine for AlreadyUsed names the 409 already_responded cause`() {
        val line = VerifierRefusal.reportLine(VerifierRefusal.Outcome.AlreadyUsed)
        assertTrue(line!!.contains("REFUSED"))
        assertTrue(line.contains("already_responded"))
    }

    @Test
    fun `reportLine for a generic Refused names the label`() {
        val line = VerifierRefusal.reportLine(VerifierRefusal.Outcome.Refused("HTTP 500"))
        assertTrue(line!!.contains("REFUSED"))
        assertTrue(line.contains("HTTP 500"))
    }

    // ----------------------------------------------------- dialogMessage

    @Test
    fun `dialogMessage is null for Sent — the existing Accepted dialog is untouched`() {
        assertNull(VerifierRefusal.dialogMessage(VerifierRefusal.Outcome.Sent))
    }

    @Test
    fun `dialogMessage for AlreadyUsed matches the owner-specified wording exactly`() {
        assertEquals(
            "This link was already used — reopen the link from the site.",
            VerifierRefusal.dialogMessage(VerifierRefusal.Outcome.AlreadyUsed),
        )
        assertEquals(VerifierRefusal.ALREADY_USED_MESSAGE, VerifierRefusal.dialogMessage(VerifierRefusal.Outcome.AlreadyUsed))
    }

    @Test
    fun `dialogMessage for a generic Refused names the label with the Verifier refused prefix`() {
        assertEquals("Verifier refused: HTTP 502", VerifierRefusal.dialogMessage(VerifierRefusal.Outcome.Refused("HTTP 502")))
    }
}
