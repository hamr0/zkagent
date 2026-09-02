# Session Stash — M2 reconciliation run, PRD dedupe, unfreeze prep (2026-09-02)

Project: zkagent · Owner: hamr · Branch: `chore/memory-consolidation` (kept
by owner request, not renamed). Orchestrator: Fable 5.1. Main session ran
as orchestrator only — every code/docs change was made by a Sonnet
subagent; every test count was read from JUnit XML, never from agent
prose.

## Owner asks, in order

1. "cont from here... #1 continue the 5 fences and run /refactor to close
   open items, reconciliation run, keep same branch, commit to it."
2. "we need to dedupe prd the contract from findings, it grew massively" →
   then "prd dedupe is /docs-builder job not you" → owner ran
   `/docs-builder reorg` then `/docs-builder cleanup prd.md`; confirmed the
   split themes; "prd split is approved".
3. "then /refactor at some point as well".
4. Corrected a misread: the PRD was 2,273 lines, lines 8-715 were a
   stacked prior-revision preamble (75 KB); §10 decisions 115 KB, §15
   history 62 KB.
5. Snackbar question: "is the tag the av:// injection midread? if doesn't
   need to appear to user then remove it" → Snackbar removed. "#2 work on
   all the rest, spawn sonnet."
6. "let's clean them all and close this branch after unfreeze. explain
   each point in simple terms and proposal to review" → owner answers:
   - #10 ok (gate permanent, close by construction)
   - #11 "it did work, confirmed" (site-named prompt title is the fix,
     close)
   - #3 ok (BiometricPrompt fence device test)
   - #4 agreed, QR code shown on laptop screen for the paste/QR test
   - #5 Q47: "it happens from when I first open the app till the end;
     when I choose date cursor still there [document field], date
     selection is fine but by mistake after ok date it's there" — confirms
     the hypothesis that the document field is the only focusable field
     and focus never leaves it after the date picker OK
   - #6 finding #4: "make it not rotate, some apps don't" → lock
     orientation to portrait
   - #7 finding #16/Q38: owner asked "screen dies as an app crash? popup
     disappears or log not saved?" — answered: not a crash, Activity
     rebuilt mid-`direct_post`, verifier got the proof, popup never shown
     AND log never written; owner: "oh well, they scan again or if you
     capture error log it after app restart as failed" → orchestrator
     offered Option A (accept+disclose, rescan; zero code) vs Option B
     (tiny on-disk marker, site host + timestamp only, log "result not
     recorded" on next start; amends D44's in-memory-only log; NO-GO #9 is
     about secrets, not disk, so no conflict) and recommended A now, B as
     first item of next module. AWAITING owner's A/B.
   - #8 confirmed
   - #9 sequence agreed: rulings recorded → one device session → any fix
     committed+reviewed → freeze lifts under D57 criteria → /release →
     merge with owner word (admin bypass, flagged) → branch closed.

## Commits this session (all on `chore/memory-consolidation`, base main `77bae4c`)

- `afc2450` chore(memory): consolidate 5 stashes (pre-existing HEAD at
  session start)
- `e13dab0` /refactor ledger mode: `LifecycleFence.kt` KDoc names the
  hazard predicate (13 sites: 10 `runOnUiThread`, 1 `onPostExecute`, 2
  `BiometricPrompt` main-executor callbacks); fix-ledger cleared to 0.
  Tests 184/0.
- `651ecd5` finding #8: pure `ChipAuthClassification`
  (fromDg14/combine/label/technical), 20 tests; strings byte-identical.
  184→204.
