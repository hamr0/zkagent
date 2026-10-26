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
        assertEquals(null, OperatorPolicy.gateMessageFor("127.0.0.1", open = false, allowedHosts = setOf("127.0.0.1")))
    }

    @Test
    fun `gateMessageFor - unlisted hostname is refused, naming the hostname`() {
        assertEquals(
            "This site (evil.example) is not on this app's approved verifier list — refused.",
            OperatorPolicy.gateMessageFor("evil.example", open = false, allowedHosts = setOf("127.0.0.1")),
        )
    }

    // ---------------------------------------- item 23: verifiers "any" (D109(i)1)
    // Truth table on the pure age-gate decision, every expectation spelled out here (never read from the build's
    // own knobs). `open` is the "any" form; the set is the array form's hostname list.

    private val listed = setOf("state.gov", "127.0.0.1")
    private val probes = listOf("state.gov", "127.0.0.1", "localhost", "never-seen.example", "ANY")

    @Test
    fun `any - admits listed, unlisted and never-seen hostnames alike`() {
        for (host in probes) {
            assertTrue("open form must admit $host", OperatorPolicy.admitsAgeVerifier(host, open = true, allowedHosts = listed))
            assertEquals(null, OperatorPolicy.gateMessageFor(host, open = true, allowedHosts = listed))
        }
        // the open form does not depend on the (empty, in a real build) list at all
        assertTrue(OperatorPolicy.admitsAgeVerifier("localhost", open = true, allowedHosts = emptySet()))
    }

    @Test
    fun `array form - admits only listed hostnames and refuses the rest loudly`() {
        val admitted = probes.filter { OperatorPolicy.admitsAgeVerifier(it, open = false, allowedHosts = listed) }
        val refused = probes.filterNot { OperatorPolicy.admitsAgeVerifier(it, open = false, allowedHosts = listed) }
        assertEquals(listOf("state.gov", "127.0.0.1"), admitted)
        // count above zero: the negative half must actually exclude something (a guard that matches nothing passes vacuously)
        assertEquals(listOf("localhost", "never-seen.example", "ANY"), refused)
        for (host in refused) {
            assertEquals(
                "This site ($host) is not on this app's approved verifier list — refused.",
                OperatorPolicy.gateMessageFor(host, open = false, allowedHosts = listed),
            )
        }
    }

    @Test
    fun `any - an origin with no resolvable hostname is still refused, never implicitly allowed`() {
        assertEquals(
            "This site's origin could not be resolved to a hostname — refused.",
            OperatorPolicy.gateMessageFor(null, open = true, allowedHosts = emptySet()),
        )
    }

    @Test
    fun `any - never widens the tier-C pin list or the multi-threshold list (they take their own set)`() {
        // Tier C (TierCGate / TierCRefusalReply) and the named exceptions call the two-argument isVerifierAllowed with
        // their OWN set, which has no "open" input. A host outside that set stays refused whatever the age gate does.
        val pinned = setOf("127.0.0.1")
        assertTrue(OperatorPolicy.admitsAgeVerifier("localhost", open = true, allowedHosts = emptySet()))
        assertFalse(OperatorPolicy.isVerifierAllowed("localhost", pinned))
        assertTrue(OperatorPolicy.isVerifierAllowed("127.0.0.1", pinned))
        assertFalse(OperatorPolicy.isVerifierAllowed("localhost", emptySet())) // empty pin list pins nothing, even under "any"
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
    // declares verifiers="any" (item 23), tiers="A+B", thresholds=the six
    // D74 values, multi_threshold_verifiers=[]. Expected values spelled out
    // independently here, not read back from OperatorPolicy's own fields,
    // so this test can actually fail if Gradle stopped wiring the file
    // through to BuildConfig.

    @Test
    fun `this build's OPERATOR_VERIFIERS wiring is the open form with an empty hostname list`() {
        // Reference operator.json: "verifiers": "any" (item 23). The open form is its own boolean; the hostname
        // list stays empty, so "any" can never be read back as a hostname-list entry.
        assertTrue(OperatorPolicy.VERIFIERS_ANY)
        assertTrue(OperatorPolicy.VERIFIERS.isEmpty())
        assertTrue(OperatorPolicy.isVerifierAllowed("127.0.0.1"))
        assertTrue(OperatorPolicy.isVerifierAllowed("evil.example"))
        assertEquals(null, OperatorPolicy.gateMessageFor("localhost"))
    }

    @Test
    fun `this build's OPERATOR_TIERS wiring is A+B and OPERATOR_THRESHOLDS is the D74 list`() {
        assertEquals("A+B", OperatorPolicy.TIERS)
        assertEquals(setOf(15, 16, 18, 21, 60, 65), OperatorPolicy.THRESHOLDS)
        assertTrue(OperatorPolicy.MULTI_THRESHOLD_VERIFIERS.isEmpty())
    }
}
