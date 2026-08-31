package com.tananaev.passportreader

import android.content.pm.PackageManager
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyInfo
import android.security.keystore.KeyProperties
import android.util.Log
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.Security
import java.security.Signature
import java.security.spec.ECGenParameterSpec

/**
 * PRD v1.17 §6.2 item 1 / item 12 — the app's own device-bound attester key.
 *
 * THROWAWAY POC code (this spike is never graduated — AGENT_RULES). This is
 * NOT the D30 `sig-ed25519/1` payload/signing-layout implementation (§6.2
 * item 9) — that is a real-app concern, out of scope here. This file only
 * answers one question: which Keystore key algorithm/backing actually
 * succeeds on this device, generated once at first run, user-auth-bound,
 * and never fed into zktag derivation (M0Probe.deriveCandidates is a
 * completely separate code path and this file never touches it).
 *
 * Key-algorithm matrix (§6.2 item 1's "Ed25519 ... StrongBox-backed where
 * available" is a claim, not a given). There is no
 * `KeyProperties.KEY_ALGORITHM_ED25519` constant on any Android release —
 * the documented Android 13+ (API 33) entry point for an Ed25519 Keystore
 * key is `KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, ...)`
 * with `ECGenParameterSpec("ed25519")` + `DIGEST_NONE` (the "EC curve" path);
 * passing the literal string `"Ed25519"` as the KeyPairGenerator algorithm
 * name is a second, less-documented path some provider versions also accept.
 * Both Ed25519 entry points are attempted, at both backings, so a row-(a)
 * failure is never misattributed to "no Ed25519 hardware support" when it
 * was actually "wrong entry point for this provider" — six rows, in this
 * fixed order (coordinator correction 2026-08-31):
 *   a1. Ed25519 via EC + ECGenParameterSpec("ed25519") — StrongBox
 *   a2. Ed25519 via literal "Ed25519" algorithm name    — StrongBox
 *   b1. Ed25519 via EC + ECGenParameterSpec("ed25519") — TEE (no StrongBox)
 *   b2. Ed25519 via literal "Ed25519" algorithm name    — TEE (no StrongBox)
 *   c.  EC P-256 — StrongBox
 *   d.  EC P-256 — TEE (no StrongBox)
 * The full matrix (all six rows, exact exception class + message per
 * failure, plus per-row [KeyInfo] security-level readback so a "StrongBox
 * requested" row that actually landed as a software/TEE key is never
 * mis-reported as a clean StrongBox OK) is always reported. The REAL
 * signing key is generated from the first success in preference order
 * a1 -> b1 -> c -> d only — a2/b2 are diagnostic-only (they exist to
 * disambiguate why a1/b1 failed) and are never selected as the actual
 * signing key, even if they succeed. §6.2 item 1 + D30 assume
 * Ed25519/StrongBox; if a1 fails, that is an ESCALATION, not a silent
 * downgrade.
 *
 * ---------------------------------------------------------------------
 * Coordinator correction 2026-08-31, BUG 1 — confirmed root cause:
 * ---------------------------------------------------------------------
 * `MainApplication.onCreate()` calls
 * `Security.insertProviderAt(BouncyCastleProvider(), 1)`, i.e. it registers
 * SpongyCastle (`org.spongycastle.jce.provider.BouncyCastleProvider`,
 * `PROVIDER_NAME = "SC"` — confirmed by disassembling
 * `prov-1.58.0.0.jar`'s `BouncyCastleProvider.class` with `javap
 * -constants`) as the HIGHEST-priority JCA provider, ahead of Android's own
 * "AndroidKeyStore" provider. A plain `Signature.getInstance("SHA256withECDSA")`
 * (no explicit provider — what this file originally called) resolves
 * through `Security.getProviders()` in priority order and lands on "SC"
 * first. SpongyCastle's `SignatureSpi.engineInitSign` tries to convert the
 * given `PrivateKey` into its own internal key-parameter representation,
 * which requires `PrivateKey.getEncoded()` — but an AndroidKeyStore-backed
 * private key is an opaque hardware handle by design and `getEncoded()`
 * returns null/unusable data, so SpongyCastle throws exactly the observed
 * `InvalidKeyException: cannot identify EC private key ... no encoding for
 * EC private key`. This is a provider-resolution bug, not a StrongBox/TEE
 * hardware limitation — the correct "AndroidKeyStore" provider would have
 * handled its own opaque key fine had it been selected.
 * FIX (round 1, insufficient): do not remove `insertProviderAt(BC, 1)` —
 * `M0Probe`/JMRTD/scuba (the UNCHANGED-reuse read path) may depend on
 * SpongyCastle algorithm coverage Android's built-in provider doesn't
 * supply, and this file has no way to verify removing it wouldn't silently
 * break the chip-read path without a full on-device regression of that path
 * too. The first fix attempt skipped providers named "SC" by name and
 * picked the next one in priority order — that is guess-the-provider, and
 * run 2 on-device proved it wrong: skipping "SC" landed on "AndroidOpenSSL"
 * (Conscrypt), which ALSO cannot use an AndroidKeyStore-opaque private key
 * handle and threw `InvalidKeyException: Unknown key type
 * ...AndroidKeyStoreECPrivateKey`. FIX (round 2, actual): [resolveByAttempt]
 * tries every provider that advertises the algorithm, in priority order, by
 * actually calling `initSign()` with the real key — the first provider
 * that doesn't throw (or throws the expected `UserNotAuthenticatedException`
 * for a not-yet-authorized per-use key) wins. No provider name is assumed
 * or pinned; the winner is discovered, not guessed. Every attempt is
 * recorded via [providerTraceLines] for the report.
 */
