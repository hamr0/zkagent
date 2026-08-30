# M1 Q23 evidence — voucher-grade (Play Integrity) and math-grade (ZK over the passport) measured on real documents (2026-08-30)

**Rule for this file (PRD v1.5, carried from M1-POC-EVIDENCE.md)**: no PII values, ever. Field
names, booleans, counts, timings, algorithm names and validity windows (dates) only. Hashes,
digests, tokens, key paths, project numbers, MRZ text, names, document numbers and dates of birth
appear nowhere in this doc.

---

## 1. Why this run

Q23 (`docs/product/learnings.md` §4, D22–D23 entries), following D22 (2026-08-30): tier A's requirement was
narrowed from same-site unlinkability to a single hard line — **nothing in the payload may be
stable across sites**. Two candidate routes for meeting that line were each measured on real
documents this session: Spike A, voucher-grade (Play Integrity, "Google vouches for the app");
Spike B, math-grade (a ZK proof over the passport itself, no attestation, §6.5 of
docs/product/learnings.md).

Pre-registered success criteria, copied verbatim from docs/product/learnings.md §4 (D22-era
Q23 framing) (written before this
run, "so the result can't be rounded up after the fact"):

1. The chosen library actually covers the SOD signature algorithms present on the owner's real
   documents.
2. A proof over the real US passport verifies on desktop.
3. Two proofs generated for the same site are byte-different from each other.
4. Proving time is recorded — first on desktop, then on the Pixel.
5. The verifier runs in plain Node with no blockchain or RPC dependency.
6. The library passes this project's standing external-dependency checklist (maintained,
   lightweight, established, security-aware).

Any one of these failing is a finding, not a setback to argue around; honor-grade remains the
fallback if math-grade fails.

## 2. Documents' SOD profiles

From `spikes/m1-zk/fixtures/real/*.sod.txt` (algorithm reports only — issuer DNs are not PII and
are reproduced as-is; nothing else from these files is used):

| | NL identity card | US passport |
|---|---|---|
| Digest algorithm (DG hashes) | SHA-256 | SHA-256 |
| SignerInfo digest algorithm | SHA-256 | SHA-256 |
| SignerInfo signature algorithm | SHA256withRSA | RSA (SignerInfo alg field; effectively SHA256withRSA per the DS cert below) |
| DS public key algorithm | RSA | RSA |
| DS public key size | RSA-2048 | RSA-2048 |
| DS signature algorithm | SHA256WITHRSA | SHA256WITHRSA |
| CSCA issuer DN | `C=NL, O=Kingdom of the Netherlands, OU=Kingdom of the Netherlands, CN=CSCA NL, SERIALNUMBER=7` | `OU=U.S. Department of State MRTD CA, OU=Certification Authorities, OU=MRTD, OU=Department of State, O=U.S. Government, C=US` |
| DS validity | 2025-11-13 → 2035-11-22 | 2023-08-22 → 2038-12-23 |
| Data groups covered | DG1, DG2, DG3, DG14, DG15 | DG1, DG2, DG11, DG12 |
| DG1 byte length | 95 | 93 |
| SOD byte length | 2236 | 2683 |

**Finding**: both documents' document-signer (DS) certificates are **RSA-2048 / SHA-256**. The
survey behind an earlier assumption (`docs/product/learnings.md` §1, ECDSA-correction entry) that the NL document uses ECDSA was wrong for the SOD
signing key — the NL ID card does carry an ECDSA key (P-256), but only for **Active
Authentication (AA)**, a separate chip-challenge mechanism, not for signing the SOD. Both DS keys
that actually sign the SOD are plain RSA-2048/SHA-256, on both documents. Criterion 1 (algorithm
coverage) is satisfied for both documents on the RSA path; the ECDSA path (needed only for AA, not
for this spike) was not exercised.

## 3. Spike A — Play Integrity (voucher-grade)

