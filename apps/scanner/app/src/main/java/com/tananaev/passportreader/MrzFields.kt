package com.tananaev.passportreader

import org.jmrtd.lds.icao.MRZInfo

/**
 * The ONE adapter from JMRTD's `MRZInfo` (the vetted parser; this app never parses a chip file itself) to
 * [TierCFields]. No parsing happens here: JMRTD has already read the file, this only picks fields and maps
 * the MRZ form. Kept out of `MainActivity` and unit-tested against JMRTD's own parser on SYNTHETIC MRZ
 * strings (`MrzFieldsTest`), since it is the exact seam where "what JMRTD returns" meets "what a predicate
 * expects" (the M0/M2 lesson: validate parsers against real captures, not only synthetic ones; the real
 * capture is the criteria 1-2 device run).
 */
object MrzFields {
    fun from(mrz: MRZInfo): TierCFields = TierCFields.fromRaw(
        dateOfExpiry = mrz.dateOfExpiry,
        dateOfBirth = mrz.dateOfBirth,
        nationality = mrz.nationality,
        documentNumber = mrz.documentNumber,
        primaryIdentifier = mrz.primaryIdentifier,
        secondaryIdentifier = mrz.secondaryIdentifier,
        form = when (mrz.documentType) {
            MRZInfo.DOC_TYPE_ID1 -> TierCFields.MrzForm.TD1
            MRZInfo.DOC_TYPE_ID2 -> TierCFields.MrzForm.TD2
            MRZInfo.DOC_TYPE_ID3 -> TierCFields.MrzForm.TD3
            else -> TierCFields.MrzForm.UNKNOWN
        },
    )
}
