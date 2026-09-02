# chiproof

**M1 buckets B1–B4 implemented and tested. No real-hardware M0 evidence backs this package yet — do not treat a "verified" verdict as proof about a person until an evidence plug you trust is configured.**

`chiproof` is the verifier SDK of the [zkagent](https://github.com/hamr0/zkagent) project: a stateless Node library that checks an attested, windowed disclosure derived from an ICAO 9303 chip document — the chip already in a passport or a modern national ID card.

The intended shape, in one paragraph: a phone reads the chip, verifies the issuing government's own signature against a public masterlist, answers exactly one question about the holder (*over 18?*), and attests that unmodified code did the reading. `chiproof` verifies that attestation and returns a verdict. No document, date of birth, name or number ever reaches the server. By default no identifier does either — two presentations by the same person are unlinkable. A second, explicitly requested mode adds a pseudonym scoped to one domain, for the cases that need to recognise a returning human.

With no evidence plug configured (the default), the result is **bare mode: captcha-grade, not identity-grade** — a "verified" verdict means only "a nonce was spent once, on time, under a challenge this process minted"; it says nothing about a person, a document, or a device. The evidence slot and four plugs (`signed-receipt/1`, `zk-passport/1`, `sig-ed25519/1`, `sig-p256/1`) exist, but an adopter must explicitly configure `evidence.require`/`evidence.accept` to leave bare mode (`evidence.require` may be per-tier — `{B: [...]}` — so one instance serves a bare tier A next to an evidence-required tier B; a plain array applies at every tier, as in 0.2.0). Each `require` entry is either a registry-key string (all-of, unchanged) or a non-empty array of registry-key strings — an **alternatives group** (D31/D36): satisfied when at least one member is present and verifies, e.g. `require: { B: [['sig-ed25519/1', 'sig-p256/1']] }` lets the device present whichever of the two it can produce. A present-but-invalid group member is never masked by another member passing — it still fails the presentation, exactly like today's all-of items. A plug may declare `binds.zktag` to additionally tie its evidence to the presented zktag (`ctx.zktag`; `null` at tier A). `zk-passport/1` is **tier-A-only**: its underlying circuit has no nonce input, so its nullifier can't double as a stable tier-B/C pseudonym (see the context file below for why). `sig-ed25519/1` and `sig-p256/1` (D30/D31/D32/D36/D38, FR12, tier ceiling B) are the attester-key family: an app's own device-bound signing key vouches for the challenge binding, algorithm chosen per-device by whichever the app's Keystore actually supports — the two are co-equal reference-default alternatives, not one privileged plug (D31). D38 (2026-09-01): the key is per-origin on the device, so an item may carry `data.pubkey` (SubjectPublicKeyInfo DER, base64) alongside `key_id`; the verifier always recomputes `key_id` from `pubkey` and refuses a mismatch. An unpinned `pubkey` is trust-on-first-sight, bound to `(scope, zktag)` via a new pluggable `attesterStore` (`{ get({scope,zktag}), bind({scope,zktag,key_id,pubkey}) }`, `InMemoryAttesterStore` reference implementation, same conventions as `InMemoryNonceStore`) — a later presentation for the same `(scope, zktag)` must carry the identical pubkey, or it's `attester_key_mismatch`.

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

- Not a zero-knowledge proof by itself: bare mode is captcha-grade. The `zk-passport/1` evidence plug verifies a third-party zero-knowledge proof, validation-grade, tier A only — see chiproof.context.md. *(amended 2026-08-30, D24/D25)* Absent that plug, the verifier checks a hardware attestation that trustworthy code computed an answer; it does not check a proof it could verify itself. The accurate description is *attested windowed disclosure*. See the design doc.
- Not an identity provider, wallet, issuer, or certificate authority.
- Not certified under any regulatory scheme, and not claiming to satisfy any legal age-verification requirement anywhere.

## Design

- Product requirements: [`docs/archive/zkagent-prd.md`](https://github.com/hamr0/zkagent/blob/main/docs/product/zkagent-prd.md)
- Design and disclosure model: [`docs/product/zkagent-design.md`](https://github.com/hamr0/zkagent/blob/main/docs/product/zkagent-design.md)

## License

Apache-2.0
