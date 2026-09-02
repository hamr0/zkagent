# Session Stash — M2 scanner: D43 blocking dialogs + D44 log view (2026-09-01)

Project: zkagent · Owner: hamr · Branch: `m2-build` (tree CLEAN at stash time,
continuation of the same day's earlier session)

Goal this session: build §6.2 items 15 (D43 blocking dialogs) and 16 (D44
per-scan log view), already through the PRD scope gate but not built.
Everything else grew out of device runs. Main session never coded or edited
docs — one code-developer subagent and one PRD subagent, resumed repeatedly.
Every user-facing string went to the owner for approval. NO-GO #10 honoured:
PRD amended before each build. Orchestrator independently re-verified every
subagent claim (build + JUnit XML counts parsed directly, not taken from
reports).

## Commits (all on `m2-build`, in order)

- `bfdca3f` feat(spikes/m2-handoff): make verifier age threshold configurable
- `590ff24` docs(stash): M2 handoff end-to-end + attester binding session
- `70490a8` docs(prd): v1.24-v1.32 — D45-D51, Q33 split, Q37 closed
- `5b44701` docs(prd): v1.33-v1.34 — D52 success confirmation, D53 log block
  trimmed
- `0a40183` feat(apps/scanner): D43-D53 — blocking dialogs, per-scan log
  view
- `ab57b4f` docs(logs): M2 device evidence for D50-D53 (2026-09-01)
- `2cd1e00` fix(apps/scanner): classify read failures by evidence, not code
  path

PRD went v1.23 → v1.35. Decisions D45-D54. Questions: Q31/Q32/Q37 closed;
Q34, Q35, Q36 OPEN.

## Decisions D45–D54 (all owner-approved 2026-09-01)

- D45 log accumulates for the app session; lifetime decoupled from
  `wipeSession()`, which had been clearing it on its own success path.
- D46 entries titled by verified site; disclosure legible to a
  non-engineer.
- D47 four-field plain block (Result/Sent/Shared/Identity) + subordinate
  `▸ technical:` line.
- D48 Identity strings confirmed; Shared states the actual predicate and
  answer.
- D49 answers always the boolean true/false; Shared is a LIST of
  predicate→boolean pairs (one element until Q34).
- D50 D39 confirmed on hardware; newest-first ordering; one log entry per
  scan; session-expiry protection. Defect 3's original causal claim was
  CORRECTED in place (see Corrections below).
- D51 D38 confirmed on hardware; third failure bucket (transient tag-loss
  keeps MRZ); mode radio REMOVED and mode derived, closing F5's class by
  construction; three-state chip status; item 6/F1's onStop MRZ wipe
  REAFFIRMED (relaxation considered and declined).
- D52 successful delivered-and-accepted presentation must confirm itself
  with a blocking modal; only Accepted qualifies.
- D53 Mode line REMOVED from the plain block (redundant with
  Sent/Shared/Identity; and under D21 the chip is read identically in both
  modes, so plain-language "mode A" risks reading as "we read less"); chip-
  auth wording made calmer.
- D54 shortened failure copy + the classification-order bug fix (folded
  into one decision, not split).

## Approved user-facing strings (verbatim, owner-approved)

- Identity: `new — minted fresh for this site` /
  `known — recognized only here from previous visit` ("only here" is
  load-bearing — it is D39's per-(origin,zktag) isolation in plain
  language; MUST NOT be simplified out).
- Shared: `age > 18: true — and nothing else.` + negation line; sourced
  from the SIGNED CLAIM MAP so it cannot drift from the payload.
- `claim_proof: self-asserted by the device — not independently proven
  (D24)`
- Session expiry: `Verification session expired — reopen the link from the
  site.` / Result `Refused — verification session expired`.
- Success dialog: `ID scanned successfully`.
- Access-establishment: `Couldn't read — check your details and try
  again.` / Result `Couldn't read — check your details`.
- Transient: `Couldn't read — keep the card at the top of your phone.` /
  Result `Couldn't read — card moved`.
- Chip auth (three states): `Verified — this document's chip proved it is
  genuine` / `Not supported — this document has no chip authenticity
  check` / `Not verified — the chip check did not pass`.

## The headline finding (Q35/Q36)

The scanner asserts `over_threshold: true` UNCONDITIONALLY with a
hardcoded threshold and never compares the chip's DOB to anything. The
threshold IS already carried, signed AND nonce-bound, at
`zkagent.challenge.threshold`, and chiproof ALREADY enforces D11 fully
(`threshold_mismatch` / `under_threshold`). So every passing run to date
passed only because two independently hardcoded 18s agreed — one in
Kotlin, one in JavaScript, with nothing in either codebase expressing the
coupling. Consequence: all `allowed=true` results so far are evidence
about PLUMBING, NOT about age. Q35 = read the already-signed value (one
line, no protocol change). Q36 = compute a real answer from DOB (genuine
design work). A sibling `zkagent.threshold` field was tried and REVERTED
because it would be JWS-signed but NOT nonce-bound.

## Device evidence (Pixel 6a, real NL ID card + US passport)

Folded into `docs/logs/M2-D50-D53-EVIDENCE.md`, 260 lines. 8 transactions
created, 7 verdicts, 1 never presented. 2 × `attester=bound_first_sight` —
first hardware confirmation of D39 per-(origin,zktag) key isolation (the
same pairing had previously shared one key under D38's per-origin
scheme). 5 × `attester=matched` — D38 first-sight binding holding
repeatedly. Device key: P-256/StrongBox; `device_preference=sig-ed25519/1
used=sig-p256/1` (F2 algorithm agility).

## Bugs found (all by real-device runs, none by tests)

1. Stale "In progress" log entry — the mint gate's biometric-prompt
   `emitReport` appended one entry and the terminal outcome appended a
   SECOND, so every scan left a dangling unresolved entry. Fixed by
   replacing the pending entry in the accumulator, NOT by suppressing the
   log call (suppressing would recreate the original unlogged-write
   defect).
2. Log ordering — newest entry must be first.
3. CLASSIFICATION-ORDER BUG (the significant one). `MainActivity.kt`
   ~900-912 caught ANY Exception during access establishment and set
   `accessFailure = true` — classifying by CODE PATH, not by evidence. The
   transient check at ~1014 was gated behind `!accessFailure`, so it could
   only fire AFTER access had already succeeded. Result: a card slipping
   mid-read during access establishment was reported as a data-entry
   problem, sending the user to re-check details that were correct.
   Fixed: `FailureTransition.classify(throwable)` checks transient FIRST,
   then access-establishment (narrowed to `AccessDeniedException` and
   documented `SW 0x6300→0x6985`), else unclassified; the mutable flag and
   its catch block are gone; unrecognised exceptions still fall to RESET.
   WHY TESTS MISSED IT: `FailureTransitionTest` asserted the keep/reset
   MAPPING, and that mapping was correct — both buckets keep MRZ+mode, so
   the state transition is identical either way. Only the MESSAGE
   differed. A suite pinning state transitions is structurally blind to a
   bug that changes advice. `FailureTransitionTest` went 13 → 26 tests.

## Corrections made in-session (recorded plainly — project standard is to correct on sight)

- The orchestrator wrongly diagnosed a "consumed session left pending"
  defect. The coder traced it and the orchestrator verified:
  `MainActivity.kt:1033-1034` (pre-existing) already cleared
  `pendingHandoff`/`verifiedRequest` on every delivery outcome. D50's
  defect-3 mechanism was CORRECTED in the PRD in place, evidence kept,
  interpretation retracted — including retracting a "same class as the
  scope-constant/threshold-constant findings" comparison that did not
  hold. The expiry check remains as NEW protection, not a bug fix.
- The orchestrator's run-count summary was wrong on three points,
  corrected by the evidence subagent against the source logs: 2 ×
  `bound_first_sight` not 3; 5 consecutive `matched` not 4; document order
  reversed (PACE + chip_auth passed first, BAC + absent second — inferred
  from the access-protocol pattern, since neither log carries a
  document-identifying value).
- The owner's five consecutive read failures at 23:37 were genuinely
  WRONG TYPED MRZ DETAILS, not a regression: `PACE unavailable
  (AccessDeniedException)` then BAC also rejecting the same MRZ-derived
  key. Diagnostic worth keeping: `PACE unavailable
  (CardServiceException)` = chip does not support PACE, falls back to
  BAC; `PACE unavailable (AccessDeniedException)` = PACE WAS available,
  attempted, key REJECTED. Both protocols rejecting the derived key
  identifies wrong input. The D43-D53 commit touched no MRZ/BAC/PACE
  key-construction code.

## Test counts

Scanner unit tests 58 → 120 (all passing, verified from JUnit XML by the
orchestrator each round). Spike suite 23/23.

## Known limitation

Span styling (the log title's +1sp size bump) is NOT unit-testable in
this module — `android.text.SpannableStringBuilder` is a non-functional
stub under `unitTests.isReturnDefaultValues = true` (returns null/0). A
probe test that appeared to pass for that wrong reason was REMOVED rather
than left in. Only the pure range calculation is tested; actual styling
needs a device or instrumented run.

## Staging recipe (for the next session)

- Verifier: `cd spikes/m2-handoff && LINK_SCHEME=av nohup node server.mjs
  > <scratchpad>/handoff.log 2>&1 &`. Restarting it CLEARS the in-memory
  attester store, so `matched` reverts to `bound_first_sight`.
- `THRESHOLD=21 node server.mjs` demonstrates Q35 on hardware: the app
  still asserts 18 and chiproof rejects it as `threshold_mismatch`.
  Requires a restart, so do it LAST.
- Device: `adb install -r
  apps/scanner/app/build/outputs/apk/regular/debug/app-regular-debug.apk`,
  `adb reverse tcp:8787 tcp:8787` (re-do after ANY adb restart — `adb
  reverse --list` empty is the two-second check), `adb shell svc power
  stayon usb`, `adb shell am force-stop com.zkagent.scanner`.
- Build: `JAVA_HOME=/home/hamr/opt/jdk-21.0.12.1+1 ./gradlew clean
  :app:assembleRegularDebug :app:testRegularDebugUnitTest` in
  `apps/scanner` (system java-25 has no javac).
- Owner flow: pre-type MRZ (page TTL 120s) → Chrome `http://127.0.0.1:8787/`
  → mode B → tap `av://` → Lock → tap document → PIN.

## Open / next

- A device run on the current build (`2cd1e00`) has NOT yet been
  completed — staging is live, APK installed, logcat capturing to
  `run3-logcat.log`. Items 15/16 cannot close without it: a modal dialog
  and a rendered log tab are not provable from unit tests.
- Q35 / Q36 — owner has not decided whether they become §6.2 items inside
  M2 or work that follows it.
- Q34 — claim vocabulary beyond age (expiry buckets etc.); shape settled
  (list of predicate→boolean pairs), vocabulary/tiers/disclosure-cost all
  open; needs its own design pass and POC.
- Deferred, not refused: a distinct mid-read "hold still" progress state
  (needs `AsyncTask.publishProgress` restructuring).
- Production follow-ups from earlier: host-vs-registrable-domain (D42
  note), `av://` → verified App Links (D37).

## Process notes / lessons

- Forcing plain language onto a payload is an effective audit: "your age
  threshold" was vague enough to hide a hardcoded `true`; `age > 18: true`
  had to name a number and an answer, and there was neither.
- Both subagents corrected the orchestrator this session, and the
  orchestrator caught nothing in their work that they had not already
  self-reported. Verification ran in both directions.
- A test can be a correct test of the wrong property. Both failure
  buckets kept the MRZ, so a mapping test could never see that the wrong
  ADVICE was being given.
- The supersede-in-place PRD convention keeps history honest but can
  mislead about the present; a conflict sweep (done at v1.31) is what
  keeps both properties.
