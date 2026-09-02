# M2 — device evidence for finding #5 fix (LifecycleFence, Pixel 6a, 2026-09-02)

**Status**: source record for an UNCOMMITTED working-tree change at the time of writing (new
`LifecycleFence.kt`, new `LifecycleFenceTest.kt`, modified `MainActivity.kt`; the orchestrator
commits after verifying this evidence doc, per the ownership-refactor process). This file is the
evidence `.claude/remember/findings.md` #5's status update and `docs/product/zkagent-prd.md`'s D57
exit-criterion annotation cite; it mirrors `M2-D58-STEP1/2/3/4-EVIDENCE.md`'s structure and
value-free rule.

**Rule for this file (carried from `M0-EVIDENCE.md` / `M2-D50-D53-EVIDENCE.md` /
`M2-D55-D56-EVIDENCE.md` / `M2-D58-STEP1/2/3/4-EVIDENCE.md`)**: no PII values, ever — field names,
verdict strings, timings, hashes/truncated transaction identifiers, and exception text only. No MRZ
field, name, date of birth, document number, raw zktag, nonce, public key, or signature appears
anywhere in this file.

---

## What changed (uncommitted)

Findings.md #5 (zero async-cancellation discipline — five unfenced `Thread{}` sites, no
`onDestroy`/cancellation/status check anywhere in the module) is FIXED, device-proven both sides.

- **Mechanism, owner-chosen over a coroutines/`lifecycleScope` rewrite**: a pure `LifecycleFence`
  class (`alive` flag, `retire()`, `passes()`), held as a **per-Activity-instance field**
  (`MainActivity.kt:265`), retired in a new `onDestroy` (`MainActivity.kt:439-442`) — the module's
  first-ever `onDestroy`. Before this pass, `grep -rn "onDestroy\|isFinishing\|isDestroyed" app/src`
  returned ZERO hits. No new Gradle dependencies.
- **Fence semantics, decided explicitly by the owner**: a fence drops a main-thread landing; it
  never cancels or aborts in-flight work — aborting a `direct_post` already in flight would be
  worse than letting it complete unobserved.
- **11 fenced sites**: 10 `runOnUiThread` blocks plus `ReadTask.onPostExecute` (an `AsyncTask` —
  same defect class, NOT one of the five `Thread{}` sites finding #5 originally named; the owner
  explicitly put it in scope for this pass). In `ReadTask.onPostExecute` the fence sits AFTER
  `paneState.readFinished()`/`showPane()`, preserving the D55/D58-step-2 invariant that those two
  calls run on every exit path regardless of fence state.
- **Logging, added in a follow-up**: a log line at every drop (11 sites) plus one at retirement (12
  messages total), because a silent drop is indistinguishable from a crash — the same defect class
  as this project's earlier un-logged `reportView.text` write (findings #7). All 12 messages are
  static strings with ZERO interpolation — no document data. Site `MainActivity.kt:2131` is the
  only `Log.w`; every other site is `Log.i`.
- Unit tests 180 → 184 (new `LifecycleFenceTest`, 4 cases, including one asserting two independent
  `LifecycleFence` instances do not affect each other), 0 failures, JUnit XML.

**Orchestrator-verified at source (not re-verified here, not contradicted)**: `LifecycleFence` is
per-instance, not a singleton; all 11 guard sites are present; `applySessionDisplay` remains the
sole writer of the four `SessionDisplay` view properties (only `MainActivity.kt:451-454` assign
them — D58 step 4's invariant, unaffected); D55's read-in-progress-cleared-on-every-exit-path
ordering is intact. Diff is 128 insertions / 4 deletions; every non-comment added line is fence
plumbing or a log line.

---

## How verified — device run (Pixel 6a, real NL ID card, verifier spike `spikes/m2-handoff` on
`127.0.0.1:8787`, 2026-09-02)

Six tests.

### T1 — happy path, 3 runs, all verifier-cross-checked

Transactions `bfiJmaKYwC2BZdVs`, `DKV9yFHAStKxDmK7`, `KvQ7aODjy4MTKW4-` each returned `status: done`,
tier B, `ok:true allowed:true`, `reason=evidence-verified`, `evidence=["sig-p256/1"]`,
`attester=matched`.

**Mistracking note, same shape as D58 step 3's**: the link the orchestrator fired
(`9OUnKZYBihzU4FF9`) stayed `pending` — the owner opened their own fresh link from the site,
superseding it. Caught only by checking the verifier's own state, not the phone's self-report.

### T2 — the singleton trap (the most important result)

Two Activity recreations were forced in ONE process (pid 24553), then the owner ran a full mode-B
mint on the third instance:

