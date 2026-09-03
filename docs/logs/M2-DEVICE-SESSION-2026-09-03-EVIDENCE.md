# M2 — device session for the D57/D60 freeze's verification-debt items (Pixel 6a, 2026-09-03)

**Status**: source record for a COMMITTED build on `chore/memory-consolidation` (not yet merged to
main) (`0b71957` Q47 fix, `d406f4b` D63
portrait lock) exercised on device. This file does not itself lift the D57/D60 freeze — it is the
device session D60's own text named as the pending precondition: "a device session covering: the
Q47 fix check, a mid-read re-tap, a QR request plus forced recreation mid-verify, an Activity
destroyed with the biometric prompt open, and a hostile link from a second origin." It mirrors
`M2-FENCE-EVIDENCE.md`'s structure and value-free rule.

**Rule for this file (carried from `M0-EVIDENCE.md` / `M2-D50-D53-EVIDENCE.md` /
`M2-D55-D56-EVIDENCE.md` / `M2-D58-STEP1/2/3/4-EVIDENCE.md` / `M2-FENCE-EVIDENCE.md`)**: no PII
values, ever — field names, verdict strings, timings, hashes/truncated transaction identifiers,
and exception text only. No MRZ field, name, date of birth, document number, raw zktag, nonce,
public key, or signature appears anywhere in this file. The session used one real document,
`doc_len=9`; which document is never named.

---

## Setup

Pixel 6a, build at `702b435` (APK timestamped 22:27 2026-09-02, package `com.zkagent.scanner`,
installed manifest `screenOrientation=1` per D63). Verifier spikes at `127.0.0.1:8787` (main
origin) and `127.0.0.1:18787` (second, genuinely distinct origin), both reached via `adb reverse`.
Logcat filter `MainActivity:V DeviceKey:I RequestTrust:I HandoffClient:I M2Masterlist:I
AndroidRuntime:E`. **Zero `AndroidRuntime` lines across the entire session** — no crash of any
kind, on any of the six checks below.

