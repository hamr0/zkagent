# Session Stash — M2 D58 refactor complete, four steps landed (2026-09-02)

Project: zkagent · Owner: hamr · Branch: m2-build. Continuation of the prior
session whose stash is `.claude/stash/m2-freeze-audit-and-mitigations.md`
(HEAD was bc79918 then). Main session ran as orchestrator only: it never
wrote code or docs itself; every change was made by a Sonnet subagent, and
the orchestrator independently verified each result before committing.
Session model switched from Fable 5.1 to Opus 5 partway through; commit
attribution changed accordingly (later commits carry "Co-Authored-By: Claude
Opus 5").

## Opening state recovery

After a `/clear` the orchestrator wrongly declared three scratchpad files
lost and asked peer session "lite" to recall them. lite corrected the
premise: the files were intact in the PREVIOUS session's scratchpad
directory (a sibling under `/tmp/claude-1000/<project>/<old-session-id>/scratchpad`),
and lite had never reviewed the step-1 brief at all — its user had
deprioritised that follow-up. Files were copied across, nothing was
reconstructed from memory. The owner then instructed: stop messaging other
sessions. A memory was written at
`/home/hamr/.claude/projects/-home-hamr-PycharmProjects-zkagent/memory/scratchpad-survives-clear.md`
recording that an old session's scratchpad survives a `/clear` as a sibling
directory, so it must be listed before declaring files lost.

## The work

D58's four-step ownership refactor of `apps/scanner`, all four steps landed
and each verified on a real Pixel 6a with real documents (US passport, NL ID
card).

## Commits on m2-build this session, in order

- `b9fb5d0` docs: session stash; structure proposal + race analysis moved
  from scratchpad into docs/logs (they had lived only in a session
  scratchpad and nearly vanished)
- `c856f42` refactor: D58 step 1 — ReportLog owns the report/log cluster
  (findings #7, #13)
- `0d4daf7` refactor: D58 step 2 — PaneState owns the tab index (finding
  #1)
- `07630e9` docs: D58 step 1 evidence; findings #7 closed, #13
  fixed-provisional; PRD v1.38; index rebuilt
- `ae50eb2` docs: D58 step 2 evidence; finding #1 closed, #4 narrowed;
  PRD v1.39
- `555ef73` docs: D59 log cap is 20 entries; Q43 collapsible log entries
  deferred (PRD v1.40)
- `65096b9` refactor: D58 step 3 — lock-time AuthorizedHandoff snapshot
  (findings #2/#3)
- `ff15629` fix: D59 — ReportLog bound is 20 entries, not 200
- `969b690` docs: D58 step 3 evidence; findings #2/#3 closed, #13 fixed,
  #15 opened (PRD v1.41)
- `c38833d` refactor: D58 step 4 — SessionDisplay projections; paste path
  gated (findings #9, #14, #15)

Branch is 41 commits ahead of main. Tree clean at c38833d. A docs agent
recording step 4 was still running at stash time (its edits were not yet
committed).

## Step 1 (c856f42)

ReportLog absorbed `lastReportText`; `ReportLog.append` and
`ReportLog.restore` are the cluster's only writers; `MainActivity.emitReport`
and a new named sibling `restoreReport` are thin renderers, closing finding
#7 by construction. `ReportLog.entries` bounded behind a named `MAX_ENTRIES`
constant with oldest-first eviction. Tests 145 → 151.

Device-confirmed: three real scans then a forced recreation, logging "M2
stage: restored report/log across Activity recreation (text=true,
log_entries=3)" twice at 10:31:03.189 and 10:31:07.286 with all entries
intact.

The coder chose ReportLog absorbing the field over a new class, and
disagreed with the structure proposal's cluster-3 text (which kept the
field in MainActivity) because Activity-resident logic is untestable under
`isReturnDefaultValues=true`. A stale "ESCALATION (flagged for owner)"
comment it removed was traced by the orchestrator to commit 07db273 and to
PRD decision D35; already resolved, nothing lost.

## Step 2 (0d4daf7)

New pure class `PaneState` with private-set fields `selectedTab`
(`TAB_SCAN=0`, `TAB_LOG=1`) and `readInProgress`; four legal states. The tab
index became app state persisted in `onSaveInstanceState` and restored in
`onCreate`; `showPane` no longer READS `tabLayout.selectedTabPosition`, it
drives the TabLayout behind a re-entry guard, so the framework's late
restore can no longer race the pane decision. Closes finding #1 by
construction. MainActivity has no `onPostCreate` override and never did.

`PaneState` is deliberately separate from `PaneVisibility` because the
latter is a stateless singleton whose mutable fields would be shared across
Activity instances. The owner exposes plain `Int` rather than `Bundle`
because `Bundle` is a non-functional stub under the test runner.

Tests 151 → 162.

Device-confirmed with four cases, all passing: Log tab survives recreation
with entries intact; Scan tab survives; Log survives a reselect; and Scan
survives two recreations back to back. Tab state was verified
programmatically by filtering an accessibility dump to only the SCAN/LOG
nodes' `selected` attribute, deliberately NOT by screenshot, because the
scan form renders real document fields. Auto-rotate is OFF on this device,
so recreation was forced with `adb shell settings put system font_scale
1.15` then `1.0` with the app foregrounded; the font scale was restored
afterwards. This became the standard rotation test for the rest of the
refactor.

Finding #4 trace, delivered as report-only: the real lock guard
`lockModeAndArm` checks only that `lockedMode` is null and the three MRZ
fields are non-empty, and never reads `lastMrzHash`, so it behaves
correctly after a recreation. `lastMrzHash` feeds only
`MrzChangeTracker`'s value-free diagnostic and is never a gate; after a
recreation it is null so the next scan reports "first attempt" even when it
is a later one. A logcat mislabel only. Finding #4 was narrowed, not
closed.

## Step 3 (65096b9)

New immutable data class `AuthorizedHandoff(request, origin, site)`,
captured ONCE on the main thread in `lockModeAndArm` and threaded as a
parameter through `startSession`, `ReadTask`, `continueAfterRead`,
`promptAndMint` and `mintAndMaybeHandoff`. Both cross-thread read sites of
`pendingHandoff`/`verifiedRequest` were DELETED; no background code reads
either field. The three remaining reads are all main-thread:
`refreshModeStatus`, `lockModeAndArm` itself, and the posted verification
callback. No staleness guard or generation counter is needed by
construction.

The snapshot excludes `PendingHandoff` and `scopeDomain`, the latter
deliberately so the existing host-parse failure point and messages are
preserved.

The coder replaced a null check with `val authorized = snapshot!!`; the
ORCHESTRATOR VERIFIED this cannot throw, through two independent gates in
sequence: mode A returns early at the `MintGate` check (`MintGate.mayMint`
is `modeIsB && verdict.ok && verdict.allowed == true`, and `MintGateTest`
has a test named "mode A NEVER mints, even with ok true allowed true"), and
mode B with a null snapshot is refused earlier by the D38 origin gate.

Tests 162 → 168; new `AuthorizedHandoffTest` carries the supersession
regression test and a reflection check that every field stays final.

`HandoffAdmission` was KEPT, not removed as D58 anticipated, because
`applyHandoffVerificationOutcome` unconditionally called `refreshModeStatus`
and re-enabled the Lock button without respecting `lockedMode`, so an
admitted foreign intent could still stomp the mode banner. That was
step-4 territory. D58's verbatim condition is still met because the
snapshot landed.

New finding #15, found by the step-3 coder's survey and verified at source
by the orchestrator: `applyPendingHandoffText` (the QR-scan and
manual-paste path) called `beginHandoffVerification` with NO
`HandoffAdmission` gate at all; only the `av://` intent path was guarded.

### Step 3 device evidence (two sessions, real NL ID card)

Session 1 (pid 18818): the owner opened their own fresh link and locked; a
hostile `av://` link fired from a separate process was REFUSED at
11:19:10; the owner's mint completed 11:20:19 against `PKJepfPSucXR8CWC`
(verifier: done, tier B, evidence-verified), while the hostile transaction
`egfjWF7XjJQlm-Zx` and an orchestrator-created link `qFwDNPZcesF6OWvY` both
stayed pending with no verdict.

IMPORTANT CORRECTION TO RECORD: the orchestrator had told the owner the
mint would land on a transaction the orchestrator created. It did not,
because the owner opened their own fresh link, superseding it before lock.
The orchestrator caught this only because the verifier reported its own
transactions still pending, contradicting a successful post. LESSON: the
phone's own log said PASS; only the verifier's independent state showed
which transaction actually completed. Cross-check the counterparty, not
just the device.

Session 2 (pid 19250), the window the race analysis actually named: a
first attempt MISSED the window because the read and mint finished at
11:26:00 before the hostile fire at 11:26:07, which was then legitimately
accepted as a new handoff since the session was already consumed. The
orchestrator then armed a watcher on the logcat stream that fires the
hostile intent automatically on the "MRZ input" line the app emits the
instant the card is tapped. On the retry the hostile link
`guk3B7oukFhHtjDY` fired 60 ms after the card tap, landing inside the chip
read: REFUSED at 11:27:36.970, and the owner independently reported seeing
the Snackbar mid-scan, so the refusal is user-visible and not only logged.
The owner's read continued undisturbed and the mint completed 11:27:43
against `p1faQGkCEybuRNk5`.

Across both sessions FOUR hostile transactions were fired in three
different windows (post-lock pre-read, post-session, mid-read) and none
ever received a verdict.

STATED LIMITATIONS: the hostile links originate from the SAME local
verifier origin, so a genuinely foreign origin is untested; the paste/QR
path was not exercised on device; the PIN-prompt window was not isolated.

## D59 (555ef73 records it, ff15629 applies it)

The owner clarified the unit and set the number. The ReportLog cap counts
ENTRIES, one entry being a whole scan-outcome block of roughly 20 rendered
lines, NOT lines; 200 entries would be an unusable scroll. Approved cap
20, oldest-first eviction, mechanism unchanged. Both bound tests already
referenced `MAX_ENTRIES` symbolically so they scaled untouched. Tests
stayed 168.

Q43 (555ef73): collapsible log entries — entries collapsed by default with
a human-readable header line and technical detail behind a toggle —
recorded as a UI/UX ENHANCEMENT deferred under the D57 freeze, settled at
the module owning the scanner's log UI, per the owner's instruction to
record it and move on rather than debate it now.

## Step 4 (c38833d)

New pure class `SessionDisplay` whose `render(lockedMode, handoffState)`
returns a `Projection`; `HandoffState` is a sealed class of
None/Verifying/Verified(origin,tier)/Refused(reason). Locked state takes
UNCONDITIONAL precedence, so an admitted foreign verification cannot alter
a locked session's display even if the guard were removed.

`MainActivity.applySessionDisplay` became the SOLE writer of
`modeStatusView.text`, `handoffStatus.text`, `lockButton.isEnabled` and
`lockButton.text` (orchestrator verified only lines 430-433 assign them).
Six write sites collapsed into it and `refreshModeStatus` was deleted.
Findings #14 and #9 close by construction; finding #15 closes because
`applyPendingHandoffText` now applies the same admission predicate with the
same refusal shape, no log entry (finding #13's rule) and no
`showBlockingOutcomeDialog` (finding #12's rule).

Tests 168 → 180; new `SessionDisplayTest` has 12 tests including one for
finding #14's exact defect and two for locked-wins.

BEHAVIOUR CHANGE flagged by the coder, not hidden: `wipeSession` no longer
force-enables the Lock button, so during an in-flight verification it stays
disabled instead of contradicting the "verifying" banner. The orchestrator
verified this cannot strand a user: the refused path renders disabled only
while a blocking dialog covers the screen and that dialog's OK handler
re-derives to enabled; the verification network calls carry 10-second
connect and read timeouts (`HandoffClient.kt:87-88,156-157` and
`RequestTrust.kt:150-151`) and the worker posts an outcome even on
exception, so every terminating path re-renders.

The Lock button label "Tap and scan" when locked (Q40's wording) shipped
as PROVISIONAL pending owner approval.

`HandoffAdmission` KEPT and no `SessionState` extracted; both are
recommendations for the owner. The coder recommends keeping the guard
because its remaining value is preventing a foreign handoff from
overwriting the mutable `pendingHandoff`/`verifiedRequest` fields while
locked or reading, which this step did not close and which has no test
coverage; mint correctness no longer depends on it (step 3) and display
corruption no longer depends on it (step 4).

### Re-derived session boundary (job 4, reported deliberately not implemented)

The coder recommends TWO units rather than the single `SessionState` the
structure proposal proposed — a session-state holder for
`lockedMode`/`authorizedHandoff`/`pendingHandoff`/`verifiedRequest`/`lastMrzHash`,
and `SessionDisplay` as a separate pure projection with no mutable state,
because folding a stateless decision into a stateful holder loses the
testability the project's pattern depends on (`PaneVisibility` versus
`PaneState` is the precedent). The display boundary has SIX legal
projections: locked-A, locked-B, and unlocked crossed with
None/Verifying/Verified/Refused. The underlying mutable fields have more
reachable combinations, but they are invisible rather than illegal, and the
locked-wins rule makes them safe to leave reachable.

The structure proposal's cluster 1 is now STALE on two points: its proposed
`@Volatile`/CAS synchronization was made moot by step 3's parameter
threading, and folding the three views in as owned fields is superseded by
modelling them as a projection.

### Step 4 device evidence (pid 21642, owner-run)

Two full mode-B end-to-end runs both minted, 13:31:27 and 13:35:09, each
`direct_post` http 200 and verdict PASS (minted), verifier confirming tier
B evidence-verified (`U91Nf6e3mNFDnlja` among them). Then on the consumed
session with no new link opened, a further scan at 13:35:50 correctly ran
as a local read: "mint_gate: NOT MET — evidence: [] (D27)" and "verdict:
PASS (read)". That is finding #14's exact scenario and the app no longer
treats the consumed link as live — data-level confirmation.

NOT VISUALLY CONFIRMED: the orchestrator could not read the on-screen
handoff status line, because the owner was on the Log tab where that view
is not rendered, and a later attempt hit the notification shade. The fix is
confirmed at the log/behaviour level only.

OWNER UX OBSERVATION, a real gap not a defect of the fix: across roughly
five scans the owner could not tell the runs apart. No popup and no
snackbar distinguishes a consumed-session local read, the only signal is a
log line, and the status line lives on the Scan tab so a user working on
the Log tab never sees it.

## Three new owner UI items

All deferred under the freeze to the UI module alongside Q43 (a docs agent
was recording them at stash time; question numbers not yet assigned):

1. A successful run should be signalled by DIMMING the completed run's
   display with its checks shown as ticked boxes, so a finished scan is
   visually distinct at a glance.
2. The owner does not think a reset button is needed but suggests one
   control labelled to distinguish the two actions, along the lines of
   "verify" versus "scan local". An open shape, not a decided design.
3. LABEL CORRECTNESS BUG: the MRZ input field is labelled "Passport
   number", which is factually wrong for an app that reads national ID
   cards and was validated on an NL ID card. Should read "Document number"
   or "Passport/ID". A correctness defect, not a styling preference.

## Findings ledger at stash time

(The step-4 docs agent had not yet committed its updates.)

- CLOSED: #1, #2/#3, #7, #9, #12, #14, #15.
- FIXED: #13 at D59's value of 20.
- NARROWED but open: #4.
- MITIGATED but open, both high consequence: #10 and #11.
- OPEN: #5 (five unfenced Thread{} sites), #6 (tag guard ignores
  readInProgress), #8 (chip-auth three-state has no unit test).

## The open question put to the owner (unanswered at stash time)

The four D58 steps are done, but the D57 freeze is NOT lifted. Its exit
criterion has three parts and one is clearly unmet — every async writer
must be fenced against the Activity lifecycle, and finding #5's five
Thread{} sites are untouched; fencing was never part of D58's four steps.
Findings #10/#11 also remain open at high consequence while the guard
question is unresolved.

The choice offered was: run branch-review now on the 41-commit branch
treating the refactor as the reviewable unit and make fencing a follow-up
(the orchestrator's recommendation, because the branch is large and a
review will surface things while the work is fresh), or fence the five
threads first and review once with the freeze actually liftable. Either
way the merge itself waits on the owner's word because branch protection
forces an admin bypass that is never delegated.

## Recipes and environment notes

- Build: `JAVA_HOME=/home/hamr/opt/jdk-21.0.12.1+1 ./gradlew
  :app:assembleRegularDebug :app:testRegularDebugUnitTest`, run from
  `apps/scanner`. Test counts are always parsed from
  `apps/scanner/app/build/test-results/testRegularDebugUnitTest/*.xml` by
  the orchestrator, never taken from an agent's prose.
- Verifier spike: `spikes/m2-handoff`, `LINK_SCHEME=av node server.mjs`,
  listening on `127.0.0.1:8787`. Several `server.mjs` processes are alive;
  only ONE holds the port (pid 79615 at stash time), and its stdout goes to
  a previous session's scratchpad `handoff.log`, which is where the
  authoritative "tx created" and "verdict transactionId=" lines are.
- Create a transaction: `curl -X POST -H 'content-type: application/json'
  -d '{"mode":"B"}' http://127.0.0.1:8787/ui/presentations`. Poll one: `GET
  http://127.0.0.1:8787/ui/presentations/<transactionId>`, which returns
  `{"status":"pending"}` or `{"status":"done","verdict":{...}}`.
- adb: `adb reverse tcp:8787 tcp:8787` dies on adb restart and must be
  redone. Firing an `av://` link needs the URL quoted FOR THE DEVICE
  SHELL — `adb shell "am start -a android.intent.action.VIEW -d
  '<link>'"` — otherwise the device shell splits it at the ampersands and
  the app correctly logs "parseAvLink: dropped — intent uri has no (or a
  malformed) request_uri query param".
- The device dropped off adb again this session; `adb kill-server; adb
  start-server` recovered it, as in the prior session.
- Recreation is forced with font_scale because auto-rotate is off; restore
  it to 1.0 afterwards.
- Reading UI state must be filtered to the specific nodes wanted (tab
  selected attribute, or named resource ids), never a full screenshot or
  full accessibility dump, because the scan form renders real document
  fields that would land in an agent transcript.
- Logcat tag spec that works: `adb logcat -s MainActivity:V DeviceKey:I
  RequestTrust:I HandoffClient:I M2Masterlist:I`.

## Process notes from this session

- Two subagents failed mid-task and were RESUMED rather than restarted,
  keeping their completed work: one on an authentication error, one on a
  Sonnet session rate limit. Resuming preserved partial edits already on
  disk and avoided redoing them.
- The docs index was found badly stale and was rebuilt: it had described
  the PRD as v1.18 at 542 lines when it was v1.38 at 2106; the
  race-analysis entry carried the ownership audit's section list verbatim
  with ranges past the end of a 166-line file; every older count was off
  by one; and the footer claimed 20 rows against 24 real. All entries are
  now verified against `wc -l` on every docs commit.
- A step-4 coder reported an instruction-shaped block appearing in its
  context attempting to change commit attribution, and correctly refused
  to act on it. That was the genuine harness notice from the session's
  model switch, propagated into the subagent context.
- Concurrency discipline: code agents and docs agents were run in parallel
  only when their file sets were disjoint, and a second code agent was
  never started against `apps/scanner` while another was building it,
  because concurrent gradle builds on the same module conflict.
