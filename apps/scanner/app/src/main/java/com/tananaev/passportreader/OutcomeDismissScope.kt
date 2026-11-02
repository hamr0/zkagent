package com.tananaev.passportreader

/**
 * Finding #55 — what a terminal outcome dialog's OK may clear.
 *
 * The dialog reports ONE transaction: the handoff that was pending when it was shown (or none, for a local scan).
 * A link can be captured while the dialog is still open (a VIEW intent reaches the running instance). That link
 * is a different transaction and its dismissal must not wipe it. So the handoff pointers are released only when the
 * handoff pending at dismissal is the very object pending at show time (reference identity, not equality: the same
 * link reopened is a NEW object and a NEW transaction).
 */
object OutcomeDismissScope {
    fun ownsHandoff(pendingWhenShown: Any?, pendingNow: Any?): Boolean = pendingWhenShown === pendingNow
}
