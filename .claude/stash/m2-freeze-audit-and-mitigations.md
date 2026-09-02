# Session Stash — M2 freeze, ownership audit, exploit mitigations (2026-09-02)

Project: zkagent · Owner: hamr · Branch: m2-build · Tree CLEAN at stash time,
HEAD bc79918.

Session model: Fable 5.1. Main session never coded or edited docs; all
code/docs by Sonnet subagents (rule: every Agent call passes model:
sonnet). Peer critic session "lite" (another Claude session on this
machine, reachable via SendMessage by bare name `lite`) reviewed every
audit revision, the structure proposal, the adversarial analysis, and
commit 730ef09.

## Commits today (all on m2-build, in order, on top of 2cd1e00)

- `4969a20` fix(apps/scanner): D55 single-owner pane visibility, D56 MRZ-change
  diagnostic — device-confirmed
- `dffb4de` docs: PRD v1.36-v1.37, ownership audit, D55/D56 evidence, findings
  log
- `730ef09` fix(apps/scanner): name the site in the PIN prompt; refuse av://
  intents mid-session
- `26f67ac` fix(apps/scanner): no log entry on a refused av:// intent (finding
  #13)
- `f602e12` docs: D58 refactor order; findings #10-#13 status; Q41/Q42
  mitigated
- `3582e11` docs(findings): #14 stale handoffStatus after a consumed session
- `bc79918` docs(evidence): finding #10 mitigation device-confirmed

PRD v1.35 → v1.37. Decisions D55–D58. Questions Q38–Q42 opened. Scanner unit
tests 120 → 145 (0 failures; counts always parsed from JUnit XML by the
orchestrator, never taken from agent reports).

## The opening bug (D55/D56)

Owner report: NL card failed on wrong details, correcting them still failed,
"second attempt is stuck"; US succeeded; NL succeeded only on first try.
Root cause by inspection + last night's logcat: activity_main.xml has
loading_layout/main_layout/log_layout as overlapping FrameLayout siblings;
the D44 tab listener wrote main<->log, startSession/onPostExecute wrote
main<->loading; neither knew the third view. After a failure on the Log
tab, onPostExecute set main VISIBLE with log still VISIBLE on top;
onTabReselected was empty; the MRZ form was covered; every re-tap re-read
the same stale MRZ (four failures 23:52:35–23:53:04, 6/10/11 s apart — too
fast for a retype). D44's own comment "loading_layout is left alone: an
edge case not covered by items 15/16" was the agent noticing the seam and
declaring it out of scope. Fix: showPane() sole .visibility writer
(MainActivity.kt:913-915 at 4969a20), backed by pure PaneVisibility.choosePane;
onTabReselected idempotent. D56: MrzChangeTracker — per-process SecureRandom
salt, hash in memory only, logs only verdict/doc_len/dob_ok/exp_ok.
Device-confirmed 08:20:43 wrong digit → AccessDenied FAIL; 08:20:59 `MRZ
input CHANGED` → PACE → PASS (minted), pid 12166. Caveat: the
Log-tab-then-retap path itself produced no UNCHANGED line (owner went
straight to the correction). Held under freeze: showPane() runs in onCreate
but TabLayout restores selection in onPostCreate; screenOrientation="fullSensor"
(src/regular/AndroidManifest.xml:7) so it opens on every rotation.

## Owner's process correction (the main outcome)

Owner: "the way you instruct isolated agents/spawned without any context or
instructions on what came before is the main culprit as they kept
solving/adding without checking and every fix thereafter already created
regression"; "LOC should never be the gate as every repo/case is different.
what gets into the code now vs later, fixes vs enhancements"; AGENT_RULES
already mandated small modules — the orchestrator failed to enforce and
never reported file growth. Feature FREEZE declared (D57). Rule set adopted
(recorded in session memory `agent-briefing-carries-history` and lite fact
`fact:survey-existing-code-before-changing-it`):

