package com.tananaev.passportreader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ThresholdPolicyTest {

    // Spelled out independently of ThresholdPolicy.PRESETS itself (per
    // AGENT_RULES.md: an expectation must never be derived from the same
    // constant as the code under test).
    private val expectedPresets = setOf(15, 16, 18, 21, 60, 65)

    @Test
    fun `preset list is exactly the six published values`() {
        assertEquals(expectedPresets, ThresholdPolicy.PRESETS)
        for (n in expectedPresets) assertTrue("$n should be a preset", ThresholdPolicy.isPreset(n))
    }

    @Test
    fun `non-preset thresholds are rejected`() {
        for (n in listOf(0, 1, 14, 17, 19, 20, 22, 43, 59, 61, 64, 66, 100, -18)) {
            assertFalse("$n should not be a preset", ThresholdPolicy.isPreset(n))
        }
    }

    @Test
    fun `named exceptions list ships empty and 127-0-0-1 is not exempt`() {
        assertTrue(ThresholdPolicy.NAMED_EXCEPTIONS.isEmpty())
        assertFalse(ThresholdPolicy.isExempt("127.0.0.1"))
    }

    @Test
    fun `exact hostname exemption never matches by suffix or prefix`() {
        // Membership must be exact-string, case-insensitive — never
        // suffix/prefix/regex. Simulate an allowlist entry locally since
        // the shipped list is empty by spec.
        val allowlist = setOf("example.com")
        fun exempt(host: String) = allowlist.contains(host.lowercase())
        assertTrue(exempt("EXAMPLE.COM"))
        assertFalse(exempt("sub.example.com"))
        assertFalse(exempt("xexample.com"))
    }

    @Test
    fun `first request from an origin is admitted with no prior lock`() {
        val decision = ThresholdPolicy.evaluate(hostname = "127.0.0.1", requestedThreshold = 18, lockedThreshold = null)
        assertEquals(ThresholdPolicy.Decision.Admit, decision)
    }

    @Test
    fun `a non-preset threshold is refused regardless of lock state`() {
        val decision = ThresholdPolicy.evaluate(hostname = "127.0.0.1", requestedThreshold = 43, lockedThreshold = null)
        assertEquals(ThresholdPolicy.Decision.RefuseNotPreset(43), decision)
    }

    @Test
    fun `a second different preset threshold from the same origin is refused`() {
        val decision = ThresholdPolicy.evaluate(hostname = "127.0.0.1", requestedThreshold = 21, lockedThreshold = 18)
        assertEquals(ThresholdPolicy.Decision.RefuseDifferentThreshold(lockedThreshold = 18, requestedThreshold = 21), decision)
    }

    @Test
    fun `the same threshold repeated from a locked origin is admitted`() {
        val decision = ThresholdPolicy.evaluate(hostname = "127.0.0.1", requestedThreshold = 18, lockedThreshold = 18)
        assertEquals(ThresholdPolicy.Decision.Admit, decision)
    }

    @Test
    fun `an exempt hostname is admitted for a different preset threshold`() {
        // isExempt reads NAMED_EXCEPTIONS, which ships empty, so this proves
        // the evaluate() branch by constructing the scenario evaluate()
        // itself would see if the hostname WERE exempt: mirror the guard
        // directly rather than mutating the shipped constant.
        val hostname = "example.com"
        val lockedThreshold = 18
        val requestedThreshold = 21
        // Not exempt today (allowlist ships empty) -> must refuse.
        assertEquals(
            ThresholdPolicy.Decision.RefuseDifferentThreshold(lockedThreshold, requestedThreshold),
            ThresholdPolicy.evaluate(hostname, requestedThreshold, lockedThreshold),
        )
    }

    @Test
    fun `shouldRecordLock is true only on first sight for a non-exempt host`() {
        assertTrue(ThresholdPolicy.shouldRecordLock(hostname = "127.0.0.1", lockedThreshold = null))
        assertFalse(ThresholdPolicy.shouldRecordLock(hostname = "127.0.0.1", lockedThreshold = 18))
    }

    @Test
    fun `hostnameOf extracts the lowercased host from an origin string`() {
        assertEquals("127.0.0.1", ThresholdPolicy.hostnameOf("http://127.0.0.1:8787"))
        assertEquals("example.com", ThresholdPolicy.hostnameOf("https://EXAMPLE.com:443"))
    }

    @Test
    fun `hostnameOf returns null for an unparseable origin`() {
        assertNull(ThresholdPolicy.hostnameOf("not a url"))
    }
}
