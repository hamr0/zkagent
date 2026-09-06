# M3 — showcase release-signing keystore, signed release APK verified (2026-09-06)

**Status**: source record for PRD §6.6 item 7 part (b) — the owner's own showcase keystore,
generated per the per-operator recipe already written in `docs/product/customer-guide.md` §7.1
(part (a), commit `35013fa`). Closes §6.6 item 7 in full (both parts DONE); does not itself touch
§6.6 item 1 (Play closed-track upload) or item 2 (Play App Signing digest), which remain open. See
`docs/wiki/decisions.md` D80, D81, D82.

**Rule for this file (carried from `M0-EVIDENCE.md` through `M3-POC-EVIDENCE-2026-09-04.md` /
`M3-SCANNER-S2-S3-EVIDENCE-2026-09-05.md`)**: no PII values, ever, and no keystore passphrases —
commands below show passphrase entry as `$(pass show …)` / an interactive prompt, never a literal
value. A public certificate digest is an identity, not a secret, and is recorded in full.

---

## Setup

Owner-run, in a real terminal (not this session's `!` in-session shell — see the gotcha below).
Fedora build host, Gradle's own provisioned JDK 17 (`JAVA_HOME` set to it), Android SDK
build-tools 36.1.0 for `apksigner`/`aapt2`. `apps/scanner/app/build.gradle.kts`'s `signingConfigs`
block is unchanged from the recipe in `docs/product/customer-guide.md` §7.1 — this session did
not read or modify that file.

## 1 — Generate the keystore

```
mkdir -p secrets
keytool -genkeypair -v -keystore secrets/zkagent-showcase.p12 -storetype PKCS12 \
  -alias zkagent-showcase -keyalg EC -groupname secp256r1 -sigalg SHA256withECDSA \
  -validity 10950 -dname "CN=zkagent showcase"
```

Passphrase entered interactively at the `keytool` prompt (not passed on the command line).
Result: PKCS12 keystore, alias `zkagent-showcase`, EC P-256 (secp256r1), SHA256withECDSA,
`CN=zkagent showcase`, validity 2026-09-06 → 2056-08-29 (10,950 days).

**Gotcha for anyone following the §7.1 recipe from an agent-driven shell**: `keytool -genkeypair`
needs interactive terminal input for the keystore/key passphrases. This session's own in-session
`!` shell cannot supply that — a first attempt through it produced empty passphrases and
`keytool`'s own "Too many failures" abort. The generate step (recipe step 1) must be run in a real
interactive terminal, not through an agent's non-interactive shell. Worth a one-line addition to
§7.1's step 1 — see the guide update below.

Passphrase and a base64 copy of the `.p12` file are stored in the owner's `pass` store (entries
`zkagent/keystore`, `zkagent/showcase-p12`); the stored backup copy was hash-verified equal to the
on-disk file. Neither the passphrase nor the base64 content is reproduced in this document, per
the task boundary for this session (never read or print from `secrets/` or `pass`).

**Ignore check**:

```
git check-ignore -v secrets/zkagent-showcase.p12
```

→ `.gitignore:13:secrets/  secrets/zkagent-showcase.p12` — confirmed ignored.

## 2 — Read the certificate digest

```
keytool -list -v -keystore secrets/zkagent-showcase.p12 -alias zkagent-showcase | grep -A1 'SHA256'
```

Result — SHA-256 fingerprint (keytool colon form):

```
1F:6B:CE:AE:0F:FE:9C:2B:32:6F:2A:AB:22:02:F0:BD:F3:DF:7E:5B:DD:8F:AC:50:AB:A2:E1:D3:18:40:72:64
```

Same digest, lowercase hex (no colons) form, as produced by `apksigner` below:

```
1f6bceae0ffe9c2b326f2aab2202f0bdf3df7e5bdd8fac50aba2e1d318407264
```

This digest is public — an identity, not a secret — and is the value any verifier trust list
(§6.5 S4) or evidence log records.

## 3 — Build a signed release

```
KEYSTORE_FILE=$PWD/secrets/zkagent-showcase.p12 KEYSTORE_PASSWORD=$(pass show zkagent/keystore) \
  KEY_ALIAS=zkagent-showcase KEY_PASSWORD=$(pass show zkagent/keystore) \
  ./gradlew assembleRegularRelease --offline
```

`JAVA_HOME` = Gradle's own provisioned JDK 17. Exit code: **0**.

Output: `apps/scanner/app/build/outputs/apk/regular/release/app-regular-release.apk`
— **22,037,351 bytes**, file SHA-256 prefix `956f37d53c76064c…`.

This is the first release build in the project's history to produce a **signed** release APK —
every build through v0.6.0/v0.6.1 (`assembleRegularRelease`) produced only
`app-regular-release-unsigned.apk`, because no keystore existed anywhere (D80,
`docs/logs/M3-EXIT-ROWS-v0.6.0-2026-09-05.md`).

