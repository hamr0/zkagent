# M2 — device evidence for D58 step 4 (SessionDisplay projections, Pixel 6a, 2026-09-02)

**Status**: source record for `c38833d` ("D58 step 4 — SessionDisplay projections; paste path gated
(findings #9, #14, #15)"), written after the fact from the orchestrator's own JUnit XML check, source
diff, and one device session's logcat, produced this session. This file is the evidence
`.claude/remember/findings.md` #9's, #10's, #11's, #14's, and #15's status updates and
`docs/product/zkagent-prd.md`'s D58 step-4 status line cite; it mirrors `M2-D58-STEP3-EVIDENCE.md`'s
structure and value-free rule.

**Rule for this file (carried from `M0-EVIDENCE.md` / `M2-D50-D53-EVIDENCE.md` /
`M2-D55-D56-EVIDENCE.md` / `M2-D58-STEP1/2/3-EVIDENCE.md`)**: no PII values, ever — field names,
verdict strings, timings, hashes/truncated transaction identifiers, and exception text only. No MRZ
field, name, date of birth, document number, raw zktag, nonce, public key, or signature appears
anywhere in this file.

---

## What changed (`c38833d`)

D58 step 4 is the last of the four ownership-refactor steps D58 ordered — re-deriving the Session
display boundary rather than leaving `handoffStatus.text`/`modeStatusView.text`/`lockButton`
write-only and multi-writered.

- A new pure class, `SessionDisplay` (`SessionDisplay.kt`): `render(lockedMode, handoffState)` returns
  a `Projection`. `HandoffState` is a sealed class — `None` / `Verifying` / `Verified(origin, tier)` /
  `Refused(reason)`. **Locked state takes unconditional precedence** in `render`: an admitted foreign
  verification cannot alter a locked session's display even if `HandoffAdmission` were removed
  entirely — the precedence rule, not the guard, is what makes display safe.
- `MainActivity.applySessionDisplay` is now the SOLE writer of `modeStatusView.text`,
  `handoffStatus.text`, `lockButton.isEnabled`, and `lockButton.text`. Orchestrator verified directly:
  only `MainActivity.kt:430-433` assign these four properties anywhere in the file. Six prior write
  sites collapse into this one applier; `refreshModeStatus` (one of the six) is deleted.
- **Finding #14 closes by construction**: the mint completion path re-derives the whole projection, so
  a consumed session can no longer leave a stale "verified/waiting" handoff status behind. **Finding
  #9 closes the same way** (`lockButton.isEnabled`'s four writers collapse into the one applier).
  **Finding #15 closes**: `applyPendingHandoffText` (the QR-scan/manual-paste path) now applies the
  same `HandoffAdmission` predicate as the `av://` path before calling `beginHandoffVerification`, with
  the same refusal shape — `Log.e` + Snackbar + return, no log entry (finding #13's rule) and no
  `showBlockingOutcomeDialog` (finding #12's rule).
- **Behaviour change, flagged by the coder, not hidden**: `wipeSession` no longer force-enables the
  Lock button. During an in-flight verification the button now stays disabled instead of contradicting
  the "verifying" banner. The orchestrator verified this cannot strand a user: the refused path renders
  the button disabled only while a blocking dialog covers the screen, and the dialog's OK handler
  re-derives the projection to enabled; the verification network calls carry 10-second connect and
  read timeouts (`HandoffClient.kt:87-88,156-157` and `RequestTrust.kt:150-151`) and the background
  worker posts an outcome even on exception, so every terminating path re-renders the projection —
  there is no path that leaves the button disabled with nothing pending behind it.
- `HandoffAdmission` was KEPT and no `SessionState` class was extracted — both were recommendations
  only, per this step's brief, not requirements. The coder RECOMMENDS keeping the guard: its remaining
  value is preventing a foreign handoff from overwriting the mutable `pendingHandoff`/`verifiedRequest`
  fields while locked or reading, which this step did not close and which has no test coverage of its
  own. Mint correctness does not depend on it (step 3's snapshot closed that); display corruption no
  longer depends on it either (this step closed that).
- **Job 4 deliverable, the re-derived Session boundary — reported, deliberately not implemented.** The
  coder recommends TWO units, not the one `SessionState` the structure proposal proposed: (a) a
  session-state holder for `lockedMode`/`authorizedHandoff`/`pendingHandoff`/`verifiedRequest`/
  `lastMrzHash`, and (b) `SessionDisplay` as a separate pure projection with no mutable state —
  because folding a stateless decision into a stateful holder loses the testability the project's
  existing pattern depends on (`PaneVisibility` vs. `PaneState` is the precedent this follows). The
  display boundary has SIX legal projections: locked-A and locked-B are each a single projection
  regardless of handoff state (the precedence rule means handoff state is irrelevant to what renders
  while locked), plus unlocked crossed with `None`/`Verifying`/`Verified`/`Refused` (four more) — 2 + 4
  = six distinct rendered outputs, not the twelve a naive full cross-product of {locked-A, locked-B,
  unlocked} × {four handoff states} would suggest. The underlying mutable fields still have more
  more reachable combinations than that, but they are invisible rather than illegal, and the
  locked-wins rule is exactly what makes leaving them reachable safe. **Record that the structure
  proposal's cluster 1 is now STALE on two points**: its proposed `@Volatile`/CAS synchronization was
  made moot by step 3's parameter threading, and its proposal to fold the three views in as owned
  fields is superseded by modelling them as a projection instead.

Untouched by this step: findings #4 and #6 (unchanged, still OPEN, not surveyed by this step's work);
finding #5 (async-cancellation discipline — see its own status update, now the principal remaining
blocker on D57's exit criterion, since this step closes #9/#14/#15 and #10/#11 stay mitigated).

---

## How verified — unit tests

JUnit XML, plain-JVM unit test run (`apps/scanner`'s `isReturnDefaultValues = true` module):

| | Before | After |
|---|---|---|
| Total tests | 168 | 180 |
| Failures | 0 | 0 |

New `SessionDisplayTest` (12 tests) covers all six legal locked×handoff-state projections, including
one test named for finding #14's exact defect (a consumed session must not render a stale
verified/waiting status) and two dedicated "locked wins" tests (a `Verified`/`Refused` handoff state
arriving while `lockedMode != null` cannot change the rendered projection).

---

## How verified — device run (Pixel 6a, app pid 21642, 2026-09-02, real documents, owner-run)

**Mode-B end-to-end runs, both minted successfully:**

| Time | Event |
|---|---|
| 13:31:27 | Mode-B run — `direct_post` HTTP 200, verdict PASS (minted), verifier confirming tier B evidence-verified |
| 13:35:09 | Second mode-B run — same outcome, `direct_post` HTTP 200, verdict PASS (minted) |

(Transaction `U91Nf6e3mNFDnlja` was among the confirmed-verified set.)

**Consumed-session confirmation (finding #14's exact scenario):**

| Time | Event |
|---|---|
| 13:35:50 | Further scan on the now-consumed session, no new link opened — correctly ran as a local read: `mint_gate: NOT MET — evidence: [] (D27)`, `verdict: PASS (read)` |

This is finding #14's exact prior-observed defect scenario (a scan running against a session the user
believed was still a live handoff), and the app no longer treats the consumed link as live — the
data/log-level behaviour is confirmed correct.

**NOT YET CONFIRMED VISUALLY**: the orchestrator could not read the `handoffStatus.text` line on
screen during these runs. The owner was on the Log tab (where that view is not rendered) for the
confirming scan, and a later attempt to check the Scan tab's status line hit the notification shade
instead. **Record plainly: this fix is confirmed at the log/behaviour level; the on-screen blank status
line itself was not visually verified this session.**

**Owner's UX gap observation, recorded as a real gap, not a defect of this fix**: across roughly five
scans the owner could not tell the runs apart from the screen alone. No popup and no Snackbar
distinguishes a consumed-session local read from any other kind of scan — the only signal is a log
line, and the status line that does carry the distinction lives on the Scan tab, so a user working on
the Log tab never sees it at all. This is a real UX gap, not a regression introduced by this step —
it is exactly the shape of the three new owner UI items recorded below, deferred under D57.

**Environment note, recurring on this host**: `adb` dropped the device mid-setup and was recovered
with `adb kill-server` / `adb start-server`. Worth keeping as a recipe note for future sessions on this
host.

---

## Three new owner UI items (all DEFERRED under the D57 freeze)

Recorded here as the device-run source for PRD Q44/Q45/Q46 (§11). All three are settled at the UI
module alongside Q43 (collapsible log entries), not designed here.

1. **Dim a completed run, with ticked checkboxes.** The owner's answer to "nothing tells me the run is
   done": a successful run should DIM the completed run's display, with its checks shown as ticked
   boxes, so a finished scan is visually distinct at a glance.
2. **A single control distinguishing "verify" vs. "scan local."** The owner does not think a reset
   button is needed, but suggests one control labelled to distinguish the two actions — along the
   lines of "verify" versus "scan local" — so the user knows in advance which kind of scan they are
   about to perform. Recorded as an open shape, not a decided design.
3. **Label correctness bug**: the MRZ input field is labelled "Passport number." That is factually
   wrong — this app reads national ID cards as well as passports, and was validated on an NL ID card.
   It should read "Document number" or "Passport/ID." This is a correctness defect, not a styling
   preference, deferred to the same UI pass.

---

## Caveats, stated plainly

- **The on-screen `handoffStatus.text` line was not visually re-verified this session** for finding
  #14's fix — confirmed at the log/behaviour level only (see above).
- **The re-derived Session boundary (job 4's deliverable) was reported, not implemented.** No
  `SessionState` holder or equivalent exists yet; the mutable `pendingHandoff`/`verifiedRequest`/
  `lockedMode`/`lastMrzHash` fields are unchanged in ownership shape from step 3.
- **`HandoffAdmission` guard removal remains an open owner decision**, now resting purely on a
  recommendation (the field-overwrite value has no test coverage), not on any remaining code
  dependency — see findings #10/#11's status updates for this commit.
- **Findings #4 and #6 are untouched**, still OPEN, not surveyed by this step.
- **Finding #5 (zero async-cancellation discipline) is untouched** and is now, by elimination, the
  principal remaining blocker on D57's exit criterion — see its own status update.
- **No genuinely foreign origin was tested this session** — the device runs used the owner's own real
  handoffs, not an adversarial fire against `HandoffAdmission` (that was step 3's device evidence, not
  repeated here).

---

## What this run did and did NOT establish

**Did establish:**
- Findings #9, #14, #15 are closed by construction in source (`SessionDisplay`, single applier,
  admission-gated paste path) and unit-verified (168→180, JUnit XML, 0 failures, including a test named
  for #14's exact defect and two locked-wins tests).
- Findings #10/#11 remain mitigated, not closed, with the guard's remaining value narrowed precisely
  to field-overwrite prevention — mint correctness and display corruption no longer depend on it.
- The `wipeSession`/Lock-button behaviour change cannot strand a user, verified via the timeout values
  at the two network call sites and the outcome-posting behaviour of the background worker on every
  terminating path, not by inspection alone.
- Two full mode-B mints succeeded end-to-end on device, and a subsequent scan on the consumed session
  correctly ran as a local read with no mint-gate evidence — finding #14's exact scenario, confirmed
  not to recur at the data/log level.
- The re-derived Session boundary's shape (two units, not one; six legal projections) is documented in
  source and here, as a recommendation for the next step, not a decision.

**Did NOT establish:**
- Visual, on-screen confirmation of the `handoffStatus.text` line's correctness during either device
  run — Log-tab visibility and a notification-shade interruption both prevented it.
- Any implementation of the re-derived Session boundary itself (job 4 was report-only, by design).
- Anything about a genuinely foreign origin's `HandoffAdmission` behaviour (not exercised this step).
- Any resolution of findings #4, #5, or #6 (unaffected, #5 now the principal remaining D57 blocker).
- Any design for the three new owner UI items or Q43/Q44/Q45/Q46 — recorded as open shapes only.

---

**No PII values appear anywhere above.** All quoted logcat lines, transaction identifiers, and
verifier states are value-free by construction — stage names, boolean/status fields, truncated
transaction IDs, and timings only — checked against this file's own rule and the project standard it
inherits from `M0-EVIDENCE.md` / `M2-D50-D53-EVIDENCE.md` / `M2-D55-D56-EVIDENCE.md` /
`M2-D58-STEP1-EVIDENCE.md` / `M2-D58-STEP2-EVIDENCE.md` / `M2-D58-STEP3-EVIDENCE.md` before inclusion.
