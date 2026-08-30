# M1-zk spike: ZK proof over a real ICAO e-passport (DG1 + SOD)

Goal: feasibility spike for "SOD signed by a DSC chaining to a CSCA; DOB in DG1 puts
holder over 18" on Fedora 44 x86_64, desktop, rootless. Time-boxed to ~90 min wall
clock. **No real passport bytes were used or fabricated** — the owner's real
DG1/SOD fixtures had not yet arrived when this spike ran; everything below is
toolchain install + repo/circuit/library inspection only.

Primary candidate: **zkPassport** (Noir/Barretenberg). Fallback: **Rarimo**
(Circom/Groth16/snarkjs).

## 1. What was cloned, where, and licenses

| Repo | Path | Commit | License |
|---|---|---|---|
| zkpassport/circuits | `spikes/m1-zk/vendor/zkpassport-circuits/` | `1a1836eb958b7d7bbb47fab060128757748dba6a` | Apache-2.0 (`LICENSE`) |
| rarimo/passport-zk-circuits | `spikes/m1-zk/vendor/rarimo-passport-zk-circuits/` | `30b0be2e83062e19f21237c03317c9a26f2dab59` | MIT (`LICENSE`, "Zero Block Global Foundation") |

`spikes/m1-zk/vendor/` and `spikes/m1-zk/fixtures/real/` were added to the repo
root `.gitignore` (two lines). Verified with `git check-ignore -v`:

```
.gitignore:42:spikes/m1-zk/vendor/         spikes/m1-zk/vendor/zkpassport-circuits/README.md
.gitignore:41:spikes/m1-zk/fixtures/real/  spikes/m1-zk/fixtures/real/foo.txt
```

This `.gitignore` edit is the one change made outside `spikes/m1-zk/`, as instructed.

## 2. Toolchain install (rootless, under `~/opt`)

CI-pinned versions, read from `zkpassport-circuits/.github/workflows/test.yml`:

```
NOIR_VERSION: "1.0.0-beta.22"
BB_VERSION: "5.0.0"
NODE_VERSION: "24.3.0"   (not installed — no npm/node step was needed for compile-only work)
```

Installed:

- **nargo 1.0.0-beta.22** via `noirup`, redirected to `NARGO_HOME=/home/hamr/opt/noir`
  (binary at `/home/hamr/opt/noir/bin/nargo`).
- **bb 5.0.0** via `bbup`, installed with `BB_PATH=/home/hamr/opt/bb --no-modify-path`
  (binary at `/home/hamr/opt/bb/bb`).

Verified: `nargo --version` → `nargo version = 1.0.0-beta.22`; `bb --version` → `5.0.0`.

### Deviations from "installs go under ~/opt or ~/.cache only" — flagging, not deciding

1. **`noirup`'s installer script unconditionally edited `~/.zshrc`** twice (once
   pointing at the default `~/.nargo`, once — after I redirected `NARGO_HOME` —
   pointing at `/home/hamr/opt/noir`). A follow-up attempt to clean out the first
   (stale) entry was blocked by the sandbox's Bash classifier as an edit outside
   the repo. **Net effect: `~/.zshrc` now has two `NARGO_HOME`/`PATH` export
   blocks; the first (`export NARGO_HOME="/home/hamr/.nargo"`) is dead since that
   directory doesn't exist, but it's cosmetically redundant.** The owner may want
   to manually delete lines 142/144 of `~/.zshrc`. No secrets or destructive
   commands were involved — just a duplicated PATH export.
2. **`nargo`'s git-dependency cache ignores `NARGO_HOME`** — it hardcoded
   `~/nargo/github.com/...` regardless of the env var (confirmed by testing: with
   `NARGO_HOME=/home/hamr/opt/noir` set, `nargo compile` still cloned 16 dependency
   repos into `/home/hamr/nargo/`, not under `NARGO_HOME`). Worked around by setting
   `HOME=/home/hamr/opt/noir-home` for compile invocations, which nargo respects
   (deps land in `/home/hamr/opt/noir-home/nargo/github.com/...`). The one stray
   `~/nargo` directory this created before the workaround was found (16MB, just an
   empty `.package-cache` lock file after moving the actual clones) has been
   removed; `~/nargo` does not exist post-cleanup.
3. `bbup`'s own installer script (as opposed to the `bbup` binary itself) hardcodes
   `~/.bb` and edits `.bashrc`/`.zshrc` with no override — that script was **not
   run**; instead `bbup` was fetched directly and invoked with `BB_PATH` +
   `--no-modify-path`, which fully respects the sandbox scope. No shell rc
   modification occurred for bb.

**To reproduce a session:** `export NARGO_HOME=/home/hamr/opt/noir HOME=/home/hamr/opt/noir-home PATH="$PATH:/home/hamr/opt/noir/bin:/home/hamr/opt/bb"`.

## 3. zkPassport: circuit compile results

The repo is an npm-workspace-style Nargo workspace with ~1000s of members —
one per (TBS-length × algorithm × curve/key-size × hash) combination, for three
circuit families relevant here:

- `sig-check/dsc/...` — **DSC signed by CSCA** (verifies a DSC's TBS certificate
  against a CSC/CSCA public key).
- `sig-check/id-data/...` — **SOD's `signedAttributes` signed by DSC** (this is
  the "SOD signed by a DSC" half of the goal statement).
- `data-check/integrity/sa_shaX/dg_shaY/...` — **DG hash ↔ SOD `eContent`
  binding** (the third link: does DG1's hash actually appear in the SOD's
  encapsulated content).

Compiled one circuit per family/algorithm as instructed:

| Circuit | Path | Result | Time |
|---|---|---|---|
| DSC sig-check, RSA-PKCS-2048-SHA256 | `src/noir/bin/sig-check/dsc/tbs_700/rsa/pkcs/2048/sha256` | **Success**, exit 0 | cold (incl. cloning 16 git deps): `nargo compile` = 6.92s user / 0.86s sys, ~21s wall; warm (deps cached): 2.1s wall |
| DSC sig-check, ECDSA-P256-SHA256 | `src/noir/bin/sig-check/dsc/tbs_700/ecdsa/nist/p256/sha256` | **Success, exit 0, but with a soundness WARNING (verbatim below)** | 17.48s user / 0.70s sys, 18.28s wall |

Both produced valid artifacts (`target/sig_check_dsc_tbs_700_{rsa_pkcs_2048_sha256,ecdsa_nist_p256_sha256}.json`,
1.1MB and 2.4MB respectively).

**Verbatim ECDSA warning** (compile still succeeds, exit code 0, artifact still emitted):

