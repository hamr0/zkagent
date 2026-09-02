# M2 — device evidence for D58 step 1 (Report/Log cluster, Pixel 6a, 2026-09-02)

**Status**: source record for `c856f42` ("D58 step 1 — ReportLog owns the report/log cluster"),
written after the fact from the orchestrator's own JUnit XML check, source diff, and one logcat
capture, all produced this session. This file is the evidence
`.claude/remember/findings.md` #7 and #13's status updates and `docs/product/zkagent-prd.md`'s D58
step-1 status line cite; it mirrors `M2-D55-D56-EVIDENCE.md`'s structure and value-free rule.

**Rule for this file (carried from `M0-EVIDENCE.md` / `M2-D50-D53-EVIDENCE.md` /
`M2-D55-D56-EVIDENCE.md`)**: no PII values, ever — field names, verdict strings, timings,
hashes/truncated identifiers, and exception text only. The logcat capture referenced below is
value-free by construction (no MRZ, name, date of birth, document number, raw zktag, nonce, public
key, or signature appears in it); every line quoted here was checked against that rule before
inclusion.

---

## What changed (`c856f42`)

D57 froze new §6.2 items until the ownership audit's structure pass lands; D58 sets that pass's
execution order, step 1 being the Report/Log cluster — "smallest, most closed, lowest consequence;
proves the single-owner pattern cheaply." This commit is that step, closing two standing findings:

- **Finding #7** (`reportView.text` written outside `emitReport`, contradicting its own KDoc): the
  `MainActivity.lastReportText` field is deleted; `ReportLog` gains a private-set `lastText: String?`
  property (`ReportLog.kt:164-175`), written only by `ReportLog.append` (`:213`) and `ReportLog.restore`
  (new `lastText` parameter, `:284-291`). `MainActivity.emitReport` (`MainActivity.kt:808-828`) no
  longer writes `reportView.text`/`lastReportText` directly — it calls `reportLog.append(...)` and
  then renders `reportLog.lastText` into `reportView.text`. The `onCreate` restore block, which
  previously wrote `lastReportText`/`reportView.text`/`logView.text` directly at three separate
  lines (the doc/code mismatch finding #7 named), is replaced by a call to a new named sibling,
  `MainActivity.restoreReport` (`:833-851`), which is now the only other writer of the cluster's
  state and ends with the same `Log.i` shape `emitReport` uses (labelled RESTORE, not REPORT, so the
  two remain distinguishable in logcat).
- **Finding #13** (unbounded `ReportLog.entries` growth): `ReportLog` gains a named constant,
  `MAX_ENTRIES = 200` (`ReportLog.kt` companion object), and `append` evicts the single oldest entry,
  oldest-first, whenever a genuinely new (non-pending-replace) append would push `entries` past that
  bound, shifting every remaining pending-attempt index down by one and dropping any index that
  pointed at the evicted slot. The number is explicitly marked PROPOSED/PROVISIONAL in the code
  comment — sized from `ReportLogTest`'s own rendered fixtures (roughly 400-900 bytes per entry, so
  200 entries is on the order of 100-180 KB) against the Bundle/binder transaction ceiling
  (`TransactionTooLargeException` risk starts around 1 MB) — but **not measured against a real
  rendered Bundle on this device**; the owner still needs to approve the exact number.

