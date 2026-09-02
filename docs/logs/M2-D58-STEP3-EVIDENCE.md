# M2 — device evidence for D58 step 3 (lock-time snapshot, Pixel 6a, 2026-09-02)

**Status**: source record for `65096b9` ("D58 step 3 — lock-time AuthorizedHandoff snapshot
(findings #2/#3)"), written after the fact from the orchestrator's own JUnit XML check, source diff,
and two device sessions' logcat + the verifier's own transaction state, all produced this session.
This file is the evidence `.claude/remember/findings.md` #2/#3's, #10's, and #11's status updates and
`docs/product/zkagent-prd.md`'s D58 step-3 status line cite; it mirrors `M2-D58-STEP2-EVIDENCE.md`'s
structure and value-free rule.

**Rule for this file (carried from `M0-EVIDENCE.md` / `M2-D50-D53-EVIDENCE.md` /
`M2-D55-D56-EVIDENCE.md` / `M2-D58-STEP1-EVIDENCE.md` / `M2-D58-STEP2-EVIDENCE.md`)**: no PII values,
ever — field names, verdict strings, timings, hashes/truncated transaction identifiers, and exception
text only. No MRZ field, name, date of birth, document number, raw zktag, nonce, public key, or
signature appears anywhere in this file.

---

## What changed (`65096b9`)

D58 step 3 is the ownership refactor's headline target: the lock-time snapshot that closes the
three-writer `pendingHandoff`/`verifiedRequest` race (`.claude/remember/findings.md` #2/#3) by
construction.

- A new immutable `data class AuthorizedHandoff(request, origin, site)` (`AuthorizedHandoff.kt`) is
  captured EXACTLY ONCE, on the main thread, inside `lockModeAndArm` — the same function that already
  derives `mode` (A vs. B) from the same two mutable fields. From that point on, the snapshot is
  threaded as a function PARAMETER through `startSession`, `ReadTask`, `continueAfterRead`,
  `promptAndMint`, and `mintAndMaybeHandoff`, matching the existing discipline the file already used
  for `mode`/`zktag`/`scopeDomain` (per the race analysis's own observation that this was "the more
  idiomatic fit for this codebase's existing discipline").
- Both cross-thread read sites of `pendingHandoff`/`verifiedRequest` named by finding #2/#3 — the old
  `continueAfterRead`'s `Thread{}` read and `mintAndMaybeHandoff`'s independent re-read — are DELETED.
  No background code reads either mutable field any more.
- The three reads that remain are all main-thread: `refreshModeStatus`, `lockModeAndArm` itself (which
  still must read the live fields once, to decide what to snapshot), and
  `applyHandoffVerificationOutcome`'s staleness check (posted via `runOnUiThread`).
- No staleness guard or generation counter was added, by construction: a later `beginHandoffVerification`
  call (a second handoff, or an admitted `av://`/paste intent) can still overwrite the mutable fields
  for the NEXT attempt, but it cannot reach back and alter an already-constructed `AuthorizedHandoff` —
  a `val` cannot be reassigned, and the class has no setter. `AuthorizedHandoffTest` includes a
  reflection check that fails if any field ever becomes `var`.
- The snapshot deliberately excludes the `HandoffClient.PendingHandoff` it was fetched from (nothing
  downstream reads `clientId`/`requestUri` off it) and excludes `scopeDomain` (its host-parse can fail;
  kept at its existing call site so the existing failure point and messages are preserved rather than
  moved earlier).
- The old defensive null-check at the NFC-tag call site was replaced with `val authorized =
  snapshot!!`. **Verified explicitly, not taken on trust**: this cannot throw, via two independent
  gates in sequence — mode A returns early at the `MintGate` check (`MintGate.mayMint` is `modeIsB &&
  verdict.ok && verdict.allowed == true`; `MintGateTest` carries a test literally named `` `mode A
  NEVER mints, even with ok true allowed true` ``), and mode B with a null snapshot is refused earlier
  still by the pre-existing D38 origin guard.
- **`HandoffAdmission` was KEPT, not removed, contrary to D58's stated expectation.** D58 said the
  guard becomes redundant once the snapshot lands and is to be removed. The coder's trace found a
  separate, still-live reason it cannot be removed yet: `applyHandoffVerificationOutcome`
  unconditionally calls `refreshModeStatus()` and sets `lockButton.isEnabled = true` on a successful
  verify, while `refreshModeStatus`'s own doc says it must never run while `lockedMode` is set —
  nothing in code enforces that except this guard refusing the foreign intent before
  `beginHandoffVerification` ever runs. Removing it would let an admitted foreign intent overwrite the
  "Locked: mode X" banner with the attacker's text — inert for re-locking (`lockedMode != null` still
  no-ops a second Lock tap) but a real user-visible display regression. **Guard removal is now
  explicitly blocked on `applyHandoffVerificationOutcome` respecting `lockedMode`** —
  `modeStatusView`/`lockButton.isEnabled` are named on this step's own MUST NOT list, so fixing that is
  D58 step 4's job, not this step's.

Untouched by this step: finding #5 (async-cancellation discipline, unaffected), finding #6 (the
NFC-tag guard's missing `readInProgress` check, unaffected), and the new finding #15 below, which
pre-dates this step and was found by its own required survey rather than introduced by it.

**New finding surfaced by this step's own survey, not fixed**: `applyPendingHandoffText`
(`MainActivity.kt:717-725`, the QR-scan/manual-paste handoff path) calls `beginHandoffVerification`
with NO `HandoffAdmission` gate at all — only the `av://` intent branch of `handleIncomingIntent` is
guarded. Same class as finding #10, asymmetric coverage, pre-existing. Recorded as
`.claude/remember/findings.md` #15.

---

## How verified — unit tests

JUnit XML, plain-JVM unit test run (`apps/scanner`'s `isReturnDefaultValues = true` module):

| | Before | After |
|---|---|---|
| Total tests | 162 | 168 |
| Failures | 0 | 0 |

New `AuthorizedHandoffTest` includes the supersession regression test (a second
`beginHandoffVerification` call after lock time does not alter an already-captured snapshot) and a
reflection check that every field stays `final` (fails if a `val` ever becomes `var`).

---

## How verified — device run (Pixel 6a, two sessions, 2026-09-02, real NL ID card)

Both sessions used a real `av://` handoff and a real chip read; the orchestrator fired hostile
`av://` intents from a separate process (`adb shell am start`) against the app's `RegularActivity`
while the legitimate session was in progress, then cross-checked the outcome against the verifier's
own independent transaction state rather than trusting the device's own log alone.

**Session 1 (app pid 18818)**

| Time | Event |
|---|---|
| — | Owner opened their own fresh link (transaction `PKJepfPSucXR8CWC`), pressed Lock |
| 11:19:10 | Orchestrator fired a hostile `av://` link from a separate process — **REFUSED**: `M2 stage: av:// handoff REFUSED — session locked or read in progress` |
| — | Owner scanned and authorized; mint completed 11:20:19, `direct_post` `http_status=200`, `verdict: PASS (minted)` |

Verifier state, independently checked: `PKJepfPSucXR8CWC` `status=done tier=B
evidence=["sig-p256/1"]`; the hostile transaction (`egfjWF7XjJQlm-Zx`) and an earlier
orchestrator-created link (`qFwDNPZcesF6OWvY`) both remain `status=pending`, no verdict.

**Cross-check lesson, recorded because it corrects a real mistake made this session**: the
orchestrator initially told the owner the mint would land on a transaction the orchestrator had
created. It did not — the owner had opened their own fresh link, superseding the orchestrator's
before lock. The orchestrator caught this only because the verifier reported its own transactions
still `pending`, contradicting what would otherwise have looked like a successful post against the
expected transaction. **The phone's own log said PASS; only the verifier's independent state showed
which transaction had actually completed.** Cross-check the counterparty, not just the device, before
asserting which transaction a run's success applies to.

**Session 2 (app pid 19250)** — the window the race analysis (`M2-RACE-ANALYSIS-2026-09-02.md`)
actually named:

| Time | Event |
|---|---|
| 11:26:00 | Read and mint finished (first attempt) |
| 11:26:07 | Hostile fire — **missed the window**: the session was already consumed, so this intent was legitimately ACCEPTED as a new handoff, not a bypass |
| — | Orchestrator armed a watcher on the logcat stream, firing the hostile intent automatically on the "MRZ input" line (emitted the instant the card is tapped) |
| 11:27:36.970 | Retry: hostile link (`guk3B7oukFhHtjDY`) fired 60ms after the card tap, landing inside the chip read — **REFUSED** |
| — | Owner independently reported seeing the Snackbar mid-scan — the refusal is user-visible, not only logged |
| — | Owner's own read continued undisturbed; PIN prompt followed; mint completed 11:27:43 against `p1faQGkCEybuRNk5` |

Verifier state: `p1faQGkCEybuRNk5` `status=done tier=B` (evidence-verified).

**Across both sessions**, FOUR hostile transactions were fired in three distinct windows (post-lock
pre-read, post-session, mid-read). None ever received a verdict from the verifier — all remain
`status=pending`.

---

## Caveats, stated plainly

- **All hostile links originated from the SAME local verifier origin.** A genuinely foreign origin
  (a different host actually resolving `.well-known/zkagent-verifier` under attacker control) is
  still untested — this run exercises the admission-guard logic (`lockedMode`/`readInProgress`), which
  does not distinguish origins at all, but it does not demonstrate anything about origin-resolution
  behaviour against a real foreign domain.
- **The QR-scan/manual-paste handoff path (`applyPendingHandoffText`, finding #15) was not
  exercised.** It has no `HandoffAdmission` gate at all — this run only probed the `av://` intent
  path, which is guarded.
- **The PIN-prompt window specifically was not tested in isolation.** Session 2's mid-read refusal
  happened before the PIN prompt appeared (the hostile intent was refused during the chip read
  itself); no run in this session fired a hostile intent while the biometric/PIN prompt was actually
  on screen.
- **The first attempt in session 2 was a missed window, not a failure.** The read/mint completed
  (11:26:00) before the hostile fire (11:26:07); the app's behaviour afterward (accepting a new
  handoff into an already-consumed session) is correct, not a gap in the guard — the guard exists to
  refuse admission while a session is LOCKED or a read is IN PROGRESS, neither of which was true by
  11:26:07.
- **Device confirmation for finding #2/#3 (the snapshot itself) is indirect.** Both sessions primarily
  exercised `HandoffAdmission`'s refusal path (findings #10/#11's mitigation); they corroborate that
  every observed mint landed against the transaction actually locked (consistent with the snapshot
  working as designed) but are not a targeted repro of the specific cross-thread-corruption scenario
  finding #2/#3 originally described. Treat this run as corroborating, not dispositive, for #2/#3
  specifically.
- **Cross-check lesson (repeated from above because it is the most important caveat in this
  document)**: the orchestrator's own initial transaction-tracking mistake in session 1 shows that a
  device log reporting PASS is not sufficient evidence of which transaction actually completed —
  independent verifier-side state is required to confirm the counterparty.

---

## What this run did and did NOT establish

**Did establish:**
- Finding #2/#3 is closed by construction in source (`AuthorizedHandoff`, immutable, single capture
  point, no remaining cross-thread reads) and unit-verified (162→168, JUnit XML, 0 failures, including
  a reflection check and a supersession regression test).
- The `!!` at the NFC-tag call site cannot throw, verified via two independent gates (`MintGate`'s
  mode-A-never-mints test; the pre-existing D38 origin guard for mode B).
- `HandoffAdmission` continues to correctly refuse hostile `av://` intents across three real timing
  windows on device (post-lock/pre-read, mid-read via an automated logcat-triggered watcher, and a
  missed-window case correctly showing acceptance once the session was already consumed), with one
  refusal independently confirmed user-visible.
- Legitimate mints in both sessions completed against the transaction actually locked, cross-checked
  against the verifier's own independent pending/done state, not the device's self-report alone.
- `HandoffAdmission` was correctly KEPT rather than removed, and the reason (an unrelated,
  still-live `modeStatusView`/`lockButton.isEnabled` display defect in
  `applyHandoffVerificationOutcome`) is now documented in source and here.

**Did NOT establish:**
- Anything about a genuinely foreign origin — all hostile links this session used the same local
  verifier origin as the legitimate one.
- Anything about the QR-scan/manual-paste handoff path (finding #15) — not exercised, and it has no
  gate regardless.
- Behaviour of a hostile intent landing specifically during the PIN/biometric prompt window — not
  isolated in either session.
- A targeted repro of finding #2/#3's original cross-thread-corruption scenario specifically (as
  opposed to corroboration via the guard's own refusal behaviour).
- Anything about D58 step 4 (the re-derived Session boundary) — not started.

---

**No PII values appear anywhere above.** All quoted logcat lines, transaction identifiers, and
verifier states are value-free by construction — stage names, boolean/status fields, truncated
transaction IDs, and timings only — checked against this file's own rule and the project standard it
inherits from `M0-EVIDENCE.md` / `M2-D50-D53-EVIDENCE.md` / `M2-D55-D56-EVIDENCE.md` /
`M2-D58-STEP1-EVIDENCE.md` / `M2-D58-STEP2-EVIDENCE.md` before inclusion.
