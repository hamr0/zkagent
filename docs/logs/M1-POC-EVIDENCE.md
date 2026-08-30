# M1 POC evidence — hardware key attestation on the Pixel 6a (2026-08-29)

**Rule for this file (PRD v1.5)**: no PII values, ever. Field names, booleans, counts, timings
and validity windows (dates) only. Device serials, certificate serials, hashes, hex identifiers,
document numbers, names and dates of birth appear nowhere in this repo.

---

## Setup

Device: Pixel 6a, stock Android 17. Probe: `spikes/m0/.../M1AttestProbe.kt` (button in the M0
spike app), Keystore EC P-256 key with a fresh 32-byte attestation challenge, two configurations
(StrongBox, TEE), two runs each, chains dumped via logcat and `spikes/m0/capture-attest.sh` into
gitignored `spikes/m1-attest/fixtures/real/`. Verifier: `spikes/m1-attest/node/` — Node stdlib
only (`node:crypto`, `node:fs`), zero npm dependencies, DER walker 163 lines. Public development
fixtures from google/android-key-attestation (URLs in the spike README). Everything in this doc
was observed in a run; nothing is inferred.

## Pre-registered failure modes

Written before the run, from the orchestrator's brief:

(a) stdlib cannot decode the attestation extension → a dependency is forced, Q14 decided by
evidence.
(b) device yields software-level attestation only.
(c) the chain contains a device-unique intermediate → PRD risk #8 is real.

## Findings

1. StrongBox available on the device (`StrongBoxUnavailableException` not thrown, both runs). Key
   generation timings measured: StrongBox 146 ms / 98 ms, TEE 142 ms / 36 ms. Chain lengths:
   StrongBox 4, TEE 5.

2. All four real chains verified by the stdlib verifier: signatures valid link by link with a
   strict linear walk, root matched a pinned Google hardware root (StrongBox chains → one pinned
   root, TEE chains → a different pinned root). Extension decoded fully:
   attestationSecurityLevel/keymintSecurityLevel = StrongBox resp. TrustedEnvironment;
   attestationChallenge present and equal to the one issued; attestationApplicationId present
   with package name and signing-cert digest (the FR10 identity); rootOfTrust.deviceLocked =
   true; verifiedBootState = Verified; os/vendor/boot patch levels present. → Failure modes (a)
   and (b) did not occur. Risk #4 (implementable within dependency rules) holds for parsing and
   signature verification.

3. Planted negatives on public fixtures: (i) one byte flipped in leaf signature → invalid,
   `signatureValid:false` on link 0; (ii) root swapped for a self-generated key → invalid,
   `rootPinned:null`. Both fired for distinct reasons.

4. Public StrongBox fixture from Google's own repo rejected by the strict walk: an intermediate
   was re-issued under a new subject with the same key, so leaf.issuer matches cert 2 not cert 1.
   Orchestrator decision: keep the strict walk; over-rejecting is the safe direction. Not observed
   on the real Pixel chains.

5. **Linkability (risk #8) — confirmed on this device.** diff-chains, same config, fresh keys:
   StrongBox run1 vs run2 → 40 fields identical, 1 differs (attestationChallenge, our own nonce);
   TEE run1 vs run2 → 46 identical, 1 differs (same). Identical across runs and device-correlated:
   every intermediate cert's serialNumber and validity; rootOfTrust.verifiedBootKey;
   verifiedBootHash; osVersion; osPatchLevel; vendorPatchLevel; bootPatchLevel. Cross-config
   (StrongBox vs TEE, same device): verifiedBootKey, verifiedBootHash and all four patch-level
   fields identical; the certificate hierarchies differ.

6. Nature of the stable intermediates (from certificate subjects and validity windows, values
   withheld): the TEE chain's cert 1 is an `O=TEE` certificate with a 13-day validity window
   (2026-08-24 → 2026-09-06) — the shape of a Remote Key Provisioning per-device attestation
   certificate; above it `Droid CA3` (~2 months), `Droid CA2` (~3 years), `Key Attestation CA1`
   (10 years). The StrongBox chain's certs 1–2 are `title=StrongBox` certificates valid 2020-09 →
   2030-09 — the shape of a factory-provisioned per-device keybox. Both are byte-identical across
   runs. → Failure mode (c) occurred: **the raw attestation chain is a device identifier, on both
   security levels.** Consequence for the design is the owner's decision (recorded as open in the
   PRD, see below), not this doc's.

7. Verifier gap found by reading, not by test: the StrongBox chain roots in the 2016→2026 Google
   root whose notAfter is 2026-05-24 — expired at run time — and the verifier reported it valid
   because `X509Certificate.verify()` checks signatures only. Fixed with an explicit per-cert
   date-validity check against a reference time (`Date.now()` by default, or a fixed date via
   `--at`), folded into `chainValid`. Default (now, 2026-08-29): both StrongBox chains fail (root
   expired 2026-05-24), both TEE chains pass. `--at 2026-05-01`: StrongBox passes, TEE fails
   ("not yet valid", 13-day RKP cert issued late Aug 2026) — no single date validates all four.
   Planted negative (iii) on a public fixture evaluated past the issuer's notAfter → invalid,
   reason expired, fired. **Open for M1 core, not decided:** policy for a chain rooted in a
   pinned-but-expired Google root (StrongBox on this device today). Whether Google's own verifier
   ignores root expiry is unverified.

8. Privacy defect in the M0 spike app, observed while driving the phone: the MRZ input fields
   (document number, expiry, date of birth) were pre-filled on launch — the upstream app persists
   them in preferences, contradicting the M0 row's "typed by hand, never stored". Harmless on the
   owner's own device; must not survive into M2 (rewrite, not graduate).

## Method findings

The two operator errors that cost time — the debug flavour's launcher is `.RegularActivity`, not
`.MainActivity`; and the first tap produced an NFC read (a document was near the phone) instead of
the probe, seen in logcat as `TECH_DISCOVERED` → `ResultActivity` — both recorded so the next run
doesn't repeat them. Also: `fixtures/real/` in `.gitignore` is root-anchored (contains a slash)
and did not cover the nested spike path; one explicit line was added and verified with
`git check-ignore`.

## NOT established

Whether Play Integrity has the same linkability shape (not captured); attestation
revocation-list handling (Q14c); keybox-extraction status (Q14a); whether any
coarsening/stripping of the chain can preserve verifiability — none of this was run. Mode-A
unlinkability as a whole (M1b) remains open; this doc shows the attestation half of the payload
fails it as-is.

## Decisions taken this session

Orchestrator, before the run: masterlist/SOD checking is the phone's job, not the verifier's —
the SOD never leaves the device, so the verifier trusts the attested client (FR10); the M1 row's
"half-loaded masterlist" negative moves to M2. M1 was re-sequenced to POC the riskiest assumption
(attestation) first instead of the verdict/nonce code.
