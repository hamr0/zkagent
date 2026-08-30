# M1 Play Integrity decode POC (Node stdlib only)

Riskiest assumption under test: **decoding a Play Integrity standard token
(OAuth2 JWT-bearer auth + the `decodeIntegrityToken` call) is implementable
with Node >=20 standard library only, zero npm dependencies**, mirroring what
`spikes/m1-attest/node/` already proved for key-attestation chains. This
directory is a throwaway spike — not production code (AGENT_RULES: never
ship the POC).

**No capture existed at build time.** `spikes/m0/capture-integrity.sh` /
`M1IntegrityProbe.kt` produce the capture file these scripts read, but that
requires running the app on the Pixel 6a with a Play Console-linked Cloud
project number, which had not happened yet. Everything below was first
verified against a self-generated throwaway key and hand-made JSON only —
see "What was verified without a real capture". A real capture was decoded
successfully the next day — see "Real device results (2026-08-30)" at the
bottom for that run (values redacted: no hashes/digests/tokens).

## Files

- `decode-tokens.mjs` — reads a capture file (see format below), reassembles
  each token from its 64-char logcat chunks, builds and RS256-signs a JWT
  with `node:crypto`, exchanges it for an OAuth2 access token, calls
  `decodeIntegrityToken` for each token, and writes/prints the decoded
  verdicts.
- `diff-verdicts.mjs` — field-by-field diff between two or more decoded
  verdict JSON files (flattened dotted paths), same output shape as
  `spikes/m1-attest/node/diff-chains.mjs`. Lists identical vs. differing
  fields; does not judge. `requestDetails.requestHash` and
  `requestDetails.timestampMillis` are marked `expectedToDiffer` but still
  reported like every other field.

## Capture file format

Produced by `spikes/m0/capture-integrity.sh` via `M1IntegrityProbe.kt`
(delimited `logcat -s M1Integrity` text):

```
===== M1 INTEGRITY REPORT BEGIN =====
--- site=site-a run=1 summary ---
request_hash_b64: <b64>
request_ms: <n>
token_length: <n>
token_sha256: <b64>
error: none
--- site=site-a run=1 raw_token (still-encrypted JWE) ---
token_chunk site=site-a run=1 idx=0: <<=64-char chunk>
token_chunk site=site-a run=1 idx=1: <<=64-char chunk>
...
--- site=site-a run=2 summary ---
...
===== SUMMARY =====
site=site-a run=1 request_ms=... token_length=... token_sha256=... error=none
...
===== M1 INTEGRITY REPORT END =====
```

Two simulated "sites" (`site-a`, `site-b`), two runs each = 4 tokens. The
capture does not currently include a `package_name:` line, so
`decode-tokens.mjs` falls back to the hardcoded package name
`com.tananaev.passportreader` (overridable with a second CLI argument, and
a `package_name:` line in the capture takes priority if one is ever added).

## How to run

### 1. App side — get a real capture

1. In `spikes/m0/app/local.properties` (gitignored), set
   `m1.integrity.cloudProjectNumber=<your Cloud project number>` — the
   **project number**, not the project id, of the Google Cloud project
   linked to this app in Play Console (Play Console > your app > Setup >
   App integrity > Play Integrity API). `M1IntegrityProbe.kt` reads this via
   a `BuildConfig` field and refuses to call the API if it's absent.
2. Build/run the app on a device, trigger the probe, then run
   `spikes/m0/capture-integrity.sh` to pull the delimited report from
   logcat into `spikes/m1-integrity/fixtures/real/integrity-<timestamp>.txt`
   (gitignored).

### 2. Decode side — this directory