```
14:30:08.920  M2 lifecycle: fence retired (onDestroy)
14:30:11.069  M2 lifecycle: fence retired (onDestroy)
14:31:05.967  M2 stage: handoff captured, verifying request object
14:31:41.462  mint_gate: MET
14:31:44.724  mint: OK / verdict: PASS (minted)
```

Zero `fence closed` lines during the run. Verifier: `PxiO_mu_QA9JYlS4` done, tier B,
evidence-verified.

**Why this matters**: a unit test can assert two `LifecycleFence` objects are independent but
cannot prove `MainActivity` constructs a fresh one per instance — that is a wiring property, not a
unit-testable one. A process restart (closing/reopening the app) can NEVER test this, because a
new process gives a fresh JVM; the owner's first attempt did exactly that (pid 24361 → 24553) and
had to be redone with in-process recreation instead.

### T3 — drop mid-verification (`MainActivity.kt:833`)

```
14:14:41.754  fence retired (onDestroy)
14:14:43.636  fence closed — dropped handoff verification outcome     [instance #1]
14:14:44.829  handoff request object verified                        [instance #2, landed normally]
```

### T4 — drop mid-read (`ReadTask.onPostExecute`, `:1414`)

```
14:34:09.656  MRZ input first attempt this session
14:34:10.742  fence retired (onDestroy)                    [1.09s into the read]
14:34:10.800  restored report/log across Activity recreation (text=true, log_entries=2)
14:34:11.954  fence closed — dropped read completion handling (report/dialog/mint start)
```

No biometric prompt appeared (owner-confirmed) — so `continueAfterRead` never started and NO mint
occurred; nothing left the device. Post-run UI verified NOT stranded via a filtered accessibility
read (tabs and buttons only, never a screenshot, because the scan form renders real document
fields): `'SCAN' selected=true`, `'LOCK MODE & SCAN' enabled=true`. That is the D55 stranding
invariant surviving a mid-read recreation.

### T5 — drop mid-mint (the consequential one) (`:2131`)

```
14:40:15.139  handoff direct_post -> http://127.0.0.1:8787/wallet/direct_post
14:40:15.759  fence retired (onDestroy)                    [0.62s later]
14:40:21.152  handoff direct_post response http_status=200
14:40:21.153  fence closed — dropped post-mint session clear/display refresh
14:40:21.153  W  fence closed — dropped mint report/confirmation (a COMPLETED result: evidence already left the device, nothing recorded or shown)
```

Verifier: `bllTnDusyX9bISKQ` done, tier B, ok/allowed true, evidence-verified, `sig-p256/1`. Phone
side: the ONLY report block emitted in that run was the pre-mint interim one at 14:40:11.450; the
final `verdict: PASS (minted)` block was never written. Post-run UI clean (unlocked, Lock re-armed)
— no stale-session regression.

One T5 attempt FAILED before reaching the mint: `PACE unavailable (AccessDeniedException)` then
`org.jmrtd.AccessDeniedException: Mutual authentication failed ... (SW = 0x6985: CONDITIONS NOT
SATISFIED)` at 14:38:16, verdict FAIL, 0.6s after MRZ input. That is D56's known
access-establishment signature for wrong MRZ input; the fence was NOT involved and the failure was
reported normally. The suspected cause is the input-focus defect recorded below as a new open
question.

---

## Stated limitations — recorded plainly, not softened

- T3's and T5's windows were widened ARTIFICIALLY by a test-harness TCP delay proxy in front of the
  verifier (`adb reverse tcp:8787` re-pointed to a local proxy that delayed `/wallet/request.jwt/`
  and `/wallet/direct_post` responses by 3s and 6s respectively). The app and the verifier spike
  were both UNMODIFIED. Natural windows are ~28ms (verification) and ~12ms (`direct_post`) on
  localhost, so these drops are real but timing-dependent and hard to hit in normal use. The proxy
  is a scratchpad script, not in the repo.
- Recreation was forced with `settings put system font_scale 1.15` then `1.0` (auto-rotate is off
  on this device); restored to `1.0` afterwards.
- A recreation does NOT fire while the screen is dozing — the config change is deferred until the
  Activity is visible again. This cost one failed attempt and is worth recording as a
  device-testing note for future sessions on this host.
- T4/T5 exercised the `av://` intent path only; the QR-scan/manual-paste path was not exercised on
  device this session.

---

## Findings and questions this run produced

1. **Finding #5 → FIXED, device-proven.** D57 exit criterion (2) ("every async writer is fenced
   against Activity lifecycle") is now MET. **The freeze does NOT lift**: criterion (3) ("no OPEN
   finding at consequence HIGH") is not met — findings #10/#11 remain open-but-mitigated. See
   `.claude/remember/findings.md` #5's status update and `docs/product/zkagent-prd.md` D57.
