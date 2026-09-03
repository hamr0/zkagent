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
| 8 — av:// + direct_post, QR fallback | `HandoffClient.kt`, regular-flavor manifest | `parseAvLink`, `fetchRequest`, `postDirectPost`, `applyPendingHandoffText` |
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
- **QR is a camera-app route, not an in-app scanner (D69, 2026-09-03,
  supersedes D68 part b).** The app has NO scanner dependency of any kind:
  the verifier renders `app_link_av` as a QR image on its own page, the
  person scans it with whatever camera app they already have, and that
  app's own `av://` VIEW intent lands on the SAME `RegularActivity`
  intent-filter a same-device link uses — feeding the SAME
  `applyPendingHandoffText` target as the manual-paste path. Nothing else in
  the handoff pipeline changes. The `com.google.android.gms:play-services-
  code-scanner` (Google Code Scanner API) dependency tried the same day
  under D68(b) was removed after a device test found it still runs its scan
  UI in a Play services process, pulls Google's data-transport telemetry
  into the merged manifest, and downloads its module from Google on first
  use — the app must be an independent tool with zero doubt about bytes
  reaching Google. There is no public Android intent to launch a camera app
  directly into QR-scanning mode, so the app cannot trigger this step
  itself. Device-proven twice (12:19:28, 12:19:38) —
  `docs/logs/M2-DEVICE-SESSION-2026-09-03-EVIDENCE.md` check 7. Both halves
  of finding #18 are CLOSED — see `.claude/remember/findings.md` #18.
- **`RegularActivity` is `launchMode="singleTask"` (finding #19, 2026-09-03).**
  A Chrome-tapped `av://` link and a camera-app-scanned one previously could
  land in two separate tasks under the old `singleTop`, leaving two live
  instances and two independent holders of handoff/session state; `singleTask`
  collapses every `av://` and NFC `TECH_DISCOVERED` intent onto one instance
  via `onNewIntent`, with no recreation and so no state loss.
- **Incoming request-object (JAR/JWS) signatures ARE verified** (§6.2 item
  14, D34/D37, `RequestTrust.kt`) — `client_id`/`request_uri`/`response_uri`
  must all resolve to one origin, and the request object must be a compact
  ES256 JWS verifying against a key resolved for that origin; either check
  failing is a refusal, never a warn-and-continue. Trust anchor is the
  origin itself, not an authority-bound allow-list: production resolves the
  key from `https://<origin>/.well-known/zkagent-verifier` over TLS, but the
  M2 spike (`spikes/m2-handoff`, plain `http://127.0.0.1`, no TLS, nothing
  at that well-known path) is carved out with ONE build-time pinned DEV
  public key, accepted only when the origin's scheme is `http` and its host
  is `127.0.0.1` or `localhost` — never for any other origin. "No production
  trust store yet" stands as a disclosure until a real TLS origin exists to
  test the well-known path against (D34/Q29/D37).
- **The masterlist CMS trust anchor (`assets/csca-germany-root.der`) is
  carried forward from the M2 opening session's provenance check
  (`docs/logs/M2-SCAN-EVIDENCE.md`), not independently re-verified by this
  build.**
- **`apps/` as the top-level location is the implementing agent's call**,
  not the owner's.
