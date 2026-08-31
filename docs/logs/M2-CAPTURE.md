# M2 — Capture: real-world age-verification request flows

**Status**: **Desk capture complete (2026-08-31). Four sources recorded; no live UK gate exercised
(geolocation NL — see Finding 7). This is documentation/source-code evidence, not packet capture.**
Purpose: PRD v1.14 §6 M2 row, POC step (a) — record the FACTUAL wire shape of live AV request
flows so the test verifier website (step b) and the Pixel 6a roundtrip (step c) are built to
observed reality, not an invented shape.

**Rule for this file**: no PII, no real tokens, no captured credential payloads. Field names,
schemes, endpoint shapes, verdicts and dates only.

---

## SETUP — sources and method (2026-08-31)

| Source | Method | Date of evidence |
|---|---|---|
| EU Age Verification Blueprint | Technical portal ageverification.dev (architecture spec + Annex A "AV Profile") and the reference verifier's own configuration docs | Portal fetched 2026-08-31 |
| UK OSA deployments (Reddit, Discord) | Vendor docs (Persona), press/trade coverage, Discord support pages | Fetched 2026-08-31; deployments dated 2025-07 onward |
| zkPassport demo verifier | SDK **source code** — `zkpassport/zkpassport-packages`, `packages/zkpassport-sdk/src/index.ts`, shallow clone 2026-08-31 | Source read 2026-08-31 |
| Android Digital Credentials API | Chrome developers blog (2025-10-03), Corbado status survey (2026) | Fetched 2026-08-31 |

No adult site was visited. No live UK age gate was triggered (Finding 7).

---

## FINDING 1 — EU AV Blueprint: OpenID4VP presentation of an `mso_mdoc`, doctype `eu.europa.ec.av.1`

Source: https://ageverification.dev/av-doc-technical-specification/docs/architecture-and-technical-specifications/
and Annex A (AV Profile): https://ageverification.dev/av-doc-technical-specification/docs/annexes/annex-A/annex-A-av-profile/
Repos: `eu-digital-identity-wallet/av-app-android-wallet-ui`, `av-web-verifier-ui`, `av-srv-web-issuing-avw-py`.

- **Protocol**: OpenID4VP (presentation), OpenID4VCI (issuance). Age attestation is an
  **ISO mdoc (`mso_mdoc`)** with doctype **`eu.europa.ec.av.1`**; the mandated claim is the boolean
  **`age_over_18`** (optional `age_over_NN` for other thresholds — the profile explicitly focuses
  on 18).
- **Invocation, per the AV Profile**: the **W3C Digital Credentials API is the default**
  presentation method, "as specified in ISO/IEC 18013-7, Annex C"; and "at least a custom URL
  scheme **`av://`** MUST be supported" as the app-invocation fallback. Cross-device is a QR code
  the phone scans; same-device is browser/app → AV app directly.
- **Response mode**: `response_mode` **MUST be `direct_post`** (deliberately *not*
  `direct_post.jwt` — the profile's stated rationale is that its threat model excludes malicious
  CAs, so response encryption is dropped). `client_id_scheme` is **`redirect_uri`** (client id =
  the `response_uri`); JAR (signed request objects) explicitly not required by the profile,
  though the reference verifier signs requests with ES256 by default.

### Representative wire shape (reference verifier `av-web-verifier-ui`, field names only)

Source: https://ageverification.dev/av-web-verifier-ui/backend-configuration/

Website/backend side:
- `POST /ui/presentations` — RP backend initialises a transaction; body carries a **DCQL** query
  asking for `proof_of_age` in `mso_mdoc` format, doctype `eu.europa.ec.av.1`, claim `age_over_18`.
- `GET /ui/presentations/{transactionId}` — RP polls for the wallet's response.

