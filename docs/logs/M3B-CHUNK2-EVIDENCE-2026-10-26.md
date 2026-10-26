# M3b chunk 2 — tier C through the verifier side and the generic refusal, device run (Pixel 6a, 2026-10-26): PASSED

**Status**: evidence record for build chunk 2 of milestones.md §6.9.7 (D109(l), D110): chiproof's tier-C wire, the demo
page and server, and the scanner's generic `access_denied` refusal notice. Written after the 2026-10-26 device run. No
pre-registered cell was edited after the fact; where something was not confirmed, it says so.

**Branch / commit**: `feat/m3b-tier-c`, HEAD `183f407`. Chunk 2 commits: `469fe66` (D110 docs), `396cfe0` (chiproof
tier C), `63478db` (demo page and server), `50d718e` (scanner generic `access_denied` refusal), `2de4054` (final wire
recorded), `c6b6e8f` (D110 later note: refusal wording), `183f407` (scanner: dialog endings depend on actual delivery,
plus a Log-tab line). The APKs below are the "chunk2b" builds.

**Rule for this file (carried from `M3B-CHUNK1-EVIDENCE-2026-10-25.md`)**: no PII, ever. Field NAMES, booleans, HTTP
statuses, transaction ids, timings and dialog texts only. No name, document number, date of birth, expiry date, or
nationality code of the owner appears here. The two real documents are "the US passport" and "the NL ID card".

---

## 1 — Scope: what chunk 2 built and what this run exercised

Chunk 2 is the verifier side of tier C plus the refusal path: chiproof carries the five-kind predicate request and the
answers-only verdict; the demo page asks for them; and when the app refuses a tier-C request before reading anything, it
posts one generic `access_denied` notice so the site can tell "refused" from "walked away" without learning why (D110).

| Check | Behaviour | Source of truth |
|---|---|---|
| 1 | Holder switch OFF: refused before read, generic refusal delivered | app logcat, demo log, page verdict, Log tab |
| 2 | Answers on the US passport (BAC), through the real page and QR | demo verdict line, logcat, page |
| 3 | Answers on the NL ID card (PACE) | demo verdict line, logcat, page |
| 4 | Off-list age: refused before read, generic refusal delivered | logcat, demo log, site verdict |
| 5 | Site unreachable at refusal time: the app must not claim the site was told | logcat, report line, dialog |
| 6 | Holder abandons: site shows "expired", distinct from "refused" | demo status, page text |
| 7 | Operator-switch-OFF build: nothing read, nothing sent | logcat, page |

## 2 — Software evidence

JDK 17 at `$HOME/.gradle/jdks/eclipse_adoptium-17-amd64-linux.2`.

**Re-run by the orchestrator:**

| Item | Result |
|---|---|
| Scanner at `183f407`, old results deleted first, summed over 40 JUnit XML files | 582 tests, 0 failures, 0 errors, 0 skipped (baseline before chunk 2: 563) |
| Scanner at `2de4054` | 575 tests, 0 failures |
| `apps/scanner/scripts/operator-json-negative.sh` at `183f407` | exit 0, 21 PASS |
| chiproof `npm test` at `2de4054` | exit 0, 209 pass, 0 fail (baseline 191); typecheck exit 0 |
| `apps/demo` `npm test` at `2de4054` | exit 0, 63 pass, 0 fail (baseline 59) |

**The coders' reports, NOT re-run by the orchestrator:**

- Local adopter type gate (pack plus a throwaway consumer project, `tsc` exit 0). It caught and fixed a real
  `Record<string, any>` type bug.
- Sabotage tables (each sabotage restored afterwards):
  - off-list age accepted: chiproof 3 red
  - `access_denied` read as pass: chiproof 1 plus demo 3 red
  - refusal sent to an unpinned host: scanner 2 red
  - refusal sent for an unverified request: scanner 1 red (guard truth table only; no real pre-JWS path exists to hit)
  - operator switch ignored: 2 red
  - demo loopback gate removed: 1 red
  - poll returning the presentation: 2 red
  - wording fix, "told" ending shown regardless of delivery: 5 red
  - wording fix, Log line dropped: 1 red

