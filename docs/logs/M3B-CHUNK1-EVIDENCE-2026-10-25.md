# M3b chunk 1 — tier C in the app, device run (Pixel 6a, 2026-10-25): PASSED

**Status**: evidence record for build chunk 1 of milestones.md §6.9.7 (D109(l)): the app side of tier C (five
predicate kinds, Settings switch, shared age lock, all-or-nothing). Written after the 2026-10-25 evening device run.
No pre-registered cell was edited after the fact; where something was not confirmed, it says so.

**Branch / commit**: `feat/m3b-tier-c`, HEAD `e480136`. The APKs were built from `f379f9b`; the scanner is unchanged in
`e480136` (that commit touches only the demo server).

**Rule for this file (carried from `M3B-POC-EVIDENCE-2026-10-25.md`)**: no PII, ever. Field NAMES, booleans, HTTP
statuses, transaction ids, timings and dialog texts only. No name, document number, date of birth, expiry date, or
nationality code of the owner appears here. The two real documents are "the US passport" and "the NL ID card". The
owner's own nationality code is deliberately not recorded; the nationality answers below are booleans only.

---

## 1 — Scope: what chunk 1 built and what this run exercised

Chunk 1 is the holder side of tier C: the app accepts a request carrying up to five predicate kinds
(`expiry_beyond`, `dob_at_least`, `nationality_is`, `nationality_not_in`, `name_match`, `document_number_is`), asks the
questions in plain words, reads the chip, answers with booleans only, and refuses the whole request (before any read)
if any part is unanswerable, conflicts with the locked age, or is not allowed. The run exercised:

| Row | Behaviour | Source of truth |
|---|---|---|
| (e) | Holder switch OFF by default; tier A still works with it off | app logcat plus verifier state |
| (d) | One age per site, shared across tiers A and C, in both orders | app logcat plus verifier state |
| (c) | All-or-nothing: off-list boundary, conflicting ages in one request | app logcat plus verifier state |
| (a) | Six predicates on a real US passport (BAC) | verifier verdict line plus logcat |
| (b) | Six predicates on a real NL ID card (PACE) | verifier verdict line plus logcat |
| (f) | Operator switch OFF build refuses tier C | app logcat plus verifier state |

The demo server ran with `TIERC_POC_ALLOW_RAW=1` (the env-gated raw-predicates hook from `e480136`), which is how
off-list and conflicting requests could be sent at all.

## 2 — Software evidence (re-run by the orchestrator at `e480136`)

JDK 17 at `$HOME/.gradle/jdks/eclipse_adoptium-17-amd64-linux.2`. The system Java 25 fails gradle toolchain setup with
"does not provide the required capabilities: [JAVA_COMPILER]".

| Item | Result |
|---|---|
| Scanner `:app:testRegularDebugUnitTest --rerun-tasks`, old results deleted first, summed over 39 JUnit XML files | 563 tests, 0 failures, 0 errors, 0 skipped (baseline 559; the probe tests were removed) |
| `apps/scanner/scripts/operator-json-negative.sh` | exit 0, 21 PASS |
| `apps/demo` `npm test` | exit 0, 59 pass, 0 fail (baseline 53) |
| Sabotage control | **The coder's report, not re-run by the orchestrator**: 4 sabotages turned 6 / 2 / 5 / 4 tests red; all restored byte-identical |

## 3 — Device, build and run facts

| Item | Value |
|---|---|
| Device | Pixel 6a, Android 17, build CP3A.261005.002.A1 |
| APK (tier-C switch ON) sha256 | `7e3f0dedfe57cf4037ff307444306fe8ab0caf151d7c88c655c6b045429ee8b8` |
| APK (tier-C switch OFF) sha256 | `2f8fc4758185eafcda3bcf73f8cf646b6936bfb11c78297b35fc23f4453fee79` |
| Signing / source | debug-signed, built from `f379f9b` |
| Verifier | demo server on `127.0.0.1:8787` via `adb reverse`, `TIERC_POC_ALLOW_RAW=1` |
| App state at start | app data cleared |
| Who did what | the owner did the phone actions and read every dialog aloud; the orchestrator ran adb and the helper commands and cross-checked each result against the verifier's own state (`GET /ui/presentations/<tx>`) plus logcat |

## 4 — Results

Times are phone logcat (local, 2026-10-25). "Verifier pending" means the verifier's state for that transaction stayed
pending: it received no presentation.

