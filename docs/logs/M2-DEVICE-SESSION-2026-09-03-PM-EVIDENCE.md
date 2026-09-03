# M2 — device session, 2026-09-03 afternoon: exit-criteria row 1 re-run on the real build

**Status**: source record for real-build device runs across three builds — `2525267` (installed
12:50), `039fee7` (installed 13:11, plus an uninstall+reinstall at 13:23), and `7f25f40` v0.2.0
(installed 13:47) — on the same Pixel 6a used for the morning session
(`M2-DEVICE-SESSION-2026-09-03-EVIDENCE.md`, checks 1–7). This is the PM continuation: the morning
page covered the Q47/D63/mid-read/`BiometricPrompt`/QR-paste/hostile-origin/native-camera-QR
checks; this page covers the exit-criteria row 1 re-run (reinstall stability, masterlist two-bucket
rule, mode A after mode B) plus two builds' worth of failures found and fixed live, and the mode A
bare-presentation fix (finding #21) exercised end to end for the first time on a real device.

**Rule for this file (carried from every prior evidence doc in this series)**: no PII values,
ever — field names, verdict strings, timings, hashes/truncated transaction identifiers, and
exception text only. **The owner pasted a raw zktag into chat during this session; it is not
reproduced anywhere in this file** — only the app's own `zktag_sha256_prefix` line (a value-free
truncated hash the app itself logs) appears below. All `av://` URLs are elided as `av://...`.

---

## Setup

Pixel 6a, package `com.zkagent.scanner`. Three builds installed in sequence: `2525267` at 12:50,
`039fee7` at 13:11 (with an uninstall+reinstall of the same build at 13:23, exercised deliberately
as the reinstall-stability check), and `7f25f40` (v0.2.0) at 13:47. Verifier spikes at
`127.0.0.1:8787` (main origin, morning session's default), `127.0.0.1:8788` (second origin, used
throughout this session), and `127.0.0.1:8789` (started fresh with `THRESHOLD=99` for the
under-threshold check). Logs read for this page:
`device-session-2.log` (12:54–13:47, builds `2525267`/`039fee7`) and `device-session-3.log` (13:47
onward, build `7f25f40`), plus verifier logs `handoff-8788.log` and `handoff-8789.log` — cited only
by `transactionId`/`tier`/`threshold`/`ok`/`allowed`/`reason`/`evidence`/`attester` fields, per this
file's own rule.

---

## 1 — Real-build mode B mint, US passport, BAC, against the 8787 spike (12:54)

12:54:47 MRZ input; 12:54:49 masterlist `588/588` CMS-verified, `passive_auth: ok=true allowed=true`,
mint gate MET, biometric requested. 12:54:53: `device_key: reused existing alias`,
`evidence_type: sig-p256/1`, `zktag_sha256_prefix (value-free, never the raw zktag): 8754ed80d9e1`,
`handoff: direct_post http_status=200 -> {"accepted": true}`, `verdict: PASS (minted)`.

**Result: PASS.** This is the first mint of the afternoon session, run before either failure below
was found, on the `2525267` build.

---

## 2 — Failures found on build `2525267`, both fixed and later device-confirmed

Two silent failures surfaced this session on the first build tested:

- **12:56 / 13:10** — the owner switched to the camera app mid-session (per §6.2 item 6, MRZ/session
  state wipes in `onStop()`), returned, tapped "Verify" against an already-verified pending handoff,
  and **nothing visible or logged happened**. Root cause: `lockModeAndArm`'s two early-exit guards
  (incomplete MRZ fields; handoff still verifying) had no matching `Log` call — a real tap was
  indistinguishable from a dropped one in logcat. Recorded as **finding #20**, fixed in `8c063ec`.
- Separately, a **BAL-blocked NFC start targeting an invisible second `RegularActivity` instance**
  was found: `12:56:18.827` and `12:57:20.375` show "Background activity launch blocked!" against an
  "invisible launch ActivityRecord" in a second task, while the visible instance's own log shows
  nothing for those taps. Recorded as **finding #19** (two live instances across two tasks, one from
  Chrome's task, one from the camera app's task), fixed via `android:launchMode="singleTask"` in
  `039fee7`.

Both fixes were device-confirmed later in this same session:

