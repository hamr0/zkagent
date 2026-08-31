# M2 — session-composition POC (§6.2 item 12, riskiest-assumption POC for the build itself)

**Status**: complete — run on both documents, **PASS** on both. This is the M2 build's own
riskiest-assumption POC (per-module rule, owner-approved 2026-08-31): before §6.2's twelve
items are built, prove the one untested interaction they all assume — that StrongBox key
generation + biometric prompt + PACE/BAC chip read compose inside a single NFC
foreground-dispatch session without the `IsoDep` tag connection being dropped by the
biometric UI interruption.

**Rule for this file (carried from `M0-EVIDENCE.md` / `M2-SCAN-EVIDENCE.md`)**: no PII
values, ever — field names, verdict strings, timings, hashes and exception text only. The raw
sign artifacts (`sig-latest.bin`, `pubkey-latest.der`) pulled off the device for independent
verification live only in the orchestrator's job tmp directory, **not** in this repo;
referenced below by hash only.

**THE POC QUESTION (§6.2 item 12)**: does an `IsoDep` session survive a biometric prompt
composed into the *same* NFC foreground-dispatch session as the chip read? **PASS** = the
session survives (`isConnected` stays true across the interruption) **and** the read
completes (DG1+SOD read, passive auth, and a StrongBox/TEE-backed signature all succeed in
one tap).

---

## SETUP — toolchain (2026-08-31)

| Component | Version | Note |
|---|---|---|
| Host | Fedora 44 | same host as M0/M1/M2-scan |
| Device | Pixel 6a (`bluejay`), Android 17 (SDK 37), security patch 2026-07-05 | `strongbox_keystore` feature v300 present; fingerprint present |
| Spike | `spikes/m2-session-poc/` — fork of `spikes/m2-scan/` | `applicationId com.zkagent.m2sessionpoc`; `ResultActivity` and the mode radio control **deleted** (§6.2 item 5 anticipated — no result screen exists to render DG1 fields); `M0Probe.kt` reused unchanged (chip read + passive auth + masterlist path) |
| Masterlist asset | Same asset as `spikes/m2-scan`, BSI provenance already re-verified 2026-08-31 (`M2-SCAN-EVIDENCE.md` SETUP) — not re-verified here | |
| New code | `SessionKey.kt` (device-key generation/signing per §6.2 item 1), `MainActivity.kt` (session composition: key → biometric → chip read → sign, one report per tap) | |

---

## RESULT — PASS on both documents

**US passport**: FAIL ×3 (runs 1–3, all key/provider bugs — see Findings), then **PASS** on
run 4 (`logcat-doc1-run4.txt`) after both bugs were fixed.
**NL ID card**: one cancelled biometric attempt (FAIL, negative evidence — see below), then
**PASS** ~38 s later on retry (`logcat-doc2.txt`).

### Six `isConnected` checkpoints, both PASS runs

| Checkpoint | US passport (run 4) | NL ID card (run 5, PASS) |
|---|---|---|
| `connect_ok` | true | true |
| `isConnected_after_key` | true | true |
| `isConnected_after_biometric` | **true** | **true** |
| `reconnected_after_loss` | false | false |
| `isConnected_after_access` | true | true |
| `isConnected_after_read` | true | true |
| `isConnected_after_sign` | true | true |

Load-bearing: `isConnected_after_biometric: true` on both documents — the tag connection was
never lost across the biometric prompt, and no reconnect was needed
(`reconnected_after_loss: false` throughout).

### Access protocol, passive auth, biometric, sign — both PASS runs

| | US passport (run 4) | NL ID card (run 5, PASS) |
|---|---|---|
| Access protocol | **BAC** — `PACE unavailable (CardServiceException: File not found, CAPDU = 00A4020C02011C, RAPDU = 6A82 (SW = 0x6A82: FILE NOT FOUND))`, then `PACE not used`, `attempting BAC`, `BAC SUCCEEDED` | **PACE SUCCEEDED** |
| `passive_auth` | `ok=true allowed=true reason=SOD signature verified to a trusted CSCA` | identical |
| `biometric_result` | `SUCCESS` | `SUCCESS` |
| `sign_result` | `OK sha256(signature)=c3724467b753418b70a27e0d53792452dbb0c5f847f1c8643770b19178a61b12` | `OK sha256(signature)=37da53a4a7145d60ede3833b40bd77f9e250525663147bb6ac6d34ae8b2a0b92` |
| `verdict` | **PASS** | **PASS** |

