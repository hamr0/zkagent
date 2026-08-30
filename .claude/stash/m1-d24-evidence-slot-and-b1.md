# Session Stash — M1 D24: evidence slot and bucket B1 (2026-08-30, late)

Project: zkagent · Owner: hamr · Host: Fedora 44

Continuation of the Q23 spikes session (see `m1-q23-spikes-zk-vs-play-integrity.md`
and its addendum). Owner rule #2 stands: the main session orchestrates only —
no coding, no doc edits; Sonnet agents code/doc and escalate every decision to
the orchestrator.

## Summary

After D23 (voucher-grade v1, Play Integrity) was taken, the owner asked
whether Play Integrity is configured per app or per user. A cited check found
Play Integrity tokens can only be decoded by the app developer's own Cloud
project (service account inside the linked project; ToS "non-transferable";
classic-request keys are per-app secrets from Play Console; quota 10,000/day
per app, pooled across all sites) — meaning it is NOT borrowable by
third-party websites. The only options were a relay we run (already ruled out
by NO-GO #3) or sharing credentials (undocumented and unsafe). This
invalidated D23.

By contrast, App Attest (iOS) IS borrowable (Apple's public root; only the
fraud receipt needs the developer's key), and Privacy Pass / PAT tokens are
verifiable by any origin holding an issuer key.

The owner then reframed the design: the core is layer 3, exposing a generic
"evidence slot." v1 ships with the slot empty (bare / mode B, captcha-grade,
the 8een shape minus the proof); adopters fill the slot themselves (ZK proof,
signed receipt, attestation). The owner confirmed this direction.

The orchestrator explained the three-layer model (engine / circuits / product
verifier — this project builds only layer 3), that engines don't stack (a
zkPassport proof cannot feed longfellow), that a layer-3 verifier accepting
two credential types (passport via zkPassport circuits, mDoc via longfellow)
is the sensible future shape — i.e. 8een and zkagent merging at layer 3 —
that a ZK proof makes attestation redundant, and that a government can vouch
for a build (trust list plus its own receipt) but can never sign each answer
(doing so would let it see them).

longfellow-zk was checked and confirmed to have no RSA anywhere, no ASN.1, no
bignum modexp; adapting it would mean writing RSA-2048/4096 and DER circuits
in C++ from scratch — not a shortcut. It remains a watch item only; Track Z's
engine remains Barretenberg.

M1 (core build) then started.

## State of the repo

Commits `85d684a`, `c3ee545`, `6c4a480` are local, NOT pushed (3 ahead of
`origin/main`); pushing would be an admin bypass of branch protection — flag
it every time.

Uncommitted:

- `docs/product/zkagent-prd.md` → v1.10 (draft): D24 recorded, D1 amended,
  D23 marked superseded, FR12 added, FR3/FR6 notes, the M1 row rewritten to
  point at the new spec doc, a Q24 note, Q25 added, version history updated.
- `docs/product/m1-verifier-core-spec.md` (new, v0.2): eight sections —
  purpose, API, objects, the evidence slot, buckets B1–B4, a negative matrix
  (cases 1–16 plus a plus-list), definition of done, and resolved TBDs.
- `docs/index.md`, `docs/log.md` regenerated.
- `.claude/remember/AGENT_RULES.md`: a rewrite present but not made by any
  agent this session, and still unconfirmed by the owner (carried over from
  the prior session's note).

In progress: a Sonnet coder building bucket B1 in `packages/chiproof/`
(`src/verdict.js`, `canonical.js`, `challenge.js`, `stores/memory.js`,
`index.js` stub; unit and integration tests; negative-matrix cases 1–5 each
paired with a matching pass case; 8een patterns ported in with Apache-2.0
attribution). The result was not yet reported back at stash time.

Google Cloud: the free project, Play Integrity API, and service account
(Editor role) from the prior session still exist, with the key at
`~/secrets/<file>` (never in the repo). It is now relevant only to a
possible adopter-run receipt experiment, not to the product itself.

## Key decisions

Owner, 2026-08-30 late, recorded in PRD v1.10 (draft):

- **D24 — evidence slot.** Presentation shape:
  `{spec:'zkagent/1', tier, claim:{over_threshold, threshold}, challenge,
  zktag?, chip_auth?, evidence:[{type,version,data}]}`. Plug contract
  `verifyEvidence(item, ctx) → {ok, valid, reason}`, and it must never throw.
  `ok:false` forces `verdict.ok:false` / `allowed:null`; `valid:false` forces
  `allowed:false`. Every plug must bind nonce + claim + scope, or it is
  refused at registration. Adopter policy is `evidence.require[]` (empty =
  bare mode) and `evidence.accept[]`; unknown types are ignored; types whose
  linkability class is not `'none'` are refused in tier A by the core
  itself. FR12 publishes a registry of evidence types: `zk-passport/1`
  (linkability none), `signed-receipt/1` (linkable at the signer),
  `app-attest/1` (later), `key-attestation/1` (device-linked, tier B/C
  only). Precedent: WebAuthn's attestation formats, including `'none'`. A
  given build ships with one fixed evidence set (FR6). D1 is amended: ZK is
  allowed in v1 as a plug, validation-grade only — no circuits are ours. D23
  is superseded. Q25 asks whether the project's own reference app ships bare
  or with `zk-passport/1` (a signed-receipt from us would itself be a
  NO-GO #3 violation). A Q24 note records that bare tier A works on
  de-Googled devices.
- **Signed M1 decisions:**
  - Nonce store follows the 8een pattern: adopter-supplied atomic
    `NonceStore{setIfAbsent(key, ttlMs)}`; an in-memory implementation exists
    for tests only and the app refuses to boot on it unless
    `NODE_ENV=test` or `allowInMemoryStore:true` is set; any store error
    forces `ok:false`.
  - Signing is Ed25519 (`node:crypto`) over `sha256` of a canonical JSON
    encoding (sorted keys, no whitespace, integers/strings only, floats
    rejected — JCS-like).
  - The `zk-passport` plug verifies by shelling out to a pinned `bb verify`
    5.0.0 on `PATH`, keeping the core itself zero-dependency.
  - B3's genericity claim must be validated with two REAL plugs of different
    kinds (`signed-receipt/1`, `zk-passport/1`, the latter run over the real
    `spikes/m1-zk` artefacts) plus adversarial TEST-ONLY plugs: one that
    throws (must force `ok:false`), one with linkability `'device'` (must be
    refused in tier A), and one that cannot bind the nonce (must be refused
    at registration).
  - Spec negotiation is out of scope for M1: any `spec` value other than
    `'zkagent/1'` gets a real `'unsupported_spec'` response, nothing more.

## Findings

- Play Integrity is non-borrowable by third-party sites — confirmed via the
  cited check (per-app service account, non-transferable ToS, per-app quota).
- Apple's App Attest is borrowable by third-party sites (public root; only
  the fraud receipt needs the developer key).
- No public-and-unlinkable voucher exists on Android today: key attestation
  is public but device-identifying, and Play Integrity is private to the
  developer.
- `longfellow-zk` has no RSA support, confirming it is not a shortcut around
  Barretenberg for this project's needs.
- Per-client adoption can solve the vouching gap on a per-client basis (each
  adopter's own developer decodes and issues their own signed receipt), but
  this cannot make an unaudited proving engine sound — that remains Track
  Z's problem, not something adoption papers over.

## Open items / next steps

- B1 checkpoint: read back the coder's test output once reported.
- B2: presentation shape, tiers, threshold handling, zktag rules, chip_auth,
  FR10.
- B3: the evidence slot itself plus the two real plugs and the adversarial
  plugs described above, exercising `bb verify`.
- B4: end-to-end run against the real NL and US proofs, including bare mode.
- Commit only when the owner asks.
- Track Z's gates are unchanged: engine stable at an audited release; an
  audit of the exact four circuits zkagent would use; a real phone-proving
  number (the NDK harness against Barretenberg's Android static lib is its
  first task); a chain-free nullifier; an open-source on-device prover.
- Owner's standing concern, recorded as-is and not resolved: v1 depends on
  nobody in bare mode, but any voucher an adopter chooses to add becomes
  that adopter's own dependency, not the project's.
- Still open, carried over: Q18 (chip cloning); the MRZ-persistence defect in
  the M0 spike app; the `zkagent@0.0.0` npm MIT mislicense; the
  `AGENT_RULES.md` rewrite still unconfirmed by the owner; the push-to-main
  decision.

## Gotchas

- Owner rule #2: the orchestrator session never codes or edits docs itself.
- Agent prompts must be self-contained; continue a running agent with
  SendMessage rather than starting a new one.
- API errors can kill an agent mid-commit — check `git log` / staging state
  before retrying rather than assuming the commit didn't happen.
- Never use `git add -A`.
- Hex/PII gates apply on every commit: vendor commit SHAs and the public
  masterlist root are allowed; real-run hashes are not.
- `grep` on this host is ugrep 7.5.0.
- Use absolute paths.
- Regenerate the docs index after any doc edit.
- Work one bucket at a time (B1 → B2 → B3 → B4).

## Recovery commands

Run the chiproof package's tests: `cd packages/chiproof && node --test`.

Spec reference: `docs/product/m1-verifier-core-spec.md`.

8een reference implementation: `~/PycharmProjects/8een/src`.

Real ZK artefacts (gitignored): `spikes/m1-zk/out/{nl,us}`.

Toolchain: `bb` at `~/opt/bb`, `nargo` at `~/opt/noir`.
