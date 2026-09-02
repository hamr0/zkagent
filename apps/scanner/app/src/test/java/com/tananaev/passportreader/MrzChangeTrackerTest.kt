package com.tananaev.passportreader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * D56's MRZ-change diagnostic. [MrzChangeTracker.hash] and
 * [MrzChangeTracker.compare] are plain JVM code (java.security.MessageDigest
 * is a real, non-stubbed class in this module, unlike the Android views
 * [PaneVisibilityTest] cannot touch) — so both the hashing and the
 * comparison are directly testable, not just the log-line formatting.
 */
class MrzChangeTrackerTest {

    private val saltA = ByteArray(32) { it.toByte() }
    private val saltB = ByteArray(32) { (it + 1).toByte() }

    @Test
    fun `same fields and same salt hash identically`() {
        val h1 = MrzChangeTracker.hash("L898902C3", "740812", "120415", saltA)
        val h2 = MrzChangeTracker.hash("L898902C3", "740812", "120415", saltA)
        assertEquals(h1, h2)
    }

    @Test
    fun `changing any one field changes the hash`() {
        val base = MrzChangeTracker.hash("L898902C3", "740812", "120415", saltA)
        assertNotEquals(base, MrzChangeTracker.hash("L898902C4", "740812", "120415", saltA))
        assertNotEquals(base, MrzChangeTracker.hash("L898902C3", "740813", "120415", saltA))
        assertNotEquals(base, MrzChangeTracker.hash("L898902C3", "740812", "120416", saltA))
    }

    @Test
    fun `field boundary shifts do not collide - concatenation is unambiguous`() {
        // Without a separator, ("AB","C") and ("A","BC") would hash the
        // same via naive string concatenation. hash() must not collide here.
        val h1 = MrzChangeTracker.hash("AB", "C", "X", saltA)
        val h2 = MrzChangeTracker.hash("A", "BC", "X", saltA)
        assertNotEquals(h1, h2)
    }

    @Test
    fun `a different salt changes the hash for identical fields`() {
        val h1 = MrzChangeTracker.hash("L898902C3", "740812", "120415", saltA)
        val h2 = MrzChangeTracker.hash("L898902C3", "740812", "120415", saltB)
        assertNotEquals(h1, h2)
    }

    @Test
    fun `the hash never contains the raw field values`() {
        val h = MrzChangeTracker.hash("L898902C3", "740812", "120415", saltA)
        assertTrue(!h.contains("L898902C3") && !h.contains("740812") && !h.contains("120415"))
        // hex-encoded SHA-256 digest: fixed 64 hex characters, nothing else.
        assertEquals(64, h.length)
        assertTrue(h.all { it in "0123456789abcdef" })
    }

    // ------------------------------------------------------------- compare

    @Test
    fun `no previous hash is FirstAttempt, not Changed`() {
        val comparison = MrzChangeTracker.compare(previousHash = null, currentHash = "abc", docLen = 9, dobOk = true, expOk = true)
        assertTrue(comparison is MrzChangeTracker.Comparison.FirstAttempt)
    }

    @Test
    fun `identical hash is Unchanged`() {
        val comparison = MrzChangeTracker.compare(previousHash = "abc", currentHash = "abc", docLen = 9, dobOk = true, expOk = true)
        assertTrue(comparison is MrzChangeTracker.Comparison.Unchanged)
    }

    @Test
    fun `different hash is Changed`() {
        val comparison = MrzChangeTracker.compare(previousHash = "abc", currentHash = "def", docLen = 9, dobOk = true, expOk = true)
        assertTrue(comparison is MrzChangeTracker.Comparison.Changed)
    }

    @Test
    fun `docLen and dobOk and expOk pass through into the comparison verbatim`() {
        val comparison = MrzChangeTracker.compare(previousHash = "abc", currentHash = "def", docLen = 7, dobOk = false, expOk = true)
        assertEquals(7, comparison.docLen)
        assertEquals(false, comparison.dobOk)
        assertEquals(true, comparison.expOk)
    }

    // ------------------------------------------------------------- logLine

    @Test
    fun `logLine for FirstAttempt is distinct from CHANGED, and value-free`() {
        val line = MrzChangeTracker.logLine(MrzChangeTracker.Comparison.FirstAttempt(docLen = 9, dobOk = true, expOk = true))
        assertEquals("M2 stage: MRZ input first attempt this session (doc_len=9 dob_ok=true exp_ok=true)", line)
    }

    @Test
    fun `logLine for Changed matches the owner-approved shape exactly`() {
        val line = MrzChangeTracker.logLine(MrzChangeTracker.Comparison.Changed(docLen = 9, dobOk = true, expOk = true))
        assertEquals("M2 stage: MRZ input CHANGED since previous attempt (doc_len=9 dob_ok=true exp_ok=true)", line)
    }

    @Test
    fun `logLine for Unchanged matches the owner-approved shape exactly`() {
        val line = MrzChangeTracker.logLine(MrzChangeTracker.Comparison.Unchanged(docLen = 9, dobOk = true, expOk = true))
        assertEquals("M2 stage: MRZ input UNCHANGED since previous attempt (doc_len=9 dob_ok=true exp_ok=true)", line)
    }

    @Test
    fun `logLine never contains anything but the verdict, doc_len, dob_ok, exp_ok`() {
        val line = MrzChangeTracker.logLine(MrzChangeTracker.Comparison.Changed(docLen = 9, dobOk = false, expOk = true))
        // Guards against a future edit accidentally interpolating a raw
        // field into the line — the string must be built entirely from
        // the four named, value-free components.
        assertEquals("M2 stage: MRZ input CHANGED since previous attempt (doc_len=9 dob_ok=false exp_ok=true)", line)
    }
}
