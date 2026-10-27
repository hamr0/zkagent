# M3b chunk 3 — open age verification (item 23), device runs (Pixel 6a): 23(d) PASSED 2026-10-26, 23(c) PASSED 2026-10-27

**Status**: evidence record for build chunk 3 of milestones.md §6.9.7 (D109(i)1, D109(j)1): `operator.json`
`"verifiers": "any"` (rule 12), plus pass criteria (c) and (d) of §6.9.2 item 23. No pre-registered cell was edited
after the fact; where something was not confirmed, it says so.

**Branch / commit**: `feat/m3b-tier-c`, HEAD `b5b3b6c`. Chunk 3 commits: `6e834df` (test-only TLS deploy kit, PUBLIC_SCHEME
knob), `f39ed8d` (scanner: `verifiers` "any", rule 12), `0540820` (docs: item 23 built, 23(c)/(d) pending), `c8b362b`
(kit: `skip_install_trust`), `b5b3b6c` (kit reworked for a live nginx host: one conf file, one certbot lineage, clean
teardown). No scanner change after `0540820`; `c8b362b` and `b5b3b6c` touch only `apps/demo/deploy`.

**Rule for this file (carried from chunks 1 and 2)**: no PII, ever, and no hostname, domain or IP of the owner's
infrastructure. The test host is "the owner's VPS (a live production box; test sites on three throwaway subdomains of an
owner domain, CA-valid Let's Encrypt cert)". The three test sites are "good", "mismatch" and "badcert"; in quoted log
lines their hosts appear as `<good-host>`, `<mismatch-host>`, `<badcert-host>`. Loopback addresses (127.0.0.1, localhost)
are quoted as they are.

---

## 1 — Scope

Item 23 lets `verifiers` be the string `"any"` (age verification, tiers A and B, open to any hostname) instead of the
exact-hostname array; rule 12 rejects any other shape and an array containing `"any"`. Tier C stays gated by the pin
list, the operator switch and the holder setting. Pass criteria run on a device: (d) real-TLS key discovery for an
arbitrary site, with paired negatives; (c) an unlisted site is answered for tier A, refused for tier C, with a paired
array-form build that refuses it.

## 2 — Software evidence

JDK 17.

**Re-run by the orchestrator (software at `0540820`):**

| Item | Result |
|---|---|
| Scanner, old results deleted first, summed over 40 JUnit XML files | 587 tests, 0 failures, 0 errors, 0 skipped |
| `apps/scanner/scripts/operator-json-negative.sh` | exit 0, 33 PASS, 0 FAIL, `operator.json` unchanged afterwards |
| `apps/demo` tests | 66 pass, 0 fail (re-run at `b5b3b6c`, still 66/0) |
| `bash -n run.sh` (deploy kit) | ok |

**The coder's sabotage table, NOT re-run by the orchestrator** (each sabotage restored afterwards):

- remove the `open ||` branch: 7 red
- array branch stops checking the list: 3 red
- `"any"` widens every list: 17 red, including the tier-C gate tests
- remove rule 12: negative script 9 FAIL

**Release-config check by the orchestrator (aapt2 on the release APK):** network config is base-config
`cleartextTrafficPermitted=false` with only system trust anchors; badging has no debuggable flag; `pkgFlags` on the
device had no DEBUGGABLE.

## 3 — Builds and device facts

| Item | Value |
|---|---|
| Device | Pixel 6a, Android 17 |
| APK, `"any"` + tier C on (debug), sha256 | `1e5613ee4ebd448a2db5045ad006be2590434d674d58d6956a0d8ad0c1d1395e` |
| APK, array `["127.0.0.1"]` + tier C on (debug), sha256 | `df2ba26d4254a85603c308a1f3f19cbb30528730aebd1166f26e0d2aa31c544b` |
| APK, release-config (`"any"`, tier C off, debug-signed release variant), sha256 | `064c7c399a2d18af84737e16af613c78c248d7a8d6115480c07c1286e9be5194` |

## 4 — 23(d): real-TLS key discovery on the owner's VPS (2026-10-26, release-config build): PASSED

**Host**: the owner's VPS, a live production box, with three throwaway test sites on subdomains of an owner domain and a
CA-valid Let's Encrypt certificate. Not a project-hosted verifier (NO-GO #3 holds).

**Preflight from the dev machine against the real internet (`check.mjs`), exit 0:**

| Site | Result |
|---|---|
| good | signature verifies; `response_uri` is the same origin |
| mismatch | key fetched; signature does NOT verify |
| badcert | TLS refused (`DEPTH_ZERO_SELF_SIGNED_CERT`) |

**On the phone:**

| Site | App lines | Dialog | Verifier side | Result |
|---|---|---|---|---|
| good (tier A, age 18) | 16:34:00 `RequestTrust: handoff request object verified — origin=https://<good-host>:443 signature_verified=true key_kind=well-known`; 16:35:01 `present-bare using pre-verified handoff request object … (no re-fetch)`, `direct_post -> https://<good-host>/wallet/direct_post`, `http_status=200` | "Delivered — the website shows the result." | verdict `tier=A threshold=18 ok=true allowed=true` | PASS |
| mismatch | 16:35:52 `handoff REFUSED — JWS verification failed: signature_invalid` | "Handoff refused: JWS verification failed: signature_invalid" | got the request download, no answer | PASS |
| badcert | 16:36:49 `RequestTrust fetchWellKnownKey: GET https://<badcert-host>:443/.well-known/zkagent-verifier failed: SSLHandshakeException: CertPathValidatorException: Trust anchor for certification path not found`; `handoff REFUSED — no trusted request-signer key resolvable for origin https://<badcert-host>:443` | "Handoff refused: no trusted request-signer key resolvable for origin https://<badcert-host>:443" | saw nothing | PASS |

