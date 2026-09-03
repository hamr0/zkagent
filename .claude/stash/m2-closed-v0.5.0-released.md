# Session Stash — M2 closed, v0.5.0 released (2026-09-03, afternoon/evening)

Project: zkagent · Owner: hamr · Orchestrator: Fable 5.1. Every code/doc
change this session was made by a Sonnet subagent; every test count was
read from JUnit XML by the orchestrator.

Session start: after `/clear`, continued from stash
`m2-exit-cleanup-round.md`, branch `chore/m2-exit-cleanup` at `fb0e75f`,
36 commits over `main` at `4a28b15`. Ended on `main` at `35fc414`, branch
deleted remotely and locally, tree clean, tag `v0.5.0`, `chiproof@0.5.0`
live on npm.

## Owner asks, in order

- "cont from here, whats next?"
- Pasted an `av://` link into the app and could not trigger it — there is
  no paste path, `av://` only arrives as a VIEW intent; orchestrator
  fired links via `adb shell am start -a android.intent.action.VIEW -d
  <link>`, transactions created with `POST /ui/presentations
  {"mode":"B"}` on spike 8788.
- Asked for clearer device instructions (first set was muddled; fixed:
  two tabs "Scan"/"Log", one big button whose label flips Scan/Verify).
- Reported the five glance checks.
- "did we finish m2 properly this round?"
- "again no release before we close m2 for good. start where it needs to
  be done and ask away"
- Four AskUserQuestion answers.
- "pass, read mode a on scan screen, and log"
- "turn 127 on, need to do few passes" (mid-turn, read as: proceed with
  review, expect fix passes)
- `/release`
- "why are we versioning the app separately from lib? how am I going to
  keep track of both" → "lockstep 0.5.0"
- "all" (push/merge/tag/publish)
- Ran `! gh workflow run publish.yml --ref main` themselves.
- "delete branch and /stash"

## Device glance results

Pixel 6a, build 14:04:35 = `fb0e75f` content, then 17:27:35 = `a61bcc8`
content:

- Item 20 PASS (Verify with link, Scan fresh).
- Item 24 PASS (version/sha footer visible; overturned this morning's
  FAIL).
- Item 17 PASS (owner on Log tab, link fired 15:10:22, app jumped to
  Scan).
- Item 23 PASS (Clear log → swipe away → reopen → empty).
- Item 19 observed as built: all entries uniformly grey because every
  terminal entry is dimmed at 60% alpha (`DIM_ALPHA_FRACTION`) and only
  an "In progress" entry is full colour — contrast invisible when idle.
- Item 18 device-confirmed (owner tapped a collapsed entry, it expanded).
- D71 glance: dialog read "This scan was Mode A, anonymous.", mode A on
  scan screen and log, log entries plain colour.

## Findings/decisions

Decisions (owner, ~15:30, D71 a–d):