### Check 1 — (e) holder switch OFF by default

| Item | Result |
|---|---|
| Request | tier C, expiry 6 months plus age 18, tx `e-nkPp0RviF4xrcI` |
| Question area before Verify | listed both questions: "Whether your document is valid for more than 6 months from today"; "Whether you are at least 18 years old" |
| App line | 20:51:51.352 `M3b tier-C: REFUSED before any read or mint — cause=holder_setting_off reason=holder_setting_off chip_read=false mint=false` |
| Dialog | "Additional verifications are turned off. You can turn them on in Settings. Nothing was read and nothing was sent." |
| Verifier | request fetched (`request_uri GET`), no verdict, status pending |
| Result | PASS |

Observation: at 20:53 the owner did a local scan (no site attached) before the next launch. Mode A, nothing sent, no lock
set (`passive_auth: ok=true allowed=true`, dialog "Read OK — nothing was sent."). Harmless.

Incident: the phone USB/adb connection dropped at 21:08. Recovered by restarting the adb server. No data lost.

### Check 2 — (e) tier A with the switch OFF still works, and locks the age

| Item | Result |
|---|---|
| Request | tier A, age 18, tx `bn0gn8K8EGd4wB37` |
| App lines | 21:33:13.634 `mode A present-bare age check — over_threshold=true threshold=18`; 21:33:13.649 `direct_post response http_status=200` |
| Dialog | "Delivered — the website shows the result." |
| Verifier | `verdict tier=A threshold=18 ok=true allowed=true reason=no-evidence-required evidence=[] attester=n/a` |
| Result | PASS. This locked age 18 for the origin |

### Check 3 — (d) tier C asks a different age than the lock

Switch turned ON. Request: tier C, age 21, tx `wy7RAjTw6Q35qaZh`.

| Item | Result |
|---|---|
| App line | 21:35:04.179 `REFUSED before any read or mint — cause=age_not_locked_age reason=dob_age_not_locked_age chip_read=false mint=false` |
| Dialog | "This website may only ask about one age, and this question asks about a different one. Nothing was read and nothing was sent." |
| Verifier | pending |
| Result | PASS |

### Check 4 — (c) all-or-nothing

| Run | Request | Question area | App line | Dialog | Verifier | Result |
|---|---|---|---|---|---|---|
| 4a, tx `c7SXpMXVReUid07V` | raw: expiry 6 plus age 19 (off-list) | collapsed to one line: "This website asks: something about your document that this app cannot answer" | 21:37:12.993 `cause=request_not_answerable reason=boundary_off_list chip_read=false mint=false` | "This website asked something this app does not answer. Nothing was read and nothing was sent." | pending | PASS |
| 4b, tx `QrjOISbGyKYquM-H` | raw: ages 18 and 21 in one request, plus expiry 6 | listed all three questions | 21:38:50.790 `cause=age_conflict_in_request reason=dob_boundaries_conflict chip_read=false mint=false` | same age dialog as check 3 | pending | PASS |

Observation (4a): the question area does NOT list the answerable expiry question; it shows only the one generic line.
That is consistent with all-or-nothing, but the wording is the owner's to rule on (see finding below).

### Check 5 — (a) six predicates on the US passport (BAC)

The owner ran the helper in their own terminal with the name and number taken from environment variables, so the
orchestrator never handled either value. All six question lines were shown (21:41:02), with no name or number echoed.

| Run | Tx | Verifier verdict line |
|---|---|---|
| First (a real negative) | `G07QvfMw4Ydgwz-b` | `tier=C ok=true allowed=true reason=answers-received answered=expiry_beyond:6=true,dob_at_least:18=true,nationality_is=true,nationality_not_in=false,name_match=true,document_number_is=false` |
| Second | `K-_QSEyHxkKRnTGh` | `tier=C ok=true allowed=true reason=answers-received answered=expiry_beyond:6=true,dob_at_least:18=true,nationality_is=true,nationality_not_in=false,name_match=true,document_number_is=true` |

