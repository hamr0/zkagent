# chiproof

**This is a name reservation. There is no code here yet. Do not install it expecting it to do anything.**

`chiproof` will be the verifier SDK of the [zkagent](https://github.com/hamr0/zkagent) project: a stateless Node library that checks an attested, windowed disclosure derived from an ICAO 9303 chip document — the chip already in a passport or a modern national ID card.

The intended shape, in one paragraph: a phone reads the chip, verifies the issuing government's own signature against a public masterlist, answers exactly one question about the holder (*over 18?*), and attests that unmodified code did the reading. `chiproof` verifies that attestation and returns a verdict. No document, date of birth, name or number ever reaches the server. By default no identifier does either — two presentations by the same person are unlinkable. A second, explicitly requested mode adds a pseudonym scoped to one domain, for the cases that need to recognise a returning human.

## Status

Pre-everything. The riskiest assumption — that a passport chip can be read and verified on a phone and yield a stable derivation — **has not yet been tested against real hardware**. Nothing in this package is implemented, and nothing about the design should be treated as settled until it is.

## What this is not

- Not a zero-knowledge proof. The verifier checks a hardware attestation that trustworthy code computed an answer; it does not check a proof it could verify itself. The accurate description is *attested windowed disclosure*. See the design doc.
- Not an identity provider, wallet, issuer, or certificate authority.
- Not certified under any regulatory scheme, and not claiming to satisfy any legal age-verification requirement anywhere.

## Design

- Product requirements: [`docs/product/zkagent-prd.md`](https://github.com/hamr0/zkagent/blob/main/docs/01-product/zkagent-prd.md)
- Design and disclosure model: [`docs/product/zkagent-design.md`](https://github.com/hamr0/zkagent/blob/main/docs/02-engineering/zkagent-design.md)

## License

Apache-2.0
