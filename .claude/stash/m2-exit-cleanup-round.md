# Session Stash — M2 exit cleanup round (2026-09-03)

Project: zkagent · Owner: hamr · Branch: `chore/m2-exit-cleanup`, created
from `main` at `4a28b15`; tip now `fb0e75f`, 36 commits, tree clean.
Orchestrator: Fable 5.1. Every code/doc change this session was made by a
Sonnet subagent; every test count was read from JUnit XML/TAP, never from
agent prose.

Session start: after `/clear`, continued from stash
`m2-unfreeze-device-session-and-close-out.md`, main at `4a28b15`. Owner:
"this round is to fix all the above and clear it up before next module".

## Owner asks, in order

- Fix everything outstanding from the prior stash.
- Q36 = mint honest `over_threshold:false` and hand off, in-app,
  D28-coarsened date (D66).
- All five UX questions (Q39/Q40/Q43/Q44/Q45/Q48) folded into this round
  (D67, §6.2 items 17–21).
- QR: owner asked "do av have qr code, what is it good for" → keep QR,
  live scanner (D68b) → owner then asked "any possibility of anything
  going to google" → answer yes (Play-services process, module download,
  datatransport telemetry, Play-services dependency) → owner "swap but
  test qr on its own first if native solves it" → Pixel Camera scanned
  the spike's QR and fired `av://` into the scanner (12:19:28, 12:19:38)
  → owner "why do I need it inside my app" → D69: in-app scanner removed
  entirely, camera-app route.
- Item 20 shape = existing button verb "Verify" with a verified `av://`
  pending / "Scan" without (D68a).
- Item 21 = adaptive icon + "zkagent Scanner" label, debug "(Debug)"
  badge.
- Owner "uninstall both" spike apps (done: `com.tananaev.passportreader`,
  `com.zkagent.m2sessionpoc` removed from Pixel).
