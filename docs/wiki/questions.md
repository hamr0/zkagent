---
type: reference
title: zkagent — open questions Q1–Q48
status: stable
sources: [docs/archive/zkagent-prd.md]
---

# zkagent — open questions Q1–Q48

Deduped index of PRD §11 (Open questions). Every Qn from Q1–Q48 is listed in order;
narrative status-update chains, commit/test-count histories, and line anchors from
the source are intentionally not reproduced here — see the citation for full detail.

Four numbers (Q3, Q4, Q10) do not exist anywhere in `zkagent-prd.md` — not just outside
the cited range — and are recorded as such rather than silently dropped. Four more
(Q2, Q5, Q6, Q9) are closed, but their one-line resolutions sit at `zkagent-prd.md:1800`,
two lines above the instructed 1802–2195 range, so no in-range citation is given for them;
this is flagged in the report back to the caller.

- **Q1** — Whether attestation-chain verification (CBOR + X.509 parsing of untrusted
  input) justifies an exception to the project's zero-runtime-deps tradition. Status:
  CLOSED — folded into Q14 as "the former Q1"; one vetted, dependency-light library
  is the accepted exception. (zkagent-prd.md:1809-1809)
- **Q2** — Whether Apple's NFC tag-reading entitlement matters for M0. Status: CLOSED —
  moot for M0 (Android-first, D2). No in-range citation (resolution text is at
  zkagent-prd.md:1800, outside the assigned 1802-2195 excerpt).
- **Q3** — No entry under this number exists anywhere in `zkagent-prd.md`.
- **Q4** — No entry under this number exists anywhere in `zkagent-prd.md`.
- **Q5** — Whether Android or iOS is the primary platform. Status: CLOSED by D2 —
  Android is primary. No in-range citation (resolution text is at zkagent-prd.md:1800,
  outside the assigned 1802-2195 excerpt).
- **Q6** — Whether iOS support is in scope for this project. Status: CLOSED by D2 —
  iOS deferred. No in-range citation (resolution text is at zkagent-prd.md:1800,
  outside the assigned 1802-2195 excerpt).
- **Q7 (M2)** — Whether exposing a client's device-assurance tier (e.g. StrongBox)
  conflicts with FR6's anti-fingerprinting uniformity requirement. Status: OPEN —
  decide between accepting a small fingerprinting cost or enforcing one uniform
  hardware bar. (zkagent-prd.md:1804-1804)
- **Q8 (M4, rung 2)** — RFC 9421 mapping details for delegation certs: which header
  carries the cert, inline-vs-URL delivery, and which covered components are
  mandatory in the signature base. Status: OPEN — URL-reference already rejected for
  v1; decide against a live off-the-shelf verifier. (zkagent-prd.md:1805-1805)
- **Q9** — How the phone→agent delegation cert is physically transported. Status:
  CLOSED — ships all three paths (QR, LAN POST, user-moved file); no zkagent-run
  server in any path. No in-range citation (resolution text is at zkagent-prd.md:1800,
  outside the assigned 1802-2195 excerpt).
- **Q10** — No entry under this number exists anywhere in `zkagent-prd.md`.
- **Q11 (M3, mode B only)** — Whether an adopter-chosen age threshold enables
  binary-search probing of a holder's exact date of birth. Status: OPEN — D13
  shrinks the risk in mode A; decide before M3 whether a fixed threshold set is
  still needed in mode A vs. mode B only. (zkagent-prd.md:1806-1806)
- **Q12 (M0/M3)** — Which ICAO documents (beyond passports) are actually readable
  by the JMRTD path used. Status: OPEN, empirical — do not state coverage numbers
  in any pitch until M0 reports per-country results. (zkagent-prd.md:1807-1807)
- **Q13 (M3, strategic)** — Who adopts zkagent first — a verifier or a client
  builder. Status: OPEN — log the first-adopter path as evidence, not assertion.
  (zkagent-prd.md:1808-1808)
- **Q14 (M1, then M2)** — Whether the attestation root should be Play Integrity,
  hardware key attestation, or both, plus keybox-extraction, vendor-quality, and
  revocation risks. Status: OPEN (leaning "both") — M1 POC found stdlib parsing
  feasible; sub-item (d), the Pixel 6a's expired root-cert policy, is unresolved.
  (zkagent-prd.md:1809-1809)
