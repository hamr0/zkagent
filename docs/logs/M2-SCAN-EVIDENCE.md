# M2 — Scan spike evidence (opening riskiest-assumption POC)

**Status**: complete — all three tests run on both documents (US passport, NL ID card),
MEASURED sections filled in, findings recorded, app uninstalled from the test device. This
file's SETUP and pre-registered failure modes were written before any scan, per PRD
discipline (M0's own `docs/logs/M0-EVIDENCE.md` "rewrite the row so the spike can fail"
precedent). **Result: TEST 1, TEST 2, and TEST 3 all PASS for both documents**; several
non-blocking findings (F1–F7, see Findings) were surfaced along the way, including one
contained privacy incident (F4) and one unresolved spike bug (F5) — both flagged for M2
proper, not fixed in this throwaway spike.

**Scope, per PRD v1.16 §6 M2 row and the owner's 2026-08-31 approval**: this spike covers
TWO of the M2 row's opening checkpoints — **zktag stability across app reinstall + rescan**
(TEST 1) and **masterlist verification on the phone with its mandated negative** (TEST 2) —
plus a cheap check on **mode A emitting no zktag after a mode-B presentation** (TEST 3). The
M2 row's *other* riskiest-assumption item — capturing real EU AV Blueprint / UK OSA wire
shapes and building the web→app→web handoff — is **explicitly out of scope for this spike**
and is not attempted here.

**Rule for this file (carried from PRD v1.5 / M0-EVIDENCE.md)**: no PII values, ever. Field
names, counts, hashes, verdicts and timings only. Document numbers, names and dates of birth
appear nowhere in this repo.

---

## SETUP — toolchain (2026-08-31)

| Component | Version | Note |
|---|---|---|
| Host | Fedora 44 | same host as M0/M1 |
| JDK | Temurin 21.0.12.1 at `~/opt/jdk-21.0.12.1+1`, via `JAVA_HOME` override | System default `java` on this host is OpenJDK 25.0.4.1 (Red Hat build), which AGP does not support; same constraint M0-EVIDENCE.md SETUP recorded |
| Android SDK | `~/Android/Sdk` — platform-tools, `android-36`, build-tools `36.1.0` (via AGP) | Same SDK M0 and `spikes/m2-handoff/android/` already built against |
| Gradle | 9.5.1 (wrapper) | unchanged from `spikes/m0` |
| Device | Pixel 6a (`bluejay`), adb serial `34011JEGR02358`, stock Android 17, NFC on | Same device as M0/M1 |
| Spike | `spikes/m2-scan/` — **fork of `spikes/m0`** (not a from-scratch app, per instruction) | `M0Probe.kt` copied byte-for-byte unchanged; MRZ-persistence defect designed out (see below); `M2MasterlistProbe.kt` added; `MainActivity.kt` extended for TEST 1/2/3 |
| APK build id | `applicationId com.zkagent.m2scan`, `versionCode 1`, `versionName "0.1-spike"`, built `2026-08-31` via `./gradlew :app:assembleRegularDebug` (BUILD SUCCESSFUL, 35 tasks) | Distinct `applicationId` from `com.tananaev.passportreader` (the M0 spike, still installed on this device) so TEST 1's uninstall/reinstall never touches the M0 app |
| Masterlist asset | `spikes/m2-scan/app/src/main/assets/masterList`, **899,665 bytes**, SHA-256 `da33466be6b98437456ee46d1d3021ea881081717c4bdd2ac7a513c2f1b7b8ae` | Byte-identical file to `spikes/m0`'s bundled asset (same size as M0-EVIDENCE.md SETUP: 899,665 bytes, BSI German Master List, 2026-05-28 publication, 588 certs / 116 countries per M0 Finding 1). Provenance re-verified against a fresh BSI download on 2026-08-31 — RESOLVED, see PENDING |
| Install | `adb -s 34011JEGR02358 install -r app-regular-debug.apk` → `Success`; confirmed running via `adb shell pidof com.zkagent.m2scan` (pid 24974) and `dumpsys activity activities` showing the activity as top, visible task; no `AndroidRuntime`/`FATAL` lines in logcat after launch | Device was locked at launch time (screenshot showed the lock screen) — launch was confirmed via `dumpsys`/`pidof`, not visually; the owner must unlock the phone before scanning |

**Deviations from `spikes/m0`, deliberate (full list in `spikes/m2-scan/README.md`):**
1. **MRZ persistence removed entirely** (the M0 defect — see next section).
2. `M1SodProbe.kt`, `M1AttestProbe.kt`, `M1IntegrityProbe.kt` deleted — out of scope for this
   spike; `M1SodProbe` in particular wrote raw DG1/SOD bytes to the app's private external
   files dir, a PII-to-disk path this spike has no reason to carry forward.