2. **New finding #16 — mint-report loss after Activity destruction.** Anchor `MainActivity.kt:2131`
   (T5 above). See `.claude/remember/findings.md` #16 for the full statement.
3. **Q38 (log lifetime) — device-confirmed answer.** `ReportLog` survives Activity recreation via
   saved instance state; NOTHING survives process death — there is no disk persistence at all.
   Evidenced by the owner's own observation ("logs don't survive after I close the app") plus three
   process-id changes across this session (23196 → 24123 → 24361 → 24553). See
   `docs/product/zkagent-prd.md` §11 Q38's status update.
4. **New open question Q47 — input focus steals back to the document-number field.** While typing
   the date fields, the cursor jumps back to the document-number field, corrupting the entered MRZ.
   A CORRECTNESS defect, not a styling preference — it has a measured cost: it is the suspected
   cause of the `SW 0x6985` read failure at 14:38:16 above, which destroyed one T5 run. Deferred
   under D57 alongside Q43-Q46; sits on the same screen as Q46's "Passport number" label defect.
5. **New open question Q48 — the three installed reader apps are indistinguishable on the
   launcher.** `com.zkagent.scanner` (0.1.0), `com.tananaev.passportreader` (3.4, the M0 fork),
   `com.zkagent.m2sessionpoc` (0.1-spike). Verified fact: ONLY `com.zkagent.scanner` declares the
   `av://` VIEW filter — checked with `cmd package query-activities -a android.intent.action.VIEW
   -d 'av://authorize'`, which resolved to `com.zkagent.scanner` alone — so intent routing is
   deterministic on this device and this is a human-identification problem, NOT a security one.
   This does NOT bear on findings #10/#11: a hostile third-party app registering `av://` would
   still produce a chooser, and that intent-hijack finding is unchanged. Suggested shape (owner's,
   not decided): distinct launcher labels carrying the version.

---

## Caveats, stated plainly

- T3's and T5's windows were only reachable via an artificial delay proxy; natural windows on this
  network path are single-digit-to-low-double-digit milliseconds. The defect class is real; hitting
  it without the proxy is much less likely.
- The QR-scan/manual-paste handoff path was not exercised this session — T4/T5 are `av://`-only.
- Finding #16 is OPEN, unfixed BY DECISION — it is a disclosure/product question for the owner, not
  a code defect left undone.
- Q47's causal link to the observed `SW 0x6985` failure is the suspected cause, not a confirmed
  root cause — no isolated repro of the focus-steal-causes-bad-MRZ-input chain was captured this
  session.
- The `LifecycleFenceTest` unit tests and this device run cover the fence mechanism; they do not
  cover findings #4, #6, #10, or #11, all of which remain OPEN/mitigated and untouched by this pass.

---

## What this run did and did NOT establish

**Did establish:**
- `LifecycleFence` is per-Activity-instance, not a singleton — proven by T2, which a unit test
  alone could not have proven.
- All 11 fenced sites correctly drop a stale main-thread landing without aborting the in-flight
  work behind it (T3, T4, T5).
- A dropped mint report is a real, device-reproduced loss (T5): evidence left the device and the
  verifier recorded a full tier-B verdict, while the phone showed and logged nothing beyond the
  single `Log.w` line.
- `ReportLog`'s survive-recreation/lose-on-process-death boundary (Q38), device-confirmed via three
  process-id changes.
- Deterministic `av://` intent routing on this device (Q48), verified via `query-activities`.

**Did NOT establish:**
- Any resolution of findings #4, #6, #10, or #11 — all unaffected by this pass.
- Any decision on finding #16 (disclosure/product question, left to the owner) or on Q38 (the owner
  has not decided whether a completed presentation needs a durable on-device record).
- A confirmed root cause for the `SW 0x6985` failure behind Q47 — the input-focus link is
  suspected, not proven.
- Natural (non-proxied) reproduction of T3/T5's drop windows.
- Any exercise of the QR-scan/manual-paste handoff path under fence conditions.

---

**No PII values appear anywhere above.** All quoted logcat lines, transaction identifiers, and
verifier states are value-free by construction — stage names, boolean/status fields, truncated
transaction IDs, and timings only — checked against this file's own rule and the project standard
it inherits from `M0-EVIDENCE.md` / `M2-D50-D53-EVIDENCE.md` / `M2-D55-D56-EVIDENCE.md` /
`M2-D58-STEP1-EVIDENCE.md` / `M2-D58-STEP2-EVIDENCE.md` / `M2-D58-STEP3-EVIDENCE.md` /
`M2-D58-STEP4-EVIDENCE.md` before inclusion.
