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
here is scheduled, with one exception: M3b / tier C was revived and approved on 2026-10-25 (item 1,
D109(h)) and is in build. **If any of the rest is revived, NO-GO #10 still applies: the owner
approves the scope in the PRD first, then it is built.**

Pointers: decisions in `decisions.md`, scope text in `milestones.md`, open-question triage in
`questions.md`, findings in `.claude/remember/findings.md`, reusable parts in
`docs/product/reuse-map.md`.

## Parked items

### 1. M3b / tier C (attributed disclosure) — REVIVED, IN BUILD (approved 2026-10-25, D109(h)); no longer parked. Tier C itself is built and device-checked and shipped in 0.9.0 (D116); the milestone is not done

Was parked by D108 (2026-10-24). Kept at this number so older references resolve. The owner
revived it (D109(a)) and approved the scope as amended on 2026-10-25 (D109(h)), which satisfies
NO-GO #10 for that scope. Tier C answers identifying questions (expiry buckets, name match,
date-of-birth range, nationality in two modes, document-number match) as booleans, only for a site
whose exact hostname is pinned in the app build, and only when the holder has turned on one app
setting, "Allow additional verifications" (OFF by default). All five questions are allowed to
every pinned site, with no per-site question permissions and no caps in this build. Scope text:
`milestones.md` §6.9 (an opening POC plus 15 scope items, including item 23, open age verification, D109(i)). Tier C is now built and device-checked and shipped in 0.9.0 (D116): the scanner answers it only when the `operator.json` switch is on, the site's exact hostname is pinned and the holder's setting is on; otherwise it refuses (§6.2 item 13). Pointers: D19, D20, D74, D89–D109;
`milestones.md` §6.4, §6.9; Q22, Q34, Q49; Q11 (tier-C residual). The deferred pieces are items 8
and 9 below.

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

### 8. Per-site tier-C question permissions and limits (allowlist entries naming which questions a site may ask; caps such as 3 questions)

Deferred by D109(h)2 and 5. In the tier-C build every pinned site may ask all five predicates with
no cap. Later, a pin-list entry could name which questions that site may ask, and caps could
bound how many a request or a site's lifetime may use (the owner floated 3). The design already
exists in `milestones.md` §6.9.2 items 10 (per-presentation cap and per-origin lifetime predicate
set), 11 (shared per-person ceiling) and 12 (Log-tab per-site lock view), kept as text with the
numbers reserved; a holder-side "limit to 3-4 questions or assign certain questions" idea is in
the owner's D109(h) words. Earlier decisions thereby deferred: D91, D93, D107(c), D107(d),
D107(e). **Why deferred:** the owner chose a simpler binary control first (D109(h)3); the cost of
no caps is disclosed limitation 17(k) (a pinned site can narrow facts down over repeated
requests). Pointers: D109(h); `milestones.md` §6.9.2 items 10-12, 17(k).

### 9. PROPOSAL / open question (not built, not scheduled): pin the site's request-signing key thumbprint as well as the hostname

