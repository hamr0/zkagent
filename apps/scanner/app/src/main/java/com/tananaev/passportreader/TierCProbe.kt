package com.tananaev.passportreader

import java.time.LocalDate
import java.util.Locale

/**
 * M3b opening POC, §6.9.1 criteria 1 and 2 — the battery run against ONE real DG1 read. Pure: it takes the
 * fields the chip returned, what the OWNER typed on the device, and the coarsened date, and returns
 * verdict lines. Every line is a check id, a kind, booleans and a duration in microseconds: never a field
 * value, never the typed value, never a date (the standing rule, §6.9.1 and MEMORY.md).
 *
 * **Where each expectation comes from (independent of the code under test).** The owner types the holder name
 * and nationality (Diagnostics tab) and already types document number, date of birth and date of expiry for
 * BAC/PACE (Scan tab). Those typed values are the truth the chip-read values are checked against; the planted
 * wrong inputs are produced by [mutateOneCharacter] and fixed decoy codes, never by calling the evaluator
 * to find out what it would say. Expiry and DOB pairs shift `today` to a point 5 days either side of the
 * typed date's bucket boundary, so each pair is "a boundary on each side of the true value" without the
 * expectation being computed by the evaluator under test; the exact day boundary, leap days and end-of-month
 * clamping are covered by the hand-computed unit table instead, labelled synthetic.
 *
 * **Non-vacuity.** Each group reports how many POSITIVE checks confirmed (the right input gave the right
 * answer) and how many NEGATIVE checks excluded (the planted wrong input gave a real `false` or a refusal).
 * A negative that excluded nothing, or a plant that did not actually change its input, is reported as a
 * FAILED check, so a green run cannot mean "the guard matched nothing".
 */
object TierCProbe {

    /** What the owner typed on the device. [toString] is redacted. */
    data class Typed(
        val name: String,
        val nationality: String,
        val documentNumber: String,
        val expiry: LocalDate,
        val dob: LocalDate,
    ) {
        override fun toString(): String = "Typed(<redacted>)"
    }

    enum class Kind { POSITIVE, NEGATIVE, OBSERVE }

    sealed class Expect {
        data class Is(val answer: Answer) : Expect()
        /** Any refusal, but NOT a real true/false. */
        object CannotTell : Expect()
        object None : Expect()
    }

    data class Check(val group: String, val id: String, val kind: Kind, val expect: Expect, val actual: Answer, val micros: Long) {
        val passed: Boolean
            get() = when (expect) {
                is Expect.Is -> actual == expect.answer
                Expect.CannotTell -> actual is Answer.CannotTell
                Expect.None -> true
            }

        fun line(): String =
            "check group=$group id=$id kind=${kind.name.lowercase(Locale.ROOT)} " +
                "expected=${when (expect) { is Expect.Is -> expect.answer.toString(); Expect.CannotTell -> "cannot_tell"; Expect.None -> "none" }} " +
                "actual=$actual pass=$passed read_to_boolean_us=$micros"
    }

    data class Result(val checks: List<Check>, val observations: List<String>) {
        val failed: List<Check> get() = checks.filter { !it.passed }
        fun positivesConfirmed(group: String) = checks.count { it.group == group && it.kind == Kind.POSITIVE && it.passed }
        fun negativesExcluded(group: String) = checks.count { it.group == group && it.kind == Kind.NEGATIVE && it.passed }
        val groups: List<String> get() = checks.map { it.group }.distinct()

        /** A group is non-vacuous only if it has at least one confirmed positive AND one excluding negative. */
        fun groupNonVacuous(group: String) = positivesConfirmed(group) > 0 && negativesExcluded(group) > 0

        fun lines(): List<String> = buildList {
            addAll(observations)
            checks.forEach { add(it.line()) }
            groups.forEach { g ->
                add("group $g pos_confirmed=${positivesConfirmed(g)} neg_excluded=${negativesExcluded(g)} non_vacuous=${groupNonVacuous(g)}")
            }
            add("summary checks=${checks.size} pass=${checks.size - failed.size} fail=${failed.size} " +
                "all_groups_non_vacuous=${groups.all { groupNonVacuous(it) || it == G_OBSERVE }}")
        }
    }

    const val G_OBSERVE = "observe"