1. entry gate FIX vs ENHANCEMENT, never size; recording a decision about a
   fix is never new scope (NO-GO #10 intact);
2. every spawn carries history IN the prompt body for the state it
   touches — prior findings, `git log -L` on relevant ranges, decisions by
   ID; resume prior agent via SendMessage when context survives;
3. bounded survey before writing: name every mutable field assigned + every
   other writer (grep);
4. one writer per piece of mutable state; every async writer fenced
   (orthogonal: single-owner = WHERE, fencing = WHEN);
4b. before briefing, grep the findings log for every function the brief
   NAMES AT ALL — call, change, or reference;
5. module boundary = testability boundary (isReturnDefaultValues=true stubs
   View/Spannable/AsyncTask/EditText; pure object holds logic, Activity is
   thin applier);
6. findings not fixed go in the report, forwarded via the durable file
   `.claude/remember/findings.md`, never a comment;
7. audit before refactor; refactor most-writers-first was REPLACED by D58's
   risk-of-change ordering; freeze has a stated exit criterion.

Durable findings file location decided by owner: `.claude/remember/findings.md`.

## Read-only ownership audit

`docs/logs/M2-OWNERSHIP-AUDIT-2026-09-02.md`, 4 revisions, 1,208 lines,
pinned 2cd1e00 + uncommitted D55/D56, repo untouched. 16 app fields + 7
framework-owned; ranked confirmed · consequence · likelihood; state join
with thread (MAIN/BACKGROUND/POSTED) and recreation (APP-PERSISTED 2 /
FRAMEWORK-RESTORED 1 / LOST 11 / process-scoped 2) columns; guards table (6
rows); call graph stamped "proves edges exist, not absent" (A14 name-grep
missed onTabReselected until an explicit-roots table was added);
re-runnable grep appendix (rev 1's appendix was circular — A1 cited the
table as its own evidence; three miscitations; fixed in rev 2). Top: #1
TabLayout selection vs showPane in onCreate (CONFIRMED); #2/#3
verifiedRequest/pendingHandoff — THREE writers (beginHandoffVerification
:605-606, mintAndMaybeHandoff :1637-1638 posted, showBlockingOutcomeDialog
OK handler :883-884), cross-thread reads inside Thread{} (continueAfterRead
:1281-1282 inside :1262; mintAndMaybeHandoff :1470-1471, sole call site
:1414) on non-@Volatile fields, no staleness guard on the mint path;
wipeSession KDoc documents the split. Zero async-cancellation discipline;
five unfenced Thread{} sites :306, :310 (release-reachable, land in
emitReport → reportLog.append :779 → persisted :533), :611, :1262, :1414.
reportView.text written at :288 outside emitReport (:769) while KDoc
:753-758 claims sole writer. Chip-auth three-state has no unit test (no
M0ProbeTest; DG14 half inline in ReadTask.doInBackground). MainActivity is
abstract (:164); RegularActivity.kt is 3 lines; the only <activity> is in
src/regular/AndroidManifest.xml (singleTop :8, exported="true" :9,
av://authorize filter :28). Line numbers are as of 2cd1e00/4969a20 and have
shifted since.

## Structure proposal (pass 2, fresh agent, lite's sketch withheld; scratchpad only, NOT in docs until D58 step 4)

6 clusters: 3 in MainActivity (Session/Handoff/Mode/Lock; Pane/read-lifecycle;
Report/Log) + 3 already-correct (ReportLog internals; DeviceKey.lastMintAlias;
DeviceKey.softwareEd25519Store — verified disjoint function sets). Owner's
"two or three" holds for the broken part, not literally; "a class for
errors" has no mutable state (FailureTransition is a stateless classifier
at ceiling). lite: the Session merge was derived from a graph the snapshot
fix will change — defer the boundary (D58 step 4); handoffStatus.text /
lockButton.isEnabled / modeStatusView.text should be DERIVED projections,
not fields; a class is right-sized if its legal states can be enumerated.

## Adversarial analysis (scratchpad scanner-induced-race-analysis.md) — verdict EXPLOITABLE, chain verified at source

MainActivity.kt:544-550 av:// branch of handleIncomingIntent called
beginHandoffVerification unguarded (NFC branch below checks lockedMode);
:605-606 unconditional overwrite; exported + singleTop → any app via
onNewIntent; RequestTrust.kt:124-132 resolves non-local keys from the
attacker's own /.well-known/zkagent-verifier (proves provenance, not
consent); :1281-1282 reads after the chip read; EvidenceSigner.kt:17-21
binds nonce||scopeDomain||zktag from whatever is there. Window 1 (during
read) = valid presentation for the attacker's site with the user's real PIN
+ chip; window 2 (during PIN) = honest answer diverted, UI shows accepted.
Biometric prompt showed only static strings (strings.xml:21-22) — consent
bound to nothing (finding #11).

## Mitigations (730ef09, 26f67ac; owner-approved strings)

HandoffAdmission.mayAdmitInboundHandoff(sessionLocked, readInProgress) =
!sessionLocked && !readInProgress gates the av:// branch (failing-first
test); refusal = Log.e + Snackbar "Ignored a site request that arrived
mid-scan." + return, assigning nothing, appending nothing.
MintPromptText.titleFor(site); title "Authorize presentation to %1$s"
(biometric_prompt_title_for_site); promptAndMint is unreachable without a
verified handoff (MintGate requires mode B; mode B only from a verified
tier; D38 refuses on null origin). Both are MITIGATIONS: findings #10/#11
stay OPEN until the lock-time snapshot (D58 step 3) lands, then the guard
is REMOVED. Near-miss #12: first draft reused showBlockingOutcomeDialog,
whose OK handler nulls pendingHandoff/verifiedRequest and wipes the
session — a refused foreign intent would have destroyed the legitimate
session on OK; caught by the coder's survey (half) + orchestrator source
check (half); rule: no refusal path may use that dialog. #13 (found by
lite): ReportLog.entries (ReportLog.kt:162) unbounded and persisted whole;
the refusal's emitReport was the first externally-triggerable append →
owner chose drop-the-entry (26f67ac); the cap stays open. Device test
bc79918: second tx fPFnvTpH4fmrZUgC delivered via `adb shell am start -a
android.intent.action.VIEW -d <av link>` after lock → 09:16:22 `av://
handoff REFUSED`; owner's mint went to _a10IjU09TN4xyAj; hostile tx got no
verdict; caveats: lock→tap window only; same-origin attacker (weaker case).

