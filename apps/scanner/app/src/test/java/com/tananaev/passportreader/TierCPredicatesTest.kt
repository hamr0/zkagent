package com.tananaev.passportreader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * M3b opening POC (§6.9.1 criteria 1-2) — the pure evaluators' truth table.
 *
 * **SYNTHETIC, not real-chip evidence.** Every document below is invented. Every expected value is worked out
 * by hand from the calendar (comments show the arithmetic) and is never read from [TierCPredicates]'s own
 * constants or arithmetic; the real-chip evidence is the device run, recorded in
 * docs/logs/M3B-POC-EVIDENCE-2026-10-25.md. Each negative case is paired with a non-vacuity pass on the
 * SAME fields and asserts a count above zero, so a guard that matched nothing would turn the test red.
 */
class TierCPredicatesTest {

    private val today = LocalDate.of(2026, 10, 25)

    private fun fields(
        expiry: String? = "300101",
        dob: String? = "850101",
        nationality: String? = "NLD",
        doc: String? = "SPE123456",
        primary: String? = "DE VRIES",
        secondary: String? = "JAN<PIETER",
        form: TierCFields.MrzForm = TierCFields.MrzForm.TD1,
    ) = TierCFields.fromRaw(expiry, dob, nationality, doc, primary, secondary, form)

    // ---------------------------------------------------------------- expiry buckets

    @Test
    fun `expiry bucket is strictly after today plus N calendar months`() {
        // 2026-10-25 + 3 months = 2027-01-25; + 6 = 2027-04-25; + 12 = 2027-10-25 (hand-computed).
        assertEquals(Answer.True, TierCPredicates.expiryBeyond(3, fields(expiry = "270126"), today))   // one day after
        assertEquals(Answer.False, TierCPredicates.expiryBeyond(3, fields(expiry = "270125"), today))  // exactly on the boundary: not strictly after
        assertEquals(Answer.False, TierCPredicates.expiryBeyond(3, fields(expiry = "270124"), today))  // one day before
        assertEquals(Answer.True, TierCPredicates.expiryBeyond(6, fields(expiry = "270426"), today))
        assertEquals(Answer.False, TierCPredicates.expiryBeyond(6, fields(expiry = "270425"), today))
        assertEquals(Answer.True, TierCPredicates.expiryBeyond(12, fields(expiry = "271026"), today))
        assertEquals(Answer.False, TierCPredicates.expiryBeyond(12, fields(expiry = "271025"), today))
    }

    @Test
    fun `end of month is clamped, not rolled over`() {
        // 2026-08-31 + 6 months: Feb 2027 has 28 days, so the boundary is 2027-02-28 (not Mar 3).
        val aug31 = LocalDate.of(2026, 8, 31)
        assertEquals(Answer.False, TierCPredicates.expiryBeyond(6, fields(expiry = "270228"), aug31))
        assertEquals(Answer.True, TierCPredicates.expiryBeyond(6, fields(expiry = "270301"), aug31))
        // 2027-08-31 + 6 months: Feb 2028 is a leap February, boundary 2028-02-29.
        val aug31Next = LocalDate.of(2027, 8, 31)
        assertEquals(Answer.False, TierCPredicates.expiryBeyond(6, fields(expiry = "280229"), aug31Next))
        assertEquals(Answer.True, TierCPredicates.expiryBeyond(6, fields(expiry = "280301"), aug31Next))
        // 2026-11-30 + 3 months = 2027-02-28.
        assertEquals(Answer.False, TierCPredicates.expiryBeyond(3, fields(expiry = "270228"), LocalDate.of(2026, 11, 30)))
    }

    @Test
    fun `an already expired document is a real false, not a refusal`() {
        assertEquals(Answer.False, TierCPredicates.expiryBeyond(3, fields(expiry = "200101"), today))
    }

    @Test
    fun `an unreadable or missing expiry is cannot tell, never false`() {
        val bad = listOf(null, "", "27X101", "271301", "270230", "2701", "2701011", "AABBCC")
        var refused = 0
        for (b in bad) {
            val a = TierCPredicates.expiryBeyond(3, fields(expiry = b), today)
            assertTrue("expected cannot_tell for '$b' but got $a", a is Answer.CannotTell)
            refused++
        }
        assertEquals(bad.size, refused)
        assertTrue(refused > 0)
        // non-vacuity pair: the same call on a readable field is a real answer
        assertTrue(TierCPredicates.expiryBeyond(3, fields(expiry = "300101"), today) !is Answer.CannotTell)
    }

