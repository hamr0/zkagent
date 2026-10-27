# M3b item 18 — link-once: handle each incoming link only once (finding #48), device run (Pixel 6a) 2026-10-27: PASSED (with stated gaps)

**Status**: evidence record for item 18 (milestones.md §6.9.2 item 18, D109(e), D87 guard completeness). Closes finding #48
(device-checked). Nothing was edited after the fact; where something was not confirmed, it says so.

**Branch / commit**: `feat/m3b-tier-c`, HEAD `cebebdd` plus the uncommitted item 18 working-tree changes (`LinkOnce.kt`,
`InFlightHandoff.kt`, `MainActivity.kt`, new `LinkOnceTest.kt`). Nothing was committed when this record was written.

**Rule for this file (carried from chunks 1 to 3, D112 and D113)**: no PII, ever, and no hostname, domain or IP of the
owner's infrastructure. This run used loopback only (127.0.0.1 via `adb reverse`), quoted as it is. None of the runs
touched PII: the owner typed the document details on the phone only.

---

## 1 — Scope

Item 18 pass criteria: (1) a unit truth table for the pure decision, with a negative control; (2) device: a truncated link
plus a forced recreation shows the Snackbar once, not twice; (3) device: a valid link mid-flow, then a forced recreation,
does not start a second verification; (4) device: a process-death restore still resumes the flow. Finding #48 (an
already-handled `av://` intent re-processed after `adb install -r`) is closed by the same guard.

Spec deviation, already in the "As built" note of item 18 and not repeated here: the guard is keyed on the whole link
string, not on `request_uri`, and a recreation during VERIFYING re-runs the verification once.

## 2 — Software evidence

JDK 17.

**Re-run by the orchestrator on the final working tree:**

| Item | Result |
|---|---|
| Scanner, old results deleted first, summed over 43 JUnit XML files | 612 tests, 0 failures, 0 errors, 0 skipped (baseline 605 over 42 files at `6c53813`) |
| `apps/scanner/scripts/operator-json-negative.sh` | exit 0, 33 PASS |

**The coder's mutation results, NOT re-run by the orchestrator** (the coder's report only): guard switched off makes the
truth table red; removing the `restoreInFlightHandoff` call makes the wiring test red (1); removing `&& holderHasEntry`
makes the truth table red (1). The wiring test reads source text only (the same weakness as finding #47).

## 3 — Builds and device facts

| Item | Value |
|---|---|
| Device | Pixel 6a, Android 17, density 420, fresh app data (`install -r` then `pm clear`) |
| Build | regular debug APK from the working tree on `cebebdd` plus the item 18 changes, committed `operator.json` |
| APK sha256 | `5e3d76ab6d0a8941f99ac803327f32032c66aa2324868464399b3394b4bf949d` |
| Setup | `adb reverse` 8787, demo verifier on loopback; the phone in dark mode (`cmd uimode night yes`) |

The owner read each screen aloud (the app forbids screen capture); the orchestrator cross-checked each against logcat and
the demo log. Times are phone logcat, 2026-10-27.

## 4 — Device checks: PASSED (criteria 2, 3, 4 and finding #48)

| # | Setup | Result |
|---|---|---|
| 2 | Truncated link `av://authorize?client_id=x`, then screen rebuilds | 16:58:12 the "That link looks broken" Snackbar (`REFUSED — data did not parse into a handoff (malformed or truncated link)`); at 17:06:04 again via `onNewIntent` when the owner asked for a re-send (a fresh tap, handled by design). Density rebuilds at 17:06:18 and 17:06:24 logged `M3b link-once: ignored an already-handled av:// link replayed on CREATE`, but the owner did not see those rebuilds reliably. A clean repeat used a new link `av://authorize?client_id=y` from a force-stopped start: REFUSED 17:09:59, the owner saw the Snackbar. A density rebuild (17:10:09) and a `uimode` night no/yes toggle (17:10:29, 17:11:31) were each logged as ignored. The owner confirmed no second Snackbar. PASS |
| 3 | Valid tier A link, tx `5QBiCdJa_REaB5-4` (ttl 300), then a recreation | The question area "This website asks if you are over 18" was BLUE. A `uimode` toggle logged `M3b link-once: restored the verified in-flight handoff after recreation (no second request_uri GET)` (17:16:53 and again 17:16:58). The owner saw the question still there in blue with the Verify button. The demo log shows exactly 1 `request_uri GET` for this tx. The owner then scanned: `direct_post` 200 at 17:18:10, popup "Delivered — the website shows the result.", verdict `tier=A threshold=18 ok=true allowed=true`. PASS |
| 48 | That delivered link as the last launch intent, then `adb install -r` of the same APK | 17:19:02 `killDueToPackageUpdate` killed pid 7520; the new pid 7820 logged `M3b link-once: ignored … replayed on CREATE` at 17:19:02.784. The demo log still shows 1 GET for that tx. The owner saw no popup, and the Log tab's newest entry was the delivered one with nothing new. PASS |
| 4 | Valid link, tx `M3wc4tqxijfLuE9O`; process death mid-flow | Question shown at 17:20:25 (pid 7968). The owner pressed Home and the orchestrator ran `am kill com.zkagent.scanner` (the process was gone afterwards). The owner reopened from Recents: the new pid 8084 logged `pendingHandoff captured from av:// intent` at 17:21:06 and re-verified, and the question line came back; the owner saw "over 18" in blue with Verify. The demo log shows 2 GETs for this tx, expected for a resume. The request was left to expire. PASS |

Process notes: the orchestrator's first dark-mode toggle left the phone on light; it was restored to dark straight away. A
launcher relaunch at about 17:08 carried no link, so it tested nothing and is not counted as evidence.

## 5 — NOT device-checked (stated, not hidden)

- **Recreation while the request is still VERIFYING** (the re-run-once path, with the dead instance's late result
  dropped): too fast to hit by hand; unit-tested only.
- **Recreation after a lock or chip read**: not run. Per the As-built note the user re-enters; no MRZ or chip state is
  held across a recreation.
- Criterion 1 (the unit truth table) rests on the software evidence in section 2; the mutation results are the coder's.

## 6 — Verdict

**Item 18 PASSED on the Pixel 6a for what a human can reach by hand.** A malformed link shows its Snackbar once and is
ignored on replay; a valid in-flight link survives a recreation without a second `request_uri` GET; an already-delivered
link replayed by `adb install -r` does nothing visible; a process-death restore still resumes. Finding #48 is closed
(device-checked 2026-10-27).

Qualifications:

- Debug build only; not a production-signed release.
- The mutation results are the coder's report, not re-run.
- The wiring test reads source text only (finding #47's weakness); the device runs supplied the real-process proof.
- The VERIFYING-recreation path is unit-proven only.

## 7 — Privacy check

None of the runs touched PII; the owner typed the document details on the phone only. No real document data in this file.

## 8 — Open items

Findings #47 and #49 stay open. Nothing here is a ruling.

Correction 2026-10-27 (D115 rule 5): the blue ask colour read in check 3 was the night shade (#90CAF9) on a light background, which has poor contrast (finding #50). The night colour variants were removed under D115; the app is now always light (ask blue #1565C0).