object SessionKey {

    private const val TAG = "M2SessionKey"
    private const val KEYSTORE = "AndroidKeyStore"
    private const val ALIAS = "m2_session_poc_attester_key"
    private const val PROBE_ALIAS = "m2_session_poc_probe_key"
    private const val ED25519_LITERAL_ALGO = "Ed25519"
    private const val ED25519_CURVE_NAME = "ed25519"

    /** Fixed, non-secret test message. NOT the D30 payload layout — see class doc. */
    val TEST_MESSAGE: ByteArray = "m2-session-poc/1 attester-key liveness check".toByteArray()

    private enum class Kind { ED25519_EC_CURVE, ED25519_LITERAL, EC_P256 }

    private data class Combo(val rowId: String, val label: String, val kind: Kind, val strongBox: Boolean)

    data class Attempt(
        val rowId: String,
        val label: String,
        val strongBoxRequested: Boolean,
        var ok: Boolean = false,
        var exception: String? = null,
        /** [KeyInfo] readback for a successful attempt — null when [ok] is false. */
        var actualSecurityLevel: String? = null,
        var insideSecureHardware: Boolean? = null,
        /** Coordinator correction 2026-08-31 — independent verification fields
         * for a successful attempt, so "OK" is never taken on faith alone:
         * the actual JCA provider `KeyPairGenerator.getInstance` bound to,
         * whether the alias is really present in the AndroidKeyStore
         * KeyStore instance right after generation, and the resulting public
         * key's algorithm + DER-encoded length (a software/BC key and a
         * genuine AndroidKeyStore key can both report "OK" — these fields
         * are what distinguish them). Null when [ok] is false. */
        var kpgProviderName: String? = null,
        var containsAliasAfterGen: Boolean? = null,
        var publicKeyAlgorithm: String? = null,
        var publicKeyEncodedLength: Int? = null,
    ) {
        /** A StrongBox row that "succeeded" but actually landed as a TEE/software
         * key is a FALSE OK (coordinator correction 2026-08-31) — flagged here so
         * report text never states a bare "OK" for that case. */
        val softwareOrTeeFallbackSuspected: Boolean
            get() = ok && strongBoxRequested && actualSecurityLevel != null && actualSecurityLevel != "STRONGBOX"

        /** Full, independent confirmation this row's key is a genuine
         * AndroidKeyStore-backed key at the security level [actualSecurityLevel]
         * claims — NOT taken on faith from `ok` alone (coordinator correction
         * 2026-08-31: verify a2's StrongBox Ed25519 claim before treating it as
         * resolving the §6.2 item 1 / D30 escalation). Requires: generation
         * succeeded, the KeyPairGenerator actually bound to the "AndroidKeyStore"
         * JCA provider (not silently answered by SpongyCastle/Conscrypt under
         * the hood), the alias is really present in AndroidKeyStore right after
         * generation, and the KeyInfo readback (itself only obtainable via the
         * AndroidKeyStore KeyFactory — see [securityFacts]) did not error. */
        val confirmedAndroidKeyStoreKey: Boolean
            get() = ok &&
                kpgProviderName == "AndroidKeyStore" &&
                containsAliasAfterGen == true &&
                actualSecurityLevel != null &&
                !actualSecurityLevel!!.startsWith("ERROR") &&
                !actualSecurityLevel!!.startsWith("UNKNOWN")
    }

