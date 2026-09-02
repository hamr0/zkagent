package com.tananaev.passportreader

/**
 * D58 step 4 (findings #9, #14; Q40) — the Session/Handoff/Mode/Lock
 * cluster's THREE write-only view projections (`MainActivity.handoffStatus
 * .text`, `MainActivity.lockButton.isEnabled`/`.text` [the latter new,
 * Q40], `MainActivity.modeStatusView.text`) collapse into ONE pure
 * decision, computed together so they can never independently drift out
 * of step with each other — same pattern as [PaneVisibility.choosePane] /
 * [MintGate.mayMint] / [HandoffAdmission.mayAdmitInboundHandoff] /
 * [MintPromptText.titleFor]: the real views (`TextView`/`Button`) are
 * themselves stubs under this module's `unitTests.isReturnDefaultValues =
 * true`, so the DECISION lives here and `MainActivity.applySessionDisplay`
 * stays a thin applier — see that function's doc.
 *
 * **Finding #14's exact defect closes BY CONSTRUCTION, not by adding a
 * fourth writer at the mint site.** The pre-refactor `refreshModeStatus()`
 * this object replaces touched ONLY [Projection.modeStatusText] — never
 * `handoffStatus.text` — so a mint's own cleanup correctly reset the mode
 * line while the handoff line (a completely separate, ad hoc write path
 * with exactly three writers and no fourth) was simply never told to
 * reset, leaving a stale "Handoff verified — … Fill in your document
 * details and lock to answer it." on screen after the handoff it
 * described had already been consumed. [render] recomputes ALL THREE
 * outputs from the CURRENT [locked]/[handoff] arguments on every call, so
 * there is no call site left that can leave one projection describing a
 * state the other two have already moved past.
 *
 * **[locked] taking absolute precedence over [handoff] is what unblocks
 * D58 step 3's guard-removal question** (findings #10/#11's status
 * updates, `HandoffAdmission`'s own class doc): `applyHandoffVerificationOutcome`
 * (the async verify callback `beginHandoffVerification` schedules) used to
 * write `modeStatusView.text`/`lockButton.isEnabled` UNCONDITIONALLY on a
 * resolved verify, with nothing in code enforcing the old
 * `refreshModeStatus`'s own "never while lockedMode is set" rule except
 * `HandoffAdmission` refusing the foreign intent before that callback
 * could ever run. Now that callback (like every other call site) always
 * calls [render] with whatever [locked] state is CURRENT at the moment it
 * fires — an admitted foreign verification resolving after the legitimate
 * session has since locked can no longer produce anything but the SAME
 * locked-banner projection every other call already renders, regardless of
 * what [handoff] argument it is passed. See
 * `MainActivity.applyHandoffVerificationOutcome`'s doc for the full trace
 * and `HandoffAdmission`'s doc for the guard-removal recommendation this
 * enables (recommendation only — that guard is NOT removed by this step).
 *
 * **Q40 (owner UX, PROVISIONAL — not yet owner-approved wording, per this
 * project's rule that every user-facing string goes back to the owner):**
 * a disabled Lock button reads as "stuck" once a session is locked and
 * waiting for the document tap (finding #9's owner observation (iii)).
 * [LockButtonLabel.TAP_AND_SCAN] is that relabel's pure decision —
 * `MainActivity` substitutes the actual owner-specified string (its own
 * companion-object constant, marked PROVISIONAL there too) rather than
 * this Android-free object owning English prose, matching
 * [MintPromptText]'s existing split between decision and copy.
 */
object SessionDisplay {

    /** Mirrors `MainActivity`'s private `PresentationMode` enum — kept as
     * its own type here (this object could not reference that private
     * nested enum even if it wanted to) so this class stays free of any
     * `MainActivity` dependency, matching every sibling pure decision
     * object in this package. */
    enum class LockedMode { A, B }

    /** The handoff-verification pipeline's phase AS OF the instant [render]
     * is called — not persisted state of its own. Every call site either
     * derives this from `MainActivity.pendingHandoff`/`verifiedRequest`
     * (`MainActivity.currentHandoffState`) or, for [Refused] specifically,
     * supplies it directly from the `RequestTrust.Outcome` that has not yet
     * been written to those fields — see
     * `MainActivity.applyHandoffVerificationOutcome`'s doc for why
     * [Refused] cannot be derived from field state alone (at the instant a
     * verification is refused, `pendingHandoff` is still non-null and
     * `verifiedRequest` is still null — indistinguishable from [Verifying]
     * by field state alone). */
    sealed class HandoffState {
        object None : HandoffState()
        object Verifying : HandoffState()
        data class Verified(val origin: String, val tier: String?) : HandoffState()
        data class Refused(val reason: String) : HandoffState()
    }

