package com.tananaev.passportreader

import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.AbsoluteSizeSpan
import android.text.style.ClickableSpan
import android.text.style.ForegroundColorSpan
import android.view.View
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * §6.2 item 16 — the per-scan report log accumulator.
 *
 * Append/clear/storage semantics are pure and Android-free, unit-testable
 * without an emulator (`ReportLogTest`). [rendered] is the one exception
 * (2026-09-01, second real-device fix, title-line sizing) — it may return
 * an `android.text.SpannableStringBuilder`, but only when given a size to
 * style with; its pure computation ([titleLineRanges]) stays fully
 * testable regardless. [MainActivity] is the ONLY caller: it is wired in
 * as an ADDITIONAL CONSUMER of the single [MainActivity.emitReport] write
 * path — never a second write site (see that function's doc for why an
 * unlogged write once made a completed run look like a hang).
 *
 * In-memory only (D44): nothing here ever touches disk. [MainActivity] owns
 * restoring/saving entries across Activity recreation (D35) via its own
 * `onSaveInstanceState` Bundle (`restore`/`entriesSnapshot`).
 *
 * **D45 (2026-09-01):** this accumulator's lifetime is DECOUPLED from
 * `MainActivity.wipeSession()`'s per-scan `!keepMrzAndMode` branch — a
 * per-scan session wipe, successful or not, MUST NOT call [clear]. Entries
 * accumulate for the life of the app session; [clear] exists only for a
 * caller that intentionally wants to empty the log (currently: never called
 * in production — retained as the one clearing primitive so the class stays
 * testable and so a future, explicitly-scoped clearing trigger, if one is
 * ever added, has exactly one place to call). The log is gone only when the
 * app process is gone.
 *
 * The timestamp is display-only (contrast D28's `current_date`, a payload
 * field) — it is formatted once, here, at append time, from a caller-
 * supplied local wall-clock value, and never enters proof/evidence
 * derivation.
 *
 * **D46 (2026-09-01):** each entry is no longer the raw report string with a
 * timestamp prefix — it is titled by site ([DisclosureSummary.site]) and
 * leads with a plain-language, non-engineer-legible disclosure summary
 * (Result/Sent/Shared/Identity), with the existing engineering report text
 * ([append]'s `text` — unmodified, byte-identical to what `emitReport`
 * writes to `reportView`) subordinated under a `▸ technical:` block so no
 * debugging detail is dropped, only demoted. Item 5's value-free constraint
 * is unchanged and binding for BOTH the summary and the technical block: no
 * MRZ, names, document fields, key bytes, signatures, nonces, fingerprints,
 * or chain contents. The site/origin name is explicitly not a document
 * field and is safe to show (D46).
 *
 * **D48/Q34 reshape (2026-09-01):** `Shared` is a LIST of predicate→answer
 * pairs ([DisclosureSummary.Claim]), not one formatted string — see
 * [DisclosureSummary.Shared]'s doc. Today the list holds exactly one claim
 * (the age predicate) or is empty; a future claim (document-expiry bucket,
 * etc.) is Q34, open and unbuilt — this reshape only makes room for it, it
 * does not add one.
 *
 * **2026-09-01 real-device fixes (owner's Pixel 6a run, both documents):**
 * [rendered] now shows the NEWEST entry first (storage order is unchanged —
 * see [entriesSnapshot]/[restore]); and [append]'s `attemptId`/`pending`
 * parameters let a mint attempt's terminal outcome REPLACE its own
 * "In progress" entry instead of appending a second one, so one scan
 * attempt never leaves more than one entry — see [append]'s doc.
 */
class ReportLog {

    /**
     * The plain-language, value-free disclosure summary for one log entry
     * (D46). Built by [MainActivity] at the same call site that already
     * calls `emitReport` — never derived here, so [ReportLog] stays pure
     * and Android-free.
     *
     * @param site the verified request origin's `host:port` (`scope_domain`,
     *   D37/D42), or the fixed label `"Local scan (no site)"` for a bare
     *   scan with no verified handoff — including a handoff whose
     *   verification itself failed (an unverified origin is never shown as
     *   though it were trusted).
     * @param result one line: what happened, in plain language — must be
     *   accurate per outcome (never claims success on a failure path, never
     *   claims something was sent when it wasn't).
     * @param chipAuthenticity (2026-09 real-device fix) the plain-language
     *   THREE-state chip-authenticity finding for this scan — verified /
     *   not supported by this document / failed — see
     *   [M0Probe.ChipAuthStatus]'s doc for why "not supported" must never
     *   read as "false". Null when no chip read completed for this entry
     *   (any read failure — the chip-auth probe may not have run at all).
     * @param sent what left the device, in plain language (e.g. "nothing
     *   left this device", or "a site-only pseudonym + a signed claim").
     * @param shared what was disclosed to the site — see [Shared]. Either
     *   [Shared.Disclosed] (one or more predicate→answer [Claim]s, rendered
     *   one per line, followed by the fixed negation line) or
     *   [Shared.NotDisclosed] (a plain-language reason nothing was shared —
     *   MUST be used on every non-disclosing path: mode A, an unmet mint
     *   gate, a refusal, or any failure).
     * @param identity only set for a mode-B mint: restates the existing
     *   `device_key: created this mint | reused existing alias` fact
     *   (D38/D39) in plain language — the two owner-confirmed strings
     *   (D47/D48) are `"new — minted fresh for this site"` and `"known —
     *   recognized only here from previous visit"` ("only here" is
     *   load-bearing: the plain-language statement of D38/D39's per-
     *   (origin,zktag) key isolation, MUST NOT be simplified out). Null
     *   when no key/identity fact applies (a read-only or refused outcome).
     * @param technicalNote (D24, D48) an optional short, factual, one-clause
     *   addition to the `▸ technical:` block — NOT the plain-language block
     *   — stating that the disclosed claim is self-asserted by the device
     *   and not independently proven by a cryptographic circuit. D24
     *   requires this be stated in every claim; set only where a `claim`
     *   was actually built and signed (this app's mode-B mint path — mode A
     *   never builds one, D27).
     */
    data class DisclosureSummary(
        val site: String,
        val result: String,
        val sent: String,
        val shared: Shared,
        val identity: String? = null,
        val technicalNote: String? = null,
        // 2026-09 real-device fix, owner decision ("Mode is redundant" —
        // see MainActivity's comment where modeLabel() used to live): a
        // `mode` field WAS here; removed because Sent/Shared/Identity
        // already state everything mode A/B means in plain language. Mode
        // still appears in the `▸ technical:` line (unchanged) — this
        // field controlled only the now-deleted plain-block line. Do not
        // re-add it without a fresh owner decision.
        val chipAuthenticity: String? = null,
    ) {
        /** One disclosed predicate and the actual asserted answer for THIS
         * scan — e.g. `Claim("age > 18", "true")`. [answer] is always
         * rendered as the literal boolean text (`true`/`false`), never
         * translated to `yes`/`no` (owner: "true/false always"). Both
         * fields MUST be sourced from the actual signed claim data, never
         * hardcoded — see the call site in `MainActivity.mintAndMaybeHandoff`
         * for why the signed `claim` map is the source of truth today. */
        data class Claim(val predicate: String, val answer: String)

        /** D48/Q34: what, if anything, was disclosed to the site.
         * [Disclosed] and [NotDisclosed] are mutually exclusive by
         * construction — there is no way to construct a state that is both
         * "claims were shared" and "nothing was shared", which is exactly
         * the outcome-accuracy invariant D47/D48 require. */
        sealed class Shared {
            /** [claims] is non-empty in every real caller — today exactly
             * one element (the age predicate); more than one is Q34,
             * open and unbuilt. Rendered one per line, in order, followed
             * by the fixed negation line. */
            data class Disclosed(val claims: List<Claim>) : Shared()

            /** [text] is the plain-language reason nothing left the device
             * / nothing was disclosed for this outcome (e.g. `"nothing"`,
             * `"nothing the site kept — it rejected the response"`) — never
             * a claim, never a colon-suffixed predicate. */
            data class NotDisclosed(val text: String) : Shared()
        }
    }

    // Stored OLDEST FIRST, unconditionally — append order, never reordered.
    // [rendered] is the ONLY place storage order and display order differ
    // (see its doc, the 2026-09-01 real-device fix: newest-first display).
    private val entries = mutableListOf<String>()

    // §6.2 item 18 (D67, Q43) — PARALLEL to [entries], same index space,
    // kept in lockstep by every mutation site below (append/replace/evict/
    // clear/restore). Per the standing "pure logic in pure classes" rule,
    // this class — not MainActivity, not a View — is the single owner of
    // the collapsed/expanded display state; MainActivity only renders
    // whatever [rendered] returns.
    //
    // Collapsed (`false`) by default for every newly-added entry; only
    // [toggleExpandedAtDisplayIndex] flips a flag. Content is NEVER altered
    // by this flag (item 18's own MUST) — only whether [rendered] shows an
    // entry's full block or just its title line.
    private val expandedFlags = mutableListOf<Boolean>()

    // §6.2 item 19 (D67, Q44) — PARALLEL to [entries]/[expandedFlags], same
    // index space, kept in lockstep the same way. Derived from the SAME
    // pending/terminal model [append]'s `attemptId`/`pending` already
    // tracks (never a new flag guessed from string content): an entry is
    // terminal (dimmed) whenever it is NOT the currently-open "In
    // progress" entry for some attempt. `pending = true` -> not terminal;
    // every other append/replace -> terminal. This reuses the existing,
    // already-correct pending model rather than re-deriving "done vs. in
    // progress" from FailureTransition/MintConfirmation's enums directly —
    // those enums decide WHICH terminal outcome a report describes, never
    // WHETHER one has been reached yet; [pending] at this class's own call
    // site is the exact fact item 19 needs, already threaded through
    // unchanged.
    private val terminalFlags = mutableListOf<Boolean>()

    /** D58 step 1 (Report/Log cluster, finding #7): the exact text of the
     * most recently emitted or restored report — this class is now the
     * SINGLE owner of this value, absorbing what used to be
     * `MainActivity.lastReportText`, a field written at two independent
     * sites (`emitReport` and the `onCreate` restore branch) that
     * [emitReport]'s own KDoc claimed, incorrectly, was the only one. Null
     * before any report has ever been appended or restored (fresh app
     * launch, or a restore payload that carried no report text). Set by
     * [append] (always, to its `text` argument, regardless of the
     * append-vs-replace-pending branch below) and by [restore] (to its
     * `lastText` argument) — no other function may write it. */
    var lastText: String? = null
        private set

    // §6.2 item 16 (2026-09-01 real-device fix, "the stale in-progress
    // entry"): which stored index (if any) currently holds an unresolved
    // in-progress entry for a given attempt id. A scan's mint pipeline
    // emits an "In progress" entry once biometric authorization is
    // requested, then EXACTLY ONE terminal outcome later — without this,
    // that terminal outcome appended a SECOND entry and the first entry
    // (with the app's original "In progress" text on Every scan) was left
    // dangling forever. Keyed by an opaque attempt id [MainActivity]
    // generates once per mint attempt and threads through every emitReport
    // call for that attempt — never derived here.
    private val pendingIndexByAttempt = mutableMapOf<String, Int>()

    /** Appends (or, if [attemptId] matches a still-open in-progress entry,
     * REPLACES) one rendered log entry — title line (timestamp + site),
     * plain-language [summary], then the existing [text] subordinated under
     * a `▸ technical:` block. [text] is stored verbatim (D46 does not
     * change engineering-report content, only adds the summary above it).
     *
     * @param attemptId identifies the scan/mint attempt this entry belongs
     *   to. Null for anything that is never part of a pending/terminal
     *   pair (a debug probe, a refusal that happens before any "In
     *   progress" entry was ever shown, etc.) — such calls always append.
     * @param pending true ONLY for the one "In progress" entry a mint
     *   attempt emits while waiting on biometric authorization. Every
     *   OTHER call for the SAME [attemptId] (there is exactly one — the
     *   attempt's eventual terminal outcome, whichever of the several
     *   possible endings it turns out to be) passes [pending] = false and
     *   REPLACES that entry in place, rather than appending a second one.
     *   An attempt that never resolves (app backgrounded/killed mid-scan)
     *   correctly leaves its "In progress" entry showing — nothing here
     *   ever removes an entry, only replaces one already known to be
     *   superseded.
     * @param nowMillis defaults to the real clock; a test may pass a fixed
     *   value so the timestamp is deterministic without sleeping. */
    fun append(text: String, summary: DisclosureSummary, attemptId: String? = null, pending: Boolean = false, nowMillis: Long = System.currentTimeMillis()) {
        lastText = text
        val timestamp = TIMESTAMP_FORMAT.format(Date(nowMillis))
        val rendered = renderEntry(timestamp, summary, text)
        val existingIndex = attemptId?.let { pendingIndexByAttempt[it] }
        if (existingIndex != null) {
            entries[existingIndex] = rendered
            pendingIndexByAttempt.remove(attemptId)
            // item 18: a replaced entry keeps its OWN prior [expandedFlags]
            // state (a user who opened an "In progress" entry to watch it
            // is not collapsed out from under them when it resolves — see
            // class doc); [terminalFlags] is recomputed below from THIS
            // call's [pending], same as the append branch.
        } else {
            entries.add(rendered)
            expandedFlags.add(false) // item 18: collapsed by default
            terminalFlags.add(false) // placeholder — the one true value is set below, in one place
            // D58 step 1 (finding #13): a genuinely NEW entry (never a
            // pending-replace, which never grows the list) can push
            // [entries] past [MAX_ENTRIES] — evict the single oldest entry
            // to hold the bound, oldest-first, matching [rendered]'s own
            // "storage order is append order" invariant (only the display
            // order is ever reversed). Every remaining pending index must
            // shift down by one to keep pointing at the SAME logical entry
            // it pointed at before the eviction; a pending index that
            // pointed at the now-evicted slot 0 is dropped — its own
            // eventual terminal outcome (finding #5's late Thread{}
            // landing is exactly this case) then appends fresh rather than
            // silently overwriting whatever unrelated entry now occupies
            // index 0.
            if (entries.size > MAX_ENTRIES) {
                entries.removeAt(0)
                expandedFlags.removeAt(0)
                terminalFlags.removeAt(0)
                val iterator = pendingIndexByAttempt.entries.iterator()
                while (iterator.hasNext()) {
                    val pendingEntry = iterator.next()
                    if (pendingEntry.value == 0) iterator.remove() else pendingEntry.setValue(pendingEntry.value - 1)
                }
            }
        }
        val finalIndex = existingIndex ?: entries.lastIndex
        // item 19: terminal iff this entry is NOT the currently-open
        // "In progress" entry — the exact fact [pending] already carries.
        terminalFlags[finalIndex] = !pending
        if (pending && attemptId != null) {
            pendingIndexByAttempt[attemptId] = finalIndex
        }
    }

    /** Empties the log. Not called anywhere in production as of D45 — a
     * per-scan session wipe MUST NOT call this (see class doc) — kept as
     * the single clearing primitive for tests and any future, explicitly-
     * scoped trigger. Does NOT touch [lastText] — [lastText] mirrors the
     * report view, which D45/`wipeSession` also never clears; only an
     * explicit [restore] with a null `lastText` argument (or a fresh
     * instance) ever nulls it. */
    fun clear() {
        entries.clear()
        pendingIndexByAttempt.clear()
        expandedFlags.clear()
        terminalFlags.clear()
    }

    /** Immutable snapshot, OLDEST first — [MainActivity]'s
     * `onSaveInstanceState` (D35) storage shape and for tests. Deliberately
     * NOT the display order [rendered] uses — see that function's doc. */
    fun entriesSnapshot(): List<String> = entries.toList()

    /** §6.2 item 18 (D67, Q43): OLDEST-first, same index space as
     * [entriesSnapshot] — [MainActivity]'s `onSaveInstanceState` sibling
     * persistence for the per-entry expand/collapse toggle, so it survives
     * Activity recreation exactly like [entriesSnapshot] already does. */
    fun expandedSnapshot(): List<Boolean> = expandedFlags.toList()

    /** §6.2 item 19 (D67, Q44): OLDEST-first, same index space as
     * [entriesSnapshot] — persisted the same way, though in practice every
     * RESTORED entry is terminal (see [restore]'s doc: no in-progress
     * attempt-id state survives recreation either). */
    fun terminalSnapshot(): List<Boolean> = terminalFlags.toList()

    /** §6.2 item 18 (D67, Q43) — the per-entry tap-to-expand toggle.
     * [displayIndex] is in DISPLAY order (0 = newest, matching what
     * [rendered] shows and what a tap callback naturally reports — see
     * [MainActivity]'s log-view click wiring), converted to storage order
     * here so [MainActivity] never has to reason about the newest-first/
     * oldest-first split itself. Silently ignored if out of range (a stale
     * tap racing a concurrent restore/clear), never throws — the same
     * defensive posture as every other public entry point on this class. */
    fun toggleExpandedAtDisplayIndex(displayIndex: Int) {
        val storageIndex = entries.lastIndex - displayIndex
        if (storageIndex in expandedFlags.indices) {
            expandedFlags[storageIndex] = !expandedFlags[storageIndex]
        }
    }

    /** Replaces the current entries with [saved] verbatim (already fully
     * rendered by a prior [append], oldest first — the SAME order
     * [entriesSnapshot] returns) — used to restore across Activity
     * recreation (D35). Never re-renders or re-timestamps. Any in-progress
     * pending tracking is dropped: [MainActivity]'s own attempt-id locals
     * do not survive recreation either, so nothing could ever reference a
     * stale pending index after this call.
     *
     * @param lastText (D58 step 1) the report text to restore [lastText]
     *   to — the sibling half of this same restore, now owned here rather
     *   than split across a second `MainActivity` field. Defaults to null:
     *   an ordinary restore that supplies only [saved] (no report text —
     *   e.g. a test pinning entry-restore behaviour in isolation) leaves
     *   [lastText] null, never re-derived from [saved]'s content.
     * @param expanded (item 18) per-entry collapsed/expanded state, SAME
     *   order as [saved]. Defaults to null, meaning "collapse everything" —
     *   `List(saved.size) { false }` — matching a caller (or a test) that
     *   restores entries without ever having persisted this sibling state.
     *   A caller supplying a non-null list MUST size it to match [saved];
     *   a mismatched size falls back to the same all-collapsed default
     *   rather than indexing out of bounds.
     * @param terminal (item 19) per-entry terminal/in-progress state, SAME
     *   order as [saved]. Defaults to null, meaning "everything terminal" —
     *   `List(saved.size) { true }` — since no in-progress attempt-id state
     *   survives Activity recreation (see this function's own doc on
     *   [pendingIndexByAttempt] being dropped): a restored entry can never
     *   correctly be "still in progress" from a NEW instance's point of
     *   view, even if it was mid-flight in the dying one. Same mismatched-
     *   size fallback as [expanded]. */
    fun restore(saved: List<String>, lastText: String? = null, expanded: List<Boolean>? = null, terminal: List<Boolean>? = null) {
        entries.clear()
        entries.addAll(saved)
        pendingIndexByAttempt.clear()
        this.lastText = lastText
        expandedFlags.clear()
        expandedFlags.addAll(expanded?.takeIf { it.size == saved.size } ?: List(saved.size) { false })
        terminalFlags.clear()
        terminalFlags.addAll(terminal?.takeIf { it.size == saved.size } ?: List(saved.size) { true })
    }

    /** The log view's full text, NEWEST entry first (2026-09-01 real-device
     * fix — the owner's own device run made a bottom-of-a-long-list newest
     * entry hard to find). Storage order ([entriesSnapshot]/[restore]) is
     * unaffected — this reverses only at render time. Empty (an empty
     * `String`, still a valid `CharSequence`) when there are no entries.
     *
     * @param titleSizePx (2026-09-01, second real-device fix) when
     *   non-null, each entry's title line (`HH:mm:ss · <site>`) is styled
     *   ONE POINT LARGER via [AbsoluteSizeSpan] at this exact pixel size —
     *   [MainActivity] computes it from the log view's OWN configured text
     *   size plus one sp, never a hardcoded number. Body lines (Result/
     *   Sent/Shared/Identity/Chip auth, the `▸ technical:` block) are
     *   NEVER styled. Spans, not `HtmlCompat.fromHtml`: the title line
     *   carries a site-derived value (an externally-influenced string),
     *   and routing that through an HTML parser to style it would be a
     *   markup-injection surface for no reason — a span applies by
     *   character range, with no parsing step, so there is nothing to
     *   inject into. Null (the default) skips styling entirely and
     *   returns a plain `String` — this is what every test in
     *   `ReportLogTest` uses to assert on rendered CONTENT; the styling
     *   itself is pinned separately, against the pure [titleLineRanges]
     *   this method also uses (real device/manual confirmation still owns
     *   whether it actually LOOKS right — `SpannableStringBuilder` is a
     *   stub with no real behavior under this module's plain-JVM unit-test
     *   sandbox, `unitTests.isReturnDefaultValues`, so no unit test here
     *   can observe the styled `CharSequence` itself).
     * @param onEntryTap (item 18, D67/Q43) when non-null, each entry's
     *   title line becomes a [ClickableSpan] that invokes this callback
     *   with the entry's DISPLAY index (0 = newest, matching this
     *   function's own newest-first order) — [MainActivity] wires this to
     *   [toggleExpandedAtDisplayIndex] plus a re-render, never mutating
     *   state here. Requires the caller's `TextView.movementMethod` be set
     *   to a link-aware one for the span to actually receive taps — this
     *   function only places the span, it does not configure the View.
     * @param dimmedTextColor (item 19, D67/Q44) when non-null, every entry
     *   whose [terminalFlags] is true is styled in this exact ARGB color
     *   via [ForegroundColorSpan] over its WHOLE displayed range (title
     *   line included, whether collapsed or expanded) — [MainActivity]
     *   computes it from the log view's OWN currently-configured text
     *   color, alpha-reduced, never a hardcoded color, the same discipline
     *   [titleSizePx] already follows for size. An entry still open as
     *   "In progress" for some attempt is never dimmed. */
    fun rendered(titleSizePx: Int? = null, onEntryTap: ((Int) -> Unit)? = null, dimmedTextColor: Int? = null): CharSequence {
        val storageIndicesNewestFirst = entries.indices.reversed().toList()
        val displayTexts = storageIndicesNewestFirst.map { displayText(it) }
        val joined = displayTexts.joinToString("\n\n")
        if (titleSizePx == null && onEntryTap == null && dimmedTextColor == null) return joined
        val builder = SpannableStringBuilder(joined)
        val titleRanges = titleLineRanges(displayTexts)
        if (titleSizePx != null) {
            titleRanges.forEach { range ->
                if (!range.isEmpty()) {
                    builder.setSpan(AbsoluteSizeSpan(titleSizePx, false), range.first, range.last + 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
            }
        }
        if (onEntryTap != null) {
            titleRanges.forEachIndexed { displayIndex, range ->
                if (!range.isEmpty()) {
                    builder.setSpan(object : ClickableSpan() {
                        override fun onClick(widget: View) {
                            onEntryTap(displayIndex)
                        }
                    }, range.first, range.last + 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
            }
        }
        if (dimmedTextColor != null) {
            var offset = 0
            displayTexts.forEachIndexed { displayIndex, text ->
                if (displayIndex > 0) offset += 2 // the "\n\n" separator
                val storageIndex = storageIndicesNewestFirst[displayIndex]
                if (terminalFlags.getOrElse(storageIndex) { true } && text.isNotEmpty()) {
                    builder.setSpan(ForegroundColorSpan(dimmedTextColor), offset, offset + text.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
                offset += text.length
            }
        }
        return builder
    }

    /** item 18: the exact CharSequence content [rendered] shows for one
     * stored entry at [storageIndex] — full, unmodified block when
     * [expandedFlags] is true; otherwise just the title line (the same
     * substring [titleLineRanges] would compute for the un-collapsed
     * entry), plus a fixed "▸" affordance so a collapsed entry visibly
     * invites the tap [onEntryTap] wires up. Item 18's "MUST NOT change the
     * block's content" is about [entries]/[entriesSnapshot] — the STORED,
     * persisted content — which this function never touches; it only
     * decides what [rendered] projects from it. */
    private fun displayText(storageIndex: Int): String {
        val full = entries[storageIndex]
        if (expandedFlags.getOrElse(storageIndex) { false }) return full
        val titleLine = full.substringBefore('\n')
        return "$titleLine  ▸"
    }

    companion object {
        /** D58 step 1 (finding #13, FIX not enhancement — see the step's
         * report, Challenge C) opened this bound on [entries]' size as
         * PROVISIONAL at 200; **D59 (2026-09-02)** sets the owner-approved
         * final value, **20**, and changes the deciding rationale: the cap
         * counts ENTRIES, not lines — one entry is a whole scan-outcome
         * block of roughly 20 rendered lines (title + Result/Sent/Shared/
         * Identity/Chip-auth + a multi-line `▸ technical:` block) — and a
         * 200-entry scroll is unusable for a human reading the log, which
         * is the DECIDING reason for 20. The persisted-Bundle-size argument
         * that originally motivated a bound at all still holds and is now
         * far more comfortable, but is a secondary, no-longer-binding
         * consideration: `onSaveInstanceState` puts the whole snapshot into
         * the outgoing `Bundle`, and a `Bundle` that grows past roughly 1MB
         * risks `TransactionTooLargeException` at the next save — this is a
         * real, externally-triggerable crash (a hostile app looping refused
         * `av://` intents at a locked session used to append one entry per
         * intent before 26f67ac, and any other high-frequency append path
         * could do the same). A rendered entry runs roughly 400-900 bytes
         * for the realistic content this class actually renders (see
         * `ReportLogTest`'s own fixtures) — 20 entries is on the order of
         * 8-18KB, a wide margin under the ~1MB transaction ceiling even
         * before accounting for the rest of the Bundle's contents. The
         * eviction mechanism (oldest-first, in [append]) is what this step
         * actually delivers, and holds for any value here. */
        const val MAX_ENTRIES = 20

        private val TIMESTAMP_FORMAT = SimpleDateFormat("HH:mm:ss", Locale.US)

        /** Pure: the character range of each entry's title line (the
         * first line, up to but excluding its terminating `\n`) within
         * the text [rendered] joins [entriesNewestFirst] into (display
         * order, newest first, "\n\n"-separated). NOT private — exposed
         * so `ReportLogTest` can pin this computation directly, without
         * going through Android's `SpannableStringBuilder` (whose
         * behavior isn't observable under this module's plain-JVM
         * unit-test stub — see [rendered]'s doc). [rendered] is the only
         * production caller. */
        fun titleLineRanges(entriesNewestFirst: List<String>): List<IntRange> {
            val ranges = mutableListOf<IntRange>()
            var offset = 0
            entriesNewestFirst.forEachIndexed { index, entry ->
                if (index > 0) offset += 2 // the "\n\n" separator joinToString inserts between entries
                val titleLength = entry.indexOf('\n').let { if (it == -1) entry.length else it }
                ranges += offset until (offset + titleLength)
                offset += entry.length
            }
            return ranges
        }

        // §6.2 item 16 (D46/D48): the fixed negation line, rendered ONLY
        // after a non-empty disclosed-claims list — an empty (NotDisclosed)
        // Shared never gets this line, since nothing was shared to negate
        // against in the first place.
        private const val NEGATION_LINE = "Not your name, date of birth, document number, or nationality."

        // Left-padding for every continuation line under a label, so
        // multi-line Shared content (D48/Q34: one or more claims, plus the
        // negation line) stays aligned under "Shared    " the same way the
        // title/label columns already align.
        private const val CONTINUATION_INDENT = "          "

        private fun renderEntry(timestamp: String, summary: DisclosureSummary, text: String): String {
            val lines = mutableListOf<String>()
            lines += "$timestamp · ${summary.site}"
            lines += ""
            lines += "Result    ${summary.result}"
            // 2026-09 real-device fix: Chip auth is a property of THIS
            // SCAN (independent of what was, or wasn't, sent to a site) —
            // placed right after Result, before the disclosure-shaped
            // lines (Sent/Shared/Identity). Null on any entry where it was
            // never determined (see DisclosureSummary's doc) — omitted
            // entirely, never rendered as an empty label or a guessed
            // value. (A `Mode` line was here too; owner decision removed
            // it as redundant with Sent/Shared/Identity — see
            // DisclosureSummary's doc.)
            summary.chipAuthenticity?.let { lines += "Chip auth $it" }
            lines += "Sent      ${summary.sent}"
            when (val shared = summary.shared) {
                is DisclosureSummary.Shared.Disclosed -> {
                    // D48/Q34: one line per claim, "Shared    " on the
                    // first, indented continuation on the rest — never a
                    // stray empty label, never a colon with nothing after
                    // it (that shape is reserved for a real claim).
                    check(shared.claims.isNotEmpty()) { "Shared.Disclosed must carry at least one claim — an empty disclosure is Shared.NotDisclosed" }
                    shared.claims.forEachIndexed { index, claim ->
                        val prefix = if (index == 0) "Shared    " else CONTINUATION_INDENT
                        lines += "$prefix${claim.predicate}: ${claim.answer}"
                    }
                    lines += "$CONTINUATION_INDENT$NEGATION_LINE"
                }
                is DisclosureSummary.Shared.NotDisclosed -> lines += "Shared    ${shared.text}"
            }
            summary.identity?.let { lines += "Identity  $it" }
            lines += ""
            // §6.2 item 24 (D70(c)) — the build stamp lives on the
            // technical block's own header line, not a new label: it is
            // diagnostic metadata about the running build, not a fact
            // about this scan. BuildConfig.GIT_SHA is computed once, in
            // build.gradle.kts — VersionStamp.format is the one place its
            // string shape is decided (see that class's doc).
            lines += "▸ technical: · build ${VersionStamp.format(BuildConfig.VERSION_NAME, BuildConfig.GIT_SHA)}"
            text.lines().forEach { lines += "  $it" }
            // §6.2 item 16 (D24, D48): the bare-claim disclosure, subordinate
            // to the technical block, never the plain-language one.
            summary.technicalNote?.let { lines += "  $it" }
            return lines.joinToString("\n")
        }
    }
}
