---
type: log
title: Item 24 device round — the demo page as test and showcase page
status: final
---

# Item 24 device round, 2026-11-02

Pixel 6a, debug build GIT_SHA `7a79ca0`, tier C ON, `tier_c_verifiers` `["127.0.0.1"]`, `verifiers` `"any"`.
Demo at `24d93c0` with `DEMO_TEST_MODE=1`. The owner read every phone screen; the orchestrator read the page
and the demo log. Value-free: field and question names and verdicts only. Source of results: the orchestrator's
report of the round; this page records them, it is not a fresh run.

| Row | What | Phone | Page | Demo log | Verdict |
|---|---|---|---|---|---|
| 1 | Tier A, over 18, `127.0.0.1` | "Delivered — the website shows the result" | "Proved: over 18", ok:true allowed:true | tier=A allowed=true | PASS |
| 4 | Tier B, first scan | PIN asked (device key), "Delivered" | registered | allowed=true, evidence-verified `sig-p256/1`, attester=bound_first_sight | PASS |
| 5 | Tier B, second scan | PIN, "Delivered" | "Already registered at this site", "zktag already seen at this site: yes" | attester=matched | PASS (wording issue, below) |
| 8 | Tier C `expiry_beyond` 3, holder setting ON | "Done. 1 yes or no answer was sent…" | "Answered … Document valid for more than 3 months: yes" | answered `expiry_beyond:3=true` | PASS |
| 14 | Tier C, holder setting OFF | "Additional verifications are turned off… nothing read or sent"; app log REFUSED on arrival cause=holder_setting_off, generic refusal posted http 200 | "Refused — The holder's app refused this request. It does not say why, and nothing was disclosed." | status done, refused_by_holder | PASS |
| 17 | Tier C as `localhost` (unpinned) | "This website is not approved to ask additional questions about your document. Nothing was read and nothing was sent." | at expiry: "No answer — … this app tells a site it has not approved nothing at all, not even a refusal." | | PASS |
| 20 | Walk away (tier C, `127.0.0.1`, no action) | no action | at expiry: "No answer — The request expired and nothing came back. The holder may have walked away." | | PASS |
| 25 | Tier B as `localhost` | PIN, "Delivered" | | tier=B allowed=true, evidence-verified, attester=bound_first_sight (a separate `localhost` key) | PASS |
| 9 (D121 pt 2) | Tier C `name_match`, owner-typed names joined by an em dash | camera-scanned QR; "Done. 1 yes or no answer…" | answered `name_match=true` | 0 em dashes in the demo log; the name was never seen by the orchestrator | PASS |

Tier A as `localhost` passed in the POC (`ITEM24-LOCALHOST-POC-2026-11-02.md`).

## Findings and gotchas

- Row 4/5 first attempt: demo verdict allowed=false reason=`attester_key_mismatch`. The demo's persisted store
  (`apps/demo/data/store.json`, gitignored) held a device key bound by an earlier app install; the reinstall
  made a new key, so D38 trust-on-first-sight refused it. Intended behaviour, not a bug. The store was moved aside
  (`data/store.json.pre-reset-2026-11-02`) and the demo restarted; rows 4 and 5 then passed.
- Row 5 wording: the page showed "Registered as a unique adult human" / "now registered" beside "Already
  registered at this site". Fixed in the demo page (heading and line say "already registered" when the zktag
  was seen), with a test in `apps/demo/tests/scenario-page.test.mjs`.
- The page's link snapshot can lag a click: the orchestrator once launched the previous, already-used link.
  Always check the request id changed before launching.
- Row 21 (stale link) was observed in passing; see the root-cause note in the milestones item 24 block.
