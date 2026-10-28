```
   ┌───────────────────────────────┐
   │   chip in ──────→ 1 bit out   │
   │   (and nothing else, ever)    │
   └───────────────┬───────────────┘
                   │
               zkagent
```

> The NFC chip already in your passport or ID card answers one question, such as "over 18?", with true or false and nothing else.

<p align="center">
  <img src="https://img.shields.io/npm/v/chiproof?label=chiproof&color=2a4f8c" alt="chiproof on npm">
  <img src="https://img.shields.io/github/v/release/hamr0/zkagent?label=scanner%20APK&color=2a4f8c" alt="scanner release">
  <img src="https://img.shields.io/badge/license-Apache%202.0-2a4f8c" alt="license: Apache 2.0">
</p>

---

## Why this exists

zkagent came out of two needs that turn out to share one root.

**1. Age verification is spreading, and the current answer over-asks.** Governments are mandating age checks for social media and adult content. The tools on offer are ID uploads, face scans and issuer-run wallets, and all of them collect far more than the question needs. [8een](https://github.com/hamr0/8een) walked the EU's own age-verification design step by step and showed the limits of the mdoc approach: it needs an issuer, an enrolment step and a wallet before anyone can answer "over 18?".

**2. The internet needs a personhood signal, and agents need a human root.** Humans now compete with machines online, and bots are getting cheaper while the humans behind them get harder to see. Sites want "one real person, once" without learning who that person is. The agent-auth standards emerging now (Web Bot Auth, OAuth on-behalf-of drafts and others) all root trust in a vendor or a custodial account. None of them can say "a real, unique human stands behind this agent" without an identity provider in the middle.

**The shared root: everyone assumes an issuer.** Governments already issued a credential: the signed chip in your passport or ID card. zkagent reuses it directly, with no new issuer, no enrolment and no account.

## What it is

zkagent is an **open-source lab** that proves one thing in working code: you can prove a trait about yourself, such as your age or that you're a unique human, from a real government document, without disclosing the document.

The phone reads the chip over NFC and checks the government's own signature on it (ICAO 9303 passive authentication). It then sends the requesting site a single answer instead of the data. The document never leaves the phone, and nothing from it is stored.

**Minimal disclosure, not zero-knowledge cryptography.** zkagent uses no zero-knowledge circuits of its own. Its privacy comes from minimalism: one bit out, a fresh single-use nonce, per-site keys, and coarsened dates so that nothing stable leaks. It's simple, auditable and deliberately boring. Third-party ZK proofs can be plugged in as optional evidence, but they are never the default.

## What's built

Three parts, all open source under Apache-2.0. Tier C (below) is built and device-checked and ships in 0.9.0, which is not out yet. The latest release is v0.8.0.

| Part | What it is | Where |
|---|---|---|
| **`chiproof`** | The verifier library: what a website runs to check an answer. It never throws, issues challenges, spends single-use nonces, negotiates tiers, builds and checks tier-C requests, and has a pluggable evidence registry. Zero runtime dependencies, with TypeScript types. | [npm](https://www.npmjs.com/package/chiproof) · `packages/chiproof` |
| **Scanner** | The reference Android app. It reads the chip, verifies it, asks the holder for consent with a biometric prompt, and sends the answer. Its Settings page has the holder's switch for tier C. | [GitHub Releases](https://github.com/hamr0/zkagent/releases) (signed APK) · `apps/scanner` |
| **Demo** | A small local website that plays the requesting site, so you can run the whole loop end to end, tier C included. | `apps/demo` |

### Two kinds of proof

| | **Tier A: anonymous** | **Tier B: unique human, per site** |
|---|---|---|
| Answers | "Over N?" true/false | "Over N?" true/false, **plus** "I'm the same person you saw before" |
| Signed? | No, by design. It carries nothing that could link two visits. | Yes, by a key held in the phone's secure hardware (StrongBox P-256, or Ed25519 where available) |
| Can the site recognise you again? | Never. Two visits look identical. | Only that site, through a per-site tag. Two sites can never connect you. |
| Good for | Pure age gates | One account per person, bans that stick, anti-bot gating |

### Tier C: yes/no checks on document fields (off by default)

Tier C answers a short list of yes/no questions about fields on the document. The site never gets the field itself. The questions are:

- the document is valid for at least 3, 6 or 12 more calendar months
- the name matches (the site sends `given_names` and `surname` as two separate fields, so there is no order to get wrong)
- the date of birth shows an age of at least 15, 16, 18, 21, 60 or 65
- the nationality is a given code, or is not in a short list of codes
- the document number matches

It is closed by default. Three things must all be true before the app answers a tier-C request:

1. The operator turned tier C on in `operator.json` (`tier_c.enabled`).
2. The site's exact hostname is on the operator's `tier_c_verifiers` list. This is a hostname pin, not a key pin. The list is baked into the build, so changing it means rebuilding.
3. The holder turned on the setting **Allow additional verifications**. It is OFF by default. While it is off, only age verification (tiers A and B) ever leaves the phone.

The app answers every question in a request or refuses the whole request, never part of it. For any tier-C refusal the site sees only a generic "refused". The specific reason shows in the app only. A build that turns tier C on without a pin list, or has a pin list without turning it on, fails to build.

Tier C is built and device-checked on a US passport and a Dutch ID card, and ships in 0.9.0. It is not zero-knowledge either: it is minimal disclosure of yes/no answers. The limits are listed under [Honest limits](#honest-limits).

### Age thresholds

Sites pick from a fixed, published list: **over 15, 16, 18, 21, 60 or 65**. Each site gets one age, locked the first time it asks, so it can't fish for your exact age by asking several. The lock is shared across tiers: the age a site first asks about, in an age check or in tier C's date-of-birth question, is the only one it can ever use. The one exception is a site the operator names in `multi_threshold_verifiers`, which can ask several ages. The showcase build names none. Dates are coarsened to the day before they're compared.

Age verification (tiers A and B) can be open to any site. In `operator.json`, `"verifiers": "any"` answers an age check from any website, and the showcase build uses it. The exact-hostname list form stays available for a closed build. `"any"` never opens tier C.

### Features

- The government's chip signature is verified against the German BSI CSCA masterlist, and the masterlist's own signature chain is checked when it loads.
- Chip authentication is reported where the document supports it.
- Same-device links (`av://`) or a QR code scanned with the phone's camera, with no in-app scanner needed.
- The site's request is signed (ES256), and its origins must all match. A refusal is always shown as a refusal, never as success.
- A per-site age lock, and an operator choice between open age verification (`"verifiers": "any"`) and an exact-hostname allowlist, both configured at build time in `operator.json`. Tier C has its own switch and pin list there. A bad config fails the build loudly.
- A refusal or error is shown in red in the app. For tier C, the requesting site sees only a generic refusal; the reason is shown in the app only.
- A capped scan log that holds no values, and a privacy policy stating that nothing from the document is stored.
- Evidence plugs: device-key signatures (`sig-p256/1`, `sig-ed25519/1`), `signed-receipt/1`, and the optional third-party ZK plug `zk-passport/1` (tier A, validation-grade).

### What it works with

- **Documents:** ICAO 9303 e-passports and chip ID cards, over BAC or PACE. Tested on a US passport and a Dutch ID card.
- **Phones:** Android with NFC. Tested on a Pixel 6a. iOS is out of scope.
- **Sites:** any Node 20+ backend, through `chiproof`. The request and response shapes follow OpenID4VP (a signed request object and a `direct_post` response).

## Use cases

**Built (tiers A and B):**
- **Age gates:** social media, adult content, alcohol, gambling and games, with over-15/16 for teen rules and over-18/21 for adult rules.
- **Senior checks:** over 60 or 65 for discounts and eligibility, without showing an ID.
- **One account per human:** sign-ups, free trials, giveaways and polls with one vote per person per site.
- **Bans that stick:** banning the per-site tag keeps the same person out, even under a new email address.
- **A human, not a bot:** a privacy-preserving captcha replacement.

**Built and device-checked, ships in 0.9.0 (tier C):**
- **KYC-style yes/no checks:** name match, date of birth within a range, nationality, document valid for at least 3, 6 or 12 months, and document-number match. These are answered only to sites whose exact hostname the operator pinned, and only when the holder allows additional verifications.

**Agents (in the IETF draft, not built here):**
- **A human-rooted agent identity:** an AI agent acting for a real, unique person, who can be held accountable and banned per site, with no identity provider in the middle.

## Agent auth and the IETF

The agent half of zkagent (delegation certificates, signed agent requests, revocation) now lives in the author's individual Internet-Draft [`draft-hamr-oauth-agent-delegation`](https://datatracker.ietf.org/doc/draft-hamr-oauth-agent-delegation/). Its Appendix A, Direction 3, cites this project as a working, issuer-free human root: a per-site, pseudonymous "unique human" signal that a delegation chain can anchor in. This repository is what that reference points to. The draft is an individual submission and has no IETF endorsement.

## What it isn't

- **Not zero-knowledge.** Neither the age checks nor tier C use ZK cryptography. It's minimal disclosure.
- **Not bank-grade.** It's captcha-grade: good for uniqueness, age gates and bans that stick, not for payments or legal identity, and it isn't certified for any legal age-verification duty.
- **No face scan, no ID upload, and no issuer, enrolment or zkagent-run service** anywhere in the path.

## Honest limits

- Passive authentication proves the chip data is genuine. It doesn't prove the person holding the phone is the document's owner.
- Where a document lacks chip authentication, a cloned chip would answer as the real holder would. This is disclosed, not mitigated.
- A person holding k documents can get up to k tags at a site: at most k, never exactly one.
- Tier A is unsigned by design, and the tier-B key comes from no configured trust source, because no device-attestation plug was built.
- Tested on one phone model and two documents. It makes no claim about other countries or devices.

Tier C adds these limits, each stated in full in [`docs/product/customer-guide.md`](docs/product/customer-guide.md) section 4.1:

- **Repeat questions.** Nothing limits how often an approved site asks. It can keep confirming guesses of a name, a document number or a nationality. Your setting is the brake: turn it off and tier C is never answered.
- **Expiry and age.** The 3, 6 and 12 month questions can be combined to narrow the expiry date. Age is locked to one value per site, except for sites in `multi_threshold_verifiers`, which can narrow it down. The one age a site gets is still one bit about the date of birth.
- **Document number.** A "yes" links you to a number the site already had.
- **Long names.** A very long name may read as "no match", because the document cuts long names short and given names must match in full.
- **The pin is a hostname.** It trusts whoever controls that hostname's DNS and TLS. The list changes only with a new build, there is no remote revocation, and no authority stands behind it.
- **Many hostnames, many ages.** The age lock is per hostname, so a party that runs many hostnames can ask a different age on each.
- **Sites learn the link was opened.** In an open build the app must fetch the request before it knows it is tier C. A site that is not pinned still learns that the link was opened and from which network address, then is refused before anything is read.
- **Little real-world testing.** Fetching a site's signing key over real HTTPS was run once, against one host. Wide interoperability is untested.

## Try it

You need an Android phone with NFC and a chip passport or ID card.

1. Sideload the signed scanner APK from [Releases](https://github.com/hamr0/zkagent/releases).
2. Run the demo site on your computer and reach it from the phone over USB with `adb reverse tcp:8787 tcp:8787`. The steps are in [`apps/demo/README.md`](apps/demo/README.md).
3. To use the verifier in your own site, run `npm install chiproof`.

[`docs/product/customer-guide.md`](docs/product/customer-guide.md) explains every screen, and section 7.1 covers building and signing your own scanner.

## Reuse it

The latest release is v0.8.0 (2026-10-24). Version 0.9.0, with tier C, is in build. zkagent has no production users and stays on 0.x. It's built to be picked apart. [`docs/product/reuse-map.md`](docs/product/reuse-map.md) lists every liftable part (the verdict core, the evidence plugs, the chip and masterlist check, the secure-hardware key, the handoff, the config gate and the publish workflow) with its maturity, evidence and caveats.

## Repo layout

- `packages/chiproof`: the verifier library (npm)
- `apps/scanner`: the Android scanner
- `apps/demo`: the local demo site
- `docs/product/zkagent-prd.md`: the spec, decisions and NO-GO table
- `docs/product/customer-guide.md`: screen-by-screen guide
- `docs/product/reuse-map.md`: parts you can lift
- `docs/wiki/backlog.md`: designed but not built, and how to revive it
- `docs/wiki/decisions.md`: the owner's decisions, with dates
- `spikes/`: the throwaway proofs of concept, one per milestone

## License

Apache 2.0. See [LICENSE](LICENSE) and [NOTICE](NOTICE).
