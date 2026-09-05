package com.tananaev.passportreader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * §6.2 item 16 (D44/D45/D46/D48) — pure append/clear/restore/render
 * semantics for [ReportLog], independent of the Android UI plumbing around
 * it. `MainActivity` wiring (single `emitReport` write path, and where the
 * claim/predicate data is actually sourced from — see that file's own doc)
 * is not re-tested here; this suite only pins how [ReportLog] renders
 * whatever [ReportLog.DisclosureSummary] it is given.
 */
class ReportLogTest {

    private val notDisclosedNothing = ReportLog.DisclosureSummary.Shared.NotDisclosed("nothing")

    private fun summary(
        site: String = "127.0.0.1:8787",
        result: String = "Verified — the site accepted you",
        sent: String = "a site-only pseudonym + a signed claim (age > 18: true)",
        shared: ReportLog.DisclosureSummary.Shared = ReportLog.DisclosureSummary.Shared.Disclosed(
            listOf(ReportLog.DisclosureSummary.Claim("age > 18", "true")),
        ),
        identity: String? = "new — minted fresh for this site",
        chipAuthenticity: String? = null,
    ) = ReportLog.DisclosureSummary(site, result, sent, shared, identity, chipAuthenticity = chipAuthenticity)

    @Test
    fun `append renders a title line with timestamp and site, then the summary, then the technical block`() {
        val log = ReportLog()
        // outcome = PASS, explicit — see the item 22 section below for the
        // glyph-prefix assertion; this test only pins the structure after it.
        log.append("mode: B\nmint: OK\nverdict: PASS (minted)", summary(), outcome = ReportLog.Outcome.PASS, nowMillis = 0L) // epoch — TZ-dependent, only structure asserted
        val entries = log.entriesSnapshot()
        assertEquals(1, entries.size)
        val entry = entries[0]
        assertTrue("title line has a glyph prefix, a timestamp, and the site", entry.lines()[0].matches(Regex("""^✓ \d{2}:\d{2}:\d{2} · 127\.0\.0\.1:8787$""")))
        assertTrue(entry.contains("Result    Verified — the site accepted you"))
        assertTrue(entry.contains("Sent      a site-only pseudonym + a signed claim (age > 18: true)"))
        assertTrue(entry.contains("Shared    age > 18: true"))
        assertTrue(entry.contains("Identity  new — minted fresh for this site"))
        assertTrue("technical block is present and subordinated", entry.contains("▸ technical:"))
        // §6.2 item 24 (D70(c)) — the build stamp lives on the technical
        // block's own header line. Matched against a literal regex, not
        // against VersionStamp.format() itself (which would only ever
        // agree with the code under test) — sha shape is either 7 hex
        // chars, optionally "-dirty", or the "nogit" fallback.
        assertTrue(
            "technical header carries the build stamp",
            entry.lines().first { it.startsWith("▸ technical:") }
                .matches(Regex("""^▸ technical: · build v\d+\.\d+\.\d+ \(([0-9a-f]{7}(-dirty)?|nogit)\)$""")),
        )
        assertTrue("technical block carries the exact report text, not a curated subset", entry.contains("  mode: B"))
        assertTrue(entry.contains("  mint: OK"))
        assertTrue(entry.contains("  verdict: PASS (minted)"))
    }

    @Test
    fun `identity line is omitted when the summary carries none`() {
        val log = ReportLog()
        log.append("verdict: FAIL", summary(identity = null), nowMillis = 0L)
        assertFalse(log.entriesSnapshot()[0].contains("Identity"))
    }

    @Test
    fun `a mode-A no-site entry uses the fixed label and reports nothing sent`() {
        val log = ReportLog()
        log.append(
            "mode: A\nverdict: PASS (read)",
            summary(
                site = "Local scan (no site)",
                result = "Read OK — nothing sent",
                sent = "nothing left this device",
                shared = notDisclosedNothing,
                identity = null,
            ),
            nowMillis = 0L,
        )
        val entry = log.entriesSnapshot()[0]
        assertTrue(entry.lines()[0].endsWith("· Local scan (no site)"))
        assertTrue(entry.contains("Result    Read OK — nothing sent"))
        assertTrue(entry.contains("Sent      nothing left this device"))
        assertTrue(entry.contains("Shared    nothing"))
        assertFalse(entry.contains("Identity"))
    }

    @Test
    fun `a failure entry never claims success`() {
        val log = ReportLog()
        log.append(
            "verdict: FAIL\nfailure: IOException: timeout",
            summary(
                site = "Local scan (no site)",
                // §6.2 item 15 (2026-09, shortened per owner: "message
                // should be shorter" — five skimmed-past device runs).
                result = "Couldn't read — check your details",
                sent = "nothing left this device",
                shared = notDisclosedNothing,
                identity = null,
            ),
            nowMillis = 0L,
        )
        val entry = log.entriesSnapshot()[0]
        assertTrue(entry.contains("Result    Couldn't read — check your details"))
        assertFalse("a failed entry must never say Verified/PASS in its plain-language Result line", entry.lines().first { it.startsWith("Result") }.contains("Verified"))
    }

    // §6.2 item 16 (D45): successive scans accumulate — this is the specific
    // behaviour D45 restored after the literal D44 clear-on-wipe rule was
    // found to wipe the log on its own success path (see ReportLog's class
    // doc). append() itself never clears; MainActivity is what must not
    // call clear() from wipeSession any more (verified there, not here).
    @Test
    fun `successive scans accumulate, oldest first, without clearing between them`() {
        val log = ReportLog()
        log.append("scan 1 report", summary(site = "site-a.test", result = "Read OK — nothing sent", shared = notDisclosedNothing), nowMillis = 0L)
        log.append("scan 2 report", summary(site = "site-b.test", result = "Verified — the site accepted you"), nowMillis = 1000L)
        log.append("scan 3 report", summary(site = "Local scan (no site)", result = "Read failed — the document could not be read", shared = notDisclosedNothing), nowMillis = 2000L)
        val entries = log.entriesSnapshot()
        assertEquals(3, entries.size)
        assertTrue(entries[0].contains("site-a.test") && entries[0].contains("scan 1 report"))
        assertTrue(entries[1].contains("site-b.test") && entries[1].contains("scan 2 report"))
        assertTrue(entries[2].contains("Local scan (no site)") && entries[2].contains("scan 3 report"))
    }

    // §6.2 item 16 (D48): Shared is a QUESTION -> ANSWER record of the
    // actual signed claim — predicate label "age > <threshold>", answer
    // ALWAYS the literal boolean (owner: "true/false always", never
    // "yes"/"no") — sourced by MainActivity from the signed claim map; this
    // test pins the RENDERING of that already-sourced Claim, matching
    // D48's worked value (threshold=18, over_threshold=true -> "age > 18: true").
    @Test
    fun `a successful mint entry renders the age-gt-N claim as predicate colon boolean, plus the D24 claim-proof technical note`() {
        val log = ReportLog()
        log.append(
            "mode: B\nmint: OK\nverdict: PASS (minted)",
            ReportLog.DisclosureSummary(
                site = "127.0.0.1:8787",
                result = "Verified — the site accepted you",
                sent = "a site-only pseudonym + a signed claim (age > 18: true)",
                shared = ReportLog.DisclosureSummary.Shared.Disclosed(listOf(ReportLog.DisclosureSummary.Claim("age > 18", "true"))),
                identity = "new — minted fresh for this site",
                technicalNote = "claim_proof: self-asserted by the device — not independently proven (D24)",
            ),
            nowMillis = 0L,
        )
        val entry = log.entriesSnapshot()[0]
        assertTrue("Shared states the actual predicate and its actual boolean answer", entry.contains("Shared    age > 18: true"))
        assertTrue("the existing negation line is preserved, after the claim", entry.contains("Not your name, date of birth, document number, or nationality."))
        assertTrue("the negation line comes strictly after the claim line", entry.indexOf("age > 18: true") < entry.indexOf("Not your name"))
        assertTrue("the D24 claim-proof note is present, subordinate, under the technical block", entry.contains("▸ technical:"))
        assertTrue(entry.substringAfter("▸ technical:").contains("claim_proof: self-asserted by the device — not independently proven (D24)"))
    }

    // A different threshold/answer must render as-is, never coerced toward
    // "18"/"true" — proves the rendering is a faithful pass-through of
    // whatever DisclosureSummary carries, not a hardcoded template. Also
    // proves the boolean answer is never translated to "yes"/"no".
    @Test
    fun `Shared renders whatever predicate and boolean answer it is given, not a fixed age-18-true template`() {
        val log = ReportLog()
        log.append(
            "mode: B\nmint: OK",
            ReportLog.DisclosureSummary(
                site = "site-c.test",
                result = "Verified — the site accepted you",
                sent = "a site-only pseudonym + a signed claim (age > 21: false)",
                shared = ReportLog.DisclosureSummary.Shared.Disclosed(listOf(ReportLog.DisclosureSummary.Claim("age > 21", "false"))),
                identity = "new — minted fresh for this site",
            ),
            nowMillis = 0L,
        )
        val entry = log.entriesSnapshot()[0]
        assertTrue(entry.contains("Shared    age > 21: false"))
        assertFalse(entry.contains("age > 18"))
        assertFalse("boolean answer is never translated to yes/no", entry.contains(": yes") || entry.contains(": no"))
    }

    // §6.2 item 16 (D48/Q34): the reshape must generalize to more than one
    // claim without any production code constructing one today — this list
    // is built DIRECTLY IN THE TEST, proving the renderer (not production
    // logic) handles multiple predicates, one per line, in order, with the
    // negation line still coming last exactly once.
    @Test
    fun `a multi-claim list renders one predicate-answer pair per line, negation line last`() {
        val log = ReportLog()
        log.append(
            "mode: C\nmint: OK",
            ReportLog.DisclosureSummary(
                site = "site-multi.test",
                result = "Verified — the site accepted you",
                sent = "a site-only pseudonym + a signed claim",
                shared = ReportLog.DisclosureSummary.Shared.Disclosed(
                    listOf(
                        ReportLog.DisclosureSummary.Claim("age > 18", "true"),
                        ReportLog.DisclosureSummary.Claim("expiry > 3 months", "false"),
                    ),
                ),
                identity = "new — minted fresh for this site",
            ),
            nowMillis = 0L,
        )
        val entry = log.entriesSnapshot()[0]
        val sharedLine = entry.lines().first { it.startsWith("Shared") }
        val continuationLine = entry.lines()[entry.lines().indexOf(sharedLine) + 1]
        val negationLine = entry.lines()[entry.lines().indexOf(sharedLine) + 2]
        assertEquals("Shared    age > 18: true", sharedLine)
        assertEquals("          expiry > 3 months: false", continuationLine)
        assertTrue("negation line comes after every claim, exactly once", negationLine.trim() == "Not your name, date of birth, document number, or nationality.")
        assertEquals(1, Regex("Not your name").findAll(entry).count())
    }

    // §6.2 item 16 (D48): an empty disclosure — NotDisclosed — MUST NOT
    // render an empty "Shared" label or a stray colon; it renders the
    // supplied plain-language reason and nothing else (no negation line,
    // no claim syntax).
    @Test
    fun `NotDisclosed renders the plain reason with no stray label, colon, or negation line`() {
        val log = ReportLog()
        log.append(
            "mint_gate: NOT MET — evidence: [] (D27)",
            summary(sent = "nothing left this device", shared = ReportLog.DisclosureSummary.Shared.NotDisclosed("nothing"), identity = null),
            nowMillis = 0L,
        )
        val entry = log.entriesSnapshot()[0]
        val sharedLine = entry.lines().first { it.startsWith("Shared") }
        assertEquals("Shared    nothing", sharedLine)
        assertFalse("no stray colon on a non-disclosing entry", sharedLine.contains(":"))
        assertFalse("no negation line when nothing was disclosed", entry.contains("Not your name"))
        assertFalse("no claim syntax leaks onto a non-disclosing entry", entry.contains("age >"))
    }

    // A per-outcome NotDisclosed reason other than the literal word
    // "nothing" (e.g. a delivery rejection) must still render plainly, with
    // no claim and no negation line — proves NotDisclosed is not hardcoded
    // to one fixed string.
    @Test
    fun `NotDisclosed renders a different plain reason verbatim, still with no claim or negation line`() {
        val log = ReportLog()
        log.append(
            "handoff: direct_post http_status=409",
            summary(
                sent = "a signed proof was prepared, but the site did not accept it",
                shared = ReportLog.DisclosureSummary.Shared.NotDisclosed("nothing the site kept — it rejected the response"),
                identity = "new — minted fresh for this site",
            ),
            nowMillis = 0L,
        )
        val entry = log.entriesSnapshot()[0]
        assertTrue(entry.contains("Shared    nothing the site kept — it rejected the response"))
        assertFalse(entry.contains("Not your name"))
        assertFalse(entry.contains("age >"))
    }

    // §6.2 item 16 (D47/D48): the two owner-confirmed Identity strings,
    // verbatim — "only here" is load-bearing in the reused-key string and
    // MUST NOT be simplified out; the spelling is "recognized" (z).
    @Test
    fun `Identity renders the two owner-confirmed strings verbatim`() {
        val log = ReportLog()
        log.append("mint: OK (new key)", summary(identity = "new — minted fresh for this site"), nowMillis = 0L)
        log.append("mint: OK (reused key)", summary(identity = "known — recognized only here from previous visit"), nowMillis = 1000L)
        val entries = log.entriesSnapshot()
        assertTrue(entries[0].contains("Identity  new — minted fresh for this site"))
        assertTrue(entries[1].contains("Identity  known — recognized only here from previous visit"))
        assertTrue("\"only here\" is load-bearing per D48, must not be dropped", entries[1].contains("only here"))
        assertFalse("owner's spelling is \"recognized\" with a z, not \"recognised\"", entries[1].contains("recognised"))
    }

    // D58 step 1 (Report/Log cluster) — ReportLog absorbs `lastReportText`
    // (finding #7's owner is now this class, not MainActivity split across
    // two call sites): [ReportLog.lastText] mirrors exactly what the most
    // recent [append] or [restore] was given, so a single owner can answer
    // "what should reportView show" without MainActivity keeping its own
    // parallel field.
    @Test
    fun `lastText is null before any report is ever appended or restored`() {
        val log = ReportLog()
        assertEquals(null, log.lastText)
    }

    @Test
    fun `append sets lastText to the exact report text, overwriting on each call`() {
        val log = ReportLog()
        log.append("first report", summary(), nowMillis = 0L)
        assertEquals("first report", log.lastText)
        log.append("second report", summary(), nowMillis = 1000L)
        assertEquals("second report", log.lastText)
    }

    @Test
    fun `a pending-replace append still updates lastText to the replacing text`() {
        val log = ReportLog()
        log.append("in progress", summary(), attemptId = "a1", pending = true, nowMillis = 0L)
        assertEquals("in progress", log.lastText)
        log.append("terminal", summary(), attemptId = "a1", nowMillis = 1000L)
        assertEquals("terminal", log.lastText)
    }

    @Test
    fun `restore sets lastText from its argument, defaulting to null when omitted`() {
        val log = ReportLog()
        log.append("stale", summary(), nowMillis = 0L)
        log.restore(listOf("09:00:00 · site-a.test\n\nResult    restored"))
        assertEquals("restore with no lastText argument defaults to null, never re-derives from entries", null, log.lastText)

        val log2 = ReportLog()
        log2.restore(listOf("09:00:00 · site-a.test\n\nResult    restored"), lastText = "restored report text")
        assertEquals("restored report text", log2.lastText)
    }

    // §6.5 S4 device fix (2026-09-05, findings.md — "diagnosis result at
    // Diagnostics tab still shows in Scan tab"). [ReportLog.Channel]/
    // [lastChannel] is the pure state `MainActivity.applyReportText` routes
    // on instead of writing every report to both TextViews unconditionally.

    @Test
    fun `lastChannel defaults to SCAN before any report is ever appended`() {
        val log = ReportLog()
        assertEquals(ReportLog.Channel.SCAN, log.lastChannel)
    }

    @Test
    fun `append with no channel argument keeps lastChannel at SCAN - every pre-fix call site`() {
        val log = ReportLog()
        log.append("a scan report", summary(), nowMillis = 0L)
        assertEquals(ReportLog.Channel.SCAN, log.lastChannel)
    }

    @Test
    fun `append with channel = DIAGNOSTICS sets lastChannel accordingly`() {
        val log = ReportLog()
        log.append("a probe report", summary(), channel = ReportLog.Channel.DIAGNOSTICS, nowMillis = 0L)
        assertEquals(ReportLog.Channel.DIAGNOSTICS, log.lastChannel)
    }

    @Test
    fun `lastChannel tracks the MOST RECENT append, overwriting on each call`() {
        val log = ReportLog()
        log.append("probe", summary(), channel = ReportLog.Channel.DIAGNOSTICS, nowMillis = 0L)
        assertEquals(ReportLog.Channel.DIAGNOSTICS, log.lastChannel)
        log.append("scan", summary(), channel = ReportLog.Channel.SCAN, nowMillis = 1000L)
        assertEquals(ReportLog.Channel.SCAN, log.lastChannel)
    }

    // targetsDiagnosticsView — the actual routing decision
    // MainActivity.applyReportText dispatches on for diagnosticsReportView.
    // Its former SCAN-side counterpart, targetsScanView, is gone along with
    // MainActivity.reportView — see ReportLog's own doc.

    @Test
    fun `targetsDiagnosticsView is true only for DIAGNOSTICS`() {
        assertFalse(ReportLog.targetsDiagnosticsView(ReportLog.Channel.SCAN))
        assertTrue(ReportLog.targetsDiagnosticsView(ReportLog.Channel.DIAGNOSTICS))
    }

    // FIX (owner, 2026-09-05) — ReportLog.lastScanInfo, the "Last scan"
    // line's data. See LastScanLineTest for the rendering truth table;
    // these tests pin only ReportLog's OWN bookkeeping (when it is written,
    // when it is skipped, the generic-default fallback, and clear/restore).

    @Test
    fun `lastScanInfo is null before any report is ever appended or restored`() {
        val log = ReportLog()
        assertEquals(null, log.lastScanInfo)
    }

    @Test
    fun `a SCAN append with no lastScanInfo argument falls back to a generic REFUSED entry`() {
        val log = ReportLog()
        log.append("report", summary(site = "state.gov"), nowMillis = 0L)
        assertEquals(LastScanLine.Entry(origin = "state.gov", reason = LastScanLine.Reason.REFUSED), log.lastScanInfo)
    }

    @Test
    fun `a SCAN append with an explicit lastScanInfo argument uses it verbatim, not the generic default`() {
        val log = ReportLog()
        val explicit = LastScanLine.Entry(origin = "state.gov", reason = LastScanLine.Reason.DELIVERED, threshold = 18, overThreshold = true)
        log.append("report", summary(site = "state.gov"), lastScanInfo = explicit, nowMillis = 0L)
        assertEquals(explicit, log.lastScanInfo)
    }

    @Test
    fun `a DIAGNOSTICS append never updates lastScanInfo, even with an explicit argument`() {
        val log = ReportLog()
        val explicit = LastScanLine.Entry(origin = "state.gov", reason = LastScanLine.Reason.DELIVERED, threshold = 18, overThreshold = true)
        log.append("probe", summary(), channel = ReportLog.Channel.DIAGNOSTICS, lastScanInfo = explicit, nowMillis = 0L)
        assertEquals(null, log.lastScanInfo)
    }

    @Test
    fun `a pending SCAN append never updates lastScanInfo - the in-progress entry must not overwrite the last completed scan`() {
        val log = ReportLog()
        val priorScan = LastScanLine.Entry(origin = "state.gov", reason = LastScanLine.Reason.DELIVERED, threshold = 18, overThreshold = true)
        log.append("terminal", summary(site = "state.gov"), lastScanInfo = priorScan, nowMillis = 0L)
        log.append("in progress", summary(site = "other-site.test"), attemptId = "a1", pending = true, nowMillis = 1000L)
        assertEquals("the pending append must not clobber the prior terminal scan's line", priorScan, log.lastScanInfo)
    }

    @Test
    fun `lastScanInfo tracks the MOST RECENT terminal SCAN append, overwriting on each call`() {
        val log = ReportLog()
        log.append("first", summary(site = "site-a.test"), nowMillis = 0L)
        assertEquals(LastScanLine.Entry(origin = "site-a.test", reason = LastScanLine.Reason.REFUSED), log.lastScanInfo)
        log.append("second", summary(site = "site-b.test"), nowMillis = 1000L)
        assertEquals(LastScanLine.Entry(origin = "site-b.test", reason = LastScanLine.Reason.REFUSED), log.lastScanInfo)
    }

    @Test
    fun `clear resets lastScanInfo to null`() {
        val log = ReportLog()
        log.append("report", summary(site = "state.gov"), nowMillis = 0L)
        log.clear()
        assertEquals(null, log.lastScanInfo)
    }

    @Test
    fun `restore sets lastScanInfo from its argument, defaulting to null when omitted (old persisted files predate this field)`() {
        val log = ReportLog()
        log.append("stale", summary(), nowMillis = 0L)
        log.restore(listOf("09:00:00 · site-a.test\n\nResult    restored"))
        assertEquals("restore with no lastScanInfo argument defaults to null", null, log.lastScanInfo)

        val log2 = ReportLog()
        val restoredEntry = LastScanLine.Entry(origin = "state.gov", reason = LastScanLine.Reason.DELIVERED, threshold = 18, overThreshold = true)
        log2.restore(listOf("09:00:00 · site-a.test\n\nResult    restored"), lastScanInfo = restoredEntry)
        assertEquals(restoredEntry, log2.lastScanInfo)
    }

    // Owner refinement (2026-09-05) — handoffGeneration/lastScanGeneration,
    // the staleness counters LastScanLine.isStale compares.

    @Test
    fun `handoffGeneration and lastScanGeneration are both 0 before any handoff capture or append`() {
        val log = ReportLog()
        assertEquals(0, log.handoffGeneration)
        assertEquals(0, log.lastScanGeneration)
    }

    @Test
    fun `noteNewHandoffCaptured increments handoffGeneration by exactly one per call`() {
        val log = ReportLog()
        log.noteNewHandoffCaptured()
        assertEquals(1, log.handoffGeneration)
        log.noteNewHandoffCaptured()
        assertEquals(2, log.handoffGeneration)
    }

    @Test
    fun `a terminal SCAN append stamps lastScanGeneration with the CURRENT handoffGeneration`() {
        val log = ReportLog()
        log.noteNewHandoffCaptured()
        log.noteNewHandoffCaptured()
        log.append("report", summary(site = "state.gov"), nowMillis = 0L)
        assertEquals(2, log.lastScanGeneration)
    }

    @Test
    fun `a pending SCAN append never stamps lastScanGeneration`() {
        val log = ReportLog()
        log.append("terminal", summary(site = "state.gov"), nowMillis = 0L)
        log.noteNewHandoffCaptured()
        log.append("in progress", summary(site = "other-site.test"), attemptId = "a1", pending = true, nowMillis = 1000L)
        assertEquals("the pending append must not advance the stamped generation", 0, log.lastScanGeneration)
    }

    @Test
    fun `a DIAGNOSTICS append never stamps lastScanGeneration`() {
        val log = ReportLog()
        log.noteNewHandoffCaptured()
        log.append("probe", summary(), channel = ReportLog.Channel.DIAGNOSTICS, nowMillis = 0L)
        assertEquals(0, log.lastScanGeneration)
    }

    @Test
    fun `clear resets lastScanGeneration but never handoffGeneration`() {
        val log = ReportLog()
        log.noteNewHandoffCaptured()
        log.append("report", summary(site = "state.gov"), nowMillis = 0L)
        log.clear()
        assertEquals("handoffGeneration tracks av:// captures, unrelated to clearing the log", 1, log.handoffGeneration)
        assertEquals(0, log.lastScanGeneration)
    }

    @Test
    fun `restore sets both generation counters from their arguments, defaulting to 0 when omitted`() {
        val log = ReportLog()
        log.restore(emptyList(), handoffGeneration = 3, lastScanGeneration = 2)
        assertEquals(3, log.handoffGeneration)
        assertEquals(2, log.lastScanGeneration)

        val log2 = ReportLog()
        log2.restore(emptyList())
        assertEquals("an old persisted file/Bundle predating these fields restores both to 0", 0, log2.handoffGeneration)
        assertEquals(0, log2.lastScanGeneration)
    }

    // D58 step 1 / finding #13 (unbounded ReportLog.entries growth,
    // TransactionTooLargeException reachable via looped av:// intents) — a
    // FIX per the owner's position (Challenge C): externally-triggerable
    // unbounded persisted growth is a crash risk, not a cosmetic gap.
    @Test
    fun `append evicts the oldest entry once entries exceed the bound, keeping the newest`() {
        val log = ReportLog()
        repeat(ReportLog.MAX_ENTRIES + 1) { i ->
            log.append("report $i", summary(site = "site-$i.test"), nowMillis = i.toLong())
        }
        val entries = log.entriesSnapshot()
        assertEquals("bound is enforced — size never exceeds MAX_ENTRIES", ReportLog.MAX_ENTRIES, entries.size)
        assertFalse("the oldest entry (report 0) was evicted", entries.any { it.contains("report 0") && it.contains("site-0.test") })
        assertTrue("the newest entry is intact", entries.last().contains("report ${ReportLog.MAX_ENTRIES}") && entries.last().contains("site-${ReportLog.MAX_ENTRIES}.test"))
        assertTrue("the entry right after the evicted one is now the oldest survivor", entries.first().contains("report 1") && entries.first().contains("site-1.test"))
    }

    @Test
    fun `eviction shifts a still-open pending index so its later terminal outcome still replaces correctly`() {
        val log = ReportLog()
        // Open a pending entry as entry #0, then push MAX_ENTRIES more
        // unrelated appends so #0's pending entry is evicted — a genuinely
        // late Thread{} landing (finding #5) must not corrupt a DIFFERENT,
        // still-open pending entry's index.
        log.append("in progress for the survivor", summary(), attemptId = "survivor", pending = true, nowMillis = 0L)
        repeat(ReportLog.MAX_ENTRIES) { i ->
            log.append("filler $i", summary(site = "filler-$i.test"), nowMillis = (i + 1).toLong())
        }
        // The survivor's pending entry has now been evicted (it was oldest).
        // Its terminal outcome must not corrupt an unrelated index — since
        // its pending marker is gone, the terminal outcome appends fresh
        // rather than silently overwriting whatever now occupies index 0.
        log.append("survivor terminal", summary(), attemptId = "survivor", nowMillis = 9999L)
        val entries = log.entriesSnapshot()
        assertTrue("the terminal outcome for an evicted pending entry appends rather than clobbering an unrelated entry", entries.last().contains("survivor terminal"))
        assertFalse("index 0 (now some filler entry) was not overwritten by the evicted attempt's terminal outcome", entries[0].contains("survivor terminal"))
    }

    @Test
    fun `clear empties the log`() {
        // Pins the clearing primitive itself, independent of whether any
        // caller invokes it — as of D45, MainActivity's wipeSession() no
        // longer does.
        val log = ReportLog()
        log.append("a report", summary(), nowMillis = 0L)
        assertEquals(1, log.entriesSnapshot().size)
        log.clear()
        assertTrue(log.entriesSnapshot().isEmpty())
        assertEquals("", log.rendered())
    }

    @Test
    fun `restore replaces entries verbatim without re-rendering`() {
        val log = ReportLog()
        log.append("stale entry", summary(), nowMillis = 0L)
        val saved = listOf("09:00:00 · site-a.test\n\nResult    restored one", "09:00:05 · site-b.test\n\nResult    restored two")
        log.restore(saved)
        assertEquals(saved, log.entriesSnapshot())
    }

    // §6.2 item 16 (2026-09-01 real-device fix, "fix 1"): the log view must
    // show the NEWEST entry first — [entriesSnapshot] (storage/D35 restore
    // shape) stays oldest-first; only [rendered] (display) is reversed.
    @Test
    fun `rendered shows the newest entry first, joined with a blank line`() {
        val log = ReportLog()
        log.append("alpha", summary(site = "alpha.test"), nowMillis = 0L)
        log.append("beta", summary(site = "beta.test"), nowMillis = 1000L)
        log.append("gamma", summary(site = "gamma.test"), nowMillis = 2000L)
        val rendered = log.rendered()
        val gammaIndex = rendered.indexOf("gamma.test")
        val betaIndex = rendered.indexOf("beta.test")
        val alphaIndex = rendered.indexOf("alpha.test")
        assertTrue("newest (gamma) renders before beta", gammaIndex in 0 until betaIndex)
        assertTrue("beta renders before oldest (alpha)", betaIndex < alphaIndex)
        assertTrue(rendered.contains("\n\n"))
    }

    // §6.2 item 16 (2026-09-01, second real-device fix — "one point bigger,
    // if it would remain light"): the title line size bump.
    //
    // NOT TESTED HERE, and deliberately: `rendered(titleSizePx = ...)`
    // builds an `android.text.SpannableStringBuilder`, and this module's
    // plain-JVM unit-test sandbox (`unitTests.isReturnDefaultValues = true`
    // in build.gradle.kts) makes that class a no-op stub — empirically
    // confirmed while building this: `SpannableStringBuilder("x").toString()`
    // returns `null` and `.length` returns `0` under this sandbox, so a
    // test that called `rendered(titleSizePx = N)` and asserted on its
    // content would not be exercising real behaviour, it would be
    // asserting on stub defaults — worse than no test, since it could
    // "pass" for the wrong reason or fail for a reason unrelated to a real
    // regression. What IS real and IS tested below is the pure
    // [ReportLog.titleLineRanges] computation `rendered` uses to decide
    // WHERE to apply the span; that whichever exists (span or no span),
    // `rendered()`'s content contract (unstyled call = plain String,
    // #verified by every other test in this file) is unaffected, since the
    // styled path only ADDS a span over an already-correct string it never
    // otherwise mutates (see `rendered`'s implementation — it builds the
    // SpannableStringBuilder from the exact same `joined` string the plain
    // path returns). Actual on-screen styling needs a real device or an
    // instrumented/Robolectric test, neither of which this suite runs.

    @Test
    fun `titleLineRanges - a single entry's range covers exactly its title line, not the body`() {
        val entry = "09:00:00 · site-a.test" + "\n\n" + "Result    Verified — the site accepted you"
        val ranges = ReportLog.titleLineRanges(listOf(entry))
        assertEquals(1, ranges.size)
        val range = ranges[0]
        assertEquals("09:00:00 · site-a.test", entry.substring(range.first, range.last + 1))
        assertFalse("the range must not bleed into the body", entry.substring(range.first, range.last + 1).contains("Result"))
    }

    @Test
    fun `titleLineRanges - multiple entries, each range lands on the correct title within the joined text`() {
        val entries = listOf(
            "09:00:02 · site-b.test" + "\n\n" + "Result    second",
            "09:00:01 · site-a.test" + "\n\n" + "Result    first",
        )
        val joined = entries.joinToString("\n\n")
        val ranges = ReportLog.titleLineRanges(entries)
        assertEquals(2, ranges.size)
        assertEquals("09:00:02 · site-b.test", joined.substring(ranges[0].first, ranges[0].last + 1))
        assertEquals("09:00:01 · site-a.test", joined.substring(ranges[1].first, ranges[1].last + 1))
    }

    @Test
    fun `titleLineRanges - empty entry list yields no ranges`() {
        assertTrue(ReportLog.titleLineRanges(emptyList()).isEmpty())
    }

    // Storage order — what onSaveInstanceState persists and restores across
    // Activity recreation (D35) — MUST stay oldest-first even though display
    // is now reversed; a round-trip through entriesSnapshot()/restore() must
    // keep coming back byte-identical in the SAME (oldest-first) order.
    @Test
    fun `entriesSnapshot stays oldest first and round-trips through restore unchanged`() {
        val log = ReportLog()
        log.append("alpha", summary(site = "alpha.test"), nowMillis = 0L)
        log.append("beta", summary(site = "beta.test"), nowMillis = 1000L)
        val snapshot = log.entriesSnapshot()
        assertTrue("storage order is oldest first", snapshot[0].contains("alpha.test") && snapshot[1].contains("beta.test"))

        val restored = ReportLog()
        restored.restore(snapshot)
        assertEquals(snapshot, restored.entriesSnapshot())
        // and the restored log still displays newest-first, same as the original
        assertTrue(restored.rendered().indexOf("beta.test") < restored.rendered().indexOf("alpha.test"))
    }

    // §6.2 item 16 (2026-09-01 real-device fix, "fix 2" — the stale
    // "In progress" entry): a mint attempt's terminal outcome must REPLACE
    // its own in-progress entry, not append a second one — one scan, one
    // entry.
    @Test
    fun `a terminal outcome replaces its own in-progress entry — one attempt, one entry`() {
        val log = ReportLog()
        log.append(
            "mode: B\nmint_gate: MET",
            summary(result = "In progress — waiting for you to authorize with biometrics or a device PIN", sent = "nothing yet", shared = ReportLog.DisclosureSummary.Shared.NotDisclosed("nothing yet")),
            attemptId = "attempt-1",
            pending = true,
            nowMillis = 0L,
        )
        assertEquals(1, log.entriesSnapshot().size)
        assertTrue(log.entriesSnapshot()[0].contains("In progress"))

        log.append(
            "mode: B\nmint: OK\nverdict: PASS (minted)",
            summary(),
            attemptId = "attempt-1",
            nowMillis = 5000L,
        )
        val entries = log.entriesSnapshot()
        assertEquals("the terminal outcome REPLACED the pending entry, never appended a second one", 1, entries.size)
        assertTrue(entries[0].contains("Verified — the site accepted you"))
        assertFalse("the stale In progress text is gone", entries[0].contains("In progress"))
    }

    // A scan interrupted mid-flight (app backgrounded/killed before the
    // terminal outcome ever arrives) must NOT be silently erased — its
    // "In progress" entry keeps showing, since nothing here ever REMOVES an
    // entry, only replaces one already known to be superseded.
    @Test
    fun `an in-progress entry with no terminal outcome still shows`() {
        val log = ReportLog()
        log.append(
            "mode: B\nmint_gate: MET",
            summary(result = "In progress — waiting for you to authorize with biometrics or a device PIN", sent = "nothing yet", shared = ReportLog.DisclosureSummary.Shared.NotDisclosed("nothing yet")),
            attemptId = "attempt-interrupted",
            pending = true,
            nowMillis = 0L,
        )
        // A later, UNRELATED entry (a different attempt, or none at all —
        // e.g. a diagnostic probe) must not clobber the interrupted one.
        log.append("mode: A\nverdict: PASS (read)", summary(shared = notDisclosedNothing), nowMillis = 1000L)
        val entries = log.entriesSnapshot()
        assertEquals(2, entries.size)
        assertTrue("the interrupted attempt's In progress entry survives, untouched", entries[0].contains("In progress"))
    }

    // Two DIFFERENT attempt ids must never be confused with each other —
    // an unrelated report (no attemptId, e.g. a probe button) between a
    // pending entry and its real terminal outcome must not accidentally
    // replace the pending entry.
    @Test
    fun `an unrelated entry between a pending entry and its resolution does not replace the pending entry`() {
        val log = ReportLog()
        log.append(
            "mode: B\nmint_gate: MET",
            summary(result = "In progress — waiting for you to authorize with biometrics or a device PIN", shared = ReportLog.DisclosureSummary.Shared.NotDisclosed("nothing yet")),
            attemptId = "attempt-2",
            pending = true,
            nowMillis = 0L,
        )
        log.append("===== MASTERLIST PROBE =====", summary(site = "Local scan (no site)", shared = notDisclosedNothing), nowMillis = 500L) // no attemptId — a probe
        assertEquals(2, log.entriesSnapshot().size)

        log.append("mode: B\nmint: OK", summary(), attemptId = "attempt-2", nowMillis = 5000L)
        val entries = log.entriesSnapshot()
        assertEquals("the probe appended; the terminal outcome replaced ONLY attempt-2's entry", 2, entries.size)
        assertTrue(entries[0].contains("Verified — the site accepted you"))
        assertTrue(entries[1].contains("MASTERLIST PROBE"))
    }

    // §6.2 item 16 (2026-09 real-device fix, "chip status in the plain
    // block"): renders as a label+value line, same shape as
    // Result/Sent/Shared/Identity, placed right after Result. (A `Mode`
    // line was here too — owner decision removed it as redundant with
    // Sent/Shared/Identity; there is no DisclosureSummary.mode field any
    // more, see that class's doc.)

    @Test
    fun `Chip auth renders as a label-value line right after Result`() {
        val log = ReportLog()
        log.append(
            "mode: B\nchip_auth (D21 payload field): passed",
            summary(chipAuthenticity = "Verified — this document's chip proved it is genuine"),
            nowMillis = 0L,
        )
        val entry = log.entriesSnapshot()[0]
        val lines = entry.lines()
        val resultIndex = lines.indexOfFirst { it.startsWith("Result") }
        assertEquals("Chip auth Verified — this document's chip proved it is genuine", lines[resultIndex + 1])
    }

    // CRITICAL per the owner: three states, not two. NOT_SUPPORTED must
    // never read as though the check ran and returned false — a document
    // (e.g. the real-device US passport) with no chip authentication at
    // all is a stated limitation, not a failed check. Owner-revised
    // wording (2026-09, second round): the original "clone" phrasing was
    // rejected as alarming to a non-technical reader appearing on every
    // US-passport-shaped scan.

    @Test
    fun `chip auth VERIFIED state renders honestly`() {
        val log = ReportLog()
        log.append("x", summary(chipAuthenticity = "Verified — this document's chip proved it is genuine"), nowMillis = 0L)
        val entry = log.entriesSnapshot()[0]
        assertTrue(entry.contains("Chip auth Verified — this document's chip proved it is genuine"))
    }

    @Test
    fun `chip auth NOT_SUPPORTED state never reads as false or as a failure`() {
        val log = ReportLog()
        log.append("x", summary(chipAuthenticity = "Not supported — this document has no chip authenticity check"), nowMillis = 0L)
        val entry = log.entriesSnapshot()[0]
        val chipAuthLine = entry.lines().first { it.startsWith("Chip auth") }
        assertEquals("Chip auth Not supported — this document has no chip authenticity check", chipAuthLine)
        assertFalse("must never render the bare word false for a not-supported document", chipAuthLine.contains("false", ignoreCase = true))
        assertFalse("must never read as though the check failed", chipAuthLine.contains("did not pass"))
    }

    @Test
    fun `chip auth FAILED state is distinct from NOT_SUPPORTED - the third state`() {
        val log = ReportLog()
        log.append("x", summary(chipAuthenticity = "Not verified — the chip check did not pass"), nowMillis = 0L)
        val entry = log.entriesSnapshot()[0]
        val chipAuthLine = entry.lines().first { it.startsWith("Chip auth") }
        assertEquals("Chip auth Not verified — the chip check did not pass", chipAuthLine)
        assertFalse("FAILED must read differently from NOT_SUPPORTED, never the same text", chipAuthLine.contains("no chip authenticity check"))
    }

    @Test
    fun `Chip auth is omitted entirely when never determined for this entry`() {
        val log = ReportLog()
        log.append("x", summary(chipAuthenticity = null), nowMillis = 0L)
        val entry = log.entriesSnapshot()[0]
        assertFalse(entry.contains("Chip auth"))
    }

    // ------------------------------------------------------------- item 18
    // §6.2 item 18 (D67, Q43): collapsed by default, per-entry toggle,
    // content unchanged. [entriesSnapshot] is the stored, persisted
    // content — item 18's MUST NOT applies to it, and every test above
    // already pins it in full regardless of collapse state. [rendered]/
    // [expandedSnapshot] are what this section pins.

    @Test
    fun `a newly-added entry is collapsed by default`() {
        val log = ReportLog()
        log.append("mode: A\nverdict: PASS (read)", summary(shared = notDisclosedNothing), nowMillis = 0L)
        assertEquals(listOf(false), log.expandedSnapshot())
    }

    @Test
    fun `rendered shows only the title line for a collapsed entry, not the body`() {
        val log = ReportLog()
        log.append("mode: A\nverdict: PASS (read)", summary(result = "Read OK — nothing sent"), nowMillis = 0L)
        val rendered = log.rendered().toString()
        assertFalse("collapsed entry must not show the Result body line", rendered.contains("Result"))
        assertTrue("collapsed entry still shows its title (timestamp + site)", rendered.contains("127.0.0.1:8787"))
    }

    @Test
    fun `toggling expanded at display index 0 reveals the full block, content unchanged from entriesSnapshot`() {
        val log = ReportLog()
        log.append("mode: A\nverdict: PASS (read)", summary(result = "Read OK — nothing sent"), nowMillis = 0L)
        log.toggleExpandedAtDisplayIndex(0)
        assertEquals(listOf(true), log.expandedSnapshot())
        val rendered = log.rendered().toString()
        assertTrue("expanded entry shows the Result body line", rendered.contains("Result    Read OK — nothing sent"))
        // Item 18's own MUST: expanding never changes the block's content —
        // the stored, full entry must appear byte-identical inside rendered().
        assertTrue(rendered.contains(log.entriesSnapshot()[0]))
    }

    @Test
    fun `toggling twice returns to collapsed`() {
        val log = ReportLog()
        log.append("x", summary(), nowMillis = 0L)
        log.toggleExpandedAtDisplayIndex(0)
        log.toggleExpandedAtDisplayIndex(0)
        assertEquals(listOf(false), log.expandedSnapshot())
    }

    @Test
    fun `toggling one entry does not affect a sibling entry's expand state`() {
        val log = ReportLog()
        log.append("first", summary(site = "site-a.test"), nowMillis = 0L)
        log.append("second", summary(site = "site-b.test"), nowMillis = 1000L)
        // display index 0 = newest = "second" (site-b.test)
        log.toggleExpandedAtDisplayIndex(0)
        assertEquals(listOf(false, true), log.expandedSnapshot()) // oldest-first: [site-a, site-b]
    }

    @Test
    fun `toggleExpandedAtDisplayIndex out of range is ignored, never throws`() {
        val log = ReportLog()
        log.append("x", summary(), nowMillis = 0L)
        log.toggleExpandedAtDisplayIndex(5)
        log.toggleExpandedAtDisplayIndex(-1)
        assertEquals(listOf(false), log.expandedSnapshot())
    }

    @Test
    fun `restore defaults every entry to collapsed when no expanded state is supplied`() {
        val log = ReportLog()
        log.restore(listOf("09:00:00 · site-a.test\n\nResult    restored"))
        assertEquals(listOf(false), log.expandedSnapshot())
    }

    @Test
    fun `restore round-trips expanded state via expandedSnapshot`() {
        val log = ReportLog()
        log.append("first", summary(site = "site-a.test"), nowMillis = 0L)
        log.append("second", summary(site = "site-b.test"), nowMillis = 1000L)
        log.toggleExpandedAtDisplayIndex(1) // the oldest, "first"
        val savedEntries = log.entriesSnapshot()
        val savedExpanded = log.expandedSnapshot()

        val restored = ReportLog()
        restored.restore(savedEntries, expanded = savedExpanded)
        assertEquals(savedExpanded, restored.expandedSnapshot())
    }

    @Test
    fun `clear empties expandedSnapshot alongside entries`() {
        val log = ReportLog()
        log.append("x", summary(), nowMillis = 0L)
        log.clear()
        assertTrue(log.expandedSnapshot().isEmpty())
    }

    // A replaced (pending -> terminal) entry keeps whatever expand state it
    // already had — a user who opened an in-progress entry to watch it is
    // not collapsed out from under them when it resolves (design decision,
    // see ReportLog's expandedFlags doc).
    @Test
    fun `a terminal outcome replacing a pending entry preserves its expanded state`() {
        val log = ReportLog()
        log.append(
            "mode: B\nmint_gate: MET",
            summary(result = "In progress — waiting for you to authorize with biometrics or a device PIN", shared = ReportLog.DisclosureSummary.Shared.NotDisclosed("nothing yet")),
            attemptId = "a1",
            pending = true,
            nowMillis = 0L,
        )
        log.toggleExpandedAtDisplayIndex(0)
        log.append("mode: B\nmint: OK", summary(), attemptId = "a1", nowMillis = 5000L)
        assertEquals(listOf(true), log.expandedSnapshot())
    }

    @Test
    fun `eviction shifts expanded flags in lockstep with entries`() {
        val log = ReportLog()
        log.append("first", summary(site = "site-0.test"), nowMillis = 0L)
        log.toggleExpandedAtDisplayIndex(0) // expand the soon-to-be-evicted entry
        repeat(ReportLog.MAX_ENTRIES) { i ->
            log.append("filler $i", summary(site = "filler-$i.test"), nowMillis = (i + 1).toLong())
        }
        assertEquals(ReportLog.MAX_ENTRIES, log.entriesSnapshot().size)
        assertEquals(ReportLog.MAX_ENTRIES, log.expandedSnapshot().size)
        // the survivor (oldest remaining, "filler 0") was never expanded
        assertFalse(log.expandedSnapshot()[0])
    }

    // ------------------------------------------------------------- item 22
    // §6.2 item 22 (D70(a)) — the quick-review glyph. Three entries in the
    // three states show three distinct prefixes; the replacing writer flips
    // PENDING -> PASS/FAIL. Every expectation here is an independent
    // literal, never derived from the same constant [ReportLog] itself uses
    // (see ReportLog.glyphFor's own literals) — a bug that changed the
    // glyph mapping would have nothing to agree with it.

    @Test
    fun `three entries in the three outcome states render three distinct glyph prefixes`() {
        val log = ReportLog()
        log.append("pass entry", summary(site = "site-pass.test"), outcome = ReportLog.Outcome.PASS, nowMillis = 0L)
        log.append("fail entry", summary(site = "site-fail.test"), outcome = ReportLog.Outcome.FAIL, nowMillis = 1000L)
        log.append(
            "pending entry",
            summary(site = "site-pending.test", result = "In progress", sent = "nothing yet", shared = notDisclosedNothing),
            attemptId = "a1",
            pending = true,
            nowMillis = 2000L,
        )
        val entries = log.entriesSnapshot()
        assertEquals(3, entries.size)
        assertTrue("PASS entry is prefixed with a checkmark", entries[0].lines()[0].startsWith("✓ "))
        assertTrue("FAIL entry is prefixed with an X", entries[1].lines()[0].startsWith("✗ "))
        assertTrue("PENDING entry is prefixed with an ellipsis", entries[2].lines()[0].startsWith("… "))
        val prefixes = entries.map { it.lines()[0].substring(0, 2) }
        assertEquals("all three prefixes are distinct", 3, prefixes.toSet().size)
    }

    @Test
    fun `the collapsed title line still carries the glyph prefix`() {
        val log = ReportLog()
        log.append("x", summary(), outcome = ReportLog.Outcome.FAIL, nowMillis = 0L)
        val rendered = log.rendered().toString()
        assertTrue("collapsed entry's title line keeps the glyph", rendered.startsWith("✗ "))
    }

    @Test
    fun `a pending entry replaced by its terminal outcome flips the glyph from pending to pass`() {
        val log = ReportLog()
        log.append(
            "in progress",
            summary(result = "In progress", sent = "nothing yet", shared = notDisclosedNothing),
            attemptId = "a1",
            pending = true,
            nowMillis = 0L,
        )
        assertTrue(log.entriesSnapshot()[0].lines()[0].startsWith("… "))
        log.append("terminal", summary(), attemptId = "a1", outcome = ReportLog.Outcome.PASS, nowMillis = 5000L)
        val entries = log.entriesSnapshot()
        assertEquals(1, entries.size)
        assertTrue("the SAME entry now shows the PASS glyph, not a second entry", entries[0].lines()[0].startsWith("✓ "))
    }

    @Test
    fun `a pending entry replaced by its terminal outcome flips the glyph from pending to fail`() {
        val log = ReportLog()
        log.append(
            "in progress",
            summary(result = "In progress", sent = "nothing yet", shared = notDisclosedNothing),
            attemptId = "a1",
            pending = true,
            nowMillis = 0L,
        )
        log.append("terminal", summary(), attemptId = "a1", outcome = ReportLog.Outcome.FAIL, nowMillis = 5000L)
        val entries = log.entriesSnapshot()
        assertEquals(1, entries.size)
        assertTrue(entries[0].lines()[0].startsWith("✗ "))
    }

    @Test
    fun `outcome defaults to FAIL — the conservative, never-guess-PASS fallback`() {
        val log = ReportLog()
        log.append("x", summary(), nowMillis = 0L) // no outcome argument
        assertTrue(log.entriesSnapshot()[0].lines()[0].startsWith("✗ "))
    }

    @Test
    fun `outcomesSnapshot is oldest-first and parallel to entriesSnapshot`() {
        val log = ReportLog()
        log.append("first", summary(site = "site-a.test"), outcome = ReportLog.Outcome.PASS, nowMillis = 0L)
        log.append("second", summary(site = "site-b.test"), outcome = ReportLog.Outcome.FAIL, nowMillis = 1000L)
        assertEquals(listOf(ReportLog.Outcome.PASS, ReportLog.Outcome.FAIL), log.outcomesSnapshot())
    }

    @Test
    fun `clear empties outcomesSnapshot alongside entries`() {
        val log = ReportLog()
        log.append("x", summary(), outcome = ReportLog.Outcome.PASS, nowMillis = 0L)
        log.clear()
        assertTrue(log.outcomesSnapshot().isEmpty())
    }

    @Test
    fun `restore round-trips outcome state via outcomesSnapshot`() {
        val log = ReportLog()
        log.append("first", summary(site = "site-a.test"), outcome = ReportLog.Outcome.PASS, nowMillis = 0L)
        log.append("second", summary(site = "site-b.test"), outcome = ReportLog.Outcome.FAIL, nowMillis = 1000L)
        val savedEntries = log.entriesSnapshot()
        val savedOutcomes = log.outcomesSnapshot()

        val restored = ReportLog()
        restored.restore(savedEntries, outcomes = savedOutcomes)
        assertEquals(savedOutcomes, restored.outcomesSnapshot())
    }

    @Test
    fun `restore defaults every entry to FAIL when no outcome state is supplied`() {
        val log = ReportLog()
        log.restore(listOf("09:00:00 · site-a.test\n\nResult    restored"))
        assertEquals(listOf(ReportLog.Outcome.FAIL), log.outcomesSnapshot())
    }

    @Test
    fun `eviction shifts outcomes in lockstep with entries`() {
        val log = ReportLog()
        log.append("first", summary(site = "site-0.test"), outcome = ReportLog.Outcome.PASS, nowMillis = 0L)
        repeat(ReportLog.MAX_ENTRIES) { i ->
            log.append("filler $i", summary(site = "filler-$i.test"), outcome = ReportLog.Outcome.FAIL, nowMillis = (i + 1).toLong())
        }
        assertEquals(ReportLog.MAX_ENTRIES, log.outcomesSnapshot().size)
        // the survivor (oldest remaining, "filler 0") is FAIL, not the
        // evicted PASS entry's value.
        assertEquals(ReportLog.Outcome.FAIL, log.outcomesSnapshot()[0])
    }

    @Test
    fun `glyphFor maps each Outcome to its own fixed, distinct literal`() {
        assertEquals("✓ ", ReportLog.glyphFor(ReportLog.Outcome.PASS))
        assertEquals("✗ ", ReportLog.glyphFor(ReportLog.Outcome.FAIL))
        assertEquals("… ", ReportLog.glyphFor(ReportLog.Outcome.PENDING))
    }
}