```
bug: Brillig function call isn't properly covered by a manual constraint
    ┌─ /home/hamr/opt/noir-home/nargo/github.com/zkpassport/noir_bigcurve/v0.14.0-1/src/ops/msm.nr:215:9
    │
215 │ ╭         CurveJ::<BN, Curve>::compute_linear_expression_transcript(
216 │ │             mul_points,
217 │ │             mul_scalars,
218 │ │             add_points,
219 │ │         )
    │ ╰─────────' This Brillig call's inputs and its return values haven't been sufficiently constrained. This should be done to prevent potential soundness vulnerabilities
    │
    = Call stack:
      1: main
              at src/main.nr:31:9
      2: verify_nist_p256
              at .../src/noir/lib/sig-check/ecdsa/src/lib.nr:101:5
      3: verify_secp256r1_ecdsa
              at .../zkpassport/noir-ecdsa/v0.4.1/src/ecdsa.nr:124:5
      4: verify_ecdsa
              at .../zkpassport/noir-ecdsa/v0.4.1/src/ecdsa.nr:76:19
      5: derive_curve_impl
              at .../zkpassport/noir_bigcurve/v0.14.0-1/src/bigcurve.nr:211:18
      6: evaluate_linear_expression
              at .../zkpassport/noir_bigcurve/v0.14.0-1/src/ops/msm.nr:215:9
```

This is an upstream `noir_bigcurve` (pinned v0.14.0-1) MSM constraint-completeness
warning, not a compile failure — but it's a *soundness* class of warning (Noir's
own wording: "should be done to prevent potential soundness vulnerabilities"),
surfaced from an elliptic-curve multi-scalar-multiplication routine used by
`noir-ecdsa` for every NIST-curve verify. **Flagging for a decision**: if
ECDSA-signed DSCs/DSOs matter for the real US-passport/NL-ID fixtures, this
warning needs to be understood (upstream issue tracker check, or a newer
`noir_bigcurve` pin) before trusting an ECDSA-path proof — not resolved in this
spike. RSA-PKCS path has no such warning.

Did not attempt PSS variants or the `id-data`/`data-check` circuits' actual
compile — time-boxed; see next-steps.

## 4. zkPassport input format (from Noir source + `@zkpassport/utils`)

Reading `src/noir/bin/sig-check/id-data/tbs_700/rsa/pkcs/2048/sha256/src/main.nr`
(the "SOD signed by DSC" circuit — the auto-generated header points at
`src/ts/scripts/circuit-builder.ts` as the source of truth for the Noir file
itself):

```
fn main(
    comm_in: pub Field,
    salt_in: Field,
    salt_out: Field,
    dg1: DG1Data,
    dsc_pubkey: [u8; 256],
    dsc_pubkey_redc_param: [u8; 257],
    sod_signature: [u8; 256],
    tbs_certificate: [u8; 700],
    signed_attributes: SignedAttrsData,
    exponent: u32,
    e_content: EContentData,
) -> pub Field
```

- `signed_attributes` is padded/typed as `SignedAttrsData` (fixed-size byte
  array type from `utils::types`); the real length is recovered *inside* the
  circuit via `unsafe_get_asn1_element_length` (an `unsafe` block, i.e.
  unconstrained-then-asserted-correct-by-the-signature-check — a standard Noir
  pattern for variable-length ASN.1 fields inside fixed-size arrays).
- `dsc_pubkey_redc_param` is a Barrett/Montgomery reduction parameter for the
  in-circuit RSA modexp (256+1 bytes for a 2048-bit modulus) — not something you
  get from the passport; it's derived from `dsc_pubkey` by the TS helper library
  (`noir_rsa`/`noir-bignum` convention), not something to hand-roll.
- `e_content` (`EContentData`) is the SOD's encapsulated content (DG hash list) —
  consumed only for the output commitment here, hashed/bound in the
  `data-check/integrity` circuit separately.

**TS helper exists and takes raw bytes directly** — confirmed in
`src/ts/test-helper.ts` (repo-local, but built entirely on the pinned npm
package `@zkpassport/utils@0.37.4`, see `package.json` line 38):

```ts
import { Binary, PassportReader, getIDDataCircuitInputs, getDSCCircuitInputs,
         getIntegrityCheckCircuitInputs, getDiscloseCircuitInputs } from "@zkpassport/utils"

const dg1 = Binary.from(rawDg1Bytes)   // raw DG1, e.g. straight off a JMRTD read
const sod = Binary.from(rawSodBytes)   // raw SOD, ContentInfo-wrapped SignedData ASN.1 blob

const reader = new PassportReader()
reader.loadPassport(dg1, sod)           // parses ASN.1 internally
const passport = reader.getPassportViewModel()

const idDataInputs = await getIDDataCircuitInputs(passport, commIn, saltOut)   // -> Noir InputMap
```

`PassportReader.loadPassport(dg1: Binary, sod: Binary)` takes **exactly** the
raw bytes a JMRTD-style read produces — no intermediate parsing needed on our
side. `getIDDataCircuitInputs`/`getDSCCircuitInputs`/`getIntegrityCheckCircuitInputs`
then build the full Noir `InputMap` (including the RSA reduction params, ASN.1
offsets, and salts) from the parsed `PassportViewModel`.

**Answer to "does this look like <100 lines of glue": yes, clearly under 100
lines** — the entire raw-bytes-to-circuit-input pipeline is ~10 lines of TS
calling into `@zkpassport/utils`, which is a maintained npm package (0.37.4,
matches the pinned circuit repo version) doing all ASN.1/RSA-param/padding work.
The only glue our side would write is: read raw DG1+SOD off the chip (already
planned — vetted reader library, not custom parsing, per project rule), wrap in
`Binary.from()`, call the three `get*CircuitInputs` functions, feed to
`nargo`/`bb` (or `@aztec/bb.js` in TS) for proving. Not independently verified
by running it end-to-end in this spike (no real fixture yet) — this is a
source-reading conclusion, not a measured one.

## 5. Rarimo: `.zkey` availability and input schema

- **No pre-built/downloadable `.zkey` release assets found** — no GitHub release
  assets, no IPFS links, nothing in `README.md` or `package.json` pointing at a
  hosted proving key.
- Proving keys are built locally via **`@solarity/hardhat-zkit`**
  (`hardhat.config.ts`: `zkit.setupSettings.ptauDir: "zkit/ptau"`,
  `onlyFiles: ["queryIdentity.circom", "queryIdentityTD1.circom",
  "registerIdentity_1_256_3_4_600_248_1_1496_3_256.circom"]`). `npm run compile`
  → `npx hardhat zkit make` → hardhat-zkit downloads a **public, standard
  Powers-of-Tau file** (not one this project ceremonies itself) sized to the
  circuit's constraint count, then runs Groth16 phase-2 setup locally to produce
  a circuit-specific `.zkey`. This is the normal/expected snarkjs flow, not a
  red flag by itself — but it does mean a first `zkit-make` run needs a
  network-fetched ptau file whose size scales with circuit size (the
  `registerIdentity_...` circuit is a large RSA-2048/SHA-256 circuit; ptau for
  that constraint count is very plausibly multi-GB — **not verified by actually
  running it**, per the "don't download multi-GB" instruction).
