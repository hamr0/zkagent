# zkagent — Learnings (findings and lessons, consolidated)

This is the distilled layer: one entry per finding, each pointing to the evidence doc, log, or
session stash that proves it. It supersedes nothing — the evidence logs (`logs/M0-EVIDENCE.md`,
`logs/M1-POC-EVIDENCE.md`, `logs/M1-Q23-EVIDENCE.md`) remain the sources of measurement, and the
PRD (`product/zkagent-prd.md`) remains the source of decisions; read those for full detail.
Entries are grouped by topic and ordered oldest-first within each group. Where two sources
disagree and it isn't clear which is current, both are recorded with dates and flagged rather
than resolved here.

---

## 1. Hardware and documents

- **2026-07-30 — ACR122U recommended as the M0 NFC reader.** Cited a bol.com listing (~€37) whose
  selling point was "werkt voor DigiD." → source: `.claude/stash/m0-deplatform-hardware.md`.
  **(overturned 2026-07-31 by `.claude/stash/m0-hardware-android-first.md`** — the ACR122U cannot
  read ICAO e-documents: it caps APDUs at 261 bytes and does not propagate time-extension
  requests, and firmware below 2.06 has further ISO 14443 problems. The "werkt voor DigiD" signal
  was about Dutch ID cards, not ePassport secure messaging, and the specific bol listing no longer
  exists.)
- **2026-07-31 — the correct USB reader part, if one is ever needed, is the ACR1581U-C1** (ACS
  DualBoost III), supported by the `acsccid` PC/SC driver on Linux with extended APDUs up to 64kB
  — the exact constraint the ACR122U fails. → source: `.claude/stash/m0-hardware-android-first.md`.
- **2026-07-31 — the real Android device filter for this project is StrongBox + a live Play
  Integrity strong-integrity patch window + stock ROM, not "NFC + recent Android."** Pixel 3a/4a
  have Titan M but are dead for the project despite it, because their security-patch support ended
  in 2023 and strong integrity requires a patch from within the last 12 months. Custom ROMs
  (GrapheneOS included) fail Play Integrity by design. → source:
  `.claude/stash/m0-hardware-android-first.md`.
- **2026-08-03 — NFC Type A/B support is not the device discriminator; attestation quality is.**
  Type A/B is baseline on every phone-class NFC controller, and the extended-length-APDU variance
  that drives commercial ID-vendor device blocklists doesn't apply here because DG2 (the facial
  image) is never read — DG1 + SOD fit in short APDUs with chaining on any chipset. The
  discriminator that actually matters is attestation quality: a Pixel gives the reference Google
  implementation, guaranteed StrongBox, stock ROM, and the longest update runway. Framed as a
  debuggability choice, not a capability one — on a Pixel a failure means the project's own code is
  wrong; on an unpredictable OEM it might be the vendor's Keymaster instead. → source:
  `.claude/stash/prd-v14-disclosure-modes-and-docs-reorg.md`; `product/zkagent-prd.md` D2.
- **2026-08-29 — one JMRTD-based build read both a BAC-only US passport and a PACE-only NL
  identity card, with no per-country code, no configuration change, and no rebuild.** The access
  protocol was chosen entirely by what the chip advertised (`EF.CardAccess` present ⇒ PACE; absent
  ⇒ BAC). Observed on exactly two documents from two issuers — not coverage. → source:
  `logs/M0-EVIDENCE.md` (Finding 8).
- **2026-08-29 — the M0 spike's bundled masterlist is the BSI German Master List**: 588
  certificates, 116 issuing countries, 8 US CSCAs and 10 NL CSCAs present — retiring PRD risk #3
  ("issuing CSCA absent from the public list") for both of the owner's documents. → source:
  `logs/M0-EVIDENCE.md` (Findings 1–2).
- **2026-08-29 — the zktag derivation input is deterministic per document and distinct across
  documents.** All three candidate fields available on both documents (`document_number`,
  `optional_data`, `dg1_full`) were byte-identical across repeat scans of the same document, and no
  candidate collided between the US passport and the NL identity card held by the same person; the
  NL card's two additional chip-bound candidates (`dg14_ca_key`, `dg15_aa_key`) were likewise
  stable across scans, including runs that involved fresh per-session cryptographic challenges —
  this shows the derivation reads the chip's stored static key material, not session state. This is
  the weakest of the M0 results for the renewal question (D9): it demonstrates the read is
  deterministic and not accidentally salted, but says nothing about stability across a document
  renewal, which cannot be tested with unrenewed documents. → source: `logs/M0-EVIDENCE.md`
  (Findings 3, 10, 11).
- **2026-08-29 — chip-authenticity support is per document, not per product, and the split runs
  opposite to intuition.** The US passport's SOD covers only DG1/DG2/DG11/DG12, has no DG14 and no
  DG15 (`SELECT EF.DG15` → `6A82 FILE NOT FOUND`) — the chip offers no challenge-response at all,
  so a dumped/cloned data set would mint the identical zktag as the genuine document (mode-B
  clone-replayable). The NL identity card carries DG14 and DG15, and both Chip Authentication and
  Active Authentication succeeded — a clone would be detected on this document. The passport, which
  reads as the "stronger" document, is the one with weaker chip-cloning resistance. → source:
  `logs/M0-EVIDENCE.md` (Findings 4, 9).
- **2026-08-29 — a planted negative can silently exclude nothing and still report a pass.** M0's
  first CSCA-exclusion negative matched the literal string "United States" against certificate
  subject DNs; the real US CSCA's DN never contains that string, so zero certificates were excluded
  and passive authentication passed for the right reason on the wrong test — a guard that reports a
  pass while testing nothing. Fixed by matching the document's actual issuer DN and asserting the
  exclusion count is nonzero. → source: `logs/M0-EVIDENCE.md` (Finding 5). See also §5 (process).
- **2026-08-29 — measured clean-tap timing is ~2.5–3.3 s across four valid runs, with BAC setup
  dominating the variance** (363 ms to 5,537 ms across taps, roughly 15×, most likely antenna
  alignment and retries rather than computation); passive-auth verification and derivation
  afterward are cheap and stable (~400–800 ms). Four runs is not a distribution and supports no
  percentile claim. → source: `logs/M0-EVIDENCE.md` (Finding 7).
- **2026-08-30 — both real documents' document-signer (DS) certificates are RSA-2048/SHA-256, not
  ECDSA.** An earlier project survey (§6.5 below, written before this
  measurement) assumed the NL identity card's SOD was ECDSA-signed. **(overturned 2026-08-30 by
  `logs/M1-Q23-EVIDENCE.md` §2** — the NL card does carry an ECDSA P-256 key, but only for Active
  Authentication challenge-response, not for signing the SOD; both documents' actual SOD-signing
  keys are plain RSA-2048/SHA-256.)
- **2026-08-30 — both real CSCAs are RSA-4096, a different and larger key than either document's
  RSA-2048 DS certificate**, confirmed by extracting all 588 certificates from the BSI masterlist
  with a ~50-line stdlib-only DER walker and chaining both real DS certificates to their CSCA with
  `openssl verify`. → source: `logs/M1-Q23-EVIDENCE.md` §4 (Part 2).
- **2026-08-31 — NFC's own foreground-dispatch pause/resume of the still-visible activity fires on
  every tag, and a wipe-secrets-on-`onPause()` hook destroys the MRZ key before the read can use
  it.** The M2 scan spike's wipe was originally wired to `onPause()`; the owner's first tap failed
  because NFC's own pause/resume cycle cleared the MRZ before BAC/PACE establishment could use it.
  Fixed by moving the wipe to `onStop()`, which fires only on an actual screen-leave — confirmed
  working across every subsequent capture in the session. → source: `logs/M2-SCAN-EVIDENCE.md`
  Finding F2.
- **2026-08-31 — wiping the typed MRZ after every access attempt, not just successful ones, turns
  a mistyped key into a full retype.** A genuine PACE failure (`SW 0x6300`) with a BAC fallback
  failure (`SW 0x6985`) during the NL card capture cleared the fields per `wipeMrz()`'s "every
  attempt, success or failure" contract, forcing a full MRZ retype before the retry could succeed —
  no document data was exposed by the failure itself. Recommendation for M2 proper: keep the typed
  fields on an access failure, wipe only on a successful read or on `onStop()`. → source:
  `logs/M2-SCAN-EVIDENCE.md` Finding F3.
