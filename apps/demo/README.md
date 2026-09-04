# apps/demo — M3 demo verifier website (moved from spikes/m2-handoff, D73)

Not published to npm (`private: true`), no PII, rung-1 / mode A+B / age-verification
only. Moved from `spikes/m2-handoff` to `apps/demo` per PRD §6.3 item 12 (`git mv`,
history preserved) — no longer a throwaway spike, now real kept code evolving under
M3's scope gate (§6.3). Layout, README, and field-display polish are POST-POC work;
this file still describes the pre-move spike shape until that pass lands.

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
- **QR is rendered as an image (D68 part b, 2026-09-03).** `POST
  /ui/presentations` returns `qr`, a `data:image/png;base64,...` QR code of
  `app_link_av`, via the `qrcode` npm package (pinned exact version,
  spike-only — never added to `packages/chiproof`). The text link is still
  shown alongside it.
- **Cross-device route is a camera app, not an in-app scanner (D69,
  2026-09-03, supersedes D68 part b).** `apps/scanner` carries no QR/camera
  scanning dependency at all — the Google Code Scanner API tried under
  D68(b) was removed the same day after a device test showed it still runs
  in a Play services process and pulls Google's telemetry into the merged
  manifest. This page's rendered QR image (above) is the whole cross-device
  contract: the person scans it with whatever camera app they already have,
  and that app's own `av://` VIEW intent lands directly on the scanner's
  existing intent filter — the identical code path a same-device link uses.
  Device-proven twice (12:19:28, 12:19:38) —
  `docs/logs/M2-DEVICE-SESSION-2026-09-03-EVIDENCE.md` check 7.
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

## Mode B (D30/D31, PRD v1.19): `sig-ed25519/1` OR `sig-p256/1` roundtrip

