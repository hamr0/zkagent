---
type: reference
title: zkagent — PRD
status: stable
sources: [docs/archive/zkagent-prd.md, docs/wiki/decisions.md D108]
---

# zkagent — PRD

> **Status (2026-10-24, D108): complete experiment. Closed cleanly, not abandoned.**
> v1 — rung 1, milestones M0–M3 — is delivered; current release **v0.7.1** (chiproof 0.7.1 on npm,
> scanner APK on GitHub Releases); the next release is 0.8.0 and the project stays on 0.x (no 1.0).
> The owner may revive it, reuse parts, or reuse the whole. Parked and unbuilt work (S4,
> Play track, rung 2, Track Z, and others) is listed in [`../wiki/backlog.md`](../wiki/backlog.md);
> the liftable components are in [`reuse-map.md`](reuse-map.md). Success criteria and evidence:
> §15 Close-out. NO-GO #10 (the scope gate) applies to any revival. The owner's agent-delegation work
> now lives in the individual Internet-Draft `draft-hamr-oauth-agent-delegation` (no IETF endorsement).
> **Update 2026-10-25 (D109, D109(h)): M3b / tier C was revived by the owner and is IN BUILD (approved
> 2026-10-25, scope in `../wiki/milestones.md` §6.9); tier C itself is now built and device-checked and ships in 0.9.0 (D116, 2026-10-27), while the M3b milestone stays in build until its remaining items, the reviews and the 0.9.0 release are done. D109(i) (2026-10-25) also
> opens age verification (tiers A and B) to any site in the build's `operator.json` and shares one age
> lock per site across tiers (§6.9.2 items 9 and 23); tier C stays gated behind the operator's list
> and the holder's setting.**

This page is the product specification of zkagent: the problem, what v1 is, the core invariant, the rung structure, goals/non-goals, requirements, and the NO-GO table. Milestones and build status live in `milestones.md`; owner decisions in `decisions.md`; open questions in `questions.md`; parked work in `backlog.md`; version history in `history.md` (all in `docs/wiki/`).

## 1. Problem

Two gaps share one root cause. **Agent accountability**: emerging auth standards (Web Bot Auth, Visa TAP, Google AP2, OAuth on-behalf-of drafts) root trust in a vendor or a custodial IdP account; the `sub` claim is "routinely overloaded… without a standard classification mechanism" and delegation chains have no anchored origin. The personhood-credentials literature (arXiv 2408.07892, 2501.09674) calls for a privacy-preserving human root but assumes an issuer nobody has stood up. **Age verification**: present demand and legal deadlines meet incumbent solutions (ID upload, credit-card check, face estimation) that are expensive or privacy-hostile; the EU's own answer requires a wallet, an attestation provider, and a batch-issuance round trip before a user can prove anything.

**Root cause: everyone assumes an issuer.** zkagent supplies the root issuer-free — the government already issued the credential, and it sits in the holder's pocket. The chip *is* the attestation provider. (zkagent-prd.md:726-735)

## 1.1 Glossary — three objects that must never share a name

Conversation history has used "nonce" for all three; the mode-A claim depends on keeping them apart.

- **challenge nonce** — a random single-use number inside the requester's signed challenge (FR4); made by the requester fresh per request; spent after one request; present in all tiers (A, B, C); cannot recognise the holder — it is the requester's number, not derived from the chip, and reuse is rejected by the verifier, so it cannot correlate two visits.
- **secret** — material derived from chip data, held in the phone's secure hardware (D10); made by the app at scan time; lives until the operator's re-scan ceiling (30–180 days); present only in modes B and C, never minted in A; never leaves the phone.
- **zktag** — `HMAC(secret, requester's verified domain)`, the pseudonym (FR2, FR11); made by the app per presentation; stable for the life of the secret, same site ⇒ same tag; present only in B and C; recognisable at that one domain only.

The nonce proves the request is fresh; the zktag proves the person is the same. Tier A wants the first and refuses the second. "Minted id" in earlier notes means the secret + zktag pair, never the challenge nonce. (zkagent-prd.md:736-750)

## 2. What v1 is

A thin native Android scanner app (the only native piece) NFC-reads the chip via a vetted library (JMRTD — never custom parsing), verifies the government signature against a public masterlist (ICAO PKD / BSI, no CA of ours), evaluates the verifier's request against the chip contents, attests that unmodified code ran those steps, and hands the answer to the web flow (QR / app link). A stateless Node verifier SDK (`chiproof`) verifies the attestation and challenge nonce, checks the client against an adopter trust list (FR10), and (mode B only) checks an adopter-supplied blocklist, producing a verdict `{ok, allowed, reason}`. The agent layer is rung 2 (§4), not v1.

