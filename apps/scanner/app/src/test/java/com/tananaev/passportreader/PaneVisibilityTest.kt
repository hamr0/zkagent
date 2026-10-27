package com.tananaev.passportreader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * D55's pane-visibility decision — pins [PaneVisibility.choosePane]'s truth
 * table directly, since `View.visibility` itself is a non-functional stub
 * under `unitTests.isReturnDefaultValues = true` in this module (see
 * [PaneVisibility]'s class doc) and cannot be asserted against real views
 * here. This is exactly why the decision was extracted to a pure function.
 */
class PaneVisibilityTest {

    @Test
    fun `read in progress always shows LOADING, regardless of tab`() {
        assertEquals(PaneVisibility.Pane.LOADING, PaneVisibility.choosePane(readInProgress = true, selectedPage = 0))
        assertEquals(PaneVisibility.Pane.LOADING, PaneVisibility.choosePane(readInProgress = true, selectedPage = 1))
    }

    @Test
    fun `no read in progress, Scan tab selected, shows SCAN`() {
        assertEquals(PaneVisibility.Pane.SCAN, PaneVisibility.choosePane(readInProgress = false, selectedPage = 0))
    }

    @Test
    fun `no read in progress, Log tab selected, shows LOG`() {
        assertEquals(PaneVisibility.Pane.LOG, PaneVisibility.choosePane(readInProgress = false, selectedPage = 1))
    }

    @Test
    fun `an out-of-range tab position that is not 1 falls back to SCAN, never LOG`() {
        // The old TabLayout returned -1 when nothing is selected
        // (e.g. before the layout has any tabs) — must not be misread as
        // "Log" (position 1).
        assertEquals(PaneVisibility.Pane.SCAN, PaneVisibility.choosePane(readInProgress = false, selectedPage = -1))
    }

    @Test
    fun `finishing a read while the Log tab is selected returns to LOG, not SCAN`() {
        // The D55 real-device scenario: a failed read leaves the user on
        // the Log tab; the fix must not silently switch them to Scan
        // (owner explicitly rejected auto-switching tabs) — it must land
        // back on whatever pane the tab selection already names.
        assertEquals(PaneVisibility.Pane.LOG, PaneVisibility.choosePane(readInProgress = false, selectedPage = 1))
    }

    // ---- D115 (item 25): Settings is page 2; there is no Diagnostics pane any more

    @Test
    fun `no read in progress, Settings page selected, shows SETTINGS and a read still shows LOADING`() {
        assertEquals(PaneVisibility.Pane.SETTINGS, PaneVisibility.choosePane(readInProgress = false, selectedPage = 2))
        assertEquals(PaneVisibility.Pane.LOADING, PaneVisibility.choosePane(readInProgress = true, selectedPage = 2))
        // the other two pages are not Settings (non-vacuity of the arm)
        for (i in 0..1) assertNotEquals(PaneVisibility.Pane.SETTINGS, PaneVisibility.choosePane(false, i))
        assertEquals(PaneVisibility.Pane.SCAN, PaneVisibility.choosePane(false, 3)) // beyond the last page falls back to Scan
    }

    @Test
    fun `the pane enum has exactly four panes, none for Diagnostics`() {
        assertEquals(listOf("SCAN", "LOG", "SETTINGS", "LOADING"), PaneVisibility.Pane.values().map { it.name })
    }
}
