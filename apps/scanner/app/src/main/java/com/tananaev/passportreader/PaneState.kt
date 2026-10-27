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
 * (D58 step 2 history; D115 changed the page set, see below.) Legal states, enumerated (this step's own right-sizing test): the two
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
 * [restoreTabIndex] are this class's equivalent (renamed [pageIndexToSave]/[restorePageIndex] by D115).
 *
 * **D115 (2026-10-27, milestones.md §6.9.2 item 25):** the four-tab strip is
 * gone. There are THREE swipeable pages — [PAGE_SCAN], [PAGE_LOG],
 * [PAGE_SETTINGS] — and this class still owns the selected one
 * ([selectedPage], formerly `selectedTab`). Diagnostics is no longer a page:
 * it is a collapsed section at the bottom of Settings. The saved-state key
 * and its integers are unchanged on purpose: an old saved value 2 (the old
 * Diagnostics tab) and 3 (the old Settings tab) both land on
 * [PAGE_SETTINGS], and any unknown value lands on [PAGE_SCAN], so a Bundle
 * written by the previous build restores safely. 3 pages x 2 read states =
 * six legal combinations. Which page a swipe or an arrow reaches is decided
 * HERE ([previousPage]/[nextPage]/[pageTitle]) so it is unit-testable; the
 * Activity only draws it.
 */
class PaneState {

    var selectedPage: Int = PAGE_SCAN
        private set

    var readInProgress: Boolean = false
        private set

    /** A real user selection (swipe or arrow). The Activity applies the
     * result via [MainActivity.showPane]. */
    fun userSelectedPage(index: Int) {
        selectedPage = normalizePage(index)
    }

    /** §6.2 item 17 (D67, Q39) — an incoming `av://` handoff intent MUST
     * switch to [PAGE_SCAN] from any page. [admitted] is the caller's own
     * [HandoffAdmission.mayAdmitInboundHandoff] verdict: a REFUSED intent
     * leaves the page alone, and folding the check in here makes that
     * directly testable against this class. */
    fun onIncomingHandoffIntent(admitted: Boolean) {
        if (admitted) selectedPage = PAGE_SCAN
    }

    /** [MainActivity.startSession]'s write — a chip read has begun. */
    fun readStarted() {
        readInProgress = true
    }

    /** [MainActivity.ReadTask.onPostExecute]'s write, the FIRST statement
     * on every exit path (D55) — the read is over. */
    fun readFinished() {
        readInProgress = false
    }

    /** The value [MainActivity.onSaveInstanceState] persists under its own
     * Bundle key. Deliberately NOT [readInProgress] (a read cannot survive
     * a recreation; audit row 11). */
    fun pageIndexToSave(): Int = selectedPage

    /** [MainActivity.onCreate]'s restore call — MUST run before
     * `showPane()` (finding #1). `null` yields [PAGE_SCAN]; an old saved
     * Diagnostics (2) or Settings (3) value yields [PAGE_SETTINGS]. */
    fun restorePageIndex(saved: Int?) {
        selectedPage = normalizePage(saved ?: PAGE_SCAN)
    }

    private fun normalizePage(index: Int): Int = when (index) {
        PAGE_LOG -> PAGE_LOG
        PAGE_SETTINGS, LEGACY_TAB_DIAGNOSTICS, LEGACY_TAB_SETTINGS -> PAGE_SETTINGS
        else -> PAGE_SCAN
    }

    companion object {
        const val PAGE_SCAN = 0
        const val PAGE_LOG = 1
        const val PAGE_SETTINGS = 2
        const val PAGE_COUNT = 3

        // Values written by builds before D115 (four tabs: Scan, Log, Diagnostics, Settings).
        private const val LEGACY_TAB_DIAGNOSTICS = 2
        private const val LEGACY_TAB_SETTINGS = 3

        /** The page one step left of [page], or null at the first page. */
        fun previousPage(page: Int): Int? = if (page in PAGE_LOG..PAGE_SETTINGS) page - 1 else null

        /** The page one step right of [page], or null at the last page. */
        fun nextPage(page: Int): Int? = if (page in PAGE_SCAN until PAGE_SETTINGS) page + 1 else null

        /** The title for [page]: exactly "Scan", "Log" or "Settings". */
        fun pageTitle(page: Int): String = when (page) {
            PAGE_LOG -> "Log"
            PAGE_SETTINGS -> "Settings"
            else -> "Scan"
        }

        /** The text of the prev arrow on [page] ("\u2039 Log"), or null when hidden. */
        fun previousArrowText(page: Int): String? = previousPage(page)?.let { "\u2039 ${pageTitle(it)}" }

        /** The text of the next arrow on [page] ("Settings \u203A"), or null when hidden. */
        fun nextArrowText(page: Int): String? = nextPage(page)?.let { "${pageTitle(it)} \u203A" }
    }
}
