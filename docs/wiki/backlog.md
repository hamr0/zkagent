---
type: reference
title: zkagent — backlog (parked and unbuilt items) and how to revive
status: stable
sources: [docs/wiki/milestones.md, docs/wiki/decisions.md, docs/wiki/questions.md]
---

# Backlog — parked and unbuilt items

zkagent was closed on 2026-10-24 as a complete experiment (D108). v1 as the PRD defines it —
rung 1, M0–M3, "and nothing else is" (`docs/product/zkagent-prd.md` §2, §4) — is delivered. This
page is the one place that lists everything that was designed or discussed but not built. Nothing
here is scheduled. **If any of it is revived, NO-GO #10 still applies: the owner approves the
scope in the PRD first, then it is built.**

Pointers: decisions in `decisions.md`, scope text in `milestones.md`, open-question triage in
`questions.md`, findings in `.claude/remember/findings.md`, reusable parts in
`docs/product/reuse-map.md`.

## Parked items

### 1. M3b / tier C (attributed disclosure) — complete design, never approved

Tier C answers identifying questions (expiry buckets, name match, date-of-birth range,
nationality in two modes, document-number match) as booleans, only for a site whose issuer key is
pinned in the app, with a holder-side consent list. Every decision point was ruled (D89–D106,
plus D107), and `milestones.md` §6.9 turns them into a numbered build scope (an opening POC plus
17 scope items, a traceability table, and a list of what is still open). The owner never approved
§6.9, so under NO-GO #10 nothing was built. The scanner still refuses tier C (§6.2 item 13), and
the README, customer guide and PRD describe tier C as designed and parked.
**Why parked:** the owner closed the project instead of building further. Pointers: D19, D20,
D74, D89–D107; `milestones.md` §6.4, §6.9; Q22, Q34, Q49; Q11 (tier-C residual).

### 2. §6.5 S4 — client trust list (FR10) via an attestation plug

The verifier would pin the scanner's package name and signing-cert digest, read from a
device-attestation token (Play Integrity or Key Attestation), because the OpenID4VP wire carries
neither. It is built together with the first attestation plug, which was never built (the
reference app ships bare, D27). Today's client identity is only the attester key bound on first
sight (D38/D39): weaker than FR10, disclosed (D78). **Why parked:** needs the attestation plug,
which in turn needs the attestation-root and fingerprinting questions (Q14, Q7) answered. Pointers: D17, D23, D27, D78; `milestones.md`
§6.5 item 4; Q7, Q14.

### 3. §6.6 — Play Store closed-testing track

A showcase listing on Google Play. Items 1–6 are parked; item 7 (the release-signing keystore)
was built and signs the GitHub-released APK. **Why parked (D83):** the owner's Play developer
account was closed for inactivity and the owner declined to open another; distribution is GitHub
Releases plus sideload. Pointers: D80–D83; `milestones.md` §6.6, §6.8; Q50 (closed, moot),
Q51.

### 4. Rung 2 — the agent layer (M4 delegation certs, M5 blocklist and appeal)

Delegation certificates, RFC 9421 request signing, per-serial revocation, a signed blocklist
format and pseudonymous appeal (FR5, FR8, G5, G6, Q8, Q19, Q21). **Not designed in this repo and
not planned.** The owner's agent-delegation work now lives in the individual Internet-Draft
`draft-hamr-oauth-agent-delegation`
(https://datatracker.ietf.org/doc/draft-hamr-oauth-agent-delegation/) — an individual submission
with no IETF endorsement. Its Appendix A, Direction 3, describes this project (see the README).
**Why parked:** the PRD gated rung 2 on rung 1 shipped plus at least one real adopter; rung 1
shipped, there has been no external adopter, and the owner closed the project. Pointers:
`zkagent-prd.md` §4, §5, FR5, FR8; `milestones.md` M4/M5 rows.

### 5. Track Z — zero-knowledge over the passport

A parallel track gated by D23's five gates (audited Barretenberg release, independent circuit
audit, measured on-device proving time, chain-free nullifier, open-source prover). No gate was met
and no milestone was ever opened. Third-party ZK exists only as a validation-grade evidence plug
(`zk-passport/1`, tier A only, D24/D25); the reference app ships bare (D27). v1 is never described
as zero-knowledge (NO-GO #7). Q26 (a circuit exposing both a stable nullifier and a fresh nonce)
is open and gated behind this track. **Why parked:** the gates did not hold. Pointers: D1, D23,
D24, D25, D26; `docs/product/zk-due-diligence.md`; `docs/logs/M2-EU-ZKP-SPIKE.md`; Q26.

### 6. D84(4) — the demo's own operator configuration

D84 point 4 deferred a verifier-side (`apps/demo`) operator config to "a later round if this round
is already big." The demo is configured by environment variables (`apps/demo/README.md` Config
table); no `operator.json`-style file exists for it. §6.9 item 15 would have used whichever
mechanism existed when built. **Why parked:** never needed for M0–M3. Pointers: D84;
`milestones.md` §6.7.7, §6.9.4.

