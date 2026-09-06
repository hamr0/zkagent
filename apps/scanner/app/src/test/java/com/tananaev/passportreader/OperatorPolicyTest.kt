package com.tananaev.passportreader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * §6.7 POC (D82/D84) truth table for [OperatorPolicy]. The two-argument
 * overloads ([OperatorPolicy.isVerifierAllowed]/[OperatorPolicy.isTierAllowed])
 * are exercised directly with explicit allowlists/modes — never derived
 * from [OperatorPolicy]'s own `BuildConfig`-sourced vals, per
 * AGENT_RULES.md's "an expectation must never be derived from the same
 * constant as the code under test." A separate wiring test below asserts
 * the one-argument convenience overloads actually read THIS build's
 * committed `apps/scanner/operator.json` correctly end-to-end.
 */
class OperatorPolicyTest {

    // ------------------------------------------------ isVerifierAllowed

    @Test
    fun `a listed hostname is allowed`() {
        assertTrue(OperatorPolicy.isVerifierAllowed("state.gov", setOf("state.gov", "127.0.0.1")))
    }

    @Test
    fun `an unlisted hostname is refused`() {
        assertFalse(OperatorPolicy.isVerifierAllowed("evil.example", setOf("state.gov", "127.0.0.1")))
    }

    @Test
    fun `hostname matching is case-insensitive`() {
        assertTrue(OperatorPolicy.isVerifierAllowed("STATE.GOV", setOf("state.gov")))
        assertTrue(OperatorPolicy.isVerifierAllowed("state.gov", setOf("STATE.GOV")))
    }

    @Test
    fun `a port-suffixed hostname never matches a bare allowlist entry`() {
        assertFalse(OperatorPolicy.isVerifierAllowed("127.0.0.1:8787", setOf("127.0.0.1")))
    }

    @Test
    fun `an empty allowlist admits nothing`() {
        assertFalse(OperatorPolicy.isVerifierAllowed("127.0.0.1", emptySet()))
        assertFalse(OperatorPolicy.isVerifierAllowed("", emptySet()))
    }

    @Test
    fun `exact match never matches by suffix or prefix`() {
        val allowlist = setOf("example.com")
        assertFalse(OperatorPolicy.isVerifierAllowed("sub.example.com", allowlist))
        assertFalse(OperatorPolicy.isVerifierAllowed("xexample.com", allowlist))
    }

    // ---------------------------------------------------- isTierAllowed

    @Test
    fun `A+B mode allows both tiers`() {
        assertTrue(OperatorPolicy.isTierAllowed("A", "A+B"))
        assertTrue(OperatorPolicy.isTierAllowed("B", "A+B"))
    }

    @Test
    fun `B-only mode allows B and refuses A`() {
        assertTrue(OperatorPolicy.isTierAllowed("B", "B"))
        assertFalse(OperatorPolicy.isTierAllowed("A", "B"))
    }

    // ------------------------------------- wiring: this build's operator.json

    // The committed reference apps/scanner/operator.json (D84 point 2)
    // declares verifiers=["127.0.0.1"], tiers="A+B", thresholds=the six
    // D74 values, multi_threshold_verifiers=[]. Expected values spelled out
    // independently here, not read back from OperatorPolicy's own fields,
    // so this test can actually fail if Gradle stopped wiring the file
    // through to BuildConfig.

    @Test
    fun `this build's OPERATOR_VERIFIERS wiring contains the committed reference hostname`() {
        assertEquals(setOf("127.0.0.1"), OperatorPolicy.VERIFIERS)
        assertTrue(OperatorPolicy.isVerifierAllowed("127.0.0.1"))
        assertFalse(OperatorPolicy.isVerifierAllowed("evil.example"))
    }

    @Test
    fun `this build's OPERATOR_TIERS wiring is A+B and OPERATOR_THRESHOLDS is the D74 list`() {
        assertEquals("A+B", OperatorPolicy.TIERS)
        assertEquals(setOf(15, 16, 18, 21, 60, 65), OperatorPolicy.THRESHOLDS)
        assertTrue(OperatorPolicy.MULTI_THRESHOLD_VERIFIERS.isEmpty())
    }
}
