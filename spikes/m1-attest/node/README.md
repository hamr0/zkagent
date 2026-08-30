# M1 attestation POC (Node stdlib only)

Riskiest assumption under test: **Android hardware key attestation
verification and decoding is implementable with Node ≥20 standard library
only, zero npm dependencies.** This directory is a throwaway spike — not
production code.

## Result (as observed, see "What was run" below)

Yes, for the subset implemented: chain verification (`node:crypto`'s
`X509Certificate.checkIssued`/`verify`) and decoding the vendor attestation
extension (a hand-rolled ~163-line DER walker, since `X509Certificate` does
not expose custom extension content) both worked against real Google
key-attestation chains with zero npm dependencies. 163 lines is a little over
the ~150-line aim for the DER walker, but not "well past" it — this is not
the Q14 trigger.

## Files

- `der.mjs` — minimal DER/BER-subset walker (SEQUENCE, SET, INTEGER,
  ENUMERATED, BOOLEAN, OCTET STRING, OBJECT IDENTIFIER, explicit
  context-specific tags). 163 lines. Not a general ASN.1 parser — only
  handles what a well-formed X.509 cert + KeyDescription extension need
  (definite-length DER, no indefinite lengths, no implicit context tags).
- `key-description.mjs` — decodes the Android `KeyDescription` structure
  (the attestation extension payload, OID `1.3.6.1.4.1.11129.2.1.17`) using
  `der.mjs`. Decodes: attestationVersion, attestationSecurityLevel,
  keymintVersion, keymintSecurityLevel, attestationChallenge, uniqueId,
  and from both AuthorizationLists: rootOfTrust (verifiedBootKey,
  deviceLocked, verifiedBootState, verifiedBootHash), osVersion,
  osPatchLevel, vendorPatchLevel, bootPatchLevel, and
  attestationApplicationId (tag 709 — package names/versions +
  signing-cert digests, itself DER-encoded inside an OCTET STRING).