Wallet/app side (what the QR / `av://` link points into):
- `GET /wallet/request.jwt/{requestId}` — app fetches the authorization request (by reference).
- `GET /wallet/pd/{requestId}` — app fetches the presentation definition.
- `POST /wallet/direct_post` — app POSTs the vp_token (mdoc device response) back.

So the roundtrip is: **website → own backend (create transaction) → QR or `av://` link →
app fetches request by reference → app POSTs response to `response_uri` (`direct_post`) →
website polls its backend for the verdict.** The browser is never handed the credential; the
response path is app→verifier-backend server-to-server, and the browser learns the outcome by
polling (or, under the DC API, receives the response in-page — Finding 6).

**Same-device vs cross-device**: both specified. Cross-device = QR encoding the request URI;
same-device = the browser opening the AV app via `av://` (or the DC API, which hides the
transport entirely).

---

## FINDING 2 — the two invocation shapes in the Blueprint are layers, not a contradiction

The AV Profile names the **DC API as the default** while the reference verifier documents plain
OpenID4VP endpoints (`request.jwt`, `direct_post`). These compose: over the DC API the *same*
OpenID4VP request rides inside `navigator.credentials.get({digital: …})` and Android's Credential
Manager picks the wallet app; over the fallback, the *same* request is reached via QR or `av://`.
The verifier backend shape (DCQL in, `direct_post` out) is identical either way. Not escalated —
the sources agree once layered; recorded because a naive read looks contradictory.

---

## FINDING 3 — live UK OSA gates are IDV-vendor iframes/redirects, NOT credential presentations

The load-bearing negative result of this capture: **no live UK OSA deployment found presents a
wallet-credential wire shape at all.** What actually shipped in 2025–26:

- **Reddit (UK, since 2025-07)**: partner is **Persona**. User is put through Persona's
  verification UI (selfie or government-ID photo). Persona's two web integration modes
  (docs.withpersona.com, fetched 2026-08-31):
  - **Embedded Flow** — Persona's JS SDK opens the vendor UI **in a sandboxed iframe** on the
    site's own page; completion returns via JS callback
    **`onComplete({ inquiryId, status, fields })`** (plus `onCancel`, `onError`). The site's
    backend then confirms the verdict server-side (webhook/API by `inquiryId`).
  - **Hosted Flow** — full **redirect** to `<subdomain>.withpersona.com/verify`, then redirect
    back to the site.
  Reddit stores only verification status + birthdate; the imagery stays with Persona (≤7 days).
  Sources: https://techcrunch.com/2025/07/15/reddit-rolls-out-age-verification-in-the-uk-to-comply-with-new-rules/ ,
  https://www.biometricupdate.com/202507/reddit-deploys-selfie-and-document-age-verification-from-persona ,
  https://docs.withpersona.com/choosing-an-integration-method
- **Discord (UK/AU trials 2025, global rollout announced for 2026-03)**: partner **k-ID**;
  facial age estimation runs **on-device** (video never leaves the device per Discord support
  docs), ID-upload as the alternative; a Persona experiment for some UK users concluded 2026-02.
  Sources: https://support.discord.com/hc/en-us/articles/33362401287959 ,
  https://idtechwire.com/discord-tests-face-scanning-and-id-upload-for-age-verification-in-uk-and-australia/ ,
  https://piunikaweb.com/2026/02/16/discord-uk-age-verification-persona-vendor-shift/

**Wire shape summary for this class**: invocation = **iframe + JS-SDK callback** (embedded) or
**redirect** (hosted); request format = vendor-proprietary session/inquiry id, not
OpenID4VP/mdoc; response = JS callback with an opaque `inquiryId` + server-side webhook;
same-device only (desktop handoff-to-phone exists inside vendor UIs but is not a
credential-presentation mechanism). **Nothing here for a wallet app to receive** — a
zkagent-style scanner app cannot plug into these flows as they exist today; the plug-in point
they expose is "be an IDV vendor", not "be a credential".

---

## FINDING 4 — zkPassport: HTTPS universal link + encrypted WebSocket bridge back