    data class KeyState(
        val attempts: List<Attempt>,
        val winnerRowId: String?,
        val signatureAlgorithm: String?,
        val perUseAuth: Boolean,
        val reusedExisting: Boolean,
        val securityLevel: String,
        val insideSecureHardware: Boolean?,
        val strongBoxFeaturePresent: Boolean,
        /** Actual auth-parameter mode read back from [KeyInfo] on the loaded key —
         * "PER_USE" / "WINDOW(Ns)" / "NOT_REQUIRED" / "UNKNOWN(...)". Coordinator
         * correction 2026-08-31 (BUG B): a reused key's mode must never be
         * ambiguous — this is read from the actual key, not assumed from how it
         * was originally requested. */
        val authMode: String,
    )

    // rowId matches the doc-comment labels a1/a2/b1/b2/c/d verbatim, for the report.
    private val plan = listOf(
        Combo("a1", "Ed25519(EC curve ed25519)/StrongBox", Kind.ED25519_EC_CURVE, true),
        Combo("a2", "Ed25519(literal \"Ed25519\")/StrongBox", Kind.ED25519_LITERAL, true),
        Combo("b1", "Ed25519(EC curve ed25519)/TEE", Kind.ED25519_EC_CURVE, false),
        Combo("b2", "Ed25519(literal \"Ed25519\")/TEE", Kind.ED25519_LITERAL, false),
        Combo("c", "EC-P256/StrongBox", Kind.EC_P256, true),
        Combo("d", "EC-P256/TEE", Kind.EC_P256, false),
    )
    // Preference order for the REAL signing key — a2/b2 are diagnostic-only,
    // never selected here even on success (see class doc).
    private val winnerPreference = listOf("a1", "b1", "c", "d")

    private fun deleteAlias(ks: KeyStore, alias: String) {
        try {
            if (ks.containsAlias(alias)) ks.deleteEntry(alias)
        } catch (e: Exception) {
            Log.w(TAG, "deleteAlias($alias) failed: ${e.javaClass.simpleName}")
        }
    }

    private fun kpgAlgorithmFor(kind: Kind): String = when (kind) {
        Kind.ED25519_EC_CURVE -> KeyProperties.KEY_ALGORITHM_EC
        Kind.ED25519_LITERAL -> ED25519_LITERAL_ALGO
        Kind.EC_P256 -> KeyProperties.KEY_ALGORITHM_EC
    }

    /**
     * Builds the KeyGenParameterSpec for one combo. Returns the builder plus
     * whether per-use (0-second validity) auth binding was actually applied —
     * §6.2 item 1's "pick per-use if the algorithm allows"; falls back to a
     * 15s validity window if the platform rejects per-use parameters for
     * this row (recorded per-row, not assumed uniform across the matrix).
     */
    private fun specBuilder(alias: String, kind: Kind, strongBox: Boolean): Pair<KeyGenParameterSpec.Builder, Boolean> {
        var perUseAuth = true
        val purposes = KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
        val builder = KeyGenParameterSpec.Builder(alias, purposes)
            .setUserAuthenticationRequired(true)
            .setIsStrongBoxBacked(strongBox)
        when (kind) {
            Kind.ED25519_EC_CURVE -> {
                builder.setAlgorithmParameterSpec(ECGenParameterSpec(ED25519_CURVE_NAME))
                builder.setDigests(KeyProperties.DIGEST_NONE)
            }
            Kind.EC_P256 -> {
                builder.setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
                builder.setDigests(KeyProperties.DIGEST_SHA256)
            }
            Kind.ED25519_LITERAL -> {
                // No curve/digest spec — this is the undocumented literal-name
                // entry point; recorded to see what the provider actually does
                // with it (most likely NoSuchAlgorithmException at getInstance).
            }
        }
        try {
            builder.setUserAuthenticationParameters(
                0,
                KeyProperties.AUTH_BIOMETRIC_STRONG or KeyProperties.AUTH_DEVICE_CREDENTIAL,
            )
        } catch (e: Throwable) {
            perUseAuth = false
            builder.setUserAuthenticationValidityDurationSeconds(15)
        }
        return builder to perUseAuth
    }