    /**
     * @param fields the real chip's DG1 fields.
     * @param typed what the owner typed.
     * @param todayUtc the D28-coarsened current date.
     * @param integrityReal passive-auth verdict of the real document: `ok && allowed==true` (null if not run).
     * @param integrityTampered passive-auth verdict with one DG1 bit flipped (the M0 negative,
     *   `M0Probe.tamperedDg1`): `ok && allowed==true`, which MUST be false (null if not run).
     */
    fun run(
        fields: TierCFields,
        typed: Typed,
        todayUtc: LocalDate,
        integrityReal: Boolean?,
        integrityTampered: Boolean?,
    ): Result {
        val checks = ArrayList<Check>()
        val observations = ArrayList<String>()

        fun timed(group: String, id: String, kind: Kind, expect: Expect, eval: () -> Answer) {
            val t0 = System.nanoTime()
            val a = try { eval() } catch (e: Exception) { Answer.CannotTell("evaluator_threw:${e.javaClass.simpleName}") }
            checks.add(Check(group, id, kind, expect, a, (System.nanoTime() - t0) / 1_000))
        }
        val yes = Expect.Is(Answer.True)
        val no = Expect.Is(Answer.False)

        // ---- field reads: non-empty, as booleans (criterion 1 "the required field is read non-empty") ----
        listOf(
            "date_of_expiry" to fields.dateOfExpiry, "date_of_birth" to fields.dateOfBirth,
            "nationality" to fields.nationality, "document_number" to fields.documentNumber,
            "primary_identifier" to fields.primaryIdentifier,
        ).forEach { (n, v) -> timed("field_reads", "read_nonempty.$n", Kind.POSITIVE, yes) { Answer.of(!v.isNullOrEmpty()) } }
        observations.add("observe field=secondary_identifier present=${!fields.secondaryIdentifier.isNullOrEmpty()}")
        observations.add("observe mrz_form=${fields.form.name}")

        // ---- chip value agrees with the typed BAC/PACE inputs (independent truth) ----
        timed("field_reads", "chip_expiry_equals_typed", Kind.POSITIVE, yes) { Answer.of(fields.dateOfExpiry == TierCPredicates.mrzDate(typed.expiry)) }
        timed("field_reads", "chip_dob_equals_typed", Kind.POSITIVE, yes) { Answer.of(fields.dateOfBirth == TierCPredicates.mrzDate(typed.dob)) }
        timed("field_reads", "chip_expiry_differs_from_typed_plus_one_day", Kind.NEGATIVE, no) { Answer.of(fields.dateOfExpiry == TierCPredicates.mrzDate(typed.expiry.plusDays(1))) }
        timed("field_reads", "chip_dob_differs_from_typed_plus_one_day", Kind.NEGATIVE, no) { Answer.of(fields.dateOfBirth == TierCPredicates.mrzDate(typed.dob.plusDays(1))) }

        // ---- expiry: three fixed buckets, 5 days either side ----
        for (m in TierCPredicates.EXPIRY_BUCKET_MONTHS) {
            val posToday = typed.expiry.minusMonths(m.toLong()).minusDays(5)
            val negToday = typed.expiry.minusMonths(m.toLong()).plusDays(5)
            timed("expiry", "expiry_beyond_${m}mo.true_side", Kind.POSITIVE, yes) { TierCPredicates.expiryBeyond(m, fields, posToday) }
            timed("expiry", "expiry_beyond_${m}mo.false_side", Kind.NEGATIVE, no) { TierCPredicates.expiryBeyond(m, fields, negToday) }
            timed("observe", "expiry_beyond_${m}mo.today", Kind.OBSERVE, Expect.None) { TierCPredicates.expiryBeyond(m, fields, todayUtc) }
        }

        // ---- DOB range: six fixed ages, 5 days either side ----
        for (age in TierCPredicates.DOB_AGES) {
            val posToday = typed.dob.plusYears(age.toLong()).plusDays(5)
            val negToday = typed.dob.plusYears(age.toLong()).minusDays(5)
            timed("dob_range", "dob_at_least_$age.true_side", Kind.POSITIVE, yes) { TierCPredicates.dobAtLeast(age, fields, posToday) }
            timed("dob_range", "dob_at_least_$age.false_side", Kind.NEGATIVE, no) { TierCPredicates.dobAtLeast(age, fields, negToday) }
            timed("observe", "dob_at_least_$age.today", Kind.OBSERVE, Expect.None) { TierCPredicates.dobAtLeast(age, fields, todayUtc) }
        }

        // ---- document number: typed value vs one digit off ----
        timed("document_number", "docnum.typed_matches", Kind.POSITIVE, yes) { TierCPredicates.documentNumberMatches(typed.documentNumber, fields) }
        val wrongNumber = mutateLastDigitOrCharacter(typed.documentNumber)
        timed("document_number", "docnum.plant_effective", Kind.POSITIVE, yes) { Answer.of(wrongNumber != null && wrongNumber.uppercase(Locale.ROOT) != typed.documentNumber.uppercase(Locale.ROOT)) }
        timed("document_number", "docnum.one_off_rejected", Kind.NEGATIVE, no) {
            if (wrongNumber == null) Answer.CannotTell("plant_vacuous") else TierCPredicates.documentNumberMatches(wrongNumber, fields)
        }

        // ---- nationality: confirmation right/wrong, exclusion without/with the holder's own ----
        val decoyA = decoyCode(typed.nationality, "ZZA")
        val decoyB = decoyCode(typed.nationality, "ZZB")
        timed("nationality", "nat.confirm_typed", Kind.POSITIVE, yes) { TierCPredicates.nationalityIs(typed.nationality, fields) }
        timed("nationality", "nat.confirm_decoy", Kind.NEGATIVE, no) { TierCPredicates.nationalityIs(decoyA, fields) }
        timed("nationality", "nat.exclusion_without_own", Kind.POSITIVE, yes) { TierCPredicates.nationalityNotIn(listOf(decoyA, decoyB), fields) }
        timed("nationality", "nat.exclusion_with_own", Kind.NEGATIVE, no) { TierCPredicates.nationalityNotIn(listOf(decoyA, typed.nationality, decoyB), fields) }
        timed("nationality", "nat.exclusion_over_cap_refused", Kind.NEGATIVE, Expect.CannotTell) {
            TierCPredicates.nationalityNotIn(List(TierCPredicates.MAX_EXCLUSION_SET + 1) { "Z${'A' + (it % 26)}${'A' + (it % 7)}" }, fields)
        }

        // ---- name: criterion 2's order and normalisation observations, both orders evaluated ----
        val orders = NameOrder.values().toList()
        val typedResults = orders.associateWith { o -> TierCPredicates.nameMatches(typed.name, fields, o) }
        orders.forEach { o -> observations.add("observe name_typed_matches order=${o.name.lowercase(Locale.ROOT)} result=${typedResults.getValue(o)}") }
        val winning = orders.firstOrNull { typedResults[it] == Answer.True }
        timed("name", "name.typed_true_under_some_order", Kind.POSITIVE, yes) { Answer.of(winning != null) }
        val wrongName = mutateOneCharacter(typed.name)
        val plantEffective = wrongName != null &&
            TierCPredicates.normalizeName(wrongName) != TierCPredicates.normalizeName(typed.name)
        timed("name", "name.plant_effective", Kind.POSITIVE, yes) { Answer.of(plantEffective) }
        timed("name", "name.one_char_changed_false_under_every_order", Kind.NEGATIVE, no) {
            if (wrongName == null) Answer.CannotTell("plant_vacuous")
            else Answer.of(orders.any { TierCPredicates.nameMatches(wrongName, fields, it) == Answer.True })
        }
        // Each D101 rule exercised on the real name (derived variants of what the owner typed):
        val order = winning ?: NameOrder.GIVEN_FIRST
        timed("name", "name.rule_case_lower", Kind.POSITIVE, yes) { TierCPredicates.nameMatches(typed.name.lowercase(Locale.ROOT), fields, order) }
        timed("name", "name.rule_case_upper", Kind.POSITIVE, yes) { TierCPredicates.nameMatches(typed.name.uppercase(Locale.ROOT), fields, order) }
        timed("name", "name.rule_angle_filler_separator", Kind.POSITIVE, yes) { TierCPredicates.nameMatches(typed.name.trim().replace(' ', '<'), fields, order) }
        timed("name", "name.rule_hyphen_separator", Kind.POSITIVE, yes) { TierCPredicates.nameMatches(typed.name.trim().replace(' ', '-'), fields, order) }
        val typedLower = typed.name.lowercase(Locale.ROOT)
        observations.add("observe name_rule_exercised case=${typed.name != typed.name.uppercase(Locale.ROOT)} " +
            "hyphen=${typed.name.contains('-') || (fields.primaryIdentifier.orEmpty() + fields.secondaryIdentifier.orEmpty()).contains('-')} " +
            "accent=${java.text.Normalizer.normalize(typedLower, java.text.Normalizer.Form.NFD).any { it.category == CharCategory.NON_SPACING_MARK }} " +
            "angle_filler=${fields.primaryIdentifier.orEmpty().contains(' ') || fields.secondaryIdentifier.orEmpty().contains('<')}")
        TierCPredicates.observeName(fields)?.let {
            observations.add("observe name_field length_known=${it.lengthKnown} at_capacity=${it.atCapacity} " +
                "uses_angle_filler=${it.usesAngleFiller} uses_space_separator=${it.usesSpaceSeparator} " +
                "contains_hyphen=${it.containsHyphen} all_ascii=${it.allAscii} has_secondary=${it.hasSecondary}")
        } ?: observations.add("observe name_field unreadable=true")

        // ---- planted negatives that must be a REFUSAL, never a real false ----
        val flipped = fields.dateOfExpiry?.let { it.replaceRange(it.length - 1, it.length, "X") } ?: "X"
        val corrupted = fields.copy(dateOfExpiry = flipped)
        for (m in TierCPredicates.EXPIRY_BUCKET_MONTHS) {
            timed("unreadable", "expiry_${m}mo.corrupted_field_is_refusal", Kind.NEGATIVE, Expect.CannotTell) { TierCPredicates.expiryBeyond(m, corrupted, todayUtc) }
        }
        timed("unreadable", "expiry.control_uncorrupted_is_not_refusal", Kind.POSITIVE, yes) {
            Answer.of(TierCPredicates.expiryBeyond(3, fields, todayUtc) !is Answer.CannotTell)
        }
        timed("unreadable", "nationality.missing_field_is_refusal", Kind.NEGATIVE, Expect.CannotTell) { TierCPredicates.nationalityIs(typed.nationality, fields.copy(nationality = null)) }
        timed("unreadable", "name.missing_field_is_refusal", Kind.NEGATIVE, Expect.CannotTell) { TierCPredicates.nameMatches(typed.name, fields.copy(primaryIdentifier = null), order) }
        timed("unreadable", "dob.missing_field_is_refusal", Kind.NEGATIVE, Expect.CannotTell) { TierCPredicates.dobAtLeast(18, fields.copy(dateOfBirth = null), todayUtc) }

        // ---- DG1 integrity (the M0 negative reused): a flipped DG1 bit must produce NO boolean ----
        if (integrityReal != null && integrityTampered != null) {
            timed("integrity", "integrity.real_document_answerable", Kind.POSITIVE, yes) { Answer.of(integrityReal) }
            timed("integrity", "integrity.tampered_dg1_not_answerable", Kind.NEGATIVE, no) { Answer.of(integrityTampered) }
        } else {
            observations.add("observe integrity_checks ran=false")
        }

        return Result(checks, observations)
    }

    /** Changes exactly one character of [s] to a different letter of the same case (first ASCII letter that can change). null if there is none. */
    fun mutateOneCharacter(s: String): String? {
        val i = s.indexOfFirst { it in 'a'..'z' || it in 'A'..'Z' }
        if (i < 0) return null
        val c = s[i]
        val base = if (c in 'a'..'z') 'a' else 'A'
        val changed = base + ((c - base + 1) % 26)
        return s.substring(0, i) + changed + s.substring(i + 1)
    }

    /** One digit off: increments the last digit mod 10, else changes the last letter. null if empty. */
    fun mutateLastDigitOrCharacter(s: String): String? {
        if (s.isEmpty()) return null
        val i = s.indexOfLast { it.isDigit() }
        if (i >= 0) return s.substring(0, i) + ((s[i] - '0' + 1) % 10) + s.substring(i + 1)
        return mutateOneCharacter(s)
    }

    private fun decoyCode(own: String, preferred: String): String =
        if (own.trim().uppercase(Locale.ROOT) == preferred) "YY${preferred.last()}" else preferred
}