1. Enable the **Play Integrity API** on the same Cloud project (Cloud
   Console > APIs & Services > Enable APIs and services > search "Play
   Integrity API" > Enable). Verified against
   https://developer.android.com/google/play/integrity/setup (2026-08-29).
2. Create a service account in that Cloud project — either via Cloud
   Console (IAM & Admin > Service Accounts > Create Service Account) or
   `gcloud iam service-accounts create`, per
   https://cloud.google.com/iam/docs/service-accounts-create (2026-08-29).
   Download a JSON key for it (Keys > Add Key > Create new key > JSON).
   Google's own Play Integrity docs at
   https://developer.android.com/google/play/integrity/standard
   (2026-08-29, section "Decode the integrity verdict") say only: "Create a
   service account within the Google Cloud project that's linked to your
   app," then "fetch the access token from your service account credentials
   using the `playintegrity` scope" — they do not name a specific IAM role
   for this call. In practice this means: the service account must belong
   to the Cloud project that Play Console has linked to the app (that
   linkage, not a granted IAM role, is what authorizes `decodeIntegrityToken`
   for that package); no additional Play Console-side grant was found
   documented. If Google later requires or recommends a specific IAM role,
   treat that as more authoritative than this note.
3. Never commit the downloaded key file. Point `M1_INTEGRITY_SA_KEY` at its
   path (e.g. `~/.secrets/zkagent-m1-integrity-sa.json`, outside the repo):
   ```
   M1_INTEGRITY_SA_KEY=/path/to/sa-key.json node decode-tokens.mjs \
     ../fixtures/real/integrity-<timestamp>.txt
   ```
   Decoded verdicts are written to
   `spikes/m1-integrity/fixtures/real/decoded-<site>-<run>.json`
   (gitignored) and printed to stdout.
4. Diff two (or more) decoded verdicts:
   ```
   node diff-verdicts.mjs ../fixtures/real/decoded-site-a-1.json \
     ../fixtures/real/decoded-site-b-1.json
   ```

## What was verified without a real capture

No real Play Integrity token or service-account key existed at build time,
so full end-to-end decoding is **not yet verified against Google's real
API** — only the parts reachable without them:

1. `M1_INTEGRITY_SA_KEY` unset: `decode-tokens.mjs` refuses cleanly with a
   one-line instruction and exit code 1, without touching the network.
2. `M1_INTEGRITY_SA_KEY` set to a self-generated throwaway RSA key wrapped
   in a fake service-account JSON: the script built a well-formed JWT
   (RS256 header + iss/scope/aud/iat/exp claims printed to stderr for
   inspection), then failed cleanly at the OAuth2 token-exchange HTTP call
   with a clear `HTTP <status> ... ` error from Google (the fake key/email
   is naturally rejected — this proves the JWT-building and HTTP-call code
   paths work, not that decoding works).
3. `diff-verdicts.mjs` run on two hand-made JSON files to confirm the
   identical/differs/crossSiteStable output shape.

Exact commands and output are recorded in the coding-session report, not
duplicated here to avoid this file going stale.

## Real device results (2026-08-30)

First run against a real capture (`integrity-20260830T031226.txt`, 4 tokens:
`site-a` run1/run2, `site-b` run1/run2) surfaced one bug: the capture is a
raw `tee`'d `adb logcat` dump, so every line carries a logcat prefix
(`MM-DD HH:MM:SS.mmm PID TID LEVEL M1Integrity: `) that the original parser
did not strip, so it found 0 tokens. `decode-tokens.mjs` now strips that
prefix (falls back to matching bare delimited lines if no prefix is
present) — fixed and re-verified against this same file. `diff-verdicts.mjs`
also needed a small fix: Google's response wraps everything in a top-level
`tokenPayloadExternal`, so `expectedToDiffer` was checking exact key names
against unprefixed field names and always coming back `false`; it now
matches by suffix.

With those two fixes, all 4 tokens decoded successfully with no Google
error. **No values that are hashes/digests/tokens are shown below.**

### Per-verdict summary (all 4 identical on every field below except where noted)

| Field | Value (all 4 verdicts) |
|---|---|
| `appIntegrity.appRecognitionVerdict` | `UNRECOGNIZED_VERSION` |
| `appIntegrity.packageName` present | yes, `com.tananaev.passportreader`, matches request package name |
| `appIntegrity.certificateSha256Digest` present | yes, 1 entry (value withheld — it's a digest) |
| `appIntegrity.versionCode` | `23` |
| `deviceIntegrity.deviceRecognitionVerdict` | `["MEETS_DEVICE_INTEGRITY"]` (1 entry — no `MEETS_STRONG_INTEGRITY`, no `MEETS_BASIC_INTEGRITY` flag beyond this) |
| `accountDetails.appLicensingVerdict` | `UNEVALUATED` |
| `deviceAttributes` / `recentDeviceActivity` / `deviceRecall` / `environmentDetails` | absent from all 4 verdicts (checked by key presence, not just top-level scan) |
| `requestDetails` fields present | `requestPackageName` (`com.tananaev.passportreader`, identical across all 4), `requestHash` (present, differs per request — value withheld), `timestampMillis` (present, differs per request) |

`appRecognitionVerdict: UNRECOGNIZED_VERSION` means this build/signing
config isn't recognized by Play as a distributed release — expected for a
locally-built spike APK, not evidence of a device or integrity problem.
`MEETS_DEVICE_INTEGRITY` only (not `MEETS_STRONG_INTEGRITY`) is consistent
with a debug-signed/sideloaded build; not investigated further here (out of
scope for this decode spike).

### `diff-verdicts.mjs` output — field names only

**Differs (expected, both requestDetails, per the flag in the raw JSON
output — not reproduced here since the values are the diff):**
- `tokenPayloadExternal.requestDetails.requestHash`
- `tokenPayloadExternal.requestDetails.timestampMillis`

**Identical / crossSiteStable, run1-vs-run2 within a site AND site-a-vs-site-b
(same list in all three pairwise diffs, and in the four-file crossSiteStable
run):**
- `tokenPayloadExternal.accountDetails.appLicensingVerdict`
- `tokenPayloadExternal.appIntegrity.appRecognitionVerdict`
- `tokenPayloadExternal.appIntegrity.certificateSha256Digest.0` (digest — value not shown)
- `tokenPayloadExternal.appIntegrity.packageName`
- `tokenPayloadExternal.appIntegrity.versionCode`
- `tokenPayloadExternal.deviceIntegrity.deviceRecognitionVerdict.0`
- `tokenPayloadExternal.requestDetails.requestPackageName`

This is not judged here — it is simply every field this one build/device/app
combination returned as identical across two simulated sites, on a single
device, in a single short session. A larger, more varied sample (different
devices, longer time gaps, real distinct sites) would be needed before
drawing any conclusion about which of these fields are stable identifiers
vs. incidental to this specific spike run.
