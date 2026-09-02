# Findings — open, durable, forwarded

> Append-only. Do not edit or delete existing entries — only add new ones or flip a `status` line
> by appending a dated status-change note under the entry it belongs to.

**What this is.** Open findings any agent noticed while working a piece of `apps/scanner` state and
did **not** fix in the same change — the durable-file half of the 2026-09-02 ownership-refactor rule
set (owner freeze after the M2 scanner reached ~4,780 unreviewed LOC across seven isolated agent
rounds): a finding an agent is not fixing goes here, never into a code comment, so the next spawn
that touches the same state inherits it instead of rediscovering it. This is the artifact rule 2
("every agent spawn carries history for the state it touches") reads from — **before spawning a
build/fix agent for any file, grep this file for that file's path and hand the matching entries to
the agent in its briefing.** Read-only audits and device runs also land findings here, not only
build/fix agents.

**Format.** One entry per finding:
- **Date** — when the finding was recorded (not necessarily when first noticed).
- **Source** — `audit` (a read-only pass), `agent` (a build/fix spawn that found something adjacent
  to its task), `owner` (a live device observation), or `device run` (a captured log/repro).
- **Anchor** — `file:line` (or `file:line-range`) plus the SHA the lines were read at. A line number
  drifts; the SHA lets a reader tell whether it still holds.
- **Finding** — stated as a fact about the code/system, not as an instruction or a task.
- **Status** — `OPEN`, `FIXED-IN-<commit>`, or `WONTFIX-<decision>` (cite the `Dn`/owner call that
  closed it).

All entries below are anchored to **`2cd1e00`** (current `main`/`m2-build` HEAD) **plus the
uncommitted D55/D56 working-tree changes** — `MainActivity.kt` is under active edit for the D55
pane-visibility fix and the D56 MRZ-change diagnostic, and two new files (`PaneVisibility.kt`,
`MrzChangeTracker.kt`, plus their tests) exist uncommitted. Line numbers below cite the state the
2026-09-02 read-only ownership audit (`docs/logs/M2-OWNERSHIP-AUDIT-2026-09-02.md`) actually read at
that moment, not necessarily what a future commit will show — re-anchor on read if the surrounding
code has moved. Source for items #1–#8 and the owner-observation item: the ownership audit; cite the
audit's own section for the full reasoning behind each row.

---

### 2026-09-02 — #1: TabLayout selection vs. `showPane()` race on rotation

- **Source**: audit (§(a2) Framework-owned state)
- **Anchor**: `apps/scanner/app/src/main/AndroidManifest.xml:7` (`android:screenOrientation=
  "fullSensor"`) + `MainActivity.kt` (tab-restore call site in `onPostCreate`, per audit §(a2)) @ `2cd1e00`
- **Finding**: the framework restores `TabLayout.selectedTabPosition` in `onPostCreate`, while the
  app's own pane-visibility decision (pre-D55: `showPane()`/its predecessor logic) runs from
  `onCreate` — two different lifecycle callbacks deciding related state, racing on every rotation
  because `fullSensor` orientation means every rotation re-triggers both. CONFIRMED on device
  2026-09-01.
- **Status**: OPEN. Held under the 2026-09-02 freeze (D57) — not fixed by the in-flight D55 change,
  which addresses the three-pane overlap but not this rotation-restore race specifically.

### 2026-09-02 — #2/#3: `verifiedRequest`/`pendingHandoff` — three writers, cross-thread reads, non-volatile

- **Source**: audit (State join §, call graph §)
- **Anchor**: `MainActivity.kt:172,177` (field declarations, not `@Volatile`); writers at
  `:605-606` (`beginHandoffVerification`), `:1637-1638` (`mintAndMaybeHandoff`, posted), `:883-884`
  (blocking-dialog OK handler); cross-thread reads at `:1281-1282` (`continueAfterRead`, inside the
  `Thread{}` opened at `:1262` — verified by reading the enclosing block, not lexical proximity) and
  `:1470-1471`/call site `:1414` (`mintAndMaybeHandoff`) @ `2cd1e00`