- `decode-chain.mjs` — shared helper (parse PEM chain, locate + decode the
  leaf's attestation extension) used by both scripts below.
- `google-hardware-roots.mjs` — loads the pinned Google hardware
  attestation root certs and matches a cert against them by DER byte
  comparison (not name/PEM-string comparison).
- `verify-attestation.mjs` — CLI: verifies a chain end-to-end (links, root
  pin, per-cert date validity) and prints a JSON report. Accepts an
  optional `--at <ISO date>` to evaluate validity as of a fixed date
  instead of "now", for reproducible runs.
- `diff-chains.mjs` — CLI: field-by-field diff between two chains (two
  files, or two runs inside one delimited capture file).
- `extract-real-capture.mjs` — CLI: splits a raw logcat capture (the Pixel
  6a spike's output format) into leaf-first PEM chain files.

## Root certificates pinned

Fetched from
https://developer.android.com/privacy-and-security/security-key-attestation
(section "Root certificate"), 2026-08-29. Google publishes 5 self-signed
root certs there (RSA, one labelled `CN=Key Attestation CA1` which is an
older/legacy software-attestation root, and four unlabelled `serialNumber=`
roots covering different key generations). All 5 are pinned; a chain is
accepted if its top-of-chain cert byte-matches ANY of them. SHA-256 of each
fetched PEM file is recorded in a comment at the top of
`google-hardware-roots.mjs` and can be reproduced with
`sha256sum fixtures/public/google-hardware-roots/*.pem`.

**Caveat found during testing:** the `algorithm_EC_SecurityLevel_StrongBox`
test fixture from Google's own `android-key-attestation` repo roots to a
self-signed cert (`serialNumber=e35d38c6897d47e8`, RSA-4096, valid
2018–2028) that is NOT among the 5 roots currently published on the
developer.android.com page. This could mean that page's root list isn't
exhaustive/historical, or that this particular test fixture predates a root
rotation. Not resolved — flagged, not judged.

## Public test fixtures used (real, uncrafted data)

From `google/android-key-attestation` (Google's own reference
implementation's test resources), fetched from
`https://raw.githubusercontent.com/google/android-key-attestation/master/src/test/resources/pem/...`:

- `algorithm_RSA_SecurityLevel_TEE/cert{0..3}.pem` → concatenated into
  `fixtures/public/rsa_tee_chain.pem`
- `algorithm_EC_SecurityLevel_StrongBox/cert{0..3}.pem` → concatenated into
  `fixtures/public/ec_strongbox_chain.pem`

These are real certificate chains as emitted by real (or Google's reference
emulator) key attestation, not synthetic data authored for this spike.

## How to run

```
# Note: at today's date this fixture's root has expired (see "Gap found" below),
# so the default (now) run below is EXPECTED to report chainValid:false.
# Use --at with a pre-expiry date to see it verify.
node verify-attestation.mjs fixtures/public/rsa_tee_chain.pem
node verify-attestation.mjs fixtures/public/rsa_tee_chain.pem --at 2026-01-01
node verify-attestation.mjs fixtures/public/rsa_tee_chain.pem --at 2028-06-01   # planted negative (iii): expired
node verify-attestation.mjs fixtures/public/ec_strongbox_chain.pem
node diff-chains.mjs fixtures/public/rsa_tee_chain.pem fixtures/public/ec_strongbox_chain.pem
```

## What was observed

**`rsa_tee_chain.pem`: chain valid, root pinned, extension decoded.**
(This result predates the date-validity fix below — see "Gap found and
fixed" — and is stale as of today: `root_2.pem` is the same root that
expired 2026-05-24, so running this fixture through the current, date-
checking verifier at today's date now correctly reports `ok: false`.
Re-run with `--at 2026-01-01` or any pre-expiry date to reproduce the
result described below.)
`ok: true`, `chainValid: true`, root matched `root_2.pem`. Extension decoded
attestationVersion 3, TEE security level, keymintVersion 4, and (in
`softwareEnforced`) an `attestationApplicationId` listing 13 packages
including `android`, `com.android.settings`, etc., version 29, with one
signing-cert digest. `hardwareEnforced.rootOfTrust.verifiedBootState` =
`Unverified`, all patch levels `201907`. Full JSON captured in this run.

**`ec_strongbox_chain.pem`: chain invalid — real, unexpected finding.**
`ok: false`, `chainValid: false`. The link-by-index check
(`cert[0].checkIssued(cert[1])`) reported `false` even though
`cert[0].verify(cert[1].publicKey)` reported `true`. Investigated directly
with raw `node:crypto` calls (not via our code): `cert[0]`'s issuer *name*
field matches `cert[2]`'s subject (`checkIssued(cert2) === true`), but
`cert[0]`'s signature cryptographically verifies against `cert[1]`'s public
key (`verify(cert1.publicKey) === true`, `verify(cert2.publicKey) ===
false`). In other words: `cert[1]` and `cert[2]` in this Google-supplied
fixture are two different certificate *identities* (different serial
numbers) sharing the *same key pair* — a real-world case of intermediate
re-issuance under a new name without a new key. Our verifier implements the
task's specified algorithm exactly (linear index-based
`checkIssued`/`verify`), and that algorithm is not robust to this case: it
correctly refuses to call the chain valid, but for the "wrong" structural
reason (name mismatch) rather than a substantive one. **This is a design
question, not a bug** — see Questions below.

**Planted negative (i) — flipped leaf-signature byte: correctly invalid.**
Took the valid `rsa_tee_chain.pem`, located the leaf's `signatureValue` BIT
STRING in its raw DER via `der.mjs`, XORed one byte 10 bytes into the
signature content, re-PEM-encoded. Result: `ok: false`, `chainValid: false`,
link 0 reports `signatureValid: false` (link 1 and 2 unaffected, root still
pinned) — confirms the check is live and localizes to the tampered link.

**Planted negative (ii) — swapped root: correctly invalid.**
Took `rsa_tee_chain.pem`, replaced its trailing (root) cert with a
freshly-generated self-signed EC cert (`openssl ecparam`/`req -x509`,
`CN=Not Google Fake Root`). Result: `ok: false`, `chainValid: false`,
`rootPinned: null`, link 2→3 reports both `checkIssued: false` and
`signatureValid: false` (the fake root neither claims to be nor
cryptographically is Google's intermediate's issuer).

Both planted negatives fail for *different, specific* reasons (bad
signature vs. broken link + unpinned root), which is evidence the checks
are actually wired to the data rather than trivially always-false.

**Gap found and fixed: the verifier initially accepted an expired chain.**
`node:crypto`'s `X509Certificate.verify()` only checks the cryptographic
signature — it does not check `notBefore`/`notAfter` at all. The first
version of `verify-attestation.mjs` therefore reported `chainValid: true`
for a real device chain whose root certificate had already expired (see
"Real device results" below — this was caught on the StrongBox chains from
the Pixel 6a capture, not on a public fixture). Fixed by adding an explicit
per-cert date-validity check (`checkValidity()` in `verify-attestation.mjs`)
against a reference time — `Date.now()` by default, or a fixed date via
`--at <ISO date>` for reproducible runs — folded into `chainValid` and
reported per-cert as `validNow`/`validityReason`. The leaf's fixed
1970→2048 window (a Keystore placeholder, not a real claim) is not
special-cased; it simply evaluates as valid at any realistic `--at`, like
any other cert.

**Planted negative (iii) — evaluate past the leaf-issuer's expiry: correctly invalid.**
Ran `verify-attestation.mjs` on `rsa_tee_chain.pem` with `--at 2028-06-01`,
a date after the leaf-issuer cert's (chain index 1) `notAfter`. Result:
`ok: false`, `chainValid: false`, `reason: "one or more certs are not
valid at 2028-06-01T00:00:00.000Z: cert 1 (expired), cert 2 (expired), cert
3 (expired)"`; per-cert `chain[1].validNow: false`,
`chain[1].validityReason: "expired"` (cert 0, the leaf, still reports
`validNow: true` — its window runs to 2106). Exit code 1. This confirms the
date check actually fires and reports the specific reason, not just a
generic failure.

**`diff-chains.mjs`: functionally verified, but on two DIFFERENT devices,
not a same-device two-run pair.** We do not have two real captures from the
same device (see below), so `diff-chains.mjs` was smoke-tested against
`rsa_tee_chain.pem` vs `ec_strongbox_chain.pem` — two unrelated Google test
fixtures (different algorithm, different security level). Result: 44
identical fields, 21 differing fields, and the watchlist intersection
flagged 5 "linkability candidate" fields:
`extension.uniqueId` (both empty — expected, `uniqueId` is normally absent
unless the caller sets `DEVICE_UNIQUE_ATTESTATION`), `osPatchLevel`
(coincidentally both `201907` — these are Google's own dated test
fixtures, not evidence of anything), `osVersion` (both `0`), and
`rootOfTrust.verifiedBootKey`/`verifiedBootHash` (both fixtures use an
all-zero placeholder verifiedBootKey — again a test-fixture artifact, not a
real device signal). **This run only proves the diff mechanism itself
works (it correctly separated identical vs. differing fields, e.g. subject,
serial, and security level all correctly reported as differing).** It says
nothing about real device linkability — that requires two runs from one
physical Pixel 6a, which is the parallel capture agent's job.

**Real Pixel 6a capture: landed after this section was first written; see
"Real device results" below for the actual run against it.**

## Real device results (Pixel 6a, 2026-08-29 capture)

A real capture landed at `spikes/m1-attest/fixtures/real/attest-20260829T225858.txt`
(raw logcat, gitignored — never copied anywhere tracked). It contains 4
chains: `strongbox` run1/run2 (4 certs each) and `tee` run1/run2 (5 certs
each), each run using a freshly generated key. `extract-real-capture.mjs`
splits the logcat capture into 4 leaf-first PEM files
(`fixtures/real/{strongbox,tee}-run{1,2}.pem`) by stripping the logcat
prefix and reassembling each cert's base64 body. This section reports
field names and true/false outcomes only — no serial numbers, hashes, keys,
or other raw hex from the device appear here or anywhere in this repo.

### `verify-attestation.mjs` on all 4 real chains

**Link/root checks**: the strict linear `checkIssued`/`verify(issuerPublicKey)`
walk (per the standing decision: kept as-is, not loosened) succeeded
end-to-end for every link in all 4 chains, unlike the `ec_strongbox`
*public* test fixture discussed above. No link failures. All 4 chains'
top-of-chain certs matched a pinned root (StrongBox → root_2, TEE →
root_1).

**Date validity (default `--at` = now = 2026-08-29): StrongBox FAILS, TEE PASSES.**
Adding the date check (see "Gap found" above) changed the honest result for
2 of the 4 chains:

| | strongbox run1 | strongbox run2 | tee run1 | tee run2 |
|---|---|---|---|---|
| chainValid @ now (2026-08-29) | **false** | **false** | true | true |
| ok @ now | **false** | **false** | true | true |
| failing cert @ now | index 3 (root), expired | index 3 (root), expired | — | — |

Both StrongBox chains report `reason: "one or more certs are not valid at
<now>: cert 3 (expired)"`. The links and root-pin both still pass — it is
purely the date check catching this. This is the honest result: on this
Pixel 6a, right now, the StrongBox key-attestation chain does not verify,
because Google's own root cert for that hierarchy expired 2026-05-24 (see
below) and this device has not been re-provisioned since.

**Date validity at `--at 2026-05-01` (before the StrongBox root's expiry): StrongBox PASSES, TEE now FAILS instead.**

| | strongbox run1 | strongbox run2 | tee run1 | tee run2 |
|---|---|---|---|---|
| chainValid @ 2026-05-01 | true | true | **false** | **false** |
| failing cert @ 2026-05-01 | — | — | index 1 and 2, not yet valid | index 1 and 2, not yet valid |

This is not a bug — it's the same honest per-cert dating applied
consistently. The TEE chain's cert 1 and cert 2 (see below) are short-lived
RKP-provisioned certs issued in mid/late-2026, so they postdate
2026-05-01 and correctly report `not yet valid` rather than `expired`.
There is no date under which all 4 real chains simultaneously verify,
because the StrongBox root's validity window (2016–2026-05-24) and the TEE
chain's provisioning window (mid-2026 onward) don't overlap on this
device's current cert set.

Decoded extension fields (all present/expected, unaffected by the date
finding — extension decoding runs independently of validity outcome):

| | strongbox run1 | strongbox run2 | tee run1 | tee run2 |
|---|---|---|---|---|
| pinned root matched (index) | root_2 | root_2 | root_1 | root_1 |
| attestationSecurityLevel | StrongBox | StrongBox | TrustedEnvironment | TrustedEnvironment |
| keymintSecurityLevel | StrongBox | StrongBox | TrustedEnvironment | TrustedEnvironment |
| attestationChallenge present | true | true | true | true |
| attestationApplicationId package | present, 1 package | present, 1 package | present, 1 package | present, 1 package |
| attestationApplicationId signing digest present | true | true | true | true |
| rootOfTrust.deviceLocked | true | true | true | true |
| rootOfTrust.verifiedBootState | Verified | Verified | Verified | Verified |
| osVersion / osPatchLevel present | true / true | true / true | true / true | true / true |
| vendorPatchLevel / bootPatchLevel present | true / true | true / true | true / true | true / true |

(Root index numbering matches `fixtures/public/google-hardware-roots/root_{0..4}.pem`,
per the standing decision to pin only currently-published roots — not
changed.)

### Certificate provisioning pattern (dates only, no hex identifiers)

- **StrongBox chain, certs 1–2** (the two intermediates between the leaf
  and the root): validity window **2020-09-11 → 2030-09-09** — long-lived,
  factory-provisioned certs. Byte-identical across strongbox run1 and
  run2 (confirmed via `diff-chains.mjs`, see below).
- **StrongBox chain, cert 3** (root): validity window **2016-05-26 →
  2026-05-24** — this is the cert that is now expired (today is
  2026-08-29), and it byte-matches pinned `root_2.pem`.
- **TEE chain, cert 1**: an `O=TEE` cert with a **13-day validity window**
  (issued late-August 2026, expiring in early September 2026) —
  consistent with Google's Remote Key Provisioning (RKP) issuing short-lived,
  per-device/per-provisioning-period certs rather than long-lived factory
  certs. Also byte-identical across tee run1 and run2, despite the short
  window — i.e. both runs were captured within the same ~13-day
  provisioning period, so they share the same RKP-issued cert rather than
  each fetching a fresh one.
- **TEE chain, certs 2–4**: longer-lived Google-issued intermediates/root,
  also byte-identical across the two TEE runs.

### `diff-chains.mjs`: same-device, fresh-key comparisons (the real risk-#8 test)

**strongbox run1 vs run2** (same device, two fresh StrongBox keys):
40 fields identical, only 1 differs (`extension.attestationChallenge` — the
nonce we supplied per run, expected to differ). Every other decoded field,
including all intermediate-chain metadata and the entire hardware-enforced
authorization list, was byte-identical across two independent key
generations. Fields identical across runs **and** on the linkability
watchlist (plausibly device-unique, not just artifacts of parsing a fixed
constant):
- `chain.1.serialNumber`, `chain.1.validFrom`
- `chain.2.serialNumber`, `chain.2.validFrom`
- `chain.3.serialNumber`, `chain.3.validFrom`
- `extension.hardwareEnforced.rootOfTrust.verifiedBootKey`
- `extension.hardwareEnforced.rootOfTrust.verifiedBootHash`
- `extension.hardwareEnforced.osVersion`
- `extension.hardwareEnforced.osPatchLevel`
- `extension.hardwareEnforced.vendorPatchLevel`
- `extension.hardwareEnforced.bootPatchLevel`
- `extension.uniqueId` (both empty — not itself evidence, `uniqueId` is
  only populated when the caller requests `DEVICE_UNIQUE_ATTESTATION`,
  which this spike did not)

**tee run1 vs run2** (same device, two fresh TEE keys): same pattern — 46
fields identical, only 1 differs (`extension.attestationChallenge`).
Identical + watchlisted fields: the same set as above
(`chain.{1,2,3}.serialNumber`/`validFrom`, `verifiedBootKey`,
`verifiedBootHash`, `osVersion`, `osPatchLevel`, `vendorPatchLevel`,
`bootPatchLevel`, `uniqueId`), plus `chain.4.serialNumber`/`validFrom`
(the TEE chain has one more intermediate than StrongBox's).

**strongbox run1 vs tee run1** (same device, different security level):
20 identical / 27 differing. Notably, `verifiedBootKey`, `verifiedBootHash`,
and all four patch-level fields are identical **even across security
levels on the same device** — i.e. these are not per-security-level
artifacts, they read the same underlying device state regardless of
whether the key path was StrongBox or TEE. The chain certs themselves
(subject/issuer/serial/validity beyond `chain.0`) differ, as expected —
StrongBox and TEE have separate certificate hierarchies.

**Reading these results**: this script does not judge linkability — it
lists what's identical. That `verifiedBootKey`, `verifiedBootHash`, and the
four patch-level fields are stable across both fresh keys *and* across
security levels on one physical device is the concrete, real-device
evidence PRD risk #8 asked for: these fields are plausible persistent
device identifiers, independent of which key/security-level generated the
attestation. Intermediate-cert serial numbers/validity being stable across
runs is expected (they identify a batch of provisioned device certs, not a
single key) but are listed for completeness since they are still
device-correlated.

## Questions for the orchestrator (design decisions, not mine to make)

1. **Chain-linking algorithm robustness.** The spec asked for linear
   index-based `checkIssued`/`verify(issuerPublicKey)`. A real Google test
   fixture (`ec_strongbox`) shows that assumption can break when an
   intermediate is re-issued under a new identity but the same key — the
   file's linear PEM order is not always a valid issuance chain by name,
   even though cryptographic delegation still holds via key reuse. Options:
   (a) keep strict linear verification (current behavior) and treat any
   such fixture/device as simply "chain invalid" — safe but may reject real
   valid device attestations that have this same pattern; (b) make chain
   verification try alternate certs in the file by public key (not just
   index+1) when the immediate index-based link fails; (c) something else.
   This changes what counts as "verified" for M2+ and I should not decide
   it unilaterally.
2. **Root list currency.** The `ec_strongbox` fixture's root isn't in
   today's published root list. Should the pinned set also include known
   historical/deprecated roots (and if so, from where — Google doesn't
   publish a deprecation list on that same page), or is "only currently
   published roots" the intended policy?
