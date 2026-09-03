# Changelog

All notable changes to zkagent are documented here.
Format: [Keep a Changelog](https://keepachangelog.com/en/1.1.0/) · versioning: [SemVer](https://semver.org/).

## [Unreleased]

- **Feature (§6.2 item 21, D67, ENHANCEMENT): launcher-distinguishable app
  built.** `apps/scanner` gets its own adaptive launcher icon — solid
  brand-colour background (`colorPrimary` `#009688`, reused from the app's
  existing theme, not a new palette pick), a monochrome white
  `verified_user` glyph foreground plus matching themed-icon (Android 13+)
  layer, replacing the inherited byte-identical passportreader icon set
  (five legacy `mipmap-*dpi/ic_launcher.png` fallbacks and `ic_launcher-web.png`
  removed as unreferenced — `minSdk 30` means only the `mipmap-anydpi-v26`
  adaptive-icon XML is ever resolved). Release label changed from
  "zkagent scanner (reference)" to "zkagent Scanner"; a new
  `app/src/debug/res/values/strings.xml` overrides it to
  "zkagent Scanner (Debug)" for debug builds, paired with a debug-only
  foreground override (`app/src/debug/res/drawable/ic_launcher_foreground.xml`)
  adding a small orange corner badge, so a release+debug side-by-side
  install is also distinguishable. `spikes/m0`/`spikes/m2-scan` intentionally
  untouched (frozen spike forks, out of this item's scope — flagged to the
  owner in Q48 if they're expected to stay installed long-term). Built and
  test-verified only; device confirmation still pending. Implements Option A
  of the coder's Q48 proposal (owner-approved 2026-09-03). See Q48;
  decisions.md D67; milestones.md §6.2 item 21.
- **Feature (§6.2 item 19, D67/Q44): dim a completed run.** `ReportLog`
  gains a second parallel flag list, `terminalFlags` (same index space as
  `entries`/`expandedFlags`), derived from the SAME pending/terminal model
  `append`'s own `attemptId`/`pending` parameters already track — never a
  new flag guessed from string content. `rendered()` gains a
  `dimmedTextColor` parameter applying a `ForegroundColorSpan` over every
  terminal entry's whole displayed range. `MainActivity.
  dimmedLogEntryColor()` derives the color from `logView`'s own configured
  text color at ~60% alpha, never a hardcoded color. Terminal state
  persists across recreation via a new `STATE_LOG_TERMINAL` Bundle key.
  Note: item 19's approved MUST text is dimming only, not the original
  Q44 "ticked checkboxes" phrasing — no checkbox was added; flagged for
  owner review. 8 new `ReportLogTest` cases. Built, device verification
  pending. Commit `a55ad9f`.
- **Feature (§6.2 item 18, D67/Q43): log entries collapsed by default,
  per-entry toggle.** `ReportLog` owns a new parallel `expandedFlags` list
  (same index space as `entries`), collapsed (`false`) by default for every
  newly-added entry and kept in lockstep through append/replace/evict/
  clear/restore; `rendered()` shows a collapsed entry's title line only
  and an expanded one's unmodified full block — `entriesSnapshot()`, the
  stored/persisted content, is never touched, matching item 18's "content
  unchanged" MUST. `MainActivity` wires a `ClickableSpan` over each
  title line (`logView.movementMethod = LinkMovementMethod.getInstance()`)
  through a new `onLogEntryTapped`/`refreshLogView` pair; expand state
  persists across Activity recreation via a new `STATE_LOG_EXPANDED`
  Bundle key. A replaced (pending -> terminal) entry keeps its prior
  expand state — design choice, flagged for owner review. 11 new
  `ReportLogTest` cases. Built, device verification pending. Commit
  `2837b9a`.
- **Feature (§6.2 item 17, D67/Q39): incoming handoff intent switches to the
  Scan pane.** `PaneState.onIncomingHandoffIntent(admitted: Boolean)` is a
  new writer on the same single owner of the tab index (finding #1, D58 step
  2 — not a second owner); `MainActivity.handleIncomingIntent`'s already-
  admitted `av://` branch calls it, then `showPane()`, before
  `beginHandoffVerification`, so the switch lands before the read begins. A
  refused (`HandoffAdmission`) intent never reaches the call — leaves the
  tab untouched, per the MUST NOT. Not fenced via `LifecycleFence`: the
  write is synchronous inside `onNewIntent`, never the late-async-landing
  shape that class guards against — flagged for owner review. 5 new
  `PaneStateTest` cases. Built, device verification pending. Commit
  `ee45300`.
- **Docs (D66/D67): Q36 resolved (real in-app age answer); Q39/Q40/Q43/Q44/Q45/Q48
  ruled; exit-criteria row 1 corrected.** D66 resolves Q36 — the scanner will
  compute a real over/under answer in-app, in a pure class, at mint time, from
  the DG1 date of birth against the D28-coarsened `current_date` with the
  Q35-sourced threshold; an under-threshold holder still gets an honest
  `over_threshold:false` mint and handoff, and a blocking dialog states the
  threshold was not met — build in flight, not yet implemented or
  device-confirmed. D67 closes Q40 (owner sign-off on "Tap and scan") and
  approves Q39/Q43/Q44/Q45/Q48 into `docs/wiki/milestones.md` §6.2 as five new
  MUST-language, ENHANCEMENT-tagged items (17-21); Q45/Q48 leave wording/design
  undecided by design ("coder proposes, owner approves" at build time). Also
  corrected: the M2 exit-criteria table's row 1 previously implied the three
  `M2-SCAN-EVIDENCE.md` checkpoints had been re-run on the real build — they
  have not; the row now states spike evidence only (2026-08-31), device
  session pending. Docs only, no code touched this session.
- **Fix (Q36/D66): scanner signs a REAL over/under-threshold answer, never
  `true` unconditionally.** `apps/scanner`'s `mintAndMaybeHandoff` previously
  signed `over_threshold: true` for every mint-B, regardless of the document
  holder's actual age — every prior "PASS (minted)" run was evidence about
  plumbing, not age. A new pure `AgeCheck` object computes the real answer
  from the chip's own DG1 date of birth (`MRZInfo.getDateOfBirth()`, ICAO 9303
  `YYMMDD`) against D28's client-side-coarsened current date (introduced here
  as the mint's one clock read — no other current-date coarsening existed in
  `apps/scanner` to reuse), using the standard ICAO 9303 MRZ sliding-century
  window and a birthday-on-or-after rule (29 Feb treated as 1 March in a
  non-leap threshold year) — both stated as owner-overturnable in `AgeCheck`'s
  class doc. An unparsable date of birth refuses the mint the same way Q35's
  absent-threshold branch does (no default). The under-threshold path still
  mints and hands off exactly as before; a new `MintOutcome` object tells an
  honest, expected `allowed:false` refusal (this device already claimed
  `over_threshold:false`) apart from any other kind of verifier refusal, so
  the two are no longer both reported as a generic "site rejected" plumbing
  failure — the blocking dialog and the `ReportLog` entry's `result` line both
  read "The site's age threshold was not met." Also cross-checked
  `Canonical`/`EvidenceSigner`'s byte layout against a `chiproof`-derived known
  vector for `over_threshold:false` (already correct — no boolean
  special-casing existed — added as a regression test). 29 new unit tests
  (`AgeCheckTest`, `MintOutcomeTest`, plus one each in `CanonicalTest`/
  `EvidenceSignerTest`; 223 -> 252 passing per variant).
