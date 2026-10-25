package com.tananaev.passportreader

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * M3b POC — the minimal tier-C request/answer path (one predicate shape: expiry bucket). Real `org.json` (see
 * `libs.json`). Every document here is SYNTHETIC; expected values are hand-computed.
 */
class TierCRequestTest {

    private val today = LocalDate.of(2026, 10, 25)

    private fun request(predicates: Any?, tier: String = "C"): JSONObject {
        val z = JSONObject().put("spec", "zkagent/1").put("tier", tier)
        if (predicates != null) z.put("predicates", predicates)
        return JSONObject().put("zkagent", z)
    }

    private fun pred(name: String, months: Any?): JSONObject = JSONObject().put("name", name).put("months", months)

    private fun fields(expiry: String?) = TierCFields.fromRaw(expiry, "850101", "NLD", "SPE123456", "DE VRIES", "JAN", TierCFields.MrzForm.TD1)

    // ---------------------------------------------------------------- parse

    @Test
    fun `a single expiry predicate on the fixed list parses`() {
        for (m in listOf(3, 6, 12)) {
            val p = TierCRequest.parse(request(JSONArray().put(pred("expiry_beyond", m))))
            assertEquals(TierCRequest.Parse.Ok(listOf(TierCRequest.Predicate.ExpiryBeyond(m))), p)
        }
    }

    @Test
    fun `every malformed or unsupported shape is refused whole, each paired with the valid shape that parses`() {
        val valid = request(JSONArray().put(pred("expiry_beyond", 6)))
        assertTrue(TierCRequest.parse(valid) is TierCRequest.Parse.Ok) // pass pair

        val refusals = listOf(
            request(null) to "predicates_absent",
            request("expiry_beyond") to "predicates_absent",
            request(JSONArray()) to "predicates_empty",
            request(JSONArray().put("expiry_beyond")) to "predicate_malformed",
            request(JSONArray().put(pred("expiry_beyond", 4))) to "boundary_off_list",
            request(JSONArray().put(pred("expiry_beyond", 0))) to "boundary_off_list",
            request(JSONArray().put(pred("expiry_beyond", "6"))) to "boundary_malformed", // a quoted number is NOT coerced
            request(JSONArray().put(pred("expiry_beyond", 6.0))) to "boundary_malformed",
            request(JSONArray().put(pred("expiry_beyond", null))) to "boundary_malformed",
            request(JSONArray().put(pred("name_match", 6))) to "predicate_not_in_poc",
            request(JSONArray().put(pred("dob_at_least", 18))) to "predicate_not_in_poc",
            request(JSONArray().put(pred("nationality_is", 1))) to "predicate_not_in_poc",
            // all-or-nothing at parse time: one good predicate beside a bad one refuses the lot
            request(JSONArray().put(pred("expiry_beyond", 6)).put(pred("name_match", 1))) to "predicate_not_in_poc",
        )
        for ((req, reason) in refusals) {
            val got = TierCRequest.parse(req)
            assertEquals(TierCRequest.Parse.Refuse(reason), got)
        }
        assertTrue(refusals.size > 0)
    }

    @Test
    fun `isTierC reads the signed tier`() {
        assertTrue(TierCRequest.isTierC(request(null, "C")))
        assertFalse(TierCRequest.isTierC(request(null, "B")))
        assertFalse(TierCRequest.isTierC(JSONObject()))
    }

    @Test
    fun `the question line is built from the request, so a different request shows a different line`() {
        val three = TierCRequest.describeRequest(request(JSONArray().put(pred("expiry_beyond", 3))))
        val twelve = TierCRequest.describeRequest(request(JSONArray().put(pred("expiry_beyond", 12))))
        assertEquals("does your document expire more than 3 months from today", three)
        assertEquals("does your document expire more than 12 months from today", twelve)
        assertNotEquals(three, twelve)
        // an unanswerable request still gets a truthful line, not the age sentence and not a blank
        assertEquals("something about your document that this app cannot answer", TierCRequest.describeRequest(request(JSONArray())))
    }

    // ---------------------------------------------------------------- answerAll

    @Test
    fun `answers are real booleans and a false is a legitimate answer, not a refusal`() {
        // expiry 2027-01-26 vs today+3mo (2027-01-25) -> true; vs today+6mo (2027-04-25) -> false
        val f = fields("270126")
        val three = TierCAnswers.answerAll(listOf(TierCRequest.Predicate.ExpiryBeyond(3)), f, today, documentAnswerable = true)
        assertEquals(TierCAnswers.Result.Answered(listOf(TierCAnswers.Answered("expiry_beyond", 3, true))), three)
        val six = TierCAnswers.answerAll(listOf(TierCRequest.Predicate.ExpiryBeyond(6)), f, today, documentAnswerable = true)
        assertEquals(TierCAnswers.Result.Answered(listOf(TierCAnswers.Answered("expiry_beyond", 6, false))), six)
    }

    @Test
    fun `all or nothing - one unanswerable predicate refuses the whole request, even beside an answerable one`() {
        val f = fields("270126")
        val ok = TierCAnswers.answerAll(listOf(TierCRequest.Predicate.ExpiryBeyond(3)), f, today, true)
        assertTrue(ok is TierCAnswers.Result.Answered) // pass pair: the first predicate alone is answerable
        val mixed = TierCAnswers.answerAll(
            listOf(TierCRequest.Predicate.ExpiryBeyond(3), TierCRequest.Predicate.ExpiryBeyond(4)), f, today, true,
        )
        assertEquals(TierCAnswers.Result.Refused("boundary_off_list"), mixed)
    }

    @Test
    fun `an unreadable field or a document that fails integrity refuses, and never becomes false`() {
        val preds = listOf(TierCRequest.Predicate.ExpiryBeyond(3))
        assertEquals(TierCAnswers.Result.Refused("expiry_unreadable"), TierCAnswers.answerAll(preds, fields("27X126"), today, true))
        assertEquals(TierCAnswers.Result.Refused("expiry_unreadable"), TierCAnswers.answerAll(preds, fields(null), today, true))
        assertEquals(TierCAnswers.Result.Refused("document_not_answerable"), TierCAnswers.answerAll(preds, fields("270126"), today, false))
        // pass pair on the same fields
        assertTrue(TierCAnswers.answerAll(preds, fields("270126"), today, true) is TierCAnswers.Result.Answered)
    }

    // ---------------------------------------------------------------- the answer on the wire

    @Test
    fun `the POC presentation carries the challenge and name boundary boolean triples only`() {
        val challenge = JSONObject().put("nonce", "n-1").put("expires_at", 123456789L)
        val p = TierCAnswers.presentation(challenge, listOf(TierCAnswers.Answered("expiry_beyond", 3, true)))
        assertEquals("zkagent-poc-tierc/0", p.getString("spec"))
        assertEquals("C", p.getString("tier"))
        assertEquals("n-1", p.getJSONObject("challenge").getString("nonce"))
        val answers = p.getJSONArray("answers")
        assertEquals(1, answers.length())
        val a = answers.getJSONObject(0)
        assertEquals(setOf("name", "months", "result"), a.keys().asSequence().toSet())
        assertEquals("expiry_beyond", a.getString("name"))
        assertEquals(3, a.getInt("months"))
        assertEquals(true, a.getBoolean("result"))
        // no member that could carry a document value, a zktag or an identity
        assertEquals(setOf("spec", "tier", "challenge", "answers"), p.keys().asSequence().toSet())
        assertFalse(p.toString().contains("zktag"))
    }
}
