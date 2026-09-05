# M3 scanner S2/S3 evidence (Pixel 6a, 2026-09-04/05)

**Status**: source record for milestones.md §6.5 S2 (pre-tap question line, D74) and S3
(scan-pane cleanup, D74/D75) as built in `apps/scanner`, UNCOMMITTED at the time of writing
(working tree on `feat/m3-poc`, HEAD `2b7d9ae`; see `git status --short apps/scanner` /
`git diff --stat apps/scanner` for the exact file list). Not an M3 criterion itself — §6.3
item 13 runs M3 against the released scanner APK as-is; S2/S3 are scanner-side follow-ups built
alongside M3b per §6.5's own framing. Also covers two owner-approved fixes made alongside S2/S3
(the 409-refusal report defect and the `error_read` dialog wording) and two pane relocations
(the M2 probes' move to a new Diagnostics tab).

**Rule for this file (carried from `M0-EVIDENCE.md` through `M3-POC-EVIDENCE-2026-09-04.md`)**:
no PII values, ever — field names, verdict strings, timings, hashes/truncated transaction
identifiers, and 12-char zktag prefixes only. No MRZ field, name, date of birth, document number,
raw zktag, nonce, public key, or signature appears anywhere in this file.

---

## Setup

Pixel 6a. Four debug builds installed over the evening of 2026-09-04/2026-09-05, each an
`adb install -r` upgrade over the previous (keystore retained across builds — see the
second-device caveat under Session B). `apps/scanner` unit tests: 375 tests, 0 failures, per the
JUnit XML from the 2026-09-05 01:08:25 run. `apps/demo` server on port 8787 via
`adb reverse tcp:8787 tcp:8787`, same store used across sessions.

Changed files this build touched (`git diff --stat apps/scanner`, working tree vs. HEAD):

| File | +/- |
|---|---|
| `MainActivity.kt` | +325/-26 |
| `PaneState.kt` | +11/-4 |
| `PaneVisibility.kt` | +8/-3 |
| `SessionDisplay.kt` | +116/-8 |
| `res/layout/activity_main.xml` | (rewritten, +/- interleaved, net +250) |
| `res/values/strings.xml` | +30/-7 |
| `PaneStateTest.kt` | +58/-0 |
| `PaneVisibilityTest.kt` | +17/-0 |
| `SessionDisplayTest.kt` | +188/-0 |
| `VerifierRefusal.kt` | new file |
| `VerifierRefusalTest.kt` | new file |

Total: 9 modified + 2 new files, 1,072 insertions / 211 deletions (`git diff --stat`, unstaged).

---

## What was built

### S2 — pre-tap question line (D74)

A bold, centered, 16sp question line above the main Scan/Verify button, sourced from the
verified request object (never a hardcoded string):
- Tier A: "This website asks if you are over 18"
- Tier B: "This website asks if you are over 18, and may recognise you again on this site"
- No pending verified request: "Local scan (no site)" (D46 wording, unchanged)

Carried by a new `SessionDisplay` projection field `questionText`, driven off
`HandoffState.Verified.threshold`.

### S3 — scan-pane cleanup (D74/D75), per DP7

Pane order, top to bottom: MRZ fields → mode line (small) → S2 question line → "Paste link"
compact text button → single Scan/Verify button → `report_view` inside the scroll.

- The old `description` blob is removed from the pane entirely. Its disclosure content (§6.2
  item 5, no-document-field-storage) and D69's camera hint moved to a new About dialog, not
  deleted.
- "Paste link" replaces the old always-visible paste field, per D75's final rule: idle/pending
  state reveals one input row on tap; locked/read-in-progress state dims the control with the
  hint "Finish this scan, or close and reopen the app to paste."
- The main button reads "Tap and verify" once pasted text is pending (`LockButtonLabel.APPLY_PASTE`)
  — tapping it both applies the paste and starts the scan (owner correction, D75; superseding an
  earlier separate-apply-step reading).
- The two M2 probes (masterlist check, device-key self-test) move to a new third tab,
  "Diagnostics" (`PaneState.TAB_DIAGNOSTICS`, `PaneVisibility.Pane.DIAGNOSTICS`), with their own
  output view fed by `ReportLog` via one `applyReportText()` fan-out — a single writer, not two.

### FIX (owner-approved 2026-09-05, "fix 409") — non-2xx `direct_post` misreported as PASS

Before: a non-2xx response from the verifier's `direct_post` endpoint was still reported as
"verdict: PASS (bare presentation sent)", with no dialog — the app could not distinguish "sent
and accepted" from "sent and refused."

After: a new pure `VerifierRefusal` classifier maps the response to one of three outcomes:
- 2xx → Sent
- 409 + `already_responded` → AlreadyUsed → dialog "This link was already used — reopen the
  link from the site."
- anything else → Refused → dialog "Verifier refused: `<error|HTTP n>`"

Each outcome now gets its own report line plus a D43 blocking dialog where appropriate (no
change to the KEEP/RESET transition rule). Finding #22 opened in `.claude/remember/findings.md`:
the same hardcoded-PASS defect exists on four other branches of the response-handling code and
was NOT fixed this session — tracked, not silently left.

### FIX — `error_read` dialog wording

Before/after are both access-establishment-classified (`Classification.ACCESS_ESTABLISHMENT`,
KEEP transition, unchanged) — only the string changed:
- Before: "Couldn't read — check your details and try again."
- After: "Couldn't read — check your details, then hold your document to the phone again."

Button stays dimmed while the session is armed. Projection unchanged; regression-lock tests
added (`SessionDisplayTest.kt`) to pin the new string against the same classification.

### Paste refusals — Snackbar removed

A refused paste attempt (e.g. mid-read) now surfaces via a new non-wiping `showBlockingNotice`,
not a Snackbar — reusing the outcome dialog for this was ruled out by finding #12 (a
terminal-outcome mechanism must not be repurposed for a non-terminal notice).