- **Finding**: THREE distinct writers for both `verifiedRequest` and `pendingHandoff`, with reads
  reachable from inside background `Thread{}` blocks on fields that are not `@Volatile` — no
  happens-before guarantee that a background read sees the main thread's most recent write, or vice
  versa. No staleness guard exists on the mint path: nothing checks that the handoff being minted
  against is still the one most recently verified. `wipeSession`'s own KDoc documents the
  main/background split as a known fact rather than closing it. Consequence, stated as a risk not yet
  confirmed as exploited: evidence could be signed against a superseded request if a write and a
  cross-thread read interleave unfavorably. Adversarial analysis in progress (per the audit).
- **Status**: OPEN. This is the headline "one writer per piece of mutable state" violation the
  2026-09-02 freeze names explicitly — a structural refactor target, not a point fix.

### 2026-09-02 — #4: 11 of 16 app fields lost on rotation; only two persist

- **Source**: audit (§(a) Mutable-state ownership table, §(a2))
- **Anchor**: `MainActivity.kt:532` (`lastReportText`, APP-PERSISTED), `:533` (`ReportLog.entries`,
  APP-PERSISTED); the other 14 of 16 tracked fields are FRAMEWORK-RESTORED (1: `TabLayout.
  selectedTabPosition`) or LOST (11, reset to declared default) — see audit §(a2) for the full
  16-row table @ `2cd1e00`
- **Finding**: on every rotation, the framework restores the tab selection while all six MRZ
  `EditText` fields and 11 of 16 tracked app-level fields silently reset to their declared defaults;
  only `lastReportText` and `ReportLog.entries` survive via explicit `onSaveInstanceState` handling.
  **Open question, not yet resolved**: after a rotation, the form re-populates from the restored
  `EditText` state (framework-level, separate from the 16-field audit table) while `lockedMode`/
  `lastMrzHash` come back null — does the lock guard, which reads live text, behave correctly against
  a post-rotation state where the text is present but the app's own tracking fields are not? Not
  traced to a conclusion by the audit.
- **Status**: OPEN.

### 2026-09-02 — #5: zero async-cancellation discipline

- **Source**: audit (§(e2), call graph §)
- **Anchor**: five unfenced `Thread{}` sites — `MainActivity.kt:306`, `:310` (both release-reachable,
  land in `emitReport` → `reportLog.append` → persisted `:533`), `:611`, `:1262`, `:1414` @ `2cd1e00`
- **Finding**: no `onDestroy`/cancellation/status check exists for any of the five `Thread{}` launch
  sites; each runs to completion (or crash) regardless of Activity lifecycle state, and at least two
  (`:306`, `:310`) can write into the persisted report log after the Activity that launched them is
  no longer the foreground Activity.
- **Status**: OPEN. Named directly by the freeze's "every async writer fenced" exit criterion.

### 2026-09-02 — #6: `handleIncomingIntent` tag guard ignores `readInProgress`

- **Source**: audit (State join §, Guards §)
- **Anchor**: `MainActivity.kt:539,560-566` (guard reads `lockedMode` + MRZ non-empty); `readInProgress`
  flag at `:920`/`:1075` (never consulted by this guard) @ `2cd1e00`
- **Finding**: the guard deciding whether an incoming NFC tag intent should be processed checks
  `lockedMode` and that the MRZ fields are non-empty, but never checks `readInProgress` — a tag
  intent arriving while a read is already in progress is not excluded by this guard on the evidence
  read.
- **Status**: OPEN.

### 2026-09-02 — #7: `reportView.text` written outside `emitReport`, contradicting its own KDoc