**Setup**: a free Google Cloud project (no Play Console account required for this POC) with the
Play Integrity API enabled; a service account (role: standard, no special IAM role documented by
Google beyond project linkage) with Editor access at the project level; the project number (not
the project id) baked into the app via a gitignored `local.properties` entry, read through
`BuildConfig`; decoding via Google's own `decodeIntegrityToken` call from a Node stdlib-only
script (`spikes/m1-integrity/node/decode-tokens.mjs`) using `node:crypto` for the RS256
JWT-bearer OAuth2 exchange — zero npm dependencies. The service-account key file lives outside the
repo (`M1_INTEGRITY_SA_KEY` environment variable), never committed.

**What was captured**: 4 standard tokens, two simulated sites (`site-a`, `site-b`) × two runs
each, issue time 11–18 ms per token (measured client-side, `request_ms` in the capture), 555 bytes
per raw token, all four raw tokens distinct.

**Decoded verdict table** (all 4 identical except where noted):

| Field | Value (all 4 verdicts) |
|---|---|
| `appIntegrity.appRecognitionVerdict` | `UNRECOGNIZED_VERSION` |
| `appIntegrity.packageName` | present, matches the request package name |
| `appIntegrity.certificateSha256Digest` | present, 1 entry (a digest — value withheld) |
| `appIntegrity.versionCode` | `23` |
| `deviceIntegrity.deviceRecognitionVerdict` | `["MEETS_DEVICE_INTEGRITY"]` only (no `MEETS_STRONG_INTEGRITY`, no additional flag) |
| `accountDetails.appLicensingVerdict` | `UNEVALUATED` |
| `deviceAttributes` / `recentDeviceActivity` / `deviceRecall` / `environmentDetails` | absent from all 4 verdicts |
| `requestDetails.requestPackageName` | present, identical across all 4 |
| `requestDetails.requestHash`, `requestDetails.timestampMillis` | present, differ per request (expected) |

`UNRECOGNIZED_VERSION` reflects a locally-built, non-Play-distributed spike APK, not a device or
integrity problem. `MEETS_DEVICE_INTEGRITY` only (not `MEETS_STRONG_INTEGRITY`) is consistent with
a debug-signed/sideloaded build; not investigated further, out of scope for this decode spike.

**`crossSiteStable`** — every field that came back identical run-to-run within a site *and*
site-a-vs-site-b, across all four decoded tokens:

- `accountDetails.appLicensingVerdict`
- `appIntegrity.appRecognitionVerdict`
- `appIntegrity.certificateSha256Digest.0` (a digest — value not shown)
- `appIntegrity.packageName`
- `appIntegrity.versionCode`
- `deviceIntegrity.deviceRecognitionVerdict.0`
- `requestDetails.requestPackageName`

**The reading**: no device-unique field appeared anywhere in the decoded verdict. Every stable
field is either app-build identity (`packageName`, `versionCode`, the app's own signing-cert
digest) or a bounded enum (`appRecognitionVerdict`, `deviceRecognitionVerdict`,
`appLicensingVerdict`) — none of it is a per-device identifier. The per-device opt-in fields
(`deviceAttributes`, `recentDeviceActivity`, `deviceRecall`, `environmentDetails`) were absent
from every verdict (checked by key presence, not just a top-level scan), consistent with those
opt-ins being off for this Cloud project. This is exactly the shape Q23/§6.7 of `docs/product/learnings.md`
predicted for the voucher-grade route: the site sees app-identity + coarse verdicts, not the
device.

**Limits, stated plainly**: this is one device, one build, one short session. It holds only for as
long as the per-device opt-in fields (`deviceAttributes`, `recentDeviceActivity`, `deviceRecall`)
stay unrequested — a verifier that later asks Google for those would reopen the linkability
question this spike just closed. Google decodes every single check (a verifier→Google round trip
for `decodeIntegrityToken`); that latency was not measured here (the ~10 ms figure already on
record in `docs/product/learnings.md` §6.7 is Google's own documentation number, not something this spike
timed). De-Googled devices are untested and fail by construction (Play Integrity requires Google
Play services). A larger, more varied sample — different devices, longer time gaps, real distinct
sites — would be needed before generalizing "no device-unique field" beyond this one
build/device/session.

