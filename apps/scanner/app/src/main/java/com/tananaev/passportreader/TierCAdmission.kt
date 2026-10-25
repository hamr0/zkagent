package com.tananaev.passportreader

import org.json.JSONObject

/**
 * M3b chunk 1, items 1, 2, 7, 9 and 13 — the WHOLE pre-read decision for a tier-C request, as one pure function:
 * the three gates ([TierCGate]: operator switch, exact-hostname pin, holder setting), then the all-or-nothing
 * parse ([TierCRequest.parse]: vocabulary, fixed boundary lists, exclusion cap), then the shared one-age-per-site
 * lock (item 9, D109(i)3, D109(j)2). Every refusal here is BEFORE any chip read; the site learns nothing and
 * only this app's own screen and report say why (D92, D109(g)5).
 *
 * **The age lock is read here and written elsewhere.** [Decision.Admit.ageToLock] says which age, if any, the
 * caller MUST record in the SAME per-hostname map tier A/B uses (`originThresholdLocks`) and only after the
 * answer is fully evaluated (item 9 rule 5: a refused request never burns the site's one age). Hostnames in
 * `multi_threshold_verifiers` are exempt (rule 6) and never get a lock written. The comparison itself is
 * [ThresholdPolicy.conflictsWithLock], the same rule tier A/B applies, so there is no second map and no second rule.
 */
object TierCAdmission {

    enum class Refusal {
        OPERATOR_SWITCH_OFF,
        NOT_PINNED,
        HOLDER_SETTING_OFF,
        /** The request is malformed, off a fixed list, out of vocabulary or over the exclusion cap. */
        REQUEST_NOT_ANSWERABLE,
        /** A date-of-birth-range age that is not this site's one locked age (item 9 rule 2). */
        AGE_NOT_LOCKED_AGE,
        /** Two different date-of-birth-range ages in one request, for a non-exempt site (item 9 rule 4). */
        AGE_CONFLICT_IN_REQUEST,
    }

    sealed class Decision {
        data class Admit(val predicates: List<TierCRequest.Predicate>, val ageToLock: Int?) : Decision()
        /** [reason] is a fixed value-free code (the parse reason, or the refusal name). */
        data class Refuse(val refusal: Refusal, val reason: String) : Decision()
    }

    /**
     * @param lockedAge the age already locked for [hostname] in the shared map, or null.
     * @param exemptHosts `multi_threshold_verifiers`; defaults to the build's list (via [ThresholdPolicy]).
     */
    fun decide(
        operatorEnabled: Boolean,
        hostname: String?,
        pinned: Set<String>,
        holderAllowsAdditional: Boolean,
        requestJson: JSONObject,
        lockedAge: Int?,
        exemptHosts: Set<String> = ThresholdPolicy.NAMED_EXCEPTIONS,
    ): Decision {
        when (val gate = TierCGate.decide(operatorEnabled, hostname, pinned, holderAllowsAdditional)) {
            is TierCGate.Decision.Refuse -> return Decision.Refuse(
                when (gate.cause) {
                    TierCGate.Cause.OPERATOR_SWITCH_OFF -> Refusal.OPERATOR_SWITCH_OFF
                    TierCGate.Cause.NOT_PINNED -> Refusal.NOT_PINNED
                    TierCGate.Cause.HOLDER_SETTING_OFF -> Refusal.HOLDER_SETTING_OFF
                },
                gate.cause.name.lowercase(),
            )
            TierCGate.Decision.Allow -> Unit
        }
        val host = hostname ?: return Decision.Refuse(Refusal.NOT_PINNED, "not_pinned") // unreachable: the gate refused null
        val predicates = when (val parsed = TierCRequest.parse(requestJson)) {
            is TierCRequest.Parse.Refuse -> return Decision.Refuse(Refusal.REQUEST_NOT_ANSWERABLE, parsed.reason)
            is TierCRequest.Parse.Ok -> parsed.predicates
        }
        val ages = TierCRequest.dobAges(predicates)
        if (ages.isEmpty()) return Decision.Admit(predicates, ageToLock = null)
        val exempt = ThresholdPolicy.isExempt(host, exemptHosts)
        if (ages.size > 1 && !exempt) return Decision.Refuse(Refusal.AGE_CONFLICT_IN_REQUEST, "dob_boundaries_conflict")
        if (!exempt) {
            val age = ages.first()
            if (ThresholdPolicy.conflictsWithLock(host, age, lockedAge, exemptHosts)) return Decision.Refuse(Refusal.AGE_NOT_LOCKED_AGE, "dob_age_not_locked_age")
            return Decision.Admit(predicates, ageToLock = if (ThresholdPolicy.shouldRecordLock(host, lockedAge, exemptHosts)) age else null)
        }
        return Decision.Admit(predicates, ageToLock = null)
    }
}