    /**
     * Coordinator correction 2026-08-31, BUG 2: row b1 previously reported a
     * bare "NullPointerException: ..." with no indication of WHERE it came
     * from, which read as "our probe is broken" rather than "this is the
     * Keystore's real answer". This now (a) tags every failure with the
     * exact stage it occurred at (getInstance / build / initialize /
     * generateKeyPair / readback — none of these stages contain any of our
     * own key-material logic, only calls into the platform), and (b) logs
     * the full stack trace via `Log.w(TAG, msg, e)` so the true origin is
     * inspectable in logcat, instead of just the one-line message this file
     * used to keep. No bug was found in this file's own code that could
     * produce that NPE — the exception originates inside
     * `KeyPairGenerator.generateKeyPair()`/`KeyGenParameterSpec.Builder.build()`
     * itself (platform code), which this staged capture now makes visible
     * rather than asserting.
     */
    private fun tryGenerate(ks: KeyStore, alias: String, combo: Combo): Attempt {
        val attempt = Attempt(combo.rowId, combo.label, combo.strongBox)
        var stage = "start"
        try {
            stage = "deleteAlias"
            deleteAlias(ks, alias)
            stage = "KeyPairGenerator.getInstance"
            val kpg = KeyPairGenerator.getInstance(kpgAlgorithmFor(combo.kind), KEYSTORE)
            stage = "specBuilder"
            val (builder, _) = specBuilder(alias, combo.kind, combo.strongBox)
            stage = "KeyGenParameterSpec.Builder.build"
            val spec = builder.build()
            stage = "KeyPairGenerator.initialize"
            kpg.initialize(spec)
            stage = "KeyPairGenerator.generateKeyPair"
            val keyPair = kpg.generateKeyPair()
            stage = "readback"
            val entry = ks.getEntry(alias, null) as? KeyStore.PrivateKeyEntry
            val (level, inside) = securityFacts(entry?.privateKey)
            attempt.ok = true
            attempt.actualSecurityLevel = level
            attempt.insideSecureHardware = inside
            // Coordinator correction 2026-08-31 — verify, don't assume: which
            // JCA provider did KeyPairGenerator actually bind to, is the alias
            // really present in AndroidKeyStore right now, and what does the
            // resulting public key look like. Logged per-row so a2's StrongBox
            // Ed25519 claim (or any row's) is independently checkable, not
            // self-reported.
            attempt.kpgProviderName = kpg.provider.name
            attempt.containsAliasAfterGen = ks.containsAlias(alias)
            attempt.publicKeyAlgorithm = keyPair.public.algorithm
            attempt.publicKeyEncodedLength = keyPair.public.encoded?.size
            Log.i(
                TAG,
                "key attempt ${combo.rowId} verification: kpgProvider=${attempt.kpgProviderName} " +
                    "containsAliasAfterGen=${attempt.containsAliasAfterGen} " +
                    "publicKeyAlgorithm=${attempt.publicKeyAlgorithm} " +
                    "publicKeyEncodedLength=${attempt.publicKeyEncodedLength} " +
                    "confirmedAndroidKeyStoreKey=${attempt.confirmedAndroidKeyStoreKey}",
            )
        } catch (e: Throwable) {
            attempt.exception = "[$stage] ${e.javaClass.simpleName}: ${e.message}"
            // Full stack trace to logcat (not just the message) so the real
            // origin — our code vs. platform code — is verifiable, not asserted.
            Log.w(TAG, "key attempt ${combo.rowId} (${combo.label}) FAILED at stage '$stage'", e)
        }
        return attempt
    }

