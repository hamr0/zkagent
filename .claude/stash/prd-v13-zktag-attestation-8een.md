# zkagent — PRD v1.3, zktag rename, attestation rethink, 8een overlap analysis

**Timestamp:** 2026-08-03
**Continues from:** `m0-hardware-android-first.md` (2026-07-31)

## Session summary

Long session in two halves. First half: hunted German refurb and used markets for the M0
Android device (the NL Marktplaats shortlist from the prior session was the baseline).
Second half: a deep design thread that materially changed the PRD — an RFC 9421
conformance check, a naming collision fix, two new owner decisions, a corrected
attestation strategy, and a strategic repositioning of the whole product toward a
borrowable core with age verification as the near-term wedge. PRD went v1.1 → v1.3.
Nothing committed. No code written. M0 still not run — that remains the top blocker.

## Key decisions

- **D2 flipped to Android-first**, now written into the PRD (v1.2). Play $25 one-time vs
  Apple $99/yr; builds on Fedora, no Mac; test builds sideload free. Resolves Q5 and Q6.
  iOS moved from "non-goal for now" to explicit non-goal. Attestation flipped Apple App
  Attest → Google Play Integrity throughout; NFCPassportReader → JMRTD; Secure Enclave →
  Keystore/StrongBox.
- **`tag` renamed to `zktag`** everywhere (~28 renames), fixing a silent, wire-visible
  collision with RFC 9421 §2.3's own `tag` signature parameter (used by Web Bot Auth,
  which the PRD says zkagent rides alongside). Deliberately NOT renamed: PRD line ~142's
  "NFC tag-reading entitlement" (Apple Core NFC term, different meaning) and three
  references to RFC 9421's own `tag` parameter.
- **D10 — derived secret expires after 30 days**, renewable only by fresh passport scan,
  enforced IN THE ENCLAVE not by app-side date arithmetic. Rationale: indefinite StrongBox
  caching meant borrowing a passport for one scan bought a permanent, undetectable,
  unrevocable identity; this converts one-time possession into recurring possession. FR1
  updated. **NOTE:** owner later indicated this should become adopter-configurable
  60/90/180 days — NOT YET APPLIED to the PRD.
- **D11 — age threshold configurable, output stays one bit.** Adopted verbatim from 8een's
  D6 (`src/index.js:195-217`, `src/verdict.js:103-117`). Rejected alternative: emitting a
  ladder of common thresholds (13/16/18/21) at once, which buckets the holder's age instead
  of disclosing one bit. A proof of a threshold other than the one requested MUST be
  rejected, not accepted as close enough (8een `src/verdict.js:211`). §2 block now emits
  `over_threshold` not `over18`.
- **Q9 resolved** — phone→agent cert handoff ships THREE paths: (1) QR phone-screen→laptop-
  camera (~400–550 bytes, one static QR, no fountain coding); (2) LAN POST to a listener on
  the agent host; (3) file the user moves by any existing means (leaks the zktag to
  whatever it's routed through — document, don't block). Key insight: the cert carries the
  agent's PUBLIC key and is signed by the phone, so integrity is free and the channel needs
  no confidentiality or authentication. No zkagent-run server in any path.
