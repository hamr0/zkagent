package com.tananaev.passportreader

/**
 * D58 step 2 (Pane cluster, finding #1) — the pane decision's tab-index
 * input becomes app state owned HERE, no longer read from
 * `TabLayout.selectedTabPosition` as the source of truth. See finding #1
 * (`.claude/remember/findings.md`): the framework restores that field
 * asynchronously — the view-hierarchy restore that runs inside the
 * DEFAULT `Activity.onPostCreate()`, which fires AFTER `onCreate()` (no
 * `onPostCreate` override exists in `MainActivity.kt` — confirmed by grep
 * before this step; there is nothing to make a no-op or remove) — so
 * reading it as the source of truth from `onCreate` raced that restore on
 * every Activity recreation. At the time this step was written the manifest
 * declared `fullSensor` orientation, so a physical rotation was the common
 * trigger; D63 (`d406f4b`) later locked
 * `apps/scanner/app/src/regular/AndroidManifest.xml`'s `screenOrientation`
 * to `portrait`, so rotation no longer recreates the Activity — recreation
 * now comes only from other config changes (font-scale, locale, a
 * low-memory kill, etc.; the 2026-09-03 device session drove one via
 * `settings put system font_scale`). The race this step fixed is
 * unaffected either way: any recreation still restores
 * `TabLayout.selectedTabPosition` asynchronously in the same
 * `onPostCreate` path, after `onCreate` — this class's ownership of
 * [selectedTab] and [readInProgress] stands regardless of which config
 * change triggers the recreation.
 * `readInProgress` (audit row 11) moves in alongside it: its own two
 * writers ([MainActivity.startSession], [MainActivity.ReadTask
 * .onPostExecute]) are both already in this exact read-lifecycle path, per
 * this step's required survey — neither is on a `Thread{}` or in the
 * handoff/session path, so consolidating it here makes the pane decision a
 * pure function of this one owner's state, per the structure proposal's
 * Cluster 2 (`docs/logs/M2-STRUCTURE-PROPOSAL-2026-09-02.md`).
 *
 * A SIBLING of [PaneVisibility], not an addition to it (design decision,
 * justified): [PaneVisibility] is a stateless Kotlin `object` — a true
 * process-wide singleton, deliberately, per its own class doc ("pure,
 * Android-free object"). Giving it mutable instance fields would make
 * every test — and every real `MainActivity` instance, since Activity
 * recreation briefly has two instances alive — share ONE pane's worth of
 * state. [PaneState] is instantiated once per `MainActivity` instance,
 * exactly like the existing `reportLog` field (D58 step 1's precedent).
 *
 * Legal states, enumerated (this step's own right-sizing test): the two
 * fields are independent — [selectedTab] is one of exactly two values,
 * [TAB_SCAN] or [TAB_LOG] (`activity_main.xml` declares exactly two
 * `TabItem`s), and [readInProgress] is a plain `Boolean`. 2 x 2 = FOUR
 * legal combinations, all reachable and all meaningful: a read can be in
 * flight while either tab is the one the user last chose —
 * [PaneVisibility.choosePane] already encodes that `readInProgress` takes
 * precedence over tab selection regardless of which tab that is.
 *
 * Bundle handling is DELIBERATELY not done inside this class, despite the
 * brief's own "restore(bundle)/save(bundle)" phrasing — design decision,
 * justified: this module runs under `unitTests.isReturnDefaultValues =
 * true` (the same limitation [PaneVisibility]'s class doc documents for
 * `View`), under which `android.os.Bundle.putInt`/`getInt` are
 * non-functional stubs — a class holding a `Bundle` parameter could never
 * have a save/restore round-trip actually asserted in this suite. D58 step
 * 1 already established the alternative pattern this follows exactly:
 * [ReportLog.restore] takes plain values, not a `Bundle`, and
 * `MainActivity` does the real Bundle read/write itself. [tabIndexToSave]/
 * [restoreTabIndex] are this class's equivalent.
 */
