---
type: draft
title: NGI Zero Commons Fund — application draft (zkagent)
status: DRAFT — not submitted
---

# NGI Zero application — DRAFT

> **This is a draft for the owner only. It has not been submitted anywhere.**
> The section structure below follows NLnet's NGI Zero application form as of this
> assistant's training knowledge, reconstructed from memory — **it was not fetched from
> the live nlnet.nl form**. Before submitting:
> - Verify the exact current field list, character/word limits, and required attachments
>   on the live form at https://nlnet.nl/propose/ under whichever NGI Zero fund/call is
>   currently open (this draft assumes NGI Zero Commons Fund; confirm the call name and
>   that it is still accepting applications).
> - Verify eligibility rules (legal entity vs. individual, EU/associated-country
>   requirements, prior-funding conflicts) against the live guidance — none of that was
>   checked online for this draft.
> - Verify the submission deadline / call cadence (NGI Zero calls are typically rolling
>   but this was not confirmed).
> - No deadlines, eligibility, or form mechanics were verified against the internet for
>   this draft — everything below is content only, drawn from this repository.

---

## Abstract (≤1200 characters)

zkagent answers one question — "is this person over 18 (or 16/21/60/65)?" — using the
NFC chip already inside a passport or national ID card, without an issuer, without a
zkagent-run server, and without storing anything from the document. A thin open-source
Android app reads the government-signed chip via a vetted library (JMRTD), checks the
signature against the public ICAO/BSI masterlist, and answers a single boolean to the
website that asked, either anonymously (no way to link two visits) or pseudonymously
(a per-site tag only that one site can recognise). The verifier is a small open-source
Node package (`chiproof`, Apache-2.0, published on npm) any site can run itself — no
zkagent infrastructure sits in the path, ever. Despite the project's name, v1 uses no
zero-knowledge cryptography of its own; "zk" survives only as the pseudonym's name
("zktag"). This grant would fund an iOS reader, a device-attestation evidence plug for
stronger client trust, an independent security review, signed Play Store release
builds, and adopter-facing documentation hardening — moving zkagent from a
solo-maintainer demo to something a second developer can safely adopt.

*(character count: verify against the live form's counter before submitting; trimmed
to fit 1200 but re-check.)*

---

## Have you been involved with projects or organisations relevant to this project before?

zkagent's owner previously built **8een** (https://github.com/hamr0/8een), which worked
through the EU's own age-verification regulation step by step and demonstrated the
approach against a real government-issued mdoc. zkagent reuses 8een's lessons learned,
its `challenge.js` request pattern, and its verdict/test discipline (`ok`/`allowed`
invariant), but is a new repository, not a fork (D6). No other prior grant funding or
formal organisational affiliation is claimed here — *owner: fill in any additional CV /
project history NLnet expects in this field.*

---

## Explain what the requested budget will be used for

