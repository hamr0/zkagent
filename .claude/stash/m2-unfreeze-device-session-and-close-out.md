# Session Stash — M2 unfreeze, device session, close-out (2026-09-03)

Project: zkagent · Owner: hamr · Branch: `chore/memory-consolidation`,
merged to `main` this session and then deleted (remote and local).
Orchestrator: Fable 5.1. Main session ran as orchestrator only — every
code/docs change was made by a Sonnet subagent; every test count was read
from JUnit XML/TAP, never from agent prose.

## Owner asks, in order

Session start: continued after `/clear` from stash
`m2-reconciliation-unfreeze-prep.md`, on branch
`chore/memory-consolidation` at `82c7dd8`. Owner rulings already in hand
from the prior turn:

- #10 gate permanent (close by construction)
- #11 closed on site-named prompt title
- #6/finding #4 → lock orientation to portrait
- #16/Q38 → Option A (accept+disclose, rescan); Option B (on-disk marker)
  deferred to next module

Sequence agreed with the owner: rulings recorded → device session → fixes
reviewed → freeze lift → `/release` → merge on owner word → branch
closed. Later in the session the owner said "unfreeze" (D65).

## Surviving/pruned agents after `/clear`

A Q47/orientation coder had been spawned in an isolation worktree cut
from `main` (`77bae4c`) instead of the branch, so its test count read 184
(main's) instead of 213. Its parent agent diagnosed the mismatch,
cherry-picked both of its commits onto the branch, and re-ran, landing at
213/0. The orchestrator separately pruned two stale agent worktrees after
verifying their content was byte-identical to the commits that had
already landed.

## Commits this session (all on `chore/memory-consolidation`, then
fast-forwarded to `main`)

- `626c1f7` docs: D61–D64 rulings (#10 closed by construction, #11
  closed, D63 portrait, D64 Option A); Q47 marked "fix in flight".
