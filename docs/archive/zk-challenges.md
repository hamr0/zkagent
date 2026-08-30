# zk-challenges — why zkagent isn't zero-knowledge yet, what that costs, and the roads out

> **Archived 2026-08-30 — merged into `docs/product/learnings.md`.** Its dated findings/measurements
> are now entries in that document's §§2–4; its explanatory narrative (not a dated finding) is
> preserved, deduplicated, in that document's §6. This file is kept for historical reference only
> and is no longer updated.

**Date**: 2026-08-30
**Companion to**: `docs/product/zkagent-design.md` and `docs/product/zkagent-prd.md` (PRD v1.8). Where those two disagree with anything here, they win — this is a standing answer to one recurring question, not a new commitment.

**Status**: Everything in §§1–5, §7 and §8 up to the measured facts is either quoted from the design/PRD or measured on 2026-08-29/30 and cited. §6, §9 and parts of §10 name unverified claims explicitly — they are flagged, not asserted.

---

## 1. The question the owner asked

In the owner's own words, roughly: *"I scan something we all have — a passport or ID with an NFC chip. Something trusted but non-central answers a boolean that can't identify me or link/profile me across sites, preferably not even on the same site. Why all these certificates? Why not compute a random id every time we answer?"*

This document is the answer, written once so it does not have to be re-derived every time the question comes back — and it will come back, because it is the right question.

---

## 2. 8een vs zkagent — the one-line difference and the flow

**8een verifies a proof someone else issued.** A government or bank issues a credential into an EU wallet app once. Per visit, the wallet computes a *fresh* zero-knowledge proof over that credential — "a validly-signed credential behind this proof clears the threshold" — and 8een checks the mathematics. The site never sees the credential, only the proof. Unlinkability is a property of the longfellow-zk scheme itself, **cited from its security analysis, not claimed by 8een** (8een README, "Why"). The trust root is the issuer list the verifier is configured with.

