# Session Stash — M2 fence pass, branch review, chiproof 0.4.0 release (2026-09-02)

Project: zkagent · Owner: hamr · Branch: m2-build (now merged and deleted).
Continuation of `.claude/stash/m2-d58-refactor-complete.md` (HEAD was
a9f5143). Main session ran as orchestrator only — never wrote code or docs
itself; every change was made by a Sonnet subagent and independently
verified by the orchestrator before committing. Model: Opus 5.

## Opening question

The owner asked what "fencing" meant in the D57 freeze's exit criterion.
Answer given: a fence is a barrier between a background thread and a dead
Activity — cancellation on destroy, plus a guard at the landing site.
`runOnUiThread` is NOT a fence: it changes thread, not liveness.

## Owner decisions this session, in order

1. Fence the five threads first, then review once (rather than reviewing
   first and fencing later).
2. Mechanism: a pure `LifecycleFence` class with no new dependencies,
   chosen over a coroutines/`lifecycleScope` rewrite.
3. `ReadTask.onPostExecute` (an AsyncTask, not one of the five `Thread{}`
   sites finding #5 named) IS in scope for the same pass.
4. "don't commit when done" — held throughout the build; commits only when
   explicitly authorised later.
5. Record the five device-pass items, commit, then run /branch-review.
6. Validate the review's blocker, fix it, verify no regression.
7. Close the branch with the freeze carried forward (recorded as D60);
   stated reason: "i want to pause this and move on, otherwise its a
   perpetual delay."
8. Add a CHANGELOG entry, then push/PR/CI/merge/tag.
9. Reversed an earlier "hold chiproof publish" and authorised publishing
   chiproof 0.4.0; chose tag name `v0.4.0`.

Orchestrator-decided (not the coder's call), stated in the brief to the
coder: fence semantics are RENDER-ONLY — a fence drops a main-thread
landing, it NEVER cancels or aborts in-flight work, because aborting a
`direct_post` already in flight would be worse than dropping its report.

## Commits made this session (all on m2-build)

- `b8e0e05` fix(apps/scanner): D57 exit criterion 2 — fence async writers
  (finding #5)
- `2a4db81` docs: fence device evidence; finding #5 fixed, #16 opened;
  Q38 answered, Q47/Q48 opened (PRD v1.43)
- `72e0b2c` fix(apps/scanner): fence the BiometricPrompt callbacks
  (finding #5 gap)
- `9584bc8` docs: correct the fence completeness claim; 13 sites, not 11
  (PRD v1.44)
- `5accddb` docs: D60 — branch close-out, D57 freeze carried forward
  (PRD v1.45)
- `67697ff` chore: untrack the two /branch-review working artifacts
- `d464d79` docs(changelog): record the apps/scanner async-fence pass

Merged to main as `77bae4c` (squash, PR #4, admin bypass — owner-authorised).
Branch deleted.

## The fence implementation

Before it, `grep -rn "onDestroy|isFinishing|isDestroyed" app/src` returned
ZERO hits — the module had no lifecycle guard of any kind. New pure
`LifecycleFence.kt` (`alive` flag, `retire()`, `passes()`), held as
`private val fence = LifecycleFence()` at `MainActivity.kt:265` — a
per-Activity-INSTANCE field, explicitly NOT a singleton/companion, because a
shared fence retired by a dying instance would permanently block the next
one. Retired in a new `onDestroy` at `:439-442`, the module's first-ever
`onDestroy`. Initially 11 fenced sites (10 `runOnUiThread` blocks + one
`ReadTask.onPostExecute`), later 13. In `ReadTask.onPostExecute` the fence
sits AFTER `paneState.readFinished()`/`showPane()`, preserving the
D55/D58-step-2 invariant. A follow-up added a logged line at every drop
plus the retirement (12, later 14, static zero-interpolation messages)
because a silent drop is indistinguishable from a crash — the same defect
class as this project's earlier un-logged `reportView.text` write. `:2131`
is the only `Log.w` (the one drop where evidence had already left the
device); the rest are `Log.i`. Unit tests 180 → 184, verified by the
orchestrator from the JUnit XML, not agent prose.

## Device pass

Pixel 6a, real NL ID card, verifier spike `spikes/m2-handoff` on
127.0.0.1:8787. Six tests.

- T1 happy path, 3 runs, all verifier-cross-checked done/tier B/
  evidence-verified/sig-p256/1: transactions `bfiJmaKYwC2BZdVs`,
  `DKV9yFHAStKxDmK7`, `KvQ7aODjy4MTKW4-`. NOTE: the link the orchestrator
  fired (`9OUnKZYBihzU4FF9`) stayed `pending` — the owner opened their own
  from the site, superseding it. Same mistracking risk as D58 step 3;
  caught only by checking the verifier, not the phone.
- T2 singleton trap, the most important result: two Activity recreations
  forced in ONE process (pid 24553), then a full mode-B mint on the third
  instance. `14:30:08.920 fence retired`, `14:30:11.069 fence retired`,
  `14:31:44.724 mint: OK / verdict: PASS (minted)`, ZERO `fence closed`
  lines during the run. Verifier `PxiO_mu_QA9JYlS4` done tier B. A unit
  test can assert two fences are independent but cannot prove
  `MainActivity` constructs a fresh one per instance — that is a wiring
  property. The owner's first attempt closed and reopened the app instead
  (pid 24361 → 24553), which can NEVER test this because a new process is
  a fresh JVM; it had to be redone.
- T3 drop mid-verification: `14:14:41.754 fence retired`, `14:14:43.636
  fence closed — dropped handoff verification outcome` (instance #1),
  `14:14:44.829 handoff request object verified` (instance #2, landed
  normally).
- T4 drop mid-read: `14:34:09.656 MRZ input`, `14:34:10.742 fence retired`
  (1.09s into the read), `14:34:11.954 fence closed — dropped read
  completion handling`. No biometric prompt appeared, so
  `continueAfterRead` never started and no mint occurred. Post-run UI
  verified NOT stranded via a filtered accessibility read (tabs/buttons
  only, never a screenshot): `'SCAN' selected=true`, `'LOCK MODE & SCAN'
  enabled=true`.
- T5 drop mid-mint: `14:40:15.139 handoff direct_post ->`, `14:40:15.759
  fence retired` (0.62s later), `14:40:21.152 direct_post response
  http_status=200`, `14:40:21.153 W fence closed — dropped mint
  report/confirmation`. Verifier `bllTnDusyX9bISKQ` done tier B ok/allowed
  true. The only report block emitted was the pre-mint interim one at
  14:40:11.450; the final `verdict: PASS (minted)` was never written.
- T6 log lifetime: answered by the owner's own observation — nothing
  survives process death.

Test-harness limitations, recorded not softened: T3/T5's windows were
widened by a scratchpad TCP delay proxy in front of the verifier (`adb
reverse tcp:8787` re-pointed at a local proxy delaying `/wallet/
request.jwt/` and `/wallet/direct_post` by 3s and 6s). App and verifier
both UNMODIFIED. Natural windows ~28ms (verification) and ~12ms
(direct_post) on localhost. Proxy is a scratchpad script, not in the repo.
Recreation forced with `settings put system font_scale 1.15` then `1.0`
(auto-rotate off); restored to 1.0. A recreation does NOT fire while the
screen is DOZING — the config change is deferred until the Activity is
visible; this cost one failed attempt. One T5 attempt failed before the
mint with `PACE unavailable (AccessDeniedException)` then
`org.jmrtd.AccessDeniedException: Mutual authentication failed ... (SW =
0x6985: CONDITIONS NOT SATISFIED)` at 14:38:16, verdict FAIL, 0.6s after
MRZ input — D56's known access-establishment signature for wrong MRZ
input; the fence was not involved; suspected cause is the Q47 focus
defect.

## /branch-review

Mid tier, 44 commits, at `2a4db81`, returned BLOCKED with one High
finding: `onAuthenticationError` inside the `BiometricPrompt.
AuthenticationCallback` called `emitReport` then
`showBlockingOutcomeDialog`, which does `AlertDialog.Builder(this).show()`
against the Activity, with no fence check. The reviewer marked the crash
MECHANISM uncertain — whether androidx.biometric 1.1.0 redelivers
post-onDestroy.

The orchestrator settled that uncertainty in the library's bytecode
(`~/.gradle/caches/modules-2/files-2.1/androidx.biometric/biometric/1.1.0/
*/biometric-1.1.0.aar`, `classes.jar`, via `/home/hamr/opt/
jdk-21.0.12.1+1/bin/javap`) and the result STRENGTHENED the finding:
`BiometricPrompt$ResetCallbackObserver.resetCallback()` is annotated
`@OnLifecycleEvent(ON_DESTROY)` and nulls
`BiometricViewModel.mClientCallback`, after which `getClientCallback()`
substitutes a no-op default — so a destroyed host is normally never called
back. BUT `addObservers()` is invoked ONLY from the library's two
`Fragment` constructors; both `FragmentActivity` constructors call neither
`addObservers` nor `getLifecycle`. `MainActivity : AppCompatActivity`
(`:186`) uses `BiometricPrompt(this, ContextCompat.getMainExecutor(this),
callback)` at `:1779` — the FragmentActivity overload. The protection is
one constructor overload away from being active, and is not.

## Root cause of the miss, and the lesson

`b8e0e05` enumerated its fence targets by grepping `runOnUiThread` — a
syntactic form — rather than by the hazard predicate. A
`BiometricPrompt.AuthenticationCallback` is a main-thread landing in
exactly the same sense but does not match that text. LESSON: enumerate
async landings by the predicate — "a callback the framework may deliver
late that touches Activity-owned UI or state" — never by grepping a
syntactic form.

## Fix (72e0b2c)

The same guard added to `onAuthenticationError` and (defence-in-depth,
placed AFTER its existing diagnostic log line so it is never suppressed)
`onAuthenticationFailed`. `onAuthenticationSucceeded` unchanged — it only
starts a `Thread{}` whose landings are already fenced. A sweep of every
other non-`runOnUiThread` main-thread landing found NO further gaps:
click/editor/long-click listeners (alive by construction),
`registerForActivityResult` (AndroidX ActivityResultRegistry IS
lifecycle-aware, unlike the BiometricPrompt path), the `DatePickerDialog`
DialogFragment, NFC via `onNewIntent`, and
`DeviceKey.exportDevAttesterPublicKeyIfPresent` (no Activity landing exists
to fence). Not unit-testable: Activity-resident callback, module runs
`isReturnDefaultValues = true`; no fake or test-only seam was added. Tests
stayed 184/0 on a clean `--rerun-tasks` build, exit 0.

## Re-review at 9584bc8

Range `2a4db81..HEAD`, returned READY. It did NOT take the orchestrator's
bytecode claim on trust — it re-derived it independently with javap and
agreed, upgrading the mechanism from plausible to real. It also re-ran the
build itself, re-grepped the `applySessionDisplay` sole-writer invariant,
and re-ran the sweep. One non-blocking ledger bullet added:
`LifecycleFence.kt`'s class-doc thread-safety proof enumerates only two
safe-read forms and is stale since `72e0b2c` added a third.

## A structural snag specific to this repo, and its fix

`.claude/remember/` is TRACKED here by design (LIBRARY_CONVENTIONS §7
exemption), but `/branch-review` writes `last-review.md` and
`fix-ledger.md` on every run. That dirties the tree (failing `/release`
Phase 0), and committing them moves HEAD past the SHA the review just
recorded (failing Phase 0.5, which names the fix ledger explicitly and
allows no exception) — the cycle never converges. `/release` assumes
`.claude/` is gitignored, so this never arises normally. Fixed in
`67697ff` by narrowly gitignoring ONLY those two review artifacts; project
memory (`MEMORY.md`, `findings.md`, `AGENT_RULES.md`,
`LIBRARY_CONVENTIONS.md`, `.claude/stash`) stays tracked, verified with
`git check-ignore -v`.

## Findings ledger at close

#5 FIXED, device-proven, with an appended correction note stating plainly
that criterion (2) was NOT actually met between `b8e0e05` and `72e0b2c`
and IS met from `72e0b2c` onward. NEW #16: mint-report loss after Activity
destruction — a completed delivery whose Activity died before
`direct_post` resolved still posts (verifier records a full tier-B
verdict) while the report and confirmation are dropped, so nothing is
written to `ReportLog` and nothing is shown; render-only fencing did NOT
create this loss (unfenced it landed in a dead instance's `ReportLog`,
never restored) — it made a silent loss explicit. OPEN by owner decision, a
disclosure/product question. Still OPEN and blocking the freeze: #10, #11
(HIGH, mitigated not closed). Also open: #4 (narrowed), #6, #8.

## Questions

Q38 ANSWERED by device evidence, not decided: `ReportLog` survives
Activity recreation via saved instance state; nothing survives process
death (three process-id changes observed: 23196 → 24123 → 24361 → 24553).
Q47 OPENED: input focus steals back to the document-number field while
typing dates, corrupting entered MRZ — a CORRECTNESS defect with a
measured cost (suspected cause of the `SW 0x6985` failure that destroyed
one T5 run). Q48 OPENED: the three installed reader apps
(`com.zkagent.scanner` 0.1.0, `com.tananaev.passportreader` 3.4,
`com.zkagent.m2sessionpoc` 0.1-spike) are indistinguishable on the
launcher; verified via `cmd package query-activities -a
android.intent.action.VIEW -d 'av://authorize'` that ONLY
`com.zkagent.scanner` declares the `av://` filter, so routing is
deterministic — human-identification, NOT security; does not bear on
#10/#11.

## D60 (PRD v1.45)

Branch close-out: the D57 freeze is CARRIED FORWARD, not lifted and not
abandoned; clearing it is the FIRST work item of the next module. Criteria
(1) and (2) MET as of `72e0b2c`; (3) NOT met. A deferral forecloses scope
rather than adding any, so NO-GO #10's gate is satisfied by the row
itself.

## Release outcome

`/release` stopped at Phase 3 and escalated the chiproof
local-ahead-of-published gap (0.4.0 local vs 0.3.0 published) rather than
resolving it, which was correct. Owner then authorised publishing.
Sequence run, every exit code read off the bare command: push 0; PR #4
created; `gh pr checks 4 --watch` exit 0, 4/4 pass; `gh pr merge 4
--admin --squash --delete-branch` exit 0 (admin bypass of `main`'s
protection, explicitly owner-authorised, flagged at the moment of
running); tags `v0.4.0` and `chiproof-v0.4.0` pushed; `gh workflow run
publish.yml --ref main`, run 33645463272, `gh run watch --exit-status`
exit 0, including the "Types are usable BY AN ADOPTER" gate. Verified
live: `npm view chiproof version` → **0.4.0** (was 0.3.0); tarball 28
files, 60.6 kB, `types/verdict.d.ts` present.

## Orchestrator errors made and corrected this session, worth recording

1. Proposed the tag name `v0.4.0` WITHOUT first checking the repo's
   existing tag convention, which is `chiproof-vX.Y.Z`
   (`chiproof-v0.1.0`/`v0.2.0`/`v0.3.0` all exist). `chiproof-v0.4.0` was
   added afterwards so the convention holds, but a stray `v0.4.0` tag now
   also points at `77bae4c`. Deleting it (`git push --delete origin
   v0.4.0`) was offered and NOT done — awaiting the owner's word. STILL
   OPEN.
2. Verified the fence pass's completeness the same way the coder
   enumerated it — by grepping `runOnUiThread` — and therefore missed the
   same gap. The review caught it. See the lesson above.
3. Inspected `dumpsys package com.tananaev.passportreader` (the M0
   spike's namespace) and briefly believed a fresh install had failed,
   before realising the applicationId is `com.zkagent.scanner`.
4. Ran `pkill -f delayproxy.py`, which matched and killed its own shell
   (exit 144). The bracket trick `pkill -f "[d]elayproxy"` avoids it.

## Still open at close, for the next module

- Clear the D57 freeze — findings #10/#11 — as the first work item.
- The stray `v0.4.0` tag (see error 1).
- The CHANGELOG entry sits under `[Unreleased]` because the publish was on
  hold when it was written; now that 0.4.0 is published, that section
  arguably wants cutting into a `[0.4.0]` heading.
- One fix-ledger bullet (`LifecycleFence.kt` doc-drift). Its anchor
  snippet fails `git grep -F` because the line now wraps — the re-review
  confirmed the finding is still live and downgraded its own "anchor
  gone" conclusion rather than dropping a real item on a mechanical miss.
- VERIFICATION DEBT: `72e0b2c`'s BiometricPrompt guard has code/bytecode
  evidence ONLY, no device run. What would settle it: destroy the Activity
  while a biometric prompt is outstanding and confirm the landing is
  dropped with no `WindowManager$BadTokenException`. The QR-scan/
  manual-paste handoff path has also never been exercised under fence
  conditions on device.

## Environment/recipes (unchanged unless noted)

Build `JAVA_HOME=/home/hamr/opt/jdk-21.0.12.1+1 ./gradlew
:app:assembleRegularDebug :app:testRegularDebugUnitTest` from
`apps/scanner`; test counts always parsed from
`apps/scanner/app/build/test-results/testRegularDebugUnitTest/*.xml`,
never taken from agent prose. Logcat spec `adb logcat -s MainActivity:V
DeviceKey:I RequestTrust:I HandoffClient:I M2Masterlist:I`. Verifier spike
`LINK_SCHEME=av node server.mjs` on 127.0.0.1:8787, pid 79615, its stdout
in an older session's scratchpad `handoff.log` where the authoritative "tx
created"/"verdict transactionId=" lines live. `adb reverse tcp:8787
tcp:8787` must be redone after any adb restart. Reading UI state must be
filtered to specific nodes, never a screenshot or full dump, because the
scan form renders real document fields.
