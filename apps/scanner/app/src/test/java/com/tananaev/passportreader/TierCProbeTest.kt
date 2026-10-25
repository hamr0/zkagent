package com.tananaev.passportreader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * M3b POC, criteria 1-2 — the probe battery itself. SYNTHETIC document, invented values. The point of these
 * tests is not that the battery is green on good input; it is that the battery CAN go red (a probe that cannot
 * fail proves nothing), that no value leaks into its lines, and that every group is non-vacuous.
 */
class TierCProbeTest {

    private val today = LocalDate.of(2026, 10, 25)

    // Distinctive synthetic values so a leak into a line is detectable by substring search.
    private val docNumber = "ZQ7314829"
    private val surname = "QUILLFEATHER"
    private val given = "ODALYS"
    private val expiry = LocalDate.of(2031, 3, 19)
    private val dob = LocalDate.of(1988, 7, 2)

    private fun fields(
        exp: String? = TierCPredicates.mrzDate(expiry),
        birth: String? = TierCPredicates.mrzDate(dob),
        nat: String? = "NLD",
        doc: String? = docNumber,
        primary: String? = surname,
        secondary: String? = given,
    ) = TierCFields.fromRaw(exp, birth, nat, doc, primary, secondary, TierCFields.MrzForm.TD1)

    private fun typed(
        name: String = "Odalys Quillfeather",
        nat: String = "NLD",
        doc: String = docNumber,
    ) = TierCProbe.Typed(name, nat, doc, expiry, dob)

    private fun run(f: TierCFields = fields(), t: TierCProbe.Typed = typed(), real: Boolean? = true, tampered: Boolean? = false) =
        TierCProbe.run(f, t, today, real, tampered)

    @Test
    fun `a faithful read of the typed document passes every check and every group is non-vacuous`() {
        val r = run()
        assertTrue("failed: ${r.failed.map { it.id }}", r.failed.isEmpty())
        for (g in listOf("field_reads", "expiry", "dob_range", "document_number", "nationality", "name", "unreadable", "integrity")) {
            assertTrue("group $g has no confirmed positive", r.positivesConfirmed(g) > 0)
            assertTrue("group $g has no excluding negative", r.negativesExcluded(g) > 0)
            assertTrue(r.groupNonVacuous(g))
        }
        assertTrue(r.lines().last().contains("fail=0"))
        assertTrue(r.lines().last().contains("all_groups_non_vacuous=true"))
    }

    @Test
    fun `the three buckets, six ages and every field are each exercised`() {
        val ids = run().checks.map { it.id }
        for (m in listOf(3, 6, 12)) {
            assertTrue(ids.contains("expiry_beyond_${m}mo.true_side"))
            assertTrue(ids.contains("expiry_beyond_${m}mo.false_side"))
        }
        for (a in listOf(15, 16, 18, 21, 60, 65)) {
            assertTrue(ids.contains("dob_at_least_$a.true_side"))
            assertTrue(ids.contains("dob_at_least_$a.false_side"))
        }
        for (id in listOf("nat.confirm_typed", "nat.confirm_decoy", "nat.exclusion_without_own", "nat.exclusion_with_own", "docnum.typed_matches", "docnum.one_off_rejected")) {
            assertTrue(id, ids.contains(id))
        }
    }

    // ---- the battery can go red ----

    @Test
    fun `a chip that disagrees with what the owner typed turns the run red`() {
        val wrongNat = run(f = fields(nat = "USA"))
        assertTrue(wrongNat.failed.any { it.id == "nat.confirm_typed" })
        val wrongDoc = run(f = fields(doc = "ZQ7314830"))
        assertTrue(wrongDoc.failed.any { it.id == "docnum.typed_matches" })
        val wrongExpiry = run(f = fields(exp = "320319"))
        assertTrue(wrongExpiry.failed.any { it.id == "chip_expiry_equals_typed" })
        assertTrue(wrongExpiry.failed.any { it.id.startsWith("expiry_beyond_") })
        val wrongDob = run(f = fields(birth = "880703"))
        assertTrue(wrongDob.failed.any { it.id == "chip_dob_equals_typed" })
        val wrongName = run(f = fields(primary = "SOMEONEELSE"))
        assertTrue(wrongName.failed.any { it.id == "name.typed_true_under_some_order" })
        // control: the faithful read is green, so the reds above are caused by the planted differences
        assertTrue(run().failed.isEmpty())
    }

