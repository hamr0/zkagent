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
 * M3b chunk 1 — the tier-C request parser, question lines and all-or-nothing answer path for all five predicate kinds.
 * Real `org.json` (see `libs.json`). Every document and site string here is SYNTHETIC; expected values are hand-computed.
 */
class TierCRequestTest {

    private val today = LocalDate.of(2026, 10, 25)

    private fun request(predicates: Any?, tier: String = "C"): JSONObject {
        val z = JSONObject().put("spec", "zkagent/1").put("tier", tier)
        // The predicates ride inside the signed challenge (chiproof item 14), at zkagent.challenge.predicates.
        z.put("challenge", JSONObject().also { c -> if (predicates != null) c.put("predicates", predicates) })
        return JSONObject().put("zkagent", z)
    }

    private fun one(vararg kv: Pair<String, Any?>): JSONObject = JSONObject().also { o -> kv.forEach { (k, v) -> o.put(k, v) } }
    private fun arr(vararg o: JSONObject) = JSONArray().also { a -> o.forEach { a.put(it) } }

    private fun fields(expiry: String? = "270126", dob: String? = "850101") =
        TierCFields.fromRaw(expiry, dob, "NLD", "SPE123456", "DE VRIES", "JAN<PIETER", TierCFields.MrzForm.TD1)

    // ---------------------------------------------------------------- parse

    @Test
    fun `every predicate kind parses to its own type, with its fixed boundary or labelled fields`() {
        val p = TierCRequest.parse(request(arr(
            one("name" to "expiry_beyond", "months" to 6),
            one("name" to "dob_at_least", "age" to 21),
            one("name" to "nationality_is", "code" to "nld"),
            one("name" to "nationality_not_in", "codes" to JSONArray(listOf("CUB", "rus"))),
            one("name" to "name_match", "given_names" to "Jan Pieter", "surname" to "de Vries"),
            one("name" to "document_number_is", "number" to "SPE123456"),
        )))
        assertEquals(
            TierCRequest.Parse.Ok(listOf(
                TierCRequest.Predicate.ExpiryBeyond(6),
                TierCRequest.Predicate.DobAtLeast(21),
                TierCRequest.Predicate.NationalityIs("NLD"),
                TierCRequest.Predicate.NationalityNotIn(listOf("CUB", "RUS")),
                TierCRequest.Predicate.NameMatch("Jan Pieter", "de Vries"),
                TierCRequest.Predicate.DocumentNumberIs("SPE123456"),
            )),
            p,
        )
    }

    @Test
    fun `every fixed boundary on the lists parses, and every off-list value is refused whole`() {
        for (m in listOf(3, 6, 12)) assertTrue(TierCRequest.parse(request(arr(one("name" to "expiry_beyond", "months" to m)))) is TierCRequest.Parse.Ok)
        for (a in listOf(15, 16, 18, 21, 60, 65)) assertTrue(TierCRequest.parse(request(arr(one("name" to "dob_at_least", "age" to a)))) is TierCRequest.Parse.Ok)
        var refused = 0
        for (m in listOf(0, 1, 2, 4, 5, 7, 9, 13, 24)) {
            assertEquals(TierCRequest.Parse.Refuse("boundary_off_list"), TierCRequest.parse(request(arr(one("name" to "expiry_beyond", "months" to m)))))
            refused++
        }
        for (a in listOf(0, 1, 14, 17, 19, 20, 22, 43, 64, 66, 100)) {
            assertEquals(TierCRequest.Parse.Refuse("boundary_off_list"), TierCRequest.parse(request(arr(one("name" to "dob_at_least", "age" to a)))))
            refused++
        }
        assertEquals(20, refused)
    }

