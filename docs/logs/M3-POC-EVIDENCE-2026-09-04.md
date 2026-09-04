# M3 — opening riskiest-assumption POC (Pixel 6a, 2026-09-04)

**Status**: source record for §6.3 item 10's opening POC, run against `apps/demo` at the current
branch state (scanner v0.5.0 debug build `app-regular-debug.apk` @ `c4fc43e`, sideloaded,
unmodified per item 13). Item 10 is the pass/fail gate this file exists to satisfy; it does not
itself close any other exit-criteria row.

**Rule for this file (carried from `M0-EVIDENCE.md` / `M2-D50-D53-EVIDENCE.md` /
`M2-D55-D56-EVIDENCE.md` / `M2-D58-STEP1/2/3/4-EVIDENCE.md` / `M2-FENCE-EVIDENCE.md` /
`M2-DEVICE-SESSION-2026-09-03-EVIDENCE.md`)**: no PII values, ever — field names, verdict strings,
timings, hashes/truncated transaction identifiers, and 12-char zktag prefixes only. No MRZ field,
name, date of birth, document number, raw zktag, nonce, public key, or signature appears anywhere
in this file.

---

## Setup

Pixel 6a, scanner v0.5.0 debug build (`app-regular-debug.apk`, `c4fc43e`) sideloaded, unmodified.
`apps/demo` at the current branch's working-tree state, store at `apps/demo/data/store.json`.
`adb reverse tcp:8787 tcp:8787`; server started with `SCOPE_DOMAIN=127.0.0.1 LINK_SCHEME=av
PORT=8787 DEMO_STORE_PATH=./data/store.json node server.mjs`; phone browser (Chrome) at
`http://127.0.0.1:8787`; same-device `av://` link tapped in-page for every transaction (item 7's
handoff path). Two real documents used throughout: a US passport and an NL ID card.

Two server processes were run in sequence: process 1, then a SIGTERM kill and restart with the
same command line (process 2), to exercise item 10's server-restart requirement.

---

## 1 — Process 1 (before restart)

```
apps/demo verifier listening on http://127.0.0.1:8787 (link scheme: av, bind host: 127.0.0.1)
[apps/demo] tx created transactionId=3glz9CrpbqcKX4Oy mode=B ttlMs=120000 threshold=18
[apps/demo] verdict transactionId=3glz9CrpbqcKX4Oy tier=B threshold=18 ok=true allowed=true reason=evidence-verified evidence=["sig-p256/1"] attester=bound_first_sight
[apps/demo] tx created transactionId=GQz7i7_pwVaaXzVL mode=B ttlMs=120000 threshold=18
[apps/demo] tx created transactionId=EAKjoEw-nkB0u3WC mode=B ttlMs=120000 threshold=18
[apps/demo] verdict transactionId=EAKjoEw-nkB0u3WC tier=B threshold=18 ok=true allowed=true reason=evidence-verified evidence=["sig-p256/1"] attester=bound_first_sight
```

- `3glz9CrpbqcKX4Oy`: mode B, `ok=true allowed=true reason=evidence-verified
  evidence=["sig-p256/1"] attester=bound_first_sight`; poll response `zktag_seen_before=false
  already_registered=false`; zktag prefix `e218e2cf6a6a`. Document established afterwards from the
  zktag identity as the NL ID card (owner initially reported US passport first; order corrected by
  the zktag match against `EAKjoEw-nkB0u3WC` below).
- `GQz7i7_pwVaaXzVL`: created, no verdict — abandoned, owner re-clicked before completion.
- `EAKjoEw-nkB0u3WC`: mode B, `allowed=true attester=bound_first_sight`; poll response
  `seen=false registered=false`; zktag prefix `a89f966d0f20`. Document: US passport.

Store after process 1: `nonces` 1, `attesterBindings` 2, `zktagsSeen` 2 (keys prefixed
`9:127.0.0.1:64:`).

Page (accessibility snapshot, taken by the orchestrator): status "response received" / "ALLOWED
(over threshold)" rendered below the QR, past the fold. Owner nits for the post-POC layout pass:
the link line is not responsive (page side-scrolls); the result sits below the fold so the owner
did not see it at first.