## 3 — Device, build and run facts

| Item | Value |
|---|---|
| Device | Pixel 6a, Android 17, build CP3A.261005.002.A1 |
| APK (tier-C switch ON, chunk2b, debug) sha256 | `3f49283e9dd70deff016624ec090327055d1cd77f306fa85271be1e2befb862c` |
| APK (tier-C switch OFF, chunk2b, debug) sha256 | `8d6a5948b82a5f5d3412ea7892b11247343f36527ae0affa46d2069cc8e7bb38` |
| Verifier | demo server on `127.0.0.1:8787` via `adb reverse`, `TIERC_POC_ALLOW_RAW=1` |
| How the app was launched | For page requests: the owner scanned the page's QR with the phone Camera app (no adb launch). For the raw-request checks (4 and 5): launched via the helper with adb. |
| Typed input | The owner typed the name and number only into the demo page, in their own browser. |
| Who did what | The owner did the phone actions and read dialogs and the page aloud; the orchestrator cross-checked each against the server log and state and logcat. |

## 4 — Results

Times are phone logcat (local, 2026-10-26). The user agent on every `request_uri GET` was the app's
(`Dalvik/2.1.0 ... Pixel 6a Build/CP3A.261005.002.A1`).

### Check 1 — holder switch OFF (fresh data)

| Item | Result |
|---|---|
| Request | page request, expiry 6, tx `TjUTYa9tscZHY33l` |
| App lines | 08:19:48.899 `M3b tier-C: REFUSED before any read or mint — cause=holder_setting_off reason=holder_setting_off chip_read=false mint=false`; 08:19:48.915 `generic refusal posted cause=holder_setting_off http_status=200` |
| Dialog | "Additional verifications are turned off. You can turn them on in Settings. Nothing was read. The website was only told that the request was refused." |
| Demo log | `verdict transactionId=TjUTYa9tscZHY33l tier=C ok=true allowed=false reason=refused_by_holder refused=true` |
| Site verdict | the owner pasted the JSON from the page: `ok:true allowed:false reason:refused_by_holder refused:true` |
| Log tab | the owner read it: "Refused: additional verifications are turned off in Settings. Refusal sent to the website…" |
| Result | PASS |

Not confirmed: the page's heading text for this refusal was not read aloud, so it is not recorded here.

### Check 2 — US passport (BAC), switch ON

| Run | Tx | Result |
|---|---|---|
| First attempt | `dG08Gg_leIYy9iYU` | The owner left the name and number fields empty, so the page asked only 2 predicates; both answered true (`answered=expiry_beyond:6=true,dob_at_least:18=true`, 08:25:23). Owner input, not a defect. |
| Retry | `NRqclqSdduX_1iku` | 4 predicates, all true: `answered=expiry_beyond:6=true,dob_at_least:18=true,name_match=true,document_number_is=true` |

Retry detail: 08:28:58.689 `admitted predicates=4 names=expiry_beyond,dob_at_least,name_match,document_number_is`;
08:29:02 four `answered predicate=...` lines (`read_to_boolean_us=4196`), `direct_post http_status=200`,
`access_protocol: BAC`. Dialog: "Done. 4 yes or no answers were sent to the website. Nothing else left your phone. Go
back to the website to continue." Page (pasted by the owner): heading "Answered" with four "...: yes" lines plus a raw
JSON of predicate names and booleans only, no PII. Result: PASS.

### Check 3 — NL ID card (PACE)

Tx `H-XAPwQxoCgG8B7_`: expiry 6 and age 18, both true (`answered=expiry_beyond:6=true,dob_at_least:18=true`);
08:31:23 `access_protocol: PACE`, `direct_post http_status=200`, `read_to_boolean_us=274`; dialog "Done. 2 yes or no
answers were sent to the website. ..."; page "Answered".

**Coverage gap, stated**: the name and number fields were again left empty, so `name_match` and `document_number_is`
on the NEW wire are proven on the US passport only. The NL card answered them in chunk 1, on the POC wire. Result:
PASS, with this gap.

