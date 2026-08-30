# M1b evidence — mode-A unlinkability probe (zk-passport/1, real NL/US documents) — read by owner 2026-08-30; verdict: pass with disclosed bucket (PRD D26)

**Status: DRAFT.** This file records what was measured. It does not decide whether any stable
field is acceptable — that reading is the owner's, per D22 ("nothing stable across sites" is the
bar; who signs off on any exception is not this file's call).

**Rule for this file (carried from M1-POC-EVIDENCE.md / M1-Q23-EVIDENCE.md)**: no PII values,
ever. Field names, byte lengths, equal/different verdicts, longest-common-run lengths, algorithm
names, and timings only. No MRZ text, DG1/SOD bytes, salts, commitments, hashes, or nonce/nullifier
values are printed anywhere below or were logged to any file outside `spikes/m1-zk/out/`
(gitignored) or the session scratchpad.

---

## 1. Why this run

M1b (PRD milestone table row, `docs/product/zkagent-prd.md:150`), answering Q15 (`:263`): does the
attestation/evidence a tier-A (mode-A) presentation carries defeat mode A's own no-identifier
promise? M1's POC already confirmed the *raw device-attestation chain* is a stable per-device
identifier (§8 finding, superseded for v1 by D23→D24: the evidence slot ships empty by default,
and `zk-passport/1` is the first real plug, tier A only, linkability `none` per its registry entry,
D25). This run is the FR9 black-box comparison against `zk-passport/1`'s actual wire bytes — the
first time Q15's suspect list is checked against real proofs of real documents rather than design
review.

**Pre-registered pass criterion, quoted verbatim from the M1b row (PRD `zkagent-prd.md:150`)**:

> No field differs across presentations except those proven independent of holder and device. **A
> planted stable field must make the check fail** — a guard you have not watched fire is not a
> guard. Blocks M3. Answers Q15. Revised by D22: fields stable across presentations to the same
> site are acceptable; fields stable across sites are not.

Method borrowed from 8een §7.3 (`docs/02-evidence/M2-EVIDENCE.md:220-291`, this project's sibling):
that section went through two retracted detectors — a behavioural check that couldn't fail because
the compared value carried no per-presentation data, and a shared-k-gram check that was blind to
any identifier under ~16 bytes — before landing on **longest contiguous common byte run**, with a
**planted positive control that must be caught**, and an explicitly measured detection floor rather
than an assumed one. This run applies that exact discipline to `zk-passport/1`, plus a structured
per-field diff (only possible here because, unlike 8een's CBOR blob, the plug's wire layout names
its 32-byte Field slots).

## 2. Corpus

Six **full** mode-A `zk-passport/1` presentations were built: for each of the two real documents
(NL identity card, US passport — same fixtures as M0/Q23, `spikes/m1-zk/fixtures/real/`), the
entire four-stage chain (DSC→CSCA, SOD→DSC, DG1↔SOD integrity, age) was **re-run from scratch**
against `site-a` twice and `site-b` once, plus a bare mode-A baseline (`evidence: []`) per site (4
more presentations, trivial). This differs from the M1/Q23 artefacts under `spikes/m1-zk/out/`,
which build the dsc/id_data/integrity stages **once per document** and only re-prove the age stage
per scope (`reprove-age-nonce.mjs`) — because those three stages' salts were hardcoded literals
(`1n`, `2n`, `3n`) in `spikes/m1-zk/run/02_build_inputs.js` / `07_build_dsc_inputs.js` /
`08_build_integrity_inputs.js`, this project's own M1/Q23 spike scripts would have produced
byte-identical dsc/id_data/integrity outputs across every "presentation" by construction — not
evidence about the circuits, an artefact of the spike's own reuse. M1b's new build script
(`spikes/m1b-unlink/build.mjs`) instead draws a **fresh random salt at every boundary the vendored
circuits accept one** for every presentation: one salt into `getDSCCircuitInputs`, a
saltIn/saltOut pair into `getIDDataCircuitInputs`, and a 4-field salt struct
(`dg1Salt`/`expiryDateSalt`/`dg2HashSalt`/`privateNullifierSalt`) into
`getIntegrityCheckCircuitInputs`, chained so `dsc.comm_out` feeds `id_data.saltIn`, and
`id_data.saltOut` feeds `integrity.saltIn` — the same chaining rule the vendored library and the
`zk-passport/1` plug both enforce. The age stage's nonce came from a real chiproof
`issueChallenge` call per presentation (D20-sealed, `scopeDomain` `site-a`/`site-b`), carried into
`service_subscope` via the plug's own registry rule (first 31 bytes of `sha256(nonce)`).

**Every one of the four stages took a fresh salt in this run — none was salt-free.** That is
itself worth recording against the task's "no salt → deterministic, a finding not a blocker"
framing: it did not arise here, because all four `get*CircuitInputs` builders accept an explicit
salt/salts argument (`circuit-matcher.d.cts`). What *is* fixed regardless of salt, deliberately,
matching Q23's own documented convention: `nullifier_secret = 0x0` throughout (the OPRF-blinded
nullifier path remains untested — see §6).

