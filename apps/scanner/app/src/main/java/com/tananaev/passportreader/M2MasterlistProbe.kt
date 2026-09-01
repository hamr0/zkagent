package com.tananaev.passportreader

import android.content.Context
import android.os.Debug
import android.util.Log
import java.io.ByteArrayInputStream
import java.security.MessageDigest

/**
 * M2 riskiest-assumption POC, masterlist half — PRD v1.16 §6 M2 row
 * ("Half-loaded masterlist on the phone ⇒ read refused (ok:false), never a
 * pass"). Runs entirely on-device against the bundled `assets/masterList`
 * (the same BSI all-country ZIP contents M0 used, per M0-EVIDENCE.md SETUP
 * and Finding 1) — no chip tap required, so it can be exercised on the
 * bench before any document is presented.
 *
 * THROWAWAY. Not shipped, not graduated (AGENT_RULES: never ship the POC).
 *
 * Deliberately reuses [M0Probe.loadMasterList] UNCHANGED — this is the same
 * masterlist-loading code path M0 measured (certs declared vs certs parsed),
 * so TEST 2's numbers are comparable to M0-EVIDENCE.md's 588/588.
 *
 * Design note on the half-load negative (see docs/logs/M2-SCAN-EVIDENCE.md
 * "escalations"): M0 Finding 5 established that an assertion which silently
 * matches/excludes nothing is indistinguishable from one that holds. That
 * lesson is applied here three ways, in order, so the negative can never
 * silently look like a pass:
 *   1. If the truncated ASN.1 stream fails to parse at all (the expected
 *      outcome — cutting a DER structure in half almost always lands
 *      mid-object), the exception itself is the refusal.
 *   2. If it happens to parse some prefix of complete top-level objects
 *      before failing, [M0Probe.loadMasterList]'s own declared-vs-parsed
 *      counters may still agree with each other over that shorter prefix
 *      (each fully-read object contributes equally to both counters) —
 *      internally "consistent" but silently short. That is exactly the
 *      Finding-5 trap in a new shape, so it is NOT trusted alone.
 *   3. The external cross-check: the half-load's parsed count is compared
 *      against a real full load's parsed count from the SAME bytes, done
 *      in the SAME run. Fewer certificates than the known-good baseline is
 *      refused regardless of what the internal counters say.
 * Every branch below produces a refusal (ok:false-shaped: "could not
 * check"), never a bare "no" and never a pass — the failure mode this
 * negative exists to catch is a false pass, so the code path that would
 * produce one is called out explicitly as INVALID rather than swallowed.
 */
object M2MasterlistProbe {

    private const val TAG = "M2Masterlist"
    private const val BEGIN = "===== M2 MASTERLIST REPORT BEGIN ====="
    private const val END = "===== M2 MASTERLIST REPORT END ====="

    /** Native heap allocated, in KB. One of two memory signals recorded — see report. */
    private fun nativeHeapKb(): Long = Debug.getNativeHeapAllocatedSize() / 1024

    private fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    /**
     * Runs the full-load measurement (TEST 2 positive) and the half-load
     * negative, and logs a PII-free delimited report. No chip data is
     * touched — this operates only on the bundled masterlist asset.
     */
    fun runAndReport(context: Context) {
        val log = StringBuilder("\n$BEGIN\n")
        try {
            val fullBytes = context.assets.open("masterList").use { it.readBytes() }
            log.append("masterlist_asset_bytes: ${fullBytes.size}\n")
            log.append("masterlist_sha256: ${sha256Hex(fullBytes)}\n")

            // ---- TEST 2 positive: full parse, timed, memory measured ----
            val heapBeforeFull = nativeHeapKb()
            val t0 = System.nanoTime()
            val full = M0Probe.loadMasterList(ByteArrayInputStream(fullBytes))
            val fullParseMs = (System.nanoTime() - t0) / 1_000_000
            val heapAfterFull = nativeHeapKb()
            log.append(
                "full_load: declared=${full.certsDeclared} parsed=${full.certsParsed} " +
                    "consistent=${full.consistent}\n"
            )
            log.append("full_load_parse_time_ms: $fullParseMs\n")
            log.append(
                "full_load_native_heap_delta_kb: ${heapAfterFull - heapBeforeFull} " +
                    "(method: android.os.Debug.getNativeHeapAllocatedSize() before/after; " +
                    "a coarse signal — JVM/Kotlin object allocation during parsing is NOT " +
                    "captured by this call, see evidence doc for the caveat)\n"
            )

            // ---- NEGATIVE: half-loaded masterlist (truncate raw bytes to 50%) ----
            val half = fullBytes.copyOf(fullBytes.size / 2)
            val heapBeforeHalf = nativeHeapKb()
            val t1 = System.nanoTime()
            val halfResult: String = try {
                val r = M0Probe.loadMasterList(ByteArrayInputStream(half))
                when {
                    !r.consistent ->
                        "REFUSED (ok:false) — internal check caught it: declared=${r.certsDeclared} " +
                            "!= parsed=${r.certsParsed}"
                    r.certsParsed < full.certsParsed ->
                        "REFUSED (ok:false via EXTERNAL cross-check, M0 Finding 5 lesson applied) — " +
                            "internal declared==parsed=${r.certsParsed} looked clean but is below the " +
                            "known-good full-load count (${full.certsParsed}) measured in this same run; " +
                            "a verifier must not trust internal consistency alone here"
                    else ->
                        "INVALID TEST — half load produced parsed=${r.certsParsed} >= full=${full.certsParsed}; " +
                            "the truncation did not shrink the parse, so this run proves nothing " +
                            "(re-run before trusting any pass/refuse claim from this negative)"
                }
            } catch (e: Exception) {
                "REFUSED (ok:false) — parse threw ${e.javaClass.simpleName}: ${e.message} on the " +
                    "truncated stream, the expected outcome for a corrupt/incomplete masterlist"
            }
            val halfParseMs = (System.nanoTime() - t1) / 1_000_000
            val heapAfterHalf = nativeHeapKb()
            log.append(
                "NEGATIVE half_loaded_masterlist (truncated to ${half.size}/${fullBytes.size} bytes, " +
                    "attempt_time_ms=$halfParseMs, native_heap_delta_kb=${heapAfterHalf - heapBeforeHalf}): " +
                    "$halfResult\n"
            )

            log.append(
                "NEGATIVE csca_removed: NOT run here — it needs a real document's signing " +
                    "certificate (M0Probe.loadMasterList(excludeAnchorFor=...)), so it runs from the " +
                    "chip-scan path after a tap (see MainActivity / M2 SCAN REPORT), exactly as M0 did.\n"
            )
        } catch (e: Exception) {
            log.append("MASTERLIST PROBE FAILED: ${e.javaClass.simpleName}: ${e.message}\n")
        }
        log.append(END)
        for (line in log.toString().trimEnd('\n').lines()) Log.i(TAG, line)
    }
}
