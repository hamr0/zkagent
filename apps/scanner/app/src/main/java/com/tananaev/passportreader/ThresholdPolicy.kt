package com.tananaev.passportreader

import java.net.URI
import java.net.URISyntaxException

/**
 * §6.5 S1 (D74) — the three-rule threshold policy: a fixed preset list, a
 * per-origin "first threshold wins" lock, and a named-hostname exception
 * allowlist for sites permitted to ask more than one threshold. Pure
 * decision object — same pattern as [HandoffAdmission]/[PaneVisibility]/
 * [MintGate] — so the truth table is pinned independently of `MainActivity`'s
 * field state, unassertable under this module's
 * `unitTests.isReturnDefaultValues = true`.
 *
 * **Origin key shape: HOSTNAME ONLY, not `RequestTrust.originOf`'s
 * scheme+host+port.** D74/§6.5 item 1 calls this "the same per-origin
 * binding shape as D38's key binding" — D38's own attester-binding key
 * (`apps/demo/store.mjs` `attesterBindings`, keyed by `(scope, zktag)`)
 * uses `SCOPE_DOMAIN`, which `apps/demo/server.mjs`'s own comment (line
 * ~100) states explicitly is "scope is HOST ONLY here" — never a scheme or
 * port. Mirroring that shape means two requests from `http://127.0.0.1:8787`
 * and (hypothetically) `https://127.0.0.1:8787` share ONE lock, matching how
 * D38 already treats the verifier's identity as host-scoped. [hostnameOf]
 * is the one place this app derives that key from a request's already-
 * verified origin string ([RequestTrust.VerifiedRequest.origin], itself
 * `scheme://host:port` from [RequestTrust.originOf]).
 *
 * **Rule 1 (preset list) is unconditional** — it applies even to a hostname
 * on the exception allowlist. Rule 3 (the allowlist) only lifts the
 * per-origin LOCK (rule 2); it never lifts rule 1's requirement that the
 * threshold itself be one of the six published values. A rogue verifier
 * asking for "over 43" is refused by rule 1 regardless of which hostname it
 * runs on.
 */
object ThresholdPolicy {

    /** The fixed, published preset list (D74). Spelled out, not derived
     * from any other constant — see [ThresholdPolicyTest] for the
     * independent-expectation discipline this enables. */
    val PRESETS: Set<Int> = setOf(15, 16, 18, 21, 60, 65)

    /** Exact-hostname exception allowlist (D74 rule 3) — no wildcards,
     * app-side only, never the verifier's own choice. Deliberately EMPTY:
     * `127.0.0.1` (M3's demo origin, D76) is explicitly NOT here, so the
     * demo exercises the per-origin lock rather than being exempt from it. */
    val NAMED_EXCEPTIONS: Set<String> = emptySet()

    fun isPreset(threshold: Int): Boolean = threshold in PRESETS

    /** Case-insensitive ASCII-lowercase EXACT match only — never a suffix
     * or regex match. `sub.example.com` and `xexample.com` never match an
     * allowlisted `example.com`. */
    fun isExempt(hostname: String): Boolean = NAMED_EXCEPTIONS.contains(hostname.lowercase())

    sealed class Decision {
        /** Admit — the threshold is a preset, and either no lock exists yet
         * for this hostname, the lock already matches, or the hostname is
         * exempt from the lock (rule 3). */
        object Admit : Decision()

        /** Rule 1 refusal: [threshold] is not one of [PRESETS]. Applies
         * regardless of hostname/lock/exemption. */
        data class RefuseNotPreset(val threshold: Int) : Decision()

        /** Rule 2 refusal: this hostname already locked a DIFFERENT
         * threshold ([lockedThreshold]) and is not on the exception
         * allowlist. */
        data class RefuseDifferentThreshold(val lockedThreshold: Int, val requestedThreshold: Int) : Decision()
    }

    /**
     * @param hostname the verified request's origin hostname, from
     *   [hostnameOf] — already lowercased by that function.
     * @param requestedThreshold the request's already-signed, nonce-bound
     *   threshold ([RequestTrust.thresholdOf]).
     * @param lockedThreshold the threshold this app has already recorded
     *   for [hostname] (from MainActivity's persisted per-origin record),
     *   or `null` if this is the first request ever seen from it.
     */
    fun evaluate(hostname: String, requestedThreshold: Int, lockedThreshold: Int?): Decision {
        if (!isPreset(requestedThreshold)) return Decision.RefuseNotPreset(requestedThreshold)
        if (lockedThreshold != null && lockedThreshold != requestedThreshold && !isExempt(hostname)) {
            return Decision.RefuseDifferentThreshold(lockedThreshold, requestedThreshold)
        }
        return Decision.Admit
    }

    /** Whether [hostname]'s first-seen threshold should now be recorded —
     * true only on genuine first sight (no existing lock) for a
     * non-exempt hostname (an exempt hostname permits multiple thresholds,
     * so there is nothing to lock). Callers MUST only consult this after
     * [evaluate] has already returned [Decision.Admit] for the same
     * arguments — this function does not re-check rule 1. The lock MUST be
     * written only when a request is ACCEPTED as pending (first-seen),
     * never on a refusal. */
    fun shouldRecordLock(hostname: String, lockedThreshold: Int?): Boolean =
        lockedThreshold == null && !isExempt(hostname)

    /** Extracts the lowercased hostname from an already-verified origin
     * string (`scheme://host:port`, [RequestTrust.originOf]'s shape) — or
     * null if it does not parse. Pure — no network, no Android API. */
    fun hostnameOf(origin: String): String? {
        return try {
            URI(origin).host?.lowercase()
        } catch (e: URISyntaxException) {
            null
        }
    }
}