### 7. Q52 — multi-threshold origins

One operator serving two thresholds (for example seniors and minors) from one hostname. Ruling
(D82(c)): no new knob; use one hostname per question (`seniors.example` / `minors.example`). The
scanner already supports an exact-hostname exception allowlist (`multi_threshold_verifiers`,
default empty, D84). **Why deferred:** owner — "follows broad adoption", which did not happen.
Pointers: D74, D82, D84; Q52.

### Smaller carried items (not scheduled)

- Finding #4 remainder: after a non-rotation recreation (font scale, locale, process death) ten
  form fields and the `lastMrzHash` diagnostic reset; rotation itself is locked off (D63). A
  logcat mislabel and form-state loss only, no gating effect. See findings #4.
- Q31: no re-enrolment path for a lost or reset device under trust-on-first-sight key binding
  (D38). Disclosed in the customer guide.
- Device verification not recorded for Q46 (the "Document number" label fix). See `questions.md`.

## How to revive

**Read first, in this order:** `README.md`; `docs/product/zkagent-prd.md` (status block, NO-GO
table); `docs/product/reuse-map.md`; `docs/product/customer-guide.md`; `docs/wiki/milestones.md`
§6 (the milestone table) and the section for the item you are reviving; `docs/product/learnings.md`
(lessons, including hardware and attestation). The evidence docs under `docs/logs/` say what was
actually proven and on what device.

**Build the scanner** (Android, builds on Linux, no Mac needed):

```
cd apps/scanner
export JAVA_HOME=$HOME/.gradle/jdks/eclipse_adoptium-17-amd64-linux.2 ANDROID_HOME=$HOME/Android/Sdk
./gradlew :app:testRegularDebugUnitTest      # unit tests; read the JUnit XML for counts
./gradlew :app:assembleRegularDebug          # debug APK
```

(The JDK path is the owner's Gradle-provisioned JDK 17; any JDK 17 works. `ANDROID_HOME` is the
SDK location on that machine.) A signed release build reads four environment variables:
`KEYSTORE_FILE`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`, then
`./gradlew :app:assembleRegularRelease`. Without `KEYSTORE_FILE` the build emits
`app-regular-release-unsigned.apk` by design. The owner's showcase keystore lives under the
gitignored `secrets/` directory; its passphrase is not in the repo. Never commit a keystore or a
password. The full recipe (generating a keystore, reading the cert digest, `apksigner verify`)
is `docs/product/customer-guide.md` §7.1. Per-deployment settings (verifier hostname allowlist,
threshold subset, tier mode, app name) are in `apps/scanner/operator.json`, schema in
`milestones.md` §6.7; `apps/scanner/scripts/operator-json-negative.sh` exercises the bad-config
cases.

**Run the demo verifier against a phone:**

```
adb install <the scanner APK>                  # sideload; USB debugging on
adb reverse tcp:8787 tcp:8787                  # phone's 127.0.0.1:8787 -> this machine
cd apps/demo && npm install && npm start       # verifier on http://127.0.0.1:8787
```

Open `http://127.0.0.1:8787` in the phone's own browser. That origin is the only supported one
(D76); a hosted or self-signed-HTTPS verifier is deliberately not supported. Steps and config:
`apps/demo/README.md`. Tests: `cd apps/demo && npm test` and `cd packages/chiproof && npm test`
(both need Node 20 or newer).

**Release** (D72 lockstep): one version across `packages/chiproof/package.json`, the scanner's
`versionName`/`versionCode`, and one `CHANGELOG.md` section; tag plain `vX.Y.Z` on `main`;
`chiproof` is republished every release through the manually dispatched
`.github/workflows/publish.yml` (npm trusted publishing over OIDC, bound to that exact filename —
never rename it); the signed APK, its sha256 and the cert digest go on the GitHub Release
(`milestones.md` §6.8). Verify a publish against the registry JSON, not the npm CLI cache.
