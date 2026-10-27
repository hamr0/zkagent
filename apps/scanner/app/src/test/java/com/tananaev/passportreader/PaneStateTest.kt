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
        assertEquals(PaneState.PAGE_SCAN, state.selectedPage)
        assertFalse(state.readInProgress)
    }

    @Test
    fun `userSelectedPage moves to the Log tab`() {
        val state = PaneState()
        state.userSelectedPage(PaneState.PAGE_LOG)
        assertEquals(PaneState.PAGE_LOG, state.selectedPage)
    }

    @Test
    fun `userSelectedPage back to the Scan tab`() {
        val state = PaneState()
        state.userSelectedPage(PaneState.PAGE_LOG)
        state.userSelectedPage(PaneState.PAGE_SCAN)
        assertEquals(PaneState.PAGE_SCAN, state.selectedPage)
    }

    @Test
    fun `an out-of-range tab index normalizes to Scan, never Log`() {
        // Mirrors PaneVisibilityTest's own -1 case: the old TabLayout.
        // selectedTabPosition returned -1 when nothing is selected.
        val state = PaneState()
        state.userSelectedPage(-1)
        assertEquals(PaneState.PAGE_SCAN, state.selectedPage)
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
        state.userSelectedPage(PaneState.PAGE_LOG)
        state.readStarted()
        assertEquals(PaneState.PAGE_LOG, state.selectedPage)
        assertTrue(state.readInProgress)
    }

    @Test
    fun `save then restore round-trips the selected tab`() {
        val state = PaneState()
        state.userSelectedPage(PaneState.PAGE_LOG)
        val saved = state.pageIndexToSave()

        val recreated = PaneState()
        recreated.restorePageIndex(saved)
        assertEquals(PaneState.PAGE_LOG, recreated.selectedPage)
    }

    @Test
    fun `save then restore round-trips the Scan tab too`() {
        val state = PaneState()
        // never touched — starts on Scan
        val saved = state.pageIndexToSave()

        val recreated = PaneState()
        recreated.userSelectedPage(PaneState.PAGE_LOG) // prove restore overwrites, not merges
        recreated.restorePageIndex(saved)
        assertEquals(PaneState.PAGE_SCAN, recreated.selectedPage)
    }

    @Test
    fun `restore with no prior selection (null, e_g_ a fresh launch) yields the default tab`() {
        val recreated = PaneState()
        recreated.userSelectedPage(PaneState.PAGE_LOG)
        recreated.restorePageIndex(null)
        assertEquals(PaneState.PAGE_SCAN, recreated.selectedPage)
    }

    @Test
    fun `restore never persists or restores readInProgress`() {
        // Audit row 11's "LOST, resets to declared default" behaviour is
        // unchanged by this step — only the tab index's fate changes.
        val state = PaneState()
        state.readStarted()
        val saved = state.pageIndexToSave()

        val recreated = PaneState()
        recreated.restorePageIndex(saved)
        assertFalse(recreated.readInProgress)
    }

    // §6.2 item 17 (D67, Q39) — an incoming, ADMITTED av:// handoff intent
    // switches to the Scan tab regardless of which tab was current.
    @Test
    fun `onIncomingHandoffIntent admitted switches to the Scan tab from the Log tab`() {
        val state = PaneState()
        state.userSelectedPage(PaneState.PAGE_LOG)
        state.onIncomingHandoffIntent(admitted = true)
        assertEquals(PaneState.PAGE_SCAN, state.selectedPage)
    }

    @Test
    fun `onIncomingHandoffIntent admitted is a no-op when already on the Scan tab`() {
        val state = PaneState()
        state.onIncomingHandoffIntent(admitted = true)
        assertEquals(PaneState.PAGE_SCAN, state.selectedPage)
    }

    // The MUST NOT half: a REFUSED intent (HandoffAdmission refusal) must
    // leave the tab exactly where it was.
    @Test
    fun `onIncomingHandoffIntent NOT admitted leaves the Log tab alone`() {
        val state = PaneState()
        state.userSelectedPage(PaneState.PAGE_LOG)
        state.onIncomingHandoffIntent(admitted = false)
        assertEquals("a refused intent must not switch the tab", PaneState.PAGE_LOG, state.selectedPage)
    }

    @Test
    fun `onIncomingHandoffIntent does not change readInProgress either way`() {
        val state = PaneState()
        state.readStarted()
        state.onIncomingHandoffIntent(admitted = true)
        assertTrue(state.readInProgress)
    }

    // ---- D115 (item 25): three pages; Diagnostics folded into Settings. Literal expectations on purpose.

    @Test
    fun `the page constants are the literal values the saved state and the pager order rely on`() {
        assertEquals(0, PaneState.PAGE_SCAN)
        assertEquals(1, PaneState.PAGE_LOG)
        assertEquals(2, PaneState.PAGE_SETTINGS)
        assertEquals(3, PaneState.PAGE_COUNT)
    }

    @Test
    fun `userSelectedPage moves to Settings and an unknown value falls back to Scan`() {
        val state = PaneState()
        state.userSelectedPage(2)
        assertEquals(2, state.selectedPage)
        state.userSelectedPage(7)
        assertEquals(0, state.selectedPage)
        state.userSelectedPage(1)
        state.userSelectedPage(-1)
        assertEquals(0, state.selectedPage)
    }

    @Test
    fun `an old saved Diagnostics tab value (2) restores to Settings, an old Settings value (3) too`() {
        val a = PaneState()
        a.restorePageIndex(2) // the old TAB_DIAGNOSTICS
        assertEquals(2, a.selectedPage) // PAGE_SETTINGS
        val b = PaneState()
        b.restorePageIndex(3) // the old TAB_SETTINGS
        assertEquals(2, b.selectedPage)
        val c = PaneState()
        c.restorePageIndex(99)
        assertEquals(0, c.selectedPage)
    }

    @Test
    fun `save then restore round-trips each of the three pages`() {
        for (page in 0..2) {
            val state = PaneState()
            state.userSelectedPage(page)
            val recreated = PaneState()
            recreated.restorePageIndex(state.pageIndexToSave())
            assertEquals(page, recreated.selectedPage)
        }
    }

    @Test
    fun `an admitted handoff intent selects Scan from every page, a refused one leaves every page`() {
        for (page in 0..2) {
            val admitted = PaneState()
            admitted.userSelectedPage(page)
            admitted.onIncomingHandoffIntent(admitted = true)
            assertEquals("admitted from page $page", 0, admitted.selectedPage)

            val refused = PaneState()
            refused.userSelectedPage(page)
            refused.onIncomingHandoffIntent(admitted = false)
            assertEquals("refused on page $page", page, refused.selectedPage)
        }
    }

    @Test
    fun `previous and next neighbours, none past either end`() {
        assertEquals(null, PaneState.previousPage(0))
        assertEquals(0, PaneState.previousPage(1))
        assertEquals(1, PaneState.previousPage(2))
        assertEquals(1, PaneState.nextPage(0))
        assertEquals(2, PaneState.nextPage(1))
        assertEquals(null, PaneState.nextPage(2))
    }

    @Test
    fun `titles and arrow texts are the owner's literal words`() {
        assertEquals("Scan", PaneState.pageTitle(0))
        assertEquals("Log", PaneState.pageTitle(1))
        assertEquals("Settings", PaneState.pageTitle(2))
        assertEquals(null, PaneState.previousArrowText(0))
        assertEquals("\u2039 Scan", PaneState.previousArrowText(1))
        assertEquals("\u2039 Log", PaneState.previousArrowText(2))
        assertEquals("Log \u203A", PaneState.nextArrowText(0))
        assertEquals("Settings \u203A", PaneState.nextArrowText(1))
        assertEquals(null, PaneState.nextArrowText(2))
    }

    @Test
    fun `pane choice over PaneState state - Settings, then a read shows LOADING, then back to Settings`() {
        val state = PaneState()
        state.userSelectedPage(PaneState.PAGE_SETTINGS)
        assertEquals(PaneVisibility.Pane.SETTINGS, PaneVisibility.choosePane(state.readInProgress, state.selectedPage))
        state.readStarted()
        assertEquals(PaneVisibility.Pane.LOADING, PaneVisibility.choosePane(state.readInProgress, state.selectedPage))
        state.readFinished()
        assertEquals(PaneVisibility.Pane.SETTINGS, PaneVisibility.choosePane(state.readInProgress, state.selectedPage))
    }
}
