package com.tananaev.passportreader

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

/**
 * M3b opening POC — the SMALLEST tier-C request/answer path that criteria 3 and 4 need an "answered end to
 * end" positive control for, and nothing wider. It supports exactly ONE predicate shape, the expiry bucket
 * (`{"name":"expiry_beyond","months":3|6|12}`), because that one needs no site-supplied value (nothing
 * typed, nothing to leak) and no shared age lock (item 9 / item 23, not built). Every other §6.9.2 item-8
 * predicate is evaluated by [TierCPredicates] for the criteria 1-2 probe but is NOT accepted from a request
 * here: a request naming one is refused whole ([Parse.Refuse]), the all-or-nothing rule of item 13.
 *
 * **The wire shape below is a POC-local choice, not a ruling.** §6.9.2 item 14 (`chiproof` predicate schema)
 * is not built and specifies none; the request carries `zkagent.predicates` and the answer is
 * [TierCAnswers.presentation]'s `zkagent-poc-tierc/0` object, verified only by the demo's POC route.
 */
object TierCRequest {

    sealed class Predicate {
        data class ExpiryBeyond(val months: Int) : Predicate()
        val name: String get() = when (this) { is ExpiryBeyond -> "expiry_beyond" }
    }

    sealed class Parse {
        data class Ok(val predicates: List<Predicate>) : Parse()
        /** [reason] is a fixed, value-free code. */
        data class Refuse(val reason: String) : Parse()
    }

    fun isTierC(requestJson: JSONObject): Boolean = RequestTrust.tierOf(requestJson) == "C"

    /** Parses `zkagent.predicates` of a VERIFIED request. Never throws; anything unexpected is a refusal. */
    fun parse(requestJson: JSONObject): Parse {
        val raw = requestJson.optJSONObject("zkagent")?.opt("predicates")
        if (raw !is JSONArray) return Parse.Refuse("predicates_absent")
        if (raw.length() == 0) return Parse.Refuse("predicates_empty")
        val out = ArrayList<Predicate>(raw.length())
        for (i in 0 until raw.length()) {
            val item = raw.opt(i) as? JSONObject ?: return Parse.Refuse("predicate_malformed")
            when (item.opt("name")) {
                "expiry_beyond" -> {
                    val months = item.opt("months") as? Int ?: return Parse.Refuse("boundary_malformed")
                    if (months !in TierCPredicates.EXPIRY_BUCKET_MONTHS) return Parse.Refuse("boundary_off_list")
                    out.add(Predicate.ExpiryBeyond(months))
                }
                else -> return Parse.Refuse("predicate_not_in_poc")
            }
        }
        return Parse.Ok(out)
    }

    /** One plain-words line per predicate asked, built from the parsed request (never a fixed string), for the
     * pre-tap question line (item 7). Names the question, never a value. */
    fun describe(predicates: List<Predicate>): String =
        predicates.joinToString("; ") {
            when (it) {
                is Predicate.ExpiryBeyond -> "does your document expire more than ${it.months} months from today"
            }
        }

    /** Describe-or-neutral for the display layer: a tier-C request that does not parse still gets a truthful line. */
    fun describeRequest(requestJson: JSONObject): String =
        when (val p = parse(requestJson)) {
            is Parse.Ok -> describe(p.predicates)
            is Parse.Refuse -> "something about your document that this app cannot answer"
        }
}

/** Evaluation and wire shape for the POC's tier-C answer. */
object TierCAnswers {

    data class Answered(val name: String, val months: Int, val result: Boolean)

    sealed class Result {
        data class Answered(val answers: List<TierCAnswers.Answered>) : Result()
        /** Refused whole: [reason] is a fixed code, never a value. The site gets nothing (D92). */
        data class Refused(val reason: String) : Result()
    }

    /**
     * All-or-nothing (item 13): every predicate answered, or the whole request refused. A document whose
     * integrity verdict is not answerable, or any predicate that is [Answer.CannotTell], refuses; a real
     * [Answer.False] is a legitimate answer and is sent.
     */
    fun answerAll(predicates: List<TierCRequest.Predicate>, fields: TierCFields, today: LocalDate, documentAnswerable: Boolean): Result {
        if (!documentAnswerable) return Result.Refused("document_not_answerable")
        val answers = ArrayList<Answered>(predicates.size)
        for (p in predicates) {
            when (p) {
                is TierCRequest.Predicate.ExpiryBeyond -> when (val a = TierCPredicates.expiryBeyond(p.months, fields, today)) {
                    Answer.True -> answers.add(Answered(p.name, p.months, true))
                    Answer.False -> answers.add(Answered(p.name, p.months, false))
                    is Answer.CannotTell -> return Result.Refused(a.reason)
                }
            }
        }
        return Result.Answered(answers)
    }

    const val POC_SPEC = "zkagent-poc-tierc/0"

    /** The POC presentation: echoes the challenge (the demo matches its nonce) and carries name/boundary/boolean triples only. */
    fun presentation(challenge: JSONObject, answered: List<Answered>): JSONObject {
        val arr = JSONArray()
        for (a in answered) {
            arr.put(JSONObject().put("name", a.name).put("months", a.months).put("result", a.result))
        }
        return JSONObject().put("spec", POC_SPEC).put("tier", "C").put("challenge", challenge).put("answers", arr)
    }
}