- **Q15 (M1b)** — Whether device attestation defeats the mode-A unlinkability claim.
  Status: CLOSED by D26 on M1b evidence — every salted field is fresh per
  presentation; only the circuit-class bucket (`vk_sha256`) is cross-site-stable,
  and is disclosed by owner decision. Evidence: `docs/logs/M1B-EVIDENCE.md` §4-§5.
  (zkagent-prd.md:1810-1810)
- **Q16 (M2)** — Whether mode-A should re-scan the document on every presentation or
  cache the verified claim under the D10 ceiling. Status: OPEN — decide on real UX
  evidence from M2. (zkagent-prd.md:1811-1811)
- **Q17 (before any age pitch)** — Whether a mode-A presentation can legally satisfy
  an age-verification duty in a named jurisdiction. Status: OPEN — project posture
  is demonstration, not certification; must be checked against a named jurisdiction
  before the framing shifts to a pitch. (zkagent-prd.md:1812-1812)
- **Q18** — Whether chip cloning breaks mode B's uniqueness/blocking guarantees.
  Status: CLOSED 2026-08-31 — M0 Finding 9 measured AA/CA support per document;
  mode B accepts non-chip-auth documents and reports `chip_auth` per D21/D29;
  clone-replay is an accepted, disclosed limitation. Mode A is unaffected.
  (zkagent-prd.md:1813-1813)
- **Q19 (parked, not designed)** — Whether freshness should be a requester-stated
  range (a floor), not just an operator ceiling (D10). Status: PARKED — do not
  design before rung 1 ships; in tension with D10's one-bit-only freshness rule.
  (zkagent-prd.md:1815-1822)
- **Q20 (resolved in principle by D20; mechanics at M1)** — How operator identity
  works in a borrowable core with no registry. Status: ANSWERED-not-fully-decided —
  the issuer's signing key is its identity; key format, challenge encoding, and
  expiry precedence still need to be fixed in code at M1. (zkagent-prd.md:1824-1829)
- **Q21 (deferred, rung-2-shaped)** — How an authority admits a requester to tier C
  (live signing service vs. delegated certificate). Status: DEFERRED — the
  delegation option is rung-2 machinery, forbidden to design before rung 1 ships;
  v1 ships only the direct case. (zkagent-prd.md:1831-1837)
- **Q22 (before tier C is built)** — What the tier-C verb vocabulary of identifying
  predicates should be. Status: OPEN — no similarity scores ever allowed; decide
  against the first real tier-C adopter, not speculatively. (zkagent-prd.md:1839-1844)
- **Q23 (resolved by D23)** — Whether tier A's attestation requirement is compatible
  with the "nothing stable across sites" unlinkability bar. Status: CLOSED by D23 —
  Play Integrity measured with no device-unique field; owner's call could still be
  reversed toward option (5), ZK over the passport SOD, per the same evidence.
  Evidence: `docs/logs/M1-Q23-EVIDENCE.md`. (zkagent-prd.md:1846-1870)
- **Q24 (open, v1 accepted)** — De-Googled devices have no D22-compliant attestation
  route. Status: OPEN, accepted for v1 by D23 — bare mode is available to them;
  revisit with the ZK track or a split verifier. (zkagent-prd.md:1872-1876)