### Timing ladders (ms, elapsed from tag discovery), both PASS runs

| Mark | US passport, run 4 (key regenerated) | NL ID card, run 5 (key reused) |
|---|---|---|
| `tag_discovered` | 0 | 0 |
| `isodep_connect_returned` | 1 | 1 |
| `key_ready` | 482 | 16 |
| `biometric_prompt_shown` | 513 | 80 |
| `biometric_succeeded` | 4976 | 2991 |
| `access_established` (BAC/PACE) | 5413 | 3708 |
| `dg1_and_sod_read` | 6400 | 4456 |
| `passive_auth_verified` | 7132 | 5202 |
| `signed` | 7198 | 5271 |

Biometric confirmation itself takes ~2.9–4.5 s of both ladders (the human interaction, not
composition overhead); the read completing at all after it is what this POC tests.

---

## KEY-ALGORITHM MATRIX (run 4, US passport — the run where the key was regenerated fresh)

| Row | Attempt | Outcome |
|---|---|---|
| a1 | Ed25519 (EC curve `"ed25519"`) / StrongBox | **FAILED** at `KeyPairGenerator.initialize` — `InvalidAlgorithmParameterException: Unsupported StrongBox EC: ed25519`, thrown at `AndroidKeyStoreKeyPairGeneratorSpi.checkValidKeySize` |
| a2 | Ed25519 (literal string `"Ed25519"`) / StrongBox | **OK**, `level=STRONGBOX inside_secure_hardware=true` |
| b1 | Ed25519 (EC curve `"ed25519"`) / TEE | **FAILED** at `KeyPairGenerator.generateKeyPair` — `NullPointerException: Attempt to invoke interface method 'java.lang.String java.security.PublicKey.getAlgorithm()' on a null object reference`, thrown at `AndroidKeyStoreProvider.makeAndroidKeyStorePublicKeyFromKeyEntryResponse` |
| b2 | Ed25519 (literal string `"Ed25519"`) / TEE | **OK**, `level=TEE inside_secure_hardware=true` |
| c | EC-P256 / StrongBox | **OK**, `level=STRONGBOX inside_secure_hardware=true` — **winner** |
| d | EC-P256 / TEE | **OK**, `level=TEE inside_secure_hardware=true` |

**ESCALATION line, quoted verbatim from the report (not softened or upgraded):**

> `ESCALATION: a1 (Ed25519 via EC curve, StrongBox) FAILED ([KeyPairGenerator.initialize] InvalidAlgorithmParameterException: Unsupported StrongBox EC: ed25519) [a2 literal-"Ed25519" entry point DID succeed at a confirmed StrongBox level — provider-specific entry-point gap, not a hardware gap] — conflicts with §6.2 item 1 / D30's sig-ed25519/1 assumption; used c EC-P256/StrongBox instead`

