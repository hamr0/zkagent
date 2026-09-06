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

Verifier stdout: **no verdict line after the two tx-created lines** — no verdict/presentation
reached the verifier. The refusal appeared before any document-details prompt, i.e. before a
read/mint was attempted.

**CORRECTION (2026-09-06, validation pass, G1)**: the sentence originally here ("the site learns
nothing") was wrong. At `962ac96` the gate ran only in
`applyHandoffVerificationOutcome`'s Verified branch — AFTER `HandoffClient.fetchRequestRaw` had
already GETed `request_uri` — so the unlisted host in this very round DID receive an HTTP GET
(and saw the device's IP) before being refused; `apps/demo` had no logging on that endpoint at the
time, so this round's own stdout could not show it either way. Correct statement: no
verdict/presentation reached the verifier; the `request_uri` GET did (fixed in `f9ddcb9` — the
gate now runs before that fetch, see "Validation pass (same day)" below).

**Result: unlisted hostname (`example.invalid` in place of `127.0.0.1`) refuses in-app before any
read/mint; no verdict/presentation reached the site.**

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
read/mint with no verdict/presentation reaching the verifier (see the correction above — the
`request_uri` GET itself DID reach the unlisted host at `962ac96`, fixed at `f9ddcb9`) — plus the
build-fails-on-bad-file half (5/5 negative cases correctly failing closed, reference file restored
and building clean).

**Result: §6.7 POC PASSED, device-confirmed 2026-09-06, on `feat/s67-poc` `962ac96`; the pre-fetch
gap (G1) closed same-day at `f9ddcb9` — see "Validation pass (same day)" below.**

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

## Validation pass (same day) — G1-G8, `feat/s67-poc` `f9ddcb9`

Owner instruction: "validate all and fix what passes, no regression. ALL." Eight gaps from an
orchestrator review of the POC above, worked FIX-then-VALIDATE, on the same branch.

### G1 — FIXED. Unlisted verifier was contacted before the gate.

**Confirmed defect**: `HandoffClient.fetchRequestRaw` (called from
`MainActivity.verifyPendingHandoff`) GETed `request_uri` BEFORE
`OperatorPolicy.isVerifierAllowed` ever ran (that check lived only in
`applyHandoffVerificationOutcome`'s Verified branch, downstream of JWS verification). An unlisted
site's server received an HTTP GET (and saw the device's IP) before ever being refused — round 2
above is direct evidence of this (see the correction inserted into that section).

**Fix** (`f9ddcb9`): `OperatorPolicy.gateMessageFor(hostname: String?): String?` — one pure
function, truth table (null/listed/unlisted) in `OperatorPolicyTest`. `RequestTrust.verifyHandoff`
(the item-14 pipeline extracted out of `MainActivity.verifyPendingHandoff` into a plain
Activity-free function, `resolveKey`/`fetchRaw` injected) calls it immediately after the
`client_id`/`request_uri` origin match and BEFORE `resolveKey`/`fetchRaw` are ever invoked, at
`apps/scanner/app/src/main/java/com/tananaev/passportreader/RequestTrust.kt` (the
`verifyHandoff` function, gate block directly after `val origin = requestUriOrigin`).
`MainActivity.applyHandoffVerificationOutcome`'s existing post-verification check now calls the
SAME `gateMessageFor` function (defence in depth) via one shared private function,
`MainActivity.refuseByOperatorPolicy`, used by both the new `RequestTrust.Outcome.PolicyRefused`
branch and the Verified branch.

Unit proof (`RequestTrustTest.kt`):
- `verifyHandoff - G1, unlisted hostname is refused BEFORE resolveKey or fetchRaw is ever called`
  — passes `resolveKey`/`fetchRaw` lambdas that throw `AssertionError` if invoked; the test would
  fail with that error if the gate did not return first.
- `verifyHandoff - G1, listed hostname proceeds past the gate to resolveKey and fetchRaw` — the
  positive control, asserting both lambdas WERE called.

Red→green (deliberate mutation of `OperatorPolicy.gateMessageFor`'s condition, reverted
byte-identical afterward — diff confirmed clean via `git diff`):

```
OperatorPolicyTest > gateMessageFor - unlisted hostname is refused, naming the hostname FAILED
OperatorPolicyTest > gateMessageFor - listed hostname admits (null message) FAILED
OperatorPolicyTest > gateMessageFor - null hostname is refused FAILED
RequestTrustTest > verifyHandoff - G5, a verified request whose response_uri origin differs... FAILED
RequestTrustTest > verifyHandoff - G5 positive control... FAILED
RequestTrustTest > verifyHandoff - G1, listed hostname proceeds past the gate... FAILED
RequestTrustTest > verifyHandoff - G1, unlisted hostname is refused BEFORE resolveKey... FAILED
62 tests completed, 7 failed
```

`apps/demo/server.mjs`'s `GET /wallet/request.jwt/:requestId` handler now logs
`[apps/demo] request_uri GET transactionId=... ua=...` (value-free — transactionId + user-agent
only) so a device re-run of round 2 can PROVE the fetch no longer happens for a refused host (see
device procedure below). `apps/demo` test suite unaffected: 41/41, exit 0.

### G2 — VALIDATED, CONFIRMED-from-source. The round-1 HTTP 409.

`apps/demo/server.mjs:625`, the `POST /wallet/direct_post` handler: `if (tx.status === 'done') {
sendJson(res, 409, { error: 'already_responded' }); return; }` — a transaction (keyed by `state`,
the OpenID4VP `direct_post` request id) that has already received one response refuses a second
with HTTP 409. The 15:20:43 round-1 refusal was a second tap of the SAME already-answered tier-A
link within a minute of the first successful tap — this is exactly the already-used-transaction
path, not a §6.7 gate result, confirming the original evidence log's read from source rather than
inference.

### G3 — VALIDATED (source), device round left to the owner. Tier mode "B" refusing tier-A.

`MainActivity.kt` (applyHandoffVerificationOutcome, Verified branch): the tier-mode gate
(`tier == "A" && !OperatorPolicy.isTierAllowed("A")`) runs immediately after the §6.7 hostname
gate returns null (line order confirmed by reading the function directly) and BEFORE the S1
threshold-policy check, `verifiedRequest` is ever set, or a chip read can begin — a refusal here
takes the same `refuseByOperatorPolicy` path (report-log line, `nothing left this device`,
`showBlockingNotice`) as the hostname gate. Reachable and correctly ordered. Device procedure
below (G3-recheck section) for the owner to run.

### G4 — VALIDATED, device-testable via an existing test-only affordance.

`apps/demo/server.mjs:496-519`: `POST /wallet/authorize` accepts an optional `?threshold=` query
param (one of the six D74 presets, TEST-ONLY, does not change the verifier's own configured
`THRESHOLD=18` — it only overrides the VALUE embedded in the outgoing request object's
`zkagent.challenge.threshold` field). This means asking the scanner for threshold 18 then 21 from
the SAME origin (`127.0.0.1`) is achievable today without any apps/demo code change — device
procedure below. Unit coverage already exists by name in `ThresholdPolicyTest.kt`: `named
exceptions list ships empty and 127-0-0-1 is not exempt`, `an exempt hostname is admitted for a
different preset threshold`, `shouldRecordLock is true only on first sight for a non-exempt host`.

### G5 — VALIDATED, test added. Origin-binding negative.

No such test existed before this pass (`RequestTrustTest.kt` had only unit tests of `originOf`
itself, never the full pipeline's response_uri-vs-request_uri binding). Added, using the existing
`GOOD_JWS` vector (response_uri fixed at `http://127.0.0.1:4173/wallet/direct_post` by that
vector's signature) unmodified, with a `request_uri` deliberately pointed at a DIFFERENT port
(`http://127.0.0.1:9999/...`) so the two origins cannot match:

- `verifyHandoff - G5, a verified request whose response_uri origin differs from request_uri
  origin is refused` — asserts `Outcome.Refused` with reason `origin mismatch: response_uri=...`.
- `verifyHandoff - G5 positive control, matching request_uri and response_uri origins verify` —
  same vector, `request_uri` origin matching `response_uri`'s, asserts `Outcome.Verified`.

Both included in the G1 red→green run above (both listed as FAILED under the inverted-gate
mutation is incidental — they exercise `verifyHandoff` end-to-end, which passes through the G1
gate first; a second, gate-untouched mutation was not additionally needed since these two tests'
own logic — the response_uri/request_uri comparison — was already covered by
`RequestTrustTest`'s pre-existing `origin mismatch` tests at the `originOf` level; these two are
new INTEGRATION coverage of the same invariant through the full pipeline).

### G6 — VALIDATED, gaps closed. Hostname edge cases.

- `"127.0.0.1:8787"` (port-suffixed, the exact shape M3's real dev origin is) was ALREADY in
  `OperatorPolicyTest.kt` (`a port-suffixed hostname never matches a bare allowlist entry`,
  pre-existing) — confirms the device round-1 admit is explained by
  `ThresholdPolicy.hostnameOf` stripping the port before `isVerifierAllowed` ever sees it, by
  code, not luck.
- Leading/trailing whitespace and mixed-case (IDN-style) hostname matching — NOT previously
  tested; added `leading and trailing whitespace on the hostname is trimmed before matching` and
  `mixed-case hostname matches regardless of which side is uppercase` to `OperatorPolicyTest.kt`.
- `verifiers` entries are validated at BUILD time (rule 4, `app/build.gradle.kts`'s
  `isBareHostname`) — a port-suffixed ENTRY was NOT previously exercised by
  `operator-json-negative.sh`; added case `port-suffixed-verifier` (`verifiers: ["127.0.0.1:8787"]`
  → expects `rule 4`). All 6 negative cases now pass, exit 0 (see G8).

### G7 — VALIDATED (unsigned only — owner's keystore passphrase required for a signed build).

```
env -u KEYSTORE_FILE -u KEYSTORE_PASSWORD -u KEY_ALIAS -u KEY_PASSWORD \
  ./gradlew :app:assembleRegularRelease --offline
```
Exit code: **0**. Produces `app-regular-release-unsigned.apk` (no `KEYSTORE_FILE` env var set, so
the `signingConfig` assignment in `build.gradle.kts` is skipped, matching existing behaviour —
this is not new for §6.7). `aapt2 dump badging`: `package: name='com.zkagent.scanner'
versionCode='4' versionName='0.6.1'`. `unzip classes*.dex` + `strings ... | grep -c OperatorPolicy`
on `classes.dex`: **8** occurrences — the gate class is compiled into this release build. A
release-SIGNED build with this gate still needs the owner's own `/release` pass (keystore
passphrase).

### G8 — full regression, no fixes needed beyond G1/G6 above.

| Command | Exit | Result |
|---|---|---|
| `JAVA_HOME=$HOME/.gradle/jdks/eclipse_adoptium-17-amd64-linux.2 ./gradlew :app:testRegularDebugUnitTest --offline` | 0 | **477/0/0/0** (baseline 468, +9: 5 `OperatorPolicyTest` gateMessageFor/whitespace/case cases, 4 `RequestTrustTest` verifyHandoff cases) — parsed from JUnit XML via python3 |
| `apps/scanner/scripts/operator-json-negative.sh` | 0 | 6/6 PASS (5 pre-existing + 1 new port-suffixed-verifier case), reference file restored, tree clean |
| `cd apps/demo && npm test` | 0 | 41/41 (baseline 41 — unchanged; the new `request_uri` GET log line does not affect any assertion) |
| `cd packages/chiproof && npm test` | 0 | 191/191 (baseline 191 — unchanged, no chiproof code touched this pass) |
| `cd packages/chiproof && npm run typecheck` | 0 | clean, no errors |
| `:app:assembleRegularDebug --offline` | 0 | unchanged debug build, gate compiled in |

`git status --porcelain` confirmed empty before and after every step in this pass; `operator.json`
byte-identical to the committed reference after the negative script's cleanup.

### Device procedures (for the owner to run)

**G1-recheck (round 2, again)** — expects NO `request_uri GET` log line at the verifier this time:
1. Temporarily edit `apps/scanner/operator.json`'s `verifiers` to `["example.invalid"]` (rule 4
   forbids an empty list).
2. Rebuild and install: `JAVA_HOME=... ./gradlew :app:assembleRegularDebug --offline` then install
   the APK.
3. Restore `apps/scanner/operator.json` to the committed reference and confirm `git status
   --porcelain` is clean for that file (the installed APK stays the modified build regardless).
4. On the demo page (`http://127.0.0.1:8787` via `adb reverse tcp:8787 tcp:8787`), create a
   tier-A "over 18" request and tap the resulting `av://` link on the device.
5. Watch the verifier's own terminal output continuously from before the tap until the app shows
   its refusal dialog.

Expectations, after the list above: the app shows "This site (127.0.0.1) is not on this app's
approved verifier list — refused." (or the `example.invalid`-configured wording, matching whatever
hostname string was actually put in `verifiers`); the report log shows outcome FAIL, "Sent: nothing
left this device"; the verifier's terminal shows the two `tx created` lines from page load but
NO `[apps/demo] request_uri GET ...` line at all — proving the fetch never happened this time,
unlike round 2 of the original POC run. Then rebuild and reinstall the reference (committed
`operator.json`) build before continuing to any other device work.

**G3 — tier mode "B" refusing a tier-A request:**
1. Temporarily edit `apps/scanner/operator.json`'s `tiers` field from `"A+B"` to `"B"`.
2. Rebuild and install: `JAVA_HOME=... ./gradlew :app:assembleRegularDebug --offline`, install.
3. Restore `apps/scanner/operator.json` to the committed reference; confirm `git status
   --porcelain` clean for that file.
4. On the demo page, create a tier-A "over 18" request and tap the resulting link on the device.
5. Separately, create a tier-B request and tap that link on the device.

Expectations, after the list above: the tier-A tap shows "This site asked for tier A, which this
operator does not allow (tier B only) — refused." with a FAIL report-log line naming operator
policy (§6.7); the tier-B tap proceeds through the normal flow (question line, Scan/Verify
available) with no refusal. Rebuild and reinstall the reference build afterward.

**G4 — multi_threshold_verifiers non-empty:**
1. Temporarily edit `apps/scanner/operator.json`'s `multi_threshold_verifiers` to `["127.0.0.1"]`.
2. Rebuild and install: `JAVA_HOME=... ./gradlew :app:assembleRegularDebug --offline`, install.
3. On the demo page, create a tier-B request with `POST /wallet/authorize?threshold=18` (the
   page's default button already does this) and tap the link on the device; complete the flow.
4. Create a second, separate request with `?threshold=21` from the same page/origin and tap that
   link on the device; complete the flow.
5. Restore `apps/scanner/operator.json`'s `multi_threshold_verifiers` to `[]` (the committed
   reference); confirm `git status --porcelain` clean.
6. Rebuild and install the reference build; repeat steps 3-4 with the same two thresholds.

Expectations, after the list above: with `multi_threshold_verifiers: ["127.0.0.1"]`, BOTH the
18-ask and the 21-ask are admitted (the exemption lifts the per-origin lock, D74 rule 3); with the
reference `[]` config, the FIRST threshold asked is admitted and locked, and the SECOND (different)
threshold from the same origin is refused ("but it first asked for over ..."), matching the
existing S1/D74 behaviour already device-confirmed in an earlier session.

---

**No PII values appear anywhere above.** The document used in the device round is referred to only
as "one real document," per the task boundary for this file; no MRZ field, name, date of birth, or
document number appears.

---

## Device results (evening 2026-09-06)

Orchestrator + owner, Pixel 6a, debug builds of `23c5adc` (stamped `23c5adc-dirty` for the rounds
where `operator.json` was temporarily edited; the file was restored and the tree confirmed clean
after each such build), `apps/demo` restarted from this branch's `server.mjs` on
`127.0.0.1:8787` (`LINK_SCHEME=av`) via `adb reverse tcp:8787 tcp:8787`, so the `request_uri GET`
log line added in the G1 fix was live for this run. One real document throughout.

### G1 recheck (allowlist, pre-fetch gate) — PASSED

Config `verifiers: ["example.invalid"]`. Owner tapped the tier-A button twice and the resulting
page link. Verifier stdout: `tx created E6blK6Iqw605Ab-3`, `tx created W9R2Lyty2N0AmlFb`, and NO
`request_uri GET` line for either transaction at that time — confirming the pre-fetch gate holds:
the fix from the earlier validation pass (`f9ddcb9`) is device-proven, not just unit-proven.
Device report log: `20:03:05` and `20:03:13`, both "Refused — This site (127.0.0.1) is not on
this app's approved verifier list — refused.", "nothing left this device".

**Observation for the ledger (cosmetic, not a blocker)**: both entries' header reads "Local scan
(no site)" instead of "127.0.0.1:8787" — because the pre-fetch refusal fires before the origin is
verified, while the entry's body names 127.0.0.1 regardless. Header and body disagree on whether a
site is known at refusal time; left for a later pass to reconcile, not fixed here.

### G3 (tier mode B) — PASSED both halves

Config `tiers: "B"`, `verifiers` at the reference value. Tier-A tap: device `20:06:58` "Refused —
This site asked for tier A, which this operator does not allow (tier B only) — refused.",
"nothing left this device"; verifier shows a `request_uri GET` for that transaction (expected —
tier is only known after the fetch, and the host itself is on the allowlist) and no verdict line.
Tier-B tap, full scan: device `20:08:10` "Verified — the site accepted you", "a site-only
pseudonym + a signed claim (age > 18: true)"; verifier `verdict transactionId=aWA0L6jl2_vkbSzE
tier=B threshold=18 ok=true allowed=true reason=evidence-verified evidence=["sig-p256/1"]
attester=matched`. Both halves of the G3 device procedure above are now closed; no device gap
remains for tier mode.

### G4 (multi_threshold_verifiers) — PASSED both halves, no scan needed

Origin `127.0.0.1` was already locked at threshold 18 from earlier scans, so both halves used the
demo's TEST-ONLY `?threshold=` override (`apps/demo/server.mjs` ~496-519) rather than a fresh
scan: `curl -X POST 'http://127.0.0.1:8787/ui/presentations?threshold=21' -d '{"mode":"A"}'`,
dispatching the resulting `app_link_av` with `adb shell am start -a android.intent.action.VIEW -d`.

- (a) Config `multi_threshold_verifiers: ["127.0.0.1"]`: the app displayed the question line
  "This website asks if you are over 21" with the document form — accepted, the exemption lifting
  the per-origin lock as D74 rule 3 specifies.
- (b) Reference config (`multi_threshold_verifiers: []`): logcat `20:11:50` `handoff REFUSED by
  threshold policy (S1, D74) — host=127.0.0.1 threshold=21`, and a matching report-log entry at
  `20:11:50`. Verifier: `tx created W5vV1wj7TnHubDxq mode=A threshold=18 embedded_threshold=21`, a
  `request_uri GET` for it, no verdict line.

  **CORRECTION (2026-09-06, later same day, FIX pass on `feat/s67-poc`)**: the line above
  originally also claimed the blocking notice ("This site asked for over 21, but it first asked for
  over 18 — refused.") was shown on screen. It was NOT — the refusal was computed and logged in
  code, but never reached the screen. A second, independent device capture the same day (full
  logcat, single process pid 24247) caught the actual failure: the `RegularActivity` process was
  created TWICE, ~220ms apart, for this ONE `av://` launch, and the notice was attempted on the
  Activity instance that was already being torn down. See "Root cause + fix" below. (b)'s threshold
  REFUSAL logic itself was and remains correct — this correction is about what the user actually
  saw, not about the policy decision.

Both halves of the G4 device procedure above are now closed; no device gap remains for
multi-threshold origins. The double-Activity display bug this correction describes is closed
separately — see "Root cause + fix" below.

### Root cause + fix (2026-09-06, FIX pass, HIGH severity)

**Symptom**: (b) above computed the correct threshold-policy refusal in code and logged it, but
the blocking notice never reached the screen — the owner saw the stale question line ("This
website asks if you are over 18") with the document form still active, as though nothing had
happened.

**Root cause (device-confirmed, Phase 1-3 of `/root-cause`)**: `RegularActivity` is
`launchMode="singleTask"` with a fixed `android:screenOrientation="portrait"` (Finding #19,
D63). Launching it cold — into a brand-new task, from a non-Activity-context caller (a
browser/camera tap, or `adb shell am start`, both routes this PRD documents) — can trigger the
PLATFORM's own follow-up relaunch: `ActivityTaskManager` logs `TaskLaunchParamsModifier:
... activity-requested-portrait`, then a SECOND `START ... with LAUNCH_SINGLE_TASK` issued by
`com.android.systemui`, ~150-300ms after the first. This briefly creates a second live
`RegularActivity` instance while the first is still mid-verification of the SAME `av://` handoff
— exactly the "two live `MainActivity` instances" scenario `LifecycleFence`'s own class doc
anticipates. Reproduced on-device (Pixel 6a, this session), pre-fix, across three separate `adb
install -r` + `am start` batches: 2 of 14 cold launches raced. Non-deterministic and clearly
condition-sensitive (one 6-launch batch raced zero times, a later batch on the same build raced
2 of 5) — matching the original report's own observation that "the same tier-A refusal in an
earlier round WAS seen — so recreation is not deterministic." A plain `adb shell am force-stop` +
`am start` (task already exists, no reinstall) never raced in 12 attempts — the race needs a
genuinely NEW task, which `adb install -r` reliably produces but a mere force-stop does not.
`android:resizeableActivity="false"` was tried and REFUTED as a fix (still raced at a comparable
rate, tested with a controlled single-variable A/B: 1 of 5 reinstall-launches raced with it set,
matching the ~1-in-5 to ~2-in-14 rate without it).

`LifecycleFence` already did its one job correctly: the dying instance's late-landing outcome
never touched its own torn-down window directly (no crash observed in this session's captures).
But "never touch this instance's UI" meant, before this fix, that a REFUSAL the user must be told
about was simply discarded — the dying instance's own `showBlockingNotice` call was skipped
outright (fence check ran BEFORE the threshold decision was even computed), and no other instance
ever learned what it had decided.

**Fix** (`apps/scanner/app/src/main/java/com/tananaev/passportreader/DroppedOutcomeRelay.kt`,
new; `MainActivity.kt` — `beginHandoffVerification`'s fenced landing, `showBlockingNotice`,
`showBlockingOutcomeDialog`, `onResume`): the fenced landing now ALWAYS runs
`applyHandoffVerificationOutcome` (state mutation, log, report-log persistence, and view-text
writes are all safe on an already-destroyed instance — none of them attach a new window). The one
genuinely unsafe operation, opening a NEW `AlertDialog` window, is now fence-gated at the point it
would actually show: when the owning instance's fence has already retired, the message is stashed
in `DroppedOutcomeRelay` (a small process-wide relay, deliberately NOT a wider promotion of
`pendingHandoff`/`verifiedRequest` to companion scope) instead of shown; `MainActivity.onResume`
on the next live instance consumes and shows it exactly once. No in-flight verification is
cancelled; scan-enabling state (`pendingHandoff`/`verifiedRequest`) is nulled on refusal exactly as
before, on whichever instance decided it — nothing is left armed.

**Device re-verification (this session, fixed build)**: a 12-launch `adb install -r` + `am start`
batch reproduced SOME form of the double-dispatch on 12 of 12 launches (the rate rose sharply in
this later batch — condition-sensitive, as above, not a regression introduced by the fix: the fix
does not touch launch/task behaviour at all, only what happens once a second instance exists).
Both known variants appeared: the exact race shape matching the original bug (two onCreate calls
~150-220ms apart, first instance destroyed mid-flight, 1 of 12) and the same-instance variant
where the platform's second `START` is redelivered to the SAME still-live instance via the
pre-existing `pendingHandoff !== handoff` supersede-guard (11 of 12, correctly absorbed with no
dialog ever lost — this variant was never broken). In the one destroy-and-recreate capture
destroy-and-recreate captures, the logcat now reads:
```
M2 lifecycle: fence closed — outcome still applied, UI relayed if user-facing
M2 stage: handoff REFUSED by threshold policy (S1, D74) — host=127.0.0.1 threshold=21
...
M2 stage: blocking notice shown: This site asked for over 21, but it first asked for over 18 — refused.
M2 lifecycle: fence closed — relaying blocking notice to next live instance
```
— the refusal is now always computed, logged, and either shown directly or relayed, never silently
lost. The surviving second instance's own independent verification of the same intent also reaches
the same REFUSED conclusion and shows its own notice.

**Q3 (scan-enabling state left armed?)**: NOT observed and not possible by construction — every
branch that computes a refusal (threshold policy, operator-policy hostname/tier gate, and the
generic `Outcome.Refused` path) nulls `pendingHandoff`/`verifiedRequest` on the SAME instance that
computed it, before any UI-show attempt; the relay carries only the already-decided, value-free
message text, never a handoff object, so a second instance can never resume or arm a scan from a
relayed message.

**Regression**: `testRegularDebugUnitTest` 481/0/0 (baseline 477/0/0 + 4 new
`DroppedOutcomeRelayTest` cases, JUnit XML parsed directly, not agent prose), `assembleRegularDebug`
green, `scripts/operator-json-negative.sh` all 7 cases PASS, `LifecycleFenceTest`'s forced-recreation
cases still green and unchanged.

**Note on the earlier "over 18" observation above** (20:12:04, this same file): that was
independently explained by the pre-existing "Observation" note immediately below the (now
corrected) G4 entry — the owner re-tapping a stale, ~9-minutes-expired round-2 browser tab, an
UNRELATED transaction (`W9R2Lyty2N0AmlFb`) fetched and verified fresh. It is not a symptom of the
double-Activity race this fix closes.

**Observation (pre-existing, not introduced by this branch — ledger candidate)**: at `20:12:04`
the app fetched and verified `W9R2Lyty2N0AmlFb` (created `20:03:xx` during the G1 recheck above,
`ttlMs=120000`) and displayed "over 18" — apparently the owner tapping the still-open round-2 page
link from the G1 recheck. A transaction roughly 9 minutes past its stated TTL still had its
`request_uri` served and its request object verified; expiry appears to be enforced only at
`direct_post`, not at the `request_uri` fetch. Prior M3 device sessions tested expired links only
at the post step. Whether the request fetch should also refuse on an expired `ttlMs` is a question
for review — not asserted or fixed here.