    /** [DEFAULT] is the button's XML-declared label
     * (`R.string.button_lock_and_scan`, substituted by `MainActivity`, this
     * object stays string-free); [TAP_AND_SCAN] is Q40's PROVISIONAL
     * relabel, shown only while [locked] is non-null. */
    enum class LockButtonLabel { DEFAULT, TAP_AND_SCAN }

    data class Projection(
        val modeStatusText: String,
        val handoffStatusText: String,
        val lockButtonEnabled: Boolean,
        val lockButtonLabel: LockButtonLabel,
    )

    /** Matches the pre-refactor `refreshModeStatus`'s own "no handoff
     * pending" branch text verbatim — not a new string. */
    private const val MODE_DEFAULT_TEXT = "Mode: A — anonymous (no site request pending)"

    /**
     * @param locked the CURRENT `MainActivity.lockedMode`, mapped to
     *   [LockedMode] by the caller (`MainActivity.lockedModeForDisplay`) —
     *   `null` for an unlocked session. Takes precedence over [handoff]:
     *   see class doc for why.
     * @param handoff the CURRENT handoff-verification phase — see
     *   [HandoffState]'s doc for how callers obtain this.
     */
    fun render(locked: LockedMode?, handoff: HandoffState): Projection {
        if (locked != null) {
            // Matches lockModeAndArm's pre-refactor text verbatim
            // ("Locked: mode ${lockedMode} — tap your document now") —
            // PresentationMode/[LockedMode]'s default toString() gives
            // exactly "A"/"B", same as before.
            return Projection(
                modeStatusText = "Locked: mode $locked — tap your document now",
                // Whatever the pre-lock handoff status said is moot once
                // locked — lockModeAndArm's own doc already required
                // reading only the verified request's tier before this
                // point; showing it again here would be exactly finding
                // #14's class of staleness one step earlier. Reverting to
                // blank (this view's own XML default, not a new string)
                // is the SAME choice this object makes for [HandoffState
                // .None] below.
                handoffStatusText = "",
                lockButtonEnabled = false,
                lockButtonLabel = LockButtonLabel.TAP_AND_SCAN,
            )
        }
        return when (handoff) {
            HandoffState.None -> Projection(
                modeStatusText = MODE_DEFAULT_TEXT,
                handoffStatusText = "",
                lockButtonEnabled = true,
                lockButtonLabel = LockButtonLabel.DEFAULT,
            )
            HandoffState.Verifying -> Projection(
                // Matches beginHandoffVerification's pre-refactor text verbatim.
                modeStatusText = "Mode: verifying the site's request…",
                handoffStatusText = "Handoff request received — verifying signature and origin…",
                lockButtonEnabled = false,
                lockButtonLabel = LockButtonLabel.DEFAULT,
            )
            is HandoffState.Verified -> Projection(
                // Matches the pre-refactor refreshModeStatus's tier-mapping
                // branch verbatim.
                modeStatusText = when (handoff.tier) {
                    "A" -> "Mode: A — anonymous"
                    "B" -> "Mode: B — recognisable to this site"
                    else -> "Mode: pending — tap Lock & scan to see the outcome"
                },
                handoffStatusText = "Handoff verified — origin: ${handoff.origin}, requested tier: ${handoff.tier ?: "<absent>"}. Fill in your document details and lock to answer it.",
                lockButtonEnabled = true,
                lockButtonLabel = LockButtonLabel.DEFAULT,
            )
            is HandoffState.Refused -> Projection(
                // The pre-refactor code left modeStatusView/lockButton
                // untouched at the instant of refusal (only handoffStatus
                // was written), relying on the terminal dialog's later OK
                // handler (wipeSession) to eventually reset them. That is
                // no longer possible once these three are one projection —
                // reverting to the same
                // "no handoff pending" defaults here is behaviourally
                // invisible (a blocking, non-cancelable AlertDialog is
                // already covering the screen by this point) rather than a
                // real change.
                modeStatusText = MODE_DEFAULT_TEXT,
                handoffStatusText = "Handoff refused (${handoff.reason}) — you may still scan manually.",
                lockButtonEnabled = false,
                lockButtonLabel = LockButtonLabel.DEFAULT,
            )
        }
    }
}