3. Play Integrity dependency and `INTERNET` permission dropped (unused by this spike).
4. `M2MasterlistProbe.kt` added — desk-only full-load + half-load-negative measurement,
   runnable without a chip tap.
5. `MainActivity`'s report extended: mode-gated derivation (TEST 3), combined `chip_auth`
   field (D21), DS-cert validity dates surfaced explicitly, native-heap measurement.

### The MRZ-persistence defect, designed out (NO-GO #9)

`spikes/m0`'s `MainActivity` wrote the typed passport number, date of birth, and expiration
date to `PreferenceManager.getDefaultSharedPreferences` — on activity create (three
`if (x != null) { ...edit { putString(...) } }` blocks) and on every keystroke (a
`TextWatcher` on the passport-number field). Those values landed in
`/data/data/com.tananaev.passportreader/shared_prefs/*.xml` and outlived the app process —
exactly the defect this task named.

**Fixed by removal, not by gating.** `spikes/m2-scan/app/src/main/java/com/tananaev/passportreader/MainActivity.kt`
has:
- No `import android.preference.PreferenceManager`.
- No `SharedPreferences`, no `DataStore`, and no other write-to-disk call for the MRZ fields.
- The three `EditText`s are the only place typed MRZ material lives, for one NFC session;
  `wipeMrz()` clears them after every read attempt (success or failure) and again in
  `onStop`.

