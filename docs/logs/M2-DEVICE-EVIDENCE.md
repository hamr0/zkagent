# M2 — Device Evidence (opening POC, device half)

**Status**: Mode-A same-device roundtrip **CONFIRMED end-to-end** on a real Pixel 6a
(happy path + on-device negative). DC API probe **RUN, RESULT NEGATIVE** (`NotAllowedError`,
no registered Credential Manager provider) — a finding, not a defect. No chip was read, no
tier B on device, no Credential Manager provider was registered. No PII, no document data —
this POC has no chip scan in it at all (see SETUP).

---

## Method — how this doc was produced, and a contention note

This doc was written **after the run**, reconstructed entirely from artifacts already on
disk: `spikes/m2-handoff/android/screenshots/01`–`10`, `spikes/m2-handoff/dcapi-test.html`,
the Kotlin source (`spikes/m2-handoff/android/app/src/main/java/test/zkagent/m2handoff/MainActivity.kt`),
`spikes/m2-handoff/android/app/src/main/AndroidManifest.xml`, `spikes/m2-handoff/scripts/rogue-relay.mjs`,
`spikes/m2-handoff/server.mjs`, and `spikes/m2-handoff/README.md`. Every screenshot below was
opened and read directly (not inferred from its filename); where a filename and its actual
content disagree, that is called out explicitly rather than papered over.