**zkagent verifies a document the government already issued — directly, with no issuer in between.** The phone reads DG1 (the machine-readable data) and the SOD (the government's signature over it) off the chip, verifies that signature locally against the public masterlist, checks the requested threshold, and sends one boolean (plus, in mode B, a per-site `zktag`) together with a proof that the *app* is genuine. The phone is the verifier of the document; the site only ever verifies the phone.

That's the whole difference: 8een's verifier checks math about a document it never sees; zkagent's phone checks a document directly and the site's verifier checks the phone.

**Decision recorded 2026-08-29 (PRD D21, M1 POC)**: the SOD never leaves the phone — it carries hashes of the holder's data groups, which functions as a fingerprint of that person's data, so masterlist checking against it has to happen on-device, not at the verifier.

| | 8een | zkagent |
|---|---|---|
| Credential source | EU wallet credential, issued once by a government/bank into the phone | The government-issued chip document itself (passport, ID card), read fresh each time |
| What the site receives | A zero-knowledge proof over the credential | A boolean (+ optional `zktag`) plus a hardware-signed attestation that the app is genuine |
| What makes it unlinkable | The math: a fresh proof every time, unlinkable by construction (cited, not built by 8een) | Nothing yet, structurally — see §3–§5. The attestation chain currently carries stable, device-identifying fields |
| Who you trust | The ZK scheme's soundness + the issuer's signing key | The government's chip signature (for identity) + the hardware vendor's attestation (for code integrity) |
| Dependency | google/longfellow-zk (vendored, Apache-2.0), an issuer trust list | A vetted chip-reading library (JMRTD), a public masterlist, a hardware attestation root (Google/Apple) |

---

## 3. Identifiable vs linkable — two questions, two axes

These are different questions and this document (and the PRD) keeps them apart on purpose:

- **Identifiable** — does the site learn *who you are*? Never true in tiers A or B; only true in tier C, and tier C is gated behind a pinned challenge-issuer key (PRD D19–D20).
- **Linkable** — can the site tell that *two visits are the same person*? This is the axis that varies.

| | Across sessions, same site | Across sites |
|---|---|---|
| **Tier A (anonymous)** | Linkable, by design of the web around it (cookies/IP/fingerprint) — see D22 below. zkagent itself adds no linker. | Must be unlinkable — no field in the payload may be stable across sites |
| **Tier B (pseudonymous)** | Linkable, by design — `zktag = HMAC(secret, domain)`; this is how a site blocks one human once and keeps them blocked | Unlinkable, by arithmetic — a different domain hashes to an unrelated tag with no shared value to join on |
| **8een** | Unlinkable (cited property of fresh ZK proofs) | Unlinkable (cited property of fresh ZK proofs) |

Two things worth separating out:

**The challenge nonce is never a linker.** It's the *site's* own fresh random number, generated per request and spent once. It says nothing about the holder and cannot be used to correlate two visits (PRD §1.1 glossary).

**D22 (2026-08-30, owner decision)**: tier A's same-site unlinkability was relaxed from a requirement to a non-goal. Reasoning: a site a holder returns to is already linking their visits through cookies, IP and browser fingerprint — promising that the *same* site specifically cannot recognise a return costs real cryptography (fresh ZK proofs per presentation) and buys little, since the site already has cheaper ways to do the same thing. What must still hold, and is now the actual requirement, is: **nothing in the payload is stable across sites.**

Why cross-site is the axis that actually matters: IP addresses and browser fingerprints are *probabilistic* — they rotate, get cleared, and don't reliably link two unrelated sites to each other. A hardware-rooted cryptographic identifier is *certain* and *permanent* — identical everywhere, a cookie you cannot clear. One site that already knows who you are — a bank, a shop with your delivery address — could use it to deanonymise you on every other site that emits the same field. The boolean was always harmless. The stable key underneath it would not have been.

---

## 4. What the certificates are for

The site does not trust the app just because the app says so: anyone can write, or modify, an Android app that prints `over_18: true` on screen without ever touching a passport. Without attestation, the boolean is just a fancier "☑ I am over 18" checkbox — no more trustworthy than the checkbox itself.

The attestation chain exists to say exactly one thing: **this answer came from the unmodified zkagent app, running on real hardware** — not "this answer is true." That's the whole job.

This is why a random id per answer, on its own, doesn't fix the actual problem. A random id fixes *privacy* (nothing to link) but says nothing about *authenticity*. A random id emitted by a genuine, unmodified app and a random id emitted by a fake app that never touched a chip look byte-for-byte identical. Privacy and authenticity are different axes, and the certificates are entirely about the second one.

The app also cannot vouch for itself. A self-signed "trust me, I'm the real app" claim is just the checkbox again, one layer down. So every route that lets the site trust the *code* rather than the *math* eventually leads to a central voucher — Google or Apple, the two parties who can sign a statement about what hardware and software actually ran. That dependency **is** lock-in, and the attestor's validity rests on exactly what the owner named it as: authority and adoption, not cryptography.

---

## 5. What the M1 POC measured (2026-08-29, `docs/logs/M1-POC-EVIDENCE.md`)

The obvious fix — mint a fresh attestation key for every request — was tried and measured, not assumed. It only rotates part of the chain.

- Generating a fresh key each time only changes the **leaf** certificate. The Pixel 6a's chain has a stable device-identifying certificate above it in both configurations: on StrongBox, a factory-provisioned keybox certificate (byte-identical across runs); on TEE, a Remote-Key-Provisioning certificate that Google reissues roughly every two weeks but which is likewise identical across every presentation made within that window and identical to *every site* the phone talks to in that window. Alongside it: stable `verifiedBootKey`, `verifiedBootHash`, and OS/vendor/boot patch-level fields, all identical across runs and across sites.
- Analogy: minting a fresh key per request is like using a new envelope every time you mail something — but every envelope in this design still carries the same postal stamp. The stamp is what a colluding recipient (or one recipient comparing notes across time) uses to tell it's the same sender.
- **Why 8een escapes this entirely**: it has no hardware chain to inspect in the first place. There is no device certificate anywhere in the payload — the math of the proof *is* the whole proof. There's nothing structurally analogous to strip.

Other things the same POC established:

- Attestation parsing and chain verification are implementable stdlib-only — a 163-line DER walker, zero runtime dependencies (satisfying the project's dependency-hierarchy rule).
- The verifier initially accepted a chain rooted in an *expired* Google root certificate (checking signatures without checking dates). This was a real bug, since fixed. With date-correctness enforced, the Pixel 6a's actual StrongBox chain fails verification today — its root expired 2026-05-24. (Whether Google's own production verifier enforces the same check is unverified — flagged in the PRD as open, Q14 sub-item d.)
- The nonce mechanism was verified working: the phone accepts a caller-supplied challenge value and bakes it into the attestation. What is *not* yet built is the end-to-end issue-and-spend flow at a verifier. Either way, a nonce fixes **replay** — it does not touch **linkability**, which is a property of the rest of the payload.

---

## 6. Two ways to use ZK, and why one of them is out

There are, in principle, two different places a zero-knowledge proof could go in this design:

**(a) ZK-prove the attestation chain itself** — hide the device fingerprint behind a proof instead of removing it. This would require circuits over several ECDSA signature verifications plus X.509 certificate parsing. The vetted circuit library this project would use, `google/longfellow-zk`, has circuits only for the ISO mdoc format 8een consumes — not for X.509 attestation chains. Building new circuits for this would mean writing "our own cryptography," which both this project's rules (D1, NO-GO #7) and 8een's precedent forbid outright. **(a) is ruled out.**

**(b) ZK-prove the passport itself, and drop attestation entirely.** The claim becomes: "I hold a DG1+SOD signed by a government key present in the public masterlist, and the date of birth in it puts me over threshold T" — proved fresh, and unlinkable by the math, every single time, the same way 8een's proofs are. Once the claim is a mathematical proof, the site no longer needs to know or care which app produced it — which means no attestation, no FR10 trust list, no Pixel-only development constraint, and Q23 (the tier-A/attestation conflict, §10) simply does not arise.

Structurally, (b) is identical to what 8een already does: the math proves the claim, so the device carrying it doesn't need to be trusted or identified. Today's zkagent design is the opposite shape — the device is trusted, which means it must be identified, which is exactly the thread that, pulled on, produced Q23, FR10, the expiring-root problem, and the Pixel-only development constraint. Every one of those is downstream of one design choice: trust the device.

Several existing projects reportedly already build (b) over passport chips: **zkPassport, OpenPassport/Self, Rarimo, and Anon Aadhaar (as a method)**. **This is unverified as of writing — a survey of these projects is running, not yet complete.** It matters beyond curiosity: the PRD's risk #1 currently states that issuer-free derivation is "unpublished" in the literature (PHC papers assume an issuer). If these projects already publish an issuer-free, chip-derived ZK proof, that claim needs to be re-checked against them and either narrowed or withdrawn — flagged here, not asserted, per the PRD's own note under risk #1.

---

## 7. The three honest products

| | Honor-grade | Voucher-grade | Math-grade |
|---|---|---|---|
| **What it is** | No attestation; a random id (or none) each time | Play Integrity / App Attest: "Google/Apple says real device + real app" | ZK proof over the passport itself, no attestation |
| **What the site actually gets** | A checkbox with extra steps | A believable answer, backed by a central vendor | An answer backed by math, backed by nothing else |
| **Lock-in** | None | Google + Apple | None |
| **Privacy** | Perfect | Linkability moves to Google/Apple (they see every check), not removed | Unlinkable by construction, even same-site |
| **De-Googled / custom-ROM phones** | Fine | Excluded (risk #7) | Fine |
| **Cost to build** | Small — legitimate captcha-grade fallback | Small, if the spike passes | Large: circuit library dependency, on-phone proving time, per-algorithm coverage (RSA-2048/4096, ECDSA P-256/384, SHA-1/256/512…), a new language in the stack (Noir/Circom/Rust), young and less-vetted toolchains — some with crypto-token ecosystems attached, D1 reversed |
| **Legal weight** | Worthless — a modified app can lie for free | Fine at the site if the underlying spike passes | Fine, same as voucher-grade |
| **Chip cloning (Q18)** | N/A | Unaffected either way (attestation targets code integrity, not the document) | **Gets worse, not better** — a proof over static chip data cannot include a live AA/CA challenge-response, so tier-B uniqueness stays clone-replayable for every single document, not just documents lacking AA/CA |

Only two things a site can ever actually trust: **the code that produced the answer** (which leads to a central voucher) or **the math of the answer itself** (which leads to ZK circuits). There is no third option that avoids both.

Component-level facts behind the math-grade row are in [zk-due-diligence.md](zk-due-diligence.md) (2026-08-30).

---

## 8. Play Integrity in simple terms

Option 3 from Q23's list, spelled out: the app asks Google to vouch for it. Google checks the device and the app, and returns a sealed, Google-signed verdict. The site checks Google's signature on that verdict, not anything about the device directly. The party that actually knows the device (Google) is not the party asking the question (the site) — which is exactly why it removes the per-device fingerprint from the site's view, at the cost of Google now seeing every check.

**Measured facts, 2026-08-30** (agent research, cited from developer.android.com/google/play/integrity/{setup,overview,standard,classic,verdicts}):

- No Play Console account is needed for a POC — a free Google Cloud project with the Play Integrity API enabled is enough.
- **Standard tokens** can only be decoded server-side, via Google's own `decodeIntegrityToken` call with a service account. This means Google sees every single check. Measured decode latency: roughly 10 ms.
- **Classic tokens** can be decrypted locally, but the decryption keys must come from Play Console.
- Default quota: 10,000 requests/day.
- A **sideloaded app** (not installed via Play) gets a `deviceIntegrity` verdict back with `appRecognitionVerdict: UNRECOGNIZED_VERSION`.
- Verdict schema: `requestDetails` (per-request), `appIntegrity` (per-app), `deviceIntegrity` (per-device — with `deviceRecall` and `recentDeviceActivity` as optional, per-device-by-design opt-ins), `accountDetails`, `environmentDetails`.
- Dependency cost, measured: +3 build artifacts (`play-services-basement`, `play-services-tasks`, `core-common`) — not the full Google Mobile Services suite.

**Status**: a probe and a stdlib decoder were built (`spikes/m0/M1IntegrityProbe.kt`, `spikes/m1-integrity/node/`) but **not yet run** — the run is blocked on the owner completing the Google Cloud Console setup steps, and it is queued behind the ZK-passport spike (§10).

---

## 9. iOS and other makers

**Everything in this section is UNVERIFIED** — orchestrator background knowledge only, to be checked before any of it enters the PRD proper.

**iOS App Attest** builds the voucher model in directly: a key is generated in the Secure Enclave, and Apple issues a per-key certificate under Apple's own shared App Attest CA — so what the site sees carries no separate per-device certificate the way Android's key attestation does. Apple sees every attestation. The receipt and environment fields Apple returns still need to be measured before any comparison to the Android findings can be made. The gate to even try this is unchanged from the PRD's existing position: $99/year, an NFC entitlement, and a Mac — iOS remains a non-goal for this project.

Android device landscape, by route:

| Device class | Play Integrity | Key attestation |
|---|---|---|
| Stock Android + Google services (Pixel, Samsung, Fairphone) | Works | Works, with the device fingerprint |
| /e/OS, LineageOS, AOSP | Fails | Only if the factory keybox survived the flash (often doesn't, on non-Pixel) |
| GrapheneOS on Pixel | Fails, by design | Works, and reports a custom OS |
| Huawei / other China-market ROMs | Neither (already excluded by D2) | Neither |

The two Android routes have opposite failure modes: Play Integrity is inclusive of ordinary users but excludes exactly the privacy-conscious people this project's ideals appeal to; key attestation is the mirror image. After D22, a de-Googled phone has **no** compliant route left except a split verifier (option 2 in Q23's list) or the math-grade route (option 5 / §6(b)). Neither Android route is issuer-free in the attestation sense — the PRD's standing correct phrasing holds regardless of which is picked: **"issuer-free identity, vendor-rooted attestation."**

**Proposed, not yet recorded as a PRD item**: **Q24** — de-Googled devices have no D22-compliant route under either Android option; is that accepted for v1, or not?

---

## 10. Where this leaves Q23, and the decision taken 2026-08-30

Decision taken 2026-08-30: see §14.

The owner's actual goal, stated in §1, is the math-grade row of §7's table. Decision taken 2026-08-30:

**Primary spike: ZK-passport feasibility.** Survey the candidate libraries named in §6 → capture the raw DG1+SOD and the SOD's actual signature algorithms off the owner's US passport and NL identity card → attempt a desktop-side proof on the owner's Linux machine → only then measure on-phone proving time.

**Play Integrity spike: queued**, to run once the owner's Cloud Console setup is done — kept in the plan (not dropped) because a real measured number for the voucher-grade row is what keeps this whole document honest instead of a comparison of one measured row against two guessed ones.

**Success criteria for the ZK spike, written down before the run** (so the result can't be rounded up after the fact):

1. The chosen library actually covers the SOD signature algorithms present on the owner's real documents.
2. A proof over the real US passport verifies on desktop.
3. Two proofs generated for the same site are byte-different from each other.
4. Proving time is recorded — first on desktop, then on the Pixel.
5. The verifier runs in plain Node with no blockchain or RPC dependency.
6. The library passes this project's standing external-dependency checklist (maintained, lightweight, established, security-aware).

Any one of these failing is a **finding**, to be recorded honestly, not a setback to be argued around — and if it fails, honor-grade (§7, column 1) remains the fallback, exactly as it already is in the PRD.

**Update 2026-08-30 (late)**: Play Integrity passed the cross-site bar on the Pixel 6a; the ZK composition verified on desktop for both documents (~16 s, 546 MB, 59 KB); no phone proving number yet (no Android bb binary is published; WASM is ~2.2× native); due diligence recorded separately. Q23 decision still open.

---

## 11. Claims we may make today

Stated precisely, so nothing here gets rounded up:

- **No identification at any tier** currently shipped (tiers A and B; tier C is a gated future tier and identification only happens there, by design, when explicitly requested).
- **No cross-site linkability**, once the attestation payload stops leaking a globally stable field (§5 — not yet true today; this is the open problem Q23 names).
- **Same-site recognition exists only in tier B, and only by the site's own explicit request** (D13, D19).

Claiming "zero-knowledge" for any of this today is an overclaim, and the design document already says so plainly (`zkagent-design.md` §5, "Is this zero-knowledge? No"): it does not become true until the math-grade road (§6(b), §10) actually ships and is measured.

---

## 12. Three layers — who builds what

Any ZK-passport system splits into three layers, and only one of them is ours to build:

| Layer | What it is | Examples | Do we build it? |
|---|---|---|---|
| **1. Engine** | Proof machinery — turns a computation into a proof, and checks a proof (e.g. `bb verify`) | longfellow-zk (Google), Barretenberg/Noir (Aztec) | **Never** — years of cryptography |
| **2. Circuits** | The specific computation written for the engine: check RSA signatures, hash DG1, compare birthdate | zkPassport's Noir circuits, Rarimo's | **Not by default** — a few hundred lines, but every line must be right or the proof lies silently; whoever writes it needs an audit |
| **3. Product verifier** | Normal code: issue the nonce, call the engine's verify, check scope/threshold/trust list, return `{ok, allowed}` | 8een's zk8een, our `chiproof` | **Yes — this is ours** |

"Vanilla is the hole in the wall" (this project's dependency-hierarchy rule) applies to layers 1–2 only. Layer 3 does no math — it calls layer 1 the same way a browser calls a TLS library.

8een is the template: longfellow-zk is layers 1+2 for 8een's use case; 8een itself is layer 3.

What "the math works" means, precisely: **soundness** (nobody can make a receipt for a false statement) plus **zero-knowledge** (the receipt leaks nothing). The engine has years of research behind those two properties. The circuit is where real bugs live — an under-constrained circuit proves false things and nobody notices. Tonight's DG1-flip passing the signature-only circuit is exactly that shape, by design.

---

## 13. The names, sorted

**Engines** (layer 1):

- **longfellow-zk** (Google, IETF draft) — mDoc/mDL, JWT, VC. What 8een uses. Does **not** read passports.
- **Noir + Barretenberg** (Aztec) — general-purpose toolkit. zkPassport's circuits are written in it.
- **Circom + Groth16** — older toolkit. Used by Rarimo and Self.

**Passport-specific ZK projects** (the math-grade road, layers 1+2+3 built by others):

- **zkPassport** — Noir, Apache-2.0, chain-free verification possible; circuits open, phone app closed; acquired by Aztec Labs 2026-05-27; this is what the spike ran.
- **Rarimo** — Circom, MIT, Halborn 2024 audit, own-chain registry.
- **Self/OpenPassport** — Circom; Celo + Google TEE; ruled out.
- **Anon Aadhaar** — India's Aadhaar QR, not a passport; method only.

**Plain readers** (no ZK, no privacy — they show everything):

- **JMRTD** — the chip library inside our M0 app.
- **tananaev/passport-reader** — the app M0 forked.

**One-line relation**: 8een = longfellow-zk + a verifier. zkagent on the math road = zkPassport circuits + a verifier. Same shape, different credential and engine.

**Are we doing what they do?** On the math road, the core idea is theirs, already built — tonight's spike ran their circuits. What would be ours is what is already 8een's role: the boring, adoptable web verifier (plain Node, no chain, no token, adopter-held trust, honest claims, the tier model). Whether these projects already fill that gap with their own SDK is a market question — unchecked.

---

## 14. What due diligence found and the decision (D23, 2026-08-30)

Summarised from [zk-due-diligence.md](zk-due-diligence.md) — read that for the full detail:

- Every passport-capable engine/circuit set is pre-1.0 or unaudited.
- Noir/Barretenberg disclosed a forged-proof soundness bug in 2026-03 (fixed v4.1.2), and a second critical bug in the v5 alpha in 2026-07.
- No standalone UltraHonk core audit was found.
- **zkPassport**: real users, no published audit, ordinary bug fixes including the outer/aggregation circuit, closed app, and an optional OPRF nullifier with undisclosed operators.
- **Rarimo**: audited in 2024 but materially changed since, including an under-constrained-circuit fix and a 2026-07 RSA/SHA-1 fix; token/chain-native.
- Writing our own circuits: an estimated 6–12 engineer-weeks (unsourced) on experimental, unaudited RSA/SHA libraries.
- No non-crypto organisation has shipped ZK-over-passport.
- The EU framework scopes ZK to wallet-issued credentials, not raw chip data.

**The decision**: v1 stays voucher-grade (Play Integrity). D1 stands. ZK becomes **Track Z** — a named second track with written gates, none of which are met yet:

1. Engine at a stable release with a published core audit.
2. Independent audit of the exact four circuits used.
3. Measured phone proving time under a PRD-set UX ceiling.
4. A chain-free nullifier with known operators, or none at all.
5. An open-source on-device prover.

**What carries over unchanged when Track Z lands**: tiers (D19), signed challenges (D20), the verifier core, the trust list — only the voucher swaps out for a proof.

**v1 claims, stated precisely**: no identification; nothing stable across sites; same-site recognition only in tier B, and only by request; Google decodes every check; de-Googled devices are excluded (Q24); the words "zero-knowledge" are not used for v1.

**Phone proving number**: not obtained. No Android `bb` binary is published, only a static library; WASM measured roughly 2.2x native speed and 1.2x memory on desktop. The NDK harness to actually measure phone proving time is Track Z's first task.

---

## 15. Could longfellow be adapted instead? (asked 2026-08-30, being checked)

**Convert passport to mdoc**: no. An mdoc is a credential signed by an issuer. A converted one needs a *new* signature from someone — either the phone (nobody trusts its key, which is back to attestation) or a conversion service (which sees passports and signs credentials — that service **is** an issuer, central, exactly the eIDAS-style adversary NO-GO #3 rules out).

**Adapt longfellow's circuits to ICAO structures** (ASN.1 SOD instead of CBOR MSO, RSA-2048/SHA-256 instead of ECDSA P-256, DG1 hash, date compare): possible in principle. This is layer-2 work on a better-reviewed engine (Google-authored, three external reviews, IETF draft) — harder to write, since there's no circuit DSL, only C++ against the framework — and our circuits would still need their own audit regardless.

**Checked 2026-08-30** (source-tree read of google/longfellow-zk, 2,689 paths): **no RSA circuit exists anywhere** (C++ or Rust); the JWT path is ES256-only; no ASN.1/DER parser; no big-integer modular exponentiation (the Rust `bignum` helper is a ~35-line bit converter); no SHA-1. Reusable: SHA-256 (`lib/circuits/sha/flatsha256_circuit.h`) and the circuit-authoring framework. Adapting longfellow to passports means writing RSA-2048/4096 verification and DER parsing from scratch in hand-written C++ against the sumcheck framework, then either upstreaming through three Google CODEOWNERS or maintaining a fork — the largest layer-2 project on the table, and our circuits would still need their own audit. Useful pattern to copy on any engine: longfellow keeps the issuer public key *outside* the circuit as a public input (`mdoc_signature.h`), so DSC→masterlist trust can be checked by plain code rather than proven in-circuit. Precedent for community credential circuits exists (an SD-JWT VC PR, ECDSA-based); passports/ICAO/RSA have never been discussed there. Status: Track Z's realistic engine remains Barretenberg (RSA circuits exist and ran tonight); longfellow is a watch item, reopened only if Google adds RSA.

---

## 16. Where everything lives

- **Evidence**: `docs/logs/M1-POC-EVIDENCE.md` (attestation chains), `docs/logs/M1-Q23-EVIDENCE.md` (Play Integrity + ZK composition).
- **Facts**: `docs/product/zk-due-diligence.md`.
- **Decisions**: PRD D22, D23, Q23 (resolved), Q24.
- **Spikes**: `spikes/m1-attest`, `spikes/m1-integrity`, `spikes/m1-zk` (`phone/` for the proving attempt).
- **Commits**: `85d684a` (M1 POC), `c3ee545` (Q23 spikes).