class PaneState {

    var selectedTab: Int = TAB_SCAN
        private set

    var readInProgress: Boolean = false
        private set

    /** The tab listener's write (a real user tap or reselect) — see
     * [MainActivity]'s tab-selection handler doc for the re-entry guard
     * this pairs with, so [MainActivity.showPane] moving the TabLayout
     * programmatically is never misread as a second user selection. */
    fun userSelectedTab(index: Int) {
        selectedTab = normalizeTab(index)
    }

    /** §6.2 item 17 (D67, Q39) — [MainActivity.handleIncomingIntent]'s write
     * for an incoming `av://` handoff intent: MUST switch to [TAB_SCAN]
     * regardless of the tab currently selected, distinct from
     * [userSelectedTab] (a real user tap) — this is a NEW writer of
     * [selectedTab] the same way [readStarted]/[readFinished] are, not a
     * second path into [userSelectedTab]'s "user tap" semantics. Distinct
     * also from the already-REJECTED D55 proposal to auto-switch on read
     * COMPLETION — see this class's own trigger, an incoming intent, never
     * a read finishing.
     *
     * [admitted] is the caller's own [HandoffAdmission.mayAdmitInboundHandoff]
     * verdict, passed in rather than trusted-by-omission: the MUST text is
     * explicit that a REFUSED intent must leave the tab alone, and folding
     * the admission check in here (rather than relying on every call site
     * to only call this after its own guard) makes "a refused intent never
     * switches the tab" directly testable against this class alone, not
     * only inferable from reading [MainActivity]'s call site. */
    fun onIncomingHandoffIntent(admitted: Boolean) {
        if (admitted) selectedTab = TAB_SCAN
    }

    /** [MainActivity.startSession]'s write — a chip read has begun. */
    fun readStarted() {
        readInProgress = true
    }

    /** [MainActivity.ReadTask.onPostExecute]'s write, the FIRST statement
     * on every exit path (unchanged discipline from before this step,
     * D55) — the read is over. */
    fun readFinished() {
        readInProgress = false
    }

    /** The value [MainActivity.onSaveInstanceState] persists under its own
     * named Bundle key. Deliberately does NOT expose [readInProgress] for
     * persistence: a read cannot meaningfully survive process death or an
     * Activity recreation (nothing resumes an in-flight `IsoDep` session),
     * matching audit row 11's pre-existing "LOST, resets to declared
     * default" behaviour exactly — this step changes ONLY the tab index's
     * fate on recreation, not `readInProgress`'s. */
    fun tabIndexToSave(): Int = selectedTab

    /** [MainActivity.onCreate]'s restore call — MUST run before
     * `showPane()` (see finding #1). `null` (fresh launch, or a Bundle
     * that never had the key) yields the default, [TAB_SCAN] — matches
     * the pre-step-2 behaviour for a first launch exactly. */
    fun restoreTabIndex(saved: Int?) {
        selectedTab = normalizeTab(saved ?: TAB_SCAN)
    }

    // Device fix (2026-09-05) — §6.5 S3 round 3, item 4: a THIRD tab
    // ("Diagnostics", the two no-tap-needed probe buttons moved out of the
    // scan pane) extends the legal-state enumeration this class's own doc
    // right-sized at "2 x 2 = FOUR" to THREE tab values x TWO readInProgress
    // values = six. [TAB_DIAGNOSTICS] is a new writable value, not a new
    // field — every existing writer ([userSelectedTab],
    // [onIncomingHandoffIntent]'s TAB_SCAN target, [restoreTabIndex]) is
    // unchanged in shape.
    private fun normalizeTab(index: Int): Int = when (index) {
        TAB_LOG -> TAB_LOG
        TAB_DIAGNOSTICS -> TAB_DIAGNOSTICS
        else -> TAB_SCAN
    }

    companion object {
        const val TAB_SCAN = 0
        const val TAB_LOG = 1
        const val TAB_DIAGNOSTICS = 2
    }
}
