# M2 — device evidence for D58 step 2 (Pane cluster, Pixel 6a, 2026-09-02)

**Status**: source record for `0d4daf7` ("D58 step 2 — PaneState owns the tab index (finding #1)"),
written after the fact from the orchestrator's own JUnit XML check, source diff, and one filtered
`uiautomator dump` capture per case, all produced this session. This file is the evidence
`.claude/remember/findings.md` #1's status update and `docs/product/zkagent-prd.md`'s D58 step-2
status line cite; it mirrors `M2-D58-STEP1-EVIDENCE.md`'s structure and value-free rule.

**Rule for this file (carried from `M0-EVIDENCE.md` / `M2-D50-D53-EVIDENCE.md` /
`M2-D55-D56-EVIDENCE.md` / `M2-D58-STEP1-EVIDENCE.md`)**: no PII values, ever — field names, verdict
strings, timings, hashes/truncated identifiers, and exception text only. The `uiautomator dump`
captures referenced below were filtered to only the SCAN/LOG tab nodes' `selected` attribute before
being read — no MRZ field, name, date of birth, document number, raw zktag, nonce, public key, or
signature appears anywhere in this file.

---

## What changed (`0d4daf7`)

D58 sets the ownership refactor's execution order; step 2 is the Pane cluster — fixes the confirmed
rotation bug, closing one standing finding:

- **Finding #1** (`TabLayout` selection vs. `showPane()` race on rotation): a new pure class,
  `PaneState` (`apps/scanner/.../PaneState.kt`, ~100 lines), becomes the pane decision's sole owner
  for both its inputs. It holds private-set `selectedTab` (`TAB_SCAN = 0`, `TAB_LOG = 1`) and
  `readInProgress`, with named transitions `userSelectedTab`, `readStarted`, `readFinished`,
  `tabIndexToSave`, `restoreTabIndex` — four legal states (2 tabs × read in flight or not), all
  reachable and all meaningful. `showPane()` no longer READS `tabLayout.selectedTabPosition` as an
  input at all; it drives the `TabLayout`'s selection FROM `PaneState`, behind a re-entry guard
  (`applyingPaneStateToTabLayout`) so a programmatic move is never misread by the tab listener as a
  fresh user tap. This closes the finding by construction: the framework's own tab-position restore
  (inside its default, still-un-overridden `onPostCreate`) can land whenever it lands — the pane
  decision no longer consults it, so there is nothing left to race.
- `readInProgress` moves into the same owner. Both its writers (`startSession`,
  `ReadTask.onPostExecute`) were already read-lifecycle-only, per this step's required survey — not
  on a `Thread{}`, not in the handoff/session path — so consolidating it here makes the pane decision
  a pure function of one owner's state. It is deliberately NOT persisted, matching its
  reset-on-recreation behaviour from before this step.
- `PaneState` is a SIBLING of `PaneVisibility`, not an addition to it: `PaneVisibility` is a
  stateless singleton `object`, so mutable fields on it would be shared across `MainActivity`
  instances, including the two that briefly coexist during a recreation.
- `PaneState` exposes plain `Int`/`Boolean`, not a `Bundle`, for the same reason `ReportLog.restore`
  does (D58 step 1's precedent): `android.os.Bundle.putInt`/`getInt` are non-functional stubs under
  this module's `unitTests.isReturnDefaultValues = true`, so a `Bundle`-typed round trip could never
  actually be asserted in this suite. `MainActivity` does the real Bundle read/write itself.
- `HandoffAdmission`'s `av://` guard (finding #10's mitigation) was updated mechanically to read
  `paneState.readInProgress` in place of the old bare field — semantics unchanged, verified by the
  orchestrator reading this diff line directly.
- **Accuracy note, recorded because it corrects a loose reading of the audit**: `MainActivity` has no
  `onPostCreate` override and never did (confirmed by grep before this step). The framework's default
  `onPostCreate` still runs and still restores `TabLayout.selectedTabPosition` — this step does not
  remove or no-op that restore, it simply removes it as an input the pane decision consults.

Untouched by this step: finding #4 (traced, not fixed — see the separate findings.md status update)
and finding #6 (the `readInProgress` field it names moved location alongside this step, but the
`handleIncomingIntent` tag guard still never consults it — finding unchanged, still OPEN).

---

## How verified — unit tests

