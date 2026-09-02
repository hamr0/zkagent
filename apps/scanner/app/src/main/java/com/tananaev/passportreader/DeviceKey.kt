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
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.Security
import java.security.Signature
import java.security.spec.ECGenParameterSpec

/**
 * §6.2 item 1 — the app's own D30 attester key. Used for NOTHING but §6.2
 * item 9's evidence signature — it MUST NOT and DOES NOT feed
 * [ZktagDerivation] (a completely separate code path; grep-provable: this
 * file never imports or calls anything in that file).
 *
 * Algorithm selection (owner decision 2026-08-31, F2 resolved as algorithm
 * agility, PRD §6.2 item 1's amendment): the app selects, per key, the
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
 *
 * ---------------------------------------------------------------------
 * D38 (2026-09-01, PRD v1.20) — PER-ORIGIN keys, not one global device key:
 * ---------------------------------------------------------------------
 * "At first run" above is superseded: there is no longer one global
 * `ALIAS`-style attester key. [ensureKey] now takes an explicit `alias`, and the
 * caller ([MainActivity.continueAfterRead]) derives it — as amended below,
 * via [aliasForOriginAndZktag] — from the VERIFIED request origin
 * (`scope_domain`, D37 — the same [RequestTrust] origin, never re-derived a
 * second way). First mint at a new (origin, zktag) pair generates that
 * pair's key; a later mint at an already-seen pair reuses it. Why: a single
 * global device key was a stable cross-site identifier riding inside every
 * mode-B presentation regardless of the zktag being domain-scoped — the
 * same shape of bug D22/Q23 found in the raw attestation chain, undetected
 * until the first real mode-B run (2026-09-01, `sig_unknown_key`). The
 * alias is one-way (SHA-256) — it never carries the raw origin or zktag
 * string, same discipline as every other origin/zktag-linked value in this
 * project.
 *
 * A mode-B mint with NO verified request origin (manual mode B, no pending
 * handoff) has no origin to scope a key to and is refused by the caller
 * before [ensureKey] is ever called — see [MainActivity.continueAfterRead]'s
 * D38 guard. This file does not special-case that: every `alias` it is
 * given is assumed to already have a legitimate scope.
 *
 * Migration: the OLD global alias (`zkagent_scanner_attester_key`, pre-D38)
 * is left in the Keystore, unreferenced and undeleted — a stray key from a
 * design this file no longer uses, not touched or cleaned up here.
 *
 * [PROBE_ALIAS] keeps its pre-D38 job — the KEY TEST self-test button
 * ([MainActivity.runDeviceKeyProbe]) still targets it explicitly, entirely
 * separate from any real per-origin mint alias (different naming scheme,
 * see [aliasForOriginAndZktag] — collision is not just unlikely, it is
 * structurally impossible). [lastMintAlias] tracks the alias most recently
 * used for a REAL per-origin mint (never [PROBE_ALIAS]) — the dev export
 * (item 4, [exportDevAttesterPublicKeyIfPresent]) uses it so the owner does
 * not have to plumb the origin back in separately.
 *
 * ---------------------------------------------------------------------
 * D38 amendment (2026-09-01 live-run finding, owner decision: "isolate"):
 * ---------------------------------------------------------------------
 * PER-ORIGIN was not enough. A real run scanned two different documents
 * (NL ID card, then a US passport) at the SAME origin: both minted with the
 * SAME per-origin key (`key_id=c303cf3f731b5307`, `device_key: reused
 * existing alias`), and the verifier logged `attester=bound_first_sight`
 * for each, because [chiproof]'s attester-key store binds `(scope,
 * zktag)` and the zktag differs per document. Consequence: one relying
 * site could see that two different pseudonyms (zktags) shared one device
 * key — i.e. it learns those two identities are the same phone, a
 * cross-zktag link D38's per-origin scoping did not close.
 *
 * [aliasForOriginAndZktag] fixes this by scoping the alias to BOTH the
 * verified origin AND the zktag — one Keystore key per (origin, zktag)
 * pair, not per origin. Two documents at the same origin now mint under
 * two different aliases, so the verifier's `bound_first_sight` binding is
 * never asked to reconcile the same key against two zktags again.
 *
 * Ordering consequence: the zktag is only knowable AFTER the chip read
 * (it's derived from DG1, item 3), so the caller must derive it BEFORE
 * calling [ensureKey] — key GENERATION needs no user auth, only the key's
 * USE (the signature) does, so this reordering does not touch the
 * biometric-authorized `CryptoObject` path at all. See
 * [MainActivity.continueAfterRead]'s restructured Thread for the new
 * order: zktag derivation -> alias -> [ensureKey] -> biometric prompt ->
 * [initSignature]-backed signature.
 *
 * Migration (same stance as D38's original per-origin migration): the OLD
 * per-origin-only aliases minted under [aliasForOrigin]'s pre-amendment
 * scheme (SHA-256 of the origin alone, same `zkagent_attester_` prefix,
 * indistinguishable in the Keystore from a per-(origin,zktag) alias by
 * construction — a 128-bit truncated hash carries no tag) are left
 * untouched, unreferenced, and undeleted. They are not actively migrated
 * or cleaned up here; a future mint at that origin simply lands on a
 * different alias (scoped by zktag too) and the old one goes stale in
 * place, exactly like the pre-D38 global alias before it.
 */
