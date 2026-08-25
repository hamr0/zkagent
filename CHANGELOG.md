# Changelog

All notable changes to zkagent are documented here.
Format: [Keep a Changelog](https://keepachangelog.com/en/1.1.0/) · versioning: [SemVer](https://semver.org/).

## [Unreleased]

- **Docs reorganised with docs-builder.** `docs/01-product/`, `docs/02-engineering/`
  and `docs/context/` (each holding a single file) flattened into `docs/product/`
  and `docs/logs/`: `zkagent-prd.md` and `zkagent-design.md` classified as
  `product` (live specs); `future-digital.md` classified as `logs` rather than
  `product` on its own self-declaration as "the collector" — a session record of
  the strategy discussion that produced the PRD, distinct from the PRD itself,
  which the same sentence calls "the filter." 15 inbound links across
  `.claude/stash/`, `README.md` and `packages/chiproof/README.md` rewritten to
  match. New `docs/index.md` (generated, whole-corpus map — never hand-edited)
  and `docs/log.md` (append-only reorg history). New `CLAUDE.md`, carrying only
  the marker-wrapped pointer to `docs/index.md`. Nothing was oversized (largest
  file: 385 lines against a 500-line ceiling), so no split ran.
- **Development device settled (D2): Pixel, stock ROM; all other vendors ruled
  out for M0–M2.** Recorded with the reasoning so it is not relitigated: NFC
  Type A/B is not the discriminator (baseline on every phone-class NFC
  controller), and the extended-length-APDU variance behind commercial ID
  vendors' device blocklists does not apply here because zkagent never reads DG2
  — DG1 + SOD fit in short APDUs with chaining. The real discriminator is
  attestation quality. Huawei and China-market ROMs excluded outright (no Play
  Services, non-Google attestation root). The rationale is debuggability rather
  than capability; a non-Pixel second device is an M2 concern.
- **`chiproof@0.0.0` reserved on npm** (published by the owner as a manual step
  per NO-GO #8; source at `packages/chiproof/`) — two files, no code, README
  stating plainly that it does nothing and that this is not zero-knowledge.
  **Apache-2.0, matching the repo** — correcting the standing defect in the
  published `zkagent@0.0.0` placeholder, which is MIT and must be fixed or
  deprecated when NO-GO #8 is next revisited.
- **D18 — sequencing: the agent layer is not designed, discussed or specified
  further until the age-verification leg is finished.** Rung 2 stays decided,
  bounded and not started; reopening it before rung 1 ships is refused by
  default, including on a passing owner request. The rationale is this session's
  own evidence rather than principle: Q18 sat undetected through four PRD
  revisions and surfaced only when the read path had to be written out
  concretely. More design is currently producing more surface, not more
  certainty.
- **D13 extended: the agent layer is mode B only, structurally.** A delegation
  cert hangs off a persistent human root — an agent that cannot be recognised
  cannot be revoked, and mode A emits nothing to bind a cert to. Recorded so
  nobody later attempts agent delegation on the anonymous path and discovers
  mid-build that it cannot work.
- New companion document `docs/02-engineering/zkagent-design.md` — the design
  and disclosure model. Semi-technical description of how the product works
  rather than what gets built: the sealed-envelope framing and the two standing
  properties (**narrow by default, fresh by default**); the read path from MRZ
  key through SOD → DS → CSCA verification; **why the window is enforced by the
  phone and not by the chip** (an ICAO chip has no selective disclosure — DG1 is
  one signed blob, so reading a birth date means reading the name and document
  number too, which is precisely what makes the attestation load-bearing rather
  than a bonus signal); exactly what crosses the wire in each mode; the
  three-outcome verdict; an honest zero-knowledge comparison; the legal posture;
  the operator configuration surface with the reasoning behind each knob; the
  passports-then-ICAO coverage path; and a limitations section. Description, not
  commitment — the PRD wins on any conflict.
- **Q17 legal posture fixed (owner, 2026-08-07): demonstration, not
  certification.** The project is not contesting the regulatory requirement and
  is not seeking certified-provider status; the point being demonstrated is that
  the privacy properties the rules reach for are obtainable with far less
  machinery than the official route requires — no wallet, no attestation
  provider, no batch issuance. This lowers what must be proven (a demonstration
  must be honest, not certified) without removing the question: legal
  sufficiency in a named jurisdiction still gates any shift from demonstration
  to pitch.
- **New Q18 — chip cloning vs the uniqueness claim** (surfaced while writing the
  design companion; mode B only). Verifying the SOD is *passive* authentication:
  it proves the data was signed by the issuing government, not that the chip
  presenting it is the original. A replayed data dump would mint **the same
  zktag as the genuine document**, so a blocked human could re-present from a
  clone — which breaks blocking at its root. The defence is the chip's own
  challenge-response (Active or Chip Authentication), which is not universally
  present: AA is optional in ICAO 9303 and omitted by some issuers on privacy
  grounds. M0's chip inventory extended to report AA/CA support. Mode A is
  unaffected — with no identifier emitted there is nothing to impersonate.
- PRD restructured to v1.4 — the largest change since v1.0.
  - **D13 — disclosure has two modes, and the verifier must ask for the one it
    needs.** **Mode A (anonymous, default)** emits one bit and no identifier of
    any kind; two presentations by the same holder to the same service are
    unlinkable. **Mode B (pseudonymous, opt-in)** additionally emits the
    domain-scoped zktag, linkable within a service and unlinkable across
    services. Uniqueness and unlinkability are in direct tension and only one
    leg of the product needs each: age verification needs no pseudonym and
    emitting one is a pure privacy regression, while agent accountability is
    *defined* by recognising a returning human. Mode B must be requested
    explicitly, never inferred or silently upgraded, and mode-A payloads must be
    byte-shape identical whether or not the device has ever made a mode-B
    presentation.
  - **EU Age Verification Blueprint checked** (`ageverification.dev`, 2026-08-07)
    and it settles the linkability question in the opposite direction from the
    common assumption: linkability is **not** required by the EU approach, it is
    what that approach engineers against — single-use attestations removed from
    the batch after one use, batch issuance mandated to prevent linkability, and
    `ValidityInfo` timestamps deliberately coarsened because a clock field is a
    correlator. The batch machinery exists because their wallet must round-trip
    to an attestation provider and cannot do ZK; zkagent has no issuer in the
    path, so per-presentation freshness is free and needs no batching. Cited in
    PRD §12 with the normative text quoted. **Not claimed:** any legal standing
    from meeting those properties (Q17).
  - **New FR9 — unlinkability budget**, and **new Q15**: mode A's guarantee is a
    property of the *entire* payload including the attestation, not of our own
    fields. A device-unique attestation key, an unshared certificate
    intermediate, an OS/patch-level string or a precise timestamp each
    reintroduce linkability through the back door. Added as riskiest-assumption
    item 8 and gated behind a new milestone **M1b**, a black-box byte probe with
    a planted positive control (8een §7.3 method) that blocks M3. Until measured,
    mode A is a design intent and is to be described as one.
  - **New NO-GO #11 — no stable identifier in mode A**: not a zktag, not a device
    id, not a "rate-limit key", not "just for fraud detection". Recorded because
    that pressure will arrive and will sound reasonable every time.
  - **FR6 narrowed, not retired (D15).** Uniformity is required within one client
    build and mode; cross-client distinguishability is *accepted and is the
    mechanism*, because the trust list works by reading exactly the package name
    and signing-cert digest that distinguish clients. Safe only because of FR11:
    a published derivation means two clients produce the same zktag, so a visible
    client identity partitions the anonymity set, not the identity space. Cost
    written down rather than discovered — the anonymity set is "users of this
    client build in this mode", not "all zkagent users".
  - **New FR10 (D17) — adopter-held trust list**, and **new FR11 (D16) — the
    derivation is a published, versioned spec** (`zkagent-derivation/1`). Both are
    prerequisites of the borrowable core: without FR10 an open core is a forgeable
    one; without FR11 two conformant clients fork the identity space and blocking
    silently breaks. We publish no list and run no registry (NO-GO #3 extended to
    say so explicitly).
  - **D14 — `acceptedDocuments` is adopter-configurable and the default is
    greedy.** `k` has no cost in mode A: with no identifier emitted, a holder with
    three documents is not three identities but three ways to answer the same
    question. `k` is a real cost only in mode B, so a mode-B adopter needing k≈1
    narrows to passports and knowingly trades reach. NO-GO #5's "at most k" is
    therefore a mode-B claim.
  - **D12 — project/package split: project `zkagent`, package `chiproof`**
    (precedent: `8een` / `zk8een`). Verified available on npm 2026-08-07.
  - **D10 revised** — the mode-B secret ceiling becomes configurable (default 30,
    max 180 days) and freshness is *negotiated*: a verifier may state
    `max_scan_age_days` in the challenge and the presentation answers with one
    bit, never an age in days, which would be a fingerprint. Mode A is unaffected
    — it caches no secret.
  - **Structure**: document reorganised into two rungs — rung 1 (core: chip read,
    signature verification, both modes, verifier SDK, demo; M0–M3) is v1, rung 2
    (agent layer; M4–M5) is decided, specified and explicitly not started.
    Cross-document identity unification made an explicit non-goal and D9 narrowed
    accordingly. Q1 folded into Q14; Q2/Q5/Q6/Q9 closed out of the open-questions
    section into one-line summaries. New §2.1 states plainly why v1 is not
    zero-knowledge, alongside a standing claim-discipline rule in the header.
- M0 (planned): throwaway POC at the riskiest assumption — read a real
  passport chip, verify its government signature against a public masterlist,
  derive the same zktag across two scans. Evidence doc required before anything
  else is built.
- PRD amended to v1.1: M0 de-platformed (desktop USB PC/SC reader or Android
  via JMRTD — no Apple account/Mac needed); Q2 resolved as moot for M0; Q6
  added (Mac-less iOS build path, decided at M2).
- PRD amended to v1.3 (continued): **Q14 — attestation root is an open
  choice, not settled on Play Integrity.** Android Keystore hardware key
  attestation returns a vendor-rooted certificate chain carrying verified-boot
  state, patch level and the app's signing-certificate digest, with no runtime
  Google service call, no Play Console registration and no Play Services on
  device — so it works on GrapheneOS and de-Googled builds. Likely answer is
  both roots, key attestation primary. Three unknowns named as things to
  verify rather than assume: keybox extraction, OEM implementation quality,
  and revocation-list handling. Risk-register items 6 and 7 corrected
  accordingly — the Google dependency is a CA relationship under key
  attestation rather than a gatekeeper, and the GrapheneOS exclusion is a
  consequence of choosing Play Integrity alone, not an unavoidable cost.
- PRD amended to v1.3 (continued): two structural risks added to the
  riskiest-assumption register. **(6) We are not issuer-free** — identity is,
  attestation is not: Play Integrity is a Google-run service that can be
  gated, quota'd or withdrawn, and NO-GO #3 only forbids *us* running an
  issuer. Correct phrasing is "issuer-free identity, Google-dependent
  attestation." **(7) The users most aligned with the product are excluded by
  it** — GrapheneOS/CalyxOS and de-Googled devices fail Play Integrity
  permanently, and they are disproportionately the people who want anonymous
  personhood proof. No known mitigation at captcha-grade; stated rather than
  hidden.
- PRD amended to v1.3 (continued): **D11 — age threshold configurable, output
  stays one bit**, adopted verbatim from 8een D6 (`over_threshold`, default 18).
  Rejected the alternative of emitting a ladder of common thresholds at once —
  that buckets the holder's age instead of disclosing one bit. A proof of a
  threshold other than the one requested must be rejected, not accepted as
  close enough. **Q9 resolved** — cert handoff ships three paths (QR, LAN POST,
  file); the cert is signed so the channel needs no confidentiality. New
  **§13 Adoption risk**, plus **Q11** (age binary-search probing), **Q12**
  (passport-only coverage vs the age-verification positioning — blocks that
  pitch until answered), **Q13** (first-adopter path).
- PRD amended to v1.3: **D10 — the derived secret expires after 30 days**,
  renewable only by a fresh passport scan. Previously the secret was cached
  in StrongBox indefinitely, which meant borrowing someone's passport for one
  scan bought their identity permanently, undetectably and unrevocably. A
  30-day ceiling converts one-time possession into recurring possession.
  Enforced in the enclave, not by app-side date checks. Accepted cost: the
  passport must stay accessible. Recorded as FR1 and D10.
- PRD amended to v1.2:
  - D2 flipped to **Android-first** (JMRTD + Play Integrity). Google Play is
    $25 one-time against Apple's $99/yr, Android builds on the owner's Linux
    box with no Mac, and test builds sideload without an entitlement gate.
    iOS deferred until user demand justifies the cost. Q5 and Q6 resolved by
    this decision; iOS moves from goal to non-goal.
  - `tag` renamed **`zktag`** throughout. RFC 9421 defines its own `tag`
    signature parameter meaning "which protocol does this signature belong
    to" — the collision would have been silent and wire-visible.
  - New **FR8**: RFC 9421 wire mapping — `alg="ed25519"`, `keyid` =
    agent-pubkey thumbprint, RFC `tag` reserved for protocol labelling and
    never carrying the zktag, signature `expires` ≤ cert `expiry`, cert in
    its own header. An off-the-shelf 9421 verifier must accept our signature
    unpatched.
  - New **Q7** (M2): device-assurance tier vs FR6 uniformity — exposing
    StrongBox/strong-integrity tiers is itself fingerprinting metadata.
  - New **Q8** (M4): remaining 9421 mapping choices — cert header, mandatory
    covered components. Cert is sent inline per request; URL-reference
    rejected for v1 as it collides with NO-GO #1/#3.
  - New **Q9** (M4): phone→agent cert handoff UX (QR / paste / localhost
    POST). Added to the M4 deliverable and checkpoint.

## [0.0.0] — 2026-07-26

- npm name reservation placeholder published (`zkagent@0.0.0`) — no functional code.
- PRD v1.0 drafted (`docs/01-product/zkagent-prd.md`): owner decisions D1–D8 signed,
  NO-GO table, milestones M0–M5, riskiest-assumption register.
- Repo initialized.
