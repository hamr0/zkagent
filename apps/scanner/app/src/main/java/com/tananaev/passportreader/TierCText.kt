package com.tananaev.passportreader

/**
 * M3b chunk 1, finding #27 / item 16: every user-facing sentence of the tier-C flow in plain language, in one pure
 * place so it is unit-testable and cannot drift between the dialog, the report and the Log tab. No sentence here ever
 * contains a site-supplied name, number or code, a document value, or a developer term such as "tier", "gate" or
 * "pin list". Pre-read refusals say "nothing was read"; post-read refusals say the document was read but nothing left
 * the phone.
 */
object TierCText {

    /** What happened to the one generic "refused" notice (D110), as far as this phone can know. Pure, so the dialog
     * and the Log line cannot claim more than happened. Only [Told] licenses "the website was told". */
    sealed class Delivery {
        /** No notice goes out for this request (operator switch off, host not pinned, or no verified request). */
        object NoNotice : Delivery()
        /** The site's server answered 2xx: it was told. */
        data class Told(val httpStatus: Int) : Delivery()
        /** 409: the site had already received a response for this request, so it was not newly told. */
        object AlreadyResponded : Delivery()
        /** The server answered something else (other 4xx, 5xx): not accepted, so not told. */
        data class NotAccepted(val httpStatus: Int) : Delivery()
        /** No answer at all (no network, timeout, ...). [timedOut] only changes the Log wording. */
        data class Unreachable(val timedOut: Boolean) : Delivery()

        val told: Boolean get() = this is Told

        companion object {
            fun from(plan: TierCRefusalReply.Plan?, outcome: TierCRefusalReply.Outcome?): Delivery = when {
                plan == null || outcome == null -> NoNotice
                outcome is TierCRefusalReply.Outcome.Sent -> when (outcome.httpStatus) {
                    in 200..299 -> Told(outcome.httpStatus)
                    409 -> AlreadyResponded
                    else -> NotAccepted(outcome.httpStatus)
                }
                else -> Unreachable((outcome as TierCRefusalReply.Outcome.Failed).label.contains("Timeout"))
            }
        }
    }

    private const val TOLD = "The website was only told that the request was refused."

    /** Pre-read refusal, specific start only (the reason the holder can act on). */
    private fun startFor(r: TierCAdmission.Refusal): String = when (r) {
        TierCAdmission.Refusal.OPERATOR_SWITCH_OFF ->
            "This version of the app does not do additional verifications, so nothing was read and nothing was sent."
        TierCAdmission.Refusal.NOT_PINNED ->
            "This website is not approved to ask additional questions about your document."
        TierCAdmission.Refusal.HOLDER_SETTING_OFF ->
            "Additional verifications are turned off. You can turn them on in Settings."
        TierCAdmission.Refusal.REQUEST_NOT_ANSWERABLE ->
            "This website asked something this app does not answer."
        TierCAdmission.Refusal.AGE_NOT_LOCKED_AGE, TierCAdmission.Refusal.AGE_CONFLICT_IN_REQUEST ->
            "This website may only ask about one age, and this question asks about a different one."
    }

    /** The shared ending. [afterRead]: the chip was already read. Only a [Delivery.Told] notice may say the website was
     * told; any other delivery gets the form with no claim about the website ("nothing was sent" before the read,
     * "No answers were sent." after it). */
    private fun ending(afterRead: Boolean, delivery: Delivery): String = when {
        delivery.told && afterRead -> "No answers were sent. $TOLD"
        delivery.told -> "Nothing was read. $TOLD"
        afterRead -> "No answers were sent."
        else -> "Nothing was read and nothing was sent."
    }

    /** Refusal dialog: a specific start, then the shared ending. The operator-switch-off sentence is unchanged (true
     * there: no notice is ever sent). */
    fun refusalDialog(r: TierCAdmission.Refusal, afterRead: Boolean, delivery: Delivery): String =
        if (r == TierCAdmission.Refusal.OPERATOR_SWITCH_OFF) startFor(r) else "${startFor(r)} ${ending(afterRead, delivery)}"

