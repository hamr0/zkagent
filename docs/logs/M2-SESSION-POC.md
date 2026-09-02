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
| Device | Pixel 6a (`bluejay`), Android 17 (SDK 37), security patch 2026-07-05 | `strongbox_keystore` feature v300 present; **no fingerprint enrolled** (`adb shell dumpsys fingerprint`, checked 2026-08-31: `"prints":[{"id":0,"count":0,...}]`) — every run authorized via device PIN under the `BIOMETRIC_STRONG \| DEVICE_CREDENTIAL` prompt, see F9 |
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

**Correction (2026-08-31, owner-reported): the human interaction on both PASS runs was a device
PIN entered at a half-screen credential prompt, not a fingerprint touch** (see F9 — no
fingerprint is enrolled on this device, and `biometric_result: SUCCESS` is the BiometricPrompt
API's generic success field; it does not distinguish which factor authorized the prompt). The
~2.9–4.5 s the ladders show is PIN-entry time, not fingerprint-touch time; the read completing at
all after it is what this POC tests, and PIN entry is the slower of the two factors, so this is
if anything a stronger survival result than a fingerprint test would have produced.

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

> **SUPERSEDED by a follow-up test (2026-08-31, same session, `logcat-keytest.txt`).** The
> "provider-specific entry-point gap, not a hardware gap" reading above is **falsified**. A
> dedicated KEY TEST button (no NFC, no MRZ) deleted the alias, re-ran the full six-row matrix
> under the full verification chain, and attempted an actual sign per generated row. Result:
>
> ```
> key attempt a2 verification: kpgProvider=AndroidKeyStore containsAliasAfterGen=true publicKeyAlgorithm=EC publicKeyEncodedLength=91 confirmedAndroidKeyStoreKey=true
> key attempt b2 verification: kpgProvider=AndroidKeyStore containsAliasAfterGen=true publicKeyAlgorithm=EC publicKeyEncodedLength=91 confirmedAndroidKeyStoreKey=true
> ```
>
> `publicKeyAlgorithm=EC publicKeyEncodedLength=91` is **identical** to rows c and d, which are
> known P-256. The orchestrator verified on the host with openssl that a 91-byte
> SubjectPublicKeyInfo of this shape is `prime256v1` / NIST P-256
> (`openssl pkey -pubin -inform DER … -text -noout` → "Public-Key: (256 bit) / ASN1 OID:
> prime256v1 / NIST CURVE: P-256"); a genuine Ed25519 SPKI is 44 bytes with algorithm EdDSA,
> not EC. **Requesting the literal algorithm name `"Ed25519"` from AndroidKeyStore on this
> device silently returns a P-256 key.** a2 and b2 were never Ed25519 keys. Signing then
> failed exactly as expected for a mislabeled EC key:
>
> ```
> Signature.Ed25519: provider 'AndroidOpenSSL' initSign FAILED InvalidKeyException: Unknown key type: android.security.keystore2.AndroidKeyStoreECPrivateKey@…
> Signature.Ed25519: provider 'AndroidKeyStoreBCWorkaround' initSign FAILED InvalidKeyException: Keystore operation failed
> ```
>
> So Ed25519 is **not available as an AndroidKeyStore key on this device by either entry
> point, at either security level** — this is a hardware/platform gap, not a provider
> entry-point quirk. Nuance: the harness's own line `a2 … RESOLVED (confirmed genuine
> AndroidKeyStore key, level=STRONGBOX)` was *true as far as it went* — it confirmed the key
> was a real Keystore key at StrongBox level — but its verification chain never checked that
> the key was the **algorithm that had been requested**. A capability probe must assert the
> algorithm/curve of what it got, not merely that generation succeeded and the key is
> hardware-backed — the same class of gap as M0 Finding 5 (assert the negative actually
> excluded something).
>
> What did verify (orchestrator, on the host, `openssl dgst -sha256 -verify` → `Verified OK`
> for all three): row c (P-256/StrongBox, unattended diagnostic), row d (P-256/TEE,
> unattended diagnostic), and row c auth-bound (the one signature made behind the owner's
> **device PIN — corrected 2026-08-31, see F9; not a fingerprint** — via `BiometricPrompt`
> `CryptoObject`). Only the c-authbound signature is evidence about the per-use-auth-bound path;
> the diagnostic keys (c, d, a2, b2) were deliberately generated with
> `setUserAuthenticationRequired(false)` so one credential prompt sufficed for the run.
> Capability summary, quoted verbatim:
>
> ```
> ed25519_strongbox: GENERATED yes (row a2, level=STRONGBOX) | SIGNED no (no provider could initSign() this unattended diagnostic key) | VERIFIED-OFF-DEVICE (pending host check)
> ed25519_tee: GENERATED yes (row b2, level=TEE) | SIGNED no (no provider could initSign() this unattended diagnostic key) | VERIFIED-OFF-DEVICE (pending host check)
> p256_strongbox: GENERATED yes (row c, level=STRONGBOX) | SIGNED yes (unattended diagnostic key, provider=AndroidKeyStoreBCWorkaround) | VERIFIED-OFF-DEVICE (pending host check)
> p256_tee: GENERATED yes (row d, level=TEE) | SIGNED yes (unattended diagnostic key, provider=AndroidKeyStoreBCWorkaround) | VERIFIED-OFF-DEVICE (pending host check)
> ```

This POC did not decide between them — it used row c
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

Before the passing NL retry, the first NL attempt hit a real cancellation of the credential
prompt (device PIN entry, per F9 — not a fingerprint):

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

**F2 — Ed25519 is not available as an AndroidKeyStore key on this device, by either entry
point, at either security level (falsifies the original a1-vs-a2 reading; open owner decision
below).** EC curve name `"ed25519"` is rejected outright by StrongBox keygen (a1: `Unsupported
StrongBox EC: ed25519`). The literal algorithm string `"Ed25519"` (a2/b2) *appears* to
succeed — but a dedicated follow-up KEY TEST (no NFC/MRZ; see KEY-ALGORITHM MATRIX/SUPERSEDED
above) showed a2/b2 silently return `publicKeyAlgorithm=EC publicKeyEncodedLength=91`,
identical to the known-P-256 rows c/d, confirmed P-256 (`prime256v1`) by openssl off-device.
a2/b2 were never Ed25519 keys, and signing with them as Ed25519 fails with
`InvalidKeyException: Unknown key type: android.security.keystore2.AndroidKeyStoreECPrivateKey`.
The original "provider-specific entry-point gap, not a hardware gap" reading is **superseded**:
this is a hardware/platform gap, not an entry-point quirk. Lesson: a capability probe must
assert the algorithm/curve actually returned, not just that generation succeeded on a
hardware-backed key — same class as M0 Finding 5.

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
fresh biometric-**or-device-credential** authorization at sign time (§6.2 item 2's prompt is
`BIOMETRIC_STRONG | DEVICE_CREDENTIAL`; see F9 — on this device that authorization was in fact a
PIN, not a fingerprint), not a 15-second validity window, proven live by reading the mode back
from `KeyInfo` rather than trusting what was requested at generation.

**F6 — the credential prompt adds ~3–4 s inside the tap, and the tag tolerates it.** Both PASS
ladders show `biometric_succeeded` (the API field name; see F9 for what factor actually fired it
on this device) landing ~3.0–4.5 s after `biometric_prompt_shown`, entirely inside one
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

**F9 — correction, owner-reported 2026-08-31: the auth factor actually exercised across every
run was a device PIN, not a fingerprint; the strong-biometric path is untested on this device.**
Every run's half-screen credential prompt was answered by typing a PIN, not touching the
fingerprint sensor. This is a valid authorization under the `BIOMETRIC_STRONG | DEVICE_CREDENTIAL`
prompt (§6.2 item 2), but `biometric_result: SUCCESS` throughout this file is the BiometricPrompt
API's generic success field — it does not distinguish which factor authorized the prompt, and
every earlier "biometric"/"fingerprint" reference in this file describing what the owner did (not
the API/prompt itself) has been corrected in place. `adb shell dumpsys fingerprint` on this device
(checked 2026-08-31) shows zero enrolled prints (`"prints":[{"id":0,"count":0,...}]`), consistent
with the owner's report and explaining why the prompt fell through to device-credential entry.
Per-use key binding and the `CryptoObject`-authorized signature both worked correctly under
device-credential auth — the run-5 `sig_result` signed under this auth mode verified off-device
independently by the orchestrator (see INDEPENDENT SIGNATURE VERIFICATION above), so the
per-use-auth-bound path is proven for at least one factor. A PIN entry is *slower* than a
fingerprint touch, and the `IsoDep` session still survived the full ~3–4 s of it on both PASS
runs — if anything this is a stronger timing result for §6.2 item 12 than a fingerprint test
would have produced, since it exercised the slower of the two factors. What remains open: the
strong-biometric (fingerprint) path itself is **untested** on this device, and §6.2 item 2's
"biometric" wording should be read throughout as "biometric or device credential", not narrowed
to "fingerprint" — the prompt as specified (`BIOMETRIC_STRONG | DEVICE_CREDENTIAL`) always
permitted device-credential fallback, and this session is the first evidence that the fallback
path itself composes correctly with the NFC session.

---

## PENDING

- [x] **F2's escalation — RESOLVED, owner decision 2026-08-31.** No longer "is Ed25519
      available via a different entry point" — the follow-up KEY TEST showed it is not
      available on this device at all, by any entry point, at any security level. Three
      options were weighed, presented neutrally (kept visible below as the record of what was
      weighed); the owner chose **option 3, algorithm agility — support both, operator chooses
      per device capability**, in the owner's own words: signing is "another thing we mention
      honestly and operator choose and we support both" — exactly how the evidence slot itself
      already works (D24). This is now recorded in `docs/product/zkagent-prd.md` v1.18, §6.2
      items 1, 9, and 11 (amended 2026-08-31); a `sig-p256/1` evidence plug is now permitted in
      `chiproof` (candidate name only — no `Dn` assigned, "candidate decision, Dn pending").
      1. **`sig-p256/1` chiproof plug** — hardware-backed (StrongBox/TEE), biometric-bindable
         (proven live via row c auth-bound); requires amending §6.2 item 11 (currently forbids
         touching `chiproof` for this) and adding a new signature-scheme plug.
      2. **Software Ed25519** — keeps D30 unchanged as written; no StrongBox binding, no
         biometric binding, the private key is extractable software material rather than
         hardware-confined.
      3. **Both, operator chooses per device capability — CHOSEN.** The owner's stated reading
         (2026-08-31): signing algorithm is a documented device-capability variable and the
         operator picks by priority order at runtime. Costs: two code paths to maintain and
         test; still needs §6.2 item 11 amended to allow the P-256 branch (done, see above).
- [ ] **Second device, untested.** This POC ran on one Pixel 6a only; whether the same
      session composition (and the same provider/key-algorithm findings) holds on a second
      device is unverified.
- [ ] **Credential Manager / DC API provider registration.** Still unspiked — unrelated to
      this POC's scope, carried over from `m2-opening-poc-complete.md`.
- [ ] **F5 (M2-SCAN-EVIDENCE.md), the mode-radio bug.** This POC's app has no mode control by
      design (deleted along with `ResultActivity`), so it neither reproduces nor clears that
      bug — it is simply not applicable here, and §6.2 item 4 still needs its own resolution
      in the M2 build.
- [ ] **Strong-biometric (fingerprint) path, untested (F9).** Every run in this file was
      authorized via device PIN, not a fingerprint — this device has zero fingerprints enrolled
      (`dumpsys fingerprint`, checked 2026-08-31). Test the `BIOMETRIC_STRONG` fingerprint path
      on a device with a fingerprint enrolled before treating §6.2 item 12's composition result
      as covering both factors of the `BIOMETRIC_STRONG | DEVICE_CREDENTIAL` prompt.

---

**No PII values appear anywhere above.** Field names, verdict strings, timings, hashes and
exception text only, per this file's own rule and the project standard it inherits from
`M0-EVIDENCE.md` / `M2-SCAN-EVIDENCE.md`.
