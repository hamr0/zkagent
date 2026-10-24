---
type: reference
title: zkagent — open questions Q1–Q52 (triaged at close, D108)
status: stable
sources: [docs/archive/zkagent-prd.md]
---

# zkagent — open questions Q1–Q52

Deduped index of PRD §11 (Open questions). Every Qn from Q1–Q52 is listed in order;
narrative status-update chains, commit/test-count histories, and line anchors from
the source are intentionally not reproduced here — see the citation for full detail.

**All questions were triaged at close-out (D108, 2026-10-24).** Each entry ends with a
`Final status (D108)` line, one of CLOSED, BACKLOG (open but parked; see `backlog.md`), or
KNOWN-LIMITATION (accepted and disclosed). Where an entry's earlier status text disagrees with
its final line, the final line governs; stale "device verification pending" wording was corrected
in place only where the entry itself records the later confirmation. Nothing is marked
device-verified unless an evidence doc records it ("built, device verification not recorded"
otherwise).

Three numbers (Q3, Q4, Q10) do not exist anywhere in `zkagent-prd.md` and are recorded as such
rather than silently dropped. Four more (Q2, Q5, Q6, Q9) are closed, but their one-line
resolutions sit at `zkagent-prd.md:1800`, two lines above the instructed 1802–2195 range, so no
in-range citation is given for them.

## Final status index

| Final status | Questions |
|---|---|
| CLOSED | Q1, Q2, Q5, Q6, Q9, Q15, Q18, Q23, Q25, Q27, Q28, Q29, Q30, Q32, Q33, Q35, Q36, Q37, Q38, Q39, Q40, Q41, Q42, Q43, Q44 (withdrawn), Q45, Q46 (device verification not recorded), Q47, Q48, Q49, Q50, Q51; plus Q11, Q16, Q20, Q22, Q34 (each with a BACKLOG residual or a by-implementation closure, see the entry) |
| BACKLOG | Q7, Q8, Q14, Q19, Q21, Q26, Q52; plus the M3b residuals of Q11, Q20, Q22, Q34 |
| KNOWN-LIMITATION | Q12, Q13, Q17, Q24, Q31 |
| Never existed | Q3, Q4, Q10 |

- **Q1** — Whether attestation-chain verification (CBOR + X.509 parsing of untrusted
  input) justifies an exception to the project's zero-runtime-deps tradition. Status:
  CLOSED — folded into Q14 as "the former Q1"; one vetted, dependency-light library
  is the accepted exception. (zkagent-prd.md:1809-1809)
  **Final status (D108, 2026-10-24): CLOSED (folded into Q14; the exception is the accepted resolution).**
- **Q2** — Whether Apple's NFC tag-reading entitlement matters for M0. Status: CLOSED —
  moot for M0 (Android-first, D2). No in-range citation (resolution text is at
  zkagent-prd.md:1800, outside the assigned 1802-2195 excerpt).
  **Final status (D108, 2026-10-24): CLOSED (moot, D2).**
- **Q3** — No entry under this number exists anywhere in `zkagent-prd.md`.
  **Final status (D108, 2026-10-24): never existed.**
- **Q4** — No entry under this number exists anywhere in `zkagent-prd.md`.
  **Final status (D108, 2026-10-24): never existed.**
- **Q5** — Whether Android or iOS is the primary platform. Status: CLOSED by D2 —
  Android is primary. No in-range citation (resolution text is at zkagent-prd.md:1800,
  outside the assigned 1802-2195 excerpt).
  **Final status (D108, 2026-10-24): CLOSED (D2).**
- **Q6** — Whether iOS support is in scope for this project. Status: CLOSED by D2 —
  iOS deferred. No in-range citation (resolution text is at zkagent-prd.md:1800,
  outside the assigned 1802-2195 excerpt).
  **Final status (D108, 2026-10-24): CLOSED (D2).**
- **Q7 (M2)** — Whether exposing a client's device-assurance tier (e.g. StrongBox)
  conflicts with FR6's anti-fingerprinting uniformity requirement. Status: OPEN —
  decide between accepting a small fingerprinting cost or enforcing one uniform
  hardware bar. (zkagent-prd.md:1804-1804)
  **Final status (D108, 2026-10-24): BACKLOG — only arises if an attestation plug that exposes a device-assurance tier is ever built (§6.5 S4); the reference app ships bare (D27), so FR6 uniformity is unaffected today. See backlog.md.**
- **Q8 (M4, rung 2)** — RFC 9421 mapping details for delegation certs: which header
  carries the cert, inline-vs-URL delivery, and which covered components are
  mandatory in the signature base. Status: OPEN — URL-reference already rejected for
  v1; decide against a live off-the-shelf verifier. (zkagent-prd.md:1805-1805)
  **Final status (D108, 2026-10-24): BACKLOG — rung 2, not designed or planned (D108). See backlog.md.**
- **Q9** — How the phone→agent delegation cert is physically transported. Status:
  CLOSED — ships all three paths (QR, LAN POST, user-moved file); no zkagent-run
  server in any path. No in-range citation (resolution text is at zkagent-prd.md:1800,
  outside the assigned 1802-2195 excerpt).
  **Final status (D108, 2026-10-24): CLOSED.**
- **Q10** — No entry under this number exists anywhere in `zkagent-prd.md`.
  **Final status (D108, 2026-10-24): never existed.**
