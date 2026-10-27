package com.tananaev.passportreader

import org.json.JSONObject

/**
 * D112 (2026-10-27, owner; finding #38) — WHEN the tier-C admission decision first runs.
 *
 * Every tier-C refusal the app can decide without the chip is shown AS SOON AS THE REQUEST ARRIVES, i.e. right after
 * the request object is fetched and its JWS is verified (the earliest point the app knows the request is tier C), the
 * same way the age-check refusals behave. This object is that arrival step: it only asks the one pure decision
 * ([TierCAdmission.decide], unchanged) and says what to do. It is READ-ONLY on the age lock: [locks] is a read-only
 * `Map` and nothing here writes it. The D74 lock is written only after the read, once every predicate is answered
 * (`MainActivity.presentTierC`). The Verify-tap check and the post-read re-decision stay (defensive second checks:
 * the holder setting or the age map can change between arrival and the tap).
 */
object TierCArrival {

    sealed class Plan {
        /** Tier A, B, absent or invalid: not this step's business; the existing paths run unchanged. */
        object NotTierC : Plan()

        /** Tier C and admitted for now: the request goes on to the question area and the Verify tap. */
        data class Proceed(val admission: TierCAdmission.Decision.Admit) : Plan()

        /** Tier C and refused without the chip: show the refusal now, no Verify tap, no document fields. */
        data class Refuse(val refusal: TierCAdmission.Decision.Refuse) : Plan()
    }

    fun plan(
        requestJson: JSONObject,
        operatorEnabled: Boolean,
        hostname: String?,
        pinned: Set<String>,
        holderAllowsAdditional: Boolean,
        locks: Map<String, Int>,
    ): Plan {
        if (!TierCRequest.isTierC(requestJson)) return Plan.NotTierC
        return when (
            val d = TierCAdmission.decide(
                operatorEnabled = operatorEnabled,
                hostname = hostname,
                pinned = pinned,
                holderAllowsAdditional = holderAllowsAdditional,
                requestJson = requestJson,
                lockedAge = hostname?.let { locks[it] },
            )
        ) {
            is TierCAdmission.Decision.Admit -> Plan.Proceed(d)
            is TierCAdmission.Decision.Refuse -> Plan.Refuse(d)
        }
    }
}