---

## 2 — Kill and restart

Server killed via `SIGTERM`, then restarted with the identical command line. Startup log line
confirmed listening; store reloaded with 2 zktags (unchanged from the process-1 end state).

---

## 3 — Process 2 (after restart)

```
apps/demo verifier listening on http://127.0.0.1:8787 (link scheme: av, bind host: 127.0.0.1)
[apps/demo] tx created transactionId=vYimSQD5IbVJZoxx mode=B ttlMs=120000 threshold=18
[apps/demo] verdict transactionId=vYimSQD5IbVJZoxx tier=B threshold=18 ok=true allowed=true reason=evidence-verified evidence=["sig-p256/1"] attester=matched
[apps/demo] tx created transactionId=smLYmV2ry3PiLlfk mode=B ttlMs=120000 threshold=18
[apps/demo] verdict transactionId=smLYmV2ry3PiLlfk tier=B threshold=18 ok=true allowed=true reason=evidence-verified evidence=["sig-p256/1"] attester=matched
[apps/demo] tx created transactionId=ghmWce24xsZ6VHyz mode=B ttlMs=120000 threshold=18
[apps/demo] verdict transactionId=ghmWce24xsZ6VHyz tier=B threshold=18 ok=true allowed=true reason=evidence-verified evidence=["sig-p256/1"] attester=matched
```

All three transactions: mode B, `allowed=true`, `attester=matched`.

- `vYimSQD5IbVJZoxx`: zktag `e218e2cf6a6a` (NL ID card), `seen=true registered=true`.
- `smLYmV2ry3PiLlfk`: zktag `a89f966d0f20` (US passport), `seen=true registered=true`.
- `ghmWce24xsZ6VHyz`: zktag `e218e2cf6a6a` (NL ID card), `seen=true registered=true`.

Store after process 2: still 2 zktags — no growth on repeat presentations.

Owner reported "both scanned both success" for the page. The page's "ALREADY REGISTERED" string
post-restart is **owner-reported only, not accessibility-snapshot-verified** — the phone had
returned to the scanner app when the orchestrator took its snapshot. Recording this honestly
rather than claiming a verification that was not obtained.

---

## Interpretation

§6.3 item 10 pass criteria met: both documents minted first-sight (process 1), both were refused
as already registered after the server process was killed and restarted (process 2, `seen=true
registered=true` for every transaction), and the handoff from the phone's own browser at
`http://127.0.0.1:8787` over `adb reverse` completed end-to-end every time — 5 verdicts, 0
failures, 1 abandoned (never-submitted) transaction. Zktags were stable across the restart for
both documents (`e218e2cf6a6a` and `a89f966d0f20` unchanged process 1 → process 2).

**Result: item 10's opening POC PASSES, device-confirmed 2026-09-04.**

---

## What this run did and did NOT establish

**Did establish:**
- Both real documents mint on first tier-B scan against the persistent store.
- Both real documents are refused as already-registered on a second scan, after the server
  process was killed and restarted — the store survives the restart correctly.