- **Source**: audit (§(a) table, cross-referenced against `emitReport`'s own KDoc)
- **Anchor**: write site `MainActivity.kt:288`; `emitReport`'s KDoc claiming sole-writer status at
  `:753-758` (see also `emitReport`'s body at `:769`, `reportLog.append` call) @ `2cd1e00`
- **Finding**: `reportView.text` is written directly at `:288`, outside `emitReport`, while
  `emitReport`'s own KDoc (`:753-758`) asserts it is "the ONE place a report is ever rendered to
  `reportView`" — the comment is contradicted by a second write site three hundred-plus lines away.
- **Status**: OPEN. Same class as the D45/2026-08-31 stall this project has already hit once
  (`reportView.text = ...` with no `Log.i`) — worth prioritizing given the precedent.

### 2026-09-02 — #8: chip-auth three-state logic has no unit test; DG14 handling inline

- **Source**: audit (§(d) Assertability map, §(e1))
- **Anchor**: chip-auth three-state (`VERIFIED`/`NOT_SUPPORTED`/`FAILED`, D51) logic — no
  `M0ProbeTest.kt` or equivalent exists in `apps/scanner/app/src/test/`; DG14 handling is inline
  inside `ReadTask.doInBackground` rather than extracted, per audit §(d)/(e1) @ `2cd1e00`
- **Finding**: the three-state chip-authenticity classification that D51/D53 made a user-facing,
  owner-approved distinction (verified / not-supported / failed, "MUST NOT render not-supported as
  false") has zero unit-test coverage; the DG14 read that feeds it lives inline in
  `ReadTask.doInBackground`, so it is not independently testable without extraction — same shape as
  the `FailureTransition`/`PaneVisibility` extractions already done for other decisions.
- **Status**: OPEN.

### 2026-09-02 — #9: `lockButton.isEnabled` — four writers, nothing reads it; joins the session and handoff clusters

- **Source**: audit (State join §)
- **Anchor**: four write sites for `lockButton.isEnabled` (per audit State join table; includes
  `:605-611` region alongside `beginHandoffVerification`'s writes) — no read site found by the audit
  @ `2cd1e00`
- **Finding**: `lockButton.isEnabled` has four independent writers and the audit found no read site
  for it at all (a `View.isEnabled` write only affects framework rendering, which is why nothing in
  app code needs to read it back — but four writers with no single owner is still the pattern the
  freeze targets). This field sits at the join between the session cluster and the handoff cluster
  (#2/#3 above) — a fifth writer or a UX change here (see owner observation (iii) below) touches both.
- **Status**: OPEN. **MUST NOT be touched before the structure pass lands** — see PRD Q40, which
  names this exact field as the seam a "Tap and scan" relabel would land on, and which the freeze
  (D57) blocks until ownership consolidation is done.

### 2026-09-02 — owner UX observations from the device run (deferred under freeze, PRD Q38/Q39/Q40)

- **Source**: owner (live device run, 2026-09-02)
- **Anchor**: (i) log lifetime — `ReportLog` in-memory only, no persistence path, ties to D45 and
  NO-GO #9; (ii) `AndroidManifest.xml` `singleTop` launch mode (audit-cited as `:8` in the relevant
  manifest) + `onNewIntent` (no tab-reset logic found by the audit); (iii) `lockButton.isEnabled`,
  see #9 above @ `2cd1e00`
- **Finding**: three ENHANCEMENT-class observations from the same run that surfaced D55/D56, deferred
  rather than actioned under the freeze: (i) the log does not survive app close — in tension with
  D45 (log accumulates for the app session) and NO-GO #9 (no on-device persistence of secrets/test
  keys, which this is not, but the boundary is worth restating precisely before building anything);
  (ii) Chrome reopens the app on its last-used tab (the Log tab) after a handoff, because `singleTop`
  + `onNewIntent` never resets tab state — the owner explicitly REJECTED an auto-switch-on-READ-
  completion fix (already covered by D55's "no auto-switch on a completed read" rule); an
  incoming-handoff-triggered switch is a distinct, undecided question; (iii) after an access failure
  the disabled Lock button reads to the owner as "stuck" — the owner wants copy closer to "Tap and
  scan," which lands directly on `lockButton.isEnabled`'s four-writer, no-single-owner state (#9).
- **Status**: OPEN, WONTFIX-not-applicable split: (ii)'s auto-switch-on-completion half is
  WONTFIX-D55 (owner considered and rejected it); the incoming-handoff-switch half and (i) and (iii)
  remain OPEN, tracked as PRD Q38/Q39/Q40 respectively, blocked by D57 until the ownership
  refactor's exit criterion is met.

### 2026-09-02 — #10: `av://` intent hijack mid-session — no lock/read-in-progress guard, unauthenticated origin binds real biometric+chip read to attacker's site

- **Source**: audit (read-only adversarial analysis, 2026-09-02; chain verified at source by the orchestrator)
- **Anchor**: `MainActivity.kt:544-550` (`av://` branch of `handleIncomingIntent`, no `lockedMode`/
  `readInProgress` guard — contrast the NFC branch immediately below, which checks `lockedMode ==
  null`); `:605-606` (`beginHandoffVerification` unconditionally overwrites `pendingHandoff`, nulls
  `verifiedRequest`); `:1281-1282` (`continueAfterRead`, reads both fields inside its `Thread{}`
  after the multi-second chip read; the D38 check refuses only on null, never on identity with the
  locked request); `:1391-1393` (biometric prompt shows only a static title/subtitle, never the
  requesting origin); `RequestTrust.kt:43,124-132` (non-local origins resolve their key from
  `https://<origin>/.well-known/zkagent-verifier` — verification proves provenance, not user
  consent); `EvidenceSigner.kt:17-21` (binds `nonce || scopeDomain || zktag` from whatever is in the
  two fields at signing time); `apps/scanner/app/src/regular/AndroidManifest.xml:8-9,28`
  (`exported="true"`, `singleTop`, so any on-device app reaches `handleIncomingIntent` via
  `onNewIntent` while the scanner is foregrounded) — **SHA `2cd1e00`; fix commit `4969a20` does NOT
  touch these lines, finding stands as of that commit too**
- **Finding**: an `av://` intent from any on-device app landing DURING an in-progress chip read
  (inside `continueAfterRead`'s `Thread{}`) overwrites `pendingHandoff`/`verifiedRequest` with no
  guard, and since the D38 identity check only refuses on `null` — never on a mismatch against the
  request that was actually locked and authorized — the read completing afterward produces a fully
  valid presentation for the ATTACKER'S origin, signed with the user's real biometric authorization
  and real passport/ID read, delivered to the attacker's `response_uri`. A second timing window (an
  intent landing during the biometric prompt itself) instead diverts the honest site's expected
  answer: the attacker's verifier rejects the resulting signature, but the UI shows accepted while
  the real site never receives anything. Both windows exist because verifying a request's signature
  (`RequestTrust`) only proves the request's origin authenticity — it proves nothing about which
  request the user actually consented to authorize, and the biometric prompt gives the user no cue
  at all about which origin is being authorized (static title/subtitle only).
- **Mitigation options recorded in the analysis, none applied**: (a) refuse/ignore `av://` intents
  while `lockedMode != null || readInProgress` — a contained, one-function fix; (b) snapshot the
  verified request into an immutable value at lock time and pass it as a parameter through the read/
  mint path, rather than re-reading the mutable fields — converges with the ownership-refactor's
  proposed `SessionState` consolidation; (c) show the requesting origin in the biometric prompt so
  the user has a real consent cue; (d) bind `response_uri` into the signed preimage so a diverted
  signature is unusable against a different endpoint. Owner has not yet ruled on whether (a) goes
  ahead of the structural refactor.
- **Status**: OPEN, **consequence HIGH**, adversary attached (any on-device app capable of sending
  an `av://` intent — no special permission required given `exported="true"`). Not fixed by
  `4969a20`. Directly overlaps findings #2/#3 (`verifiedRequest`/`pendingHandoff` multi-writer,
  non-volatile, cross-thread reads) — this entry is the exploit case that risk was flagged as
  "adversarial analysis in progress" for; that analysis is now complete and confirms it. Blocks
  under D57 pending an explicit owner ruling on whether mitigation (a) is a FIX that can proceed
  ahead of the full ownership refactor, or waits for it.
- Status update 2026-09-02: MITIGATED in 730ef09 — `HandoffAdmission.mayAdmitInboundHandoff` gating
  the av:// branch; remains OPEN for the ownership fix (lock-time snapshot / SessionState); the
  guard is to be REMOVED when that lands.
- Status update 2026-09-02: mitigation DEVICE-CONFIRMED (build 26f67ac) — see
  docs/logs/M2-D55-D56-EVIDENCE.md; still OPEN for the ownership fix.

### 2026-09-02 — #11: biometric prompt shows no origin/site/tier — consent defect, independent of and surviving #10's mitigations

- **Source**: second-session review + orchestrator verification at source, 2026-09-02
- **Anchor**: `MainActivity.kt:1391-1393` (`promptAndMint`, `BiometricPrompt.PromptInfo.Builder`
  built from two static string-resource lookups only); `strings.xml:21`
  (`biometric_prompt_title` = "Authorize this presentation"), `:22` (`biometric_prompt_subtitle` =
  "Required only to emit a result — the document has already been read"); `promptAndMint`'s own
  signature already receives `site` and `scopeDomain` as parameters, and its call site at `:1369`
  already has both values in scope — neither is used anywhere in the prompt construction — SHA
  `4969a20`
- **Finding**: the biometric/device-credential authorization prompt is built from two hardcoded
  strings with no reference to which origin, site, or presentation tier the authorization is for —
  `promptAndMint` has `site` and `scopeDomain` sitting in scope as parameters (received at its call
  site, `:1369`) and uses neither. The cryptographic signature IS bound to `scopeDomain`
  (`EvidenceSigner.kt:17-21`, per finding #10), but the human authorization the biometric prompt
  extracts is bound to nothing — a user cannot tell, from the prompt itself, whose request they are
  about to authorize. **Independent of finding #10, and survives every mitigation option #10 lists**:
  even with #10's guard (a), snapshot (b), or `response_uri` binding (d) applied and the intent-
  hijack timing windows fully closed, a user going through the ordinary, single-legitimate-request
  happy path still authorizes blind — the prompt never states the destination regardless of whether
  the destination is genuine or attacker-controlled. #10's own mitigation (c) ("show origin in the
  biometric prompt") is this finding's fix, listed there as an option; recorded here as its own
  entry because it is a defect on its own terms, not merely a partial mitigation for #10.
- **Mitigation option, not applied**: render `site` (the verified origin's host:port, already
  computed by `siteTitleFor` elsewhere in the file) in the prompt subtitle — both values needed are
  already in scope at the call site, so this is close to free. A FIX under D57's gate (adds no new
  UI surface, corrects an existing prompt's content) rather than an enhancement.
- **Status**: OPEN, **consequence HIGH** (consent defect — the user's biometric authorization is not
  meaningfully informed about what it authorizes). Not fixed by `4969a20`. Blocked by D57 pending
  owner ruling on sequencing (same open question as #10: does a contained, low-risk FIX like this
  proceed ahead of the full ownership refactor, or wait for it).
- Status update 2026-09-02: MITIGATED in 730ef09 — the site-named prompt title (`MintPromptText`,
  `strings.xml` `biometric_prompt_title_for_site`); remains OPEN for the ownership fix (lock-time
  snapshot / SessionState); the guard is to be REMOVED when that lands.

### 2026-09-02 — #12: reused `showBlockingOutcomeDialog` for the #10 refusal path would have let a refused foreign intent wipe the legitimate locked session — CLOSED-BY-CONSTRUCTION before commit

- **Source**: orchestrator + coder, 2026-09-02, SHA `730ef09`
- **Anchor**: `MainActivity.kt` ~:905-909 at `4969a20` (now ~:921-925) — `showBlockingOutcomeDialog`'s
  OK handler nulls `pendingHandoff`/`verifiedRequest` and calls `wipeSession(false)` whenever
  `keepMrzAndMode` is false
- **Finding**: the first draft of the #10 mitigation reused `showBlockingOutcomeDialog` for the
  refusal path. That dialog's OK handler unconditionally nulls `pendingHandoff`/`verifiedRequest`
  and wipes the session when `keepMrzAndMode` is false — reusing it for a refused FOREIGN intent
  would have let the user's own OK tap destroy the LEGITIMATE locked session, a one-tap DoS via the
  mitigation itself. Caught by the coder's required survey (found half — the wipe) and the
  orchestrator's source check (found the pointer-nulling half). Fixed before commit by using a
  Snackbar instead (no state transition). This hazard was AVOIDED, not fixed — the underlying
  dialog handler is still the audit finding "guards row 6 / dismissal handler writes five fields."
  Lesson for the briefing rule: grep the findings log for every function a brief tells an agent to
  **CALL**, not only those it tells it to change.
- **Status**: CLOSED-BY-CONSTRUCTION in `730ef09`. **Rule**: no refusal/ignore path may use
  `showBlockingOutcomeDialog`; that dialog's contract is terminal-outcome-with-state-transition.

### 2026-09-02 — #13: unbounded `ReportLog.entries` growth — the #10 refusal path is the first externally-triggerable APPEND, `TransactionTooLargeException` reachable via looped `av://` intents

- **Source**: second-session review of `730ef09`, verified at source by the orchestrator, 2026-09-02, SHA `730ef09`
- **Anchor**: `ReportLog.kt:162` (`entries`, a plain `mutableListOf<String>()` — no max size, eviction,
  or trim anywhere in the file); `MainActivity.kt:573-581` (the #10 refusal path calls `emitReport`
  before returning, which appends an entry); `onSaveInstanceState` (`:533`-equivalent) persists the
  whole list into the Bundle
- **Finding**: a hostile app firing `av://` intents at a locked session in a loop appends to
  `ReportLog.entries` indefinitely — unbounded in-memory list and unbounded Bundle, ending in
  `TransactionTooLargeException` at the next save. Before `730ef09` a foreign intent overwrote two
  fields (O(1) state); the refusal path is the first code that lets an external trigger APPEND. The
  `Log.e` on the same path is likewise remotely drivable but bounded by logcat's ring buffer. CREATED
  BY the #10 mitigation.
- **Options recorded, not applied**: (a) do not `emitReport` on refusal — the Snackbar + `Log.e`
  already inform; a refused foreign intent is not a disclosure event (would reverse the
  owner-approved Result-line string, so owner decision); (b) cap `ReportLog.entries` (touches D45
  "accumulates for the app session" — needs a decision on the bound); (c) both. Owner ruling pending.
- **Status**: OPEN, consequence MEDIUM (crash, not disclosure; needs a hostile app installed).
- Status update 2026-09-02: option (a) applied in 26f67ac — the refusal path no longer calls
  emitReport (Log.e + Snackbar only; the approved Result-line string was deleted with it). The
  unbounded ReportLog.entries itself remains OPEN for the refactor (cap decision pending, touches
  D45).

### 2026-09-02 — #14: `handoffStatus.text` — stale "verified/waiting" status survives a consumed or wiped session, misled the owner into two unintended mode-A scans

- **Source**: owner device run 2026-09-02 09:05-09:07 (pid 13802), diagnosed by the orchestrator
  from logcat + source; SHA `f602e12`
- **Anchor**: `handoffStatus.text` written at `MainActivity.kt:643` (received), `:716` (verified —
  "Handoff verified — origin: … Fill in your document details and lock to answer it."), `:744`
  (refused) and NOWHERE ELSE. A successful mint consumes the session (`mintAndMaybeHandoff` nulls
  `pendingHandoff`/`verifiedRequest`, ~:1681-1682) and the success dialog's OK calls
  `wipeSession(false)`, but the status line is never rewritten, so after a mint the screen still
  says the handoff is verified and waiting while no handoff exists.
- **Finding**: **Classification: projection defect; belongs to D58 step 4 (Session projections
  derived from state), NOT a standalone fix** — adding a fourth writer to `handoffStatus.text`
  would be the pattern the refactor removes. **Observed:** 09:05:42 handoff verified
  (127.0.0.1:8787) → 09:06:15 NL card minted (consumes it) → 09:07:02 and 09:07:14 the owner
  scanned the US passport twice (one deliberate wrong-details BAC AccessDenied SW=0x6982, then a
  correct read) WITHOUT re-opening a link, believing the handoff was still live because the status
  line said so → both scans ran as mode A bare scans (D33: no verified handoff = mode A by
  definition), correctly titled with the no-site label in the Log tab and `mint_gate: NOT MET —
  evidence: [] (D27)`. Owner's report: "US wrong scan and right scan appear as local site header."
  The header was right; the status line above it was stale. **Audit cross-ref:** (a) row for
  `handoffStatus.text` (3 writers, LOW) — consequence should be re-rated MEDIUM: a stale projection
  changed what the user did next. **Structure-proposal/critique note:** `handoffStatus.text`,
  `lockButton.isEnabled`, `modeStatusView.text` are write-only projections that must be DERIVED
  from session state and rendered, not written from three call sites — this is that finding
  manifesting on device. **Related:** Q40 (Lock button reads as stuck after a failure) is the same
  class — a projection nobody rewrites when the state it describes changes.
- **Status**: OPEN, consequence MEDIUM (user misled about which site a scan answers; no wrong data
  sent — the log entries were honest).