**Provenance / contention**: the entire on-device run (all 10 screenshots, `dcapi-test.html`)
was produced by one continuous agent session that predated a rate-limit interruption. The
orchestrator briefly believed that agent had died and spawned a second (this) agent as a
duplicate; both agents drove the same physical device for a short window (roughly the period
covering screenshots 06–10) before the orchestrator identified the collision and killed the
duplicate's phone access. During that window this author (the duplicate) independently
force-stopped the app, relaunched Chrome, fired an `av://` intent by hand, and toggled the
screen — actions that were **not** saved as numbered artifacts but that plausibly caused the
tab-focus churn visible in screenshot 09 (see Finding 2) and may have added latency to
whatever was mid-flight in Chrome at the time (see Finding 4's timing caveat). Nothing in this
doc is based on that duplicate agent's own screenshots; only the 10 files that ship in the
repo are cited as evidence.

---

## SETUP

| Component | Value |
|---|---|
| Device | Pixel 6a (`bluejay`), adb serial `34011JEGR02358` |
| Browser | Chrome 151.0.0.0 (from the DC API probe's own `navigator.userAgent`, screenshots 07/10): `Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/151.0.0.0 Mobile Safari/537.36`. Note the UA string reports "Android 10; K" — a known WebView/Chrome UA-reduction quirk, not the device's real OS version (Android 17 per project memory); recorded here so a future implementer isn't misled by it. |
| Spike app | package `test.zkagent.m2handoff`, single `MainActivity`, `singleTask` launch mode, `android:exported="true"` (source: `AndroidManifest.xml`) |
| Verifier server | `spikes/m2-handoff/server.mjs`, `npm start` → `http://127.0.0.1:8790` (age-gate demo page, mode-A/mode-B buttons — screenshot 01) |
| Rogue relay (on-device negative) | `spikes/m2-handoff/scripts/rogue-relay.mjs`, `REAL=http://127.0.0.1:8790 PORT=8791 node scripts/rogue-relay.mjs` → `http://127.0.0.1:8791` (per its own header comment and the `REAL`/`PORT` env vars read at the top of the file) |
| DC API test page | `spikes/m2-handoff/dcapi-test.html`, served at `http://127.0.0.1:8792/dcapi-test.html` (screenshots 07–10). **How port 8792 was served is not recorded on disk** — neither `server.mjs` nor `README.md` mention it, so it was evidently an ad hoc static server (e.g. `python3 -m http.server` or similar) started and torn down by the original agent outside the files this doc could inspect. Flagged as an undetermined mechanism, not a defect. |
| adb reverse | Confirmed by the orchestrator (not re-verified by this agent, per instruction) that `adb reverse --list` is now empty — ports 8790/8791(/8792) were reverse-forwarded from the device to the host during the run and have since been torn down |
| Chip / PII | **None.** This POC never scans a passport or ID chip and carries no document data — every credential in it (`over_threshold`, tier-A/B claims) is synthetic. The DC API request in `dcapi-test.html` never resolves far enough to touch any real credential (see Finding 4). |

---

## FINDINGS

### Finding 1 — Mode-A same-device happy path confirmed end-to-end (screenshots 01–04)

1. **01** (`01-agegate-page.png`, 6:56): the age-gate demo at `127.0.0.1:8790` loads in Chrome, showing "Verify your age (mode A)" / "(mode B)" buttons, sourced from `server.mjs`'s described flow.
2. **02** (`02-tx-created-avlink.png`, 6:56): after tapping mode A, the page shows the same-device app link as both a clickable `av://authorize?client_id=...&request_uri=.../wallet/request.jwt/Ig7OaMjYG` anchor and, separately, a "Cross-device" text block with the identical link (QR rendering is explicitly TODO per the page's own "TODO: render this link as a QR image" text — matches README's "QR is TODO" note).
3. **03** (`03-app-presented-tierA.png`, 6:57): the app (`MainActivity`, red-vs-green background per `render()` in the Kotlin source — green here) shows its working log: fetched `request.jwt` (1111 chars), JWS header `{"alg":"ES256","typ":"oauth-authz-req+jwt","kid":"dev-request-signer-1"}`, **"request JWS verified (ES256, kid=dev-request-signer-1)"**, then `POST direct_post -> HTTP 200 { "accepted": true }`.
4. **04** (`04-page-poll-allowed.png`, 6:59): the page's poll shows `response received / ALLOWED (over threshold)` with the verdict JSON verbatim:
   ```json
   {
     "tier": "A",
     "reason": "no-evidence-required",
     "evidence": [],
     "ok": true,
     "allowed": true
   }
   ```
   This is chiproof's tier-A bare-evidence verdict shape (D27), unmodified end-to-end from the device's `direct_post` through the poll endpoint — exactly the invariant the spike is built to demonstrate (`ok:true`/`allowed:true` only on a genuine pass).

Elapsed wall-clock across screenshots 01→04: roughly 3 minutes (6:56→6:59), but that includes
manual/agent interaction time (reading the page, tapping, waiting for the poll), not isolated
protocol latency — see TIMINGS below for what is and isn't a real measurement.

### Finding 2 — on-device negative: tampered request object is refused, transaction stays pending (screenshots 05, 06, and dex verification)

`rogue-relay.mjs` sits between the phone and the real server: it proxies `POST /ui/presentations`
and the poll endpoint straight through, but on `GET /wallet/request.jwt/{id}` it fetches the
real, validly-signed request object and then **edits the decoded payload's
`zkagent.challenge.threshold` to `16` after signing**, leaving the original ES256 signature
bytes untouched — so the JWS is well-formed but its signature no longer matches its payload.

- **05** (`05-rogue-tx-created.png`, 7:00): the rogue relay page (`127.0.0.1:8791`) after
  "Start tampered mode-A request" — shows the (rogue-base) `av://` app link and the poll
  status already reading `REAL tx still PENDING (wallet refused) ✓` / `{"status":"pending"}`.
  Note the checkmark text is the *page's own* optimistic label (it prints that on every
  non-`done` poll), not yet a confirmed outcome at the moment of this screenshot — screenshot
  06 is the actual on-device confirmation.
- **06** (`06-app-refused-tampered.png`, 7:04): the app's log shows it fetched the same
  `request.jwt` shape (1111 chars, same ES256/oauth-authz-req+jwt header) **and stops there** —
  no "request JWS verified" line, no `POST direct_post` line, and the background is the app's
  red/refuse color. The visible text does not include the `REFUSED` title or
  `Did NOT post any response. / reason = ...` lines from `refuse()` in `MainActivity.kt` — this
  agent could not fit those extra lines inside the visible screenshot bounds and cannot rule out
  that they render above the captured area or were cropped by the capture tool used at the time. What
  **is** independently confirmed, not inferred from the picture alone: this agent extracted the
  compiled app's `classes3.dex` from the installed APK
  (`spikes/m2-handoff/android/app/build/outputs/apk/debug/app-debug.apk`) with `unzip` + `strings`
  and confirmed the literal strings `REFUSED`, `signature_invalid`, and `Did NOT post any
  response.` are present in the shipped code (i.e. the refusal path exists and is reachable —
  it is not a build artifact bug), and the source (`refuse()`, `verifyEs256()`, `runFlow()` in
  `MainActivity.kt`) shows the only path that stops logging after "JWS header: ..." without
  reaching "request JWS verified" is a `refuse()` call — malformed/invalid signature, matching
  exactly the rogue relay's tamper. Combined, this agent treats the on-device refusal as
  established, but flags that the screenshot alone does not visually prove the `REFUSED` title
  rendered — a future run should scroll/re-screenshot to capture the full text if that visual
  confirmation matters.
- The real transaction never received a `direct_post`: `rogue-relay.mjs` logs
  `[rogue] !!! app POSTed direct_post to the rogue relay — wallet did NOT refuse` only if the
  app ever tries to post to it, and no screenshot or artifact shows that message or a `done`
  status. Given the classes3.dex/source corroboration above, this agent treats "transaction
  stayed pending" as established, though the specific pending-JSON screenshot for *this exact*
  transaction id was not captured as a separate numbered artifact (05's pending JSON is for the
  same run, one poll tick earlier).

### Finding 3 — `av://` was used instead of `https` App Links, and why (not a defect)

`server.mjs`'s `LINK_SCHEME` config comment and `AndroidManifest.xml`'s own inline comment both
record the same rationale: the EU Age Verification Blueprint's AV Profile mandates `av://` as a
fallback ("at least `av://` MUST be supported"), while `https` App Links are the Profile's
*primary* same-device method but require a hosted `/.well-known/assetlinks.json` for Android to
verify the app-link ownership before deep-linking. This spike has no hosted origin, so it
targets `av://` as the on-device primary (`AndroidManifest.xml`'s intent filter has
`android:autoVerify="false"` and matches `android:scheme="av" android:host="authorize"` only).
**This is a scope-appropriate simplification of the spike, not a finding that App Links don't
work** — a real deployment would need a verified `assetlinks.json`-backed App Link host, which
is real infrastructure work this spike deliberately didn't build.

### Finding 4 — DC API probe: ran, rejected with `NotAllowedError` (screenshots 07–10, one screenshot mismatched)

`dcapi-test.html` calls `navigator.credentials.get({ digital: { requests: [{ protocol:
'openid4vp-v1-unsigned', data: { ... dcql_query for eu.europa.ec.av.1 / age_over_18 ... } }] } })`
— the DC API envelope shape from `docs/logs/M2-CAPTURE.md` Finding 5 — with **no Credential
Manager provider registered** on the device (this run did not touch Play Console, did not
register the spike app or any app as a provider; see PENDING).

- **07** (`07-dcapi-feature-detect.png`, 7:05): page loaded at `127.0.0.1:8792/dcapi-tes[t.html]`,
  tab 3. Feature detection reads `window.DigitalCredential present: true`,
  `navigator.credentials.get present: true` — **the API surface exists in Chrome 151 on this
  device**, before any call is made. Result box: `(not run yet)`.
- **08** (`08-dcapi-system-consent.png`, 7:06): after tapping "Call
  navigator.credentials.get({digital})", the underlying text changes to "calling
  navigator.credentials.get(...)" and a **system-level modal** appears: *"Do you trust this
  site with your data? http://127.0.0.1:8792 wants to use personal info from your digital
  wallet."* with Cancel/Continue buttons. This is Android's own Credential Manager consent
  prompt, not page content — it fires even with zero registered providers, i.e. Android shows
  the trust prompt before it knows whether anything can answer it.
- **09** (`09-dcapi-info-not-found.png`, 7:06): **this agent read the actual image and it does
  NOT show a DC API result.** Its content is the ROGUE relay page (`127.0.0.1:8791`, tab 4,
  freshly loaded, no transaction started) — unrelated to the DC API probe despite the filename.
  This agent cannot determine what the filename's implied "info not found" state (the
  intermediate `NotFoundError`/provider-search state one would expect between the consent
  dialog and a final rejection) actually looked like; the artifact does not establish it, and
  this agent treats it as an **explicit gap**, not a finding, and notes the timing lines up with
  the cross-agent tab-focus contention described in the Method section above — this agent was
  independently interacting with the rogue-relay page on the device in roughly the same
  1-minute window (7:06).