    /** One-line reason (report/Log "result"), value-free. [reason] is the fixed code from [TierCAdmission.Decision.Refuse]
     * (a parse reason, or a gate/age code): when it is one [reasonWords] knows, the line says it in plain words after the
     * general sentence, so the Log is specific while the dialog (which never takes a reason) stays one simple message
     * and the website is told only a generic "refused" (D109 g5/h4, finding #53). */
    fun refusalResult(r: TierCAdmission.Refusal, reason: String? = null): String = when (r) {
        TierCAdmission.Refusal.OPERATOR_SWITCH_OFF -> "Refused: this version of the app does not do additional verifications"
        TierCAdmission.Refusal.NOT_PINNED -> "Refused: this website is not approved to ask additional questions"
        TierCAdmission.Refusal.HOLDER_SETTING_OFF -> "Refused: additional verifications are turned off in Settings"
        TierCAdmission.Refusal.REQUEST_NOT_ANSWERABLE ->
            "Refused: the website asked something this app does not answer" + (reason?.let(::reasonWords)?.let { " ($it)" } ?: "")
        TierCAdmission.Refusal.AGE_NOT_LOCKED_AGE -> "Refused: the website asked about a different age than the one it first asked"
        TierCAdmission.Refusal.AGE_CONFLICT_IN_REQUEST -> "Refused: the website asked about two different ages in one request"
    }

    /** Plain words for one fixed refusal code, for the Log only: the request codes ([TierCRequest.parse]) and the
     * after-read codes ([TierCAnswers.answerAll] / [TierCPredicates]). Value-free: no name, number, country code or URL.
     * Null for a code it does not know (the caller then says only the general sentence). */
    fun reasonWords(code: String): String? = when (code) {
        "predicates_absent" -> "no questions were listed"
        "predicates_empty" -> "no question was asked"
        "predicate_malformed" -> "a question was not readable"
        "predicate_not_in_vocabulary" -> "a question this app does not know"
        "nationality_asked_more_than_once" -> "nationality was asked more than once"
        "boundary_malformed" -> "a question had an unreadable limit"
        "boundary_off_list" -> "a question used a limit this app does not allow"
        "code_invalid" -> "a question named a country code this app cannot read"
        "exclusion_set_size" -> "a question listed no countries or too many"
        "name_fields_malformed" -> "a name question was incomplete"
        "number_malformed" -> "a document number question was incomplete"
        "document_not_answerable" -> "your document did not pass the app's checks"
        "expiry_unreadable" -> "the expiry date could not be read"
        "dob_unreadable" -> "the date of birth could not be read"
        "nationality_unreadable" -> "the nationality could not be read"
        "document_number_unreadable" -> "the document number could not be read"
        "name_unreadable" -> "the name could not be read"
        "claimed_invalid" -> "a question's details were not valid"
        else -> null
    }

    /** The Log tab line: the reason, then whether the refusal reached the site. Value-free (no name, number, code or URL). */
    fun logLine(reasonResult: String, delivery: Delivery): String = "$reasonResult. " + when (delivery) {
        Delivery.NoNotice -> "No refusal notice is sent for this request."
        is Delivery.Told -> "Refusal sent to the website (HTTP ${delivery.httpStatus})."
        Delivery.AlreadyResponded -> "Refusal not sent: the website had already received a response (HTTP 409)."
        is Delivery.NotAccepted -> "Refusal could not be sent (the website answered HTTP ${delivery.httpStatus})."
        is Delivery.Unreachable -> if (delivery.timedOut) "Refusal could not be sent (no reply in time)." else "Refusal could not be sent (unreachable)."
    }

    /** The report's "what was sent" line for a refusal, from what actually happened. */
    fun refusalSent(delivery: Delivery): String =
        when (delivery) {
            Delivery.NoNotice -> "nothing left this device"
            is Delivery.Told -> REFUSAL_NOTICE_SENT
            else -> "nothing the website accepted"
        }

    /** Post-read refusal: the document could not answer every question (item 13). */
    fun couldNotAnswerDialog(delivery: Delivery): String =
        "Your document could not answer every question the website asked. ${ending(true, delivery)}"
    const val COULD_NOT_ANSWER_RESULT = "Refused: your document could not answer every question"

    /** The Log "result" for a post-read refusal: the general sentence plus the specific [reasonWords] (finding #53). */
    fun couldNotAnswerResult(reason: String): String =
        COULD_NOT_ANSWER_RESULT + (reasonWords(reason)?.let { " ($it)" } ?: "")

    /** D110: the report's "what was sent" line when the app tells the website, in one plain sentence, that the request
     * was refused. No reason, nothing about the holder or the document. */
    const val REFUSAL_NOTICE_SENT = "a plain \"refused\" notice to the website; no reason, nothing about you or your document"

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
        "Off: the app only answers age checks.\n\n" +
            "On: websites approved in this app may also ask specific questions about " +
            "your document, such as whether it expires soon, your nationality, or whether a name matches. You see every " +
            "question before you scan, and only yes or no answers are sent.\n\n" +
            "An approved website can ask the same questions again on later scans. This setting turns off again if you " +
            "reinstall the app."
    const val SETTING_UNAVAILABLE = "Additional verifications are not available in this version of the app."
}
