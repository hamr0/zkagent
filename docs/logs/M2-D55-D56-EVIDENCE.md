# M2 — device evidence for D55–D56 (Pixel 6a, 2026-09-02)

**Status**: source record only, written after the fact from three logcat captures already on disk
— a value-free-baseline happy-path capture (`MainActivity:V`), a targeted D55 reproduction capture
(`MainActivity:V`), and the earlier broken-filter capture that motivated redoing it. This file is
the evidence `docs/product/zkagent-prd.md` §10 D55 and D56 cite; those rows already narrate the bug
and the owner's decisions in full — this file exists so the underlying run data survives
independently of the scratchpad it was produced in, and so a reader can check the PRD's claims
against the source rather than trusting the prose alone.

**Rule for this file (carried from `M0-EVIDENCE.md` / `M2-D50-D53-EVIDENCE.md`)**: no PII values,
ever — field names, verdict strings, timings, hashes/truncated identifiers, and exception text
only. All three source logs are value-free by construction (no MRZ, name, date of birth, document
number, raw zktag, nonce, public key, or signature appears in any of them); every line quoted below
was checked against that rule before inclusion.

**Sources**: `baseline-happy-paths.log` (450 lines truncated to the relevant window, `MainActivity`
tag at `:V`, 2026-09-02 08:14:07–08:15:01, two processes, pids 11803 and 11937), `repro-d55-logcat.log`
(`MainActivity:V`, 08:19:56–08:21:06, two processes, pids 11937 and 12166), and `baseline-logcat.log`
(the discarded first attempt at the baseline capture, same window as the first two lines of
`baseline-happy-paths.log`, filtered `MainActivity:I MainActivity:E`). None of the three source
files is committed to this repo; all were produced in a scratchpad and are recorded here by
content, not by path. All three come from the same Pixel 6a used in every prior M0–M2 run, against
the `spikes/m2-handoff` verifier over `av://`/`direct_post`, mode B, both of the project's two real
documents.

---

## Logcat-filter lesson (why there are three source logs, not two)

`baseline-logcat.log` was captured first, with `adb logcat MainActivity:I MainActivity:E *:S`. It
contains only `W`-level `RequestTrust` lines and `I`-level `DeviceKey` lines for its window — every
`I`-level `MainActivity` line (the entire value-free report, every `M2 stage:` diagnostic) is
missing. **Cause: `adb logcat` tag filters are evaluated last-spec-wins, not additively** — a second
`MainActivity:<level>` spec for the same tag replaces the first rather than unioning with it, so
`MainActivity:E` silently overrode `MainActivity:I` and every line below `E` for that tag was
dropped, even though the intent was "show both". The fix used for `baseline-happy-paths.log` and
`repro-d55-logcat.log` is a single spec at the lowest level needed: `adb logcat MainActivity:V *:S`
— `V` (verbose) is inclusive of every level at or above it for that tag, so one spec suffices;
multiple same-tag specs should not be combined at all.

---

## RESULT 1 — baseline happy paths, both documents, first-attempt MRZ (`baseline-happy-paths.log`)

Two independent processes, each a single scan-to-mint run with no retry:

| Process (pid) | Time | MRZ-change diagnostic (D56) | `access_protocol` | `chip_auth` | Outcome |
|---|---|---|---|---|---|
| 11803 | 08:14:07–08:14:14 | `first attempt this session` | PACE | passed | `verdict: PASS (minted)` |
| 11937 | 08:14:56–08:15:01 | `first attempt this session` | PACE unavailable (`CardServiceException`) → BAC | absent, and `CA not supported (no DG14) (CardServiceException)` | `verdict: PASS (minted)` |

Both mints show the established evidence-plug/device-key lines unchanged from the D50–D53 baseline
(`device_key: alg=EC curve=P-256 security_level=STRONGBOX`, `evidence_plug: device_preference=
sig-ed25519/1 used=sig-p256/1`, `evidence_type: sig-p256/1`) and both `direct_post` to
`http://127.0.0.1:8787/wallet/direct_post` returned `http_status=200 {"accepted": true}`. The
second process's PACE-unavailable→BAC fallback, with no DG14 (so no chip-authentication check is
possible), matches this project's standing profile for the document that lacks chip authenticity
(M0, 2026-08-29) — consistent with, not newly establishing, that split. Both are ordinary D56
`first attempt this session` diagnostics: neither process had a prior read attempt to compare
against.

---

## RESULT 2 — D55 reproduction: a deliberate wrong digit, then a corrected retry (`repro-d55-logcat.log`, pid 12166)

One process, two consecutive read attempts on the same document, entered deliberately to reproduce
D55/D56 rather than to observe a spontaneous failure:

- **08:20:43.034** — `M2 stage: MRZ input first attempt this session (doc_len=9 dob_ok=true
  exp_ok=true)`, immediately followed at **08:20:43.898** by `PACE unavailable
  (AccessDeniedException)`, then a full stack trace ending `verdict: FAIL` / `failure:
  AccessDeniedException: Mutual authentication failed: expected length: 40 + 2, actual length: 2
  (SW = 0x6985: CONDITIONS NOT SATISFIED)` at `BACProtocol.doBAC` — an access-establishment
  failure (D43/D54 bucket), consistent with a deliberately wrong digit in one MRZ field: PACE was
  attempted and the derived key was rejected, not merely unsupported.