    @Test
    fun `an off-list bucket is refused`() {
        for (m in listOf(0, 1, 2, 4, 5, 7, 11, 13, 24, -3)) {
            assertEquals(Answer.CannotTell("boundary_off_list"), TierCPredicates.expiryBeyond(m, fields(), today))
        }
        for (m in listOf(3, 6, 12)) assertTrue(TierCPredicates.expiryBeyond(m, fields(), today) !is Answer.CannotTell)
    }

    // ---------------------------------------------------------------- DOB range

    @Test
    fun `dob range is true on the birthday and false the day before`() {
        // dob 2008-10-25 turns 18 on 2026-10-25.
        assertEquals(Answer.True, TierCPredicates.dobAtLeast(18, fields(dob = "081025"), today))
        assertEquals(Answer.False, TierCPredicates.dobAtLeast(18, fields(dob = "081026"), today))
        // 1960-03-01 is 65 on 2025-03-01 (before today) and 60 long before.
        assertEquals(Answer.True, TierCPredicates.dobAtLeast(65, fields(dob = "600301"), today))
        assertEquals(Answer.False, TierCPredicates.dobAtLeast(65, fields(dob = "611026"), today)) // born 1961-10-26: turns 65 on 2026-10-26
        assertEquals(Answer.True, TierCPredicates.dobAtLeast(65, fields(dob = "611025"), today))
    }

    @Test
    fun `all six ages are accepted and nothing else`() {
        for (age in listOf(15, 16, 18, 21, 60, 65)) assertTrue(TierCPredicates.dobAtLeast(age, fields(), today) !is Answer.CannotTell)
        var off = 0
        for (age in listOf(0, 14, 17, 19, 20, 22, 59, 66, 100)) {
            assertEquals(Answer.CannotTell("boundary_off_list"), TierCPredicates.dobAtLeast(age, fields(), today))
            off++
        }
        assertTrue(off > 0)
    }

    @Test
    fun `an unreadable or missing dob is cannot tell`() {
        for (b in listOf(null, "", "85XX01", "851301", "850230", "8501")) {
            assertTrue("for '$b'", TierCPredicates.dobAtLeast(18, fields(dob = b), today) is Answer.CannotTell)
        }
        assertTrue(TierCPredicates.dobAtLeast(18, fields(dob = "850101"), today) !is Answer.CannotTell)
    }

    // ---------------------------------------------------------------- nationality

    @Test
    fun `confirmation mode is exact and case-insensitive on the claim`() {
        assertEquals(Answer.True, TierCPredicates.nationalityIs("NLD", fields(nationality = "NLD")))
        assertEquals(Answer.True, TierCPredicates.nationalityIs("nld", fields(nationality = "NLD")))
        assertEquals(Answer.False, TierCPredicates.nationalityIs("USA", fields(nationality = "NLD")))
        assertEquals(Answer.False, TierCPredicates.nationalityIs("NL", fields(nationality = "NLD")))
    }

    @Test
    fun `an ICAO special code is its own value and is never mapped to an ISO code (D109(g)3)`() {
        // JMRTD reports Germany's MRZ code as "D<<"; fromRaw only drops the filler, so it becomes "D".
        val german = fields(nationality = "D<<")
        assertEquals(Answer.True, TierCPredicates.nationalityIs("D", german))
        assertEquals(Answer.False, TierCPredicates.nationalityIs("DEU", german))
        // and the reverse: a holder whose MRZ really says DEU is not "D"
        assertEquals(Answer.False, TierCPredicates.nationalityIs("D", fields(nationality = "DEU")))
        assertEquals(Answer.True, TierCPredicates.nationalityIs("DEU", fields(nationality = "DEU")))
        // exclusion mode sees the same distinction
        assertEquals(Answer.True, TierCPredicates.nationalityNotIn(listOf("DEU"), german))
        assertEquals(Answer.False, TierCPredicates.nationalityNotIn(listOf("D"), german))
    }

    @Test
    fun `exclusion mode is true only when the holder is outside the set`() {
        val nld = fields(nationality = "NLD")
        assertEquals(Answer.True, TierCPredicates.nationalityNotIn(listOf("CUB", "CHN", "RUS"), nld))
        assertEquals(Answer.False, TierCPredicates.nationalityNotIn(listOf("CUB", "NLD", "RUS"), nld))
        assertEquals(Answer.False, TierCPredicates.nationalityNotIn(listOf("nld"), nld))
    }

