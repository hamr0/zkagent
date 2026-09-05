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
 * **Q40 (owner UX, CLOSED D67 — owner sign-off 2026-09-03):** a disabled
 * Lock button reads as "stuck" once a session is locked and waiting for the
 * document tap (finding #9's owner observation (iii)).
 * [LockButtonLabel.TAP_AND_SCAN] is that relabel's pure decision —
 * `MainActivity` substitutes the actual owner-specified string (its own
 * companion-object constant) rather than this Android-free object owning
 * English prose, matching [MintPromptText]'s existing split between
 * decision and copy.
 *
 * **§6.2 item 20 (D68, owner ruling 2026-09-03, Q45):** the single scan-
 * action button ALSO changes verb — "Verify" while a verified `av://`
 * handoff request is pending or driving the current lock, "Scan" otherwise
 * — layered onto the same [LockButtonLabel] enum rather than a second
 * control (owner: NO new control; the existing button's label is a pure
 * projection of handoff state, exactly [HandoffState] already is for the
 * mode/handoff lines above). See [render]'s `handoffDrivenLock` parameter
 * doc for why the LOCKED verb cannot be derived from the live [handoff]
 * argument the way the unlocked verb is.
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
        // §6.5 S2 (D74) — [threshold] is the already-signed
        // `zkagent.challenge.threshold` field ([RequestTrust.thresholdOf],
        // the same parse path item 1's per-origin lock and item 13's mint
        // gate already use), carried alongside [tier] so [questionTextFor]
        // can compose the exact pre-tap question without re-parsing
        // anything. Defaulted to `null` so every pre-S2 call site (this
        // constructor is used by three existing call sites/tests with only
        // two positional args) keeps compiling unchanged.
        data class Verified(val origin: String, val tier: String?, val threshold: Int? = null) : HandoffState()
        data class Refused(val reason: String) : HandoffState()
    }

    /**
     * [SCAN] is the button's default unlocked verb (no site request
     * active); [VERIFY] is item 20's verb for a VERIFIED (not merely
     * pending-verification) `av://` request, unlocked. [TAP_AND_SCAN] is
     * Q40's locked relabel for a bare local lock; [TAP_AND_VERIFY] is the
     * same locked relabel for a handoff-driven lock (item 20's collision
     * wording, D68 — "Tap and verify"/"Tap and scan" once both Q40's
     * waiting-frame and item 20's verb apply at once). [APPLY_PASTE] is the
     * §6.5 S3 (D75) owner correction, 2026-09-05: while UNLOCKED, the paste
     * reveal area is open, and its field holds text that has NOT yet been
     * applied, the ONE main button reads the SAME text as [TAP_AND_VERIFY]
     * ("Tap and verify" — owner verbatim) but is a DISTINCT value: unlike
     * [TAP_AND_VERIFY] (locked, button disabled, armed for an NFC tap) this
     * one is UNLOCKED and ENABLED, and a tap on it calls
     * `MainActivity.applyPendingHandoffText` (begins verifying the pasted
     * text, exactly as an `av://` intent would) rather than
     * `lockModeAndArm`. Kept as its own enum value rather than reusing
     * [TAP_AND_VERIFY] so `MainActivity`'s click dispatch can be driven by
     * the SAME projection this text comes from, never by string-matching
     * the displayed label. `MainActivity` substitutes the actual
     * owner-specified strings; this object stays string-free.
     */
    enum class LockButtonLabel { SCAN, VERIFY, TAP_AND_SCAN, TAP_AND_VERIFY, APPLY_PASTE }

    data class Projection(
        val modeStatusText: String,
        val handoffStatusText: String,
        val lockButtonEnabled: Boolean,
        val lockButtonLabel: LockButtonLabel,
        // §6.5 S2 (D74) — the exact pre-tap question, sourced from
        // [questionTextFor]. See [render]'s `lockedQuestionHandoff` param
        // doc for why this needs its own frozen-snapshot argument, mirroring
        // [handoffDrivenLock].
        val questionText: String,
        // §6.5 S3 (D75) — the "Paste link" button's enabled state, the SAME
        // [HandoffAdmission.mayAdmitInboundHandoff] predicate the av://
        // intent and manual-paste call sites already gate admission on
        // (`MainActivity.handleIncomingIntent`/`applyPendingHandoffText`) —
        // not a second, independently-derived lock check.
        val pasteButtonEnabled: Boolean,
    )

    /** §6.2 item 25 (D71b, 2026-09-03) — the plain-language label for each
     * [LockedMode], factored out of the mode-line strings below so a
     * terminal-outcome dialog ([MainActivity.showBlockingOutcomeDialog],
     * via `OutcomeText`) can name the SAME "anonymous"/"recognisable to
     * this site" wording this status line already uses, rather than a
     * second, independently-typed pair of strings that could drift from
     * it. Not private — `OutcomeText` is the one other caller. */
    fun modeLabel(locked: LockedMode): String = when (locked) {
        LockedMode.A -> "anonymous"
        LockedMode.B -> "recognisable to this site"
    }

    /** Matches the pre-refactor `refreshModeStatus`'s own "no handoff
     * pending" branch text verbatim — not a new string. */
    private val MODE_DEFAULT_TEXT = "Mode: A — ${modeLabel(LockedMode.A)} (no site request pending)"

    /**
     * @param locked the CURRENT `MainActivity.lockedMode`, mapped to
     *   [LockedMode] by the caller (`MainActivity.lockedModeForDisplay`) —
     *   `null` for an unlocked session. Takes precedence over [handoff]:
     *   see class doc for why.
     * @param handoff the CURRENT handoff-verification phase — see
     *   [HandoffState]'s doc for how callers obtain this. Drives the
     *   UNLOCKED verb ([LockButtonLabel.SCAN]/[VERIFY]) directly, since a
     *   live incoming request legitimately changing what the unlocked
     *   screen shows is the whole point of [Verifying]/[Verified].
     * @param handoffDrivenLock whether THIS lock (if [locked] is non-null)
     *   was itself authorized from a verified handoff — i.e. the caller's
     *   OWN `authorizedHandoff != null` snapshot, frozen at lock time.
     *   Deliberately NOT derived from [handoff]: [handoff] can keep
     *   changing after lock (an admitted foreign handoff's async
     *   verification resolving late — see class doc's "locked wins"
     *   passage and finding #14/D58 step 3), and a locked screen's VERB
     *   must be just as immune to that as its mode/handoff text already
     *   is — `locked always wins over a foreign Verified outcome arriving
     *   after lock` (`SessionDisplayTest`) pins exactly this: that test
     *   passes this parameter's default (`false`) precisely because the
     *   scenario's lock was never established as handoff-driven, so a
     *   foreign [HandoffState.Verified] landing afterward must still
     *   render [LockButtonLabel.TAP_AND_SCAN], never [TAP_AND_VERIFY].
     *   Ignored while [locked] is `null`. Default `false` matches every
     *   pre-item-20 call site (a bare mode-A lock).
     */
    /**
     * §6.5 S2 (D74) — the exact pre-tap question, D74's own worked example
     * verbatim ("This website asks if you are over 18"). Only a VERIFIED
     * request has an actual question to ask; every other [HandoffState]
     * (none pending, still verifying, refused) falls back to D46's existing
     * "no site" label ([MintPromptText.NO_HANDOFF_FALLBACK]) — there is
     * nothing signed yet for those states to name. Tier B appends D47-
     * consistent recognition wording; a [HandoffState.Verified.threshold]
     * of `null` (a verified request whose threshold this build cannot read
     * — should not occur once [RequestTrust.thresholdOf]'s own gate has run,
     * but this function must not guess a number it was not given) falls
     * back to a threshold-free phrasing rather than printing "over null".
     */
    private fun questionTextFor(handoff: HandoffState): String {
        val verified = handoff as? HandoffState.Verified ?: return MintPromptText.NO_HANDOFF_FALLBACK
        val base = if (verified.threshold != null) {
            "This website asks if you are over ${verified.threshold}"
        } else {
            "This website asks if you are over its requested age"
        }
        return if (verified.tier == "B") "$base, and may recognise you again on this site" else base
    }

    /**
     * @param locked see [render]'s existing doc.
     * @param handoff see [render]'s existing doc.
     * @param handoffDrivenLock see [render]'s existing doc.
     * @param lockedQuestionHandoff §6.5 S2 (D74) — the question line's OWN
     *   frozen-snapshot argument, for the identical reason
     *   [handoffDrivenLock] is one: once [locked] is non-null, the live
     *   [handoff] argument can keep changing (an admitted foreign handoff's
     *   async verification resolving after lock — see class doc's "locked
     *   wins" passage), and the question line must be just as immune to
     *   that as the mode/handoff text and the verb already are. Callers
     *   pass their OWN lock-time snapshot (`AuthorizedHandoff`, mapped to a
     *   [HandoffState.Verified]) here, not [handoff]. Defaults to
     *   [HandoffState.None] (D46's "Local scan (no site)" wording) — every
     *   pre-S2 call site keeps compiling with no behaviour change for the
     *   fields it already asserts on.
     * @param readInProgress §6.5 S3 (D75) — `MainActivity.paneState
     *   .readInProgress`, threaded straight into
     *   [HandoffAdmission.mayAdmitInboundHandoff] for
     *   [Projection.pasteButtonEnabled] — the SAME two inputs
     *   (`sessionLocked`, `readInProgress`) that predicate already gates
     *   admission on, not a second lock check. Defaults to `false`,
     *   matching every pre-S3 call site.
     * @param pasteTextPending §6.5 S3 (D75) owner correction, 2026-09-05 —
     *   `MainActivity`'s own `pasteFieldRevealed && handoffManualInput.text`
     *   non-blank check, computed ONCE by the caller and passed in here
     *   rather than this Android-free object ever touching the `EditText`
     *   itself (same reasoning as every other primitive-in, decision-out
     *   parameter this function takes). Only changes anything when [locked]
     *   is `null` AND [handoff] is [HandoffState.None] — see [LockButtonLabel
     *   .APPLY_PASTE]'s doc: once ANY handoff is pending/verifying/verified,
     *   or the session is locked, the existing verb for THAT state takes
     *   over unconditionally, matching the "locked always wins" precedent.
     *   Defaults to `false`, matching every pre-owner-correction call site.
     */
    fun render(
        locked: LockedMode?,
        handoff: HandoffState,
        handoffDrivenLock: Boolean = false,
        lockedQuestionHandoff: HandoffState = HandoffState.None,
        readInProgress: Boolean = false,
        pasteTextPending: Boolean = false,
    ): Projection {
        val pasteButtonEnabled = HandoffAdmission.mayAdmitInboundHandoff(sessionLocked = locked != null, readInProgress = readInProgress)
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
                lockButtonLabel = if (handoffDrivenLock) LockButtonLabel.TAP_AND_VERIFY else LockButtonLabel.TAP_AND_SCAN,
                questionText = questionTextFor(lockedQuestionHandoff),
                pasteButtonEnabled = pasteButtonEnabled,
            )
        }
        return when (handoff) {
            HandoffState.None -> Projection(
                modeStatusText = MODE_DEFAULT_TEXT,
                handoffStatusText = "",
                lockButtonEnabled = true,
                // §6.5 S3 (D75) owner correction: only the bare-None state
                // (nothing else already pending/verifying/verified) yields
                // to a pending, un-applied paste — see [LockButtonLabel
                // .APPLY_PASTE]'s doc.
                lockButtonLabel = if (pasteTextPending) LockButtonLabel.APPLY_PASTE else LockButtonLabel.SCAN,
                questionText = questionTextFor(handoff),
                pasteButtonEnabled = pasteButtonEnabled,
            )
            HandoffState.Verifying -> Projection(
                // Matches beginHandoffVerification's pre-refactor text verbatim.
                // Verb stays SCAN, not VERIFY: D68's ruling names a
                // VERIFIED request, and this signature has not resolved
                // yet (the button is disabled here regardless).
                modeStatusText = "Mode: verifying the site's request…",
                handoffStatusText = "Handoff request received — verifying signature and origin…",
                lockButtonEnabled = false,
                lockButtonLabel = LockButtonLabel.SCAN,
                questionText = questionTextFor(handoff),
                pasteButtonEnabled = pasteButtonEnabled,
            )
            is HandoffState.Verified -> Projection(
                // Matches the pre-refactor refreshModeStatus's tier-mapping
                // branch verbatim.
                modeStatusText = when (handoff.tier) {
                    "A" -> "Mode: A — ${modeLabel(LockedMode.A)}"
                    "B" -> "Mode: B — ${modeLabel(LockedMode.B)}"
                    else -> "Mode: pending — tap Lock & scan to see the outcome"
                },
                handoffStatusText = "Handoff verified — origin: ${handoff.origin}, requested tier: ${handoff.tier ?: "<absent>"}. Fill in your document details and lock to answer it.",
                lockButtonEnabled = true,
                lockButtonLabel = LockButtonLabel.VERIFY,
                questionText = questionTextFor(handoff),
                pasteButtonEnabled = pasteButtonEnabled,
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
                // real change. Verb reverts to SCAN — the handoff that
                // would have justified VERIFY is exactly what was refused.
                modeStatusText = MODE_DEFAULT_TEXT,
                handoffStatusText = "Handoff refused (${handoff.reason}) — you may still scan manually.",
                lockButtonEnabled = false,
                lockButtonLabel = LockButtonLabel.SCAN,
                questionText = questionTextFor(handoff),
                pasteButtonEnabled = pasteButtonEnabled,
            )
        }
    }
}
