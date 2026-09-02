package com.tananaev.passportreader

/**
 * Finding #11 (`.claude/remember/findings.md`) — FIX: the biometric/device-
 * credential authorization prompt was built from two hardcoded strings with
 * no reference to which origin the authorization is for
 * (`MainActivity.promptAndMint` had `site`/`scopeDomain` in scope and used
 * neither). [titleFor] is the extracted, pure DECISION of what to show —
 * same pattern as [PaneVisibility] / [MintGate]: `BiometricPrompt.
 * PromptInfo.Builder`/`getString` are themselves stubs under this module's
 * `unitTests.isReturnDefaultValues = true`, so the decision is pinned here
 * and `MainActivity.promptAndMint` stays a thin applier — it substitutes
 * this value into `R.string.biometric_prompt_title_for_site` via `getString`,
 * never composes English text itself, matching every other user-facing
 * string in this project living in `strings.xml`.
 *
 * `MainActivity.promptAndMint` is reachable ONLY when [MintGate.mayMint]
 * is `true`, which requires `modeIsB` — and mode B is reachable only via a
 * verified handoff's `zkagent.tier` ([MainActivity.tierOutcomeFor]). So in
 * practice [titleFor] is always called with a real, non-blank site; the
 * blank/null fallback below exists for defensive completeness (a caller
 * whose invariant later changes) and is exercised only by
 * [MintPromptTextTest], never by a real call site today.
 *
 * NOTE: this is a MITIGATION for finding #11's consent defect — it makes
 * the destination visible in the prompt, but it does nothing about finding
 * #10's induced-handoff race (a separate, open finding); see
 * [HandoffAdmission]'s doc for that one.
 */
object MintPromptText {
    /** Matches [MainActivity.SITE_NO_HANDOFF] verbatim (kept as one string
     * here rather than referencing the Activity's constant, so this object
     * stays free of any Android/Activity dependency — same reasoning as
     * [PaneVisibility] taking primitives instead of a [MainActivity]
     * reference). */
    const val NO_HANDOFF_FALLBACK = "Local scan (no site)"

    /**
     * @param site the verified origin's `host:port` ([MainActivity.siteTitleFor]
     *   of `verified.origin`) — already computed at the `promptAndMint` call
     *   site for the log title; this is the SAME value, not a re-derivation.
     * @return the value to substitute into `R.string.biometric_prompt_title_for_site`'s
     *   `%1$s` — [site] itself, or [NO_HANDOFF_FALLBACK] when blank/null.
     */
    fun titleFor(site: String?): String = if (site.isNullOrBlank()) NO_HANDOFF_FALLBACK else site
}
