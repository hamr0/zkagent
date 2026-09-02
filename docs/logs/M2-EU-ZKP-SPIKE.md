# M2 — EU issuer-free ZKP age library evaluation spike

**Status**: read-only source/metadata audit. No build, no proving, no toolchain install attempted
(explicitly out of scope for this spike). Answers the GO/NO-GO question on whether
`av-lib-android-zkp-age-icao` is worth adopting as a `chiproof` evidence type.

**Rule for this file (PRD standing rule)**: no PII values, ever. Only field names, counts,
licence text, and code/config excerpts appear below.

---

## SETUP

| Component | Value |
|---|---|
| Target repo | `eu-digital-identity-wallet/av-lib-android-zkp-age-icao` |
| Clone URL | `https://github.com/eu-digital-identity-wallet/av-lib-android-zkp-age-icao.git` |
| Commit examined | `5f1d806834b819e47913efc0aaf4cfd493c1553f` (`main`, tag-less) — re-cloned fresh this session, SHA verified to match the commit a previous agent read (`docs/logs/M2-CONFORMANCE.md` Finding 6) |
| Clone path | `/tmp/claude-1000/-home-hamr-PycharmProjects-zkagent/17b3eeda-2ed0-4f1f-8313-7bc24e3a48bb/scratchpad/av-lib-android-zkp-age-icao` (outside the repo, per instruction — never committed) |
| Repo history | 6 commits total; first commit 2026-02-02, HEAD commit 2026-05-21 — **~3 months stale as of today (2026-08-31)** |
| Declared version | `0.0.3-SNAPSHOT` (no Git tags; three `release-0.0.x-SNAPSHOT` branches on remote, no stable release ever cut) |
| Cross-references read first | `docs/logs/M2-CONFORMANCE.md` Finding 6 (prior summary); `docs/logs/M1-Q23-EVIDENCE.md` §2 (our documents' SOD crypto profiles); `docs/product/zkagent-prd.md` FR12/D24/D25/D26/Q26 (evidence-type contract) |

---

## 1. LICENCE (highest priority)

**Repo root carries a LICENSE file — Apache License, Version 2.0**, byte-identical to the
canonical text (`LICENSE`, first lines):

```
                                 Apache License
                           Version 2.0, January 2004
                        http://www.apache.org/licenses/
```

`NOTICE.txt` confirms the copyright holder and scope:

```
EU Digital Identity Wallet
Copyright (c) 2023 European Commission

Licensed under the Apache License, Version 2.0 (the "License");
...
(1) google/identity-credential (available at https://github.com/google/identity-credential)
Certain file packages were originally created under the Apache License, Version 2.0.
```

Every source file carries a matching SPDX-style header (`file_header.txt`, applied repo-wide):

```
/*
 * Copyright (c) 2023 European Commission
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 ...
 */
```

**This is Apache-2.0, unambiguously, and it is compatible with an Apache-2.0 project** (`chiproof`
itself is Apache-2.0 per the standing memory note on the npm placeholder). No conflict, no
non-commercial or evaluation-only clause anywhere in the repo's own licence text.

**Third-party components, per `licenses.md` (the repo's own dependency-license report, dated
2026-05-19):**

| Dependency | Licence |
|---|---|
| `androidx.core:core-ktx:1.17.0` | Apache-2.0 |
| `org.jetbrains.kotlin:kotlin-stdlib:2.3.0` | Apache-2.0 |
| `org.jetbrains.kotlinx:kotlinx-serialization-json:1.10.0` | Apache-2.0 |
| `org.jmrtd:jmrtd:0.8.3` | **LGPL-3.0** |
| `com.github.madztheo:noir_android:1.0.0-beta.20-2` | MIT |

**Finding — one LGPL-3.0 dependency (JMRTD).** LGPL-3 permits use as a dynamically-linked library
by an Apache-2.0-licensed consumer (it does not require the consumer's own code to relinquish its
licence), but it does carry its own compliance obligations (relink-ability, LGPL notice
propagation) that a `chiproof` adopter would inherit if this library's JMRTD dependency ships
inside the same artifact. This is a manageable compliance line-item, not a blocker — but it is
JMRTD 0.8.3, not the 0.7.18 this project's own `spikes/m0/` fork used (see `docs/logs/M0-EVIDENCE.md`
SETUP), so it is a *different* JMRTD than what this project has already vetted.