object DeviceKey {

    private const val TAG = "DeviceKey"
    private const val KEYSTORE = "AndroidKeyStore"
    const val PROBE_ALIAS = "zkagent_scanner_attester_probe" // D38: exposed (not private) so MainActivity.runDeviceKeyProbe can target it explicitly
    private const val SOFTWARE_ED25519_ALIAS_TAG = "software" // not a Keystore alias — see softwareEd25519Store

    /** D38 amendment (2026-09-01 live-run finding, owner decision:
     * "isolate" — see class doc): deterministic, one-way, per-(origin,
     * zktag) Keystore alias — NOT per-origin alone (that let one verifier
     * see two documents share a device key, see class doc). `origin` MUST
     * be [RequestTrust.originOf]'s verified form (`scope_domain`, D37);
     * `zktag` MUST be the SAME `document_number`-derived value (D9) the
     * caller is about to mint evidence for — the ONE source for each, never
     * re-derived a second way.
     *
     * Hashed as `SHA-256(origin + "\n" + zktag)`, truncated to 32 hex chars
     * (128 bits). The `"\n"` separator is load-bearing, not decorative:
     * neither input can ever contain it — `origin` is a URI
     * (scheme://host[:port]) and `zktag` is MRZ-derived (`document_number`,
     * restricted to `[A-Z0-9<]`) — so `origin + "\n" + zktag` cannot be
     * produced by any other (origin', zktag') pair; concatenation alone
     * (no separator) could collide across a boundary shift (e.g.
     * origin="a", zktag="bc" vs. origin="ab", zktag="c"). Never carries the
     * raw origin or zktag string in the alias itself — same "derived, not
     * stored raw" discipline this project applies to every other
     * origin/zktag-linked value (Keystore aliases are readable by anything
     * with root/backup-level access to the keystore file). */
    fun aliasForOriginAndZktag(origin: String, zktag: String): String {
        val hex = MessageDigest.getInstance("SHA-256")
            .digest((origin + "\n" + zktag).toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
            .take(32)
        return "zkagent_attester_$hex"
    }

    /** D38 item 4 (dev export): the alias most recently used for a REAL
     * per-origin mint — set by [ensureKey] whenever it is called with
     * anything other than [PROBE_ALIAS]. In-memory only; reset on process
     * death like every other piece of session state in this app. */
    @Volatile
    private var lastMintAlias: String? = null

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
        /** D38: the Keystore alias this key actually lives under — [PROBE_ALIAS]
         * for the KEY TEST self-test, [aliasForOriginAndZktag]'s output for a
         * real per-(origin,zktag) mint, or [SOFTWARE_ED25519_ALIAS_TAG] for the
         * non-Keystore software path (informational only there — there is
         * no Keystore alias to read back). [initSignature]/report code reads
         * THIS, never a re-derived alias, so there is exactly one source for
         * "which key did we actually just use." No default — every call
         * site states its alias explicitly, same "no silent default" ethos
         * as item 13's mode capture. */
        val alias: String,
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
     * Generates (first use of `alias`) or reuses (later uses) the hardware
     * attester key AT `alias`. Always probes the full matrix on a
     * fresh-generation run so the selected algorithm is reported with
     * evidence, not assumed.
     *
     * @param alias D38: the Keystore alias to generate/reuse — [PROBE_ALIAS]
     *   for the KEY TEST self-test, or [aliasForOriginAndZktag]'s output for a
     *   real per-(origin,zktag) mint. The caller decides which; this function does not
     *   guess. [lastMintAlias] is updated here whenever `alias != PROBE_ALIAS`.
     * @param preferSoftwareUniformity item 1's adopter-chosen trade: skip
     *   hardware selection and use a SOFTWARE Ed25519 keypair instead
     *   (extractable, not StrongBox/TEE-confined). See class doc ESCALATION
     *   — the real config surface for this is not yet specified.
     */
    fun ensureKey(context: Context, alias: String, preferSoftwareUniformity: Boolean = false): KeyState {
        if (preferSoftwareUniformity) return ensureSoftwareEd25519()
        if (alias != PROBE_ALIAS) lastMintAlias = alias

        val ks = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_STRONGBOX_KEYSTORE)

        if (ks.containsAlias(alias)) {
            val entry = ks.getEntry(alias, null) as? KeyStore.PrivateKeyEntry
            val mode = authModeLabel(entry?.privateKey)
            if (mode == "PER_USE") {
                return reuseState(entry?.privateKey, mode, alias)
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
            ?: return KeyState(Algorithm.P256_HARDWARE, "SHA256withECDSA", "NONE", false, "UNKNOWN(no winning row)", attempts, null, "no hardware key algorithm succeeded on this device", alias = alias)
        val winner = byRow.getValue(winnerRowId)

        deleteAlias(ks, alias)
        val kpg = KeyPairGenerator.getInstance(kpgAlgorithmFor(winner.kind), KEYSTORE)
        kpg.initialize(specBuilder(alias, winner.kind, winner.strongBox).build())
        kpg.generateKeyPair()
        val entry = ks.getEntry(alias, null) as KeyStore.PrivateKeyEntry

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
            alias = alias,
        )
    }

    private fun reuseState(privateKey: PrivateKey?, mode: String, alias: String): KeyState {
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
            alias = alias,
        )
    }