- (a) item 19 dimming DROPPED, Q44 closed "not wanted".
- (b) new §6.2 item 25 — outcome dialog carries one mode sentence ("Mode
  A, anonymous" / "Mode B, recognisable to this site"), ENHANCEMENT,
  PRD-first.
- (c) item 22 glyph ✗ for honest under-threshold KEPT.
- (d) locked-button wording "Tap and verify"/"Tap and scan" KEPT.

D72 (~18:15): lockstep versioning — one repo version in three places
(chiproof package.json, scanner versionName, one CHANGELOG section),
chiproof republished every release even if unchanged, tags plain
`vX.Y.Z` (chiproof-vX.Y.Z tags are history). Rationale: app's 0.2.0
number came from item 24's stamp with no decision behind it.

## Commits

On the branch this session, in order:

- `996ae46` docs (items 17/20/23/24 device-confirmed, item 24
  FIXED-IN-fb0e75f, item 19 observed)
- `76ba727` docs D71 (item 19 withdrawn, item 25 added, items
  22/verb confirmed, item 18 confirmed, PRD v1.55)
- `5cf39bf` fix D71a dimming removal (ReportLog `terminalFlags`/
  `terminalSnapshot`/`dimmedTextColor`, MainActivity
  `dimmedLogEntryColor`/`DIM_ALPHA_FRACTION`/`STATE_LOG_TERMINAL`,
  ReportLogStore `terminal` field; 8 dimming tests removed)
- `a61bcc8` feat D71b item 25 (`OutcomeText.withModeSentence`,
  `SessionDisplay.modeLabel`, wired into the single
  `showBlockingOutcomeDialog`, paired Log.i, 5 `OutcomeTextTest` cases)
- `cb0cb52` docs D71 device-confirmed (PRD v1.56, evidence page
  section 14)
- `e973312` stash commit (`m2-exit-cleanup-round.md`, needed for a
  clean tree before review)
- `35fc414` `release: v0.5.0 — M2 scanner close-out, first lockstep
  release (D72)` (CHANGELOG [0.5.0] — 2026-09-03, D72, M2 row closed,
  history v1.57 + stale header fix v1.0–v1.57, chiproof
  package.json/lock 0.5.0, scanner versionName 0.5.0 versionCode 2)

Coder ran in worktree `.claude/worktrees/agent-afd73f64eb354fa58` reset
to `996ae46` (worktree commits `be08559`/`2fc2373` cherry-picked as
`5cf39bf`/`a61bcc8`); coder fixed the worktree's `local.properties` to
`sdk.dir=/home/hamr/Android/Sdk` (gitignored).

Tests: scanner 654 (327 × 2 variants, was 660: −8 dimming +5
OutcomeText), chiproof 191 + typecheck, spike 23, all 0 failures. JDK
`/home/hamr/.gradle/jdks/eclipse_adoptium-17-amd64-linux.2`.

## Reviews/release

`/branch-review main high` at `e973312`, re-review of range
`2525267..HEAD` (20 commits): READY, 0 blockers, all three stages ran;
mutation-tested `LockPrecondition`/`MintGate`/`OutcomeText`/
`M0Probe.checkTrustPath`/`ReportLog.glyphFor` (all go red); release APK:
no Google/gms components, no CAMERA permission, cleartext off, GIT_SHA
matches HEAD; fix ledger 0 → 1 (Low: version stamp "-dirty" suffix runs
`git status --porcelain` from `app/` with no pathspec, so any untracked
file anywhere in the monorepo marks the scanner dirty; reproduced).
Reviewer noted `HandoffAdmission.kt` unchanged in range so a second
`av://` while the first is pending-unverified is still admitted
(pre-existing, not re-judged). `last-review.md` sha `e973312`.

`/release` preflight passed (review sha = HEAD, ready, chiproof 0.4.0
local = published, scanner 0.1.0 on main vs 0.2.0 on branch). Worker ran
ship.md checklist green (no lint configured, no migrations).
Exit-criteria rows not phrased device-confirmed but judged non-blocking:
items 13/14 "Implemented" (exercised on every handoff), 15/16 one
unreproduced sub-case each, 22 (owner tapped by glyph today; row not
flipped). Flagged: `spikes/m2-handoff/package-lock.json` still resolves
local chiproof at 0.4.0 (regenerable, not a release field).

Sequence: `git push -u origin chore/m2-exit-cleanup` OK; `git push
origin chore/m2-exit-cleanup:main` OK (bypass "Changes must be made
through a pull request" fired, `4a28b15..35fc414`); `git tag v0.5.0
35fc414 && git push origin v0.5.0` OK; classifier blocked the
orchestrator's `gh workflow run` (and `gh workflow view`), owner
dispatched run `33793707159` via `! `; `gh run watch --exit-status` exit
0 with annotation "npm publish exited 0 but chiproof@0.5.0 not yet
visible after ~2 min (registry reflection lag)"; `npm view chiproof
version` still said 0.4.0 (client cache) while `curl
https://registry.npmjs.org/chiproof` showed latest 0.5.0; tarball 0.5.0
= 28 files / 182615 bytes, identical to 0.4.0. `git push origin
--delete chore/m2-exit-cleanup`, local branch deleted, checkout on main
`35fc414`.

Memory: updated
`~/.claude/projects/-home-hamr-PycharmProjects-zkagent/memory/release-merge-is-direct-ff-push.md`
(plain `vX.Y.Z` tag per D72; owner dispatches publish.yml via `! `;
verify via registry curl not npm cache) and its MEMORY.md line.

## Still open / owner-owned

- `/remember` (project MEMORY.md "M2 FROZEN" fact stale; this and the
  prior stash unprocessed).
- Fix ledger 1 Low item → next `/refactor`.
- Docs reorg still owed.
- Item 22 exit-criteria row could be flipped to device-confirmed (owner
  tapped a glyph entry 2026-09-03 PM).
- Spike lockfile chiproof 0.4.0.
- `HandoffAdmission` second-pending-`av://` case (pre-existing,
  unreviewed in range).
- Spikes on 8788 (threshold 18) and 8789 (THRESHOLD=99) still running,
  8787 down.
- Next module after M2 not started (M3 demo is next milestone per
  milestones table; rung-2 M4/M5 stay frozen).

## Orchestrator lessons

- Device instructions must name the exact UI elements (tab names, the
  one button) — the first set caused "you are not making sense".
- An `av://` link cannot be pasted into the app, only delivered as a
  VIEW intent (adb `am start` is the same code path as a browser tap).
- A dimming rule tied to "in progress vs terminal" is invisible when
  nothing is in progress — a visual cue needs a visible contrast in the
  idle state or it reads as broken.
- The classifier blocks `gh workflow run`/`gh workflow view` and
  multi-step push chains from the orchestrator — run pushes as single
  bare commands and let the owner dispatch workflows via `! `.
- `npm view` served a stale version for minutes after a successful
  publish — verify against the registry JSON.
- The stash note must be committed before `/branch-review` because the
  stash dir is tracked.
- `/release` asks rather than decides when the versioned unit is
  ambiguous, and that question produced D72.

## Environment

Pixel 6a serial `34011JEGR02358`, scanner installed 17:27:35 (`a61bcc8`
content, versionName 0.2.0 — the 0.5.0/versionCode 2 build has NOT been
installed on the device); adb reverse 8787/8788/8789/18787; spikes
8788/8789 up, 8787 down; session scratchpad
`/tmp/claude-1000/-home-hamr-PycharmProjects-zkagent/433b8f4e-ab55-4a0d-adb9-8ead200256ea/scratchpad`
(tx.json, tx2.json, av.txt, av2.txt, push1.out, run.out); prior session
scratchpads `ebcbb332-…` (device logs) and `57f9ea18-…` (empty capture).
