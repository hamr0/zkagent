# spikes/m2-handoff — M2 POC step (b): test verifier website

**THROWAWAY SPIKE** (convention: `spikes/m0`, `spikes/m1-*`). Not shipped code, not
published, no PII, rung-1 / mode A / age-verification only. It does actually run.

A zero-dependency (beyond `chiproof`, consumed as-is via `file:../../packages/chiproof`)
Node verifier website implementing the EU-Blueprint-shaped same-device flow recorded in
`docs/logs/M2-CAPTURE.md` Finding 1 (reference verifier `av-web-verifier-ui`):

| Wire step | Endpoint here | Capture source |
|---|---|---|
| page creates transaction | `POST /ui/presentations` | Finding 1, website/backend side |
| app fetches request by reference | `GET /wallet/request.jwt/{requestId}` — **ES256-signed request object** (compact JWS, JAR/RFC 9101, `typ: oauth-authz-req+jwt`, content-type `application/oauth-authz-req+jwt`) | Finding 1, wallet/app side; reference verifier signs ES256 by default |
| app POSTs response (browser never sees it) | `POST /wallet/direct_post` (form-encoded) | Finding 1, `response_mode` MUST be `direct_post` |
| page polls for verdict | `GET /ui/presentations/{transactionId}` | Finding 1, website/backend side |

`chiproof` is the verdict core: the presentation request carries a chiproof challenge
(tier A, bare evidence set `evidence: []` per D27); `direct_post`'s payload is verified
with `verify()`; the poll endpoint returns the chiproof verdict shape unmodified
(never throw; `ok:false` ⇒ `allowed:null`).

## Run

```
cd spikes/m2-handoff
npm install          # installs only chiproof from ../../packages/chiproof
npm test             # node --test — roundtrip + negatives against a real HTTP server
npm start            # http://127.0.0.1:8787 — age-gate demo page
node scripts/fake-wallet.mjs --base http://127.0.0.1:8787 [--mode valid|tamper|expired]
```

`scripts/fake-wallet.mjs` plays the phone's role headlessly (fetch request.jwt → build
tier-A presentation → direct_post → poll), so the whole roundtrip is provable without
the Pixel. `--mode tamper` edits `challenge.expires_at` after minting (breaks the D20
HMAC seal → `nonce_forged`); `--mode expired` waits out a short-TTL challenge.

Config (env): `PORT`, `LINK_SCHEME` (`https` app link primary, `av` custom-scheme
variant per the Blueprint AV Profile), `APP_LINK_BASE`, `SCOPE_DOMAIN`,
`CHALLENGE_SECRET` (dev default baked in — spike only), `REQUEST_SIGNER_KID`/`REQUEST_SIGNER_PRIVKEY_PEM`.

## Deliberate simplifications (recorded, not hidden)

- **RESOLVED (owner decision 2026-08-31): `request.jwt` is now ES256-signed.**
  `jws.mjs` (stdlib-only compact JWS: ECDSA P-256/SHA-256, ieee-p1363 signatures)
  signs the exact request-object JSON as claims, matching the reference verifier's
  default; the fake wallet verifies against the pinned dev signer pubkey and
  REFUSES (exit 3, before any `direct_post`) on a bad signature — covered by a
  rogue-relay negative test. Dev keypair in `dev-request-signer-key.mjs`
  (dev-only, like the attester key); env overrides `REQUEST_SIGNER_KID`,
  `REQUEST_SIGNER_PRIVKEY_PEM` (server) / `REQUEST_SIGNER_PUBKEY_PEM` (wallet).
- **The DCQL block is shape-only.** It matches the captured query (`mso_mdoc`,
  doctype `eu.europa.ec.av.1`, claim `age_over_18`) but the credential actually
  verified is chiproof's `zkagent/1` presentation riding in the request's `zkagent`
  member — this spike is not an mdoc verifier and makes no interop claim (PRD §5).
- **QR is TODO.** The app link is the QR payload; the page shows it as text.
  Cross-device-only polish; no QR npm dependency without escalating.
- **`ttlMs` in `POST /ui/presentations`** is a spike-only affordance so the expiry
  negative is testable; not part of the captured shape.
- **`InMemoryNonceStore` with `allowInMemoryStore: true`** — single-process demo, the
  documented demo carve-out; a real deployment needs an atomic store.
- **Verdict semantics note:** a tampered/expired challenge is a *real no* in chiproof
  (`{ok:true, allowed:false, reason:'nonce_forged'|'challenge_expired'}`), not
  `ok:false` — `ok:false` is reserved for "could not check" and always carries
  `allowed:null`. The load-bearing negative invariant is that the poll never says
  `allowed:true`; the tests assert exactly that plus the §3 invariant.
- **D28**: `current_date`/`max_scan_age` coarsening is client-side; the only trace here
  is a comment in `fake-wallet.mjs` at the point a real client would coarsen (this bare
  tier-A flow carries no scan evidence, so it is a no-op).

## Pending / flagged elsewhere

- `docs/index.md` row for this spike — not edited here by rule.
- Step (c): the Pixel 6a plays `fake-wallet.mjs`'s role over the same wire.

### Android app: cleartext scoped to the dev host (review finding fix)

