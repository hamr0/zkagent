# M2 — device evidence for D50–D53 (Pixel 6a, 2026-09-01)

**Status**: source record only, written after the fact from two logs already on disk — the
verifier's value-free transaction log and a device logcat capture. This file is the evidence
`docs/product/zkagent-prd.md` §10 D50, D51, D52, and D53 cite; those rows already narrate the
defects and the owner's decisions in full — this file exists so the underlying run data survives
independently of the scratchpad it was produced in, and so a reader can check the PRD's claims
against the source rather than trusting the prose alone.

**Rule for this file (carried from `M0-EVIDENCE.md` / `M2-SESSION-POC.md`)**: no PII values,
ever — field names, verdict strings, timings, hashes/truncated identifiers, and exception text
only. Both source logs are value-free by construction (no MRZ, name, date of birth, document
number, raw zktag, nonce, public key, or signature appears in either); every line quoted below
was checked against that rule before inclusion.

**Sources**: a verifier transaction log (`[m2-handoff]`-prefixed lines, 16 lines, spike verifier
at `http://127.0.0.1:8787`) and a device logcat capture (`MainActivity`/`DeviceKey`/`RequestTrust`
tags, 450 lines, 2026-09-01 21:59–23:08) from `apps/scanner/` running against that verifier on a
Pixel 6a. Neither source file is committed to this repo; both were produced in a scratchpad and
are recorded here by content, not by path.

---

## SETUP

| Component | Value |
|---|---|
| Device | Pixel 6a, same device as every prior M0–M2 run |
| App under test | `apps/scanner/` (the M2 reference scanner, not a spike) |
| Verifier | the `m2-handoff` spike verifier, `http://127.0.0.1:8787`, `av://` same-device handoff |
| Documents | two real documents, re-scanned across the session; distinguished below only by the
`access_protocol`/`chip_auth` pattern each produced (PACE + chip-auth-passed vs. BAC +
chip-auth-absent) — no document-identifying field appears in either log, so which physical
document is which is an inference from that pattern, not a directly observed fact. That pattern
matches the profile recorded for this project's two working documents (M0, 2026-08-29): one
supports PACE/chip authentication, one does not. |
| Window | 21:59:51 – 23:07:50, single continuous session, four app process restarts (PIDs 5314,
5618, 7434/7783/7907, 8207 — an `av://` intent redelivery/relaunch pattern, not four separate
test setups) |

---

## RESULT — 8 transactions created, 7 delivered and accepted, 1 not

The verifier log records 8 `tx created` lines and 7 `verdict` lines — every verdict is
`ok=true allowed=true reason=evidence-verified evidence=["sig-p256/1"]`. The 8th transaction
(`RfYVrh0sPTnMMkby`) has no matching verdict line: it was created and never received a
presentation.

Cross-referencing the verdict order against the logcat's 7 `verdict: PASS (minted)` /
`direct_post` acceptances (same count, same order) ties each verifier verdict to a device-side
`evidence_type: sig-p256/1 key_id=...` line:

| # | transactionId (truncated as logged) | attester | key_id | access_protocol | chip_auth |
|---|---|---|---|---|---|
| 1 | `pzupFEi5jvYKOZfm` | `bound_first_sight` | `9aa88722553a42ec` | PACE | passed |
| 2 | `lHj8Sd0ZljkTDd-P` | `bound_first_sight` | `a0b15dc66c4245f8` | BAC | absent |
| — | `RfYVrh0sPTnMMkby` | *(no verdict — created, never presented)* | — | — | — |
| 3 | `dql5oZgvI08tCT2i` | `matched` | `9aa88722553a42ec` | PACE | passed |
| 4 | `ukHotz0V9wV50Ch-` | `matched` | `9aa88722553a42ec` | PACE | passed |
| 5 | `5nrnm_xZeMX2UzgB` | `matched` | `a0b15dc66c4245f8` | BAC | absent |
| 6 | `FMuP9oSS0wSbtkF3` | `matched` | `a0b15dc66c4245f8` | BAC | absent |
| 7 | `T73M06S3pc1YBnjq` | `matched` | `a0b15dc66c4245f8` | BAC | absent |

