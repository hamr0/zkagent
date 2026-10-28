# Phone check of 0.9.0 and the 0.9.1 redirect and Log-reason fix (Pixel 6a) 2026-10-28: PASSED after a fix, with stated gaps

**Status**: evidence record for the phone check of items shipped in 0.9.0 but never checked on a phone (the tier-C-off
Settings screen, the green delivered line, the post-read refusal line, D118, redirects), and for the fix it caused
(findings #52 and #53). Nothing was edited after the fact; where something was not run, it says so.

**Branch / commit**: 0.9.0 phase A on the released APK (tag `v0.9.0` -> `4bdc25c`); phase B on a debug build of `cb3f07a`
(main); the fix is `72dc143` on `fix/0.9.1-redirect-and-log-reason`. Finding #54 (popup wording) was changed after the device
re-check, on the same branch, and has not been seen on a device.

**Rule for this file (carried from chunks 1 to 3, D112 and D113)**: no PII, ever, and no hostname, domain or IP of the
owner's infrastructure. Every run used loopback only (127.0.0.1 via `adb reverse`). The owner typed document details on
the phone only. The plan these checks followed was the scenario list now carried in `docs/product/install-and-test-guide.md`
section 9.

---

## 1 — Scope

Items checked: tier-C-off Settings screen (released APK); green delivered line; post-read refusal line (HTTP 409);
D118 (at most one `nationality_is` and one `nationality_not_in`); a 3xx on the refusal POST, the answer POST and the
`request_uri` GET. TalkBack was in the plan and was **not run** (the owner declined); it stays disclosed as not checked.

## 2 — Builds and device facts

| Item | Value |
|---|---|
| Device | Pixel 6a, Android 17 |
| Phase A build | released APK `app-regular-release.apk`, versionName 0.9.0, versionCode 8, sha256 `3a2576d1df8e424974284b0c3d2a2e17c78eac248bd18bafb82aac4739c080ab` |
| Phase A signer | certificate SHA-256 digest `1f6bceae0ffe9c2b326f2aab2202f0bdf3df7e5bdd8fac50aba2e1d318407264` (showcase key) |
| Phase B build | debug tier-C-on build of `cb3f07a`, `-PoperatorJson=poc/operator.tierc-on.json` (tier C on, `127.0.0.1` pinned), sha256 `88a37f31…6b56d` (abbreviated as recorded) |
| Fix re-check build | debug tier-C-on build of the fix `72dc143` |
| Setup | `adb reverse tcp:8787 tcp:8787`, demo verifier on loopback with the raw-predicate hook on, a redirecting proxy (`apps/demo/scripts/redirect-proxy.mjs`) for the redirect runs |

The owner read each screen aloud (the app forbids screen capture); the orchestrator cross-checked each against logcat, the demo
log and the site's own state (`GET /ui/presentations/<tx>`).

## 3 — Software evidence

| Item | Result |
|---|---|
| Scanner unit tests at the fix `72dc143` | 642 tests, 0 failures, 0 errors, 0 skipped (46 JUnit XML files) |

## 4 — Phase A: released APK

| # | Check | Result |
|---|---|---|
| 3 | Tier-C-off Settings screen | The owner read footer `0.9.0 4bdc25c`, the sentence "Additional verifications are not available in this version of the app.", no switch, and Diagnostics folding open to two probe buttons. PASS |
| 5a | TalkBack over Scan, Log and Settings | NOT RUN (owner declined). Disclosed as not checked |

## 5 — Phase B: debug tier-C-on build of `cb3f07a`

| # | Check | Result |
|---|---|---|
| 4 | Green delivered line (tier A) | Demo verdict `ok=true allowed=true`, site status `done`; the owner read the delivered popup and a green delivered line. PASS |
| 2 | Post-read refusal | The chip read passed `chip_auth`, then `direct_post` got HTTP 409 `already_responded` (the transaction was poisoned after the app fetched the request). The owner read the red "already used" popup and the red "refused" line; the Log read "Sent, but the site rejected the response (HTTP 409)". PASS |
| 1 | D118, two `nationality_is` and two `nationality_not_in` | Both refused on arrival: logcat `reason=nationality_asked_more_than_once chip_read=false`; the site saw `refused=true` and the reason word occurred 0 times in the demo log. Control, one of each: admitted and both answered true; the demo reported `answered=nationality_is=true,nationality_not_in=true`. Site-side `chiproof` check by curl: HTTP 400 `nationality_asked_more_than_once` twice, HTTP 201 for one of each. PASS |
| 6 | 307 on the refusal POST | `http_status=307`, the app said "nothing was sent". PASS |
| 6 | 302 on the refusal POST | The redirect was followed as a body-less GET; the app said "website was only told" while the demo stayed `pending`. FAIL |
| 6 | 302 on the answer POST | The redirect was followed; the app showed "Delivered" and a green line while the demo stayed `pending` with no verdict. FAIL |

The two FAIL rows are finding #52 (HIGH): a redirect read as a successful delivery. The owner also ruled (finding #53) that the Log page
must show the specific tier-C refusal reason while the popup stays one generic message.

## 6 — The fix, `72dc143`

No redirects are followed on any of the four site calls (the refusal POST, the answer POST, the `request_uri` GET, and the key
discovery GET that already had it), so any 3xx takes the existing not-delivered or refused path. The Log now names the specific
refusal reason in plain, value-free words; the site still gets only a generic `access_denied`. Scanner unit tests 642/0/0/0
(command and counts in section 3).

Re-check on a debug tier-C-on build of the fix, with the proxy:

| # | Check | Result |
|---|---|---|
| R1 | 302 on the refusal POST | Log "...(nationality was asked more than once). Refusal could not be sent (the website answered HTTP 302).", no hit on the proxy's landing page, demo `pending`. PASS |
| R2 | 302 on the answer POST | Popup "Verifier refused: HTTP 302", demo `pending`. PASS on behaviour; the owner called the wording crude, which became finding #54 (the wording has since changed to "The website did not accept the answer — reopen the link from the site.", changed after the re-check, on the same branch, and **not yet seen on a device**) |
| R3 | 302 on the `request_uri` GET | "This website's request could not be verified, so it was not answered. Nothing was read and nothing was sent.", no landing hit. PASS |

The line colours for R2 and R3 were not read by the owner; logcat shows the refusals.

## 7 — Verdict

**Passed for what a human can reach by hand, after one real defect was found and fixed.** The phone check found that a redirect was read as a
delivered result on two paths (#52); the fix was re-checked on a device on all three paths.

Qualifications (stated limits):

- All phase B evidence is debug-build; the released APK cannot reach loopback, so the release build carries only the Settings check.
- The tier-C post-read refusal has no deterministic trigger with real documents, so it was not exercised.
- TalkBack was not run.
- The finding #54 wording has not been seen on a device.
- The re-check line colours for R2 and R3 were not read by the owner.

## 8 — Privacy check

None of the runs touched PII beyond the owner typing document details on the phone; scratch logs were value-free (country codes the site asked
about are not document values). No real document data in this file.

## 9 — Open items

Finding #54 (wording) awaits a device look. Nothing here is a ruling.
