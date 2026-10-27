package com.tananaev.passportreader

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Finding #51 / D115 — a source-wiring guard. [ReportLog.append] no longer invents a REFUSED entry for a SCAN call
 * with no `lastScan` (that default painted "Read OK — nothing was sent." red); it leaves the line alone. This test
 * reads MainActivity.kt and requires every terminal (non-pending, non-Diagnostics) `emitReport(` call to pass
 * `lastScan` explicitly, and the pre-scan website refusals to pass `LastScanLine.requestRefused()`. It cannot prove
 * the right reason is chosen (the device does that); it proves no terminal path is silently skipped.
 */
class LastScanWiringTest {

    private fun source(): String {
        val f = listOf("src/main/java", "app/src/main/java").map { File(it, "com/tananaev/passportreader/MainActivity.kt") }.first { it.exists() }
        return f.readText()
    }

    /** The text of each `emitReport(...)` call (balanced parentheses), excluding the declaration. */
    private fun calls(src: String): List<String> {
        val out = mutableListOf<String>()
        var i = 0
        while (true) {
            i = src.indexOf("emitReport(", i)
            if (i < 0) break
            val declaration = src.substring(maxOf(0, i - 12), i).contains("fun ")
            val start = i + "emitReport(".length
            var depth = 1
            var j = start
            var inStr = false
            while (j < src.length && depth > 0) {
                val c = src[j]
                if (c == '"' && src[j - 1] != '\\') inStr = !inStr
                if (!inStr) { if (c == '(') depth++ else if (c == ')') depth-- }
                j++
            }
            if (!declaration) out += src.substring(start, j).lines().filterNot { it.trim().startsWith("//") }.joinToString("\n")
            i = j
        }
        return out
    }

    @Test
    fun `every terminal SCAN emitReport call passes lastScan explicitly (no implicit default, D115)`() {
        val all = calls(source())
        // Diagnostics probes are not scans and must NOT touch the line; pending calls are ignored by ReportLog.
        val terminal = all.filter { !it.contains("Channel.DIAGNOSTICS") && !it.contains("pending = true") }
        assertTrue("guard must match something (found ${terminal.size} of ${all.size})", terminal.size >= 20)
        assertTrue(all.size > terminal.size) // the exclusions really excluded something
        for (c in terminal) {
            assertTrue("a terminal emitReport has no lastScan: ${c.take(120)}", c.contains("lastScan"))
        }
    }

    @Test
    fun `pre-scan website refusals pass requestRefused, never a site name`() {
        val markers = listOf(
            "verification session expired before lock",
            "valid verification mode",
            "threshold policy (S1/D74)",
            "operator policy (",
            "handoff: REFUSED — \${outcome.reason}",
        )
        val all = calls(source())
        for (m in markers) {
            val matching = all.filter { it.contains(m) }
            assertTrue("marker not found: $m", matching.isNotEmpty())
            for (c in matching) assertTrue("$m must use requestRefused()", c.contains("LastScanLine.requestRefused()"))
        }
    }

    @Test
    fun `the Read OK branch passes the neutral chip-check-passed entry`() {
        val c = calls(source()).single { it.contains("Read OK — nothing sent") }
        assertTrue(c.contains("LastScanLine.Reason.CHIP_CHECK_PASSED"))
        assertEquals(1, Regex("lastScan = ").findAll(c).count())
    }
}