The two refusal dialogs are technical; recorded as finding #37.

**The live box**: the kit touched one nginx conf file and one certbot lineage only, ran `nginx -t` before every reload,
ran the demo as `nobody` on loopback, and reused an existing certbot account (no new account). Teardown exit 0;
verify-clean "the box is clean" exit 0. The orchestrator also removed its own kit copy and compared the
accounts/renewal/archive/live/conf.d listings against a pre-test baseline: identical. No leftover process, nothing
listening on the port, and the box's own live sites returned 200 before, during and after. The owner deleted the three
DNS records; they no longer resolve.

**Process note**: the first kit design (Caddy owning ports 80 and 443) was dropped once the read-only check showed the
box is a live production nginx host. The orchestrator also caught that Caddy's `tls internal` would have installed its
CA into the box's system trust store (`c8b362b`); the kit was then reworked for nginx (`b5b3b6c`).

## 5 — 23(c): unlisted site on loopback (2026-10-27, `adb reverse`, demo with `TIERC_POC_ALLOW_RAW=1`): PASSED

Local times are phone logcat. The app user agent on every `request_uri GET` was `Dalvik/2.1.0 … Pixel 6a`.

| # | Build | Request | Result |
|---|---|---|---|
| 1 | "any", fresh data | tier A from `localhost` (listed nowhere), tx `hxojGfHeC3Bxmwjb` | Answered. Log: `origin=http://localhost:8787 signature_verified=true`, `direct_post http_status=200`; verdict `tier=A threshold=18 ok=true allowed=true`; dialog "Delivered — the website shows the result." PASS |
| 2 | "any", holder switch ON | tier C from `localhost`, tx `D4e-6cyDOPPUepcZ` | Refused before any read: `REFUSED before any read or mint — cause=not_pinned … chip_read=false mint=false`; `generic refusal NOT sent` (correct per D110: not pinned); dialog "This website is not approved to ask additional questions about your document. Nothing was read and nothing was sent."; site status pending. PASS, with the observation below |
| 3 | "any", tier C | tier C from `127.0.0.1`, tx `MqzNZ6gghUtN7KVQ` | `admitted predicates=1 names=expiry_beyond`; `answered predicate=expiry_beyond result=true`; `direct_post http_status=200`; verdict `tier=C … answered=expiry_beyond:6=true`; dialog "Done. 1 yes or no answer was sent to the website. Nothing else left your phone. Go back to the website to continue." PASS |
| 4 | array `["127.0.0.1"]` | tier A from `localhost`, tx `PKlATtm2oqqvZpok` | Refused immediately on open: `handoff REFUSED by operator policy (§6.7 POC) — host=localhost`; "This site (localhost) is not on this app's approved verifier list — refused." The demo log shows the tx created and NO `request_uri GET` for it (the gate runs before any fetch). PASS |

Check 4 is the paired non-vacuity pass: the open answer of check 1 is keyed on `"any"`, because the same request is
refused on the array build.

**Observation (check 2)**: on this path the app first demanded the three document fields ("Enter the document number,
date of birth and expiry date, then tap again.") before running the tier-C pin check. The owner entered them and did NOT
hold the card. Nothing was read or sent, but in chunk 1 the holder-off and age refusals fired on Verify without the
fields. The order is inconsistent, and an unpinned site makes the holder type document data before being refused.
Finding #38.

## 6 — Verdict

**Chunk 3 PASSED 23(c) and 23(d) on the Pixel 6a.** Open age verification answers an unlisted site for tier A, still
refuses it for tier C in-app before any read, and the array form refuses it before any fetch. Real-TLS key discovery
works against a CA-valid arbitrary HTTPS origin on the release network configuration, with a bad-certificate host and a
wrong-key host both refused.

Qualifications, stated rather than hidden:

- The sabotage table is the coder's report, not re-run.
- Dialogs were read by the owner aloud (the app forbids screen capture) and matched to the logcat
  `terminal outcome dialog shown` lines.
- 23(d) used a debug-signed release-config build, not a production-signed release.
- The well-known key fetch has no unit coverage; 23(d) is its only real exercise (finding #39).
- 23(c) check 2 shows an inconsistent check order (finding #38).

## 7 — Privacy check

The owner's name string was grepped in all chunk 3 logs; 0 hits. No hostname, domain or IP of the VPS or the test sites is
in this file.

## 8 — Open items and findings

Recorded in `.claude/remember/findings.md` as #37 to #42: refusal dialogs too technical (owner), pin check ordering,
no unit coverage for the well-known fetch, JWKS `keys[0]` only, cross-host redirects on the key fetch (needs an owner
decision), and the `request_uri` GET for unpinned tier-C requests under "any" (disclose in item 17). Nothing here is a
ruling.
