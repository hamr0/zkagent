package com.tananaev.passportreader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/** §6.2 item 9 — Canonical.kt must byte-match chiproof's canonicalize()
 * (packages/chiproof/src/canonical.js) for the claim shapes this app signs. */
class CanonicalTest {

    @Test
    fun `claim shape matches chiproof's key-sorted, no-whitespace form`() {
        val claim = mapOf("over_threshold" to true, "threshold" to 18)
        assertEquals("""{"over_threshold":true,"threshold":18}""", Canonical.canonicalize(claim))
    }

    @Test
    fun `Q36 D66 - a false over_threshold claim canonicalizes identically to chiproof's canonical json`() {
        // Cross-checked byte-for-byte against packages/chiproof/src/canonical.js's
        // stringify (no boolean special-casing there either — `false` and
        // `true` take the same code path). This is a REGRESSION test, not
        // a bug fix: Canonical.kt's Boolean branch was already generic.
        val claim = mapOf("over_threshold" to false, "threshold" to 21)
        assertEquals("""{"over_threshold":false,"threshold":21}""", Canonical.canonicalize(claim))
    }

    @Test
    fun `keys are sorted regardless of insertion order`() {
        val a = mapOf("threshold" to 18, "over_threshold" to true)
        val b = mapOf("over_threshold" to true, "threshold" to 18)
        assertEquals(Canonical.canonicalize(a), Canonical.canonicalize(b))
    }

    @Test
    fun `booleans and null render as JS literals`() {
        assertEquals("true", Canonical.canonicalize(true))
        assertEquals("false", Canonical.canonicalize(false))
        assertEquals("null", Canonical.canonicalize(null))
    }

    @Test
    fun `strings are quoted and escaped`() {
        assertEquals("\"hello\"", Canonical.canonicalize("hello"))
        assertEquals("\"a\\\"b\"", Canonical.canonicalize("a\"b"))
    }

    @Test
    fun `arrays keep given order, not sorted`() {
        assertEquals("[3,1,2]", Canonical.canonicalize(listOf(3, 1, 2)))
    }

    @Test
    fun `nested objects sort keys at every level`() {
        val nested = mapOf("b" to mapOf("z" to 1, "a" to 2), "a" to 1)
        assertEquals("""{"a":1,"b":{"a":2,"z":1}}""", Canonical.canonicalize(nested))
    }

    @Test
    fun `unsupported type throws rather than silently coercing`() {
        assertThrows(IllegalArgumentException::class.java) { Canonical.canonicalize(3.14) }
    }
}
