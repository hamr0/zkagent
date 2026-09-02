package com.tananaev.passportreader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * D58 step 2 (Pane cluster, finding #1) — pins [PaneState]'s enumerated
 * legal states and every transition. See [PaneState]'s class doc for why
 * this is a plain instantiated class (not a `Bundle`-taking one, despite
 * the brief's literal phrasing) and why it is a sibling of
 * [PaneVisibility] rather than an addition to it.
 */
class PaneStateTest {

    @Test
    fun `starts on the Scan tab, no read in progress`() {
        val state = PaneState()
        assertEquals(PaneState.TAB_SCAN, state.selectedTab)
        assertFalse(state.readInProgress)
    }

    @Test
    fun `userSelectedTab moves to the Log tab`() {
        val state = PaneState()
        state.userSelectedTab(PaneState.TAB_LOG)
        assertEquals(PaneState.TAB_LOG, state.selectedTab)
    }

    @Test
    fun `userSelectedTab back to the Scan tab`() {
        val state = PaneState()
        state.userSelectedTab(PaneState.TAB_LOG)
        state.userSelectedTab(PaneState.TAB_SCAN)
        assertEquals(PaneState.TAB_SCAN, state.selectedTab)
    }

    @Test
    fun `an out-of-range tab index normalizes to Scan, never Log`() {
        // Mirrors PaneVisibilityTest's own -1 case: TabLayout.
        // selectedTabPosition returns -1 when nothing is selected.
        val state = PaneState()
        state.userSelectedTab(-1)
        assertEquals(PaneState.TAB_SCAN, state.selectedTab)
    }

    @Test
    fun `readStarted and readFinished toggle readInProgress`() {
        val state = PaneState()
        state.readStarted()
        assertTrue(state.readInProgress)
        state.readFinished()
        assertFalse(state.readInProgress)
    }

    @Test
    fun `readStarted does not change the selected tab`() {
        val state = PaneState()
        state.userSelectedTab(PaneState.TAB_LOG)
        state.readStarted()
        assertEquals(PaneState.TAB_LOG, state.selectedTab)
        assertTrue(state.readInProgress)
    }

    @Test
    fun `save then restore round-trips the selected tab`() {
        val state = PaneState()
        state.userSelectedTab(PaneState.TAB_LOG)
        val saved = state.tabIndexToSave()

        val recreated = PaneState()
        recreated.restoreTabIndex(saved)
        assertEquals(PaneState.TAB_LOG, recreated.selectedTab)
    }

    @Test
    fun `save then restore round-trips the Scan tab too`() {
        val state = PaneState()
        // never touched — starts on Scan
        val saved = state.tabIndexToSave()

        val recreated = PaneState()
        recreated.userSelectedTab(PaneState.TAB_LOG) // prove restore overwrites, not merges
        recreated.restoreTabIndex(saved)
        assertEquals(PaneState.TAB_SCAN, recreated.selectedTab)
    }

    @Test
    fun `restore with no prior selection (null, e_g_ a fresh launch) yields the default tab`() {
        val recreated = PaneState()
        recreated.userSelectedTab(PaneState.TAB_LOG)
        recreated.restoreTabIndex(null)
        assertEquals(PaneState.TAB_SCAN, recreated.selectedTab)
    }

    @Test
    fun `restore never persists or restores readInProgress`() {
        // Audit row 11's "LOST, resets to declared default" behaviour is
        // unchanged by this step — only the tab index's fate changes.
        val state = PaneState()
        state.readStarted()
        val saved = state.tabIndexToSave()

        val recreated = PaneState()
        recreated.restoreTabIndex(saved)
        assertFalse(recreated.readInProgress)
    }

    @Test
    fun `choosePane over PaneState's own state matches PaneVisibility's existing truth table`() {
        val state = PaneState()
        assertEquals(
            PaneVisibility.Pane.SCAN,
            PaneVisibility.choosePane(state.readInProgress, state.selectedTab),
        )

        state.userSelectedTab(PaneState.TAB_LOG)
        assertEquals(
            PaneVisibility.Pane.LOG,
            PaneVisibility.choosePane(state.readInProgress, state.selectedTab),
        )

        state.readStarted()
        assertEquals(
            PaneVisibility.Pane.LOADING,
            PaneVisibility.choosePane(state.readInProgress, state.selectedTab),
        )

        state.readFinished()
        assertEquals(
            PaneVisibility.Pane.LOG,
            PaneVisibility.choosePane(state.readInProgress, state.selectedTab),
        )
    }
}