---

## Device evidence (logcat, value-free; owner reports)

### Session A — build 1, 00:03–00:09

Question line rendered "Local scan (no site)" bare on open; after an `av://` intent verified,
line changed to "This website asks if you are over 18." Paste area revealed on tap but nothing
applied — no apply control existed yet in this build. **Fixed in build 2** (main button gains
the apply-paste behavior).

### Session B — builds 2/3, 00:27–00:30

Paste-applied tier-A scan completed successfully (server verdict `allowed=true`). Re-tapping a
link whose transaction had already completed produced a `direct_post` HTTP 409
(`already_responded`); the app logged "verdict: PASS (bare presentation sent)" — this is the 409
defect fixed afterward (see FIX above). Owner feedback from this session: question line must be
bold/centered; the paste control was too large/prominent; the two probes belong on their own tab.

Note: builds were installed via `adb install -r` throughout, which retains the app's existing
keystore entry across upgrades — so no key-mismatch case was exercised this evening; the
second-device (different key, same zktag) negative remains untested on real hardware (still
covered only by `apps/demo/tests/tier-b.test.mjs`, per the prior POC evidence doc).

### Session C — build 3, 01:01–01:03

Second tap of a now-used link: log line "verdict: REFUSED — verifier: link already used (HTTP
409, already_responded)"; dialog "This link was already used — reopen the link from the site."
shown — confirms the 409 fix. Tier-B handoff on this build: question line read "This website
asks if you are over 18, and may recognise you again on this site"; server verdict
`allowed=true`, `attester=matched`.

Diagnostics tab: both probes ran (log lines "===== MASTERLIST PROBE =====" and "===== DEVICE KEY
SELF-TEST =====" both present) but their output was not visible on the tab in this build —
**fixed in build 4** (routed through the single `applyReportText()` fan-out).

### Session D — build 4, owner report 2026-09-05

Wrong-digit read: dialog "Couldn't read — check your details, then hold your document to the
phone again," session stayed armed, corrected re-hold succeeded. Correct read: "ID scanned
successfully." Owner, verbatim intent for this path: "this is the failure mode we need for
failed to scan either wrong details or card was moved too quickly/away."

Diagnostics tab output visibility on build 4: **owner-confirmed 2026-09-05** ("diagnosis worked fine") — both probe reports render under the buttons on the Diagnostics tab.

### Server (`apps/demo` log)

Tier-A verdicts `allowed=true` for each completed scan this evening. The 409 case created no
verdict record (consistent with the request never reaching a valid `direct_post`). One tier-B
verdict `allowed=true`.

---

## What this run did and did NOT establish

**Did establish:**
- S2's question line renders correctly for tier A, tier B, and bare-local-scan cases, sourced
  from the verified request object, across builds 1–4.
- S3's pane reorder, paste-button reveal/apply/dim states, and Diagnostics-tab relocation all
  behaved as specified on at least one build each.
- The 409 misreport (finding, this session) is fixed and device-confirmed: a used link now
  produces a Refused/AlreadyUsed dialog instead of a false PASS.
- The `error_read` wording change is device-confirmed and does not alter the underlying KEEP
  transition.
- 375/375 unit tests pass per the JUnit XML at 2026-09-05 01:08:25.

**Established since (2026-09-05, later same evening):**
- The second-device / different-key-on-an-already-bound-zktag negative — not exercised in Sessions
  A–D above (only `adb install -r` upgrades were used, which retain the keystore across builds) —
  is now device-confirmed: an `adb uninstall`/reinstall of the same build 4 APK empties the
  AndroidKeyStore, and the following tier-B handoff with the NL ID card correctly refused
  (`ok=true allowed=false reason=attester_key_mismatch`). See
  `M3-POC-EVIDENCE-2026-09-04.md`, Session 4, for the full record.

**Did NOT establish:**
- Finding #22 (same hardcoded-PASS-on-non-2xx defect on four other response-handling branches) —
  opened, not fixed, this session.
- No formal accessibility-snapshot capture was taken this session; all device evidence above is
  logcat plus owner report, per the setup.

---

**No PII values appear anywhere above.** All quoted log lines, dialog strings, and file diffs
are value-free by construction — field names, verdict strings, boolean/status fields, and
line-count diffs only — checked against this file's own rule and the project standard it
inherits through `M3-POC-EVIDENCE-2026-09-04.md` before inclusion.
