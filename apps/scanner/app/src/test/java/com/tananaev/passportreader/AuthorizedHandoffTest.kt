package com.tananaev.passportreader

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Modifier
import java.net.URI

/**
 * D58 step 3 (findings #2/#3, the ownership refactor's headline target) —
 * pins [AuthorizedHandoff], the lock-time snapshot that replaces every
 * downstream (post-lock) read of `MainActivity.pendingHandoff`/
 * `verifiedRequest`. See that class's own doc for the full construction/
 * threading story; this suite covers the three properties the D58 step 3
 * brief calls out explicitly: pure construction, immutability, and the
 * SUPERSESSION property (a later verification of a DIFFERENT handoff
 * cannot change an already-taken snapshot) — the regression test for
 * findings #2/#3.
 */
class AuthorizedHandoffTest {

    private fun verifiedRequestFor(origin: String, nonce: String = "n"): RequestTrust.VerifiedRequest {
        val json = JSONObject().apply {
            put("response_uri", "$origin/callback")
            put("zkagent", JSONObject().apply {
                put("challenge", JSONObject().apply { put("nonce", nonce) })
            })
        }
        return RequestTrust.VerifiedRequest(json, origin)
    }

    @Test
    fun `constructs from a verified request, carrying its origin and a site title`() {
        val verified = verifiedRequestFor("https://example.com:8443")
        val snapshot = AuthorizedHandoff(verified, verified.origin, "example.com:8443")

        assertEquals(verified, snapshot.request)
        assertEquals("https://example.com:8443", snapshot.origin)
        assertEquals("example.com:8443", snapshot.site)
    }

    @Test
    fun `the mint path's scopeDomain is derivable from the snapshot's own origin, never a fresh source`() {
        val verified = verifiedRequestFor("https://example.com:8443")
        val snapshot = AuthorizedHandoff(verified, verified.origin, "example.com:8443")

        // Same derivation MainActivity's mint-path Thread performs —
        // URI(origin).host — sourced from THIS snapshot's origin, not a
        // re-read of any mutable field.
        val scopeDomain = URI(snapshot.origin).host
        assertEquals("example.com", scopeDomain)
    }

    @Test
    fun `the mint path's nonce and response_uri come from the snapshot's own verified json`() {
        val verified = verifiedRequestFor("https://example.com:8443", nonce = "abc123")
        val snapshot = AuthorizedHandoff(verified, verified.origin, "example.com:8443")

        val zkagent = snapshot.request.json.optJSONObject("zkagent")!!
        val nonce = zkagent.optJSONObject("challenge")!!.optString("nonce")
        val responseUri = snapshot.request.json.optString("response_uri")

        assertEquals("abc123", nonce)
        assertEquals("https://example.com:8443/callback", responseUri)
    }

    @Test
    fun `has no mutable fields — a supersession could only ever construct a NEW instance, never mutate this one`() {
        val fields = AuthorizedHandoff::class.java.declaredFields.filter { !it.isSynthetic }
        assertTrue("expected at least the three declared properties", fields.size >= 3)
        for (f in fields) {
            assertTrue("field ${f.name} must be immutable (final) — a `var` here would let a later " +
                "write reach back and change an already-captured snapshot", Modifier.isFinal(f.modifiers))
        }
    }

    @Test
    fun `equal field values produce equal snapshots — pure value semantics, no hidden identity state`() {
        val verified = verifiedRequestFor("https://example.com:8443")
        val a = AuthorizedHandoff(verified, verified.origin, "example.com:8443")
        val b = AuthorizedHandoff(verified, verified.origin, "example.com:8443")
        assertEquals(a, b)
    }

    // ---------------------------------------------------------- supersession
    /** THE regression test for findings #2/#3: in the exploit trace
     * (`docs/logs/M2-RACE-ANALYSIS-2026-09-02.md` §3), a second handoff
     * (an attacker's `av://` intent, or simply the next legitimate site)
     * verifying WHILE a first handoff's snapshot is already captured and
     * in flight through the read/mint pipeline must not be able to change
     * what that first snapshot carries — the whole point of threading it
     * as an immutable parameter rather than re-reading a mutable field.
     * This is necessarily true by construction for a `val`-only data
     * class (asserted structurally above), so this test demonstrates the
     * property behaviourally: constructing a LATER snapshot for a
     * different origin leaves an EARLIER, already-held snapshot reference
     * completely unchanged. */
    @Test
    fun `a later verification of a DIFFERENT handoff does not change an already-taken snapshot`() {
        val legitimate = verifiedRequestFor("https://site-a.example", nonce = "legit-nonce")
        val lockTimeSnapshot = AuthorizedHandoff(legitimate, legitimate.origin, "site-a.example")

        // What the exploit trace's Window 1/Window 2 would do: an
        // unrelated later handoff (e.g. an admitted or racing av://
        // intent) verifies for a completely different origin, AFTER
        // lockTimeSnapshot was already captured and handed off downstream.
        val attacker = verifiedRequestFor("https://attacker.evil", nonce = "attacker-nonce")
        @Suppress("UNUSED_VARIABLE")
        val supersedingSnapshot = AuthorizedHandoff(attacker, attacker.origin, "attacker.evil")

        // The value already captured and threaded into the read/mint
        // pipeline is untouched — same origin, same site, same nonce —
        // regardless of the attacker's later, independent verification.
        assertEquals("https://site-a.example", lockTimeSnapshot.origin)
        assertEquals("site-a.example", lockTimeSnapshot.site)
        assertEquals(
            "legit-nonce",
            lockTimeSnapshot.request.json.optJSONObject("zkagent")!!.optJSONObject("challenge")!!.optString("nonce"),
        )
        assertNotEquals(lockTimeSnapshot.origin, supersedingSnapshot.origin)
    }
}