**Bundled Noir/Barretenberg-chain assets checked for their own terms:**
- `av-zkp-icao/src/main/assets/zkp-icao.bundle.js` (1.7 MB, minified JS glue code, license comment
  block at file tail) bundles `ieee754` (BSD-3-Clause), `buffer` (MIT), `pvtsutils`/`pvutils`
  (MIT, Peculiar Ventures), `asn1js` (BSD-style, GMO GlobalSign/Peculiar Ventures). All permissive,
  no non-commercial terms. A full-text search of this file and `circuit.json` for
  `non-commercial`, `evaluation only`, `CC-BY-NC`, `proprietary`, `all rights reserved` returned
  **only one hit**, and it is the BSD-3 header's own boilerplate "All rights reserved" line inside
  the `asn1js` licence block above — not a restrictive clause.
- `av-zkp-icao/src/androidTest/res/raw/srs.local` (67,109,076 bytes) is the **Structured Reference
  String (SRS)** for proof generation. README §"Structured Reference String (SRS)" states the
  library either downloads this automatically **"from Aztec's server"** or accepts a local path;
  this is Aztec's public KZG-style trusted-setup data (the same class of artifact `packages/chiproof`
  already depends on transitively via `bb` for `zk-passport/1`), not a separately licensed or
  gated artifact — no accompanying terms file was found for it in this repo, consistent with it
  being public ceremony output rather than EU-proprietary material.

**Not independently vetted in this pass (flagged, not asserted either way):** `noir_android`
(MIT, per `licenses.md`) itself wraps native Barretenberg bindings; this spike did not clone
`madztheo/noir_android` to check what it in turn bundles (out of scope — the task named one
target repo). **Could not determine** whether `noir_android`'s own native/WASM payload carries
further embedded third-party terms beyond what its own MIT `licenses.md` entry states.

**Verdict on Q1**: Apache-2.0, usable by an Apache-2.0 project, no non-commercial trap found in
the target repo or its declared dependencies. One LGPL-3.0 dependency (JMRTD) requires ordinary
LGPL compliance handling, not a licence-compatibility blocker.

---

## 2. RSA-3072-DSC / RSA-4096-CSC — fundamental circuit limit or snapshot limit?

**The README states the constraint explicitly (§"Supported Cryptographic Parameters"):**

```
- DSC certificate signature algorithm: RSA PKCS#1 v1.5 with SHA-256
- SOD signature algorithm: RSASSA-PSS with SHA-256
- CSC public key: RSA-4096
- DSC public key: RSA-3072
- TBSCertificate size: 1600 bytes
```

Confirmed independently from the compiled circuit's own ABI (`circuit.json`, `.abi.parameters`):
`csc_pubkey` is a fixed `[u8; 512]` (512 bytes = 4096 bits), `dsc_pubkey` is a fixed `[u8; 384]`
(384 bytes = 3072 bits), `tbs_certificate` is a fixed `[u8; 1600]`. These are hard array-length
constraints baked into the compiled ACIR bytecode — **not a runtime-configurable parameter**. A
document with any other DSC/CSC key size cannot be proven by this specific compiled circuit,
structurally, not as a matter of missing plumbing.

**Our two real documents (`docs/logs/M1-Q23-EVIDENCE.md` §2) are both DSC RSA-2048** — this
circuit as compiled today cannot process either the US passport or the NL ID card.

**(a) Is another key size already supported by another circuit in this repo?** No — this repo
ships exactly one compiled circuit (`av-zkp-icao/src/main/assets/circuit.json`, one `.abi`, one
bytecode blob). No `Nargo.toml`/`.nr` circuit sources are checked into the repo at all — the
circuit was compiled elsewhere and only the ACIR JSON artifact was vendored in.

**(b) Is a different key size planned/straightforward to add — i.e. is this per-parameter-set by
nature?** Yes, and there is direct evidence of that from the compiled circuit's own embedded debug
symbols. `circuit.json.debug_symbols.file_map` lists the build-time absolute source paths, e.g.:

```
/home/papathanasiou/two_circuits/crates/epassport_rsa4096_3072_pss/src/main.nr
/home/papathanasiou/two_circuits/crates/passport_common/src/lib.nr
/home/papathanasiou/two_circuits/src/rsa/src/lib.nr
/home/papathanasiou/nargo/github.com/zkpassport/noir_rsa/v0.11.0/src/rsa.nr
```