`POST /ui/presentations` with `{"mode":"B"}` mints a **tier-B** challenge; the
request's `zkagent` member carries `evidence_required: [["sig-ed25519/1",
"sig-p256/1"]]` — an any-of ALTERNATIVES GROUP (D31, chiproof 0.4.0's
`evidence.require` extension), not a single required plug. The device picks
whichever its Keystore actually produced (D36: never a chosen downgrade,
only a fallthrough on failure of the preferred algorithm) and the verifier
accepts either. The verifier pins BOTH attester pubkeys
(`ATTESTER_PUBKEY_PEM`/`ATTESTER_KEY_ID` for Ed25519, `ATTESTER_P256_PUBKEY_PEM`/
`ATTESTER_P256_KEY_ID` for P-256; both default to the DEV-ONLY keypairs in
`dev-attester-key.mjs`/`dev-attester-key-p256.mjs` — labeled like
`CHALLENGE_SECRET`, never for real use). Both plugs are chiproof's own
(`sigEd25519`/`sigP256`, `packages/chiproof/src/plugs/attester-sig.js`) —
this spike's former local `sig-ed25519-plug.mjs` is retired: chiproof 0.4.0's
plug is byte-identical to it (same preimage, same item shape), so carrying a
second copy here bought nothing once the package shipped both algorithms.
Registered through chiproof's **existing** evidence-slot extension contract —
chiproof is consumed as-is, zero edits to the library itself.

```
node scripts/fake-wallet.mjs --base http://127.0.0.1:8787 --tier B [--mode valid|wrongkey|missing|firstsight]
```

(`fake-wallet.mjs`'s `valid` mode exercises the `sig-ed25519/1` alternative
under the pinned DEV_ATTESTER key; the `sig-p256/1` alternative under the
pinned P-256 key is exercised by `tests/tier-b.test.mjs`'s "D31 alternative"
test; `firstsight` — see below — exercises an UNPINNED `sig-p256/1` device
key instead.)

### Per-origin device keys, trust-on-first-sight (D38, 2026-09-01)

The mode-B key is no longer expected to be one fixed, operator-pinned
constant — the scanner generates it **per origin** (per site). A
presentation MAY now carry the key's own `pubkey` (SubjectPublicKeyInfo DER,
base64) alongside `key_id`; the verifier always recomputes `key_id` from
`pubkey` (chiproof's `keyIdFor`, byte-identical to the scanner's Kotlin
`EvidenceSigner.keyIdFor`) and refuses a mismatch. An unpinned `pubkey` is
trust-on-first-sight: `makeVerifier()` wires an `InMemoryAttesterStore` per
algorithm (chiproof 0.4.0, `src/stores/attester.js`) into each plug, and the
first presentation for a given `(scopeDomain, zktag)` binds its key; a later
presentation for that same pair must carry the identical key or it's
refused (`attester_key_mismatch`) — never silently re-bound.

The `ATTESTER_KEY_ID`/`ATTESTER_PUBKEY_PEM` and
`ATTESTER_P256_KEY_ID`/`ATTESTER_P256_PUBKEY_PEM` pinned-key env overrides
keep working exactly as before D38 — a pinned `key_id` is resolved before
the store is ever consulted. This is what lets the owner keep pinning a real
Pixel key today, ahead of the scanner actually sending `pubkey`.

`node scripts/fake-wallet.mjs --tier B --mode firstsight` demonstrates the
new path end-to-end: it generates a fresh, UNPINNED P-256 device keypair,
derives its `key_id` the same way a real device would, and sends `pubkey` —
expects `allowed:true` with `verdict.warnings` carrying
`attester_bound_first_sight`. `tests/tier-b.test.mjs`'s D38 section covers
first-sight bind-then-match, a different key for an already-bound
`(scope, zktag)` (`attester_key_mismatch`), and a `pubkey`/`key_id`
inconsistency (`sig_key_id_mismatch`), all against the real HTTP server.

**Server-side logging (owner request, 2026-09-01):** `POST
/ui/presentations` logs one value-free line on transaction creation
(`transactionId`, `mode`, `ttlMs`); `POST /wallet/direct_post` logs one
value-free line once the verdict is computed (`transactionId`, `tier`,
`ok`, `allowed`, `reason`, `evidence`, and `attester` —
`bound_first_sight`/`matched`/`n/a`, read off the verdict's own
`warnings`/`evidence` fields, no new field added). Never logged: `zktag`,
`nonce`, `pubkey`, `sig`, or `state` — this is a server-side echo of what
the verdict already is, not a new source of device-linkable material.

### Scope domain: real-device bug found and fixed (D37, 2026-09-01)

A live Pixel 6a mode-B run against this spike returned `NOT ALLOWED /
sig_invalid` — the pinned key resolved fine (never `sig_unknown_key`), so
the cause was scope, not the key. Scope is bound into the signature
preimage (see byte layout below): the scanner signs with scope = the HOST
of its verified request origin (D37, `MainActivity.kt:876`) — e.g.
`127.0.0.1` — while this server's `SCOPE_DOMAIN` was hardcoded to an
unrelated fixed string (`'m2-handoff.test'`). One string differs, every
real-device signature fails.

**Fixed**: `SCOPE_DOMAIN` now defaults to `BIND_HOST` (`'127.0.0.1'`, the
address this server always binds — `server.mjs`), not an arbitrary literal;
still overridable (`SCOPE_DOMAIN=...`, exactly the stopgap already applied
to the owner's running instance). **Decision (a) over (b)**: derived ONCE
at startup from the bind address, not per-transaction from each request's
origin — chiproof's `createVerifier` takes `scopeDomain` as fixed, boot-time
config (no per-call override in `verify()`), so per-transaction derivation
(b) would need one verifier instance per origin, not a per-call scope
parameter; fine for this single-origin spike, flagged as awkward for a
genuinely multi-origin deployment under chiproof's current API.

**Escalated for the PRD, not decided here**: scope is **host only** here,
matching the scanner — but D37's origin-*consistency* check (verifying the
request object's own origin before trusting it, D34) uses the **full
scheme+host+port** origin. That is a deliberate difference in granularity
for two different jobs (a stable per-site pseudonym scope vs. an exact
same-request-object check), not an oversight — recommendation (orchestrator,
not owner-decided): keep scope host-only, keep the consistency check on the
full origin. Needs owner confirmation; the PRD file itself was not edited
by this session.

**The suite's own blind spot, and the fix**: `tests/tier-b.test.mjs` and
`scripts/fake-wallet.mjs` each carried their OWN hardcoded `SCOPE_DOMAIN`
literal, which happened to equal the server's old literal — proving nothing
about a real client's derivation, exactly why this suite passed 17/17 while
a real device failed. Neither file may hardcode a scope or import the
server's constant anymore:
- `scripts/fake-wallet.mjs` derives `SCOPE_DOMAIN` from
  `requestObject.response_uri`'s host, AFTER verifying that request
  object's JWS — the same verified-origin mechanism D37 specifies for the
  real scanner.
- `tests/tier-b.test.mjs` derives it from `new URL(srv.url).hostname` once
  the ephemeral test server is up.

Either derivation disagreeing with the server's actual configured
`scopeDomain` now fails the same way the real device did (`sig_invalid`),
for the right reason, instead of silently agreeing with a copied literal.

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