**Proof, not assertion** (grep against the fork's own source, 2026-08-31):

```
$ grep -rn "SharedPreferences\|DataStore\|putString\|writeText\|writeBytes" \
    spikes/m2-scan/app/src/main/java/
(no output)
```

Zero hits. There is no write path for the MRZ fields (or anything else) to disk anywhere in
this module. The only thing this app writes to disk at all is nothing — no fixture files, no
prefs file, no cache file with document-derived content. The only persistence of any
derivation output is the operator's own `adb logcat` capture off the phone, of a one-way HMAC
hash — never the MRZ key itself.

**Method finding — the `onPause` wipe broke NFC dispatch on device (2026-08-31).** The
first implementation wired the NO-GO #9 defense-in-depth wipe into `onPause()` instead of
`onStop()`. Android's NFC foreground dispatch delivers a scanned tag through a
`PendingIntent` that pauses and resumes the activity (it stays visible throughout) *before*
`onNewIntent` runs with the tag — so the `onPause` wipe cleared the typed MRZ milliseconds
before the read needed it, and every tap failed and asked the operator to retype. Found on
the owner's first tap during hand-off. Fixed by moving the wipe to `onStop()`: an NFC
dispatch never stops the activity, but genuinely leaving the screen does, so the NO-GO #9
guarantee is preserved and a tap survives. `onPause` still disables foreground dispatch; the
per-read-attempt wipe (success or failure) is unchanged.

---

## PRE-REGISTERED FAILURE MODES (written before any scan)

### TEST 1 — zktag stability across reinstall

**Protocol**: Mode B, scan document → record `document_number` HMAC tag from
`M2 SCAN REPORT` → `adb uninstall com.zkagent.m2scan` → reinstall the same APK → scan the
same document again in Mode B → diff the two tags.

**Pass**: the two tags are byte-identical, AND both match the *prefix* of M0's recorded
value for the same document (`docs/logs/M0-EVIDENCE.md` Findings 3/10/11: `1e8f3d88…` for
the US passport, `fa305d88…` for the NL card) — comparable because `M0Probe.deriveCandidates`
is reused unmodified.

**This test can fail, and here is what each failure would mean, decided in advance:**
- **Tag changes after reinstall, but is stable across two scans *within* the same install** →
  the derivation depends on something the OS resets on uninstall — Android Keystore-backed
  material, an app-scoped install ID, or anything under `/data/data/<pkg>/` that `adb
  uninstall` wipes. Per the task's own instruction: **this is a design finding to report,
  not to paper over.** `M0Probe.deriveCandidates` (unchanged from M0) derives
  `HMAC(SHA-256(document_number bytes), domain)` from DG1 content read fresh off the chip
  each time — no Keystore call, no persisted seed — so a failure here would mean either (a)
  a bug in this fork's read path that differs from what was actually verified, or (b) that
  the *document itself* is somehow yielding different bytes per read (contradicts M0 Findings
  3/10/11 on the same documents). Either way: escalate, do not adjust the derivation to make
  it pass.
- **Tag does not even match M0's own recorded prefix on the very first (pre-reinstall) scan**
  → this fork's read/derive path has diverged from M0's, independent of reinstall. Also a
  stop-and-escalate condition — the comparison basis for TEST 1 would be broken.
- **Tag matches across reinstall and matches M0** → PASS. Confirms the D9 derivation field
  (`document_number`) is stable under exactly one axis M0 could not test (a reinstalled
  client, not a renewed document) — still does **not** establish renewal stability, which
  remains untestable with documents in hand (unchanged from M0's own caveat).

**What D10's 30-day secret is/is not, confirmed from FR11/D10 text before running anything**:
D10's "mode-B derived secret" with the enclave-enforced 30-day ceiling is a **freshness
control on presentations**, not a derivation input. FR11's derivation is
`zktag = HMAC(KDF(chip data), domain)` — chip data and domain only. D10's secret is a
separate, expiring value the *client* caches after a scan so it need not re-scan every
presentation; it is not read by, and does not feed, `deriveCandidates`. **This spike does not
implement D10's caching/expiry at all** — every scan re-derives from a fresh chip read, so
TEST 1 exercises the derivation's own stability, not the cached-secret mechanism. No
ambiguity found in the PRD text on this point; no escalation needed here.

### TEST 2 — masterlist verification on the phone

**Protocol, desk half (no tap)**: tap "Run masterlist checks" → `./capture-masterlist.sh`.
Measures: full-load parse time, full-load native-heap delta, and the half-loaded-masterlist
negative (see `M2MasterlistProbe.kt`'s three-layer design in its class doc).

**Protocol, chip half**: during a normal scan, the CSCA-removed negative runs exactly as M0
ran it (`M0Probe.loadMasterList(excludeAnchorFor = <this document's own signer cert>)`),
plus DS-cert `notBefore`/`notAfter` are surfaced explicitly in the report.

**Pass, full load**: `certsDeclared == certsParsed` (expect 588, matching M0-EVIDENCE.md
exactly, since it is the same asset). Fail = any mismatch, reported as-is (would mean the
asset was corrupted in the fork/build step, not a design finding).

**Pass, half-loaded negative**: the run must land in one of the two `REFUSED` branches in
`M2MasterlistProbe`'s log line — either a parse exception on the truncated stream, or the
external cross-check (half-load's parsed count below the full-load's, measured in the same
run) — never the `INVALID TEST` branch (which would mean the truncation failed to shrink
anything and the negative proved nothing) and never a bare pass.

**Escalation — a wording conflict found while implementing, not resolved unilaterally**:
this task's instructions state *both* negatives (half-loaded masterlist, and "a list with the
issuing CSCA removed") "MUST yield `ok:false` — refused, 'could not check' — NEVER a 'no' and
NEVER a pass." That contradicts M0's own established, evidence-backed design: M0-EVIDENCE.md
Finding 5 and `M0Probe.passiveAuth`'s own code comment treat CSCA-removed as a **real "no"**
(`ok:true, allowed:false`) — "the checker successfully determines the issuer is untrusted,"
explicitly *not* an "unknown" outcome. M0's PRD row and go/no-go table (§6.1) also phrase
negative (ii) as "MUST yield `ok:false`" in the *M0* row, yet M0Probe's actual implementation
(reviewed and evidence-backed) returns `ok:true, allowed:false` for it, and that was accepted
as correct at the time (Finding 5's fix was about proving the exclusion *took effect*, not
about changing which outcome bucket it belongs in). **This fork keeps M0's implemented,
evidence-backed semantics unchanged (CSCA-removed = ok:true/allowed:false, a legitimate
"no"; half-loaded masterlist = ok:false/unknown, "could not check") rather than
reinterpreting an already-validated invariant from a paraphrase.** Flagging this discrepancy
for the owner/orchestrator to confirm which reading is intended going forward — **not
resolved by this spike, deliberately.**

**On DS-cert validity dates (M1's lesson)**: `M0Probe.passiveAuth` already calls
`c.checkValidity()` explicitly on each document-signer certificate *before* path validation,
returning a real "no" (not a silent pass) if the cert is expired or not-yet-valid — this was
already correct in the code inherited from M0 and is unchanged here. This spike additionally
logs `ds_cert_not_before` / `ds_cert_not_after` directly in the M2 SCAN REPORT so the dates
are visible, not just enforced silently.

### TEST 3 — mode A after mode B emits no zktag

**Protocol**: pick Mode B, scan, confirm `zktag_candidates` appear in the report; then pick
Mode A on the same device (same app process or a fresh one), scan again, confirm the report
says `mode: A ... derivation SKIPPED` and contains **no** `zktag_candidates` block at all.

**Pass**: Mode A's report contains zero derivation output — not a placeholder, not a
redacted value, an absent code path. Implementation note: `MainActivity.runM2Report()` calls
`M0Probe.deriveCandidates` inside `if (modeB) { ... }` only; grep confirms
`deriveCandidates` appears exactly once in the file, inside that branch.

**Fail / not-run**: if the harness needs more than the toggle already built (e.g., if mode
state needs to persist correctly across the two scans, or a UI issue prevents re-arming NFC
dispatch between runs), record as **not-run** per the task's own allowance — this is the
"cheap, if the harness allows" test.

---

## MEASURED — TEST 1 (zktag stability across reinstall)

**DONE — both documents.** Captures: `pre-reinstall-US.txt` / `post-reinstall-US.txt`,
`pre-reinstall-NL.txt` / `post-reinstall-NL.txt` (all under the operator's local tmp capture
directory, PII-free by construction — field names/hashes/verdicts only).

**US passport**

| | Pre-reinstall scan | Post-reinstall scan |
|---|---|---|
| Document | US passport | US passport |
| Mode | B | B |
| `document_number` tag | `1e8f3d88221b533102e8b3fdc53e4f9ada899301fb18081763ede6b20477c559` | identical |
| Matches M0's recorded prefix (`1e8f3d88…`)? | YES | YES |
| Byte-identical to the other column? | — | YES (`document_number`, `optional_data`, `dg1_full` all identical; US card has no DG14/DG15, consistent with M0/`chip_authentication: false`) |

**Verdict: PASS.**

**NL ID card**

| | Pre-reinstall scan | Post-reinstall scan |
|---|---|---|
| Document | NL ID card | NL ID card |
| Mode | B | B (see Finding — mode-radio bug, below: an earlier attempt at this step landed in Mode A instead and was repurposed as the TEST 3 NL capture; this row is the redone, verified-Mode-B capture) |
| `document_number` tag | `fa305d8824f933e0427806a3001bed17f680aca46ed0dfc483338d7aca1aece2` | identical |
| Matches M0's recorded prefix (`fa305d88…`)? | YES | YES |
| Byte-identical to the other column? | — | YES — all five candidates identical: `document_number`, `optional_data`, `dg1_full`, `dg14_ca_key`, `dg15_aa_key` |

**Verdict: PASS.** Note on `dg14_ca_key`/`dg15_aa_key`: Chip Authentication (DG14) and Active
Authentication (DG15) each involve a fresh ephemeral key-agreement/challenge-response
exchange per session (real per-tap randomness on the wire). These two derived candidates
being byte-identical across an independent reinstall + rescan therefore demonstrates the
derivation reads the chip's **stored, static** public key material recorded in DG14/DG15 —
not any of that session's ephemeral randomness — exactly as `M0Probe.deriveCandidates`
(unchanged from M0) is documented to do.

Both documents pass the pre-registered TEST 1 bar: byte-identical across reinstall, and
matching M0's own recorded prefix on the first (pre-reinstall) scan. No divergent-tag or
match-failure branch fired for either document.

## MEASURED — TEST 2 (masterlist on device)

**DONE.** Captures: `m2-masterlist-report.txt` (desk half), `pre-reinstall-US.txt` /
`pre-reinstall-NL.txt` (chip half, CSCA-removed negative + DS-cert dates).

| Quantity | Value |
|---|---|
| Full load: certs declared / parsed | 588 / 588, `consistent=true` |
| Full load: parse time (ms) | 585 ms |
| Full load: native heap delta (KB) | -32 KB (see heap-measurement caveat, Findings, below) |
| Half-load negative: which branch fired | Exception branch — truncated to 449,832/899,665 bytes, `IOException: corrupted stream - out of bounds length found: 899660 >= 449832` → `REFUSED (ok:false)`, the expected outcome for a corrupt/incomplete masterlist |
| Half-load negative: attempt time (ms), native heap delta (KB) | 0 ms, 0 KB (failed fast, before any meaningful parse work) |
| CSCA-removed negative: excluded count, verdict — US | excluded=8, kept=580 → `ok=true allowed=false` — FIRED (good) |
| CSCA-removed negative: excluded count, verdict — NL | excluded=2, kept=586 → `ok=true allowed=false` — FIRED (good) |
| DS cert `notBefore` / `notAfter` — US | `Tue Aug 22 22:55:34 GMT+02:00 2023` / `Thu Dec 23 10:38:23 GMT+01:00 2038` |
| DS cert `notBefore` / `notAfter` — NL | `Thu Nov 13 01:00:00 GMT+01:00 2025` / `Thu Nov 22 01:00:00 GMT+01:00 2035` |

**Verdict: PASS**, both halves, both documents. The half-loaded negative landed in the
pre-registered exception branch (never the `INVALID TEST` branch, never a bare pass) with an
excluded/kept count of 0 remaining ambiguous — not applicable here since this negative is a
parse failure, not an exclusion count; the exclusion count applies to the CSCA-removed
negative, and both documents show a nonzero excluded count (8 and 2 respectively, `excluded
> 0` in both cases, satisfying the "assert count > 0" discipline from ag-001/M0's own lesson
about a negative that silently proves nothing).

**Escalation from PRE-REGISTERED section — unresolved, not decided in this spike**: this
spike keeps M0's implemented, evidence-backed semantics (CSCA-removed = `ok:true,
allowed:false`, a real "no"; half-loaded masterlist = `ok:false`, "could not check") rather
than a stricter reading of this task's own wording that both negatives "MUST yield
`ok:false`". Confirmed unchanged behavior in both this spike's captures. Still flagged for
the owner/orchestrator to confirm which reading M2 proper should use — not resolved here.

**RESOLVED (post-spike)**: the orchestrator ruled on this the same day and it is recorded in
the PRD (§6 M0 row marked superseded; §6 M2 row carries the two-bucket rule): a masterlist
**integrity** failure (truncated/half-loaded, parsed≠declared) ⇒ `ok:false` ("could not
check"); a well-formed list that lacks the issuing CSCA ⇒ `ok:true, allowed:false` (a real
"no", issuer-untrusted). This is exactly what this spike implements and what TEST 2 measured
above — no code or doc change required as a result.

## MEASURED — TEST 3 (mode A after mode B)

**DONE — both documents.** Captures: `mode-a-US.txt`, `mode-a-NL.txt` (the latter is a
repurposed capture — see Finding, mode-radio bug, below — not the originally-planned
sequencing, but a valid Mode-A read on its own terms).

| | US passport | NL ID card |
|---|---|---|
| Mode B report (same session) contains `zktag_candidates`? | YES (`pre-reinstall-US.txt`) | YES (`pre-reinstall-NL.txt`) |
| Mode A report contains `zktag_candidates`? | NO — absent code path, not a placeholder (`mode-a-US.txt`: `mode: A (anonymous) — derivation SKIPPED, no zktag computed or emitted`, no `zktag_candidates` block anywhere in the file) | NO — same (`mode-a-NL.txt`: identical `mode: A` line, no `zktag_candidates` block) |
| Passive auth + negatives still fired in Mode A? | YES — `passive_auth ok=true allowed=true`; `NEGATIVE_1 dg1_byte_flipped` and `NEGATIVE_2 csca_removed` both `ok=true allowed=false … FIRED (good)` | YES — same two negatives FIRED, `chip_authentication (CA/DG14): true`, `chip_auth (D21 payload field): passed` (NL card only; US card has no CA/AA) |
| Run, or not-run? | Run | Run |

**Verdict: PASS**, both documents. No `zktag_candidates` line appeared in either Mode-A
report — this was the fail condition pre-registered for this test, and it did not occur.

---

## Findings

**F1 — MRZ-persistence defect (NO-GO #9), designed out.** Unchanged from the PRE-REGISTERED
section above: confirmed by grep, zero write-path hits for the typed MRZ fields anywhere in
this fork. Static, not scan-dependent.

**F2 — the `onPause`→`onStop` wipe-timing fix.** Already documented in SETUP: the wipe was
originally wired to `onPause()`, which broke NFC dispatch (NFC's own pause/resume of the
still-visible activity wiped the MRZ before the read could use it). Fixed by moving the wipe
to `onStop()`, which only fires on an actual screen-leave. Confirmed working across every
capture in this session — every tap after the fix succeeded without needing a retype
(except F3, a different failure mode).

**F3 — wipe-on-failed-attempt forces a full retype (method finding, new this session).**
During the NL Mode-B pre-reinstall attempt, a mistyped MRZ key caused a genuine chip-access
failure (PACE `SW 0x6300`, then a BAC fallback attempt `SW 0x6985`). Per `wipeMrz()`'s own
contract ("called after every read attempt, success or failure"), the fields were cleared
after that failed attempt, forcing the owner to retype the entire MRZ by hand before the
retry could succeed. No document data was exposed by this — the failed-attempt logcat lines
contain only protocol status words, no MRZ or DG1 content — but the UX cost is real.
**Recommendation for M2 proper**: keep the typed fields on an access failure (BAC/PACE
establishment failure specifically — before any document data is read), and wipe only on a
successful read or on `onStop()` (leaving the screen), rather than on every attempt
regardless of outcome.

**F4 — ResultActivity renders DG1 personal fields; a live accessibility-snapshot exposure
occurred (privacy incident, contained).** ResultActivity (inherited unchanged from
`spikes/m0`) renders First name / Last name / Gender / Country / Nationality and the
Passive/Chip Authentication verdicts directly on screen. During this session, a
`monkey -c LAUNCHER` relaunch did not restart at the input form as expected — Android's
task-resume semantics brought the existing task, still on ResultActivity from the prior scan,
back to the foreground — and a scheduled accessibility-tree snapshot (intended for the input
form) landed on that screen instead, capturing partial field text (labels plus fragments of
values) into this agent's local tool-call transcript before the operator recognized the
screen and stopped. **Exposure was bounded**: on-device display plus a local session
transcript under the operator's own machine (`~/.claude`) — nothing entered this repository,
its docs, or git; no full name, full document number, or DOB value was extracted, logged
verbatim, or repeated after recognition. This finding is recorded with **no PII values** per
this file's own rule. **For M2 proper: this result screen must not exist as built.** Mode B
may show a verdict and the derived tag; Mode A must show only a verdict — never DG1/personal
fields on any screen, ever, regardless of mode. **Process fix adopted mid-session**: the
navigation procedure was changed to `adb shell am force-stop com.zkagent.m2scan` (kills the
task outright) before every relaunch, plus mandatory `adb shell dumpsys window | grep
mCurrentFocus` verification showing `RegularActivity` before any further accessibility
snapshot is taken. This procedure held for the remainder of the session (one retry was needed
once, when a relaunch landed on `NotificationShade` instead — resolved by `KEYCODE_HOME` then
retry, confirmed via the same focus check before snapshotting).

**F5 — mode-radio state did not match the mode the scan actually executed under (spike bug,
confirmed, not a mis-tap).** After the reinstall/relaunch cycle for the NL Mode-B
post-reinstall step, the UI was confirmed via accessibility snapshot to show `Radio [ref=5]
"Mode B (pseudonymous — derive zktag)" [checked]` immediately before the tap — yet the
resulting scan report read `mode: A (anonymous) — derivation SKIPPED, no zktag computed or
emitted`, with no `zktag_candidates` block at all. The owner confirmed they did not touch the
radio control between the snapshot and the tap, ruling out a mis-tap. That capture was not
discarded — it was a valid Mode-A read in its own right (clean chip data, both negatives
fired, chip auth true) and was kept as the TEST 3 NL capture (`mode-a-NL.txt`); the NL
Mode-B post-reinstall measurement was then redone with an explicit tap on the Mode B radio
immediately before the scan, reconfirmed via a second snapshot quoting the same `[checked]`
line, and that redo produced `mode: B` with all five candidates matching (see TEST 1 table
above).

Root-cause check against the actual code, per instruction to verify rather than assume: the
hypothesis going in was that the mode value is only updated by a `RadioGroup` change
listener, so a fresh launch would show the XML-default checked radio in the UI while an
internal field still held a stale initial value. **The code does not support this
hypothesis.** `MainActivity.kt` has no `setOnCheckedChangeListener` on `modeGroup` at all (a
targeted grep for it returns no hits), and there is no separate cached mode field — the mode
is read live, once, at tag-discovery time:

```kotlin
// MainActivity.kt, onNewIntent (~line 225)
val modeB = modeGroup.checkedRadioButtonId == R.id.mode_b
```

This reads `RadioGroup.checkedRadioButtonId` directly from the live UI at the moment a chip
tag is discovered — there is no caching, no listener-driven field, and no code path by which
a stale value could be read instead of the RadioGroup's current state. The XML layout
(`app/src/main/res/layout/activity_main.xml`, line 142) sets `android:checked="true"` on
`mode_b` (not `mode_a`) as the inflate-time default, which is consistent with the correct
Mode-B display the operator's own eyes and the snapshot both saw. **The actual root cause is
therefore not identified by reading the code** — the observed mismatch (correct UI state,
incorrect executed mode, no user interaction in between) is not explained by anything in
`MainActivity.kt`'s mode-handling logic as written, and this spike does not attempt to
diagnose further (e.g., an Android `RadioGroup` internal `mCheckedId`/visual-state desync
around inflate/relaunch timing, or an accessibility-snapshot/live-state race, are both
plausible but unverified). **Recorded as an open, unresolved spike bug for M2 proper**, with
this concrete recommendation: M2 proper must capture the presentation mode from a single,
tested source of truth read at the same instant a chip session begins, and that source-of-
truth read should itself be covered by an instrumented test (not just an accessibility
snapshot taken moments earlier by an external harness) before the mode gates any
zktag-vs-no-zktag derivation decision in production code.

**F6 — native-heap measurement is a weak signal (caveat carried from the masterlist
report).** `M2Masterlist`'s own logged caveat: `android.os.Debug.getNativeHeapAllocatedSize()`
before/after captures native allocation only — JVM/Kotlin object allocation during parsing
(the actual bulk of `M0Probe.loadMasterList`'s work) is not captured by this call. The -32 KB
full-load delta and the US/NL native-heap deltas in the per-scan reports should be read as a
coarse, non-authoritative signal only, not a memory-footprint measurement of the masterlist
parse itself.

**F7 — the derivation gate in this spike is mode-only, not verdict-gated (design input for
M2 proper, not a spike defect).** `MainActivity.kt`'s TEST 3 branch (~line 446) derives
zktag candidates whenever `modeB` is true — `if (modeB) { ... M0Probe.deriveCandidates(...)
... }` — with no check of the passive-auth verdict computed earlier in the same scan. That
is correct for a measurement probe: TEST 1 and TEST 3 need candidates on every mode-B run,
including runs with planted negatives, in order to observe what the derivation actually
produces. It is wrong for the product. M2 proper MUST gate minting on `passiveAuth.ok &&
passiveAuth.allowed == true` in the app itself, in addition to whatever the verifier already
enforces via the evidence tier — D21 (always read, conditionally mint; owner-confirmed
2026-08-31) puts the operator, not the verifier, in control of that gate. A masterlist real-
no (`ok:true, allowed:false`) must derive and emit no zktag at all — the challenge is
answered with a real no and consumed, not silently paired with a pseudonymous tag from an
issuer-untrusted document.

---

## What this establishes / does NOT establish

**Established, from this session's scans:**
- TEST 1 (zktag stability across app reinstall): PASS for both documents. The
  `document_number` derivation is stable across an uninstall/reinstall of the client app, for
  both a document with no chip authentication (US passport) and one with full chip
  authentication (NL ID card) — extending M0's own within-install stability finding along
  exactly the one axis M0 could not test.
- The `dg14_ca_key`/`dg15_aa_key` candidates (NL only) are also reinstall-stable, which
  further establishes that these two candidates derive from the chip's stored static key
  material, not per-session CA/AA ephemeral exchange randomness.
- TEST 2 (masterlist verification on the phone): PASS. Full load matches M0's own count
  exactly (588/588); the half-loaded-masterlist negative refuses cleanly via the pre-
  registered exception branch; the CSCA-removed negative fires with a nonzero excluded count
  on both documents; DS-cert validity dates are surfaced and were not found expired for
  either document at scan time.
- TEST 3 (mode A emits no zktag after a mode-B presentation): PASS for both documents. Mode
  A's report contains zero derivation output — an absent code path, not a redacted or
  placeholder value — confirmed for both the US passport and the NL ID card.
- The MRZ-persistence defect identified going in is designed out in this fork (F1), and the
  APK builds/installs/runs cleanly (unchanged from before any scan).
- The masterlist asset is byte-identical to M0's own bundled copy (unchanged from before any
  scan) and its provenance against a fresh BSI download is now independently verified —
  RESOLVED, see PENDING.

**Findings surfaced by running the scans (not established as "safe" — the opposite; these
are defects/risks to carry into M2 proper, per F1–F7 above):**
- F3: wipe-on-any-attempt forces an unnecessary full MRZ retype on an access failure.
- F4: the M0-inherited ResultActivity renders DG1 personal fields on screen and was
  transiently exposed to an automation snapshot this session — M2 proper must not have this
  screen in any form.
- F5: an unresolved, unexplained mismatch between displayed and executed presentation mode —
  a correctness risk for the mode gate itself, not just a UX issue, since a production system
  must not silently execute the wrong disclosure mode.
- F6: native-heap deltas throughout this document (and M0's) are a coarse signal only.
- F7: the spike's derivation gate is mode-only (`if (modeB)`), not verdict-gated — M2
  proper must additionally gate minting on `passiveAuth.ok && passiveAuth.allowed ==
  true` in the app.

**NOT established by this spike:**
- Renewal stability of the derivation field (D9's real open question) — untestable with
  documents in hand, unchanged from M0.
- Anything about the EU AV Blueprint / UK OSA wire-shape capture or the web→app→web handoff —
  out of scope for this spike (see Scope, top of file).
- The root cause of F5 — flagged as open, not diagnosed.
- Whether the CSCA-removed-negative outcome-bucket question (raised in TEST 2's
  PRE-REGISTERED section) should be reinterpreted for M2 proper — still unresolved, owner/
  orchestrator call pending.

---

## Owner tap protocol (hand-off — exact steps)

Device is unlocked and `com.zkagent.m2scan` is already installed (confirmed via `adb shell
pidof` — see SETUP). All commands below assume:
```bash
export ANDROID_HOME=/home/hamr/Android/Sdk
export PATH=$ANDROID_HOME/platform-tools:$PATH
cd /home/hamr/PycharmProjects/zkagent/spikes/m2-scan
```

1. **Clear logcat before every capture** — `adb -s 34011JEGR02358 logcat -c`.
2. **TEST 2 desk half (do this first, no document needed)**: open the app, tap
   "Run masterlist checks", then run `./capture-masterlist.sh`. Send/save the output.
3. **TEST 1 + TEST 2 chip half + TEST 3, part A**: in the app, leave **Mode B** selected
   (default), type the MRZ (document number, expiration date, date of birth) by hand, place
   the phone on the document, wait for the read to finish. Run `./capture-report.sh` and save
   the output as `pre-reinstall.txt`. Note which document was used (US passport or NL ID
   card — do not write the document number itself, just which document).
4. **TEST 1 reinstall step**:
   ```bash
   adb -s 34011JEGR02358 uninstall com.zkagent.m2scan
   adb -s 34011JEGR02358 install -r app/build/outputs/apk/regular/debug/app-regular-debug.apk
   adb -s 34011JEGR02358 logcat -c
   ```
5. **TEST 1, post-reinstall scan**: open the freshly-reinstalled app, Mode B still selected,
   type the SAME document's MRZ again by hand, scan the SAME document. Run
   `./capture-report.sh` and save as `post-reinstall.txt`.
6. **TEST 3, part B**: in the app, switch the radio button to **Mode A**, type the MRZ again,
   scan the same (or either) document. Run `./capture-report.sh` and save as `mode-a.txt`.
   Confirm this output has no `zktag_candidates` line.
7. Hand all four captures (`m2-masterlist-report.txt`, `pre-reinstall.txt`,
   `post-reinstall.txt`, `mode-a.txt`) back to the orchestrator — they are PII-free by
   construction (field names/hashes/verdicts only), so they can be pasted directly.

If time allows, repeat steps 3–6 with the **second document** (whichever was not used first)
to get both US-passport and NL-ID-card readings, matching M0's own two-document coverage.

---

## PENDING

- [x] Owner scans per the protocol above; MEASURED sections filled in. (2026-08-31, both
      documents, all three tests, TEST 1/2/3 all PASS — see MEASURED sections above.)
- [x] Escalation resolved: which outcome bucket (`ok:false`/"could not check" vs. `ok:true,
      allowed:false`/"no") the CSCA-removed negative is *intended* to land in for M2. RESOLVED
      by orchestrator ruling, recorded in the PRD (§6 M0 row marked superseded; §6 M2 row
      carries the two-bucket rule: integrity failure ⇒ `ok:false`, missing-issuer-CSCA on a
      well-formed list ⇒ `ok:true, allowed:false`) — matches this spike's implemented and
      measured behavior exactly; see the resolution note in TEST 2's escalation paragraph
      above.
- [ ] F5 (mode-radio mismatch) root cause — confirmed not explained by `MainActivity.kt`'s
      mode-handling code as written; not diagnosed further in this spike. M2 proper should
      not inherit this ambiguity — see F5's recommendation.
- [ ] F4 (ResultActivity DG1-rendering screen) must not exist in M2 proper in any form —
      not a "fix the existing screen" item, a "do not build this screen" item.
- [ ] F3 (wipe-on-any-attempt UX) — recommend keeping fields on access failure, wiping only
      on success or `onStop()`.
- [ ] F7 (mode-only derivation gate) — M2 proper must gate minting on
      `passiveAuth.ok && passiveAuth.allowed == true` in the app, on top of whatever the
      verifier enforces via the evidence tier; a masterlist real-no must derive and emit
      no zktag.
- [x] BSI ZIP provenance re-check — RESOLVED (2026-08-31). Committed asset (899,665 bytes,
      sha256 `da33466b…7b8ae`) is byte-identical to M0's copy (`cmp`). Fresh BSI download via
      headless browser (bsi.bund.de blocks curl with Akamai Bot Manager): CSCA page → German
      master list → `GermanMasterList` ZIP (507,904 bytes, sha256 `ac294f59…26b612c`) →
      `DE_ML_2026-05-28-08-28-45.ml` (CMS SignedData, 902,359 bytes, sha256
      `e036f8c9…d7526dd0`, signingTime 2026-05-28 06:28:45 GMT). `openssl cms -verify`
      succeeded — signer `CN=CSCA Master List Signer, serialNumber=0039, C=DE, O=bund,
      OU=bsi`, issuer `CN=csca-germany`. Extracted eContent (899,665 bytes, sha256
      `da33466b…7b8ae`) matches the committed asset exactly — it is the raw eContent (SET OF
      Certificate, 588 certs), not the CMS wrapper. Verdict: IDENTICAL — provenance verified.
- [ ] Renewal stability of `document_number` — untestable with documents in hand.
- [x] `com.zkagent.m2scan` uninstalled from the test device at end of session (see SETUP/
      end-of-session note) — confirmed via `adb shell pm list packages | grep m2scan`
      returning empty.
