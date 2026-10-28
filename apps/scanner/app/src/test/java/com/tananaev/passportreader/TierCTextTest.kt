package com.tananaev.passportreader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** M3b chunk 1, finding #27 - the user-facing sentences are plain words and carry no developer term or value. */
class TierCTextTest {

    private val developerTerms = listOf("tier", "Tier", "gate", "pin ", "pinned", "mode C", "Mode C", "operator", "predicate", "no tier-C flow", "POC", "build")

    private val told = TierCText.Delivery.Told(200)
    private val plan = TierCRefusalReply.Plan("https://v.example/cb", null)

    @Test
    fun `refusal dialogs - specific start then the shared ending, exact strings, before the read`() {
        val R = TierCAdmission.Refusal.values().associateBy { it.name }
        val cases = listOf(
            TierCAdmission.Refusal.HOLDER_SETTING_OFF to "Additional verifications are turned off. You can turn them on in Settings.",
            TierCAdmission.Refusal.REQUEST_NOT_ANSWERABLE to "This website asked something this app does not answer.",
            TierCAdmission.Refusal.AGE_NOT_LOCKED_AGE to "This website may only ask about one age, and this question asks about a different one.",
            TierCAdmission.Refusal.AGE_CONFLICT_IN_REQUEST to "This website may only ask about one age, and this question asks about a different one.",
        )
        for ((r, start) in cases) {
            assertEquals(r.name, "$start Nothing was read. The website was only told that the request was refused.", TierCText.refusalDialog(r, false, told))
            assertEquals(r.name, "$start Nothing was read and nothing was sent.", TierCText.refusalDialog(r, false, TierCText.Delivery.Unreachable(false)))
        }
        assertEquals(TierCAdmission.Refusal.values().size, R.size)
    }

    @Test
    fun `refusal dialogs after the read say no answers were sent`() {
        val start = "Additional verifications are turned off. You can turn them on in Settings."
        assertEquals("$start No answers were sent. The website was only told that the request was refused.", TierCText.refusalDialog(TierCAdmission.Refusal.HOLDER_SETTING_OFF, true, told))
        assertEquals("$start No answers were sent.", TierCText.refusalDialog(TierCAdmission.Refusal.HOLDER_SETTING_OFF, true, TierCText.Delivery.NotAccepted(500)))
    }

    @Test
    fun `operator switch off and not-approved sites, where no notice goes out, keep the nothing-sent wording`() {
        assertEquals(
            "This version of the app does not do additional verifications, so nothing was read and nothing was sent.",
            TierCText.refusalDialog(TierCAdmission.Refusal.OPERATOR_SWITCH_OFF, false, TierCText.Delivery.NoNotice),
        )
        assertEquals(
            "This website is not approved to ask additional questions about your document. Nothing was read and nothing was sent.",
            TierCText.refusalDialog(TierCAdmission.Refusal.NOT_PINNED, false, TierCText.Delivery.NoNotice),
        )
    }

    @Test
    fun `the website-was-told ending appears only for a 2xx - never for a failed post, a 409 or another status`() {
        val notTold = listOf(
            TierCText.Delivery.NoNotice, TierCText.Delivery.AlreadyResponded, TierCText.Delivery.NotAccepted(500),
            TierCText.Delivery.NotAccepted(403), TierCText.Delivery.Unreachable(false), TierCText.Delivery.Unreachable(true),
        )
        var checked = 0
        for (r in TierCAdmission.Refusal.values()) for (afterRead in listOf(false, true)) for (d in notTold) {
            assertFalse("$r $afterRead $d", TierCText.refusalDialog(r, afterRead, d).contains("only told"))
            assertFalse(TierCText.couldNotAnswerDialog(d).contains("only told"))
            checked++
        }
        assertTrue(checked > 0)
        assertTrue(TierCText.couldNotAnswerDialog(told).contains("only told"))
    }

