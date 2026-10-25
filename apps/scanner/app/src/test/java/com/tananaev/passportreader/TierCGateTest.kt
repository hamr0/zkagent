package com.tananaev.passportreader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * M3b opening POC, §6.9.1 criteria 3 and 4 — the pure gate. Every expectation is written out by hand from
 * item 7's rule ("answered only if the operator switch is on, the origin is pinned, AND the holder setting is on"),
 * never derived from [OperatorPolicy]'s `BuildConfig`-sourced values.
 *
 * Each refusal row is paired with the allow row it differs from by exactly one input, and the whole table asserts a
 * refusal count above zero (a gate that refused nothing, or allowed nothing, would fail here).
 */
class TierCGateTest {

    private val pinned = setOf("127.0.0.1")

    @Test
    fun `the full eight-row table of switch x pinned x holder setting`() {
        data class Row(val switchOn: Boolean, val host: String, val holderOn: Boolean, val expected: TierCGate.Decision)
        val rows = listOf(
            Row(true, "127.0.0.1", true, TierCGate.Decision.Allow),
            Row(true, "127.0.0.1", false, TierCGate.Decision.Refuse(TierCGate.Cause.HOLDER_SETTING_OFF)),
            Row(true, "localhost", true, TierCGate.Decision.Refuse(TierCGate.Cause.NOT_PINNED)),
            Row(true, "localhost", false, TierCGate.Decision.Refuse(TierCGate.Cause.NOT_PINNED)),
            Row(false, "127.0.0.1", true, TierCGate.Decision.Refuse(TierCGate.Cause.OPERATOR_SWITCH_OFF)),
            Row(false, "127.0.0.1", false, TierCGate.Decision.Refuse(TierCGate.Cause.OPERATOR_SWITCH_OFF)),
            Row(false, "localhost", true, TierCGate.Decision.Refuse(TierCGate.Cause.OPERATOR_SWITCH_OFF)),
            Row(false, "localhost", false, TierCGate.Decision.Refuse(TierCGate.Cause.OPERATOR_SWITCH_OFF)),
        )
        var allowed = 0
        var refused = 0
        for (r in rows) {
            val got = TierCGate.decide(r.switchOn, r.host, pinned, r.holderOn)
            assertEquals("switch=${r.switchOn} host=${r.host} holder=${r.holderOn}", r.expected, got)
            if (got == TierCGate.Decision.Allow) allowed++ else refused++
        }
        assertEquals(1, allowed)
        assertEquals(7, refused)
        assertTrue(refused > 0 && allowed > 0) // non-vacuity: the table both admits and excludes
    }

    @Test
    fun `criterion 3 pair - the pinned hostname is allowed and the second hostname served by the same spike is refused for tier C`() {
        // Same switch, same holder setting, same pin list: the ONLY difference is the hostname.
        assertEquals(TierCGate.Decision.Allow, TierCGate.decide(true, "127.0.0.1", pinned, true))
        assertEquals(TierCGate.Decision.Refuse(TierCGate.Cause.NOT_PINNED), TierCGate.decide(true, "localhost", pinned, true))
    }

    @Test
    fun `criterion 3 control - the operator switch is a separate gate from the pin`() {
        // 127.0.0.1 IS pinned and the holder setting IS on, and it is still refused when only the switch differs.
        assertEquals(TierCGate.Decision.Allow, TierCGate.decide(true, "127.0.0.1", pinned, true))
        assertEquals(TierCGate.Decision.Refuse(TierCGate.Cause.OPERATOR_SWITCH_OFF), TierCGate.decide(false, "127.0.0.1", pinned, true))
    }

    @Test
    fun `criterion 4 pair - the holder setting is the only difference`() {
        assertEquals(TierCGate.Decision.Refuse(TierCGate.Cause.HOLDER_SETTING_OFF), TierCGate.decide(true, "127.0.0.1", pinned, false))
        assertEquals(TierCGate.Decision.Allow, TierCGate.decide(true, "127.0.0.1", pinned, true))
    }

