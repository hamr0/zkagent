package com.tananaev.passportreader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * §6.2 item 23 (D70(b)) — pure round-trip/corruption/cap tests for
 * [ReportLogStore], independent of any real file I/O (that lives in
 * `MainActivity`, not tested here — see that class's doc).
 */
class ReportLogStoreTest {

    private fun snapshot(
        entries: List<String> = listOf("09:00:00 · site-a.test\n\nResult    ok"),
        expanded: List<Boolean> = listOf(false),
        outcomes: List<ReportLog.Outcome> = listOf(ReportLog.Outcome.PASS),
        lastText: String? = "last report text",
    ) = ReportLogStore.Snapshot(entries, expanded, outcomes, lastText)

    @Test
    fun `round-trips a snapshot through toJson and fromJson byte-identically`() {
        val original = snapshot(
            entries = listOf(
                "09:00:00 · site-a.test\n\nResult    ok",
                "09:00:05 · site-b.test\n\nResult    refused",
            ),
            expanded = listOf(false, true),
            outcomes = listOf(ReportLog.Outcome.PASS, ReportLog.Outcome.FAIL),
        )
        val restored = ReportLogStore.fromJson(ReportLogStore.toJson(original))
        assertEquals(original, restored)
    }

    @Test
    fun `round-trips all three Outcome values`() {
        val original = snapshot(
            entries = listOf("e1", "e2", "e3"),
            expanded = listOf(false, false, false),
            outcomes = listOf(ReportLog.Outcome.PASS, ReportLog.Outcome.FAIL, ReportLog.Outcome.PENDING),
        )
        val restored = ReportLogStore.fromJson(ReportLogStore.toJson(original))
        assertEquals(listOf(ReportLog.Outcome.PASS, ReportLog.Outcome.FAIL, ReportLog.Outcome.PENDING), restored.outcomes)
    }

    @Test
    fun `round-trips a null lastText as null`() {
        val original = snapshot(lastText = null)
        val restored = ReportLogStore.fromJson(ReportLogStore.toJson(original))
        assertEquals(null, restored.lastText)
    }

    @Test
    fun `an empty snapshot round-trips to an empty snapshot`() {
        val original = ReportLogStore.Snapshot.EMPTY
        val restored = ReportLogStore.fromJson(ReportLogStore.toJson(original))
        assertEquals(original, restored)
    }

    @Test
    fun `malformed JSON returns an empty snapshot, never throws`() {
        val restored = ReportLogStore.fromJson("{ not valid json ][")
        assertEquals(ReportLogStore.Snapshot.EMPTY, restored)
    }

    @Test
    fun `an entries array with a missing required field returns an empty snapshot, never a partial one`() {
        // "text" is required; this entry omits it.
        val json = """{"version":1,"entries":[{"expanded":false,"outcome":"PASS"}],"last_text":null}"""
        val restored = ReportLogStore.fromJson(json)
        assertEquals(ReportLogStore.Snapshot.EMPTY, restored)
    }

    @Test
    fun `an unrecognised outcome name falls back to FAIL, never throws or guesses PASS`() {
        val json = """{"version":1,"entries":[{"text":"e1","expanded":false,"outcome":"SOMETHING_NEW"}],"last_text":null}"""
        val restored = ReportLogStore.fromJson(json)
        assertEquals(listOf(ReportLog.Outcome.FAIL), restored.outcomes)
        assertEquals(listOf("e1"), restored.entries)
    }

    @Test
    fun `completely empty JSON object returns an empty snapshot`() {
        val restored = ReportLogStore.fromJson("{}")
        assertEquals(ReportLogStore.Snapshot.EMPTY, restored)
    }

    @Test
    fun `fromJson enforces the D59 cap on load, keeping the newest entries`() {
        val entries = (0 until ReportLog.MAX_ENTRIES + 5).map { "entry $it" }
        val outcomes = entries.map { ReportLog.Outcome.PASS }
        val original = snapshot(entries = entries, expanded = entries.map { false }, outcomes = outcomes)
        val restored = ReportLogStore.fromJson(ReportLogStore.toJson(original))
        assertEquals("cap is enforced on load", ReportLog.MAX_ENTRIES, restored.entries.size)
        assertTrue("the oldest entries were dropped", restored.entries.none { it == "entry 0" })
        assertTrue("the newest entry survives", restored.entries.last() == "entry ${ReportLog.MAX_ENTRIES + 4}")
    }

    @Test
    fun `fromJson cap eviction keeps the parallel lists in lockstep`() {
        val entries = (0 until ReportLog.MAX_ENTRIES + 3).map { "entry $it" }
        val expanded = entries.indices.map { it % 2 == 0 }
        val outcomes = entries.indices.map { if (it % 3 == 0) ReportLog.Outcome.PASS else ReportLog.Outcome.FAIL }
        val original = snapshot(entries = entries, expanded = expanded, outcomes = outcomes)
        val restored = ReportLogStore.fromJson(ReportLogStore.toJson(original))
        assertEquals(ReportLog.MAX_ENTRIES, restored.entries.size)
        assertEquals(ReportLog.MAX_ENTRIES, restored.expanded.size)
        assertEquals(ReportLog.MAX_ENTRIES, restored.outcomes.size)
        // the last surviving entry's own expanded/outcome must match what
        // the SAME original index had, not an off-by-one from the eviction.
        val lastOriginalIndex = entries.lastIndex
        assertEquals(expanded[lastOriginalIndex], restored.expanded.last())
        assertEquals(outcomes[lastOriginalIndex], restored.outcomes.last())
    }
}