- **Q8(b) narrowed** — cert sent INLINE per request. URL-reference rejected for v1: hosting
  it anywhere we control violates NO-GO #1/#3; adopter-side hosting is just caching;
  agent-side hosting assumes the agent runs a web server. Web Bot Auth's directory-URL
  pattern does not transfer (it serves bot operators with many rotating keys, not one
  user's single agent).
- **New FR8** — RFC 9421 wire mapping: `alg="ed25519"`; `keyid` = agent-pubkey thumbprint;
  RFC `tag` reserved for protocol labelling and MUST NOT carry the zktag; signature
  `expires` MUST be ≤ cert `expiry` (verifier enforces the earlier); cert travels in its
  own header, never in `keyid`; an off-the-shelf 9421 verifier must accept our signature
  unpatched.
- **Non-goal rewritten**: readable set is ICAO 9303-compliant chip documents (passports,
  most EU national ID cards, some residence permits) — anything with an SOD chain
  verifiable against a public masterlist. Passports first, others per-country on evidence.
  EXPLICITLY EXCLUDED AND NOT "LATER": US driving-licence PDF417 barcodes (AAMVA text, no
  verifiable signature), photos, photocopies, OCR. Signed mDLs may qualify eventually but
  are a separate read path. Each document type added raises k (NO-GO #5) — a holder of
  passport + national ID mints two unlinkable identities per service, so new types are a
  uniqueness regression and must be priced as one.
- **8een overlap decision: Option A** — zkagent stays a SEPARATE project and borrows
  heavily from 8een (see Findings). Rejected option B (age as a second credential backend
  inside 8een) because zkagent needs a mobile app and 8een deliberately builds no clients
  (NO-GO #4), and because merging would contaminate 8een's completed, clean refutation.
  Rejected option C (drop zkagent's age leg) because age is the half with real demand and
  it de-risks the chip read for everything above it.
- **Strategic repositioning agreed in-session, NOT yet written into the PRD** (see Part 6
  below for full detail): zkagent becomes a borrowable core; rung 1 ships age + zktag
  together with the agent layer deferred to a later phase; name stays `zkagent` but the
  published package may split off (e.g. `chipproof`, undecided); attestation root is
  reopened as Q14 rather than assumed to be Play Integrity.

## Doc/repo state

- `docs/archive/zkagent-prd.md` — now "PRD v1.3 (draft)" with amendment lines for v1.2
  and v1.3 in the header. **Still uncommitted** (all of v1.1, v1.2, v1.3 are uncommitted).
- `CHANGELOG.md` — updated with all v1.2/v1.3 changes under `[Unreleased]`.
  **Still uncommitted.**
- `.gitignore` — unchanged this session, still as created previously.
- Commit policy unchanged: commit only when the owner asks.
- 8een repo referenced read-only at `/home/hamr/PycharmProjects/8een` — M0–M5 all passed,
  `zk8een@0.5.0` published to npm, zero runtime dependencies. Not modified this session.

## Findings index

### PRD v1.2 → v1.3 changes (full detail)

v1.2 (dated 2026-08-02): D2 flip to Android-first (see Key decisions); `tag`→`zktag`
rename; new FR8 (RFC 9421 wire mapping); new Q7 (device-assurance tier vs FR6 uniformity),
Q8 (9421 mapping details).

v1.3 (dated 2026-08-03): D10 (30-day enclave-enforced expiry); D11 (configurable threshold,
one-bit output, borrowed from 8een D6); Q9 resolved (three cert-handoff paths); Q8(b)
narrowed (inline cert only); new §13 Adoption risk (names the two-sided market problem as
outranking the crypto risks; records near-zero integration cost as partial mitigation;
records that agent accountability = forecast demand vs age verification = present demand
with legal deadlines; states zkagent is ONE product with TWO POSITIONINGS not two products;
flags legal/compliance suitability as unverified against any named jurisdiction; notes M3
is a demo not evidence of demand); new Q11 (age binary-search probing — configurable N +
repeat queries can find exact DOB in ~7 tries; mitigations: fixed allowed set 13/16/18/21
caps resolution at 5 buckets, and/or rate-limit distinct thresholds per zktag), Q12
(passport-only coverage vs age-verification positioning — marked "do not pitch consumer
age verification until answered"), Q13 (first-adopter path); risk register items 6 and 7
added then corrected — item 6: we are NOT issuer-free, correct phrasing is "issuer-free
identity, vendor-rooted attestation"; item 7: attestation choice decides whether the
product contradicts its own audience (GrapheneOS/CalyxOS users are exactly the people who
want anonymous personhood proof); new Q14 (attestation root reopened, see below);
non-goal rewritten (ICAO 9303 scope, see Key decisions).

### RFC 9421 conformance check (drove the rename)

The repo referenced RFC 9421 five times as an ASSUMPTION, never verified: PRD lines 35, 62,
85, 150 and `docs/logs/future-digital.md:59`. Checked against the actual spec. Four
problems found:
1. **`tag` collision** — RFC 9421 §2.3 already defines `tag` as a signature parameter: "An
   application-specific tag for the signature as a String value... used by applications to
   help identify signatures relevant for specific applications or protocols." zkagent used
   `tag` for the pseudonymous identity — same word, same protocol context, opposite
   meanings, and Web Bot Auth (PRD:150) uses `tag` in the RFC sense. Fixed by renaming to
   `zktag`.
2. **RFC 9421 defines NO delegation chain mechanism at all.** Spec explicitly puts key
   trust out of scope; `keyid` is an opaque string. PRD:35 said "verifier checks chain" —
   there is no chain to check. Transport must be defined by us.
3. **Two expiries, no precedence** — RFC `expires` (signature) vs cert `expiry`
   (delegation). Resolved by FR8: verifier enforces the earlier.
4. **`nonce` overlap** — RFC 9421 `nonce` param vs the step-5 challenge-nonce. Different
   layers, same word.

Fine: `alg` registry includes `ed25519`, so the agent keypair choice is conformant. Signing
HTTP requests per 9421 is the right transport; PRD:150's "attaches as fields, competes with
nothing" holds. **UNVERIFIED and flagged as such:** Web Bot Auth's `Signature-Agent` header
semantics — do not adopt without checking.

### Q14 — attestation root reopened (not settled on Play Integrity)

Android Keystore hardware key attestation returns a vendor-rooted certificate chain
asserting: key resides in hardware (TEE/StrongBox), verified-boot state and root-of-trust
key, OS version and patch level, and the **attestation application ID** (package name +
app signing-certificate digest — this is what answers "is this the app you published?",
since a modified APK must be re-signed, changing the digest). Unlike Play Integrity it
needs NO runtime Google service call, NO Play Console registration, NO quota, and NO Play
Services on device — so it works on GrapheneOS (which supports it deliberately; their
Auditor app is built on it with a pinnable verified-boot root key). Likely answer is BOTH
roots, key attestation primary. THREE THINGS MUST BE VERIFIED BEFORE THIS BECOMES A
DECISION: (a) keybox extraction — attestation keys have been pulled from real devices and
circulated; (b) OEM implementation quality — some fall back to software attestation; (c)
revocation — Google publishes a revocation list that must be fetched and honoured,
reintroducing a small live dependency. Also: key attestation covers key origin, app
identity and boot state; it does NOT cover active runtime tampering the way Play
Integrity's rolled-up verdicts attempt to.

Owner flagged a belief that Google is clamping down on GrapheneOS via face verification.
This was **NOT confirmed** — explicitly recorded as UNVERIFIED; it decides whether key
attestation actually buys that audience, so it needs a real check.

### 8een overlap analysis

8een is at `/home/hamr/PycharmProjects/8een`. STATUS: M0–M5 all passed, `zk8een@0.5.0`
published to npm, zero runtime dependencies. What it is: "the verifier the EU didn't ship"
— takes a ZK age proof + trust anchor + nonce, returns one bit. Authors no cryptography,
wraps `google/longfellow-zk`. Deliberately NOT an issuer (NO-GO #2) and NOT a wallet
(NO-GO #4).

**The decisive difference:** 8een's chain is issuer → wallet → verifier and it controls
only the third link. zkagent controls 2 of 2 (government already issued the passport; you
build client + verifier). A weaker guarantee you can ship beats a stronger one you can't
invoke.

8een's own EU-STACK-AUDIT (`docs/02-evidence/EU-STACK-AUDIT.md`) supports the owner's
"easy path wins" thesis with hard evidence: ZK is `SHOULD` not `SHALL` in the EU blueprint
and sits in a chapter titled "Experimental features"; on OpenID4VP (the protocol the web
actually uses) the wallet is never handed the ZK machinery and physically cannot emit a
proof, and the flagship OpenID4VP verifier backend contains zero ZK code; when proof
generation fails the wallet default is `ZkResponsePolicy.FallbackToFullDisclosure`, which
silently discloses the entire document, and neither the app nor wallet-core ever sets the
safe value the library's own docs recommend for production.

That audit also retracted FOUR of 8een's own earlier claims — two false, one unfair to the
EU. Its method (adversarial refutation, every claim pinned to file+line+commit, retractions
written down, checkers instructed to default to REFUTED on thin evidence) is the model
zkagent should copy before making any EU or legal claim.

**Borrow list (concrete):**
- `src/verdict.js` (never-throw classifier, machine-readable reason enum)
- `src/challenge.js` (self-authenticating nonce `random ‖ expiry ‖ HMAC`, issuance stores
  nothing)
- `src/gate.js` (drop-in HTTP gate, replay-safe by default, ok→200 / ok:false→503)
- D6 (threshold configurable, one bit — already taken as zkagent D11)
- **D7 (credential currency configurable — and the finding that an expired credential
  still proves adulthood, since age is monotonic)** — a gift to zkagent because an expired
  passport in a drawer still works, widening coverage and partially answering Q12
- D8 (replay defence opt-in because it needs adopter infrastructure, fails CLOSED when on)
- §7.3 unlinkability method (black-box byte probe with a PLANTED positive control, because
  "a guard you have not watched fire is not a guard" — two earlier versions of this check
  were written and retracted for being unable to fail)
- §7.2 lesson (the adoption-cost criterion could not be discharged in-house because
  everyone who could run it wrote it — deferred to the first external adopter)

## Part 2 — side thread: optical/air-gap transfer (parked, not adopted)

Researched at owner's request. Three real repos compared: txqr (Go, MIT, fountain+QR, 3.2k
stars, no benchmarks in README, iOS reader — weakest fit now that Android-first);
decimen-optical-transfer (TypeScript/Node, MIT, LT fountain, node-qrcode + zxing-wasm,
claims 129.2 KB/s goodput); libcimbar (C++17, MPL-2.0, colour tile grid + Reed-Solomon +
wirehair fountain + zstd, ~850 kbit/s ≈106 KB/s, up to 33MB compressed, arm64+Android +
WASM/PWA).

**Conclusion: PARK IT, don't build.** Outbound (zktag + attestation) is KB-scale — plain QR
suffices, no fountain code. The only place throughput is load-bearing is INBOUND: CSCA
masterlist refresh (MB-scale) into a sealed device — a camera is a physically read-only
inbound path, unlike USB. Air-gap protects exfiltration, NOT inbound integrity (that comes
from CSCA signatures regardless of transport). Blocked by NO-GO #5 (captcha-grade ceiling)
— the threat model is above the product's assurance bar. Free thing to do today: keep
derivation network-call-free behind a clean interface and give the masterlist loader a
byte-stream source, so the sealed variant stays buildable. libcimbar's MPL-2.0 is the only
dependency here that constrains an Apache-2.0 repo (file-level copyleft; fine to link,
changes to MPL files must be published).

Also parked: adjacent project ideas where optical air-gap is the product not a feature —
journalist/whistleblower file intake (replacing USB in SecureDrop-style workflows),
software-free data diode (hardware diodes cost €5k–50k; screen+camera is a €0 one-way
valve; risk: buyers in utilities/defence require CERTIFIED HARDWARE and may be
structurally unable to buy a software diode — check before building), offline release
signing. Crypto wallet signing is taken (SeedSigner/Keystone/Foundation).

## Part 3 — strategic repositioning (agreed in-session, NOT yet written into PRD)

- **zkagent becomes a borrowable CORE, not just a product.** Anyone — including a
  government — may embed the core in their own app. "We deliver the core that delivers the
  functionality; wrap it with your app or use ours, doesn't matter." This is a one-sided
  sale (the adopter brings their own users) and materially improves the §13 two-sided-
  market problem.
- **Rung 1 ships age + zktag TOGETHER** — the zktag is the novel bit. Agent layer (FR5,
  FR8, M4, M5, Q4, Q8, Q9) moves to a clearly-marked later phase.
- **Name: keep `zkagent`** (owner's call — "zk" as direction of travel, "agent" covering
  both AI agents and agents-as-proxies). Standing constraint: the name may be aspirational,
  the CLAIMS may not — nothing may say "zero-knowledge" while v1 is attested selective
  disclosure (NO-GO #5). Suggested precedent from owner's own history: 8een the project /
  zk8een the package — so zkagent the project, `chipproof` the published package. Not yet
  decided.
- **Why it is not ZK, plainly:** ZK = "here is a proof, check the maths yourself." zkagent
  = "here is an answer, Google says trustworthy code computed it." The verifier never
  checks a proof; it checks an attestation that unmodified code ran, then believes the
  number. Break attestation and the claim collapses; with ZK, breaking attestation buys
  nothing.
- **Correction recorded: uniqueness is NOT a ZK feature.** ZK gives selective disclosure
  with proof. Uniqueness comes from the credential being scarce. They pull against each
  other: 8een's ZK proofs are UNLINKABLE (two visits look like two strangers), which is
  useless for "have I seen this person before." zkagent's zktag is deliberately linkable
  WITHIN a service and unlinkable ACROSS services. **8een cannot do uniqueness by
  construction; zkagent can — this is the strongest argument for zkagent existing at all.**
- **Cross-document identity cannot merge by default.** Different documents carry different
  numbers; nothing stable, unique and common exists across a passport and a licence
  (name+DOB collides and changes). BUT: a national personal number (e.g. BSN in NL) present
  in BOTH a country's passport and ID card WOULD unify them. This makes **D9 far more
  consequential than it looked** — not just "which field survives renewal" but "which
  field survives renewal AND unifies across document types." Costs: personal numbers are
  highly sensitive, not every country has one, not every chip exposes one. Unification is
  achievable per-country where the number exists, not universally. M0 reports what the
  chip actually contains, then D9 is decided on that.
- **One app CAN read both passports and eIDs** (both ICAO eMRTDs, JMRTD doesn't care).
  Proposed: make it the adopter's call, like the threshold — `acceptedDocuments:
  ['passport']` for k≈1, `['passport','eid']` for wider reach and knowingly higher k.
- **TRUST LIST model.** Every Android app is signed with a developer key; attestation
  reports a digest of that signing certificate — that IS the app's identity. If anyone can
  wrap the core, many apps produce zktags, so a verifier must decide which app identities
  to accept. THE ADOPTER holds that list (consistent with D3/FR3 "all stores adopter-
  supplied" and with 8een's `caCerts` as THE trust boundary) — we never run a registry, so
  NO-GO #3 holds. Open core, curated trust: being open does not make an app trusted.
  Worked example given to owner: Dutch police publish once — package `nl.politie.id`, cert
  digest `sha256:CC:DD:...`, spec version `zkagent-derivation/1`. A Dutch webshop
  configures `trustedApps: [{official}, {politie}]` plus `threshold: 18`,
  `acceptedDocuments`, `masterlist`. At verify time the verifier checks the Google
  signature, extracts package + cert digest, and accepts only if listed. A malicious clone
  is rejected even though the code is open. No API key, no contract, no relationship with
  zkagent.
- **CRITICAL CONSEQUENCE: the derivation must be a PUBLISHED SPEC, not an implementation
  detail.** zktag = HMAC(KDF(chip data), domain) mentions no app, so same passport + same
  domain = same zktag whichever conformant app computed it. Good (no lock-in, no
  fragmentation) but it REQUIRES that two implementations agree, or the identity space
  splits.
- **FR6 CANNOT SURVIVE AS WRITTEN.** FR6 says all clients emit identical-shaped payloads
  and metadata must not fingerprint, marked "not retrofittable." Under the borrowable-core
  model different apps have different package names and cert digests, and the trust list
  WORKS BY READING EXACTLY THOSE — distinguishing clients is the mechanism, not a leak. Two
  options put to owner: RETIRE FR6, or NARROW it to uniformity within a client while
  accepting cross-client distinguishability. Recommendation was to narrow, with the
  consequence stated plainly: the anonymity set becomes "users of this app," not "all
  zkagent users" — if one app has 10,000 users and another 10 million, the smaller app's
  users are in a set a thousand times smaller. **THIS IS THE OPEN QUESTION BLOCKING THE
  FULL PRD RESTRUCTURE.**

## Part 4 — hardware market (German market, checked 2026-07-31)

Baseline for the M0 device purchase; assessment below concludes the prior session's NL
Marktplaats shortlist and the German options are comparable, with the Neumünster listing
the single best line item found across both sessions.

**German refurb:**
- rebuy.de: Pixel 6a from €123.99 (chalk in stock; charcoal €125.99 and sage €124.99
  unavailable). Pixel 7a €177.99 (charcoal only in stock). Pixel 8a €229.99 porcelain /
  from €301.99 obsidian. Pixel 6 from €134.99, Pixel 7 from €179.99. 3-year rebuy warranty.
  Category URLs: https://www.rebuy.de/kaufen/handy/google/pixel-6-generation/pixel-6a and
  https://www.rebuy.de/kaufen/handy/google/pixel-7-generation/pixel-7a
- refurbed.de: Pixel 7a €192.64 condition "Gut"
  (https://www.refurbed.de/p/google-pixel-7a/), Pixel 8a €231.00 condition "Exzellent" in
  stock (https://www.refurbed.de/p/google-pixel-8a/), Pixel 6a SOLD OUT. Shipping included
  in price; refurbed active in 24 European markets incl. NL.
- backmarket.de: Pixel 6a from €190. Cloudflare-blocked after first query, 7a/8a
  unverified.
- Dry holes: clevertronic.de (no Pixel results), asgoodasnew.de (only Pixel 8/9/10 gens in
  stock, no a-series in 6/7/8 range), mobileup.de (nothing).
- rebuy.de → NL shipping UNVERIFIED (their shipping page 404s). They run a separate
  rebuy.nl storefront — check that before ordering cross-border.

**German used (Kleinanzeigen, all links live at time of check), Pixel 6a, all with Versand
möglich:**
- €80 Stadtlohn, OVP + Hülle — https://www.kleinanzeigen.de/s-anzeige/google-pixel-6a-smartphone-mit-ovp-und-huelle/3471505632-173-1342
- €85 Tüttendorf, 128GB schwarz + Hülle + 2x Displayschutz — https://www.kleinanzeigen.de/s-anzeige/google-pixel-6a-128gb-handy-schwarz-huelle-und-2x-displayschutz/3472329564-173-13550
- €100 Verden, "endlich mal ein nicht defektes Handy" — https://www.kleinanzeigen.de/s-anzeige/google-pixel-6a/3472023039-173-2725
- €100 VB Gummersbach, private sale no warranty — https://www.kleinanzeigen.de/s-anzeige/google-pixel-6a-128g/3470930760-173-1887
- €110 Siegburg, + Ladegerät + Hülle — https://www.kleinanzeigen.de/s-anzeige/google-pixel-6a-inkl-ladegeraet-und-handyhuelle/3472756141-173-1670
- €125 Neubrandenburg, OVP, down from €150 — https://www.kleinanzeigen.de/s-anzeige/google-pixel-6a-128-gb-smartphone-mit-originalverpackung/3468447759-173-253

**Pixel 7a, all with Versand:**
- €111 VB Bruchhausen-Vilsen — https://www.kleinanzeigen.de/s-anzeige/google-pixel-7a/3471840227-173-18731
- €120 Offenburg, 128GB schwarz — https://www.kleinanzeigen.de/s-anzeige/google-pixel-7a-128-gb-in-schwarz/3472559767-173-9038
- €150 VB Zwoenitz — https://www.kleinanzeigen.de/s-anzeige/google-pixel-7a/3470789068-173-4028
- €190 VB Schwelm, weiß OVP — https://www.kleinanzeigen.de/s-anzeige/google-pixel-7a-weiss-128-gb-sehr-guter-zustand-mit-ovp/3470710558-173-1558

**Pixel 8a, all with Versand:**
- €180 VB Neumünster, side scuffs (cosmetic only) — https://www.kleinanzeigen.de/s-anzeige/google-pixel-8a-zu-verkaufen/3472540268-173-646 ← **best value found anywhere across both sessions**
- €230 VB Grafenau — https://www.kleinanzeigen.de/s-anzeige/handy-google-pixel-8a-blau/3470634933-173-10970
- €240 VB Hamm, Porcelain — https://www.kleinanzeigen.de/s-anzeige/google-pixel-8a/3468847196-173-2023
- €249 Frankfurt, commercial seller w/ Verkäufergarantie — https://www.kleinanzeigen.de/s-anzeige/-google-pixel-8a-128gb-wie-neu-top-zustand-in-black-nur-249-/3472277877-173-4300

**DO NOT BUY (reproduce prior session's gotchas):**
- 6a GrapheneOS €399 https://www.kleinanzeigen.de/s-anzeige/google-pixel-6a-mit-grapheneos-sehr-guter-zustand/3471274269-173-23503 ; 7a GrapheneOS €159 https://www.kleinanzeigen.de/s-anzeige/google-pixel-7a-5g-128gb-sehr-gut-hellblau-grapheneos/3472260055-173-2038 (custom ROM fails Play Integrity permanently)
- 6a defekt €50 https://www.kleinanzeigen.de/s-anzeige/google-pixel-6a-als-defekt-/3469470659-173-4559 ; 7a Display kaputt €60 https://www.kleinanzeigen.de/s-anzeige/google-pixel-7a-1-5-jahre-benutzt-display-kaputt-sonst-top/3472283720-173-4714 ; 8a Displayschaden €140 https://www.kleinanzeigen.de/s-anzeige/google-pixel-8a-displayschaden-/3470152468-173-5426 (dead display = cannot clear setup wizard to enable ADB)
- €75 Jena 7a: battery needs replacing AND seller states "Keine Zahlung per Kleinanzeigen" (no buyer protection by design) — skip
- Trier 8a €180 is PICKUP ONLY, not Versand (an earlier statement in-session that it shipped was corrected)

**Key buying constraint:** Kleinanzeigen "Sicher bezahlen" buyer protection covers
shipments WITHIN GERMANY ONLY
(https://hilfe.kleinanzeigen.de/hc/de/articles/17211553583388-Was-ist-Sicher-bezahlen-wie-funktioniert-der-K%C3%A4uferschutz).
Buying into NL = no Käuferschutz. Workaround: pay PayPal Waren & Dienstleistungen (~3% fee,
works cross-border), offer to cover fee + international postage, budget €15–20 extra
shipping DE→NL.

**Assessment:** For the 6a, used (~€105 delivered, no recourse) does not clearly beat
rebuy €124 with 3-year warranty and guaranteed factory-reset stock ROM. For 7a and 8a used
wins properly. The €180 Neumünster 8a is the single best line item found; 8a
strong-integrity runway to ~May 2032 is the longest of any candidate.

## Blocker ranking (given to owner)

1. **M0 not run — no device, nothing measured** (blocks everything)
2. Masterlist coverage for owner's country unverified
3. D9 derivation field undecided (needs M0)
4. Q12 — document coverage vs the age-verification pitch
5. Legal/compliance unchecked in any named market
6. Q13 — nobody asks for zkagent by name; two-sided market
7. Q11 — age binary-search probing (before M3 goes public)
8. Q8 — RFC mapping never tested against a real 9421 verifier
9. Google-dependence + GrapheneOS exclusion (now tractable via Q14)

Items 4–9 are all downstream of 1. Standing advice repeated several times: buy the phone,
run M0 — it answers 1, 2 and 3 in a weekend for €80–180 and is the only thing that can
invalidate everything else.

## Next steps

1. Owner buys the M0 device. Best options: rebuy 6a €124 w/ 3yr warranty, or Kleinanzeigen
   8a €180 Neumünster (longest runway, to ~May 2032). Pay PayPal Waren & Dienstleistungen
   for cross-border protection.
2. Owner answers FR6: retire or narrow. This blocks the PRD restructure.
3. Then: full PRD restructure into rungs — core v1 (chip read + gov signature +
   `over_threshold` + zktag + pluggable attestation + verifier SDK borrowed from 8een) vs
   later phase (agent delegation, blocklist/appeals, iOS, ZK tier).
4. Apply the deferred change: D10's fixed 30 days → adopter-configurable 60/90/180.
5. Decide the package-name split (project `zkagent` / package `chipproof`?) and check npm
   availability early — 8een lost the bare name `8een` to npm's typo-squat filter with no
   appeal.
6. Verify before relying on: Web Bot Auth `Signature-Agent` semantics; the three Q14
   unknowns; the GrapheneOS/face-verification claim; legal age-verification requirements
   in a named jurisdiction.
7. Commit the pending PRD + CHANGELOG + .gitignore when owner asks (commit only on
   request — standing policy).

## Gotchas

- PRD v1.1, v1.2 and v1.3 edits, CHANGELOG edits and .gitignore are ALL UNCOMMITTED. Do not
  assume any of it is committed.
- Do NOT blind-replace `tag` in the PRD: line ~142's "NFC tag-reading entitlement" is Apple
  Core NFC terminology and must stay, as must the three deliberate references to RFC 9421's
  own `tag` parameter.
- The PRD is becoming the collector NO-GO #10 warns against — it now carries ~14 open
  questions, 11 decisions, 8 FRs and 7 risk items for a project with zero lines of code.
  This session alone added six questions and two decisions. Restructure is partly about
  deleting, not just moving.
- `grep` on this host is ugrep 7.5.0 — bounded-repetition regexes fail slowly and look like
  a hang. Parse structured data with python3 instead.
- Kleinanzeigen listing hrefs are NOT in barebrowse ARIA snapshots; fetch the page with
  curl + a browser UA and regex `/s-anzeige/...` out of the HTML.
- backmarket.de Cloudflare-challenges after roughly one query.
- npm placeholder package still mislicensed as MIT (carried from the founding session) —
  gated by NO-GO #8, still open.
