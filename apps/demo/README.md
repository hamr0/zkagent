# apps/demo — M3 age-gate demo (PRD §6.3, D73–D76)

The M3 vanilla demo: a plain HTML/CSS/JS website (no framework, no build step) that plays the
role of an adopter's OWN verifier around `chiproof` (the M1 verdict core). It is meant as a
mockup any operator can run and test the M2 scanner app against right away — "clean and
precise," owner's words (D73). No PII is ever shown or sent: tier A and tier B (D19) carry
none.

Two buttons on one page:
- **"Prove you're over 18"** (tier A) — bare evidence, captcha-grade (D27).
- **"Prove you're a unique adult human"** (tier B) — requires `sig-ed25519/1` or `sig-p256/1`
  evidence (D31) and rejects a second scan of the same document at this site as
  "already registered" (§6.3 item 4).

## Run (≤10 lines, D76)

```
adb --version                                # 1. confirm adb is installed
adb devices                                  # 2. enable USB debugging, confirm the phone shows up
adb install app-regular-debug.apk            # 3. sideload the M2 scanner debug build, unmodified
adb reverse tcp:8787 tcp:8787                # 4. forward the phone's localhost:8787 to this machine
cd apps/demo && npm install                  # 5. install deps (chiproof + qrcode)
LINK_SCHEME=av npm start                     # 6. start the verifier on http://127.0.0.1:8787
```
Then open `http://127.0.0.1:8787` in the phone's own browser (Chrome) — 7. This is the ONLY
supported origin for M3 (D76): the scanner's network security config trusts cleartext HTTP
only to `127.0.0.1`/`localhost`/`10.0.2.2` in debug builds and to nothing at all in release, so
a LAN-IP origin or a self-signed HTTPS cert is refused by the released APK. No scanner-side
change is required or in scope (§6.3 item 13).

## Wire steps

| Wire step | Endpoint here | Capture source |
|---|---|---|
| page creates transaction | `POST /ui/presentations` | `docs/logs/M2-CAPTURE.md` Finding 1, website/backend side |
| app fetches request by reference | `GET /wallet/request.jwt/{requestId}` — **ES256-signed request object** (compact JWS, JAR/RFC 9101, `typ: oauth-authz-req+jwt`, content-type `application/oauth-authz-req+jwt`) | Finding 1, wallet/app side; reference verifier signs ES256 by default |
| app POSTs response (browser never sees it) | `POST /wallet/direct_post` (form-encoded) | Finding 1, `response_mode` MUST be `direct_post` |
| page polls for verdict | `GET /ui/presentations/{transactionId}` | Finding 1, website/backend side |

`chiproof` is the verdict core: the presentation request carries a chiproof challenge; the
`direct_post` payload is verified with `verify()`; the poll endpoint returns the chiproof
verdict shape unmodified (never throw; `ok:false` ⇒ `allowed:null`), plus two sibling fields
this demo itself adds — `presentation` (the received presentation payload, for the tier-A
comparison table below) and `zktag_seen_before`/`already_registered` (this demo's own
dedupe state — chiproof stores nothing, D3/FR3).

## Both handoff paths (§6.3 item 2)

- **Same-device app link** — tap the link shown on the page (`av://` custom scheme, or an
  `https` app link; `LINK_SCHEME` selects which — see Config below). The POC evidence in
  `docs/logs/M3-POC-EVIDENCE-2026-09-04.md` used `av://` in-page taps against `LINK_SCHEME=av`.
- **Cross-device QR** — the page also renders a scannable QR image (`qrcode` npm package,
  D68 part b) of the same `av://` link, for a second phone's camera app (D69: the scanner
  carries no in-app QR/camera-scanning dependency itself — the QR is scanned by whatever
  camera app the person already has, which lands on the scanner's existing `av://` intent
  filter, the identical code path a same-device tap uses).

## Tier-A comparison table (§6.3 item 5, DP3)

The page keeps the last two tier-A presentations in browser-tab memory (a reload resets this —
the page says so) and renders a per-field diff, computed by `compare.mjs`. That file is served
as-is at `GET /compare.mjs` and imported unmodified by the page (`<script type="module">`) —
the SAME file `tests/compare.test.mjs` exercises with `node --test`, so there is exactly one
copy of the diff logic, not a page-script reimplementation.

**Fields actually shown** (enumerated by inspecting what `direct_post` receives for tier A —
none of them PII): `spec`, `tier`, `claim.over_threshold`, `claim.threshold`,
`challenge.tier`, `challenge.verbs`, `challenge.threshold`, `challenge.max_scan_age`,
`challenge.issued_at`, `challenge.expires_at`, `challenge.nonce`, `evidence`.