- Owner "single instance ok" (finding #19 → `singleTask`).
- Owner device asks → D70: item 22 glyph on collapsed log entries, item
  23 persistent log (Option B promoted), item 24 version stamp.
- Owner asked what masterlist is, mode A vs B, 8788 vs 8789, when A vs B
  (verifier decides via signed tier), linkability (A unlinkable
  everywhere; B linkable same-site only), whether users are told which
  mode (status line, PIN prompt names site, log Identity line).

## Commits

On `chore/m2-exit-cleanup`, in order:

- `151c0f1` stash
- `ee14a3d` Q35 threshold from signed request (`RequestTrust.thresholdOf`,
  mint-time refusal)
- `e244255` `PaneState` KDoc
- `eb36858` #18 logging half
- `8351ae1` D66/D67 docs, items 17–21, exit-criteria row 1 marked not
  re-run
- `5a4b13b` Q36/D66 (`AgeCheck`, `MintOutcome`, DOB threaded, never
  logged)
- `355c59d` D66 note
- `dad71dd` item 21 icon/label
- `d584f00`/`c34de45` item 17
- `8bc37a4`/`cdc41c0` item 18
- `1bc345b`/`0ed940b` item 19
- `fc5faec` item 20
- `9561d28` Q36 follow-up (honest-under from own answer)
- `7708ce9` D68 docs
- `0cf436b` spike QR image (`qrcode@1.5.4`)
- `e5f2008` Google Code Scanner (later removed)
- `5ea5210` docs sweep
- `55ee40b` ledger nits
- `f8320af`/`2525267` D69 remove scanner
- `8c063ec`/`1901e6a` #20 `lockModeAndArm` early exits logged +
  incomplete-fields blocking dialog (`LockPrecondition`)
- `e3c92c8` D70 docs + finding #19
- `039fee7` #19 `singleTask`
- `1a8ab6d` item 24 version stamp, versionName 0.2.0
- `3f65290` masterlist bucket (ii) unit test (`PassiveAuthTrustTest`,
  `M0Probe.checkTrustPath` extracted)
- `6f3f134`/`00211ed` items 22/23 (`ReportLog.Outcome`,
  `ReportLogStore` JSON in filesDir, Clear log)
- `9765261` #19 FIXED docs + D70(d)
- `20a60dc`/`7f25f40` #21 mode A bare tier-A presentation
  (`MintGate.actionFor`, `presentBareA`, item-15 modal) proven vs real
  chiproof over HTTP (`verify-mode-a-bare.mjs`)
- `b37830b` PM evidence page
  `docs/logs/M2-DEVICE-SESSION-2026-09-03-PM-EVIDENCE.md` (290 lines)
- `fb0e75f` item 24 layout fix (pane FrameLayout 0dp/weight 1; stamp was
  off-screen)

Tests: scanner 660 (330 × 2 variants), chiproof 191, spike 23, all 0
failures. `/branch-review` at `2525267`: READY, 0 blockers
(mutation-tested `AgeCheck`/`MintOutcome`/`thresholdOf`/`ReportLog`;
release manifest free of Google components); HEAD has moved since → a
re-review is needed before `/release`. Fix-ledger: 0 open. `last-review.md`
sha `2525267`.

## Device session

Pixel 6a. Builds: `2525267` (12:50), `039fee7` (13:11), `7f25f40` v0.2.0
(13:47), fix APK `e8e0c33` (14:04, worktree build; content = `fb0e75f`).

Spikes: 8788 threshold 18, 8789 `THRESHOLD=99` (all four earlier node
processes were killed by a subagent's cleanup ~13:50; restarted with
`setsid`). `adb reverse` 8787/8788/8789/18787.

Logs: scratchpad `device-session-2.log`, `device-session-3.log`,
`handoff-8788.log`, `handoff-8789.log`, `qr-native-test.log` (scratchpad
`/tmp/claude-1000/-home-hamr-PycharmProjects-zkagent/ebcbb332-d50b-411e-b12e-14a44f9c465c/scratchpad`).

Results:

- 12:54 US passport mode B vs 8787 PASS, prefix `8754ed80d9e1`.
- 12:56/13:10 "press Verify nothing" → finding #20.
- BAL-blocked NFC to invisible second instance → finding #19.
- 13:22 NL mode B 8788 PASS, prefix `f462a66b50bc`.
- 13:27 mode A read but no `direct_post` → finding #21 (HIGH).
- Uninstall/reinstall 13:23.
- 13:52 NL mode B after reinstall: same prefix `f462a66b50bc` → zktag
  stability PASS.
- 13:54 mode A bare presentation sent, verifier tier A allowed, no
  zktag → PASS (also: mode A after mode B emits no zktag).
- 13:56 8789 `over_threshold=false threshold=99`, verifier
  `allowed=false under_threshold`, owner saw "threshold not met" dialog →
  Q36 PASS.
- 13:57 masterlist probe 588/588, half-load REFUSED → PASS.
- 13:58:13 #20 dialog fired (device-confirmed).
- 13:58:41 local scan with item-15 dialog.
- Owner: logs persist (item 23), glyphs seen, icon/label seen, version
  stamp NOT visible → fixed at `fb0e75f`, glance pending.
- BAL "blocked" lines still appear on the `singleTask` build but read
  continues on the same pid via `onNewIntent` (refined, not overturned).

Not yet observed by owner: item 17 tab switch, item 19 dim, item 20 verb
wording, Clear log survival, footer stamp on the fix build.

## Findings/decisions

- Finding #18 closed (D69).
- Finding #19 FIXED-IN-`039fee7` (device-confirmed).
- Finding #20 FIXED-IN-`8c063ec` (device-confirmed).
- Finding #21 FIXED-IN-`20a60dc` (device-confirmed).
- Decisions D66–D70 recorded. PRD history v1.53.
- Exit-criteria row 1 re-run on the real build recorded on the PM page.

## Reviews/release

`/branch-review` at `2525267`: READY, 0 blockers (mutation-tested
`AgeCheck`/`MintOutcome`/`thresholdOf`/`ReportLog`; release manifest free
of Google components). HEAD has moved since (through `fb0e75f`) — a
re-review is needed before `/release`. `/release` itself was not run this
session. Fix-ledger: 0 open. `last-review.md` sha `2525267`.

## Still open / owner-owned

- Owner glance of the footer stamp + the four unobserved checks (item 17
  tab switch, item 19 dim, item 20 verb wording, Clear log survival).
- One docs commit needed for those statuses.
- `/branch-review` at the final HEAD.
- `/release` (versionName 0.2.0 proposed by coder, owner-overturnable;
  chiproof unchanged at 0.4.0).
- Merge on owner word (FF push, bypass flagged).
- Docs reorg still due.
- `learnings.md` updated through 09-03 in `5ea5210`.
- MEMORY.md "frozen" fact still stale → next `/remember`.
- Spikes on 8788/8789 still running.
- Owner may want the outcome dialog to state the mode (offered, not
  decided).
- Item 22 glyph rule honest-under = ✗ (owner may overturn).
- "Tap and verify" wording (owner may overturn).

## Orchestrator lessons

- Every green suite and both prior device sessions were mode B only, so
  mode A never delivering a presentation (#21) was invisible until a mode
  A device run.
- An unlogged early-exit Snackbar (#20) reproduced the exact "nothing
  happened" symptom of #18 — audit every early return for a log line.
- A subagent asked to "stop the server" it started killed every node
  server on the box — instruct subagents to kill by pid, never by
  pattern.
- Cherry-picking many worktree branches conflicts on `docs/index.md` and
  CHANGELOG every time — resolve docs mechanically (keep both) and
  regenerate the index once at the end.
- A "no permission added" dependency can still move the sensitive work
  into a vendor process — audit where bytes go, not just the manifest.
- The owner's one-minute native-camera test replaced a dependency
  decision with a fact.
- Worktree-based coders must be told the exact rebase SHA and that the
  fixture dir is absent.
- Commit messages carrying self-referential "FIXED-IN-`<sha>`" are always
  one amend behind — cite the branch sha in a follow-up docs commit
  instead.

## Environment

Verifier spikes on ports 8787/8788/8789/18787, reached via `adb reverse`
(all four re-established after the box-wide node kill, restarted with
`setsid`). Spikes: 8788 threshold 18, 8789 `THRESHOLD=99`. Logs in
scratchpad `/tmp/claude-1000/-home-hamr-PycharmProjects-zkagent/ebcbb332-d50b-411e-b12e-14a44f9c465c/scratchpad`:
`device-session-2.log`, `device-session-3.log`, `handoff-8788.log`,
`handoff-8789.log`, `qr-native-test.log`. Spike apps
`com.tananaev.passportreader` and `com.zkagent.m2sessionpoc` uninstalled
from the Pixel 6a this session.