    @Test
    fun `an exclusion set over ten countries, or empty, is refused (D102)`() {
        val nld = fields(nationality = "NLD")
        val ten = (1..10).map { "ZA${'A' + it}" }
        val eleven = ten + "ZZZ"
        assertTrue(TierCPredicates.nationalityNotIn(ten, nld) !is Answer.CannotTell)       // pass: exactly 10 is allowed
        assertEquals(Answer.CannotTell("exclusion_set_size"), TierCPredicates.nationalityNotIn(eleven, nld))
        assertEquals(Answer.CannotTell("exclusion_set_size"), TierCPredicates.nationalityNotIn(emptyList(), nld))
        // the cap is on entries as given, even if they are duplicates
        assertEquals(Answer.CannotTell("exclusion_set_size"), TierCPredicates.nationalityNotIn(List(11) { "ZZZ" }, nld))
    }

    @Test
    fun `a missing nationality or a malformed claim is cannot tell`() {
        assertTrue(TierCPredicates.nationalityIs("NLD", fields(nationality = null)) is Answer.CannotTell)
        assertTrue(TierCPredicates.nationalityIs("", fields()) is Answer.CannotTell)
        assertTrue(TierCPredicates.nationalityIs("NL1", fields()) is Answer.CannotTell)
        assertTrue(TierCPredicates.nationalityIs("NLDX", fields()) is Answer.CannotTell)
        assertTrue(TierCPredicates.nationalityNotIn(listOf("N!D"), fields()) is Answer.CannotTell)
        assertTrue(TierCPredicates.nationalityNotIn(listOf("CUB"), fields(nationality = null)) is Answer.CannotTell)
    }

    // ---------------------------------------------------------------- document number

    @Test
    fun `document number is a boolean match after dropping spaces and filler`() {
        val f = fields(doc = "SPE123456")
        assertEquals(Answer.True, TierCPredicates.documentNumberMatches("SPE123456", f))
        assertEquals(Answer.True, TierCPredicates.documentNumberMatches("spe 123456", f))
        assertEquals(Answer.True, TierCPredicates.documentNumberMatches("SPE123456<<<", f))
        assertEquals(Answer.False, TierCPredicates.documentNumberMatches("SPE123457", f)) // one digit off
        assertEquals(Answer.False, TierCPredicates.documentNumberMatches("SPE12345", f))
        assertTrue(TierCPredicates.documentNumberMatches("", f) is Answer.CannotTell)
        assertTrue(TierCPredicates.documentNumberMatches("SPE123456", fields(doc = null)) is Answer.CannotTell)
    }

    // ---------------------------------------------------------------- name match (D101)

    @Test
    fun `normalisation folds case, accents, hyphens and the angle filler`() {
        assertEquals("JOSE", TierCPredicates.normalizeName("José"))
        assertEquals("MULLER", TierCPredicates.normalizeName("Müller"))
        assertEquals("JEAN PIERRE", TierCPredicates.normalizeName("Jean-Pierre"))
        assertEquals("JEAN PIERRE", TierCPredicates.normalizeName("JEAN<PIERRE<<<<"))
        assertEquals("JEAN PIERRE", TierCPredicates.normalizeName("  jean   pierre "))
        assertEquals("", TierCPredicates.normalizeName("<<<"))
    }

    // ---------------------------------------------------------------- name match (D101, D109(k))
    // Every document and site string below is SYNTHETIC. Expected values are written by hand from the ICAO Part 3
    // forms; none is read from TierCNameMatch's own table.

    private fun name(given: String, surname: String, docGiven: String?, docSurname: String?): Answer =
        TierCPredicates.nameMatches(given, surname, fields(primary = docSurname, secondary = docGiven))

    @Test
    fun `two fields - both must match, given names in full, with each half failing alone beside the paired pass`() {
        val f = fields(primary = "DE VRIES", secondary = "JAN<PIETER")
        assertEquals(Answer.True, TierCPredicates.nameMatches("Jan Pieter", "de Vries", f)) // pass pair
        var excluded = 0
        for ((g, s) in listOf("Jan Pieter" to "de Vrees", "Jan Pietr" to "de Vries", "Jan" to "de Vries", "Pieter Jan" to "de Vries", "Jan Pieter Karel" to "de Vries")) {
            assertEquals("$g/$s", Answer.False, TierCPredicates.nameMatches(g, s, f))
            excluded++
        }
        assertEquals(5, excluded)
        // the fields are not interchangeable: surname typed as given names does not match
        assertEquals(Answer.False, TierCPredicates.nameMatches("de Vries", "Jan Pieter", f))
    }

