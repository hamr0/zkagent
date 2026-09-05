# M3 scanner S1 evidence (Pixel 6a, 2026-09-05)

**Status**: source record for `docs/wiki/milestones.md` §6.5 item 1 (S1 — preset threshold list,
per-origin first-seen lock, named exceptions, D74) as built in `apps/scanner`, committed at
`def7b64`. Also covers, from the same afternoon, the D79 wrong-details re-entry device run
(commit `aca234b`), which had not yet been written up in a dedicated evidence file.

**Rule for this file (carried from `M0-EVIDENCE.md` through
`M3-SCANNER-S2-S3-EVIDENCE-2026-09-05.md`)**: no PII values, ever — field names, verdict strings,
timings, hashes/truncated transaction identifiers, and 12-char zktag prefixes only. No MRZ field,
name, date of birth, document number, raw zktag, nonce, public key, or signature appears anywhere
in this file.

---

## Setup

Pixel 6a, app package `com.zkagent.scanner`, build 7 debug APK. `apps/demo` running on port 8787,
reached via `adb reverse tcp:8787 tcp:8787`. `apps/scanner` unit tests: 458/0/0, per the JUnit XML
from the 2026-09-05 15:56 run. `apps/demo` tests: 39/0/0. All S1 device runs below used `adb`
`VIEW` intents directly (no card needed — S1's lock/refusal check runs before any chip read is
attempted).

---

## S1 — preset threshold list, per-origin lock (device run, 16:00:55–16:01:08)

Four `av://` links dispatched via `adb shell am start -a android.intent.action.VIEW`, in order,
no force-stop between the first two:

| Time | Link threshold | Result | Notice / log |
|---|---|---|---|
| 16:00:55 | 18 | Admitted, locked | "handoff request object verified — origin=http://127.0.0.1:8787", then "threshold 18 locked for origin host=127.0.0.1 (S1, D74)"; question line: "This website asks if you are over 18" |

App force-stopped (`am force-stop com.zkagent.scanner`) and reopened before the next link, to
confirm the lock is read back from the persisted store rather than in-memory state.

| Time | Link threshold | Result | Notice / log |
|---|---|---|---|
| 16:01:01 | 21 | Refused | "handoff REFUSED by threshold policy (S1, D74) — host=127.0.0.1 threshold=21"; blocking notice: "This site asked for over 21, but it first asked for over 18 — refused."; question line stays "Local scan (no site)" |
| 16:01:05 | 43 | Refused | Blocking notice: "This site asked for over 43, which is not a supported threshold — refused." |
| 16:01:08 | 18 | Admitted, verified normally | Question line: "This website asks if you are over 18" |

**Interpretation**: the lock survives a force-stop/reopen (read from the persisted store, not
in-memory), and both refusal branches fire on the two distinct negatives (`RefuseDifferentThreshold`
at 21, `RefuseNotPreset` at 43) without arming Scan/Verify or reaching the question line beyond
"Local scan (no site)". The 18-threshold link at 16:01:08 shows the lock does not itself block a
later request that matches the already-locked value.

---

## D79 wrong-details re-entry (device run, commit `aca234b`, same afternoon)

Demo store reset to empty at 14:3x (confirmed: store held 1 zktag, 1 attester binding at the time
of this run, both minted fresh this session — no carryover from prior sessions).

Three consecutive attempts, each with identical wrong details typed into the app's entry form:
each failed BAC mutual authentication with `SW 0x6982`, and each left the app reporting "MRZ input
UNCHANGED" (the access-establishment failure branch; Scan/Verify re-armed each time, pending
handoff preserved).

Fourth attempt, details corrected: app reported "MRZ input CHANGED"; the button re-armed and the
read succeeded. Server log:

```
15:09:54 direct_post 200
```

App: "verdict: DELIVERED (minted)". Server verdict: `tier=B allowed=true attester=matched`
(matched against the freshly-reset store — this was the first presentation of this document at
this site this session). Last-scan line: "Last scan: 127.0.0.1, over 18: true, delivered ✓" —
shown after the successful scan, survived an app force-stop, and was unaffected by both
Diagnostics-tab probes being run in between.

---

## What this run did and did NOT establish

**Did establish:**
- S1's per-origin threshold lock persists across a force-stop/reopen (read from the item-23 log
  store file, not in-memory).
- Both S1 negative branches (`RefuseDifferentThreshold`, `RefuseNotPreset`) fire correctly via the
  `av://` intent path, with the specified blocking-notice wording and no chip read attempted.
- A threshold matching the existing lock (18, re-sent after the two refusals) is still admitted
  normally.
- The D79 wrong-details re-arm behaviour holds across three consecutive failed attempts before a
  corrected entry delivers successfully, on a store that had just been reset to empty.
- The last-scan line persists correctly across a force-stop and is unaffected by Diagnostics
  probes.
- 458/458 scanner unit tests and 39/39 demo tests pass per the JUnit XML at 15:56.

**Did NOT establish:**
- The 21/43 refusals were exercised via the `av://` intent path only — the paste path's identical
  check is unit-tested (`ThresholdPolicyTest.kt`) but not device-run this session.
- No second real hostname was tested — only `127.0.0.1` — so the exact-hostname `NAMED_EXCEPTIONS`
  allowlist (which ships empty) is unit-tested only, not device-confirmed against a real second
  origin.

---

**No PII values appear anywhere above.** All quoted log lines, dialog strings, and timings are
value-free by construction — field names, verdict strings, boolean/status fields, timings, and
12-char zktag prefixes only — checked against this file's own rule and the project standard it
inherits through `M3-SCANNER-S2-S3-EVIDENCE-2026-09-05.md` before inclusion.