    /** Returns [state]'s key's [Signature] — D38: reads [KeyState.alias],
     * NEVER a re-derived alias, so this always signs with the SAME key
     * [ensureKey] just generated/reused for this call — ready for
     * `BiometricPrompt.CryptoObject` (per-use) or pending re-init after auth
     * (validity-window fallback) — F8/BUG-B discipline preserved from the
     * item-12 POC this is drawn from. */
    fun initSignature(state: KeyState): Signature? {
        val ks = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        val entry = ks.getEntry(state.alias, null) as? KeyStore.PrivateKeyEntry ?: return null
        return resolveByAttempt(state.signatureAlgorithm, entry.privateKey)
    }

    /** D38: `alias` MUST be the same alias [ensureKey]/[KeyState.alias] just
     * used — this file never guesses which per-origin key a caller means. */
    fun currentPublicKeyDer(alias: String): ByteArray? = try {
        val ks = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        ks.getCertificate(alias)?.publicKey?.encoded
    } catch (e: Exception) {
        null
    }

    /** Task 2 (M2 handoff-fix session, owner-approved scope): asserts what
     * the CURRENTLY STORED key at `alias` actually is (D38: per-origin, not
     * a fixed global alias), by querying [KeyInfo] on it — never what was
     * requested. Matches the "probe must assert what came back" discipline
     * (F2/KEY TEST finding: this device silently substitutes P-256 for
     * Ed25519 via AndroidKeyStore). Value-free by construction:
     * algorithm/curve names, an enum, a boolean, a duration — no key bytes,
     * no fingerprint. */
    data class KeyDetails(
        val jcaAlgorithm: String,
        val curve: String,
        val securityLevel: String,
        val origin: String,
        val userAuthRequired: Boolean,
        val authValiditySeconds: Int,
    )