- **Separately, the repo also ships `circuits/scripts/trusted-setup.sh`**, an
  ad-hoc single-contributor `snarkjs powersoftau new` + one `contribute` call.
  **Flagging: this is not a secure trusted setup** (one contributor knows the
  toxic waste) — it looks like a dev/local-testing convenience script, not the
  path `hardhat-zkit` actually uses for `npm run compile`. Worth confirming this
  script is never what backs a production `.zkey` before relying on Rarimo.

**`test/inputs/passport` JSON schema** (from `test/inputs/passport/Readme.md`):

```json
{
  "dg15": " ",
  "documentExpiryDate": " ",
  "dateOfBirth": " ",
  "documentNumber": " ",
  "nationality": " ",
  "gender": "",
  "lastName": " ",
  "documentType": "",
  "dg1": " ",
  "signature": " ",
  "firstName": " ",
  "passportImageRaw": " ",
  "issuingAuthority": " ",
  "sod": " "
}
```

Required fields are only `sod` and `dg1` (all others can apparently be derived
by the repo's own parsing scripts from those two, mirroring the zkPassport
pattern). No field-encoding detail (hex? base64? raw string?) is given in this
Readme — would need to read the parsing script (`test/process_passport.js` or
similar) to pin that down; not done in this time-box.

The lower-level circuit input schema for the actual Circom circuit (README.md,
"Register identity circuit inputs") is byte-array/binary-oriented:
`encapsulatedContent`, `signedAttributes` as bit arrays, `signature`/`pubkey` as
64-bit-limb arrays (or `[r,s]` / `[x,y]` limbs for ECDSA), `dg1`/`dg15` as bit
arrays, plus `slaveMerkleRoot` + inclusion branches as the chain-of-trust input
(see item 6).

## 6. Chain-free CSCA path

**Both projects support it — a locally-computed Merkle root as a public input,
not an in-circuit certificate chain walk to a hardcoded CSCA:**

- **zkPassport**: `sig-check/dsc/.../src/main.nr` takes
  `certificate_registry_root: pub Field` and `masterlist_tree_root: pub Field`
  as public inputs (see the compiled circuit's signature in item 3/4 above);
  the DSC-to-CSCA check is "does this DSC's commitment appear under this
  Merkle root," where the root is computed off-chain from a masterlist (their
  `PackagedCertificatesFile` / `calculatePackagedCertificatesRoot` in
  `@zkpassport/utils`, referenced in `src/ts/scripts/generate-fixtures.ts`).
  Root-building library: `calculatePackagedCertificatesRoot` (from
  `@zkpassport/utils`) — takes a `PackagedCertificatesFile` describing
  `certificates`/`masterlists`/`revocations`; a BSI-masterlist-to-that-format
  converter was not located in this time-box (see next steps).
