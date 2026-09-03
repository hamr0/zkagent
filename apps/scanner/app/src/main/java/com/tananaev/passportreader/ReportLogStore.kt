package com.tananaev.passportreader

import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

/**
 * §6.2 item 23 (D70(b)) — pure JSON serialize/deserialize for [ReportLog]'s
 * value-free entries/expanded/terminal/outcome state, independent of
 * Android's `File`/`Context` APIs so the round-trip and corrupt-input
 * behavior is unit-testable without touching disk (`ReportLogStoreTest`).
 * [MainActivity] is the only caller with actual file I/O (atomic
 * temp-then-rename write to `filesDir`, read on `onCreate`) — this class
 * never opens a file itself.
 *
 * **Storage choice: a single JSON file, not `SharedPreferences`.**
 * `SharedPreferences` has no native array type — persisting four
 * PARALLEL, index-aligned lists (entries/expanded/terminal/outcomes) would
 * mean four independently-encoded strings (e.g. `String.join`) that could
 * silently drift to different lengths if one write partially failed. A
 * single JSON document keeps the four lists visibly parallel — ONE array
 * of small per-entry objects, mirroring [ReportLog]'s own index-per-entry
 * shape — and is no more code than the SharedPreferences alternative would
 * have needed anyway once each list is (de)serialized.
 *
 * **D59's cap, enforced again on load.** [fromJson] trims to the newest
 * [ReportLog.MAX_ENTRIES] entries (oldest-first, same eviction shape as
 * [ReportLog.append]) — belt-and-suspenders against a file written by an
 * older/future build that did not yet enforce the cap, or edited by hand.
 *
 * **Value-free by construction (item 5/D46 unchanged).** Every field this
 * class reads or writes — `text` (the exact rendered entry [ReportLog]
 * already produces), `expanded`, `terminal`, `outcome` — is already
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
     * the disk-shaped sibling of the four Bundle keys `MainActivity`
     * already saves in `onSaveInstanceState`. */
    data class Snapshot(
        val entries: List<String>,
        val expanded: List<Boolean>,
        val terminal: List<Boolean>,
        val outcomes: List<ReportLog.Outcome>,
        val lastText: String?,
    ) {
        companion object {
            val EMPTY = Snapshot(emptyList(), emptyList(), emptyList(), emptyList(), null)
        }
    }

    /** Serializes [snapshot] to a JSON string. Pure — never touches disk. */
    fun toJson(snapshot: Snapshot): String {
        val entriesArray = JSONArray()
        for (i in snapshot.entries.indices) {
            val entryObject = JSONObject()
            entryObject.put("text", snapshot.entries[i])
            entryObject.put("expanded", snapshot.expanded.getOrElse(i) { false })
            entryObject.put("terminal", snapshot.terminal.getOrElse(i) { true })
            entryObject.put("outcome", snapshot.outcomes.getOrElse(i) { ReportLog.Outcome.FAIL }.name)
            entriesArray.put(entryObject)
        }
        val root = JSONObject()
        root.put("version", SCHEMA_VERSION)
        root.put("entries", entriesArray)
        root.put("last_text", snapshot.lastText ?: JSONObject.NULL)
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
            val terminal = mutableListOf<Boolean>()
            val outcomes = mutableListOf<ReportLog.Outcome>()
            for (i in 0 until entriesArray.length()) {
                val entryObject = entriesArray.getJSONObject(i)
                entries += entryObject.getString("text")
                expanded += entryObject.optBoolean("expanded", false)
                terminal += entryObject.optBoolean("terminal", true)
                outcomes += runCatching { ReportLog.Outcome.valueOf(entryObject.getString("outcome")) }
                    .getOrDefault(ReportLog.Outcome.FAIL)
            }
            val lastText = if (root.isNull("last_text")) null else root.optString("last_text").ifEmpty { null }
            // D59: the cap is re-enforced on load, not just on append —
            // keep the newest MAX_ENTRIES, oldest-first (same shape as
            // ReportLog.append's own eviction).
            val overflow = entries.size - ReportLog.MAX_ENTRIES
            if (overflow > 0) {
                repeat(overflow) {
                    entries.removeAt(0)
                    expanded.removeAt(0)
                    terminal.removeAt(0)
                    outcomes.removeAt(0)
                }
            }
            Snapshot(entries, expanded, terminal, outcomes, lastText)
        } catch (e: Exception) {
            Log.w(TAG, "M2 stage: persisted log file is corrupt/unparseable — starting with an empty log (${e.javaClass.simpleName}: ${e.message})")
            Snapshot.EMPTY
        }
    }
}
