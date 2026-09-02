# Session Stash — M1 Q23 spikes: ZK proof vs Play Integrity (2026-08-30)

Project: zkagent · Owner: hamr · Host: Fedora 44

Continuation of the M1 POC session (see `m1-poc-attestation-q23.md`). Owner
rule #2 stands: the main session orchestrates only — no coding, no doc
edits; Sonnet agents code/doc and escalate every decision to the
orchestrator.

## Summary

The owner challenged the attestation design ("why all these certs? why not
a random id every time?"). Outcome: D22 — tier A same-site unlinkability is
relaxed to a non-goal; the actual requirement is "nothing stable across
sites." Q23 was reframed around that. Two spikes ran the same night against
the owner's real Pixel 6a and real documents: (3) Play Integrity — passes
the cross-site bar; (5) a ZK proof over the passport using zkPassport
circuits — full DSC→SOD→DG1→age composition verifies offline on desktop for
both documents. The Q23 decision is still the owner's to make; choosing (5)
would reverse D1 (no ZK in v1). The earlier M1 POC session's commit stands
as committed; everything from 2026-08-30 is uncommitted.

## State of the repo

All uncommitted, on top of the prior M1 POC commit:

- `docs/archive/zkagent-prd.md` → v1.8 (draft): D22 recorded; D19 tier-A
  wording changed to "this site learns one fact and nothing that follows
  you to other sites"; FR9 plus the M1b criterion "stable across sites"
  added; Q23 rewritten with options 1–5 and an evidence paragraph; risk #1's
  "unpublished" claim withdrawn; Q14(d) pinned-but-expired-root sub-item;
  version history updated.
- `docs/product/zk-challenges.md` (new, 188 lines): the full argument —
  8een vs zkagent, identifiable-vs-linkable axes, what the certs are for,
  the M1 measurements, two possible ZK roads, three honestly-named product
  tiers (honor-grade / voucher-grade / math-grade), Play Integrity explained
  in plain terms, a note on iOS/other manufacturers (marked UNVERIFIED), the
  decision plus pre-registered success criteria, and the claims the project
  may make.
- `docs/logs/M1-Q23-EVIDENCE.md` (new, roughly 270+ lines): both spikes,
  part 1 (each spike alone) and part 2 (the full composition).
- `docs/index.md`, `docs/log.md` regenerated.
- `.claude/remember/AGENT_RULES.md` shows a large uncommitted rewrite that
  no agent made this session (PRD-as-portal wording; the guardrails section
  removed) — presumed to be the owner's own edit or their tooling; left
  untouched; not yet confirmed with the owner.
- `spikes/m0`: `M1IntegrityProbe.kt` plus a new "M1 integrity probe" button;
  `M1SodProbe.kt` (writes the raw DG1/SOD plus a `.sod.txt` algorithm report
  to the app's files dir; wired into the read task after the SOD read);
  `build.gradle.kts` adds the Play Integrity library (plus three transitive
  artifacts: play-services-basement, play-services-tasks, core-common); a
  BuildConfig field sourced from a gitignored `local.properties` key holding
  the cloud project number; `AndroidManifest` gains the INTERNET permission;
  new `capture-integrity.sh` and `pull-fixtures.sh` scripts.
- `spikes/m1-integrity/node/`: `decode-tokens.mjs` (stdlib-only RS256 JWT
  decoding → OAuth → decodeIntegrityToken; the service-account key path is
  read from an environment variable), `diff-verdicts.mjs`, and a `README.md`
  with the real results. `fixtures/real/` is gitignored (the capture plus
  four decoded JSON verdicts).
- `spikes/m1-zk/`: `README.md` (toolchain, input format, NL/US results, full
  composition results, and a list of what was NOT run); `run/` scripts 01–09
  covering loading the passport, building inputs, masterlist
  extraction/root, and the DSC/integrity/age inputs plus negatives, and a
  `run/masterlist/extract_certs.py`; `vendor/` (gitignored clones of the
  zkPassport circuits repo and, as an unused fallback, the Rarimo passport
  ZK circuits repo, each pinned to a specific commit); `out/` gitignored;
  `fixtures/real/` gitignored (the DG1/SOD/algorithm-report files for both
  documents). The toolchain is installed rootless under the owner's home
  directory (Noir, Barretenberg, and a Noir home cache); the shell profile
  has a cosmetic duplicate NARGO_HOME block left by the Noir installer.
- `.gitignore` gained entries for the two spikes' `fixtures/real/`
  directories, `spikes/m1-zk/vendor/`, `spikes/m1-zk/out/`, and
  `spikes/m1-zk/run/node_modules/`.
- Google Cloud: a free project (its id lives only in the owner's key file)
  with the Play Integrity API enabled and a service account with Editor
  role; the key JSON lives at `~/secrets/<file>` (mode 600) — it was briefly
  at the repo root, untracked, never committed, and was moved out
  immediately. It must never go back in the repo root.

## Key decisions

- D22 (owner): tier A's same-site unlinkability requirement is relaxed to a
  non-goal; the requirement is that nothing stay stable across sites.
- Owner ran the Play Integrity spike "to get it out of the way," with the ZK
  spike as the primary line of investigation.
- `zk-challenges.md` was written as the owner's own reference document on
  the topic.
- Orchestrator decisions made on agent escalations: zkPassport as the
  primary circuit library, with Rarimo kept only as an unused fallback;
  strict certificate-chain walk and current roots only in the attestation
  verifier; standard (not classic) Play Integrity requests; the spike's ZK
  nullifier secret fixed at a placeholder value for testing; the CSCA
  masterlist sourced from the real BSI list rather than a stand-in.

## Findings

- Document SOD profiles (field names/algorithms/sizes only, no identifiers):
  NL ID card — data-group digests SHA-256, SignerInfo SHA256withRSA, DS key
  RSA-2048, CSCA NL (RSA-4096), data groups 1/2/3/14/15 present, DG1 95
  bytes, SOD 2236 bytes. US passport — SHA-256 digests, SignerInfo
  rsaEncryption (PKCS#1 v1.5 with SHA-256), DS key RSA-2048, CSCA U.S.
  Department of State MRTD CA (RSA-4096), data groups 1/2/11/12 present, DG1
  93 bytes, SOD 2683 bytes. Both documents sit on the most-supported circuit
  profile; an earlier survey's guess that NL uses ECDSA in the SOD was
  wrong — ECDSA is only used for the Active Authentication key, not the SOD
  signature.
- Play Integrity (Pixel 6a): four standard tokens captured, decode latency
  11–18 ms, token size 555 bytes, all four tokens distinct; all decoded
  successfully by Google; device integrity verdict MEETS_DEVICE_INTEGRITY;
  app recognition verdict UNRECOGNIZED_VERSION (expected — sideloaded); app
  licensing verdict UNEVALUATED; the fields stable across sites are limited
  to package name, signing certificate digest, version code, and the verdict
  enums — no device-unique field is present (deviceAttributes,
  recentDeviceActivity, deviceRecall, and environmentDetails were all
  absent). Limits observed: tested on one device/build/session only;
  per-device opt-in settings must stay off; Google decodes every check
  server-side; decode latency at scale was not measured; behavior on a
  de-Googled device is untested.
- ZK (zkPassport circuits, run on the desktop Fedora machine): the
  signature-check circuits compile — RSA-2048 about 2 seconds warm;
  ECDSA-P256 about 18 seconds with a soundness warning from the underlying
  big-curve library, which is moot since both real documents use RSA.
  Turning the raw DG1 and SOD into circuit inputs required under 100 lines
  of glue code using the zkPassport passport-reader utility. Per document,
  the composition measured: id-data circuit execute 2.1 s / 389 MB, prove
  1.3 s / 203 MB, verify true; DSC→CSCA circuit (RSA-4096, larger
  "to-be-signed" size for the US CSCA than the NL one) about 4.6 s / 545 MB,
  verify true; integrity circuit true; age-at-least-18 circuit (evaluated
  against 2026-08-30) true; totals roughly 16.1 s wall time and 546 MB peak
  memory (the age circuit was the peak), with four independent proofs
  totalling 59,072 bytes (four proofs of 14,656 bytes each plus public
  inputs) and no recursion. Commitments matched across every circuit
  boundary. Confirmed to run fully offline. Freshness check: two proofs
  generated from identical inputs are byte-different from each other, and
  both verify. Nullifier behavior: stable within one verification scope,
  different across scopes, and different across the two documents.
  Negative-test results: flipping a bit in the SOD signature makes the
  signature-check circuit fail; using the wrong verification key fails;
  flipping a bit in DG1 does NOT fail the signature-check circuit (DG1 is
  unconstrained there) but DOES fail the integrity circuit; for the age
  circuit, setting the minimum age to 200 hits an internal assertion
  (minimum age must be under 100), setting it to 90 hits the real
  comparison assertion as expected, and a birth year of 1990 fails as
  "Document is expired" due to an MRZ two-digit-year quirk.
- Library survey (as cited in `zk-challenges.md`): zkPassport is
  Apache-2.0-licensed, built in Noir, uses a universal setup, verifies
  chain-free, has a published mobile proving time of 10–50 seconds, has
  roughly 8 contributors, has not been audited, and its mobile app is not
  open-sourced; its nullifier uses an OPRF via a threshold network. Rarimo
  is MIT-licensed, built in Circom with Groth16, was audited by Halborn in
  2024, maintains its own L2 registry, and has no downloadable proving key.
  Self carries a NOASSERTION license and depends on Celo plus a Google TEE,
  which rules it out. longfellow-zk is not established for passports.
- Attestation (carried over from the 2026-08-29 session): the raw
  certificate chain is a global per-device identifier on both the StrongBox
  and TEE paths; the StrongBox chain's root in Google's published list
  expired 2026-05-24.

## Open items / next steps

- Q23 decision is still the owner's: voucher-grade (option 3, Play
  Integrity) versus math-grade (option 5, ZK proof); choosing (5) would
  reverse D1. The evidence supports both as technically feasible; option
  (5) matches the owner's stated goal of "zk, non-central."
- If (5) is chosen: run a phone-side proving spike on the Pixel 6a to get a
  real time/RAM figure for the on-device user experience; evaluate the
  dependency on the OPRF nullifier network; look at recursive proof
  aggregation; run the full dependency checklist against zkPassport (audit
  status, contributor count, closed-source mobile app); the chip-cloning
  question (Q18) remains open since the proof is over static signed data;
  tier B would use the nullifier.
- If (3) is chosen: build M1 core with Play Integrity as the attestation
  mechanism; measure decode latency at realistic scale; decide the
  de-Googled-device policy (a new Q24 was proposed in conversation but not
  yet formally recorded); keep per-device opt-in settings off.
- Also proposed but not yet formally recorded: Q24 (de-Googled devices have
  no D22-compliant voucher route); a note under D2 flagging Apple's App
  Attest as an unverified analog on iOS.
- The MRZ-persistence defect found in the M0 spike app (fields pre-filled
  on launch) is still open and must be removed before M2.
- The uncommitted rewrite of `AGENT_RULES.md` still needs confirmation from
  the owner.
- Commit only when the owner asks; nothing from 2026-08-30 is committed;
  any push to `main` is an admin bypass of branch protection — flag it
  every time.
- `zkagent@0.0.0` npm package is still mislicensed as MIT.

## Gotchas

- Owner rule #2: the orchestrator session never codes or edits docs itself.
  Agent prompts must be self-contained since fresh agents have no
  conversation context; continue a running agent with SendMessage rather
  than starting a new one.
- Never write PII or hex identifiers into docs, READMEs, or reports.
- Real captures parse differently from synthetic test data (logcat line
  prefixes, an extra wrapper layer around the token payload) — always run
  the parsers against real captures, not just synthetic fixtures.
- The phone drops off `adb` when its screen locks; the launcher activity is
  `.RegularActivity`; an NFC read fires automatically whenever a document
  is held near the phone. Both probes share the same M0 app build, so phone
  work must be sequenced rather than run in parallel.
- `grep` on this host is ugrep 7.5.0.
- Use absolute paths — the shell's working directory can drift between
  commands.
- Regenerate the docs index after any doc edit with the docs-builder
  index-flat command.

## Recovery commands

Build/install as in the earlier M1 POC session's stash (Java and Android
SDK environment variables set, `./gradlew --no-daemon :app:assembleRegularDebug`
run from `spikes/m0`, then `adb install -r` the resulting debug APK, then
launch `.RegularActivity`).

Play Integrity spike: tap "M1 integrity probe" on the phone, then run
`./spikes/m0/capture-integrity.sh`, then run
`spikes/m1-integrity/node/decode-tokens.mjs` against the capture with the
service-account key path (`~/secrets/<file>.json`) passed via its
environment variable, then run `spikes/m1-integrity/node/diff-verdicts.mjs`
against the resulting decoded JSON files.

Passport raw capture: tap the document to the phone, then run
`./spikes/m0/pull-fixtures.sh`.

ZK spike: see `spikes/m1-zk/README.md`; run the numbered scripts in
`spikes/m1-zk/run/` in order (01 through 09); the Noir and Barretenberg
toolchains are installed rootless under the owner's home directory.

## Addendum (later on 2026-08-30)

D23 taken: voucher-grade v1 (Play Integrity), D1 stands, Track Z opened as a
named second track over the passport gated on five conditions: (1) engine
stable at a release with a published core audit; (2) an independent audit of
the exact four circuits zkagent would use; (3) measured phone proving time
under a UX ceiling the PRD sets at that time; (4) a chain-free nullifier path
with known operators, or none; (5) an open-source on-device prover. Q23
resolved by D23; Q24 added (de-Googled devices excluded in v1). PRD bumped to
v1.9 (draft).

`docs/product/zk-due-diligence.md` written: engine is pre-1.0 with a
2026-03 forged-proof soundness bug plus a second critical in 2026-07, no
published UltraHonk core audit; zkPassport was acquired by Aztec on
2026-05-27, has no published audit and a closed mobile app; Rarimo was
audited in 2024 but has changed since; no non-crypto organisation has a
production ZK-over-passport precedent; the EU scopes ZK to wallet
credentials, not passports directly.

`docs/product/zk-challenges.md` §12-16 added: three layers (who builds what),
the names sorted, the D23 decision, the longfellow question plus its answer,
and where everything lives. Phone proving time was NOT obtained this session
— no Android `bb` binary exists, only a static library; building an NDK
harness is Track Z's first task. WASM proving measured at roughly 2.2x
native time and roughly 1.2x native memory. longfellow-zk has no RSA or
ASN.1 support — confirmed not a shortcut around Barretenberg.

Owner's standing concern recorded as-is, not resolved: v1 depends on Google
(and later Apple) for attestation — accepted openly for v1, and reversible
if Track Z's gates are met.

Memory file `q23-decision-voucher-v1-zk-gated.md` written.

Commits: `c3ee545` (Q23 spikes). Uncommitted since that commit:
`zk-due-diligence.md`, PRD v1.9, `zk-challenges.md` §12-16, the
`spikes/m1-zk/` phone spike files, and a `.gitignore` line for
`spikes/m1-zk/phone/dl/`.

Next step: M1 core (verdict classifier, nonce/D20 signed challenges, tier
negotiation, FR10 trust-list check, Play Integrity wired through a pluggable
attestation interface) — the owner's earlier "M1" is the trigger word to
start it. The AGENT_RULES.md rewrite is still unconfirmed by the owner.
