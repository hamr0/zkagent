```
   ┌───────────────────────────────┐
   │   chip in ──────→ 1 bit out   │
   │   (and nothing else, ever)    │
   └───────────────┬───────────────┘
                   │
               zkagent
```

> The NFC chip already in your passport or ID card, answering one question: over 18, true or false — and nothing else.

<p align="center">
  <img src="https://img.shields.io/github/package-json/v/hamr0/zkagent?filename=packages%2Fchiproof%2Fpackage.json&label=version&color=2a4f8c" alt="version (auto from package.json)">
  <img src="https://img.shields.io/badge/license-Apache%202.0-2a4f8c" alt="license: Apache 2.0">
</p>

---

## What it is

Western governments are pushing social-media age mandates that ask for far more personal data than the question actually needs. [8een](https://github.com/hamr0/8een) walked the EU's own age-verification regulation step by step and proved the point with a government-issued mdoc. zkagent goes further: it reuses the NFC chip already sitting in the ID card or passport in your pocket to answer the exact same single question. Yes — a phone can check a real government document and answer one thing, are you over 18, true or false, and disclose nothing else.

Every time you're verified, the site may recognise you again, or not — the operator's choice, not zkagent's. It's open source, full of knobs, and entirely up to whoever runs it — an operator, a bank, a government agency — to take it, deploy it, and turn those knobs as they see fit.

**Status: complete experiment, closed cleanly (2026-10-24).** The yes/no answer from a passport or ID-card chip is built and was tested end to end on a real phone (a Pixel 6a) with a real US passport and a real Dutch ID card. It is released as v0.7.1 and the next release is 0.8.0; the project stays on 0.x. It has no production users and no external adopter, and the owner may revive it, reuse parts of it, or reuse the whole thing. What was designed but never built is listed in [`docs/wiki/backlog.md`](docs/wiki/backlog.md).

**What you can try today.** The scanner is a signed Android app (APK) on [GitHub Releases](https://github.com/hamr0/zkagent/releases) that you sideload; the demo is a small local website acting as the requesting site, run on your computer and reached from the phone over a USB cable; and the verifier library `chiproof` is on npm. Steps are under [How to try it](#how-to-try-it).

## What it isn't

No ZK circuits of ours — despite the name's history, v1 is never "zero-knowledge"; third-party ZK enters only as an optional evidence plug, never the default. No face scan, no ID upload, no document ever leaving the phone. No issuer, no enrollment, no zkagent-run service anywhere in the path. Nothing from the document is stored on the phone or sent to the site; the phone keeps only its own per-site keys.

## Modes

The phone checks the government's own signature on the chip (passive authentication, ICAO 9303) and answers one yes/no question. It does not use zero-knowledge proofs. zkagent answers in one of three tiers (also called modes A/B/C):

- **Tier A — anonymous, built.** One yes/no bit, sent with no signature. Nothing in it identifies the holder, so two visits cannot be told apart.
- **Tier B — pseudonymous, built.** The same bit, signed by a key held on the phone, plus a per-site tag so that one site (and only that site) can tell it's seen this document before — a unique-human signal, never a cross-site one. The site binds the phone's key to the tag the first time it sees it.
- **Tier C — attributed, designed and parked (M3b).** Booleans over identifying fields — for example document validity or expiry — from a published, pinned-issuer-gated verb list, for KYC-class uses. The design is complete but it was never approved for building, so nothing of it exists in the app. See [`docs/wiki/backlog.md`](docs/wiki/backlog.md).

Age thresholds come from a fixed, published list — one threshold per site, locked on first sight. An operator can run the default signing (ES256 request objects, a device attester key in P-256 or Ed25519) or add device attestation as an optional evidence plug — Play Integrity is one such plug, never a default.

Two things worth stating plainly. Passive authentication proves the data on the chip is genuine; it does not bind the bit to the person presenting it. And chip authentication, where the document supports it, proves the physical chip is genuine; where it doesn't, a cloned chip mints the same identity as the real holder. Disclosed, not mitigated. Known gaps: tier A is unsigned, and the tier-B key comes from no configured trust source (no attestation plug was built).

## Also useful for

Beyond the yes/no age gate: KYC checks for banks, government services gated by age, "is this document still valid for at least six months" — all answerable without disclosing the underlying fields, bound to a nonce good for that occasion only. These are tier-C, which is designed and parked (M3b), not built.

## How to try it

`chiproof` is the published verifier core (`packages/chiproof`, `npm install chiproof`); the scanner app and demo verifier are its reference adopters, not the product itself. To try the whole loop you need an Android phone with NFC and a passport or ID card with a chip: sideload the scanner APK from [GitHub Releases](https://github.com/hamr0/zkagent/releases) and run the local demo verifier — a page on `localhost` acting as the requesting site — reached from the phone over USB (`adb reverse tcp:8787 tcp:8787`). See [`apps/demo/README.md`](apps/demo/README.md) for the run steps and [`docs/product/customer-guide.md`](docs/product/customer-guide.md) for what each screen means. To build the scanner yourself, see the revival notes in [`docs/wiki/backlog.md`](docs/wiki/backlog.md).

## Honest limits

- **Captcha-grade, not bank-grade.** Trust rests on the government chip signature (and, optionally, device attestation) — good for uniqueness, age-gating, and bans that stick, not for payments or legal identity.
- **At most k tags per human**, where k is the number of documents they hold — never "exactly one."
- **Clone replay is a real, disclosed gap** where a document lacks chip authentication.
- Tier C doesn't exist: it is designed and parked.
- Tested on one phone model (a Pixel 6a) and two documents (a US passport and an NL ID card); no coverage claim is made for other countries or devices.
- No device-attestation plug was built, so a site cannot yet tell which app is talking to it beyond the phone's own key.
- Android only; iOS is a non-goal.

## Repo layout

- `packages/chiproof` — the published verifier core npm package.
- `apps/scanner` — the reference Android scanner app.
- `apps/demo` — the local demo verifier (a page acting as the requesting site).
- `spikes/` — riskiest-assumption proofs of concept, one per milestone.
- `docs/product/zkagent-prd.md` — the PRD: architecture, milestones, NO-GO table, owner decisions. Start here.
- `docs/product/customer-guide.md` — what the app does, screen by screen.
- `docs/wiki/backlog.md` — everything designed or discussed but not built, and how to revive it.
- `docs/product/reuse-map.md` — one page of the parts you can lift into another project.
- `docs/product/privacy-policy.md` — the reference build's data-handling statement.

## Cited by

The owner's individual Internet-Draft [`draft-hamr-oauth-agent-delegation`](https://datatracker.ietf.org/doc/draft-hamr-oauth-agent-delegation/) (rev -03, 2026-10-22) cites this project as "zkagent: One Yes/No Answer from a Passport or ID Card Chip" in its Appendix A, Direction 3. It is an individual submission with no IETF endorsement. This repository is what that reference points to.

## License

Apache 2.0. See [LICENSE](LICENSE) and [NOTICE](NOTICE).