**Method note**: two parser bugs surfaced only on real data, both now fixed. (1) The capture is a
raw `tee`'d `adb logcat` dump, so every line carries a logcat prefix
(`MM-DD HH:MM:SS.mmm PID TID LEVEL M1Integrity: `) the original parser didn't strip, so it found 0
tokens on the first attempt — `decode-tokens.mjs` now strips the prefix, falling back to matching
bare delimited lines if no prefix is present. (2) `diff-verdicts.mjs` compared field names against
Google's actual response shape, which wraps everything in a top-level `tokenPayloadExternal`; the
`expectedToDiffer` check was matching on unprefixed names and always returning `false` — it now
matches by suffix. Both fixes were re-verified against the same real capture file.

## 4. Spike B — ZK over the passport (math-grade), zkPassport circuits

Toolchain: Noir (`nargo` 1.0.0-beta.22), Barretenberg (`bb` 5.0.0), commit
`1a1836eb958b7d7bbb47fab060128757748dba6a` of `zkpassport/circuits` (Apache-2.0). Rarimo
(`rarimo/passport-zk-circuits`, commit `30b0be2e83062e19f21237c03317c9a26f2dab59`, MIT) was cloned
as a fallback candidate but not used for any proving in this run — no pre-built proving keys are
published for it, and building one locally requires a Powers-of-Tau download plausibly multi-GB
in size for the RSA-2048 circuit, which was explicitly not attempted.

**Compile results** (both `sig-check/id-data/tbs_*/rsa/pkcs/2048/sha256` variants):

| Circuit | Compile time |
|---|---|
| NL — `tbs_1000/rsa/pkcs/2048/sha256` | 6.28s wall (5.87s user) |
| US — `tbs_1600/rsa/pkcs/2048/sha256` | 6.54s wall (6.12s user) |

