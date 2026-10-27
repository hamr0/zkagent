package com.tananaev.passportreader

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Item 18 / finding #48, proof 1 (truth table) and proof 2 (source wiring
 * trace). Proof 3 -- a real process replaying the intent -- is the device run.
 * Expected values are literals; the links are made-up, no real request_uri.
 */
class LinkOnceTest {

    private val linkA = "av://authorize?client_id=site.example&request_uri=http://127.0.0.1:8787/request/aaa"
    private val linkB = "av://authorize?client_id=site.example&request_uri=http://127.0.0.1:8787/request/bbb"
    private val truncated = "av://authorize?client_id=site.example"

    private fun reload(l: LinkOnce) = LinkOnce.deserialize(l.serialize())

    /** The decision table: (case, matches expectation). [guard] false = guard removed (always HANDLE), the negative control. */
    private fun table(guard: Boolean): List<Pair<String, Boolean>> {
        fun v(l: LinkOnce, link: String?, s: LinkOnce.Source, held: Boolean = false) =
            if (!guard) LinkOnce.Verdict.HANDLE else l.decide(link, s, held)
        val H = LinkOnce.Verdict.HANDLE
        val R = LinkOnce.Verdict.RESTORE
        val I = LinkOnce.Verdict.IGNORE
        val out = mutableListOf<Pair<String, Boolean>>()
        var l = LinkOnce()
        out += "first sight handled" to (v(l, linkA, LinkOnce.Source.CREATE) == H)
        out += "same key again, in flight, same process, holder has it: restored" to (v(l, linkA, LinkOnce.Source.CREATE, true) == R)
        out += "different link handled" to (v(l, linkB, LinkOnce.Source.CREATE) == H)
        l = LinkOnce()
        v(l, linkA, LinkOnce.Source.CREATE)
        out += "in flight, seen, holder EMPTY: handled again, never idle" to (v(l, linkA, LinkOnce.Source.CREATE, false) == H)
        l = LinkOnce()
        out += "absent key handled once" to (v(l, null, LinkOnce.Source.CREATE) == H)
        out += "absent key second time restored/ignored, not handled" to (v(l, null, LinkOnce.Source.CREATE, true) == R)
        l = LinkOnce()
        v(l, linkA, LinkOnce.Source.CREATE); l.markTerminal(linkA)
        l = reload(l)
        out += "terminal key ignored after reload" to (v(l, linkA, LinkOnce.Source.CREATE) == I)
        out += "terminal key ignored even if holder has it" to (v(l, linkA, LinkOnce.Source.CREATE, true) == I)
        l = LinkOnce()
        v(l, linkA, LinkOnce.Source.CREATE)
        l = reload(l)
        out += "in-flight key resumes after reload (process death)" to (v(l, linkA, LinkOnce.Source.CREATE, false) == H)
        out += "...then a recreation in that process restores" to (v(l, linkA, LinkOnce.Source.CREATE, true) == R)
        l = LinkOnce()
        v(l, truncated, LinkOnce.Source.CREATE); l.markTerminal(truncated)
        out += "malformed (no request_uri) keyed on whole link, replay ignored" to (v(l, truncated, LinkOnce.Source.CREATE) == I)
        l = LinkOnce()
        v(l, linkA, LinkOnce.Source.CREATE); l.markTerminal(linkA)
        out += "fresh tap (NEW_INTENT) of a terminal link handled" to (v(l, linkA, LinkOnce.Source.NEW_INTENT) == H)
        out += "...and re-armed as in flight" to (l.statusOf(linkA) == LinkOnce.Status.IN_FLIGHT)
        return out
    }

    @Test
    fun `truth table with the guard in place passes every case`() {
        val rows = table(guard = true)
        assertEquals(13, rows.size)
        val failed = rows.filter { !it.second }.map { it.first }
        assertTrue("failed: $failed", failed.isEmpty())
    }

    @Test
    fun `negative control - guard removed turns the table red and excludes cases`() {
        val failed = table(guard = false).filter { !it.second }.map { it.first }
        assertTrue("the control must exclude something", failed.isNotEmpty())
        assertEquals(
            listOf(
                "same key again, in flight, same process, holder has it: restored",
                "absent key second time restored/ignored, not handled",
                "terminal key ignored after reload",
                "terminal key ignored even if holder has it",
                "...then a recreation in that process restores",
                "malformed (no request_uri) keyed on whole link, replay ignored",
                "...and re-armed as in flight",
            ),
            failed,
        )
    }

