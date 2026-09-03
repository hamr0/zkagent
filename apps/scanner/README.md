# zkagent M2 reference scanner

The real Android reference-scanner app for zkagent, PRD `docs/product/zkagent-prd.md`
§6.2, items 1-11. This is a **rewrite**, not a graduated spike — code was
drawn from `spikes/m2-scan` (masterlist/passive-auth read path) and
`spikes/m2-session-poc` (StrongBox key + biometric composition, item 12's
already-PASSED POC), but restructured wherever §6.2 requires it. Everything
under `spikes/` is untouched — spikes are thrown away, not moved.

This location (`apps/scanner/`, a new top-level `apps/` directory) is the
implementing agent's own call, not the owner's — trivially reversible.

## Provenance and licence

Package/namespace kept as the upstream fork's (`com.tananaev.passportreader`)
— cosmetic, matches every M0/M1/M2 spike in this repo. `applicationId` is
`com.zkagent.scanner`, distinct from every spike APK, so it installs
independently. Base app forked (transitively, through the spikes) from
[tananaev/passport-reader](https://github.com/tananaev/passport-reader);
resolve the missing upstream `LICENSE` file before any distribution (carried
over, unresolved, from every prior spike's README).

## Build

```
export JAVA_HOME=$HOME/opt/jdk-21.0.12.1+1 ANDROID_HOME=$HOME/Android/Sdk
./gradlew :app:assembleRegularDebug      # debug — one cleartext exception (10.0.2.2/localhost)
./gradlew :app:assembleRegularRelease    # release — NO cleartext exception anywhere (unsigned unless KEYSTORE_FILE etc. are set)
```

## Unit tests (no device needed)

```
export JAVA_HOME=$HOME/opt/jdk-21.0.12.1+1 ANDROID_HOME=$HOME/Android/Sdk
./gradlew :app:testRegularDebugUnitTest
```

Covers: the canonical-JSON claim encoding (`CanonicalTest`), the §6.2 item 9
signed-message byte layout against a known vector produced independently by
`spikes/m2-handoff/sig-ed25519-plug.mjs` (`EvidenceSignerTest`), and the §6.2
item 7 masterlist CMS integrity + two-bucket rule against the REAL bundled
`assets/DE_ML.ml` (`MasterlistVerifierTest`) — including a real half-truncated
negative and a flipped-signature-byte negative, both asserted to refuse
(`ok:false`), never to pass.

## Install / run

```
adb devices                       # if empty: adb kill-server && adb start-server
adb shell svc power stayon usb     # avoids the doze/relaunch dance during a session
adb install -r app/build/outputs/apk/regular/debug/app-regular-debug.apk
adb shell am start -n com.zkagent.scanner/com.tananaev.passportreader.RegularActivity
```

**Both `spikes/m2-scan`, `spikes/m2-session-poc` and this app share the exact
same activity class name** (`com.tananaev.passportreader.RegularActivity`) —
always compare the PACKAGE (`com.zkagent.scanner` here) when checking what is
in the foreground, never the activity name alone.

## What each §6.2 item maps to (file:symbol)

| Item | File(s) | Symbol |
|---|---|---|
| 1 — device key, algorithm agility | `DeviceKey.kt` | `ensureKey`, `KeyState.algorithm`/`.tradeoffNote` |
| 2 — biometric-or-device-credential gate before minting | `MainActivity.kt` | `promptAndMint` (called only from `continueAfterRead`'s mint-gate branch) |
| 3 — always read, conditionally mint | `MainActivity.kt` | `continueAfterRead`'s `mayMint` gate |
| 4 — mode captured once, structurally | `MainActivity.kt` | `lockModeAndArm` (the one `checkedRadioButtonId` call site) |
| 5 — no field rendering, no ResultActivity | (absence) | `ResultActivity.kt` deleted; `reportView` is value-free by construction |
| 6 — lifecycle wipe rules | `MainActivity.kt` | `onStop` -> `wipeSession`; access-failure branch in `ReadTask.onPostExecute` |
| 7 — masterlist CMS + two-bucket | `MasterlistVerifier.kt` | `load` |
| 8 — av:// + direct_post, QR fallback | `HandoffClient.kt`, `MainActivity.kt` (`qrScanner`/`launchQrScan`), regular-flavor manifest | `parseAvLink`, `fetchRequest`, `postDirectPost`, `launchQrScan` |
| 9 — evidence byte layout | `EvidenceSigner.kt`, `Canonical.kt` | `sigMessage`, `sign` |
| 10 — network security config | `app/src/main/res/xml/…` (release), `app/src/debug/res/xml/…` (debug) | — |
| 11 — non-goals | (absence) | no ZK prover, no mdoc/wallet code, no Credential Manager provider, no rung-2 code anywhere in this module |

The full per-item conformance table (what's verified, how, and what still
needs the owner's device) is in the implementing session's final report —
ask the orchestrator for it, or see `docs/logs/` once it is written up there
(not written by this build — see the task's "do not write to docs/ for runs
that have not happened" rule).

## Known escalations (not decided by this build — see final report)

- **`sig-p256/1` byte layout is NOT confirmed.** This device's real
  attester key is hardware P-256 (Ed25519 is unavailable — see
  `docs/logs/M2-SESSION-POC.md` F2). `EvidenceSigner.kt` signs the identical
  item-9 message bytes with ECDSA and reports `type: "sig-p256"` as this
  app's own best-effort extension, not a confirmed chiproof spec.
- **QR is a live in-app camera scan (fixed, finding #18, D68 part b,
  2026-09-03).** Superseded the earlier `TakePicturePreview` +
  `QrCapture`/`zxing-core` single-thumbnail decode, which failed to decode a
  laptop-screen-rendered `av://` link across three attempts on device.
  `com.google.android.gms:play-services-code-scanner` (Google Code Scanner
  API) is the **new dependency**, chosen over ML Kit's bundled
  `barcode-scanning` because its scan UI runs in a Play-services-owned
  delegate activity, adding neither an app-manifest `CAMERA` permission nor
  a network dependency of this app's own (see item 10's constraint above and
  `MainActivity.kt`'s `qrScanner` doc comment for the permission-inspection
  evidence). Decoded text feeds `applyPendingHandoffText` unchanged; the
  three Snackbars on that path stay logged (the earlier half of finding
  #18's fix). Both halves of finding #18 are now FIXED — see
  `.claude/remember/findings.md` #18. Not yet device-confirmed against a
  real laptop-screen QR (see the device-check steps in this build's report).
- **Incoming request-object (JAR/JWS) signatures are NOT verified** —
  there is no owner-approved `trustedChallengeIssuers` pinning surface in
  this build (D20 names the shape; §6.2 doesn't specify where an app build
  gets one). See `HandoffClient.kt`'s class doc.
- **The masterlist CMS trust anchor (`assets/csca-germany-root.der`) is
  carried forward from the M2 opening session's provenance check
  (`docs/logs/M2-SCAN-EVIDENCE.md`), not independently re-verified by this
  build.**
- **`apps/` as the top-level location is the implementing agent's call**,
  not the owner's.