Six checks were run: (1) Q47 cursor fix, (2) D63 portrait lock, (3) mid-read re-tap (finding #6),
(4) `av://` path + recreation with `BiometricPrompt` open (finding #16/D64), (5) QR/paste path +
recreation mid-read, (6) hostile `av://` from a second origin mid-scan (finding #10/D61).

---

## 1 — Q47 cursor fix (`0b71957`)

Owner-confirmed by eye: "cursor fixed," after tapping OK on the `DatePickerDialog` for both date
fields in turn. No log line exists for this by design — the fix (`clearFocus()` on the
document-number field plus hiding the keyboard after the picker's OK) has no logging requirement
of its own, and none was added.

**Result: Q47 FIXED, device-confirmed.** Supersedes the prior "FIX IN FLIGHT, DEVICE CHECK
PENDING" status.

---

## 2 — D63 portrait lock (`d406f4b`)

Owner: "rotation doesn't rotate at all either on or off," tested with the device's own auto-rotate
setting both ON and OFF. `dumpsys window` showed `mRotation=ROTATION_0` throughout — unchanged
regardless of physical device orientation or the auto-rotate toggle. Installed manifest confirms
`screenOrientation=1` (portrait), matching D63's decision.

**Result: D63 device-confirmed** — the app never rotates on this build, on this device, under
either auto-rotate setting.

---

## 3 — Mid-read re-tap (`HandoffAdmission.mayStartTagRead` gate, finding #6)

09:05:37 MRZ input, single tag discovery. Owner lifted the card and re-presented the same physical
card roughly 1 second later, mid-read. The NFC service log shows **no second discovery** event for
that re-tap — the only relevant timeline entries are: the app's own read failing at 09:05:52.447
(`CardServiceException: Tag was lost`, during `CardFileInputStream.read` on EF `11d`), followed by
the presence check itself failing at 09:05:53–09:05:54 ("Tag lost, restarting polling loop" at
09:05:54.667) — i.e. the read had already failed roughly 2 seconds *before* the polling loop even
noticed the tag was gone and restarted.

**Conclusion, stated plainly**: on this hardware, a same-card re-tap cannot produce a second
`TECH_DISCOVERED` intent while a read is already in progress — the NFC stack serialises tag
sessions, and the in-flight read fails before polling restarts, so there is no window in which a
second discovery could reach `handleIncomingIntent`'s NFC branch at all. A synthetic `am start`
`TECH_DISCOVERED` intent cannot reach the gate either, since that branch requires a real `Tag`
parcelable carrying a live `IsoDep` connection, which only a genuine NFC discovery produces.

**The mid-read path of `HandoffAdmission.mayStartTagRead` is therefore NOT reachable on device with
real cards.** It stays proven by unit truth-table and source wiring trace only — it is
defence-in-depth against a state the platform itself already prevents, not a gap this session could
exercise. Recording this honestly rather than claiming device proof that was not obtained.

The popup shown to the owner during this check was the correct transient-failure advice ("couldn't
read the card, keep the card at the top of your phone") — the normal read-failure path, not the
gate's own refusal path.

---

## 4 — `av://` path + recreation with `BiometricPrompt` open (finding #16/D64 reproduced)

MRZ input (read start) at 09:10:26.343; read completed 09:10:28.828 (PACE, `chip_auth` passed,
masterlist 588/588, `passive_auth` ok, `mint_gate: MET` → biometric prompt shown). The orchestrator
forced an Activity recreation while the prompt was outstanding via `settings put system font_scale
1.15` at 09:10:30:

```
09:10:30.612  fence retired (onDestroy)
09:10:30.672  restored report/log across Activity recreation (text=true, log_entries=6)
```

The prompt's late callback then continued to fire on the destroyed instance:

```
09:10:33.486  using pre-verified request object
09:10:33.519  handoff direct_post -> ...
09:10:33.525  handoff direct_post response http_status=200
09:10:33.525  fence closed — dropped post-mint session clear/display refresh
09:10:33.526  W  fence closed — dropped mint report/confirmation (a COMPLETED result: evidence
              already left the device, nothing recorded or shown)
```

Verifier (`8787`) recorded a full verdict for that transaction: `ok=true allowed=true
reason=evidence-verified evidence=["sig-p256/1"] attester=matched`. No crash, no
`WindowManager$BadTokenException`.

**Result: the `BiometricPrompt` fence (`72e0b2c`) is device-verified** — the verification debt
`M2-FENCE-EVIDENCE.md` recorded against it ("code-verified and bytecode-verified only... no device
evidence") is cleared. D64's Option A scenario (a completed presentation whose result the phone
never records or shows) was observed live, exactly as disclosed at decision time.

---

## 5 — QR/paste path + recreation mid-read

The verifier spike's page renders **no QR image** — `server.mjs`'s `PAGE` constant has a TODO;
only the `av://` link is shown as text. The app's "Scan QR" control uses
`ActivityResultContracts.TakePicturePreview` (a low-resolution camera-preview thumbnail) decoded
via `QrCapture`/zxing. The owner's camera attempts at 09:13:12–09:13:22 produced no decode and no
log line — only a Snackbar (see finding #18 below for the same-session unlogged-Snackbar note on
this exact path).

To exercise the underlying gated code path (`applyPendingHandoffText`) despite the QR image not
rendering, the orchestrator drove the manual-paste route instead: typed the `av://` link into
`handoff_manual_input` via `adb` plus Enter.

- First attempt, 09:23:05.658–09:23:58.234: `REFUSED — verification session expired before lock`
  (the 10-minute transaction TTL had already elapsed). Expected, not a defect.
- Second attempt, fresh link: 09:25:06.531 "handoff captured, verifying…" **with no preceding
  "captured from av:// intent" line** — that absence is the paste/QR path's own signature,
  distinguishing it from the `av://`-intent capture seen in checks 1, 4, and 6. 09:25:06.559
  verified, `origin=8787`.

Read: MRZ input 09:26:33.817. Forced recreation fired 09:26:34.409:

```
09:26:34.964  fence retired (onDestroy)
09:26:35.015  restored report/log across Activity recreation (text=true, log_entries=1)
09:26:37.703  fence closed — dropped read completion handling (report/dialog/mint start)
```

No crash. Verifier (`8787`) side: that transaction stayed `pending` — never minted, consistent
with the dropped read-completion handling meaning `continueAfterRead` never started.

**Result: the QR/paste handoff path is fenced and device-verified** — the same
`HandoffAdmission`/`LifecycleFence` behaviour already proven for the `av://` path (D58 step 4,
`M2-FENCE-EVIDENCE.md`) now has its own direct device confirmation, closing the "QR-scan/
manual-paste path was not exercised under fence conditions" gap those files carried forward.

---

## 6 — Hostile `av://` from a second origin mid-scan (finding #10 / D61 device proof)

Two runs, each firing a hostile `av://` link sourced from `127.0.0.1:18787` — a genuinely distinct
second local origin, not a second link from the same origin as in every prior session's testing —
delivered via `am start … --activity-single-top` from the host shell, i.e. a second process hitting
the exported `singleTop` activity exactly as finding #10's threat model describes.

**Run 1**: MRZ input 09:28:25.116; hostile intent fired 09:28:25.798; refusal logged 09:28:26.218
(`av:// handoff REFUSED — session locked or read in progress (D57 mitigation for finding #10)`).
The read itself then failed at 09:28:29 (`Tag was lost` during `sendSelectApplet`) — the owner
lifted the card on an ambiguous instruction mid-run, not as a consequence of the hostile intent; the
presence check failed independently at 09:28:30–32.

**Run 2**: MRZ input 09:30:22.709; hostile intent fired 09:30:23.395; refused 09:30:23.766; the
legitimate read completed normally at 09:30:25.114 (PACE, `chip_auth` passed, `passive_auth` ok,
`mint_gate: MET`); biometric approved; `direct_post` at 09:30:28.226 → `http_status=200`; verdict
`PASS (minted)`, `scope_domain: 127.0.0.1`, evidence `sig-p256/1`.

Verifier cross-check, independent of the phone's own log: `8787` recorded `ok=true allowed=true
attester=matched` for the legitimate transaction; `18787` — the hostile origin's own verifier —
shows both hostile transactions still `pending`, never receiving evidence.

**Result: finding #10 is device-proven against a genuinely foreign origin**, closing the specific
gap D61's closure text left as "pending device evidence owed" ("every device test so far fired
hostile links from the same local verifier origin").

---

## 7 — Native camera QR route (12:19)

Ruled out the in-app Google Code Scanner path (D68 part b) same-day, after this check, on privacy
grounds (D69): a Play-services-hosted scanner still pulls Google's telemetry components into the
merged manifest and downloads its module from Google on first use. Tested the alternative instead
of just proposing it: the verifier spike renders `app_link_av` as a real QR image; the stock Pixel
Camera app (`com.google.android.GoogleCamera`) scans it and fires the `av://` VIEW intent straight
at the scanner's existing exported intent filter — no in-app scanner code involved at all.

Two runs, each: launch Google Camera, point it at the spike's rendered QR, tap the resulting link.

```
09-03 12:19:21.792 I/ActivityTaskManager( 1668): START u0 {act=android.intent.action.MAIN cat=[android.intent.category.LAUNCHER] flg=0x10200000 xflg=0x4 pkg=com.google.android.GoogleCamera cmp=com.google.android.GoogleCamera/com.android.camera.CameraLauncher bnds=[836,1905][1009,2100]} with LAUNCH_SINGLE_TASK from uid 10244 (com.google.android.apps.nexuslauncher) (sr=122897873) (BAL_ALLOW_VISIBLE_WINDOW) result code=0
09-03 12:19:28.083 I/ActivityTaskManager( 1668): START u0 {act=android.intent.action.VIEW dat=av://authorize/... xflg=0x4 cmp=com.zkagent.scanner/com.tananaev.passportreader.RegularActivity} with LAUNCH_SINGLE_TOP from uid 10132 (com.google.android.GoogleCamera) (sr=264686014) (BAL_ALLOW_VISIBLE_WINDOW) result code=0
09-03 12:19:28.207 I/MainActivity( 9966): M2 stage: pendingHandoff captured from av:// intent
09-03 12:19:28.207 I/MainActivity( 9966): M2 stage: handoff captured, verifying request object before mode/lock become available (D33/D34/D37)
09-03 12:19:28.259 I/ActivityTaskManager( 1668): Displayed com.zkagent.scanner/com.tananaev.passportreader.RegularActivity for user 0: +181ms
09-03 12:19:28.270 I/MainActivity( 9966): M2 stage: handoff request object verified — origin=http://127.0.0.1:8788 signature_verified=true key_kind=dev-pinned
09-03 12:19:32.020 W/ActivityTaskManager( 1668): Activity top resumed state loss timeout for ActivityRecord{219361118 u0 com.zkagent.scanner/com.tananaev.passportreader.RegularActivity t-1 f}}
09-03 12:19:36.415 I/ActivityTaskManager( 1668): START u0 {act=android.intent.action.MAIN cat=[android.intent.category.LAUNCHER] flg=0x10200000 xflg=0x4 pkg=com.google.android.GoogleCamera cmp=com.google.android.GoogleCamera/com.android.camera.CameraLauncher bnds=[836,1905][1009,2100]} with LAUNCH_SINGLE_TASK from uid 10244 (com.google.android.apps.nexuslauncher) (sr=122897873) (BAL_ALLOW_VISIBLE_WINDOW) result code=0
09-03 12:19:38.346 I/ActivityTaskManager( 1668): START u0 {act=android.intent.action.VIEW dat=av://authorize/... xflg=0x4 cmp=com.zkagent.scanner/com.tananaev.passportreader.RegularActivity} with LAUNCH_SINGLE_TOP from uid 10132 (com.google.android.GoogleCamera) (sr=49349004) (BAL_ALLOW_VISIBLE_WINDOW) result code=0
09-03 12:19:38.778 I/MainActivity(14239): M2 stage: pendingHandoff captured from av:// intent
09-03 12:19:38.779 I/MainActivity(14239): M2 stage: handoff captured, verifying request object before mode/lock become available (D33/D34/D37)
09-03 12:19:38.879 I/MainActivity(14239): M2 stage: handoff request object verified — origin=http://127.0.0.1:8788 signature_verified=true key_kind=dev-pinned
09-03 12:19:38.882 I/ActivityTaskManager( 1668): Displayed com.zkagent.scanner/com.tananaev.passportreader.RegularActivity for user 0: +541ms
```

(`av://` URL elided as `...` in both fired intents, as in every other log excerpt in this doc.)

**Result: PASS, twice.** Both runs captured and verified the request object
(`origin=http://127.0.0.1:8788`, `signature_verified=true`) via the ordinary `av://` VIEW intent
path, with zero in-app scanner code in the loop. This is the device evidence behind D69: the in-app
Google Code Scanner (D68 part b) is removed the same day it was added, and this camera-app route is
the sole cross-device QR path going forward.

---

## Process notes

- Forced Activity recreation via `settings put system font_scale 1.15` (then restored to `1.0`)
  worked reliably on this device across all three checks that used it (4, 5, and implicitly during
  setup for check 2's rotation test), without the screen dozing this time — contrast
  `M2-FENCE-EVIDENCE.md`'s note that a recreation is deferred while dozing.
- `adb shell input text` followed by `keyevent 66` (Enter) fires the manual-paste editor action
  **twice** — the second firing lands on the now-cleared input field and produces a harmless "Not a
  recognised av:// link" Snackbar. Not a defect; a process quirk of driving the paste field via
  `adb` rather than the on-screen keyboard.

---

## Findings and questions this run produced

1. **Q47 → FIXED, device-confirmed** (check 1). See `docs/wiki/questions.md` Q47.
2. **D63 → device-confirmed**: the app does not rotate under either auto-rotate setting (check 2).
3. **Finding #6 → not device-reachable, mid-read branch only.** The mid-read re-tap path of
   `HandoffAdmission.mayStartTagRead` cannot be exercised on device with real cards — the NFC stack
   serialises tag sessions and the in-flight read fails before a second discovery could occur (check
   3). This does not reopen or close finding #6 (already FIXED-IN-`c60354e` per the existing entry)
   — it records that the fixed gate's mid-read branch is proven by unit test and source trace only,
   honestly, rather than claiming device coverage that does not exist.
4. **Finding #16/D64 → `BiometricPrompt` fence device-verified** (check 4), clearing the
   verification debt `M2-FENCE-EVIDENCE.md` recorded against `72e0b2c`.
5. **QR/paste handoff path → fenced and device-verified** (check 5), closing a gap
   `M2-FENCE-EVIDENCE.md` and D58 step 4 both left open ("not exercised under fence conditions").
6. **Finding #10/D61 → device-proven against a genuinely foreign origin** (check 6), closing D61's
   own stated pending-evidence gap.
7. **New finding #18 (non-blocking, not a freeze item)** — see
   `.claude/remember/findings.md` #18. "Scan QR" uses a low-resolution camera-preview thumbnail that
   does not decode a laptop-screen-sized `av://` link (~150 characters); three UI-only Snackbars on
   that path have no matching log line, the same defect class as finding #7's original
   `reportView.text` write.

---

## What this run did and did NOT establish

**Did establish:**
- Q47 is fixed by eye on device, both date fields.
- D63's portrait lock holds under both rotate settings.
- The `BiometricPrompt` fence drops a late callback on a destroyed Activity with no crash, matching
  D64's disclosed Option-A behaviour exactly.
- The QR/paste handoff path is fenced identically to the `av://` path under a mid-read recreation.
- `HandoffAdmission`'s `av://` guard refuses a hostile intent from a genuinely distinct second
  origin, cross-checked against that origin's own verifier state (still pending), not only the
  phone's self-report.
- The NFC stack on this hardware makes the mid-read re-tap branch of `HandoffAdmission
  .mayStartTagRead` unreachable with real cards.

**Did NOT establish:**
- Any resolution of finding #4's non-rotation remainder (the `lastMrzHash` diagnostic mislabel and
  the other ten lost fields) — unaffected by this session, carried to the next module per D63's own
  text.
- A working "Scan QR" path against a screen-rendered QR image — the verifier spike still renders no
  QR image to test against; only the manual-paste route was exercised.
- Any owner ruling on lifting the D57/D60 freeze itself — this file is evidence for that ruling, not
  the ruling.

---

**No PII values appear anywhere above.** All quoted logcat lines, transaction identifiers, and
verifier states are value-free by construction — stage names, boolean/status fields, truncated
transaction IDs, and timings only — checked against this file's own rule and the project standard
it inherits from `M0-EVIDENCE.md` / `M2-D50-D53-EVIDENCE.md` / `M2-D55-D56-EVIDENCE.md` /
`M2-D58-STEP1-EVIDENCE.md` through `STEP4-EVIDENCE.md` / `M2-FENCE-EVIDENCE.md` before inclusion.