## Finding #14 (3582e11)

handoffStatus.text written only at :643/:716/:744; never rewritten after a
mint consumes the session → owner scanned the US passport twice on a
consumed link believing it live; both ran as honest mode-A scans titled
with the no-site label. Projection defect for D58 step 4, not a standalone
fix. Q40 (Lock button reads stuck) is the same class.

## D58 refactor order (owner-confirmed; lite corrected its own contradictory queue to this)

1. Report/Log — smallest/most closed; named restoreReport/restoreLog
   sibling of emitReport closes #7; bound on ReportLog.entries (number needs
   owner approval);
2. Pane — persist tab index, TabLayout DRIVEN FROM pane state; confined to
   onSaveInstanceState/onCreate, must NOT touch onNewIntent (Q39);
3. lock-time snapshot of the verified request threaded as a parameter —
   closes #2/#3 by construction, deletes the cross-thread reads, then
   REMOVE HandoffAdmission;
4. re-derive the Session boundary on the corrected graph.

CONDITION: the snapshot MUST land; if the sequence stalls after step 2 the
ownership bug is live and the record must say so.

## Owner UX observations, recorded not built (Q38–Q40, ENHANCEMENTS under freeze)

Q38 log does not survive app close (D45 process lifetime; NO-GO #9
tension). Q39 Chrome reopens app on last tab (Log) — singleTop +
onNewIntent never resets the tab; owner rejected auto-switch on READ
completion; incoming-handoff switch is a separate decision. Q40 disabled
Lock after an access failure reads as stuck; owner wants "Tap and scan";
lands on lockButton.isEnabled (the field welding Session and Handoff
clusters) — MUST NOT be touched before step 4.