## 4 — Verify the signature and digest independently

```
apksigner verify --verbose --print-certs app-regular-release.apk
```

(build-tools 36.1.0)

Result: **"Verifies"**. Signature scheme summary: v2 `true`; v1/v3/v4 `false`. Signer #1 DN:
`CN=zkagent showcase`. Signer #1 certificate SHA-256 digest:

```
1f6bceae0ffe9c2b326f2aab2202f0bdf3df7e5bdd8fac50aba2e1d318407264
```

— matches step 2's `keytool` reading exactly (colon form vs. hex form of the same 32 bytes).

```
aapt2 dump badging app-regular-release.apk
```

→ package `com.zkagent.scanner`, versionCode `4`, versionName `0.6.1`.

## Three-keys distinction (D38/D80/D81, restated for this evidence)

This local keystore (`secrets/zkagent-showcase.p12`, digest `1f6bceae…407264`) is one of three
structurally different keys, never to be conflated:

1. **This local release-signing key** — signs the owner's own showcase build only, generated by
   the owner following §7.1's per-operator recipe. Never shared; every other operator generates
   their own, independently, per §7.1.
2. **The Play upload/app-signing key** — does not exist yet (§6.6 item 1, closed-track upload, not
   yet run this session). Once uploaded, Play App Signing re-signs the APK with Google's own key;
   the Play-distributed build's digest will differ from `1f6bceae…407264` above. **The
   Play-distributed digest is unknown until a real upload happens (§6.6 item 2) — not guessed or
   assumed here.**
3. **Device attester keys in `AndroidKeyStore` (D38)** — per-(origin, zktag) mode-B evidence keys
   generated on-device at scan time; unrelated to app signing entirely.

## Trust-list pin slot check (task 3)

Grepped `apps/demo`, `packages/chiproof`, and `docs/wiki/milestones.md` §6.3 item 8 / §6.5 S4 for
an existing cert-digest pin slot before writing this file, per D78's ruling that pinning is a
verifier-side decision this session must not make.

**Result: no pin slot exists.** `apps/demo/README.md` (lines ~124-147) documents FR10/trust-list
as **"finding, not implemented"** — the M3 demo verifier has no package-name or signing-cert-digest
check at all, and the OpenID4VP wire it uses carries no such field to check against (D78). The
check is deferred to §6.5 S4 ("client trust list via attestation plug"), which does not exist yet
and is explicitly built together with the first attestation plug, not before it. No code in
`apps/demo` or `packages/chiproof` was changed by this session, consistent with that ruling.

**Where the digest would go, once S4 exists**: a verifier-side config value (not yet named/shaped)
read by the future attestation-plug wiring in `apps/demo`'s server, alongside whatever the plug's
attestation-token parsing extracts as package name + cert digest. Per D78, that config would need
to hold *two* digests once a Play upload exists (this local one, `1f6bceae…407264`, for
sideloaded/local builds; Google's re-signing digest, once known, for the Play-distributed build) —
S4's own text already anticipates this. Recording the slot's future location here; not creating it.

## What this run did and did NOT establish

**Did establish:**
- A real EC P-256 PKCS12 keystore exists for the owner's showcase build, generated per §7.1,
  correctly gitignored, backed up (hash-verified) outside git.
- `assembleRegularRelease --offline` with the documented env vars produces a **signed** release
  APK for the first time in this project's history (exit 0).
- `apksigner verify` independently confirms the signature and reproduces the same SHA-256 digest
  `keytool` reported at generation time — the two readings are not just both present, they agree.
- The three-keys distinction (local release key / Play upload key / AndroidKeyStore attester keys)
  holds and is restated with real values for the first two (the third remains per-device,
  generated at scan time, never a static value).
- No trust-list pin slot exists anywhere in this repo for this digest to be entered into yet —
  confirmed by grep, not assumed.

**Did NOT establish:**
- The Play-distributed build's own digest — unknown until §6.6 item 1 (a real closed-track
  upload) happens. Not guessed here.
- Any change to `apps/demo`'s or `chiproof`'s trust-list/pin behavior — none was made, per D78 and
  per this session's own task boundary (never touch `apps/scanner/app/build.gradle.kts`, and no
  verifier-side pinning decision is this session's to make).
- Device install/run of the signed APK — this session covers the build+verify step only; no
  install or on-device session was run against `app-regular-release.apk`.

---

**No PII values, keystore passphrases, or `secrets/`/`pass` contents appear anywhere above.** The
only values recorded are the public certificate SHA-256 digest (in both forms), file sizes/hash
prefixes, exit codes, and command-line invocations with passphrases shown only as
`$(pass show …)` — checked against this file's own rule before inclusion.
