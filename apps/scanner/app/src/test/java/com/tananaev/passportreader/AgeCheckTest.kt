package com.tananaev.passportreader

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Q36/D66 — AgeCheck.overThreshold's real over/under answer. Expectations
 * below are computed independently from the birthday/century rules stated
 * in AgeCheck's own class doc, never from a shared constant with the code
 * under test. */
class AgeCheckTest {

    // ---- ordinary birthday, non-leap DOB ----------------------------------

    @Test
    fun `day before the 18th birthday is under`() {
        // DOB 2008-03-15 -> 18th birthday 2026-03-15.
        assertEquals(false, AgeCheck.overThreshold("080315", LocalDate.of(2026, 3, 14), 18))
    }

    @Test
    fun `the birthday itself is over`() {
        assertEquals(true, AgeCheck.overThreshold("080315", LocalDate.of(2026, 3, 15), 18))
    }

    @Test
    fun `the day after the birthday is over`() {
        assertEquals(true, AgeCheck.overThreshold("080315", LocalDate.of(2026, 3, 16), 18))
    }

    // ---- 29 Feb births ------------------------------------------------------

    @Test
    fun `29 Feb DOB in a leap threshold year lands on 29 Feb`() {
        // DOB 2004-02-29, threshold 16 -> target year 2020 (leap) -> 2020-02-29.
        assertEquals(false, AgeCheck.overThreshold("040229", LocalDate.of(2020, 2, 28), 16))
        assertEquals(true, AgeCheck.overThreshold("040229", LocalDate.of(2020, 2, 29), 16))
    }

    @Test
    fun `29 Feb DOB in a non-leap threshold year lands on 1 March, not 28 Feb`() {
        // DOB 2000-02-29, threshold 18 -> target year 2018 (non-leap) -> 2018-03-01.
        // Not yet over on Feb 28 (the naive `plusYears` "clamped" date) —
        assertEquals(false, AgeCheck.overThreshold("000229", LocalDate.of(2018, 2, 28), 18))
        // — only from 1 March.
        assertEquals(true, AgeCheck.overThreshold("000229", LocalDate.of(2018, 3, 1), 18))
    }

    // ---- century sliding window ---------------------------------------------

    @Test
    fun `century boundary - YY resolves to 1900s when the 2000s candidate would be in the future`() {
        // yy=30: if "now" is 2025-06-01, a 2030 birth would be in the
        // future, so this must resolve to 1930-06-01, making the holder
        // long over any real threshold.
        assertEquals(true, AgeCheck.overThreshold("300601", LocalDate.of(2025, 6, 1), 18))
    }

    @Test
    fun `century boundary - YY resolves to 2000s when that candidate is not in the future`() {
        // yy=10, "now" is 2025-06-01: 2010-06-01 is not in the future, so
        // this resolves to the 2000s candidate -> 15 years old, under 18.
        assertEquals(false, AgeCheck.overThreshold("100601", LocalDate.of(2025, 6, 1), 18))
    }

    @Test
    fun `century boundary - 2000s candidate exactly on the reference date is not future, resolves 2000s`() {
        // yy=25, "now" is exactly 2025-06-01: the 2000s candidate equals the
        // reference date (not after it) so it's chosen -> 0 years old.
        assertEquals(false, AgeCheck.overThreshold("250601", LocalDate.of(2025, 6, 1), 18))
    }

    // ---- unparsable input -----------------------------------------------------

    @Test
    fun `wrong length is unparsable`() {
        assertNull(AgeCheck.overThreshold("80315", LocalDate.of(2026, 1, 1), 18))
        assertNull(AgeCheck.overThreshold("0803150", LocalDate.of(2026, 1, 1), 18))
    }

    @Test
    fun `non-digit characters are unparsable`() {
        assertNull(AgeCheck.overThreshold("08031X", LocalDate.of(2026, 1, 1), 18))
    }

    @Test
    fun `no valid calendar date in either century is unparsable`() {
        // Month 13 doesn't exist in the 2000s OR the 1900s.
        assertNull(AgeCheck.overThreshold("081315", LocalDate.of(2026, 1, 1), 18))
    }

    // ---- literal thresholds 16 / 18 / 21 ---------------------------------------

    @Test
    fun `threshold 16 - exact literal expectations`() {
        // DOB 2010-01-01 -> turns 16 on 2026-01-01.
        assertEquals(false, AgeCheck.overThreshold("100101", LocalDate.of(2025, 12, 31), 16))
        assertEquals(true, AgeCheck.overThreshold("100101", LocalDate.of(2026, 1, 1), 16))
    }

    @Test
    fun `threshold 18 - exact literal expectations`() {
        // DOB 2008-01-01 -> turns 18 on 2026-01-01.
        assertEquals(false, AgeCheck.overThreshold("080101", LocalDate.of(2025, 12, 31), 18))
        assertEquals(true, AgeCheck.overThreshold("080101", LocalDate.of(2026, 1, 1), 18))
    }

    @Test
    fun `threshold 21 - exact literal expectations`() {
        // DOB 2005-01-01 -> turns 21 on 2026-01-01.
        assertEquals(false, AgeCheck.overThreshold("050101", LocalDate.of(2025, 12, 31), 21))
        assertEquals(true, AgeCheck.overThreshold("050101", LocalDate.of(2026, 1, 1), 21))
    }
}