- **10** (`10-dcapi-js-rejection.png`, 7:09): back on `127.0.0.1:8792/dcapi-test...`, tab 9
  (tab count climbed 3→9 across the run, consistent with repeated Chrome/app-switch churn). The
  Result box shows, verbatim:
  ```
  REJECTED in 13563 ms
  name: NotAllowedError
  message: Request is cancelled.
  toString: NotAllowedError: Request is cancelled.
  ```

**Reading of Finding 4, stated carefully**: the DC API surface is present and callable in
Chrome 151 on this Pixel 6a; invoking it with an OpenID4VP digital-credential request DOES
surface Android's system consent prompt even absent any registered provider; the end state is a
JS-visible rejection (`NotAllowedError: Request is cancelled`) roughly 13.6 seconds after the
call. This agent explicitly does **not** claim to know whether the ~13.6s reflects (a) Android
searching for and timing out on zero matching Credential Manager providers, (b) the human/agent
tapping "Cancel" or dismissing the dialog rather than a pure platform timeout, or (c) added
latency from the concurrent-agent contention described in the Method section — screenshot 09's
mismatch means the interstitial state that would disambiguate this was not actually captured.
This is recorded as an **undetermined limitation**, not resolved by inference.

### Finding 5 — Android JWS verification note (ES256, ieee-p1363 → DER), for a future implementer