Source: SDK source, `zkpassport-packages/packages/zkpassport-sdk/src/index.ts` (read 2026-08-31);
demo at https://demo.zkpassport.id/.

- **Invocation**: the SDK builds one HTTPS universal link (rendered as a **QR code**
  cross-device, or a **tap link** same-device — same URL both ways):

  ```
  https://zkpassport.id/r?d=<rp-domain>&t=<requestId>&c=<base64 config>&s=<base64 service
  name/logo/purpose>&p=<bridge pubkey>&m=<mode>&v=<sdk version>&dt=<timestamp>&dev=<0|1>
  ```

  (field names verbatim from source, line 985; values elided). Being an HTTPS app link, Android
  routes it to the app if installed — no custom scheme.
- **Response path**: **not** an HTTP POST to the RP. The SDK and the phone app meet on an
  end-to-end-encrypted **WebSocket bridge** (`@obsidion/bridge`; the `p` param is the SDK's
  session public key; `t` is the bridge topic). The website gets events in-page:
  `onRequestReceived` → `onGeneratingProof` → `onProofGenerated({ proof, vkeyHash, version,
  name })` per sub-proof → **`onResult({ uniqueIdentifier, verified, result })`**.
- **Request format**: proprietary query-builder (`request({name, logo, purpose, scope})` then
  `.disclose()/.gte('age',18)/.done()`), serialized into the `c` param — not OpenID4VP.
- **Same-device vs cross-device**: symmetric by construction; the bridge decouples where the
  proof is generated from where the page runs, so the same mechanism serves both. This is the
  cleanest cross-device story of the three classes captured.

---

## FINDING 5 — Android Digital Credentials API status (dated)

