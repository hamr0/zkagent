package com.tananaev.passportreader

import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

/**
 * §6.2 item 23 (D70(b)) — pure JSON serialize/deserialize for [ReportLog]'s
 * value-free entries/expanded/outcome state, independent of
 * Android's `File`/`Context` APIs so the round-trip and corrupt-input
 * behavior is unit-testable without touching disk (`ReportLogStoreTest`).
 * [MainActivity] is the only caller with actual file I/O (atomic
 * temp-then-rename write to `filesDir`, read on `onCreate`) — this class
 * never opens a file itself.
 *
 * **Storage choice: a single JSON file, not `SharedPreferences`.**
 * `SharedPreferences` has no native array type — persisting the
 * PARALLEL, index-aligned lists (entries/expanded/outcomes) would mean
 * independently-encoded strings (e.g. `String.join`) that could silently
 * drift to different lengths if one write partially failed. A single JSON
 * document keeps the lists visibly parallel — ONE array of small
 * per-entry objects, mirroring [ReportLog]'s own index-per-entry shape —
 * and is no more code than the SharedPreferences alternative would have
 * needed anyway once each list is (de)serialized.
 *
 * **D59's cap, enforced again on load.** [fromJson] trims to the newest
 * [ReportLog.MAX_ENTRIES] entries (oldest-first, same eviction shape as
 * [ReportLog.append]) — belt-and-suspenders against a file written by an
 * older/future build that did not yet enforce the cap, or edited by hand.
 *
 * **Value-free by construction (item 5/D46 unchanged).** Every field this
 * class reads or writes — `text` (the exact rendered entry [ReportLog]
 * already produces), `expanded`, `outcome` — is already
 * present, unmodified, in what [ReportLog.rendered] shows on screen today.
 * No MRZ, zktag, nonce, or key material is ever threaded through this
 * class; item 6 (session state wiped in `onStop`) is untouched — this
 * class only ever sees what [MainActivity.emitReport] already wrote to the
 * Log pane.
 */
object ReportLogStore {

    private const val TAG = "ReportLogStore"
    private const val SCHEMA_VERSION = 1

    /** Everything [ReportLog] exposes that needs to survive process death —
     * the disk-shaped sibling of the Bundle keys `MainActivity` already
     * saves in `onSaveInstanceState`.
     *
     * @param lastScanInfo (owner FIX, 2026-09-05) the "Last scan" line's
     *   data — see [ReportLog.lastScanInfo]/[LastScanLine.Entry]'s doc.
     *   Defaults to null so every existing positional/named construction
     *   (including [EMPTY]) keeps compiling unchanged.
     * @param handoffGeneration @param lastScanGeneration (owner refinement,
     *   2026-09-05) the staleness counters — see
     *   [ReportLog.handoffGeneration]/[ReportLog.lastScanGeneration]'s doc.
     *   Both default to 0, same backward-compatibility reasoning as
     *   [lastScanInfo].
     * @param originThresholdLocks §6.5 S1 (D74) — the per-origin
     *   "first-seen threshold" lock (`ThresholdPolicy`'s own decision),
     *   keyed by HOSTNAME ONLY (see [ThresholdPolicy]'s class doc for why),
     *   value the locked threshold. Deliberately a SEPARATE field from
     *   [entries]/[expanded]/[outcomes]/[lastText]/[lastScanInfo] — S1's own
     *   spec requires "Clear log" to empty the log but NEVER this record
     *   (it is policy state, not log); [MainActivity.clearLog] only ever
     *   clears [ReportLog]'s own fields, never this map, so a `persistLog()`
     *   call after a clear still round-trips whatever locks were already
     *   recorded. Defaults to an empty map, same backward-compatibility
     *   reasoning as [lastScanInfo]/[handoffGeneration] — an OLD file
     *   written before this field existed simply lacks the key, read back
     *   as empty with no version check required. */
    data class Snapshot(
        val entries: List<String>,
        val expanded: List<Boolean>,
        val outcomes: List<ReportLog.Outcome>,
        val lastText: String?,
        val lastScanInfo: LastScanLine.Entry? = null,
        val handoffGeneration: Int = 0,
        val lastScanGeneration: Int = 0,
        val originThresholdLocks: Map<String, Int> = emptyMap(),
    ) {
        companion object {
            val EMPTY = Snapshot(emptyList(), emptyList(), emptyList(), null)
        }
    }

