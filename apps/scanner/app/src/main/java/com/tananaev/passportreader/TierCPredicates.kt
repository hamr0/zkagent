package com.tananaev.passportreader

import java.text.Normalizer
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * M3b opening POC (milestones.md §6.9.1, D109(f)) — what the five item-8 tier-C
 * predicates read off a REAL chip's DG1/MRZ, as plain strings, with every
 * JMRTD type kept out of this file so the evaluators below are JVM-unit-testable
 * (this module's unit tests run under `isReturnDefaultValues=true`). The
 * Activity adapter is the ONLY place a JMRTD `MRZInfo` is turned into one of these.
 *
 * **Never logged, never rendered.** [toString] is overridden so a stray
 * `"$fields"` in a log line (data classes print every field by default) prints
 * nothing: the standing rule (D109 §6.9.1, MEMORY.md) is field NAMES, booleans,
 * lengths and timings only. A null field means "unreadable or missing" and every
 * evaluator maps it to [Answer.CannotTell], never to [Answer.False] (§6.9.1
 * criterion 1: "no" and "could not tell" are never conflated).
 *
 * @property dateOfExpiry raw ICAO `YYMMDD`, or null if unreadable/missing.
 * @property dateOfBirth raw ICAO `YYMMDD`, or null.
 * @property nationality the MRZ nationality code exactly as written, trailing `<`
 *   filler removed and upper-cased, nothing else: ISO 3166 alpha-3 stays alpha-3 and an
 *   ICAO special code (for example `D`) stays its own distinct value, never mapped to
 *   `DEU` or anything else (D109(g)3).
 * @property documentNumber the document number with `<` filler removed, upper-cased.
 * @property primaryIdentifier the MRZ primary (surname) identifier as JMRTD reports it.
 * @property secondaryIdentifier the MRZ secondary (given names) identifier as JMRTD
 *   reports it (JMRTD keeps `<` between given names); null when the holder has none.
 * @property form the MRZ form (TD1/TD2/TD3), used only to know the name field's capacity.
 */
data class TierCFields(
    val dateOfExpiry: String?,
    val dateOfBirth: String?,
    val nationality: String?,
    val documentNumber: String?,
    val primaryIdentifier: String?,
    val secondaryIdentifier: String?,
    val form: MrzForm,
) {
    override fun toString(): String = "TierCFields(<redacted>)"

    enum class MrzForm(val nameFieldCapacity: Int?) {
        TD1(30), TD2(31), TD3(39), UNKNOWN(null)
    }

    companion object {
        /** The ONE normaliser from raw JMRTD strings to a [TierCFields]. Blank means
         * missing (null), so a blank field can never be evaluated as a value. */
        fun fromRaw(
            dateOfExpiry: String?,
            dateOfBirth: String?,
            nationality: String?,
            documentNumber: String?,
            primaryIdentifier: String?,
            secondaryIdentifier: String?,
            form: MrzForm,
        ): TierCFields = TierCFields(
            dateOfExpiry = dateOfExpiry?.trim()?.takeIf { it.isNotEmpty() },
            dateOfBirth = dateOfBirth?.trim()?.takeIf { it.isNotEmpty() },
            nationality = nationality?.trim()?.trimEnd('<')?.uppercase(Locale.ROOT)?.takeIf { it.isNotEmpty() },
            documentNumber = documentNumber?.trim()?.replace("<", "")?.uppercase(Locale.ROOT)?.takeIf { it.isNotEmpty() },
            primaryIdentifier = primaryIdentifier?.trim()?.takeIf { it.isNotEmpty() },
            secondaryIdentifier = secondaryIdentifier?.trim()?.trimEnd('<')?.takeIf { it.isNotEmpty() },
            form = form,
        )
    }
}

/**
 * The three-way result every predicate returns (§6.9.1 criterion 1). [True] and
 * [False] are real answers; [CannotTell] is a refusal ("could not tell"): the
 * all-or-nothing gate (item 13) turns any [CannotTell] into a refused request and
 * the site learns nothing. [CannotTell.reason] is a fixed, value-free code.
 */
sealed class Answer {
    object True : Answer() { override fun toString() = "true" }
    object False : Answer() { override fun toString() = "false" }
    data class CannotTell(val reason: String) : Answer() { override fun toString() = "cannot_tell($reason)" }

    companion object {
        fun of(b: Boolean): Answer = if (b) True else False
    }
}

/** How a typed or site-supplied full name is ordered against the MRZ name (§6.9.1
 * leaves this open; see [TierCPredicates.nameMatches]). */
enum class NameOrder { GIVEN_FIRST, SURNAME_FIRST }

/**
 * The five item-8 predicate evaluators as pure functions of ([TierCFields],
 * coarsened current date). Boundary lists are FIXED (item 9, D95/D97): an
 * off-list value is [Answer.CannotTell]("boundary_off_list"), the fail-loud no-mint
 * shape. `today` is the D28-coarsened midnight-UTC date the caller threads through;
 * these functions never read a clock.
 */
object TierCPredicates {

    /** Item 9, D97: expiry buckets, in calendar months. */
    val EXPIRY_BUCKET_MONTHS: List<Int> = listOf(3, 6, 12)

    /** Item 9, D97: DOB-range ages, identical to D74's list. */
    val DOB_AGES: List<Int> = listOf(15, 16, 18, 21, 60, 65)

    /** Item 8(d), D102: an exclusion set may name at most this many countries. */
    const val MAX_EXCLUSION_SET = 10

    private val MRZ_DATE = DateTimeFormatter.ofPattern("yyMMdd", Locale.ROOT)

    /** `YYMMDD` of [date], the way an MRZ writes it (used to compare a typed date to a chip date). */
    fun mrzDate(date: LocalDate): String = date.format(MRZ_DATE)

    /**
     * Expiry bucket (item 9, D109(g)4): TRUE iff the document's date of expiry is strictly
     * after `today + months` calendar months, end-of-month clamped (`LocalDate.plusMonths`
     * clamps, so 31 Aug + 6 months is 28/29 Feb). Strictly after: D90 writes the bucket
     * as `expiry > 6mo`. An already-expired document is a real FALSE, not a refusal.
     * The two-digit year is read as 20YY (a document in circulation expires after 2000),
     * which is checked here, not assumed: a date that is not a real calendar date is
     * CannotTell.
     */
    fun expiryBeyond(months: Int, fields: TierCFields, today: LocalDate): Answer {
        if (months !in EXPIRY_BUCKET_MONTHS) return Answer.CannotTell("boundary_off_list")
        val expiry = parseExpiry(fields.dateOfExpiry) ?: return Answer.CannotTell("expiry_unreadable")
        return Answer.of(expiry.isAfter(today.plusMonths(months.toLong())))
    }

    private fun parseExpiry(raw: String?): LocalDate? {
        if (raw == null || raw.length != 6 || !raw.all { it in '0'..'9' }) return null
        return try {
            LocalDate.of(2000 + raw.substring(0, 2).toInt(), raw.substring(2, 4).toInt(), raw.substring(4, 6).toInt())
        } catch (e: Exception) {
            null
        }
    }

    /** DOB range: TRUE iff the holder is [age] or older on [today]. Reuses [AgeCheck] (D66)
     * so the tier-A/B and tier-C age answers can never disagree. */
    fun dobAtLeast(age: Int, fields: TierCFields, today: LocalDate): Answer {
        if (age !in DOB_AGES) return Answer.CannotTell("boundary_off_list")
        val dob = fields.dateOfBirth ?: return Answer.CannotTell("dob_unreadable")
        val over = AgeCheck.overThreshold(dob, today, age) ?: return Answer.CannotTell("dob_unreadable")
        return Answer.of(over)
    }

    private fun normalizeCode(code: String): String = code.trim().trimEnd('<').uppercase(Locale.ROOT)

    /** Nationality, confirmation mode: TRUE iff the holder's nationality code equals [claimed]
     * exactly (D109(g)3: no mapping, `D` and `DEU` are different values). */
    fun nationalityIs(claimed: String, fields: TierCFields): Answer {
        val have = fields.nationality ?: return Answer.CannotTell("nationality_unreadable")
        val want = normalizeCode(claimed)
        if (want.isEmpty() || want.length > 3 || !want.all { it in 'A'..'Z' }) return Answer.CannotTell("claimed_invalid")
        return Answer.of(have == want)
    }

    /** Nationality, exclusion mode: TRUE iff the holder's nationality is NOT in [excluded]. More
     * than [MAX_EXCLUSION_SET] entries (or none) is a refusal (D102), counted as given. */
    fun nationalityNotIn(excluded: List<String>, fields: TierCFields): Answer {
        if (excluded.isEmpty() || excluded.size > MAX_EXCLUSION_SET) return Answer.CannotTell("exclusion_set_size")
        val have = fields.nationality ?: return Answer.CannotTell("nationality_unreadable")
        val set = excluded.map { normalizeCode(it) }
        if (set.any { it.isEmpty() || it.length > 3 || !it.all { c -> c in 'A'..'Z' } }) return Answer.CannotTell("claimed_invalid")
        return Answer.of(have !in set)
    }

    /** Document-number match: boolean only, the number is never returned (D90 item 5). Compared
     * after upper-casing and dropping spaces and `<`. */
    fun documentNumberMatches(claimed: String, fields: TierCFields): Answer {
        val have = fields.documentNumber ?: return Answer.CannotTell("document_number_unreadable")
        val want = claimed.uppercase(Locale.ROOT).replace("<", "").replace(" ", "")
        if (want.isEmpty()) return Answer.CannotTell("claimed_invalid")
        return Answer.of(have == want)
    }

    /**
     * D101 normalisation, applied to BOTH sides: decompose and drop combining marks (accent
     * strip); treat `<`, `-` and the Unicode hyphens as word separators; upper-case each character
     * one-for-one (`Char.uppercaseChar`, never a locale or multi-character expansion); collapse runs
     * of whitespace.
     *
     * Interpretations the owner may overturn (the POC reports, it does not rule): a hyphen is a
     * SEPARATOR (so `Jean-Pierre` equals the MRZ's `JEAN<PIERRE`), not deleted; and characters D101 does
     * not name are left alone: `ß`, `Ø`, `Æ` and apostrophes are NOT transliterated (ICAO writes `SS`,
     * `O`, `AE` and omits apostrophes in the MRZ, so such names are an unruled gap, §6.9.1 criterion 2).
     */
    fun normalizeName(raw: String): String {
        val sb = StringBuilder(raw.length)
        for (c in Normalizer.normalize(raw, Normalizer.Form.NFD)) {
            if (c.category == CharCategory.NON_SPACING_MARK) continue
            when (c) {
                '<', '-', '‐', '‑' -> sb.append(' ')
                else -> sb.append(c.uppercaseChar())
            }
        }
        return sb.toString().trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.joinToString(" ")
    }

    /**
     * Name match, boolean only (Q22: no score). The comparison is on the whole name as one string.
     * WHICH ORDER a site's string follows (given names first, as a person types it, or surname first,
     * as the MRZ prints it) is NOT ruled by D101 or §6.9.1, so the caller names the order and the POC
     * probe evaluates both; this function does not choose one. A missing primary identifier is
     * CannotTell; a missing secondary is legitimate (a mononym) and is simply absent.
     */
    fun nameMatches(claimed: String, fields: TierCFields, order: NameOrder): Answer {
        val primary = fields.primaryIdentifier ?: return Answer.CannotTell("name_unreadable")
        val given = fields.secondaryIdentifier.orEmpty()
        val wanted = normalizeName(claimed)
        if (wanted.isEmpty()) return Answer.CannotTell("claimed_invalid")
        val document = normalizeName(
            when (order) {
                NameOrder.GIVEN_FIRST -> "$given $primary"
                NameOrder.SURNAME_FIRST -> "$primary $given"
            },
        )
        return Answer.of(wanted == document)
    }

    /**
     * Value-free facts about the REAL name field, for §6.9.1 criterion 2: is it at its capacity
     * (possible truncation, D101/17(d)), and which separator shapes does it show. Booleans and a
     * length only.
     */
    data class NameObservation(
        val lengthKnown: Boolean,
        val atCapacity: Boolean?,
        val usesAngleFiller: Boolean,
        val usesSpaceSeparator: Boolean,
        val containsHyphen: Boolean,
        val allAscii: Boolean,
        val hasSecondary: Boolean,
    )

    fun observeName(fields: TierCFields): NameObservation? {
        val primary = fields.primaryIdentifier ?: return null
        val secondary = fields.secondaryIdentifier
        // MRZ name field = PRIMARY<<SECONDARY with word separators written as '<'; JMRTD returns the
        // primary with spaces for those separators and the secondary with '<' kept.
        val rawLength = primary.length + if (secondary != null) 2 + secondary.length else 0
        val cap = fields.form.nameFieldCapacity
        val all = primary + (secondary ?: "")
        return NameObservation(
            lengthKnown = cap != null,
            atCapacity = cap?.let { rawLength >= it },
            usesAngleFiller = all.contains('<'),
            usesSpaceSeparator = all.contains(' '),
            containsHyphen = all.contains('-'),
            allAscii = all.all { it.code < 128 },
            hasSecondary = secondary != null,
        )
    }
}