    @Test
    fun `delivery is classified from the real outcome - 2xx told, 409 already responded, others not accepted, failures unreachable`() {
        assertEquals(TierCText.Delivery.NoNotice, TierCText.Delivery.from(null, null))
        assertEquals(TierCText.Delivery.NoNotice, TierCText.Delivery.from(null, TierCRefusalReply.Outcome.Sent(200)))
        assertEquals(TierCText.Delivery.Told(200), TierCText.Delivery.from(plan, TierCRefusalReply.Outcome.Sent(200)))
        assertEquals(TierCText.Delivery.Told(204), TierCText.Delivery.from(plan, TierCRefusalReply.Outcome.Sent(204)))
        assertEquals(TierCText.Delivery.AlreadyResponded, TierCText.Delivery.from(plan, TierCRefusalReply.Outcome.Sent(409)))
        assertEquals(TierCText.Delivery.NotAccepted(400), TierCText.Delivery.from(plan, TierCRefusalReply.Outcome.Sent(400)))
        assertEquals(TierCText.Delivery.NotAccepted(302), TierCText.Delivery.from(plan, TierCRefusalReply.Outcome.Sent(302)))
        assertEquals(TierCText.Delivery.Unreachable(false), TierCText.Delivery.from(plan, TierCRefusalReply.Outcome.Failed("ConnectException")))
        assertEquals(TierCText.Delivery.Unreachable(true), TierCText.Delivery.from(plan, TierCRefusalReply.Outcome.Failed("SocketTimeoutException")))
    }

    @Test
    fun `the Log line carries the reason and whether the refusal reached the site, with no value`() {
        val reason = "Refused: additional verifications are turned off in Settings"
        assertEquals("Refused: additional verifications are turned off in Settings. Refusal sent to the website (HTTP 200).", TierCText.logLine(reason, told))
        assertEquals("$reason. Refusal could not be sent (unreachable).", TierCText.logLine(reason, TierCText.Delivery.Unreachable(false)))
        assertEquals("$reason. Refusal could not be sent (no reply in time).", TierCText.logLine(reason, TierCText.Delivery.Unreachable(true)))
        assertEquals("$reason. Refusal could not be sent (the website answered HTTP 503).", TierCText.logLine(reason, TierCText.Delivery.NotAccepted(503)))
        assertEquals("$reason. Refusal not sent: the website had already received a response (HTTP 409).", TierCText.logLine(reason, TierCText.Delivery.AlreadyResponded))
        assertEquals("$reason. No refusal notice is sent for this request.", TierCText.logLine(reason, TierCText.Delivery.NoNotice))
        assertEquals("Refused: this website is not approved to ask additional questions", TierCText.refusalResult(TierCAdmission.Refusal.NOT_PINNED))
        for (r in TierCAdmission.Refusal.values()) for (term in developerTerms) assertFalse("${r.name} '$term'", TierCText.refusalResult(r).contains(term))
    }

    @Test
    fun `the report sent-line claims the notice only when it was told`() {
        assertEquals("nothing left this device", TierCText.refusalSent(TierCText.Delivery.NoNotice))
        assertEquals("nothing the website accepted", TierCText.refusalSent(TierCText.Delivery.Unreachable(false)))
        assertEquals("nothing the website accepted", TierCText.refusalSent(TierCText.Delivery.AlreadyResponded))
        assertEquals("a plain \"refused\" notice to the website; no reason, nothing about you or your document", TierCText.refusalSent(told))
    }

    @Test
    fun `dialogs carry no developer term`() {
        for (r in TierCAdmission.Refusal.values()) for (d in listOf(told, TierCText.Delivery.NoNotice)) for (term in developerTerms) {
            assertFalse("${r.name} '$term'", TierCText.refusalDialog(r, false, d).contains(term))
            assertFalse("'$term'", TierCText.couldNotAnswerDialog(d).contains(term))
        }
    }