`MainActivity.kt`'s `verifyEs256()`/`p1363ToDer()` (lines ~151–181) document a real interop
trap: the request-object JWS's signature bytes are raw 64-byte `R || S` (ieee-p1363, the JWS/JOSE
convention per RFC 7518), but `java.security.Signature.getInstance("SHA256withECDSA")` on
Android expects a DER `SEQUENCE(INTEGER r, INTEGER s)`. The app hand-rolls the P1363→DER
conversion (`BigInteger(1, ...)` per half, then a manual DER SEQUENCE/INTEGER byte-writer) before
calling `Signature.verify()`. This is exactly the kind of vetted-library-avoidance risk called
out for chip parsing in project memory, generalized to JOSE — a real implementation should use a
proper JOSE library rather than hand-rolled ASN.1, but the spike's manual version is a useful,
working reference for the conversion itself.

---

## TIMINGS

Recovered only from screenshot status-bar clocks (minute resolution) — **not** instrumented
protocol timing, and includes human/agent interaction time in every gap:

| Stage | Screenshot | Clock |
|---|---|---|
| Age-gate page loaded | 01 | 6:56 |
| Mode-A tx created, app link shown | 02 | 6:56 |
| App presented tier-A, direct_post accepted | 03 | 6:57 |
| Page poll shows ALLOWED | 04 | 6:59 |
| Rogue tx created | 05 | 7:00 |
| App refused tampered request | 06 | 7:04 |
| DC API page loaded, feature-detected | 07 | 7:05 |
| DC API consent dialog shown | 08 | 7:06 |
| (gap — mismatched artifact, see Finding 4) | 09 | 7:06 |
| DC API JS rejection observed | 10 | 7:09 |