**Mode A · anonymous (default).** Output: `{ claims: { over_threshold }, attestation, challenge }` — no identifier of any kind. Two presentations by the same holder to the same service are unlinkable; nothing is derived or cached that could become an identity. This is the age-verification mode (D13).

**Mode B · pseudonymous (opt-in, requested in the challenge).** `secret = KDF(chip stable data)`, never leaves the device (Keystore/StrongBox, biometric-gated, max age enforced — D10); `zktag = HMAC(secret, verified service domain)`. Output adds `zktag`. Linkable *within* one service (dedupe, blocklist, "have I seen this human before"); unlinkable *across* services (D13). Same human + same service = same zktag forever; different services = unlinkable zktags. Banning a zktag kills every agent of that human at that service, with no re-mint. Forging requires beating hardware attestation or forging a government chip — captcha-grade, never bank-grade.

### 2.1 Why this is not zero-knowledge, stated plainly

A ZK proof lets the verifier check the mathematics itself; zkagent instead has the verifier check a hardware attestation that unmodified code produced the answer, then trust that answer — break the attestation and the claim collapses, whereas breaking attestation buys nothing against real ZK. The privacy outcome in mode A is comparable (one bit crosses the wire); the trust model is not. D1 and NO-GO #7 forbid ZK circuits *of ours* in v1 (amended 2026-08-30, D24/D25): third-party ZK may enter only as evidence — the `zk-passport/1` plug in the FR12 registry, validation-grade, tier A only, verifying zkPassport/Barretenberg circuits that are not ours, gated by Track Z. Uniqueness is not a ZK property: ZK gives unlinkable-by-construction selective disclosure, useless for "have I seen this person before"; uniqueness comes from the credential being scarce (one passport per person), which zkagent can leverage and ZK cannot. (zkagent-prd.md:751-800)

## 3. The invariant (inherited from 8een, adopted verbatim)

**`ok` (did the checker manage to check) is separate from `allowed` (what the answer was), and `ok:false` ⇒ `allowed:null`, never `false`.** A broken verifier saying "no" is indistinguishable from a working one — it would turn away every legitimate human while looking healthy.

| Condition | Verdict |
|---|---|
| holder under the requested threshold | `ok:true, allowed:false` (real no) |
| zktag on blocklist (mode B) | `ok:true, allowed:false` (real no) |
| attestation invalid / nonce replayed / client not on trust list | `ok:true, allowed:false` (real no) |
| masterlist half-loaded, blocklist store unreachable, attestation root unreachable | `ok:false, allowed:null` — never a "no" |

