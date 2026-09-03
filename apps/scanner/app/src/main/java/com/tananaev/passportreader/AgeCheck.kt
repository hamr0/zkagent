package com.tananaev.passportreader

import java.time.LocalDate
import java.time.Year

/**
 * Q36/D66 (owner ruling, 2026-09-03) — the real over/under age-threshold
 * answer, computed from the chip's own DG1 date of birth
 * (`MRZInfo.getDateOfBirth()`, ICAO 9303 `YYMMDD`) and D28's client-side
 * coarsened current date. Pure and value-free: this object returns a
 * Boolean (or null when the DOB can't be parsed) and NOTHING else — no
 * caller of [overThreshold] may derive or log a birth year, an age in
 * years, or the parsed [LocalDate] itself. See [MainActivity
 * .mintAndMaybeHandoff]'s D66 block for the one caller and its logging
 * discipline (`over_threshold=<bool> threshold=<int>` only).
 *
 * **Century rule (owner may overturn)**: standard ICAO 9303 MRZ sliding
 * window. A two-digit `YY` is ambiguous between two centuries; this object
 * resolves it against [currentDateUtcMidnight] (the SAME D28-coarsened
 * value threaded through the rest of the mint path — never a second,
 * independent clock read) by picking whichever century does NOT place the
 * birth date in the future relative to that reference: try the 2000s
 * candidate first, and only fall back to the 1900s candidate if the 2000s
 * one is either invalid (e.g. a genuinely non-leap Feb-29 in 2000s vs.
 * 1900s) or itself still after [currentDateUtcMidnight] — a document
 * holder cannot have been born after the (coarsened) current date.
 *
 * **Birthday rule (owner may overturn)**: the holder is over the threshold
 * ON, not after, the calendar day that turns them [thresholdYears] old —
 * i.e. `dob + thresholdYears years <= currentDateUtcMidnight`. A DOB of
 * 29 February is treated as landing on **1 March** in a non-leap
 * threshold year (rather than `java.time`'s default silent clamp to
 * 28 February) — the common legal convention for a leap-day birthday,
 * made explicit here because `LocalDate.plusYears` would otherwise apply
 * it by silently rolling back a day, not forward.
 */
object AgeCheck {

    /**
     * @param dobYYMMDD the chip's raw `MRZInfo.getDateOfBirth()` string.
     * @param currentDateUtcMidnight D28's coarsened "now" — midnight UTC.
     * @param thresholdYears the site's verified threshold (Q35).
     * @return `true`/`false`, or `null` if [dobYYMMDD] is not a well-formed
     *   6-digit MRZ date (wrong length, non-digits, or no valid calendar
     *   date in EITHER candidate century).
     */
    fun overThreshold(dobYYMMDD: String, currentDateUtcMidnight: LocalDate, thresholdYears: Int): Boolean? {
        val dob = parseMrzDate(dobYYMMDD, currentDateUtcMidnight) ?: return null
        val turnsThresholdOn = birthdayLandingFor(dob, thresholdYears)
        return !turnsThresholdOn.isAfter(currentDateUtcMidnight)
    }

    private fun parseMrzDate(raw: String, referenceForCentury: LocalDate): LocalDate? {
        if (raw.length != 6 || !raw.all { it.isDigit() }) return null
        val yy = raw.substring(0, 2).toInt()
        val mm = raw.substring(2, 4).toInt()
        val dd = raw.substring(4, 6).toInt()
        val candidate2000 = tryDate(2000 + yy, mm, dd)
        val candidate1900 = tryDate(1900 + yy, mm, dd)
        return when {
            candidate2000 != null && !candidate2000.isAfter(referenceForCentury) -> candidate2000
            candidate1900 != null -> candidate1900
            else -> candidate2000
        }
    }

    private fun tryDate(year: Int, month: Int, day: Int): LocalDate? =
        try {
            LocalDate.of(year, month, day)
        } catch (e: Exception) {
            null
        }

    /** The calendar date [dob] turns [thresholdYears] old, per the class
     * doc's 29-Feb rule. */
    private fun birthdayLandingFor(dob: LocalDate, thresholdYears: Int): LocalDate {
        if (dob.monthValue == 2 && dob.dayOfMonth == 29) {
            val targetYear = dob.year + thresholdYears
            return if (Year.isLeap(targetYear.toLong())) {
                LocalDate.of(targetYear, 2, 29)
            } else {
                LocalDate.of(targetYear, 3, 1)
            }
        }
        return dob.plusYears(thresholdYears.toLong())
    }
}
