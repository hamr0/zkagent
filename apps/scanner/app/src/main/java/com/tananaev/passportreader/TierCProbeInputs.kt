package com.tananaev.passportreader

import java.time.LocalDate

/**
 * Pure parse of what the owner typed into the Scan tab (document number, the two dates the DatePicker writes as
 * `yyyy-MM-dd`) and the Diagnostics tab (name, nationality code) into a [TierCProbe.Typed]. Anything missing or
 * not a real date is null, which `MainActivity` turns into "probe not armed": the probe never runs on guessed
 * input. Nothing here is logged.
 */
object TierCProbeInputs {
    fun parse(documentNumber: String?, expiryText: String?, dobText: String?, name: String?, nationality: String?): TierCProbe.Typed? {
        val doc = documentNumber?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val nm = name?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val nat = nationality?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val expiry = parseDate(expiryText) ?: return null
        val dob = parseDate(dobText) ?: return null
        return TierCProbe.Typed(name = nm, nationality = nat, documentNumber = doc, expiry = expiry, dob = dob)
    }

    private fun parseDate(text: String?): LocalDate? {
        if (text == null) return null
        return try {
            LocalDate.parse(text.trim())
        } catch (e: Exception) {
            null
        }
    }
}