    @Test
    fun `the completion message says it is done, how many yes or no answers went, and what to do next`() {
        assertEquals(
            "Done. 1 yes or no answer was sent to the website. Nothing else left your phone. Go back to the website to continue.",
            TierCText.completionMessage(1),
        )
        assertEquals(
            "Done. 6 yes or no answers were sent to the website. Nothing else left your phone. Go back to the website to continue.",
            TierCText.completionMessage(6),
        )
        for (term in developerTerms) assertFalse(term, TierCText.completionMessage(3).contains(term))
    }

    @Test
    fun `a post-read refusal says the document could not answer and that no answers were sent`() {
        assertEquals(
            "Your document could not answer every question the website asked. No answers were sent. The website was only told that the request was refused.",
            TierCText.couldNotAnswerDialog(told),
        )
        assertEquals(
            "Your document could not answer every question the website asked. No answers were sent.",
            TierCText.couldNotAnswerDialog(TierCText.Delivery.Unreachable(false)),
        )
    }

    @Test
    fun `the settings help is three paragraphs, literal, words unchanged`() {
        assertEquals(
            "Off: the app only answers age checks.\n\n" +
                "On: websites approved in this app may also ask specific questions about your document, such as whether it expires soon, your nationality, or whether a name matches. You see every question before you scan, and only yes or no answers are sent.\n\n" +
                "An approved website can ask the same questions again on later scans. This setting turns off again if you reinstall the app.",
            TierCText.SETTING_HELP,
        )
        assertEquals(3, TierCText.SETTING_HELP.split("\n\n").size)
        // negative control: the unavailable text is a single paragraph and does not carry the reinstall sentence
        assertEquals(1, TierCText.SETTING_UNAVAILABLE.split("\n\n").size)
        assertFalse(TierCText.SETTING_UNAVAILABLE.contains("reinstall"))
    }

    // ---------------------------------------------------------------- finding #53: the Log says the specific reason

    private val notAnswer = "Refused: the website asked something this app does not answer"

    @Test
    fun `arrival and Verify-tap refusals - the Log result names the specific reason in plain words (literal table)`() {
        val table = listOf(
            "predicates_absent" to "$notAnswer (no questions were listed)",
            "predicates_empty" to "$notAnswer (no question was asked)",
            "predicate_malformed" to "$notAnswer (a question was not readable)",
            "predicate_not_in_vocabulary" to "$notAnswer (a question this app does not know)",
            "nationality_asked_more_than_once" to "$notAnswer (nationality was asked more than once)",
            "document_number_asked_more_than_once" to "$notAnswer (the document number was asked more than once)",
            "boundary_malformed" to "$notAnswer (a question had an unreadable limit)",
            "boundary_off_list" to "$notAnswer (a question used a limit this app does not allow)",
            "code_invalid" to "$notAnswer (a question named a country code this app cannot read)",
            "exclusion_set_size" to "$notAnswer (a question listed no countries or too many)",
            "name_fields_malformed" to "$notAnswer (a name question was incomplete)",
            "number_malformed" to "$notAnswer (a document number question was incomplete)",
        )
        for ((code, text) in table) assertEquals(code, text, TierCText.refusalResult(TierCAdmission.Refusal.REQUEST_NOT_ANSWERABLE, code))
        assertEquals(12, table.size)
        // an unknown code falls back to the general sentence, and no reason at all is the old line
        assertEquals(notAnswer, TierCText.refusalResult(TierCAdmission.Refusal.REQUEST_NOT_ANSWERABLE, "something_new"))
        assertEquals(notAnswer, TierCText.refusalResult(TierCAdmission.Refusal.REQUEST_NOT_ANSWERABLE))
        // the gate and age refusals are already specific per Refusal
        assertEquals("Refused: this version of the app does not do additional verifications", TierCText.refusalResult(TierCAdmission.Refusal.OPERATOR_SWITCH_OFF, "operator_switch_off"))
        assertEquals("Refused: this website is not approved to ask additional questions", TierCText.refusalResult(TierCAdmission.Refusal.NOT_PINNED, "not_pinned"))
        assertEquals("Refused: additional verifications are turned off in Settings", TierCText.refusalResult(TierCAdmission.Refusal.HOLDER_SETTING_OFF, "holder_setting_off"))
        assertEquals("Refused: the website asked about a different age than the one it first asked", TierCText.refusalResult(TierCAdmission.Refusal.AGE_NOT_LOCKED_AGE, "dob_age_not_locked_age"))
        assertEquals("Refused: the website asked about two different ages in one request", TierCText.refusalResult(TierCAdmission.Refusal.AGE_CONFLICT_IN_REQUEST, "dob_boundaries_conflict"))
    }

