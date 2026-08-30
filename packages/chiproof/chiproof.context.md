# chiproof — Integration Guide

## What this is

`chiproof` is the verifier SDK of the [zkagent](https://github.com/hamr0/zkagent)
project: a stateless Node library that checks an attested, windowed
disclosure derived from an ICAO 9303 chip document (the chip already in a
passport or a modern national ID card). You call `createVerifier(config)`
once, then `issueChallenge()` to mint a nonce, hand it to a client, and
`verify(presentation, ctx)` when the client comes back. The library does no
cryptographic math of its own beyond `node:crypto` primitives — it calls
pinned verification keys and adopter-registered evidence plugs the way a
browser calls a TLS library, and it never stores anything: every piece of
state (the nonce store, the trust lists, the challenge secret) is supplied by
you at construction.

## What chiproof is and is not

- It is a **verifier**, not an identity provider, wallet, issuer, or
  certificate authority. It never talks to a government, a masterlist
  server, or a phone directly — a client presentation arrives already built.
- With **no evidence plug configured** (the default — `evidence.require`
  empty), a "verified" verdict means only *"a nonce was spent once, on time,
  under a challenge this process minted, and the caller declared an answer"*.
  This is **bare mode: captcha-grade, not identity-grade** — it proves
  nothing about a person, a document, or a device. See "Threat model
  summary" below before treating any unconfigured verifier's output as
  evidence of anything beyond that.
- `zk-passport/1`, the one real cryptographic-proof plug shipped in this
  version, is **tier-A-only** — see "What's NOT in chiproof, and why".
- It does not run an HTTP server, a queue, or a background job. There is no
  `gate`-style helper in this version (M1 spec §5 B4) — you wire challenge
  issuance and verification into your own request handlers.

## Minimal usage

```js
import { createVerifier, InMemoryNonceStore } from 'chiproof';

// InMemoryNonceStore is TEST-ONLY (single process, no persistence). A real
// deployment must pass a real atomic NonceStore — see "NonceStore contract".
process.env.NODE_ENV = 'test';

const verifier = createVerifier({
  scopeDomain: 'example.com',
  challengeSecret: 'a-secret-at-least-16-bytes-long',
  stores: { nonce: new InMemoryNonceStore({ quiet: true }) },
});

const challenge = verifier.issueChallenge({ tier: 'A', ttlMs: 60_000 });
// ... hand `challenge` to the client; it comes back inside a presentation ...

const verdict = await verifier.verify({
  spec: 'zkagent/1',
  tier: 'A',
  claim: { over_threshold: true, threshold: 18 },
  challenge,
});
// => { ok: true, allowed: true, tier: 'A', reason: 'no-evidence-required', evidence: [] }
```

Run this exact snippet: it is real, copy-pasteable code (verified against
`src/index.js` on 2026-08-30, not hand-typed and hoped-for).

## All options

`createVerifier(config)` validates every key below **at construction** and
throws a `TypeError` describing the first defect — nothing here is silently
accepted and weakened later.

| Option | Type | Default | What it does / what's checked at boot |
|---|---|---|---|
| `stores.nonce` | `NonceStore` | **required** | Must implement `setIfAbsent(key, ttlMs)`. See "NonceStore contract". |
| `challengeSecret` | `Buffer\|Uint8Array\|string` | **required** | HMAC key that seals every challenge field (D20). Must be present; the underlying challenge code additionally requires **≥16 bytes of entropy** or `issueChallenge`/`verifyChallenge` throw. Stable across restarts and shared across every replica — a per-process secret rejects a sibling's nonces. |
| `threshold` | integer | `18` | The one threshold this verifier serves. `issueChallenge()` throws if a caller asks for a different one; `verify()` refuses (`threshold_mismatch`) any presentation whose claim or challenge threshold differs from this value. |
| `tiers.max` | `'A'\|'B'\|'C'` | `'A'` | Fail-closed ceiling: an adopter must opt **in** to tier B/C, never fall into them by omission. `verify()` refuses (`tier_exceeds_max`) any presentation above this. |
| `trustedChallengeIssuers` | `{pubkey, key_id, maxTier}[]` | `[]` | Pinned public keys allowed to sign challenges, each with its own tier ceiling (D20). A signed challenge from an unlisted `key_id`, or above its ceiling, is refused. |
| `trustedClients` | `{name?, package, certDigest, specVersion?}[]` | `[]` | FR10 trust list. If `ctx.clientIdentity` is supplied to `verify()` and this list is non-empty, the identity must match an entry (`package` + `certDigest`, and `specVersion` if both sides supply it) or the presentation is refused (`client_untrusted`). |
| `scopeDomain` | non-empty `string` | **required** | This verifier's own scope. Bound into `signed-receipt/1`'s signed message and `zk-passport/1`'s `service_scope` field; passed to every plug via `ctx.scopeDomain`. |
| `masterlistRoot` | `string` | `undefined` | Not validated by the core. Passed through unchanged to plugs via `ctx.masterlistRoot` for a plug that needs it (e.g. to check which masterlist snapshot a document's chain was verified against). The core does not read or fetch a masterlist itself — masterlist verification is the phone's job (M1 spec §1). |
| `evidence.plugs` | `Record<string, Plug>` | `{}` | Plugs to register, keyed by `"type/version"` (e.g. `"zk-passport/1"`). Each value is the object returned by a plug factory (`zkPassport(...)`, `signedReceipt(...)`, or your own). Registration runs `assertPlug` and throws on a malformed plug — see "Evidence plug contract". |
| `evidence.require` | `string[]` | `[]` | Evidence types (registry keys) that **must** be present and valid, or `verify()` refuses (`evidence_required_missing`). Empty = bare mode. Every entry must name a plug registered in `evidence.plugs`, or construction throws. |
| `evidence.accept` | `string[]` | `[]` | Evidence types checked **if present**, but not required. Same registration constraint as `require`. |
| `evidence.maxItems` | integer ≥ 1 | `4` | Hard cap on `presentation.evidence.length`, enforced before any plug runs — a presentation cannot buy N plug verifications for the price of one. |
| `evidence.maxItemBytes` | integer ≥ 1 | `262144` | Hard cap on one evidence item's canonical-JSON byte size, enforced before any plug runs. |
| `allowInMemoryStore` | `boolean` | `false` | Explicit override to boot with `InMemoryNonceStore` outside `NODE_ENV==='test'`. You should not need this outside a demo — see "Gotchas". |

## Public API

Every export below is re-exported from the package root (`import { ... } from 'chiproof'`).

| Export | Contract |
|---|---|
| `createVerifier(config)` | **Throws** `TypeError` on a bad config shape (see "All options"). Returns `{ issueChallenge(opts), verify(presentation, ctx) }` — never throws again after construction. |
| `.issueChallenge(opts)` | `opts: { tier, verbs?, max_scan_age?, ttlMs, issuer?, now? }`. **Throws** `TypeError` on a bad `opts` shape (a config error, not untrusted input) or if `opts.threshold` disagrees with the configured threshold. `threshold` and `challengeSecret` are always taken from the verifier's own config. Returns the challenge object (see M1 spec §3). |
| `.verify(presentation, ctx?)` | `ctx: { now?, clientIdentity? }`. **Never throws**, for any input — internal failures are mapped to `{ok:false, allowed:null, reason:'verifier_internal_error'}`. Returns `Promise<Verdict>`. |
| `cannotCheck(reason)` | Pure, never throws. Builds `{ok:false, allowed:null, reason}` — "we could not get a trustworthy answer." |
| `realNo(reason)` | Pure, never throws. Builds `{ok:true, allowed:false, reason}` — "we got an answer, and it was negative." |
| `yes(extra)` | Pure, never throws. Builds `{...extra, ok:true, allowed:true}` — `extra` is spread **before** `ok`/`allowed` are set, so it can never override them. |
| `canonicalize(value)` | Pure. **Throws** `TypeError` on a float, non-finite number, `undefined`, function, symbol, or non-safe-integer — a JCS-like canonical JSON serializer for signing, not a general-purpose one. |
| `sha256(value)` | `createHash('sha256').update(canonicalize(value)).digest()`. Propagates `canonicalize`'s throw. |
| `issueChallenge(opts)` | The unwrapped primitive behind `verifier.issueChallenge`. **Throws** `TypeError` on bad config (`tier`, `threshold`, `ttlMs`, `now`, or a `challengeSecret` under 16 bytes) — config errors fail loud, they are never smuggled into an attacker-shaped result. |
| `verifyChallenge(challenge, opts)` | **Never throws** on untrusted `challenge` input. Returns `{ok, valid, reason}`. |
| `spendNonce(challenge, store, opts)` | **Never throws** — a store that rejects or throws is mapped to `{ok:false, valid:null, reason:'nonce_store_unreachable'}`. Returns `Promise<{ok, valid, reason}>`. |
| `InMemoryNonceStore` | Test-only `NonceStore`. See "Gotchas". |
| `EvidenceRegistry` | `new EvidenceRegistry()`; `.registerPlug(type, plug)` **throws** via `assertPlug` on a malformed plug; `.has(type)`, `.get(type)`. |
| `assertPlug(type, plug)` | **Throws** `TypeError` describing the first defect in a plug's declaration. Used internally by `registerPlug`; exported so you can validate a custom plug before registering it. |
| `routeEvidence(slot, items, tier, ctx)` | **Never throws.** Returns either `{verified: string[], warnings: string[]}` on success, or a verdict (`realNo(...)`/`cannotCheck(...)`) on refusal — the internal pipeline `createVerifier` wires up for you; documented for anyone building an alternative pipeline around the same plugs. |
| `signedReceipt({keys})` | **Throws** `TypeError` at registration if `keys` is empty/malformed or has a duplicate `key_id`. Returns a `Plug` (linkability `'signer'`, tier ceiling `'C'`) whose `verify()` never throws. |
| `receiptMessage(claim, nonce, scopeDomain)` | Pure. Returns the exact `Buffer` a signer must sign for `signed-receipt/1`. Propagates `canonicalize`'s throw if `claim` is unsignable (e.g. contains a float). |
| `zkPassport({bbPath, vks, threshold, timeoutMs?, tmpDir?})` | **Throws** `TypeError` at registration if `bb` cannot be run at `bbPath`, reports a version other than `5.0.0`, `threshold` has no pinned `param_commitment`, or `vks` is malformed. Returns a `Plug` (linkability `'none'`, tier ceiling `'A'`) whose `verify()` never throws (bb failures map to `ok:false`, never a thrown error). |
| `subscopeFromNonce(nonce)` / `scopeField(scopeDomain)` | Pure. Each returns a 32-byte `Buffer` (first byte `0x00`, then the first 31 bytes of `sha256(utf8(input))`) — see "Constraints" for why this construction, not `@zkpassport/utils`'s. |
| `paramCommitment(threshold)` | Pure. Returns the pinned 32-byte `Buffer` for a threshold from the vendored table, or `undefined` if unpinned. |

## NonceStore contract

```
NonceStore { setIfAbsent(key: string, ttlMs: number): Promise<boolean> }
```

- **Must be atomic** — the same "SET NX PX" shape as `Redis SET key NX PX ttl`
  (set the key only if absent, with an expiry, as one indivisible operation).
  `true` means "this call recorded it — first use, fresh"; `false` means
  "already present — a replay."
- **Workers KV is NOT atomic** and must not be used to implement this
  contract directly: its writes are eventually consistent across regions, so
  two concurrent `setIfAbsent` calls for the same key can both observe
  "absent" and both return `true`, defeating single-use replay defence
  exactly when it matters (a race, not a crash — it will look fine in
  testing). Use a backend with a real compare-and-set or `NX`-style
  primitive (Redis, DynamoDB conditional writes, a SQL `UNIQUE` constraint
  with `INSERT ... ON CONFLICT DO NOTHING`, etc.).
- A store that throws or rejects is **not** a "no" — `spendNonce` maps it to
  `{ok:false, valid:null, reason:'nonce_store_unreachable'}`, which
  `verify()` turns into `{ok:false, allowed:null}` (D3, NO-GO #1: an
  unreachable store fails closed, never affirmatively "no").
- `InMemoryNonceStore` (exported) satisfies the interface with a plain `Map`
  in one process. It is **test-only**: it does not survive a restart and is
  not shared across replicas — behind two instances, a replay routed to the
  instance that didn't see the first use is accepted. `createVerifier`
  refuses to boot with it unless `NODE_ENV==='test'` or
  `allowInMemoryStore:true` is passed explicitly.

## Evidence plug contract

A plug is any object shaped:

```js
{
  binds: { nonce: true, claim: true, scope: true },  // all three, or registration throws
  linkability: 'none' | 'signer' | 'device',
  tierCeiling: 'A' | 'B' | 'C',
  verify(item, ctx) {
    // may be async
    return { ok, valid, reason, expiresAt?, warnings? };
  },
}
```

**Registration-time refusals** (`assertPlug`, called by `registerPlug` and
therefore by `createVerifier` for every entry in `evidence.plugs`) — each
throws a `TypeError` naming the defect:

- `binds.nonce`, `binds.claim`, and `binds.scope` must each be exactly
  `true`. Evidence that cannot be tied to *this* challenge, *this* claim,
  and *this* scope is replayable by construction, so it is refused **at
  boot**, before it can ever reach `verify()`.
- `linkability` must be one of `'none'`, `'signer'`, `'device'`.
- `tierCeiling` must be `'A'`, `'B'`, or `'C'`.
- `verify` must be a function.

**At verify time**, the routing rules (M1 spec §4) apply before your plug's
`verify()` is ever called:

- Tier A refuses any registered evidence type whose `linkability !== 'none'`
  (`evidence_forbidden_at_tier_a`) — even if that type is in neither
  `require` nor `accept`; a device-class item's mere *presence* leaks
  linkability at tier A, so its declared class is enforced regardless of
  whether it's checked.
- A presented tier above the plug's own `tierCeiling` is refused
  (`evidence_tier_exceeds_plug_ceiling`).
- Types in neither `require` nor `accept` are recognised (if registered) or
  ignored (if not), but never verified.
- Bounds run first, on every item, before any plug is invoked: duplicate
  `type/version` keys → `evidence_duplicate`; more than
  `config.evidence.maxItems` items → `evidence_too_many`; an item whose
  canonical JSON exceeds `config.evidence.maxItemBytes` →
  `evidence_too_large`.

**`ctx` shape** (M1 spec §4), passed to every plug's `verify(item, ctx)`:

```
{
  nonce,            // string — the challenge's nonce
  claim,            // { over_threshold, threshold } — the presented claim
  tier,             // 'A'|'B'|'C' — the presented tier
  scopeDomain,      // string — this verifier's config.scopeDomain
  masterlistRoot,   // string | undefined — passed through from config, unvalidated
  trustedClients,   // the configured FR10 list, for a plug that wants to cross-check
  now,              // number (ms since epoch) — same clock verify() used
  maxScanAge,       // number | null — from challenge.max_scan_age; null = unlimited
}
```

**Your `verify()`'s return contract** — the §3 invariant extended one level:

- `ok: false` (regardless of `valid`/`reason`) → the whole presentation
  becomes `{ok:false, allowed:null}`. Use this for *your plug's own*
  failures (a backend you depend on is unreachable, a subprocess crashed) —
  never for "the evidence didn't check out," which is a real no.
- `ok: true, valid: false` → the presentation becomes
  `{ok:true, allowed:false}` with your `reason`. This is "the evidence was
  checked and it did not verify."
- `ok: true, valid: true` → the item counts toward `verdict.evidence`.
  Optionally set `expiresAt` (ms since epoch) to date your own evidence — the
  slot rejects the presentation (`evidence_expired`) if `ctx.now` is past it.
  Optionally set `warnings: string[]` for non-fatal notes (e.g.
  `tmpdir_cleanup_failed`) that should surface without changing the verdict.
- **A throwing or rejecting `verify()` is caught by the slot and mapped to
  `ok:false`** (`evidence_plug_failed`) — a broken plug is never evidence
  about a person. You do not need your own top-level try/catch for this
  specific guarantee, but you should still use one to attach a useful
  `reason`/`ok:false` yourself rather than relying on the generic fallback.

## What's NOT in chiproof, and why

- **No relay or server we run.** chiproof is a library you call in-process;
  there is no chiproof-operated endpoint of any kind (NO-GO #3 — no CA, no
  issuer, no enrollment server, no trust list run by us). Every store, key
  list, and secret is yours.
- **No HTTP gate helper.** An `8een`-style `gate.js` HTTP wrapper is
  explicitly out of scope for this version (M1 spec §5, bucket B4: "A
  `gate`-style HTTP helper is out of M1 — TBD later"). You wire
  `issueChallenge`/`verify` into your own request handlers.
- **No ZK circuits of ours.** chiproof verifies proofs against pinned
  verification keys; it does not write, compile, fork, or vendor a circuit
  (D1, as amended by D24: ZK proofs are allowed in v1 as an evidence plug,
  validation-grade, but no circuit is ours).
- **No Play Integrity plug.** Google Play Integrity tokens are decode-able
  only by the app developer's own Google Cloud project — non-transferable
  per Google's ToS and gated by a per-app quota. That makes it a
  **non-borrowable** voucher: it cannot be shipped as a plug anyone can drop
  in, only wired up per-adopter as their own `signed-receipt/1` (the
  adopter's own decode, signed by the adopter, verified by chiproof as any
  other signed receipt) (D23 superseded by D24 on exactly this point).
- **No zktag derivation validation.** How a client derives a stable
  pseudonym from chip material is out of scope for this version (FR11 is
  future work) — chiproof only checks that a `zktag` is present/absent per
  the tier rules (D21), never how it was computed.
- **`zk-passport/1` is tier-A-only.** The zkPassport age circuit exposes no
  nonce input at all; its one free public field, `service_subscope`, is what
  feeds the circuit's own nullifier. To bind the challenge nonce at all, this
  plug carries it *in* `service_subscope` — which makes the nullifier
  per-request (ideal for tier A: unlinkable by construction) but unusable as
  a stable tier-B/C pseudonym. Tier B/C ZK evidence needs a circuit exposing
  *both* a stable nullifier and a fresh nonce, and is deferred (PRD D25, open
  as Q26). No circuit is forked to fix this — D1 stands.

## Threat model summary

- **The core invariant (M1 spec §3): `ok:false` ⇒ `allowed:null`, never
  `false`.** A broken verifier that answers "no" is indistinguishable from a
  working one — it would turn away every legitimate holder while looking
  healthy. This is enforced structurally: `cannotCheck`/`realNo`/`yes` are
  the *only* three ways to build a verdict, and none of them can construct
  the forbidden `{ok:false, allowed:false}` shape.
- **The challenge is sealed, not just recognised (D20 seal).** The nonce's
  HMAC covers every challenge field (`tier`, `verbs`, `threshold`,
  `max_scan_age`, `expires_at`), not just a random value — editing any field
  after minting breaks the tag (`nonce_forged`), so even an *unsigned*
  tier-A/B challenge is tamper-evident.
- **Single-use is checked before anything expensive runs.** `spendNonce`
  runs immediately after the challenge is confirmed live, ahead of tier,
  threshold, and evidence checks, so a replayed nonce is rejected as cheaply
  as possible and never buys a second evidence-plug run (which, for
  `zk-passport/1`, means a `bb` subprocess).
- **What a leaked `challengeSecret` does and does not allow.** It lets an
  attacker **mint** challenges that `verifyChallenge` will accept as
  genuinely-ours (the HMAC is the only thing that says "we minted this"). It
  does **not** let them forge a passing *evidence* answer: the threshold is
  checked three ways (`claim.threshold === challenge.threshold ===
  config.threshold`), and in any mode with `evidence.require` non-empty, a
  minted challenge still needs a real evidence item that binds that
  specific nonce/claim/scope and verifies against a plug's own key material
  (a signature key, a proof verification key) that the secret leak does not
  expose. In **bare mode** (no evidence required), a leaked secret plus a
  self-declared `claim.over_threshold: true` *is* enough to pass — this is
  exactly why bare mode is captcha-grade, not identity-grade, independent of
  any secret leak.
- **Bare mode's guarantee is narrow on purpose.** It proves a nonce was
  spent once, on time, under a challenge this process minted. It does not
  prevent fabrication of the claim itself — only replay. State this
  explicitly to your own users if you ship bare mode; do not let "verified"
  imply more than it does.
- **Evidence binding (nonce + claim + scope) is what stops proof reuse once
  evidence is required.** Every shipped plug's `binds` are all `true` by
  registration-time requirement — an evidence item that doesn't tie to
  *this* nonce, claim, and scope is rejected at registration, before it can
  ever be replayed at verify time.
- **Evidence bounds run before any plug does.** Item-count and item-size
  limits are enforced by the slot itself, on untrusted input, before a
  single plug's `verify()` is invoked — a presentation cannot buy N
  expensive verifications (e.g. N `bb verify` subprocess runs) for the cost
  of one malformed item.

## Gotchas

- **Changing the nonce format invalidates every challenge already issued.**
  The nonce is a fixed 56-byte frame (16 random + 8 `issued_at` + 32 HMAC
  tag); a challenge minted before a `challengeSecret` rotation, or before any
  change to the sealed-field set, fails `verifyChallenge` as
  `nonce_forged` on presentation — by design (a rotated secret should
  invalidate outstanding challenges), but plan your rotation window
  accordingly (challenges expire on their own `ttlMs`, so a rotation that
  waits out the longest outstanding `ttlMs` has zero fallout).
- **`max_scan_age` only does something if a plug reads it.** The core passes
  `ctx.maxScanAge` to every plug; `zk-passport/1` enforces it against the
  proof's own `current_date` field, but `signed-receipt/1` does not enforce
  it at all (nothing in that envelope carries a scan date) — a receipt-only
  configuration gets no scan-age enforcement unless your own signer encodes
  and checks one.
- **`zkPassport({bbPath, ...})` needs an explicit, pinned path — `bb` is
  never searched on `PATH`.** Registration runs `bb --version` at `bbPath`
  and throws unless it reports exactly `5.0.0`; there is no fallback to a
  system-wide install.
- **A `bb` timeout, signal, or spawn failure is `ok:false`, never
  `valid:false`.** Only a clean non-zero exit from `bb verify` itself counts
  as "the proof didn't check out." A killed process, a timeout, or an
  `ENOENT` (bad path) means the verifier couldn't get an answer at all —
  never mistake a broken toolchain for a rejected proof.
- **`zk-passport/1` can report a `warnings: ['tmpdir_cleanup_failed']`
  alongside a successful verdict.** Its temp directory (proof/public-input
  files written per verify call) is best-effort cleaned up; a cleanup
  failure does not fail the verdict but does surface as a warning worth
  logging and alerting on (disk fills up silently otherwise).

## Constraints

- **Node ≥ 20.** Uses `node:crypto`'s `sign`/`verify` with an explicit `null`
  algorithm (Ed25519 raw), `timingSafeEqual`, and `node:test` — all stable
  since Node 18/20; the `engines` field pins the floor the package is tested
  against.
- **Zero runtime dependencies.** `package.json` `dependencies` is `{}` (NO-GO
  #2/#9). Everything — HMAC, Ed25519, SHA-256, subprocess control — comes
  from Node's standard library. `bb` is an external *binary* you supply a
  path to, not an npm dependency.
- **Canonical JSON accepts only integers and strings as numeric/scalar
  leaves — floats are rejected.** `canonicalize()` throws on any
  non-integer number (and on non-safe integers, `NaN`, `Infinity`,
  `undefined`, functions, symbols). This is deliberate: RFC 8785's
  ECMAScript-number serialization has enough edge cases (`-0`, `1e21`) that
  a signing canonicalizer should not depend on them — anything you sign or
  hash through this package must encode fractional values as strings.
- **`presentation.spec` must be exactly `'zkagent/1'`.** There is no version
  negotiation in this release (PRD, signed 2026-08-30) — anything else is a
  real no (`unsupported_spec`), not an upgrade path.
- **`service_scope`/`service_subscope` are this project's own field rule,
  not an import.** `scopeField`/`subscopeFromNonce` are defined here (FR12
  registry, `zk-passport/1`) and built to be interoperable with the field
  derivation zkPassport's own tooling uses — checked against the public
  inputs of real proofs (`spikes/m1-zk`), not merely assumed compatible. No
  code from `@zkpassport/utils` is included in chiproof; that package
  declares no license (no `license` field, no `LICENSE` file, and no license
  in the upstream `zkpassport-packages` repository's own metadata), so
  nothing from it is reproduced or imported here.