## Staging / recipes

Verifier: spikes/m2-handoff, `LINK_SCHEME=av node server.mjs`, HTTP 200 on
127.0.0.1:8787 (two server.mjs processes were alive; only one holds the
port). Create a transaction: `curl -X POST -H 'content-type: application/json'
-d '{"mode":"B"}' http://127.0.0.1:8787/ui/presentations` → JSON with
app_link_av, transactionId. Verifier log: the previous session's scratchpad
handoff.log. Device: `adb install -r
apps/scanner/app/build/outputs/apk/regular/debug/app-regular-debug.apk`;
`adb reverse tcp:8787 tcp:8787` (dies on adb restart — re-do); `adb shell
svc power stayon usb`; `adb shell am force-stop com.zkagent.scanner`.
Build: `JAVA_HOME=/home/hamr/opt/jdk-21.0.12.1+1 ./gradlew
:app:assembleRegularDebug :app:testRegularDebugUnitTest` in apps/scanner.
LOGCAT LESSON: `-s MainActivity:I MainActivity:E` silences I-level — last
spec per tag wins; use `MainActivity:V DeviceKey:I RequestTrust:I
HandoffClient:I M2Masterlist:I`. `pkill -f <pattern>` matched the invoking
shell and killed it — kill by `pgrep -f "^adb logcat"` pids instead. Device
dropped off adb once; `adb kill-server; adb start-server` recovered it.
Owner run protocol for the attacker test: open fresh link → Lock → owner
types "go" → orchestrator fires am start → owner taps card + PIN.

## Session scratchpad (session-specific /tmp, will not survive)

scanner-ownership-audit.md (copied to docs/logs), scanner-structure-proposal.md
(NOT in docs), scanner-induced-race-analysis.md (NOT in docs),
refactor-step1-brief.md (draft, with lite for review), baseline-happy-paths.log,
repro-d55-logcat.log, attacker-test-logcat.log, attacker-link.txt,
attacker-tx.json.

## Open / next

- lite's review of the step-1 (Report/Log) brief is PENDING; step 1 does
  not start until it lands and the owner approves the proposed log bound.
  Four things the orchestrator asked lite to challenge: owner-class choice
  left to the coder; "late landing harmless" may be step-2 work; whether
  the bound is a fix or an enhancement; what the brief names that the
  findings log describes.
- Findings OPEN: #1 (rotation, step 2), #2/#3 (race, step 3), #4 (rotation
  class), #5 (five unfenced async), #6 (tag guard ignores readInProgress),
  #7 (:288, step 1), #8 (chip-auth untested), #9 (lockButton.isEnabled),
  #10/#11 (mitigated), #13 (cap, step 1), #14 (projection, step 4). #12
  closed by construction.
- Structure proposal and adversarial analysis live only in the session
  scratchpad — copy to docs/logs if they must survive (owner decision; D58
  says the proposal is not copied until step 4).

## Lessons (this session)

- A test can be structurally blind: visibility invariants are not
  expressible under isReturnDefaultValues; the remedy is structural (pure
  object), not more assertions.
- Single-owner says WHERE; fencing says WHEN. showPane is a perfect owner
  and the rotation bug is still live.
- A mitigation that reuses a correct single-owner mechanism inherits that
  mechanism's CONTRACT (dialog = terminal-outcome-with-state-transition;
  emitReport = every scan outcome is logged). Two near-misses today from
  treating an owner as a free utility.
- The orchestrator was the write-only channel: agent reports stopped at the
  orchestrator; the next spawn started from zero. Durable findings file +
  history-in-prompt is the fix.
- Three layers of proof for one guard: predicate truth table (unit),
  wiring (source trace), exported surface (a real second process via am
  start). Only the third catches a guard that never fires.
- Sort keys that can't be falsified inform nothing; change ordering follows
  risk-of-change, not bug rank.
- Verify a peer's cited precedent before repeating it (lite's "mechanical
  gate" quote was real — in friction_raw.jsonl).
</content>