Today the tier-C pin is by exact hostname only (D109(g)1-2), so it trusts whoever controls that
hostname's DNS and TLS (limitation 17(i), kept). The proposal is to also pin a thumbprint of the
site's request-signing key per entry (the previously rejected option K), so a DNS or TLS takeover
alone is not enough to pass the gate. Cost: a new APK on every site key rotation, plus a new
entry shape and validation rules. Raised by the owner at D109(h)6 ("unless you see a workaround
and would still go to backlog as a proposal or open question"); no ruling is pending and nothing
is decided. Pointers: D109(g)1, D109(h)6; `milestones.md` §6.9.2 item 2, 17(i).

### 10. Registration and signing for allowlisted sites (the dropped membership statements)

Deferred by D109(b) and restated by D109(i)2. The owner's words (D109(i)): everything beyond age
verification is "per registeration/allowlist only (later to build and sigining)". The first M3b
draft had an operator-signed membership statement per site (D96-D100, D103-D107(b)); D109(b)
replaced it with a plain exact-hostname pin list in `operator.json`, and this build does only the
tier-C verifications plus that allowlist/pin. What stays for later is the registration process and
signing: an operator vetting and adding third-party sites without a rebuild, and a signed statement
the app can check. Related: item 9 (key-thumbprint pin) and item 8 (per-site question
permissions). **Why deferred:** the owner chose the pin list first. Pointers: D96, D109(b),
D109(i)2; `milestones.md` §6.9.2 items 2 and 3 (DROPPED, number reserved), 17(h)-(j).

### Smaller carried items (not scheduled)

- Finding #4 remainder: after a non-rotation recreation (font scale, locale, process death) ten
  form fields and the `lastMrzHash` diagnostic reset; rotation itself is locked off (D63). A
  logcat mislabel and form-state loss only, no gating effect. See findings #4.
- Q31: no re-enrolment path for a lost or reset device under trust-on-first-sight key binding
  (D38). Disclosed in the customer guide.
- Device verification not recorded for Q46 (the "Document number" label fix). See `questions.md`.

### Parked after 0.9.1 (2026-10-29)

Nothing here is scheduled. Each line is one parked item with its anchor; the tag says whether it
is a behaviour change (something that exists is wrong), an idea (something missing) or a nit
(wording or noise, no behaviour). Source: the fix ledger and `.claude/remember/findings.md`.

**Behaviour changes (affect functionality):**

- Tier-C fence drops the Log entry when the Activity is recreated during the tier-C answer POST:
  the answer is delivered but no Log entry is written and the link stays in flight, so a rescan
  gets a 409 popup. Code read only. Anchor: `apps/scanner/app/src/main/java/com/tananaev/passportreader/MainActivity.kt`. Change.
- `issueChallenge({tier:'C'})` without an `issuer` mints an unsigned challenge without throwing;
  the mistake shows only after a real scan (`signature_required_at_tier_c`). Anchor:
  `packages/chiproof/src/challenge.js`. Change.
- The demo's loopback gate trusts the Host header behind nginx, so a request with a forged Host
  would open mode C publicly. Must be fixed before the demo is ever hosted (NO-GO #3 keeps it
  local-only for now). Anchor: `apps/demo/tierc.mjs`. Change.
- chiproof's non-blank test for `name_match` strips only `<`, whitespace and ASCII `-`, while the
  app's `siteWords` also drops apostrophes and Unicode dashes; a name field of only `'` passes
  chiproof, is signed, then the app refuses the whole request as malformed. Anchor:
  `packages/chiproof/src/tier-c.js`. Change.
- Resolved by disclosure, not by code: D119 made `issueChallenge` throw for two
  `document_number_is` in a patch release (0.9.1). The CHANGELOG 0.9.1 upgrade note and
  `customer-guide.md` now say so. The ledger bullet can be treated as resolved (the owner chose
  0.9.1 knowingly); it stays listed here so the behaviour change is not forgotten. Anchor:
  `packages/chiproof/src/challenge.js`, `packages/chiproof/src/tier-c.js`.

**Ideas and nice-to-haves:**

- Idea: no test runs a demo-signed two-of-a-kind request (the `TIERC_POC_ALLOW_RAW` hook) against
  the app's parser, so a wire-shape mismatch would show only on a device. Anchor: `apps/demo/server.mjs`.
- Nit: `docs/log.md` carries repeated identical `index-flat` lines (generated by docs-builder).
- Nit: the comment on `Delivery.NotAccepted` omits 3xx, which since #52 also lands there; the
  behaviour is correct and tested. Anchor: `apps/scanner/app/src/main/java/com/tananaev/passportreader/TierCText.kt`.

**Still-open LOW findings (in `.claude/remember/findings.md`):**

- #30: the refusal dialog can appear up to about 6 s after Verify with no "Sending" indicator
  (defensive Verify-tap path only, since D112). Nice to have.
- #33: the refusal notice (`error=access_denied` plus `state`) is unauthenticated and first write
  wins; low on loopback, 96-bit `state`; recommend drop or document.
- #34: the phone keeps showing a stale request after it expires; expiry is checked only at the
  Verify tap, which then refuses clearly. Nice to have.
- #35: the NL ID card's name and document number on the new tier-C wire were not exercised on a
  device (US passport only); needs an NL-card device run to close.
- #40: JWKS handling reads `keys[0]` only, while the class doc says the first EC/P-256 entry
  (`RequestTrust.kt`); code and doc disagree.
- #43: the document-fields check still runs before the defensive Verify-tap tier-C check;
  reachable only if the holder switch or age lock changes between arrival and the tap.
- #32: the demo page lead says "Two things a website can ask" (there are now three). Folded into
  item 24 (showcase page, 0.10.0).
- #46: `apps/demo/scripts/tierc-poc-request.mjs` has no tier-A threshold flag. Folded into item 24.

**Not run or not checked (disclosed):**

- Install guide: the Fedora `sudo dnf install java-17-openjdk-devel` line and the `sdkmanager`
  line were read from docs, not run (`docs/product/install-and-test-guide.md` lines 54 and 72).
- TalkBack was found unusable and dropped; it stays disclosed as not checked.

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