- Finding #20: `13:58:13.260 W/MainActivity M2 stage: lock refused — document fields incomplete
  (doc_present=false dob_present=false exp_present=false)` — the fixed guard now logs and shows a
  dialog on a real incomplete-field tap, on build `7f25f40`.
- Finding #19: **stated precisely, correcting this session's own working assumption** — BAL-blocked
  NFC starts against an "invisible launch ActivityRecord" continue to appear after `13:47`
  (`13:52:40.302`, `13:54:16.868`, `13:56:10.154`, `13:58:38.714`, all on build `7f25f40`) — the
  `singleTask` fix does **not** eliminate the block itself. What it does establish, confirmed by
  reading the surrounding lines: each blocked start is immediately followed by `M2 stage: MRZ input
  first attempt this session` on the **same** `MainActivity` pid that had already captured that
  session's handoff — i.e. the foreground-dispatched NFC intent still reaches the one live instance
  via `onNewIntent` regardless of the superfluous, blocked launch attempt. No second pid/instance
  appears anywhere in `device-session-3.log` handling a tag read. Finding #19's actual defect (two
  independent instances, two independent holders of session state) does not recur on `7f25f40`; the
  BAL block itself is a harmless, unrelated side effect of how Android's foreground NFC dispatch
  re-issues the same intent, present before and after the fix, and not itself part of what `039fee7`
  or `7f25f40` changed.

---

## 3 — Native camera QR route (already check 7 on the morning page)

Reused throughout the afternoon, not re-tested independently: every `av://` capture in the sessions
read for this page (12:54, 13:22, 13:26, 13:47, 13:52, 13:53, 13:55) arrived via the Pixel Camera
app's `START ... act=android.intent.action.VIEW dat=av://authorize/...` firing directly at the
scanner's exported intent filter, per D69/finding #18.

---

## 4 — Mode B, NL card, PACE, chip_auth passed, against 8788 (13:22)

13:22:10 handoff captured via camera-app `av://` link, verified (`origin=http://127.0.0.1:8788`).
Read completed with `access_protocol: PACE`, `chip_auth (D21 payload field): passed`, masterlist
`588/588`, `passive_auth: ok=true allowed=true`. Mint: `device_key: created this mint`,
`zktag_sha256_prefix (value-free, never the raw zktag): f462a66b50bc`, `evidence_type: sig-p256/1`,
`handoff: direct_post http_status=200 -> {"accepted": true}`, `verdict: PASS (minted)`.

**Result: PASS.**

---

## 5 — Mode A on build `039fee7`, NL card, against 8788 (13:27)

MRZ input 13:27 (following the `13:26:49.828` camera-app `av://` capture). The read itself
completed (masterlist/passive-auth lines are not repeated in this excerpt, matching the shape of
the other reads in this session), but **no `direct_post` occurred and the transaction stayed
pending** — the fixed `MintGate.actionFor` behaviour had not yet been exercised on device at this
point in the session (this run predates the mode-A-bare-presentation confirmation in check 7 below).
Recorded as **finding #21** (mode A never delivers a presentation; item 9's "ships bare" MUST unmet,
item 15's modal missing for that outcome) — the same defect class already fixed in source
(`40737a2`) but not yet confirmed on this device at the time of this run.

**Result: FAIL as run, root cause matches the already-open finding #21; fixed and re-confirmed
device-side later this session (check 7).**

---

## 6 — Reinstall zktag stability (exit-criteria row 1, part 1)

The NL card's zktag was minted twice against the SAME per-(origin, zktag) identity, across an
uninstall+reinstall of the app: uninstalled and reinstalled build `039fee7` at 13:23; on build
`7f25f40` at 13:52:47 the NL card minted again, logging `device_key: created this mint` (a fresh
Keystore alias post-reinstall, expected — Keystore state does not survive an uninstall) and the
**same** `zktag_sha256_prefix: f462a66b50bc` seen in check 4, pre-reinstall.

