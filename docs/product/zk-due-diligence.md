# zk due diligence — engines, circuits, precedent (2026-08-30)

**Preface**: This report was requested to assess candidate components — proving engines, circuit libraries, and prior art — for a chain-free, phone-proving, age-from-passport product (the math-grade road named in `zk-challenges.md` §6(b) and §10). It was produced by a research agent on 2026-08-30. It contains findings only: what each component is, what has been audited, what bugs are known, who backs it, and what lock-in it carries. **Nothing in this document is a recommendation.** The decision of what, if anything, to adopt is reserved for the owner.

---

## A. Engine — Noir + Barretenberg (UltraHonk), Aztec

| Column | Findings |
|---|---|
| **Status** | Both pre-1.0/beta. Noir at v1.0.0-beta.26 (2026-07-31) with near-daily nightlies, no stable 1.0 tag. Barretenberg lives in the AztecProtocol/aztec-packages monorepo (standalone repo archived); latest tagged binary v4.1.2 (2026-03-26, a security patch), broader stack moving through v5-alpha toward v6 later in 2026. No published 1.0 migration/breaking-change doc exists yet. Sources: github.com/noir-lang/noir/releases, github.com/AztecProtocol/aztec-packages (fetched 2026-08-30). |
| **Audit** | Bigfield (a Barretenberg big-int primitive) audited by zkSecurity, Zellic, and Spearbit, completed 2024-12-09 (aztec.network/blog, fetched 2026-08-30). ZK Email's Noir circuits audited by Consensys Diligence (2024-12, findings on regex soundness/compiler immaturity/SHA256 template issues — summary only, PDF fetch 403). **No standalone public audit of UltraHonk's core arithmetization itself was found** — only a sub-primitive (Bigfield) and a third-party circuit library (ZK Email), not the proving system. |
| **Known bugs** | Critical soundness bug disclosed 2026-03-17/27: forged proofs could be accepted by Barretenberg — found independently by TU Vienna and Consensys Diligence, fixed in v4.1.2. A second critical vulnerability was found in the V5 Alpha prover on 2026-07-27 (less-verified, search-snippet sourced). This is a real, recent, recurring pattern — not theoretical. |
| **Backing** | Aztec Labs: ~$119–180M raised (figures diverge across sources) from a16z, Consensys, A.Capital, Samos; a $40.2M token sale in Dec 2025 at $480M valuation. Network status: partial "Ignition" mainnet (block production only, no contract execution) launched 2025-11-19; testnet still runs Alpha proving software. Aztec's own flagship deployment is pre-GA. |
| **Lock-in flags** | None chain-specific for Noir/Barretenberg as a proving stack (it's usable standalone), but the whole ecosystem's release discipline and audit cadence trail Aztec's own roadmap, not an independent standards body. |
| **Verdict-worthy facts** | `nargo` has default-on static checks (Brillig-call-constraint check, underconstrained-value check) that can be disabled via flags but shouldn't be for production; the March 2026 soundness bug reportedly evaded these checks anyway. zkPassport is live production precedent for exactly this stack doing NFC-passport-to-Noir-to-UltraHonk-on-device proving. |

## B. Circuits — zkPassport (github.com/zkpassport/circuits, Apache-2.0)

| Column | Findings |
|---|---|
| **Status** | Built by Obsidion (Michael Elliot, Theo Madzou); **acquired by Aztec Labs on 2026-05-27**, team folded into Aztec. Real usage: 17,000+ participants used it for nationality/sanctions checks during Aztec's token sale; used as a conference-ticket-discount verifier across 11 Latin American countries at Devconnect. iOS app connects to Ethereum, Base, and Aztec verifiers. |
| **Audit** | **No completed, published third-party audit found.** zkSecurity published only an educational blog post on unlinkability design (not a formal audit). An open, unmerged GitHub PR (#96, "DO NOT MERGE - audit comments") contains informal review notes, not a delivered audit. Status: unverified/likely none yet. |
| **Known bugs** | Multiple real fixes found via issue/PR history: an open PR (#88) fixing an off-by-one array-index bug in three nullifier/commitment hash functions (still unmerged); a closed PR (#145) "fix outer circuit constraints" (name implies an under-constraint class bug in the aggregation/outer circuit); a closed PR (#79) fixing a data-integrity check that broke on Indonesian passports; a closed PR (#63) fixing "occasional incorrect ECDSA signature processing"; a closed PR (#103) fixing DG2 hash endianness. None carry formal severity ratings — these are plain dev fixes, not disclosed CVEs. |
| **Backing** | Now backed by Aztec Labs' funding (~$125M equity + ~$60M token sale) rather than independent funding. Pre-acquisition zkPassport-specific funding figures are muddled with a differently-named project ("zkPass") in aggregator data — unverified. No zkPassport-specific token; AZTEC is Aztec's own token. |
| **Lock-in flags** | Tied to Noir/Barretenberg (Aztec stack) by construction, now organizationally inside Aztec Labs. Mobile app is **not yet open source** — only circuits and SDK are (Apache-2.0); app open-sourcing is promised "when out of testing," no firm date, reaffirmed post-acquisition but still undated. |
| **Verdict-worthy facts** | Architecture composes ≥4 Noir subproofs (DSC signature, CSCA/master-list chain, DG1-to-SOD data-integrity, disclosure), with confirmed evidence of an **outer/aggregation circuit** (recursive composition, not bare hash commitments) — a closed PR literally named "fix outer circuit constraints" confirms this component exists and had a bug. The OPRF-based "salted unique identifier" nullifier is **explicitly optional**; which network/who runs the OPRF nodes is undisclosed in available docs. |

## C. Circuits — Rarimo passport-zk-circuits (MIT)

| Column | Findings |
|---|---|
| **Status** | Miami-based, founded ~2021-2022. Circuits are Circom (MIT), with a parallel Noir port (`passport-zk-circuits-noir`) that has **not** been shown to be covered by any audit. Used in "Freedom Tool," a privacy-preserving voting/polling tool reportedly used in Russia/Iran/Georgia-linked contexts and for token airdrops — claims sourced from Rarimo's own comms and crypto press, not independently verified. |
| **Audit** | Halborn audited 17 Circom files (passport verification, SHA-1/SHA-256, Merkle logic, verification contracts), 2024-02-23 to 2024-03-08. Findings: 0 Critical/High/Medium, 1 Low (missing constraint in `passportVerificationSHA1`, fixed), 1 Informational (Circom version inconsistency, fixed). 100% of findings addressed per Halborn. |
| **Known bugs** | Substantial changes **since** the audit, outside its scope: a fix for an explicitly named "under-constrained" problem in the `DataEncoder` circuit (2025-01-30); a concerning commit "PK inclusion verification should not be commented" (2026-02-14 — implies a check had been disabled in shipped code and was later re-enabled); a date-encoder missing-checks fix (2026-04-24); and a maintainer-labeled "Fix RSAWithSHA1 signature vulnerability" (2026-07-02) — roughly 2.5 years post-audit, unaudited since. |
| **Backing** | Raised a $2.5M "Vision round" (Vitalik Buterin, and figures from Celestia/RiscZero/Gnosis/Aleo/Monad/Aztec-affiliated angels); some aggregators cite $14.5M total (unverified/inconsistent). Rarimo is **token-native**: RMO is a governance/staking token; self-custody wallet setup "automatically reserves RMO tokens" per Rarimo's own docs. |
| **Lock-in flags** | The circuit code itself (MIT) is technically usable standalone without the chain/token, but this isn't an explicitly documented/endorsed mode — Rarimo's production design assumes an on-chain identity-state Sparse Merkle Tree and RMO staking. A chain-free adopter would be going off Rarimo's supported path. |
| **Verdict-worthy facts** | DG1/SOD binding follows standard ICAO passive authentication (DG1 hash → SOD encapsulated content → signed attributes → DSC → CSCA chain), mirrored faithfully in-circuit. Trust in the composed claim rests on **hash commitments passed through an on-chain identity-state Merkle tree**, not proof recursion/aggregation — architecturally simpler than zkPassport's outer-circuit approach, but it presumes an on-chain root of trust, which conflicts with a no-blockchain design goal. |

## D. Google longfellow-zk

| Column | Findings |
|---|---|
| **Status** | Scoped explicitly to mDL (ISO 18013-5), JWT, and W3C VCs — **zero mention of ICAO 9303/DG1/SOD** in README, docs, or the one relevant closed GitHub issue. Google's launch blog frames it around mDL age-assurance (with Sparkasse/EU), not passports. No roadmap item for passport support found. |
| **Audit** | An IETF CFRG draft exists (`draft-google-cfrg-libzk-01`, presented at IETF 125). Google states three completed security reviews (Trail of Bits, Ligero, ISRG) found "no issues... with respect to the ZK scheme," plus two additional academic/industry panel reviews described as ongoing/planned — their actual findings/report content could not be fetched (unverified). |
| **Known bugs** | None found (scope of external review appears limited to the core ZK scheme, not passport-specific handling, which doesn't exist). |
| **Backing** | Google. |
| **Lock-in flags** | A European fork (`dyne/longfellow-zk`) removes a Google Play API dependency for EUDI mdoc use; an EUDI-wallet iOS wrapper (`av-lib-ios-longfellow-zkp`) also targets mdoc credentials, not passports. No third party is building ICAO passport support on this codebase. |
| **Verdict-worthy facts** | Not currently a viable component for this project — it doesn't address the DG1/SOD data model at all, only mdoc/VC-issued attestations. |

## E. Custom Noir circuits (build vs. adopt)

| Column | Findings |
|---|---|
| **Status** | Available building blocks: `noir-lang/noir_rsa` (official, now unmaintained, README redirects to a zkPassport-maintained fork), `richardliang/noir-rsa` (community, PKCS1v15+SHA-256, 2048-bit modulus max, built on `noir-lang/noir-bignum`), Noir's stdlib SHA-256 plus a standalone `noir-lang/sha256` package (~7,049 constraints, tested against Noir v0.36.0 — an old, likely-outdated pin). |
| **Audit** | `richardliang/noir-rsa` carries an explicit "experimental... no warranties" disclaimer — **unaudited**. No audit found for `noir_rsa`, `noir-bignum`, or the SHA-256 packages. |
| **Known bugs** | None specifically documented; but SHA-1 support (needed for legacy passports) is thin-to-absent in maintained Noir libraries. |
| **Backing** | Loose community/individual maintainers, not an organization; the "official" RSA lib is unmaintained. |
| **Lock-in flags** | None beyond Noir/Barretenberg itself (see A). |
| **Verdict-worthy facts** | Effort estimate (informed, **not sourced**, no zkPassport line-count data was fetchable): roughly 6–12 engineer-weeks to build a working 4-circuit passport composition (DG1 parse, SOD/signature verify incl. RSA+SHA-256+legacy SHA-1, age comparison, DG1↔SOD binding) from these libraries, plus more before audit-ready — versus days-to-low-single-digit-weeks to integrate zkPassport's existing suite, which inherits zkPassport's own audit gap and Aztec-oriented architectural assumptions instead of ones you chose. |

## F. Precedent

| Column | Findings |
|---|---|
| **Status** | **No confirmed production (non-pilot) ZK passport/ID deployment found at any non-crypto organization.** The EU's own AV Blueprint "mini-wallet" (announced 2026-04-15) is the closest institutional candidate, but per EFF (2026-08) and Techdirt, its ZK features are **only enabled in a closed demo/prototype build**, not the shipped app people use — and a researcher reportedly bypassed the prototype's over-18 check via a replayed stale token. |
| **Audit** | N/A — no shipped system to audit. |
| **Known bugs** | The one documented bypass of the EU prototype's ZK age check (via a Chrome extension replaying a stale token), per EFF/Techdirt reporting. |
| **Backing** | EU Commission (AV Blueprint), with France/Denmark/Greece/Italy/Spain/Cyprus/Ireland named as front-runner integrators into national EUDI Wallets — still pilot/rollout stage. |
| **Lock-in flags** | N/A |
| **Verdict-worthy facts** | The EUDI Architecture Reference Framework's own "Topic G – Zero Knowledge Proof" explicitly scopes ZK proof generation to **already-issued PIDs/attestations** in mdoc or SD-JWT VC format — not to raw ICAO 9303 passport-chip data. Passport-chip-derived ZK, as this project proposes, sits entirely outside the EU's current defined approach. |

---

### Plain-language risk summary (no recommendation)

1. Every ZK-passport-capable engine or circuit set surveyed is pre-1.0/beta or unaudited (or both) — there is no mature, audited option today.
2. Noir/Barretenberg had a real, disclosed critical soundness bug in March 2026 (forged proofs could pass) and a second critical bug in the successor prover in July 2026 — this is a live, recent pattern, not ancient history.
3. No standalone audit of UltraHonk's core proving system was found — only a sub-primitive (Bigfield) and an unrelated library (ZK Email) have been audited.
4. zkPassport is the closest working precedent for this exact use case (NFC passport, Noir, on-device UltraHonk proving) and has real users, but has no completed published audit and several plain bug fixes (including in its aggregation "outer circuit").
5. Rarimo's circuits are the only ones in this set with a completed third-party audit (Halborn, 2024), but substantial code has changed since, including an "under-constrained" fix and an RSA/SHA-1 vulnerability fix from July 2026 — well outside the audited scope.
6. Rarimo's trust model leans on an on-chain identity-state Merkle tree, which conflicts with a no-blockchain design goal unless run in an undocumented standalone mode.
7. zkPassport's architecture uses proof aggregation/recursion (an "outer circuit"), which is more complex and has had its own constraint bugs; Rarimo's simpler hash-commitment approach depends on a chain you may not want.
8. Google's longfellow-zk, despite credible external cryptographic review, does not support ICAO passport data at all — only mdoc/VC formats — and nobody is building that support on it.
9. Writing custom Noir circuits avoids adopting someone else's architecture, but the available RSA library is explicitly labeled experimental/unaudited, and SHA-1 (needed for legacy passports) has weak library support.
10. No non-crypto organization has shipped a production ZK passport/ID system; the EU's own attempt has its ZK features live only in a demo build, which was reportedly bypassed.
11. The EU's official architecture doesn't address passport-chip ZK at all — it works from wallet-issued credentials, not raw chip data — so there is no regulatory-aligned template to follow.
12. Funding/backing across this whole space is thin and consolidating (Aztec acquired zkPassport in May 2026), meaning the roadmap for the closest-fit option is now set by one company's broader token/network priorities.
13. Every "no known bug" finding in this research is bounded by what's public — private/internal findings, unpublished audits, or undisclosed advisories cannot be ruled out.
14. Multiple facts here (funding totals, some bug severities, the OPRF nullifier's operator) were unconfirmable and are flagged unverified rather than assumed.
15. The overall picture is one of an actively-moving, unaudited-to-partially-audited ecosystem, not a settled choice among mature, audited components.

---

## How this feeds Q23

- No self-vouching passport ZK stack exists today (longfellow-zk is mdoc/VC only, no passport roadmap).
- Noir/Barretenberg: pre-1.0, a disclosed forged-proof soundness bug fixed 2026-03 (v4.1.2) and a second critical in the v5 alpha 2026-07 (less verified), no standalone UltraHonk core audit found.
- zkPassport: acquired by Aztec Labs 2026-05-27, real users, no published audit, ordinary bug-fix history incl. the outer/aggregation circuit, mobile app still closed, OPRF nullifier optional with undisclosed operators.
- Rarimo: the only audited circuits (Halborn 2024) but materially changed since incl. an under-constrained fix and a 2026-07 RSA/SHA-1 fix, token/chain-native by design.
- Writing our own circuits: 6–12 engineer-weeks (unsourced estimate) on experimental, unaudited RSA/SHA libraries.
- No non-crypto organisation has shipped ZK-over-passport; the EU framework scopes ZK to wallet-issued credentials, not chip data.
