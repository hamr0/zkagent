package com.tananaev.passportreader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
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

    @Test
    fun `order is a caller choice and each order matches only its own arrangement`() {
        val f = fields(primary = "DE VRIES", secondary = "JAN<PIETER")
        // given names first, as a person types it
        assertEquals(Answer.True, TierCPredicates.nameMatches("Jan Pieter de Vries", f, NameOrder.GIVEN_FIRST))
        assertEquals(Answer.False, TierCPredicates.nameMatches("Jan Pieter de Vries", f, NameOrder.SURNAME_FIRST))
        // surname first, as the MRZ prints it
        assertEquals(Answer.True, TierCPredicates.nameMatches("DE VRIES JAN PIETER", f, NameOrder.SURNAME_FIRST))
        assertEquals(Answer.False, TierCPredicates.nameMatches("DE VRIES JAN PIETER", f, NameOrder.GIVEN_FIRST))
    }

    @Test
    fun `a one-character change is a real false under both orders, with the unchanged name as the paired pass`() {
        val f = fields(primary = "DE VRIES", secondary = "JAN<PIETER")
        var excluded = 0
        for (order in NameOrder.values()) {
            val typed = if (order == NameOrder.GIVEN_FIRST) "Jan Pieter de Vries" else "de Vries Jan Pieter"
            val changed = TierCProbe.mutateOneCharacter(typed)!!
            assertNotEquals(typed, changed)
            assertEquals(Answer.True, TierCPredicates.nameMatches(typed, f, order))   // pass
            assertEquals(Answer.False, TierCPredicates.nameMatches(changed, f, order)) // exclusion
            excluded++
        }
        assertEquals(2, excluded)
    }

    @Test
    fun `the four D101 rules each match on their own`() {
        val f = fields(primary = "MULLER", secondary = "JEAN<PIERRE")
        assertEquals(Answer.True, TierCPredicates.nameMatches("jean pierre muller", f, NameOrder.GIVEN_FIRST))   // case
        assertEquals(Answer.True, TierCPredicates.nameMatches("Jean-Pierre Müller", f, NameOrder.GIVEN_FIRST))   // hyphen + accent
        assertEquals(Answer.True, TierCPredicates.nameMatches("JEAN<PIERRE<MULLER", f, NameOrder.GIVEN_FIRST))   // angle filler
        assertEquals(Answer.False, TierCPredicates.nameMatches("Jean Pierre Mueller", f, NameOrder.GIVEN_FIRST)) // ICAO UE is NOT the accent rule
    }

    @Test
    fun `UNRULED edge cases are pinned as current behaviour only, not as a ruling`() {
        // D101 does not name these; the POC reports and does not rule (§6.9.1 criterion 2). The assertions record what
        // the code does TODAY so a change is visible; none of them says what the behaviour SHOULD be.
        // ICAO writes ß as SS, Ø as O, Æ as AE, and omits apostrophes. This code leaves ß/Ø/Æ/' alone.
        assertEquals(Answer.False, TierCPredicates.nameMatches("Strauß", fields(primary = "STRAUSS", secondary = null), NameOrder.GIVEN_FIRST))
        assertEquals(Answer.False, TierCPredicates.nameMatches("Søren X", fields(primary = "X", secondary = "SOREN"), NameOrder.GIVEN_FIRST))
        assertEquals(Answer.False, TierCPredicates.nameMatches("O'Brien", fields(primary = "OBRIEN", secondary = null), NameOrder.GIVEN_FIRST))
        // control: the same names typed the way the MRZ prints them do match, so the lines above are real exclusions.
        assertEquals(Answer.True, TierCPredicates.nameMatches("Strauss", fields(primary = "STRAUSS", secondary = null), NameOrder.GIVEN_FIRST))
        assertEquals(Answer.True, TierCPredicates.nameMatches("Soren X", fields(primary = "X", secondary = "SOREN"), NameOrder.GIVEN_FIRST))
        assertEquals(Answer.True, TierCPredicates.nameMatches("OBrien", fields(primary = "OBRIEN", secondary = null), NameOrder.GIVEN_FIRST))
    }

    @Test
    fun `a name truncated by the MRZ field cannot match its full typed form (disclosed limitation 17(d))`() {
        // TD1 name field is 30 chars; the chip holds only the first 30 of this long name.
        val full = "Alexandra Wilhelmina Sophia de Vries van der Berg" // typed in full
        val truncatedSecondary = "ALEXANDRA<WILHELMINA" // what fits after "DE VRIES VAN DER BERG<<" in 30 chars
        val f = fields(primary = "DE VRIES VAN DER BERG", secondary = truncatedSecondary, form = TierCFields.MrzForm.TD1)
        assertEquals(Answer.False, TierCPredicates.nameMatches(full, f, NameOrder.GIVEN_FIRST))
        assertEquals(Answer.True, TierCPredicates.nameMatches("Alexandra Wilhelmina de Vries van der Berg", f, NameOrder.GIVEN_FIRST)) // pass: the part that fits
        val obs = TierCPredicates.observeName(f)!!
        assertEquals(true, obs.atCapacity) // 21 + 2 + 20 = 43 >= 30
    }

    @Test
    fun `a missing primary identifier is cannot tell and a mononym is legitimate`() {
        assertTrue(TierCPredicates.nameMatches("X", fields(primary = null), NameOrder.GIVEN_FIRST) is Answer.CannotTell)
        assertTrue(TierCPredicates.nameMatches("", fields(), NameOrder.GIVEN_FIRST) is Answer.CannotTell)
        assertEquals(Answer.True, TierCPredicates.nameMatches("Sukarno", fields(primary = "SUKARNO", secondary = null), NameOrder.GIVEN_FIRST))
    }

    // ---------------------------------------------------------------- name observations (criterion 2)

    @Test
    fun `name observation reports capacity by MRZ form and the separator shapes`() {
        val short = TierCPredicates.observeName(fields(primary = "DE VRIES", secondary = "JAN<PIETER", form = TierCFields.MrzForm.TD1))!!
        assertEquals(true, short.lengthKnown)
        assertEquals(false, short.atCapacity)       // 8 + 2 + 10 = 20 < 30
        assertTrue(short.usesAngleFiller)           // JAN<PIETER
        assertTrue(short.usesSpaceSeparator)        // DE VRIES
        assertFalse(short.containsHyphen)
        assertTrue(short.allAscii)
        assertTrue(short.hasSecondary)
        // the same name field is ALSO under a TD3 passport's 39
        assertEquals(false, TierCPredicates.observeName(fields(form = TierCFields.MrzForm.TD3))!!.atCapacity)
        // exactly at capacity on TD1: 8 + 2 + 20 = 30
        val atCap = TierCPredicates.observeName(fields(primary = "DE VRIES", secondary = "A".repeat(20), form = TierCFields.MrzForm.TD1))!!
        assertEquals(true, atCap.atCapacity)
        assertEquals(false, TierCPredicates.observeName(fields(primary = "DE VRIES", secondary = "A".repeat(19), form = TierCFields.MrzForm.TD1))!!.atCapacity)
        // unknown form: capacity cannot be judged, and says so rather than guessing
        val unknown = TierCPredicates.observeName(fields(form = TierCFields.MrzForm.UNKNOWN))!!
        assertEquals(false, unknown.lengthKnown)
        assertNull(unknown.atCapacity)
        assertNull(TierCPredicates.observeName(fields(primary = null)))
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