    @Test
    fun `an integrity result that lets a tampered document through turns the run red`() {
        val r = run(tampered = true)
        assertTrue(r.failed.any { it.id == "integrity.tampered_dg1_not_answerable" })
        val real = run(real = false)
        assertTrue(real.failed.any { it.id == "integrity.real_document_answerable" })
        assertTrue(run().failed.isEmpty())
    }

    @Test
    fun `integrity checks are reported as not run, not silently skipped, when no verdicts are given`() {
        val r = run(real = null, tampered = null)
        assertTrue(r.lines().any { it.contains("integrity_checks ran=false") })
        assertTrue(r.checks.none { it.group == "integrity" })
    }

    @Test
    fun `a plant that does not actually change its input is a failed check, not a green one`() {
        // A name with no ASCII letters cannot be mutated by one-character change.
        val r = run(t = typed(name = "李小龍"))
        assertTrue(r.failed.any { it.id == "name.plant_effective" })
        assertTrue(r.failed.any { it.id == "name.one_char_changed_false_under_every_order" })
        // A document number with no characters at all cannot be mutated.
        assertNull(TierCProbe.mutateLastDigitOrCharacter(""))
        val lastDigit = TierCProbe.mutateLastDigitOrCharacter("AB123")!!
        assertEquals("AB124", lastDigit)
        assertEquals("AB120", TierCProbe.mutateLastDigitOrCharacter("AB129"))
        assertNotEquals("ABC", TierCProbe.mutateLastDigitOrCharacter("ABC"))
        assertEquals("Pdalys", TierCProbe.mutateOneCharacter("Odalys"))
        assertEquals("Az", TierCProbe.mutateOneCharacter("Zz")) // Z wraps to A without throwing
        assertEquals("Ba", TierCProbe.mutateOneCharacter("Aa"))
        assertNull(TierCProbe.mutateOneCharacter("1234"))
    }

    // ---- refusal vs false ----

    @Test
    fun `a corrupted or missing field is a refusal, distinct from a false answer`() {
        val r = run()
        val unreadable = r.checks.filter { it.group == "unreadable" && it.kind == TierCProbe.Kind.NEGATIVE }
        assertTrue(unreadable.size >= 5)
        for (c in unreadable) {
            assertTrue("${c.id} was ${c.actual}", c.actual is Answer.CannotTell)
            assertNotEquals(Answer.False, c.actual)
            assertTrue(c.passed)
        }
        // the paired control: uncorrupted fields are NOT a refusal
        assertTrue(r.checks.first { it.id == "expiry.control_uncorrupted_is_not_refusal" }.passed)
    }

    @Test
    fun `an expected refusal that comes back as a real false is reported as a failure`() {
        // CannotTell expectation must not accept False
        val c = TierCProbe.Check("g", "id", TierCProbe.Kind.NEGATIVE, TierCProbe.Expect.CannotTell, Answer.False, 1)
        assertFalse(c.passed)
        assertTrue(TierCProbe.Check("g", "id", TierCProbe.Kind.NEGATIVE, TierCProbe.Expect.CannotTell, Answer.CannotTell("x"), 1).passed)
        // and a real-false expectation must not accept a refusal
        assertFalse(TierCProbe.Check("g", "id", TierCProbe.Kind.NEGATIVE, TierCProbe.Expect.Is(Answer.False), Answer.CannotTell("x"), 1).passed)
    }

    // ---- name observations ----

    @Test
    fun `both name orders are evaluated and reported, and the winning order drives the rule variants`() {
        val given = run() // typed given-first matches
        assertTrue(given.lines().any { it.contains("name_typed_matches order=given_first result=true") })
        assertTrue(given.lines().any { it.contains("name_typed_matches order=surname_first result=false") })
        val surnameFirst = run(t = typed(name = "Quillfeather Odalys"))
        assertTrue(surnameFirst.lines().any { it.contains("name_typed_matches order=surname_first result=true") })
        assertTrue(surnameFirst.lines().any { it.contains("name_typed_matches order=given_first result=false") })
        assertTrue(surnameFirst.failed.isEmpty())
    }