The budget funds a solo maintainer's time to close five gaps that separate the current
M3 demo from an adopter-ready open-source project (see work packages and budget table
below): an iOS reader, a device-attestation evidence plug, an independent security
review, signed release builds on a real distribution track, and documentation hardening.
No new hires, no infrastructure spend — zkagent is deliberately stateless and runs no
server of its own (NO-GO #1/#3); the money goes to engineering and audit time, denominated
in EUR day-rates the owner will size (placeholders below).

---

## Compare your own project with existing or historical efforts

**vs. the EU Age Verification Solution / eIDAS wallet blueprint.** The EU's own answer
requires a wallet, an attestation provider, and a batch-issuance round trip before a
user can prove anything — it assumes an issuer. zkagent's core insight is that no new
issuer is needed: the government already issued the credential, and it is sitting in
the holder's pocket as the passport/ID chip itself. zkagent is not part of that
ecosystem, is not eIDAS-conformant, and must never imply certification (PRD §4
non-goals) — it only borrows the blueprint's origin-bound request/response handoff
shape as prior art, not its trust model.

**vs. commercial KYC/NFC-passport vendors.** Commercial NFC identity-verification
products are closed-source, typically route data through a vendor server, and are
priced for enterprise KYC, not a yes/no age gate. zkagent is issuer-free, stores
nothing server-side ever (NO-GO #1), and is Apache-2.0 licensed end to end.

**vs. other open-source passport-chip readers.** JMRTD (Android/desktop) and
NFCPassportReader (iOS) are the vetted chip-reading libraries zkagent wraps rather than
reimplements (NO-GO #2: no custom chip-parsing code) — they are dependencies, not
competitors.

**vs. ZK-based passport-proof projects (OpenPassport / Self / zkPassport and similar
"ZK-passport" efforts).** These are genuinely zero-knowledge: the verifier checks the
mathematics itself. zkagent is not — v1's trust root is a government chip signature
plus platform attestation, checked by the verifier trusting an attestation, not a
circuit (D1). Despite the project's name, no ZK circuit in this repository is "ours" in
v1; third-party ZK (e.g. zkPassport-style circuits) may only enter as an optional,
validation-grade evidence plug, gated and disclosed (D24/D26), never the default and
never marketed as zkagent's own zero-knowledge proof. Where a real circuit-based
comparator is named in this application it is described as "ZK-based, circuit-bound,"
not equated with zkagent's own trust model.

---

## What are significant technical challenges you expect to solve during the project?

- **iOS reader parity without a custom chip parser.** NFCPassportReader has a different
  API shape and platform trust model (Secure Enclave vs. Android Keystore/StrongBox)
  than JMRTD; the challenge is wiring the same `chiproof` verifier contract to a second,
  independently-vetted reading library without introducing any project-owned chip- or
  crypto-parsing code (NO-GO #2 holds on iOS too).
- **A device-attestation evidence plug that stays pluggable.** Adding Play Integrity /
  Android Key Attestation as a client-trust-list mechanism (§6.5 S4) must slot into the
  existing evidence-plug registry (FR12) alongside the already-shipped `sig-ed25519/1`
  /`sig-p256/1` plugs, without becoming a fixed default — Play Integrity tokens are
  non-transferable and per-app-quota, which is exactly why the plug design stays
  pluggable rather than hardcoded.
- **Independent security review under the project's own invariants.** A reviewer
  unfamiliar with the codebase needs to validate that `ok:false` never collapses into
  `allowed:false` (the core invariant, §3), that no stable identifier leaks in mode A
  (NO-GO #11), and that the disclosed circuit-class leak in the ZK evidence plug (D26)
  is bounded to what's already documented and not worse in practice.
- **Signed release builds without weakening the trust-list identity.** Moving from a
  sideloaded debug APK to a Play-signed release changes the app's signing-cert digest
  (Play App Signing re-signs the APK), which is the client's trust-list identity under
  FR10/D17 — every verifier configuration, including the reference demo, has to account
  for both digests once the Play digest is known.

---

## Describe the ecosystem of the project, and how you will engage with relevant actors and promote the outcomes

zkagent's ecosystem is deliberately thin by design: no zkagent-run service exists to
engage users through (NO-GO #3), so promotion means engaging the *adopters* who would
embed `chiproof` or the scanner into their own site or app — age-gated services with
legal age-verification deadlines, and (rung 2, currently frozen) sites doing RFC 9421
Web Bot Auth who might want a human-personhood signal. Relevant actors: the ICAO
9303/PKD and BSI masterlist maintainers (consumed, not engaged as partners — zkagent
runs no CA and issues nothing, NO-GO #3); the JMRTD and NFCPassportReader maintainer
communities, as upstream dependencies; the RFC 9421 (Web Bot Auth) implementer
community at the transport layer; and other document-derived-proof projects
(zkPassport/Self/OpenPassport) as comparators, not partners, given the different trust
model. Outcomes will be promoted through the existing public GitHub repository
(Apache-2.0, all code and PRD already public), the published `chiproof` npm package,
and this project's existing practice of writing every milestone's PRD section and
decision log in the open before building it.

---

## Requested amount

**EUR — placeholder, see budget table below (sums to a figure in the €30,000–€50,000
range). Owner to confirm final requested amount against the live form's stated ceiling
for the current NGI Zero Commons Fund call before submitting.**

## Project website / repository

- Repository: https://github.com/hamr0/zkagent (Apache-2.0)
- Published package: `chiproof` on npm (verifier SDK)
- *Owner: add a project website URL if one exists beyond the GitHub repo/README.*

---

## Proposed work packages and budget (EUR)

Day-rate and day-count are placeholders — the owner sizes both; the table below shows
the reasoning shape and sums to a mid-range total for reference.

| # | Work package | Days (placeholder) | Day rate (EUR, placeholder) | Subtotal (EUR) |
|---|---|---|---|---|
| 1 | iOS reader via NFCPassportReader (vetted library, no custom chip parsing) | 15 | 500 | 7,500 |
| 2 | Device-attestation evidence plug (Play Integrity / Android Key Attestation) enabling the client trust list (§6.5 S4) | 12 | 500 | 6,000 |
| 3 | Independent security review of `chiproof` and the scanner app (external reviewer, not the owner's own time) | — | — | 8,000 |
| 4 | Signed release builds + Play Store closed testing track (§6.6) | 8 | 500 | 4,000 |
| 5 | Documentation / spec hardening for third-party adopters (FR10/FR11/FR12 registry docs, integration guide) | 10 | 500 | 5,000 |
| | **Contingency (~10%)** | | | 3,000 |
| | **Total** | | | **≈33,500** |

*Owner: replace the placeholder day rate and day counts with real numbers; adjust the
external security-review quote (line 3) once an actual reviewer/firm is contacted —
that line was left as a round placeholder since it depends on a quote, not a day rate.*

---

## Known limitations (stated plainly, not hidden)

- **Not zero-knowledge.** v1's trust root is a government chip signature plus platform
  attestation; the verifier trusts an attestation rather than checking mathematics
  itself. "zk" in the name and in "zktag" is historical/naming only.
- **Chip cloning is an accepted, disclosed gap.** Where a document lacks chip
  authentication, a cloned chip mints the same identity as the genuine holder;
  mode-B uniqueness only holds where the verdict's `chip_auth` field reads `true`.
- **At most k tags per human**, k = number of documents held (~1–3) — never "exactly
  one human, one identity."
- **Per-device, per-origin keys, no re-enrollment.** Mode-B attester keys are bound on
  first sight (trust-on-first-use); a factory reset permanently locks a user out at
  every site that already bound them (open question, not yet resolved).
- **Tier A has a known disclosed leak.** The optional ZK evidence plug (validation-grade
  only, never default) discloses a cross-site-stable document circuit-class bucket —
  disclosed, not hidden, and not removable without an upstream circuit change.
- **Not interoperable with the EU eIDAS wallet by design.** zkagent has no issuer and no
  wallet round trip; it may share privacy properties with the EU Age Verification
  Solution but is explicitly not part of that ecosystem and must never claim
  certification or conformance.
- **Captcha-grade, not bank-grade.** Good for uniqueness, age-gating, and bans that
  stick — never claimed as replay-safe, sybil-proof, or a substitute for legal identity
  or payments.

---

## Timing

Two options for when the owner files this application; no recommendation is given here
(that call belongs to whoever is orchestrating the decision, not this draft).

**Option A — file now, after M3, before M3b**
- Momentum: M3 just shipped (v0.6.0, 2026-09-05) with a working end-to-end demo, fresh
  device evidence, and a clean exit-criteria table — the strongest "here's a working
  thing" story available today.
- Risk: the application would ask for money to build things (iOS, attestation plug,
  signed release) that don't exist yet, on a project with no signed release build and
  no external security review to point to as precedent.
- Grant funding could directly determine M3b/rung-2 sequencing rather than the owner
  deciding it solo — a funder's own scope preferences might pull the roadmap.

**Option B — file after Play closed track / signed release**
- Stronger evidence base: a real signed APK on a real distribution track and at least
  one completed milestone beyond a sideloaded demo make the "adoption-ready" claim more
  credible to a reviewer.
- Delay cost: NGI Zero calls take time to review either way; waiting adds a full
  release cycle (§6.6, Play closed-track review) before even submitting.
- The application's own work packages (signed release, security review) would already
  be partially done, weakening the case that this is what the grant itself is needed to
  produce — a funder may ask "why do you need money for something you already shipped."

---

## Sources

- `README.md` — what it is/isn't, modes, honest limits, repo layout, license.
- `docs/product/zkagent-prd.md` — §1 problem, §1.1 glossary, §2 what v1 is, §2.1 why
  not zero-knowledge, §9 NO-GO table, §12 grounding, §13 success criteria, §14 adoption
  risk.
- `docs/wiki/decisions.md` — D1, D23, D26, D38, D73, D74, D75, D76, D77, D78, D79.
- `docs/product/customer-guide.md` — §1 what this is, §2 glossary (tier A/B/C, zktag,
  verifier, attester key, chip authentication, masterlist, threshold, origin, store).
- `CHANGELOG.md` — `[0.6.0] — 2026-09-05` section (lockstep release, S1-S3/D79 work,
  item 13 gap and its resolution path).
- `docs/logs/M3-EXIT-ROWS-v0.6.0-2026-09-05.md` — re-run exit-criteria evidence against
  the v0.6.0 release build, incl. the no-signed-release-keystore fact (Setup section)
  used in the Timing section above.
- `packages/chiproof/package.json` — package name/version/license/repository fields.
- `LICENSE` — Apache License 2.0.

## Generative-AI disclosure plan (NLnet policy v1.1, 2026-01-26)

Owner: "we will come to it later." Recorded here as a plan, not yet executed.

**Policy** (fetched 2026-09-05, https://nlnet.nl/foundation/policies/generativeAI/,
v1.1, 2026-01-26): GenAI may be used to prepare applications but must be disclosed via
a prompt provenance log (model, dates/times, prompts, unedited output). "Outcomes
purely generated by AI are not allowed to be submitted as work eligible for payment (as
part) of the grant." Human contributors remain accountable for accuracy, originality,
and integration, and may not present AI output as their own. Substantive GenAI use must
be publicly disclosed, typically in the README, with commits that add generated code
marked with model and prompts. All work must sit under recognised FLOS licenses.
Non-compliance can mean rejection or termination.

**Interpretation** (orchestrator, owner agreed): the payment line is between
AI-assisted deliverables with documented human design/direction/verification (payable,
disclosed) and unreviewed generated output (not payable) — not a reuse restriction.

**Project facts.** Code in this repo is AI-written (Claude, via Claude Code) under the
owner's direction; every commit carries a `Co-Authored-By: Claude` trailer plus a
session link (179 trailers over 150 commits as of `31c9d20`). Design, scope, decisions,
device/document testing, and sign-off are the owner's — evidenced by
`docs/wiki/decisions.md` (D1–D79, verbatim owner quotes and dates),
`docs/wiki/questions.md`, PRD history v1.x–v1.64, per-session evidence logs under
`docs/logs/`, session stashes under `.claude/stash/`, and `.claude/remember/` (owner
corrections on record). README currently has **no** GenAI disclosure section — a gap to
close before filing.

**Plan, in order:**
1. README "How this project is built" disclosure section, plus a `docs/wiki` page
   giving counts pulled from git and docs (commits, trailers, sessions, decisions,
   questions, evidence logs) — pulled, not asserted.
2. Prompt provenance log for this application draft itself (model
   `claude-fable-5-1` / `claude-sonnet-5` for the drafting agent, dates, prompts,
   unedited output) — the orchestrator can export it from the session transcript.
3. Budget framed around the human work packages (architecture and protocol decisions,
   device/document testing, security-review coordination, iOS port hands-on).
4. Optional: email NLnet a two-line question about an AI-assisted solo project before
   filing.

Owner's stance, verbatim: "AI is a slop if you have a slop thinking, but we can pull how
many sessions/turns per project and decisions made and disclose and share it all."
