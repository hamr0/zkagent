# chiproof

**M1 buckets B1–B4 implemented and tested. No real-hardware M0 evidence backs this package yet — do not treat a "verified" verdict as proof about a person until an evidence plug you trust is configured.**

`chiproof` is the verifier SDK of the [zkagent](https://github.com/hamr0/zkagent) project: a stateless Node library that checks an attested, windowed disclosure derived from an ICAO 9303 chip document — the chip already in a passport or a modern national ID card.

The intended shape, in one paragraph: a phone reads the chip, verifies the issuing government's own signature against a public masterlist, answers exactly one question about the holder (*over 18?*), and attests that unmodified code did the reading. `chiproof` verifies that attestation and returns a verdict. No document, date of birth, name or number ever reaches the server. By default no identifier does either — two presentations by the same person are unlinkable. A second, explicitly requested mode adds a pseudonym scoped to one domain, for the cases that need to recognise a returning human.

With no evidence plug configured (the default), the result is **bare mode: captcha-grade, not identity-grade** — a "verified" verdict means only "a nonce was spent once, on time, under a challenge this process minted"; it says nothing about a person, a document, or a device. The evidence slot and two plugs (`signed-receipt/1`, `zk-passport/1`) exist, but an adopter must explicitly configure `evidence.require`/`evidence.accept` to leave bare mode. `zk-passport/1` is **tier-A-only**: its underlying circuit has no nonce input, so its nullifier can't double as a stable tier-B/C pseudonym (see the context file below for why).

## Quickstart

```js
import { createVerifier, InMemoryNonceStore } from 'chiproof';

process.env.NODE_ENV = 'test'; // InMemoryNonceStore is test-only; see the context file
const verifier = createVerifier({
  scopeDomain: 'example.com',
  challengeSecret: 'a-secret-at-least-16-bytes-long',
  stores: { nonce: new InMemoryNonceStore({ quiet: true }) },
});
const challenge = verifier.issueChallenge({ tier: 'A', ttlMs: 60_000 });
const verdict = await verifier.verify({
  spec: 'zkagent/1', tier: 'A',
  claim: { over_threshold: true, threshold: 18 }, challenge,
});
```

## Full integration guide

Every config option, the complete public API, the `NonceStore` and evidence-plug
extension contracts, the threat model, and the sharp edges live in
[`chiproof.context.md`](./chiproof.context.md) — read that (not the source) to
wire this library up, or point an integrating agent at it directly.

## Running the tests

```
cd packages/chiproof
node --test
```

Tests are split per [`AGENT_RULES.md`](../../.claude/remember/AGENT_RULES.md): `tests/unit/` covers the pure logic (`verdict.js`, `canonical.js`); `tests/integration/` exercises `challenge.js`, `index.js` (boot checks) and `verify()` against the real in-memory store and real Ed25519 keys; `tests/e2e/` runs against the gitignored `spikes/m1-zk/out/` artefacts and skips cleanly when they or `bb` are absent.

## What this is not

- Not a zero-knowledge proof. The verifier checks a hardware attestation that trustworthy code computed an answer; it does not check a proof it could verify itself. The accurate description is *attested windowed disclosure*. See the design doc.
- Not an identity provider, wallet, issuer, or certificate authority.
- Not certified under any regulatory scheme, and not claiming to satisfy any legal age-verification requirement anywhere.

## Design

- Product requirements: [`docs/product/zkagent-prd.md`](https://github.com/hamr0/zkagent/blob/main/docs/product/zkagent-prd.md)
- Design and disclosure model: [`docs/product/zkagent-design.md`](https://github.com/hamr0/zkagent/blob/main/docs/product/zkagent-design.md)

## License

Apache-2.0
