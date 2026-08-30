# chiproof

**M1 bucket B1 only. There is no `verify()` yet — do not install this expecting it to check anything end to end.**

`chiproof` is the verifier SDK of the [zkagent](https://github.com/hamr0/zkagent) project: a stateless Node library that checks an attested, windowed disclosure derived from an ICAO 9303 chip document — the chip already in a passport or a modern national ID card.

The intended shape, in one paragraph: a phone reads the chip, verifies the issuing government's own signature against a public masterlist, answers exactly one question about the holder (*over 18?*), and attests that unmodified code did the reading. `chiproof` verifies that attestation and returns a verdict. No document, date of birth, name or number ever reaches the server. By default no identifier does either — two presentations by the same person are unlinkable. A second, explicitly requested mode adds a pseudonym scoped to one domain, for the cases that need to recognise a returning human.

Any mode this package supports today is **bare mode: captcha-grade, not identity-grade.** With no evidence plug configured (M1 spec §4, deferred past B1), a "verified" verdict means only "a nonce was spent once, on time, under a challenge this process minted" — it says nothing about a person, a document, or a device. Do not treat B1 output as proof of anything beyond that until the evidence slot (B3) and a real plug land.

## Status

M1 bucket B1 is implemented and tested: the `ok`/`allowed` verdict invariant, canonical JSON + sha256 for signing, the self-authenticating HMAC challenge/nonce (with optional Ed25519 issuer signing per D20), a single-use nonce spend against an adopter-supplied store, and a test-only in-memory store. Bucket B2 is implemented and tested on top of it: `createVerifier(config).verify(presentation, ctx)` checks spec, shape, challenge liveness and single use, tier negotiation (refuse, never downgrade), threshold match (D11), zktag / `chip_auth` presence rules (D21) and the FR10 trust list against `ctx.clientIdentity`. `config.challengeSecret` (the nonce HMAC key) is mandatory at boot. Bucket B3's evidence slot (`src/evidence.js`: plug registry with boot-time binding/linkability/ceiling checks, `require`/`accept` routing, fault isolation to `ok:false`) and the `signed-receipt/1` plug (`src/plugs/signed-receipt.js`) are implemented and tested. The `zk-passport/1` plug is NOT implemented: the spike's real public inputs carry no challenge nonce, so the plug cannot honestly declare the nonce binding registration requires — see `docs/product/m1-verifier-core-spec.md` and the B3 report. B4 (end-to-end integration) is not started.

The riskiest assumption behind the whole project — that a passport chip can be read and verified on a phone and yield a stable derivation — **has not yet been tested against real hardware as part of this package.** Nothing here should be treated as settled beyond what B1's own tests establish.

## What exists so far (B1)

- `src/verdict.js` — `cannotCheck(reason)`, `realNo(reason)`, `yes(extra)`: the only three ways to construct a verdict, and none of them can produce the forbidden `{ok:false, allowed:false}` shape.
- `src/canonical.js` — a JCS-like (not full RFC 8785) canonical JSON serializer plus `sha256()`, used for signing. Numbers must be integers or strings; floats throw.
- `src/challenge.js` — `issueChallenge()`, `verifyChallenge()`, `spendNonce()`. The nonce is a self-authenticating HMAC frame (random + issued_at + tag), ported from [8een](https://github.com/hamr0/8een)'s `src/challenge.js` pattern (Apache-2.0) — a forged nonce is rejected without ever touching the nonce store. Challenges may optionally be signed (Ed25519, D20); unsigned challenges are accepted at tiers A/B and refused at tier C.
- `src/stores/memory.js` — `InMemoryNonceStore`, a single-process, test-only `NonceStore` (`setIfAbsent(key, ttlMs): Promise<boolean>`).
- `src/index.js` — re-exports the above, plus a `createVerifier(config)` stub that validates config shape and refuses to boot with `InMemoryNonceStore` outside `NODE_ENV==='test'` unless `allowInMemoryStore:true` is passed explicitly.

## Running the tests

```
cd packages/chiproof
node --test
```

Tests are split per [`AGENT_RULES.md`](../../.claude/remember/AGENT_RULES.md): `tests/unit/` covers the pure logic (`verdict.js`, `canonical.js`); `tests/integration/` exercises `challenge.js`, `index.js` (boot checks) and `verify()` against the real in-memory store and real Ed25519 keys — no mocks beyond a counting wrapper used to prove a forged nonce never reaches the store.

## What this is not

- Not a zero-knowledge proof. The verifier checks a hardware attestation that trustworthy code computed an answer; it does not check a proof it could verify itself. The accurate description is *attested windowed disclosure*. See the design doc.
- Not an identity provider, wallet, issuer, or certificate authority.
- Not certified under any regulatory scheme, and not claiming to satisfy any legal age-verification requirement anywhere.

## Design

- Product requirements: [`docs/product/zkagent-prd.md`](https://github.com/hamr0/zkagent/blob/main/docs/01-product/zkagent-prd.md)
- Design and disclosure model: [`docs/product/zkagent-design.md`](https://github.com/hamr0/zkagent/blob/main/docs/02-engineering/zkagent-design.md)

## License

Apache-2.0