    @Test
    fun `the four D101 rules each match on their own`() {
        val f = fields(primary = "MULLER", secondary = "JEAN<PIERRE")
        assertEquals(Answer.True, TierCPredicates.nameMatches("jean pierre", "muller", f))        // case
        assertEquals(Answer.True, TierCPredicates.nameMatches("Jean-Pierre", "Müller", f))        // hyphen + umlaut as plain letter
        assertEquals(Answer.True, TierCPredicates.nameMatches("JEAN<PIERRE", "MULLER", f))        // angle filler in the site string
        assertEquals(Answer.True, TierCPredicates.nameMatches("Jean\u2010Pierre", "Muller", f))   // unicode hyphen
        assertEquals(Answer.False, TierCPredicates.nameMatches("JeanPierre", "Muller", f))        // hyphen is a separator, not deleted
        assertEquals(Answer.True, TierCPredicates.nameMatches("Jose", "X", fields(primary = "X", secondary = "JOSE")))
        assertEquals(Answer.True, TierCPredicates.nameMatches("Jos\u00e9", "X", fields(primary = "X", secondary = "JOSE")))  // accent, composed
        assertEquals(Answer.True, TierCPredicates.nameMatches("Jose\u0301", "X", fields(primary = "X", secondary = "JOSE"))) // accent, decomposed
    }

    @Test
    fun `SYNTHETIC ICAO transliteration table - one row per mapping, each beside its excluded neighbour`() {
        // (site given, site surname, doc given, doc surname, expected). Document forms are what ICAO Part 3 lets a state print.
        data class Row(val g: String, val s: String, val dg: String?, val ds: String, val expect: Boolean, val why: String)
        val rows = listOf(
            // German umlauts: AE/OE/UE (German documents) AND plain A/O/U (other states) both accepted
            Row("Hans", "Müller", "HANS", "MUELLER", true, "u-umlaut as UE"),
            Row("Hans", "Müller", "HANS", "MULLER", true, "u-umlaut as U"),
            Row("Hans", "Mueller", "HANS", "MUELLER", true, "site already wrote UE"),
            Row("Hans", "Mueller", "HANS", "MULLER", false, "site UE does not collapse to U"),
            Row("Hans", "Müller", "HANS", "MUELER", false, "one letter short"),
            Row("Jörg", "Schmidt", "JOERG", "SCHMIDT", true, "o-umlaut as OE"),
            Row("Jörg", "Schmidt", "JORG", "SCHMIDT", true, "o-umlaut as O"),
            Row("Jörg", "Schmidt", "JEORG", "SCHMIDT", false, "wrong letter"),
            Row("Käthe", "Schmidt", "KAETHE", "SCHMIDT", true, "a-umlaut as AE"),
            Row("Käthe", "Schmidt", "KATHE", "SCHMIDT", true, "a-umlaut as A"),
            Row("Käthe", "Schmidt", "KAATHE", "SCHMIDT", false, "AA is not the umlaut form"),
            Row("Käse", "Schmidt", "KAESE", "SCHMIDT", true, "ambiguous char, long form"),
            Row("Käse", "Schmidt", "KASE", "SCHMIDT", true, "ambiguous char, short form"),
            Row("Käse", "Schmidt", "KAEESE", "SCHMIDT", false, "too long for either form"),
            // sharp s, ligature AE, O-slash, A-ring, thorn
            Row("Hans", "Strauß", "HANS", "STRAUSS", true, "sharp s as SS"),
            Row("Hans", "Strauss", "HANS", "STRAUSS", true, "site already wrote SS"),
            Row("Hans", "Strauß", "HANS", "STRAUS", false, "sharp s is two letters"),
            Row("Ægir", "Holm", "AEGIR", "HOLM", true, "AE ligature as AE"),
            Row("Ægir", "Holm", "EGIR", "HOLM", false, "AE ligature is not E"),
            Row("Søren", "Holm", "SOEREN", "HOLM", true, "O-slash as OE"),
            Row("Søren", "Holm", "SOREN", "HOLM", true, "O-slash as O"),
            Row("Søren", "Holm", "SEREN", "HOLM", false, "O-slash is not E"),
            Row("Åse", "Holm", "AASE", "HOLM", true, "A-ring as AA"),
            Row("Åse", "Holm", "ASE", "HOLM", true, "A-ring as A (accent strip)"),
            Row("Þór", "Holm", "THOR", "HOLM", true, "thorn as TH, o-acute as O"),
            Row("Þór", "Holm", "TOR", "HOLM", false, "thorn is not T"),
            // apostrophe dropped, other diacritics stripped
            Row("Sean", "O'Brien", "SEAN", "OBRIEN", true, "ascii apostrophe dropped"),
            Row("Sean", "O\u2019Brien", "SEAN", "OBRIEN", true, "typographic apostrophe dropped"),
            Row("Sean", "O'Brien", "SEAN", "O BRIEN", false, "apostrophe is not a space"),
            Row("François", "Lefèvre", "FRANCOIS", "LEFEVRE", true, "cedilla and grave stripped"),
            Row("Zoë", "Da Silva", "ZOE", "DA SILVA", true, "diaeresis on e stripped"),
        )
        var excluded = 0
        var confirmed = 0
        for (r in rows) {
            val got = name(r.g, r.s, r.dg, r.ds)
            assertEquals(r.why, Answer.of(r.expect), got)
            if (r.expect) confirmed++ else excluded++
        }
        assertTrue(excluded > 0 && confirmed > 0)
        assertEquals(rows.size, excluded + confirmed)
    }

