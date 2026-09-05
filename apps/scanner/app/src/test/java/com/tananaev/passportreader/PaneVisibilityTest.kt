package com.tananaev.passportreader

import org.junit.Assert.assertEquals
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
        assertEquals(PaneVisibility.Pane.LOADING, PaneVisibility.choosePane(readInProgress = true, selectedTabPosition = 0))
        assertEquals(PaneVisibility.Pane.LOADING, PaneVisibility.choosePane(readInProgress = true, selectedTabPosition = 1))
    }

    @Test
    fun `no read in progress, Scan tab selected, shows SCAN`() {
        assertEquals(PaneVisibility.Pane.SCAN, PaneVisibility.choosePane(readInProgress = false, selectedTabPosition = 0))
    }

    @Test
    fun `no read in progress, Log tab selected, shows LOG`() {
        assertEquals(PaneVisibility.Pane.LOG, PaneVisibility.choosePane(readInProgress = false, selectedTabPosition = 1))
    }

    @Test
    fun `an out-of-range tab position that is not 1 falls back to SCAN, never LOG`() {
        // TabLayout.selectedTabPosition returns -1 when nothing is selected
        // (e.g. before the layout has any tabs) — must not be misread as
        // "Log" (position 1).
        assertEquals(PaneVisibility.Pane.SCAN, PaneVisibility.choosePane(readInProgress = false, selectedTabPosition = -1))
    }

    @Test
    fun `finishing a read while the Log tab is selected returns to LOG, not SCAN`() {
        // The D55 real-device scenario: a failed read leaves the user on
        // the Log tab; the fix must not silently switch them to Scan
        // (owner explicitly rejected auto-switching tabs) — it must land
        // back on whatever pane the tab selection already names.
        assertEquals(PaneVisibility.Pane.LOG, PaneVisibility.choosePane(readInProgress = false, selectedTabPosition = 1))
    }

    // ---- Device fix (2026-09-05) — §6.5 S3 round 3 item 4: Diagnostics tab

    @Test
    fun `no read in progress, Diagnostics tab selected, shows DIAGNOSTICS`() {
        assertEquals(PaneVisibility.Pane.DIAGNOSTICS, PaneVisibility.choosePane(readInProgress = false, selectedTabPosition = 2))
    }

    @Test
    fun `read in progress always shows LOADING even with Diagnostics selected`() {
        assertEquals(PaneVisibility.Pane.LOADING, PaneVisibility.choosePane(readInProgress = true, selectedTabPosition = 2))
    }

    @Test
    fun `finishing a read while the Diagnostics tab is selected returns to DIAGNOSTICS`() {
        assertEquals(PaneVisibility.Pane.DIAGNOSTICS, PaneVisibility.choosePane(readInProgress = false, selectedTabPosition = 2))
    }
}
