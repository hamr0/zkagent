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

Note, 2026-09-02: the status-change notes added this session (under #4, #5, #6, #8, #10, #11) are
anchored at `c60354e` on `chore/memory-consolidation`, not at the `2cd1e00`/uncommitted-D55/D56
state the sentence above describes — a DOCS reconciliation pass, no code touched.

Note, 2026-09-02 (later): this session's further status-change notes (under #6, #8) and new entry
#17 are anchored at `d4653b9` on `chore/memory-consolidation` — a DOCS tag session with no device
attached; facts about `57f5ddd`/`840779c`/`d4653b9` were verified by the orchestrator from diffs and
JUnit XML, not from a device run.

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
- Status update 2026-09-02: CLOSED by construction in `0d4daf7` (D58 step 2) — the tab index becomes
  app state owned by a new `PaneState` class (`PaneState.kt`), persisted in `onSaveInstanceState`
  and restored in `onCreate` before `showPane()` runs; `showPane()` no longer READS
  `tabLayout.selectedTabPosition` as an input at all, so the framework's own (still-default,
  un-overridden) `onPostCreate` tab restore has nothing left to race — it can land whenever it lands.
  `showPane()` instead DRIVES `tabLayout`'s selection from `PaneState`, behind a re-entry guard
  (`applyingPaneStateToTabLayout`) so that programmatic move is never misread by the tab listener as
  a fresh user tap. Unit tests 151→162 (JUnit XML, 0 failures; new `PaneStateTest`, 11 tests covering
  all four legal states and both restore paths; failing-first demonstrated as 30 unresolved-reference
  compile errors by moving `PaneState.kt` aside). Device-confirmed, Pixel 6a pid 17066, 2026-09-02,
  four cases (Log tab survives recreation with log intact; Scan tab survives recreation; Log-tab
  reselect then recreation survives with no flicker; two back-to-back recreations on Scan tab both
  survive), tab state verified programmatically via a filtered `uiautomator dump` (not a screenshot —
  the scan form renders real MRZ fields, unsafe to snapshot) rather than by eye. See
  `docs/logs/M2-D58-STEP2-EVIDENCE.md`.

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
- Status update 2026-09-02: CLOSED BY CONSTRUCTION in `65096b9` (D58 step 3) — a new immutable
  `data class AuthorizedHandoff(request, origin, site)` is captured EXACTLY ONCE, on the main thread,
  in `lockModeAndArm`, and threaded as a parameter through `startSession`, `ReadTask`,
  `continueAfterRead`, `promptAndMint`, and `mintAndMaybeHandoff`. Both cross-thread read sites named
  above (`continueAfterRead`'s `Thread{}` at the old `:1281-1282`, `mintAndMaybeHandoff`'s independent
  re-read at the old `:1470-1471`) are DELETED — no background code reads `pendingHandoff` or
  `verifiedRequest` any more. The three reads that remain are all main-thread (`refreshModeStatus`,
  `lockModeAndArm` itself, and `applyHandoffVerificationOutcome`'s staleness check, posted via
  `runOnUiThread`), so the non-`@Volatile`/cross-thread half of this finding no longer applies — there
  is no happens-before gap left to close, because there is no cross-thread read left. No staleness
  guard or generation counter was added, by construction: a later `beginHandoffVerification` call can
  still overwrite the mutable fields for the NEXT attempt, but it cannot reach back and change what an
  already-constructed `AuthorizedHandoff` carries — a `val` cannot be reassigned, and the class has no
  setter (a reflection test in the new `AuthorizedHandoffTest` fails if any field ever becomes `var`).
  **Verified explicitly, per this file's own rule against taking a `!!` on trust**: the coder replaced
  the old defensive null-check with `val authorized = snapshot!!` at the NFC-tag call site. The
  orchestrator confirmed this cannot throw, via two independent gates in sequence rather than by
  argument alone — mode A returns early at the `MintGate` check (`MintGate.mayMint` is
  `modeIsB && verdict.ok && verdict.allowed == true`, i.e. `false` whenever `modeIsB` is `false`;
  `MintGateTest` carries a test literally named `` `mode A NEVER mints, even with ok true allowed
  true` ``), and mode B with a null snapshot is refused earlier still by the pre-existing D38 origin
  guard. Unit tests 162 → 168 (JUnit XML, 0 failures); new `AuthorizedHandoffTest` adds the
  supersession regression test (a second `beginHandoffVerification` call after lock time does not
  alter an already-captured snapshot) plus the reflection check above. **`HandoffAdmission` (finding
  #10's mitigation) was KEPT, not removed, contrary to D58's stated expectation** — see #10's own
  status update below for why; this finding's own closure (mint correctness against the snapshot) does
  not depend on that guard staying or going. Device-confirmed indirectly on the Pixel 6a, 2026-09-02
  (two sessions, real NL ID card): every mint observed landed against the transaction the locked
  session actually authorized, including a case where the orchestrator itself briefly mis-tracked
  which transaction would complete — the device's own report said PASS, and only the verifier's
  independent pending/done state distinguished the superseded transaction from the one actually
  minted (see finding #10's status update and `docs/logs/M2-D58-STEP3-EVIDENCE.md` for the full
  device narrative; this run exercises the guard/refusal path primarily, not a targeted #2/#3
  cross-thread-corruption repro, so treat it as corroborating rather than dispositive for this
  specific finding). See `docs/logs/M2-D58-STEP3-EVIDENCE.md`.

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
- Status update 2026-09-02 (D58 step 2, traced by the coder as a report-only deliverable, no fix,
  verified at source by the orchestrator): the open question is narrowed, not closed. The lock-guard
  half is now ANSWERED — the real guard, `lockModeAndArm`, checks only that `lockedMode` is null and
  that the three MRZ fields are non-empty; it never reads `lastMrzHash` at all. So after a rotation,
  `lockedMode` correctly comes back null and a Lock tap is treated as a fresh lock over the
  framework-restored MRZ text — this behaves correctly, by omission rather than by design.
  `lastMrzHash` is consulted in exactly one place, the NFC-tag branch of `handleIncomingIntent`,
  feeding only `MrzChangeTracker`'s value-free `Log.i` diagnostic — it is never a gate. Consequence:
  after a rotation, `lastMrzHash` is null, so the next scan's comparison reports "first attempt" even
  when it is genuinely a later attempt in the same user session — a logcat mislabel only, no effect
  on gating, security, or user-facing state. What remains OPEN: the `lastMrzHash` diagnostic mislabel
  itself, plus the other ten of the eleven lost fields this finding names, none of which this step
  touched. Belongs with `lastMrzHash`/`SessionState` in a later D58 step. Finding NOT closed.
- Status update 2026-09-02: unaffected this session, still OPEN — the `lastMrzHash` diagnostic
  mislabel and the other ten lost fields.
- Status update 2026-09-02 (D63, owner ruling): `AndroidManifest.xml` `screenOrientation` locks
  portrait (was `fullSensor`), landing in a follow-up commit on this branch. This closes the
  ROTATION vector only — the framework-restore/lost-field mechanics this finding describes still
  apply to non-rotation recreation (font-scale change, locale change, low-memory process death).
  The `lastMrzHash` diagnostic mislabel and the other ten lost fields remain OPEN, carried forward
  to the next module's `SessionState` design item, not fixed by D63.

### 2026-09-02 — #5: zero async-cancellation discipline

- **Source**: audit (§(e2), call graph §)
- **Anchor**: five unfenced `Thread{}` sites — `MainActivity.kt:306`, `:310` (both release-reachable,
  land in `emitReport` → `reportLog.append` → persisted `:533`), `:611`, `:1262`, `:1414` @ `2cd1e00`
- **Finding**: no `onDestroy`/cancellation/status check exists for any of the five `Thread{}` launch
  sites; each runs to completion (or crash) regardless of Activity lifecycle state, and at least two
  (`:306`, `:310`) can write into the persisted report log after the Activity that launched them is
  no longer the foreground Activity.
- **Status**: OPEN. Named directly by the freeze's "every async writer fenced" exit criterion.
- Status update 2026-09-02: partially bears on this — `c856f42` (D58 step 1) bounds the landing site
  (`ReportLog.MAX_ENTRIES = 200`, see #13), so a late `emitReport`/`reportLog.append` from one of
  these unfenced threads after the Activity has moved on now lands on a bounded structure instead of
  an unbounded one (harmless to this cluster's worst-case size). The underlying defect — no
  `onDestroy`/cancellation/status check at any of the five `Thread{}` sites, so each still runs to
  completion regardless of Activity lifecycle — is untouched by step 1 and remains OPEN.
- Status update 2026-09-02 (D58 step 4, `c38833d`): unaffected, still OPEN — this step closed findings
  #9/#14/#15 (display projections) and left #10/#11 mitigated-not-closed (guard kept by
  recommendation); #4 and #6 are also still OPEN, untouched. With #9/#13/#14/#15 now closed and #10/#11
  mitigated, this finding — zero async-cancellation discipline at all five `Thread{}` sites — is now
  **the principal remaining blocker on D57's exit criterion** (criterion (2): "every async writer is
  fenced against Activity lifecycle"). No fix or further tracing this step; named here so the next
  spawn touching any of the five `Thread{}` sites inherits this status rather than treating it as one
  finding among several equally-open ones.
- Status update 2026-09-02 (FIX, uncommitted at time of writing — new `LifecycleFence.kt`, new
  `LifecycleFenceTest.kt`, modified `MainActivity.kt`): **FIXED, device-proven.** A pure
  `LifecycleFence` class (`alive` flag, `retire()`, `passes()`) held as a per-Activity-instance field
  (`MainActivity.kt:265`), retired in a new `onDestroy` (`:439-442`, the module's first-ever
  `onDestroy`); 11 sites fenced (10 `runOnUiThread` blocks plus `ReadTask.onPostExecute`, an
  `AsyncTask` the owner explicitly put in scope — not one of the five originally-named `Thread{}`
  sites, same defect class). Owner-decided semantics: a fence drops a main-thread landing, it never
  cancels or aborts in-flight work. A logged line at every drop plus retirement (12 static,
  zero-interpolation messages; `:2131` is the only `Log.w`). Unit tests 180→184 (new
  `LifecycleFenceTest`, 4 cases including cross-instance independence), 0 failures, JUnit XML.
  Orchestrator-verified at source: per-instance not singleton; all 11 guards present;
  `applySessionDisplay` still the sole writer of the four `SessionDisplay` view properties; D55
  ordering intact; diff 128 insertions / 4 deletions, every non-comment added line is fence plumbing
  or a log line. **Device-proven on the Pixel 6a (real NL ID card) across six tests** — see
  `docs/logs/M2-FENCE-EVIDENCE.md`: T2 proved per-instance construction (a property no unit test
  can prove) via two in-process recreations then a clean mint on the third instance with zero
  `fence closed` lines; T3 dropped a mid-verification landing without affecting the next instance;
  T4 dropped a mid-read landing with no mint and no stranded UI (D55 invariant held); T5 dropped a
  post-mint landing AFTER a real `direct_post` 200 and verifier tier-B verdict — evidence left the
  device with nothing recorded or shown on the phone beyond the one `Log.w` line (see new finding
  #16). **D57 exit criterion (2) is now MET. The freeze does NOT lift** — criterion (3) is not met;
  findings #10/#11 remain open-but-mitigated. T3/T5's drop windows were only reachable via an
  artificial test-harness delay proxy (natural windows ~12-28ms on localhost); the QR-scan/
  manual-paste path was not exercised under fence conditions this session.

- Status update 2026-09-02 (correction, commit `72e0b2c`, following a `/branch-review` finding over
  the 44-commit branch): **the prior status-change note's completeness claim was WRONG in scope.**
  That note (and PRD D57's matching annotation) said "11 sites fenced," full stop — implying
  exhaustive coverage of every main-thread landing reachable after Activity destruction. It was not
  exhaustive: it was a syntactic enumeration (`grep`-shaped — every `runOnUiThread` call plus
  `ReadTask.onPostExecute`), not an enumeration by the actual hazard predicate. A
  `BiometricPrompt.AuthenticationCallback` is dispatched on the main executor and is a main-thread
  landing in exactly the same sense as the 11 sites already fenced, but it does not say
  `runOnUiThread` anywhere, so it was missed. `onAuthenticationError` called `emitReport` then
  `showBlockingOutcomeDialog`, which does `AlertDialog.Builder(this).show()` against the Activity —
  a real landing, not a hypothetical one.
- **Why it was reachable, bytecode-verified by the orchestrator in `androidx.biometric` 1.1.0, not
  assumed** (worth recording precisely — it is counter-intuitive):
  `BiometricPrompt$ResetCallbackObserver.resetCallback()` is annotated `@OnLifecycleEvent(ON_DESTROY)`
  and nulls `BiometricViewModel.mClientCallback`, after which `getClientCallback()` substitutes a
  no-op default — so a destroyed host is *normally* never called back. BUT `addObservers()` is
  invoked ONLY from the library's two `Fragment` constructors; both `FragmentActivity` constructors
  call neither `addObservers` nor `getLifecycle`. `MainActivity` is an `AppCompatActivity` (`:186`)
  and uses the `FragmentActivity` overload (`:1779`), so that protection is NOT active here — "androidx
  handles lifecycle" is a wrong but very reachable conclusion, one constructor overload away from
  being true.
- **Fix, commit `72e0b2c`**: the same guard as the other 11 sites added to `onAuthenticationError`,
  plus `onAuthenticationFailed` for defence-in-depth (placed AFTER its existing diagnostic log line
  so it never suppresses it, so future UI work appended there inherits the fence).
  `onAuthenticationSucceeded` unchanged — it only starts a `Thread{}` whose landings are already
  fenced. Both new messages static, zero interpolation, `Log.i` not `Log.w` (nothing minted, nothing
  left the device on these paths, unlike `:2131`). **13 fenced sites now, not 11.** Unit tests 184/0
  unchanged, verified by the orchestrator on a clean `--rerun-tasks` build, exit 0. Not
  unit-testable (Activity-resident callback, module runs `isReturnDefaultValues=true`) — settling it
  needs a device test that destroys the Activity while a biometric prompt is outstanding, confirming
  the landing is dropped with no `WindowManager$BadTokenException`; not yet run.
- **Sweep result**: the coder swept every other non-`runOnUiThread` main-thread landing in
  `MainActivity.kt` by the correct predicate — "a callback the framework may deliver late that
  touches Activity-owned UI or state" — not by text match, and found NO further gaps: click/editor/
  long-click listeners (alive by construction — an input event requires an attached, foregrounded
  view), `registerForActivityResult`/`qrCaptureLauncher` (AndroidX `ActivityResultRegistry` is
  lifecycle-aware, unlike the `BiometricPrompt` path), the `DatePickerDialog` `DialogFragment` (added
  via the normal `FragmentManager`, torn down with its host), NFC tag handling via `onNewIntent`
  (synchronous, resumed-only), and `DeviceKey.exportDevAttesterPublicKeyIfPresent` (verified at
  source: writes only to `openFileOutput`/`Log`, no Activity landing exists to fence).
- **Criterion (2) status, stated precisely so the record does not read as continuously true**:
  criterion (2) ("every async writer is fenced against Activity lifecycle") **was NOT actually met
  between `b8e0e05` and `72e0b2c`** — the prior note's "MET" claim held only from `72e0b2c` onward.
  It **is MET now**, at 13 sites. **The freeze does NOT lift** — criterion (3) is still not met;
  findings #10/#11 remain OPEN, mitigated not closed.
- **Recurrence-prevention lesson**: enumerate async landings by the predicate — a callback the
  framework may deliver late that touches Activity-owned UI or state — never by grepping for a
  syntactic form such as `runOnUiThread`; the form is an implementation detail of some landings, not
  a definition of the hazard class.
- Status update 2026-09-02 (`e13dab0`): the `.claude/remember/fix-ledger.md` doc-drift bullet the
  `9584bc8` re-review raised against `LifecycleFence.kt`'s KDoc (the thread-safety proof enumerating
  two syntactic forms as exhaustive, contradicting this finding's own recurrence-prevention lesson)
  is fixed — the KDoc now states the real invariant (every `passes()` read is a main-thread landing),
  lists the three forms observed (10 `runOnUiThread`, 1 `onPostExecute`, 2 main-executor
  `BiometricPrompt` callbacks = 13 sites) as observed-not-exhaustive, and names the hazard predicate
  as the criterion for new sites. `fix-ledger` is now cleared (0 bullets). This is a doc-drift fix
  only, not new coverage — the `72e0b2c` BiometricPrompt fence guard itself remains without device
  evidence, unchanged this session (no device attached).

### 2026-09-02 — #6: `handleIncomingIntent` tag guard ignores `readInProgress`

- **Source**: audit (State join §, Guards §)
- **Anchor**: `MainActivity.kt:539,560-566` (guard reads `lockedMode` + MRZ non-empty); `readInProgress`
  flag at `:920`/`:1075` (never consulted by this guard) @ `2cd1e00`
- **Finding**: the guard deciding whether an incoming NFC tag intent should be processed checks
  `lockedMode` and that the MRZ fields are non-empty, but never checks `readInProgress` — a tag
  intent arriving while a read is already in progress is not excluded by this guard on the evidence
  read.
- **Status**: OPEN.
- Status update 2026-09-02: unaffected by D58 step 2 (`0d4daf7`) — the `readInProgress` flag this
  finding names moved ownership from a bare `MainActivity` field to `paneState.readInProgress` (both
  its writers, `startSession` and `ReadTask.onPostExecute`, are read-lifecycle-only, per that step's
  required survey). The only call site this step updated to the new location is
  `HandoffAdmission.mayAdmitInboundHandoff` (the `av://` guard, finding #10's mitigation) — a
  mechanical read-site update, semantics unchanged. `handleIncomingIntent`'s NFC-tag branch, the
  guard this finding is actually about, still never consults `readInProgress` under either name.
  The finding itself is unchanged and remains OPEN.
- Status update 2026-09-02 (FIX, `c60354e`): **FIXED-IN-c60354e.** A new pure predicate
  `HandoffAdmission.mayStartTagRead(sessionLocked, readInProgress) = sessionLocked &&
  !readInProgress` (opposite polarity to `mayAdmitInboundHandoff`, kept in the same object because
  both gate the same two fields for the two intent branches of one function), 4 new truth-table
  tests in `HandoffAdmissionTest.kt`. Wired into the NFC `ACTION_TECH_DISCOVERED` branch of
  `handleIncomingIntent`, after the existing `lockedMode == null` check (its log text unchanged)
  and before the MRZ snapshot and the `MrzChangeTracker` diagnostic, so a refused tap does not
  disturb `lastMrzHash`. Refusal shape mirrors the `av://` path: static value-free
  `Log.w("M2 stage: ignoring tag intent — a read is already in progress")` + `Snackbar` (new
  constant `TAG_REFUSED_MID_READ_MESSAGE = "Ignored a tag that arrived mid-read."`, LENGTH_SHORT) +
  return; no `reportLog` append (finding #13's rule), no `showBlockingOutcomeDialog` (finding #12's
  rule), no state assignment. Tests 204 → 208, 0 failures. **Coverage, stated precisely**: the guard
  is unit-tested at the predicate level (the 4-case truth table) and source-verified at the call
  site only — no runtime/device confirmation that the wiring actually fires as traced. **Residuals**:
  (a) the Snackbar wording is PROPOSED, pending owner approval, same convention as
  `HANDOFF_REFUSED_MID_SESSION_MESSAGE`; (b) NO device evidence — no device was attached this
  session; a device test would lock a session, tap to start a read, then re-tap or fire a synthetic
  `ACTION_TECH_DISCOVERED` intent mid-read and confirm the new log line plus Snackbar with no second
  `startSession`.
- Status update 2026-09-02 (`57f5ddd`, DOCS session, no device attached): the mid-read Snackbar and
  its `TAG_REFUSED_MID_READ_MESSAGE` constant were **removed** from the NFC branch of
  `handleIncomingIntent`, by owner decision — a refused mid-read tag is normally the same physical
  card the user is already holding, so no user-facing message is needed. The static
  `Log.w("M2 stage: ignoring tag intent — a read is already in progress")` and the
  `HandoffAdmission.mayStartTagRead` gate itself are UNCHANGED and still fire. Tests 208, 0 failures
  (message-only removal, no new test surface). This retires residual (a) above (Snackbar wording is
  moot now that there is no Snackbar). Residual (b) — no device evidence for the guard firing — still
  stands; still no device attached this session either.
- Status update 2026-09-03 (device session, `docs/logs/M2-DEVICE-SESSION-2026-09-03-EVIDENCE.md`
  check 3): residual (b) is resolved, but not by a device confirmation — the mid-read branch of
  `HandoffAdmission.mayStartTagRead` is **NOT reachable on device with real cards**. A same-card
  re-tap ~1s into a read produced no second NFC discovery at all: the in-flight read failed with
  `Tag was lost` before the presence-check's own "Tag lost, restarting polling loop" line appeared,
  meaning the NFC stack serialises tag sessions and the read fails first. A synthetic
  `ACTION_TECH_DISCOVERED` intent cannot reach the gate either, since that branch requires a real
  `Tag`/`IsoDep` parcelable that only a genuine discovery produces. The gate's mid-read path
  therefore stays proven by the 4-case unit truth table and the source wiring trace only — it is
  defence-in-depth against a state this platform already prevents, not a device-provable path. The
  fix itself (`c60354e`) is unaffected and remains FIXED — this update is scoped to residual (b)'s
  device-coverage claim only.

### 2026-09-02 — #7: `reportView.text` written outside `emitReport`, contradicting its own KDoc

- **Source**: audit (§(a) table, cross-referenced against `emitReport`'s own KDoc)
- **Anchor**: write site `MainActivity.kt:288`; `emitReport`'s KDoc claiming sole-writer status at
  `:753-758` (see also `emitReport`'s body at `:769`, `reportLog.append` call) @ `2cd1e00`
- **Finding**: `reportView.text` is written directly at `:288`, outside `emitReport`, while
  `emitReport`'s own KDoc (`:753-758`) asserts it is "the ONE place a report is ever rendered to
  `reportView`" — the comment is contradicted by a second write site three hundred-plus lines away.
- **Status**: OPEN. Same class as the D45/2026-08-31 stall this project has already hit once
  (`reportView.text = ...` with no `Log.i`) — worth prioritizing given the precedent.
- Status update 2026-09-02: CLOSED by construction in `c856f42` (D58 step 1) — `ReportLog` now owns
  `lastReportText`; `MainActivity.emitReport` and its new named sibling `restoreReport` are the only
  renderers, both routed through `ReportLog.append`/`ReportLog.restore`. Unit tests 145→151 (JUnit
  XML, 0 failures; `ReportLogTest` adds lastText/bound/eviction/restore-round-trip, failing-first
  demonstrated as 13 compile errors before the production change). Device-confirmed, Pixel 6a pid
  15939, 2026-09-02: `M2 stage: restored report/log across Activity recreation (text=true,
  log_entries=3)` logged twice (10:31:03.189, 10:31:07.286) across a forced config-change recreation
  (auto-rotate is off on this device; recreation forced via `adb shell settings put system
  font_scale 1.15`/`1.0` with the app foregrounded), all three prior scan entries intact. See
  `docs/logs/M2-D58-STEP1-EVIDENCE.md`.

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
- Status update 2026-09-02 (FIX, `651ecd5`): **FIXED-IN-651ecd5.** A new pure `ChipAuthClassification`
  object (`fromDg14`, `combine`, `label`, `technical`) replaces the inline DG14 decision, the CA+AA
  combine rule, and both owner-approved D53 string mappings previously inline in `MainActivity` — I/O
  stays inline; only the decision moved; the rendered strings are byte-identical to before. New
  `ChipAuthClassificationTest.kt`: 20 tests — a hand-written 3×3 combine truth table, 4 DG14 cases,
  an assertion that NOT_SUPPORTED never contains "false"/"Not verified", an assertion that FAILED
  contains "Not verified", and a check that the three labels are distinct. Tests 184 → 204, 0
  failures. **Remaining gap, INSIDE this same finding (narrower, not a new numbered finding)**:
  noticed but not changed this session — `M0Probe.tryActiveAuth` (`M0Probe.kt` ~:238) still holds
  its own separate inline VERIFIED/NOT_SUPPORTED/FAILED decision, with no direct unit test of its
  own. This is the second half of the same underlying defect (`M0Probe`'s AA-only three-state logic
  never got the same extraction `ReadTask`'s DG14/CA+AA logic just did) and belongs to whatever spawn
  next touches `M0Probe.kt`.
- Status update 2026-09-02 (FIX, `840779c`, DOCS-session-adjacent code fix, no device attached):
  **remaining gap CLOSED, finding fully FIXED-IN-651ecd5+840779c.** `M0Probe.tryActiveAuth`'s
  three-state decision (DG15 absent → NOT_SUPPORTED; present and doAA verified → VERIFIED; present
  and failed/threw → FAILED) extracted to a new pure `ChipAuthClassification.fromActiveAuth(...)`;
  JMRTD I/O stays inline in `M0Probe`; detail strings byte-identical to before. 5 new hand-written
  tests added TDD-style (compile-error red confirmed first, then green). Tests 208 → 213, 0 failures.
  No device evidence for this change; none was available this session.

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
- Status update 2026-09-02 (D58 step 4, `c38833d`): **CLOSED**. `lockButton.isEnabled` (alongside
  `modeStatusView.text` and `handoffStatus.text`) is now one of four properties written EXCLUSIVELY by
  `MainActivity.applySessionDisplay`, itself the sole caller-side consumer of the new pure
  `SessionDisplay.render(lockedMode, handoffState)` function — orchestrator verified only
  `MainActivity.kt:430-433` assign these four properties; the four prior write sites for
  `lockButton.isEnabled` named by this finding are collapsed into that one applier and
  `refreshModeStatus` (one of the four) is deleted outright. Closes by construction, same mechanism as
  #14/#15 below — locked state takes unconditional precedence in `render`, so the join with the
  handoff cluster (#2/#3) this finding named can no longer produce a fifth uncoordinated writer. Unit
  tests 168→180 (JUnit XML, 0 failures), new `SessionDisplayTest` (12 tests) covers all six legal
  locked×handoff-state projections including two "locked wins" cases. See
  `docs/logs/M2-D58-STEP4-EVIDENCE.md`.

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
- Status update 2026-09-02 (D58 step 3, `65096b9`): still **MITIGATED, NOT CLOSED**. The
  MINT-CORRECTNESS half of this finding (evidence signed against a superseded request) is now closed
  by construction — see finding #2/#3's own status update, same commit, for the `AuthorizedHandoff`
  lock-time snapshot. `HandoffAdmission` was explicitly **KEPT, not removed, contrary to D58's
  verbatim expectation** that it "becomes redundant and be removed" once the snapshot lands. Reason,
  from the coder's trace, verified at source by the orchestrator: `applyHandoffVerificationOutcome`
  (the async verify callback `beginHandoffVerification` schedules) unconditionally calls
  `refreshModeStatus()` and sets `lockButton.isEnabled = true` on a successful verify, while
  `refreshModeStatus`'s own doc says it must never run while `lockedMode` is set — nothing in code
  enforces that except this guard refusing the foreign intent before `beginHandoffVerification` ever
  runs. Removing the guard today would let an admitted foreign intent overwrite the "Locked: mode X"
  banner with the attacker's "verifying…"/tier text (inert for re-locking — `lockedMode != null`
  still no-ops a second Lock tap — but a real user-visible display regression). **Guard removal is
  now explicitly blocked on `applyHandoffVerificationOutcome` respecting `lockedMode`** — that is D58
  step 4 (`modeStatusView`/`lockButton.isEnabled` are named on step 3's own MUST NOT list). Device
  evidence, two sessions on the Pixel 6a, real NL ID card, 2026-09-02: the guard correctly REFUSED
  four separate hostile `av://` transactions fired from the same local verifier origin across three
  distinct windows (post-lock/pre-read, mid-read via a logcat-triggered watcher landing 60ms after
  the card tap, and one first attempt that missed the window entirely because the read/mint finished
  before the hostile fire, so the subsequent fire was legitimately ACCEPTED as a new handoff — a
  missed window, not a bypass); one refusal was independently confirmed user-visible (owner reported
  seeing the Snackbar mid-scan, not only the log line); the owner's own legitimate mints in both
  sessions completed correctly against the transaction actually locked, confirmed against the
  verifier's own independent pending/done state rather than the device's self-report alone (see the
  cross-check lesson in `docs/logs/M2-D58-STEP3-EVIDENCE.md`). **Stated limitations, carried forward,
  not closed by this run**: all hostile links originated from the SAME local verifier origin (a
  genuinely foreign origin is still untested); the PIN-prompt window specifically was not tested in
  isolation (the mid-read refusal happened before the prompt appeared); the QR-scan/manual-paste
  handoff path has NO gate at all (see new finding #15, same class, asymmetric coverage). See
  `docs/logs/M2-D58-STEP3-EVIDENCE.md`.
- Status update 2026-09-02 (D58 step 4, `c38833d`): still **MITIGATED, NOT CLOSED — guard KEPT, by
  RECOMMENDATION, not by a still-live code dependency.** The specific reason step 3 gave for keeping
  `HandoffAdmission` — `applyHandoffVerificationOutcome` rewriting `modeStatusView`/`lockButton`
  without respecting `lockedMode` — is now CLOSED: `SessionDisplay.render` puts locked state in
  unconditional precedence over any handoff state, so an admitted foreign verification cannot alter a
  locked session's display even with the guard removed (this step's own commit message states this
  explicitly, and `SessionDisplayTest` has two dedicated "locked wins" tests). **What the guard still
  buys, precisely**: preventing a foreign handoff from overwriting the mutable
  `pendingHandoff`/`verifiedRequest` fields while `lockedMode != null` or a read is in progress — field
  overwrite only. **What no longer depends on it**: mint correctness (closed by step 3's
  `AuthorizedHandoff` snapshot) and display corruption (closed by this step's `SessionDisplay`
  projection). The coder RECOMMENDS keeping the guard anyway because the field-overwrite value has no
  test coverage of its own and removing it was not this step's job; the owner has not ruled on removal.
  No new device evidence against a genuinely foreign origin this step. See
  `docs/logs/M2-D58-STEP4-EVIDENCE.md`.
- Status update 2026-09-02 (RECONCILIATION, no status change, no fix this session): what remains,
  precisely, for this entry to move from MITIGATED to CLOSED, derived only from the status text
  already in this file: (1) an owner ruling on `HandoffAdmission`'s fate — keep it as a tested
  first-class gate, or remove it now that mint correctness (#2/#3) and display correctness (#9) are
  both closed by construction and the guard is kept only by recommendation, not by any remaining
  code dependency; (2) the still-untested cases this file already names — a genuinely foreign origin
  (every device test so far fired hostile links from the same local verifier origin), and the
  PIN-prompt window specifically in isolation (the mid-read refusal observed happened before the
  prompt appeared). Pending owner ruling.
- **Status update 2026-09-02 (D61, owner ruling): CLOSED BY CONSTRUCTION.** The owner ruling this
  entry's prior update was pending now resolves item (1) above: `HandoffAdmission` is permanent
  policy, not a kept-by-recommendation stopgap. Owner: "#10 ok." Item (2) — device proof against a
  genuinely foreign origin (`127.0.0.1:18787` firing mid-scan) — is recorded as pending device
  evidence owed, not as a condition of this closure. See `decisions.md` D61.
- **Status update 2026-09-03 (device session,
  `docs/logs/M2-DEVICE-SESSION-2026-09-03-EVIDENCE.md` check 6): item (2) above is now CLOSED.**
  Two runs fired a hostile `av://` link from `127.0.0.1:18787` — a genuinely distinct second local
  origin, delivered via `am start … --activity-single-top` from the host shell (a second process
  hitting the exported `singleTop` activity). Both were refused
  (`av:// handoff REFUSED — session locked or read in progress (D57 mitigation for finding #10)`).
  Run 2's legitimate read/mint completed normally afterward and was cross-checked against both
  verifiers independently: `127.0.0.1:8787` (the legitimate origin) recorded `ok=true allowed=true
  attester=matched`; `127.0.0.1:18787` (the hostile origin) shows both its transactions still
  `pending`, never receiving evidence. **Finding #10 is now device-proven against a genuinely
  foreign origin, with no remaining open item under this entry.**

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
- Status update 2026-09-02 (D58 step 3, `65096b9`): still **MITIGATED, NOT CLOSED** — same status as
  #10, which this finding is independent of but shares a guard with. The lock-time `AuthorizedHandoff`
  snapshot (finding #2/#3's status update, same commit) closes the mint-correctness question this
  finding is not about; the site-named prompt title mitigation from `730ef09` is untouched by this
  step. `HandoffAdmission` was explicitly **KEPT, not removed, contrary to D58's expectation** —
  see #10's status update for the full reason (`applyHandoffVerificationOutcome` re-enabling
  `lockButton`/rewriting `modeStatusView` without respecting `lockedMode`). **Guard removal is now
  explicitly blocked on `applyHandoffVerificationOutcome` respecting `lockedMode`** — D58 step 4. No
  new device evidence specific to the biometric prompt's content was gathered this session.
- Status update 2026-09-02 (D58 step 4, `c38833d`): still **MITIGATED, NOT CLOSED — guard KEPT, by
  RECOMMENDATION**, same status change and same reasoning as #10's own status update for this commit —
  this finding shares the guard with #10 and is independent of it only in what it is about (consent
  content, not mint correctness or display), not in what closes or keeps its mitigation. The
  `applyHandoffVerificationOutcome`/`lockedMode` display defect that blocked guard removal is now
  closed by `SessionDisplay`'s locked-wins precedence. **What the guard still buys**: preventing field
  overwrite of `pendingHandoff`/`verifiedRequest` while locked or reading — unrelated to this finding's
  own subject (the biometric prompt's content), which remains mitigated only by the site-named prompt
  title from `730ef09`, untouched by this step. No new device evidence specific to the biometric
  prompt's content was gathered this step either. See `docs/logs/M2-D58-STEP4-EVIDENCE.md`.
- Status update 2026-09-02 (RECONCILIATION, no status change, no fix this session): what remains,
  precisely, for this entry to move from MITIGATED to CLOSED, derived only from the status text
  already in this file: the mitigation option the entry itself already named — the site-named prompt
  title (`730ef09`, `MintPromptText`/`biometric_prompt_title_for_site`) — IS the fix that was
  applied; what remains is (1) an owner ruling that the site-named title actually satisfies the
  consent requirement this finding raised, and (2) device evidence specific to the prompt's content,
  which this file states was never gathered ("No new device evidence specific to the biometric
  prompt's content was gathered this session" / "this step either"). Pending owner ruling.
- **Status update 2026-09-02 (D62, owner ruling): CLOSED.** Item (1) above is resolved: the
  site-named `BiometricPrompt` title is accepted as the fix. Owner confirmed it on device by eye:
  "it did work, confirmed." See `decisions.md` D62.
- Status update 2026-09-03: n/a — the 2026-09-03 device session
  (`docs/logs/M2-DEVICE-SESSION-2026-09-03-EVIDENCE.md`) did not re-examine the prompt's content; D62
  already closed this entry by owner eye-confirmation and nothing this session touches it.

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
- Status update 2026-09-02: FIXED-PROVISIONAL in `c856f42` (D58 step 1) — `ReportLog.entries` is now
  bounded at `MAX_ENTRIES = 200` (named constant), oldest-first eviction, `ReportLog.append` the
  sole writer. The number is PROVISIONAL pending owner approval: sized from rendered-fixture
  estimates (400-900 bytes/entry, ~100-180 KB at cap vs. the ~1 MB Bundle/binder transaction limit),
  not measured on device. Unit tests cover eviction and the restore round-trip (145→151, JUnit XML).
  See `docs/logs/M2-D58-STEP1-EVIDENCE.md`.
- Status update 2026-09-02: **FIXED** — no longer provisional. D59 (owner decision) set the
  owner-approved unit and value: the cap counts ENTRIES, not lines (one entry is a whole scan-outcome
  block of roughly 20 rendered lines), and the approved value is **20**, oldest-first eviction
  unchanged. That constant change landed in commit `ff15629` (`ReportLog.MAX_ENTRIES = 200 → 20`),
  replacing the PROVISIONAL 200 from `c856f42`. Both bound tests already referenced `MAX_ENTRIES`
  symbolically rather than by literal, so they scaled to the new value untouched — 168/0/0, unchanged.

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
- Status update 2026-09-02 (D58 step 4, `c38833d`): **CLOSED BY CONSTRUCTION**, exactly per this
  entry's own classification above (a D58 step 4 projection defect, not a standalone fix). The mint
  completion path now re-derives the whole `SessionDisplay` projection from `HandoffState`
  (`None`/`Verifying`/`Verified`/`Refused`) rather than leaving a prior write in place: a consumed
  session (`mintAndMaybeHandoff` nulls the handoff fields) or a wiped one re-renders through
  `applySessionDisplay`, so `handoffStatus.text` cannot remain "verified/waiting" once nothing is
  verified or waiting — the exact scenario this finding's device evidence captured (09:06:15 mint,
  then two mode-A scans the owner mistook for mode B) is now structurally excluded rather than merely
  patched at a fourth call site. `SessionDisplayTest` includes a test named for this finding's exact
  defect. Device-level confirmation this session (2026-09-02, pid 21642): a scan on an already-
  consumed session correctly logged `mint_gate: NOT MET — evidence: [] (D27)` / `verdict: PASS
  (read)` — the data-level behaviour finding #14 named is confirmed not to recur. **Caveat, stated
  plainly**: the on-screen `handoffStatus.text` line itself was NOT visually re-checked this session
  (the owner was on the Log tab, where that view does not render, during the confirming runs) — this
  finding is confirmed at the log/behaviour level, not re-verified by eye on screen. See
  `docs/logs/M2-D58-STEP4-EVIDENCE.md`.

### 2026-09-02 — #15: `applyPendingHandoffText` (QR-scan/manual-paste handoff path) has NO `HandoffAdmission` gate at all — only the `av://` intent path is guarded

- **Source**: D58 step 3 coder's required survey (touching `beginHandoffVerification`'s call sites),
  verified at source by the orchestrator, 2026-09-02, SHA `65096b9`
- **Anchor**: `MainActivity.kt:717-725` (`applyPendingHandoffText` — parses pasted text or a scanned
  QR payload via `HandoffClient.parsePastedText`, then calls `beginHandoffVerification(handoff)`
  unconditionally on any successful parse); its two call sites at `:288` (manual-paste button handler)
  and `:392` (post-QR-scan callback); contrast `:666` (the `av://` intent branch of
  `handleIncomingIntent`), which gates the identical call through
  `HandoffAdmission.mayAdmitInboundHandoff(sessionLocked = lockedMode != null, readInProgress =
  paneState.readInProgress)` before ever calling `beginHandoffVerification` — `applyPendingHandoffText`
  has no equivalent check anywhere in its body
- **Finding**: `applyPendingHandoffText` calls `beginHandoffVerification` with **no `HandoffAdmission`
  gate of any kind** — neither `lockedMode` nor `paneState.readInProgress` is consulted before it
  unconditionally overwrites `pendingHandoff`/`verifiedRequest`, exactly the call finding #10's
  mitigation exists to guard on the `av://` path. This is the SAME class of defect as #10 (an
  unguarded capture site for the handoff-verification pipeline), and the guard's coverage is
  **asymmetric**: only the `av://` intent path is admission-gated; the QR-scan/manual-paste path is
  not. PRE-EXISTING (not introduced by D58 step 3) — found by the step-3 coder while surveying every
  call site of `beginHandoffVerification` as that step required, not by this step's own change.
  **Consequence, stated as lower than #10's**: exploiting this path requires the DEVICE'S OWN USER to
  paste text or scan a QR code (no on-device attacker app can drive it the way `av://` intents can be
  fired from any installed app with no permission), so the practical attacker model is narrower — but
  the underlying race (a foreign handoff admitted mid-lock/mid-read, landing on the same
  `pendingHandoff`/`verifiedRequest` fields) is otherwise identical in shape to #10's.
- **Status**: OPEN. Not exercised on device this session (the device runs that probed #10's mitigation
  used only the `av://` path). Belongs with D58 step 4 and the guard-removal decision alongside #10/
  #11 — whatever mechanism ends up gating admission for the ownership-refactored session boundary
  should cover this call site too, not just `handleIncomingIntent`'s `av://` branch.
- Status update 2026-09-02 (D58 step 4, `c38833d`): **CLOSED**. `applyPendingHandoffText` (the QR-scan/
  manual-paste path) now applies the SAME `HandoffAdmission.mayAdmitInboundHandoff` predicate as the
  `av://` intent path before calling `beginHandoffVerification`, closing the asymmetric-coverage gap
  this finding named — both call sites are gated identically. The refusal shape is likewise matched to
  the `av://` path's already-established rule: `Log.e` + Snackbar + return, **no log entry** (per
  finding #13's rule against an externally-triggerable append) and **no
  `showBlockingOutcomeDialog`** (per finding #12's rule that a refusal path must never use the
  terminal-outcome-with-state-transition dialog). Not exercised on device this session (device runs
  this step used only the `av://` path, same as step 3); the underlying `HandoffAdmission` guard itself
  remains KEPT-by-recommendation per #10/#11's own status updates for this commit, and this path now
  shares that same status rather than having none. See `docs/logs/M2-D58-STEP4-EVIDENCE.md`.

### 2026-09-02 — #16: mint-report loss after Activity destruction (evidence left the device, nothing recorded or shown)

- **Source**: device run (`docs/logs/M2-FENCE-EVIDENCE.md`, T5)
- **Anchor**: `MainActivity.kt:2131` (`Log.w` site, fenced post-mint report/confirmation landing),
  uncommitted `LifecycleFence` pass at time of writing
- **Finding**: a completed delivery whose Activity was destroyed before `direct_post` resolved
  still posts successfully — real evidence leaves the device and the verifier records a full
  tier-B verdict (T5: `bllTnDusyX9bISKQ` done, tier B, ok/allowed true, evidence-verified,
  `sig-p256/1`) — while the report and confirmation dialog are DROPPED by the fence, so NOTHING is
  written to `ReportLog` and nothing is shown to the user, on this instance or any later one. The
  only trace left on the device is the single `Log.w` line at `:2131`. **Render-only fencing did
  NOT create this loss**: unfenced, the report would have landed in the dead instance's
  `ReportLog`, which is never restored to a later instance anyway (Q38: no disk persistence, only
  saved-instance-state survival across recreation, nothing survives process death) — so the report
  was equally invisible before this pass. The fence changed a silent, accidental loss into an
  explicit, logged one; it did not introduce the loss itself.
- **Consequence**: HIGH-adjacent but not classified HIGH here — a real presentation completes and
  is verifiable server-side, but the device holding it has zero record and shows the user nothing,
  which is a disclosure/trust gap (a user cannot confirm what left their device) rather than a
  security break (no unauthorized data left, no session confusion, no forgeable state). Judgement
  call: this is a product/disclosure question for the owner (does a completed presentation need a
  durable, destruction-surviving on-device record — the same question Q38 already opens), not a
  code defect with an obvious fix, since the fence's own job is exactly to prevent a dead instance
  from writing to a UI/state surface a later instance owns.
- **Status**: OPEN, unfixed BY DECISION. Not a bug left undone — a disclosure/product question for
  the owner, adjacent to and overlapping Q38 (log lifetime). Device-proven both sides in
  `docs/logs/M2-FENCE-EVIDENCE.md` T5 (drop) and T1 (the same path succeeding normally when no
  recreation intervenes).
- **Status update 2026-09-02 (D64, owner ruling): CLOSED as Option A (accept and disclose).** If the
  screen is recreated mid-`direct_post`, the site still receives the proof and the phone shows
  nothing; the user rescans. Zero code changes; D44's in-memory-only log stands unamended. Option B
  (a tiny on-disk "sent, awaiting result" marker — host + timestamp only, surfaced as "previous
  presentation to \<host\>: result not recorded" on next app start; would amend D44, no NO-GO #9
  conflict since #9 is about secrets, not disk state) is deferred to the next module's list as its
  first small item, not designed further here. Owner: "oh well, they scan again or if you capture
  error log it after app restart as failed," then "option A." See `decisions.md` D64, `questions.md`
  Q38.
- Status update 2026-09-03 (device session,
  `docs/logs/M2-DEVICE-SESSION-2026-09-03-EVIDENCE.md` check 4): D64's Option A scenario was
  reproduced live on device, not merely disclosed. A recreation forced while a `BiometricPrompt` was
  outstanding (mid-mint) let the prompt's late callback complete `direct_post` on the destroyed
  instance — verifier recorded a full tier-B verdict — while the phone showed and logged nothing
  beyond the fence's own `Log.w` line. No crash. This also clears the `BiometricPrompt` fence
  (`72e0b2c`, finding #5)'s own outstanding verification debt: `M2-FENCE-EVIDENCE.md` stated that fix
  was "code-verified and bytecode-verified only... NO device evidence" — it now has direct device
  confirmation.

### 2026-09-02 — #17: Q47 focus-steal — investigated, not fixed; root cause not isolable from source, needs device repro

- **Source**: agent (owner-directed investigation of PRD Q46/Q47, worked as fixes per owner
  direction 2026-09-02 that these two, previously deferred under D57 as correctness defects, be
  worked now)
- **Anchor**: `apps/scanner/app/src/main/res/layout/activity_main.xml` lines ~122 and ~139 (both
  date-field `EditText`s, `android:focusableInTouchMode="false"`); `MainActivity.kt` ~:404-424 (date
  field `OnClickListener`s opening a `DatePickerDialog`, whose callback only calls `setText`);
  `strings.xml:5` (`input_passport_number`, the sole touch-focusable `EditText` on the form as of
  `d4653b9`) @ `d4653b9`
- **Finding**: this is a device-evidence question, not a code finding — no defect was located in
  source. The coder traced the reported symptom (typing/interacting with the date fields steals
  input focus back to the document-number field, corrupting entered MRZ data, per Q47) and, verified
  by the orchestrator against the diff and the cited line ranges, **ruled out** every code-level
  mechanism that could cause it: zero `requestFocus()` calls anywhere in the module; no
  `TextWatcher`/`OnFocusChangeListener`/`clearFocus` calls anywhere; the two date fields are not
  actually typeable (`focusableInTouchMode="false"`) — their only interaction is a tap that opens a
  `DatePickerDialog`, whose dismiss callback does nothing but `setText` on the field itself, with no
  focus call in that path; and `showPane`/`SessionDisplay.render`/`PaneVisibility`, the module's
  other rendering/state-projection paths, are reachable only from lifecycle/tab/handoff/lock call
  sites — never from date-field interaction, so none of those can be the mechanism either.
  **Standing hypothesis, UNCONFIRMED**: `input_passport_number` is the only touch-focusable
  `EditText` on the form, so after the `DatePickerDialog` dismisses, Android's own default focus
  restoration (a framework mechanism, not app code) lands back on it — this would look exactly like
  "focus steals back to document number" without any app-code cause, but nothing in this session
  confirms it; it needs a real device repro to test. **Discrepancy for the owner**, flagged rather
  than silently resolved one way: `docs/logs/M2-FENCE-EVIDENCE.md` ~:168 describes the symptom as the
  cursor jumping "while typing the date fields," but the date fields have no typing path at all
  (tap-to-picker only per the anchor above) — the repro wording needs sharpening (was the user typing
  in the document-number field and got interrupted, or did focus move right after a picker closed?)
  before the next spawn touches this file again.
- Status update 2026-09-02 (owner direction, fix in flight): a coder fix (`clearFocus()` on the
  document-number field plus hiding the keyboard after the `DatePickerDialog`'s OK) is landing on
  this branch. Not device-confirmed — status stays OPEN pending that check, not marked FIXED. See
  `questions.md` Q47.
- **Status**: OPEN — needs device repro; not fixable from source alone. No device was attached this
  session; nothing here should be read as device evidence.
- **Status update 2026-09-03 (device session,
  `docs/logs/M2-DEVICE-SESSION-2026-09-03-EVIDENCE.md` check 1): CLOSED.** The in-flight
  `clearFocus()`-plus-hide-keyboard fix (`0b71957`) is device-confirmed by owner eye: "cursor
  fixed," after tapping OK on the `DatePickerDialog` for both date fields. No log line exists for
  this by design. See `docs/wiki/questions.md` Q47 (now FIXED, device-confirmed).

### 2026-09-03 — #18: "Scan QR" thumbnail capture does not decode a laptop-screen `av://` link; three unlogged Snackbars on that path (FIXED, both halves)

- **Source**: device session, `docs/logs/M2-DEVICE-SESSION-2026-09-03-EVIDENCE.md` check 5
- **Anchor**: `MainActivity.kt:288` (`qrCaptureLauncher =
  registerForActivityResult(ActivityResultContracts.TakePicturePreview())`), `:290` (Snackbar "QR
  capture cancelled"), `:295` (Snackbar "No QR code found in that photo — try again"), `:841`
  (Snackbar "Not a recognised av:// link or request_uri")
- **Finding**: "Scan QR" uses `TakePicturePreview`, a low-resolution camera-preview thumbnail, not a
  full-resolution capture or a dedicated barcode-scanning intent. A ~150-character `av://` link
  rendered as a QR code on a laptop screen did not decode across three attempts on device
  (09:13:12–09:13:22) — no decode, no crash, only a Snackbar. Separately, three UI-only Snackbars
  on this same code path (`:290`, `:295`, `:841`) have no matching `Log` call, the same class of
  defect the project already fixed once for finding #7's `reportView.text` write — a UI-only status
  write with no log makes a real outcome indistinguishable from nothing having happened, in log
  form.
- **Status**: split. The logging half is **FIXED** (`eb36858` "fix(apps/scanner): #18 — log
  the three unlogged Snackbar sites") — `Log.i`/`Log.w`
  calls added
  beside all three Snackbar sites (`MainActivity.kt:290/291`, `:295/296`, `:844/845`), each
  value-free (length/fixed-scheme-prefix only where the site has a string to describe; no pasted
  text logged).
- **Status update 2026-09-03 (owner ruling, decisions.md D68 part b): decode half FIXED.**
  Owner: keep QR as the cross-device fallback; replace the `TakePicturePreview`/`QrCapture`
  (zxing-core) thumbnail-decode path with a live camera barcode scanner (Google Code Scanner API,
  `com.google.android.gms:play-services-code-scanner:16.1.0` — chosen over ML Kit's bundled
  `barcode-scanning` because its scan UI runs inside a Play-services-owned delegate activity,
  confirmed by inspecting that aar's own `AndroidManifest.xml` to declare zero permissions, adding
  no manifest permission and no app-owned network surface, meeting item 10's constraint;
  `aapt2 dump permissions` on the built debug APK is byte-identical to the pre-change baseline —
  no `CAMERA` line, no new permission of any kind). `QrCapture.kt` deleted; decoded text feeds
  `applyPendingHandoffText` unchanged (same target function as the manual-paste path). The
  verifier spike (`spikes/m2-handoff/server.mjs`) additionally renders a real QR image of
  `app_link_av` (npm `qrcode@1.5.4`, spike-only). See milestones.md §6.2 item 8 amendment. **Both
  halves of finding #18 are now FIXED** — decode half `e5f2008` "fix(apps/scanner): #18 — live
  camera QR scanner replaces thumbnail capture", logging half `eb36858` (above). Not
  yet device-confirmed against a real laptop-screen `av://` QR (see this build's report for the
  4-step device check).
- **Status update 2026-09-03 (owner ruling, ~12:20, decisions.md D69): decode half CLOSED by D69
  (in-app scanner removed; native camera-app route device-proven 2026-09-03).** Same-day reversal
  of D68(b) after a device test: `play-services-code-scanner` still runs its scan UI in a Play
  services process, pulls Google's data-transport telemetry into the merged manifest, and downloads
  its module from Google on first use — the app must be an independent tool with zero doubt about
  bytes reaching Google. The dependency, `launchQrScan`/`qrScanner`, and the "Scan QR" button are
  removed entirely (replaced by a one-line non-interactive hint). The alternative was device-proven
  instead of merely proposed: Pixel Camera scanned the spike's rendered QR and fired the `av://`
  VIEW intent straight into the scanner twice (12:19:28, 12:19:38), both captured and verified
  (origin `http://127.0.0.1:8788`, `signature_verified=true`) — see
  `docs/logs/M2-DEVICE-SESSION-2026-09-03-EVIDENCE.md` check 7. See milestones.md §6.2 item 8/11
  amendment and decisions.md D69.

### 2026-09-03 — #19: two live `RegularActivity` instances across two tasks; unlocked `TECH_DISCOVERED` starts blocked by BAL hardening, not by app logic (OPEN, consequence MEDIUM, owner ruling pending)

- **Source**: owner, device session (`docs/logs/M2-DEVICE-SESSION-2026-09-03-EVIDENCE.md`)
- **Anchor**: `AndroidManifest.xml` (`RegularActivity` launch-mode entry, default `standard`), at `55ee40b`
- **Finding**: two live `RegularActivity` instances existed simultaneously in two separate tasks —
  one launched from Chrome's task at 12:54, one from the camera app's task at 12:55–12:56. With no
  lock active on either instance, the manifest-level `TECH_DISCOVERED` intent-filter start at
  12:56:18 and again at 12:57:20 targeted the invisible instance and was blocked by Android's
  background-activity-launch hardening: logcat shows "Background activity launch blocked! ...
  callingPackage: com.zkagent.scanner" and "invisible launch ActivityRecord". Benign today only
  because an unlocked tap is ignored by design (nothing to admit or refuse); the structural problem
  is that two instances means two independent holders of handoff/session state, a single-ownership
  violation of the same class the D58 ownership refactor was built to close for in-Activity state —
  this one is at the Activity-instance level instead.
- **Proposed fix (not applied this session — DOCS-only pass, no code touched)**:
  `android:launchMode="singleTask"` (or `singleInstance`) on `RegularActivity` so every `av://`
  intent lands in the one instance via `onNewIntent`, which item 17 (D67/Q39, tab-switch-on-intent)
  already handles. Needs an owner ruling before it ships, and a device re-check that both a
  Chrome-launched and a camera-app-launched link land in and reuse the same single instance after
  the change.
- **Also note**: the M2 exit-criteria table's row 1 status is unaffected by this finding and remains
  "NOT YET RE-RUN on the real build" for a different reason — the real-build re-run of the three
  `M2-SCAN-EVIDENCE.md` checkpoints is in progress: one mode-B mint completed on the real build
  (12:54, against the 8787 spike, US passport); reinstall, negatives, and mode-A steps are still
  pending.
- **Status**: OPEN, consequence MEDIUM, owner ruling pending.