- `0b71957` fix(apps/scanner): Q47 —
  `clearPassportNumberFocusAndKeyboard()` (`clearFocus` +
  `hideSoftInputFromWindow`) called on date-field tap and in both
  `DatePickerDialog` positive callbacks; `main_layout` `ScrollView` set
  `focusableInTouchMode=true`. No unit test written (View/IMM stubs are
  inert under Robolectric's default config).
- `d406f4b` fix(apps/scanner): `src/regular/AndroidManifest.xml`
  `screenOrientation` `fullSensor`→`portrait` (D63).
- `702b435` chore(stash): prior session stash committed.
- `/branch-review` at `702b435` (target `82c7dd8..HEAD`): READY, 0
  blockers, 213/0; one fix-ledger bullet (`PaneState.kt:13` stale
  `fullSensor` KDoc). Note: this reviewer finished all its tool work at
  22:28 but never emitted a final message and sat "running" for 9 hours
  until `TaskStop` the next morning; its record file on disk was
  complete and correct throughout.
- `48775fe` chore(memory): `/remember` — 1 stash processed, facts 65→71,
  10 episodes kept (the M1b episode folded into the adopter-gate fact),
  34 friction clusters reviewed, all single-session (5 already counted
  under ag-001, 13 new written nowhere, 16 dropped), ag-001 hot
  sessions=8 with 0 recurrences; I6/I7 checks passed; docs index-flat run
  found 17 docs changed since `a7e0341` → a reorg is due.
- `2c7f527` docs: device-session evidence page
  `docs/logs/M2-DEVICE-SESSION-2026-09-03-EVIDENCE.md` (253 lines),
  findings #6/#10/#11/#16/#17 device notes, NEW finding #18, Q47 marked
  FIXED and device-confirmed, CHANGELOG updated.
- `cf4b3ac` docs: D65 — freeze LIFTED (owner said "unfreeze"); history
  bumped to v1.48; decisions title updated to D1–D65.
- `/branch-review` at `cf4b3ac` (target `702b435..HEAD`): BLOCKED —
  CHANGELOG line 8 said "Freeze lifted (D65)" while the very next bullet
  still said "lift ruling still pending owner"; fix-ledger +1 (index.md
  count 252 vs actual 253).
- `3412516` docs: CHANGELOG contradiction resolved; index count corrected
  to 253.
- `/branch-review` at `3412516` (target `cf4b3ac..HEAD`): READY; the
  252-bullet ledger item was deleted as disproven, the `PaneState` bullet
  was kept. Note: this reviewer first wrote its record as a bullet list
  with a short sha and had to be told to rewrite it in the spec's
  key:value format with the full sha.
- `/release`: Phase 0 clean tree, 20 commits / 27 files vs
  `origin/main`; Phase 0.5 sha match, verdict ready, coverage all ran;
  owner chose "No bump, merge only" (`chiproof` 0.4.0 local matched
  published and was untouched in range; `apps/scanner` versionName 0.1.0
  remains unpublished). `/ship` worker GREEN: scanner 213/0, chiproof
  191/191, build OK, no secrets/PII/debug leftovers, release manifest
  cleartext-free at the source level (no `aapt2` binary check run).
  Branch had no upstream. Docs sweep edited `docs/wiki/milestones.md`
  (M2 status cell "frozen" → "D57/D60 freeze LIFTED (D65)") and
  `apps/scanner/README.md` (finding #18 note); flagged
  `docs/product/learnings.md` as unmaintained since 2026-08-31.
- `4a28b15` release: docs sweep commit, no version cut.
- Owner ran `git push -u origin chore/memory-consolidation` and
  `git push origin chore/memory-consolidation:main` (fast-forward
  `77bae4c..4a28b15`; this bypassed the "changes must go through a pull
  request" branch-protection rule — flagged, not blocked, per owner
  action). Orchestrator verified `origin/main == HEAD`, checked out
  `main`, and deleted both the remote and local
  `chore/memory-consolidation` branch. No tag was cut, no npm publish was
  made.

## Device session

Pixel 6a, build at `702b435`, APK built 22:27 Sep 2, package
`com.zkagent.scanner`. `adb` needed a `kill-server`/`start-server` cycle
to see the phone; `adb reverse` set up for ports 8787 and 18787; verifier
spikes pid 79615 (:8787) and pid 82562 (:18787) were both alive
throughout. Logcat filter used:
`MainActivity:V DeviceKey:I RequestTrust:I HandoffClient:I
M2Masterlist:I AndroidRuntime:E`. Session produced 270 log lines across 8
reads, with 0 `AndroidRuntime` crash lines.

1. **Q47** — owner reported "cursor fixed" on both date fields. PASS.
2. **Portrait lock** — owner reported "rotation doesn't rotate at all
   either on or off"; device log showed `mRotation=ROTATION_0`; installed
   manifest had `screenOrientation=1`. PASS.
3. **Mid-read re-tap (`mayStartTagRead` gate)** — NOT REACHABLE on
   device. The NFC service saw only one discovery event (09:05:36), no
   second discovery; a presence check failed at 09:05:53–54, after the
   app's own read had already failed at 09:05:52 ("Tag was lost", EF
   11d). A synthetic `am start` `TECH_DISCOVERED` intent can't reach the
   gate — it needs a real `Tag` parcelable carrying `IsoDep`. The gate
   remains unit- and wiring-proven; it is defence-in-depth against a
   state the platform itself prevents.
4a. **`av://` path + forced recreation** — driven via `adb shell settings
    put system font_scale 1.15`, fired by a watcher on the "MRZ input"
    log line. The read finished in 2.5 s, so the forced rebuild landed
    while the `BiometricPrompt` was open. Fence retired at 09:10:30.612;
    report/log state was restored (`log_entries=6`); the prompt's late
    callback then minted and `direct_post` returned 200/accepted at
    09:10:33; log showed "fence closed — dropped post-mint session clear"
    and "dropped mint report/confirmation (COMPLETED result...)"; the
    verifier on :8787 independently showed `allowed=true`,
    `attester=matched`. This exercised both check 5 (BiometricPrompt
    fence, device-verified) and D64 Option A observed live. PASS.
4b. **QR/paste path** — the verifier spike's page renders no QR image (a
    known TODO; it links the `av://` URL as text instead). The app's
    "Scan QR" flow (a `TakePicturePreview` thumbnail → `QrCapture.decode`)
    did not decode during the owner's camera attempts at 09:13:12–22;
    those produced only an unlogged Snackbar. The orchestrator generated
    a QR PNG with `qrencode` as a second attempt, which also failed to
    decode, then drove the same code path
    (`applyPendingHandoffText`) directly via
    `adb shell input text '<av link>'` + `keyevent 66` into the
    `handoff_manual_input` field. First attempt at 09:23:05 hit
    "verification session expired before lock" (the 10-minute
    transaction opened at 09:12 had expired); a fresh transaction opened
    at 09:24:21, and the read from 09:25:06 was captured and verified
    with no "captured from av:// intent" line present — confirming it
    went through the paste/QR code path, not the intent path. `keyevent
    66` fired the editor action twice, so a second (harmless) "Not a
    recognised av:// link" Snackbar fired against the now-cleared field
    — also unlogged. Read completed at 09:26:33.817; forced recreation
    fired at 09:26:34.409; fence retired at 09:26:34.964; state was
    restored (`log_entries=1`); at 09:26:37.703 the log showed "fence
    closed — dropped read completion handling"; the verifier transaction
    stayed `pending`. PASS.
