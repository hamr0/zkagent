package com.tananaev.passportreader

import android.content.Context
import android.content.pm.PackageManager
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyInfo
import android.security.keystore.KeyProperties
import android.util.Log
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.Security
import java.security.Signature
import java.security.spec.ECGenParameterSpec

/**
 * §6.2 item 1 — the app's own D30 attester key. Generated once at first run,
 * StrongBox-backed where available, per-use auth bound, and used for NOTHING
 * but §6.2 item 9's evidence signature — it MUST NOT and DOES NOT feed
 * [ZktagDerivation] (a completely separate code path; grep-provable: this
 * file never imports or calls anything in that file).
 *
 * Algorithm selection (owner decision 2026-08-31, F2 resolved as algorithm
 * agility, PRD §6.2 item 1's amendment): the app selects, at first run, the
 * STRONGEST algorithm this device actually supports and reports which. On
 * the Pixel 6a that resolves to hardware-backed P-256 — Ed25519 is
 * unavailable as an AndroidKeyStore key on this device, at either security
 * level, by either entry point (`docs/logs/M2-SESSION-POC.md` F2, a KEY TEST
 * finding, not a guess). Preference order, in priority:
 *   1. Ed25519, StrongBox   (EC-curve entry point, then the literal-name
 *      entry point as a diagnostic-only disambiguator — see [Matrix])
 *   2. Ed25519, TEE
 *   3. P-256,   StrongBox   <- what this device actually lands on
 *   4. P-256,   TEE
 * A fifth option, SOFTWARE Ed25519 (no AndroidKeyStore at all — a plain JCE
 * keypair, extractable, NOT hardware-confined), is available ONLY by
 * explicit opt-in (`preferSoftwareUniformity=true`), for an adopter who
 * values algorithm uniformity over hardware custody (item 1's stated
 * trade). It is never selected automatically, and [KeyState.tradeoffNote]
 * states the trade in the exact terms item 1 requires whenever it is
 * chosen. **ESCALATION** (see conformance report): the API/config surface
 * an adopter would actually flip this preference through is not specified
 * anywhere in §6.2 — this file exposes it as a constructor-time Boolean on
 * [ensureKey] only, pending an owner decision on the real surface.
 */
object DeviceKey {

    private const val TAG = "DeviceKey"
    private const val KEYSTORE = "AndroidKeyStore"
    private const val ALIAS = "zkagent_scanner_attester_key"
    private const val PROBE_ALIAS = "zkagent_scanner_attester_probe"
    private const val SOFTWARE_ED25519_ALIAS_TAG = "software" // not a Keystore alias — see softwareEd25519Store

    /** Top of [winnerPreference] (row a1) — the algorithm/evidence-plug
     * identifier this device would use if it could. Exposed so callers (the
     * mint report) can state "requested vs used" without duplicating the
     * preference order. */
    const val PREFERRED_EVIDENCE_TYPE = "sig-ed25519/1"

    /** Registry-facing algorithm identifiers — MUST match what chiproof's
     * evidence-type registry calls these (item 9's `sig-ed25519/1` is
     * shipped; `sig-p256/1` is a CANDIDATE plug the parallel chiproof work
     * is adding — see EvidenceSigner's own escalation note). */
    enum class Algorithm(val evidenceType: String) {
        ED25519_HARDWARE("sig-ed25519"),
        ED25519_SOFTWARE("sig-ed25519"),
        P256_HARDWARE("sig-p256"),
    }

    private enum class Kind { ED25519_EC_CURVE, ED25519_LITERAL, EC_P256 }
    private data class Combo(val rowId: String, val label: String, val kind: Kind, val strongBox: Boolean, val algorithm: Algorithm)

    private val plan = listOf(
        Combo("a1", "Ed25519(EC curve)/StrongBox", Kind.ED25519_EC_CURVE, true, Algorithm.ED25519_HARDWARE),
        Combo("a2", "Ed25519(literal)/StrongBox", Kind.ED25519_LITERAL, true, Algorithm.ED25519_HARDWARE), // diagnostic-only, see winnerPreference
        Combo("b1", "Ed25519(EC curve)/TEE", Kind.ED25519_EC_CURVE, false, Algorithm.ED25519_HARDWARE),
        Combo("b2", "Ed25519(literal)/TEE", Kind.ED25519_LITERAL, false, Algorithm.ED25519_HARDWARE), // diagnostic-only
        Combo("c", "P-256/StrongBox", Kind.EC_P256, true, Algorithm.P256_HARDWARE),
        Combo("d", "P-256/TEE", Kind.EC_P256, false, Algorithm.P256_HARDWARE),
    )
    private val winnerPreference = listOf("a1", "b1", "c", "d") // a2/b2 diagnostic-only, see SessionKey precedent (F2)