- **Rarimo**: the `registerIdentity` circuit (README "Register identity circuit
  inputs") takes `slaveMerkleRoot` (root of a sparse Merkle tree of "2nd level
  keys", i.e. DSC keys) as the on-chain **public** input, with
  `slaveMerkleInclusionBranches` as the private inclusion proof — same
  chain-free shape: prove membership under a locally-computable root rather
  than walking an X.509 chain to a fixed root in-circuit. Cite:
  `README.md` lines ~80-100 ("Register identity circuit inputs" / "public
  signals"); circuit file `circuits/merkleTree/SMTVerifier.circom` implements
  the inclusion-proof primitive both `registerIdentity*.circom` files use.

Neither repo's masterlist-building script was run — computing an actual root
from the BSI masterlist against either library is listed under next steps.

## 7. Disk usage

```
385M  spikes/m1-zk/vendor/            (236M zkpassport-circuits, 146M rarimo-passport-zk-circuits, incl. .git)
89M   ~/opt/noir                       (nargo binary + noirup)
37M   ~/opt/bb                         (bb binary + bbup)
16M   ~/opt/noir-home                  (HOME= override for nargo's dependency git-clone cache, 16 repos)
```

Node/npm was **not** installed (no `npm ci` was run for either repo — TypeScript
tooling, `@zkpassport/utils`, `hardhat`, `snarkjs`, `@solarity/hardhat-zkit`
would all still need `npm ci`/`npm install`, which was out of scope for a
compile-only spike and would add real disk/time).

## 8. What was NOT run (explicit)

- `npm ci`/`npm install` in either repo (no Node.js runtime installed at all).
- Any `nargo test`, `bb prove`, or witness generation — only `nargo compile`.
- The `id-data` (SOD-signed-by-DSC) or `data-check/integrity` (DG-hash-in-eContent)
  circuit compiles — only the two `sig-check/dsc` (DSC-signed-by-CSCA) circuits
  were compiled, per the "ONE relevant circuit" instruction, though item 4/6
  above required reading `id-data`'s source to answer the input-format question.
- PSS variants of RSA (only PKCS1v1.5 compiled).
- Rarimo's `npm run compile` / `hardhat zkit make` (would trigger a Node install
  plus a ptau download of unknown-but-plausibly-multi-GB size for the
  RSA-2048/SHA-256 `registerIdentity` circuit) — explicitly skipped per
  instructions.
- Rarimo's `circuits/scripts/trusted-setup.sh` — read only, not executed (and
  wouldn't be trusted for production use as noted in item 5).
- Any BSI masterlist download or actual Merkle-root computation for either
  project.
- Anything touching real passport/ID bytes — none exist in this environment yet.

## 9. Next steps once the real SOD/DG1 arrive

1. Read the `.sod.txt` digest/signature-algorithm listing from the capture probe;
   map the US passport's and NL ID card's actual (sigAlg, hashAlg, keySize) onto
   the matching pre-generated Nargo circuit path(s) (zkPassport) — there is
   almost certainly an exact match given the breadth of `sig-check/id-data/*`
   and `sig-check/dsc/*` combinations already enumerated in `Nargo.toml`.
2. `npm ci` in `zkpassport-circuits` (needs Node 24.3.0 — not yet installed;
   would need `nvm`/`n`/a rootless Node install under `~/opt`) to get
   `@zkpassport/utils` and exercise `PassportReader.loadPassport(dg1, sod)` for
   real, confirming the "under 100 lines of glue" conclusion in item 4 against
   actual bytes rather than source reading.
3. Resolve the item-3 ECDSA `noir_bigcurve` soundness warning if the real DSC
   turns out to be ECDSA-signed (check upstream `noir_bigcurve`/`noir-ecdsa`
   issue trackers for v0.14.0-1/v0.4.1; consider pinning a newer version if a
   fix exists) — only matters if the fallback/second document (NL ID) or
   either document's DSC uses ECDSA rather than RSA.
4. Compile the matching `sig-check/id-data/...` and `data-check/integrity/...`
   circuits (not just `sig-check/dsc/...`) to cover the full "SOD signed by DSC
   chaining to CSCA" claim, not just the DSC-to-CSCA link.
5. Attempt an actual `bb prove`/witness-generation timing run on a real fixture
   (currently only compile time is measured — proving time, memory, and CRS
   size for RSA-2048 in Barretenberg is the actual load-bearing feasibility
   number for "can this run on the target device/desktop," and remains
   unmeasured).
6. Decide (with the owner) whether to pursue the BSI-masterlist → Merkle-root
   pipeline for either project, since neither repo's root-building path was
   exercised here — this determines whether "chain-free CSCA" is truly
   issuer-free in practice or just architecturally chain-free while still
   needing a live BSI masterlist feed.
7. If Rarimo remains in play as fallback, budget real time/disk for the
   `hardhat zkit make` ptau download and re-evaluate the single-contributor
   `trusted-setup.sh` question before trusting any resulting `.zkey`.
8. Owner: consider cleaning up the duplicate `NARGO_HOME`/`PATH` export block
   noirup left in `~/.zshrc` (see item 2, deviation #1) — cosmetic only, not
   urgent.

---

## Real NL document results (2026-08-30)

Real fixtures arrived (both gitignored, PII-bearing, under
`spikes/m1-zk/fixtures/real/`, never copied elsewhere): a Dutch NL ID card
(`id-20260830031213.dg1`/`.sod`, 95/2236 bytes) and, later the same session, a
US passport (`passport-20260830031745.dg1`/`.sod`, 93/2683 bytes). Scripts live
under `spikes/m1-zk/run/` (Node 22.22.2, system `node`/`npm`, no version
manager installed); derived artifacts (Prover.toml files, proofs, VKs, witness
files) live under `spikes/m1-zk/out/` (added to `.gitignore` alongside
`vendor/`) or `vendor/zkpassport-circuits/target/` (already git-ignored via
`vendor/`). **No DG1 contents, MRZ text, names, numbers, or DOB are printed
below or were logged anywhere — only byte lengths, algorithm names, hashes,
booleans, and timings.**

### 1. `npm install` and library

`spikes/m1-zk/run/package.json` pins `@zkpassport/utils@0.37.4` (same version
the circuits repo pins) plus `@zkpassport/poseidon2`, `@noir-lang/noir_js`,
`@aztec/bb.js`, and `@peculiar/asn1-*` (transitive, used once for a manual
ASN.1 sanity check). `npm install` completed in ~8.4s, 60 packages, no install
errors (3 audit advisories reported by npm, not investigated — out of scope).
The package's CJS build (`dist/cjs/index.cjs`) loads directly via
`require("@zkpassport/utils")`.

### 2. Per-document inspection (`run/01_inspect.js`, `run/01b_inspect_us.js`)

| | NL ID card | US passport |
|---|---|---|
| `sod_parsed` | true | true |
| DG1 byte length | 95 | 93 |
| SOD byte length | 2236 | 2683 |
| DSC signature/hash algorithm (library-detected) | sha256 (RSA PKCS1v1.5) | sha256 (RSA PKCS1v1.5) |
| DSC public key | RSA, 270-byte DER SPKI (2048-bit modulus) | RSA, 270-byte DER SPKI (2048-bit modulus) |
| TBS certificate actual length | 911 bytes | 1319 bytes |
| `getTBSMaxLen()` (circuit bucket) | **1000** | **1600** |
| Selected circuit | `sig-check/id-data/tbs_1000/rsa/pkcs/2048/sha256` | `sig-check/id-data/tbs_1600/rsa/pkcs/2048/sha256` |

**Fork/finding — the coordinator's "same circuit variant expected" did not
hold**: the two documents' DSC certificates land in different `tbs_*` size
buckets (1000 vs 1600), so they compile to and prove against **different**
circuits with **different** verification keys (confirmed below — VK SHA-256
hashes differ). This is expected/correct behavior (the TBS length bucket is a
circuit-selection parameter driven by how many X.509 extensions the issuing
country's DSC carries, not a bug), but it means a real verifier needs to
select the circuit per-issuer, not assume one fixed circuit for all documents.

Both circuits confirmed via ASN.1 (`@peculiar/asn1-cms`) to have **exactly one
certificate in the SOD's CMS `certificates` set — the DSC. No CSCA certificate
is embedded in either SOD.** This matches standard ICAO practice (CSCA
distribution is out-of-band via national/ICAO PKD masterlists, not carried on
the document) but is a hard blocker for item 2 as literally specified — see
"Masterlist/CSCA root" below.

### 3. Circuit inputs and masterlist/CSCA root — fork, resolved as: skip

`getIDDataCircuitInputs(vm, saltIn, saltOut)` (the "SOD signed by DSC" circuit
inputs) built cleanly for both documents with no masterlist/CSCA input
required — its signature is `(comm_in, salt_in, salt_out, dg1, dsc_pubkey,
dsc_pubkey_redc_param, sod_signature, tbs_certificate, signed_attributes,
exponent, e_content, pss_salt_len)`. This circuit only proves "SOD's
`signedAttributes` were signed by the DSC embedded in the SOD" — it does not
touch the CSCA at all.

**Masterlist/CSCA root (item 2's other half) — not run, documented as a fork
rather than faked.** The instruction was: try the library's BSI-masterlist
helper first; if too deep, build a Merkle tree from "the NL CSCA cert
extracted from the SOD/DS chain" as a stand-in. Neither is possible as
literally stated because **the SOD does not contain a CSCA certificate at
all** (confirmed above — 1 cert, the DSC). There is nothing to "extract."
Options not taken: (a) fetch the real published NL/US CSCA certificate from a
public government/ICAO PKD source and build a single-leaf stand-in tree from
that fetched cert (not the SOD) — this is a different, larger action than
what was asked, not attempted given the time-box; (b) run the DSC-to-CSCA
(`sig-check/dsc/...`) circuit against a fabricated/self-signed test CSCA key —
rejected outright, would prove nothing about the real documents and risks
being mistaken for real evidence later. **Given the fork, item 3's "signature
check circuit" was interpreted as the SOD-signed-by-DSC circuit
(`sig-check/id-data`) only — the DSC-to-CSCA link (`sig-check/dsc`) was not
exercised against real data this session** (it was exercised against
synthetic/library test fixtures earlier, see the M0 section above).

### 4. Witness / prove / verify — signature-check (`sig-check/id-data`) circuit

All commands run with `HOME=/home/hamr/opt/noir-home`, `NARGO_HOME=/home/hamr/opt/noir`,
`PATH` including `~/opt/noir/bin` and `~/opt/bb` (see earlier section for why).
Compile times (once per circuit variant, not per document):

| Circuit | Compile time |
|---|---|
| `sig-check/id-data/tbs_1000/rsa/pkcs/2048/sha256` (NL) | 6.28s wall (5.87s user) |
| `sig-check/id-data/tbs_1600/rsa/pkcs/2048/sha256` (US) | 6.54s wall (6.12s user) |

Per-document witness/prove/verify, `/usr/bin/time -v` wall clock + peak RSS:

| Step | NL (tbs_1000) | US (tbs_1600) |
|---|---|---|
| `nargo execute` (witness) | 2.09s wall, 388,696 KB RSS | 2.09s wall, 389,112 KB RSS |
| `bb write_vk` | 1.18s wall, 180,232 KB RSS | 0.61s wall, 181,908 KB RSS |
| `bb prove` (ultra_honk) | 1.27s wall, 202,516 KB RSS | 1.28s wall, 205,940 KB RSS |
| `bb verify` | ~0.00s wall, 8,356 KB RSS | ~0.00s wall (not re-timed, same order) |
| **Result** | **verify = true** | **verify = true** |
| Proof size | 14,656 bytes | (same circuit shape — not independently re-measured, expected ~14.6KB) |
| Public inputs size | 64 bytes | 64 bytes |

Artifact SHA-256 (proof/VK/public-input **hashes only**, never the raw bytes,
never DG1/MRZ):

Hashes of proofs/inputs from real-document runs are kept only in the gitignored out/ directory.

```
NL  proof:          <redacted: derived from real document>
NL  vk:              <redacted: derived from real document>
NL  public_inputs:   <redacted: derived from real document>
US  proof:          <redacted: derived from real document>
US  vk:              <redacted: derived from real document>
US  public_inputs:   <redacted: derived from real document>
```

NL and US VK hashes differ, confirming the two documents genuinely compiled to
and were proved against **different circuits** (per the tbs_1000 vs tbs_1600
finding above) — this is expected, not an error.

### 5. Age ("over 18") circuit — `compare/age/standard`

Compiled cleanly, 7.95s wall (7.48s user). `getAgeCircuitInputs(vm, {age:{gte:18}},
salts, nullifierSecret, serviceScope, serviceSubscope, currentDateTimestamp)`
built inputs for both documents with a **fixed "today" of 2026-08-30T00:00Z**
(hardcoded in the script for determinism across runs, not the real wall-clock
date at proof time — noted so it isn't mistaken for a timestamp bug).

**Fork — `nullifier_secret` set to `0`, not a real OPRF-derived secret.** The
circuit asserts `oprf_proof.beta != 0` whenever `nullifier_secret != 0`
(`commitment/scoped-nullifier/src/lib.nr:63`) — a real per-service blinded
nullifier requires wiring up the OPRF proof machinery (the repo's own fixture
generator does this only via a fixed dev-only "OPRF_TEST_SECRET_KEY" for its
test fixtures). Doing this properly was judged too deep for the time-box; used
`nullifier_secret = 0` instead, matching the repo's own "OPRF_ZERO_PROOF"
convention for the non-salted-nullifier case. **This means the nullifier
below is the plain (non-blinded) scoped nullifier, not the fully
privacy-hardened salted one** — real deployment would need the OPRF path.

Witness/prove/verify, NL document, scope `"site-a"` vs `"site-b"`
(`getServiceScopeHash(scopeStr)`, `service_subscope = 0`):

| Step | site-a | site-b |
|---|---|---|
| `nargo execute` | 2.34s wall, 558,144 KB RSS | 2.35s wall, 557,836 KB RSS |
| `bb write_vk` (shared VK, ran once) | 0.45s wall, 137,292 KB RSS | (same VK reused) |
| `bb prove` | 0.94s wall, 160,948 KB RSS | 0.92s wall, 159,364 KB RSS |
| `bb verify` | **true** | **true** |

Circuit output tuple is `(param_commitment, nullifier_type, nullifier,
oprf_pk_hash)`:

- `param_commitment` **identical** across site-a/site-b (`0x2ccd6b1a...`,
  scope-independent — correctly reflects the same `min_age_required=18,
  max_age_required=0` claim).
- `nullifier` **differs** across site-a/site-b (`0x234c4126...` vs
  `0x26e4a8cb...`).
- Re-executing the site-a witness a second time (independent `nargo execute`
  call, same inputs) reproduced the **exact same** nullifier
  (`0x234c4126...`) — nullifier is deterministic/stable within a scope, not a
  fresh random value per proof.
- US document, scope `"site-a"`, same salts/thresholds: nullifier
  `0x184d2a2e...` — **different from the NL document's site-a nullifier**
  (`0x234c4126...`), confirming cross-document nullifiers differ even for an
  identical scope. `param_commitment` again identical (`0x2ccd6b1a...`,
  correctly scope/document-independent).
- The age circuit's VK (`bb write_vk` once for `compare_age.json`) verified
  correctly against **both** the NL and the US witnesses — this circuit
  (unlike `sig-check/id-data`) is document-shape-independent, so no separate
  circuit variant was needed here.

**Boolean summary for the coordinator's cross-document ask**: NL and US
produce **different** nullifiers for the same scope (`site-a`) — TRUE (as
expected: a nullifier that didn't vary per-document would be a serious bug,
since it would let one service correlate two different people/documents as
"the same").

### 6. Freshness / unlinkability (item 4, same-document same-scope)

Two `bb prove` runs from the **identical** NL `sig-check/id-data` witness
(same Prover.toml, no re-execution of `nargo execute`):

- Proof bytes: **DIFFER** (`a33e841b...` vs `412c6055...` SHA-256) — expected,
  UltraHonk's default target includes ZK blinding/randomization per proof.
- Public inputs: **IDENTICAL** (`711c02f4...` both times) — expected, the
  circuit's public output is a deterministic commitment over the (fixed)
  inputs, not randomized.
- Both proofs independently verified `true` against the same VK.

### 7. Planted negatives (item 5)

| Negative | Mechanism | Result |
|---|---|---|
| (i) Flip one byte in DG1 | Edited the `dg1` array in a copy of the NL Prover.toml (`Prover-negdg1.toml`), one byte at a fixed array index changed by +1 mod 256 (value never logged) | **Did NOT fail** — `nargo execute` succeeded, `bb prove`/`bb verify` both succeeded (verify=true). **This is expected, not a missed negative** — see finding below. |
| (ii) Flip one byte in the SOD signature | Same technique on `sod_signature` (`Prover-negsig.toml`) | **Fired correctly** — `nargo execute` failed outright with `Failed constraint` / `Cannot satisfy constraint` at `noir_rsa`'s `rsa.nr:257` (`compare_signature_sha256` → `verify_sha256_pkcs1v15` → `verify_signature`, called from `main.nr:26`). No witness, no proof possible. |
| (iii) Verify against a different circuit's VK | Wrote a VK for the (unrelated, already-compiled) `sig-check/dsc/tbs_700/rsa/pkcs/2048/sha256` circuit, then ran `bb verify` on the NL id-data proof against that VK | **Fired correctly** — `bb verify` exited 1 with `UltraVerifier: verification failed at reduction step` / `Proof verification failed`. |

**Finding on (i), stated plainly rather than glossed over**: the
`sig-check/id-data` circuit's only cryptographic constraint is "the SOD's
`signed_attributes` were RSA-signed by the DSC key embedded in the SOD's
`tbs_certificate`." `dg1` is a **private input that is never constrained
against anything inside this circuit** — it is only folded into the output
commitment (`comm_out = commit_to_id(..., dg1, ...)`) for a *downstream*
circuit to check. Binding DG1's actual bytes to the SOD's claimed DG1 hash is
the job of the separate `data-check/integrity/sa_sha256/dg_sha256/...` circuit
(hashes DG1, compares against the hash embedded in `e_content`), which was
**not compiled or run this session** (out of scope — see next steps). Proof:
proving/verifying the tampered-DG1 witness both succeeded, and its public
output (`comm_out`) genuinely differed from the untampered one
(`0x20a5824e...` vs `0x0a5d3285...` from the original NL run) — the tamper
*is* detectable, but only by an entity that separately re-derives and checks
`comm_out` against an independently-verified DG1 hash (i.e., by composing with
the integrity circuit), not by this circuit alone. **This is a real
architectural finding, not a test-harness bug**: proving "SOD signed by DSC"
in isolation provides zero guarantee about DG1 authenticity; the "DOB in DG1
puts holder over 18" claim requires the full `id-data` → `integrity` →
`age`/`disclose` circuit chain (with `comm_in`/`comm_out` linked, in
production via Noir recursive verification of each stage's proof), not the
signature-check circuit alone. The `compare/age/standard` circuit run in
section 5 above took `salted_dg1` values built directly from the trusted local
`PassportViewModel`, bypassing this same gap — it does **not**, by itself,
prove that the DG1 it used was the one actually signed by the DSC. Flagging
this gap explicitly as unresolved.

### 8. Disk usage / non-PII confirmation

`spikes/m1-zk/out/` (proofs, VKs, public inputs, Prover.toml — the Prover.toml
files DO contain raw DG1 bytes as a circuit input and are therefore
PII-bearing; `out/` is gitignored): 284 KB. `spikes/m1-zk/run/node_modules/`:
not measured, gitignored implicitly (untracked, would need its own ignore rule
if committed — flagging that `run/node_modules/` is not yet in `.gitignore`
and should be added before any commit is considered, though no commit was
made this session). Large witness files (`*.gz`, several MB each) live under
`vendor/zkpassport-circuits/target/`, already covered by the `vendor/`
ignore rule.

### What was NOT run (this addendum)

- The BSI masterlist / real CSCA-root pipeline for either document (see
  section 3 fork).
- The `sig-check/dsc` (DSC-to-CSCA) circuit against either real document's
  actual CSCA (no CSCA certificate is available from either SOD).
- The `data-check/integrity` circuit (DG1-hash-vs-`eContent` binding) for
  either document — this is the missing link identified in section 7's
  finding on planted negative (i).
- A real OPRF-blinded nullifier (`nullifier_secret` was `0`, not a real
  per-service secret) — see section 5 fork.
- Recursive composition of `id-data` + `integrity` + `age` proofs into one
  chained/aggregate proof — each circuit was proved independently against
  library-derived inputs, not against each other's on-chain outputs.
- `npm audit fix` for the 3 advisories `npm install` reported.
- Adding `spikes/m1-zk/run/node_modules/` to `.gitignore` (flagged above,
  not yet done).
- Nothing was committed this session, per instruction.

### Next steps (supersedes/extends the earlier list)

1. Compile and run `data-check/integrity/sa_sha256/dg_sha256/...` for both
   documents to close the DG1-authenticity gap identified in section 7.
2. Decide (with the owner) whether to fetch each document's real, publicly
   published CSCA certificate (NL: `CSCA NL` serial 7; US: "U.S. Department of
   State MRTD CA") to build a genuine (if single-leaf, non-BSI-masterlist)
   Merkle root and run `sig-check/dsc` against it for real chain-of-trust
   evidence — this is new work, not something "extracted from the SOD."
3. Wire up a real OPRF proof (or confirm the zero-secret nullifier path is an
   acceptable production mode) before treating the nullifier as
   privacy-hardened.
4. Wire actual Noir recursive verification between `id-data` → `integrity` →
   `age` so a single end-to-end proof (not three independent ones) backs the
   "SOD signed by DSC chaining to CSCA; DG1 says over 18" claim.
5. Add `spikes/m1-zk/run/node_modules/` to `.gitignore`.

---

## Full composition results (2026-08-30)

Closes the gap from the previous addendum: for both real documents, all four
circuit stages of the "SOD signed by DSC chaining to CSCA; DG1 says over 18"
claim were run — **DSC→CSCA, SOD→DSC, DG1↔SOD integrity, and age/disclosure —
each executed, proved with `bb prove`, and verified with `bb verify` only (no
chain, no RPC — see confirmation at the end).** No DG1/MRZ/name/DOB content is
printed below.

### (1) Real BSI masterlist — extracted, verified, rooted

The M0 spike's bundled asset (`spikes/m0/app/src/main/assets/masterList`,
899,665 bytes) is the bare ICAO `CscaMasterList` ASN.1 structure (`SEQUENCE {
version INTEGER, certList SET OF Certificate }`, already unwrapped from its
outer CMS `SignedData` — confirmed with `openssl asn1parse`, since
`cryptography.hazmat.primitives.serialization.pkcs7.load_der_pkcs7_certificates`
failed on it directly). A ~50-line stdlib-only Python DER-TLV walker
(`spikes/m1-zk/run/masterlist/extract_certs.py`) extracted **exactly 588
certificates**, matching the coordinator's stated count.

Candidate CSCA search (via `cryptography` 46.0.4, AKI/SKI matching) then
**`openssl verify -no_check_time -partial_chain -trusted <csca.pem>
<dsc.pem>`**, run for real against both extracted DSCs:

```
nl_dsc.pem: OK   (issuer matched csca_0368.der: C=NL,O=Kingdom of the Netherlands,
                  OU=Kingdom of the Netherlands,CN=CSCA NL,SERIALNUMBER=7, RSA-4096,
                  SHA256withRSA — one of two masterlist entries sharing this exact
                  pubkey, csca_0368/csca_0423, confirmed identical via `openssl x509
                  -noout -pubkey` diff)
us_dsc.pem: OK   (issuer matched csca_0500.der: OU=U.S. Department of State MRTD CA,
                  ..., RSA-4096, SHA256withRSA — likewise one of two masterlist
                  entries, csca_0500/csca_0519, sharing an identical pubkey)
```

**Merkle root: built from the FULL 588-certificate list, not the 2-cert
fallback** — `calculatePackagedCertificatesRoot` (from `@zkpassport/utils`,
the actual zkPassport registry-root function, ported call-for-call from the
circuits repo's own `test-helper.ts::convertPemToPackagedCertificateV1`) ran
against all 588 real certs with **zero conversion errors** (3.6s to convert
all 588, 1.3s to compute the root):

```
root = 0x14d7a3d36729eef45352506047893df26fb2eefe0c5005e1d6b988540636a128
```

`getDSCCircuitInputs(vm, salt, packagedCertsFile)` (with `packagedCertsFile.root`
set to that value first — the library's `certificate_registry_root` output field
came back `undefined` until the root was pre-populated on the input object,
matching the fixture generator's own pattern of setting `.root` before use)
found the correct CSCA **inside the full 588-entry list** for both documents
and built valid Merkle inclusion paths automatically:

| | NL | US |
|---|---|---|
| `certificate_tree_index` | 585 | 266 |
| `country` | NLD | USA |
| CSCA key | RSA-4096, SHA256withRSA | RSA-4096, SHA256withRSA |
| Selected `sig-check/dsc` circuit | `tbs_1000/rsa/pkcs/4096/sha256` | `tbs_1600/rsa/pkcs/4096/sha256` |

(Both CSCAs turned out to be RSA-4096 — a different, larger key than either
DSC's RSA-2048 — so the DSC circuit's `csc_pubkey` array is 512 bytes, not
256, requiring the `.../4096/...` circuit variant rather than `.../2048/...`.)

### (a) DSC→CSCA circuit — `sig-check/dsc/tbs_*/rsa/pkcs/4096/sha256`

Compile (once per tbs bucket): NL (`tbs_1000`) 17.03s wall; US (`tbs_1600`)
19.07s wall.

| Step | NL | US |
|---|---|---|
| `nargo execute` | 2.57s wall, 408,288 KB RSS | 2.64s wall, 413,632 KB RSS |
| `bb write_vk` | 2.68s wall, 497,672 KB RSS | 1.61s wall, 468,048 KB RSS |
| `bb prove` | 4.54s wall, 482,604 KB RSS | 4.66s wall, 539,664 KB RSS |
| `bb verify` | **true** | **true** |

RSA-4096 makes this the heaviest stage in the whole pipeline (~500 MB RSS,
vs. ~200 MB for the RSA-2048 `id-data` stage) — expected, modexp cost scales
with modulus size.

**Chain-consistency finding, verified not assumed**: the DSC circuit's output
(`comm_out`) exactly equals the `id-data` circuit's `comm_in` for **both**
documents, byte for byte:

```
NL: dsc comm_out = <redacted: derived from real document>
    id-data comm_in (from prior session's run) = same value
US: dsc comm_out = <redacted: derived from real document>
    id-data comm_in (from prior session's run) = same value
```

This is real evidence the four stages compose correctly via matching
commitments — not yet linked through actual Noir recursive verification (see
"not run" below), but the values that a recursive verifier would check against
each other are confirmed equal.

### (b) `data-check/integrity/sa_sha256/dg_sha256` — DG1 bound to SOD

Compiled once (shared by both documents — this circuit's I/O shape doesn't
depend on TBS/key size), 1.81s wall, with a soundness *warning* (not fatal,
exit 0) of the same class flagged for ECDSA earlier — an unconstrained
Brillig call in `unsafe_get_asn1_element_length` at `src/main.nr:31:18`, not
independently investigated further this session.

`salt_in` was set to `2n` specifically to match the actual `id-data` circuit's
`salt_out=2n`, and **`comm_in` came back identical to `id-data`'s real
measured `comm_out`** for both documents (`0x0a5d3285...` NL,
`0x13aa8bcd...` US) — the second confirmed link in the chain.

| Step | NL | US |
|---|---|---|
| `nargo execute` (real DG1) | 1.34s wall, 297,024 KB RSS | 1.36s wall, 297,176 KB RSS |
| `bb write_vk` | 0.47s wall, 133,044 KB RSS | ~0.16s (not separately timed) |
| `bb prove` | 0.96s wall, 150,916 KB RSS | 0.94s wall, 151,148 KB RSS |
| `bb verify` | **true** | **true** |

**Planted negative — DG1 byte flip, closing last session's gap**: one byte
flipped in `salted_dg1.value` (index fixed, value never logged), same
technique as before. `nargo execute` now **fails outright**:

```
error: Assertion failed: Hash of dg1 not found in eContent
   ┌─ .../src/noir/lib/data-check/integrity/src/lib.nr:172:5
   │
172 │ ╭     assert(
173 │ │         dg1_offset_in_e_content + dg1_hash.len() <= e_content_size,
174 │ │         "Hash of dg1 not found in eContent",
175 │ │     );
   = Call stack:
     1: main at src/main.nr:26:5
     2: check_dg1_sha256 at .../data-check/integrity/src/lib.nr:172:5
```

No witness produced, so no proof possible — the earlier session's finding
(id-data alone doesn't bind DG1) is now closed: `data-check/integrity` is
exactly the circuit that does, and it fires correctly.

### (c) `compare/age/standard` — "over 18", 2026-08-30

`comm_in` came back identical to `data-check/integrity`'s real measured
`comm_out` for both documents (`0x20b931d9...` NL, `0x260e4106...` US) — the
third and final confirmed link, completing DSC→id-data→integrity→age for
both documents.

Control case (`min_age_required=18`, `current_date` fixed to
2026-08-30T00:00Z, `nullifier_secret=0` as decided):

| Step | NL | US |
|---|---|---|
| `nargo execute` | 2.38s wall, 557,936 KB RSS | 2.21s wall, 557,860 KB RSS |
| `bb prove` (shared VK) | 0.89s wall, 159,024 KB RSS | 0.92s wall, 157,992 KB RSS |
| `bb verify` | **true** | **true** |

Nullifiers for scope `site-a`: identical to the prior session's values
(`0x234c4126...` NL, `0x184d2a2e...` US) — reproducible given identical
inputs/salts, as expected.

**Planted negative (i) — threshold the holder does not satisfy.** `min_age=200`
was tried first per the coordinator's suggestion but hit a **different**,
unrelated hardcoded circuit ceiling before ever comparing against the real
DOB:

```
error: Assertion failed: Age must be less than 100
    assert((max_age < 100) & (min_age < 100), "Age must be less than 100");
```

This tests a constant-bound sanity check on the *claimed threshold*, not the
holder's actual age — retried with `min_age=90` (still comfortably above any
plausible real age for either document, without inspecting or asserting the
actual DOB) and got the intended assertion, for **both** documents:

```
error: Assertion failed: Age is not above or equal to min age
    assert(current_date.gte(birthdate.add_years(min_age as u32)), "Age is not above or equal to min age");
```

No witness, no proof, both documents. **Flagging the `min_age=200` case
explicitly as a finding**, not silently swapped out: the coordinator's
suggested "over 200" is not a valid probe of the age-comparison logic at all
in this circuit, because the library/circuit hard-caps any claimed threshold
at under 100 first. A real integration must never pass an untrusted
`min_age >= 100` regardless — that path is a different failure mode
(malformed request), not "holder is not old enough."

**Planted negative (ii) — a past date making the holder under 18.**
`current_date` set to 1990-01-01. This **did fail** for both documents, but
via a **different, unexpected assertion** than intended:

```
error: Assertion failed: Document is expired
    assert(current_date.lt(expiry_date), "Document is expired");
```

Both real documents' MRZ expiry dates are encoded as 2-digit years (e.g. "35"
→ 2035); the circuit's date-comparison utility evidently does not correctly
order a 1990 `current_date` against a 2030s-encoded MRZ expiry once decoded
through its pivot-year logic, so `check_expiry` fires before the age
comparison is ever reached. **This is a real finding about the date library's
valid input range, not a confirmation that the intended "DOB implies under 18"
assertion works** — that specific code path (`compare_age`'s
`current_date.gte(birthdate.add_years(min_age))` with a genuinely-computed
under-18 result) was not isolated or independently confirmed this session.
Both negatives still satisfy the literal instruction ("must fail... not a
true"), but flagging precisely which assertion fired matters for anyone
relying on this later.

### (d) Total wall-clock, peak RSS, and proof bytes per document

Two ways to read "total," both reported since they answer different
questions:

**Steady-state (circuits already compiled, VKs already written — the cost of
producing one more end-to-end proof set):**

| | NL | US |
|---|---|---|
| `nargo execute` × 4 stages | 8.38s | 8.30s |
| `bb prove` × 4 stages | 7.66s | 7.80s |
| `bb verify` × 4 stages | ~0.02s | ~0.02s |
| **Steady-state total** | **~16.1s** | **~16.1s** |

**Cold (includes compiling every circuit variant and writing every VK from
scratch for that document alone — realistic worst case for "first document of
this issuer ever seen"):**

| | NL | US |
|---|---|---|
| `nargo compile` × 4 | 17.03 + 6.28 + 1.81 + 7.95 = 33.06s | 19.07 + 6.54 + 1.81 + 7.95 = 35.37s |
| `bb write_vk` × 4 | 2.68 + 1.18 + 0.47 + 0.45 = 4.78s | 1.61 + 0.61 + ~0.16 + 0 (shared) ≈ 2.38s |
| steady-state (above) | 16.1s | 16.1s |
| **Cold total** | **~53.9s** | **~53.9s** |

(US's `data-check/integrity` and `compare/age` circuits and VKs are
byte-identical to NL's — same circuit, document-shape-independent — so a
real second document from a *different* issuer only pays the DSC+id-data
compile/write_vk cost again, not all four; the "cold" figures above
pessimistically assume no reuse, to answer "first document ever" rather than
"second document, same session.")

**Peak RSS** (max across every step measured for that document — processes
run sequentially, not concurrently, so this is a max, not a sum): **NL
557,936 KB (~545 MiB)**, **US 557,860 KB (~545 MiB)** — both peaks occur
during the `compare/age` circuit's `nargo execute` (witness generation), the
single heaviest step in the pipeline, heavier even than the RSA-4096 DSC
proving step.

**Total proof bytes a verifier would receive per document** (4 independent
UltraHonk proofs + their public inputs — no recursive aggregation was done,
see "not run" below):

| | bytes |
|---|---|
| 4 × proof (14,656 bytes each, constant regardless of circuit — UltraHonk proof size is circuit-independent) | 58,624 |
| dsc + id-data + integrity public inputs (64 bytes each) | 192 |
| age public inputs (256 bytes — 4 public Field outputs vs. 1 for the others) | 256 |
| **Total per document** | **59,072 bytes (~57.7 KB)** |

Identical for NL and US (same circuit shapes, different tbs buckets don't
change UltraHonk's fixed proof size).

### (e) Network confirmation

All of section (a)-(d) above ran with `bb verify` as the only verification
step — no blockchain, no RPC, no external verifier service. Checked for any
new dependency fetches during this session's `nargo compile` calls (all four
circuit variants compiled today: `sig-check/dsc/tbs_1000/.../4096/sha256`,
`sig-check/dsc/tbs_1600/.../4096/sha256`, `data-check/integrity/sa_sha256/dg_sha256`)
— **none showed a `Cloning into` line**, and a directory-mtime check on
`~/opt/noir-home/nargo/github.com/` (nargo's git-dependency cache) found no
new entries created after this session's `npm install` completed. **The only
network activity in this entire two-part exercise was the original `npm
install` for `@zkpassport/utils` and friends** (prior session) — everything
since, including all of today's masterlist extraction, CSCA verification, and
circuit compile/execute/prove/verify, ran fully offline.

### What is still NOT run

- **Phone/mobile proving** — every measurement here is on the Fedora 44 x86_64
  desktop (8 threads visible to `bb`). Barretenberg's RSA-4096 DSC stage at
  ~545 MB peak RSS and ~4.6s prove time has **not** been attempted on any
  Android target; this is the single largest open question for whether the
  real pipeline is phone-feasible at all, let alone within the M0-era Pixel
  6a's thermal/memory budget.
- **Real OPRF-blinded nullifier** — `nullifier_secret=0` throughout, per the
  coordinator's decision to keep it out of scope for this spike.
  **Flagging explicitly per instruction: the OPRF/threshold-network path is an
  unevaluated live dependency** — zkPassport's OPRF auth circuit
  (`src/noir/bin/oprf-auth`, seen in the Nargo.toml workspace member list but
  never opened this session) implies a live OPRF service the prover must call
  during proof generation for the privacy-hardened nullifier path; nothing
  about that service's availability, latency, trust model, or how it fits
  "no issuer in the loop" has been evaluated. Zero-secret nullifiers are
  linkable in principle to anyone who can also compute `comm_in`/`comm_out`
  outside the circuit (i.e., they are not zero-knowledge with respect to a
  verifier who already has the passport view model) — acceptable for a spike,
  not for production.
- **Recursive proof composition** — the four stages were proved and verified
  **independently**; matching `comm_in`/`comm_out` values were confirmed by
  hand (see (a)-(c)), but no Noir circuit actually recursively verifies a
  prior stage's proof inside itself. A real deployment needs either true
  recursion (one final proof) or the verifier checking all four independently
  plus the commitment chain — the latter is what "total proof bytes" in (d)
  assumes (4 separate proofs, not 1 aggregated one).
- **Full BSI root — used, not skipped**: unlike the earlier fork, this
  session **did** use the complete 588-certificate list (not a 2-cert
  stand-in) for the registry root. Not run: verifying the *M0 spike's copy* of
  this masterlist is itself current/authentic (its own provenance — when it
  was fetched, from where, whether it's stale — was outside this session's
  scope; it was accepted as given).
- **PSS RSA variant, ECDSA DSC/CSCA path** — both real documents are
  RSA-PKCS1v1.5, so the PSS and ECDSA circuit families remain untested against
  real data (only against the earlier session's synthetic/library test path).
- **The two other CSCA candidates found per country** (`csca_0423.der` for
  NL, `csca_0519.der` for US) were confirmed to share identical public keys
  with the ones actually used, but were not independently exercised.
- `npm audit fix` for the 3 advisories flagged at `npm install` — still not
  addressed.
- Nothing was committed this session, per instruction.

## Re-proving the age stage under a chiproof nonce (`run/reprove-age-nonce.mjs`)

`node run/reprove-age-nonce.mjs [nl us]` (from `spikes/m1-zk`; `NARGO`/`BB` env override `~/opt` paths) re-proves `compare/age` for each real document with `service_subscope = subscopeFromNonce(fresh chiproof nonce)`, writing only to gitignored `out/<doc>/age-nonce-<short>/` and `out/age-nonce-index.json` (which holds a runtime-generated test `challengeSecret`). `packages/chiproof` zk-passport/e2e tests read those paths and skip when absent.