- The `av://` same-device handoff (item 7's path) completed end-to-end from the phone's own
  Chrome browser at `http://127.0.0.1:8787` via `adb reverse`, against the sideloaded debug build,
  on every attempted transaction.
- Zktags are stable across a server restart for both documents.

**Did NOT establish:**
- The plain pre-restart same-document repeat scan (item 4's duplicate case without a restart in
  between) was not exercised on-device this session — only in the node test suite
  (`apps/demo/tests/store.test.mjs`, 29/29 passing at the time of this run). This is a gap, not a
  pass, for that specific sub-case.
- The page's "ALREADY REGISTERED" string after the restart is owner-reported only; it was not
  captured in an accessibility snapshot, because the phone had returned to the scanner app by the
  time the orchestrator snapshotted. Treat the string's exact rendering as unverified pending a
  follow-up snapshot.
- Any layout/responsiveness fix for the link line or the below-the-fold result placement the owner
  flagged — carried to the post-POC layout pass, not addressed by this session.

---

---

## Session 2 — page pass (2026-09-04 afternoon)

**Setup**: `apps/demo` working tree, after the layout build (uncommitted, not staged/committed
per repo rule this session). Scanner v0.5.0 debug build (`app-regular-debug.apk`, `c4fc43e`)
sideloaded, unmodified — no scanner changes made or needed. 34/34 `apps/demo` node tests passing
at the time of this session. Server restarted on port 8787 (`adb reverse tcp:8787 tcp:8787`)
against the same `data/store.json` already holding the 2 zktags minted in Session 1.

Server log excerpt (`demo-run3.log`), verbatim:

```
apps/demo verifier listening on http://127.0.0.1:8787 (link scheme: av, bind host: 127.0.0.1)
  LAN: http://192.168.178.166:8787, http://192.168.178.220:8787, http://100.106.216.44:8787, http://172.18.0.1:8787
[apps/demo] tx created transactionId=lDniImmH784CNmfM mode=A ttlMs=120000 threshold=18
[apps/demo] verdict transactionId=lDniImmH784CNmfM tier=A threshold=18 ok=true allowed=true reason=no-evidence-required evidence=[] attester=n/a
[apps/demo] tx created transactionId=QuWBjH0Mm8ff6hKj mode=A ttlMs=120000 threshold=18
[apps/demo] verdict transactionId=QuWBjH0Mm8ff6hKj tier=A threshold=18 ok=true allowed=true reason=no-evidence-required evidence=[] attester=n/a
[apps/demo] tx created transactionId=cH3Nv44EEEqvnT2M mode=B ttlMs=120000 threshold=18
[apps/demo] verdict transactionId=cH3Nv44EEEqvnT2M tier=B threshold=18 ok=true allowed=true reason=evidence-verified evidence=["sig-p256/1"] attester=matched
```

- **Tier A x2** (NL ID card, both times): `lDniImmH784CNmfM` and `QuWBjH0Mm8ff6hKj`, both
  `ok=true allowed=true reason=no-evidence-required evidence=[]` `attester=n/a`. The orchestrator
  diffed the two `presentation` objects returned by the poll endpoint: 12 flattened fields, exactly
  3 differ — `challenge.nonce`, `challenge.issued_at`, `challenge.expires_at`. No signature field
  exists in this build (tier-A challenges are unsigned, per D20). Owner confirmed on the phone: no
  horizontal scroll, outcome visible without scrolling when the browser regained focus, no PIN
  prompt (expected for tier A — no device key involved).
- **Tier B x1** (NL ID card): `cH3Nv44EEEqvnT2M`, `ok=true allowed=true
  evidence=["sig-p256/1"] attester=matched`, zktag prefix `e218e2cf6a6a`, `zktag_seen_before=true
  already_registered=true`. Owner confirmed the page showed the "Already registered at this site"
  block. Store still 2 zktags — no growth.
- Owner also reported "second verification no conf popup" during the two tier-A scans. Meaning not
  yet clarified — possibly the scanner's outcome dialog not appearing on the second scan. Recorded
  here as an open observation, scanner-side if confirmed, not a demo issue; not investigated
  further this session.
  **Correction (2026-09-04, later same day):** the server log for these two transactions
  (`lDniImmH784CNmfM`/`QuWBjH0Mm8ff6hKj`) shows both were mode A — but a re-check of which button
  the owner actually tapped for the report above found the underlying missing-popup reports came
  from scans where the owner had tapped the bottom ("Prove you're a unique adult human," tier B)
  button, not these two tier-A transactions. A subsequent controlled run (orchestrator-driven tap
  on "Prove you're over 18" via the accessibility tree, confirmed `mode=A` in the server log) showed
  the outcome popup DID appear; these two consecutive tier-A scans recorded above also completed
  normally with no popup anomaly. Standing status: **tier-A popup — confirmed, every scan.
  Tier-B repeat-scan popup — still an unconfirmed observation, unresolved** (may not appear on a
  repeat scan; not reproduced under controlled conditions this session).

### Escalations raised, owner decision pending (recorded here as open; §6.3 items 5 and 8 text NOT
changed by this session)

