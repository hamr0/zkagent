# M1 — verifier core (`chiproof`) spec, v0.2 (2026-08-30)

> Written from the orchestrator's brief. Where the brief is silent, this document
> says so explicitly (`TBD (orchestrator)`) rather than inventing a design.
> All open TBDs from v0.1 were resolved by owner-signed decision on
> 2026-08-30 — see §5 and §8.

## 1. Purpose and non-goals

M1 builds **Layer 3 only** — the product verifier (`docs/product/learnings.md`
§6.9: "Normal code: issue the nonce, call the engine's verify, check
scope/threshold/trust list, return `{ok, allowed}`"). It does no cryptographic
math of its own; it calls layer-1 engines the way a browser calls a TLS
library.

The verifier core is:
- **Stateless** — all stores are adopter-supplied and fail closed (D3, NO-GO #1).
- **Never-throw** — every input, however malformed, returns a verdict, never an
  exception (§3 invariant).
- **Zero runtime dependencies** — `package.json` `dependencies` is empty (NO-GO
  #2, NO-GO #9, AGENT_RULES dependency hierarchy).
- **Node ≥20, ESM.**
- Tested with `node:test` (no test-framework dependency).

**Not in M1** (explicitly out of scope):
- The phone app (scanning, chip read, SOD/masterlist verification — that is
  the phone's job per the M1 PRD row: "Masterlist/SOD verification is the
  **phone's** job (the SOD never leaves the device); the verifier trusts the
  attested client").
- Any circuit (Layer 2) — M1 verifies proofs against pinned verification keys;
  it does not write, compile, or vendor circuits.
- Any server we run (NO-GO #3 — no CA, no issuer, no enrollment server, no
  trust list run by us).
- Tier-C verbs (Q22 — deferred).
- Rung 2 (agent delegation, RFC 9421 signing) — frozen until rung 1 ships
  (D18).

## 2. Public API (target)

```
createVerifier(config) → { issueChallenge(opts), verify(presentation, ctx) }
```

`verify()` returns:

```
{ ok, allowed, reason, tier?, zktag?, evidence?, warnings? }
```

`tier`/`zktag`/`evidence` only on `allowed:true`; `evidence` lists the
`type/version` keys actually verified (`[]` in bare mode). `warnings?` carries
non-fatal plug notes (e.g. `tmpdir_cleanup_failed`) without changing the verdict.

**The §3 invariant applies**: `ok:false ⇒ allowed:null`, never `false`.

**Config shape:**

```
{
  threshold = 18,
  tiers: { max: 'A' | 'B' | 'C' },
  issuer: { privateKey?, publicKeys: [] },
  trustedChallengeIssuers: [{ pubkey, key_id, maxTier }],
  trustedClients: [{ name, package, certDigest, specVersion }],
  acceptedDocuments,
  evidence: { require: [], accept: [], plugs: {} },
  stores: { nonce: adopterSupplied },
  challengeSecret,   // HMAC key for self-authenticating nonces; shared across replicas, from env
  scopeDomain,       // this verifier's own scope; bound into receipts and the zk service_scope field
  masterlistRoot?,   // optional; passed to plugs via ctx
}
```

Stores are adopter-supplied and fail closed (D3): an unreachable store is
`ok:false`, never a "no".

## 3. Objects

Shapes copied verbatim from the PRD.

**Challenge** (D20):

```
{ nonce, tier, verbs, threshold, max_scan_age, issued_at, expires_at, key_id, sig }
```

Unsigned challenges are accepted at tiers A and B (D20: "nothing to protect
that the tier does not already protect"); refused at tier C. Expiry
precedence: **the earlier of** the challenge's own expiry and any evidence
item's expiry.

**Presentation:**

```
{
  spec: 'zkagent/1',
  tier,
  claim: { over_threshold, threshold },
  challenge,
  zktag?,
  chip_auth?,   // B/C only (D21)
  evidence: [{ type, version, data }],
}
```

**Verdict:** `{ ok, allowed, reason, tier?, zktag?, evidence?, warnings? }` (§2). `tier`/`zktag`/`evidence` only on `allowed:true`.

## 4. Evidence slot (D24)

Plug contract:

```
verifyEvidence(item, ctx) → { ok, valid, reason }
```

`ctx` shape:

```
{ nonce, claim, tier, scopeDomain, masterlistRoot?, trustedClients, now,
  maxScanAge }   // ms, from challenge.max_scan_age; null = unlimited
```

**Registration-time checks** a plug must satisfy:
- declares its bindings (nonce, claim, scope);
- declares its linkability class;
- declares its tier ceiling.

**Routing rules:**
- `require` evidence types must be present and valid or the presentation is
  refused.
- `accept` evidence types are checked if present.
- Unknown evidence types are ignored — *unknown* means not registered; a
  registered type that is in neither `require` nor `accept` is still
  *recognised*, so its linkability class and tier ceiling are enforced (a
  device-class item at tier A leaks by its mere presence) even though it is
  not verified or listed in `evidence`.
- Tier A refuses any evidence type other than `'none'`.

**Bounds** (the slot limits its own untrusted input before any plug runs):
- duplicate `type/version` keys in `presentation.evidence` →
  `evidence_duplicate`;
- more than `config.evidence.maxItems` items (integer ≥ 1, default 4) →
  `evidence_too_many`;
- an item whose canonical JSON exceeds `config.evidence.maxItemBytes`
  (default 262144) → `evidence_too_large`.

**Fault isolation:** a throwing plug is caught and mapped to `ok:false`
(never `allowed:false`) — the §3 invariant extended to the evidence slot: a
broken plug is not evidence about a person.

**FR12 registry stub** — plugs named (not necessarily implemented) in M1:
- `zk-passport/1`
- `signed-receipt/1`
- `app-attest/1` (later)
- `key-attestation/1` (B/C only)

## 5. Buckets

Small, each works alone, each ends in a checkpoint.

### B1 — verdict + challenge

- `verdict.js`: never-throw, `ok`/`allowed` constructor that **cannot represent**
  `ok:false` + `allowed:false` at the type level (mirrors 8een
  `src/verdict.js:80–92`, the `answered()` / `unanswerable()` pair).
- `challenge.js` ported from 8een: HMAC self-authenticating nonce (8een
  `src/challenge.js:34–100`, `issueChallenge`/`inspectChallenge`) — amended so
  the HMAC seals **all** challenge fields (`tier, verbs, threshold,
  max_scan_age, expires_at` alongside `random ‖ issued_at`); `verifyChallenge`
  recomputes it over the presented fields, so any edit is `nonce_forged` —
  single-use
  spend via an adapter with an atomic `SET NX PX` shape (8een
  `src/challenge.js:166–199`, `applySingleUse`), an in-memory adapter for
  tests only that refuses to boot in production without a real store (8een
  `src/challenge.js:207–254`, `InMemoryNonceStore`, and `src/gate.js:372–404`,
  `startGate`'s fail-closed check for `challengeSecret` + `nonceStore` before
  the slow load).
- **Nonce store — 8een pattern (signed 2026-08-30):** adopter-supplied,
  atomic `NonceStore { setIfAbsent(key, ttlMs) → Promise<boolean> }`. The
  in-memory adapter is test-only; `createVerifier` refuses to boot with it
  unless `NODE_ENV === 'test'` or `config.allowInMemoryStore === true`. A
  store error (rejected promise, thrown, unreachable backend) maps to
  `ok:false, allowed:null` — never a real no.
- D20 signature: Ed25519 via `node:crypto`, plus `trustedChallengeIssuers`
  with a tier ceiling.

**Checkpoint:** negative matrix items 1–5 (§6) fail as specified, each paired
with a passing non-vacuity test.

### B2 — presentation + tiers + trust list

- Parse/validate presentation.
- Tier negotiation: requested vs. presented vs. issuer ceiling; refuse, never
  downgrade.
- Threshold match (D11): a proof of another threshold is rejected, not
  accepted as close enough (mirrors 8een `src/verdict.js:211`-style
  behaviour ported to the `over_threshold`/`threshold` claim shape).
- zktag presence rules: never at tier A; required at B/C.
- `chip_auth` rules (D21): reported only at tiers B/C; tier A never emits it.
- FR10 trust list check on client identity when evidence carries it.

**Checkpoint:** negative matrix items 6–10, each paired with a passing
non-vacuity test.

### B3 — evidence slot + plugs

- Slot routing (§4).
- `signed-receipt/1` plug: Ed25519 signature over
  `sha256(canonical(claim) ‖ nonce)`; `key_id` looked up in an adopter-supplied
  key list.
  - **Signing/canonicalization — Ed25519 via `node:crypto` (signed
    2026-08-30):** the signature is over the sha256 digest of the canonical
    JSON encoding of `claim` (keys sorted recursively, no whitespace, UTF-8;
    only integers and strings are permitted — floats are rejected at
    canonicalization). This is documented as JCS-like, not a full RFC 8785
    implementation.
- `zk-passport/1` plug: verifies the four-proof composition from
  `spikes/m1-zk` against pinned verification keys (spike evidence: 4 proofs,
  4 sets of public inputs, a vk per circuit, 59,072 bytes total per document —
  `spikes/m1-zk/README.md`, "Full composition results", 2026-08-30).
  - **Verification method — shell out to `bb verify` (signed 2026-08-30):**
    M1 shells out to a pinned `bb verify` (5.0.0) binary at an explicit `bbPath` passed at registration (never searched on `PATH`), version pinned 5.0.0, checked
    for presence at plug registration (not at proof-verification time). This
    keeps core zero-deps (NO-GO #2/#9); `@aztec/bb.js` WASM may become a
    separate, optional plug later but is not part of M1.
  - Binding requirement: `nonce`, `threshold`, and `scope` must appear in the
    proofs' public inputs — checked against the real public-inputs files
    from the spike, not asserted.
  - Must reject the spike's planted-negative proofs (a flipped byte).
- **B3 genericity validation — real plugs + adversarial test-only plugs
  (signed 2026-08-30):** the evidence slot is proven generic by shipping the
  vanilla core plus **two real plugs of different kinds** —
  `signed-receipt/1` (in-process signature verification) and `zk-passport/1`
  (external proof verification, run over the real `spikes/m1-zk` artefacts)
  — plus **adversarial test-only plugs** built specifically to attack the
  slot, never shipped as product:
  - a plug that always throws → must map to `ok:false`, never
    `allowed:false`;
  - a plug that declares linkability class `'device'` → must be refused at
    tier A;
  - a plug that cannot bind the nonce → must be refused at registration
    time, not at verify time.
  Mocks are test fixtures only; no mock evidence type ships as part of the
  product registry.

**Checkpoint:** negative matrix items 11–16, each paired with a passing
non-vacuity test.

### B4 — integration

A `gate`-style HTTP helper (8een `src/gate.js`) is **out of M1** — TBD later.
Instead, an end-to-end test:

1. Issue a challenge.
2. Build a presentation from the real NL and US proof artefacts (gitignored
   fixtures; the test **skips with a clear message** if the fixtures are
   absent).
3. Verify → `allowed:true`.
4. Bare mode (no evidence configured) → `allowed:true` with reason
   `'no-evidence-required'`.

## 6. Negative matrix

Each item must fail **and** be paired with a non-vacuity pass test (a
matching case that should succeed, proving the test harness can distinguish
the two).

1. Replayed nonce.
2. Expired challenge.
3. Challenge signed by an unknown key at tier C → refused.
4. Unsigned challenge at tier C → refused.
5. Nonce store unreachable → `ok:false`.
6. Tier presented > tier requested.
7. Tier C presented with issuer ceiling B → refused.
8. Threshold mismatch (proof for 21 when 18 asked).
9. zktag present in tier A → reject.
10. Client not on trust list.
11. Required evidence missing.
12. Evidence bound to a different nonce.
13. Evidence for a different claim/threshold.
14. Plug throws → `ok:false`, `allowed:null`.
15. Device-class evidence in tier A → refused.
16. `zk-passport` proof with a flipped byte → `valid:false`.

**Plus:**
- Malformed presentation → `ok:true, allowed:false` with a reason (an input
  error is a real no).
- JSON of any shape never throws.
- `presentation.spec !== 'zkagent/1'` → `ok:true, allowed:false`,
  `reason: 'unsupported_spec'` (signed 2026-08-30, §8 — no version
  negotiation in M1).

## 7. Definition of done

- All buckets' checkpoints (B1–B4) are green under `node --test`.
- Zero runtime deps: `package.json` `dependencies` is empty.
- README states bare mode is captcha-grade.
- PII: no real fixture content in tests; real proof artefacts are referenced
  only via gitignored paths.

## 8. Resolved TBDs (owner-signed 2026-08-30)

All TBDs from v0.1 are resolved. No open TBDs remain for M1.

- **`zk-passport/1` verification method (signed 2026-08-30):** shell out to a
  pinned `bb verify` (5.0.0) binary at an explicit `bbPath` passed at registration (never searched on `PATH`), version pinned 5.0.0, presence checked at plug
  registration. Core stays zero-deps; `@aztec/bb.js` WASM may become a
  separate plug later. See §5, B3.
- **Canonical JSON rule for signing (signed 2026-08-30):** sha256 of
  canonical JSON (keys sorted recursively, no whitespace, UTF-8;
  integers/strings only, floats rejected); documented as JCS-like, not full
  RFC 8785. See §5, B3.
- **Spec version negotiation (signed 2026-08-30):** out of scope for M1.
  `presentation.spec` must equal exactly `'zkagent/1'`; anything else is a
  real no, `ok:true, allowed:false`, `reason: 'unsupported_spec'`.
- **Store adapter interface (signed 2026-08-30):** `stores.nonce` is an
  adopter-supplied `NonceStore { setIfAbsent(key, ttlMs) → Promise<boolean> }`
  (8een pattern). The in-memory adapter is test-only; `createVerifier`
  refuses to boot with it unless `NODE_ENV === 'test'` or
  `allowInMemoryStore: true`. A store error maps to `ok:false, allowed:null`.
  See §5, B1.
- **Challenge HMAC secret (signed 2026-08-30):** lives at `config.challengeSecret`;
  `createVerifier` refuses to boot without it.
- **D20 seal (owner-approved 2026-08-30):** the nonce HMAC covers every
  challenge field; unsigned challenges are tamper-evident, not just
  recognisable.
