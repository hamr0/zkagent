# M2 scan spike — throwaway

**This is not zkagent.** It is a fork of `spikes/m0` (itself a modified copy of
someone else's app), used once to run PRD §6 M2's opening riskiest-assumption
POC: zktag stability across app reinstall + rescan, and masterlist
verification on the phone with its mandated negative.
**It is thrown away, never graduated, never shipped** (AGENT_RULES: never ship
the POC).

Results: [`docs/logs/M2-SCAN-EVIDENCE.md`](../../docs/logs/M2-SCAN-EVIDENCE.md).

## Provenance and licence

Forked from `spikes/m0` (2026-08-31), itself vendored from
[tananaev/passport-reader](https://github.com/tananaev/passport-reader) @
`master` (cloned 2026-08-29). Same licence caveat as `spikes/m0`: upstream is
stated Apache-2.0 but ships no `LICENSE` file in this vendored slice; resolve
before any form of distribution. Package name/namespace are still upstream's
(`com.tananaev.passportreader`) — cosmetic for a throwaway fork; the
`applicationId` is changed to `com.zkagent.m2scan` so this installs
independently of the M0 spike APK still on the test device.

## What was changed from spikes/m0, and why

| Change | Reason |
|---|---|
| **All `SharedPreferences`/`PreferenceManager` use for the typed MRZ fields removed** | NO-GO #9 hard rule. M0's `MainActivity` wrote passport number / DOB / expiry to `SharedPreferences` on every keystroke and on activity create, so the values outlived the app process on disk. This fork has no such write path at all — see the class doc at the top of `MainActivity.kt`, and grep for `SharedPreferences\|DataStore` against `app/src/main/java/`: zero hits. |
| **`wipeMrz()` added**, called after every read attempt (success or failure) and in `onPause` | Defense-in-depth: the three `EditText`s are the only place typed MRZ material lives, for the duration of one session, and they are cleared as soon as that session ends. |
| Intent-extra MRZ prefill (`passportNumber`/`dateOfBirth`/`dateOfExpiry` from the launching `Intent`) removed | Not needed here — the owner types the MRZ by hand every run per the M0/M2 protocol — and it was itself a second, unnecessary path for MRZ material to cross a process/Intent boundary. |
| `M1SodProbe.kt` deleted (raw DG1/SOD-to-disk fixture capture for a different, not-yet-built spike) | Out of scope for M2, and itself a PII-to-disk path this spike has no reason to carry forward. |
| `M1AttestProbe.kt`, `M1IntegrityProbe.kt` deleted, Play Integrity dependency and `INTERNET` permission dropped | M2's opening POC needs neither; M1's attestation questions are already answered (`docs/logs/M1-Q23-EVIDENCE.md` etc.) and out of scope here. |
| `M2MasterlistProbe.kt` added | TEST 2's desk-only half: full masterlist load (timed, memory-measured) and the mandated half-loaded-masterlist negative, runnable without a chip tap. |
| `MainActivity`'s report/negatives extended for M2 (`M2MasterlistProbe`, mode-gated derivation, `chip_auth` combined field, DS-cert validity dates surfaced) | TEST 1, TEST 2's chip-dependent half (CSCA-removed negative, reusing M0's exact mechanism), and TEST 3 (mode A must emit no zktag). |
| `ads.APPLICATION_ID` meta-data removed, `allowBackup` set `false` | Not needed; defense-in-depth against Android auto-backup carrying any state off-device (there is none to carry, by design). |
| `jnbis` dependency dropped | Unused since DG2 (facial image) is not read, same as M0. |

`M0Probe.kt` itself is **unchanged** — `deriveCandidates`, `passiveAuth`, and
`loadMasterList` are byte-for-byte the same code M0 measured, so TEST 1's tag
and TEST 2's full-load numbers are directly comparable to
`docs/logs/M0-EVIDENCE.md`.

## Privacy rules this spike follows

- The MRZ key (document number, date of birth, expiry) is **typed by hand
  every run**, never stored, never hardcoded, never committed, and is now
  wiped from the UI itself after every attempt (see above — this fork closes
  the M0 defect, it does not just repeat M0's stated intent).
- Nothing derived from a real chip is written to disk inside the repo.
- Only field *names*, counts, hashes, verdicts and timings are logged — never
  values. The derived zktag is a one-way HMAC output, not the MRZ key itself.

## Running it

```bash
export JAVA_HOME=~/opt/jdk-21.0.12.1+1 ANDROID_HOME=~/Android/Sdk
./gradlew :app:assembleRegularDebug
~/Android/Sdk/platform-tools/adb install -r app/build/outputs/apk/regular/debug/app-regular-debug.apk
~/Android/Sdk/platform-tools/adb logcat -c        # clear, so the next report is unambiguous
```

- **TEST 2 desk half (no tap needed)**: tap "Run masterlist checks" in the
  app, then `./capture-masterlist.sh`.
- **TEST 1 / TEST 2 chip half / TEST 3**: pick a mode (A or B), type the MRZ,
  tap the document, then `./capture-report.sh`.
- **TEST 1 reinstall step**: after recording the Mode-B `document_number` tag,
  `adb uninstall com.zkagent.m2scan`, reinstall the APK, clear logcat, scan the
  same document again in Mode B, and diff the two `document_number` values.