    @Test
    fun `every malformed or unsupported shape is refused whole, each paired with the valid shape that parses`() {
        val valid = request(arr(one("name" to "expiry_beyond", "months" to 6)))
        assertTrue(TierCRequest.parse(valid) is TierCRequest.Parse.Ok) // pass pair

        val good = one("name" to "expiry_beyond", "months" to 6)
        val refusals = listOf(
            request(null) to "predicates_absent",
            request("expiry_beyond") to "predicates_absent",
            request(JSONArray()) to "predicates_empty",
            request(JSONArray().put("expiry_beyond")) to "predicate_malformed",
            request(arr(one("name" to "expiry_beyond", "months" to "6"))) to "boundary_malformed", // a quoted number is NOT coerced
            request(arr(one("name" to "expiry_beyond", "months" to 6.0))) to "boundary_malformed",
            request(arr(one("name" to "expiry_beyond"))) to "boundary_malformed",
            request(arr(one("name" to "dob_at_least", "age" to "18"))) to "boundary_malformed",
            request(arr(one("name" to "shoe_size", "size" to 9))) to "predicate_not_in_vocabulary",
            request(arr(one("name" to null))) to "predicate_not_in_vocabulary",
            request(arr(one("name" to "nationality_is", "code" to "N!D"))) to "code_invalid",
            request(arr(one("name" to "nationality_is", "code" to "NLDX"))) to "code_invalid",
            request(arr(one("name" to "nationality_is", "code" to 5))) to "code_invalid",
            request(arr(one("name" to "nationality_not_in", "codes" to JSONArray()))) to "exclusion_set_size",
            request(arr(one("name" to "nationality_not_in", "codes" to JSONArray(listOf("CUB", "R1S"))))) to "code_invalid",
            request(arr(one("name" to "nationality_not_in", "codes" to "CUB"))) to "code_invalid",
            request(arr(one("name" to "name_match", "given_names" to "Jan"))) to "name_fields_malformed", // one field only
            request(arr(one("name" to "name_match", "full_name" to "Jan de Vries"))) to "name_fields_malformed", // the old combined string
            request(arr(one("name" to "name_match", "given_names" to "", "surname" to "de Vries"))) to "name_fields_malformed",
            request(arr(one("name" to "name_match", "given_names" to "Jan", "surname" to "  "))) to "name_fields_malformed",
            request(arr(one("name" to "document_number_is", "number" to ""))) to "number_malformed",
            request(arr(one("name" to "document_number_is", "number" to "<<<"))) to "number_malformed",
            request(arr(one("name" to "document_number_is"))) to "number_malformed",
            // all-or-nothing at parse time: one good predicate beside a bad one refuses the lot
            request(arr(good, one("name" to "dob_at_least", "age" to 19))) to "boundary_off_list",
            request(arr(good, one("name" to "shoe_size"))) to "predicate_not_in_vocabulary",
        )
        for ((req, reason) in refusals) assertEquals(TierCRequest.Parse.Refuse(reason), TierCRequest.parse(req))
        assertTrue(refusals.size > 0)
    }

    @Test
    fun `an exclusion set of exactly ten countries parses and eleven is refused (D102)`() {
        fun set(n: Int) = request(arr(one("name" to "nationality_not_in", "codes" to JSONArray((1..n).map { "ZA${'A' + it}" }))))
        assertTrue(TierCRequest.parse(set(10)) is TierCRequest.Parse.Ok)
        assertTrue(TierCRequest.parse(set(1)) is TierCRequest.Parse.Ok)
        assertEquals(TierCRequest.Parse.Refuse("exclusion_set_size"), TierCRequest.parse(set(11)))
        assertEquals(TierCRequest.Parse.Refuse("exclusion_set_size"), TierCRequest.parse(set(30)))
    }

