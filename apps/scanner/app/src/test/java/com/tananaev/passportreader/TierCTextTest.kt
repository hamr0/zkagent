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
}