    @Test
    fun `post-read refusals - the Log result names the specific reason (literal table)`() {
        val base = "Refused: your document could not answer every question"
        val table = listOf(
            "document_not_answerable" to "$base (your document did not pass the app's checks)",
            "predicates_empty" to "$base (no question was asked)",
            "boundary_off_list" to "$base (a question used a limit this app does not allow)",
            "expiry_unreadable" to "$base (the expiry date could not be read)",
            "dob_unreadable" to "$base (the date of birth could not be read)",
            "nationality_unreadable" to "$base (the nationality could not be read)",
            "claimed_invalid" to "$base (a question's details were not valid)",
            "exclusion_set_size" to "$base (a question listed no countries or too many)",
            "document_number_unreadable" to "$base (the document number could not be read)",
            "name_unreadable" to "$base (the name could not be read)",
        )
        for ((code, text) in table) assertEquals(code, text, TierCText.couldNotAnswerResult(code))
        assertEquals(base, TierCText.couldNotAnswerResult("something_new"))
    }

    @Test
    fun `the full Log line for a doubled nationality question on arrival, and the popup stays the one generic message`() {
        val result = TierCText.refusalResult(TierCAdmission.Refusal.REQUEST_NOT_ANSWERABLE, "nationality_asked_more_than_once")
        assertEquals(
            "Refused: the website asked something this app does not answer (nationality was asked more than once). Refusal sent to the website (HTTP 200).",
            TierCText.logLine(result, told),
        )
        // the popup takes no reason: identical for every reason, unchanged wording
        assertEquals(
            "This website asked something this app does not answer. Nothing was read. The website was only told that the request was refused.",
            TierCText.refusalDialog(TierCAdmission.Refusal.REQUEST_NOT_ANSWERABLE, false, told),
        )
    }

    @Test
    fun `no Log reason text carries a developer term, a code or a value`() {
        val codes = listOf("predicates_absent", "predicates_empty", "predicate_malformed", "predicate_not_in_vocabulary", "nationality_asked_more_than_once", "document_number_asked_more_than_once",
            "boundary_malformed", "boundary_off_list", "code_invalid", "exclusion_set_size", "name_fields_malformed", "number_malformed",
            "document_not_answerable", "expiry_unreadable", "dob_unreadable", "nationality_unreadable", "document_number_unreadable", "name_unreadable", "claimed_invalid")
        var checked = 0
        for (c in codes) {
            val words = TierCText.reasonWords(c)
            assertTrue("$c has words", words != null)
            assertFalse("$c leaks its code", words!!.contains("_"))
            for (term in developerTerms) assertFalse("$c '$term'", words.contains(term))
            checked++
        }
        assertEquals(codes.size, checked)
    }

    @Test
    fun `MainActivity hands the Log the specific reason on both refusal paths (source wiring)`() {
        val src = listOf("src/main/java", "app/src/main/java").map { java.io.File(it, "com/tananaev/passportreader/MainActivity.kt") }.first { it.exists() }.readText()
        assertTrue(src.contains("TierCText.refusalResult(refusal.refusal, refusal.reason)"))
        assertTrue(src.contains("TierCText.logLine(TierCText.couldNotAnswerResult(answered.reason), delivery)"))
        // the wire stays generic: the post is built from the plan alone, never from a reason
        assertFalse(src.contains("TierCRefusalReply.formBody(refusal"))
    }
}
