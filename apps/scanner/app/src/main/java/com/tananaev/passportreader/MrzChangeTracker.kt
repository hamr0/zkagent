package com.tananaev.passportreader

import java.security.MessageDigest

/**
 * §6.2 (D56): a value-free diagnostic answering "did the corrected MRZ
 * details actually reach the app before this read attempt?" — extracted to
 * a pure, testable helper the same way [FailureTransition]/[MintGate] are.
 *
 * BACKGROUND (the real-device bug this diagnoses): a user stranded on a
 * stale MRZ (see [PaneVisibility]'s doc for the D55 pane bug that caused
 * it) re-tapped the document several times, and every tap re-read the SAME
 * wrong key with no way to tell, from the log alone, whether a since-typed
 * correction had actually reached this read attempt. This is the ONE
 * datum that answers that question in one line, without ever naming the
 * MRZ values themselves.
 *
 * NEVER LOG THE FIELD VALUES. [hash] takes the raw passport number/date
 * strings only to produce an opaque digest; [Comparison] and [logLine]
 * carry only: the comparison verdict, the document-number LENGTH, and
 * whether each date parsed. The digest itself is held only in an in-memory
 * field by the caller ([MainActivity.lastMrzHash]) — never rendered, never
 * written to `reportView`/`ReportLog`, never in `onSaveInstanceState`,
 * never on disk.
 *
 * SALTED, deliberately: a bare truncated SHA-256 of a short document
 * number (typically <=9 characters from a small alphabet) is trivially
 * brute-forceable — an unsalted digest of it would itself be PII. [hash]
 * requires a caller-supplied salt; [MainActivity] generates that salt ONCE
 * per process (a companion-object field, not per-Activity-instance, so an
 * Activity recreation does not spuriously read as a "changed" first
 * attempt) and never persists it.
 */
object MrzChangeTracker {

    /** The outcome of comparing this attempt's MRZ hash against the
     * previous one — see [MainActivity]'s call site for how [docLen]/
     * [dobOk]/[expOk] are derived; none of the three ever carries a field
     * value, only its shape. */
    sealed class Comparison {
        abstract val docLen: Int
        abstract val dobOk: Boolean
        abstract val expOk: Boolean

        /** No previous hash exists yet in this process — distinct from
         * [Changed] so the log line does not imply a prior attempt that
         * never happened. */
        data class FirstAttempt(override val docLen: Int, override val dobOk: Boolean, override val expOk: Boolean) : Comparison()
        data class Changed(override val docLen: Int, override val dobOk: Boolean, override val expOk: Boolean) : Comparison()
        data class Unchanged(override val docLen: Int, override val dobOk: Boolean, override val expOk: Boolean) : Comparison()
    }

    /** Salted digest of the three MRZ fields. Never log or persist the
     * return value anywhere but an in-memory field — see the class doc. */
    fun hash(passportNumber: String, birthDate: String, expirationDate: String, salt: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(salt)
        // Length-prefix-free but unambiguous: a NUL separator between
        // fields, none of which can itself contain a NUL byte (MRZ dates
        // are numeric, the document number is uppercase alphanumeric).
        digest.update(passportNumber.toByteArray(Charsets.UTF_8))
        digest.update(0)
        digest.update(birthDate.toByteArray(Charsets.UTF_8))
        digest.update(0)
        digest.update(expirationDate.toByteArray(Charsets.UTF_8))
        return digest.digest().joinToString(separator = "") { "%02x".format(it) }
    }

    /** Compares [currentHash] against [previousHash] (null = no attempt
     * yet this process) and returns the value-free [Comparison]. */
    fun compare(previousHash: String?, currentHash: String, docLen: Int, dobOk: Boolean, expOk: Boolean): Comparison = when {
        previousHash == null -> Comparison.FirstAttempt(docLen, dobOk, expOk)
        previousHash == currentHash -> Comparison.Unchanged(docLen, dobOk, expOk)
        else -> Comparison.Changed(docLen, dobOk, expOk)
    }

    /** The exact `Log.i` line for [comparison] — owner-approved shape
     * (D56):
     * `M2 stage: MRZ input UNCHANGED since previous attempt (doc_len=9 dob_ok=true exp_ok=true)`
     * `M2 stage: MRZ input CHANGED since previous attempt (doc_len=9 dob_ok=true exp_ok=true)`
     * plus a distinct first-attempt variant. */
    fun logLine(comparison: Comparison): String {
        val verdict = when (comparison) {
            is Comparison.FirstAttempt -> "first attempt this session"
            is Comparison.Changed -> "CHANGED since previous attempt"
            is Comparison.Unchanged -> "UNCHANGED since previous attempt"
        }
        return "M2 stage: MRZ input $verdict (doc_len=${comparison.docLen} dob_ok=${comparison.dobOk} exp_ok=${comparison.expOk})"
    }
}
