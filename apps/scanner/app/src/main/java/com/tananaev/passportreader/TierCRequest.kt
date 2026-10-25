package com.tananaev.passportreader

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

/**
 * M3b chunk 1 — the tier-C request parser, the plain-words question lines, and the all-or-nothing answer path,
 * for ALL five item-8 predicate kinds (expiry bucket, two-field name match, date-of-birth range, nationality in
 * both modes, document-number match).
 *
 * **The wire shape below is still POC-local, not a ruling.** §6.9.2 item 14 (`chiproof` tier-C schema) is chunk 2
 * and replaces it. The request carries `zkagent.predicates`:
 *
 *  - `{"name":"expiry_beyond","months":3|6|12}`
 *  - `{"name":"name_match","given_names":"..","surname":".."}`        (D109(k): two labelled fields)
 *  - `{"name":"dob_at_least","age":15|16|18|21|60|65}`
 *  - `{"name":"nationality_is","code":"NLD"}`                           (confirmation mode)
 *  - `{"name":"nationality_not_in","codes":["CUB","RUS"]}`              (exclusion mode, 1..10 codes)
 *  - `{"name":"document_number_is","number":".."}`
 *
 * The answer is [TierCAnswers.presentation]'s `zkagent-poc-tierc/0` object: one `{name, [months|age], result}` per
 * predicate, in request order. A site-supplied name, number or code is never echoed back, and never logged,
 * rendered or put in a report: [Predicate.toString] is redacted for the two sensitive kinds, and
 * [describe] names the question without the value (§6.9.2 item 16(a)).
 *
 * Parsing is all-or-nothing (item 13): any predicate that is malformed, off a fixed list, out of vocabulary or over
 * the exclusion cap refuses the whole request. Nothing here throws.
 */
object TierCRequest {

    sealed class Predicate {
        data class ExpiryBeyond(val months: Int) : Predicate()
        data class DobAtLeast(val age: Int) : Predicate()
        data class NationalityIs(val code: String) : Predicate()
        data class NationalityNotIn(val codes: List<String>) : Predicate()
        data class NameMatch(val givenNames: String, val surname: String) : Predicate() {
            override fun toString(): String = "NameMatch(<redacted>)"
        }
        data class DocumentNumberIs(val number: String) : Predicate() {
            override fun toString(): String = "DocumentNumberIs(<redacted>)"
        }

        /** The fixed vocabulary name, as on the wire and in logs. */
        val name: String
            get() = when (this) {
                is ExpiryBeyond -> "expiry_beyond"
                is DobAtLeast -> "dob_at_least"
                is NationalityIs -> "nationality_is"
                is NationalityNotIn -> "nationality_not_in"
                is NameMatch -> "name_match"
                is DocumentNumberIs -> "document_number_is"
            }
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
            when (val one = parseOne(item)) {
                is One.Ok -> out.add(one.predicate)
                is One.Bad -> return Parse.Refuse(one.reason)
            }
        }
        return Parse.Ok(out)
    }

    private sealed class One {
        data class Ok(val predicate: Predicate) : One()
        data class Bad(val reason: String) : One()
    }

    private fun parseOne(item: JSONObject): One {
        return when (item.opt("name")) {
            "expiry_beyond" -> {
                val months = item.opt("months") as? Int ?: return One.Bad("boundary_malformed")
                if (months !in TierCPredicates.EXPIRY_BUCKET_MONTHS) return One.Bad("boundary_off_list")
                One.Ok(Predicate.ExpiryBeyond(months))
            }
            "dob_at_least" -> {
                val age = item.opt("age") as? Int ?: return One.Bad("boundary_malformed")
                if (age !in TierCPredicates.DOB_AGES) return One.Bad("boundary_off_list")
                One.Ok(Predicate.DobAtLeast(age))
            }
            "nationality_is" -> {
                val code = TierCPredicates.validCode(item.opt("code") as? String) ?: return One.Bad("code_invalid")
                One.Ok(Predicate.NationalityIs(code))
            }
            "nationality_not_in" -> {
                val arr = item.opt("codes") as? JSONArray ?: return One.Bad("code_invalid")
                if (arr.length() == 0 || arr.length() > TierCPredicates.MAX_EXCLUSION_SET) return One.Bad("exclusion_set_size")
                val codes = ArrayList<String>(arr.length())
                for (i in 0 until arr.length()) {
                    codes.add(TierCPredicates.validCode(arr.opt(i) as? String) ?: return One.Bad("code_invalid"))
                }
                One.Ok(Predicate.NationalityNotIn(codes))
            }
            "name_match" -> {
                val given = item.opt("given_names") as? String
                val surname = item.opt("surname") as? String
                if (given == null || surname == null) return One.Bad("name_fields_malformed")
                if (TierCNameMatch.siteWords(given).isEmpty() || TierCNameMatch.siteWords(surname).isEmpty()) {
                    return One.Bad("name_fields_malformed")
                }
                One.Ok(Predicate.NameMatch(given, surname))
            }
            "document_number_is" -> {
                val number = item.opt("number") as? String
                if (number == null || number.replace("<", "").replace(" ", "").isEmpty()) return One.Bad("number_malformed")
                One.Ok(Predicate.DocumentNumberIs(number))
            }
            else -> One.Bad("predicate_not_in_vocabulary")
        }
    }

    /** The distinct date-of-birth ages a request asks (item 9: they are checked against the shared age lock). */
    fun dobAges(predicates: List<Predicate>): Set<Int> =
        predicates.filterIsInstance<Predicate.DobAtLeast>().map { it.age }.toSet()

    /** One plain-words line per predicate asked, built from the parsed request (never a fixed string), for the
     * pre-tap question line (item 7). Names the question and, for nationality, the country codes asked about; it NEVER
     * contains a site-supplied name or document number (item 16(a)). */
    fun describe(predicates: List<Predicate>): List<String> = predicates.map {
        when (it) {
            is Predicate.ExpiryBeyond -> "Whether your document is valid for more than ${it.months} months from today"
            is Predicate.DobAtLeast -> "Whether you are at least ${it.age} years old"
            is Predicate.NationalityIs -> "Whether your nationality is ${it.code}"
            is Predicate.NationalityNotIn -> "Whether your nationality is none of: ${it.codes.joinToString(", ")}"
            is Predicate.NameMatch -> "Whether your name matches the name the website holds"
            is Predicate.DocumentNumberIs -> "Whether your document number matches the one the website holds"
        }
    }

    const val UNANSWERABLE_LINE = "something about your document that this app cannot answer"

    /** Describe-or-neutral for the display layer: a tier-C request that does not parse still gets a truthful line.
     * One bullet per predicate, newline separated. */
    fun describeRequest(requestJson: JSONObject): String =
        when (val p = parse(requestJson)) {
            is Parse.Ok -> describe(p.predicates).joinToString("\n") { "• $it" }
            is Parse.Refuse -> UNANSWERABLE_LINE
        }
}