    fun currentKeyDetails(alias: String): KeyDetails? {
        return try {
            val ks = KeyStore.getInstance(KEYSTORE).apply { load(null) }
            val entry = ks.getEntry(alias, null) as? KeyStore.PrivateKeyEntry ?: return null
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

    /** DEBUG-BUILD-ONLY dev export (owner-approved, this session): writes a
     * key's public key — PEM, SubjectPublicKeyInfo/X.509, exactly
     * [currentPublicKeyDer]'s bytes, base64-wrapped at 64 columns — and its
     * `key_id` ([EvidenceSigner.keyIdFor], NOT re-derived here) to this
     * app's PRIVATE `filesDir`, as `attester_pub.pem` / `attester_key_id.txt`.
     * Lets the owner pin the real on-device attester key into the
     * `spikes/m2-handoff` dev verifier (`adb shell run-as <applicationId>
     * cat files/attester_pub.pem`).
     *
     * D38 item 4: which key? [lastMintAlias] — the alias most recently used
     * for a REAL per-origin mint — if one exists this process; otherwise
     * [PROBE_ALIAS] (the KEY TEST self-test key), since D38 has no "manual"
     * alias (a manual mode-B mint with no verified origin is now REFUSED by
     * the caller, never key-scoped — see [MainActivity.continueAfterRead]'s
     * D38 guard). Logs which source it used — never the alias/origin VALUE
     * itself, just which of the two categories.
     *
     * Two hard rules, unchanged from pre-D38:
     *  - NEVER generates a key as a side effect — a no-op (logged, no file
     *    write) if the chosen alias does not already exist. Triggered only
     *    from a long-press on the existing KEY TEST button ([MainActivity]).
     *  - NEVER logs the key bytes or the `key_id` VALUE — a public key is a
     *    stable per-device identifier (per-module memory rule: keep
     *    device-linkable material out of any transcript-visible surface,
     *    same discipline as [MainActivity.emitReport]'s value-free report).
     *    Exactly one fixed, value-free log line on success; failures log
     *    only the exception class, never key material.
     *
     * Gated on [BuildConfig.DEBUG] here AND by the caller (defense in
     * depth) — absent from any release build; not wired to any release-path
     * UI element. */
    fun exportDevAttesterPublicKeyIfPresent(context: Context) {
        if (!BuildConfig.DEBUG) return
        val alias = lastMintAlias ?: PROBE_ALIAS
        val sourceLabel = if (alias == PROBE_ALIAS) "KEY TEST probe key (no per-origin mint yet this process)" else "most recently minted per-origin key"
        try {
            val ks = KeyStore.getInstance(KEYSTORE).apply { load(null) }
            if (!ks.containsAlias(alias)) {
                Log.i(TAG, "DeviceKey: dev export skipped — no key present yet for source=$sourceLabel")
                return
            }
            val der = ks.getCertificate(alias)?.publicKey?.encoded
            if (der == null) {
                Log.w(TAG, "DeviceKey: dev export FAILED — could not read public key bytes")
                return
            }
            val pem = buildString {
                append("-----BEGIN PUBLIC KEY-----\n")
                append(java.util.Base64.getMimeEncoder(64, byteArrayOf('\n'.code.toByte())).encodeToString(der))
                append("\n-----END PUBLIC KEY-----\n")
            }
            val keyId = EvidenceSigner.keyIdFor(der)
            context.openFileOutput("attester_pub.pem", Context.MODE_PRIVATE).use { it.write(pem.toByteArray(Charsets.UTF_8)) }
            context.openFileOutput("attester_key_id.txt", Context.MODE_PRIVATE).use { it.write(keyId.toByteArray(Charsets.UTF_8)) }
            Log.i(TAG, "DeviceKey: dev export written (public key + key_id) to filesDir — source=$sourceLabel")
        } catch (e: Exception) {
            Log.w(TAG, "DeviceKey: dev export FAILED: ${e.javaClass.simpleName}")
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
                    "software Ed25519 requested but no JCE provider on this device supports it: ${e.javaClass.simpleName}",
                    alias = SOFTWARE_ED25519_ALIAS_TAG)
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
            // Not a real Keystore alias — initSignature()/currentPublicKeyDer()
            // don't apply to this path (see signSoftware()); informational only.
            alias = SOFTWARE_ED25519_ALIAS_TAG,
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