    @Test
    fun `rule exercise is observed per rule and says what the real name did not touch`() {
        val line = run().lines().first { it.startsWith("observe name_rule_exercised") }
        // typed "Odalys Quillfeather": mixed case yes; no hyphen; no accent; the MRZ name has a space separator in the primary? no
        assertTrue(line.contains("case=true"))
        assertTrue(line.contains("hyphen=false"))
        assertTrue(line.contains("accent=false"))
        val accented = run(f = fields(primary = "MULLER", secondary = "JOSE"), t = typed(name = "José Müller")).lines().first { it.startsWith("observe name_rule_exercised") }
        assertTrue(accented.contains("accent=true"))
        assertTrue(run(t = typed(name = "ODALYS QUILLFEATHER")).lines().first { it.startsWith("observe name_rule_exercised") }.contains("case=false"))
    }

    // ---- no value leaks ----

    @Test
    fun `no line contains a document value, a typed value or a date`() {
        val r = run()
        // durations are random digits; scrub them so a numeric secret cannot collide with a timing by chance
        val text = r.lines().joinToString("\n").replace(Regex("read_to_boolean_us=\\d+"), "read_to_boolean_us=N")
        assertTrue(text.length > 500) // non-vacuity: there is a lot of output to scan
        for (secret in listOf(
            docNumber, surname, given, surname.lowercase(), given.lowercase(), "Odalys", "Quillfeather", "Odalys Quillfeather",
            "2031", "1988", "190331", "310319", "880702", "2031-03-19", "1988-07-02", "ZQ73",
        )) {
            assertFalse("line output leaked '$secret'", text.contains(secret))
        }
        // nationality code is also a value; the decoys are fixed strings and may appear, the holder's own may not
        assertFalse(text.contains("NLD"))
        assertFalse(typed().toString().contains("Odalys"))
        assertFalse(fields().toString().contains(docNumber))
    }

    @Test
    fun `every line is a known shape - checks, groups, observations and one summary`() {
        val lines = run().lines()
        assertTrue(lines.all { it.startsWith("check ") || it.startsWith("group ") || it.startsWith("observe ") || it.startsWith("summary ") })
        assertEquals(1, lines.count { it.startsWith("summary ") })
        assertTrue(lines.count { it.startsWith("check ") } > 50)
        assertNotNull(lines.firstOrNull { it.contains("read_to_boolean_us=") })
    }

    @Test
    fun `the decoy nationality never equals the holder's own`() {
        // typed nationality equal to the first decoy must still produce a pair that excludes
        val r = run(f = fields(nat = "ZZA"), t = typed(nat = "ZZA"))
        assertTrue(r.failed.isEmpty())
        assertTrue(r.negativesExcluded("nationality") > 0)
    }

    // ---- inputs ----

    @Test
    fun `probe inputs refuse to arm on anything missing or not a real date`() {
        assertNotNull(TierCProbeInputs.parse("ZQ7314829", "2031-03-19", "1988-07-02", "Odalys Quillfeather", "NLD"))
        val bad = listOf(
            arrayOf(null, "2031-03-19", "1988-07-02", "N", "NLD"),
            arrayOf("", "2031-03-19", "1988-07-02", "N", "NLD"),
            arrayOf("D", "", "1988-07-02", "N", "NLD"),
            arrayOf("D", "2031-02-30", "1988-07-02", "N", "NLD"),
            arrayOf("D", "31-03-19", "1988-07-02", "N", "NLD"),
            arrayOf("D", "2031-03-19", null, "N", "NLD"),
            arrayOf("D", "2031-03-19", "1988-07-02", "  ", "NLD"),
            arrayOf("D", "2031-03-19", "1988-07-02", "N", ""),
        )
        for (b in bad) assertNull(TierCProbeInputs.parse(b[0], b[1], b[2], b[3], b[4]))
        assertEquals(8, bad.size)
    }
}