**Toolchain**: `nargo` 1.0.0-beta.22 (`~/opt/noir/bin/nargo`), `bb` 5.0.0 (`~/opt/bb/bb`), circuits
selected per document exactly as Q23 found them (NL: TBS-bucket 1000 for both DSC and SOD→DSC
circuits, RSA-4096 CSCA / RSA-2048 DS; US: TBS-bucket 1600, same key sizes; integrity and age use
one shared circuit variant for both documents, SHA-256 throughout). Verification keys were reused
from the existing Q23 artefacts (`spikes/m1-zk/out/<doc>/{dsc,,integrity,age2}/bb/vk`) — a VK is a
deterministic function of the *circuit*, not of any witness, so reusing it does not affect the
salt-freshness claim above.

**Wall-clock, four-stage build per presentation, steady state (circuits pre-compiled)**:

| Presentation | Total wall (ms) | dsc `nargo execute`/`bb prove` (s) | id_data (s) | integrity (s) | age (s) |
|---|---:|---|---|---|---|
| nl site-a r1 | 18,735 | 2.62 / 3.86 | 2.14 / 1.45 | 1.41 / 1.13 | 3.01 / 1.72 |
| nl site-a r2 | 26,288 | 3.79 / 5.44 | 2.98 / 2.29 | 2.03 / 1.78 | 3.31 / 1.71 |
| nl site-b r1 | 25,710 | 3.69 / 5.59 | 3.00 / 2.35 | 2.06 / 1.86 | 3.31 / 1.73 |
| us site-a r1 | 26,088 | 3.70 / 6.25 | 2.98 / 2.31 | 2.04 / 1.78 | 3.34 / 1.58 |
| us site-a r2 | 26,860 | 3.84 / 6.44 | 3.09 / 2.51 | 2.09 / 1.79 | 3.34 / 1.63 |
| us site-b r1 | 26,749 | 3.74 / 6.39 | 3.03 / 2.47 | 2.12 / 1.83 | 3.39 / 1.63 |

Total: 150.4 s wall for the six full presentations (24 stage-proofs), desktop (Fedora 44 x86_64),
not measured on the Pixel (out of scope here, already flagged NOT-established by Q23). `bb verify`
per stage: 8–19 ms, consistent with Q23.

**All six presentations independently verify with real chiproof.** `packages/chiproof/src/plugs/zk-passport.js`'s
`zkPassport()` plug (real `bb verify`, 4 stages, all binding checks: chain, nonce, threshold,
scope) returned `{ok:true, valid:true, reason:'zk_verified'}` for every one of the six, 12–25 ms
each (`spikes/m1b-unlink/verify.mjs`). An unverifiable presentation would prove nothing about
unlinkability — this step exists to rule that out before trusting any byte comparison below.

## 3. Method

**(a) Structured field diff.** Each stage's `public_inputs` is split into 32-byte Field slots, per
the plug's own documented layout (`zk-passport.js` header comment, itself read from
`compare/age/standard/src/main.nr`): `dsc`/`id_data`/`integrity` → `[field0, comm_out]` (64 B, 2
fields); `age` → `[comm_in, current_date, service_scope, service_subscope, param_commitment,
nullifier_type, nullifier, oprf_pk_hash]` (256 B, 8 fields). Each field, plus `vk_sha256`, proof
length, and total presentation length, is compared equal/different for three pairs: **a1 vs a2**
(same document, same site, two runs — tests same-site stability, which D22 makes acceptable),
**a1 vs b1** (same document, different site — the actual D22 bar), and **nl-a1 vs us-a1**
(different documents, same site — tests document-bucketing, the Q15/Q23 concern).

