package com.tananaev.passportreader

/**
 * M3b chunk 1, finding #27 / item 16: every user-facing sentence of the tier-C flow in plain language, in one pure
 * place so it is unit-testable and cannot drift between the dialog, the report and the Log tab. No sentence here ever
 * contains a site-supplied name, number or code, a document value, or a developer term such as "tier", "gate" or
 * "pin list". Pre-read refusals say "nothing was read"; post-read refusals say the document was read but nothing left
 * the phone.
 */
object TierCText {

    /** Pre-read refusal: the dialog text and the one-line report result. */
    data class Refusal(val dialog: String, val result: String)

    fun refusalFor(r: TierCAdmission.Refusal): Refusal = when (r) {
        TierCAdmission.Refusal.OPERATOR_SWITCH_OFF -> Refusal(
            "This version of the app does not do additional verifications, so nothing was read and nothing was sent.",
            "Refused: this version of the app does not do additional verifications",
        )
        TierCAdmission.Refusal.NOT_PINNED -> Refusal(
            "This website is not approved to ask additional questions about your document. Nothing was read and nothing was sent.",
            "Refused: this website is not approved to ask additional questions",
        )
        TierCAdmission.Refusal.HOLDER_SETTING_OFF -> Refusal(
            "Additional verifications are turned off. You can turn them on in Settings. Nothing was read and nothing was sent.",
            "Refused: additional verifications are turned off in Settings",
        )
        TierCAdmission.Refusal.REQUEST_NOT_ANSWERABLE -> Refusal(
            "This website asked something this app does not answer. Nothing was read and nothing was sent.",
            "Refused: the website asked something this app does not answer",
        )
        TierCAdmission.Refusal.AGE_NOT_LOCKED_AGE, TierCAdmission.Refusal.AGE_CONFLICT_IN_REQUEST -> Refusal(
            "This website may only ask about one age, and this question asks about a different one. Nothing was read and nothing was sent.",
            "Refused: the website asked about a different age than the one it first asked",
        )
    }

    /** Post-read refusal: the document could not answer every question, so nothing was sent (item 13). */
    const val COULD_NOT_ANSWER_DIALOG =
        "Your document could not answer every question the website asked, so nothing was sent."
    const val COULD_NOT_ANSWER_RESULT = "Refused: your document could not answer every question"

    const val NOT_DELIVERED_DIALOG = "The answers could not be delivered to the website."

    /** The completion message after a delivered tier-C answer (finding #27): says it is done, how many answers went,
     * that they were only yes or no, and what to do next. Delivery status only; the website's own verdict is never the
     * app's to show. */
    fun completionMessage(answerCount: Int): String {
        val what = if (answerCount == 1) "1 yes or no answer was" else "$answerCount yes or no answers were"
        return "Done. $what sent to the website. Nothing else left your phone. Go back to the website to continue."
    }

    /** The one sentence appended to an outcome dialog for a tier-C session (replaces "Mode C, ..." developer wording). */
    const val SCAN_SENTENCE = "This scan answered specific questions about your document."

    // ---- Settings tab ----
    const val SETTING_LABEL = "Allow additional verifications"
    const val SETTING_HELP =
        "Off: the app only answers age checks. On: websites approved in this app may also ask specific questions about " +
            "your document, such as whether it expires soon, your nationality, or whether a name matches. You see every " +
            "question before you scan, and only yes or no answers are sent."
    const val SETTING_UNAVAILABLE = "Additional verifications are not available in this version of the app."
}