**Note on the verifier-side signal**: a verifier restart at approximately 13:50 (all spike processes
were killed by a subagent's cleanup between builds) reset the attester store, so a verifier-side
first-sight/rebind mismatch check was not available for this comparison. The proof here is the
app-side prefix equality alone — the same zktag was independently derived from the same document's
chip data both before and after a full app uninstall/reinstall, which is what this exit criterion is
actually about (identity survives reinstall, not verifier bookkeeping).

**Result: PASS.**

---

## 7 — Mode A bare presentation (finding #21 fix), NL card, against 8788 (13:54)

13:53:52 handoff captured and verified (`origin=http://127.0.0.1:8788`). 13:54:16 MRZ input; read
completed (PACE, `chip_auth: passed`, masterlist `588/588`, `passive_auth: ok=true allowed=true`).

```
mint_gate: MET (present bare, item 3) — sending a bare tier-A presentation (no zktag, no device key,
no biometric prompt, item 9)
M2 stage: mode A present-bare age check — over_threshold=true threshold=18
M2 stage: mode A present-bare direct_post -> http://127.0.0.1:8788/wallet/direct_post
M2 stage: mode A present-bare direct_post response http_status=200 body={"accepted": true}
present: bare tier-A (item 9) — evidence: [], no zktag, no device key
verdict: PASS (bare presentation sent)
```

No `zktag_sha256_prefix` line anywhere in this block, and no biometric prompt was shown — consistent
with a bare tier-A presentation carrying no device key or zktag. Verifier (`8788`) recorded (per
`handoff-8788.log`): `tier=A threshold=18 ok=true allowed=true reason=no-evidence-required
evidence=[] attester=n/a`.

**Result: PASS.** This closes finding #21's device-pending residual, and is also **exit-criteria row
1, part 3**: mode A emits no zktag, run immediately after a mode-B mint of the same document
(check 4/check 6, `zktag_sha256_prefix: f462a66b50bc`) at this same origin.

---

## 8 — Under-threshold mint (Q36/D66), NL card, against a fresh 8789 spike (THRESHOLD=99, 13:56)

13:55:44 handoff captured, verified (`origin=http://127.0.0.1:8789`). 13:56:10 MRZ input; read
completed identically to the prior checks. `M2 stage: age check — over_threshold=false
threshold=99`. Mint proceeded anyway (`device_key: created this mint`,
`zktag_sha256_prefix: f462a66b50bc` — same document, same prefix as checks 4/6/7, a different
origin), `handoff: direct_post http_status=200 -> {"accepted": true}`, `verdict: PASS (minted)`.
Verifier (`8789`, per `handoff-8789.log`): `tier=B threshold=99 ok=true allowed=false
reason=under_threshold evidence=[]`. Owner reported seeing the "threshold not met" dialog on screen.

**Result: PASS.** An honest `over_threshold:false` mint still hands off and is correctly refused by
the verifier on the stated threshold — matching D66's design exactly.

---

## 9 — Masterlist probe (exit-criteria row 1, part 2; bucket (i) on device)

13:57:32, immediately after check 8's mint, on the same instance:

```
===== MASTERLIST PROBE =====
full_load: OK declared=588 parsed=588 (369ms)
NEGATIVE half_loaded: REFUSED (ok:false) — CMS parse failed: CMSException: IOException reading
content. (good)
===== END =====
```

**Result: PASS.** Bucket (i) — full load parses cleanly, and a corrupted/half-loaded CMS is refused
as an integrity failure (`ok:false`), matching the two-bucket rule (§6.2 item 7). Bucket (ii) —
a well-formed list lacking the issuing CSCA (`ok:true, allowed:false`) — remains unit-proven only,
per `PassiveAuthTrustTest` (`4ca350e`); this session's real documents both have their issuing CSCA
present in the bundled list, so that specific negative cannot be planted device-side without a
document that lacks CSCA coverage, which this session did not have.

---

## 10 — Local scan, no handoff (13:58:41)

Following the finding #20 confirmation (check 2) at 13:58:13, a fresh MRZ input at 13:58:38 with no
pending handoff (the prior handoff, against `8789`, was already consumed by check 8's mint) produced
a mode-A local read: masterlist `588/588`, `passive_auth: ok=true`, `mint_gate: MET (present bare,
item 3) — evidence: [] (D27); no handoff pending, nothing to send`, `verdict: PASS (read)`.

**Result: PASS.**

---

## 11 — Owner eye-confirmations on `7f25f40`

- **Log entries persist across app close (item 23)** — PASS. `device-session-3.log` shows `M2 stage:
  loaded persisted log from disk (entries=N)` at the start of each of the three post-13:47 sessions
  (`entries=1`, `entries=2`, `entries=3`), confirming the on-disk log survives process death and
  accumulates correctly across the three separate app instances this session produced.
- **Collapsed-entry glyphs/collapse (items 18/22)** — seen by the owner; not independently visible in
  the value-free logcat excerpt (no logging requirement exists for this UI-only behaviour, by design,
  same as Q47's fix).
- **Launcher icon/label (item 21)** — seen by the owner at approximately 12:5x, during the same
  window as check 1/2's builds.
- **Visible version stamp (item 24)** — **NOT visible anywhere**, on any of the three builds
  installed this session. Searching both `device-session-2.log` and `device-session-3.log` for any
  `versionName`/git-SHA/stamp-shaped string returns no matches. **Result: DEVICE FAIL.** Open,
  investigation in progress — item 24 was recorded as "Built" in the exit-criteria table prior to
  this session; this is the first device attempt to actually see the stamp, and it does not appear.

---

## 12 — Not exercised this session

- **Item 17 (switch to scan pane on handoff intent)** — owner did not report on this; not observed.
- **Item 19 (dim a completed run)** — owner did not report on this; not observed.
- **Item 20 (verb wording, "Verify"/"Scan"/"Tap and verify"/"Tap and scan")** — owner did not report
  on this; not observed.
- **"Clear log" control** — not exercised this session.

Recorded as "not observed," honestly, rather than inferred from adjacent evidence.

---

## 13 — Second device pass, 2026-09-03 ~15:02–15:15 (build `fb0e75f`, versionName 0.2.0,
installed 14:04:35)

Continuation session on the same Pixel 6a and the same verifier spike (`127.0.0.1:8788`, via
`adb reverse`), running the fix build `fb0e75f` — content-identical to the `e8e0c33` worktree
build installed at 14:04 (per the layout fix committed same-day for item 24's device FAIL in
section 11 above). The orchestrator fired `av://` links with `adb shell am start -a
android.intent.action.VIEW -d <link>`, the same VIEW-intent path a browser or camera-app tap
uses. This pass exercises the four checks section 12 recorded as "not exercised" (items 17, 19,
20, and "Clear log") plus a re-check of item 24's device FAIL from section 11. Per this file's own
rule, only value-free log lines and owner-reported outcomes appear below — no PII.

**13.1 — Item 20 (button verb, D68a): PASS.** With a verified `av://` link pending, the
scan-action button read "Verify"; after a fresh open with no link pending it read "Scan". App log
at `15:02:27` and again at `15:10:22`: "pendingHandoff captured from av:// intent" then "handoff
request object verified — origin=http://127.0.0.1:8788 signature_verified=true
key_kind=dev-pinned". **Result: PASS**, closing Q45/item 20's device-verification-pending status.

**13.2 — Item 24 (version stamp footer): PASS, overturns section 11's device FAIL.** On this
build (`fb0e75f`) the owner saw the version/short-git-SHA stamp at the bottom of the screen. This
overturns the DEVICE FAIL recorded in section 11 above (build `7f25f40`) — the same-day `fb0e75f`
commit had already diagnosed and fixed the layout bug (the pane-container `FrameLayout` consuming
all weighted height in the parent's weighted-sizing pass and pushing `version_stamp_view` below
the screen edge) before this owner pass confirmed it on screen. **Result: FIXED-IN-fb0e75f,
device-confirmed.**

**13.3 — Item 17 (tab switch on link arrival): PASS.** Owner sat on the Log tab; the `av://` link
fired at `15:10:22` and the app switched to the Scan tab on its own, with no manual tap. **Result:
PASS**, closing Q39/item 17's device-verification-pending status.

**13.4 — Item 23 (persistent log + Clear log survival): PASS.** Clear log, swipe the app away,
reopen: the log came back empty. (Earlier the same day, log persistence across restart with
populated entries was already confirmed in section 11 — "loaded persisted log from disk
(entries=6)" at `15:02:24`; this pass adds the "Clear log" half section 12 had flagged as not
exercised.) **Result: PASS**, closing the last unexercised half of item 23/Q38.

**13.5 — Item 19 (dim completed runs, Q44): OBSERVED AS BUILT, owner decision pending.** Owner saw
every log entry rendered in the same monotone grey, with no visible dimming contrast between
entries. This matches the design as built, not a bug: `ReportLog.rendered` dims every
terminal-outcome entry (60% alpha of `logView`'s text colour, `DIM_ALPHA_FRACTION = 0.6` in
`MainActivity`) and only an "in progress" entry keeps full colour — with no scan in flight during
this check, every visible entry was terminal, so all rendered equally dimmed; contrast is only
visible during a live scan, mid-run. Owner has been offered two options: keep as built, or have
the newest entry stay bright regardless of terminal state. **Result: not PASS, not FAIL —
device-observed-as-built, owner decision pending.**

---

## Findings and questions this run produced or moved

1. **Finding #19 — status refined, not changed.** The fix (`039fee7`, `android:launchMode=
   "singleTask"`) still holds: no second `MainActivity` pid/instance appears handling a tag read
   anywhere in `device-session-3.log`. What this session corrects is a scope assumption: the BAL
   "Background activity launch blocked!" log line itself continues to appear after the fix and is
   not, on its own, evidence of the two-instance defect — it is a superfluous, blocked launch
   attempt that coexists with (and does not prevent) the live instance correctly receiving the same
   intent via `onNewIntent`. Device re-check: **PASS**, precisely stated as above.
2. **Finding #20 — device-confirmed.** `13:58:13.260` shows the fixed guard's dialog/log firing on a
   real incomplete-field tap.
3. **Finding #21 — device-confirmed.** Check 5 reproduced the original defect on `039fee7`
   (mode A never delivering); check 7 confirmed the fix on `7f25f40` (mode A sends a bare tier-A
   presentation, verifier records `ok=true allowed=true reason=no-evidence-required`).
4. **Item 24 (version stamp) — DEVICE FAIL.** Not visible on any build this session. Open.
5. **Exit-criteria row 1 — RE-RUN on the real build.** Reinstall stability (check 6): PASS.
   Masterlist bucket (i) (check 9): PASS on device; bucket (ii) remains unit-proven only
   (`3f65290`/`4ca350e`), no device-plantable negative available with these two documents. Mode A
   emits no zktag after a mode-B presentation of the same document (checks 4/6 → check 7): PASS.

**Addendum, second device pass 2026-09-03 ~15:02–15:15 (section 13):**

6. **Item 24 — status corrected, PASS.** Section 13.2 supersedes item 4 above: the DEVICE FAIL
   recorded against build `7f25f40` does not hold against the fix build `fb0e75f` — the stamp is
   visible. Status: **FIXED-IN-fb0e75f, device-confirmed.**
7. **Items 17 and 20 — device-confirmed, PASS.** Section 13.1 (item 20/Q45) and section 13.3 (item
   17/Q39) close both items' device-verification-pending status.
8. **Item 23's "Clear log" half — device-confirmed, PASS.** Section 13.4 closes the one half of
   item 23 section 11 had left unexercised.
9. **Item 19 — observed as built, not a pass/fail.** Section 13.5: the dimming design works as
   built (every terminal entry renders equally dimmed when no scan is in flight); owner decision
   pending on whether the rule itself should change (keep as built vs. newest-entry-stays-bright).

---

## What this run did and did NOT establish

**Did establish:**
- Exit-criteria row 1's three checkpoints, re-run on the real build for the first time: reinstall
  stability (PASS, NL card), masterlist two-bucket rule bucket (i) (PASS on device), mode A silent
  after mode B (PASS).
- Findings #19 (refined, not overturned), #20, and #21 all device-confirmed fixed.
- Q36/D66's honest under-threshold mint-and-refuse path, exercised end to end against a live
  verifier with a distinct threshold (99).
- Item 23 (log survives app close) working across three separate app instances in one session.

**Did NOT establish:**
- Item 24's version stamp — searched for and not found on any of the three builds. Open, device FAIL.
- Masterlist bucket (ii) (well-formed list lacking the issuing CSCA) on device — no document
  available this session lacks CSCA coverage; stays unit-proven only.
- Items 17, 19, 20, or "Clear log" — not reported by the owner, not independently observed, recorded
  as not-observed rather than assumed.

**Addendum, second device pass 2026-09-03 ~15:02–15:15 (section 13) — now established:**
- Item 24's version stamp — visible on build `fb0e75f`. FIXED-IN-fb0e75f, device-confirmed
  (overturns the FAIL above, which stands as the record for build `7f25f40`).
- Items 17 (tab switch) and 20 (button verb) — both PASS, device-confirmed.
- "Clear log" — PASS, device-confirmed (log empties and stays empty across a swipe-away/reopen).
- Item 19 (dim a completed run) — observed as built exactly as designed (all-terminal-entries
  render equally dimmed when idle); this is a design property, not a pass/fail outcome, and the
  owner's choice between "keep as built" and "newest entry stays bright" is still pending.

---

**No PII values appear anywhere above.** All quoted logcat lines and verifier states are value-free
by construction — stage names, boolean/status fields, truncated transaction identifiers/hashes, and
timings only. **The owner's raw zktag pasted into chat during this session is deliberately excluded**
from this file; only the app's own `zktag_sha256_prefix` value-free hash line appears, checked
against this file's own rule before inclusion.
