---
type: log
title: Item 24 POC — localhost as the second site (D122)
status: draft
---

# Item 24 POC: `localhost` as the second site (D122)

Riskiest assumption: the same demo, opened as `http://localhost:8787`, behaves to the app as a second,
unlisted site (rows 17, 25, 26), and the phone can reach it through `adb reverse`.

PC side (done, no phone): the demo previously verified everything with scope `127.0.0.1`, so a tier-B
request minted at `localhost` failed `sig_invalid`; tier A and tier C did not depend on scope. Fixed in
`apps/demo/server.mjs` (one verifier per scope, shared store) and proved by
`apps/demo/tests/origin-scope.test.mjs` (fail-first: 2 of 6 red on the old server). Phone side: PENDING.

## Setup (orchestrator)

Temp operator file (never edit the committed `apps/scanner/operator.json`). Tier C ON, only
`127.0.0.1` pinned, open age verification (`"verifiers": "any"`), so `localhost` is on no list:

```
mkdir -p /tmp/zkagent-item24
cat > /tmp/zkagent-item24/operator.json <<'JSON'
{
  "schema_version": 1,
  "operator": { "name": "zkagent item 24 POC", "contact_url": "https://github.com/hamr0/zkagent/issues" },
  "tiers": "A+B",
  "thresholds": [15, 16, 18, 21, 60, 65],
  "verifiers": "any",
  "multi_threshold_verifiers": [],
  "evidence_plug": "none",
  "tier_c": { "enabled": true },
  "tier_c_verifiers": ["127.0.0.1"],
  "strings": { "app_name": "zkagent Scanner" }
}
JSON
```

(The committed reference file already has `"verifiers": "any"`, `tier_c_verifiers: []`, tier C off.
`apps/scanner/poc/operator.tierc-on.json` is NOT usable here: its `verifiers` is the list
`["127.0.0.1","localhost"]`, which would put `localhost` on a list and defeat row 25.)

Build, install, tunnel, demo (JDK 17; do not run `operator-json-negative.sh` alongside gradle):

```
cd /home/hamr/PycharmProjects/zkagent/apps/scanner
./gradlew :app:assembleRegularDebug -PoperatorJson=/tmp/zkagent-item24/operator.json
git status --short            # expect clean
adb uninstall com.zkagent.scanner
adb install app/build/outputs/apk/regular/debug/app-regular-debug.apk
adb reverse tcp:8787 tcp:8787
adb reverse --list            # expect: tcp:8787 tcp:8787
cd /home/hamr/PycharmProjects/zkagent/apps/demo && npm start   # 127.0.0.1:8787, loopback only
```

Logcat (start before each step, confirm it is alive):

```
adb logcat -c
adb logcat -v time -s MainActivity RequestTrust HandoffClient AndroidRuntime --pid=$(adb shell pidof com.zkagent.scanner) > /tmp/zkagent-item24/logcat.txt &
```

(If the app is not running yet, `pidof` is empty: capture without `--pid` and grep for `zkagent` after.)

Launching a link from the PC (force-stop first, whole `am start` string quoted, `-p` the package):

```
adb shell am force-stop com.zkagent.scanner
adb shell "am start -a android.intent.action.VIEW -d '<av:// link, whole link>' -p com.zkagent.scanner"
```

How a localhost link is made today: open `http://localhost:8787` in the PC browser. The page uses
relative calls, so the request and response addresses in the `av://` link carry the host the page was
opened on (`localhost:8787`); for tier A/B copy or read the `av://` link the page shows. Tier C: the
page's tier-C control appears on `localhost` too (loopback gate), or run
`node apps/demo/scripts/tierc-poc-request.mjs --tier C --host localhost --port 8787 --months 6 --launch`
(hostnames accepted by the loopback gate: `127.0.0.1`, `localhost`, `[::1]`). The 127.0.0.1 control
link is the same thing opened at `http://127.0.0.1:8787`. The `av://` link must be confirmed to contain
`localhost%3A8787` (localhost) or `127.0.0.1%3A8787` (control) before launching.