- **Q11 (M3, mode B only)** — Whether an adopter-chosen age threshold enables
  binary-search probing of a holder's exact date of birth. Status: CLOSED for tiers A/B by D74 (see below; original wording: "OPEN — D13
  shrinks the risk in mode A; decide before M3 whether a fixed threshold set is
  still needed in mode A vs. mode B only"). **Must close before M3 build — see
  milestones.md §6.3 item 6 / DP4 (D73, 2026-09-03): M3 ships threshold 18 only,
  no picker, pending this question's resolution.** **CLOSED by D74 (2026-09-03).**
  Summary: preset published threshold list `{15,16,18,21,60,65}`, first-seen
  threshold locked per origin, exact-hostname exception allowlist, question shown
  above the Verify button before the tap — removes the binary-search space rather
  than rate-limiting it; scanner build tracked as milestones.md §6.5 S1/S2.
  **Tier-C shape (D95, 2026-09-07/08; list contents fixed by D97, 2026-09-26):
  NARROWED further, residual point still OPEN for tier C.** D74's closure above
  is scoped to M3/mode B. For tier C, D95 rules that DOB-range/expiry-bucket
  boundaries also come from a FIXED published list, in the same shape as D74,
  never chosen by the requesting verifier, with the same fail-loudly rule for
  an off-list boundary. D97 then fixed the list's actual contents, which D95
  had left open: expiry buckets `{3 months, 6 months, 1 year}`; DOB-range
  boundaries the same six ages D74 already uses for tier-A/B, `{15, 16, 18,
  21, 60, 65}` (an owner-proposed 31 was corrected to 21 in the same exchange
  and never adopted). Consequence noted, not further ruled: tier-C DOB-range
  now answers the same kind of question tier-A/B thresholds already answer,
  attached to an attributed presentation instead of an unlinkable one. Even
  against a fixed list, three predicates still yield three bits about a date
  of birth, on top of D91's flat cap of 3 and D93's shared per-origin ceiling
  (those bound how often the bits can be asked, not whether asking them is
  itself informative) — this residual point remains OPEN; the boundary
  mechanism and list contents are both now DECIDED. Tracked as milestones.md
  §6.4 item 4. See decisions.md D74, D91, D93, D95, D97.
  (zkagent-prd.md:1806-1806)
  **Final status (D108, 2026-10-24): CLOSED by D74 for tiers A/B (device-confirmed, §6.5 S1; evidence M3-SCANNER-S1-EVIDENCE-2026-09-05.md). The tier-C residual (three DOB-range predicates are still three bits) is BACKLOG with M3b, §6.4 item 4.**
- **Q12 (M0/M3)** — Which ICAO documents (beyond passports) are actually readable
  by the JMRTD path used. Status: OPEN, empirical — do not state coverage numbers
  in any pitch until M0 reports per-country results. (zkagent-prd.md:1807-1807)
  **Final status (D108, 2026-10-24): KNOWN-LIMITATION — readability was measured on exactly two documents (a US passport, BAC; an NL ID card, PACE) with one code path and no per-country logic (M0-EVIDENCE.md Finding 8). No coverage numbers are claimed anywhere.**
- **Q13 (M3, strategic)** — Who adopts zkagent first — a verifier or a client
  builder. Status: OPEN — log the first-adopter path as evidence, not assertion.
  (zkagent-prd.md:1808-1808)
  **Final status (D108, 2026-10-24): KNOWN-LIMITATION — no external adopter was ever found; the only adopter is the repo's own demo (S3). Nothing to log.**