**Correction to §6.3 item 5's wording, device-verified 2026-09-04**: the PRD text says "only
the fresh nonce and its signature may ever land in the differs column." On a real two-scan run
this demo's own tier-A challenge is UNSIGNED (D20: unsigned challenges are accepted at tiers
A/B) — there is no `signature` field at all — and `challenge.issued_at`/`challenge.expires_at`
differ too, every time, because each challenge is minted fresh (`Date.now()`) at the moment the
button is tapped, not at page load. The page's caption states this honestly and computes the
differing-fields list live from the real diff rather than asserting the PRD's literal sentence,
which would be untrue for this build's actual payload shape. Flagged for the owner; not decided
here — see the "Open questions" section below.

## Tier-B "already registered" (§6.3 item 3/4)

A second tier-B scan of the same document at this site renders a distinct "Already registered
at this site" block (not a prefix on the allowed line), plus "zktag already seen at this site:
yes/no" and the raw verdict JSON, all above the fold.

**Chip-authentication caveat (D29), disclosed on every load** (collapsible "About this demo"):
a document without chip authentication (`chip_auth: false` — e.g. some passports) is
clone-replayable: a cloned document mints the identical zktag as the genuine holder's, so
"unique adult human" is only as strong as chip authentication allows. This is an accepted,
stated limitation, not something this demo (or M3) mitigates.

## Store: location and reset

One flat JSON file (`store.mjs`, `JsonFileStore`) backs nonces, both attester-key bindings, and
the zktags-seen dedupe state — persistent across restarts, no in-memory fallback (fail closed,
§6.3 item 9). Default path: `apps/demo/data/store.json` (override with `DEMO_STORE_PATH`).

**To reset the demo** (forget every zktag/device-key binding and start fresh): stop the server
and delete the store file (`rm apps/demo/data/store.json`, or whatever `DEMO_STORE_PATH`
points at) — the next start recreates an empty one. There is no in-app reset control by design
(§6.3 item 9: no store fallback to in-memory once the demo is running in a persistent/
operator-facing mode).

## Config (env)

| Var | Default | Meaning |
|---|---|---|
| `PORT` | `8787` | listen port |
| `BIND_HOST` | `127.0.0.1` | listen address |
| `LINK_SCHEME` | `https` | `av` for the custom-scheme app link (used in the M3 POC evidence run); `https` for the app-link variant |
| `APP_LINK_BASE` | `https://wallet.example.invalid/authorize` | where the `https` app link points (no online hosting is in scope for M3, D76 — this stays a non-resolving placeholder unless an operator has a real wallet app-link host) |
| `SCOPE_DOMAIN` | `127.0.0.1` | must match the HOST the phone's request actually arrives on (D37) — stays `127.0.0.1` for the `adb reverse` recipe above |
| `CHALLENGE_SECRET` | dev-only baked-in default | HMAC key sealing the challenge nonce — a real deployment supplies its own (≥16 bytes) |
| `DEMO_STORE_PATH` | `apps/demo/data/store.json` | the persistent store file (see above) |
| `ATTESTER_KEY_ID` / `ATTESTER_PUBKEY_PEM` | DEV_ATTESTER (Ed25519) | pinned mode-B attester key, Ed25519 alternative |
| `ATTESTER_P256_KEY_ID` / `ATTESTER_P256_PUBKEY_PEM` | DEV_ATTESTER_P256 | pinned mode-B attester key, P-256 alternative |
| `REQUEST_SIGNER_KID` / `REQUEST_SIGNER_PRIVKEY_PEM` | DEV_REQUEST_SIGNER | request-object (JAR) signing key |

Threshold is **hardcoded to 18** (§6.3 item 6, DP4/D74) — there is no `THRESHOLD` env override
and no picker on the page; the preset-bracket / per-origin-lock / named-exception policy D74
actually specifies is scanner-side work (§6.5 S1/S2), not M3 scope.

## Trust list (FR10) — finding, not implemented

§6.3 item 8 requires the verifier to pin the M2 scanner's package name and signing-cert digest
as its one accepted client identity. **This does not exist anywhere in this demo or in
`chiproof` today**, and it is NOT added by this change — see "Open questions" below. Nothing
in the current OpenID4VP-shaped wire contract (`POST /wallet/direct_post`) carries a package
name or signing-cert digest at all; the scanner's HTTP client is indistinguishable from any
other HTTP client at this layer. Building this would mean adding a new field to what the
scanner sends (a wire-contract change) and/or a device-attestation channel (e.g. Play
Integrity, gated behind Track Z per D23) — both out of M3 scope (item 13: no scanner-side
changes) and escalated to the owner rather than invented here.

## Open questions (escalated, not decided in this change)

1. **§6.3 item 5's "only nonce+signature differ" wording** — see the tier-A comparison section
   above. The real diff also flags `challenge.issued_at`/`challenge.expires_at` because tier-A
   challenges here are unsigned (no `signature` field exists at all) and are freshly
   timestamped every scan. The page's caption lists the actual differing fields rather than
   asserting the PRD's literal sentence. Needs owner sign-off: is this acceptable as-is, or
   should chiproof's challenge shape change to make issued_at/expires_at derivable rather than
   stored per-challenge (a chiproof-level change, out of scope here)?
