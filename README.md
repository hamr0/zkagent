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

**Status: work in progress.** Not something to install yet — read on for what's here to try today.

## What it isn't

No ZK circuits of ours — despite the name's history, v1 is never "zero-knowledge"; third-party ZK enters only as an optional evidence plug, never the default. No face scan, no ID upload, no document ever leaving the phone. No issuer, no enrollment, no zkagent-run service anywhere in the path. Nothing from the document is stored on the phone or sent to the site; the phone keeps only its own per-site keys.

## Modes

zkagent answers in one of three tiers (also called modes A/B/C):

- **Mode A — anonymous.** One yes/no bit. Unlinkable, even to the same site on a repeat visit.
- **Mode B — pseudonymous.** Same yes/no, plus a per-site tag so that one site (and only that site) can tell it's seen this document before — a unique-human signal, never a cross-site one.
- **Mode C — attributed** *(future, M3b)*. Booleans over identifying fields — for example document validity or expiry — from a published, pinned-issuer-gated verb list. For KYC-class uses. Not built yet.

Age thresholds come from a fixed, published list — one threshold per site, locked on first sight. An operator can run the default signing (ES256 request objects, a device attester key in P-256 or Ed25519) or add device attestation as an optional evidence plug — Play Integrity is one such plug, never a default.

Caveat worth stating plainly: chip authentication, where the document supports it, proves the physical chip is genuine; where it doesn't, a cloned chip mints the same identity as the real holder. Disclosed, not mitigated.

## Also useful for

Beyond the yes/no age gate: KYC checks for banks, government services gated by age, "is this document still valid for at least six months" — all answerable without disclosing the underlying fields, bound to a nonce good for that occasion only. These are mode-C, future work (M3b), not built yet.

## How to try it

`chiproof` is the published verifier core (`packages/chiproof`); the scanner app and demo verifier are its reference adopters, not the product itself. To try the whole loop: sideload the scanner app on a phone and run the local demo verifier — a page on `localhost` acting as the requesting site — over USB. See [`apps/demo/README.md`](apps/demo/README.md) for the run steps and [`docs/product/customer-guide.md`](docs/product/customer-guide.md) for what each screen means.

## Honest limits

- **Captcha-grade, not bank-grade.** Trust rests on the government chip signature (and, optionally, device attestation) — good for uniqueness, age-gating, and bans that stick, not for payments or legal identity.
- **At most k tags per human**, where k is the number of documents they hold — never "exactly one."
- **Clone replay is a real, disclosed gap** where a document lacks chip authentication.
- Mode C doesn't exist yet.

## Repo layout

- `packages/chiproof` — the published verifier core npm package.
- `apps/scanner` — the reference Android scanner app.
- `apps/demo` — the local demo verifier (a page acting as the requesting site).
- `spikes/` — riskiest-assumption proofs of concept, one per milestone.
- `docs/product/zkagent-prd.md` — the PRD: architecture, milestones, NO-GO table, owner decisions. Start here.
- `docs/product/customer-guide.md` — what the app does, screen by screen.

## License

Apache 2.0. See [LICENSE](LICENSE) and [NOTICE](NOTICE).