1. §6.3 item 5's page-text requirement states only nonce and signature differ between two tier-A
   presentations. In this build there is no signature field, and the challenge timestamps
   (`issued_at`, `expires_at`) also differ, alongside the nonce — 3 fields, not 1. The page's
   caption states the real fields observed. Owner decision pending on whether §6.3 item 5's text
   should be corrected to match.
2. §6.3 item 8 (trust list): no package-name/cert-digest check exists in `apps/demo` or
   `chiproof`, and the OpenID4VP wire this build uses carries no such field to check against.
   Building it would be a scanner wire-contract change, which conflicts with item 13's "no scanner
   changes" requirement. Owner decision pending.

---

---

## Session 3 — on-device negatives (2026-09-04 evening)

**Setup**: same as Session 2 — scanner v0.5.0 debug build (`app-regular-debug.apk`, `c4fc43e`)
sideloaded, unmodified; `apps/demo` working tree now committed at `aec100e`/`1bb86c8`; server on
port 8787 via `adb reverse tcp:8787 tcp:8787`; US passport used throughout; page reloaded before
each case. Four cases run, all deliberate negative/edge paths rather than the happy path Sessions 1
and 2 covered.

Server log excerpt (transaction IDs truncated to 4 characters), verbatim:

```
[apps/demo] tx created npzv mode=A ttlMs=120000 threshold=18
[apps/demo] tx created lF0p mode=A ttlMs=120000 threshold=18
[apps/demo] verdict lF0p tier=A threshold=18 ok=true allowed=true reason=no-evidence-required evidence=[] attester=n/a
[apps/demo] tx created BVHa mode=A ttlMs=120000 threshold=18
```

### Case 1 — wrong details (one digit of the document number changed in the scanner)

Tier A, tap link, hold passport. Scanner dialog: owner reported "Couldn't read — check your
details". Server: tx `npzv` created, no verdict logged.

Checked against source: this is the access-establishment branch of the read-failure classifier
(`FailureTransition.classify` → `Classification.ACCESS_ESTABLISHMENT`) in
`apps/scanner/app/src/main/java/com/tananaev/passportreader/MainActivity.kt`. Two distinct strings
exist for this branch, and the owner's paraphrase matches one but not the other exactly:
- The blocking dialog itself renders `getString(R.string.error_read)` (`MainActivity.kt:1718`),
  whose resource text in `apps/scanner/app/src/main/res/values/strings.xml` is **"Couldn't read —
  check your details and try again."** — the owner's paraphrase drops the trailing "and try again."
- A second, shorter copy of the same string, **"Couldn't read — check your details"** (no res-file
  entry — a literal at `MainActivity.kt:1730`), is what's written into the `ReportLog.DisclosureSummary`
  shown in the app's own Log tab, not the dialog. The owner's report exactly matches this second
  string, not the dialog's `error_read` resource — most likely what was actually read/remembered
  came from the Log tab entry rather than the dialog text itself, but both strings correspond to the
  same classification and the same PASS.

**PASS** — access-establishment failure classified correctly; nothing reached the verifier (no `tx`
line at all for this case, consistent with the scanner never starting a handoff on a read failure
before lock).

### Case 2 — card lifted after ~1 s

Tier A. Scanner dialog: owner reported "Couldn't read — keep the card at the top of your phone"
(transient classification, not check-details — the `FailureTransition` ordering fix from M2 holds).

Checked against source: this is `Classification.TRANSIENT_CHIP_COMMUNICATION`, whose dialog text is
`TRANSIENT_READ_FAILURE_MESSAGE`, a private companion-object constant in `MainActivity.kt:3132` (not
a string resource — no `res/values` entry) — **"Couldn't read — keep the card at the top of your
phone."** — an exact match to the owner's report (modulo the trailing period).

Owner then re-tapped the SAME link and completed the scan. Server: tx `lF0p` created, then `verdict
lF0p tier=A threshold=18 ok=true allowed=true reason=no-evidence-required evidence=[] attester=n/a`.