JUnit XML, plain-JVM unit test run (`apps/scanner`'s `isReturnDefaultValues = true` module):

| | Before | After |
|---|---|---|
| Total tests | 151 | 162 |
| Failures | 0 | 0 |

Eleven new tests in `PaneStateTest`, covering all four legal states and both restore paths (initial
state, tab-selection transitions in both directions, out-of-range-index normalization to Scan,
`readStarted`/`readFinished` toggling independently of tab selection, and save/restore round-trips
for both tabs). Failing-first was demonstrated as 30 unresolved-reference compile errors, produced by
moving `PaneState.kt` aside before the production change landed.

---

## How verified — device run (Pixel 6a, app pid 17066, 2026-09-02)

Four cases, all passed, using a real `av://` handoff and a real chip read to put the app into a
realistic post-scan state before forcing recreation:

| Time | Event |
|---|---|
| 10:41:50 | `av://` handoff verified — origin `http://127.0.0.1:8787`, dev-pinned key |
| 10:42:22 | NL card, `direct_post` `http_status=200`, `verdict: PASS (minted)` |

Auto-rotate is OFF on this device (same constraint as step 1), so recreation was forced with
`adb shell settings put system font_scale 1.15` followed by `adb shell settings put system
font_scale 1.0`, with the app foregrounded throughout each case; font scale was restored to `1.0`
afterwards.

Tab state was verified programmatically, not by eye: `adb shell uiautomator dump`, filtered to only
the SCAN/LOG tab nodes' `selected` attribute, so no document field entered any transcript. **Why not
a screenshot**: the scan form renders real MRZ fields, and a screenshot (or an unfiltered
accessibility dump) of that screen is unsafe to capture into any transcript, this one included.

| Case | Steps | Result |
|---|---|---|
| a | On Log tab after a scan, recreate | Still Log; log entries intact — restore line logged twice, `10:43:03.070` and `10:43:06.147`, both `M2 stage: restored report/log across Activity recreation (text=true, log_entries=1)` |
| b | Tap Scan, recreate | Still Scan |
| c | Tap Log, tap Log again (reselect), recreate | Still Log, no flicker |
| d (orchestrator-added, not in the brief) | On Scan, two recreations back to back | Still Scan across both |

Process pid `17066` was unchanged across every recreation in all four cases, confirming the Activity
was rebuilt while the process lived (not a process restart, which would not exercise this step's
restore path at all).

No pane-specific logcat line exists for this step — `PaneState`'s restore has no `Log.i` of its own.
The evidence for the tab surviving recreation is the filtered `uiautomator dump`'s `selected`
attribute (case a/b/c/d above); step 1's existing restore line (`M2 stage: restored report/log
across Activity recreation...`) is reused here only as corroboration that the same recreation also
carried the report/log cluster through correctly, not as evidence for the tab index itself.

---

## Caveats, stated plainly

- **Auto-rotate is off on this device.** As in step 1, recreation was forced via a `font_scale`
  config-change toggle, not a physical rotation — the same `onSaveInstanceState`/`onCreate` path a
  real rotation exercises, but not a rotation itself. No rotation-triggered recreation of this
  specific change has been separately captured.
- **Tab state was verified by a filtered UI dump, not a screenshot or a full accessibility dump.**
  This is deliberate, not a shortcut: the scan form renders real MRZ fields, so a screenshot or an
  unfiltered dump of that screen would put document data into this transcript. The filter reduces the
  capture to the SCAN/LOG tab nodes' `selected` attribute only.
- **No new pane-specific logcat line exists.** Unlike the report/log cluster (step 1), `PaneState`
  emits no restore log of its own — this step's device evidence for the tab index is the filtered UI
  dump, corroborated by (not derived from) step 1's existing restore line confirming the same
  recreation also carried the report/log cluster through.
- **Case (d) was not in the original brief.** The orchestrator added it to check two recreations in
  immediate succession, since a single-recreation case cannot distinguish "restored correctly" from
  "never actually reset in the first place" if the underlying `PaneState` object happened to survive
  by accident. It passed, but a genuine third-party review of the brief's other three cases was not
  independently re-run beyond what is recorded here.
- **`readInProgress`'s own device behaviour was not separately exercised.** All four cases above
  recreate the Activity with no read in flight (a read cannot meaningfully survive an Activity
  recreation regardless — nothing resumes an in-flight `IsoDep` session, unchanged from before this
  step). This step's device evidence is about the tab index only.

---

## What this run did and did NOT establish

**Did establish:**
- Finding #1 is closed by construction: `PaneState` is the pane decision's single owner for both
  inputs; `showPane()` no longer reads `tabLayout.selectedTabPosition`, so the framework's
  independently-timed tab restore cannot race it.
- The tab index survives a forced Activity recreation correctly in all four tested combinations
  (Scan/Log × plain recreation/reselect-then-recreation/back-to-back recreations), verified by a
  filtered UI dump rather than by eye.
- The same recreation that carries the tab index through also carries the report/log cluster through
  correctly (step 1's restore line fired as expected), i.e. this step did not regress step 1.

**Did NOT establish:**
- Behaviour under a genuine physical rotation (only the font-scale config-change trick was used, as
  in step 1).
- Anything about `readInProgress`'s device behaviour across a recreation (not exercised — a read
  cannot survive one regardless, by design).
- Finding #4's remaining open surface (the `lastMrzHash` diagnostic mislabel, and the other ten of
  eleven lost fields) — traced by the coder as a report-only deliverable this session, not fixed, and
  not exercised on device by this run.
- Finding #6 (the NFC-tag guard's missing `readInProgress` check) — unaffected by this step; not
  exercised by this run.

---

**No PII values appear anywhere above.** All quoted logcat lines and UI-dump references are
value-free by construction — stage names, boolean/count fields, tab-selection state, and timings
only — checked against this file's own rule and the project standard it inherits from
`M0-EVIDENCE.md` / `M2-D50-D53-EVIDENCE.md` / `M2-D55-D56-EVIDENCE.md` / `M2-D58-STEP1-EVIDENCE.md`
before inclusion.