- **08:20:59.771**, ~16 s later — `M2 stage: MRZ input CHANGED since previous attempt (doc_len=9
  dob_ok=true exp_ok=true)` — the D56 diagnostic correctly distinguishing this retry from the
  first attempt. The corrected retry proceeds through PACE (passed), a fresh masterlist/passive-auth
  check, mint, and a second `direct_post` accepted (`http_status=200 {"accepted": true}`),
  `verdict: PASS (minted)` at 08:21:06.104, reusing the same device key (`key_id` matches the
  first document's key_id from the baseline run above, confirming the reproduction used the same
  physical document as `baseline-happy-paths.log` process 11803).

This is the CHANGED half of D56's UNCHANGED/CHANGED/first-attempt diagnostic, confirmed on device:
the field-level `doc_len=9 dob_ok=true exp_ok=true` shape is identical between the failed and
corrected attempts (by design — D56 logs structural checks, never values or a value-derived
comparison result beyond changed/unchanged), while the `UNCHANGED`/`CHANGED` verdict itself
correctly flips between the two attempts in this process.

**Caveat, stated plainly:** this run did not exercise the Log-tab-then-retap stranding path D55
fixes (opening the Log tab after the failure, then retapping while `main_layout` is still covered
by `log_layout`) — the retry here corrected the MRZ from the same tab with no intervening tab
switch. No `UNCHANGED` line appears in either capture, meaning the specific "stale-value re-tap"
symptom described in D55's original bug report was not reproduced by these two runs; what IS
confirmed is the two halves D56 depends on working correctly in isolation: (1) the form remains
reachable and editable after a failure (`08:20:59.771`'s `CHANGED` line proves the corrected value
reached the app, i.e. "form-reachable-after-failure" holds for this manual, no-tab-switch retry),
and (2) the diagnostic itself correctly reports first-attempt vs. changed vs. (untested here)
unchanged.

---

## RESULT 3 — prior-session evidence, recorded here for completeness (2026-09-01, pre-fix)

Not from a log file read for this document — carried forward from the PRD's own D55 text
(`docs/product/zkagent-prd.md` §10 D55/D56 header), because it is the run the D55 fix was written
against and belongs alongside this file's own captures as the "before" state. **pid 9948**, four
consecutive `PACE unavailable (AccessDeniedException)` → `org.jmrtd.AccessDeniedException` failures
at **23:52:35, 23:52:41 (D6 s later), 23:52:51 (D10 s), and 23:53:04 (D11 s)**, with one handoff
captured at 23:52:09 before the run started — spacings of 6/10/11 seconds, judged too fast for a
retype between attempts, which is itself the evidence the retries could not have carried corrected
MRZ details: the user had opened the Log tab after the first failure and could not get back to the
form (the D55 bug, pre-fix). This run predates both the pane-visibility fix and the D56 diagnostic,
so no `MRZ input CHANGED/UNCHANGED` line exists for it — its evidentiary weight is the timing
pattern and the failure-class repetition alone, exactly as already recorded in the PRD.

---

## What these runs did and did NOT establish

**Did establish:**
- The `adb logcat` last-spec-wins tag-filter behavior (RESULT 1's `baseline-logcat.log` vs.
  `baseline-happy-paths.log` contrast) — a real capture-tooling defect, not an app defect, worth
  recording so it is not re-hit.
- Both documents' happy paths mint cleanly with no retry, using the established evidence-plug/
  device-key mechanism, unchanged from D50–D53 (RESULT 1).
- D56's `CHANGED` diagnostic correctly flips from a first-attempt failure to a corrected retry,
  confirmed on hardware for a manual, no-tab-switch retry (RESULT 2).
- Form-reachable-after-failure holds for a retry that never opens the Log tab (RESULT 2) — this is
  a narrower claim than "D55's stranding bug is fixed and reproduced-then-resolved on device," which
  these two captures do not show.

**Did NOT establish:**
- The specific Log-tab-then-retap stranding path D55's fix targets — no `UNCHANGED` line appears in
  either capture, and neither run switched to the Log tab mid-retry. This is a real gap in this
  evidence set, stated rather than papered over: the fix's own hardware confirmation for the exact
  failure sequence D55 describes is still pending a run that reproduces it end to end.
- Anything about a second device — both captures, like every prior M2 run, used the one Pixel 6a.
- The 2026-09-01 four-failure run's own MRZ-change state, since D56 did not exist yet when it was
  captured (RESULT 3, recorded from the PRD's prior text only).

---

## PENDING

- A device run that reproduces the exact D55 stranding sequence (failure → open Log tab → retap →
  observe `UNCHANGED` from stale MRZ → fix confirms the form is reachable and `CHANGED` appears
  once corrected) has not yet been captured. Until then, D55/D56 are confirmed as working code
  paths (RESULT 1, RESULT 2) but not as a reproduced-then-resolved regression test against the
  original failure mode.
- A second device remains untested for D55/D56, same standing gap recorded in
  `M2-D50-D53-EVIDENCE.md`.

---

**No PII values appear anywhere above.** All three source logs are value-free by construction —
field names, verdict strings, timings, truncated identifiers, hashes, and exception text only — and
every quoted line was checked against that rule before inclusion, per this file's own rule and the
project standard it inherits from `M0-EVIDENCE.md` / `M2-D50-D53-EVIDENCE.md`.