### Check 4 — off-list age (19) plus expiry 6, via helper `--raw`

| Item | Result |
|---|---|
| Tx | `S5RBROspCuPbISES` (ttl 300000 ms) |
| Question area | "This website asks: something about your document that this app cannot answer" |
| App lines | 08:34:44.559 `REFUSED before any read or mint — cause=request_not_answerable reason=boundary_off_list chip_read=false mint=false`; 08:34:44.574 `generic refusal posted ... http_status=200` |
| Dialog | "This website asked something this app does not answer. Nothing was read. The website was only told that the request was refused." |
| Demo log | `verdict ... tier=C ok=true allowed=false reason=refused_by_holder refused=true` |
| Site verdict | `refused=true`, `answered=none` |
| Result | PASS |

### Check 5 — site unreachable at refusal time

Same raw request, tx `ohOgQW-i2fNG-FRL`. After the app's `request_uri GET` and before Verify, the orchestrator stopped
the demo server (killed by exact pid).

| Item | Result |
|---|---|
| App lines | 08:36:37.576 `REFUSED before any read or mint — cause=request_not_answerable reason=boundary_off_list chip_read=false mint=false`; 08:36:37.585 `generic refusal NOT delivered cause=request_not_answerable error=IOException (not retried)` |
| Report line | "Refused: the website asked something this app does not answer. Refusal could not be sent (unreachable)." |
| Dialog | "This website asked something this app does not answer. Nothing was read and nothing was sent." |
| Result | PASS: the app made no false "website was told" claim |

After the server was down, a later relaunch produced `request_uri fetch failed: HTTP 404` (08:37:39, a different
request path, dialog "Handoff refused: request_uri fetch failed: HTTP 404 ..."); it is an ordinary stale-link refusal,
not part of this check.

### Check 6 — holder abandons

Page request, expiry 6, tx `FbZvvWrSjpZ3cyCm`. The owner opened it and did not tap Verify. Site status "expired"; page
text "The request expired and nothing came back. The holder may have walked away." This is distinct from "Refused".
Result: PASS.

Observation: the phone kept showing the stale question ("This website asks:") after the request expired (logcat lines
through 08:44:21); the app does not check expiry until Verify. Link-once and stale handling is item 18 (chunk 4).

### Check 7 — operator-switch-OFF build, page request

| Item | Result |
|---|---|
| Tx | `liE0Wmr1YrT7nb6Y` |
| App lines | 08:47:44.701 `REFUSED before any read or mint — cause=operator_switch_off reason=operator_switch_off chip_read=false mint=false`; 08:47:44.701 `generic refusal NOT sent (no verified, pinned, operator-enabled request) cause=operator_switch_off` |
| Report line | "Refused: this version of the app does not do additional verifications. No refusal notice is sent for this request." |
| Dialog | "This version of the app does not do additional verifications, so nothing was read and nothing was sent." |
| Page | after expiry: "The request expired and nothing came back..." (the site was not told, by design) |
| Result | PASS |

## 5 — Verdict

**Overall: chunk 2 PASSED all seven checks on the Pixel 6a.** Every refusal fired before any chip read
(`chip_read=false mint=false`); every delivered refusal showed on the site as `refused=true` and never as an answer;
the undelivered refusal (check 5) and the operator-OFF refusal (check 7) did not claim the site was told.

Qualifications, stated rather than hidden:

- Name and number on the new wire were exercised on the US passport only (check 3 gap).
- The first US-passport attempt used empty name/number fields (owner input).
- Check 1's page heading was not read aloud.
- Dialogs were read by the owner aloud (the app forbids screen capture) and matched to the logcat `terminal outcome
  dialog shown` lines, which agree.
- The software sabotage tables and the local adopter type gate are the coders' reports, not re-run.
- Debug builds only; exit evidence for a release build is still owed at release time.

## 6 — Privacy check

The owner's name string was grepped in the full logcat and the demo server log by the orchestrator; 0 hits.

## 7 — Open items and findings

Recorded in `.claude/remember/findings.md` as #30 to #36. Nothing here is a ruling.