    @Test
    fun `at most one nationality_is and one nationality_not_in per request, counted by kind (D118)`() {
        fun natIs(c: String) = one("name" to "nationality_is", "code" to c)
        fun natNot(vararg c: String) = one("name" to "nationality_not_in", "codes" to JSONArray(c.toList()))
        val name = one("name" to "name_match", "given_names" to "Jan", "surname" to "de Vries")
        val refused = listOf(
            arr(natIs("NLD"), natIs("DEU")),
            arr(natNot("CUB"), natNot("RUS")),
            arr(natNot("CUB"), name, natNot("RUS")),
            arr(natIs("NLD"), one("name" to "document_number_is", "number" to "X1"), natIs("USA")),
        )
        for (preds in refused) {
            assertEquals(TierCRequest.Parse.Refuse("nationality_asked_more_than_once"), TierCRequest.parse(request(preds)))
        }
        // forty full-size exclusion sets (the route D118 closes) are refused too, not just a pair
        val forty = JSONArray().also { a -> repeat(40) { a.put(natNot("AAA", "BBB", "CCC", "DDD", "EEE", "FFF", "GGG", "HHH", "III", "JJJ")) } }
        assertEquals(TierCRequest.Parse.Refuse("nationality_asked_more_than_once"), TierCRequest.parse(request(forty)))

        // one of each kind is accepted, alone and beside every other kind: no general cap
        assertTrue(TierCRequest.parse(request(arr(natIs("NLD"), natNot("CUB")))) is TierCRequest.Parse.Ok)
        assertTrue(TierCRequest.parse(request(arr(natIs("NLD")))) is TierCRequest.Parse.Ok)
        assertTrue(TierCRequest.parse(request(arr(natNot("CUB")))) is TierCRequest.Parse.Ok)
        val all = arr(
            one("name" to "expiry_beyond", "months" to 6), one("name" to "dob_at_least", "age" to 21),
            natIs("NLD"), natNot("CUB", "RUS"), name, one("name" to "document_number_is", "number" to "ZZ9988776"),
        )
        val parsed = TierCRequest.parse(request(all))
        assertTrue(parsed is TierCRequest.Parse.Ok)
        assertEquals(6, (parsed as TierCRequest.Parse.Ok).predicates.size)
        // the other kinds are not capped by D118
        val repeated = arr(name, name, one("name" to "expiry_beyond", "months" to 3), one("name" to "expiry_beyond", "months" to 12))
        assertTrue(TierCRequest.parse(request(repeated)) is TierCRequest.Parse.Ok)
    }

    @Test
    fun `an ICAO special code stays its own value and is a valid claim`() {
        val p = TierCRequest.parse(request(arr(one("name" to "nationality_is", "code" to "D"))))
        assertEquals(TierCRequest.Parse.Ok(listOf(TierCRequest.Predicate.NationalityIs("D"))), p)
        // and it is NOT mapped: D and DEU are different claims against a document that prints D
        val f = TierCFields.fromRaw("270126", "850101", "D<<", "C01X00T47", "MUSTERMANN", "ERIKA", TierCFields.MrzForm.TD1)
        assertEquals(Answer.True, TierCPredicates.nationalityIs("D", f))
        assertEquals(Answer.False, TierCPredicates.nationalityIs("DEU", f))
    }

    @Test
    fun `isTierC reads the signed tier`() {
        assertTrue(TierCRequest.isTierC(request(null, "C")))
        assertFalse(TierCRequest.isTierC(request(null, "B")))
        assertFalse(TierCRequest.isTierC(JSONObject()))
    }

    // ---------------------------------------------------------------- the question lines (item 7, item 16(a))

    @Test
    fun `one plain-words line per predicate, built from the request, so another request shows other lines`() {
        val lines = TierCRequest.describe(listOf(
            TierCRequest.Predicate.ExpiryBeyond(6),
            TierCRequest.Predicate.DobAtLeast(21),
            TierCRequest.Predicate.NationalityIs("NLD"),
            TierCRequest.Predicate.NationalityNotIn(listOf("CUB", "RUS")),
            TierCRequest.Predicate.NameMatch("Jan", "de Vries"),
            TierCRequest.Predicate.DocumentNumberIs("SPE123456"),
        ))
        assertEquals(
            listOf(
                "Whether your document is valid for more than 6 months from today",
                "Whether you are at least 21 years old",
                "Whether your nationality is NLD",
                "Whether your nationality is none of: CUB, RUS",
                "Whether your name matches the name the website holds",
                "Whether your document number matches the one the website holds",
            ),
            lines,
        )
        val a = TierCRequest.describeRequest(request(arr(one("name" to "expiry_beyond", "months" to 3))))
        val b = TierCRequest.describeRequest(request(arr(one("name" to "expiry_beyond", "months" to 12))))
        assertEquals("• Whether your document is valid for more than 3 months from today", a)
        assertNotEquals(a, b)
        // an unanswerable request still gets a truthful line, not the age sentence and not a blank
        assertEquals(TierCRequest.UNANSWERABLE_LINE, TierCRequest.describeRequest(request(JSONArray())))
    }