    /**
     * Generates (first run) or reuses (subsequent runs) the device attester
     * key, and always returns the full six-row key-algorithm matrix on a
     * fresh-generation run. The matrix is probed with a throwaway alias so
     * the real [ALIAS] key ends up holding exactly the preferred winning
     * combination's key material, not whatever combo was tried last.
     */
    fun ensureKey(context: android.content.Context): KeyState {
        val ks = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        val strongBoxFeature = context.packageManager.hasSystemFeature(PackageManager.FEATURE_STRONGBOX_KEYSTORE)

        if (ks.containsAlias(ALIAS)) {
            val entry = ks.getEntry(ALIAS, null) as? KeyStore.PrivateKeyEntry
            val privateKey = entry?.privateKey
            val mode = authModeLabel(privateKey)
            if (mode == "PER_USE") {
                val (level, inside) = securityFacts(privateKey)
                Log.i(TAG, "key state: REUSED existing alias, alg=${privateKey?.algorithm}, level=$level, mode=$mode")
                return KeyState(
                    attempts = emptyList(),
                    winnerRowId = null,
                    signatureAlgorithm = sigAlgForActualKey(privateKey),
                    perUseAuth = true, // set at original generation time; not re-probed on reuse
                    reusedExisting = true,
                    securityLevel = level,
                    insideSecureHardware = inside,
                    strongBoxFeaturePresent = strongBoxFeature,
                    authMode = mode,
                )
            }
            // Coordinator correction 2026-08-31 (BUG B): the existing alias is
            // NOT per-use (e.g. left over from a prior run's misattributed
            // validity-window fallback, from before provider resolution was
            // fixed). Self-heal: fall through and regenerate fresh as per-use
            // — do NOT return the stale key. Never silently keep signing with
            // a key whose auth mode doesn't match what was requested.
            Log.w(
                TAG,
                "key state: existing alias is NOT per-use (mode=$mode) — regenerating fresh as per-use " +
                    "now that provider resolution (resolveByAttempt) is fixed",
            )
        }

        // First run for this alias, OR self-healing a stale non-per-use key:
        // probe all six rows, then generate the real key using the first row
        // that succeeds in winnerPreference order.
        val attempts = mutableListOf<Attempt>()
        for (combo in plan) {
            val attempt = tryGenerate(ks, PROBE_ALIAS, combo)
            deleteAlias(ks, PROBE_ALIAS)
            Log.i(
                TAG,
                "key attempt ${combo.rowId} (${combo.label}): " +
                    if (attempt.ok) {
                        "OK level=${attempt.actualSecurityLevel}" +
                            if (attempt.softwareOrTeeFallbackSuspected) " [SUSPECTED FALLBACK — StrongBox requested but not granted]" else ""
                    } else {
                        "FAILED ${attempt.exception}"
                    },
            )
            attempts.add(attempt)
        }

        val byRowId = plan.associateBy { it.rowId }
        val winnerRowId = winnerPreference.firstOrNull { rowId -> attempts.any { it.rowId == rowId && it.ok } }
        if (winnerRowId == null) {
            return KeyState(attempts, null, null, false, false, "NONE", null, strongBoxFeature, "UNKNOWN(no winning row)")
        }
        val winnerCombo = byRowId.getValue(winnerRowId)

        var perUseAuth: Boolean
        try {
            deleteAlias(ks, ALIAS)
            val kpg = KeyPairGenerator.getInstance(kpgAlgorithmFor(winnerCombo.kind), KEYSTORE)
            val (builder, perUse) = specBuilder(ALIAS, winnerCombo.kind, winnerCombo.strongBox)
            perUseAuth = perUse
            kpg.initialize(builder.build())
            kpg.generateKeyPair()
        } catch (e: Throwable) {
            // The exact combo just succeeded under the probe alias; a failure
            // here is itself a finding (non-deterministic Keystore behavior).
            Log.e(TAG, "REAL key generation for winning row $winnerRowId (${winnerCombo.label}) failed unexpectedly", e)
            return KeyState(attempts, winnerRowId, null, false, false, "ERROR", null, strongBoxFeature, "UNKNOWN(generation error)")
        }

        val entry = ks.getEntry(ALIAS, null) as KeyStore.PrivateKeyEntry
        val (level, inside) = securityFacts(entry.privateKey)
        val mode = authModeLabel(entry.privateKey)
        return KeyState(
            attempts = attempts,
            winnerRowId = winnerRowId,
            signatureAlgorithm = sigAlgForActualKey(entry.privateKey),
            perUseAuth = perUseAuth,
            reusedExisting = false,
            securityLevel = level,
            insideSecureHardware = inside,
            strongBoxFeaturePresent = strongBoxFeature,
            authMode = mode,
        )
    }

    /**
     * The Signature algorithm to use for the ACTUAL key material generated.
     * Both Ed25519 entry points (EC-curve and literal) produce a key whose
     * `PrivateKey.algorithm` is expected to report "EC" or "Ed25519"
     * depending on provider — checked at runtime rather than assumed, since
     * this is exactly the kind of provider-behavior variance this matrix
     * exists to surface. Falls back on the row that was actually used when
     * `PrivateKey.algorithm` alone is ambiguous (EC could mean P-256 or the
     * Ed25519 curve alias).
     */
    private fun sigAlgForActualKey(privateKey: PrivateKey?): String? {
        if (privateKey == null) return null
        return when (privateKey.algorithm) {
            "Ed25519", "EdDSA" -> "Ed25519"
            KeyProperties.KEY_ALGORITHM_EC -> {
                // Ambiguous by algorithm name alone (P-256 vs Ed25519-via-EC-curve).
                // Keystore reports the curve via KeyInfo/AlgorithmParameterSpec for
                // EC keys generically as "EC" regardless of curve, so callers that
                // need to disambiguate use KeyState.winnerRowId, not this string
                // alone, for report text; for actual Signature.getInstance() use,
                // "SHA256withECDSA" is correct for P-256 and WRONG for the
                // ed25519-curve case (that needs plain "Ed25519") — see
                // sigAlgForRow, which is what ensureKey/initSignature actually use.
                "SHA256withECDSA"
            }
            else -> null
        }
    }

    private fun sigAlgForRow(rowId: String?): String? = when (rowId) {
        "a1", "b1", "a2", "b2" -> "Ed25519"
        "c", "d" -> "SHA256withECDSA"
        else -> null
    }