The crate is literally named `epassport_rsa4096_3072_pss` inside a workspace literally named
`two_circuits`. This is strong, if indirect, evidence that (i) circuits in this project are one
compiled artifact per fixed parameter set, exactly as expected for a Noir/ACIR circuit (arrays
must be statically sized), and (ii) the author's own workspace name implies at least one sibling
circuit for a different parameter combination exists in their build tree — **but that sibling was
not published in this repo**, and this spike cannot see its parameter set, whether it covers
RSA-2048, or whether it was ever finished. Also notable: the same JS glue code
(`getDSCCircuitInputs`) that builds circuit inputs handles **both RSA and EC (P-256/P-384/P-521,
brainpool) CSCA public keys** in its general-purpose branch — meaning the surrounding
tooling/registry format is parameter-set-agnostic even though this one compiled circuit is not.

**(c) Hard-blocked?** No evidence of a hard architectural block — the opposite: RSA `noir_rsa`
(zkpassport's own library, the same one `zk-passport/1` already depends on per
`packages/chiproof`) is parameterizable by construction, and the bundled test certificate registry
(`root_certs_default` in `zkp-icao.bundle.js`, see below) already contains real-world CSCA keys at
2048, 3072, 4096, and EC 256/384/521/6144-bit sizes — the registry format was clearly built to
carry more than this one circuit accepts, again consistent with "more circuits are intended, not
yet shipped here."

**What fraction of real-world documents does the current parameter set cover?** The repo bundles a
`environment: "test"` certificate registry snapshot (`root_certs_default`, embedded in
`zkp-icao.bundle.js`, `root: "0x2e31e60b..."`, `timestamp: 1777479919`) with **549 CSCA
certificate entries across 137 countries**. Its algorithm/key-size distribution (counted directly
from the embedded JSON):

| Key size (bits) | Count | Algorithm |
|---|---|---|
| 4096 | 339 | RSA |
| 384 | 64 | ECDSA |
| 3072 | 61 | RSA |
| 256 | 43 | ECDSA |
| 521 | 15 | ECDSA |
| 512 | 12 | ECDSA (unusual curve size, uncounted further) |
| 6144 | 8 | RSA |
| **2048** | **7** | **RSA** |

301 RSA / 134 ECDSA (some entries have ambiguous/missing `signature_algorithm`, so the two totals
don't exactly sum to 549). This is CSCA-level data, not DSC-level (DSC key sizes vary per document
and are not part of a static registry), so it only bounds one half of the constraint (CSC=RSA-4096
requirement) — but it establishes that **only ~1.3% (7/549) of the bundled real-world CSCA
population is even RSA-2048-class**, and none of the 549 are the RSA-4096 CSC **and** RSA-3072 DSC
combination that this specific circuit demands end-to-end, since DSC size is a per-document,
per-issuance fact this registry snapshot cannot speak to at all. **Flagging explicitly: this
project cannot determine, from this repo alone, what fraction of real ICAO documents in the wild
present a CSCA=RSA-4096 chained to a DSC=RSA-3072 — that would require a DSC-level survey this
repo does not contain and this spike did not attempt.** What can be said with confidence: the
circuit's fixed 4096/3072 pairing is a narrow slice of a landscape the registry format itself shows
is dominated by 4096-bit RSA CSCAs (339/549) but includes a long tail of other sizes and EC curves
this circuit cannot touch, and our own two hand-scanned documents (both DSC RSA-2048) fall outside
it entirely.

---

## 3. Nonce binding (Q26/D25)

**Direct answer: no. The public circuit ABI has no nonce, challenge, scope, or subscope input of
any kind**, and the public Kotlin API (`ZkpIcao.prove(zkpIcaoData, ageAttestations)`) accepts none
either. Full enumeration of the compiled circuit's ABI parameters
(`circuit.json.abi.parameters`, all 26 of them):

```
certificate_registry_root (public), certificate_registry_index (private),
revocation_tree_root (public), masterlist_tree_root (public),
certificate_registry_hash_path (private), certificate_tags (private), salt (private),
country (private), tbs_certificate (private), csc_pubkey (private),
csc_pubkey_redc_param (private), dsc_signature (private), exponent (private),
current_date (public), rule_ages (public), rule_ops (public), rules_len (public),
dg1 (private), dsc_pubkey (private), dsc_pubkey_redc_param (private),
sod_signature (private), signed_attributes (private), e_content (private),
pss_salt_len (private), csc_expiry (private), timestamp (public),
csc_fingerprint (private), schema_version (public)
```