    @Test
    fun `the question line never contains the site-supplied name or document number (item 16a)`() {
        val given = "Zqxjw"; val surname = "Vbnmlk"; val number = "ZZ9988776"
        val req = request(arr(
            one("name" to "name_match", "given_names" to given, "surname" to surname),
            one("name" to "document_number_is", "number" to number),
        ))
        val line = TierCRequest.describeRequest(req)
        assertTrue(line.isNotEmpty() && line.contains("name matches") && line.contains("document number matches"))
        for (secret in listOf(given, surname, number, given.lowercase(), surname.lowercase())) assertFalse(secret, line.contains(secret))
        // and the predicates' own toString (what a stray log line would print) is redacted too
        val parsed = (TierCRequest.parse(req) as TierCRequest.Parse.Ok).predicates
        for (p in parsed) for (secret in listOf(given, surname, number)) assertFalse(secret, p.toString().contains(secret))
        assertTrue(parsed.all { it.toString().contains("redacted") })
        // control: the check can fail - the secrets are in the parsed predicates themselves
        val n = parsed[0] as TierCRequest.Predicate.NameMatch
        assertEquals(given, n.givenNames)
    }

    // ---------------------------------------------------------------- answerAll

    @Test
    fun `several predicates are answered from one set of fields, a false being a legitimate answer`() {
        // today 2026-10-25: +6 months = 2027-04-25; expiry 2027-01-26 is not beyond it -> false; +3 months = 2027-01-25 -> true
        val preds = listOf(
            TierCRequest.Predicate.ExpiryBeyond(3),                    // true (one day after 2027-01-25)
            TierCRequest.Predicate.ExpiryBeyond(6),                    // false
            TierCRequest.Predicate.DobAtLeast(21),                     // born 1985-01-01 -> 41: true
            TierCRequest.Predicate.NationalityIs("NLD"),               // true
            TierCRequest.Predicate.NationalityNotIn(listOf("CUB")),    // true
            TierCRequest.Predicate.NameMatch("Jan Pieter", "de Vries"),// true
            TierCRequest.Predicate.DocumentNumberIs("SPE123457"),      // false (one digit off)
        )
        val r = TierCAnswers.answerAll(preds, fields(), today, documentAnswerable = true)
        assertEquals(
            TierCAnswers.Result.Answered(preds.zip(listOf(true, false, true, true, true, true, false)).map { (p, b) -> TierCAnswers.Answered(p, b) }),
            r,
        )
    }

    @Test
    fun `all or nothing - one unanswerable predicate refuses the whole request, even beside answerable ones`() {
        val ok = listOf(TierCRequest.Predicate.ExpiryBeyond(3), TierCRequest.Predicate.NationalityIs("NLD"))
        assertTrue(TierCAnswers.answerAll(ok, fields(), today, true) is TierCAnswers.Result.Answered) // pass pair
        val mixed = ok + TierCRequest.Predicate.DobAtLeast(21)
        // the same request on a document whose date of birth is unreadable refuses the lot, naming a value-free reason
        assertEquals(TierCAnswers.Result.Refused("dob_unreadable"), TierCAnswers.answerAll(mixed, fields(dob = null), today, true))
        assertEquals(TierCAnswers.Result.Refused("dob_unreadable"), TierCAnswers.answerAll(mixed, fields(dob = "85XX01"), today, true))
        assertTrue(TierCAnswers.answerAll(mixed, fields(), today, true) is TierCAnswers.Result.Answered)
        assertEquals(TierCAnswers.Result.Refused("predicates_empty"), TierCAnswers.answerAll(emptyList(), fields(), today, true))
    }