- **2026-08-31 — any screen that renders DG1 fields is a leak surface twice over: on-device
  display, and an agent's own accessibility-tree snapshot of that screen.** The M0-inherited
  `ResultActivity`, unchanged in the M2 scan spike, displays name/gender/country/nationality plus
  the auth verdicts; a mistimed relaunch during this session brought that screen back to the
  foreground and a scheduled accessibility snapshot captured partial field text into a local
  tool-call transcript before the operator caught it — contained (nothing entered the repo or git;
  no PII value is recorded even here), but the mechanism generalizes beyond the on-screen risk
  itself. M2 proper has no such screen: mode B shows only a verdict and the derived tag, mode A
  shows only a verdict, never DG1/personal fields on any screen, in any mode. → source:
  `logs/M2-SCAN-EVIDENCE.md` Finding F4.
- **2026-08-31 — UI state is not proof of scan-time state.** During a reinstall capture the mode
  radio was confirmed via accessibility snapshot to show Mode B checked immediately before the tap,
  with the owner not touching the control in between, yet the resulting report executed under Mode
  A (no `zktag_candidates` emitted). A code read found no listener-driven or cached mode field —
  `MainActivity.kt` reads `RadioGroup.checkedRadioButtonId` live, once, at tag-discovery time — so
  the mismatch is not explained by anything in the mode-handling code as written; root cause left
  open. Recommendation for M2 proper: capture the presentation mode from a single, tested source of
  truth read at the instant a chip session begins, itself covered by an instrumented test, before
  it gates any zktag-vs-no-zktag derivation decision. → source: `logs/M2-SCAN-EVIDENCE.md`
  Finding F5 (root cause open).

## 2. Attestation and vouching

- **2026-08-03 — hardware key attestation reopened as a live candidate rather than assuming Play
  Integrity.** Key attestation needs no runtime Google service call, no Play Console registration,
  no quota, and no Play Services on the device — it works on GrapheneOS, which supports it
  deliberately. Three things were flagged as needing verification before this becomes a decision:
  keybox extraction/circulation, per-OEM implementation quality, and revocation-list handling. →
  source: `.claude/stash/prd-v13-zktag-attestation-8een.md`; `product/zkagent-prd.md` Q14.
- **2026-08-29 — attestation chain parsing and verification is implementable stdlib-only.** A
  163-line DER walker with zero runtime dependencies fully decoded and verified all four real
  chains captured from the Pixel 6a (StrongBox ×2, TEE ×2), including the Android KeyDescription
  extension (security level, challenge, attestation application ID, verified-boot state, patch
  levels). → source: `logs/M1-POC-EVIDENCE.md` (Findings 1–2).
- **2026-08-29 — minting a fresh attestation key per request only rotates the leaf certificate; a
  stable, device-identifying certificate persists above it on both security levels.** On StrongBox
  it is a factory-provisioned keybox certificate, byte-identical across runs; on TEE it is a
  Remote-Key-Provisioning certificate Google reissues roughly every two weeks but which is
  identical across every presentation made within that window and identical to every site the
  phone talks to in that window. `verifiedBootKey`, `verifiedBootHash`, and OS/vendor/boot
  patch-level fields are likewise stable across runs and across the two security-level
  configurations. Diffing two fresh-key runs on the same configuration showed only the caller's own
  nonce (`attestationChallenge`) differed — everything else was identical. **This confirms PRD risk
  #8: the raw attestation chain is itself a device identifier, on both security levels, and tier A
  (anonymous, no-identifier-emitted) cannot carry it as-is.** → source: `logs/M1-POC-EVIDENCE.md`
  (Findings 4–6); summarized in §6.2/§6.4 below.
- **2026-08-29 — a verifier that checks only certificate signatures, not certificate dates, is a
  real, exploitable gap.** The initial verifier accepted a chain rooted in an already-expired
  Google root because `X509Certificate.verify()` checks signatures only. Fixed with an explicit
  per-certificate validity-date check. With the fix in place, the Pixel 6a's actual StrongBox chain
  fails verification today (its pinned root expired 2026-05-24) while its TEE chain passes; no
  single reference date validated all four captured chains at once. Whether Google's own production
  verifier enforces the same date check is unverified. → source: `logs/M1-POC-EVIDENCE.md`
  (Finding 7); open item Q14(d) in `product/zkagent-prd.md`.
- **2026-08-30 — Play Integrity, measured on the Pixel 6a across two simulated sites, carried no
  device-unique field in the decoded verdict.** Every field that was stable across runs and across
  sites was either app-build identity (package name, signing-certificate digest, version code) or a
  bounded enum (recognition/licensing verdicts); the per-device opt-in fields
  (`deviceAttributes`, `recentDeviceActivity`, `deviceRecall`, `environmentDetails`) were absent
  from every verdict. This meets the "nothing stable across sites" bar the project set (D22), but
  only for one device, one build, one session, and only for as long as those per-device opt-ins
  stay off. → source: `logs/M1-Q23-EVIDENCE.md` §3.
- **2026-08-30 — Play Integrity standard tokens can only be decoded by the app developer's own
  Google Cloud project (a service account inside that project); this is non-transferable per
  Google's ToS and quota is per app.** This means Play Integrity is not borrowable by third-party
  adopters — only the app's own developer can ever decode a check. Apple's App Attest, by contrast,
  is borrowable: it is verified against Apple's own public root, and only the optional fraud-check
  receipt needs the developer's key (unverified against real data — App Attest itself is untested
  in this project). → source: `.claude/stash/m1-d24-evidence-slot-and-b1.md`;
  `product/zkagent-prd.md` D24.
- **2026-08-30 — no attestation route on Android today is simultaneously publicly verifiable and
  free of a device-unique field.** Key attestation is public but device-identifying (§ above);
  Play Integrity is unlinkable but private to the app developer. This finding is what drove the
  design decision to make attestation a pluggable "evidence slot" rather than commit to one
  voucher (D24). → source: `.claude/stash/m1-d24-evidence-slot-and-b1.md`;
  `product/zkagent-prd.md` D23/D24.

## 3. ZK engines and circuits

- **2026-08-30 — every passport-capable ZK engine or circuit set surveyed is pre-1.0/beta,
  unaudited, or both.** Noir is at v1.0.0-beta.26 with near-daily nightlies and no stable 1.0 tag;
  Barretenberg's standalone repo is archived and folded into the Aztec monorepo. No standalone
  public audit of UltraHonk's core proving system was found — only a sub-primitive (Bigfield,
  audited by zkSecurity/Zellic/Spearbit, 2024-12) and an unrelated circuit library (ZK Email,
  audited by Consensys Diligence). → source: `product/zk-due-diligence.md` §A; summarized in
  §4 below (D23 entry).
- **2026-08-30 — Barretenberg had a disclosed, critical, forged-proof soundness bug in March 2026
  (fixed in v4.1.2) and a second critical bug in the v5-alpha prover in July 2026** — a live,
  recent, recurring pattern rather than settled history. → source: `product/zk-due-diligence.md`
  §A.
- **2026-08-30 — zkPassport (Noir circuits, Apache-2.0) is the closest working precedent for this
  exact use case** (NFC passport → Noir → on-device UltraHonk proving), with real usage (17,000+
  participants in a token-sale nationality check; a conference-ticket-discount verifier across 11
  Latin American countries), but has no completed published third-party audit, several plain bug
  fixes including in its aggregation "outer circuit," and a mobile app that is not open-sourced.
  It was acquired by Aztec Labs on 2026-05-27. Its optional OPRF-based nullifier has undisclosed
  operators. → source: `product/zk-due-diligence.md` §B.
- **2026-08-30 — Rarimo's Circom circuits are the only ones in this survey with a completed
  third-party audit (Halborn, 2024-02/03; zero Critical/High/Medium findings), but substantial code
  has changed since**, including an under-constrained-circuit fix (2025-01), a commit that
  re-enabled a check that had been disabled in shipped code (2026-02), and an RSA/SHA-1
  vulnerability fix (2026-07) — all outside the audited scope. Rarimo's trust model also leans on
  an on-chain identity-state Merkle tree, which conflicts with a no-blockchain design goal unless
  run in an undocumented standalone mode. → source: `product/zk-due-diligence.md` §C.
- **2026-08-30 — Google's longfellow-zk has no ICAO-passport support at all: no RSA circuit
  anywhere (C++ or Rust), no ASN.1/DER parser, no big-integer modular exponentiation, and the JWT
  path is ES256-only.** It is scoped to mDL/JWT/VC formats. Adapting it to passports would mean
  writing RSA-2048/4096 verification and DER parsing from scratch in hand-written C++ against its
  sumcheck framework — the largest layer-2 project on the table, not a shortcut. Confirmed by a
  source-tree read of 2,689 paths. Reusable: its SHA-256 circuit and its authoring framework; also
  a useful pattern worth copying regardless of engine — it keeps the issuer public key *outside*
  the circuit as a public input, so DSC→masterlist trust is checked by plain code, not proven
  in-circuit. → source: `product/zk-due-diligence.md` §D; §6.11 below.
