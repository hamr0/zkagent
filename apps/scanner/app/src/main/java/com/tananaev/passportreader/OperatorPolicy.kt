package com.tananaev.passportreader

/**
 * §6.7 POC (D82/D84, milestones.md §6.7.5) — the single reader of the
 * `OPERATOR_*` [BuildConfig] fields Gradle emitted from `apps/scanner/
 * operator.json` at configure time (see `app/build.gradle.kts`'s
 * `loadOperatorConfig`, which is the ENTIRE validation surface — nothing
 * here re-validates). One writer per knob, same discipline as
 * [ThresholdPolicy]/[MintGate]/every other pure decision object in this
 * app: this object reads `BuildConfig.OPERATOR_*` exactly ONCE, at
 * class-init, into vals — no other file in this app may read an
 * `OPERATOR_*` `BuildConfig` field directly (grep before adding a second
 * reader). [ThresholdPolicy.PRESETS]/[ThresholdPolicy.NAMED_EXCEPTIONS] are
 * sourced FROM here now, not hardcoded — see that class's doc.
 *
 * The riskiest assumption this POC targets (§6.7.5) is [isVerifierAllowed]:
 * the verifier-hostname allowlist did not exist in code before this file —
 * any request whose JWS verified was accepted from any origin. Wired at
 * the ONE place a request's origin is accepted for a handoff,
 * `MainActivity.applyHandoffVerificationOutcome`'s `Verified` branch.
 *
 * Decision logic is split from `BuildConfig` access (AGENT_RULES "split the
 * decision from the machinery"): [isVerifierAllowed] and [isTierAllowed]
 * each have a two-argument pure overload taking the allowlist/mode
 * explicitly, so the truth table is testable without touching
 * `BuildConfig` at all — the same reason [ThresholdPolicy.evaluate] takes
 * `lockedThreshold` as a parameter rather than reading a field itself.
 */
object OperatorPolicy {

    /** Splits a comma-joined `BuildConfig` field into a lowercased,
     * trimmed, non-empty set of hostnames. An empty input string yields an
     * empty set (never a set containing one empty-string element) — this
     * is how [MULTI_THRESHOLD_VERIFIERS] stays empty when operator.json's
     * `multi_threshold_verifiers` is `[]`. */
    private fun parseHostnameList(csv: String): Set<String> =
        csv.split(",").map { it.trim().lowercase() }.filter { it.isNotEmpty() }.toSet()

    /** Splits a comma-joined `BuildConfig` field into a set of ints.
     * operator.json's `thresholds` is validated non-empty at build time
     * (rule 3), so this is never empty for [THRESHOLDS] in a real build —
     * but the parse itself makes no such assumption. */
    private fun parseThresholdList(csv: String): Set<Int> =
        csv.split(",").map { it.trim() }.filter { it.isNotEmpty() }.map { it.toInt() }.toSet()

    /** D74's preset list, as this build's operator.json declared it
     * (validated at build time to be a subset of the fixed six values —
     * see `build.gradle.kts` rule 3). Consumed by [ThresholdPolicy.PRESETS]. */
    val THRESHOLDS: Set<Int> = parseThresholdList(BuildConfig.OPERATOR_THRESHOLDS)

    /** The verifier-hostname allowlist (§6.7 item 2, this POC's target).
     * Exact hostnames only — no wildcards, no ports (validated at build
     * time, rule 4). */
    val VERIFIERS: Set<String> = parseHostnameList(BuildConfig.OPERATOR_VERIFIERS)

    /** D74 rule 2's named-exceptions list, exposed as a knob (D84) —
     * consumed by [ThresholdPolicy.NAMED_EXCEPTIONS]. */
    val MULTI_THRESHOLD_VERIFIERS: Set<String> = parseHostnameList(BuildConfig.OPERATOR_MULTI_THRESHOLD_VERIFIERS)

    /** `"A+B"` or `"B"` (validated at build time, rule 2). */
    val TIERS: String = BuildConfig.OPERATOR_TIERS

    /** `"none"` today — see `build.gradle.kts` rule 6's doc for why this is
     * validated-only in this POC (no runtime wiring; reserved field). */
    val EVIDENCE_PLUG: String = BuildConfig.OPERATOR_EVIDENCE_PLUG

    val NAME: String = BuildConfig.OPERATOR_NAME
    val CONTACT_URL: String = BuildConfig.OPERATOR_CONTACT_URL

    /** Convenience overload reading this build's [VERIFIERS]. */
    fun isVerifierAllowed(hostname: String): Boolean = isVerifierAllowed(hostname, VERIFIERS)

    /** Exact, case-insensitive hostname match — no wildcards, no ports, no
     * suffix/prefix matching. [hostname] MUST already be a bare host (the
     * same shape [ThresholdPolicy.hostnameOf] produces) — a scheme- or
     * port-suffixed input is rejected because it will never equal a bare
     * hostname in [allowedHosts], not via any special-cased stripping
     * here: `"127.0.0.1:8787"` is a different string than `"127.0.0.1"`. */
    fun isVerifierAllowed(hostname: String, allowedHosts: Set<String>): Boolean =
        allowedHosts.any { it.trim().lowercase() == hostname.trim().lowercase() }

    /** G1 fix (2026-09-06, orchestrator gap list): the ONE pure function
     * both call sites use for the verifier-hostname-allowlist refusal —
     * the pre-fetch dispatch check (`MainActivity.verifyPendingHandoff`,
     * BEFORE [HandoffClient.fetchRequestRaw] ever contacts an unlisted
     * host) and the post-verification Verified-branch check
     * (`MainActivity.applyHandoffVerificationOutcome`, defence in depth —
     * [RequestTrust]'s origin-consistency check already binds
     * `response_uri`==`request_uri`'s origin, but re-checking here costs
     * nothing and survives a future change to that binding). Exactly ONE
     * refusal wording lives here — neither call site spells its own
     * message. [hostname] null means "could not resolve a hostname from
     * the origin" (refused, never implicitly allowed); returns null when
     * [hostname] is allowed (nothing to refuse). */
    fun gateMessageFor(hostname: String?): String? = if (hostname == null) {
        "This site's origin could not be resolved to a hostname — refused."
    } else if (!isVerifierAllowed(hostname)) {
        "This site ($hostname) is not on this app's approved verifier list — refused."
    } else {
        null
    }

    /** Convenience overload reading this build's [TIERS]. */
    fun isTierAllowed(tier: String): Boolean = isTierAllowed(tier, TIERS)

    /** Whether [tier] ("A" or "B") is permitted under [tiersMode] ("A+B" or
     * "B"). "A+B" permits both; "B" permits only "B" — a tier-A request
     * against a B-only operator is refused (§6.7 item 1). Tier "C" is not
     * this function's concern: it stays refused outright by existing code
     * (`MainActivity.tierOutcomeFor`) regardless of this knob, and an
     * absent/invalid tier is likewise left to that existing fail-loud
     * path — this function is only ever asked about "A" or "B". */
    fun isTierAllowed(tier: String, tiersMode: String): Boolean = when (tiersMode) {
        "B" -> tier == "B"
        else -> true // "A+B" (the only other value schema v1 validates, rule 2)
    }
}