Counted directly from the verifier log: **2** `attester=bound_first_sight`, **5**
`attester=matched` (7 verdicts total, matching 8 tx created minus the 1 unpresented). Each of the
2 `bound_first_sight` verdicts is the *first* appearance of its key_id in the session; every
later appearance of the same key_id is `matched` — 2 repeats of key `9aa88722553a42ec`, 3 repeats
of key `a0b15dc66c4245f8`, summing to the 5 `matched` verdicts. The session's last **5**
consecutive verdicts (rows 3–7) are all `matched`.

### First-sight pair: two keys, one per document, same origin (D39)

Transactions 1 and 2 are the session's first two mints, both against the same verifier origin
(`http://127.0.0.1:8787`), and they produced **different** key_ids — `9aa88722553a42ec` (PACE,
chip-auth passed) then `a0b15dc66c4245f8` (BAC, chip-auth absent). This is the first hardware
confirmation of D39's per-(origin, zktag) key isolation: two different zktags at the same origin
get two different attester keys, not one shared key. By the chip_auth pattern above, the
PACE/chip-auth-passed document was scanned first in this session, then the BAC/chip-auth-absent
document — the reverse order from an earlier informal summary of this run, which is corrected
here against the source logs rather than repeated.

Contrast with the 2026-09-01 11:43 run recorded previously (§10 D39's own text): the same
document pairing at the same origin there produced one *shared* `key_id=c303cf3f731b5307` under
D38's earlier per-origin-only scheme — the leak that forced D39. This run's two distinct key_ids
are the first on-hardware evidence that D39's fix holds.

### Repeat presentations: `matched` holding across five transactions (D38)

Rows 3–7 are all `attester=matched` — a returning document, presenting the same key it was
first bound to, recognized as the same key every time it returned in this session (2 repeats for
one document, 3 for the other). D38's first-sight binding held repeatedly, not once.

### The one unpresented transaction (D51's third failure bucket)

`RfYVrh0sPTnMMkby` was created between verdicts 2 and 3 and never received a presentation. In the
same window, the logcat shows a chip read that failed mid-way through:

```
09-01 22:37:13.851 ... E MainActivity: M2 READ FAILED
09-01 22:37:13.851 ... E MainActivity: net.sf.scuba.smartcards.CardServiceException: Tag was lost.
    at net.sf.scuba.smartcards.IsoDepCardService.transmit(...)
    at org.jmrtd.protocol.ReadBinaryAPDUSender.sendSelectApplet(...)
09-01 22:37:13.910 ... I MainActivity: verdict: FAIL
09-01 22:37:13.910 ... I MainActivity: failure: CardServiceException: Tag was lost.
```

followed ~18 s later by a retry that failed the same way while reading a DG file, surfacing as a
wrapped `IOException`:

```
09-01 22:37:31.364 ... E MainActivity: java.io.IOException: Unexpected exception
    at net.sf.scuba.smartcards.CardFileInputStream.read(...)
    ...
Caused by: net.sf.scuba.smartcards.CardServiceException: Read binary failed on file 11d
    at org.jmrtd.DefaultFileSystem.readBinary(...)
Caused by: net.sf.scuba.smartcards.CardServiceException: Tag was lost.
    at net.sf.scuba.smartcards.IsoDepCardService.transmit(...)
09-01 22:37:31.456 ... I MainActivity: verdict: FAIL
09-01 22:37:31.456 ... I MainActivity: failure: IOException: Unexpected exception
```

Both failures are the same root cause — the document physically moved off the NFC field mid-read
— reported first from `sendSelectApplet` (session re-establishment) and then, on retry, from a
DG file read inside `DefaultFileSystem.readBinary`. Neither read reaches minting, so neither
produces a `direct_post`; this is the transaction that was created (from the handoff already in
progress) and never presented. This is the evidence behind D51's third failure-transition bucket
(transient chip-communication loss, distinct from an access-establishment failure).

### Two chip-access failures, and the corrected reading of them (D50)

Also present, earlier in the session, immediately after the first mint:

```
09-01 22:01:16.852 ... E MainActivity: org.jmrtd.AccessDeniedException: Mutual authentication
  failed: expected length: 40 + 2, actual length: 2 (SW = 0x6982: SECURITY STATUS NOT SATISFIED)
    at org.jmrtd.protocol.BACProtocol.doBAC(BACProtocol.java:90)
09-01 22:01:37.817 ... E MainActivity: org.jmrtd.AccessDeniedException: Mutual authentication
  failed: expected length: 40 + 2, actual length: 2 (SW = 0x6982: SECURITY STATUS NOT SATISFIED)
```

**Correction, carried from the PRD's own D50 row and restated here against the source:** these
two failures were initially read as evidence of a consumed-session defect — a mint that left the
handoff pending on an already-spent nonce, inviting a doomed retry. That causal reading is
**wrong**. `MainActivity.kt:1033-1034` (pre-existing, not written this session) already clears
`pendingHandoff`/`verifiedRequest` on every delivery outcome, and no new `pendingHandoff captured
from av:// intent` line appears in the logcat between the first mint and these two failures — so
there was no pending handoff for them to have consumed. The logs are consistent with two genuine
chip-access failures (SW=0x6982) after the handoff had already been cleared normally; the dialog
the app showed for them was accurate, and the app's state was not wrong. **The evidence stands;
the first interpretation of it did not.** What the run does establish as a real, standing gap:
nothing previously checked a handoff session's expiry, so a session could in principle age out
while still formally pending (clearing happens only on definitive completion, never on elapsed
time alone) — a genuinely missing check, not the bug originally inferred from these two lines.

---

## Device-key and evidence-plug evidence (F2 algorithm agility)

Every successful mint's report carries the same device-key and evidence-plug lines, e.g.:

```
device_key: alg=EC curve=P-256 security_level=STRONGBOX origin=GENERATED user_auth_required=true auth_validity=0s
evidence_plug: device_preference=sig-ed25519/1 used=sig-p256/1 reason=not selected by DeviceKey's preference order on this device — see device_key_tradeoff
evidence_required: any-of[sig-ed25519/1,sig-p256/1]
device_key_algorithm: P256_HARDWARE (STRONGBOX, sig_alg=SHA256withECDSA)
```

This is F2's algorithm-agility path taking the P-256 branch on this hardware, consistent with
every prior finding that Ed25519 is unavailable via `AndroidKeyStore` on this device
(`M2-SESSION-POC.md` F2) — this run adds no new information about *why*, only that the `any-of`
evidence-slot mechanism (D24) correctly falls through to `sig-p256/1` on every mint, with the
device preference (`sig-ed25519/1`) and the actually-used plug (`sig-p256/1`) both logged
explicitly rather than silently substituted.

---

## Code-inspection finding: the passing verdicts above are plumbing evidence, not age evidence

Not a device observation — found by reading `apps/scanner/` and `packages/chiproof/` source
during this session, recorded here because it directly qualifies how to read every `allowed=true`
result above.

- `apps/scanner/.../MainActivity.kt` (currently lines 1415–1416, in a file under active edit —
  cite by content, not by a line number expected to hold) sets:
  ```kotlin
  val threshold = 18
  val claim = mapOf("over_threshold" to true, "threshold" to threshold)
  ```
  — a hardcoded threshold and an unconditionally-`true` `over_threshold` claim. No comparison
  against the chip's date of birth exists anywhere in the scan path; `over_threshold` is asserted,
  never computed.
- The threshold is already carried, signed, and nonce-bound in the challenge object the scanner
  already reads: `zkagent.challenge.threshold` (`packages/chiproof/src/challenge.js:73,76,152-175`
  — part of the nonce's covered fields, so an edited threshold is `nonce_forged`, not silently
  accepted).
- `chiproof` already enforces D11 fully at verification time (`packages/chiproof/src/index.js:232-236`):
  ```js
  if (claim.threshold !== challenge.threshold || claim.threshold !== settled.threshold) {
    return realNo('threshold_mismatch');
  }
  if (claim.over_threshold !== true) return realNo('under_threshold');
  ```

**Therefore**: every `allowed=true` verdict recorded in this file passed only because the
scanner's hardcoded `18` and chiproof's `createVerifier` default threshold happen to agree — two
independently hardcoded constants agreeing, not a real age comparison. This is Q35 (read the
request-carried threshold instead of hardcoding it — a one-line scanner fix, verifier-side
enforcement already exists) and Q36 (compute a real DOB-vs-threshold answer — open design work,
nothing chosen) restated against this run's own data. **The `allowed=true` results in the RESULT
section above are evidence about the handoff/key/attester-binding plumbing (D38/D39/F2). They are
NOT evidence that age verification itself works, and must not be read as such.**

---

## What this run did and did NOT establish

**Did establish:**
- D39's per-(origin, zktag) key isolation, confirmed on hardware for the first time: two
  documents at one origin got two different attester keys (transactions 1–2).
- D38's first-sight attester binding, confirmed holding repeatedly, not once: five consecutive
  `matched` verdicts across two documents (transactions 3–7).
- F2's algorithm-agility fallback (`sig-ed25519/1` preferred, `sig-p256/1` used) firing correctly
  and logging both the preference and the actual plug on every mint.
- A transient chip-communication loss (tag moved mid-read) produces a created-but-unpresented
  transaction server-side and a clean device-local `FAIL`, not a hang or a mismatched app/verifier
  state — the basis for D51's third failure-transition bucket.
- That the two `SW=0x6982` chip-access failures earlier in the session were NOT caused by a
  consumed-session defect — code inspection of `MainActivity.kt:1033-1034` and the absence of any
  intervening `pendingHandoff` capture in the logcat both rule that reading out.

**Did NOT establish:**
- Anything about age verification itself. Every `allowed=true` result in this file reflects two
  hardcoded `18`s agreeing (see the code-inspection finding above), not a chip-DOB comparison.
- Which physical document is which — the PACE/chip-auth-passed vs. BAC/chip-auth-absent pattern
  is an inference from a previously-documented profile, not a field present in either log.
- Anything about a second device — this run, like every prior M2 run, used the one Pixel 6a.
- Precise transaction-to-failure correspondence beyond what the counts and ordering support — the
  verifier log carries no timestamps of its own; its ordering, cross-referenced against the
  logcat's chronological order and matching event counts, is the basis for every pairing above.

---

## PENDING

- Q35 — scanner reads `zkagent.challenge.threshold` instead of hardcoding `18`; scoped as a
  one-line fix, not attempted in this run (out of scope — `apps/scanner/` was not modified while
  writing this file).
- Q36 — computing a real DOB-vs-threshold answer; open design work, nothing chosen.
- Dialog wording for D50's session-expiry refusal and D51's transient-failure retry are not yet
  owner-approved (per §10) — unrelated to this file's evidence, noted for completeness.
- A second device remains untested for all of D38/D39/F2's findings above, same standing gap as
  `M2-SESSION-POC.md`.

---

**No PII values appear anywhere above.** Both source logs are value-free by construction — field
names, verdict strings, timings, truncated identifiers, hashes, and exception text only — and
every quoted line was checked against that rule before inclusion, per this file's own rule and
the project standard it inherits from `M0-EVIDENCE.md` / `M2-SESSION-POC.md`.