- `c60354e` finding #6: `HandoffAdmission.mayStartTagRead(sessionLocked,
  readInProgress) = sessionLocked && !readInProgress`, 4 tests; NFC branch
  refuses tag mid-read (Log.w + Snackbar + return, before MRZ
  snapshot/MrzChangeTracker). 204→208.
- `747ee6b` docs: findings #6/#8 FIXED notes, #10/#11 what-remains notes,
  CHANGELOG [Unreleased]→[0.4.0] — 2026-09-02, PRD v1.46 pointer row.
- `f7a70ce` /docs-builder reorg: corpus already sorted, index rebuilt 27
  rows; ledger stamped.
- `a7e0341` /docs-builder cleanup split of `docs/product/zkagent-prd.md`
  (2,276 lines, 430 KB): core page `docs/product/zkagent-prd.md` 157
  lines; `docs/wiki/milestones.md` 102 (15 numbered §6.2 items; source has
  no item 2), `decisions.md` 134 (D1-D60, 60/60), `questions.md` 212
  (Q1-Q48; Q3/Q4/Q10 do not exist in source), `history.md` 61 (48 rows).
  Original byte-frozen at `docs/archive/zkagent-prd.md` (sha256
  `d8087070a7f3284e`). 700-line preamble under no H2, not carried into any
  page. validate PASS 1551/1551 lines, 0 citation violations. Labels by
  Haiku (5 themes, core=spec); pages by Sonnet, 3 at a time.
- `0049035` repointed 28 prose references from `docs/archive/zkagent-prd.md`
  back to `docs/product/zkagent-prd.md` (wiki `sources:`/citations and core
  page `sources:` left on archive — line numbers refer to original);
  `history.md` v1.46 owner-approved.
- `57f5ddd` Snackbar + `TAG_REFUSED_MID_READ_MESSAGE` removed from mid-read
  tag refusal (owner decision); Log.w + gate kept. 208/0.
- `840779c` `ChipAuthClassification.fromActiveAuth(...)` extracted from
  `M0Probe.tryActiveAuth`, I/O inline, strings byte-identical, 5 tests (TDD
  red by compile error). 208→213.
- `d4653b9` Q46: `strings.xml` `input_passport_number` "Passport
  number"→"Document number"; resource id and `passportNumberView`
  unchanged.
- `82c7dd8` docs: findings #6/#8 notes, NEW finding #17 (Q47
  investigation: zero `requestFocus`, no
  `TextWatcher`/`OnFocusChangeListener`/`clearFocus` in module; date
  fields `focusableInTouchMode=false`, tap-to-`DatePickerDialog` only,
  `MainActivity` ~:404-424; hypothesis = framework focus restoration onto
  the only focusable EditText; evidence-wording discrepancy "while typing
  the date fields" vs no typing path), `questions.md` Q46 FIXED/Q47
  investigated, CHANGELOG 4 bullets.
- `57f5ddd`/`840779c`/`d4653b9` were made by a Sonnet coder in an isolated
  git worktree while a review ran on the main checkout, then
  fast-forwarded; worktree removed.

## Reviews

- `/branch-review` #1 at `0049035`, target `77bae4c..HEAD` (prior record's
  sha `67697ff` was on deleted `m2-build`, not an ancestor — reviewed the
  branch's own range instead): READY, 0 blockers, 0 ledger, ran
  `apps/scanner` 208/208 and chiproof 191/191.
- `/branch-review` #2 at `82c7dd8`, target `0049035..HEAD`: READY, 0
  blockers, 0 ledger, 213/213. `.claude/remember/last-review.md` sha =
  `82c7dd8`. `/refactor` had nothing to consume (ledger empty).

## In flight at stash time

- Sonnet coder spawned on the MAIN checkout (no review running) for two
  commits: (1) Q47 fix — after `DatePickerDialog` OK and on date-field
  tap, `clearFocus` on `passportNumberView` + hide soft keyboard, possibly
  root/scroll container `focusableInTouchMode=true` as landing; no
  editable date fields; (2) orientation lock — `AndroidManifest.xml:7`
  `screenOrientation` `fullSensor`→`portrait`, check regular-flavour
  manifest, keep `PaneState`/`LifecycleFence` (recreation still possible
  on font-scale/locale/process death), list stale `fullSensor` doc
  mentions. Baseline 213 tests.
- After it lands: docs agent to record rulings — D61 (#10 closed by
  construction, `HandoffAdmission` permanent tested gate), D62 (#11
  closed on site-named prompt title), D63 (orientation locked to portrait,
  closes #4's rotation vector), #16/Q38 per owner's A/B answer;
  findings.md notes; decisions.md/questions.md; CHANGELOG.
- Then device session (Pixel NOT attached at stash time; verifier spikes
  alive: pid 79615 `LINK_SCHEME=av node server.mjs` on 127.0.0.1:8787,
  pid 82562 `PORT=18787` on 127.0.0.1:18787 = second origin for the
  foreign-origin #10 test; cwd `spikes/m2-handoff`). Device items: Q47 fix
  check (open app, type doc number, pick date, OK, cursor/keyboard gone
  from doc field); re-tap mid-read → expect Log.w "M2 stage: ignoring tag
  intent — a read is already in progress" and no second `startSession`;
  QR request on laptop screen + forced recreation mid-verify (font_scale
  1.15→1.0 trick; recreation deferred while screen dozes); destroy
  Activity with biometric prompt open → "fence closed" log, no
  `BadTokenException`; hostile `av://` from 18787 mid-scan → refusal; #11
  prompt shows host (owner already confirmed by eye). `adb reverse
  tcp:8787 tcp:8787` after any adb restart; logcat spec `adb logcat -s
  MainActivity:V DeviceKey:I RequestTrust:I HandoffClient:I
  M2Masterlist:I`; never screenshot/full-dump the scan form.