The first run's `document_number_is=false` was caused by a typo in hidden terminal entry; the owner re-typed it visibly.
It serves as a real negative: a wrong number gives an honest `false`, not a refusal. App lines for the second run:
21:45:47.126 `admitted predicates=6`; 21:45:51 six `answered predicate=...` lines (`read_to_boolean_us=4667`),
`direct_post http_status=200`, `access_protocol: BAC`, `passive_auth: ok=true allowed=true reason=SOD signature
verified to a trusted CSCA`. (The first run's read-to-boolean was 1714 us.) Dialog (both runs): "Done. 6 yes or no
answers were sent to the website. Nothing else left your phone. Go back to the website to continue."

Result: PASS (second run all six as the owner expected; first run's false was a planted-by-accident negative).

### Check 6 — (b) six predicates on the NL ID card (PACE)

Tx `zuQyvdUiUgEkR91r`. Question area showed the six questions, with the nationality question naming the NL code
requested by the site (a value the site sent, not read off the card).

Verifier: `verdict tier=C ok=true allowed=true reason=answers-received answered=expiry_beyond:6=true,dob_at_least:18=true,nationality_is=false,nationality_not_in=false,name_match=true,document_number_is=true`.

App: 21:47:21 six `answered` lines, `read_to_boolean_us=3107` (US passport: 1714 us on the first run, 4667 us on the second), `direct_post http_status=200`, `access_protocol: PACE`, same dialog as check 5.

Both nationality answers are CORRECT: the owner is not a Dutch national, so the card's nationality field is not the
NL code. The actual code is deliberately not recorded here. The orchestrator's pre-run prediction that the NL
nationality question would answer true was wrong; the app was right.

Result: PASS.

### Check 7 — (d reverse) tier C locks the age, then tier A is refused

After `pm clear` and the switch ON:

| Run | Request | Result |
|---|---|---|
| Tier C, age 21 plus expiry 6, tx `ekT1HbPceuw7e8mz` | both answered true; 21:50:21.172 `age 21 locked for origin host=127.0.0.1 (shared map, D109(i)3)`; `direct_post http_status=200`; verifier `verdict tier=C ... answered=expiry_beyond:6=true,dob_at_least:21=true`; dialog "Done. 2 yes or no answers were sent to the website. Nothing else left your phone. Go back to the website to continue." | PASS |
| Tier A, age 18, tx `6gxyxQgePFgyHG9v` | 21:50:30.826 `handoff REFUSED by threshold policy (S1, D74) — host=127.0.0.1 threshold=18`; message "This site asked for over 18, but it first asked for over 21 — refused."; verifier pending (the request was fetched twice, no verdict) | PASS |

The same tier-A refusal recurred at 21:51:14 on a relaunch of the same link, as expected.

### Check 8 — (f) operator switch OFF build

| Item | Result |
|---|---|
| Settings tab note, read aloud by the owner | "Additional verifications are not available in this version of the app." |
| Request | tier C, expiry 3 months, tx `jUKsvVXhNSo2px5A` |
| App line | 21:52:32.687 `REFUSED before any read or mint — cause=operator_switch_off reason=operator_switch_off chip_read=false mint=false` |
| Dialog | "This version of the app does not do additional verifications, so nothing was read and nothing was sent." |
| Verifier | pending |
| Result | PASS |

Not confirmed: whether the switch control itself was hidden on this build. The owner read the note; they were not asked
separately about the switch, so this is unconfirmed.

## 5 — Verdict

**Overall: chunk 1 PASSED all eight checks on the Pixel 6a.** Every refusal fired before any chip read
(`chip_read=false mint=false`) and the verifier's own state stayed pending each time; every answered request reached the
verifier with `ok=true allowed=true` and the exact booleans listed above.

Qualifications, stated rather than hidden:

- The first six-predicate run answered `document_number_is=false` because of a hidden-entry typo, not a defect.
- Only the US passport (BAC) and NL ID card (PACE) were exercised; no other document type.
- The screen was read by the owner aloud; the app forbids screen capture, so the dialog texts above are the owner's
  readings matched to the logcat `terminal outcome dialog shown` lines, which agree.
- Hidden-switch on the OFF build: unconfirmed (check 8).
- The sabotage control is the coder's report only.

## 6 — Privacy check and a process slip

Privacy check: the owner's name string was grepped in the full logcat and the demo server log by the orchestrator;
0 hits.

Process slip, recorded honestly: after check 8 the orchestrator ran one `uiautomator dump` on the phone, against the
no-accessibility-snapshot rule for screens that can render passport fields. The file was never read and was deleted from
the device (verified absent).

## 7 — Open items and findings

Recorded in `.claude/remember/findings.md` as #28 (coder-proposed gaps) and #29 (the run's observation on the
generic off-list line). Nothing here is a ruling.