    @Test
    fun `many ambiguous characters stay fast and still decide correctly`() {
        // 300 a-umlauts: each may be A or AE. 300 'A' is one valid reading; 300 'AE' another; 'AAE...' a mix; 301 'A' is none.
        val site = "\u00c4".repeat(300)
        val f = { s: String -> fields(primary = "X", secondary = s) }
        assertEquals(Answer.True, TierCPredicates.nameMatches(site, "X", f("A".repeat(300))))
        assertEquals(Answer.True, TierCPredicates.nameMatches(site, "X", f("AE".repeat(300))))
        assertEquals(Answer.True, TierCPredicates.nameMatches(site, "X", f("A".repeat(150) + "AE".repeat(150))))
        assertEquals(Answer.False, TierCPredicates.nameMatches(site, "X", f("A".repeat(301))))
        assertEquals(Answer.False, TierCPredicates.nameMatches(site, "X", f("AE".repeat(300) + "E")))
    }

    @Test
    fun `a name truncated by the MRZ field cannot match its full form (disclosed limitation 17(d))`() {
        // TD1 name field is 30 chars; the chip holds only the first 30 of this long name.
        val f = fields(primary = "DE VRIES VAN DER BERG", secondary = "ALEXANDRA<WILHELMINA", form = TierCFields.MrzForm.TD1)
        assertEquals(Answer.False, TierCPredicates.nameMatches("Alexandra Wilhelmina Sophia", "de Vries van der Berg", f))
        assertEquals(Answer.True, TierCPredicates.nameMatches("Alexandra Wilhelmina", "de Vries van der Berg", f)) // pass: the part that fits
    }

    @Test
    fun `a missing primary identifier or an empty site field is cannot tell, and a mononym document is a real false`() {
        assertTrue(TierCPredicates.nameMatches("X", "Y", fields(primary = null)) is Answer.CannotTell)
        assertTrue(TierCPredicates.nameMatches("", "DE VRIES", fields()) is Answer.CannotTell)
        assertTrue(TierCPredicates.nameMatches("Jan", "  ", fields()) is Answer.CannotTell)
        assertTrue(TierCPredicates.nameMatches("'", "DE VRIES", fields()) is Answer.CannotTell) // only an apostrophe: nothing to compare
        // pass pair: complete input on the same fields is a real answer
        assertEquals(Answer.True, TierCPredicates.nameMatches("Jan Pieter", "de Vries", fields()))
        // a document with no given names: a site that sends given names gets a real false, never a refusal
        assertEquals(Answer.False, TierCPredicates.nameMatches("Sukarno", "Sukarno", fields(primary = "SUKARNO", secondary = null)))
    }

    // ---------------------------------------------------------------- fields never print

    @Test
    fun `the field holder never prints its values`() {
        val f = fields(doc = "ZQ7314829", primary = "QWERTYUIOP")
        val s = f.toString()
        assertFalse(s.contains("ZQ7314829"))
        assertFalse(s.contains("QWERTYUIOP"))
        assertFalse(s.contains("NLD"))
        assertTrue(s.contains("redacted"))
    }
}