`android/app/src/main/AndroidManifest.xml` no longer sets app-wide
`android:usesCleartextTraffic="true"` (that permitted plaintext HTTP to ANY
host). It now points `android:networkSecurityConfig` at
`android/app/src/main/res/xml/network_security_config.xml`, which permits
cleartext ONLY for `127.0.0.1` — the `adb reverse` dev host the server
listens on — and denies it everywhere else via `base-config`.
**UNBUILT / UNVERIFIED ON DEVICE**: this is a config-only change; the APK was
not rebuilt and this was not re-run on the Pixel 6a as part of this fix.

## Mode B (D30, PRD v1.15): `sig-ed25519/1` roundtrip

`POST /ui/presentations` with `{"mode":"B"}` mints a **tier-B** challenge; the
request's `zkagent` member carries `evidence_required: ["sig-ed25519/1"]` (D30:
the default mode-B evidence delivery). The verifier pins the attester pubkey
(`ATTESTER_PUBKEY_PEM` / `ATTESTER_KEY_ID` env; defaults to the DEV-ONLY keypair
in `dev-attester-key.mjs` — labeled like `CHALLENGE_SECRET`, never for real use).
The plug (`sig-ed25519-plug.mjs`) is registered through chiproof's **existing**
evidence-slot extension contract — chiproof is consumed as-is, zero edits.

```
node scripts/fake-wallet.mjs --base http://127.0.0.1:8787 --tier B [--mode valid|wrongkey|missing]
```

### SETTLED byte layout — v2, owner-confirmed 2026-08-31

**v1 (no zktag) is SUPERSEDED.** A code review flagged v1 as vulnerable to a
zktag-swap: v1 signed only claim+nonce+scope and the plug registered without
`binds.zktag`, so a relay could rewrite `presentation.zktag` after the
attester signed and the vouch still verified under chiproof's contract
(evidence didn't tie to the presented zktag at all). chiproof 0.3.0 closed the
gap that made this fixable — `PlugCtx` now exposes `ctx.zktag` and a plug may
declare `binds.zktag: true` — and the owner confirmed the fix below.

```
message = sha256( utf8("sig-ed25519/1\n")        // domain separation
                  || sha256(canonicalize(claim)) // claim binding
                  || base64urlDecode(nonce)      // challenge binding
                  || utf8(scopeDomain)           // scope binding
                  || utf8(zktag) )               // zktag binding (NEW, v2)
evidence item = { "type": "sig-ed25519", "version": 1, "data": { "key_id", "sig" } }
sig = base64( Ed25519(attesterPrivateKey, message) )
```

The plug now declares `binds: { nonce: true, claim: true, scope: true, zktag: true }`
and `verify()` refuses (`ok:false, valid:null, reason:'zktag_unavailable_to_plug'`)
if chiproof's router ever calls it without a presented zktag string (defence
in depth only — tier A is refused upstream as `evidence_zktag_unavailable`).

The nonce stays **base64url-DECODED** (raw bytes), matching chiproof's shipped
`signed-receipt/1` plug (`packages/chiproof/src/plugs/signed-receipt.js`) and
the owner's 2026-08-30 ruling recorded there. NOTE: chiproof's own 0.3.0
test-only reference fixture
(`packages/chiproof/tests/fixtures/sig-ed25519-zktag-plug.js`) instead treats
the nonce as a plain UTF-8 string (`utf8(nonce)`) — that fixture is
inconsistent with the shipped plug's convention. Flagged for a later chiproof
cleanup; not changed here, chiproof is out of scope for this spike.

Deviation from D30's literal wording ("nonce + scope"): the **claim hash is also
bound**, because chiproof's plug contract refuses at registration any plug not
declaring `binds.claim === true` (evidence untied to the claim is replayable
across claims). Still flagged for owner confirmation (open, unlike the layout
itself which is now settled).

### Mode-B spike caveats (recorded, not hidden)

- **RESOLVED (chiproof 0.3.0): one verifier instance, both modes.** chiproof's
  `evidence.require` now accepts a per-tier `{A?, B?, C?}` object
  (`require: { A: [], B: ['sig-ed25519/1'] }`), so `server.mjs` runs a single
  `createVerifier()` for both modes — tier A stays bare (D27) while tier B
  requires `sig-ed25519/1` (D30), sharing one nonce store. The former
  two-chiproof-instances workaround (one per mode, routed by `tx.mode`) is
  gone.
- **The zktag is SYNTHETIC** (`SYNTHETIC-DEV-ZKTAG-…`): this spike has no chip
  and no scanner app. A real mode-B client derives the zktag from the document
  number (D9). chiproof checks presence/shape per D21, never derivation (FR11).
- Observed chiproof contract, asserted in tests: wrong-key signature ⇒ plug
  `valid:false` ⇒ `{ok:true, allowed:false, reason:'sig_invalid'}`; missing
  evidence on a tier-B challenge ⇒ `{ok:true, allowed:false,
  reason:'evidence_required_missing'}`; missing zktag ⇒ `zktag_required`;
  zktag-swapped evidence (valid signature over a different zktag than the one
  presented) ⇒ `{ok:true, allowed:false, reason:'sig_invalid'}` (a real no).