Async landings (finding #5, the five unfenced `Thread{}` sites) are explicitly untouched by this
step — deferred to D58 steps 2/3 per the commit message.

---

## How verified — unit tests

JUnit XML, plain-JVM unit test run (`apps/scanner`'s `isReturnDefaultValues = true` module):

| | Before | After |
|---|---|---|
| Total tests | 145 | 151 |
| Failures | 0 | 0 |

Six new tests in `ReportLogTest`: `lastText` set by `append`, `lastText` set/overwritten by
`restore`, the `MAX_ENTRIES` bound enforced under repeated append, oldest-first eviction order, and
a restore round-trip (`restore` then `append` then `restore` again) confirming `lastText` and
`entries` are independently restorable. Failing-first was demonstrated as 13 compile errors
(`Unresolved reference: lastText`, `Unresolved reference: MAX_ENTRIES`) against the pre-change
production code, before the production change landed.

---

## How verified — device run (Pixel 6a, app pid 15939, 2026-09-02)

Three real scans captured in one process, followed by a forced Activity recreation:

| Time | Event |
|---|---|
| 10:22:02 | `av://` handoff verified — origin `http://127.0.0.1:8787`, dev-pinned key |
| 10:22:32 | US passport read, `verdict: PASS (read)` |
| 10:23:43 | NL card read, `verdict: PASS (read)` |
| 10:24:15 | fresh `av://` link delivered |
| 10:24:47 | NL card, `direct_post` `http_status=200`, `verdict: PASS (minted)` |

Auto-rotate is OFF on this device, so a physical rotation would not have recreated the Activity; the
orchestrator instead forced recreation with `adb shell settings put system font_scale 1.15` followed
by `adb shell settings put system font_scale 1.0` while the app was in the foreground (a
configuration change Android delivers as an Activity destroy/recreate like a rotation would).

Logcat, two lines, one per config-change recreation:

```
10:31:03.189  M2 stage: restored report/log across Activity recreation (text=true, log_entries=3)
10:31:07.286  M2 stage: restored report/log across Activity recreation (text=true, log_entries=3)
```

Both lines carry `text=true, log_entries=3` — the finding #7/#13 restore path (`restoreReport`)
fired once per config change, and all three prior scan entries (the two reads plus the mint) were
intact across both recreations. Full capture at the orchestrator's scratchpad,
`step1-device-logcat.log` (not committed — produced outside this repo, referenced here by content
per this file's own convention). No MRZ/DG1/document-number line from that capture is reproduced
above or elsewhere in this file.

---

## Caveats, stated plainly

- **Auto-rotate is off on this device.** The device run above used a font-scale toggle, not a
  physical rotation, to force Activity recreation — the same `onSaveInstanceState`/`onCreate` path a
  real rotation exercises, but not a rotation itself. No rotation-triggered recreation of this
  specific change has been separately captured.
- **`MAX_ENTRIES = 200` is not device-measured.** The 100-180 KB estimate against the ~1 MB
  transaction ceiling comes from `ReportLogTest`'s rendered-fixture byte counts, not from a real
  `onSaveInstanceState` Bundle measured on hardware at or near the cap. The number is PROVISIONAL
  pending owner approval; the eviction mechanism itself (oldest-first, in `append`) is what this
  step delivers and holds for any value chosen.
- **Async landings (finding #5) are untouched.** The bound helps that cluster's worst case (a late
  unfenced-`Thread{}` append now lands on a bounded structure) but does not fix the underlying
  no-cancellation defect; see `.claude/remember/findings.md` #5's own status update.
- **The structure proposal's cluster-3 recommendation ("no new class, keep the field in
  `MainActivity`") was not followed.** The coder instead followed the `PaneVisibility` precedent —
  `Activity`-resident logic is untestable under `isReturnDefaultValues = true`, so the state and its
  invariants (bound, eviction, restore) needed to live in a plain class to be unit-testable at all.
  This is recorded as a correction to the proposal's applicability, not an edit to the proposal file
  itself — `docs/logs/M2-STRUCTURE-PROPOSAL-2026-09-02.md` stays as originally written; D58 step 4
  re-derives the session boundary on the corrected call/state graph.
- **Peer review of the step-1 brief was skipped by owner decision.** The four review challenges the
  brief would otherwise have gone through were self-answered in the coder's own report rather than
  by an independent second reviewer.

---

## What this run did and did NOT establish

**Did establish:**
- Finding #7 is closed by construction: `ReportLog` is the single owner of the report/log cluster's
  state; `emitReport` and `restoreReport` are its only two renderers, both logged.
- Finding #13's eviction mechanism works as coded, both in unit tests (bound + oldest-first order)
  and does not interfere with a real restore on hardware (3 entries survive two recreations intact).
- The restore path fires correctly across a forced config-change recreation on real hardware, with
  both the text and the log entries intact.

**Did NOT establish:**
- That `MAX_ENTRIES = 200` is itself the right number — only that the mechanism enforcing whatever
  number is chosen works.
- Behavior at or near the actual 200-entry bound on real hardware (the device run above captured 3
  entries, not 200) — no real measurement of a near-cap Bundle's actual serialized size exists yet.
- Anything about finding #5 (async-cancellation) beyond the bounded-landing-site observation above.
- Behavior under a genuine physical rotation (only the font-scale config-change trick was used).

---

**No PII values appear anywhere above.** All quoted logcat lines are value-free by construction —
stage names, boolean/count fields, and timings only — checked against this file's own rule and the
project standard it inherits from `M0-EVIDENCE.md` / `M2-D50-D53-EVIDENCE.md` /
`M2-D55-D56-EVIDENCE.md` before inclusion.
