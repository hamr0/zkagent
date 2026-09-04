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

**No PII values appear anywhere above, in either session.** All quoted log lines, transaction
identifiers, and store states are value-free by construction — stage names, boolean/status fields,
truncated transaction IDs, 12-char zktag prefixes, and timings only — checked against this file's
own rule and the project standard it inherits from `M0-EVIDENCE.md` through
`M2-DEVICE-SESSION-2026-09-03-EVIDENCE.md` before inclusion.