This is the log's own wording, and it is deliberately **not** upgraded to "RESOLVED" or
softened to "SUSPECTED" — the log states a1 vs a2 as an observed **provider-specific
entry-point gap, not a hardware gap**, and that reading is carried forward as-is. It is an
**open escalation for the owner**: whether D30's `sig-ed25519/1` can keep both Ed25519 and
StrongBox on this hardware by always going through the literal-`"Ed25519"` entry point (a2),
or whether a `sig-p256/1` variant is needed instead (which would touch `chiproof`, currently
forbidden by §6.2 item 11's non-goals). This POC did not decide between them — it used row c
(EC-P256/StrongBox) only because it was the first row proven to sign end-to-end, not as a
resolution of the escalation.

---

## JCA PROVIDER FINDING (headline defect, runs 1–3)

`MainApplication.onCreate()` calls `Security.insertProviderAt(BouncyCastleProvider(), 1)` —
the bundled BouncyCastle jar at provider **position 1** (highest priority). `javap -constants`
against `prov-1.58.0.0.jar`'s `BouncyCastleProvider.class` shows `PROVIDER_NAME = "SC"` — this
is **SpongyCastle**, not upstream BouncyCastle, despite the import path
(`org.spongycastle.jce.provider.BouncyCastleProvider`).

A plain `Signature.getInstance("SHA256withECDSA")` with no explicit provider resolved to
`"SC"` first. SpongyCastle's `SignatureSpi.engineInitSign` needs `PrivateKey.getEncoded()` —
but an AndroidKeyStore-backed key is an opaque hardware handle and `getEncoded()` returns
null/unusable data, producing `no encoding for EC private key`. The first fix skipped
providers named `"SC"` by name; run 2 proved that wrong — skipping `"SC"` landed on
`"AndroidOpenSSL"` (Conscrypt), which **also** cannot use an opaque handle and threw
`Unknown key type: android.security.keystore2.AndroidKeyStoreECPrivateKey`.

**Fix (round 2, actual) = attempt-based resolution.** `resolveByAttempt` tries `initSign()`
on every provider that advertises the algorithm, in priority order, against the real key —
first success wins; `UserNotAuthenticatedException` is treated as "correct provider, pending
auth" (a per-use key legitimately can't sign before the biometric prompt fires). Winner on
this device: **`AndroidKeyStoreBCWorkaround`**. Three-line provider trace, verbatim (run 4):

```
provider='SC': initSign FAILED InvalidKeyException: cannot identify EC private key: java.security.InvalidKeyException: no encoding for EC private key
provider='AndroidOpenSSL': initSign FAILED InvalidKeyException: Unknown key type: android.security.keystore2.AndroidKeyStoreECPrivateKey@6678440d
provider='AndroidKeyStoreBCWorkaround': initSign OK
```

**Landmine for any JMRTD+Keystore app**, not specific to this spike — JMRTD/scuba's read path
pulls in a bundled crypto provider at high priority for algorithm coverage Android's built-in
provider lacks, so any bare `Signature.getInstance(...)` against an AndroidKeyStore key in the
same process silently resolves to the wrong one. Belongs in the M2 build as a known
constraint, not rediscovered there.

---

## VERDICT-INTEGRITY DEFECT (run 3)

Run 3's log printed `verdict: PASS` while the same session's `sign_result` was
`FAILED SignatureException: object not initialized for signature or verification` — a report
contradicting itself, the ag-001 class of failure (never claim success without checking every
recorded step). **Fixed**: `SessionReport.allStepsOk()` is now the single source of truth
both `finishSession()` and `buildReport()` read from — a step marked FAILED anywhere in the
session can never coexist with a PASS verdict again. Fixed code, quoted from
`MainActivity.kt`:

```kotlin
fun allStepsOk(): Boolean {
    if (failureStep != null) return false
    if (!connectIsConnected) return false
    if (!dg1SodRead) return false
    val pa = passiveAuthVerdict ?: return false
    if (!pa.contains("ok=true")) return false
    val sr = signResult ?: return false
    if (!sr.startsWith("OK")) return false
    return true
}
```

Confirmed working from run 4 onward: `verdict` and `sign_result` never disagreed again in
this session's captures.

---

## NEGATIVE EVIDENCE (run 5, first attempt — NL ID card)

Before the passing NL retry, the first NL attempt hit a real biometric cancellation:

```
verdict: FAIL
failure_step: biometric_prompt
failure_mode: biometric error 10: Authentication canceled
biometric_result: ERROR(10): Authentication canceled
isConnected_after_biometric: true
sign_result: not reached
passive_auth: not reached
```

`isConnected_after_biometric: true` here is the most valuable single data point in this run —
the tag survived a **cancelled** prompt, not just a successful one. The retry ~38 s later
(`17:29:59` → `17:30:36`) passed **without re-entering the MRZ**, evidencing §6.2 item 6's
keep-state-on-failure requirement. Per M0 Finding 5's lesson (a negative that silently proves
nothing is worthless): this harness distinguishes PASS from FAIL correctly — the FAIL branch
fired for a real cancellation, and every field it could have populated shows "not reached"
rather than a stale or fabricated value.

---

## INDEPENDENT SIGNATURE VERIFICATION (run outside the app, by the orchestrator)

The orchestrator pulled the run-5 PASS signature and public key off the device
(`adb exec-out run-as … sig-latest.bin` / `pubkey-latest.der`, app-private files dir, no
document content in either file) and verified them on the host, independent of the app's own
self-reported `sign_result: OK`:

- Message: the literal ASCII string `m2-session-poc/1 attester-key liveness check`
  (`SessionKey.TEST_MESSAGE`) — not document-derived.
- `openssl dgst -sha256 -verify pubkey-latest.der -keyform DER -signature sig-latest.bin msg.txt`
  → **`Verified OK`**.
- `sha256sum` of the pulled signature file: `37da53a4a7145d60ede3833b40bd77f9e250525663147bb6ac6d34ae8b2a0b92`
  — matches the value the on-device report printed for `sign_result`, byte-for-byte.
- Public key: EC, P-256 (`prime256v1` / NIST P-256), 256-bit — confirmed via `openssl ec -pubin -inform DER -text -noout`.

