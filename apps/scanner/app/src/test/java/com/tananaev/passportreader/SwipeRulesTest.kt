package com.tananaev.passportreader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** D115 (item 25) — when a fling is a page swipe. Literal expectations. */
class SwipeRulesTest {

    @Test
    fun `swipe left goes to the next page, swipe right to the previous`() {
        assertEquals(1, SwipeRules.targetPage(0, false, -150f, 5f, -900f))
        assertEquals(2, SwipeRules.targetPage(1, false, -150f, 5f, -900f))
        assertEquals(0, SwipeRules.targetPage(1, false, 150f, 5f, 900f))
        assertEquals(1, SwipeRules.targetPage(2, false, 150f, 5f, 900f))
    }

    @Test
    fun `nothing past either end`() {
        assertNull(SwipeRules.targetPage(0, false, 150f, 0f, 900f))
        assertNull(SwipeRules.targetPage(2, false, -150f, 0f, -900f))
    }

    @Test
    fun `no page change while a read is in flight`() {
        assertNull(SwipeRules.targetPage(0, true, -150f, 0f, -900f))
    }

    @Test
    fun `too short, too slow, or mostly vertical is not a swipe`() {
        assertNull(SwipeRules.targetPage(0, false, -40f, 0f, -900f)) // short
        assertNull(SwipeRules.targetPage(0, false, -150f, 0f, -100f)) // slow
        assertNull(SwipeRules.targetPage(0, false, -150f, 100f, -900f)) // diagonal: dx < 2 * dy
        assertEquals(1, SwipeRules.targetPage(0, false, -150f, 70f, -900f)) // just horizontal enough
    }
}