/** Evaluation and wire shape for the tier-C answer. */
object TierCAnswers {

    data class Answered(val predicate: TierCRequest.Predicate, val result: Boolean)

    sealed class Result {
        data class Answered(val answers: List<TierCAnswers.Answered>) : Result()
        /** Refused whole: [reason] is a fixed code, never a value. The site gets nothing (D92). */
        data class Refused(val reason: String) : Result()
    }

    /** The one evaluator per predicate kind. */
    fun evaluate(p: TierCRequest.Predicate, fields: TierCFields, today: LocalDate): Answer = when (p) {
        is TierCRequest.Predicate.ExpiryBeyond -> TierCPredicates.expiryBeyond(p.months, fields, today)
        is TierCRequest.Predicate.DobAtLeast -> TierCPredicates.dobAtLeast(p.age, fields, today)
        is TierCRequest.Predicate.NationalityIs -> TierCPredicates.nationalityIs(p.code, fields)
        is TierCRequest.Predicate.NationalityNotIn -> TierCPredicates.nationalityNotIn(p.codes, fields)
        is TierCRequest.Predicate.NameMatch -> TierCPredicates.nameMatches(p.givenNames, p.surname, fields)
        is TierCRequest.Predicate.DocumentNumberIs -> TierCPredicates.documentNumberMatches(p.number, fields)
    }

    /**
     * All-or-nothing (item 13): every predicate answered, or the whole request refused. A document whose
     * integrity verdict is not answerable, or any predicate that is [Answer.CannotTell], refuses; a real
     * [Answer.False] is a legitimate answer and is sent. Every predicate is evaluated before the verdict is
     * returned only when all are answerable; the first CannotTell short-circuits to the refusal.
     */
    fun answerAll(predicates: List<TierCRequest.Predicate>, fields: TierCFields, today: LocalDate, documentAnswerable: Boolean): Result {
        if (!documentAnswerable) return Result.Refused("document_not_answerable")
        if (predicates.isEmpty()) return Result.Refused("predicates_empty")
        val answers = ArrayList<Answered>(predicates.size)
        for (p in predicates) {
            when (val a = evaluate(p, fields, today)) {
                Answer.True -> answers.add(Answered(p, true))
                Answer.False -> answers.add(Answered(p, false))
                is Answer.CannotTell -> return Result.Refused(a.reason)
            }
        }
        return Result.Answered(answers)
    }

    const val POC_SPEC = "zkagent-poc-tierc/0"

    /** The POC presentation: echoes the challenge (the demo matches its nonce) and carries, per predicate in request
     * order, the predicate name, its fixed boundary where it has one (months, age) and the boolean. Never a site value. */
    fun presentation(challenge: JSONObject, answered: List<Answered>): JSONObject {
        val arr = JSONArray()
        for (a in answered) {
            val o = JSONObject().put("name", a.predicate.name)
            when (val p = a.predicate) {
                is TierCRequest.Predicate.ExpiryBeyond -> o.put("months", p.months)
                is TierCRequest.Predicate.DobAtLeast -> o.put("age", p.age)
                else -> Unit
            }
            arr.put(o.put("result", a.result))
        }
        return JSONObject().put("spec", POC_SPEC).put("tier", "C").put("challenge", challenge).put("answers", arr)
    }

    /** The plain-words, value-free label for one answered predicate (report and log tab). */
    fun label(p: TierCRequest.Predicate): String = TierCRequest.describe(listOf(p)).first()
}