The one precise, in-band measurement available is from the DC API result itself:
**`REJECTED in 13563 ms`** (screenshot 10, `performance.now()` delta inside `dcapi-test.html`) —
this is the only number in this doc that is an actual instrumented measurement rather than a
clock read.

Everything else (mode-A roundtrip latency, on-device negative refusal latency,
`request.jwt`-fetch-to-refusal latency) was **not measured** — no instrumentation exists for it
in this spike, and minute-resolution status-bar clocks aren't precise enough to report a number.

---

## What this run did and did NOT establish

**Did establish:**
- A real Android app (not a headless script) can fetch a request-by-reference over HTTP, verify
  an ES256-signed request object against a pinned key, build a tier-A bare `zkagent/1`
  presentation, and `direct_post` it — and the verifier's poll endpoint reflects chiproof's
  verdict shape unmodified, on-device, over the `av://` same-device flow.
- The same app correctly refuses a request object whose payload was tampered after signing
  (signature no longer matches), and does not post — corroborated by both the screenshot
  sequence and a direct dex-string check of the installed APK.
- Chrome 151 on this device exposes `navigator.credentials.get({digital})` and Android surfaces
  its own system consent UI for it, but the call ends in `NotAllowedError` with zero registered
  providers.

**Did NOT establish:**
- Nothing about chip reading — this POC never scans a passport or ID document; no chip
  interaction of any kind occurred.
- Tier B on a real device — no `sig-ed25519/1` evidence flow was run on the phone; the
  Mode-B button on the age-gate page (screenshot 01) was never exercised in this run.
- Whether a *registered* Credential Manager provider changes the DC API outcome — this run
  deliberately did not register the spike app (or any app) as a Credential Manager provider, per
  the "no Play Console / Play Services registration" instruction for this run. What that
  registration would require is a plain Android platform question (a `CredentialProviderService`
  declared in the manifest, a matching provider-selection entry, and — per Android platform
  behavior generally — a real, non-throwaway signing story), not something this spike attempted
  or measured.
- What the true interstitial DC API state looks like between the consent dialog (08) and the
  final rejection (10) — screenshot 09 does not show it (Finding 4).
- Any precise protocol-level latency beyond the single `13563 ms` DC API measurement.

---

## PENDING

- **Two-instance verifier pattern.** `server.mjs` runs two separate chiproof verifier instances
  (one per mode) because chiproof's `evidence.require()` is instance-global, not per-tier
  (documented in both `server.mjs`'s header comment and `README.md`). **chiproof is now at
  0.3.0 and reportedly supports a per-tier `require`** (per project memory) — migrating this
  spike off the two-instance workaround to a single instance is real follow-up work, not done
  here, and this doc does not verify the 0.3.0 capability claim itself (that would mean touching
  `packages/chiproof`, out of scope for this run).
- QR rendering for the cross-device app link (page 02's own "TODO" text) — same as recorded in
  README.md, not attempted here.
- What actually served `dcapi-test.html` on port 8792 is not recorded anywhere on disk (see
  SETUP) — worth writing down properly if this spike is extended, so a future run doesn't have
  to reconstruct it from screenshots.
- Screenshot 09's content mismatch (Finding 4) — if the interstitial DC API state matters for a
  future decision, it should be re-captured in a run without concurrent-agent device contention.
- Registering a real Credential Manager provider and re-running the DC API probe against it is
  the natural next step if the DC API path is pursued further — explicitly not done here per
  the "no Play Console / Play Services registration" instruction.

---

## CLEANUP

Per the orchestrator's direct verification (not re-checked by this agent, per instruction — this
run did not touch the phone):

- `test.zkagent.m2handoff` is uninstalled: `pm list packages | grep zkagent` returns empty.
- `adb reverse --list` is empty (the 8790/8791 reverse-forwards are torn down).
- Both `node server.mjs` and `node scripts/rogue-relay.mjs` processes are stopped.

The killed original agent completed this cleanup itself before the orchestrator's kill took
effect, per the orchestrator's report.
