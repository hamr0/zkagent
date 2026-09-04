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

**No PII values appear anywhere above.** All quoted log lines, transaction identifiers, and store
states are value-free by construction — stage names, boolean/status fields, truncated transaction
IDs, 12-char zktag prefixes, and timings only — checked against this file's own rule and the
project standard it inherits from `M0-EVIDENCE.md` through `M2-DEVICE-SESSION-2026-09-03-EVIDENCE.md`
before inclusion.
