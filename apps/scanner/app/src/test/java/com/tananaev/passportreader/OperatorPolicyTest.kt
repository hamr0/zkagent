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
        // G6 (2026-09-06 validation pass) — "127.0.0.1:8787" is the exact
        // shape M3's real dev origin looks like as a string; this is what
        // explains the device round-1 admit (verifiers=["127.0.0.1"]) by
        // code, not luck: ThresholdPolicy.hostnameOf strips the port BEFORE
        // this comparison ever sees it, so isVerifierAllowed itself never
        // has to.
        assertFalse(OperatorPolicy.isVerifierAllowed("127.0.0.1:8787", setOf("127.0.0.1")))
    }

    @Test
    fun `leading and trailing whitespace on the hostname is trimmed before matching`() {
        // G6: this side is untrimmed operator input (or a hostname a
        // caller failed to trim) — allowedHosts is already trimmed at
        // OperatorPolicy.parseHostnameList time, so this asserts the OTHER
        // side, the [hostname] argument.
        assertTrue(OperatorPolicy.isVerifierAllowed("  127.0.0.1  ", setOf("127.0.0.1")))
        assertTrue(OperatorPolicy.isVerifierAllowed("127.0.0.1", setOf("  127.0.0.1  ")))
    }

    @Test
    fun `mixed-case hostname matches regardless of which side is uppercase`() {
        // G6: an IDN/mixed-case variant, beyond the single-word
        // "hostname matching is case-insensitive" case above.
        assertTrue(OperatorPolicy.isVerifierAllowed("StAtE.GoV", setOf("state.gov")))
        assertTrue(OperatorPolicy.isVerifierAllowed("state.gov", setOf("StAtE.GoV")))
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

    // -------------------------------------------------- gateMessageFor (G1)

    @Test
    fun `gateMessageFor - null hostname is refused`() {
        assertEquals(
            "This site's origin could not be resolved to a hostname — refused.",
            OperatorPolicy.gateMessageFor(null),
        )
    }

    @Test
    fun `gateMessageFor - listed hostname admits (null message)`() {
        // gateMessageFor's one-arg form reads THIS build's committed
        // reference operator.json (verifiers=["127.0.0.1"], D84 point 2).
        assertEquals(null, OperatorPolicy.gateMessageFor("127.0.0.1"))
    }

    @Test
    fun `gateMessageFor - unlisted hostname is refused, naming the hostname`() {
        assertEquals(
            "This site (evil.example) is not on this app's approved verifier list — refused.",
            OperatorPolicy.gateMessageFor("evil.example"),
        )
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