    /** [KeyInfo] facts for the actual key material generated — attestation-free. */
    private fun securityFacts(privateKey: PrivateKey?): Pair<String, Boolean?> {
        if (privateKey == null) return "UNKNOWN" to null
        return try {
            val factory = KeyFactory.getInstance(privateKey.algorithm, KEYSTORE)
            val info = factory.getKeySpec(privateKey, KeyInfo::class.java) as KeyInfo
            val level = try {
                when (info.securityLevel) {
                    KeyProperties.SECURITY_LEVEL_STRONGBOX -> "STRONGBOX"
                    KeyProperties.SECURITY_LEVEL_TRUSTED_ENVIRONMENT -> "TEE"
                    KeyProperties.SECURITY_LEVEL_SOFTWARE -> "SOFTWARE"
                    else -> "UNKNOWN(${info.securityLevel})"
                }
            } catch (e: Throwable) {
                if (info.isInsideSecureHardware) "SECURE_HARDWARE(pre-31 API)" else "SOFTWARE(pre-31 API)"
            }
            level to info.isInsideSecureHardware
        } catch (e: Exception) {
            "ERROR(${e.javaClass.simpleName})" to null
        }
    }

    /**
     * Coordinator correction 2026-08-31 (BUG B): reads the ACTUAL auth-parameter
     * mode of a key from [KeyInfo] rather than trusting what was requested at
     * generation time — `KeyInfo.getUserAuthenticationValidityDurationSeconds()`
     * is 0 for a per-use/per-operation key (whether requested via the modern
     * `setUserAuthenticationParameters(0, ...)` or the legacy
     * `setUserAuthenticationValidityDurationSeconds(0)`), and >0 for a
     * time-bound validity window. This is what [ensureKey] uses to decide
     * whether a reused key needs to be self-healed back to per-use, and what
     * [initSignature]/the caller use to decide which post-biometric signing
     * path is correct (PER_USE: sign with the CryptoObject's own returned
     * Signature; WINDOW: re-`initSign()` after auth succeeds, never sign on a
     * Signature whose `initSign()` threw).
     */
    private fun authModeLabel(privateKey: PrivateKey?): String {
        if (privateKey == null) return "UNKNOWN(no key)"
        return try {
            val factory = KeyFactory.getInstance(privateKey.algorithm, KEYSTORE)
            val info = factory.getKeySpec(privateKey, KeyInfo::class.java) as KeyInfo
            if (!info.isUserAuthenticationRequired) return "NOT_REQUIRED"
            val dur = info.userAuthenticationValidityDurationSeconds
            when {
                dur == 0 -> "PER_USE"
                dur > 0 -> "WINDOW(${dur}s)"
                else -> "UNKNOWN(duration=$dur)"
            }
        } catch (e: Exception) {
            "UNKNOWN(${e.javaClass.simpleName})"
        }
    }

    /**
     * Coordinator correction 2026-08-31, round 2 — BUG 1 root cause was right
     * (SpongyCastle at provider priority 1) but the fix was wrong: skipping
     * providers BY NAME is guess-the-provider. Run 2 proved it — skipping
     * "SC" landed on "AndroidOpenSSL" (Conscrypt) next in priority order,
     * which ALSO cannot use an AndroidKeyStore-opaque private key handle and
     * threw `InvalidKeyException: Unknown key type ...AndroidKeyStoreECPrivateKey`.
     * Conscrypt is a real, general-purpose JCA provider on Android — it just
     * doesn't know about Keystore's opaque key objects either, exactly like
     * SpongyCastle didn't. There is no reliable way to know in advance which
     * provider name is "the right one" — it varies by algorithm and OS build
     * (expected candidate: "AndroidKeyStoreBCWorkaround", but that name is
     * NOT pinned; it must be confirmed by trying).
     *
     * Fixed by ATTEMPT-based selection instead of name-based skipping: try
     * every provider that advertises the algorithm, in priority order, by
     * actually calling `initSign()` with the real private key; the first
     * one that doesn't throw wins. `UserNotAuthenticatedException` is
     * treated specially — that is the CORRECT provider recognizing this is
     * a per-use/time-bound authenticated key that isn't authorized yet
     * (exactly the expected pre-biometric state for a `CryptoObject` flow:
     * `initSign()` succeeding-with-this-exception is what you wrap in
     * `BiometricPrompt.CryptoObject` and hand to `authenticate()` — the
     * biometric HAL authorizes the pending Keymaster operation this
     * `Signature` already holds). Any other exception means "wrong
     * provider, keep trying." Every provider attempted and its outcome is
     * recorded in [lastProviderTrace] for the value-free report.
     */
    data class ProviderAttempt(val providerName: String, val outcome: String)

    private var lastProviderTrace: List<ProviderAttempt> = emptyList()

