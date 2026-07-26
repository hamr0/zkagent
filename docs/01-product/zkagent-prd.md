# zkagent — PRD v1.0 (draft)

**Status**: Draft — owner decisions D1–D8 signed off 2026-07-26; awaiting M0 evidence before any promise hardens.
**Owner**: hamr · **Repo**: zkagent (new; sibling of 8een) · **npm**: `zkagent` (reserve `0.0.0` placeholder — owner action, browser UI)
**Parent standards**: `AGENT_RULES.md` (POC-first, dependency hierarchy, prove-don't-assert, security invariants). When anything here disagrees with AGENT_RULES, AGENT_RULES wins.

**One-liner**: Prove there is exactly one accountable, blockable human behind an AI agent — captcha-grade, anonymous, no CA, no ZK circuits in v1 — rooted in the passport chip the government already issued.

---

## 1. Problem

Agent traffic passed human traffic and every emerging auth standard (Web Bot Auth, Visa TAP, Google AP2, OAuth on-behalf-of drafts) roots trust in a **vendor** ("OpenAI's agent") or a **custodial account** (an IdP login — free, infinite, ban-proof). The IETF's own drafts name the gap: the `sub` claim is "routinely overloaded… without a standard classification mechanism"; delegation chains have no anchored origin. The personhood-credentials literature (arXiv 2408.07892, 2501.09674) calls for exactly the missing piece — a unique, privacy-preserving, human root — but assumes an *issuer* nobody has stood up.

zkagent supplies the root, issuer-free: the passport chip **is** the already-issued credential.

## 2. What v1 is

```
SCANNER APP (thin native iOS app; the ONLY native piece)
  1. NFC-read passport chip            (vetted lib: NFCPassportReader — never our parsing)
  2. Verify government signature       (public masterlist — ICAO PKD / BSI; no CA of ours)
  3. secret = KDF(chip stable data)    (never leaves device; Secure Enclave, biometric-gated)
     tag    = HMAC(secret, verified service domain)
  4. App Attest: Apple signs "genuine iPhone, unmodified app ran steps 1–3"
  5. Hand {tag, over18: bool, attestation, challenge-nonce} to the web flow (QR / universal link)

VERIFIER SDK (Node, stateless — the npm package services install)
  verify attestation (Apple root) → verify challenge nonce (single-use)
  → check adopter-supplied blocklist → verdict {ok, allowed, reason}

AGENT LAYER (after the human loop works)
  delegation cert: {agent pubkey, tag, scope, expiry, serial} signed under the tag
  agent signs requests per RFC 9421; verifier checks chain + blocklist
```

- Passport data never leaves the phone. A service sees: a tag, a yes/no, a signature chain.
- Same human + same service = same tag, forever (deterministic). Different services = unlinkable tags.
- Ban the tag → all the human's agents die at that service, and there is no re-mint (no second passport).
- Forging requires beating Apple hardware attestation or forging a government chip — far above the captcha bar this product promises. **Not above a bank's bar. Never claim otherwise.**

## 3. The invariant (inherited from 8een, adopted verbatim)

**`ok` (did the checker manage to check) is separate from `allowed` (what the answer was), and `ok:false` ⇒ `allowed:null`, never `false`.**

A broken verifier saying "no" is indistinguishable from a working one — it would turn away every legitimate human while looking healthy. Concretely:

| Condition | Verdict |
|---|---|
| tag on blocklist | `ok:true, allowed:false` (real no) |
| attestation invalid / nonce replayed | `ok:true, allowed:false` (real no) |
| masterlist half-loaded, blocklist store unreachable, Apple endpoint down | `ok:false, allowed:null` — never a "no" |

Corollary (8een's recurring bug shape, found 7+ times there): **never trust a health check, a config value, or a client-supplied field.** The masterlist is a PEM list — assume it can silently half-load (19-in-file-17-parsed) and prove it can't.

## 4. Goals / Non-goals

**Goals (v1)**
- G1: One human ↦ one stable tag per service, from a passport scan, iPhone-first.
- G2: Captcha-grade verification a service adopts with one npm install and zero PII handling.
- G3: Agent delegation certs + RFC 9421 request verification riding the tag.
- G4: Blocklist + pseudonymous appeal, all state adopter-supplied.

**Non-goals (v1) — the scope-creep magnets, named explicitly**
- ZK circuits (D1 — future tier, not built, not scaffolded).
- Android (until a device exists; design must not preclude Play Integrity).
- Money/payments, legal-grade identity, one-person-one-vote (k-bound makes it dishonest — see NO-GO #5).
- EU wallet / mdoc / eIDAS integration (the "EU way" we're diverting from; zk8een remains the bridge if ever needed).
- **National eID cards** — v1 reads passports only (NFCPassportReader's domain). eID cards are a different read path; later.
- **Predicates beyond `over18`** — v1 discloses exactly two claims: unique tag + over-18. No under-18 spaces, no nationality, no residency. Each added predicate leaks anonymity bits and grows the scanner; new predicates require a PRD change (NO-GO #10).
- **signedreply / attestation-ledger / reputation integration** — separate product; zkagent must stand alone. No cross-product coupling in v1.
- **Federated/shared blocklist service** — v1 defines the signed blocklist *format* and adopter store interface only. We run no list, host no reputation.
- **Tag continuity across passport renewal** — deferred to D9 evidence; at captcha-grade a ~10-yearly tag rotation may simply be acceptable.
- **Browser extension, desktop scanner, or any second client** — one scanner app, one verifier SDK, one demo page. Nothing else.

## 5. Milestones — small buckets, each with a checkpoint

| M | Deliverable | Checkpoint (evidence, not prose) |
|---|---|---|
| **M0 — POC at the riskiest assumption** | Throwaway iPhone spike: read owner's real passport, verify SOD vs public masterlist, derive tag, **rescan → identical tag**; second passport/ID if findable → different tag | Two scan runs logged with matching tag; timings measured (scan, verify, derive); masterlist load count asserted against file count. Evidence doc `docs/02-evidence/M0-EVIDENCE.md`. POC is thrown away, never shipped |
| **M1 — Verifier SDK core** | `verdict.js`-style never-throw classifier; challenge nonce (port 8een `challenge.js` pattern); App Attest verification against fixtures captured from the M0 device | Full negative matrix runs (bad attestation, replayed nonce, half-loaded masterlist ⇒ `ok:false`); every reject-test paired with a non-vacuity pass-test |
| **M2 — Scanner app (rewrite, not graduate)** | Real app: enclave storage, biometric gate, App Attest wired, QR/universal-link handoff | End-to-end on real device against local verifier; tag stability across app reinstall + re-scan measured |
| **M3 — Captcha-replacement demo** | Web page: "prove you're a unique adult human" via phone scan; responsive (mandatory) | Live flow demo; second scan from same passport rejected as duplicate tag (uniqueness shown, not asserted). *Clarification vs NO-GO #1: the demo site acts as its own adopter and keeps its own seen-tags store — the zkagent SDK still stores nothing* |
| **M4 — Agent layer** | Delegation certs, RFC 9421 middleware, per-serial revocation | Agent request accepted with valid chain; killed by tag-block; single-use serial burns once |
| **M5 — Blocklist/appeal** | Signed blocklist format, adopter store interface, prove-control-of-tag appeal | Replay 8een's store pattern: fails closed, never silently falls back to in-memory |

One milestone at a time. Each works alone before the next integrates.

## 6. Riskiest-assumption register (what M0 must answer — no PRD promise survives a miss)

1. **Issuer-free derivation works**: chip's stable data is readable, verifiable against a public masterlist, and yields the same secret on every scan. (The PHC literature assumes an issuer; nobody has published the issuer-free variant. This is the novel bit — and the whole product.)
2. **NFCPassportReader + owner's actual passport + owner's dev account/NFC entitlement** actually cooperate on this desk, this month.
3. **Masterlist coverage**: owner's issuing country's CSCA cert is present and current in the free public lists.
4. **App Attest verification is implementable within our dependency rules** (see Open Question Q1).
5. Derivation-field choice: document number (changes at renewal → tag rotates ~10-yearly — acceptable at captcha grade?) vs personal number where present. M0 reports what the chip actually contains; decision D9 taken after, on evidence.

## 7. Requirements (condensed — full behavior specified per-milestone at build time)

- FR1 Scanner: vetted-lib chip read; local SOD verification; KDF in enclave; no telemetry, no account, no network call except masterlist refresh.
- FR2 Tag: `HMAC(secret, verified-domain)`; domain computed client-side (FIDO-style origin binding), never accepted from the server.
- FR3 Verifier: stateless; never-throw; verdict `{ok, allowed, reason}`; all stores adopter-supplied and failing closed.
- FR4 Challenge: HMAC self-authenticating nonce, single-use spend, atomic store shape (Redis `SET NX PX`) — 8een piece-2 design reused.
- FR5 Delegation: VC-shaped cert, per-agent serial, individually revocable, expiry mandatory.
- FR6 Uniformity: all clients emit identical-shaped payloads (fixed sizes/versions); metadata must not fingerprint (decided v1, not retrofittable).
- FR7 Any web surface is responsive, mobile-first (AGENT_RULES hard requirement).

## 8. NO-GO table — check before proposing any feature

| # | NO-GO | Why |
|---|---|---|
| 1 | **We store nothing server-side. Ever.** No identity, no chip data, no tags-at-rest, no logs of who verified | Statelessness is the security argument, not a limitation (8een NO-GO #7 lineage) |
| 2 | **No custom security-critical code**: chip parsing, attestation parsing, crypto — vetted libs / platform APIs / stdlib only | AGENT_RULES; 8een NO-GO #8 lineage. If the answer is "write our own X" and X is cryptographic or parses untrusted input, the answer is wrong |
| 3 | **No CA, no issuer, no enrollment server run by us** | Issuer-free is the product. The day we run an issuer we've rebuilt the thing we set out to kill |
| 4 | **No unmasking capability** — not escrowed, not quorum-gated, not "for emergencies." Max penalty = exclusion | A capability that exists can be compelled. Owner decision, final |
| 5 | **Never claim "one human = one tag"** — always "at most k (k = documents held, ~1–3)". Never claim more than captcha-grade assurance. Never describe v1 as replay-safe/sybil-proof beyond what a measurement showed | Overclaim is the death of a trust product; 8een's evidence-doc discipline applies |
| 6 | **No web-NFC scanner** — the scan is native, period | Platform wall (NDEF-only browsers), not a preference |
| 7 | **No ZK circuits in v1** — not built, not vendored, not scaffolded "for later" | D1. Captcha-grade bar; every line must have a purpose today |
| 8 | **No npm publish until the package is standalone-usable** — placeholder reservation only; publishing is a deliberate manual owner step | zk8een binary-distribution lesson, verbatim |
| 9 | **No secrets/test keys in the tree** — runtime-generated, temp dirs only | AGENT_RULES + 8een PRD §10 |
| 10 | **No feature enters a milestone unless it's in this PRD first.** New idea → PRD change → owner sign-off → build. Mid-milestone additions are refused by default, including owner-tempting ones ("while we're in there…") | The scope gate. This project's conversation history generates ideas faster than any team could build them; the PRD is the filter, not the collector |

## 9. Owner decisions (signed 2026-07-26)

| D | Decision |
|---|---|
| D1 | v1 trust root = government chip signature + OS attestation. No ZK circuits in v1; ZK is a named future tier |
| D2 | Native thin scanner app (iOS first, owner's own app wrapping NFCPassportReader); everything else web |
| D3 | Stateless, 8een-style: blocklist/nonce stores adopter-supplied; we store nothing |
| D4 | `ok`/`allowed` invariant adopted verbatim (§3) |
| D5 | `tag = HMAC(chip-derived secret, verified service domain)` — client-side scope binding |
| D6 | New repo; zk8een reused as lessons + `challenge.js` pattern + verdict/test discipline; zk8een repo untouched |
| D7 | Name: **zkagent** (repo + npm). Verifier SDK ships via npm; scanner app via TestFlight/App Store |
| D8 | Issuer-free derivation is the named riskiest assumption; M0 targets it before anything else is built |
| D9 | *(open — taken after M0 evidence)* derivation field: document number vs personal number; renewal-rotation acceptability |

## 10. Open questions (resolve at the milestone that hits them, on evidence)

- **Q1 (M1)**: App Attest verification needs CBOR + X.509 parsing of untrusted input. AGENT_RULES demands a vetted lib for that; zk8een tradition demands zero runtime deps. One well-vetted, dependency-light attestation-verification lib may be the honest exception — decide at M1 against the External Dependency Checklist, not before.
- **Q2 (M0)**: does the owner's Apple dev account already carry the NFC tag-reading entitlement?
- **Q3 (M3)**: uniqueness demo needs a second document (borrowed, consenting) to show different-passport ⇒ different-tag; plan for it.
- **Q4 (M4)**: delegation cert format — plain JSON+sig vs W3C VC envelope. Start plain (simplicity advocate); adopt VC shape only when an integration (AP2 mandate embed) actually needs it.
- **Q5 (later)**: Android + Play Integrity when a device exists.

## 11. Grounding (why this isn't a dart in the dark)

- Personhood credentials called for, issuer assumed, none stood up: arXiv **2408.07892** (OpenAI/Microsoft/Harvard et al.); delegation-to-agents follow-up: arXiv **2501.09674** (MIT et al.)
- IETF pipes without a water source: `draft-oauth-ai-agents-on-behalf-of-user`, `draft-klrc-aiagent-auth`, AIP `draft-prakash-aip`, WIMSE — delegation chains rooted in custodial IdP accounts; `sub`-overloading and chain-splicing named as open gaps
- Transport converged on RFC 9421 (Web Bot Auth live at Cloudflare edge since 2026-03; Visa TAP same base) — zkagent attaches as fields, competes with nothing
- Deployed precedent for attestation-backed anonymous auth at captcha-grade: Apple Private Access Tokens
- Prior art for passport-derived proofs (ZK variant, web3-aimed, no agent story): zkPassport, Self, Anon-Aadhaar — de-risks the chip side, validates the gap on the agent side
- Trust root: ICAO Doc 9303 (signed chip + SOD), public CSCA masterlists (ICAO PKD, BSI). Passport numbers change on renewal (drives D9)

## 12. Success criteria

- S1: M0 evidence shows same-tag-twice from a real passport, all numbers measured (no guessed timings — 8een's 10×-wrong lesson).
- S2: A service integrates the verifier with zero PII handling, one dependency decision documented.
- S3: The demo shows a ban that survives identity reset (new bots, new keys, new IP — same human blocked).
- S4: Every milestone has an evidence doc with deviations and retractions recorded, 8een-style.
