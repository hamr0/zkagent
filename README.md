# zkagent

Human-rooted, privacy-preserving authentication for AI agents.

> **Status: pre-development.** `zkagent@0.0.0` on npm is a name reservation only —
> there is nothing to install or use yet. This README describes what is being built.

## What it will be

One passport scan on your phone becomes one stable, anonymous **tag** per website.
Your AI agents act under that tag; services verify it with one library call and zero
personal data. Ban the tag and every agent behind it stays banned — there is no
second passport to re-register with.

```
Phone (native scanner app)                    Service (Node verifier SDK)
  NFC-read passport chip                        verify device attestation
  verify government signature locally     →     verify single-use challenge
  tag = HMAC(chip-derived secret, domain)       check blocklist (your store)
  OS attestation: "this ran untampered"         → {ok, allowed, reason}
```

- Passport data never leaves the phone. A service sees a tag, a yes/no, and signatures.
- Same person + same service = same tag. Different services = unlinkable tags.
- No CA, no issuer, no account, no server-side identity storage — the government
  already issued the credential; zkagent only derives from it, locally.

## Honest limits (read before adopting)

- **Captcha-grade**, not bank-grade: v1 trust roots are the government chip signature
  plus Apple/Google device attestation. Suitable for uniqueness, age-gating, rate
  limiting, and bans that stick — not for payments or legal identity.
- **At most k tags per human**, where k is the number of passports they hold (~1–3).
  Never "exactly one."
- v1 claims are exactly two: *unique tag* and *over 18*. Nothing else is disclosed.

## What it is NOT

No ZK circuits (v1), no cryptocurrency, no identity wallet, no EU/eIDAS integration,
no reputation system, no data collection of any kind. The full NO-GO list lives in
the PRD and is binding.

## Repo layout

- `docs/product/zkagent-prd.md` — the PRD: architecture, milestones, NO-GO table,
  owner decisions. Start here.
- `docs/02-evidence/` — measured evidence per milestone (created as milestones run).

## License

Apache-2.0
