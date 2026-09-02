package com.tananaev.passportreader

/**
 * §6.2 item 15 (D43 extension, 2026-09 real-device fix — "confirm success
 * too"): D43 made the app loud about every failure and silent about
 * success; the owner's real runs showed this leaves the one outcome the
 * user most wants confirmed (a mode-B presentation the site actually
 * accepted) with nothing telling them to go back to the browser.
 *
 * A mint attempt confirms success to the user with a blocking modal ONLY
 * when the presentation was actually delivered AND accepted by the site
 * (HTTP 2xx). A signed-but-undelivered presentation, one the site
 * rejected, or one that reached no `response_uri` at all is NEVER a
 * confirmed success — that distinction is the whole reason
 * `MainActivity.DeliveryResult` exists as four separate outcomes rather
 * than a boolean.
 *
 * Extracted as a pure function, the same precedent as [MintGate] and
 * [FailureTransition], so this one true/false decision has a unit test
 * independent of [MainActivity]'s `DeliveryResult` sealed class.
 */
object MintConfirmation {
    fun confirmsSuccess(deliveryAccepted: Boolean): Boolean = deliveryAccepted
}