    data class Attempt(
        val rowId: String,
        val ok: Boolean,
        val exception: String? = null,
        val actualSecurityLevel: String? = null,
        val publicKeyAlgorithm: String? = null,
        val publicKeyEncodedLength: Int? = null,
    )

    data class KeyState(
        val algorithm: Algorithm,
        val signatureAlgorithm: String, // JCA Signature algorithm name to use
        val securityLevel: String, // STRONGBOX | TEE | SOFTWARE
        val perUseAuth: Boolean,
        val authMode: String, // read back from KeyInfo — never assumed (F8)
        val matrix: List<Attempt>,
        val winnerRowId: String?,
        val tradeoffNote: String,
        /** Task 2 (M2 handoff-fix session): true iff this KeyState came from
         * the reuse path (existing per-use alias found), false if a fresh
         * key was generated this call. Defaulted so the generate-path and
         * software-Ed25519 call sites below don't all need updating. */
        val reusedExistingKey: Boolean = false,
    )

    private fun deleteAlias(ks: KeyStore, alias: String) {
        try {
            if (ks.containsAlias(alias)) ks.deleteEntry(alias)
        } catch (e: Exception) {
            Log.w(TAG, "deleteAlias($alias) failed: ${e.javaClass.simpleName}")
        }
    }

    private fun kpgAlgorithmFor(kind: Kind): String = when (kind) {
        Kind.ED25519_EC_CURVE -> KeyProperties.KEY_ALGORITHM_EC
        Kind.ED25519_LITERAL -> "Ed25519"
        Kind.EC_P256 -> KeyProperties.KEY_ALGORITHM_EC
    }