- **Fix (Q35): scanner signs the verified request's threshold, never a
  hardcoded `18`.** `apps/scanner`'s `mintAndMaybeHandoff` previously signed
  `threshold = 18` unconditionally, ignoring whatever the verifier's request
  object actually asked for. `RequestTrust.thresholdOf` (pure extractor,
  mirroring `tierOf`/`expiresAtOf`) now reads the already-signed, nonce-bound
  `zkagent.challenge.threshold` field; absent, non-integer, or non-positive
  values fail loudly — the mint is refused (log + `ReportLog` entry +
  blocking dialog, no default) before any claim is built, mirroring item
  13's tier-refusal discipline. Closes D48's threshold-from-request MUST;
  `over_threshold` remains unconditionally `true` (Q36, unchanged, still
  open). 10 new unit tests in `RequestTrustTest` (213 -> 223 passing).
- Finding #18 (logging half) FIXED: `apps/scanner`'s three unlogged Snackbar sites in
  `MainActivity.kt` (QR capture cancelled, no QR decoded, unrecognised pasted/QR handoff text)
  each now log a value-free `Log.i`/`Log.w` line beside the Snackbar (length/fixed-scheme-prefix
  only — never the pasted text itself). The decode half of #18 (low-resolution QR thumbnail
  capture) remains OPEN, pending owner ruling. See `.claude/remember/findings.md` #18.
- `apps/scanner`'s `PaneState.kt` class doc corrected: rotation no longer recreates the Activity
  after D63's portrait lock (`d406f4b`); recreation now comes from other config changes only. The
  tab-index race/ownership reasoning is unaffected.
- Freeze lifted (D65).
- **Device session 2026-09-03 clears the D57/D60 freeze's remaining verification-debt
  items; the lift was ruled the same day, see D65 above.** Verified on the Pixel 6a: Q47's
  input-focus fix (`0b71957`) confirmed by owner eye ("cursor fixed"); D63's
  portrait lock (`d406f4b`) confirmed under both auto-rotate settings; the
  `BiometricPrompt` fence (finding #5/#16, D64 Option A) reproduced live —
  evidence left the device via a completed `direct_post` while the phone showed
  and logged nothing, exactly as disclosed; the QR-scan/manual-paste handoff path
  confirmed fenced under a mid-read recreation; finding #10/D61 device-proven
  against a genuinely foreign origin (`127.0.0.1:18787`), cross-checked against
  that origin's own verifier state. **Finding #6 note**: the mid-read branch of
  `HandoffAdmission.mayStartTagRead` is confirmed NOT reachable on device with
  real cards — the NFC stack serialises tag sessions and an in-flight read fails
  before a second discovery could occur; the fix stays proven by unit test and
  source trace only, recorded honestly rather than claimed as device-verified.
  **New finding #18** (non-blocking, not a freeze item): "Scan QR" uses a
  low-resolution preview thumbnail that does not decode a laptop-screen-sized
  `av://` link, and three Snackbars on that path have no matching log line. See
  `docs/logs/M2-DEVICE-SESSION-2026-09-03-EVIDENCE.md`, `docs/wiki/decisions.md`
  D57/D60, `docs/wiki/questions.md` Q47, `.claude/remember/findings.md`
  #6/#10/#16/#17/#18. Docs only, no code touched this session.
- **Owner rulings D61-D64: freeze exit criterion (3) MET; D57/D60 freeze not yet
  lifted.** D61 closes finding #10 (`av://` hijack) by construction —
  `HandoffAdmission` is now permanent policy, not a kept-by-recommendation
  stopgap; device proof against a genuinely foreign origin is still owed. D62
  closes finding #11 (biometric prompt origin disclosure) — the site-named
  prompt title is accepted as the fix, owner-confirmed on device. D63 locks
  `AndroidManifest.xml` `screenOrientation` to portrait (was `fullSensor`,
  landing in a follow-up commit on this branch), closing finding #4's
  rotation vector only; the non-rotation part of #4 carries to the next
  module's `SessionState` design item. D64 closes finding #16 and Q38 as
  Option A (accept and disclose): a mid-`direct_post` recreation still
  delivers the proof to the site while the phone shows nothing; zero code,
  D44's log stands unamended; Option B (an on-disk "sent, awaiting result"
  marker) is deferred to the next module's list. D57's exit criterion (3)
  is now MET by D61/D62, but the freeze's actual lift is withheld pending a
  dedicated device session and is not recorded here. Q47 (input-focus
  steal) has a fix in flight from a coder (`clearFocus()` plus hiding the
  keyboard after the `DatePickerDialog`), not yet device-confirmed — marked
  "fix in flight, device check pending," not FIXED. See `docs/wiki/decisions.md`
  D61-D64, `docs/wiki/questions.md` Q38/Q47, `.claude/remember/findings.md`
  #4/#10/#11/#16/#17. Docs only, no code touched this session.
- **`apps/scanner` — finding #6 residual (a) retired: mid-read tag-refusal
  Snackbar removed by owner decision.** `57f5ddd` removes the mid-read
  Snackbar and its `TAG_REFUSED_MID_READ_MESSAGE` constant from the NFC
  branch of `handleIncomingIntent` — a refused mid-read tag is normally the
  same physical card the user is already holding, so no user-facing message
  is needed. The static `Log.w` line and the `HandoffAdmission.mayStartTagRead`
  gate itself are unchanged and still fire. Tests 208, 0 failures. No device
  evidence for the guard firing — none was attached this session.
- **`apps/scanner` — finding #8 fully closed: `M0Probe.tryActiveAuth`'s
  three-state decision extracted and unit-tested.** `840779c` extracts
  `M0Probe.tryActiveAuth`'s DG15-absent/verified/failed three-state decision
  to a pure `ChipAuthClassification.fromActiveAuth(...)`, closing the
  narrower gap left open inside finding #8 after `651ecd5`. JMRTD I/O stays
  inline; detail strings byte-identical. 5 new hand-written tests, added
  TDD-style (compile-error red confirmed, then green). Tests 208 → 213, 0
  failures.
- **`apps/scanner` — Q46 fixed: MRZ input label corrected to "Document
  number."** `d4653b9` changes `strings.xml` `input_passport_number` from
  "Passport number" to "Document number" (owner's stated preference,
  Q46) — resource id and `passportNumberView` field name unchanged on
  purpose. Worked as a fix now, per owner direction 2026-09-02, rather than
  left deferred to the shared UI pass. No device evidence.
- **`apps/scanner` — Q47 investigated, not fixed: input-focus steal on the
  document-number field.** Traced every app-code focus mechanism
  (`requestFocus`, `TextWatcher`/`OnFocusChangeListener`/`clearFocus`, the
  date fields' `DatePickerDialog` callbacks, `showPane`/`SessionDisplay`/
  `PaneVisibility`) and ruled all of them out as the cause. Standing,
  unconfirmed hypothesis: Android's own default post-dialog focus
  restoration landing on `input_passport_number`, the form's only
  touch-focusable field — a framework mechanism, not app code, requiring a
  device repro to confirm or refute; none was performed this session. Also
  flagged a wording discrepancy in the existing repro evidence
  (`docs/logs/M2-FENCE-EVIDENCE.md`) for the owner to sharpen. See
  `.claude/remember/findings.md` #17 for the full record.

- **`apps/scanner` — finding #5 (fence): fix-ledger doc-drift bullet cleared.**
  `e13dab0` rewrites `LifecycleFence.kt`'s KDoc so its thread-safety proof no
  longer enumerates two syntactic forms (`runOnUiThread`, `onPostExecute`) as
  exhaustive. It now states the real invariant — every `passes()` read is a
  main-thread landing — lists the three forms actually observed (10
  `runOnUiThread`, 1 `onPostExecute`, 2 main-executor `BiometricPrompt`
  callbacks = 13 sites) as observed-not-exhaustive, and names the hazard
  predicate itself as the criterion for any future site. This closes the
  single open `.claude/remember/fix-ledger.md` bullet (ledger now 0 bullets;
  the file is gitignored). Doc-only: the `72e0b2c` BiometricPrompt fence
  guard remains without device evidence. Tests 184/0, unchanged.