*Correction made while building this section*: `dsc`'s circuit (`sig-check/dsc/.../src/main.nr`)
does **not** take or return a `comm_in` — its `main` returns a single `pub Field`, and its first
public input slot is `certificate_registry_root` (the masterlist Merkle root), not a chained
commitment. The plug's `[comm_in, comm_out]` shorthand is a **binding-purpose** label (dsc's
`field(1)` is what `id_data`'s `field(0)` must chain to); it does not claim dsc's `field(0)` is a
commitment at all. Read from `main.nr` directly rather than assumed, and noted here so a reader of
the results table below (which uses the plug's own field name) isn't misled.

**(b) Byte probe: longest contiguous common byte run.** `spikes/m1b-unlink/probe.mjs` implements
this as 8een's §7.3 does — binary search on run length, backed by a Rabin-Karp rolling hash
(double modulus, then a byte-for-byte confirmation to rule out hash collisions) — **not** k-gram
counting (8een's second, retracted, margin-blind version). Run over two scopes: the whole
serialized presentation, and each stage's raw `proof` bytes (decoded from base64 — comparing
undecoded base64 text would inflate the "structural" floor with base64's own encoding artifacts).

**(c) Positive controls, both required by the task, both shown failing before passing** —
mirroring 8een's own finding that a control which passes on the first try, unwatched, is suspect:

- **Byte-run control**: bytes are copied from `nl-a1`'s `age.proof` into a **copy** of `nl-b1`'s
  `age.proof`, at a fixed offset (2000) chosen — from a measurement, see §4(b) — to sit well past
  the buffers' natural common region. Sizes 8/16/64/128/256/300/512 B were tried in escalating
  order; "caught" requires the detector's reported longest-common-run to have **moved to the plant
  offset and grown to at least the planted length** (not merely that the pre-existing floor already
  exceeded the plant size — the exact trap 8een's first byte-run attempt fell into).
- **Field-plant control**: one whole 32-byte field (`age.comm_in`) is copied from `nl-a1`'s
  `public_inputs` into a copy of `nl-b1`'s, and detector (a) must flip that field's verdict from
  different to equal.

## 4. Results

### (a) Structured field diff

Legend: **=** identical across the pair, **≠** differs (both are informative; `=` on a
holder/document-independent field is expected and benign, `≠` on a per-presentation salted
commitment is the freshness the plug's `verify()` is designed to require).

| Field | a1 vs a2 (same site) | a1 vs b1 (cross-site) | nl-a1 vs us-a1 (cross-doc) |
|---|---|---|---|
| `dsc.field0` (`certificate_registry_root`) | = | = | = |
| `dsc.comm_out` | ≠ | ≠ | ≠ |
| `dsc.vk_sha256` | = | = | **≠** |
| `dsc.proof_length` | = (14,656 B) | = | = |
| `id_data.comm_in` | ≠ | ≠ | ≠ |
| `id_data.comm_out` | ≠ | ≠ | ≠ |
| `id_data.vk_sha256` | = | = | **≠** |
| `id_data.proof_length` | = | = | = |
| `integrity.comm_in` | ≠ | ≠ | ≠ |
| `integrity.comm_out` | ≠ | ≠ | ≠ |
| `integrity.vk_sha256` | = | = | = |
| `integrity.proof_length` | = | = | = |
| `age.comm_in` | ≠ | ≠ | ≠ |
| `age.current_date` | = | **=** | = |
| `age.service_scope` | = | **≠** | = |
| `age.service_subscope` | ≠ | ≠ | ≠ |
| `age.param_commitment` | = | = | = |
| `age.nullifier_type` | = | = | = |
| `age.nullifier` | ≠ | ≠ | ≠ |
| `age.oprf_pk_hash` | = | **=** | **=** |
| `age.vk_sha256` | = | = | = |
| `age.proof_length` | = | = | = |
| total presentation length | = (79,618 B) | = | = |

Bold `=` marks the fields flagged for owner reading in §5. All salted commitments (`comm_in`,
`comm_out` of every stage; `age.nullifier`; `age.service_subscope`) differ in every pair, including
the same-site pair — freshness is per-presentation, stronger than D22 requires (D22 only demands
same-site *may* repeat; it does not, here, because every salt was redrawn every time).

### (b) Byte probe: longest contiguous common run

| Pair | Scope | Longest common run | Located at |
|---|---|---:|---|
| a1 vs a2 (same-site) | whole presentation (JSON) | 468 B | offset 39,769 in a1 |
| a1 vs a2 | `dsc.proof` / `id_data.proof` / `integrity.proof` / `age.proof` | 271 B each | offset 0 (every stage) |
| a1 vs b1 (cross-site) | whole presentation | 467 B | offset 39,770 |
| a1 vs b1 | each stage proof | 271 B | offset 0 |
| nl-a1 vs us-a1 (cross-doc) | whole presentation | 461 B | offset 59,525 |
| nl-a1 vs us-a1 | each stage proof | 271 B | offset 0 |

**The 271-byte run is a fixed structural prefix, not a document/site/holder identifier.** Directly
confirmed (`node -e` scan, not printed above but reproducible from `spikes/m1b-unlink/probe.mjs`):
it starts at byte 0 in *both* buffers of *every* pair, including cross-document — i.e. it is a
UltraHonk proof-format/circuit-shape header common to any proof of the same circuit, independent of
witness. The whole-presentation figure (~461–468 B) is larger for a mundane reason: JSON repeats
the same key names (`"proof":`, `"public_inputs":`, `"vk_sha256":`, `"type":"zk-passport"`, …) four
times (once per stage) plus base64 re-encodes the same 271-byte proof prefix, compounding the
structural floor rather than adding anything document-specific.

### (c) Positive controls

**Byte-run control** (plant offset 2000, chosen clear of the measured 271 B prefix):

| Planted size | Longest common run after plant | Match location | Caught? |
|---:|---:|---|---|
| 8 B | 271 B | offset 0 (unchanged — the pre-existing prefix, not the plant) | **no** |
| 16 B | 271 B | offset 0 | **no** |
| 64 B | 271 B | offset 0 | **no** |
| 128 B | 271 B | offset 0 | **no** |
| 256 B | 271 B | offset 0 | **no** |
| 300 B | 300 B | offset 2000 (the plant) | **yes** |
| 512 B | 512 B | offset 2000 (the plant) | **yes** |

**Detection floor for this byte-run detector, over raw proof bytes: between 256 B and 300 B** —
measured, not assumed, and far higher than 8een's ~11 B floor (M2-EVIDENCE.md §7.3) because this
proof format's constant structural region (271 B) is itself much larger than a CBOR mdoc's framing
overhead. **An 8- or even 256-byte planted identifier inside a proof body would NOT be caught by
this detector as configured** — exactly the class of miss the task asked to surface rather than
paper over. The 8-byte attempt specifically: **not caught**, as instructed to check first. (The
structured field-diff detector, §(a), does not share this weakness — see next.)

**Field-plant control**: baseline `age.comm_in` equal/different verdict for a1-vs-b1 was `≠`
(different, correctly — fresh salts). After copying a1's full 32-byte `age.comm_in` field into a
copy of b1's `public_inputs`, the verdict became `=` (equal). **Caught.** This control has no
floor problem — each field is compared as a whole 32-byte unit with no surrounding structural
noise, so a full-field plant (or any real stable field, including tiny ones, since the unit of
comparison is the whole slot) is always visible to detector (a).

### (d) Masked re-measurement — the 271 B/256–300 B floor above is an artefact, not a limit

**Follow-up, 2026-08-30.** §4(b)/(c)'s floor was measured by comparing whole proof buffers
including their format-constant regions, so any planted identifier smaller than that constant
region was structurally invisible regardless of the detector's real resolving power — the floor
measured a coincidence of scale, not a property of the method. `spikes/m1b-unlink/mask-probe.mjs`
fixes this: it computes, per stage, the **constant mask** — every byte position that is identical
across **all six** corpus presentations (both documents, all three sites/runs) — then re-runs the
longest-common-run search rejecting any candidate match whose *entire* span falls inside that mask
(a match fully inside the mask is guaranteed to exist by the mask's own definition and proves
nothing; a match that includes even one byte outside the mask is real evidence).

**Mask sizes, measured (not the 271 B assumed from §4b's single visible run)**:

| Scope | Total bytes | Masked (constant across all 6) | Masked fraction |
|---|---:|---:|---:|
| `dsc.proof` | 14,656 | 2,752 | 18.8% |
| `id_data.proof` | 14,656 | 2,752 | 18.8% |
| `integrity.proof` | 14,656 | 2,752 | 18.8% |
| `age.proof` | 14,656 | 2,912 | 19.9% |
| whole presentation (JSON) | 79,618 | 15,473 | 19.4% |

The mask is **not** one 271 B block. It is a 271 B run at offset 0 (the header found in §4b),
followed by a *repeating* pattern of ~15–17 B constant runs roughly every 32 bytes for the rest of
the buffer (e.g. `age.proof`'s mask: offset 0/len 271, then 288/17, 320/15, 352/17, 384/15, …, 155
runs total). This stride matches the proof's own 32-byte Field-element framing — consistent with
several other proof elements (not just the header) having fixed leading bytes on every proof of
this circuit (e.g. curve-point/domain-parameter encodings that don't depend on the witness), not a
single contiguous "preamble" as §4b's framing implied.

**Masked longest-common-run, same three pairs**:

| Pair | Scope | Longest common run excluding the mask | Located at |
|---|---|---:|---|
| a1 vs a2 (same-site) | `dsc.proof` | 20 B | non-header offset |
| a1 vs a2 | `id_data.proof` | 20 B | non-header offset |
| a1 vs a2 | `integrity.proof` | 18 B | offset 1,248 |
| a1 vs a2 | `age.proof` | 18 B | offset 11,520 |
| a1 vs a2 | whole presentation | 468 B | offset 39,769 (**unchanged from §4b — see caveat below**) |
| a1 vs b1 (cross-site) | `dsc.proof` | 19 B | offset 11,711 |
| a1 vs b1 | `id_data.proof` | 33 B | offset 9,535 |
| a1 vs b1 | `integrity.proof` | 18 B | offset 1,248 |
| a1 vs b1 | `age.proof` | 19 B | offset 11,072 |
| a1 vs b1 | whole presentation | 467 B | offset 39,770 (**unchanged — caveat below**) |
| nl-a1 vs us-a1 (cross-doc) | `dsc.proof` | 19 B | offset 11,839 |
| nl-a1 vs us-a1 | `id_data.proof` | 19 B | offset 11,968 |
| nl-a1 vs us-a1 | `integrity.proof` | 19 B | offset 11,903 |
| nl-a1 vs us-a1 | `age.proof` | 18 B | offset 11,520 |
| nl-a1 vs us-a1 | whole presentation | 86 B | offset 79,231 (**dropped from 461 B — see caveat below**) |

**Per-stage-proof floor, once the global constant is excluded: 18–33 B** — an order of magnitude
below §4b's 271–300 B, and now close to 8een's own ~11 B floor (§7.3, M2-EVIDENCE.md), which is the
expected shape once both methods are actually measuring incidental/residual structure rather than a
gross format header.

**Masked plant ladder** (`age.proof`, plant offset 2000, same as §4c): baseline non-trivial match
(no plant) is **19 B** at offset 11,072 — i.e. even before planting anything, two different nl
presentations share a 19 B non-header run somewhere in the buffer; this is the real noise floor
the plant must be measured against.

| Planted size | Longest common run after plant (excl. mask) | Match location | Caught? |
|---:|---:|---|---|
| 8 B | 19 B | offset 11,072 (baseline run, unaffected by the plant) | **no** |
| 11 B | 19 B | offset 11,072 | **no** |
| 16 B | 19 B | offset 11,072 | **no** |
| 32 B | 32 B | offset 2,000 (the plant) | **yes** |

**New measured floor for the masked byte-run detector: strictly between 16 B and 32 B** — not
between 256 B and 300 B as §4c reported. The 8-byte case specifically: still **not caught** (as
with the unmasked detector), confirming an 8-byte planted identifier is invisible to this class of
detector regardless of masking; the 32-byte gap between 16 B (fails) and 32 B (only size tried that
succeeds) was not narrowed further, since the coordinator's ladder specified exactly these four
sizes — a finer probe (e.g. 20/24/28 B) would tighten this range if wanted.

**Caveat, stated rather than hidden: the whole-presentation scope did NOT drop to the per-stage
floor for the two nl-only pairs (a1-a2, a1-b1 stayed at 467–468 B; only the cross-document pair
dropped, 461 B → 86 B).** The 6-way mask only removes bytes constant across *both* documents. NL's
own three presentations (a1, a2, b1) share additional structure that is constant for **NL's specific
circuit variant** (TBS-bucket 1000) but not for US's (TBS-bucket 1600, a different `vk_sha256` per
§4a finding 3) — e.g. circuit-shape parameters baked into that variant's proof format. A 6-way mask
cannot see this, because it is not universal, only nl-universal. This is not a document/holder leak
by itself (any two NL presentations share it because they use the *same circuit*, not because of
who scanned) but it is the same class of finding as §5 item 3 (document-bucket, not holder,
linkable) showing up again at the byte level. A doc-scoped mask (computed from only the three NL
presentations) was not built in this follow-up — left as a further NOT-established item (§6).

### (e) `current_date` granularity — measured with a genuine time gap

**Follow-up, 2026-08-30.** §4(a)/§5's original `age.current_date = (stable across sites)` finding
was explicitly flagged as a method artifact (one shared `Date.now()` built all six presentations).
A seventh presentation (nl, site-b, run 2) was built by `spikes/m1b-unlink/extra-current-date.mjs`
using a fresh, later `Date.now()` call — run well after the original corpus, a genuine elapsed-time
gap, not a synthetic offset — and diffed against nl site-b run 1:

| Check | Result |
|---|---|
| `age.current_date` equal to nl b1's? | **different** — confirms the earlier stability was the shared-timestamp artifact, not a real property |
| `challenge.issued_at` / `challenge.expires_at` equal to nl b1's? | different (expected — independently minted) |
| Decoded wire `current_date` (u64 BE, low 8 bytes of the 32-byte slot) equals the `nowSeconds` fed into `getAgeCircuitInputs`? | **yes, exact match** |
| Leading 24 bytes of the `current_date` slot zero? | yes (consistent with a `u64` in a 32-byte Field slot) |
| Sub-second component of the real timestamp truncated before use? | yes (`Math.floor(Date.now()/1000)`, so precision is exactly 1 second, not finer and not coarser) |

**Wire unit and precision, from source, then confirmed by the decode above**: `main.nr`
(`compare/age/standard/src/main.nr`) declares `current_date: pub u64`. The input builder
(`spikes/m1-zk/run/03_build_age_inputs.js`, and this follow-up's own call into
`getAgeCircuitInputs`) feeds it `Math.floor(<wall-clock ms> / 1000)` — **Unix epoch seconds, not a
`YYYYMMDD` date encoding and not day-truncated.** The value on the wire is exactly what was fed in,
byte-for-byte, confirmed by decoding the real proof's public inputs rather than assumed from
reading the input builder alone.

**The chiproof plug's `max_scan_age` check gates on exactly this field.** Confirmed empirically,
not just from re-reading `zk-passport.js`'s source: verifying the new presentation with
`maxScanAge: 1` (ms) returned `{valid:false, reason:'zk_scan_too_old'}`; the same presentation with
`maxScanAge: null` (unlimited) returned `{valid:true, reason:'zk_verified'}`. Both a real `bb
verify` and every binding check ran in both cases — the only difference was the `max_scan_age`
comparison against `current_date`. This means `current_date` **could** be coarsened (e.g. to the
day) without breaking `max_scan_age`'s own function, *provided* the coarsening still lets
`max_scan_age` distinguish "within the allowed window" from "too old" at whatever granularity an
adopter configures that window to — a day-coarsened `current_date` would make any `max_scan_age`
tighter than ~24h meaningless, which is a real trade-off, not a free fix. Whether to coarsen it, and
to what granularity, is the owner's call (§5 item 5, revised below); this run only establishes that
the check does not require second-level precision, it simply currently gets it.

## 5. Reading against the criterion (facts only; acceptability is the owner's call)

**Owner ruling (D26): disclose. See PRD.**

Per D22, the bar is "nothing stable across sites." Fields found stable **across sites** (bold rows
in §4a), with the most likely explanation for each, established from source or first-order
reasoning where noted:

1. **`dsc.field0` / `certificate_registry_root`** — stable across sites *and* documents in this
   corpus. Explanation: it's the Merkle root of the masterlist/trust-list both documents were
   proved against (`spikes/m1-zk/out/masterlist-certs/packaged-certs-full.json`, the same file for
   both docs here) — a property of the **trust list**, not the holder or device. It would change
   only when the trust list itself is rotated, giving it the cardinality of "how many trust-list
   versions are concurrently accepted," analogous to FR10's already-accepted client-build
   visibility. Confirmed from `main.nr`'s signature (§3 correction) that this slot is an *input*
   the prover is handed, not a commitment the circuit derives from anything document-specific.
2. **`integrity.vk_sha256`, `age.vk_sha256`** — stable across sites and (in this corpus) across
   documents, because both real documents happen to share the same DG-hash algorithm (SHA-256,
   per Q23 §2) and the age circuit has no document-driven variant at all ("standard" only was
   exercised). Explanation: circuit-selector, would differ only for a document whose DG hash
   algorithm bucket differs (SHA-1/224/384/512 variants exist in the vendored repo but were not
   exercised — see §6).
3. **`dsc.vk_sha256`, `id_data.vk_sha256`** — stable across sites **for the same document**, but
   **differ across documents** (nl vs us, marked `≠` in §4a). This reconfirms, now through
   `zk-passport/1`'s actual wire bytes rather than design review, Q23's finding 4: the DSC/SOD
   circuit variant is chosen by the issuing country's DS-certificate TBS-length bucket, which is a
   property of the document (and by extension, likely, the issuing country), not the holder. A
   verifier — or any two colluding sites — that can see `vk_sha256` can bucket a holder's document
   type/issuing-country class and correlate that bucket across sites, without ever seeing a salted
   commitment. This is the single most concrete escalation candidate this run produced.
4. **`age.oprf_pk_hash`** — stable across sites and across documents in this corpus. Explanation:
   `nullifier_secret` was held at the fixed testing constant `0x0` for every one of the six
   presentations (matching Q23's documented "non-blinded" convention, not a random per-run value),
   and `oprf_pk_hash` is a deterministic function of `nullifier_secret`. With the secret fixed, the
   hash is fixed for everyone in this corpus by construction — **this measures the current
   convention, not the real OPRF-blinded deployment**, which is untested (§6). Whether a live
   OPRF network would make this field vary per verifier/service (as intended) or leave a stable
   residual is exactly what is NOT established here.
5. **`age.current_date`** — **REVISED, 2026-08-30 follow-up (§4e): NOT stable under a real time
   gap.** The original six-presentation corpus showed it stable across every pair, but that was
   confirmed to be a method artifact (one shared `Date.now()` built all six) — a seventh
   presentation built later, with a genuinely later `Date.now()`, differs from nl site-b run 1 on
   this exact field. So `current_date` does **not** belong in the "stable across sites" list on the
   evidence actually gathered; it is corrected out of this finding. What the follow-up instead
   establishes, measured rather than inferred: the wire field is **Unix epoch seconds** (not
   `YYYYMMDD`, not day-truncated — `main.nr`'s `current_date: pub u64`, confirmed by decoding a real
   proof's public inputs and getting back exactly the second-precision timestamp that was fed in),
   and the chiproof plug's `max_scan_age` check reads this exact field (empirically confirmed:
   `maxScanAge:1` → `zk_scan_too_old`, `maxScanAge:null` → verified). The residual concern Q15
   raised is therefore about **precision, not stability**: a real deployment's `current_date` is a
   near-unique per-scan second-level timestamp by default (not itself a stable cross-site
   identifier the way a fixed field would be, but a correlatable one — two sites comparing "who
   scanned within the same second" is a timing side-channel, the class of risk the EU AVS blueprint
   blurs `ValidityInfo` clocks for). Coarsening `current_date` (e.g. to the day) is possible without
   breaking `max_scan_age`'s pass/fail logic in principle, but would cap how tight an adopter's
   `max_scan_age` window can usefully be — a real trade-off for the owner, not a free fix. Whether
   to coarsen, and to what granularity, is escalated in §6/below.

Fields correctly **not** stable across sites, i.e. behaving as D22 requires: every salted
commitment (`comm_in`/`comm_out` at every stage boundary), `age.service_subscope` (nonce-bound, as
D25 designed it to be), `age.nullifier` (differs same-site run-to-run too — fresher than D22
requires), `age.service_scope` (differs cross-site as intended, since it's `scopeField(domain)` —
it is stable **within** a site across the two same-site runs, which D22 explicitly permits), and
`age.current_date` (§4e follow-up: differs given a real elapsed time gap between builds — it is
not a stable identifier, though its second-level precision is itself a separate, narrower
correlation concern, see item 5 above).

Proof lengths and total presentation length are constant everywhere (14,656 B/stage proof, 79,618 B
total) — expected for fixed-shape UltraHonk proofs of fixed circuits, and a positive match against
FR6's "byte-shape identical" requirement for one client build in one mode, not a linkability risk
by itself (it doesn't distinguish one presentation from another, only this evidence type from
others or from bare mode A, which FR6 already accepts as visible).

## 6. NOT established

- **The OPRF-blinded nullifier path.** `nullifier_secret = 0x0` throughout, matching the Q23
  spike's own documented non-blinded convention — a live OPRF/threshold-network dependency was not
  evaluated in Q23 and was not evaluated here either. Whether `age.oprf_pk_hash` (and `nullifier`)
  behave as intended under real per-service blinding is unmeasured.
- ~~Real inter-presentation timing gaps~~ — **resolved by the §4e follow-up**: a genuinely later
  presentation shows `current_date` differs, as expected for a real Unix-seconds timestamp. What
  remains NOT established: real-world correlation risk from that precision (whether an adversary
  who sees `current_date` across two sites' logs can usefully narrow down "same scanning session")
  — this run shows the field *can* carry that signal, not that it *is* exploited or how coarsening
  would trade off against `max_scan_age` in a live deployment.
- **A document-scoped constant mask.** §4(d)'s 6-way mask only removes bytes constant across both
  documents; NL's three presentations share additional structure (up to ~467 B in the whole
  presentation, per §4d's stated caveat) that a 3-way NL-only mask would likely also exclude as
  "circuit-variant-constant, not holder-specific" — that narrower mask was not built in this
  follow-up.
- **A finer masked-detector floor.** The masked plant ladder (§4d) used exactly the four sizes
  specified (8/11/16/32 B) and found the boundary between 16 B (fails) and 32 B (succeeds); sizes
  in between (e.g. 20/24/28 B) were not tried, so the true floor is only bounded to that range, not
  pinned to a single byte count.
- **Non-SHA-256 DG-hash circuit variants** (`data-check/integrity/sa_sha256/dg_sha{1,224,384,512}`,
  and the corresponding `sa_sha{1,224,384,512}` families) — both real documents use SHA-256
  throughout, so only the `sa_sha256/dg_sha256` integrity variant and the RSA-PKCS1v1.5 sig-check
  variants were exercised. A document using a different digest or ECDSA would select a different
  `vk_sha256` bucket by the same mechanism as finding 3 above, untested here.
- **Phone (on-device) timing** for the M1b build — every measurement in §2 is on the Fedora 44
  x86_64 desktop, matching Q23's own scope limit.
- **Whether `certificate_registry_root` varies meaningfully in a real multi-trust-list deployment**
  — only one packaged-certs file was used for both documents in this run.
- **A larger corpus.** Six presentations (three per document) is enough to exercise same-site,
  cross-site and cross-document pairs once each, per the task's explicit plan — it is not enough to
  rule out a rare or probabilistic leak (e.g. a salt drawn from a narrower-than-expected range, or
  a circuit branch taken only for some inputs).
- **Bare mode-A (`evidence: []`) byte comparison.** Two bare baseline presentations were built (one
  per site) to exist alongside the zk ones, but no detector pass was run comparing them to each
  other in this session — the bare shape is fixed by `createVerifier`'s presentation envelope
  (`spec`/`tier`/`claim`/`challenge`/`evidence:[]`) and D19/D21 already require it carry no
  `zktag`/`chip_auth` at tier A; unlinkability of the bare shape specifically was not re-derived
  here.

## 7. Method findings

- **Q15/Q23's document-bucketing finding reconfirms at the wire-format level, not just the
  circuit-selection level.** Q23 already knew NL and US select different TBS-bucket circuits; this
  run shows that fact is visible to *any verifier* as a plain `vk_sha256` byte-equality check on
  the `dsc`/`id_data` stages, with no cryptographic work needed — the same information a
  circuit-selection argument implies, but now demonstrated as an observable wire property rather
  than inferred from source.
- **The byte-run detector's floor is format-dependent, not a fixed constant borrowed from 8een —**
  and the *first* number reported for it (271–300 B, §4c) was itself an artefact worth catching.
  Comparing whole buffers meant the floor measured was really "how big is the format's own constant
  region," a coincidence of scale, not the detector's true resolving power. Masking out everything
  constant across the whole 6-presentation corpus (§4d follow-up) dropped the measured floor from
  256–300 B to 16–32 B, close to 8een's own ~11 B. The lesson generalizes: a byte-run detector over
  a format with a large constant region needs that region excluded BEFORE the floor is trusted, or
  the number reported is really just "how big is the boilerplate," not a detection limit.
- **The masked floor still isn't fully clean, and that was reported rather than smoothed over.**
  The whole-presentation scope kept a 467–468 B match for NL-only pairs even after 6-way masking
  (§4d's caveat) — a second, narrower layer of constant structure (NL's own circuit-variant
  framing) a global 6-way mask cannot see, because it isn't universal across both documents, only
  universal within NL's. Reporting "floor: 16–32 B" without that caveat would have buried a real
  residual finding under one clean headline number.
- **A structural mislabeling was caught by reading `main.nr` directly, not by trusting the plug's
  own doc comment.** The plug's `[comm_in, comm_out]` shorthand for `dsc` reads as if `dsc` takes
  an incoming commitment; it does not — `dsc.field(0)` is `certificate_registry_root`, an
  unrelated public input. The label is fine for the plug's own binding logic (which only cares that
  `dsc.field(1)` chains to `id_data.field(0)`), but would have produced a wrong explanation in §5
  above ("comm_in is stable because commitments to a null input are stable") had the circuit source
  not been checked.
