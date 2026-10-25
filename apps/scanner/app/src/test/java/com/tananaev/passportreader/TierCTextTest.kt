package com.tananaev.passportreader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** M3b chunk 1, finding #27 - the user-facing sentences are plain words and carry no developer term or value. */
class TierCTextTest {

    private val developerTerms = listOf("tier", "Tier", "gate", "pin ", "pinned", "mode C", "Mode C", "operator", "predicate", "no tier-C flow", "POC", "build")

    @Test
    fun `every refusal is plain words, says what happened to the data, and has no developer term`() {
        var checked = 0
        for (r in TierCAdmission.Refusal.values()) {
            val t = TierCText.refusalFor(r)
            assertTrue(r.name, t.dialog.contains("Nothing was read and nothing was sent") || t.dialog.contains("nothing was read and nothing was sent"))
            for (term in developerTerms) {
                assertFalse("${r.name} dialog has '$term'", t.dialog.contains(term))
                assertFalse("${r.name} result has '$term'", t.result.contains(term))
            }
            checked++
        }
        assertEquals(TierCAdmission.Refusal.values().size, checked)
        assertTrue(checked > 0)
        // the operator-switch-off wording is the one that used to read as developer text
        assertEquals(
            "This version of the app does not do additional verifications, so nothing was read and nothing was sent.",
            TierCText.refusalFor(TierCAdmission.Refusal.OPERATOR_SWITCH_OFF).dialog,
        )
        // the holder-off refusal tells the holder where the switch is
        assertTrue(TierCText.refusalFor(TierCAdmission.Refusal.HOLDER_SETTING_OFF).dialog.contains("Settings"))
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
    fun `a post-read refusal says the document could not answer and that nothing was sent`() {
        assertTrue(TierCText.COULD_NOT_ANSWER_DIALOG.contains("nothing was sent"))
        for (term in developerTerms) assertFalse(term, TierCText.COULD_NOT_ANSWER_DIALOG.contains(term))
    }
}