- **Q14 (M1, then M2)** — Whether the attestation root should be Play Integrity,
  hardware key attestation, or both, plus keybox-extraction, vendor-quality, and
  revocation risks. Status: OPEN (leaning "both") — M1 POC found stdlib parsing
  feasible; sub-item (d), the Pixel 6a's expired root-cert policy, is unresolved.
  (zkagent-prd.md:1809-1809)
  **Final status (D108, 2026-10-24): BACKLOG — never decided. The reference app ships bare, with no attestation plug (D27), so no attestation root was chosen; sub-item (d) (the Pixel 6a's expired root-cert policy) stays unresolved. Gates S4 in backlog.md.**
- **Q15 (M1b)** — Whether device attestation defeats the mode-A unlinkability claim.
  Status: CLOSED by D26 on M1b evidence — every salted field is fresh per
  presentation; only the circuit-class bucket (`vk_sha256`) is cross-site-stable,
  and is disclosed by owner decision. Evidence: `docs/logs/M1B-EVIDENCE.md` §4-§5.
  (zkagent-prd.md:1810-1810)
  **Final status (D108, 2026-10-24): CLOSED (D26).**
- **Q16 (M2)** — Whether mode-A should re-scan the document on every presentation or
  cache the verified claim under the D10 ceiling. Status: OPEN — decide on real UX
  evidence from M2. (zkagent-prd.md:1811-1811)
  **Final status (D108, 2026-10-24): CLOSED by implementation, not by an explicit ruling — the scanner always reads the chip on every presentation (§6.2 item 3, `continueAfterRead`) and FR1 forbids caching identity-bearing material in mode A. No UX study was run.**
- **Q17 (before any age pitch)** — Whether a mode-A presentation can legally satisfy
  an age-verification duty in a named jurisdiction. Status: OPEN — project posture
  is demonstration, not certification; must be checked against a named jurisdiction
  before the framing shifts to a pitch. (zkagent-prd.md:1812-1812)
  **Final status (D108, 2026-10-24): KNOWN-LIMITATION — the project posture is demonstration, not certification; legal sufficiency in a named jurisdiction was never checked and is not claimed (README, privacy policy, customer guide).**
- **Q18** — Whether chip cloning breaks mode B's uniqueness/blocking guarantees.
  Status: CLOSED 2026-08-31 — M0 Finding 9 measured AA/CA support per document;
  mode B accepts non-chip-auth documents and reports `chip_auth` per D21/D29;
  clone-replay is an accepted, disclosed limitation. Mode A is unaffected.
  (zkagent-prd.md:1813-1813)
  **Final status (D108, 2026-10-24): CLOSED (2026-08-31).**
- **Q19 (parked, not designed)** — Whether freshness should be a requester-stated
  range (a floor), not just an operator ceiling (D10). Status: PARKED — do not
  design before rung 1 ships; in tension with D10's one-bit-only freshness rule.
  (zkagent-prd.md:1815-1822)
  **Final status (D108, 2026-10-24): BACKLOG (parked, not designed; in tension with D10).**
- **Q20 (resolved in principle by D20; mechanics at M1)** — How operator identity
  works in a borrowable core with no registry. Status: ANSWERED-not-fully-decided —
  the issuer's signing key is its identity; key format, challenge encoding, and
  expiry precedence still need to be fixed in code at M1. (zkagent-prd.md:1824-1829)
  **Final status (D108, 2026-10-24): CLOSED for tiers A/B (D20, D37: the request signer's key is resolved from the requesting origin; built as `RequestTrust`, ES256 request objects, device-confirmed in M2/M3). The tier-C pinned-key mechanics are BACKLOG with M3b (D94).**
- **Q21 (deferred, rung-2-shaped)** — How an authority admits a requester to tier C
  (live signing service vs. delegated certificate). Status: DEFERRED — the
  delegation option is rung-2 machinery, forbidden to design before rung 1 ships;
  v1 ships only the direct case. (zkagent-prd.md:1831-1837)
  **Final status (D108, 2026-10-24): BACKLOG — rung-2-shaped (D108).**
- **Q22 (before tier C is built)** — What the tier-C verb vocabulary of identifying
  predicates should be. Status: **substantially CLOSED.** D90 (2026-09-07) settled which
  predicates exist (expiry buckets, name-match, DOB-range, nationality in two boolean modes,
  document-number match) — see Q34. D101 (2026-09-26) closed the name-match comparison rule
  (case/hyphen-insensitive, accent-stripped, MRZ `<` treated as a separator; still a hard
  boolean, no similarity scores — this rule stands unchanged) and flagged, but did not rule
  on, MRZ field-length truncation as a disclosed limitation; further normalization beyond
  accent-stripping (e.g. `ß`, `Ø`, transliteration tables) stays an OPEN edge case for M3b's
  POC. D102 (2026-09-26) closed the nationality-exclusion set size at a cap of 10 countries.
  Remaining OPEN: MRZ truncation behavior and any further normalization beyond D101's rule.
  See decisions.md D90, D101, D102. (zkagent-prd.md:1839-1844)
  **Final status (D108, 2026-10-24): CLOSED for the vocabulary (D90, D101, D102). The remaining edge cases (MRZ truncation, normalisation beyond accent-stripping) are BACKLOG with M3b.**
- **Q23 (resolved by D23)** — Whether tier A's attestation requirement is compatible
  with the "nothing stable across sites" unlinkability bar. Status: CLOSED by D23 —
  Play Integrity measured with no device-unique field; owner's call could still be
  reversed toward option (5), ZK over the passport SOD, per the same evidence.
  Evidence: `docs/logs/M1-Q23-EVIDENCE.md`. (zkagent-prd.md:1846-1870)
  **Final status (D108, 2026-10-24): CLOSED (D23, superseded by D24).**
- **Q24 (open, v1 accepted)** — De-Googled devices have no D22-compliant attestation
  route. Status: OPEN, accepted for v1 by D23 — bare mode is available to them;
  revisit with the ZK track or a split verifier. (zkagent-prd.md:1872-1876)
  **Final status (D108, 2026-10-24): KNOWN-LIMITATION — accepted for v1 by D23: bare mode is available to de-Googled devices; no attestation route exists.**
- **Q25 (closed by D27)** — Whether the reference app itself should carry Play
  Integrity evidence. Status: CLOSED — the reference app ships bare or with
  `zk-passport/1`, never a self-signed receipt (would violate NO-GO #3).
  (zkagent-prd.md:1878-1880)
  **Final status (D108, 2026-10-24): CLOSED (D27).**
- **Q26 (open, gated behind Track Z)** — Whether a ZK passport circuit can expose
  both a stable scope-nullifier and a fresh challenge nonce as public inputs.
  Status: OPEN — no option chosen among upstream PR, a different circuit family, or
  unproven per-request nullifiers. (zkagent-prd.md:1882-1887)
  **Final status (D108, 2026-10-24): BACKLOG — gated behind Track Z (backlog.md); no gate was met.**
- **Q27 (closed by D28)** — Whether `current_date` granularity in the age circuit
  should be coarsened. Status: CLOSED — coarsen to day granularity, midnight-UTC
  (D28); `max_scan_age` floors at 1 day. (zkagent-prd.md:1889-1895)
  **Final status (D108, 2026-10-24): CLOSED (D28).**
- **Q28 (closed by D36)** — Whether a device may voluntarily offer a weaker evidence
  plug than its strongest available one. Status: CLOSED — no such choice exists; the
  device always tries its strongest-preference plug first and falls through only on
  failure (D36). (zkagent-prd.md:1897-1905)
  **Final status (D108, 2026-10-24): CLOSED (D36).**
- **Q29 (closed by D37)** — How `trustedChallengeIssuers` request-signer keys are
  provisioned/rotated on-device. Status: CLOSED — the trust anchor is the requesting
  site's HTTPS origin, fetched from a well-known path, not a pinned list (D37); M2
  keeps one build-time dev key until a real TLS origin exists. (zkagent-prd.md:1906-1917)
  **Final status (D108, 2026-10-24): CLOSED (D37).**
- **Q30** — Whether the zktag's domain-scope derivation and the origin-consistency
  security check should use the same granularity (host vs. full origin). Status:
  CLOSED 2026-09-01 — host-only signing scope, full-origin consistency check (D42);
  the host-vs-registrable-domain divergence is flagged as a production note, not a
  blocker. (zkagent-prd.md:1919-1932)
  **Final status (D108, 2026-10-24): CLOSED (D42).**
- **Q31 (open, orchestrator-flagged)** — How a user re-enrolls after key loss under
  first-sight (TOFU) attester-key binding. Status: OPEN — options listed (manual
  unbinding, binding TTL, recovery secret, or permanent refusal), none decided;
  genuine tension with the security property TOFU exists to provide.
  (zkagent-prd.md:1933-1944)
  **Final status (D108, 2026-10-24): KNOWN-LIMITATION — no re-enrolment path exists; a lost or reset phone may be treated by a site as a key mismatch (D38). Disclosed in customer-guide.md. Options listed above were never decided.**
- **Q32 (closed by D47)** — Exact shape of the log view's plain-language disclosure
  summary, and the no-site label wording. Status: CLOSED — owner chose a four-field
  block (`Result`/`Sent`/`Shared`/`Identity`) plus a subordinate technical line
  (D47), and confirmed "Local scan (no site)" as the no-site label.
  (zkagent-prd.md:1945-1956)
  **Final status (D108, 2026-10-24): CLOSED (D47).**
- **Q33 (superseded, split into Q35/Q36)** — Original claim: the scanner asserts
  `over_threshold: true` unconditionally and never implements D11's threshold
  comparison. Status: SUPERSEDED 2026-09-01 — the premise was incomplete (the
  request-carried threshold and D11 enforcement already exist in code); split into
  Q35 (scanner-side one-line fix) and Q36 (real DOB comparison, genuine design
  work). (zkagent-prd.md:1958-1983)
  **Final status (D108, 2026-10-24): CLOSED (superseded; split into Q35 and Q36).**
- **Q34 (CLOSED by D90/D91, 2026-09-07)** — Whether/how zkagent should support a general claim
  vocabulary beyond age (e.g. document-expiry buckets). D49 had already settled only the
  rendering SHAPE (a list of predicate→boolean pairs). Status: **CLOSED** — D90 settles which
  claims exist (expiry buckets, name-match, DOB-range, nationality in two modes, document-number
  match, all boolean, v1 vocabulary complete); D91 settles the per-tier limit (flat cap of 3
  per presentation, no operator knob) and the cross-site/repeat-presentation accumulation
  question (a per-origin lifetime predicate set, capped at 3, mirroring D74's lock/refuse
  shape). What remains open, precisely: (1) D91 narrows but does not close Q11's DOB-range
  binary-search risk in its tier-C shape — a first request of three well-chosen DOB-range
  predicates is still three bits the app cannot refuse; tracked as milestones.md §6.4 item 4,
  not as part of Q34 itself. (2) D91(d)'s reading of the re-ask rule (a subset re-ask of the
  locked set is permitted) is now CONFIRMED (2026-09-07, owner: "same 3 or subset of it") — no
  longer open. A related, distinct question raised in the same 2026-09-07 exchange — how the
  per-origin lock is keyed across a holder's multiple documents — is settled by D93 (per-document
  records, shared 3-predicate ceiling); D93 is not part of Q34, tracked under milestones.md §6.4
  item 2. (3) §6.4 items 3 and 5 (pinning UX / Q49, and lifting the scanner's tier-C refusal) are
  untouched by this closure. **Cross-referenced 2026-09-03 (D73): this is M3b's problem, not
  M3's — see milestones.md §6.4.** (zkagent-prd.md:2034-2060)
  **Final status (D108, 2026-10-24): CLOSED (D90/D91). The tier-C DOB-range residual is BACKLOG with M3b (§6.4 item 4).**
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
  **Device-confirmed 2026-09-03 PM**: a fresh verifier spike started with
  `THRESHOLD=99` (not the default 18) produced `over_threshold=false
  threshold=99` in the app's own log — the scanner is genuinely reading the
  request-carried threshold, not a hardcoded value. See
  `docs/logs/M2-DEVICE-SESSION-2026-09-03-PM-EVIDENCE.md` check 8.
  **Final status (D108, 2026-10-24): CLOSED (fixed; device-confirmed 2026-09-03 PM).**
- **Q36 (closed by D66, descendant of Q33 part b)** — Compute a real
  DOB-vs-threshold answer instead of asserting `true` unconditionally. Status:
  FIXED-IN-5a4b13b (D66) — owner ruled D66 (2026-09-03): the
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
  split was then not exercisable end-to-end (device-confirmed later, 2026-09-03 PM, see below).
  (zkagent-prd.md:2019-2032; decisions.md D66, owner 2026-09-03)
  **Device-confirmed 2026-09-03 PM**: the honest-under path was exercised
  end-to-end against a real verifier spike (`THRESHOLD=99`) — the app logged
  `over_threshold=false threshold=99`, still minted and `direct_post`ed, and
  the verifier independently recorded `ok=true allowed=false
  reason=under_threshold`, confirming the split verdict the spike previously
  could not exercise. See `docs/logs/M2-DEVICE-SESSION-2026-09-03-PM-EVIDENCE.md`
  check 8.
  **Final status (D108, 2026-10-24): CLOSED (D66; device-confirmed 2026-09-03 PM).**
- **Q37 (closed, resolved by implementation)** — Whether "consumed" vs. "expired"
  handoff sessions can be distinguished device-side without a verifier round-trip.
  Status: CLOSED 2026-09-01 — challenge expiry is reachable from the verified
  request object directly; "consumed" needs no detection since a used session is
  already cleared from app state. (zkagent-prd.md:2062-2077)
  **Final status (D108, 2026-10-24): CLOSED.**
- **Q38 (closed by D64, persistence promoted by D70(b))** — Whether the in-app
  log needs to survive app close (process death). Status: CLOSED 2026-09-02 —
  Option A (accept and disclose) for the in-flight-mint-loss case: a mid-
  `direct_post` recreation still delivers the proof to the site while the phone
  shows nothing; that half is unchanged. Option B (persist the log itself across
  process death/restart, host + timestamp + outcome, in app-private storage) was
  deferred to the next module's list at close; **promoted into §6.2 item 23
  (D70(b), 2026-09-03)** — the value-free log now MUST persist under the D59 cap
  with a "Clear log" control, but this does not change the accept-and-disclose
  answer for the in-flight-mint-loss case itself. Status: BUILT, device-confirmed
  2026-09-03 PM (see the confirmation below). `ReportLogStore` (new, pure, Android-free) serializes
  `ReportLog`'s entries/expanded/terminal/outcome state to a single JSON file
  in `filesDir`, written atomically (temp file + rename) on every entry
  append/replace/toggle; D59's cap is re-enforced on load. Loaded in
  `onCreate` before first render — the existing `onSaveInstanceState` Bundle
  path is kept as the fast/authoritative path for an in-process recreation
  (it always wins when present), disk is the durable path for a cold start.
  No MRZ/zktag/nonce/key material is threaded through it (item 6 unchanged).
  A new "Clear log" button empties memory + disk and logs
  `M2 stage: log cleared by user (entries=<n>)`. 10 new `ReportLogStoreTest`
  cases. This does NOT change the in-flight-mint-loss case (finding #16):
  the loss happens before any outcome is known to write a log entry at all.
  Owner: "option A," then (D70) the persistence promotion from the device
  session. Evidence: `docs/logs/M2-FENCE-EVIDENCE.md`,
  `.claude/remember/findings.md` #16. (owner,
  2026-09-02; 2026-09-03)
  **Device-confirmed 2026-09-03 PM**: `M2 stage: loaded persisted log from
  disk (entries=N)` observed at the start of three separate app instances in
  one session, `entries` climbing 1→2→3 as scans accumulated — the log
  genuinely persists across process death/restart. "Clear log" was not
  exercised this session. See
  `docs/logs/M2-DEVICE-SESSION-2026-09-03-PM-EVIDENCE.md` check 11.
  **Final status (D108, 2026-10-24): CLOSED (D64, D70(b)); device-confirmed 2026-09-03 PM. "Clear log" was not exercised on device.**
- **Q39 (opened 2026-09-02)** — Whether an incoming handoff intent should switch
  the visible tab. Status: APPROVED into §6.2 item 17 (D67) — distinct from the
  already-rejected D55 (auto-switch on read completion); the tab-state ownership
  seam (`.claude/remember/findings.md` #1) that previously blocked this is closed.
  **BUILT-IN-`d584f00`; device-confirmed 2026-09-03 PM (see below).** `PaneState.
  onIncomingHandoffIntent(admitted: Boolean)` is a new writer on the SAME
  owner (not a second owner of the tab index); `MainActivity.
  handleIncomingIntent`'s already-admitted `av://` branch calls it before
  `beginHandoffVerification`. Unit-tested (`PaneStateTest`): admitted
  switches Log -> Scan, is a no-op already on Scan, and — the MUST NOT half
  — a refused (`HandoffAdmission`) intent leaves the tab untouched.
  (zkagent-prd.md:2099-2111)
  **Device-confirmed 2026-09-03 PM**: owner sat on the Log tab; an `av://`
  link fired at 15:10:22 and the app switched to the Scan tab on its own,
  with no manual tap. See
  `docs/logs/M2-DEVICE-SESSION-2026-09-03-PM-EVIDENCE.md` section 13.3.
  **Final status (D108, 2026-10-24): CLOSED (D67); device-confirmed 2026-09-03 PM.**
- **Q40 (opened 2026-09-02)** — The disabled Lock button after an
  access-establishment failure reads as "stuck"; owner wants copy closer to
  "Tap and scan." Status: CLOSED (owner sign-off 2026-09-03) — the ownership
  precondition (`.claude/remember/findings.md` #9) was already closed and the
  "Tap and scan" label already shipped; the wording itself is now signed off.
  (zkagent-prd.md:2113-2132)
  **Final status (D108, 2026-10-24): CLOSED (owner sign-off 2026-09-03).**
- **Q41 (opened 2026-09-02, EXPLOITABLE, consequence HIGH, deferred under D57)** —
  The unguarded `av://` intent-handling path lets any on-device app hijack a
  session mid-read. Status: CLOSED by D61 (2026-09-02) — `HandoffAdmission` gates the
  path (commit `730ef09`) and is permanent policy; device-proven against a hostile second
  origin (findings #10). Earlier text: "MITIGATED, not closed; remains OPEN for the full ownership fix". See
  `.claude/remember/findings.md` #10. (zkagent-prd.md:2134-2134)
  **Final status (D108, 2026-10-24): CLOSED (D61; findings #10).**
- **Q42 (opened 2026-09-02, consequence HIGH, deferred under D57)** — The
  biometric-authorization prompt shows no origin/site/tier, so the user cannot
  tell whose request they authorized. Status: CLOSED by D62 (2026-09-02) — the prompt
  now renders the site (commit `730ef09`), owner-confirmed on device. Earlier text:
  "MITIGATED, not closed; remains OPEN for the full ownership fix". See `.claude/remember/findings.md` #11. (zkagent-prd.md:2136-2136)
  **Final status (D108, 2026-10-24): CLOSED (D62; findings #11).**
- **Q43 (opened 2026-09-02 — UI/UX ENHANCEMENT, not a fix; follow-on glyph
  ENHANCEMENT opened 2026-09-03)** — Collapse each ~20-line log entry by
  default, behind a toggle. Status: APPROVED into §6.2 item 18 (D67).
  **BUILT-IN-`8bc37a4`; device-confirmed 2026-09-03 PM (see below).** `ReportLog` owns the
  collapsed/expanded state (a parallel `expandedFlags` list, same index space
  as `entries`); `rendered()` shows the title line only when collapsed, the
  unmodified full block when expanded — the stored content
  (`entriesSnapshot()`) is never altered. A tap on an entry's title line
  (`ClickableSpan`, `logView.movementMethod = LinkMovementMethod`) toggles via
  `MainActivity.onLogEntryTapped`. 11 new `ReportLogTest` cases. Follow-on:
  the collapsed title line alone gave no at-a-glance outcome — **APPROVED into
  §6.2 item 22 (D70(a), 2026-09-03)**, a pass/fail/pending glyph on the
  collapsed title line, derived from item 19's existing terminal-outcome
  state. Status: BUILT, device-confirmed 2026-09-03 PM (glyphs seen on device, see below). A new `ReportLog.Outcome`
  enum ({PENDING, PASS, FAIL}) is set at the SAME single write site
  ([ReportLog.append]) that already sets `terminalFlags`, classified by
  `MainActivity` from types it already has (`DeliveryResult`,
  `M0Probe.Verdict.ok`, each diagnostic probe's own `failed` boolean) —
  never parsed from the report string; the PASS/FAIL rule is documented in
  `ReportLog.Outcome`'s own KDoc as owner-overturnable (see that doc for
  the `RefusedHonestUnderThreshold` -> FAIL call). Glyphs are the fixed
  Unicode prefixes "✓ "/"✗ "/"… " baked into the entry's stored title line,
  so both collapsed and expanded views show it identically with no extra
  span/styling work. 11 new `ReportLogTest` cases. (zkagent-prd.md:2138-2144)
  **Owner-observed 2026-09-03 PM**: collapsed entries and their glyphs were
  seen on device (item 18/22), not independently re-verified against the log
  by this session's own reading of logcat (no logging requirement exists for
  this UI-only behaviour, by design). See
  `docs/logs/M2-DEVICE-SESSION-2026-09-03-PM-EVIDENCE.md` check 11.
  **Device-confirmed 2026-09-03 PM, second pass (~15:02–15:15)**: the owner
  tapped an older collapsed entry and it expanded — item 18's per-entry
  toggle device-confirmed in the same glance as section 13's other checks.
  See `docs/logs/M2-DEVICE-SESSION-2026-09-03-PM-EVIDENCE.md` section 13
  addendum.
  **Final status (D108, 2026-10-24): CLOSED (D67, D70(a)); device-confirmed 2026-09-03 PM.**
- **Q44 (opened 2026-09-02 — UI/UX ENHANCEMENT, not a fix)** — Dim a completed
  run with ticked checkboxes to show it is done. Status: APPROVED into §6.2
  item 19 (D67). **BUILT-IN-`1bc345b`; WITHDRAWN by D71(a) (see the closing note below) — the dimming was removed.** Item
  19's approved MUST text is "visually dim a completed run" only — it does
  not carry the original "ticked checkboxes" phrasing forward, so the
  build implements dimming (a `ForegroundColorSpan` over the whole entry,
  color derived from `logView`'s own text color at ~60% alpha) and does
  NOT add checkboxes; flagged for owner review in case a checkbox was
  intended as well as, not instead of, dimming. `ReportLog` owns a new
  `terminalFlags` list (same index space as `entries`/`expandedFlags`),
  derived from the existing `append`'s `pending` parameter — never a new
  flag guessed from strings. 8 new `ReportLogTest` cases. Follow-on:
  **item 22 (D70(a), 2026-09-03)** derives its pass/fail/pending glyph from
  this same `terminalFlags`-backed terminal-outcome state — no new state
  introduced for the glyph. (zkagent-prd.md:2146-2152)
  **Device-observed 2026-09-03 PM (as built, owner decision pending)**: owner
  saw every log entry rendered in the same monotone grey, with no visible
  contrast between entries. This is the design as built, not a bug —
  `ReportLog.rendered` dims every terminal-outcome entry (60% alpha) and only
  an in-progress entry keeps full colour; with no scan in flight during this
  check, every visible entry was terminal, so all rendered equally dimmed.
  Contrast is only visible during a live scan. Owner has been offered "keep
  as built" vs. "newest entry stays bright regardless of terminal state";
  neither is chosen yet. Not PASS, not FAIL. See
  `docs/logs/M2-DEVICE-SESSION-2026-09-03-PM-EVIDENCE.md` section 13.5.
  **CLOSED 2026-09-03 ~15:30 (D71a) — not wanted.** Owner chose a third
  option over both offered ("keep as built" / "newest entry stays bright"):
  drop dimming entirely. §6.2 item 19 is WITHDRAWN (number reserved, not
  reused); code removal is owed as a follow-up. See decisions.md D71(a);
  milestones.md item 19 (withdrawn) and its exit-criteria row.
  **Final status (D108, 2026-10-24): CLOSED — WITHDRAWN by D71(a) (dimming dropped; `ReportLog.kt` no longer contains the terminal-flag/dimming code, checked 2026-10-24).**
- **Q45 (opened 2026-09-02 — UI/UX ENHANCEMENT, not a fix)** — A single control
  distinguishing a "verify" scan from a "scan local" one. Status:
  BUILT-IN-fc5faec; device-confirmed 2026-09-03 PM (see below) — owner ruled D68 (2026-09-03):
  no new control; the existing scan-action button's verb ("Verify"/"Scan",
  locked "Tap and verify"/"Tap and scan") is a pure projection of handoff
  state, driven by the lock-time `authorizedHandoff` snapshot. Unit-tested
  (`SessionDisplayTest`); initially not exercised on a real device, then device-confirmed (below). (zkagent-prd.md:2154-2160;
  decisions.md D68)
  **Device-confirmed 2026-09-03 PM**: with a verified `av://` link pending the
  button read "Verify"; after a fresh open with no link pending it read
  "Scan". See `docs/logs/M2-DEVICE-SESSION-2026-09-03-PM-EVIDENCE.md`
  section 13.1.
  **Final status (D108, 2026-10-24): CLOSED (D68); device-confirmed 2026-09-03 PM.**
- **Q46 (opened 2026-09-02, deferred under D57 — label CORRECTNESS defect, not a
  styling preference)** — The MRZ input field is labelled "Passport number," which
  is factually wrong since the app also reads ID cards. Status: **FIXED in
  `d4653b9`** — `strings.xml` `input_passport_number` now reads "Document number"
  (resource id and `passportNumberView` field name unchanged on purpose); worked as
  a fix rather than left deferred, per owner direction 2026-09-02 that Q46/Q47 be
  worked now despite the earlier D57 UI-pass deferral. No device evidence is recorded for this label change (no device attached that session, and no later
  evidence doc mentions it): built, device verification not recorded. (zkagent-prd.md:2162-2168)
  **Final status (D108, 2026-10-24): CLOSED — fixed in `d4653b9`; built, device verification not recorded.**
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
  **Final status (D108, 2026-10-24): CLOSED (device-confirmed 2026-09-03; findings #17).**
- **Q48 (opened 2026-09-02 — UI/UX ENHANCEMENT, human-ID only, not a security
  question)** — Three installed reader apps are indistinguishable on the
  launcher. Status: BUILT-IN-07285b4; device-confirmed 2026-09-03 PM (see below) — Option
  A of the coder's proposal shipped (adaptive icon, solid brand-colour
  background + monochrome white glyph foreground/themed-icon layer, release
  label "zkagent Scanner", debug label "zkagent Scanner (Debug)" plus a
  corner-badge debug icon); confirmed via `aapt2 dump badging` on a built
  debug APK. `spikes/m0`/`spikes/m2-scan` intentionally untouched (frozen
  spike forks, out of item 21's scope). Verified NOT a security gap (`av://`
  routing resolves deterministically to the scanner app); does not bear on
  Q41/Q42's findings. (zkagent-prd.md:2181-2194)

  **Device-confirmed 2026-09-03 PM**: owner saw the launcher icon/label on
  device (~12:5x), matching this description. See
  `docs/logs/M2-DEVICE-SESSION-2026-09-03-PM-EVIDENCE.md` check 11.

  **Addendum (2026-09-03, item 24, D70(c)):** the launcher-level fix above
  distinguishes the three apps at the icon/label level, before install;
  item 24's version stamp (scan-pane footer + each log entry's `▸
  technical:` line) is the in-app complement — it disambiguates two
  installs of the SAME app (e.g. a debug build over a release build, or
  two builds from different commits) once already open, which no launcher
  label alone can do. Not itself a Q48 answer — a separate, narrower
  ENHANCEMENT — noted here since both address the same underlying
  "which app/build am I looking at" gap. Built, **DEVICE FAIL
  2026-09-03 PM** on builds `2525267`/`039fee7`/`7f25f40` — the stamp did not
  appear anywhere across the three builds tested that pass; root-caused
  same day to a layout bug (the pane-container `FrameLayout` consuming all
  weighted height and pushing `version_stamp_view` off-screen) and fixed in
  `fb0e75f`. **Device-confirmed 2026-09-03 PM, second pass (~15:02–15:15)**:
  the stamp is now visible on `fb0e75f`. Status: **FIXED-IN-fb0e75f,
  device-confirmed.** See
  `docs/logs/M2-DEVICE-SESSION-2026-09-03-PM-EVIDENCE.md` check 11 (FAIL)
  and section 13.2 (fix confirmed); see milestones.md §6.2 item 24,
  decisions.md D70(c).
  **Final status (D108, 2026-10-24): CLOSED (fixed; device-confirmed 2026-09-03 PM, including the version stamp on `fb0e75f`).**
- **Q49 (opened 2026-09-03, M3b-shaped)** — How an operator's issuer key actually
  gets pinned into a real tier-C deployment. Status: **CLOSED, by D89 and D94
  together (2026-09-07/08).** Q49 bundled two questions: (1) whether "preapproved
  list" (the owner's phrase, D73) means a HOLDER-side consent list or a
  VERIFIER-side request list — closed by **D89** (holder-side, matching D19's
  framing). (2) The UX/mechanism for getting a specific issuer key into a live
  operator's pinned list — closed by **D94**: the key list is a new
  `operator.json` knob (§6.7), validated and baked in at build time exactly like
  the existing verifier hostname allowlist, checked fully offline; D94 also
  corrects a routing misreading (the `av://` VIEW intent carries no key at all;
  the site signs its own challenge with its own key, D20; the app holds the
  pinned list and verifies offline; unpinned tier-C is refused, never
  downgraded, D19/D20). Both halves now resolved — **Q49 is fully CLOSED.** See
  milestones.md §6.4 item 3; decisions.md D19, D20, D73, D89, D94.
  **Final status (D108, 2026-10-24): CLOSED (D89, D94).**
- **Q50 (opened 2026-09-04, parked by D76)** — How does a Play-installed (non-sideloaded) user
  reach a verifier over HTTPS at all? M3's origin is fixed to `http://127.0.0.1:8787` via
  `adb reverse` against a sideloaded debug build (D76) precisely because the scanner's network
  security config refuses both plain LAN-IP `http://` and self-signed HTTPS, and zkagent hosting
  a real verifier itself was rejected (NO-GO #3/D3, one global zktag registry across testers).
  None of that gives a Play-distributed (non-debug, non-sideloaded) install any route to a
  verifier over HTTPS — that gap is not addressed by M3 at all. Status: **CLOSED by D83
  (moot)** — the Play developer account was closed for inactivity and the owner declined to
  open a new one; distribution moves to GitHub Releases + sideload, so there is no
  Play-installed owner's build for this question to apply to. Owner, 2026-09-04: "playstore
  will be clearer when we get to it." See milestones.md §6.3 item 7, §6.6, §6.8; decisions.md
  D76, D83.
  **Final status (D108, 2026-10-24): CLOSED (moot, D83).**
- **Q51 (opened 2026-09-05, owner)** — Distribution model: who signs the scanner? Owner,
  verbatim: "if everyone makes their own we just give them the adb, why do i have to create and
  share sensitive details with any adopter/operator?" and, clarifying intent: "operators need to
  change ui and do adjust knobs to their use, i want the copy to be from one place, modified
  knobs/ui to operator liking." Orchestrator's clarification the owner accepted: a keystore is
  never shared; only the certificate's SHA-256 fingerprint is published (public by design) so a
  verifier can pin it (FR10/D17). Two models: (A) source-only — operators fork ONE upstream (this
  repo), adjust UI/knobs, build and sign their own app with their own keystore, pin their own
  fingerprint in their own verifier; the owner's keystore signs only a showcase build; a Play
  listing is each operator's decision for their own package; (B) one canonical build — the owner
  signs, publishes one fingerprint, lists on Play; all operators pin it; end users install one
  app. The owner's stated intent points to (A). Consequences to record if (A) is confirmed: §6.6
  item 7 (D80 keystore) becomes a documented per-operator recipe (keytool, secrets/, env vars)
  plus the owner's own showcase keystore, not a shared secret; §6.6 items 1–4 (closed track, Play
  digest, review objections) apply to the owner's showcase listing only and are showcase evidence,
  not a requirement on operators; §6.5 S4's trust list is per operator (their own digest); Q50
  (Play HTTPS reachability) is then each operator's hosting question. Open decision points for the
  owner: (1) confirm (A); (2) whether "one upstream" implies an operator-config layer
  (knobs/UI as configuration rather than forking source) — that would be a new PRD item, not
  assumed here; (3) whether the owner runs the showcase Play listing at all under (A). Status:
  CLOSED (all three points). D81 (2026-09-05, owner): distribution model (A) confirmed and an
  operator-configuration layer confirmed as a deliverable (knob list and location PRD-gated, not
  decided — new PRD item required, NO-GO #10, see milestones.md §6.7). D82 (2026-09-06, owner)
  closed point (3): the owner builds and lists the showcase scanner on Google Play, closed
  testing track only — "do we need to build ours on play anyways as a way of showcasing without
  sideloading and knowing what stores ask for?", confirmed "#2 yes." §6.6 is now LIVE (ungated).
  **D82(a) superseded by D83** (2026-09-06): the Play developer account was closed for
  inactivity and the owner declined to open a new one; §6.6 items 1–6 are now PARKED, and
  distribution is GitHub Releases + sideload instead. Point (3) reverts to "no Play listing."
  See decisions.md D81, D82, D83; milestones.md §6.6, §6.7, §6.8.
  **Final status (D108, 2026-10-24): CLOSED (D81, D82; the Play part superseded by D83).**
- **Q52 (opened 2026-09-06, owner; DEFERRED by D82(c))** — Multi-threshold origins: one operator
  such as `state.gov` serves both seniors (65+) and minors (16+); D74 locks the first-seen
  threshold per origin and refuses any other. Resolution agreed with the owner: no new knob,
  D74 unchanged; the pattern is one question per hostname (`seniors.state.gov` /
  `minors.state.gov`, each listed separately in the exact-hostname allowlist, each locking its
  own threshold). Reasoning: D74 closes single-origin age-narrowing by repeated asks; two
  hostnames can only narrow the same person if the site links them across hostnames, which it
  already can via its own login and which any two colluding sites can do — D74 never claimed to
  stop collusion; each threshold costs the user a separate consented scan whose question line
  names the threshold, so exposure is bounded by the fixed list and equals that of unrelated
  sites; tier-A presentations stay unlinkable across hostnames and tier-B zktags are per-origin.
  Status: **DEFERRED**, not open for work — follows broad adoption, not scheduled. Owner,
  verbatim: "park the subdomain for now … i generally think it will be a minor case for the
  future … the whole thing is just av for now and nothing else besides, the absolute domain
  multiple ask follows broad adoption." **D84 (2026-09-06) note:** the mechanism already exists
  per D74 rule 2 (`ThresholdPolicy.NAMED_EXCEPTIONS`); exposed as a §6.7 knob
  (`multi_threshold_verifiers`), default empty; ruling unchanged. See decisions.md D82(c), D84;
  milestones.md §6.7.
  **Final status (D108, 2026-10-24): BACKLOG (deferred, D82(c); follows broad adoption). See backlog.md.**