- **2026-08-30 — no non-crypto organization has shipped a production ZK-passport/ID system.** The
  EU's own Age Verification Blueprint "mini-wallet" has its ZK features live only in a closed
  demo/prototype build, not the shipped app, and a researcher reportedly bypassed the prototype's
  over-18 check via a replayed stale token. The EU's own architecture framework scopes ZK proof
  generation to already-issued wallet credentials (mdoc/SD-JWT VC), not raw ICAO 9303 chip data —
  there is no regulatory-aligned template for what this project proposes. → source:
  `product/zk-due-diligence.md` §F.
- **2026-08-30 — a real ZK proof over the passport was built and verified end to end on desktop for
  both of the owner's real documents**, using zkPassport's Noir/Barretenberg circuits: DSC→CSCA
  (RSA-4096), SOD→DSC (RSA-2048), DG1↔SOD integrity, and an age-threshold claim all proved and
  verified true independently, with matching commitments across every stage boundary (not yet via
  true recursive composition). Totals per document, circuits already compiled: ~16.1 s wall time,
  ~546 MB peak RSS, 59,072 bytes to the verifier across four independent UltraHonk proofs, fully
  offline (no chain, no RPC). Two proofs generated from identical inputs were byte-different from
  each other (per-proof ZK blinding) while their public-input commitment was identical both times.
  A scoped nullifier was stable within one site/document, differed across sites, and differed
  across documents — but was run with `nullifier_secret = 0` (the repo's documented
  "non-blinded" convention), not the real OPRF-blinded path, which needs a live threshold-network
  dependency that was not evaluated. → source: `logs/M1-Q23-EVIDENCE.md` §4; pre-registered success
  criteria and reading against them in §5.
- **2026-08-30 — a planted one-byte flip in DG1 did not fail the signature-check circuit in
  isolation, because that circuit never constrains DG1 against anything** — it is folded
  unchecked into an output commitment for a separate circuit to check. Adding the
  `data-check/integrity` circuit into the pipeline (Part 2 of the same spike) closed this gap: the
  identical DG1 tamper then failed outright at witness generation. This is a finding about which
  circuit in a multi-stage composition actually binds which data, not a missed negative once the
  full pipeline is assembled. → source: `logs/M1-Q23-EVIDENCE.md` §4; see also §5 (process).
- **2026-08-30 — writing custom Noir circuits instead of adopting an existing suite was estimated
  at roughly 6–12 engineer-weeks (an informed, unsourced estimate, not measured) to reach a working
  4-circuit passport composition**, and would rely on an explicitly "experimental... no warranties"
  community RSA library with weak-to-absent SHA-1 support (needed for legacy passports) — versus
  days-to-low-single-digit-weeks to integrate zkPassport's existing suite, at the cost of
  inheriting zkPassport's own audit gap and Aztec-oriented architectural assumptions. → source:
  `product/zk-due-diligence.md` §E.
- **2026-08-30 — funding and backing figures across this ecosystem are thin, self-reported, and
  sometimes inconsistent across sources** (e.g. Aztec Labs' total raise is cited as "~$119–180M"
  depending on the source; pre-acquisition zkPassport funding figures were found muddled with a
  differently-named project in aggregator data). Flagged as unverified rather than asserted. →
  source: `product/zk-due-diligence.md` §A, §B (plain-language risk summary, item 14).
- **Decision taken on this evidence (D23, 2026-08-30, later superseded in part by D24 — see §4):**
  v1 stays voucher-grade (Play Integrity); ZK-over-passport becomes "Track Z," a named second track
  gated on five written conditions (stable audited engine release; an independent audit of the
  exact circuits used; a measured phone-proving time under a PRD-set UX ceiling; a chain-free
  nullifier with known operators or none; an open-source on-device prover) — none met yet. → source:
  `product/zkagent-prd.md` D23; `product/zk-due-diligence.md` (plain-language risk summary).
- **2026-08-30 (B3 checkpoint) — zkPassport age circuit cannot be nonce-bound while keeping a
  stable nullifier.**
  - The age circuit (`vendor/zkpassport-circuits/.../compare/age/standard/src/main.nr`) exposes
    exactly four public inputs — `comm_in`, `current_date`, `service_scope`, `service_subscope` —
    and returns `param_commitment`, `nullifier_type`, `nullifier`, `oprf_pk_hash`.
  - The three earlier stages expose only `comm_in`/`comm_out`. There is no nonce input anywhere in
    the composition.
  - `service_subscope` is the only free public slot and it feeds `nullify()`: carry a nonce there
    and the nullifier changes per request (no stable zktag); leave it fixed and the proof is
    replayable within the scope until `current_date` rolls over.
  - Threshold appears only as `Poseidon2(min_age, max_age)` (`param_commitment`); the scope
    string→Field mapping lives in `@zkpassport/utils`.
  - Consequence: with this circuit family a proof can be challenge-bound OR a stable pseudonym, not
    both.
  - Measured: `bb verify` 5.0.0 on the real NL composition ≈0.035 s total (8–11 ms per stage); a
    flipped proof byte is rejected at deserialisation ("bad proof serde"), a flipped public-input
    byte at the cryptographic step.
  - Status: `zk-passport/1` plug NOT written pending owner decision (proposed: ship it tier-A-only
    with nonce-hash in subscope; defer tier B/C ZK to Track Z as Q26). → source: `packages/chiproof`
    (B3 coder report 2026-08-30); `spikes/m1-zk/README.md` "Full composition results".

- **2026-08-30 — `@zkpassport/utils` declares no license.** The npm package (0.37.4) has no
  `license` field in its `package.json` and ships no `LICENSE` file; the `zkpassport-packages`
  monorepo it comes from has no `LICENSE` at its root or under `packages/zkpassport-utils` either
  (GitHub API `license` field null; verified 2026-08-30). The sibling `zkPassport circuits` repo
  IS Apache-2.0 — this is specific to the `utils` package, not the project generally. Consequence:
  `chiproof` takes no code from `@zkpassport/utils`; the `service_scope`/`service_subscope` field
  rule is defined by this project and checked for interoperability against real proof public
  inputs, not copied (`NOTICE` records this). Open item: raise upstream; any future dependency on
  that package is blocked until it is licensed. → source: `packages/chiproof/NOTICE`;
  `packages/chiproof/src/plugs/zk-passport.js` header comment.

- **2026-08-30 — a ZK proof's circuit variant is a cross-site bucket that no wire-format change
  can hide.** M1b (`logs/M1B-EVIDENCE.md` §4–§5) found every salted commitment, nullifier and
  subscope fresh per `zk-passport/1` presentation, but `dsc.vk_sha256`/`id_data.vk_sha256` — the
  DSC circuit's TBS-length/key-size/hash class — are stable per document and differ NL vs US.
  Leak-closure spikes confirmed dropping the field doesn't help: with more than one pinned key the
  plug fails closed (`zk_unknown_circuit`) rather than hiding the class, and a verifier trying
  pinned keys against the proof recovers it deterministically in ~50–90 ms; the raw proof bytes
  themselves carried no separate NL/US fingerprint. → source: `logs/M1B-EVIDENCE.md`;
  `product/zkagent-prd.md` D26.

- **2026-08-31 — the EU itself has published an issuer-free ZK-over-ICAO age-proof library,
  architecturally the same shape as zkagent's own Track Z thesis, but hardwired to one RSA
  parameter set.** `av-lib-android-zkp-age-icao` (commit `5f1d806`) runs a Noir/Barretenberg
  circuit directly over a chip's DG1+SOD+COM triple — no issuer round-trip, no online masterlist
  call visible in its public API — emitting a bespoke `{data:{age_over_18,...}, proof:...}` JSON.
  It is fixed to RSA-4096 CSC / RSA-3072 DSC / RSA PKCS#1v1.5-SHA-256 DSC-sig /
  RSASSA-PSS-SHA-256 SOD-sig / a fixed 1600-byte TBSCertificate — a different, narrower set than
  either of this project's own two real documents, both RSA-2048/SHA-256 DSC (§1 above,
  `logs/M1-Q23-EVIDENCE.md` §2), so this specific library cannot currently process either
  document in hand. Generalizes past this one library: a ZK circuit over passport signatures is
  hardwired to one signature parameter combination, and supporting real-world document diversity
  means a circuit per combination — the same structural fact behind D26's disclosed `vk_sha256`
  circuit-class bucket, immediately above. "Does it support my document?" is therefore a
  per-circuit empirical question, never a general claim. → source: `logs/M2-CONFORMANCE.md`
  SETUP, Finding 6. See also §4 below and §6.11 (cross-referenced there).