    /** Human-readable trace lines for the report — provider name + outcome only,
     * never key material. */
    fun providerTraceLines(): List<String> = lastProviderTrace.map { "  provider='${it.providerName}': ${it.outcome}" }

    /**
     * Tries every JCA provider that advertises Signature.[algorithm], in
     * priority order, by actually calling `initSign([privateKey])`. Returns
     * the first successfully-initialized (or correctly-pending-auth)
     * `Signature`, or null if every provider rejected the key outright.
     * Records a one-line-per-provider trace via [lastProviderTrace] as a
     * side effect (POC scope — a return-tuple would be cleaner but this
     * keeps every call site simple; see class doc, BUG 1 round 2).
     */
    private fun resolveByAttempt(algorithm: String, privateKey: PrivateKey): Signature? {
        val trace = mutableListOf<ProviderAttempt>()
        var winner: Signature? = null
        for (provider in Security.getProviders()) {
            if (provider.getService("Signature", algorithm) == null) continue
            try {
                val sig = Signature.getInstance(algorithm, provider)
                try {
                    sig.initSign(privateKey)
                    trace.add(ProviderAttempt(provider.name, "initSign OK"))
                    Log.i(TAG, "Signature.$algorithm: provider '${provider.name}' initSign OK — winner")
                    winner = sig
                    break
                } catch (e: android.security.keystore.UserNotAuthenticatedException) {
                    // The CORRECT provider, recognizing an authenticated key that
                    // isn't authorized yet — exactly the expected CryptoObject
                    // pre-biometric state. Use this Signature, don't keep trying.
                    trace.add(ProviderAttempt(provider.name, "initSign PENDING_AUTH (UserNotAuthenticatedException — expected pre-biometric, using this provider)"))
                    Log.i(TAG, "Signature.$algorithm: provider '${provider.name}' needs auth (expected) — winner")
                    winner = sig
                    break
                } catch (e: Throwable) {
                    trace.add(ProviderAttempt(provider.name, "initSign FAILED ${e.javaClass.simpleName}: ${e.message}"))
                    Log.i(TAG, "Signature.$algorithm: provider '${provider.name}' initSign FAILED ${e.javaClass.simpleName}: ${e.message}")
                }
            } catch (e: Throwable) {
                trace.add(ProviderAttempt(provider.name, "getInstance FAILED ${e.javaClass.simpleName}: ${e.message}"))
                Log.i(TAG, "Signature.$algorithm: provider '${provider.name}' getInstance FAILED ${e.javaClass.simpleName}: ${e.message}")
            }
        }
        lastProviderTrace = trace
        if (winner == null) {
            Log.w(TAG, "Signature.$algorithm: no provider could initSign() this key — see providerTraceLines()")
        }
        return winner
    }

    /** Which matrix row produced the currently-installed [ALIAS] key, so
     * [initSignature] can pick the correct Signature algorithm and so a
     * runtime per-use-auth rejection can be retried against the SAME row
     * with a validity-window fallback instead of silently changing key. */
    private var lastWinnerRowId: String? = null
    /** Actual auth-parameter mode currently in effect for [ALIAS] — see
     * [authModeLabel]. Drives both [initSignature]'s fallback-retry decision
     * and the caller's post-biometric signing path choice (BUG B). */
    private var lastAuthModeLabel: String = "UNKNOWN(not yet probed)"