    private fun specBuilder(alias: String, kind: Kind, strongBox: Boolean): KeyGenParameterSpec.Builder {
        val purposes = KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
        val builder = KeyGenParameterSpec.Builder(alias, purposes)
            .setUserAuthenticationRequired(true)
            .setIsStrongBoxBacked(strongBox)
        when (kind) {
            Kind.ED25519_EC_CURVE -> {
                builder.setAlgorithmParameterSpec(ECGenParameterSpec("ed25519"))
                builder.setDigests(KeyProperties.DIGEST_NONE)
            }
            Kind.EC_P256 -> {
                builder.setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
                builder.setDigests(KeyProperties.DIGEST_SHA256)
            }
            Kind.ED25519_LITERAL -> {}
        }
        try {
            builder.setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG or KeyProperties.AUTH_DEVICE_CREDENTIAL)
        } catch (e: Throwable) {
            builder.setUserAuthenticationValidityDurationSeconds(15)
        }
        return builder
    }

    private fun securityFacts(privateKey: PrivateKey?): String {
        if (privateKey == null) return "UNKNOWN"
        return try {
            val factory = KeyFactory.getInstance(privateKey.algorithm, KEYSTORE)
            val info = factory.getKeySpec(privateKey, KeyInfo::class.java) as KeyInfo
            when (info.securityLevel) {
                KeyProperties.SECURITY_LEVEL_STRONGBOX -> "STRONGBOX"
                KeyProperties.SECURITY_LEVEL_TRUSTED_ENVIRONMENT -> "TEE"
                KeyProperties.SECURITY_LEVEL_SOFTWARE -> "SOFTWARE"
                else -> "UNKNOWN(${info.securityLevel})"
            }
        } catch (e: Exception) {
            "ERROR(${e.javaClass.simpleName})"
        }
    }

    /** F8: never assume the requested auth mode was granted — read it back. */
    private fun authModeLabel(privateKey: PrivateKey?): String {
        if (privateKey == null) return "UNKNOWN(no key)"
        return try {
            val factory = KeyFactory.getInstance(privateKey.algorithm, KEYSTORE)
            val info = factory.getKeySpec(privateKey, KeyInfo::class.java) as KeyInfo
            if (!info.isUserAuthenticationRequired) return "NOT_REQUIRED"
            when {
                info.userAuthenticationValidityDurationSeconds == 0 -> "PER_USE"
                info.userAuthenticationValidityDurationSeconds > 0 -> "WINDOW(${info.userAuthenticationValidityDurationSeconds}s)"
                else -> "UNKNOWN"
            }
        } catch (e: Exception) {
            "UNKNOWN(${e.javaClass.simpleName})"
        }
    }

    private fun tryGenerate(ks: KeyStore, alias: String, combo: Combo): Attempt {
        return try {
            deleteAlias(ks, alias)
            val kpg = KeyPairGenerator.getInstance(kpgAlgorithmFor(combo.kind), KEYSTORE)
            kpg.initialize(specBuilder(alias, combo.kind, combo.strongBox).build())
            val keyPair = kpg.generateKeyPair()
            // b1/b2 (Ed25519, TEE, no StrongBox) can silently hand back a
            // KeyPair whose public component is null instead of throwing —
            // that used to surface as a raw
            // "PublicKey.getAlgorithm() on a null object reference" NPE.
            // Make the same "unsupported" outcome a1 already reports
            // (InvalidAlgorithmParameterException: "Unsupported StrongBox EC:
            // ed25519") explicit here too, instead of leaking the NPE text.
            val publicKey = keyPair.public
                ?: throw IllegalStateException(
                    "Unsupported ${if (combo.strongBox) "StrongBox" else "TEE"} EC: ${combo.kind.name.lowercase()} (provider returned a null public key)",
                )
            val entry = ks.getEntry(alias, null) as? KeyStore.PrivateKeyEntry
            Attempt(
                rowId = combo.rowId,
                ok = true,
                actualSecurityLevel = securityFacts(entry?.privateKey),
                publicKeyAlgorithm = publicKey.algorithm,
                publicKeyEncodedLength = publicKey.encoded?.size,
            )
        } catch (e: NullPointerException) {
            // Same silent-substitution family caught one level up, in case
            // the null shows up somewhere other than keyPair.public itself
            // (e.g. inside KeyStore.getEntry for this combo).
            Attempt(
                combo.rowId,
                ok = false,
                exception = "Unsupported ${if (combo.strongBox) "StrongBox" else "TEE"} EC: ${combo.kind.name.lowercase()} (${e.javaClass.simpleName}: ${e.message})",
            )
        } catch (e: Throwable) {
            Attempt(combo.rowId, ok = false, exception = "${e.javaClass.simpleName}: ${e.message}")
        }
    }

    /** F1's fix (SpongyCastle shadowing "SC" at provider priority 1): resolve
     * the signing provider by ATTEMPT, never by name. */
    fun resolveByAttempt(algorithm: String, privateKey: PrivateKey): Signature? {
        for (provider in Security.getProviders()) {
            if (provider.getService("Signature", algorithm) == null) continue
            try {
                val sig = Signature.getInstance(algorithm, provider)
                try {
                    sig.initSign(privateKey)
                    return sig
                } catch (e: android.security.keystore.UserNotAuthenticatedException) {
                    return sig // correct provider, pending auth — expected pre-biometric state
                } catch (e: Throwable) {
                    Log.i(TAG, "Signature.$algorithm provider '${provider.name}' rejected key: ${e.javaClass.simpleName}")
                }
            } catch (e: Throwable) {
                Log.i(TAG, "Signature.$algorithm provider '${provider.name}' getInstance failed: ${e.javaClass.simpleName}")
            }
        }
        return null
    }

    private fun sigAlgForRow(rowId: String): String = when (rowId) {
        "a1", "a2", "b1", "b2" -> "Ed25519"
        else -> "SHA256withECDSA"
    }

    /**
     * Generates (first run) or reuses (later runs) the hardware attester key.
     * Always probes the full matrix on a fresh-generation run so the
     * selected algorithm is reported with evidence, not assumed.
     *
     * @param preferSoftwareUniformity item 1's adopter-chosen trade: skip
     *   hardware selection and use a SOFTWARE Ed25519 keypair instead
     *   (extractable, not StrongBox/TEE-confined). See class doc ESCALATION
     *   — the real config surface for this is not yet specified.
     */
    fun ensureKey(context: Context, preferSoftwareUniformity: Boolean = false): KeyState {
        if (preferSoftwareUniformity) return ensureSoftwareEd25519()

        val ks = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_STRONGBOX_KEYSTORE)

        if (ks.containsAlias(ALIAS)) {
            val entry = ks.getEntry(ALIAS, null) as? KeyStore.PrivateKeyEntry
            val mode = authModeLabel(entry?.privateKey)
            if (mode == "PER_USE") {
                return reuseState(entry?.privateKey, mode)
            }
            Log.w(TAG, "existing key not per-use (mode=$mode) — regenerating (F8 self-heal)")
        }

        val attempts = mutableListOf<Attempt>()
        for (combo in plan) {
            val a = tryGenerate(ks, PROBE_ALIAS, combo)
            deleteAlias(ks, PROBE_ALIAS)
            attempts.add(a)
            Log.i(TAG, "matrix ${combo.rowId}: ${if (a.ok) "OK level=${a.actualSecurityLevel}" else "FAILED ${a.exception}"}")
        }
        val byRow = plan.associateBy { it.rowId }
        val winnerRowId = winnerPreference.firstOrNull { rowId -> attempts.any { it.rowId == rowId && it.ok } }
            ?: return KeyState(Algorithm.P256_HARDWARE, "SHA256withECDSA", "NONE", false, "UNKNOWN(no winning row)", attempts, null, "no hardware key algorithm succeeded on this device")
        val winner = byRow.getValue(winnerRowId)

        deleteAlias(ks, ALIAS)
        val kpg = KeyPairGenerator.getInstance(kpgAlgorithmFor(winner.kind), KEYSTORE)
        kpg.initialize(specBuilder(ALIAS, winner.kind, winner.strongBox).build())
        kpg.generateKeyPair()
        val entry = ks.getEntry(ALIAS, null) as KeyStore.PrivateKeyEntry

        val tradeoff = if (winner.algorithm == Algorithm.P256_HARDWARE) {
            "hardware-backed P-256 selected (StrongBox where available) — the algorithm Android " +
                "guarantees at that level on this device class; Ed25519 is unavailable as an " +
                "AndroidKeyStore key on this device (F2). Trade stated per item 1: this key is " +
                "hardware-confined (never extractable) but is NOT the same algorithm software " +
                "Ed25519 clients elsewhere may present."
        } else {
            "hardware-backed Ed25519 selected (row $winnerRowId) — StrongBox/TEE-confined."
        }

        return KeyState(
            algorithm = winner.algorithm,
            signatureAlgorithm = sigAlgForRow(winnerRowId),
            securityLevel = securityFacts(entry.privateKey),
            perUseAuth = true,
            authMode = authModeLabel(entry.privateKey),
            matrix = attempts,
            winnerRowId = winnerRowId,
            tradeoffNote = tradeoff,
        )
    }

    private fun reuseState(privateKey: PrivateKey?, mode: String): KeyState {
        val alg = when (privateKey?.algorithm) {
            "Ed25519", "EdDSA" -> Algorithm.ED25519_HARDWARE
            else -> Algorithm.P256_HARDWARE
        }
        return KeyState(
            algorithm = alg,
            signatureAlgorithm = if (alg == Algorithm.ED25519_HARDWARE) "Ed25519" else "SHA256withECDSA",
            securityLevel = securityFacts(privateKey),
            perUseAuth = true,
            authMode = mode,
            matrix = emptyList(),
            winnerRowId = null,
            tradeoffNote = "reused existing device key from a prior run",
            reusedExistingKey = true,
        )
    }

    /** Returns the current [ALIAS] key's [Signature], ready for
     * `BiometricPrompt.CryptoObject` (per-use) or pending re-init after auth
     * (validity-window fallback) — F8/BUG-B discipline preserved from the
     * item-12 POC this is drawn from. */
    fun initSignature(state: KeyState): Signature? {
        val ks = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        val entry = ks.getEntry(ALIAS, null) as? KeyStore.PrivateKeyEntry ?: return null
        return resolveByAttempt(state.signatureAlgorithm, entry.privateKey)
    }

    fun currentPublicKeyDer(): ByteArray? = try {
        val ks = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        ks.getCertificate(ALIAS)?.publicKey?.encoded
    } catch (e: Exception) {
        null
    }

    /** Task 2 (M2 handoff-fix session, owner-approved scope): asserts what
     * the CURRENTLY STORED [ALIAS] key actually is, by querying [KeyInfo] on
     * it — never what was requested. Matches the "probe must assert what
     * came back" discipline (F2/KEY TEST finding: this device silently
     * substitutes P-256 for Ed25519 via AndroidKeyStore). Value-free by
     * construction: algorithm/curve names, an enum, a boolean, a duration —
     * no key bytes, no fingerprint. */
    data class KeyDetails(
        val jcaAlgorithm: String,
        val curve: String,
        val securityLevel: String,
        val origin: String,
        val userAuthRequired: Boolean,
        val authValiditySeconds: Int,
    )

    fun currentKeyDetails(): KeyDetails? {
        return try {
            val ks = KeyStore.getInstance(KEYSTORE).apply { load(null) }
            val entry = ks.getEntry(ALIAS, null) as? KeyStore.PrivateKeyEntry ?: return null
            val privateKey = entry.privateKey
            val factory = KeyFactory.getInstance(privateKey.algorithm, KEYSTORE)
            val info = factory.getKeySpec(privateKey, KeyInfo::class.java) as KeyInfo
            val curve = when (privateKey) {
                is java.security.interfaces.ECKey -> when (val fieldSize = privateKey.params.curve.field.fieldSize) {
                    256 -> "P-256"
                    else -> "EC-$fieldSize"
                }
                else -> privateKey.algorithm // e.g. "Ed25519" — JCA has no separate curve name for it
            }
            KeyDetails(
                jcaAlgorithm = privateKey.algorithm,
                curve = curve,
                securityLevel = when (info.securityLevel) {
                    KeyProperties.SECURITY_LEVEL_STRONGBOX -> "STRONGBOX"
                    KeyProperties.SECURITY_LEVEL_TRUSTED_ENVIRONMENT -> "TEE"
                    KeyProperties.SECURITY_LEVEL_SOFTWARE -> "SOFTWARE"
                    else -> "UNKNOWN(${info.securityLevel})"
                },
                origin = when (info.origin) {
                    KeyProperties.ORIGIN_GENERATED -> "GENERATED"
                    KeyProperties.ORIGIN_IMPORTED -> "IMPORTED"
                    KeyProperties.ORIGIN_UNKNOWN -> "UNKNOWN"
                    else -> "UNKNOWN(${info.origin})"
                },
                userAuthRequired = info.isUserAuthenticationRequired,
                authValiditySeconds = info.userAuthenticationValidityDurationSeconds,
            )
        } catch (e: Exception) {
            Log.w(TAG, "currentKeyDetails() failed: ${e.javaClass.simpleName}: ${e.message}")
            null
        }
    }

    // ---- software Ed25519 opt-in path (item 1's stated trade) -------------
    // Deliberately NOT AndroidKeyStore: this is the "software-extractable
    // key" half of the trade item 1 requires be stated plainly. Not wired
    // into any UI in this build — see class doc ESCALATION.

    private var softwareEd25519Store: java.security.KeyPair? = null

    private fun ensureSoftwareEd25519(): KeyState {
        val kp = softwareEd25519Store ?: run {
            val kpg = try {
                KeyPairGenerator.getInstance("Ed25519")
            } catch (e: Exception) {
                // No software Ed25519 provider on this platform level either.
                return KeyState(Algorithm.ED25519_SOFTWARE, "Ed25519", "UNAVAILABLE", false, "UNKNOWN", emptyList(), null,
                    "software Ed25519 requested but no JCE provider on this device supports it: ${e.javaClass.simpleName}")
            }
            kpg.generateKeyPair().also { softwareEd25519Store = it }
        }
        return KeyState(
            algorithm = Algorithm.ED25519_SOFTWARE,
            signatureAlgorithm = "Ed25519",
            securityLevel = "SOFTWARE",
            perUseAuth = false, // no Keystore-enforced auth binding on a plain JCE key
            authMode = "NOT_APPLICABLE(software key, no Keystore auth binding)",
            matrix = emptyList(),
            winnerRowId = null,
            tradeoffNote = "SOFTWARE Ed25519 selected (adopter opt-in, algorithm uniformity over hardware " +
                "custody): this key is extractable software material, NOT StrongBox/TEE-confined, and " +
                "NOT biometric/device-credential bound the way the hardware path is (item 2's auth gate " +
                "still applies to MINTING, but this key itself carries no Keystore auth binding).",
        )
        // Note: kp is intentionally unused in the returned state — the caller
        // uses a separate signSoftware() entry point for this path.
    }

    fun signSoftware(message: ByteArray): ByteArray? {
        val kp = softwareEd25519Store ?: return null
        val sig = Signature.getInstance("Ed25519")
        sig.initSign(kp.private)
        sig.update(message)
        return sig.sign()
    }
}
