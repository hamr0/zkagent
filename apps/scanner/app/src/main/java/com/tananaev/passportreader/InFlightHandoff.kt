package com.tananaev.passportreader

/**
 * Item 18, owner ruling: the process-scoped holder that lets an in-flight handoff survive an Activity
 * RECREATION (same process). One slot, in memory only, never persisted (process death empties it and
 * [LinkOnce] then resumes by re-handling the link).
 *
 * Holds ONLY handoff/request data ([pending], [verified]) -- never MRZ, document fields, lock state or
 * chip-read state (item 6). A recreation that hit a lock or a chip read therefore comes back at the
 * pre-lock "question + Verify" state; the user re-enters the document fields and rescans.
 *
 * [takenOver] marks that a recreated instance re-ran the verification itself, so the dead instance's
 * late async result must be dropped (no duplicate Log entry / second outcome).
 */
class InFlightHandoff<P : Any, V : Any> {
    private var key: String? = null
    private var pending: P? = null
    private var verified: V? = null
    private var takenOver = false

    data class Snapshot<P, V>(val pending: P, val verified: V?)

    @Synchronized fun begin(linkKey: String?, p: P) {
        key = linkKey; pending = p; verified = null; takenOver = false
    }

    @Synchronized fun setVerified(linkKey: String?, v: V) {
        if (linkKey != null && linkKey == key) verified = v
    }

    @Synchronized fun has(linkKey: String): Boolean = key == linkKey && pending != null

    @Synchronized fun snapshot(linkKey: String): Snapshot<P, V>? {
        val p = pending
        return if (key == linkKey && p != null) Snapshot(p, verified) else null
    }

    @Synchronized fun markTakenOver(linkKey: String) { if (key == linkKey) takenOver = true }

    @Synchronized fun isTakenOver(linkKey: String?): Boolean = linkKey != null && key == linkKey && takenOver

    /** Terminal end of [linkKey]: a stale request can never resurface. */
    @Synchronized fun clear(linkKey: String?) {
        if (linkKey != null && linkKey == key) { key = null; pending = null; verified = null; takenOver = false }
    }
}