Return type: a single public `field`. No parameter or the return value is named or documented as
a nullifier, nonce, or challenge anywhere in the ABI, the Kotlin public API, the README, or a
full-text search of the 1.7 MB JS bundle for `nonce`, `nullifier`, or `challenge` (the one
`challenge` string hit in the bundle is the unrelated PKCS#9 `challengePassword` OID label, not a
circuit concept — confirmed by inspecting the surrounding text).

The library's own `ZkpProver.kt` (`buildInputs`) further confirms `current_date` is **not
caller-supplied at all** — it is hardcoded inside the library to "now, rounded to the hour, UTC":

```kotlin
val currentDate: ZonedDateTime =
    ZonedDateTime.now(ZoneOffset.UTC)
        .withMinute(0).withSecond(0).withNano(0)
```

This means an adopter cannot even apply zkagent's own D28 day-granularity coarsening without
patching the library — the freshness value the circuit commits to is fixed by the library at
hour-granularity, finer (i.e. more identifying) than the day-granularity zkagent settled on for
`zk-passport/1` after M1b evidence.

**Consequence for tier ceiling**: since there is no nonce input, this circuit **cannot be bound to
a fresh per-presentation challenge at all** — it is strictly worse on this axis than
`zk-passport/1`, which at least carries the nonce through `service_subscope` (D25) even though
that forces it to tier A only. This library's proof, as published, would need a challenge bound
**outside** the circuit entirely (e.g. wrapped the way `sig-ed25519/1` wraps a challenge binding,
D30) to be usable at all against replay — it does not solve, and does not even partially address,
Q26 (a circuit exposing both a nonce and a stable nullifier). **This caps it at tier A too, by the
same logic as `zk-passport/1` (D25), but for a different and more basic reason: not "nonce OR
nullifier, never both" — simply no nonce mechanism exists in the published circuit at all.**

---

## 4. Output shape, verification key, and Node-verifiability

**Proof/claims JSON shape** (from `ZkpIcao.kt`, `ZkpProofResult`):

```json
{
  "data": { "age_over_18": true, "age_over_21": true, "age_over_65": false },
  "proof": "<base64-encoded proof bytes>"
}
```

**Verification key situation**: **no VK file, pinned VK hash, or exported verification artifact
exists anywhere in this repo.** `find . -iname "*vk*" -o -iname "*.vkey"` (repo-wide) returns
nothing. Verification is performed via `noir_android`'s `Circuit.verify(proofBase64)`, called from
`ZkpProver.kt`, and the Kotlin-level `ZkpIcao.verify()` wrapper is explicitly documented as
**internal-only**:

```kotlin
/**
 * Verifies a generated ZKP proof. This is intended for internal testing only —
 * production verification is performed server-side by the verifier.
 */
internal suspend fun verify(proofBase64: String): Result<Boolean> {
```