## 4. Protocol and design

- **2026-08-03 — RFC 9421 was cited five times in the repo as an assumption and had never been
  checked against the actual spec; four problems surfaced on inspection.** (1) RFC 9421 §2.3 already
  defines a `tag` signature parameter with a different meaning (application-specific signature
  labelling) than the project's own pseudonymous identifier — same word, same protocol context,
  opposite meanings, and Web Bot Auth (which the project rides alongside) uses `tag` in the RFC
  sense; fixed by renaming the project's field to `zktag` everywhere except the RFC's own parameter
  and Apple's unrelated "NFC tag-reading entitlement" term, which must stay as `tag`. (2) RFC 9421
  defines no delegation-chain mechanism at all and explicitly puts key trust out of scope — there
  is no chain for a conformant verifier to check; any delegation transport must be defined by this
  project. (3) Two expiries can conflict (the RFC's own signature `expires` vs. a delegation
  cert's `expiry`) with no precedence rule in the spec; resolved by making the verifier enforce
  whichever is earlier. (4) The RFC's own `nonce` parameter and the project's challenge-nonce are
  different layers that happen to share a word. → source:
  `.claude/stash/prd-v13-zktag-attestation-8een.md`; `product/zkagent-prd.md` FR8.
- **2026-08-25 — chip cloning (Q18) was discovered while writing the design companion document,
  not during PRD review, and it dismantles D10's stated rationale for a 30-day secret-expiry
  ceiling.** Verifying the SOD is passive authentication only — it proves the issuing government
  signed the bytes, not that the chip presenting them is the original chip. The BAC/PACE unlock key
  lives inside the DG1 dump itself, so anyone holding a one-time-captured dump can replay it from an
  emulator and produce a byte-identical zktag to the real document, indefinitely — defeating D10's
  assumption that a expiry ceiling forces costly physical re-scanning. The defence is Active
  Authentication or Chip Authentication (the chip proving it holds a key it never releases), but AA
  is optional in ICAO 9303 and some issuers omit it deliberately. Mode A (anonymous) is unaffected —
  there is no identifier to impersonate. → source:
  `.claude/stash/prd-v14-disclosure-modes-and-docs-reorg.md`; `product/zkagent-prd.md` Q18.
  **Confirmed on real documents 2026-08-29**: the US passport has no AA/CA at all (clone-replayable);
  the NL identity card has both (clone-detectable) — see §1.
- **2026-08-30 — tier A's same-site unlinkability promise was relaxed from a requirement to a
  non-goal (D22); the operative requirement became "nothing in the payload is stable across
  sites."** Reasoning: a site a holder returns to already links visits through cookies, IP, and
  browser fingerprint; promising that the *same* site specifically cannot recognise a return costs
  real cryptography (fresh ZK proofs per presentation) for little marginal benefit, since IP/cookie
  linking is probabilistic and clearable while a hardware-rooted identifier is certain and
  permanent — a genuine profiling risk across sites, not within one. → source:
  §6.3 below; `product/zkagent-prd.md` D22.
- **2026-08-30 — the original claim that issuer-free ZK derivation over a passport is "unpublished
  in the literature" was checked and withdrawn.** zkPassport, Rarimo, and Self already publish and
  ship issuer-free ZK proofs over passport SODs; what remains novel for this project is only the
  combination with its own disclosure/tier model, not the underlying technique. → source:
  `product/zkagent-prd.md` risk register item 1 (§7).
- **2026-08-30 — Play Integrity's non-borrowability (§2) invalidated the "v1 attestation is
  voucher-grade" decision (D23) on the specific claim that Play Integrity is a shared voucher; D24
  superseded it the same day** by reshaping the core around a generic, pluggable "evidence slot"
  (`{spec, tier, claim, challenge, zktag?, evidence[]}`), where each evidence type is a plug with a
  fixed contract (`verifyEvidence(item, ctx) → {ok, valid, reason}`, never throws, binds the nonce
  + claim + scope or is refused at registration) and the adopter — not the core — decides which
  evidence types to require or accept. v1 ships with the slot legitimately empty ("bare mode,"
  captcha-grade, stated as such). This is recorded as a decision correction, not a contradiction
  between still-standing sources: D23's Play Integrity measurements (§2 above) remain valid
  evidence, but D23's framing of it as *the* v1 voucher does not stand. → source:
  `.claude/stash/m1-d24-evidence-slot-and-b1.md`; `product/zkagent-prd.md` D23, D24.
- **2026-08-30 — masterlist/SOD verification was moved on-device (phone), not to the verifier**,
  because the SOD carries hashes of the holder's own data groups and functions as a fingerprint of
  that person's data — letting it reach the verifier at all would be a tier-A leak. The verifier
  instead trusts the attested/evidenced client (FR10) to have done that check correctly. → source:
  `.claude/stash/m1-poc-attestation-q23.md`; §6.2 below.
- **2026-08-30 — Mode B flow, bare vs with evidence — what attestation adds and where.**
  - Setup: site holds `challengeSecret` (env), a nonce store, `threshold: 18`, `evidence.require: []`
    (bare) or `["<type>/1"]`.
  - Bare mode: (1) site→phone challenge `{nonce, tier:"B", threshold:18, expires_at}`; (2) phone
    NFC-reads the passport and verifies the chip signature (SOD→CSCA) on the phone; (3) phone
    computes age≥18 and derives `zktag = H(passport-stable-bits, site-scope)`; (4) phone→site
    `{claim:{over_threshold:true, threshold:18}, challenge, zktag}`; (5) site checks spec, challenge
    HMAC, expiry, nonce spent once, tier ≤ requested, threshold triple-equality (claim = challenge =
    config), zktag present; (6) `allowed:true`, site remembers zktag; (7) next visit same passport →
    same zktag, other sites see a different one. What the site trusts in step 5: that steps 2–3
    happened — it has no evidence; anyone can send step 4 for a fresh challenge. Captcha-grade, by
    design (D24).
  - With evidence: steps 1–4 identical plus `evidence:[{type, version, data}]` whose data binds
    nonce + claim + scope; step 5 then runs each required plug: `ok:false` → `allowed:null`;
    `valid:false` → `allowed:false`; all valid → `allowed:true`.
  - Table — type / proves / where trust moves: none / nothing / the sender; `key-attestation/1` /
    the answer came from this signed app on this locked device / app build + Google hardware root,
    device-linkable so tiers B/C only; `signed-receipt/1` / a party the site trusts signed
    hash(claim)‖nonce‖scope (e.g. adopter's own Play Integrity decode) / that signer; `zk-passport/1`
    / the math: a gov-signed passport with age≥18 exists for this nonce / nobody — offline-verifiable,
    trust only circuit+engine (Track Z).
  - Where attestation is not: never in zktag derivation (custody, not identity — MEMORY rule); never
    in tier A if it carries a device identifier.
  - One line: bare trusts the sender; attestation trusts the signer; ZK trusts the math; the
    nonce/zktag/claim shapes are identical — only `evidence[]` differs.
  → source: `docs/product/m1-verifier-core-spec.md` §4; PRD D24.

- **2026-08-31 — the EU runs two disconnected age-verification wire shapes, and zkagent as built
  interoperates with neither as a credential.** (a) The mandated shape: OpenID4VP + DCQL selecting
  an `mso_mdoc` credential, doctype `eu.europa.ec.av.1`, boolean claim `age_over_18` — verified
  end to end against a really-running reference verifier backend (`av-srv-verifier-endpoint` @
  `787089b`, the EU org's own published Docker image, pulled and booted locally). (b) The
  issuer-free ZK shape described in §3 above (`av-lib-android-zkp-age-icao`) — grepping that
  repo's Kotlin sources and markdown for `openid4vp`/`dcql`/`mso_mdoc` returned zero matches, and
  no adapter between the two was found anywhere in the EU's published repos. **Interop verdict,
  stated plainly, two independent blockers**: **credential format** — a wallet holding no
  `mso_mdoc` of that doctype has nothing to select, and minting a genuine one needs an issuer
  signature, which NO-GO #3 forbids (this reconfirms §6.11's existing "no" on passport→mdoc
  conversion, below); and **client identification** — the reference verifier's real default is a
  `PreRegistered`/x509 `VerifierId` with a hardcoded SIOPv2 `aud`
  (`"https://self-issued.me/v2"`), not the `client_id_scheme: redirect_uri` that the Blueprint's
  own docs (and this project's own earlier spike, `logs/M2-CAPTURE.md` Finding 1) assumed was the
  mechanism — `redirect_uri` client identification exists in the real source only as an
  off-by-default legacy-wallet compatibility flag, not the default `VerifierId` shape. → source:
  `logs/M2-CONFORMANCE.md` SETUP, Findings 1, 2, 6, 7.
- **2026-08-31 — live UK age gates (Reddit/Discord-class) are not credential flows at all.** They
  are IDV-vendor iframes/redirects (Persona, k-ID) exchanging opaque inquiry ids via a JS
  `onComplete` callback plus a server-side webhook — nothing wallet-shaped for a zkagent-style
  scanner app to answer. The deployed reality and the standardised EU wallet architecture are two
  different worlds today. → source: `logs/M2-CAPTURE.md` Finding 3.
- **2026-08-31 — the EU ZK library (§3 above) is NO-GO for adoption as-is; licence was not the
  problem, three other axes were.** Licence is clean: Apache-2.0 at the repo root, matching
  `NOTICE.txt` and per-file SPDX headers, no non-commercial clause found anywhere in the target
  repo or its bundled JS/SRS assets; one transitive LGPL-3.0 dependency (JMRTD 0.8.3, a *different*
  JMRTD version than this project's own vetted 0.7.18 fork) needs ordinary LGPL compliance
  handling, not a blocker. The decisive blocker is the RSA-4096-CSC/RSA-3072-DSC constraint (§3)
  confirmed as a **hard-compiled ACIR ABI array-length limit** (`csc_pubkey: [u8;512]`,
  `dsc_pubkey: [u8;384]`, `tbs_certificate: [u8;1600]`), not a config flag — debug-symbol paths
  name a `two_circuits`/`epassport_rsa4096_3072_pss` workspace, implying sibling parameter sets
  exist in the author's build tree but were never published. Two further, independently sufficient
  blockers: **no nonce, challenge, or nullifier field exists anywhere in the 26-parameter ABI**
  (worse than `zk-passport/1`, which at least carries a challenge through `service_subscope`,
  D25) — and `current_date` is hardcoded inside the library at hour granularity, not
  caller-adjustable, a linkability regression against this project's own D28 day-granularity
  choice; and **no verification path exists outside Android** — no exported VK, no Node binding,
  no documented wire contract for the "server-side verifier" the library's own docstring promises,
  on an untested Noir `1.0.0-beta.21` vs. `bb 5.0.0` pairing (the version `zk-passport/1` already
  pins). One genuine, unverified-by-testing advantage: `country`/`certificate_tags`/
  `certificate_type` are private ABI fields, so — unlike `zk-passport/1` — this circuit does not
  structurally leak the issuer/circuit-class bucket D26 had to disclose; no leak-closure spike (the
  `M1B-EVIDENCE.md` method) was run to confirm it holds. Maturity: `0.0.3-SNAPSHOT`, no tags, 6
  commits, ~3 months stale, its one instrumented test file sits behind a CI job disabled with
  `if: false`, and still no OpenID4VP/DCQL adapter has appeared. GO-IF, recorded as actionable:
  a published circuit variant covering real-world DSC key sizes, an exported VK with a documented
  off-device verification contract, and any nonce-carrying mechanism. **Durable lesson**: an
  issuer-free ZK age library existing is not the same as one being adoptable — parameter coverage,
  nonce binding, and an off-device verification contract are the three things that actually decide
  it, independent of and orthogonal to licence, and this library is clean on licence while failing
  all three. → source: `logs/M2-EU-ZKP-SPIKE.md` SETUP, §1 (licence), §2 (RSA-parameter limit),
  §3 (no nonce), §4 (no off-device verifier), §5 (private fields), §6 (maturity), RECOMMENDATION.
- **2026-08-31 — measured: mode-B zktag derivation survives app uninstall/reinstall on both real
  documents, and reads stored chip key material, not session state.** `document_number` /
  `optional_data` / `dg1_full` were byte-identical pre- and post-reinstall for both documents; the
  NL card's chip-bound candidates (`dg14_ca_key`, `dg15_aa_key`) — each drawn from a Chip/Active
  Authentication exchange that runs fresh per-session cryptographic challenges — were likewise
  byte-identical across the reinstall, showing the derivation reads DG14/DG15's stored static
  public keys, not that session's ephemeral randomness. This depends on nothing device-held,
  exactly as FR11 specifies. → source: `logs/M2-SCAN-EVIDENCE.md` TEST 1; `product/zkagent-prd.md`
  FR11.
- **2026-08-31 — measured: the BSI masterlist parses 588/588 certificates in 585 ms on the Pixel
  6a, and the PRD's masterlist two-bucket rule is now written down, matching what M0 and this spike
  both already implement.** A truncated/half-loaded masterlist (integrity failure) yields
  `ok:false` (could not check); a well-formed masterlist that simply lacks the issuing CSCA yields
  `ok:true, allowed:false` (a real no, issuer-untrusted) — the CSCA-removed negative excluded a
  nonzero count on both documents (8 US, 2 NL). M0's PRD row wording ("must yield `ok:false`" for
  the CSCA-removed case) is marked superseded. → source: `logs/M2-SCAN-EVIDENCE.md` TEST 2;
  `product/zkagent-prd.md` §6 M0/M2 rows.
- **2026-08-31 — D9 × D29 interaction: two independently-sound decisions combined into an
  unwritten consequence.** D9 (mode-B derivation field = `document_number`) and D29 (mode B
  accepts non-chip-auth documents) together mean mode-B uniqueness and blocking are forgeable
  wherever `chip_auth: false` — `document_number` lives in DG1, which a cloned chip can replay
  verbatim with no challenge-response, minting the identical zktag as the genuine holder and
  inheriting their pseudonymous reputation (or evading a block placed on them). The guarantee holds
  only where `chip_auth: true` (D21); FR11 now states this as an explicit conditional rather than
  leaving it implicit. Lesson: decisions that are each sound in isolation can combine into a
  consequence neither decision's own review surfaced — check pairs, not just each decision alone.
  → source: `product/zkagent-prd.md` v1.16 preamble, D9, D29, FR11.
- **2026-08-31 — a fix existing in a library is not the same as the default caller using it.** The
  `sig-ed25519/1` evidence plug's settled byte layout binds claim-hash + nonce-bytes + scope +
  zktag, but code review found the spike's own plug still lacked the zktag binding after chiproof
  0.3.0 had already shipped the capability to add it (fixed; review closed 17/17 findings, one
  High). The shipped `signed-receipt/1` plug (owner ruling 2026-08-30) is the reference for
  nonce-bytes encoding (base64url-decoded, not utf8) — chiproof's own test fixture for that plug
  still uses `utf8(nonce)`, flagged for cleanup rather than a spec deviation. → source:
  `product/zkagent-prd.md` D30, FR12.
- **2026-08-31 — real-device handoff: the EU-Blueprint-shaped `av://` app-link roundtrip works on
  the Pixel 6a end to end with an ES256-signed request object; the DC API is live on Chrome 151
  (its own system consent gate renders) but ends in `NotAllowedError` with no registered Credential
  Manager provider.** Interop with real EU wallets remains impossible regardless, for the separate
  reasons already recorded under the EU wire-shape findings above (credential format, client
  identification) — cross-referenced here as the on-device half of the same story, not a new
  blocker. → source: `logs/M2-DEVICE-EVIDENCE.md` Findings 3–4; `logs/M2-CONFORMANCE.md` SETUP.

## 5. Process lessons

- **2026-07-30/31 — verify a hardware claim against the actual use case, not against a loosely
  related compatibility signal.** The ACR122U recommendation was accepted on a "works for DigiD"
  listing note — a claim about Dutch ID cards, not ePassport secure messaging — and was falsified
  the very next day against the real constraint (APDU size limits, protocol type). → source:
  `.claude/stash/m0-hardware-android-first.md`; `.claude/remember/MEMORY.md` episode
  2026-07-30.
- **2026-08-07/25 — design-only sessions accumulate unresolved surface faster than they resolve
  it.** By 2026-08-03 the PRD was flagged as becoming the "collector" its own scope-gate NO-GO
  warns against, for a project with zero lines of code. Q18 (chip cloning), which undermines an
  already-adopted decision's entire rationale, then sat undetected through four further PRD
  revisions and only surfaced when the read path had to be written out concretely for a companion
  doc — evidence that more design was producing more surface, not more certainty. This is the
  stated reason the project's agent-delegation layer (rung 2) was frozen until the age-verification
  leg (rung 1) ships real evidence. → source: `.claude/stash/prd-v13-zktag-attestation-8een.md`;
  `.claude/stash/prd-v14-disclosure-modes-and-docs-reorg.md`; `product/zkagent-prd.md` D18;
  `.claude/remember/MEMORY.md` episodes 2026-08-03, 2026-08-25.
- **2026-08-29 — a test that can silently pass by testing nothing is worse than no test.** M0's
  first planted negative matched a substring that never appeared in the real certificate DN,
  excluded zero certificates, and reported a pass for the right document via the wrong mechanism —
  a "plausible pass" indistinguishable from a real one until inspected. The fix generalizes: every
  negative test must assert that its precondition actually took effect (e.g. an exclusion count
  greater than zero), not just that the overall check still passed. → source: `logs/M0-EVIDENCE.md`
  (Finding 5).
- **2026-08-29 — real third-party code conflated "no" with "couldn't check."** The upstream
  passport-reader library this project forked wrapped digest comparison, masterlist load, path
  validation, and signature verification in a single catch block that set one boolean false either
  way — collapsing "forged" and "undecidable" into the same value, which is exactly the failure
  class this project's own `ok`/`allowed` invariant exists to forbid. Carried forward as a concrete
  test case for the verifier's own test suite. → source: `logs/M0-EVIDENCE.md` (Finding 6).
- **2026-08-29/30 — real captures and real documents reveal bugs that synthetic fixtures don't.**
  Two independent parser bugs in the Play Integrity decoder (an unstripped logcat line prefix; an
  unhandled `tokenPayloadExternal` wrapper in Google's actual response shape) surfaced only once a
  real device capture and a real Google API response existed, invisible against hand-made JSON
  fixtures. Separately, the DG1-tampering gap in the ZK signature-check circuit (§3) was only
  discovered by running a planted negative against a real DG1/SOD pair — a synthetic fixture
  authored to "look like" a passport would not have forced the question of which circuit actually
  binds DG1 to its signed hash. → source: `logs/M1-Q23-EVIDENCE.md` §7 (Method findings).
  Generalizes the project standard "the test must be able to fail" (`.claude/remember/AGENT_RULES.md`)
  from a design rule into a repeated, independently-observed result.
  **A privacy defect was similarly found only by observation, not design review**: the M0 spike
  app's MRZ input fields were pre-filled on launch because the upstream library persists them in
  preferences, contradicting the project's own stated "typed by hand, never stored" rule — harmless
  on the owner's own device, flagged not to survive into the M2 rewrite. → source:
  `logs/M1-POC-EVIDENCE.md` (Finding 8).
- **2026-08-29/30 — orchestration process rule adopted mid-project: the main/orchestrator session
  never codes or edits docs itself; a fresh coding agent always does, and must escalate every
  design decision back to the orchestrator rather than deciding alone**, because fresh agents carry
  no conversation context and a wrong default choice made silently is expensive to unwind later. →
  source: `.claude/stash/m1-poc-attestation-q23.md`;
  `~/.claude/projects/-home-hamr-PycharmProjects-zkagent/memory/orchestrator-only-sonnet-codes.md`.
- **Standing environment note, repeatedly relevant to this project's own data analysis:** `grep` on
  the owner's host is ugrep 7.5.0, which fails slowly (and looks like a hang) on bounded-repetition
  regexes that GNU grep accepts; structured data (JSON captures, ASN.1 dumps) should be parsed with
  `python3` instead. → source: `.claude/remember/MEMORY.md` (Facts); recurring across every stash
  file's Gotchas section.
- **2026-08-31 — omitting `model` on an Agent spawn silently inherits the parent model, not a safe
  default.** Several coding agents spawned this session without an explicit `model` ran on Fable
  instead of Sonnet, exhausting credits mid-run before it was caught; a forking skill (e.g.
  `/code-review`) runs on the parent model by construction and needs no override. Rule adopted:
  every `Agent` spawn passes `model: sonnet` explicitly, and review work is delegated to a Sonnet
  `quality-assurance` agent, never the bare review skill. → source: this session's own record,
  2026-08-31; `~/.claude/projects/-home-hamr-PycharmProjects-zkagent/memory/orchestrator-only-sonnet-codes.md`.
- **2026-08-31 — spawning a replacement agent without checking for a still-running one put two
  agents on the same physical phone at once.** The orchestrator briefly believed an in-progress
  device-handoff agent had died and spawned a duplicate; both drove the same device for a short
  window (the contention visible in `M2-DEVICE-EVIDENCE.md` screenshots 06–10) before the collision
  was identified and the duplicate's phone access was killed. → source: `logs/M2-DEVICE-EVIDENCE.md`
  Method section ("Provenance / contention"); `.claude/remember/MEMORY.md` (Facts, "`/clear` keeps
  agents running").
- **2026-08-31 — writing artifacts to disk as the run proceeds, not only at the end, made two
  mid-run agent kills cost only the unfinished tail.** The device-handoff evidence doc was
  reconstructed entirely from screenshots and source files already saved to disk before the
  contention above was caught, rather than losing the whole run. → source: `logs/M2-DEVICE-EVIDENCE.md`
  Method section.
- **2026-08-31 — self-consistency is not conformance.** 15/15 green tests in the spike's own test
  suite proved only that the spike's two ends agree with each other; its DCQL block, diffed against
  the real EU reference verifier's own request-object builder, was confirmed decorative — accepted
  unvalidated at init time, enforcement pushed to a wallet's credential-selection step no
  `zkagent`-only wallet can pass. Only running the third party's actual code (the EU org's own
  Docker image, and a source read of the ZK library's Kotlin) turned "we haven't checked" into
  "checked, does not interop, for two independent reasons." → source: `logs/M2-CONFORMANCE.md`
  Finding 1, §"What this establishes" (15/15 green tests).
- **2026-09-02 — ~4,780 unreviewed LOC across seven isolated agent rounds, plus one shipped pane
  bug, forced a feature freeze and a process reset.** D57 froze all new `apps/scanner` §6.2
  items/enhancements until a full field-ownership audit and async-writer fencing were done, and
  adopted standing rules going forward: entry gate is FIX-vs-ENHANCEMENT, never LOC; every agent
  spawn carries prior history; one writer per mutable field; every async writer fenced; findings
  recorded durably in `.claude/remember/findings.md`, never a code comment. The fence pass itself
  found the fix's first sweep (11 sites, grepped on `runOnUiThread`) missed two `BiometricPrompt`
  callback sites that land on the main thread the same late-async way but don't match that
  syntactic pattern — caught only by enumerating the underlying hazard (a late framework callback
  touching Activity state), not the grep. → source: `docs/wiki/decisions.md` D57–D60;
  `docs/logs/M2-FENCE-EVIDENCE.md`.
- **2026-09-03 — a device session cleared the freeze's remaining verification debt, and a hostile
  link from a second origin proved the mint-path guard actually fires.** Six checks on the Pixel
  6a (Q47 cursor fix, D63 portrait lock, a mid-read re-tap, an `av://` handoff plus forced
  recreation with `BiometricPrompt` open, the QR/paste path plus recreation mid-read, and a
  hostile `av://` request from a genuinely second local origin fired mid-scan) all passed with
  zero crashes logged across the whole session. This was the third and decisive layer of proof for
  one guard — after a unit-test truth table and a source wiring trace, only a real second process
  hitting the exported surface confirmed the guard fires when it matters. → source:
  `docs/logs/M2-DEVICE-SESSION-2026-09-03-EVIDENCE.md`; `docs/wiki/decisions.md` D65.
- **2026-09-03 — every `allowed=true` run to date had been evidence about plumbing, not age.** The
  scanner asserted `over_threshold: true` unconditionally against a hardcoded 18 that only
  coincidentally matched chiproof's already-signed threshold, so a successful handoff proved the
  request/response wiring worked, never that the holder's actual age had been checked. Resolved
  (Q35/Q36) by reading the threshold from the signed request and computing a real in-app
  over/under answer from the DG1 date of birth, minted honestly either way. A related citation gap
  in the same pass — an exit-criteria row citing spike evidence as if it were the real-build
  re-run — was the same species of error: a passing-looking record that had never actually been
  re-verified against the current build. → source: `docs/wiki/decisions.md` D66; CHANGELOG.md
  Unreleased (Q35/Q36, exit-criteria row 1 correction).

## 6. Background — the ZK question, explained

*Merged 2026-08-30 from `docs/product/zk-challenges.md` (now archived at
`docs/archive/zk-challenges.md`) — its dated findings and measurements were folded into §§2–4
above as entries; this section keeps its explanatory narrative, the part that isn't a dated
finding, deduplicated against those entries.*

### 6.1 The question the owner asked

In the owner's own words, roughly: *"I scan something we all have — a passport or ID with an NFC
chip. Something trusted but non-central answers a boolean that can't identify me or link/profile
me across sites, preferably not even on the same site. Why all these certificates? Why not compute
a random id every time we answer?"* The rest of this section is the answer, written once so it
doesn't have to be re-derived every time the question comes back.

### 6.2 8een vs zkagent — the one-line difference and the flow

**8een verifies a proof someone else issued.** A government or bank issues a credential into an EU
wallet app once. Per visit, the wallet computes a *fresh* zero-knowledge proof over that credential
— "a validly-signed credential behind this proof clears the threshold" — and 8een checks the
mathematics. The site never sees the credential, only the proof. Unlinkability is a property of the
longfellow-zk scheme itself, cited from its security analysis, not built by 8een. The trust root is
the issuer list the verifier is configured with.

**zkagent verifies a document the government already issued — directly, with no issuer in
between.** The phone reads DG1 (the machine-readable data) and the SOD (the government's signature
over it) off the chip, verifies that signature locally against the public masterlist, checks the
requested threshold, and sends one boolean (plus, in tier B, a per-site `zktag`) together with
evidence that the app/device is genuine. The phone is the verifier of the document; the site only
ever verifies the phone (or, since D24, whatever evidence the phone's build supplies).

| | 8een | zkagent |
|---|---|---|
| Credential source | EU wallet credential, issued once by a government/bank into the phone | The government-issued chip document itself (passport, ID card), read fresh each time |
| What the site receives | A zero-knowledge proof over the credential | A boolean (+ optional `zktag`) plus evidence that the app/device is genuine |
| What makes it unlinkable | The math: a fresh proof every time, unlinkable by construction (cited, not built by 8een) | Depends on the evidence plug in use — see §2 above |
| Who you trust | The ZK scheme's soundness + the issuer's signing key | The government's chip signature (for identity) + whatever evidence plug is configured (for code/device integrity) |
| Dependency | google/longfellow-zk (vendored, Apache-2.0), an issuer trust list | A vetted chip-reading library (JMRTD), a public masterlist, an evidence plug (attestation root, or a ZK proof, or none) |

### 6.3 Identifiable vs linkable — two questions, two axes

These are different questions and the project's documents keep them apart on purpose:

- **Identifiable** — does the site learn *who you are*? Never true in tiers A or B; only true in
  tier C, and tier C is gated behind a pinned challenge-issuer key.
- **Linkable** — can the site tell that *two visits are the same person*? This is the axis that
  varies.

| | Across sessions, same site | Across sites |
|---|---|---|
| **Tier A (anonymous)** | Linkable, by design of the web around it (cookies/IP/fingerprint) — zkagent itself adds no linker (D22, §4) | Must be unlinkable — no field in the payload may be stable across sites |
| **Tier B (pseudonymous)** | Linkable, by design — `zktag = HMAC(secret, domain)`; this is how a site blocks one human once and keeps them blocked | Unlinkable, by arithmetic — a different domain hashes to an unrelated tag with no shared value to join on |
| **8een** | Unlinkable (cited property of fresh ZK proofs) | Unlinkable (cited property of fresh ZK proofs) |

Two things worth separating out: **the challenge nonce is never a linker** — it's the *site's* own
fresh random number, spent once, and says nothing about the holder. And cross-site is the axis that
actually matters, not same-site: IP addresses and browser fingerprints are *probabilistic* — they
rotate, get cleared, and don't reliably link two unrelated sites to each other. A hardware-rooted
(or otherwise globally stable) identifier is *certain* and *permanent* — identical everywhere, a
cookie you cannot clear. One site that already knows who you are (a bank, a shop with your delivery
address) could use it to deanonymise you on every other site that emits the same field. This is the
reasoning behind D22 (§4 above).

### 6.4 What the certificates (or any evidence) are for

The site does not trust the app just because the app says so: anyone can write, or modify, an
Android app that prints `over_18: true` on screen without ever touching a passport. Without some
form of evidence, the boolean is just a fancier "☑ I am over 18" checkbox — no more trustworthy
than the checkbox itself.

Evidence exists to say exactly one thing: **this answer came from a genuine read of a genuine
document** — not "this answer is true." That's the whole job. This is why a random id per answer,
on its own, doesn't fix the actual problem: a random id fixes *privacy* (nothing to link) but says
nothing about *authenticity*. A random id emitted by a genuine, unmodified app and one emitted by a
fake app that never touched a chip look byte-for-byte identical. Privacy and authenticity are
different axes.

An app also cannot vouch for itself — a self-signed "trust me, I'm the real app" claim is just the
checkbox again, one layer down. Historically (D23-era), every route that let the site trust the
*code* rather than the *math* led to a central voucher — Google or Apple, the two parties who can
sign a statement about what hardware and software actually ran, which **is** lock-in. D24's
evidence-slot design is the response to that: the core no longer bakes in one voucher, and one of
the pluggable evidence types (`zk-passport/1`) trusts the *math* instead of a voucher (see §6.5).

### 6.5 Two ways to use ZK, and why one of them is out

There are, in principle, two different places a zero-knowledge proof could go in this design:

**(a) ZK-prove the attestation chain itself** — hide the device fingerprint behind a proof instead
of removing it. This would require circuits over several ECDSA signature verifications plus X.509
certificate parsing. `google/longfellow-zk` has circuits only for the ISO mdoc format 8een
consumes, not for X.509 attestation chains, and building new circuits for this would mean writing
"our own cryptography," which both this project's rules and 8een's precedent forbid outright.
**(a) is ruled out.**

**(b) ZK-prove the passport itself, and drop attestation entirely** — the claim becomes: "I hold a
DG1+SOD signed by a government key present in the public masterlist, and the date of birth in it
puts me over threshold T," proved fresh and unlinkable by the math every single time, the same way
8een's proofs are. Once the claim is a mathematical proof, the site no longer needs to know or care
which app produced it. Structurally, (b) is identical to what 8een already does: the math proves
the claim, so the device carrying it doesn't need to be trusted or identified. This is the shape
several existing projects reportedly already build over passport chips — zkPassport,
OpenPassport/Self, Rarimo, and Anon Aadhaar (as a method) — see §3 above for what due diligence
found about each. Under D24, (b) survives in v1 as the optional `zk-passport/1` evidence plug
(validation-grade, not yet Track-Z-gated for a security claim), rather than as a mandatory
replacement for attestation.

### 6.6 The three honest products

| | Honor-grade | Voucher-grade | Math-grade |
|---|---|---|---|
| **What it is** | No evidence; a random id (or none) each time | Play Integrity / App Attest / key attestation: a vendor vouches for the device/app | ZK proof over the passport itself, no attestation |
| **What the site actually gets** | A checkbox with extra steps | A believable answer, backed by a central vendor | An answer backed by math, backed by nothing else |
| **Lock-in** | None | Whichever vendor is plugged in | None |
| **Privacy** | Perfect | Depends on the plug — see §2 findings | Unlinkable by construction, even same-site |
| **De-Googled / custom-ROM phones** | Fine | Excluded for the Google-rooted plugs | Fine |
| **Cost to build** | Small — legitimate captcha-grade fallback | Small once a plug exists | Large: circuit library dependency, on-phone proving time, per-algorithm coverage, a new language in the stack, young and less-vetted toolchains |
| **Legal weight** | Worthless — a modified app can lie for free | Fine at the site if the underlying evidence is sound | Fine, same as voucher-grade |
| **Chip cloning (Q18)** | N/A | Unaffected either way (evidence targets code/device integrity, not the document) | **Gets worse, not better** — a proof over static chip data cannot include a live AA/CA challenge-response, so tier-B uniqueness stays clone-replayable for every document, not just documents lacking AA/CA |

Only two things a site can ever actually trust: **the code that produced the answer** (which leads
to a central voucher) or **the math of the answer itself** (which leads to ZK circuits). There is
no third option that avoids both — this is why the core ended up shaped as a pluggable evidence
slot (D24) rather than committing to one of the three rows.

### 6.7 Play Integrity in simple terms

The app asks Google to vouch for it. Google checks the device and the app, and returns a sealed,
Google-signed verdict. The site checks Google's signature on that verdict, not anything about the
device directly. The party that actually knows the device (Google) is not the party asking the
question (the site) — which is why it removes the per-device fingerprint from the site's view, at
the cost of Google now seeing every check (measured in §2 above).

Documented facts (developer.android.com, 2026-08-30, ahead of the measurement in §2): standard
tokens can only be decoded server-side, via Google's own `decodeIntegrityToken` call with a service
account — Google's own documented decode latency is roughly 10 ms (not independently timed by this
project; the measured 11–18 ms client-side issue time in §2 is a different number). Classic tokens
can be decrypted locally, but the decryption keys must come from Play Console. Default quota:
10,000 requests/day. A sideloaded app gets `appRecognitionVerdict: UNRECOGNIZED_VERSION`. Verdict
schema: `requestDetails`, `appIntegrity`, `deviceIntegrity` (with `deviceRecall` and
`recentDeviceActivity` as optional, per-device-by-design opt-ins), `accountDetails`,
`environmentDetails`. Dependency cost: +3 build artifacts (`play-services-basement`,
`play-services-tasks`, `core-common`), not the full Google Mobile Services suite.

### 6.8 iOS and other makers

**Everything in this subsection is UNVERIFIED** — background knowledge only, not independently
checked by this project, flagged here rather than silently treated as fact.

**iOS App Attest** builds the voucher model in directly: a key is generated in the Secure Enclave,
and Apple issues a per-key certificate under Apple's own shared App Attest CA — so what the site
sees carries no separate per-device certificate the way Android's key attestation does; Apple sees
every attestation. (This is consistent with the borrowability finding in §2 — App Attest verifies
against a public Apple root — but the receipt/environment fields themselves have not been
measured.) The gate to even try this is unchanged from the PRD's standing position: $99/year, an
NFC entitlement, and a Mac — iOS remains a non-goal for this project.

Android device landscape, by route:

| Device class | Play Integrity | Key attestation |
|---|---|---|
| Stock Android + Google services (Pixel, Samsung, Fairphone) | Works | Works, with the device fingerprint |
| /e/OS, LineageOS, AOSP | Fails | Only if the factory keybox survived the flash (often doesn't, on non-Pixel) |
| GrapheneOS on Pixel | Fails, by design | Works, and reports a custom OS |
| Huawei / other China-market ROMs | Neither (already excluded by D2) | Neither |

The two Android routes have opposite failure modes: Play Integrity is inclusive of ordinary users
but excludes exactly the privacy-conscious people this project's ideals appeal to; key attestation
is the mirror image. Neither Android route is issuer-free in the attestation sense — the project's
standing correct phrasing holds regardless of which is picked: **"issuer-free identity,
vendor-rooted attestation."**

### 6.9 Three layers — who builds what

Any ZK-passport system splits into three layers, and only one of them is this project's to build:

| Layer | What it is | Examples | Do we build it? |
|---|---|---|---|
| **1. Engine** | Proof machinery — turns a computation into a proof, and checks a proof (e.g. `bb verify`) | longfellow-zk (Google), Barretenberg/Noir (Aztec) | **Never** — years of cryptography |
| **2. Circuits** | The specific computation written for the engine: check RSA signatures, hash DG1, compare birthdate | zkPassport's Noir circuits, Rarimo's | **Not by default** — a few hundred lines, but every line must be right or the proof lies silently; whoever writes it needs an audit |
| **3. Product verifier** | Normal code: issue the nonce, call the engine's verify, check scope/threshold/trust list, return `{ok, allowed}` | 8een's zk8een, our `chiproof` | **Yes — this is ours** |

8een is the template: longfellow-zk is layers 1+2 for 8een's use case; 8een itself is layer 3. What
"the math works" means, precisely: **soundness** (nobody can make a receipt for a false statement)
plus **zero-knowledge** (the receipt leaks nothing). The circuit is where real bugs live — an
under-constrained circuit proves false things and nobody notices; the DG1-flip finding in §3 above
is exactly that shape, by design.

### 6.10 The names, sorted

**Engines** (layer 1): **longfellow-zk** (Google, IETF draft) — mDoc/mDL, JWT, VC; what 8een uses;
does not read passports. **Noir + Barretenberg** (Aztec) — general-purpose toolkit; zkPassport's
circuits are written in it. **Circom + Groth16** — older toolkit; used by Rarimo and Self.

**Passport-specific ZK projects** (layers 1+2+3 built by others): **zkPassport** — Noir,
Apache-2.0, chain-free verification possible, circuits open, phone app closed, acquired by Aztec
Labs 2026-05-27; this is what the project's own spike ran (§3 above). **Rarimo** — Circom, MIT,
Halborn 2024 audit, own-chain registry. **Self/OpenPassport** — Circom; Celo + Google TEE; ruled
out. **Anon Aadhaar** — India's Aadhaar QR, not a passport; method only.

**Plain readers** (no ZK, no privacy — they show everything): **JMRTD** — the chip library inside
the M0 app. **tananaev/passport-reader** — the app M0 forked.

**One-line relation**: 8een = longfellow-zk + a verifier. zkagent on the math road = zkPassport
circuits + a verifier. Same shape, different credential and engine.

### 6.11 Could longfellow be adapted instead?

**Convert passport to mdoc**: no. An mdoc is a credential signed by an issuer. A converted one
needs a *new* signature from someone — either the phone (nobody trusts its key, which is back to
attestation) or a conversion service (which sees passports and signs credentials — that service
**is** an issuer, central, exactly the kind of adversary this project's NO-GO #3 rules out).

**Adapt longfellow's circuits to ICAO structures** (ASN.1 SOD instead of CBOR MSO, RSA-2048/SHA-256
instead of ECDSA P-256, DG1 hash, date compare): possible in principle — layer-2 work on a
better-reviewed engine (Google-authored, three external reviews, IETF draft) — harder to write,
since there's no circuit DSL, only C++ against the framework — and the circuits would still need
their own audit regardless. The concrete findings from checking this (no RSA circuit anywhere, no
ASN.1/DER parser, no bignum modexp) are recorded in §3 above. Useful pattern to copy on any engine:
longfellow keeps the issuer public key *outside* the circuit as a public input, so DSC→masterlist
trust can be checked by plain code rather than proven in-circuit. Precedent for community
credential circuits exists on that repo (an SD-JWT VC PR, ECDSA-based); passports/ICAO/RSA have
never been discussed there. Status: Track Z's realistic engine remains Barretenberg (RSA circuits
exist and have run against real documents, §3); longfellow is a watch item, reopened only if Google
adds RSA.

**Confirmed empirically 2026-08-31**: the "convert passport to mdoc? no" call above was
reconfirmed against real EU-org source and a locally-run reference verifier, not re-derived here —
see the §3 and §4 entries dated 2026-08-31 (`logs/M2-CONFORMANCE.md`, `logs/M2-CAPTURE.md`). Those
entries also record that the EU has separately published its own Barretenberg/Noir circuit over
ICAO documents (`av-lib-android-zkp-age-icao`), architecturally the same road as this section's
"(b) ZK-prove the passport itself" — but not wired into its own OpenID4VP/DCQL wire, and hardwired
to a narrower RSA parameter set than either of this project's two real documents.

### 6.12 Claims we may make today

Stated precisely, so nothing here gets rounded up:

- **No identification at any tier** currently shipped (tiers A and B; tier C is a gated future tier
  and identification only happens there, by design, when explicitly requested).
- **No cross-site linkability**, for whichever evidence plug is actually configured — the Play
  Integrity plug measures clean on this bar (§2); the raw attestation-chain shape does not (§2) and
  must not be used where tier A is promised.
- **Same-site recognition exists only in tier B, and only by the site's own explicit request.**

Claiming "zero-knowledge" for any of this today is an overclaim, and the design document
(`zkagent-design.md` §5, "Is this zero-knowledge? No") already says so plainly: it does not become
true product-wide until the math-grade evidence plug (§6.5, `zk-passport/1`) is the one actually in
use and Track Z's gates (§3 above) are met for a security claim to rest on it.

### 6.13 Where everything lives

- **Findings and lessons (distilled)**: this document, `docs/product/learnings.md`.
- **Evidence**: `docs/logs/M0-EVIDENCE.md` (chip reads), `docs/logs/M1-POC-EVIDENCE.md`
  (attestation chains), `docs/logs/M1-Q23-EVIDENCE.md` (Play Integrity + ZK composition).
- **Component facts**: `docs/product/zk-due-diligence.md`.
- **Decisions**: `docs/product/zkagent-prd.md` — D19–D24, Q23 (resolved), Q24, Q25.
- **Archived narrative**: `docs/archive/zk-challenges.md` (superseded by this section).
- **Spikes**: `spikes/m1-attest`, `spikes/m1-integrity`, `spikes/m1-zk` (`phone/` for the proving
  attempt).
- **Commits**: `85d684a` (M1 POC), `c3ee545` (Q23 spikes), `6c4a480` (voucher-grade v1 decision).

---

*Compiled 2026-08-30. No real passport/ID data, MRZ fragments, real-run hashes, or device serials
appear in this document — see the evidence logs' own PII rules for why.*