**PASS** — retry on the same (still-live) transaction works; the transient dialog did not force the
user to re-check details or restart from a fresh link.

### Case 3 — stale link (re-tapping the link of an already-completed transaction, no new button tap)

Scanner dialog: owner reported "Verification session expired — reopen the link from the site".
Server: no new `tx created` line for this re-tap.

Checked against source: this is `SESSION_EXPIRED_MESSAGE`, a private companion-object constant in
`MainActivity.kt:3127` (again no `res/values` entry) — **"Verification session expired — reopen
the link from the site."** — an exact match. This is the up-front check in `lockModeAndArm`, one of
the two call sites sharing this single constant (the other is the belt-and-suspenders re-check in
`continueAfterRead`), per the constant's own comment (`MainActivity.kt:3121-3127`).

Server-side: the `GET /wallet/request.jwt/{requestId}` handler (`apps/demo/server.mjs:564-578`,
which a link re-tap fetches) has no logging call in it at all — confirmed by reading the handler —
so a stale-link re-fetch produces **no server log line either way** (whether the fetch 404s or
succeeds). This case is therefore evidenced by the *absence* of a new `tx created` line plus the
scanner's own dialog, not by any positive server-side signal — there is nothing for the server to
log here even in principle.

**PASS** — resolves the earlier Session 2 "second scan no popup" observation (see the correction
block above, "Standing status: tier-A popup — confirmed, every scan"): a used/stale link produces a
clear dialog, not silence.

### Case 4 — expired request (button tapped, link opened after >3 min, `ttlMs=120000`)

Scanner dialog: owner reported "Verification session expired — reopen the link from the site" — the
same `SESSION_EXPIRED_MESSAGE` constant as Case 3, confirmed by source (`MainActivity.kt:3127`).
Server: tx `BVHa` created, no verdict logged.

**PASS** — expired request refused app-side (the up-front `lockModeAndArm` check, ttl already past
`expires_at`) before any `direct_post` was attempted; the transaction record exists (the link was
fetched and a tx object exists server-side) but nothing after it.

### Not run on device

Second device presenting a different key for an already-bound zktag (only one physical device
available this session) — covered instead by the node test `apps/demo/tests/tier-b.test.mjs`: "D38:
a DIFFERENT unpinned device key presented for an ALREADY-BOUND zktag => attester_key_mismatch".
Not a device-confirmed case; recorded here as a gap, not a pass, for that specific sub-case.

### Session 3 summary

| Case | Dialog (as reported) | Exact source string | Resource / constant | Server evidence | Result |
|---|---|---|---|---|---|
| 1 wrong details | "Couldn't read — check your details" | dialog: "...and try again."; log entry: exact match | `R.string.error_read` (dialog) / literal at `MainActivity.kt:1730` (log entry) | tx `npzv`, no verdict | PASS |
| 2 card lifted | "Couldn't read — keep the card at the top of your phone" | exact match | `TRANSIENT_READ_FAILURE_MESSAGE`, `MainActivity.kt:3132` | tx `lF0p`, verdict `allowed=true` on retry | PASS |
| 3 stale link | "Verification session expired — reopen the link from the site" | exact match | `SESSION_EXPIRED_MESSAGE`, `MainActivity.kt:3127` | no new tx (handler is unlogged either way) | PASS |
| 4 expired request | "Verification session expired — reopen the link from the site" | exact match | `SESSION_EXPIRED_MESSAGE`, `MainActivity.kt:3127` | tx `BVHa`, no verdict | PASS |

All four on-device cases PASS. One sub-case (Tier B, second device / different key on an
already-bound zktag) remains node-test-only, not device-confirmed.

---

**No PII values appear anywhere above, in any session.** All quoted log lines, transaction
identifiers, and store states are value-free by construction — stage names, boolean/status fields,
truncated transaction IDs, 12-char zktag prefixes, and timings only — checked against this file's
own rule and the project standard it inherits from `M0-EVIDENCE.md` through
`M2-DEVICE-SESSION-2026-09-03-EVIDENCE.md` before inclusion.
