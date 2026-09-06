# M3 — §6.7 operator-knobs POC, device-verified (2026-09-06)

**Status**: source record for §6.7.5's POC on `feat/s67-poc` (`962ac96`, one code commit ahead of
`main` `9b28981`) — the verifier-hostname allowlist gate (`OperatorPolicy.isVerifierAllowed`,
wired at `MainActivity.applyHandoffVerificationOutcome`'s Verified branch). Closes §6.7.5's three
required proofs: a unit truth table, a source wiring trace, and a real second process (`apps/demo`)
hitting the exported surface. Does not itself close §6.7 as a whole — see "What this run did and
did NOT establish" below for the remaining open items.

**Rule for this file (carried from `M0-EVIDENCE.md` through `M3-KEYSTORE-EVIDENCE-2026-09-06.md`)**:
no PII values, ever. The document used in the device round is referred to only as "one real
document," never named beyond that.

---

## 1 — Unit truth table

```
./gradlew :app:testRegularDebugUnitTest --offline
```

Exit code: **0**. JUnit XML totals: **468 run / 0 failed / 0 errors / 0 skipped** (baseline before
this branch: 458; +10 new `OperatorPolicyTest` cases —
`apps/scanner/app/src/test/java/com/tananaev/passportreader/OperatorPolicyTest.kt`).

Red→green evidence (both reverted byte-identical afterward, full suite green again):

- Inverting the comparisons in `OperatorPolicy` → 7 of 10 `OperatorPolicyTest` cases fail.
- Hardcoding a wrong `OPERATOR_THRESHOLDS` `buildConfigField` → 4 failures spread across
  `ThresholdPolicyTest` and `OperatorPolicyTest`.

## 2 — Source wiring trace

`git grep` confirms single-writer discipline for every value `operator.json` now drives:

- `resValue("string", "app_name", …)` in `apps/scanner/app/build.gradle.kts` is the only
  main-source writer of `app_name` (the pre-existing debug source-set override, "zkagent Scanner
  (Debug)", is unchanged).
- `ThresholdPolicy.PRESETS = OperatorPolicy.THRESHOLDS` and
  `ThresholdPolicy.NAMED_EXCEPTIONS = OperatorPolicy.MULTI_THRESHOLD_VERIFIERS`.
- `OperatorPolicy.kt` is the sole reader of the `BuildConfig.OPERATOR_*` fields Gradle emits from
  `operator.json`.

Gate wiring: `MainActivity.applyHandoffVerificationOutcome`, Verified branch, before the S1
threshold block and before `verifiedRequest` is set —
`else if (!OperatorPolicy.isVerifierAllowed(gateHostname))` (`MainActivity.kt` ~line 1301).

## 3 — Build-fails-on-bad-file half

```
apps/scanner/scripts/operator-json-negative.sh
```

Exit code: **0** (all cases PASS). Five deliberately-broken `operator.json` variants, each run
against a real `:app:assembleRegularDebug --offline`, each restored to the committed reference file
afterward:

| Case | Expected rule | Result |
|---|---|---|
| Unknown top-level key | rule 8 | build exit 1, rule 8 named |
| Threshold `43` (outside `{15,16,18,21,60,65}`) | rule 3 | build exit 1, rule 3 named |
| Non-empty `tier_c_verifiers` | rule 7 | build exit 1, rule 7 named |
| Wildcard hostname in `verifiers` | rule 4 | build exit 1, rule 4 named |
| Empty `thresholds` | rule 3 | build exit 1, rule 3 named |
| Restored good file | — | build exit 0 |

## 4 — Real second process: device run

Pixel 6a, debug build of `962ac96`, `apps/demo` on `127.0.0.1:8787` via `adb reverse
tcp:8787 tcp:8787`, `LINK_SCHEME=av`, tier A "over 18", one real document.

### Round 1 — reference `operator.json` (`verifiers: ["127.0.0.1"]`)

Three taps. Device report log:

```
15:20:00  ✓ Verified — sent without identity
15:20:43  ✗ Sent, but the site rejected the response (HTTP 409)
15:21:16  ✓ [delivered]
```

The 15:20:43 refusal is the pre-existing already-used-link path (HTTP 409), not the §6.7 gate —
recorded for completeness, not as a gate result. Build stamp on device: `v0.6.1 (962ac96)`.

Verifier's own stdout: two tier-A verdicts, both `ok=true allowed=true
reason=no-evidence-required` (transaction IDs `sohDPQJH5g_jDCsH`, `VsxKl0UirB6KiCby`).
`apps/demo/data/store.json` updated at 15:21.

**Result: listed hostname (`127.0.0.1`) admits — handoff completes normally end-to-end.**

### Round 2 — `operator.json` temporarily edited to `verifiers: ["example.invalid"]`

(`rule 4` forbids an empty `verifiers` list, so a single non-matching hostname was substituted
rather than emptying it.) Rebuilt, reinstalled, then the source file was restored to the committed
reference and the tree confirmed clean before continuing.

Page reopened, button tapped — verifier created two transactions (`o2bqm07_WGxW5ywt`,
`R4nmVOeIGJYgMI_r`) — link tapped. Owner saw the refusal dialog:

> "This site (127.0.0.1) is not on this app's approved verifier list — refused."

Device report log at 15:28:48: outcome **FAIL**, "Sent: nothing left this device", "handoff:
REFUSED — operator policy (§6.7)", build stamp `962ac96-dirty` (expected — `operator.json` was
modified for this round).

Verifier stdout: **no verdict line after the two tx-created lines** — nothing reached it. The
refusal appeared before any document-details prompt, i.e. before a read/mint was attempted.

**Result: unlisted hostname (`example.invalid` in place of `127.0.0.1`) refuses in-app before any
read/mint; the site learns nothing.**

Afterwards the reference build (committed `operator.json`, `verifiers: ["127.0.0.1"]`) was
rebuilt and reinstalled; device shows `versionName 0.6.1` again.

### Environment note

A demo server from a previous session (pid started 2026-09-05 21:08) already held port 8787; a
fresh `npm start` attempt failed `EADDRINUSE` and the existing server was used instead — its stdout
is the evidence source above. Two further stale servers on 8788/8789 remain from 2026-09-03,
unrelated to this run.

## Interpretation

§6.7.5's three required proofs are all observed: (1) the unit truth table is green (468/0/0,
red→green demonstrated on two independent mutations), (2) the source wiring trace confirms a
single writer for every config-derived value and shows the gate wired at the one origin-acceptance
site, and (3) a real second process (`apps/demo`) confirms both directions live on device — a
listed hostname completes a handoff normally, an unlisted hostname is refused in-app before any
read/mint with nothing reaching the verifier — plus the build-fails-on-bad-file half (5/5 negative
cases correctly failing closed, reference file restored and building clean).

**Result: §6.7 POC PASSED, device-confirmed 2026-09-06, on `feat/s67-poc` `962ac96`.**

---

## What this run did and did NOT establish

**Did establish:**
- The verifier hostname allowlist gate exists, is unit-proven, is source-wired at the correct
  single site, and behaves correctly on a real device against a real second process in both
  directions (admit / refuse).
- The build-fails-on-bad-file half of the original POC candidate (§6.7.5's original scope) still
  holds: all 9 §6.7.4 validation rules are enforced at Gradle configure time; 5 representative bad
  files were exercised live, each failing the correct rule; the good file was restored and builds
  clean.
- Single-writer discipline for every config-derived value (`app_name`,
  `ThresholdPolicy.PRESETS`/`NAMED_EXCEPTIONS`) confirmed by `git grep`, not assumed.

**Did NOT establish (unverified on device):**
- Tier-mode "B" refusing a tier-A request — unit-tested only
  (`OperatorPolicyTest`'s `isTierAllowed` cases), not exercised against a real device/verifier
  round this session.
- `multi_threshold_verifiers` non-empty — unit-tested only (`ThresholdPolicy` tests carrying
  `OperatorPolicy.MULTI_THRESHOLD_VERIFIERS` through), not device-exercised.
- A release-signed build with the §6.7 gate — only a debug build (`962ac96`) was installed and
  exercised today; no release-signed APK was built or tested against this gate this session.
- `apps/demo`'s own verifier-side config — explicitly deferred (D84 point 4), untouched this
  session.

---

**No PII values appear anywhere above.** The document used in the device round is referred to only
as "one real document," per the task boundary for this file; no MRZ field, name, date of birth, or
document number appears.
