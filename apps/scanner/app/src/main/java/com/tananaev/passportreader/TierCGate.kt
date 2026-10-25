package com.tananaev.passportreader

/**
 * M3b opening POC, §6.9.1 criteria 3 and 4 — the pure tier-C admission decision. Three independent
 * gates, ALL of which must pass before a tier-C request is answered (item 7: "a tier-C request is
 * answered only if the operator switch is on, the origin is pinned, AND the holder setting is on"):
 *
 *  1. the OPERATOR switch (item 1, `operator.json` `tier_c.enabled`): off means the existing §6.2
 *     item 13 tier-C refusal, unchanged;
 *  2. the PIN list (item 2, `tier_c_verifiers`): exact hostnames only, checked fully offline;
 *  3. the HOLDER setting (item 7, "Allow additional verifications", OFF by default).
 *
 * This object decides; it reads no `BuildConfig`, no preferences, no clock. `MainActivity` passes the
 * three inputs in and applies the result at the one lock-time site that used to refuse every tier C.
 * Every refusal is BEFORE any chip read or mint, and the site only ever learns that nothing came back
 * (item 13, D92); the [Cause] is for the app's own log and screen, never the wire.
 */
object TierCGate {

    enum class Cause { OPERATOR_SWITCH_OFF, NOT_PINNED, HOLDER_SETTING_OFF }

    sealed class Decision {
        object Allow : Decision()
        data class Refuse(val cause: Cause) : Decision()
    }

    /**
     * @param operatorEnabled `OperatorPolicy.TIER_C_ENABLED`.
     * @param hostname the verified request origin's bare hostname ([ThresholdPolicy.hostnameOf]), or null if
     *   it could not be resolved (refused, never implicitly allowed).
     * @param pinned `OperatorPolicy.TIER_C_VERIFIERS`.
     * @param holderAllowsAdditional [HolderSettings.allowAdditionalVerifications].
     */
    fun decide(operatorEnabled: Boolean, hostname: String?, pinned: Set<String>, holderAllowsAdditional: Boolean): Decision = when {
        !operatorEnabled -> Decision.Refuse(Cause.OPERATOR_SWITCH_OFF)
        hostname == null || !OperatorPolicy.isVerifierAllowed(hostname, pinned) -> Decision.Refuse(Cause.NOT_PINNED)
        !holderAllowsAdditional -> Decision.Refuse(Cause.HOLDER_SETTING_OFF)
        else -> Decision.Allow
    }

    /** Whether a predicate answer may be produced at all from a document whose passive-auth verdict is
     * ([ok], [allowed]). Same condition `MintGate` uses for tier B: anything other than `ok && allowed ==
     * true` (a tampered DG1 is `ok=true, allowed=false`; an integrity failure is `ok=false`) yields NO
     * boolean, which is a refusal and is distinct from a real `false` answer (§6.9.1 criterion 1). */
    fun documentAnswerable(ok: Boolean, allowed: Boolean?): Boolean = ok && allowed == true
}

/**
 * Item 7: the one holder setting, "Allow additional verifications", OFF by default. A tiny wrapper over a
 * boolean store so the default and the fail-closed read are unit-testable without `SharedPreferences`
 * (a stub under `isReturnDefaultValues=true`). A store that throws reads as OFF: a broken settings file
 * must never turn tier C on. The ONE writer is [setAllowAdditionalVerifications], called from the
 * Settings switch's listener.
 */
class HolderSettings(private val store: BooleanStore) {

    interface BooleanStore {
        fun read(key: String): Boolean?
        fun write(key: String, value: Boolean)
    }

    fun allowAdditionalVerifications(): Boolean =
        try {
            store.read(KEY) ?: false
        } catch (e: Exception) {
            false
        }

    fun setAllowAdditionalVerifications(value: Boolean) {
        store.write(KEY, value)
    }

    companion object {
        const val KEY = "allow_additional_verifications"
    }
}
