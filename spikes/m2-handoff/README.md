# spikes/m2-handoff — M2 POC step (b): test verifier website

**THROWAWAY SPIKE** (convention: `spikes/m0`, `spikes/m1-*`). Not shipped code, not
published, no PII, rung-1 / mode A / age-verification only. It does actually run.

A zero-dependency (beyond `chiproof`, consumed as-is via `file:../../packages/chiproof`)
Node verifier website implementing the EU-Blueprint-shaped same-device flow recorded in
`docs/logs/M2-CAPTURE.md` Finding 1 (reference verifier `av-web-verifier-ui`):

| Wire step | Endpoint here | Capture source |
|---|---|---|
| page creates transaction | `POST /ui/presentations` | Finding 1, website/backend side |
| app fetches request by reference | `GET /wallet/request.jwt/{requestId}` | Finding 1, wallet/app side |
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
`CHALLENGE_SECRET` (dev default baked in — spike only).

## Deliberate simplifications (recorded, not hidden)

- **`request.jwt` serves unsigned JSON, not an ES256-signed JWT.** The AV Profile does
  not require JAR ("explicitly not required"); the reference verifier signs by default.
  If step (c) needs a signed request object, that is new work — escalate first.
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

### PROPOSED byte layout — for the owner to confirm, NOT a settled spec

FR12 says the exact layout is an M2 implementation detail fixed when the plug is
built; this spike is that first build. Candidate:

```
message = sha256( utf8("sig-ed25519/1\n")        // domain separation
                  || sha256(canonicalize(claim)) // claim binding
                  || base64urlDecode(nonce)      // challenge binding
                  || utf8(scopeDomain) )         // scope binding
evidence item = { "type": "sig-ed25519", "version": 1, "data": { "key_id", "sig" } }
sig = base64( Ed25519(attesterPrivateKey, message) )
```

Deviation from D30's literal wording ("nonce + scope"): the **claim hash is also
bound**, because chiproof's plug contract refuses at registration any plug not
declaring `binds.claim === true` (evidence untied to the claim is replayable
across claims). The **zktag is NOT bound**: chiproof's `PlugCtx` does not expose
the presented zktag to plugs, so no plug can bind it with chiproof as-is. Both
points escalated for the owner alongside the layout.

### Mode-B spike caveats (recorded, not hidden)

- **Two chiproof instances, one per mode.** chiproof's `evidence.require` is
  instance-global, not per-tier — a single instance requiring `sig-ed25519/1`
  would fail mode-A bare presentations with `evidence_required_missing`. The
  server routes each transaction to its mode's instance; the
  `state_challenge_mismatch` guard keeps every nonce inside its own instance's
  store. If the real M2 verifier wants one instance serving both modes with
  mode-B-required evidence, chiproof needs a per-tier `require` — an upstream
  decision, not patched here.
- **The zktag is SYNTHETIC** (`SYNTHETIC-DEV-ZKTAG-…`): this spike has no chip
  and no scanner app. A real mode-B client derives the zktag from the document
  number (D9). chiproof checks presence/shape per D21, never derivation (FR11).
- Observed chiproof contract, asserted in tests: wrong-key signature ⇒ plug
  `valid:false` ⇒ `{ok:true, allowed:false, reason:'sig_invalid'}`; missing
  evidence on a tier-B challenge ⇒ `{ok:true, allowed:false,
  reason:'evidence_required_missing'}`; missing zktag ⇒ `zktag_required`.
