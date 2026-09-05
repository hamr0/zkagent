package com.tananaev.passportreader

/**
 * §6.2 (D55): which of the three overlapping panes ([Pane.SCAN], [Pane.LOG],
 * [Pane.LOADING]) should be visible, extracted to a pure, Android-free
 * object — same pattern as [FailureTransition]/[MintGate].
 *
 * WHY THIS EXISTS AS A PURE OBJECT, NOT INLINE IN [MainActivity]: this
 * module runs with `unitTests.isReturnDefaultValues = true`, under which
 * `View.visibility` is a non-functional stub (same limitation already
 * documented for `SpannableStringBuilder` / the log-title styling code) —
 * a visibility invariant is NOT directly assertable against real views in
 * this suite. Extracting the DECISION into a function of
 * (readInProgress, selectedTabPosition) that returns a plain enum is what
 * makes it testable at all; [MainActivity.showPane] becomes a thin
 * applier that is not itself unit-tested.
 *
 * ROOT CAUSE this replaces (2026-09, real-device bug, D55):
 * `activity_main.xml` places `loading_layout`, `main_layout` and
 * `log_layout` as overlapping siblings inside one `FrameLayout`, in that
 * XML order — so `log_layout` draws ON TOP of `main_layout` whenever both
 * are VISIBLE. Two independent code paths used to write these
 * visibilities directly (the tab listener owning main<->log, never
 * touching loading; `startSession`/`ReadTask.onPostExecute` owning
 * main<->loading, never touching log) and neither knew about the third
 * view. On hardware: a user on the Log tab after a failed read taps the
 * document again -> `onPostExecute` sets `main_layout = VISIBLE` while
 * `log_layout` is STILL `VISIBLE` -> the log covers the MRZ form, the tab
 * still reads "Log", and `onTabReselected` was a no-op, so re-tapping the
 * tab did nothing — the user could not reach the document-number field to
 * correct a stale MRZ, and every re-tap re-read the SAME wrong key.
 *
 * [choosePane] makes the both-visible state UNREPRESENTABLE rather than
 * merely fixing today's reachable path to it: it always names exactly ONE
 * pane, so a caller that applies its result to all three views can never
 * leave two of them `VISIBLE` at once.
 */
object PaneVisibility {
    // Device fix (2026-09-05) — §6.5 S3 round 3, item 4: a fourth pane,
    // [Pane.DIAGNOSTICS], for the two no-tap-needed probe buttons moved out
    // of the scan pane into their own tab. Same "always names exactly ONE
    // pane" invariant this object's class doc already establishes — now
    // four overlapping siblings, still mutually exclusive by construction.
    enum class Pane { SCAN, LOG, DIAGNOSTICS, LOADING }

    /**
     * @param readInProgress true while a chip read is in flight (mirrors
     *   [MainActivity]'s `readInProgress` field) — takes precedence over
     *   tab selection, matching the pre-D55 behaviour where a read in
     *   progress always showed the loading pane regardless of which tab
     *   was selected.
     * @param selectedTabPosition the tab layout's current selection (0 =
     *   Scan, 1 = Log, 2 = Diagnostics) — only consulted when no read is in
     *   progress.
     */
    fun choosePane(readInProgress: Boolean, selectedTabPosition: Int): Pane = when {
        readInProgress -> Pane.LOADING
        selectedTabPosition == 1 -> Pane.LOG
        selectedTabPosition == 2 -> Pane.DIAGNOSTICS
        else -> Pane.SCAN
    }
}