    /** Serializes [snapshot] to a JSON string. Pure — never touches disk. */
    fun toJson(snapshot: Snapshot): String {
        val entriesArray = JSONArray()
        for (i in snapshot.entries.indices) {
            val entryObject = JSONObject()
            entryObject.put("text", snapshot.entries[i])
            entryObject.put("expanded", snapshot.expanded.getOrElse(i) { false })
            entryObject.put("outcome", snapshot.outcomes.getOrElse(i) { ReportLog.Outcome.FAIL }.name)
            entriesArray.put(entryObject)
        }
        val root = JSONObject()
        root.put("version", SCHEMA_VERSION)
        root.put("entries", entriesArray)
        root.put("last_text", snapshot.lastText ?: JSONObject.NULL)
        // FIX (owner, 2026-09-05): nested object, or JSONObject.NULL when
        // there is no "Last scan" line yet — an OLD file written before
        // this field existed simply lacks this key entirely, which
        // `fromJson`'s `optJSONObject` already reads back as null (see its
        // doc) with no special-case code needed for backward compatibility.
        root.put("last_scan_info", LastScanLine.toJsonObject(snapshot.lastScanInfo) ?: JSONObject.NULL)
        // Owner refinement (2026-09-05) — the staleness counters, persisted
        // alongside last_scan_info so a restart cannot forget a handoff was
        // captured after the last scan. Old files simply lack these keys —
        // fromJson's optInt(..., 0) below already reads that back as 0.
        root.put("handoff_generation", snapshot.handoffGeneration)
        root.put("last_scan_generation", snapshot.lastScanGeneration)
        // §6.5 S1 (D74) — the per-origin threshold-lock record, a plain
        // JSON object keyed by hostname. Old files simply lack this key —
        // fromJson's optJSONObject below already reads that back as an
        // empty map with no special-case code needed for backward
        // compatibility (same shape as last_scan_info above).
        val locksObject = JSONObject()
        for ((hostname, threshold) in snapshot.originThresholdLocks) {
            locksObject.put(hostname, threshold)
        }
        root.put("origin_threshold_locks", locksObject)
        return root.toString()
    }

    /** Parses [json] into a [Snapshot], enforcing the D59 cap again on
     * load. ANY parse failure — malformed JSON, a missing/wrong-typed
     * field, an unrecognised [ReportLog.Outcome] name — returns
     * [Snapshot.EMPTY] rather than a partially-recovered snapshot or a
     * thrown exception: a corrupt file must never crash app startup and
     * must never surface a guessed-at partial log. The caller
     * ([MainActivity]) is responsible for the `Log.w` this class itself
     * emits here, so a corrupt-file recovery is never silent. */
    fun fromJson(json: String): Snapshot {
        return try {
            val root = JSONObject(json)
            val entriesArray = root.optJSONArray("entries") ?: JSONArray()
            val entries = mutableListOf<String>()
            val expanded = mutableListOf<Boolean>()
            val outcomes = mutableListOf<ReportLog.Outcome>()
            for (i in 0 until entriesArray.length()) {
                val entryObject = entriesArray.getJSONObject(i)
                entries += entryObject.getString("text")
                expanded += entryObject.optBoolean("expanded", false)
                outcomes += runCatching { ReportLog.Outcome.valueOf(entryObject.getString("outcome")) }
                    .getOrDefault(ReportLog.Outcome.FAIL)
            }
            val lastText = if (root.isNull("last_text")) null else root.optString("last_text").ifEmpty { null }
            // FIX (owner, 2026-09-05): optJSONObject returns null for both
            // an explicit JSON null AND a wholly absent key — the exact
            // backward-compatibility behaviour an OLD file (written before
            // this field existed) needs, with no version check required.
            val lastScanInfo = LastScanLine.fromJsonObject(root.optJSONObject("last_scan_info"))
            // Owner refinement (2026-09-05) — optInt's default (0) is
            // exactly the backward-compatible value an old file (missing
            // both keys entirely) needs — no version check required.
            val handoffGeneration = root.optInt("handoff_generation", 0)
            val lastScanGeneration = root.optInt("last_scan_generation", 0)
            // §6.5 S1 (D74) — optJSONObject returns null for both an
            // explicit JSON null AND a wholly absent key (an OLD file
            // written before this field existed) — read back as an empty
            // map either way, same backward-compatibility shape as
            // last_scan_info above. A malformed (non-integer) value for a
            // given hostname is skipped rather than failing the whole
            // parse — one bad entry must not lose every other recorded
            // lock or crash startup.
            val locksJson = root.optJSONObject("origin_threshold_locks")
            val originThresholdLocks = mutableMapOf<String, Int>()
            if (locksJson != null) {
                val keys = locksJson.keys()
                while (keys.hasNext()) {
                    val hostname = keys.next()
                    if (locksJson.isNull(hostname)) continue
                    val value = locksJson.optInt(hostname, -1)
                    if (value > 0) originThresholdLocks[hostname] = value
                }
            }
            // D59: the cap is re-enforced on load, not just on append —
            // keep the newest MAX_ENTRIES, oldest-first (same shape as
            // ReportLog.append's own eviction).
            val overflow = entries.size - ReportLog.MAX_ENTRIES
            if (overflow > 0) {
                repeat(overflow) {
                    entries.removeAt(0)
                    expanded.removeAt(0)
                    outcomes.removeAt(0)
                }
            }
            Snapshot(entries, expanded, outcomes, lastText, lastScanInfo, handoffGeneration, lastScanGeneration, originThresholdLocks)
        } catch (e: Exception) {
            Log.w(TAG, "M2 stage: persisted log file is corrupt/unparseable — starting with an empty log (${e.javaClass.simpleName}: ${e.message})")
            Snapshot.EMPTY
        }
    }
}