`sign OK` is therefore externally proven against a real cryptographic verification on the
host, not merely self-reported by the app under test. The raw signature/pubkey files
themselves stay in the orchestrator's job tmp directory and are not committed to this repo;
this file records only their hashes and the verification outcome.

---

## Findings

**F1 — SpongyCastle-at-priority-1 provider landmine (headline defect, runs 1–3).** See "JCA
PROVIDER FINDING" above; root cause identified by `javap`, not guessed, and the naive
"skip by name" fix was tried and disproven on-device before the attempt-based fix landed.

**F2 — a1-vs-a2 Ed25519 entry-point asymmetry (open escalation, not resolved here).** EC curve
name `"ed25519"` is rejected by StrongBox keygen; the literal algorithm string `"Ed25519"`
succeeds at a confirmed StrongBox level (see KEY-ALGORITHM MATRIX/ESCALATION above). Recorded
exactly as the log states it — a provider-specific entry-point gap, not a hardware gap — and
not upgraded or resolved by this POC.

**F3 — platform NPE on the TEE Ed25519-by-curve-name path (b1).** `generateKeyPair()` throws
`NullPointerException` inside
`AndroidKeyStoreProvider.makeAndroidKeyStorePublicKeyFromKeyEntryResponse` for the same
EC-curve-name entry point outside StrongBox too — the same API-misuse pattern as a1, on a
different backend, both in Android platform code, not this app's own logic.

**F4 — verdict-conjunction discipline (run 3 defect, fixed).** See VERDICT-INTEGRITY DEFECT
above; `allStepsOk()` is now the sole PASS/FAIL authority. This is the ag-001 class of failure
(a report contradicting itself).

**F5 — per-use auth via `CryptoObject` works, and is the strong form of §6.2 item 2.** Every
PASS run shows `auth_mode (read back from KeyInfo, never assumed): PER_USE` — the key requires
fresh biometric authorization at sign time, not a 15-second validity window, proven live by
reading the mode back from `KeyInfo` rather than trusting what was requested at generation.

**F6 — biometric adds ~3–4 s inside the tap, and the tag tolerates it.** Both PASS ladders show
`biometric_succeeded` landing ~3.0–4.5 s after `biometric_prompt_shown`, entirely inside one
continuous NFC session with the tag connection intact — the §6.2 item 12 result restated as a
timing fact.

**F7 — the device dozes between adb steps; evidence capture needs a fixed sequence.** Reliable
capture required wake, dismiss keyguard, force-stop, relaunch, and a focus check before the
next tap — otherwise a stale/re-delivered tag intent is ignored (`M2 stage: ignoring tag
intent — MRZ fields are empty (stale/re-delivered intent, not a new attempt)`) rather than
starting a fresh session.

**F8 — auth mode must be read back from `KeyInfo`, never assumed from what was requested.**
Run 2 shows `key state: REUSED existing alias, alg=EC, level=STRONGBOX` with no mode field,
and that reused key's `initSign()` failed outright — a window-mode key had silently persisted
from an earlier attempt and kept breaking signing across runs 1–3 until run 4 explicitly
regenerated it (`key state: existing alias is NOT per-use (mode=WINDOW(15s)) — regenerating
fresh as per-use now that provider resolution (resolveByAttempt) is fixed`).

---

## PENDING

- [ ] **F2's escalation (owner decision needed)**: keep Ed25519 via the literal `"Ed25519"`
      StrongBox entry point (a2), or accept the log's finding as reason enough to open a
      `sig-p256/1` question for `chiproof` — currently out of scope per §6.2 item 11.
- [ ] **Second device, untested.** This POC ran on one Pixel 6a only; whether the same
      session composition (and the same provider/key-algorithm findings) holds on a second
      device is unverified.
- [ ] **Credential Manager / DC API provider registration.** Still unspiked — unrelated to
      this POC's scope, carried over from `m2-opening-poc-complete.md`.
- [ ] **F5 (M2-SCAN-EVIDENCE.md), the mode-radio bug.** This POC's app has no mode control by
      design (deleted along with `ResultActivity`), so it neither reproduces nor clears that
      bug — it is simply not applicable here, and §6.2 item 4 still needs its own resolution
      in the M2 build.

---

**No PII values appear anywhere above.** Field names, verdict strings, timings, hashes and
exception text only, per this file's own rule and the project standard it inherits from
`M0-EVIDENCE.md` / `M2-SCAN-EVIDENCE.md`.