- **Q25 (closed by D27)** — Whether the reference app itself should carry Play
  Integrity evidence. Status: CLOSED — the reference app ships bare or with
  `zk-passport/1`, never a self-signed receipt (would violate NO-GO #3).
  (zkagent-prd.md:1878-1880)
- **Q26 (open, gated behind Track Z)** — Whether a ZK passport circuit can expose
  both a stable scope-nullifier and a fresh challenge nonce as public inputs.
  Status: OPEN — no option chosen among upstream PR, a different circuit family, or
  unproven per-request nullifiers. (zkagent-prd.md:1882-1887)
- **Q27 (closed by D28)** — Whether `current_date` granularity in the age circuit
  should be coarsened. Status: CLOSED — coarsen to day granularity, midnight-UTC
  (D28); `max_scan_age` floors at 1 day. (zkagent-prd.md:1889-1895)
- **Q28 (closed by D36)** — Whether a device may voluntarily offer a weaker evidence
  plug than its strongest available one. Status: CLOSED — no such choice exists; the
  device always tries its strongest-preference plug first and falls through only on
  failure (D36). (zkagent-prd.md:1897-1905)
- **Q29 (closed by D37)** — How `trustedChallengeIssuers` request-signer keys are
  provisioned/rotated on-device. Status: CLOSED — the trust anchor is the requesting
  site's HTTPS origin, fetched from a well-known path, not a pinned list (D37); M2
  keeps one build-time dev key until a real TLS origin exists. (zkagent-prd.md:1906-1917)
- **Q30** — Whether the zktag's domain-scope derivation and the origin-consistency
  security check should use the same granularity (host vs. full origin). Status:
  CLOSED 2026-09-01 — host-only signing scope, full-origin consistency check (D42);
  the host-vs-registrable-domain divergence is flagged as a production note, not a
  blocker. (zkagent-prd.md:1919-1932)
- **Q31 (open, orchestrator-flagged)** — How a user re-enrolls after key loss under
  first-sight (TOFU) attester-key binding. Status: OPEN — options listed (manual
  unbinding, binding TTL, recovery secret, or permanent refusal), none decided;
  genuine tension with the security property TOFU exists to provide.
  (zkagent-prd.md:1933-1944)
- **Q32 (closed by D47)** — Exact shape of the log view's plain-language disclosure
  summary, and the no-site label wording. Status: CLOSED — owner chose a four-field
  block (`Result`/`Sent`/`Shared`/`Identity`) plus a subordinate technical line
  (D47), and confirmed "Local scan (no site)" as the no-site label.
  (zkagent-prd.md:1945-1956)
- **Q33 (superseded, split into Q35/Q36)** — Original claim: the scanner asserts
  `over_threshold: true` unconditionally and never implements D11's threshold
  comparison. Status: SUPERSEDED 2026-09-01 — the premise was incomplete (the
  request-carried threshold and D11 enforcement already exist in code); split into
  Q35 (scanner-side one-line fix) and Q36 (real DOB comparison, genuine design
  work). (zkagent-prd.md:1958-1983)
- **Q34 (open, owner-raised)** — Whether/how zkagent should support a general claim
  vocabulary beyond age (e.g. document-expiry buckets). Status: OPEN — D49 settled
  only the rendering SHAPE (a list of predicate→boolean pairs); which claims exist,
  their per-tier limits, and cumulative-disclosure cost remain undecided and need
  their own design pass and riskiest-assumption POC. (zkagent-prd.md:2034-2060)
- **Q35 (descendant of Q33 part a)** — The scanner must read the already-signed,
  nonce-bound `zkagent.challenge.threshold` field instead of its hardcoded `18`.
  Status: FIXED-IN-this commit — `RequestTrust.thresholdOf` (pure extractor,
  mirrors `tierOf`/`expiresAtOf`) reads the verified request's
  `zkagent.challenge.threshold`; absent, non-integer, or non-positive values fail
  loudly (refuse the mint via the existing `ReportLog`/blocking-dialog failure
  path, mirroring item 13's tier discipline) with no default of `18` anywhere.
  Decision made without prior owner sign-off, flagged for review: the refusal is
  checked at MINT time (`MainActivity.mintAndMaybeHandoff`, where `nonce` is
  already parsed from the same `zkagent.challenge` object), not at lock time
  alongside tier — threshold plays no part in deriving the presentation mode the
  way tier does, so nothing needs it earlier. Closes D48's threshold-from-request
  MUST. `over_threshold` remains unconditionally `true` at the time of this fix
  (Q36, closed separately). (zkagent-prd.md:1985-2017)
- **Q36 (closed by D66, descendant of Q33 part b)** — Compute a real
  DOB-vs-threshold answer instead of asserting `true` unconditionally. Status:
  FIXED-IN-7daeba4 (cherry-picked; D66) — owner ruled D66 (2026-09-03): the
  scanner computes the real over/under answer in-app, in a pure class, at mint
  time, from the DG1 date of birth against the D28-coarsened `current_date`
  with the Q35-sourced threshold; an under-threshold holder still gets an
  honest `over_threshold:false` mint and handoff, and a blocking dialog states
  the threshold was not met. Date of birth never enters the report, log, or
  any screen. Implementation: a new pure `AgeCheck` object computes the real
  answer from the chip's own DG1 date of birth (`MRZInfo.getDateOfBirth()`)
  against D28's client-side-coarsened current date, using the standard ICAO
  9303 MRZ sliding-century-window rule and a "on-or-after the calendar
  birthday, 29-Feb treated as 1 March in a non-leap year" birthday rule (both
  stated as owner-overturnable in `AgeCheck`'s class doc). An unparsable DOB
  refuses the mint the same way Q35's absent-threshold branch does. The
  under-threshold path still mints and hands off as before; a new
  `MintOutcome` object tells an honest, expected `allowed:false` refusal
  (this device already claimed `over_threshold:false`) apart from any other
  kind of verifier refusal — though the current verifier spike's
  `direct_post` returns only `accepted:true`, so this honest-under verdict
  split is not yet exercisable end-to-end; device verification pending.
  (zkagent-prd.md:2019-2032; decisions.md D66, owner 2026-09-03)
- **Q37 (closed, resolved by implementation)** — Whether "consumed" vs. "expired"
  handoff sessions can be distinguished device-side without a verifier round-trip.
  Status: CLOSED 2026-09-01 — challenge expiry is reachable from the verified
  request object directly; "consumed" needs no detection since a used session is
  already cleared from app state. (zkagent-prd.md:2062-2077)
- **Q38 (closed by D64)** — Whether the in-app log needs to survive app close
  (process death). Status: CLOSED 2026-09-02 — Option A (accept and disclose):
  a mid-`direct_post` recreation still delivers the proof to the site while the
  phone shows nothing; zero code, D44's in-memory-only log stands unamended.
  Option B (a tiny on-disk "sent, awaiting result" marker, host + timestamp only)
  is deferred to the next module's list, not designed here. Owner: "option A."
  Evidence: `docs/logs/M2-FENCE-EVIDENCE.md`, `.claude/remember/findings.md` #16.
  (owner, 2026-09-02)
- **Q39 (opened 2026-09-02)** — Whether an incoming handoff intent should switch
  the visible tab. Status: APPROVED into §6.2 item 17 (D67) — distinct from the
  already-rejected D55 (auto-switch on read completion); the tab-state ownership
  seam (`.claude/remember/findings.md` #1) that previously blocked this is closed.
  **BUILT-IN-`ee45300`, device verification pending.** `PaneState.
  onIncomingHandoffIntent(admitted: Boolean)` is a new writer on the SAME
  owner (not a second owner of the tab index); `MainActivity.
  handleIncomingIntent`'s already-admitted `av://` branch calls it before
  `beginHandoffVerification`. Unit-tested (`PaneStateTest`): admitted
  switches Log -> Scan, is a no-op already on Scan, and — the MUST NOT half
  — a refused (`HandoffAdmission`) intent leaves the tab untouched.
  (zkagent-prd.md:2099-2111)
- **Q40 (opened 2026-09-02)** — The disabled Lock button after an
  access-establishment failure reads as "stuck"; owner wants copy closer to
  "Tap and scan." Status: CLOSED (owner sign-off 2026-09-03) — the ownership
  precondition (`.claude/remember/findings.md` #9) was already closed and the
  "Tap and scan" label already shipped; the wording itself is now signed off.
  (zkagent-prd.md:2113-2132)
- **Q41 (opened 2026-09-02, EXPLOITABLE, consequence HIGH, deferred under D57)** —
  The unguarded `av://` intent-handling path lets any on-device app hijack a
  session mid-read. Status: MITIGATED, not closed — `HandoffAdmission` gates the
  path (commit `730ef09`); remains OPEN for the full ownership fix. See
  `.claude/remember/findings.md` #10. (zkagent-prd.md:2134-2134)
- **Q42 (opened 2026-09-02, consequence HIGH, deferred under D57)** — The
  biometric-authorization prompt shows no origin/site/tier, so the user cannot
  tell whose request they authorized. Status: MITIGATED, not closed — the prompt
  now renders the site (commit `730ef09`); remains OPEN for the full ownership
  fix. See `.claude/remember/findings.md` #11. (zkagent-prd.md:2136-2136)
- **Q43 (opened 2026-09-02 — UI/UX ENHANCEMENT, not a fix)** — Collapse each
  ~20-line log entry by default, behind a toggle. Status: APPROVED into §6.2
  item 18 (D67). **BUILT-IN-`2837b9a`, device verification pending.**
  `ReportLog` owns the collapsed/expanded state (a parallel `expandedFlags`
  list, same index space as `entries`); `rendered()` shows the title line
  only when collapsed, the unmodified full block when expanded — the
  stored content (`entriesSnapshot()`) is never altered. A tap on an
  entry's title line (`ClickableSpan`, `logView.movementMethod =
  LinkMovementMethod`) toggles via `MainActivity.onLogEntryTapped`. 11 new
  `ReportLogTest` cases. (zkagent-prd.md:2138-2144)
- **Q44 (opened 2026-09-02 — UI/UX ENHANCEMENT, not a fix)** — Dim a completed
  run with ticked checkboxes to show it is done. Status: APPROVED into §6.2
  item 19 (D67). (zkagent-prd.md:2146-2152)
- **Q45 (opened 2026-09-02 — UI/UX ENHANCEMENT, not a fix)** — A single control
  distinguishing a "verify" scan from a "scan local" one. Status: APPROVED into
  §6.2 item 20 (D67), open shape only — the coder proposes at build time, owner
  approves. (zkagent-prd.md:2154-2160)
- **Q46 (opened 2026-09-02, deferred under D57 — label CORRECTNESS defect, not a
  styling preference)** — The MRZ input field is labelled "Passport number," which
  is factually wrong since the app also reads ID cards. Status: **FIXED in
  `d4653b9`** — `strings.xml` `input_passport_number` now reads "Document number"
  (resource id and `passportNumberView` field name unchanged on purpose); worked as
  a fix rather than left deferred, per owner direction 2026-09-02 that Q46/Q47 be
  worked now despite the earlier D57 UI-pass deferral. No device evidence — no
  device attached this session. (zkagent-prd.md:2162-2168)
- **Q47 (opened 2026-09-02, deferred under D57 — input-focus CORRECTNESS defect,
  not a styling preference)** — Typing in the date fields steals focus back to the
  document-number field, corrupting entered MRZ data. Status: **FIXED,
  device-confirmed 2026-09-03.** The `clearFocus()`-plus-hide-keyboard fix
  (`0b71957`) is confirmed on device by owner eye ("cursor fixed") after using the
  `DatePickerDialog` OK on both date fields; no log line exists for this by design.
  Prior session's investigation traced and ruled out every app-code focus mechanism
  (see `.claude/remember/findings.md` #17 for the full trace); standing hypothesis
  (unconfirmed, and not needed to close this — the fix works regardless of root
  cause): Android's own post-`DatePickerDialog` default focus restoration landing
  on `input_passport_number`, the form's only touch-focusable field — a framework
  mechanism, not app code. Evidence:
  `docs/logs/M2-DEVICE-SESSION-2026-09-03-EVIDENCE.md` check 1; prior investigation:
  `docs/logs/M2-FENCE-EVIDENCE.md`; full record: `.claude/remember/findings.md` #17.
  (zkagent-prd.md:2170-2179; owner, 2026-09-02/2026-09-03)
- **Q48 (opened 2026-09-02 — UI/UX ENHANCEMENT, human-ID only, not a security
  question)** — Three installed reader apps are indistinguishable on the
  launcher. Status: BUILT-IN-07285b4, device verification pending — Option
  A of the coder's proposal shipped (adaptive icon, solid brand-colour
  background + monochrome white glyph foreground/themed-icon layer, release
  label "zkagent Scanner", debug label "zkagent Scanner (Debug)" plus a
  corner-badge debug icon); confirmed via `aapt2 dump badging` on a built
  debug APK. `spikes/m0`/`spikes/m2-scan` intentionally untouched (frozen
  spike forks, out of item 21's scope). Verified NOT a security gap (`av://`
  routing resolves deterministically to the scanner app); does not bear on
  Q41/Q42's findings. (zkagent-prd.md:2181-2194)
