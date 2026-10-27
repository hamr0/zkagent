package com.tananaev.passportreader

import java.security.MessageDigest

/**
 * M3b chunk 4 item 18 + finding #48 -- link-once: the pure decision whether an
 * incoming `av://` VIEW link must be handled, or is a REPLAY of one this app
 * already handled.
 *
 * Why a persisted record and not a Bundle flag or `savedInstanceState == null`
 * (finding #48, device-observed): `adb install -r` kills the process
 * ("killDueToPackageUpdate") and Android restarts the activity with its LAST
 * launch intent and NO saved state. `onSaveInstanceState` does not save the
 * handoff either, so a null-bundle guard would drop a legitimate process-death
 * resume while a Bundle flag would not exist after a package update. The
 * record therefore lives in app-private SharedPreferences ([serialize] /
 * [deserialize]) and survives process death.
 *
 * Two facts per link key: a status and "seen by THIS process".
 *  - [Status.TERMINAL]: the handoff reached an end (refused, delivered,
 *    expired, walked away, superseded, malformed Snackbar shown). A replay is
 *    ignored, in every process.
 *  - [Status.IN_FLIGHT]: captured, not yet at an end. Seen by THIS process
 *    (an Activity recreation) -> [Verdict.RESTORE] from [InFlightHandoff] when it
 *    still holds the entry (the check survives, no second capture, criterion 3),
 *    else handled again. Not seen
 *    by this process (process death restore) -> handled again, so the flow
 *    resumes (criterion 4). The seen-set is in memory only and deliberately
 *    not serialised, which is exactly what distinguishes the two.
 *
 * Only an `onCreate` replay is deduplicated. [Source.NEW_INTENT] is a genuine
 * fresh delivery by the framework (a tap), never a replay, so it is always
 * handled and re-arms the key as IN_FLIGHT.
 *
 * Privacy: only the SHA-256 hex of the WHOLE link string is kept (the
 * malformed case has no request_uri, so the whole string is the one key that
 * covers both), never the link, origin or request_uri. The list is capped
 * ([maxEntries], default 50, oldest dropped): a hash of a link whose
 * verifier already controls the link carries no secret, and an evicted key
 * only means a replay of a link older than 50 later links is handled once
 * more, which is the pre-fix behaviour, not a new risk.
 */
class LinkOnce(private val maxEntries: Int = DEFAULT_MAX_ENTRIES) {

    enum class Status { IN_FLIGHT, TERMINAL }
    enum class Source { CREATE, NEW_INTENT }
    /** HANDLE = run the normal capture path; IGNORE = drop; RESTORE = same-process recreation of an
     * in-flight handoff whose state is still in the process-scoped [InFlightHandoff]: re-show it, no second capture. */
    enum class Verdict { HANDLE, IGNORE, RESTORE }

    // insertion order == age order; re-recording a key moves it to the newest end.
    private val statuses = LinkedHashMap<String, Status>()
    private val seenThisProcess = HashSet<String>()

    /** Decides AND records. A null [link] (no data) is keyed as the empty string, handled once. */
    @Synchronized
    fun decide(link: String?, source: Source, holderHasEntry: Boolean = false): Verdict {
        val key = keyOf(link ?: "")
        if (source == Source.CREATE) {
            when (statuses[key]) {
                Status.TERMINAL -> return Verdict.IGNORE
                // Seen by this process = a recreation. With the holder entry: restore the
                // user's check. WITHOUT it (the "holder empty but seen" edge) fall through
                // to HANDLE, so the user is never left idle with the link silently dropped.
                Status.IN_FLIGHT -> if (key in seenThisProcess && holderHasEntry) return Verdict.RESTORE
                null -> Unit
            }
        }
        record(key, Status.IN_FLIGHT)
        seenThisProcess.add(key)
        return Verdict.HANDLE
    }

    /** Marks [link] as having reached an end. No-op for a null link or one never recorded. */
    @Synchronized
    fun markTerminal(link: String?) {
        if (link == null) return
        val key = keyOf(link)
        if (key in statuses) record(key, Status.TERMINAL)
    }

    @Synchronized
    fun statusOf(link: String): Status? = statuses[keyOf(link)]

    @Synchronized
    fun size(): Int = statuses.size

    private fun record(key: String, status: Status) {
        statuses.remove(key)
        statuses[key] = status
        while (statuses.size > maxEntries) {
            val oldest = statuses.keys.first()
            statuses.remove(oldest)
            seenThisProcess.remove(oldest)
        }
    }

    /** One `<hex>,<I|T>` line per key, oldest first. Plain text: org.json is a stub under the JVM tests. */
    @Synchronized
    fun serialize(): String = statuses.entries.joinToString("\n") {
        it.key + "," + (if (it.value == Status.TERMINAL) "T" else "I")
    }

    companion object {
        const val DEFAULT_MAX_ENTRIES = 50

        fun keyOf(link: String): String =
            MessageDigest.getInstance("SHA-256").digest(link.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }

        /** A fresh instance (empty seen-set, as after a process restart). Corrupt lines are skipped. */
        fun deserialize(text: String?, maxEntries: Int = DEFAULT_MAX_ENTRIES): LinkOnce {
            val out = LinkOnce(maxEntries)
            if (text.isNullOrEmpty()) return out
            for (line in text.split('\n')) {
                val parts = line.split(',')
                if (parts.size != 2 || parts[0].length != 64) continue
                val status = when (parts[1]) {
                    "T" -> Status.TERMINAL
                    "I" -> Status.IN_FLIGHT
                    else -> continue
                }
                out.record(parts[0], status)
            }
            return out
        }
    }
}