6. **Hostile `av://` from a second origin** — sent from the :18787
    verifier via
    `adb shell am start -a VIEW -d <link> -n
    com.zkagent.scanner/com.tananaev.passportreader.RegularActivity
    --activity-single-top` (the activity is `singleTop` and exported),
    fired 0.7 s after the "MRZ input" log line. Run 1 (09:28:26.218) got
    "av:// handoff REFUSED — session locked or read in progress"; the
    legitimate read then itself failed on tag loss at `sendSelectApplet`
    because the owner lifted the card on an ambiguous orchestrator
    instruction ("under a second into the read, a second verifier fires
    its own link" was read as a cue to act). Run 2 (09:30:23.766) also
    got REFUSED, and the legitimate read completed at 09:30:25 (PACE,
    chip_auth passed, passive_auth ok), biometric was approved,
    `direct_post` to :8787 returned 200/accepted, verdict PASS (minted),
    `scope_domain` 127.0.0.1, plug `sig-p256/1`; the verifier on :8787
    independently showed `allowed=true`, `attester=matched`; both hostile
    transactions on :18787 stayed `pending`. PASS.

## Findings/decisions

D61–D65 recorded. Findings #10, #11, #17 closed. #16 closed as D64
(Option A). #4's rotation vector closed (its remaining, non-rotation part
carries forward to the next module's SessionState design). #6's mid-read
branch confirmed not device-reachable (see item 3 above — remains
unit/wiring-proven only). NEW finding #18 opened: the QR path's
`TakePicturePreview` thumbnail can't decode a long `av://` URL rendered
on a laptop screen, and three unlogged Snackbars exist in
`MainActivity.kt` around lines ~290/295/841 — non-blocking, deferred to
the next module.

## Reviews/release

Two `/branch-review` passes this session (at `702b435` and `3412516`,
described under Commits above) plus one BLOCKED intermediate pass (at
`cf4b3ac`) caught by a CHANGELOG self-contradiction, which was fixed and
re-reviewed READY. `/release` ran clean with the owner choosing
"No bump, merge only"; `/ship` GREEN; docs sweep caught a normative
contradiction in `docs/wiki/milestones.md` that the review passes had
missed. Merge to `main` was a direct fast-forward push done by the owner
(bypassing the PR-required branch protection rule — flagged), verified
by the orchestrator, after which the feature branch was deleted on both
remote and local.

## Still open / owner-owned

- `docs/product/learnings.md` unmaintained since 2026-08-31.
- MEMORY.md's fact stating "M2 ... FROZEN (D57/D60)" is now stale and
  needs correcting at the next `/remember`.
- A docs reorg is due (17 docs changed since `a7e0341`).
- Fix-ledger has 1 open item (`PaneState.kt` stale `fullSensor` KDoc).
- Finding #18 (QR thumbnail / unlogged Snackbars) — next module.
- Option B (on-disk marker for #16/Q38) — next module.
- Installing the fixed `docs-builder` (the split link-rewrite bug fix
  from the prior session remains uninstalled).
- Verifier spikes are still running on the owner's box.

## Orchestrator lessons this session

- Worktree isolation branches from `main`, not the caller's branch — the
  spawning agent must state the exact rebase SHA, or a subagent's test
  counts silently read the wrong baseline.
- A background subagent can finish all its tool work and then stall with
  no final message for hours — check its written artefacts/commits and
  `TaskStop` it rather than assuming it's stuck or waiting on it further.
- Instructions to a human tester mid-test must carry only actions, never
  timing narration — "under a second into the read, a second link
  fires..." was read by the owner as a cue to physically lift the card.
- A same-card NFC re-tap can't be used to produce a second discovery
  intent on this Pixel 6a — that gate can only be proven by unit test and
  wiring trace, not by a live device repro.
- The verifier spike's "QR" is text-only (no rendered image), so any
  QR-path device test has to go through the manual paste field instead.
- `adb shell input text` followed by `keyevent 66` double-fires an
  EditText's editor action — expect a second, harmless callback against
  the now-cleared field.
- A `/branch-review` record must be written in the spec's key:value form
  with the full sha, or `/release` can't match it against the tree.
- A review pass reading only commits since its own last record can miss
  a normative contradiction sitting in a sibling page (e.g.
  `milestones.md`) that a later, differently-scoped sweep (the release
  docs sweep) then catches.

## Environment

Verifier spikes: `LINK_SCHEME=av node server.mjs` at pid 79615 on
`127.0.0.1:8787` (stdout redirected to
`/tmp/claude-1000/-home-hamr-PycharmProjects-zkagent/0dfa7f99-9876-4be0-bf0b-c009bbeb4066/scratchpad/handoff.log`)
and pid 82562 on `127.0.0.1:18787`. `POST /ui/presentations
{"mode":"B","ttlMs":600000}` (`MAX_TTL_MS` 600000) returns `app_link_av`;
`GET /ui/presentations/<txId>` returns `pending`|`done`. `adb reverse`
must be redone for both ports after any `adb` restart. Forced Activity
recreation was driven via `adb shell settings put system font_scale
1.15`, then reset to `1.0`. The scan form must never be screenshotted or
dumped — it renders real document fields.