    /** Signature over [TEST_MESSAGE], initialized (or pending-auth) but not
     * yet consumed — wrap in a BiometricPrompt.CryptoObject before
     * authenticate(). Resolved via [resolveByAttempt] — see BUG 1 (round 2)
     * in the class doc: the winning provider is discovered by actually
     * trying initSign(), never guessed from its name.
     *
     * Coordinator note (2026-08-31): per-use auth + CryptoObject requires
     * `initSign()` before the biometric prompt. If EVERY provider rejects
     * that combination outright for this key (not the expected
     * UserNotAuthenticatedException case), this regenerates the SAME row's
     * key under a 15s validity-window fallback instead and retries once —
     * reporting which path actually ran via [currentAuthModeLabel]. Never
     * silently changes to a different row.
     */
    fun initSignature(): Signature? {
        val ks = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        val entry = ks.getEntry(ALIAS, null) as? KeyStore.PrivateKeyEntry ?: return null
        val rowId = lastWinnerRowId
        val alg = sigAlgForRow(rowId) ?: sigAlgForActualKey(entry.privateKey) ?: return null

        val sig = resolveByAttempt(alg, entry.privateKey)
        if (sig != null) return sig

        Log.w(TAG, "initSign() failed on every provider for row=$rowId alg=$alg (mode=$lastAuthModeLabel)")
        if (rowId == null || lastAuthModeLabel != "PER_USE") return null // no row to regenerate, or already on the fallback path — don't loop
        val combo = plan.firstOrNull { it.rowId == rowId } ?: return null
        Log.i(TAG, "retrying row $rowId under a 15s validity-window fallback (per-use auth rejected on every provider at initSign)")
        val fallbackOk = try {
            deleteAlias(ks, ALIAS)
            val kpg = KeyPairGenerator.getInstance(kpgAlgorithmFor(combo.kind), KEYSTORE)
            val purposes = KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
            val builder = KeyGenParameterSpec.Builder(ALIAS, purposes)
                .setUserAuthenticationRequired(true)
                .setIsStrongBoxBacked(combo.strongBox)
                .setUserAuthenticationValidityDurationSeconds(15)
            when (combo.kind) {
                Kind.ED25519_EC_CURVE -> {
                    builder.setAlgorithmParameterSpec(ECGenParameterSpec(ED25519_CURVE_NAME))
                    builder.setDigests(KeyProperties.DIGEST_NONE)
                }
                Kind.EC_P256 -> {
                    builder.setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
                    builder.setDigests(KeyProperties.DIGEST_SHA256)
                }
                Kind.ED25519_LITERAL -> {}
            }
            kpg.initialize(builder.build())
            kpg.generateKeyPair()
            true
        } catch (e2: Throwable) {
            Log.w(TAG, "validity-window fallback regeneration for row $rowId also failed: ${e2.javaClass.simpleName}: ${e2.message}")
            false
        }
        if (!fallbackOk) return null
        lastAuthModeLabel = "WINDOW(15s)"
        val retryEntry = ks.getEntry(ALIAS, null) as KeyStore.PrivateKeyEntry
        return resolveByAttempt(alg, retryEntry.privateKey)
    }

    /** Records which row/auth-mode state is current, for [initSignature]'s
     * retry logic and for report text — call once after [ensureKey]. */
    fun noteKeyState(state: KeyState) {
        lastAuthModeLabel = state.authMode
        lastWinnerRowId = if (!state.reusedExisting) state.winnerRowId else null
        // Reused across app restarts within one adb session: row identity from
        // the original generation isn't persisted (POC scope) — Signature
        // algorithm selection falls back to sigAlgForActualKey() instead.
    }

    /** Is the currently-installed key per-use (0-second validity) bound? Drives
     * which post-biometric signing path the caller should use (BUG B):
     * PER_USE -> sign with the CryptoObject's own returned Signature; anything
     * else (a validity-window key) -> re-`initSign()` after auth succeeds. */
    fun isPerUseMode(): Boolean = lastAuthModeLabel == "PER_USE"

    /** Full auth-mode label of the currently-installed key, for report text. */
    fun currentAuthModeLabel(): String = lastAuthModeLabel

    /** Runs the already-authorized [Signature] (from a successful BiometricPrompt
     * CryptoObject callback) over [TEST_MESSAGE]. Returns (sha256(signature) hex,
     * raw signature bytes) — the caller decides where the raw bytes go (never
     * the on-screen report or logcat; see [MainActivity]'s sign step, which
     * writes them only to the app's private files dir for off-device
     * verification, coordinator instruction 2026-08-31). */
    fun signTestMessage(authorizedSignature: Signature): Pair<String, ByteArray> {
        authorizedSignature.update(TEST_MESSAGE)
        val sig = authorizedSignature.sign()
        val hex = MessageDigest.getInstance("SHA-256").digest(sig).joinToString("") { "%02x".format(it) }
        return hex to sig
    }

    /** sha256(signature) hex only — never the raw signature or key material.
     * Thin wrapper over [signTestMessage] kept for call sites that only need
     * the on-screen-safe digest. */
    fun signTestMessageSha256(authorizedSignature: Signature): String = signTestMessage(authorizedSignature).first

    /**
     * DER-encoded (X.509 SubjectPublicKeyInfo) public key of the currently
     * installed [ALIAS] signing key, or null if there is none. Coordinator
     * instruction 2026-08-31: written to the app's private files dir
     * alongside the raw signature so the sign step can be verified off-device
     * with `openssl`, independent of this app's own self-reported "OK".
     */
    fun currentPublicKeyDer(): ByteArray? {
        return try {
            val ks = KeyStore.getInstance(KEYSTORE).apply { load(null) }
            ks.getCertificate(ALIAS)?.publicKey?.encoded
        } catch (e: Exception) {
            Log.w(TAG, "currentPublicKeyDer() failed: ${e.javaClass.simpleName}: ${e.message}")
            null
        }
    }
}