    @Test
    fun `an unreadable field or a document that fails integrity refuses, and never becomes false`() {
        val preds = listOf(TierCRequest.Predicate.ExpiryBeyond(3))
        assertEquals(TierCAnswers.Result.Refused("expiry_unreadable"), TierCAnswers.answerAll(preds, fields(expiry = "27X126"), today, true))
        assertEquals(TierCAnswers.Result.Refused("expiry_unreadable"), TierCAnswers.answerAll(preds, fields(expiry = null), today, true))
        assertEquals(TierCAnswers.Result.Refused("document_not_answerable"), TierCAnswers.answerAll(preds, fields(), today, false))
        assertTrue(TierCAnswers.answerAll(preds, fields(), today, true) is TierCAnswers.Result.Answered)
        // a name predicate on a document with no readable name is a refusal too
        val noName = TierCFields.fromRaw("270126", "850101", "NLD", "SPE123456", null, null, TierCFields.MrzForm.TD1)
        assertEquals(
            TierCAnswers.Result.Refused("name_unreadable"),
            TierCAnswers.answerAll(listOf(TierCRequest.Predicate.NameMatch("Jan", "de Vries")), noName, today, true),
        )
    }

    @Test
    fun `predicates are read from the signed challenge only - the old zkagent-level list is not read`() {
        val list = arr(one("name" to "expiry_beyond", "months" to 3))
        val inChallenge = request(list)
        assertTrue(TierCRequest.parse(inChallenge) is TierCRequest.Parse.Ok)
        // the same list at the POC's old place (zkagent.predicates) and none in the challenge: not a request
        val oldPlace = JSONObject().put("zkagent", JSONObject().put("tier", "C").put("predicates", list).put("challenge", JSONObject()))
        assertEquals(TierCRequest.Parse.Refuse("predicates_absent"), TierCRequest.parse(oldPlace))
        // no challenge object at all
        assertEquals(TierCRequest.Parse.Refuse("predicates_absent"), TierCRequest.parse(JSONObject().put("zkagent", JSONObject().put("tier", "C").put("predicates", list))))
    }

    // ---------------------------------------------------------------- the answer on the wire

    @Test
    fun `the presentation carries the challenge and name, fixed boundary and boolean only - never a site value`() {
        val challenge = JSONObject().put("nonce", "n-1").put("expires_at", 123456789L)
        val answered = listOf(
            TierCAnswers.Answered(TierCRequest.Predicate.ExpiryBeyond(3), true),
            TierCAnswers.Answered(TierCRequest.Predicate.DobAtLeast(21), false),
            TierCAnswers.Answered(TierCRequest.Predicate.NameMatch("Zqxjw", "Vbnmlk"), true),
            TierCAnswers.Answered(TierCRequest.Predicate.DocumentNumberIs("ZZ9988776"), false),
            TierCAnswers.Answered(TierCRequest.Predicate.NationalityNotIn(listOf("CUB", "RUS")), true),
        )
        val p = TierCAnswers.presentation(challenge, answered)
        assertEquals("zkagent/1", p.getString("spec"))
        assertEquals("C", p.getString("tier"))
        assertEquals("n-1", p.getJSONObject("challenge").getString("nonce"))
        val a = p.getJSONArray("answers")
        assertEquals(5, a.length())
        assertEquals(setOf("name", "months", "result"), a.getJSONObject(0).keys().asSequence().toSet())
        assertEquals(setOf("name", "age", "result"), a.getJSONObject(1).keys().asSequence().toSet())
        for (i in 2..4) assertEquals(setOf("name", "result"), a.getJSONObject(i).keys().asSequence().toSet())
        assertEquals(listOf("expiry_beyond", "dob_at_least", "name_match", "document_number_is", "nationality_not_in"), (0 until 5).map { a.getJSONObject(it).getString("name") })
        assertEquals(setOf("spec", "tier", "challenge", "answers"), p.keys().asSequence().toSet())
        val wire = p.toString()
        for (secret in listOf("Zqxjw", "Vbnmlk", "ZZ9988776", "CUB", "RUS", "zktag")) assertFalse(secret, wire.contains(secret))
    }
}
