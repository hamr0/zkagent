package com.tananaev.passportreader

import java.text.Normalizer
import java.util.Locale

/**
 * M3b item 8(b), D109(k): the two-field name match. The site sends `given_names` and `surname`; each is
 * compared against its OWN MRZ identifier (given names against the secondary identifier, surname against the
 * primary) and BOTH must match. Given names match IN FULL (so "John" against "JOHN PAUL" is false), hyphen and
 * the MRZ `<` filler are word separators, and each side's site string is first transliterated the way ICAO
 * Doc 9303 Part 3 allows a name to be written in the machine-readable zone.
 *
 * **Several written forms can be right for one character.** ICAO lets a state write the German umlauts as
 * AE/OE/UE or as plain A/O/U, so a site typing "Müller" must match a document that prints MUELLER and one that
 * prints MULLER. Each site character therefore maps to a SET of allowed forms and the site word matches a
 * document word if ANY combination equals it ([wordMatches] walks the combinations as a reachable-position set,
 * so cost is linear in the product of lengths, not exponential in the number of umlauts).
 *
 * **Sources for the table (see [FORMS]): read the status column before trusting a row.**
 *  - VERIFIED-SECONDARY: the rows ICAO Doc 9303 Part 3 recommends (Ä AE, Å AA, Æ AE, Ø OE, Ö OE, Ü UE, Þ TH,
 *    ß SS), as reproduced in the "Name Standards" page of the SEVIS Help Hub (studyinthestates.dhs.gov), which says
 *    it copies the table from ICAO Doc 9303. That is a secondary source; the primary ICAO PDF could not be fetched
 *    from this environment (404), so the primary text is NOT checked.
 *  - D109(k)4: ß to SS, Ø to OE, Æ to AE, apostrophe dropped (owner-announced orchestrator defaults).
 *  - UNVERIFIED: the plain-letter alternatives A for Ä, O for Ö, U for Ü (the task brief says ICAO allows them and a
 *    Unicode-list post says the choice "depends on the circumstance", neither quotes the standard) and O for Ø.
 *  - Å's plain-A form comes from the general accent-strip rule of D101, not from a table row.
 * Characters not listed here and not decomposable (for example Ð, Đ, Ł, Œ) are NOT mapped: a name with one
 * reads as no match, which is the safe direction. Proposed finding, not built here.
 *
 * Pure and total: no clock, no logging, never throws, and nothing here ever echoes a site string.
 */
object TierCNameMatch {

    /** One allowed set of MRZ forms per special character (UPPER case key). */
    private val FORMS: Map<Char, List<String>> = mapOf(
        'Ä' to listOf("AE", "A"), // AE verified-secondary; A unverified
        'Ö' to listOf("OE", "O"), // OE verified-secondary; O unverified
        'Ü' to listOf("UE", "U"), // UE verified-secondary; U unverified
        'Å' to listOf("AA", "A"), // AA verified-secondary; A via D101 accent strip
        'Æ' to listOf("AE"),      // verified-secondary, D109(k)4
        'Ø' to listOf("OE", "O"), // OE verified-secondary, D109(k)4; O unverified
        'ß' to listOf("SS"),      // verified-secondary, D109(k)4
        'ẞ' to listOf("SS"),      // capital sharp s, same rule
        'Þ' to listOf("TH"),      // verified-secondary
    )

    /** D109(k)4: apostrophes are dropped (typographic variants included). */
    private val APOSTROPHES = setOf('\'', '’', '‘', 'ʼ', '`', '´')

    private fun isSeparator(c: Char): Boolean =
        c == '<' || c == '-' || c == '‐' || c == '‑' || c == '‒' || c == '–' || c.isWhitespace()

    /** The site string as words, each word a list of per-character allowed forms. Empty words are dropped. */
    internal fun siteWords(raw: String): List<List<List<String>>> {
        val words = ArrayList<List<List<String>>>()
        var current = ArrayList<List<String>>()
        fun flush() {
            if (current.isNotEmpty()) words.add(current)
            current = ArrayList()
        }
        for (c in Normalizer.normalize(raw, Normalizer.Form.NFC)) {
            when {
                isSeparator(c) -> flush()
                c in APOSTROPHES -> Unit
                else -> {
                    val special = FORMS[c.uppercaseChar()] ?: FORMS[c]
                    if (special != null) {
                        current.add(special)
                    } else {
                        val sb = StringBuilder()
                        for (d in Normalizer.normalize(c.toString(), Normalizer.Form.NFD)) {
                            if (d.category != CharCategory.NON_SPACING_MARK) sb.append(d)
                        }
                        val form = sb.toString().uppercase(Locale.ROOT)
                        if (form.isNotEmpty()) current.add(listOf(form))
                    }
                }
            }
        }
        flush()
        return words
    }

    /** True iff some combination of the allowed forms of [alts] equals [doc]. Iterative (no recursion depth). */
    internal fun wordMatches(alts: List<List<String>>, doc: String): Boolean {
        var reach = BooleanArray(doc.length + 1)
        reach[0] = true
        for (forms in alts) {
            val next = BooleanArray(doc.length + 1)
            var any = false
            for (j in 0..doc.length) {
                if (!reach[j]) continue
                for (f in forms) {
                    if (doc.startsWith(f, j)) {
                        next[j + f.length] = true
                        any = true
                    }
                }
            }
            if (!any) return false
            reach = next
        }
        return reach[doc.length]
    }

    private fun docWords(identifier: String?): List<String> =
        TierCPredicates.normalizeName(identifier.orEmpty()).split(' ').filter { it.isNotEmpty() }

    /** One field: same word count and every word matches. */
    internal fun fieldMatches(site: List<List<List<String>>>, identifier: String?): Boolean {
        val doc = docWords(identifier)
        if (site.size != doc.size) return false
        for (i in site.indices) if (!wordMatches(site[i], doc[i])) return false
        return true
    }

    /**
     * Both fields must match. A missing primary identifier or an empty site field is [Answer.CannotTell]
     * (never false). A document with no given names (a mononym) is a real false for a site that sends given names.
     */
    fun matches(givenNames: String, surname: String, fields: TierCFields): Answer {
        if (fields.primaryIdentifier == null) return Answer.CannotTell("name_unreadable")
        val given = siteWords(givenNames)
        val sur = siteWords(surname)
        if (given.isEmpty() || sur.isEmpty()) return Answer.CannotTell("claimed_invalid")
        val givenOk = fieldMatches(given, fields.secondaryIdentifier)
        val surOk = fieldMatches(sur, fields.primaryIdentifier)
        return Answer.of(givenOk && surOk)
    }
}