The two documents' DS certificates fall into different `tbs_*` size buckets (1000 vs 1600 bytes —
911-byte and 1319-byte actual TBS certificates respectively), so they compile to and prove against
different circuits with different verification keys. This is expected/correct (the bucket is
driven by how many X.509 extensions the issuing country's DS certificate carries), not a bug — but
it means a real verifier must select the circuit per issuer rather than assume one fixed circuit
for every document.

**Raw-bytes input path**: `@zkpassport/utils@0.37.4`'s `PassportReader.loadPassport(dg1, sod)`
takes exactly the raw DG1 and SOD bytes a chip read produces — no custom ASN.1/RSA-parameter
parsing needed on this project's side. `getIDDataCircuitInputs()` builds the full Noir input map
(RSA reduction parameters, ASN.1 offsets, salts) from the parsed `PassportViewModel`. Confirmed
against both real documents this session (superseding the source-reading-only conclusion recorded
in the spike's earlier section).

**Per-document witness/prove/verify** (`sig-check/id-data` circuit; `/usr/bin/time -v`, wall clock
+ peak RSS; `HOME`/`NARGO_HOME` pinned to rootless installs under `~/opt`):

| Step | NL (tbs_1000) | US (tbs_1600) |
|---|---|---|
| `nargo execute` (witness) | 2.09s wall, 388,696 KB RSS | 2.09s wall, 389,112 KB RSS |
| `bb write_vk` | 1.18s wall, 180,232 KB RSS | 0.61s wall, 181,908 KB RSS |
| `bb prove` (ultra_honk) | 1.27s wall, 202,516 KB RSS | 1.28s wall, 205,940 KB RSS |
| `bb verify` | ~0.00s wall, 8,356 KB RSS | ~0.00s wall (not independently re-timed, same order) |
| Result | verify = **true** | verify = **true** |
| Proof size | 14,656 bytes | same circuit shape, ~14.6KB (not independently re-measured) |
| Public inputs size | 64 bytes | 64 bytes |

Verification-key hashes for the NL and US proofs differ, confirming the two documents genuinely
compiled to and were proved against different circuits (per the `tbs_1000` vs `tbs_1600` finding
above) — expected, not an error.

**Freshness**: two `bb prove` runs from the identical NL witness produced proof bytes that
**differ** (UltraHonk's default target includes per-proof ZK blinding/randomization), while the
public-input commitment was **identical** both times (a deterministic function of the fixed
inputs, not randomized). Both proofs independently verified true against the same verification
key.

**Nullifier behaviour** (`compare/age/standard` circuit, "over 18" claim, fixed "today" of
2026-08-30T00:00Z for determinism): the nullifier is **stable within a scope** — re-executing the
same NL/`site-a` witness a second time reproduced the exact same nullifier value. It **differs
across scopes** — the NL document's `site-a` nullifier differs from its `site-b` nullifier. It
**differs across documents** — the NL and US documents' `site-a` nullifiers differ from each
other, even for the identical scope. The `param_commitment` output (the age claim itself) was
identical across scopes and documents, as expected for a claim that doesn't depend on either.

**Planted negatives**:

| Negative | Result |
|---|---|
| Flip one byte in DG1 | Did **not** fail — witness generation, proving, and verification all succeeded (verify = true) |
| Flip one byte in the SOD signature | Fired correctly — `nargo execute` failed outright with a constraint violation; no witness, no proof possible |
| Verify against a different circuit's verification key | Fired correctly — `bb verify` exited 1, verification failed |

The DG1-flip **not** failing is explained, not a missed negative: the `sig-check/id-data` circuit
proves only "the SOD's `signedAttributes` were signed by the DSC key embedded in the SOD" — DG1 is
a private input that this circuit never constrains against anything; it is folded unchecked into
an output commitment for a separate, downstream circuit to check. Binding DG1's actual bytes to
the SOD's claimed DG1 hash is the job of the `data-check/integrity` circuit, which was **not**
compiled or run this session. The tamper genuinely changed the output commitment (proving the
tamper is detectable in principle), but only by an entity that independently re-derives and checks
that commitment via the integrity circuit — not by the signature-check circuit in isolation.

**CSCA finding**: both SODs' CMS `certificates` sets confirmed via ASN.1 parsing to contain
**exactly one certificate — the DS (document signer)**. Neither SOD carries a CSCA certificate;
this matches standard ICAO practice (CSCA distribution is out-of-band via national/ICAO PKD
masterlists, not carried on the document) but means the DSC-to-CSCA link (`sig-check/dsc` circuit)
could not be exercised against either real document's actual CSCA this session — there is no CSCA
to extract from the SOD. It was exercised earlier only against synthetic/library test fixtures,
not real data.

**`nullifier_secret = 0`**: the age circuit's real deployment mode uses an OPRF (oblivious
pseudorandom function)-blinded nullifier secret, requiring a live threshold-network dependency that
was not evaluated this session. `nullifier_secret` was set to `0` (the repo's own documented
"non-blinded" convention) to exercise the rest of the circuit. This means the nullifier values
measured above are the plain, non-blinded scoped nullifier — not the fully privacy-hardened
salted one a real deployment would use.

**Part 2 (full composition, 2026-08-30)** — closes the DG1-binding gap and the missing
DSC→CSCA link identified above, for both real documents.

**Real BSI masterlist.** The M0 spike's bundled masterlist asset (899,665 bytes) turned out to be
the bare ICAO `CscaMasterList` ASN.1 structure, not CMS-wrapped as first assumed. A ~50-line
stdlib-only Python DER walker extracted exactly 588 certificates. `openssl verify -no_check_time
-partial_chain -trusted` confirmed both real DS certificates chain to a CSCA inside that list —
**both CSCAs are RSA-4096** (a different, larger key than either DS certificate's RSA-2048),
requiring the `.../4096/...` DSC circuit variant rather than `.../2048/...`. `@zkpassport/utils`'s
own `calculatePackagedCertificatesRoot` helper built a Merkle registry root from the **full
588-certificate list** (not a stand-in), with zero conversion errors; the correct CSCA was found
inside that full list for both documents, with valid inclusion paths built automatically.

**Per-stage results, both documents, all four circuit stages (DSC→CSCA, SOD→DSC, DG1↔SOD
integrity, age):**

| Stage | NL | US |
|---|---|---|
| DSC→CSCA (`sig-check/dsc`, RSA-4096) — `bb prove` | 4.54s wall, 482,604 KB RSS, verify=**true** | 4.66s wall, 539,664 KB RSS, verify=**true** |
| SOD→DSC (`sig-check/id-data`, RSA-2048, from Part 1) | verify=**true** (§4 above) | verify=**true** (§4 above) |
| DG1↔SOD integrity (`data-check/integrity`) — `bb prove` | 0.96s wall, 150,916 KB RSS, verify=**true** | 0.94s wall, 151,148 KB RSS, verify=**true** |
| Age ("over 18", `compare/age/standard`) — `bb prove` | 0.89s wall, 159,024 KB RSS, verify=**true** | 0.92s wall, 157,992 KB RSS, verify=**true** |

RSA-4096 makes the DSC→CSCA stage the heaviest in the pipeline (~500 MB RSS vs. ~200 MB for the
RSA-2048 SOD→DSC stage) — expected, modexp cost scales with modulus size.

**Chain consistency, verified not assumed**: each stage's output commitment was checked
byte-for-byte against the next stage's input commitment, for both documents — DSC's `comm_out` =
SOD→DSC's `comm_in`; SOD→DSC's `comm_out` = integrity's `comm_in`; integrity's `comm_out` = age's
`comm_in`. All four boundaries matched for both documents. This is real evidence the four stages
compose correctly, though not yet through actual Noir recursive verification — see below.

**DG1-flip planted negative now fires correctly.** With the integrity circuit in the loop, the
same one-byte DG1 flip that did not fail in Part 1 (§4) now fails outright at witness generation:
`assert(dg1_offset_in_e_content + dg1_hash.len() <= e_content_size, "Hash of dg1 not found in
eContent")`. This closes the gap identified in Part 1 — `data-check/integrity` is exactly the
circuit that binds DG1 to the SOD, and it fires as expected.

**Age-negative quirks, both flagged as findings rather than silently swapped out:**

- A `min_age=200` threshold does not test the age comparison at all — it hits an unrelated
  hardcoded circuit ceiling first (`assert((max_age < 100) & (min_age < 100), "Age must be less
  than 100")`), a sanity check on the *claimed threshold*, not the holder's actual age. Retried
  with `min_age=90` (still comfortably above any plausible real age) and got the intended
  assertion for both documents: `assert(current_date.gte(birthdate.add_years(min_age as u32)),
  "Age is not above or equal to min age")`.
- Setting `current_date` to 1990-01-01 (to simulate "holder is under 18") failed for both
  documents, but via a different, unexpected assertion: `assert(current_date.lt(expiry_date),
  "Document is expired")`. Both documents' MRZ expiry dates are 2-digit-year-encoded (e.g. "35" →
  2035); the circuit's date utility does not correctly order a 1990 date against a 2030s-encoded
  expiry, so the expiry check fires before the age comparison is reached. This is a finding about
  the date library's valid input range, not a confirmation that the intended "DOB implies under
  18" code path works — that path was not independently isolated this session.

**Totals, steady-state (circuits already compiled), per document**: ~16.1s wall (execute + prove +
verify across all four stages), ~546 MB peak RSS (the age-witness step, the single heaviest step
in the pipeline, heavier even than RSA-4096 DSC proving), 59,072 bytes total to the verifier (4
independent UltraHonk proofs at 14,656 bytes each, plus public inputs — no recursive aggregation).
Identical for NL and US (same circuit shapes; different `tbs` buckets don't change UltraHonk's
fixed proof size).

**Offline confirmed**: no new dependency fetches during any of this session's `nargo compile`
calls, no new entries in nargo's dependency cache after the original `npm install`. The only
network activity across both sessions of this spike was that one `npm install` — every circuit
compile, execute, prove, and verify ran fully offline, and verification used `bb verify` only, no
blockchain or RPC.

## 5. Reading against the pre-registered criteria

1. **Covered algorithms** — yes, both documents (RSA-2048/SHA-256 sig-check circuits compiled and
   proved against both real DS keys).
2. **Proof over real docs verifies on desktop** — yes. The full "SOD signed by DSC chaining to
   CSCA, DG1 over 18" claim now verifies end to end for both real documents (Part 2, §4): all four
   stages (DSC→CSCA, SOD→DSC, DG1↔SOD integrity, age) individually verify true, and the
   commitments match across every stage boundary — as four independent proofs, not one recursively
   aggregated proof.
3. **Two proofs for the same input byte-different** — yes (measured, §4 freshness result).
4. **Proving time** — desktop: recorded (§4 table, ~1.3s `bb prove` per document plus ~2.1s
   witness generation). Phone: **not run**.
5. **Verifier plain Node, no chain/RPC** — yes for what was run (`bb verify`, offline, no
   blockchain or RPC call).
6. **Dependency checklist** — **not assessed** this session: contributor count (~8, per earlier
   spike notes), no published third-party audit located, the mobile reference app is not
   open-sourced, and the OPRF nullifier network is a live, unevaluated dependency. Listed as open,
   not scored.

## 6. NOT established

- Phone (on-device) proving time and RAM for any circuit — every measurement in §4 is on the
  Fedora 44 x86_64 desktop; nothing has been attempted on an Android target.
- The OPRF-blinded nullifier path (`nullifier_secret` was `0` throughout, not a real per-service
  secret) — a live OPRF/threshold-network dependency remains unevaluated.
- Recursive proof composition — the four stages were proved and verified independently, with
  matching commitments confirmed by hand; no Noir circuit recursively verifies a prior stage's
  proof inside itself, so a real deployment still needs either true recursion or a verifier that
  checks all four proofs plus the commitment chain.
- The PSS RSA variant and the ECDSA DSC/CSCA circuit path — both real documents are RSA-PKCS1v1.5,
  so PSS and ECDSA remain untested against real data.
- Play Integrity behaviour on any device, build, or session other than the one measured in §3.
- `decodeIntegrityToken` round-trip latency (verifier → Google) — not timed this session.
- Anything about iOS (App Attest remains an explicit non-goal, unverified per `docs/product/learnings.md` §6.8).

## 7. Method findings

What only real data revealed, not design review or synthetic fixtures:

- **The DG1-authenticity gap** (§4 planted negatives): the sig-check circuit's silence on DG1
  tampering was invisible until a real DG1/SOD pair was run through a planted negative — synthetic
  fixtures authored to "look like" a passport would not have forced the question of which circuit
  actually binds DG1 to the signed hash.
- **Two parser bugs in the Play Integrity decoder** (§3 method note) — the logcat-prefix strip and
  the `tokenPayloadExternal` wrapping — surfaced only once a real device capture and a real Google
  API response existed; both were invisible against hand-made JSON fixtures.
- **The NL document's SOD-signing key is RSA-2048, not ECDSA** (§2) — correcting an assumption
  carried in `docs/product/learnings.md` §6.5's survey of candidate libraries; the NL card's ECDSA key exists but
  signs Active Authentication challenges, not the SOD.
