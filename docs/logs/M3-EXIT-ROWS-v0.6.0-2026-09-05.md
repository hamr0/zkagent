# M3 exit rows — re-run on v0.6.0 build (Pixel 6a, 2026-09-05)

**Status**: source record for `docs/wiki/milestones.md` §6.3 exit-criteria row 13's owner-chosen
option (a) — cut a release incorporating §6.5 S1–S3/D79 and re-run M3's exit rows against that
released build, rather than re-baselining against the untouched `v0.5.0` APK. This file is that
re-run, against the build at the `v0.6.0` release tag (`de97c82`). It also carries fresh evidence
for the exit rows the S1–S3/D79 changes touch (items 4, 5, 6, 7, 10).

**Rule for this file (carried from `M0-EVIDENCE.md` through
`M3-SCANNER-S2-S3-EVIDENCE-2026-09-05.md`)**: no PII values, ever — field names, verdict strings,
timings, hashes/truncated transaction identifiers, and 12-char zktag prefixes only. No MRZ field,
name, date of birth, document number, raw zktag, nonce, public key, or signature appears anywhere
in this file.

---

## Setup

Pixel 6a. Debug APK built at `de97c82` (`versionName` 0.6.0, `versionCode` 3), installed
**in place over build 7** (`adb install -r`) — app keys and the S1 per-origin threshold lock were
kept, not reset. `dumpsys package com.zkagent.scanner` confirmed `versionName=0.6.0` after
install.

**Release-signing fact, not a defect.** There is no release keystore anywhere in this repo or on
this machine. `assembleRegularRelease` therefore produces only
`app-regular-release-unsigned.apk` (22 MB; `network_security_config` carries
`cleartextTrafficPermitted="false"` with zero exceptions, verified via `aapt2 dump` during
`/release`) — an unsigned artefact, not something that can be installed or run. No signed scanner
release build has ever existed in this project. "The released scanner APK" in item 13's wording
therefore means, and has only ever meant, the debug build sideloaded at the release tag's commit.
Flagging this back to the owner as a fact to note (a signed release keystore would be a new PRD
item), not something this run treats as a gap.

`apps/demo` run from the working tree at `de97c82`, reachable at `http://127.0.0.1:8787` via
`adb reverse tcp:8787 tcp:8787` (item 7/D76). Store file `apps/demo/data/store.json` held 2
zktags / 2 attester bindings (NL `..3266b46a`, US `..9b7181fe`) before the server restart exercised
below — both minted in an earlier session, not fresh to this run.

---

## 1 — Threshold rows (item 6 / §6.5 S1), `av://` VIEW intents, 20:56

The S1 per-origin lock persisted across the in-place `adb install -r` upgrade (i.e. across a
version change, not just a force-stop/reopen as S1's own evidence already covered).

| Link threshold | Result |
|---|---|
| 21 | Refused — "REFUSED by threshold policy — host=127.0.0.1 threshold=21" |
| 43 | Refused |
| 18 (plain) | Admitted; question line "This website asks if you are over 18" |

---

## 2 — Pre-restart tier-B scan, US passport (21:07)

21:07:00 — one attempt with details not matching the document held: BAC failed `SW 0x6982`, app
reported "MRZ input UNCHANGED" (the standard access-establishment failure branch), retried once
with the same wrong details, same result. Not a build issue — same signature as the earlier D79
wrong-details device run.

21:07:43 — corrected details, US passport: registered as this document's first sight at this
verifier (`attester=bound_first_sight`, `access_protocol=PACE`).

---

## 3 — Server restart

Server process killed (SIGTERM) and relaunched with the same command line at ~21:10. Store
re-read on relaunch: 2 zktags, 2 bindings — unchanged, confirming the persistent-store restart
requirement (item 10) still holds on this build.

---

## 4 — Post-restart tier B, both documents (item 4, duplicate-zktag rejection)

**NL ID card** — two transactions, `o6UJxzD-zEiOOqrc` and `69qzkDHdfJWOgZuh`: both
`allowed=true attester=matched`, zktag `..3266b46a`, `zktag_seen_before=true
already_registered=true`. Phone: "verdict: DELIVERED (minted)" at 21:56:46.

**US passport** — transaction `QlaSMa0cqPg-Xi96`: `allowed=true attester=matched`, zktag
`..9b7181fe`, `seen_before=true already_registered=true`. Phone: "verdict: DELIVERED (minted)" at
21:58:38.

Both documents mint-then-duplicate correctly across the restart, on the build at the release tag.

---

## 5 — Tier A, indistinguishability (item 5)

Two transactions, `n6W9IaL7xvDUtncX` and `_o4btlqf640Bsv1d`: both `allowed=true
reason=no-evidence-required`. Phone: "verdict: DELIVERED (bare presentation sent)" at 21:55:23.

Diff computed by the orchestrator with `apps/demo/compare.mjs`'s `diffPresentations`, against the
server's own stored presentations (not the page display). Exactly 3 fields differ:
`challenge.nonce`, `challenge.issued_at`, `challenge.expires_at`. Everything else is identical:
`claim.over_threshold=true`, `claim.threshold=18`, tier A, `evidence=[]`, `spec=zkagent/1`,
`verbs=[]`. Matches D77's specified 3-field diff exactly — no regression from the S1–S3/D79
changes.

---

## 6 — Not covered in this run

- Page strings (item 3's payload/store display) were not read by the orchestrator this pass — the
  owner reviewed them visually; not independently re-confirmed here. Item 3's row stays sourced to
  `M3-POC-EVIDENCE-2026-09-04.md` for that half.
- The paste-field path (§6.5 S3) was not exercised this session.
- One tier-B link, `P52FYfZZjdH1wjzg`, was opened and abandoned without a scan — no data, not
  counted toward any row above.

---

## Summary against the exit-criteria table

| Item | Result this run |
|---|---|
| 10 — server-restart duplicate-zktag survival | Re-confirmed, both documents, on the `v0.6.0` build |
| 4 — tier-B duplicate rejection, both documents | Re-confirmed post-restart, both documents |
| 3 — payload/store display | Not re-read by the orchestrator this run (page-side, owner visual only) |
| 5 — tier-A indistinguishability | Re-confirmed, 3-field diff exactly matching D77, via `compare.mjs` |
| 6 — fixed threshold, incl. S1 | Re-confirmed; S1 lock also shown to persist across an in-place version upgrade |
| 7 — hosting | Re-confirmed, `adb reverse`, `http://127.0.0.1:8787` |
| 13 — no scanner changes vs. released APK | **CLOSED.** Evidence above was gathered against the build at the `v0.6.0` release tag (`de97c82`) itself, which is what "released scanner APK" means absent a signed-release keystore (see Setup) — the item 13 gap flagged in `v1.62`/`v1.63` no longer applies because the release itself now incorporates S1–S3/D79. |

**No PII values appear anywhere above.** All quoted log lines, dialog strings, and timings are
value-free by construction — field names, verdict strings, boolean/status fields, timings, and
12-char zktag prefixes only.
