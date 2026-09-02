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
- `sig-ed25519/1` and `sig-p256/1` (D30/FR12, PRD §6.2 items 1/9/11) are the
  attester-key plug family, tier ceiling **B** — see "Attester-key evidence
  plug family" below. `sig-p256/1` is a **candidate plug name under a
  pending decision number (`Dn`)**, not an owner-numbered decision the way
  `sig-ed25519/1`/D30 is.
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
| `evidence.plugs` | `Record<string, Plug>` | `{}` | Plugs to register, keyed by `"type/version"` (e.g. `"zk-passport/1"`). Each value is the object returned by a plug factory (`zkPassport(...)`, `signedReceipt(...)`, `sigEd25519(...)`, `sigP256(...)`, or your own). Registration runs `assertPlug` and throws on a malformed plug — see "Evidence plug contract". |
| `evidence.require` | `RequireEntry[]` or `{A?: RequireEntry[], B?: RequireEntry[], C?: RequireEntry[]}` | `[]` | Evidence that **must** be present and valid, or `verify()` refuses (`evidence_required_missing`). Empty = bare mode. The plain-array form applies at **every** tier (the 0.2.0 semantics, unchanged); the per-tier object form lets ONE instance serve a bare tier A next to an evidence-required tier B (D27/D30) — a tier absent from the object requires nothing at that tier. A `RequireEntry` is a registry-key string (all-of, e.g. `'sig-ed25519/1'`) **or** a non-empty array of registry-key strings — an **alternatives group** (D31/D36, 0.4.0): satisfied when at least one member is present and verifies, e.g. `require: { B: [['sig-ed25519/1', 'sig-p256/1']] }`. A group member that is present but fails verification is a real no exactly like any other checked item — it is never masked by another member of the same group passing. Every string named anywhere in `require` (bare or inside a group) must name a plug registered in `evidence.plugs`, or construction throws. |
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
| `InMemoryAttesterStore` | Test-only `AttesterStore` (D38). See "AttesterStore contract". |
| `EvidenceRegistry` | `new EvidenceRegistry()`; `.registerPlug(type, plug)` **throws** via `assertPlug` on a malformed plug; `.has(type)`, `.get(type)`. |
| `assertPlug(type, plug)` | **Throws** `TypeError` describing the first defect in a plug's declaration. Used internally by `registerPlug`; exported so you can validate a custom plug before registering it. |
| `routeEvidence(slot, items, tier, ctx)` | **Never throws.** Returns either `{verified: string[], warnings: string[]}` on success, or a verdict (`realNo(...)`/`cannotCheck(...)`) on refusal — the internal pipeline `createVerifier` wires up for you; documented for anyone building an alternative pipeline around the same plugs. `slot.require` may be a plain array (0.2.0 form, same list at every tier) or a per-tier `{A, B, C}` object; each entry may be a string or an alternatives-group array (0.4.0, D31). `verified` lists every registry key actually checked, in presentation order — including which member of a satisfied alternatives group was used (D31: "the verdict MUST record which plug was actually used"); `verify()` re-exposes this unchanged as `Verdict.evidence`. |
| `normalizeRequire(raw)` | **Throws** `TypeError` on a shape that is neither an array nor a per-tier `{A?, B?, C?}` object, or whose entries are not a registry-key string or a non-empty array of registry-key strings. Returns the frozen `{A, B, C}` object `createVerifier` builds from `config.evidence.require` — exported for anyone wiring `routeEvidence` directly. |
| `signedReceipt({keys})` | **Throws** `TypeError` at registration if `keys` is empty/malformed or has a duplicate `key_id`. Returns a `Plug` (linkability `'signer'`, tier ceiling `'C'`) whose `verify()` never throws. |
| `receiptMessage(claim, nonce, scopeDomain)` | Pure. Returns the exact `Buffer` a signer must sign for `signed-receipt/1`. Propagates `canonicalize`'s throw if `claim` is unsignable (e.g. contains a float). |
| `zkPassport({bbPath, vks, threshold, timeoutMs?, tmpDir?})` | **Throws** `TypeError` at registration if `bb` cannot be run at `bbPath`, reports a version other than `5.0.0`, `threshold` has no pinned `param_commitment`, or `vks` is malformed. Returns a `Plug` (linkability `'none'`, tier ceiling `'A'`) whose `verify()` never throws (bb failures map to `ok:false`, never a thrown error). |
| `subscopeFromNonce(nonce)` / `scopeField(scopeDomain)` | Pure. Each returns a 32-byte `Buffer` (first byte `0x00`, then the first 31 bytes of `sha256(utf8(input))`) — see "Constraints" for why this construction, not `@zkpassport/utils`'s. |
| `paramCommitment(threshold)` | Pure. Returns the pinned 32-byte `Buffer` for a threshold from the vendored table, or `undefined` if unpinned. |
| `sigEd25519({keys, attesterStore})` | **Throws** `TypeError` at registration if `keys` is malformed, has a duplicate `key_id`, or is empty/absent WITHOUT an `attesterStore` also supplied (D38: a store-only registration, no pinned keys at all, is valid); also throws if `attesterStore` is supplied but doesn't implement `{get, bind}`. Returns a `Plug` (linkability `'signer'`, tier ceiling `'B'`, `binds.zktag: true`) whose `verify()` never throws — see "Attester-key evidence plug family" for the D38 pinned/store key-resolution order. |
| `sigEd25519Message(claim, nonce, scopeDomain, zktag)` | Pure. Returns the exact `Buffer` an attester must sign for `sig-ed25519/1` — `sha256(preimage)`. Propagates `canonicalize`'s throw if `claim` is unsignable. |
| `sigP256({keys, attesterStore})` | Same contract as `sigEd25519({keys, attesterStore})`, for the candidate `sig-p256/1` plug (`Dn` pending). |
| `sigP256Message(claim, nonce, scopeDomain, zktag)` | Pure. Returns the exact `Buffer` an attester must sign for `sig-p256/1` — the raw `preimage`, unhashed (see "Attester-key evidence plug family"). |
| `keyIdFor(publicKeyDer)` | Pure (D38). `sha256(publicKeyDer)` as lowercase hex, truncated to 16 chars (8 bytes) — MUST stay byte-identical to the scanner's Kotlin `EvidenceSigner.keyIdFor`. Used to recompute and verify a presented item's `key_id` from its carried `pubkey`; exported so a client/adopter can produce the same id. |

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