- Then: freeze lifts (D57 criteria 1,2,3 all met), /release (chiproof
  untouched, local=published 0.4.0, nothing to publish; release = merge
  to main), merge on owner word with admin bypass, branch closed.

## Cross-session (peer "lite", liteagents, owns docs-builder.cjs)

Reported the cleanup-apply ordering bug: archive step rewrites all inbound
links to `docs/archive/`, then relocates the core page; 33 files / 43
occurrences here (5 exempt sources: lines, 38 real). Peer reproduced with
a failing-first test 25c, fixed in liteagents `ebf397b`
(feat/friction-antecedent-matching): a third restore pass exempting PAGES
and core page `sources:`. Orchestrator reproduced on the real corpus in a
scratch worktree at `f7a70ce` by absolute script path
(`/home/hamr/PycharmProjects/liteagents/packages/claude/commands/docs-builder/docs-builder.cjs`):
"restored 38 inbound reference(s)", byte-identical to `0049035` except the
later `history.md` approval edit; noted a units mismatch (advisory "33
link rewrite(s)" = files vs "38 reference(s)" = occurrences) and
`docs/index.md` needlessly in the restore list. Installed
`~/.claude/commands/docs-builder/docs-builder.cjs` (Sep 1 build) still
buggy — install is the owner's call. Memory saved:
`docs-builder-split-link-bug.md`.

## Still open / owner-owned

- Owner's A/B on #16/Q38.
- Stray `v0.4.0` tag: already gone (only `chiproof-v0.4.0` exists) — a
  prior stash's note flagging it was stale.
- Finding #4's remaining non-rotation part (`lastMrzHash` diagnostic
  mislabel) → next module SessionState design.
- Verification debt closes only with the device session.
- Installing the fixed docs-builder.

## Orchestrator lessons this session

- A review record SHA on a squash-merged, deleted branch is not an
  ancestor; review the branch's own merge-base range and say so.
- Two review passes converged because each read only commits since the
  last record.
- Run coders in a worktree while a review holds the main checkout;
  fast-forward after.
- A count printed by a tool can be files or occurrences; reconcile units
  before calling a gap.
- The Q47 coder refused to guess-fix; the owner's one-sentence device
  account then confirmed the hypothesis.

## Environment/recipes

Verifier spike `LINK_SCHEME=av node server.mjs` on 127.0.0.1:8787, pid
79615, cwd `spikes/m2-handoff`; second origin `PORT=18787` on
127.0.0.1:18787, pid 82562, for the foreign-origin #10 test. `adb reverse
tcp:8787 tcp:8787` must be redone after any adb restart. Logcat spec `adb
logcat -s MainActivity:V DeviceKey:I RequestTrust:I HandoffClient:I
M2Masterlist:I`. Never screenshot or full-dump the scan form — it renders
real document fields.