## Owner steps (phone only; the orchestrator launches the links)

Before step 1: Settings in the app, "Allow additional verifications" is OFF (fresh install).

1. Open the tier-A link made at `localhost` (arrives in the app).
2. Tap Verify; hold the document to the phone; wait for the app to finish.
3. Read the post-scan dialog aloud.
4. Turn ON "Allow additional verifications" in Settings.
5. Open the tier-C link made at `localhost`.
6. Read the screen aloud as it arrives. Do not tap Verify.
7. Open the tier-C link made at `127.0.0.1`.
8. Tap Verify; hold the document to the phone; wait for the app to finish.
9. Read the post-scan dialog aloud.

## Expected (read after the steps)

- (a) Steps 1-3: the app accepts the `localhost` link (no allowlist or network refusal), the chip read
  runs, the dialog reports delivery of the age answer. The demo page opened at `localhost:8787` shows a
  correct verdict (over the threshold, allowed). Demo log: `request_uri GET` then a `verdict ... ok=true
  allowed=true`.
- (b) Steps 4-6: a refusal popup on arrival, no chip read, no Verify tap needed (D112); red text (D113).
  The demo page shows the one generic "Refused". In-app Log reason names the site not being pinned
  (D110, #53). Demo log shows an `access_denied` refusal for tier C.
- (c) Steps 7-9: the `127.0.0.1` tier-C request is NOT refused on arrival; the app asks for the
  tier-C checks, reads the chip, and delivers. The page shows the tier-C result.
- Risk check: if (a) or (b) show a connection failure ("could not fetch"/unreachable) rather than the
  expected outcome, `localhost` is not reaching the PC (IPv6 `::1` on the phone vs `adb reverse`, which
  forwards the IPv4 loopback). Record it as BLOCKED; do not widen the demo bind.

## Results (owner run, 2026-11-02, Pixel 6a, debug build of 7a79ca0, tier C ON, `tier_c_verifiers ["127.0.0.1"]`, `"verifiers": "any"`)

The owner read every screen; the demo log was read independently. Verdict: **PASS**. Value-free: no document field
or name appears here.

| Step group | Expected | Result | Notes |
|---|---|---|---|
| (a) tier A at `localhost` | age answer delivered, page verdict allowed | PASS | Owner read "Delivered" with a green check. Demo log: `verdict tier=A threshold=18 ok=true allowed=true`. |
| (b) tier C (`nationality_is`) at `localhost` | refusal popup on arrival, nothing read | PASS, with a correction to the expected text | Owner read "This website is not approved to ask additional questions about your document. Nothing was read and nothing was sent." App Log: `REFUSED on arrival (D112) cause=not_pinned chip_read=false mint=false`; also `generic refusal NOT sent (no verified, pinned, operator-enabled request) cause=not_pinned`. The demo stayed `pending`: the app tells an unpinned site nothing. The page therefore shows "No answer" on expiry, not "Refused" (D122 amendment). |
| (c) tier C `expiry_beyond 3` at `127.0.0.1` (control) | works as normal | PASS | Owner read "Done. 1 yes or no answer was sent to the website. Nothing else left your phone. Go back to the website to continue." Demo log: `tier=C ok=true allowed=true answered=expiry_beyond:3=true`. |
| Risk: `localhost` reaches the PC through `adb reverse` | yes | PASS | On the phone `localhost` resolves via `/system/etc/hosts` to `127.0.0.1` only (no `::1`), and `adb reverse tcp:8787` carries it: a raw HTTP 200 from the phone shell for both hosts. |

The expected text for (b) above ("The demo page shows the one generic Refused", "Demo log shows an `access_denied`
refusal") was wrong and is superseded by the observed result: for an unpinned site the app sends no refusal at all
(D122 amendment, owner: "Correct the plan").

Gotcha for the device-test guide: an orchestrator `am force-stop` before launching a link wipes the document fields the
owner has already typed in the app; the first attempt of (b) expired that way. When fields are typed, launch the link
without force-stop.