The doc comment asserts production verification happens "server-side" but **this repo contains no
server-side verifier, no exported VK, and no documented wire contract for what a third-party
verifier needs** — that half of the system is not published here (and was not found in the M2-
CONFORMANCE.md Finding 6 pass either, which is why that finding already flagged "not itself wired
into the OpenID4VP/DCQL wire").

**Does adopting this mean another `bb` dependency, a different prover version, or a Java/Android-
only verification path?** All three are live open questions, evidenced as follows:
- The circuit was compiled with `noir_version: "1.0.0-beta.21+89a0f0faf3a5f1273c8ac4843b7877882437e277"`
  (`circuit.json.noir_version`). `packages/chiproof`'s existing `zk-passport/1` plug pins
  `bb --version` to exactly `5.0.0` (`src/plugs/zk-passport.js`, `BB_VERSION = '5.0.0'`,
  registration throws on any mismatch). **Could not determine from this repo alone** whether ACIR
  bytecode from Noir `1.0.0-beta.21` verifies under `bb 5.0.0` — the noir/bb version compatibility
  matrix was not checked (would require the toolchain install this spike is barred from doing).
  This is exactly the kind of untested cross-version assumption `zk-passport.js`'s own strict
  version pin exists to catch.
- The only shipped verification path in this repo is **Android-only**, via `noir_android`'s Kotlin
  bindings driven from a WebView (`ZkpJsEngine.kt` loads `zkp-icao.html`/`zkp-icao.bundle.js` in a
  headless `WebView` and bridges JS↔Kotlin via `@JavascriptInterface`). There is no npm package, no
  Node binding, and no documented `bb`-CLI-shaped verification path analogous to what
  `zk-passport/1` already does. Building a Node verifier for this evidence type would mean writing
  new integration code from scratch, at minimum extracting/pinning a VK from the `circuit.json`
  bytecode via the `bb` toolchain, something this library does not do or document for its
  consumers.

**Verdict on Q4**: adopting this today means chiproof would have to build the entire off-device
verification path itself — VK derivation, `bb` invocation, version pinning — none of it exists
in a form this repo exposes; the "server-side verifier" the docstring promises is not this repo.

---

## 5. Disclosure / linkability, mapped to FR12

Public inputs (§3 list above) are: `certificate_registry_root`, `revocation_tree_root`,
`masterlist_tree_root`, `current_date`, `rule_ages`, `rule_ops`, `rules_len`, `timestamp`,
`schema_version`, plus the single `field` return value.

**Positive finding relative to `zk-passport/1`'s D26 disclosed bucket**: `country`,
`certificate_tags`, and `certificate_type` are all **private** ABI parameters (confirmed in the
parameter list above) — the proof does not expose which country's CSCA signed the document, nor
its key type/size, the way `zk-passport/1`'s `vk_sha256` does (D26 finding, `docs/logs/
M1B-EVIDENCE.md` §4–§5). Because this circuit fixes CSC=RSA-4096/DSC=RSA-3072 for every prover
using it, and proves Merkle-membership against **one shared registry root** rather than exposing
a per-DSC verification-key hash, a verifier who only sees the proof cannot bucket holders by
issuing country/circuit-class the way `zk-passport/1` currently does. This is a genuine
architectural improvement on the specific leak D26 had to accept and disclose — **if** the
Merkle-membership design holds up under the same kind of leak-closure testing `docs/logs/
M1B-EVIDENCE.md` applied to `zk-passport/1` (not attempted here; no proving was run).

**What is still disclosed, mapped to FR12's linkability classes:**
- `certificate_registry_root` / `revocation_tree_root` / `masterlist_tree_root` — fixed per
  registry snapshot (shared by every prover using that snapshot), not per-holder; this is
  registry-version linkability, not holder linkability, but a verifier watching root values change
  over time could in principle correlate "which registry snapshot a given prover build shipped
  with" the way `zk-passport/1`'s `vk_sha256` bucket does today, at one level of indirection
  (build/registry-version bucket instead of issuer/circuit-class bucket). **Not tested**; no
  leak-closure spike (the `spikes/m1b-unlink/leak-*.mjs` method) was run against this circuit —
  could not determine whether this is a real bucket or negligible.
- `current_date` — hour-granularity, hardcoded by the library (§3), not caller-adjustable. Worse
  than zkagent's own D28 (day granularity) on the freshness-fingerprint axis, and cannot be fixed
  by an adopter without a library patch, only inspection of source.
- `timestamp` — appears to be the registry snapshot's own build timestamp (`packagedCerts.timestamp`
  in the JS glue code), not a per-presentation clock reading; same registry-version-bucket caveat
  as above.
- `schema_version` — a small integer versioning the registry format; same caveat, coarser still.
- **No `nullifier` output exists (§3)** — so unlike `zk-passport/1`, there is no per-presentation
  freshness value proving the proof wasn't replayed; a captured proof, if it verified once, is not
  bound to change on a second presentation to another site, or on a second presentation to the same
  site. This is a **linkability-adjacent problem in the opposite direction from disclosure**: not
  "the proof leaks who you are" but "the proof can be replayed" — which is a Q18-shaped concern
  (chip-clone-style replay, see PRD D29/D9 v1.16 annotation) transplanted onto proof replay instead
  of chip replay.

**Mapped to FR12's `linkability class` field**: on current evidence this evidence type would need
to register as `linkability: 'none'` for the per-document/per-holder axis (an improvement over
`zk-passport/1`'s `'signer'`-adjacent disclosed bucket) but the **absence of a nonce means it is
not "none" on the replay axis** — FR12's registry doesn't currently have a column for
replay-resistance separate from linkability, and this library is the first evidence-type
candidate this project has looked at where that gap matters enough to name.

---

## 6. Maturity signals

| Signal | Value | Evidence |
|---|---|---|
| Version | `0.0.3-SNAPSHOT`, no stable release ever tagged | `git tag` → empty; 3 `release-0.0.x-SNAPSHOT` branches, all still SNAPSHOT-suffixed |
| Commit history | 6 commits total | `git log --oneline` |
| First commit | 2026-02-02 | `git log --reverse` |
| Last commit (HEAD) | 2026-05-21 | `git log -1` — **~3 months stale** as of this spike (2026-08-31) |
| Test coverage | 1 real instrumented test file (`ZkpIcaoTest.kt`, 122 lines, 2 `@Test` methods) plus Android-Studio-template boilerplate (`ExampleUnitTest.kt` in both modules, unmodified defaults) | `find . -path '*/test/*'`, `grep -n '@Test'` |
| CI | GitHub Actions `ci.yml` runs `build` and unit `test` on push/PR; the `android-tests` job (instrumented tests, which is where `ZkpIcaoTest.kt` lives) is **disabled**: `android-tests: if: false` | `.github/workflows/ci.yml` |
| EU reference-app usage | **Could not determine from this repo.** No reference to this library was found inside this repo pointing at a consuming app; `docs/logs/M2-CONFORMANCE.md` Finding 6 (the prior pass, which examined the separate `av-srv-verifier-endpoint` reference backend) already established this library is **not wired into the OpenID4VP/DCQL wire** that reference backend speaks. This spike adds: a repo-wide grep for `openid4vp`, `dcql`, `mso_mdoc`, `mdoc` in this repo turns up **zero genuine hits** — the only string matches are coincidental substrings inside base64-encoded binary blobs in `circuit.json`/`zkp-icao.bundle.js`, confirmed by inspecting the surrounding bytes (e.g. the `mdoc` hit lands mid-base64, not on a word boundary). No adapter has appeared since the prior pass. |
| Issue tracker | Not queried — no network fetch of the GitHub issues API was performed in this pass (repo was worked from a local clone only, per the read-only/local-only framing of the task). **Could not determine.** |

---

## RECOMMENDATION

**NO-GO (for adoption as-is), with a possible GO-IF for a future, better-published circuit
generation from the same EU project.**

**The single most decisive blocker: the RSA-3072-DSC/RSA-4096-CSC circuit, as compiled and
published in this repo, cannot process either of this project's own real documents** (both
DSC RSA-2048, §2) or, per the bundled test registry's own CSCA distribution, more than a small
slice of real-world documents even before accounting for the DSC-level constraint the registry
can't measure. This is not a paperwork problem — it is a hard array-length constraint in the
compiled ACIR bytecode. Nothing in this repo suggests a 2048-bit-compatible circuit has been
published; the `two_circuits`/`epassport_rsa4096_3072_pss` naming in the debug symbols is evidence
that more parameter sets exist somewhere in the author's own build tree, but not evidence that
they've been released, or when, or whether 2048 is even among them.

**Two further blockers, either of which is independently sufficient to defer adoption even if the
key-size problem were solved:**

1. **No nonce, at all (§3).** Not "nonce or nullifier, pick one" like `zk-passport/1` (D25) — this
   circuit has neither. A captured proof is not bound to a fresh challenge in any way the circuit
   enforces; any challenge-binding would have to be bolted on outside the circuit, which is exactly
   what `sig-ed25519/1` (D30) already does more simply, without a 67 MB SRS or an Android WebView.
2. **No verification path outside Android exists in this repo (§4).** The library's own docstring
   promises "server-side" verification that this repo does not implement, publish a VK for, or
   document a wire contract for. Adopting this evidence type today means building chiproof's
   *entire* Node-side verifier from scratch against an unpublished contract, on an unverified
   Noir/`bb` version pairing (`1.0.0-beta.21` vs. the pinned `bb 5.0.0` `zk-passport/1` already
   uses), for a circuit that presently rejects our own two documents.

**What would change the recommendation to GO-IF:** if the EU project publishes (a) a circuit
variant matching real-world DSC key-size distribution (2048/3072/4096, RSA and ECDSA) rather than
one fixed RSA-4096/3072 pairing, (b) an exported/pinned VK and a documented off-device verification
contract, and (c) some nonce-carrying mechanism (even a `service_subscope`-style channel as crude
as `zk-passport/1`'s) — at that point the private `country`/`certificate_tags` design (§5) is a
genuine linkability improvement over `zk-passport/1`'s disclosed bucket and worth a real Track-Z
spike. None of those three conditions hold in the commit examined here.

**This finding does not reopen or contradict `docs/logs/M2-CONFORMANCE.md`'s Finding 6** — it
sharpens it with source-level evidence Finding 6 didn't have (the RSA key-size ABI constraint, the
missing nonce, the missing verifier). The owner decides; this file only narrows the options,
consistent with the pattern already established at D23/D24 (two independently-viable-looking paths
don't resolve a scope decision by themselves — evidence only narrows).

---

## What this establishes and does NOT establish

**Established (cited, source-quoted above):**
1. Apache-2.0 licence, clean of non-commercial traps, one LGPL-3.0 transitive dependency (JMRTD)
   requiring ordinary compliance handling (Finding 1/§1).
2. The RSA-3072/4096 constraint is a hard, compiled-in ABI limit of this one circuit, not a
   configuration flag; evidence of sibling circuits in the author's own build tree exists but none
   are published here (§2).
3. No nonce/challenge/scope input exists anywhere in the public ABI or API; `current_date` is
   hardcoded to hour granularity inside the library, not caller-adjustable (§3).
4. No VK, no Node/off-device verification path, no documented wire contract for the
   "server-side verifier" the library's own docstring promises (§4).
5. `country`/`certificate_tags`/`certificate_type` are private ABI parameters — a real
   architectural improvement over `zk-passport/1`'s disclosed circuit-class bucket, unverified by
   any leak-closure test (§5).
6. Pre-release (`0.0.3-SNAPSHOT`, no tags), 6 commits, ~3 months since last activity, thin/disabled
   instrumented test coverage, and no OpenID4VP/DCQL adapter has appeared since the prior
   `M2-CONFORMANCE.md` pass (§6).

**NOT established — do not state these anywhere:**
- That the "two circuits" implied by the debug-symbol workspace name include an RSA-2048 variant,
  or that one is coming — this is inference from a build-machine path string, not a roadmap.
- Anything about `noir_android`'s own bundled native/WASM licensing beyond its MIT `licenses.md`
  entry — that repo was not cloned or read (out of scope per the task brief).
- Whether Noir `1.0.0-beta.21` ACIR output actually verifies under the `bb 5.0.0` this project has
  already pinned for `zk-passport/1` — no proving or verification toolchain was installed or run.
- Whether the private `country`/`certificate_tags` design actually closes linkability the way it
  appears to on paper — no leak-closure spike (the `M1B-EVIDENCE.md` method) was run against this
  circuit; this is a structural read of the ABI, not a measurement.
- Any claim that adopting this library would make zkagent "zero-knowledge" as a product — unchanged
  standing constraint (NO-GO #7); this file evaluates one candidate evidence-type plug only.
- Whether an EU reference app or production deployment consumes this library — not found in this
  repo, not independently checked against a live GitHub issues/PR feed.

---

## PENDING

- [ ] Whether a wider-parameter-set circuit (2048-bit DSC coverage in particular) exists in the EU
      org's other repos or unpublished branches — worth a periodic recheck, same posture as the
      existing watch item in `docs/logs/M2-CONFORMANCE.md` PENDING.
- [ ] GitHub issue tracker was not queried over the network in this pass — open-issue count and
      recent-activity signal beyond raw commit dates is unmeasured.
- [ ] `madztheo/noir_android`'s own licensing/native-payload contents — not cloned, not vetted.
- [ ] Noir `1.0.0-beta.21` vs. `bb 5.0.0` ACIR compatibility — would require an actual toolchain
      install and proving attempt, explicitly out of scope for this spike.
- [ ] Whether the Merkle-registry design (`certificate_registry_root`) actually resists a
      leak-closure probe the way the ABI's private fields suggest — no spike run.
- [ ] `docs/index.md` needs a row for this file — not edited here by rule (orchestrator regenerates
      it).