2. **Trust list / FR10 (item 8)** — see above. No implementation exists; needs an owner
   decision on whether/how to add a client-identity channel to the wire contract, and (once a
   real APK is signed) the actual package name + signing-cert digest, which can only come from
   `apksigner`/`keytool` run against the installed APK — not invented here.

## Deliberate simplifications (recorded, not hidden)

- **The DCQL block is shape-only.** It matches the captured query (`mso_mdoc`, doctype
  `eu.europa.ec.av.1`, claim `age_over_18`) but the credential actually verified is chiproof's
  `zkagent/1` presentation riding in the request's `zkagent` member — this demo is not an mdoc
  verifier and makes no interop claim (PRD §5).
- **`ttlMs` in `POST /ui/presentations`** is a testing affordance so the expiry negative is
  exercisable without waiting out the real TTL; not part of the captured wire shape.
- **Verdict semantics note:** a tampered/expired challenge is a *real no* in chiproof
  (`{ok:true, allowed:false, reason:'nonce_forged'|'challenge_expired'}`), not `ok:false` —
  `ok:false` is reserved for "could not check" and always carries `allowed:null`.
- **D28**: `current_date`/`max_scan_age` coarsening is client-side; this bare tier-A/B flow
  carries no scan-dated evidence, so there is nothing to coarsen here.

## Mode B: `sig-ed25519/1` OR `sig-p256/1` (D30/D31)

`POST /ui/presentations` with `{"mode":"B"}` mints a tier-B challenge; the request's `zkagent`
member carries `evidence_required: [["sig-ed25519/1", "sig-p256/1"]]` — an any-of alternatives
group (D31), not one fixed required plug. The device picks whichever its Keystore actually
produced; the verifier accepts either, pinning both attester pubkeys by default
(`ATTESTER_*`/`ATTESTER_P256_*` env vars above, both DEV-ONLY unless overridden).

### Per-origin device keys, trust-on-first-sight (D38)

A presentation may carry the device key's own `pubkey` (SubjectPublicKeyInfo DER, base64)
alongside `key_id`; the verifier recomputes `key_id` from `pubkey` and refuses a mismatch. An
unpinned `pubkey` is trust-on-first-sight: the first presentation for a given
`(scopeDomain, zktag)` binds its key in the store above; a later presentation for that same
pair must carry the identical key or is refused (`attester_key_mismatch`) — never silently
re-bound.

### Testing without a phone

```
node scripts/fake-wallet.mjs --base http://127.0.0.1:8787 [--tier A|B] [--mode valid|tamper|expired|wrongkey|missing|firstsight]
```

Plays the phone's role headlessly (fetch `request.jwt` → verify its JWS → build a presentation →
`direct_post` → poll), so the roundtrip is provable without a device. See the script's own
header comment for what each `--mode` exercises.

## Byte layout, scope-domain history, and other implementation notes

Kept from the pre-M3 spike history (not re-derived here — no behavior in this section changed
by the M3 build pass):

- **Byte layout (v2, owner-confirmed 2026-08-31):**
  ```
  message = sha256( utf8("sig-ed25519/1\n")        // domain separation
                    || sha256(canonicalize(claim)) // claim binding
                    || base64urlDecode(nonce)      // challenge binding
                    || utf8(scopeDomain)           // scope binding
                    || utf8(zktag) )               // zktag binding
  evidence item = { "type": "sig-ed25519", "version": 1, "data": { "key_id", "sig" } }
  sig = base64( Ed25519(attesterPrivateKey, message) )
  ```
  The plug declares `binds: { nonce: true, claim: true, scope: true, zktag: true }`.
- **Scope domain (D37):** the scanner signs mode-B evidence with `scope` = the HOST of its
  verified request origin (never a port or scheme) — `SCOPE_DOMAIN` here MUST match that host
  exactly, which for the `adb reverse` recipe above is always `127.0.0.1`.
- The zktag in `fake-wallet.mjs`'s tier-B runs is SYNTHETIC
  (`SYNTHETIC-DEV-ZKTAG-no-chip-in-this-spike`) — no chip involved; a real mode-B client
  derives the zktag from the document number (D9).

### Android app: cleartext scoped to the dev host

`android/app/src/main/AndroidManifest.xml` (an old dev-side test rig in this directory, not the
M2 scanner) no longer sets app-wide `android:usesCleartextTraffic="true"`; it points
`android:networkSecurityConfig` at a config permitting cleartext only for `127.0.0.1`.
**UNBUILT / UNVERIFIED ON DEVICE** — config-only, not re-run on a Pixel as part of this change.
