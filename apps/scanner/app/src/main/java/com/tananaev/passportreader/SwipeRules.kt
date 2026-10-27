package com.tananaev.passportreader

/**
 * D115 (item 25) — when is a touch gesture a page swipe, and which page does it reach. Pure and Android-free so the
 * thresholds and the direction are unit-tested; [MainActivity.dispatchTouchEvent] only feeds it a fling's distances
 * and speed, converted to dp so the rule does not depend on screen density.
 *
 * A swipe LEFT (finger moves left, negative dx) reaches the NEXT page; a swipe RIGHT reaches the PREVIOUS page. Past
 * either end nothing happens (null). No page change while a chip read is in flight (the loading pane covers the
 * screen and the read must not be disturbed). The fling must be clearly horizontal (at least twice as far across as
 * down), at least [MIN_DISTANCE_DP] long and at least [MIN_VELOCITY_DP_PER_SEC] fast, so a vertical scroll or a slow
 * drag inside a page never changes it.
 */
object SwipeRules {
    const val MIN_DISTANCE_DP = 80f
    const val MIN_VELOCITY_DP_PER_SEC = 300f

    /** @return the page to switch to, or null when the gesture is not a page swipe (or there is no page that way). */
    fun targetPage(currentPage: Int, readInProgress: Boolean, dxDp: Float, dyDp: Float, velocityXDpPerSec: Float): Int? {
        if (readInProgress) return null
        val absDx = Math.abs(dxDp)
        if (absDx < MIN_DISTANCE_DP) return null
        if (absDx < 2f * Math.abs(dyDp)) return null
        if (Math.abs(velocityXDpPerSec) < MIN_VELOCITY_DP_PER_SEC) return null
        return if (dxDp < 0f) PaneState.nextPage(currentPage) else PaneState.previousPage(currentPage)
    }
}