    @Test
    fun `holder keeps handoff data across a recreation and is emptied at the terminal end`() {
        val h = InFlightHandoff<String, String>()
        assertTrue(!h.has("k"))
        h.begin("k", "pending1")
        assertEquals(InFlightHandoff.Snapshot("pending1", null), h.snapshot("k"))
        h.setVerified("other", "nope")
        assertNull(h.snapshot("k")!!.verified)
        h.setVerified("k", "req1")
        assertEquals(InFlightHandoff.Snapshot("pending1", "req1"), h.snapshot("k"))
        assertNull(h.snapshot("different"))
        h.markTakenOver("k")
        assertTrue(h.isTakenOver("k"))
        assertTrue(!h.isTakenOver(null))
        h.clear("different")
        assertTrue(h.has("k"))
        h.clear("k")
        assertTrue(!h.has("k"))
        assertTrue(!h.isTakenOver("k"))
        h.begin("k", "p2")
        assertTrue("a new begin resets takeover", !h.isTakenOver("k"))
    }

    @Test
    fun `cap evicts the oldest and the evicted link is handled again`() {
        var l = LinkOnce(maxEntries = 3)
        for (i in 1..4) { l.decide("av://authorize?request_uri=r$i", LinkOnce.Source.CREATE); l.markTerminal("av://authorize?request_uri=r$i") }
        assertEquals(3, l.size())
        assertNull(l.statusOf("av://authorize?request_uri=r1"))
        assertEquals(LinkOnce.Status.TERMINAL, l.statusOf("av://authorize?request_uri=r4"))
        l = reload(l)
        assertEquals(LinkOnce.Verdict.HANDLE, l.decide("av://authorize?request_uri=r1", LinkOnce.Source.CREATE))
        assertEquals(LinkOnce.Verdict.IGNORE, l.decide("av://authorize?request_uri=r3", LinkOnce.Source.CREATE, true))
    }

    @Test
    fun `persisted form holds only 64-hex hashes and a status letter - no link text`() {
        val l = LinkOnce()
        l.decide(linkA, LinkOnce.Source.CREATE); l.markTerminal(linkA)
        l.decide(linkB, LinkOnce.Source.CREATE)
        val lines = l.serialize().split('\n')
        assertEquals(2, lines.size)
        for (line in lines) assertTrue(line, Regex("[0-9a-f]{64},[IT]").matches(line))
        assertTrue(!l.serialize().contains("127.0.0.1") && !l.serialize().contains("site.example"))
        // independent known SHA-256 of "abc"
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", LinkOnce.keyOf("abc"))
    }

    @Test
    fun `corrupt persisted lines are skipped, never throw`() {
        val l = LinkOnce.deserialize("garbage\n123,T\n" + "a".repeat(64) + ",X\n" + "b".repeat(64) + ",T")
        assertEquals(1, l.size())
    }

    // Proof 2: source wiring trace (the activity itself cannot run under the JVM stubs).
    @Test
    fun `wiring - guard precedes parse, onCreate replays as CREATE, onNewIntent as NEW_INTENT, no raw handoff nulling`() {
        val src = File("src/main/java/com/tananaev/passportreader/MainActivity.kt").readText()
        assertTrue(src.contains("handleIncomingIntent(intent, LinkOnce.Source.CREATE)"))
        assertTrue(src.contains("handleIncomingIntent(intent, LinkOnce.Source.NEW_INTENT)"))
        val fn = src.substringAfter("private fun handleIncomingIntent(")
        val decideAt = fn.indexOf("ledger.decide(linkKey, source, holderHasEntry")
        val parseAt = fn.indexOf("HandoffClient.parseAvLink(data)")
        assertTrue("decide must exist and precede the parse", decideAt in 0 until parseAt)
        assertTrue(src.contains("inFlightHandoff.setVerified(pendingLinkKey, outcome.request)"))
        assertTrue(src.contains("restoreInFlightHandoff(linkKey)"))
        assertTrue(src.contains("inFlightHandoff.clear(linkKey)"))
        val rawNulls = Regex("(?m)^\\s*pendingHandoff = null$").findAll(src).count()
        assertEquals("only releasePendingHandoff may null it directly", 1, rawNulls)
        assertTrue(src.contains("beginHandoffVerification(handoff, linkKey)"))
    }
}
