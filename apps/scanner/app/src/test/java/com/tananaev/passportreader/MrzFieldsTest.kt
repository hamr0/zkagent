package com.tananaev.passportreader

import org.jmrtd.lds.icao.MRZInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [MrzFields] is the seam between JMRTD's own parser and the tier-C predicates. These run JMRTD's REAL parser over
 * SYNTHETIC MRZ strings, so they pin what JMRTD 0.7.18 actually returns (padding, filler, the unmapped `D<<`)
 * — not what we assume it returns. They are NOT real-chip evidence (that is the device run); the M0/M2 lesson is
 * that a parser checked only on synthetic data misses real wrapper layers.
 */
class MrzFieldsTest {

    private fun pad(s: String, n: Int): String = (s + "<".repeat(n)).substring(0, n)

    private fun td1(name: String = "DE<VRIES<<JAN<PIETER", nationality: String = "NLD"): MRZInfo {
        val l1 = pad("IDNLD" + "SPE123456" + "7", 30)
        val l2 = pad("850101" + "7" + "M" + "310101" + "8" + nationality, 30)
        return MRZInfo(l1 + l2 + pad(name, 30))
    }

    private fun td3(name: String, nationality: String = "USA"): MRZInfo {
        val l1 = pad("P<$nationality$name", 44)
        val l2 = pad("123456789" + "0" + nationality + "850101" + "7" + "F" + "310101" + "8", 44)
        return MRZInfo(l1 + l2)
    }

    @Test
    fun `TD1 identity card fields come through, with the secondary's trailing padding removed`() {
        val raw = td1()
        // JMRTD keeps the padding on the secondary identifier; this pins that fact (it is why fromRaw trims).
        assertTrue(raw.secondaryIdentifier.endsWith("<<<<"))
        val f = MrzFields.from(raw)
        assertEquals(TierCFields.MrzForm.TD1, f.form)
        assertEquals("SPE123456", f.documentNumber)
        assertEquals("310101", f.dateOfExpiry)
        assertEquals("850101", f.dateOfBirth)
        assertEquals("NLD", f.nationality)
        assertEquals("DE VRIES", f.primaryIdentifier)
        assertEquals("JAN<PIETER", f.secondaryIdentifier)
    }

    @Test
    fun `TD3 passport maps to its form and name capacity`() {
        val f = MrzFields.from(td3("SMITH<<MARY<ANN"))
        assertEquals(TierCFields.MrzForm.TD3, f.form)
        assertEquals(39, f.form.nameFieldCapacity)
        assertEquals("SMITH", f.primaryIdentifier)
        assertEquals("MARY<ANN", f.secondaryIdentifier)
        assertEquals("USA", f.nationality)
    }

    @Test
    fun `JMRTD reports Germany as D with filler and the adapter keeps it as D, never DEU (D109(g)3)`() {
        val raw = td3("MUELLER<<ANNA", nationality = "D<<")
        assertEquals("D<<", raw.nationality) // JMRTD does NOT map it; if a library upgrade starts mapping, this goes red
        val f = MrzFields.from(raw)
        assertEquals("D", f.nationality)
        assertEquals(Answer.True, TierCPredicates.nationalityIs("D", f))
        assertEquals(Answer.False, TierCPredicates.nationalityIs("DEU", f))
    }

    @Test
    fun `a single-name holder has no secondary identifier`() {
        val f = MrzFields.from(td3("SUKARNO"))
        assertEquals("SUKARNO", f.primaryIdentifier)
        assertNull(f.secondaryIdentifier)
        assertEquals(TierCPredicates.observeName(f)!!.hasSecondary, false)
    }

    @Test
    fun `the TD1 name field is judged against 30 and a full one is at capacity`() {
        // DE VRIES<<  + 20 given-name characters = exactly 30
        val f = MrzFields.from(td1(name = "DE<VRIES<<" + "A".repeat(20)))
        assertEquals(true, TierCPredicates.observeName(f)!!.atCapacity)
        val g = MrzFields.from(td1(name = "DE<VRIES<<" + "A".repeat(19)))
        assertEquals(false, TierCPredicates.observeName(g)!!.atCapacity)
    }
}