    @Test
    fun `pin matching is by exact hostname only`() {
        // a port is not part of a hostname, a prefix/suffix/wildcard is not a match
        assertEquals(TierCGate.Decision.Refuse(TierCGate.Cause.NOT_PINNED), TierCGate.decide(true, "127.0.0.1:8787", pinned, true))
        assertEquals(TierCGate.Decision.Refuse(TierCGate.Cause.NOT_PINNED), TierCGate.decide(true, "127.0.0.10", pinned, true))
        assertEquals(TierCGate.Decision.Refuse(TierCGate.Cause.NOT_PINNED), TierCGate.decide(true, "sub.example.com", setOf("example.com"), true))
        assertEquals(TierCGate.Decision.Refuse(TierCGate.Cause.NOT_PINNED), TierCGate.decide(true, "example.com", setOf("sub.example.com"), true))
        // case-insensitive, like the verifier allowlist
        assertEquals(TierCGate.Decision.Allow, TierCGate.decide(true, "EXAMPLE.com", setOf("example.COM"), true))
    }

    @Test
    fun `an unresolvable hostname or an empty pin list is a refusal, never an allow`() {
        assertEquals(TierCGate.Decision.Refuse(TierCGate.Cause.NOT_PINNED), TierCGate.decide(true, null, pinned, true))
        assertEquals(TierCGate.Decision.Refuse(TierCGate.Cause.NOT_PINNED), TierCGate.decide(true, "127.0.0.1", emptySet(), true))
    }

    @Test
    fun `the causes report in a fixed order when several gates fail`() {
        // switch off outranks not-pinned outranks holder-off
        assertEquals(TierCGate.Cause.OPERATOR_SWITCH_OFF, (TierCGate.decide(false, "evil.example", pinned, false) as TierCGate.Decision.Refuse).cause)
        assertEquals(TierCGate.Cause.NOT_PINNED, (TierCGate.decide(true, "evil.example", pinned, false) as TierCGate.Decision.Refuse).cause)
    }

    // ---- document integrity: "no boolean at all" is distinct from a real false ----

    @Test
    fun `only ok and allowed true makes a document answerable`() {
        assertTrue(TierCGate.documentAnswerable(ok = true, allowed = true))
        assertFalse(TierCGate.documentAnswerable(ok = true, allowed = false))  // tampered DG1 / untrusted signer: a real "no"
        assertFalse(TierCGate.documentAnswerable(ok = false, allowed = null))  // could not check
        assertFalse(TierCGate.documentAnswerable(ok = true, allowed = null))
        assertFalse(TierCGate.documentAnswerable(ok = false, allowed = true))  // never trust allowed when ok is false
    }

    // ---- the holder setting ----

    private class FakeStore(var value: Boolean? = null, val throwOnRead: Boolean = false) : HolderSettings.BooleanStore {
        var writes = 0
        override fun read(key: String): Boolean? {
            if (throwOnRead) throw IllegalStateException("store unreadable")
            assertEquals(HolderSettings.KEY, key)
            return value
        }
        override fun write(key: String, value: Boolean) { assertEquals(HolderSettings.KEY, key); this.value = value; writes++ }
    }

    @Test
    fun `the holder setting is OFF by default`() {
        assertFalse(HolderSettings(FakeStore()).allowAdditionalVerifications())
    }

    @Test
    fun `the holder setting round-trips and a broken store fails closed`() {
        val store = FakeStore()
        val settings = HolderSettings(store)
        settings.setAllowAdditionalVerifications(true)
        assertTrue(settings.allowAdditionalVerifications())
        settings.setAllowAdditionalVerifications(false)
        assertFalse(settings.allowAdditionalVerifications())
        assertEquals(2, store.writes)
        // a store that throws must read as OFF, never as ON
        assertFalse(HolderSettings(FakeStore(value = true, throwOnRead = true)).allowAdditionalVerifications())
        assertEquals("allow_additional_verifications", HolderSettings.KEY)
    }

    // ---- wiring: this build's committed operator.json is tier-C off ----

    @Test
    fun `the reference build ships with tier C off and an empty pin list`() {
        // Reads the BuildConfig fields Gradle generated from the committed apps/scanner/operator.json. The tier-C POC builds
        // use -PoperatorJson and are different APKs; this suite always runs against the committed reference file.
        assertFalse(OperatorPolicy.TIER_C_ENABLED)
        assertTrue(OperatorPolicy.TIER_C_VERIFIERS.isEmpty())
        assertEquals(TierCGate.Decision.Refuse(TierCGate.Cause.OPERATOR_SWITCH_OFF), TierCGate.decide(OperatorPolicy.TIER_C_ENABLED, "127.0.0.1", OperatorPolicy.TIER_C_VERIFIERS, true))
    }
}
