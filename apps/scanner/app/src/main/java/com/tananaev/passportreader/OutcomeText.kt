package com.tananaev.passportreader

/**
 * §6.2 item 25 (D71b, 2026-09-03, PRD item 25, owner-approved) — the one
 * extra sentence a terminal-outcome dialog appends, naming which
 * presentation mode the outcome it reports belongs to.
 * [MainActivity.showBlockingOutcomeDialog] is the ONE call site for every
 * terminal-outcome dialog this app shows — item 15's mode-A bare
 * presentation modal, every mint success/refusal dialog, Q36's
 * threshold-not-met dialog, and every access-establishment/transient
 * failure dialog — so this one function covers all of them without a
 * second, ad hoc implementation anywhere.
 *
 * Pure decision, Android-free and unit-testable without an emulator
 * (`OutcomeTextTest`) — same "pure logic in pure classes" split every
 * sibling decision object in this package (`SessionDisplay`, `MintGate`,
 * `HandoffAdmission`, ...) already follows, since `AlertDialog` is a stub
 * under this module's `unitTests.isReturnDefaultValues` test sandbox.
 *
 * **Source of truth is the LOCKED mode of the session the dialog
 * reports** — `MainActivity`'s own `lockedMode` (mapped to
 * [SessionDisplay.LockedMode] via the SAME `lockedModeForDisplay()` the
 * status line already uses), never re-derived from the incoming request:
 * the dialog is reporting what the app actually locked to and presented,
 * not what a request merely asked for.
 *
 * **Wording is borrowed, not duplicated.** [SessionDisplay.modeLabel]
 * already carries the "anonymous"/"recognisable to this site" fragments
 * the post-lock mode status line renders — this function reuses that
 * SAME resource rather than a second, independently-typed pair of
 * strings that could drift from it.
 */
object OutcomeText {

    /**
     * @param message the dialog's existing message body, UNCHANGED apart
     *   from the sentence this function may append.
     * @param locked the session's locked mode at the moment the dialog is
     *   shown. `null` for a failure that happens before any mode was ever
     *   locked (e.g. incomplete MRZ fields at lock time, a handoff
     *   refused/expired before lock) — there is no presentation to name
     *   yet, so NO sentence is appended and [message] is returned as-is.
     * @return [message], with " This scan was Mode A, anonymous." or
     *   " This scan was Mode B, recognisable to this site." appended when
     *   [locked] is non-null; [message] itself, byte-identical, when
     *   [locked] is null.
     */
    fun withModeSentence(message: String, locked: SessionDisplay.LockedMode?): String {
        val mode = locked ?: return message
        val label = when (mode) {
            SessionDisplay.LockedMode.A -> "A"
            SessionDisplay.LockedMode.B -> "B"
        }
        return "$message This scan was Mode $label, ${SessionDisplay.modeLabel(mode)}."
    }
}
