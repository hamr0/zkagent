package com.tananaev.passportreader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * D38 amendment (2026-09-01 live-run finding, owner decision: "isolate") —
 * [DeviceKey.aliasForOriginAndZktag] pure logic, no Android/Keystore
 * dependency (SHA-256 + string formatting only), exercisable head-on in a
 * plain JVM unit test.
 *
 * Supersedes the pre-amendment per-origin-only alias test suite: the alias
 * is now scoped to (origin, zktag), not origin alone — a real run found
 * that a per-origin-only key let one verifier see two different documents
 * (scanned at the same origin) share a device key.
 */
class DeviceKeyAliasTest {

    @Test
    fun `aliasForOriginAndZktag is deterministic for the same origin and zktag`() {
        val a = DeviceKey.aliasForOriginAndZktag("https://verifier.example:443", "L898902C3")
        val b = DeviceKey.aliasForOriginAndZktag("https://verifier.example:443", "L898902C3")
        assertEquals(a, b)
    }

    @Test
    fun `aliasForOriginAndZktag is distinct for different origins, same zktag`() {
        val a = DeviceKey.aliasForOriginAndZktag("https://verifier.example:443", "L898902C3")
        val b = DeviceKey.aliasForOriginAndZktag("http://127.0.0.1:8787", "L898902C3")
        val c = DeviceKey.aliasForOriginAndZktag("https://verifier.example:8443", "L898902C3") // port-only difference
        assertNotEquals(a, b)
        assertNotEquals(a, c)
        assertNotEquals(b, c)
    }

    @Test
    fun `aliasForOriginAndZktag is distinct for the same origin, different zktag`() {
        // The exact scenario the live run hit: two different documents
        // (NL ID card, US passport) scanned at the SAME origin must not
        // collapse onto the same alias.
        val origin = "https://verifier.example:443"
        val a = DeviceKey.aliasForOriginAndZktag(origin, "L898902C3")
        val b = DeviceKey.aliasForOriginAndZktag(origin, "999999999")
        assertNotEquals(a, b)
    }

    @Test
    fun `aliasForOriginAndZktag has the documented prefix and fixed-length hex suffix`() {
        val alias = DeviceKey.aliasForOriginAndZktag("https://verifier.example:443", "L898902C3")
        assertTrue("expected zkagent_attester_ prefix, got $alias", alias.startsWith("zkagent_attester_"))
        val suffix = alias.removePrefix("zkagent_attester_")
        assertEquals(32, suffix.length)
        assertTrue("suffix must be lowercase hex, got $suffix", suffix.matches(Regex("[0-9a-f]{32}")))
    }

    @Test
    fun `aliasForOriginAndZktag never carries the raw origin or zktag string`() {
        val origin = "https://verifier.example:443"
        val zktag = "L898902C3"
        val alias = DeviceKey.aliasForOriginAndZktag(origin, zktag)
        assertFalse(alias.contains("verifier"))
        assertFalse(alias.contains("example"))
        assertFalse(alias.contains("443"))
        assertFalse(alias.contains("https"))
        assertFalse(alias.contains("L898902C3"))
        assertFalse(alias.contains("898902"))

        val localOrigin = "http://127.0.0.1:8787"
        val localZktag = "999999999"
        val localAlias = DeviceKey.aliasForOriginAndZktag(localOrigin, localZktag)
        assertFalse(localAlias.contains("127"))
        assertFalse(localAlias.contains("0.0.1"))
        assertFalse(localAlias.contains("8787"))
        assertFalse(localAlias.contains("999999999"))
    }

    @Test
    fun `the separator prevents a boundary-shift collision between (origin,zktag) pairs`() {
        // origin="a", zktag="bc" must NOT collide with origin="ab", zktag="c"
        // — proves the "\n" separator is doing real work, not just present.
        // Neither raw input can ever contain "\n" (origin is a URI;
        // zktag/document_number is MRZ-restricted to [A-Z0-9<]), so
        // concatenation with that separator is unambiguous.
        val ab_c = DeviceKey.aliasForOriginAndZktag("ab", "c")
        val a_bc = DeviceKey.aliasForOriginAndZktag("a", "bc")
        assertNotEquals(ab_c, a_bc)

        // Same pair, order/grouping preserved, still equal (sanity check
        // the collision test above isn't vacuously true for any two args).
        val ab_c2 = DeviceKey.aliasForOriginAndZktag("ab", "c")
        assertEquals(ab_c, ab_c2)
    }

    @Test
    fun `aliasForOriginAndZktag does not collide with PROBE_ALIAS by construction`() {
        // Different naming scheme entirely (zkagent_attester_<hex32> vs the
        // literal PROBE_ALIAS string) — structurally impossible to collide,
        // not merely unlikely. Exercised for a handful of representative
        // (origin, zktag) pairs as a sanity check.
        val pairs = listOf(
            "https://a.example" to "L898902C3",
            "http://127.0.0.1:1" to "999999999",
            "https://verifier.example:443" to "AB1234567",
        )
        for ((o, z) in pairs) {
            assertNotEquals(DeviceKey.PROBE_ALIAS, DeviceKey.aliasForOriginAndZktag(o, z))
        }
    }
}