Shipped stable in **Chrome 141, 2025-09-30** (Android + desktop; blog 2025-10-03:
https://developer.chrome.com/blog/digital-credentials-api-shipped). Safari 26 (2025-09-15) ships
it but **only** the `org-iso-mdoc` protocol; Firefox 149 has it behind a flag (Q1 2026). Request
shape:

```javascript
navigator.credentials.get({
  digital: { requests: [{ protocol: "openid4vp-v1-unsigned", data: { /* OpenID4VP params */ } }] }
})
```

Protocol registry is now a hardcoded list: `openid4vp-v1-unsigned|signed|multisigned`,
`org-iso-mdoc`, `openid4vci-v1`. Android requires Play services ≥ 24.0 and a wallet registered
with Credential Manager. Cross-device works via QR-initiated encrypted channel (FIDO-hybrid
style). **Production usage is nascent**: the one confirmed production RP found is California
DMV's OpenCred (v10.0.0, 2026-02-27); the EU AV Profile names the DC API as its default; no UK
OSA gate found using it. Issuance (`credentials.create`) is still an origin trial with an open
wallet-binding vulnerability. (Survey: https://www.corbado.com/blog/digital-credentials-api,
2026.) **Verdict: real and stable on the exact target (Chrome on Android) since 2025-09, but
adoption-wise ahead of deployed reality.**

---

## FINDING 6 — the three observed response paths are structurally different

| Class | Invocation | Request format | Response path | Cross-device |
|---|---|---|---|---|
| EU AV Blueprint | DC API (default) or QR / `av://` link | OpenID4VP + DCQL, `mso_mdoc` `eu.europa.ec.av.1` | app → `POST {response_uri}/wallet/direct_post`; site polls its backend | QR (specified) |
| UK OSA live (Persona-class) | iframe + JS SDK, or redirect | vendor inquiry id (opaque) | JS `onComplete({inquiryId,status,fields})` + server webhook | inside vendor UI only |
| zkPassport | HTTPS universal link (QR = same URL) | proprietary query in `c=` param | E2E-encrypted WebSocket bridge → in-page callbacks | symmetric, via bridge |

A verifier website therefore needs: a backend endpoint able to receive an out-of-band POST
(EU shape), *or* a socket/poll channel to learn the verdict (zkPassport shape) — in both real
credential flows **the browser page never receives the credential directly**; it learns the
outcome via its backend or a bridge. The M1 `chiproof` never-throw verdict sits naturally behind
either.

---

## FINDING 7 — what was NOT captured, and why

- **No live UK gate was exercised.** This machine is geolocated in NL; Reddit/Discord UK gates
  are geo-triggered and were not expected to fire, so no browser session was attempted against
  them. The Persona-class wire shape above comes from the vendor's own integration docs, which
  bind Reddit's deployment only via press reporting, not via a captured session. Recorded as a
  limitation, not worked around.
- **No packet/HAR capture anywhere.** All shapes are from specs, vendor docs, and SDK source.
  The EU reference verifier is deployable (docker) — running it locally IS step (b) of this POC
  and will produce the first real capture.
- **No adult-site flow**, by rule.

---

## What this capture did and did not establish

**Established (cited, dated):**
1. The only *standardised* real-world AV request shape is the EU Blueprint's: OpenID4VP + DCQL
   asking for an `mso_mdoc` doctype `eu.europa.ec.av.1` claim `age_over_18`, response via
   `direct_post`, invoked by DC API (default), QR, or `av://` (Findings 1–2).
2. Live UK OSA gates ship vendor iframes/redirects with opaque inquiry ids — there is no
   credential-shaped request for an app to answer in that class today (Finding 3).
3. zkPassport's web→app→web roundtrip is an HTTPS universal link out and an encrypted WebSocket
   bridge back, with exact URL params and callback names recorded from source (Finding 4).
4. The DC API is stable on Chrome-on-Android since 141 (2025-09-30) with `openid4vp-v1-*`
   protocols, and production adoption is thin but real (Finding 5).

**NOT established — do not state these anywhere:**
- That any site the owner cares about will *accept* an EU-shaped or any other presentation —
  nothing here is an interop claim; zkagent is not part of the EU ecosystem (PRD §5).
- That the Persona/k-ID class will not move to credential presentation later; only that it has
  not as of 2026-08.
- Timing, UX, or reliability of any flow — nothing was executed end-to-end here.
- That `av://` or the DC API works on the Pixel 6a specifically — that is exactly what POC step
  (c) measures.

---

## RECOMMENDATION (owner decides)

**Primary target for the Pixel 6a roundtrip: the EU-Blueprint-shaped same-device flow —
HTTPS app link / custom-scheme invocation carrying an OpenID4VP-style request-by-reference,
response via `direct_post` to the test verifier's `response_uri`, site polls its own backend.**
Reasons: it is the one *documented-to-be-real* credential-presentation shape (Finding 1); it
needs no Credential Manager registration or Play-services gate to demo; both zkPassport and the
EU fallback path prove the link-out/POST-or-bridge-back pattern is what actually ships; and the
test verifier built to it (step b) doubles as the M3 demo skeleton.

**Second experiment, same POC, cheap**: attempt the **DC API** path
(`navigator.credentials.get`, `openid4vp-v1-unsigned`) on the same Pixel 6a in Chrome ≥141 —
it is the Blueprint's declared default and the forward path; treat failure as a finding, not a
blocker (the app must register with Android Credential Manager, an M2-app work item either way).

**Fallback (per PRD, unchanged)**: QR/app-link cross-device — which under the shapes above is
not a different protocol, just a different way to deliver the same request URI.

---

## PENDING

- [ ] `docs/index.md` needs a row for this file (not edited here by rule — flagged).
- [ ] Step (b): deploy/build the test verifier to the Finding 1 shape; first real HTTP capture.
- [ ] Step (c): Pixel 6a roundtrip over the primary mechanism; DC API side-experiment.
- [ ] Re-check whether any OSA gate has adopted DC API/OpenID4VP before M3 claims are written.