Corollary (8een's recurring bug shape, found 7+ times there): never trust a health check, a config value, or a client-supplied field. The masterlist is a PEM list — assume it can silently half-load and prove it can't. (zkagent-prd.md:801-815)

## 4. Rungs — what ships when, and what is deliberately not yet built

Two rungs; only the first is v1. Rung 2 is not being built and is not planned — it stays in the source PRD so it is not re-invented.

| Rung | Contents | Milestones | Status |
|---|---|---|---|
| 1 — the core | Chip read, government-signature verification, mode A age bit, mode B zktag, attestation, verifier SDK, demo. Published as `chiproof`. | M0–M3 | **This is v1. Delivered** (v0.7.1; see §15). Nothing else is v1. |
| 2 — the agent layer | Delegation certs, RFC 9421 request signing, per-serial revocation, blocklist + pseudonymous appeal. | M4–M5 | **Backlog, not planned (D108).** Specified in outline (FR5/FR8, Q8), never designed or built here; gated on at least one real adopter, of which there is none. See `backlog.md`. |

Rung 1 ships both modes together — the zktag is opt-in per presentation (D13), so it does not delay the age wedge. Track Z (parallel, gated by D23) covers ZK over the passport; no milestone until the gates hold. The core is borrowable: anyone, including a government, may embed `chiproof` in their own app — a one-sided sale that mitigates the two-sided-market problem (§14). Two hard prerequisites: the derivation must be a published spec (FR11), or two clients fork the identity space; and the verifier must decide which clients it accepts (FR10), or openness becomes forgeability. (zkagent-prd.md:816-830)

## 5. Goals / Non-goals

**Goals (rung 1)**
- G1: a holder proves one attribute from a government chip, Android-first, with no issuer, no account, no PII handled by anyone.
- G2: in mode A, a service learns exactly one bit and cannot link two presentations by the same holder — measured, not asserted (FR9).
- G3: in mode B, one human ↦ one stable zktag per service, unlinkable across services.
- G4: a service adopts with one npm install, zero PII handling, and its own trust list.

**Goals (rung 2, not v1)**
- G5: agent delegation certs + RFC 9421 request verification riding the zktag.
- G6: blocklist + pseudonymous appeal, all state adopter-supplied.

**Non-goals (v1) — the scope-creep magnets, named explicitly**
- ZK circuits (D1 — future tier; not built, not vendored, not scaffolded).
- iOS (deferred until demand justifies $99/yr and a Mac/cloud-Mac build path; design must not preclude App Attest).
- Money/payments, legal-grade identity, one-person-one-vote (k-bound makes it dishonest — NO-GO #5).
- Becoming an EU wallet, an attestation provider, or an eIDAS-conformant component; zkagent may share privacy properties with the EU Age Verification Solution but is not part of that ecosystem and must never imply certification.
- Non-chip documents. Readable set is ICAO 9303-compliant chip documents (verifiable SOD signature chain). Explicitly excluded, not "later": US driving-licence PDF417 barcodes (no verifiable signature); photos; photocopies; OCR. Signed mobile credentials (US mDL) may qualify eventually via a separate read path.
- Predicates beyond a single threshold per presentation in tiers A and B. Tier A discloses exactly one bit; tier B adds exactly one pseudonym. Identifying predicates exist only in tier C (D19), from the published verb list; every new verb is a spec revision plus owner sign-off (NO-GO #10).
- Unifying identities across documents — explicitly rejected (owner, 2026-08-07); a holder with two documents holds two identities (see D9, NO-GO #5).
- signedreply / attestation-ledger / reputation integration — separate product.
- Federated or shared blocklist service — rung 2 defines the signed blocklist format and adopter store interface only; zkagent runs no list, hosts no reputation, publishes no trust list.
- Browser extension, desktop scanner, or any second client — one scanner app, one verifier SDK, one demo page, nothing else.

(zkagent-prd.md:831-854)

## 8. Requirements

- **FR1 Scanner** — vetted-lib chip read; local SOD verification; no telemetry, no account, no network call except masterlist refresh. Mode B derivation happens in the enclave with an enclave-enforced max age (D10), never app-side date arithmetic; an expired secret blocks mode-B presentation and cert issuance alike. Mode A derives no secret and caches no identity-bearing material.
- **FR2 zktag (mode B only)** — `HMAC(secret, verified-domain)`; domain computed client-side (FIDO-style origin binding), never accepted from the server.
- **FR3 Verifier** — stateless; never-throw; verdict `{ok, allowed, reason}`; all stores adopter-supplied and failing closed; routes evidence to registered plugs and enforces the ok/valid separation, never judging evidence itself.
- **FR4 Challenge** — HMAC self-authenticating nonce, single-use spend, atomic store shape (Redis `SET NX PX`); carries the mode request, threshold, and any freshness requirement (D10).
- **FR5 Delegation (rung 2)** — VC-shaped cert, per-agent serial, individually revocable, expiry mandatory.
- **FR6 Uniformity (narrowed, v1.4, D15)** — all presentations from one client build in one mode MUST be byte-shape identical (fixed field set, sizes, version string, no per-device/per-user metadata, coarse timestamps). Cross-client distinguishability is accepted and is the mechanism: package name and signing-cert digest are visible by design for FR10's trust list. Consequence: the anonymity set is "users of this client build in this mode," not "all zkagent users" — must be measured (FR9), not asserted.
- **FR7 Responsive** — any web surface is responsive, mobile-first.
- **FR8 RFC 9421 mapping (rung 2)** — requests signed `alg="ed25519"`, `keyid` = agent-key thumbprint. RFC 9421's own `tag` parameter MUST NOT carry the zktag — the zktag rides only in the delegation cert. Signature `expires` MUST be ≤ cert `expiry`; the cert travels in its own header, never in `keyid`; a conformant off-the-shelf 9421 verifier accepts the signature unpatched.
- **FR9 Unlinkability budget (v1.4)** — mode A's no-identifier guarantee covers the entire emitted payload including the attestation, shown by black-box byte comparison with a planted positive control; anything not shown independent of holder/device must be removed, coarsened, or the mode-A claim withdrawn. Blocks M3. Revised by D22: stability is judged across sites, not across presentations. M1b (2026-08-30) passed with one disclosed bucket (D26).
- **FR10 Trust list, adopter-held (v1.4, D17)** — verifier configured with accepted client identities `{name, package, certDigest, specVersion}`; attestation reports package name + signing-cert digest, which is the client's identity (a modified APK must be re-signed). We publish no list and run no registry (NO-GO #3); a client off the list is rejected even with identical source.
- **FR11 Published derivation spec (v1.4, D16)** — mode-B derivation is a versioned public spec (`zkagent-derivation/1`); input is the document number (D9); `zktag = HMAC(KDF(chip data), domain)` so the same document + domain produce the same zktag under any conformant client — else the identity space forks and blocking silently breaks. Uniqueness is conditional, not absolute: `document_number` (DG1) cannot be distinguished by passive authentication from a byte-for-byte clone; "one human, one zktag" holds only where the verdict's `chip_auth` field (D21) reads `true`. An adopter requiring unforgeable uniqueness must gate on `chip_auth: true` and accept the resulting coverage loss (D29); the reference posture does not gate on it.
- **FR12 Evidence-type registry (D24)** — published, versioned; each entry declares data schema, binding rule, who can verify, linkability class (`none` | `signer` | `device`), and tier ceiling. `zk-passport/1`: tier ceiling A (D25), nonce via `service_subscope`; discloses the document circuit class (DSC profile ≈ issuing country/generation), stable across sites (D26, disclosed not hidden); bare mode reveals nothing. `current_date` is client-coarsened to midnight-UTC before it feeds the circuit, flooring effective `max_scan_age` precision at 1 day (D28); the M2 reference app ships bare, not `zk-passport/1` (D27). `sig-ed25519/1`/`sig-p256/1` (D30, D38-D41): an attester-held key signs claim-hash + nonce + scope + zktag; `item.data` is `{key_id, pubkey, sig}`, verifier recomputes `key_id` and never trusts the claimed value; trust store is a pluggable attester-key store keyed by `(scope, zktag)` with TOFU-on-first-sight as default and an operator-pinned key list as a supported alternative — the store is operator-run, NO-GO #3 unchanged. Linkability class settled at `signer`, tier ceiling B, closed (D41): the class is a property of the plug measured from its actual payload, never inferred from its technology category — binding for every future registry entry.

(zkagent-prd.md:1699-1713)

## 9. NO-GO table — check before proposing any feature

| # | NO-GO | Why |
|---|---|---|
| 1 | We store nothing server-side. Ever. No identity, no chip data, no zktags-at-rest, no logs of who verified | Statelessness is the security argument, not a limitation (8een NO-GO #7 lineage) |
| 2 | No custom security-critical code: chip parsing, attestation parsing, crypto — vetted libs / platform APIs / stdlib only | AGENT_RULES; 8een NO-GO #8 |
| 3 | No CA, no issuer, no enrollment server, and no trust list run by us | Issuer-free is the product |
| 4 | No unmasking capability — not escrowed, not quorum-gated, not "for emergencies." Max penalty = exclusion | A capability that exists can be compelled |
| 5 | Never claim "one human = one zktag" — always "at most k (k = documents held, ~1–3)". Never claim more than captcha-grade, or replay-safe/sybil-proof/zero-knowledge beyond what a measurement showed | Overclaim is the death of a trust product |
| 6 | No web-NFC scanner — the scan is native, period | Platform wall (NDEF-only browsers) |
| 7 | No ZK circuits of ours in v1, and v1 is never described as zero-knowledge; third-party ZK proofs may enter only as an evidence plug (D24), validation-grade, tier A only (D25), gated by Track Z (amended 2026-08-30, D24/D25) | D1. Captcha-grade bar |
| 8 | No npm publish until the package is standalone-usable — placeholder reservation only; publishing is a deliberate manual owner step | zk8een binary-distribution lesson |
| 9 | No secrets/test keys in the tree — runtime-generated, temp dirs only | AGENT_RULES + 8een PRD §10 |
| 10 | No feature enters a milestone unless it's in this PRD first. New idea → PRD change → owner sign-off → build. Mid-milestone additions refused by default | The scope gate |
| 11 | No stable identifier in mode A — not a zktag, device id, rate-limit key, or hashed anything, even "just for fraud detection" | Mode A's value is that the field does not exist to be leaked or correlated; a service needing recognition must request mode B (D13) |

(zkagent-prd.md:1714-1729)

## 12. Grounding (why this isn't a dart in the dark)

- Personhood credentials called for, issuer assumed, none stood up: arXiv 2408.07892 (OpenAI/Microsoft/Harvard et al.); delegation-to-agents follow-up: arXiv 2501.09674 (MIT et al.).
- IETF drafts (`draft-oauth-ai-agents-on-behalf-of-user`, `draft-klrc-aiagent-auth`, AIP `draft-prakash-aip`, WIMSE) root delegation chains in custodial IdP accounts; `sub`-overloading and chain-splicing are named open gaps.
- Transport converged on RFC 9421 (Web Bot Auth live at Cloudflare edge since 2026-03; Visa TAP same base); zkagent attaches as fields. The RFC signs the message but puts key trust explicitly out of scope — that gap is the zkagent insertion point (FR8/FR10/Q8).
- Deployed precedent for attestation-backed anonymous auth at captcha-grade: Apple Private Access Tokens. Prior art for document-derived proofs (ZK variant, web3-aimed, no agent story): zkPassport, Self, Anon-Aadhaar.
- Trust root: ICAO Doc 9303 (signed chip + SOD), public CSCA masterlists (ICAO PKD, BSI); document numbers change on renewal (drives D9).
- EU Age Verification Blueprint (checked 2026-08-07): linkability is not required by the EU approach, it is engineered against — one-time attestation use, batch issuance, coarse `ValidityInfo` timestamps, domain-specific pseudonyms. That batch machinery exists because their wallet must round-trip to an attestation provider and cannot do ZK; zkagent has no issuer in the path, so per-presentation freshness is free and needs no batching (D13 mode A). The Blueprint also confirms zkagent's hardware posture (Secure Enclave / TEE / StrongBox). Not verified and not to be claimed: that meeting these properties confers any legal standing (see `questions.md`).

(zkagent-prd.md:2196-2205)

## 13. Success criteria

- S1: M0 evidence shows same-zktag-twice from a real document, all numbers measured (no guessed timings — 8een's 10×-wrong lesson).
- S2: M1b shows two mode-A presentations byte-identical, with a planted stable field proven to break the check.
- S3: A service integrates the verifier with zero PII handling and one dependency decision documented.
- S4: The demo shows a mode-B ban that survives identity reset (new bots, new keys, new IP — same human blocked).
- S5: Every milestone has an evidence doc with deviations and retractions recorded, 8een-style.

(zkagent-prd.md:2206-2213)

## 14. Adoption risk (named, because it outranks the crypto risks)

The dominant risk is not a break in the chain — it is that no one installs the verifier.

- **Two-sided market.** Sites will not check for a signal users do not carry; users will not carry a signal no site checks. Nothing in the cryptography solves this.
- **The borrowable core is the real mitigation** (§4). An adopter who embeds `chiproof` in their own app brings their own users, converting a two-sided problem into a one-sided sale — works only if FR10 and FR11 hold.
- **Partial mitigation:** integration cost is near-zero for a site already verifying RFC 9421 — header present ⇒ extra signal, header absent ⇒ unchanged behaviour. Low cost to adopt is not a reason to adopt.
- **The two legs have different timing.** Agent accountability is forecast demand; age verification is present demand with legal deadlines. zkagent is one product with two positionings, not two products — same app, same chip read, same SDK, D13 makes the difference a mode flag. Rung 1 serves the age wedge; rung 2 is the agent bet.
- **M3 is a demo, not evidence of demand.** It proves the flow works; it proves nothing about whether anyone wants it.
- **Standing warning (NO-GO #10 lineage):** this document is the filter, not the collector — see `decisions.md` and `questions.md` for the current counts. M0 evidence status lives in `milestones.md`.

(zkagent-prd.md:2214-2224)

## 15. Close-out (D108, 2026-10-24)

zkagent is closed as a complete experiment. This section is the filter's final word; detail lives in
the wiki pages it points to.

**What shipped.**
- `chiproof` — the verifier SDK, on npm (`chiproof@0.7.1`, Apache-2.0, zero runtime dependencies), `packages/chiproof`.
- The reference scanner — signed Android APK on GitHub Releases (`zkagent-scanner-0.7.1.apk`, with its sha256), `apps/scanner`.
- The demo verifier — `apps/demo`, a local site acting as the requesting party, its own adopter of `chiproof`; run over USB with `adb reverse tcp:8787 tcp:8787` (D76).
- What each tier does: tier A returns one yes/no bit and carries no signature by design (D27, D77); tier B adds a per-site tag and a phone-key signature that the site binds on first sight (D38). The honest limits are in `docs/product/customer-guide.md` §10 and the README.

**Versioning.** Lockstep (D72): `packages/chiproof/package.json`, scanner `versionName`, one `CHANGELOG.md` section. The project stays on 0.x — the next release is 0.8.0, "no v 1.0" (D108(b)). "v1" in this PRD names a scope (rung 1), not a version number.

**Success criteria (§13) against evidence.**

| # | Criterion | Result | Evidence |
|---|---|---|---|
| S1 | M0 shows the same zktag twice from a real document, numbers measured | Met | `docs/logs/M0-EVIDENCE.md` Finding 3 (candidate tags byte-identical across two taps and two app processes; timing marks recorded). Caveat stated there: static signed data, so any correct read reproduces it; renewal stability untestable. |
| S2 | M1b: mode-A presentations compared, a planted stable field proven to break the check | Met, as revised by D22/D26 (not as literally worded) | `docs/logs/M1B-EVIDENCE.md` §4–5 and (d): salted fields differ per presentation; the planted-field control is caught; one cross-site-stable bucket (`vk_sha256` circuit class) is disclosed, not hidden (D26). The word "byte-identical" in S2 no longer holds: D22 made the requirement "nothing stable across sites". For the bare tier-A presentation the shipped demo shows exactly `challenge.nonce`, `challenge.issued_at`, `challenge.expires_at` differing (D77; `docs/logs/M3-POC-EVIDENCE-2026-09-04.md`). |
| S3 | A service integrates the verifier with zero PII handling and one documented dependency decision | Met | `apps/demo` is `chiproof`'s own adopter (`apps/demo/README.md`: tiers A/B carry no PII; `docs/logs/M3-POC-EVIDENCE-2026-09-04.md`). The integration dependency is `chiproof` itself; the demo also pulls `qrcode` (D68(b)) to render a page QR, which is not part of verification. |
| S4 | The demo shows a mode-B ban that survives identity reset | Met in substance; there is NO explicit "ban" UI | Duplicate-zktag refusal ("Already registered at this site") survives a server restart on both real documents (`docs/logs/M3-EXIT-ROWS-v0.6.0-2026-09-05.md` §4; `docs/logs/M3-POC-EVIDENCE-2026-09-04.md`). The zktag is stable across app reinstall + re-scan (`docs/logs/M2-SCAN-EVIDENCE.md` TEST 1, both documents; re-run on the real build, NL, `docs/logs/M2-DEVICE-SESSION-2026-09-03-PM-EVIDENCE.md`). A fresh device key presenting an already-bound zktag is refused `attester_key_mismatch`, device-confirmed after a full uninstall/reinstall (`docs/logs/M3-POC-EVIDENCE-2026-09-04.md` Session 4). The demo has no operator blocklist or ban control, and "new IP / new bots" were not exercised. |
| S5 | Every milestone has an evidence doc with deviations and retractions recorded | Met | M0 `M0-EVIDENCE.md`; M1 `M1-POC-EVIDENCE.md`, `M1-Q23-EVIDENCE.md`; M1b `M1B-EVIDENCE.md`; M2 `M2-SCAN-EVIDENCE.md`, `M2-DEVICE-EVIDENCE.md`, `M2-DEVICE-SESSION-2026-09-03-EVIDENCE.md` and its `-PM-` page; M3 `M3-POC-EVIDENCE-2026-09-04.md`, `M3-EXIT-ROWS-v0.6.0-2026-09-05.md`, `M3-SCANNER-S1-EVIDENCE-2026-09-05.md`, `M3-SCANNER-S2-S3-EVIDENCE-2026-09-05.md` (all under `docs/logs/`). |

**Known limits, stated with the criteria.** Captcha-grade, not bank-grade (NO-GO #5). Chip-cloning is an accepted, disclosed limitation: "one human, one zktag" holds only where `chip_auth` is true (FR11, D29). Tier A is unsigned by design; the tier-B key comes from no configured trust source and is bound on first sight (D38, D78). v1 is not zero-knowledge (NO-GO #7). The reference scanner ships with no device-attestation plug (D27), so client identity is only the attester key (S4 in `backlog.md`).

**Not delivered.** Everything in `backlog.md`: M3b/tier C is no longer parked (revived 2026-10-25, D109(h); tier C is built and device-checked and ships in 0.9.0, D116; the M3b milestone is still in build and 0.9.0 is not released), and the rest: §6.5 S4, the Play track, rung 2 (M4/M5), Track Z, the demo's own operator config, multi-threshold origins.