- **`apps/scanner` — finding #8 fixed: chip-auth classification extracted
  and unit-tested for the first time.** `651ecd5` adds a pure
  `ChipAuthClassification` object (`fromDg14`, `combine`, `label`,
  `technical`) that replaces the inline DG14 decision, the CA+AA combine
  rule, and both owner-approved D53 string mappings previously inline in
  `MainActivity` — I/O stays inline, only the decision moved, and the
  rendered strings are byte-identical to before. New
  `ChipAuthClassificationTest.kt` (20 tests: a hand-written 3×3 combine
  truth table, 4 DG14 cases, NOT_SUPPORTED never contains "false"/"Not
  verified", FAILED contains "Not verified", three distinct labels). Tests
  184 → 204, 0 failures. **Noticed, not changed**: `M0Probe.tryActiveAuth`
  (`M0Probe.kt` ~:238) still holds its own separate inline
  VERIFIED/NOT_SUPPORTED/FAILED decision with no direct unit test — the
  same finding's narrower second half, left for the next spawn touching
  `M0Probe.kt`.
- **`apps/scanner` — finding #6 fixed: NFC-tag branch of
  `handleIncomingIntent` now refuses a tag arriving mid-read.** `c60354e`
  adds a pure predicate `HandoffAdmission.mayStartTagRead(sessionLocked,
  readInProgress) = sessionLocked && !readInProgress` (opposite polarity to
  the existing `mayAdmitInboundHandoff`, kept in the same object since both
  gate the same two fields for the two intent branches of one function), 4
  new truth-table tests. Wired into the NFC branch after the existing
  `lockedMode == null` check and before the MRZ snapshot/`MrzChangeTracker`
  diagnostic, so a refusal never disturbs `lastMrzHash`. Refusal shape
  mirrors the `av://` path: static `Log.w` + Snackbar (new
  `TAG_REFUSED_MID_READ_MESSAGE = "Ignored a tag that arrived mid-read."`) +
  return — no report-log append (finding #13's rule), no blocking dialog
  (finding #12's rule), no state assignment. Tests 204 → 208, 0 failures.
  Guard is unit-tested at the predicate level and source-verified at the
  call site only. **Residuals**: the Snackbar wording is PROPOSED, pending
  owner approval; NO device evidence — no device was attached this session.
  See `.claude/remember/findings.md` #6/#8/#5 for the full record.

## [0.4.0] — 2026-09-02

- **`apps/scanner` — D57 exit criterion (2) met: every async writer fenced
  against the Activity lifecycle (commits `b8e0e05`, then `72e0b2c`), with
  a completeness correction found by `/branch-review` recorded plainly, not
  buried.** Before this pass the module had NO `onDestroy` override and no
  lifecycle guard of any kind — `runOnUiThread` changes which thread code
  runs on, not whether the Activity it touches is still alive. New pure
  `LifecycleFence` (`alive`/`retire()`/`passes()`), held as a
  per-Activity-INSTANCE field, retired in a new `onDestroy` — deliberately
  not a singleton, since a shared fence retired by a dying instance would
  permanently block the next one. Owner-decided semantics: a fence drops a
  main-thread LANDING; it never cancels or aborts in-flight work — aborting
  a `direct_post` already in flight would be worse than letting it complete
  unobserved. Every drop and the retirement itself are logged (static,
  zero-interpolation messages), because an unlogged drop is
  indistinguishable from a crash — the same defect class as this project's
  earlier un-logged `reportView.text` write. No new Gradle dependencies.
  Unit tests 180 → 184.
  **The completeness correction**: `b8e0e05` enumerated its fence targets
  by grepping `runOnUiThread` (10 hits) plus `ReadTask.onPostExecute` and
  claimed 11 fenced sites — WRONG IN SCOPE, because that enumeration was
  syntactic, not by the actual hazard predicate. `/branch-review` found
  `BiometricPrompt.AuthenticationCallback`, dispatched on the main executor,
  unfenced: `onAuthenticationError` called `emitReport` then
  `showBlockingOutcomeDialog`, which does `AlertDialog.Builder(this).show()`
  against a possibly-destroyed Activity. Fixed in `72e0b2c` — **13 sites,
  not 11**; the same guard added to `onAuthenticationError` and, for
  defence-in-depth, `onAuthenticationFailed` (placed after its existing
  diagnostic log line so it never suppresses it). `onAuthenticationSucceeded`
  is unchanged — it only starts a `Thread{}` whose landings were already
  fenced. **Criterion (2) was NOT actually met between `b8e0e05` and
  `72e0b2c`**; it IS met now, at 13 sites.
  **Why the gap was reachable, bytecode-verified in `androidx.biometric`
  1.1.0** (worth recording — it is counter-intuitive): a destroyed host is
  *normally* never called back, because
  `BiometricPrompt$ResetCallbackObserver.resetCallback()` is annotated
  `@OnLifecycleEvent(ON_DESTROY)` and nulls
  `BiometricViewModel.mClientCallback`, after which `getClientCallback()`
  substitutes a no-op default. BUT `addObservers()` is invoked ONLY from the
  library's two `Fragment` constructors; both `FragmentActivity`
  constructors call neither `addObservers` nor `getLifecycle`. `MainActivity`
  is an `AppCompatActivity` using the `FragmentActivity` overload, so that
  protection is not active here — the safe behaviour exists and is one
  constructor overload away from being true.
  **Device evidence** (Pixel 6a, real NL ID card, `spikes/m2-handoff`
  verifier): six tests. Three verifier-cross-checked happy-path mints; a
  singleton-trap test proving `LifecycleFence` is constructed fresh per
  Activity instance — a property no unit test can give — via two in-process
  Activity recreations followed by a clean mint on the third instance with
  zero drops; a mid-verification drop; a mid-read drop with no mint and no
  stranded UI; and a mid-mint drop. **Limitations stated, not softened**:
  the mid-verification and mid-mint drop windows were reachable only via an
  artificial test-harness delay proxy (app and verifier both unmodified;
  natural windows ~12-28ms on localhost), and the `72e0b2c` biometric fix
  has NO device evidence at all — code/bytecode-verified only. Full record:
  `docs/logs/M2-FENCE-EVIDENCE.md`.
  **New finding #16, open by owner decision, not an oversight**: a
  completed delivery whose Activity died before `direct_post` resolved
  still posts — the verifier records a full tier-B verdict — while the
  report and confirmation dialog are dropped, so nothing is written to
  `ReportLog` and nothing is shown to the user. Render-only fencing did NOT
  create this loss: unfenced, the report would have landed in the dead
  instance's `ReportLog`, which is never restored to a later instance
  anyway — the fence turned a silent, accidental loss into an explicit,
  logged one. A disclosure question for the owner, not a bug left undone.
  **Q38 answered by device evidence, not decided**: `ReportLog` survives
  Activity recreation via saved instance state; nothing survives process
  death. **Q47/Q48 opened**: input focus steals back to the document-number
  field while typing date fields, corrupting entered MRZ (suspected — not
  confirmed — cause of one run's `SW 0x6985` access-establishment failure);
  and the three installed reader apps are indistinguishable on the
  launcher (only `com.zkagent.scanner` declares the `av://` VIEW filter,
  so intent routing is deterministic on this device — a
  human-identification problem, not a security one; does not bear on
  findings #10/#11).
  **D60 — branch close-out.** The D57 freeze is CARRIED FORWARD, not lifted
  and not abandoned: clearing it is the FIRST work item of the next module,
  ahead of that module's own work. Exit criteria (1) and (2) are met;
  criterion (3) is not — findings #10 and #11 remain OPEN at consequence
  HIGH, mitigated (commit `730ef09`) not closed, and are the entire reason
  the freeze does not lift. This is a deferral, not a lift: no new §6.2
  item, enhancement, or UX change lands while D57 stands, and every UI item
  already deferred under D57 (Q43-Q48) stays deferred.
- **`spikes/m2-handoff` — real-device scope-domain bug found and fixed
  (D37), plus the test-suite blind spot that let it pass — 23/23 spike
  tests, 191/191 chiproof tests.** A live Pixel 6a mode-B run returned
  `sig_invalid` (pinned key resolved fine — never `sig_unknown_key`): the
  scanner signs scope = host of its verified request origin (D37,
  `MainActivity.kt:876`, e.g. `127.0.0.1`), while `server.mjs`'s
  `SCOPE_DOMAIN` was hardcoded to an unrelated literal (`'m2-handoff.test'`)
  — one differing string in the signed preimage, every real-device
  signature fails. Fixed: `SCOPE_DOMAIN` now defaults to `BIND_HOST`
  (`'127.0.0.1'`, the address the server always binds), still overridable
  by env var. Decision recorded: derived ONCE at startup from the bind
  address (not per-transaction from the request origin) — chiproof's
  `createVerifier` takes `scopeDomain` as fixed boot-time config with no
  per-call override in `verify()`, so per-transaction derivation would need
  one verifier instance per origin; fine for a single-origin spike, flagged
  as awkward for chiproof's current API on a genuinely multi-origin
  deployment. **Escalated for the PRD, not decided here:** scope is HOST
  ONLY (matching the scanner) while D37's origin-consistency check (D34)
  uses the FULL scheme+host+port origin — a deliberate difference in
  granularity between two different jobs, recommended to stay that way,
  needs owner confirmation; the PRD file was not edited. The suite itself
  had carried its own hardcoded `SCOPE_DOMAIN` copies
  (`tests/tier-b.test.mjs`, `scripts/fake-wallet.mjs`) that happened to
  match the server's old literal — proving nothing about a real client's
  derivation, which is exactly why 17/17 passed while the real device
  failed. Fixed to derive independently: the fake wallet now derives scope
  from `requestObject.response_uri`'s host AFTER verifying that request
  object's JWS (the same verified-origin mechanism D37 specifies for the
  real scanner); the test file derives it from `new URL(srv.url).hostname`
  once the ephemeral server is up. Neither hardcodes a scope or imports the
  server's constant anymore, so a future scope-config regression fails for
  the right reason instead of silently agreeing with a copied literal.
- **`chiproof` 0.4.0 (still unpublished) — per-origin attester keys,
  trust-on-first-sight (D38) — 191/191 tests passing (173 + 18 new).**
  `sig-ed25519/1`/`sig-p256/1` items may now carry `data.pubkey`
  trust-on-first-sight (D38) — 191/191 tests passing (173 + 18 new).**
  `sig-ed25519/1`/`sig-p256/1` items may now carry `data.pubkey`
  (SubjectPublicKeyInfo DER, base64) alongside `key_id`; the verifier always
  recomputes `key_id` from `pubkey` via the new exported `keyIdFor(der)`
  (`sha256(der)` hex, first 16 chars — byte-identical construction to the
  scanner's Kotlin `EvidenceSigner.keyIdFor`) and refuses a mismatch
  (`sig_key_id_mismatch`). Key resolution order: operator-pinned `keys`
  (unchanged, still the only path that accepts an item without `pubkey`),
  else a new pluggable `attesterStore` (`{get({scope,zktag}),
  bind({scope,zktag,key_id,pubkey})}`, `InMemoryAttesterStore` reference
  implementation in `src/stores/attester.js`, same conventions as
  `InMemoryNonceStore`) binds an unpinned key to `(scope, zktag)` on first
  sight — only after its signature verifies, never before — and surfaces
  `attester_bound_first_sight` via the plug's existing `warnings` channel (no
  parallel field); a later presentation for the same `(scope, zktag)`
  carrying a DIFFERENT key is `attester_key_mismatch`, never silently
  re-bound. Store `get`/`bind` throwing maps to `ok:false`/`allowed:null`,
  never a "no" — same discipline as `NonceStore`. `sigEd25519`/`sigP256` now
  accept construction with an `attesterStore` and NO pinned `keys` at all
  (pre-D38: an empty/absent `keys` list always threw); `verify()` is now
  `async` (the store lookup needs it) — one pre-existing direct-call test
  updated to `await` it, no behavioural change. New test file
  `tests/integration/attester-sig-d38.test.js` (18 tests: pinned-path
  unaffected/pinned-precedence-over-store, first-sight bind then match (both
  algorithms), first-sight then a different key, pubkey/key_id mismatch,
  unpinned-no-pubkey, store `get`/`bind` throwing, bind-only-after-verify,
  registration validation, `InMemoryAttesterStore` itself). Types
  regenerated (`AttesterStore` typedef, `InMemoryAttesterStore` export).
  **Escalated, not decided:** whether the D30 `linkability: 'signer'`
  declaration is still the right one now that the key is per-origin rather
  than a single fixed device key — `evidence.js`'s tier gating treats
  `'signer'` and `'device'` identically today, so nothing in the code forces
  either answer; left unchanged pending owner sign-off (see PR/report).
  `spikes/m2-handoff` wired an `InMemoryAttesterStore` into its verifier so a
  real device binds on first sight, keeping the env-override pinned key path
  working; `tests/tier-b.test.mjs` and `scripts/fake-wallet.mjs` updated to
  send `pubkey`.
- **`chiproof` 0.4.0 (still unpublished) — `evidence.require` alternatives
  groups (D31/D36) — 173/173 tests passing (165 + 8 new).** A `require`
  entry may now be a registry-key string (all-of, unchanged since 0.2.0) or
  a non-empty array of registry-key strings — an alternatives GROUP,
  satisfied when at least one member is present and verifies, e.g.
  `require: { B: [['sig-ed25519/1', 'sig-p256/1']] }` lets the verifier
  accept whichever attester-sig algorithm a device's Keystore actually
  produced (D31 supersedes D30's single-required-plug framing; D36: the
  device never chooses to downgrade, only falls through on failure).
  `normalizeRequire` and `routeEvidence` (`src/evidence.js`) both extended;
  `Verdict.evidence` (already exposed, §6.2 item 9) continues to list every
  registry key actually checked, so which group member was used needs no
  new field — a present-but-invalid group member is a real no exactly like
  any other checked item, never masked by a different member of the same
  group passing. New test file
  `tests/integration/evidence-alternatives.test.js` (8 tests). Types
  regenerated (`RequireEntry = string | string[]`); `spikes/m2-handoff`
  updated to register both `sig-ed25519/1` and `sig-p256/1` and require the
  any-of group for tier B, retiring its local `sig-ed25519-plug.mjs` in
  favor of chiproof's own `sig-ed25519/1`/`sig-p256/1` plugs.
- **`chiproof` 0.4.0 — attester-key evidence plug family (`sig-ed25519/1`,
  `sig-p256/1`), PRD §6.2 items 1/9/11 + FR12/D30 — 165/165 tests passing.**
  One shared preimage (`src/plugs/attester-sig.js`) drives both plugs so
  their byte layouts cannot drift: `utf8(PLUG_TYPE + "\n") ‖
  sha256(canonical(claim)) ‖ base64urlDecode(nonce) ‖ utf8(scopeDomain) ‖
  utf8(zktag)`, domain-separated by the literal plug-type string so a
  signature minted for one algorithm cannot verify as the other.
  `sig-ed25519/1` (D30, the mode-B reference default) signs
  `sha256(preimage)` — Ed25519 has no prehash step in the Node/JCA APIs;
  `sig-p256/1` (candidate name, `Dn` pending, permitted by the §6.2 item 11
  amendment because Ed25519 is unavailable as an AndroidKeyStore key on the
  Pixel 6a, `docs/logs/M2-SESSION-POC.md` F2) signs the raw `preimage` with
  `ECDSA-P256-with-SHA256`, whose native prehash does the same job — applying
  sha256 in each algorithm's own native place is what keeps one preimage
  definition true on both sides (orchestrator-recommended reading of item
  9's layout for the P-256 case, not owner-decided — flagged for veto). Both:
  `binds: {nonce, claim, scope, zktag: true}`, `linkability: 'signer'`,
  `tierCeiling: 'B'`. P-256 signatures are DER-encoded (Node's default, and
  what Android Keystore produces); no raw r‖s support added. New test suite
  (`tests/integration/attester-sig.test.js`, 39 tests) covers both happy
  paths, cross-algorithm replay in both directions, wrong
  nonce/claim/scope/zktag each paired with a passing control, unknown
  `key_id`, malformed signatures, wrong key type pinned per plug, a proof
  that base64url-decoded nonce bytes (not the utf8 string) are what's
  signed, and a throwing plug mapping to `ok:false` never `allowed:false`.
- **`chiproof` M1 verifier core (buckets B1–B4) implemented and tested — 116/116
  passing, zero runtime deps.** Spec: `docs/product/m1-verifier-core-spec.md`.
  B1: the `ok`/`allowed` verdict invariant (`src/verdict.js`) structurally
  forbids `{ok:false, allowed:false}`; canonical JSON + sha256 for signing
  (`src/canonical.js`, JCS-like, floats rejected); a self-authenticating HMAC
  challenge/nonce with optional Ed25519 issuer signing (`src/challenge.js`,
  D20), ported from 8een. B2: `createVerifier(config).verify(presentation,
  ctx)` (`src/index.js`) checks spec, shape, challenge liveness and single
  use, tier negotiation (refuse, never downgrade), threshold match (D11),
  zktag/`chip_auth` presence rules (D21) and the FR10 trust list; fails loud
  at boot on a missing/weak `challengeSecret` or an `InMemoryNonceStore`
  outside tests. B3: the evidence slot (`src/evidence.js`, D24/FR12) — a plug
  registry with boot-time binding/linkability/tier-ceiling checks, `require`/
  `accept` routing, and fault isolation (`ok:false` on any throw) — plus two
  shipped plugs: `signed-receipt/1` (Ed25519 over `hash(claim)‖nonce‖scope`)
  and `zk-passport/1` (D25, the four-stage zkPassport UltraHonk composition,
  verified by shelling out to a pinned `bb` 5.0.0 binary, never on `PATH`).
  B4: end-to-end tests against real NL/US zkPassport proofs from
  `spikes/m1-zk/`, skipping cleanly when the artefacts or `bb` are absent.
  Applied a round of code-review findings before merge: evidence bounds
  checked before any plug runs, the four `bb verify` calls run in parallel,
  `bb`'s exit classified (clean non-zero = a real no; signal/timeout/spawn
  failure = the verifier being broken, `ok:false`), and tmpdir cleanup
  failures surfaced as warnings rather than swallowed.
- **D19–D25 (owner decisions, 2026-08-29/30) settle the M1 verifier design**,
  recorded in the PRD (v1.7 → v1.11) and `docs/product/learnings.md`:
  - **M1 POC (2026-08-29)** on the Pixel 6a confirmed risk #8: the raw
    Android key-attestation chain carries a stable per-device intermediate
    on both StrongBox and TEE paths (plus stable verified-boot fields), so
    tier A cannot carry it raw — opening **Q23**.
  - **D22** relaxes tier A's promise from same-site to cross-site
    unlinkability only (a site a holder returns to already links visits by
    other means; nothing in the payload may be stable *across* sites).
  - **D23** resolves Q23 for v1: **voucher-grade attestation (Play
    Integrity)**, D1 stands, and ZK-over-the-passport becomes a named,
    gated second track ("Track Z") rather than a maybe — five explicit
    gates (audited Barretenberg release, an audit of zkagent's own
    circuits, measured on-phone proving time, a chain-free nullifier path,
    an open-source on-device prover) must all hold before D1 is revisited.
    Measured on real documents: `docs/logs/M1-Q23-EVIDENCE.md`,
    `docs/product/zk-due-diligence.md`.
  - **D24** supersedes D23's Play Integrity framing after finding tokens are
    **not borrowable** (decode is tied to the app developer's own Google
    Cloud project) and introduces the **evidence slot**: the core ships with
    it empty (bare mode, captcha-grade, knowingly), adopters choose what
    fills it, plugs are published/versioned (FR12), and every plug must bind
    nonce + claim + scope or registration refuses it.
  - **D25** ships `zk-passport/1` as the evidence slot's genericity proof,
    **tier A only**: the zkPassport age circuit has no nonce input, so the
    challenge nonce rides in `service_subscope` — which also feeds the
    nullifier, making it per-request (unlinkable, but unusable as a stable
    tier-B/C zktag). Tier B/C ZK evidence is deferred to Track Z as **Q26**.
    Measured: `bb verify` 5.0.0 ≈0.035s for the four-stage composition on a
    real NL document.
  - **D20 seal amendment**: the nonce HMAC now covers every challenge field
    (tier, verbs, threshold, max_scan_age, expires_at), so an unsigned
    tier-A/B challenge is tamper-evident — editing any field after minting
    breaks the tag — without touching the nonce store.
- **PRD v1.6 — the post-M0 disclosure model, recorded as shape with mechanics deferred.**
  Also a new **§1.1 glossary** separating three objects the project's own notes had been
  calling "nonce": the *challenge nonce* (requester's single-use random number, all tiers,
  cannot recognise anyone — reuse is rejected, so replay protection and unlinkability are
  the same mechanism), the *secret* (chip-derived, phone-resident, never transmitted,
  never minted in tier A), and the *zktag* (the domain-scoped pseudonym, tiers B/C only,
  recognisable at one domain). One line: the nonce proves the request is fresh; the zktag
  proves the person is the same. Tier A wants the first and refuses the second.
  Three decisions of *shape*, taken so the stages that follow can be planned against them,
  and explicitly reopened at M1/M2 against code rather than prose. **D19 — three tiers**:
  A anonymous (one boolean, the default, open to any requester); B pseudonymous (A plus
  the domain-scoped zktag, open because a site can only compute its own pseudonym — safety
  by arithmetic, not judgment); C attributed (identifying booleans such as name-matches,
  gated to challenge issuers whose key the app build pins at tier C, and *refused* rather
  than downgraded from anyone else). The holder sees the tier's plain-language wording
  before every tap. The operator's surface is a **published verb vocabulary** with verbs
  switched on or off — a list of question *types* we write, never a registry of *askers*
  — and the PRD now says *asked*, not *captured*, because nothing is retained by anyone.
  Field count was rejected as the knob: two verbs can be fully anonymous or a full
  identification. **D20 — signed challenges**: the issuer's public key is its identity,
  pinned per build with a tier ceiling; resolves Q20 without a registry and supersedes the
  split-nonce sketch. **D21 — always read, conditionally mint**: no mode selection up
  front; `chip_auth: passed | absent` travels in tiers B/C only and the *verifier* enforces
  the requester's acceptance policy (M0 showed the US passport cannot prove it is the
  original and the NL card can). Tier A never carries the flag. New **Q21** (how an
  authority admits a bank to tier C — delegation, rung-2-shaped, deferred by D18) and
  **Q22** (the tier-C verb list; exact booleans only, no similarity scores). The
  predicates non-goal is narrowed to tiers A/B rather than retired.

- **M0 RUN — the riskiest assumption tested against real documents, and partially
  retired.** First evidence in this project; everything before this was design.
  Four valid runs on a Pixel 6a (stock Android 17): the owner's US passport twice
  and NL identity card twice, from a throwaway fork of `tananaev/passport-reader`
  (JMRTD 0.7.18) at `spikes/m0/`, telemetry and DG2 stripped. Eight planted
  negatives across the four runs; **all eight observed to fire.** Evidence:
  `docs/logs/M0-EVIDENCE.md` (11 findings, with what was *not* established stated
  as prominently as what was).
  - **One code path, two protocols, no per-country logic.** The same build read a
    BAC-only US passport and a PACE NL card, protocol chosen by what the chip
    advertised (`EF.CardAccess` present ⇒ PACE, absent ⇒ BAC). No configuration
    change, no rebuild. Two documents from two issuers is not coverage; the ban on
    coverage numbers stands (Q12).
  - **Government-signature verification works against a free public list.** The
    master list bundled with the fork was identified as the BSI all-country list
    (588 certificates, 116 issuing countries; US 8, NL 10), so PRD risk #3 is
    retired for both documents. Declared-vs-parsed asserted equal on every run.
  - **Q18 resolves per document, not per product — and runs opposite to
    intuition.** The **US passport carries neither DG14 nor DG15** (`SELECT EF.DG15`
    → `6A82 FILE NOT FOUND`): no challenge-response of any kind, so a cloned data
    set would mint the identical zktag. The **NL identity card carries both**, and
    both succeeded (Chip Authentication, and Active Authentication signing a fresh
    challenge with an EC key). Mode A is unaffected either way. **Mode B is
    clone-replayable on a US passport and clone-detectable on an NL card**, which
    makes chip-authenticity an adopter configuration trade-off (`acceptedDocuments`,
    D14) rather than a product-level claim.
  - **Derivation input is deterministic per document and distinct across
    documents.** All candidates byte-identical across two taps and two app processes
    per document; no candidate collides between the two documents (k>1 for one
    holder, as D14 predicts). The NL rescan is the stronger of the two, because AA
    and CA inject per-session randomness that a session-derived candidate would have
    exposed as a mismatch.
  - **New constraint on D9, only visible because both documents were read**: the
    chip-bound fields (`dg14_ca_key`, `dg15_aa_key`) are the most attractive
    derivation inputs precisely because they defeat cloning — and they **do not
    exist on a US passport**, so choosing one narrows `acceptedDocuments` by
    construction. `document_number` exists everywhere but rotates at renewal. D9
    stays open, now with the trade-off measured rather than theorised.
  - **A planted negative that silently tested nothing** (M0 run 1, recorded rather
    than quietly fixed): the CSCA-removal guard matched the literal string
    `"United States"`, which never appears in the US CSCA DN
    (`OU=U.S. Department of State MRTD CA … C=US`), so it excluded zero certificates
    and passive auth passed for the honest reason that the anchor was still present
    — a *plausible pass* proving nothing. Run 1 was voided under PRD §6.1. The guard
    now matches the document's real issuer DN and **asserts that the exclusion
    removed something**, because "excluded nothing" and "failed to fire" were
    indistinguishable. Carry to M1: every negative test must assert its precondition
    took effect.
  - **A live `ok`/`allowed` conflation found in third-party code.** Upstream
    `doPassiveAuth()` wraps digest comparison, master-list load, path validation and
    signature check in one `catch { Log.w(...) }`, leaving `passiveAuthSuccess =
    false` — "forged" and "undecidable" become the same value, exactly what PRD §3
    forbids, in an app with 451 stars. Replaced in the spike by a
    `Verdict(ok, allowed, reason)` that cannot represent `ok:false, allowed:false`.
    Evidence *for* the invariant, from the wild; cite it in the M1 SDK tests.
  - **Measured timings, no guessed numbers**: clean tap 2.5–3.3 s end to end
    (US 2,531 ms; NL 3,300/3,322 ms). BAC setup varied 363 ms → 5,537 ms across two
    taps of the same passport — alignment, not computation. Four runs is not a
    distribution and no percentile may be quoted from it.
  - **Explicitly NOT established, and written into the evidence doc as such**:
    renewal stability (D9's real question — no renewed document exists to test);
    mode-A unlinkability (no attestation was involved in M0 at all — still a design
    intent until M1b, FR9/Q15); coverage; performance distribution; anything about
    attestation, StrongBox or Play Integrity (Q14 unchanged).

- **PRD v1.5 — the M0 row rewritten so the spike could fail.** Written *before* the
  run, and it is the reason the run produced findings rather than reassurance.
  Planted negatives made mandatory (DG1 byte flip; issuing CSCA removed ⇒ must not
  return `allowed:true`); documents named (US passport primary, NL identity card
  second); access protocol negotiated by the chip and never hardcoded per country;
  MRZ key typed by hand, never stored, never in source; DG1 + SOD only, with
  DG14/DG15 probed for authenticity support; a zktag candidate derived per stable
  field so D9 is decided on a table rather than an argument; BSI all-country master
  list pinned with a certs-parsed = certs-declared assertion; a PII-free evidence
  rule (field names, counts, hashes and verdicts only — never values). New **§6.1
  M0 go/no-go table**: nine outcomes, each with its meaning and consequence, agreed
  in advance so a surprise could not be rationalised afterwards. Evidence path moved
  to `docs/logs/` (the `docs/02-evidence/` path predated the reorg); M0 device
  wording aligned with D2's Pixel-only decision; risk #2 now names PACE and both
  documents.

- **Q19 and Q20 parked, not designed** (owner-raised, 2026-08-29). **Q19 —
  freshness as a range, not a ceiling**: D10 fixes an operator *ceiling*, and the
  mirror case is a requester-stated *floor* (a bank wanting a scan under 24 hours
  old), which implies conveying when the secret was minted — in direct tension with
  D10's one-bit answer, since a precise mint date is a fingerprint (FR6/FR9).
  **Q20 — operator identity in a borrowable core**: if anyone may wrap the library,
  anyone may generate a challenge, and the hard part is uniqueness without a
  registry (NO-GO #3 forbids one). Recorded with the two existing mechanisms it must
  reconcile with — FR2 binds the zktag to the client-verified domain, FR10
  identifies *clients* by signing-cert digest — neither of which covers *operator*
  identity. Both stay parked until rung 1 ships (D18).

- **Licence and provenance recorded for the vendored spike.** `spikes/m0/LICENSE`
  (Apache-2.0, matching the repo) and `spikes/m0/NOTICE` added. The NOTICE records
  what the spike is a copy of, that **upstream states Apache-2.0 in its README but
  ships no LICENSE file** (so the text here rests on that statement and must be
  revisited if upstream's licensing is ever clarified otherwise), every modification
  made and why, and the fact that **the package name is still upstream's and must be
  changed before any distribution**. Upstream's own `google-services.json` (their
  Firebase project keys, dead once the flavour was removed) and `PRIVACY.md` (a
  Play-listing policy describing upstream's app) were deleted rather than carried.

- **Toolchain, reproducible and rootless.** Temurin JDK 21 at `~/opt` (Fedora 44
  packages only JDK 25/26 — `java-21-openjdk-devel` does not exist there) and the
  Android SDK at `~/Android/Sdk` (platform-tools 37.0.1, platform 36, build-tools
  36.1.0). The spike pins `compileSdk`/`targetSdk` 36 rather than upstream's 37,
  which is preview-channel only.

- **Docs reorganised with docs-builder.** `docs/01-product/`, `docs/02-engineering/`
  and `docs/context/` (each holding a single file) flattened into `docs/product/`
  and `docs/logs/`: `zkagent-prd.md` and `zkagent-design.md` classified as
  `product` (live specs); `future-digital.md` classified as `logs` rather than
  `product` on its own self-declaration as "the collector" — a session record of
  the strategy discussion that produced the PRD, distinct from the PRD itself,
  which the same sentence calls "the filter." 15 inbound links across
  `.claude/stash/`, `README.md` and `packages/chiproof/README.md` rewritten to
  match. New `docs/index.md` (generated, whole-corpus map — never hand-edited)
  and `docs/log.md` (append-only reorg history). New `CLAUDE.md`, carrying only
  the marker-wrapped pointer to `docs/index.md`. Nothing was oversized (largest
  file: 385 lines against a 500-line ceiling), so no split ran.
- **Development device settled (D2): Pixel, stock ROM; all other vendors ruled
  out for M0–M2.** Recorded with the reasoning so it is not relitigated: NFC
  Type A/B is not the discriminator (baseline on every phone-class NFC
  controller), and the extended-length-APDU variance behind commercial ID
  vendors' device blocklists does not apply here because zkagent never reads DG2
  — DG1 + SOD fit in short APDUs with chaining. The real discriminator is
  attestation quality. Huawei and China-market ROMs excluded outright (no Play
  Services, non-Google attestation root). The rationale is debuggability rather
  than capability; a non-Pixel second device is an M2 concern.
- **`chiproof@0.0.0` reserved on npm** (published by the owner as a manual step
  per NO-GO #8; source at `packages/chiproof/`) — two files, no code, README
  stating plainly that it does nothing and that this is not zero-knowledge.
  **Apache-2.0, matching the repo** — correcting the standing defect in the
  published `zkagent@0.0.0` placeholder, which is MIT and must be fixed or
  deprecated when NO-GO #8 is next revisited.
- **D18 — sequencing: the agent layer is not designed, discussed or specified
  further until the age-verification leg is finished.** Rung 2 stays decided,
  bounded and not started; reopening it before rung 1 ships is refused by
  default, including on a passing owner request. The rationale is this session's
  own evidence rather than principle: Q18 sat undetected through four PRD
  revisions and surfaced only when the read path had to be written out
  concretely. More design is currently producing more surface, not more
  certainty.
- **D13 extended: the agent layer is mode B only, structurally.** A delegation
  cert hangs off a persistent human root — an agent that cannot be recognised
  cannot be revoked, and mode A emits nothing to bind a cert to. Recorded so
  nobody later attempts agent delegation on the anonymous path and discovers
  mid-build that it cannot work.
- New companion document `docs/02-engineering/zkagent-design.md` — the design
  and disclosure model. Semi-technical description of how the product works
  rather than what gets built: the sealed-envelope framing and the two standing
  properties (**narrow by default, fresh by default**); the read path from MRZ
  key through SOD → DS → CSCA verification; **why the window is enforced by the
  phone and not by the chip** (an ICAO chip has no selective disclosure — DG1 is
  one signed blob, so reading a birth date means reading the name and document
  number too, which is precisely what makes the attestation load-bearing rather
  than a bonus signal); exactly what crosses the wire in each mode; the
  three-outcome verdict; an honest zero-knowledge comparison; the legal posture;
  the operator configuration surface with the reasoning behind each knob; the
  passports-then-ICAO coverage path; and a limitations section. Description, not
  commitment — the PRD wins on any conflict.
- **Q17 legal posture fixed (owner, 2026-08-07): demonstration, not
  certification.** The project is not contesting the regulatory requirement and
  is not seeking certified-provider status; the point being demonstrated is that
  the privacy properties the rules reach for are obtainable with far less
  machinery than the official route requires — no wallet, no attestation
  provider, no batch issuance. This lowers what must be proven (a demonstration
  must be honest, not certified) without removing the question: legal
  sufficiency in a named jurisdiction still gates any shift from demonstration
  to pitch.
- **New Q18 — chip cloning vs the uniqueness claim** (surfaced while writing the
  design companion; mode B only). Verifying the SOD is *passive* authentication:
  it proves the data was signed by the issuing government, not that the chip
  presenting it is the original. A replayed data dump would mint **the same
  zktag as the genuine document**, so a blocked human could re-present from a
  clone — which breaks blocking at its root. The defence is the chip's own
  challenge-response (Active or Chip Authentication), which is not universally
  present: AA is optional in ICAO 9303 and omitted by some issuers on privacy
  grounds. M0's chip inventory extended to report AA/CA support. Mode A is
  unaffected — with no identifier emitted there is nothing to impersonate.
- PRD restructured to v1.4 — the largest change since v1.0.
  - **D13 — disclosure has two modes, and the verifier must ask for the one it
    needs.** **Mode A (anonymous, default)** emits one bit and no identifier of
    any kind; two presentations by the same holder to the same service are
    unlinkable. **Mode B (pseudonymous, opt-in)** additionally emits the
    domain-scoped zktag, linkable within a service and unlinkable across
    services. Uniqueness and unlinkability are in direct tension and only one
    leg of the product needs each: age verification needs no pseudonym and
    emitting one is a pure privacy regression, while agent accountability is
    *defined* by recognising a returning human. Mode B must be requested
    explicitly, never inferred or silently upgraded, and mode-A payloads must be
    byte-shape identical whether or not the device has ever made a mode-B
    presentation.
  - **EU Age Verification Blueprint checked** (`ageverification.dev`, 2026-08-07)
    and it settles the linkability question in the opposite direction from the
    common assumption: linkability is **not** required by the EU approach, it is
    what that approach engineers against — single-use attestations removed from
    the batch after one use, batch issuance mandated to prevent linkability, and
    `ValidityInfo` timestamps deliberately coarsened because a clock field is a
    correlator. The batch machinery exists because their wallet must round-trip
    to an attestation provider and cannot do ZK; zkagent has no issuer in the
    path, so per-presentation freshness is free and needs no batching. Cited in
    PRD §12 with the normative text quoted. **Not claimed:** any legal standing
    from meeting those properties (Q17).
  - **New FR9 — unlinkability budget**, and **new Q15**: mode A's guarantee is a
    property of the *entire* payload including the attestation, not of our own
    fields. A device-unique attestation key, an unshared certificate
    intermediate, an OS/patch-level string or a precise timestamp each
    reintroduce linkability through the back door. Added as riskiest-assumption
    item 8 and gated behind a new milestone **M1b**, a black-box byte probe with
    a planted positive control (8een §7.3 method) that blocks M3. Until measured,
    mode A is a design intent and is to be described as one.
  - **New NO-GO #11 — no stable identifier in mode A**: not a zktag, not a device
    id, not a "rate-limit key", not "just for fraud detection". Recorded because
    that pressure will arrive and will sound reasonable every time.
  - **FR6 narrowed, not retired (D15).** Uniformity is required within one client
    build and mode; cross-client distinguishability is *accepted and is the
    mechanism*, because the trust list works by reading exactly the package name
    and signing-cert digest that distinguish clients. Safe only because of FR11:
    a published derivation means two clients produce the same zktag, so a visible
    client identity partitions the anonymity set, not the identity space. Cost
    written down rather than discovered — the anonymity set is "users of this
    client build in this mode", not "all zkagent users".
  - **New FR10 (D17) — adopter-held trust list**, and **new FR11 (D16) — the
    derivation is a published, versioned spec** (`zkagent-derivation/1`). Both are
    prerequisites of the borrowable core: without FR10 an open core is a forgeable
    one; without FR11 two conformant clients fork the identity space and blocking
    silently breaks. We publish no list and run no registry (NO-GO #3 extended to
    say so explicitly).
  - **D14 — `acceptedDocuments` is adopter-configurable and the default is
    greedy.** `k` has no cost in mode A: with no identifier emitted, a holder with
    three documents is not three identities but three ways to answer the same
    question. `k` is a real cost only in mode B, so a mode-B adopter needing k≈1
    narrows to passports and knowingly trades reach. NO-GO #5's "at most k" is
    therefore a mode-B claim.
  - **D12 — project/package split: project `zkagent`, package `chiproof`**
    (precedent: `8een` / `zk8een`). Verified available on npm 2026-08-07.
  - **D10 revised** — the mode-B secret ceiling becomes configurable (default 30,
    max 180 days) and freshness is *negotiated*: a verifier may state
    `max_scan_age_days` in the challenge and the presentation answers with one
    bit, never an age in days, which would be a fingerprint. Mode A is unaffected
    — it caches no secret.
  - **Structure**: document reorganised into two rungs — rung 1 (core: chip read,
    signature verification, both modes, verifier SDK, demo; M0–M3) is v1, rung 2
    (agent layer; M4–M5) is decided, specified and explicitly not started.
    Cross-document identity unification made an explicit non-goal and D9 narrowed
    accordingly. Q1 folded into Q14; Q2/Q5/Q6/Q9 closed out of the open-questions
    section into one-line summaries. New §2.1 states plainly why v1 is not
    zero-knowledge, alongside a standing claim-discipline rule in the header.
- M0 (planned): throwaway POC at the riskiest assumption — read a real
  passport chip, verify its government signature against a public masterlist,
  derive the same zktag across two scans. Evidence doc required before anything
  else is built.
- PRD amended to v1.1: M0 de-platformed (desktop USB PC/SC reader or Android
  via JMRTD — no Apple account/Mac needed); Q2 resolved as moot for M0; Q6
  added (Mac-less iOS build path, decided at M2).
- PRD amended to v1.3 (continued): **Q14 — attestation root is an open
  choice, not settled on Play Integrity.** Android Keystore hardware key
  attestation returns a vendor-rooted certificate chain carrying verified-boot
  state, patch level and the app's signing-certificate digest, with no runtime
  Google service call, no Play Console registration and no Play Services on
  device — so it works on GrapheneOS and de-Googled builds. Likely answer is
  both roots, key attestation primary. Three unknowns named as things to
  verify rather than assume: keybox extraction, OEM implementation quality,
  and revocation-list handling. Risk-register items 6 and 7 corrected
  accordingly — the Google dependency is a CA relationship under key
  attestation rather than a gatekeeper, and the GrapheneOS exclusion is a
  consequence of choosing Play Integrity alone, not an unavoidable cost.
- PRD amended to v1.3 (continued): two structural risks added to the
  riskiest-assumption register. **(6) We are not issuer-free** — identity is,
  attestation is not: Play Integrity is a Google-run service that can be
  gated, quota'd or withdrawn, and NO-GO #3 only forbids *us* running an
  issuer. Correct phrasing is "issuer-free identity, Google-dependent
  attestation." **(7) The users most aligned with the product are excluded by
  it** — GrapheneOS/CalyxOS and de-Googled devices fail Play Integrity
  permanently, and they are disproportionately the people who want anonymous
  personhood proof. No known mitigation at captcha-grade; stated rather than
  hidden.
- PRD amended to v1.3 (continued): **D11 — age threshold configurable, output
  stays one bit**, adopted verbatim from 8een D6 (`over_threshold`, default 18).
  Rejected the alternative of emitting a ladder of common thresholds at once —
  that buckets the holder's age instead of disclosing one bit. A proof of a
  threshold other than the one requested must be rejected, not accepted as
  close enough. **Q9 resolved** — cert handoff ships three paths (QR, LAN POST,
  file); the cert is signed so the channel needs no confidentiality. New
  **§13 Adoption risk**, plus **Q11** (age binary-search probing), **Q12**
  (passport-only coverage vs the age-verification positioning — blocks that
  pitch until answered), **Q13** (first-adopter path).
- PRD amended to v1.3: **D10 — the derived secret expires after 30 days**,
  renewable only by a fresh passport scan. Previously the secret was cached
  in StrongBox indefinitely, which meant borrowing someone's passport for one
  scan bought their identity permanently, undetectably and unrevocably. A
  30-day ceiling converts one-time possession into recurring possession.
  Enforced in the enclave, not by app-side date checks. Accepted cost: the
  passport must stay accessible. Recorded as FR1 and D10.
- PRD amended to v1.2:
  - D2 flipped to **Android-first** (JMRTD + Play Integrity). Google Play is
    $25 one-time against Apple's $99/yr, Android builds on the owner's Linux
    box with no Mac, and test builds sideload without an entitlement gate.
    iOS deferred until user demand justifies the cost. Q5 and Q6 resolved by
    this decision; iOS moves from goal to non-goal.
  - `tag` renamed **`zktag`** throughout. RFC 9421 defines its own `tag`
    signature parameter meaning "which protocol does this signature belong
    to" — the collision would have been silent and wire-visible.
  - New **FR8**: RFC 9421 wire mapping — `alg="ed25519"`, `keyid` =
    agent-pubkey thumbprint, RFC `tag` reserved for protocol labelling and
    never carrying the zktag, signature `expires` ≤ cert `expiry`, cert in
    its own header. An off-the-shelf 9421 verifier must accept our signature
    unpatched.
  - New **Q7** (M2): device-assurance tier vs FR6 uniformity — exposing
    StrongBox/strong-integrity tiers is itself fingerprinting metadata.
  - New **Q8** (M4): remaining 9421 mapping choices — cert header, mandatory
    covered components. Cert is sent inline per request; URL-reference
    rejected for v1 as it collides with NO-GO #1/#3.
  - New **Q9** (M4): phone→agent cert handoff UX (QR / paste / localhost
    POST). Added to the M4 deliverable and checkpoint.

## [0.3.0] — 2026-08-31

- **`chiproof@0.3.0`** — Two spec gaps found by the M2 handoff spike, closed
  together (owner-approved 2026-08-31). **Plugs can now bind the presented
  zktag:** `PlugCtx` gains `zktag` (`string | null` — `null` at tier A, which
  refuses a zktag by D21), and a plug may declare `binds.zktag: true`,
  mirroring `binds.claim`. Evidence bound to one zktag now dies under another
  — a zktag-swapped presentation is a real no (`ok:true, allowed:false`), not
  a shrug — while a zktag-binding plug on a tier-A presentation yields
  `ok:false, reason:'evidence_zktag_unavailable'` (could not check, never a
  "no"), and `binds.zktag:true` with `tierCeiling:'A'` is refused at
  registration as a plug that could never run. **`evidence.require` is now
  optionally per-tier:** `{A?, B?, C?}` lets one verifier instance serve a
  bare tier A (D27) beside an evidence-required tier B (D30), replacing the
  two-instance workaround the spike needed; the 0.2.0 plain-array form keeps
  its instance-global semantics unchanged and `routeEvidence` accepts both
  (new export `normalizeRequire`). 126 tests (116 pre-existing, zero
  regressions), typecheck and a strict-TS adopter gate green; no other
  runtime change.

## [0.2.0] — 2026-08-30

- **`chiproof@0.2.0`** — Types: `verify()` returns `Verdict`; `issueChallenge()`
  typed (options + `Challenge`) — 0.1.0's `.d.ts` declared both as `object`,
  breaking every TypeScript adopter. No runtime change. Added a shared
  `IssueChallengeOptions` typedef (`src/types.js`) and wired both
  `challenge.js`'s `issueChallenge` and `createVerifier(...).issueChallenge`
  to it and to the existing `Challenge` typedef, replacing the widened
  inline `object` annotations. The adopter gate (`ci.yml`/`publish.yml`) now
  also asserts `challenge.nonce: string` reads and rejects
  `issueChallenge({tier: 'Z', ...})` via `@ts-expect-error`, so either bug
  regressing fails the build.

## [0.0.0] — 2026-07-26

- npm name reservation placeholder published (`zkagent@0.0.0`) — no functional code.
- PRD v1.0 drafted (`docs/01-product/zkagent-prd.md`): owner decisions D1–D8 signed,
  NO-GO table, milestones M0–M5, riskiest-assumption register.
- Repo initialized.