## AttesterStore contract (D38)

```
AttesterStore {
  get(key: {scope: string, zktag: string}): Promise<{key_id: string, pubkey: Buffer}|undefined>
  bind(binding: {scope: string, zktag: string, key_id: string, pubkey: Buffer}): Promise<void>
}
```

Passed as `attesterStore` to `sigEd25519({keys?, attesterStore})` /
`sigP256({keys?, attesterStore})` (D38) — NOT wired through
`createVerifier`'s `stores` config, unlike `NonceStore`: each attester-sig
plug takes its own, the same way `signedReceipt`/`zkPassport` take their own
options, since which plug instance owns which store is a per-plug choice,
not a verifier-wide one.

- `get` returning `undefined` means "no binding yet for this `(scope,
  zktag)`" — first sight. A defined result means a prior presentation was
  already verified and bound; the plug refuses (`attester_key_mismatch`) any
  later presentation for the same pair carrying a DIFFERENT `pubkey`.
- `bind` is called **only after** a signature has verified successfully —
  never before, and never on a failed or unverifiable presentation. A store
  therefore never records an unproven key.
- Either method throwing (or rejecting) is **not** a "no" — the plug maps it
  to `{ok:false, valid:null, reason:'attester_store_unreachable'}`, which
  `verify()` turns into `{ok:false, allowed:null}` upstream, same discipline
  as `NonceStore`'s `nonce_store_unreachable`.
- `InMemoryAttesterStore` (exported) satisfies the interface with a plain
  `Map` in one process. It is **test-only**, same caveats as
  `InMemoryNonceStore`: no persistence, no cross-replica sharing — behind two
  replicas, the SAME device could bind under a different key on whichever
  replica it happens to hit next, exactly the hijack the real contract
  exists to prevent. A real deployment needs a real shared, atomic store
  keyed on `scope` + `zktag` (Redis/Postgres/etc — no `setIfAbsent`-style
  atomicity requirement here since a plain conditional write suffices: the
  plug itself, not the store, decides "match vs. mismatch vs. first sight").

## Evidence plug contract

A plug is any object shaped:

```js
{
  binds: { nonce: true, claim: true, scope: true, zktag?: boolean },
                    // nonce/claim/scope: all three, or registration throws
                    // zktag (optional, default false): this evidence is also
                    // tied to the presented zktag (ctx.zktag)
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
- `binds.zktag`, when declared, must be a boolean. Declaring `true` means the
  evidence is additionally bound to the presented zktag, so a valid item
  cannot be replayed under a different zktag (the zktag-swap attack).
- `binds.zktag === true` with `tierCeiling: 'A'` is refused — tier A never
  carries a zktag (D21), so such a plug could never run.
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
- A checked item whose plug declares `binds.zktag === true`, on a
  presentation that carries no zktag (tier A), is "could not check":
  `{ok:false, allowed:null, reason:'evidence_zktag_unavailable'}` — the
  binding cannot be evaluated at all, which is never a "no" (§3 invariant).
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
  zktag,            // string | null — the presented zktag (tier B/C); null at
                    // tier A. A plug declaring binds.zktag === true never sees
                    // null here: the router answers evidence_zktag_unavailable
                    // before calling it.
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

## Attester-key evidence plug family (`sig-ed25519/1`, `sig-p256/1`)

The D30 attester-key plugs (PRD §6.2 items 1/9/11, FR12): an app's own
device-bound signing key (its Android Keystore attester key, D30 — never fed
into zktag derivation) signs the challenge binding. `sig-ed25519/1` is the
reference default for mode-B presentations (D30); `sig-p256/1` is a
**candidate plug name under a pending decision number (`Dn`)**, added because
Ed25519 is unavailable as an AndroidKeyStore key on the Pixel 6a, at either
security level, by either entry point (`docs/logs/M2-SESSION-POC.md` F2) —
P-256 is what StrongBox actually offers on that device.

Both share ONE preimage definition, stated once, so their implementation
cannot drift apart:

```
preimage = utf8(PLUG_TYPE + "\n")
         ‖ sha256(canonical(claim))
         ‖ base64urlDecode(nonce)
         ‖ utf8(scopeDomain)
         ‖ utf8(zktag)
```

`PLUG_TYPE` is the literal plug string (`"sig-ed25519/1"` or `"sig-p256/1"`)
— the domain separator that makes a signature minted for one algorithm
unusable as the other, even over an otherwise-identical claim/nonce/scope/
zktag. The nonce bytes are base64url-**decoded**, not the UTF-8 string, same
convention as `signed-receipt/1`.

The two algorithms differ only in **where** sha256 is applied, and that
difference is forced by each platform's own signing primitive, not chosen:

- **`sig-ed25519/1`**: `Ed25519(sha256(preimage))` — Ed25519 has no prehash
  step in the Node/JCA APIs, so the signer is handed the 32-byte digest, not
  the raw preimage.
- **`sig-p256/1`**: `ECDSA-P256-with-SHA256(preimage)` — Android Keystore's
  `SHA256withECDSA` (and Node's `crypto.sign('sha256', ...)`) hashes its own
  input, so the signer is handed the raw preimage and SHA-256 happens inside
  the signing primitive.

Applying SHA-256 in each algorithm's own native place is what keeps this ONE
preimage definition true on both sides. **This reading of item 9's layout for
the P-256 case is an orchestrator recommendation, not an owner decision — it
may be vetoed.**

`item.data = { key_id, pubkey?, sig }` (base64), following `signed-receipt/1`'s
precedent (`pubkey` added in D38, see below). The **P-256 signature is
DER-encoded ECDSA** — Node's `crypto.verify` default, and what Android
Keystore produces by default for a `SHA256withECDSA` key. No IEEE-P1363/raw
`r‖s` decoding is implemented: it is an explicit-opt-in-only shape per the
build instructions, and nothing built so far needs it, so it was left out
rather than added speculatively.

Which algorithm a given presentation used is identified by **the plug type
itself** (item 9: "MUST be reported alongside the evidence, not inferred by
the verifier") — there is no in-band `alg` field the verifier trusts; the
registry key (`sig-ed25519/1` vs `sig-p256/1`) is the only source of truth.

Both plugs: `binds: { nonce: true, claim: true, scope: true, zktag: true }`,
`linkability: 'signer'`, `tierCeiling: 'B'` (a signer key stable across sites
would break tier A's cross-site bar, D22/FR9 — the ceiling is
orchestrator-recommended, owner may veto it, same as `sig-ed25519/1`'s
original FR12 entry). **D38 keeps this declaration unchanged (`'signer'`) —
flagged, not silently reconsidered:** `evidence.js`'s tier gating (the
`tier === 'A' && plug.linkability !== 'none'` check) treats `'signer'` and
`'device'` identically today, so nothing in the code forced either answer;
per-origin keys (below) are, if anything, a weaker cross-site linkability
signal than a hardware attestation chain (`key-attestation/1`'s literal
`'device'` example in FR12) would be, since each site sees a *different* key
for the same physical device — closer to "a party the adopter trusts signs"
than to a stable hardware fingerprint. See owner escalation in the D38 report.

### D38 (2026-09-01): per-origin device keys, trust-on-first-sight

The scanner now generates the mode-B attester key **per origin** (site/scope)
rather than one fixed device key, and a presentation MAY carry the key's own
`pubkey` (SubjectPublicKeyInfo DER, base64) alongside `key_id` — the verifier
never trusts a caller-declared `key_id` on its own once a `pubkey` is
carried: `key_id` is always independently recomputed via `keyIdFor(pubkey)`
and a mismatch is `sig_key_id_mismatch`, never silently accepted.

**Key resolution, in order:**

1. **Operator-pinned** (`keys` at registration) — unchanged pre-D38
   behaviour. An item without `pubkey` is only ever accepted this way.
2. **Else, `attesterStore`-backed (D38)**, only if the item carries
   `pubkey` and a store was configured: look up the binding for
   `(ctx.scopeDomain, ctx.zktag)` (D37: scope = the challenge's scope
   domain).
   - **Binding exists** — the carried `pubkey` MUST equal the bound one, or
     it's `attester_key_mismatch` (`valid:false`) — a DIFFERENT key showing
     up for an already-recognised `(scope, zktag)` is refused outright,
     never silently re-bound (that would let a relay hijack a recognised
     zktag under its own key).
   - **No binding (first sight)** — the carried `pubkey` verifies the
     signature; **only on success** is the binding recorded
     (`attesterStore.bind`, never before verification). The plug result
     carries the non-fatal note `attester_bound_first_sight` in its
     `warnings` array (the existing pass-through channel — `evidence.js`
     forwards `result.warnings` onto `Verdict.warnings` unchanged; no
     parallel field was added).
3. **Neither applies** (unpinned, no store configured, or no `pubkey`
   carried): `sig_unknown_key`, same reason as pre-D38.

A tier-A presentation never reaches any of this: `binds.zktag: true` means
the router refuses it upstream as `evidence_zktag_unavailable`
(`cannotCheck`) before `verify()` is ever called — D38 changes nothing here,
it was already the existing zktag-unavailable path (confirmed, not
re-implemented).

`attesterStore.get`/`.bind` throwing (or rejecting) is `ok:false` /
`allowed:null`, never surfaced as a "no" — see "AttesterStore contract"
below, same discipline as `NonceStore`.

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
