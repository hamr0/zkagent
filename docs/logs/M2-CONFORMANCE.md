# M2 — Conformance: spike wire vs. a real EU reference implementation

**Status**: **Reference verifier backend obtained and RUN locally (Docker); real request/response
pairs captured against it.** `spikes/m2-handoff` was diffed field-by-field against its actual
source, not against docs. Result: the DCQL block in `spikes/m2-handoff/server.mjs` is confirmed
decorative in exactly the way the orchestrator's audit suspected, **plus** at least one deeper,
previously-unknown incompatibility (`client_id`/`aud`) that would independently break interop even
if the DCQL/credential-format problem were fixed. No wire-format or `chiproof` contract change was
made — this file is evidence only, per the task brief.

**Rule for this file (per project convention)**: no PII, no real tokens, no captured credential
payloads. The JWTs and transaction ids quoted below are locally self-generated test artifacts
against a locally-run dev instance — not credentials, not secrets, not anyone's data.

---

## SETUP (2026-08-31)

| Component | Value |
|---|---|
| Host | Fedora 44, Docker 29.7.2 (daemon reachable, rootful), SELinux **Enforcing** |
| Repo cloned | `eu-digital-identity-wallet/av-srv-verifier-endpoint` (active; **replaces** the archived `av-srv-web-verifier-endpoint-23220-4-kt`, last push 2026-01-26, `archived: true`) |
| Clone commit | `787089b7239e39dceaf31c0274d9de745b880194` (shallow clone, `main`, fetched 2026-08-31) |
| Also cloned | `av-web-verifier-ui` @ `7cb50392e3582928c22d03de2df67c60e6ba0574` (not run — see PENDING) |
| Also cloned | `av-lib-android-zkp-age-icao` @ `5f1d806834b819e47913efc0aaf4cfd493c1553f` (read only — feeds FINDING 6) |
| Runtime image | `ghcr.io/eu-digital-identity-wallet/av-srv-verifier-endpoint:latest`, pulled 2026-08-31, digest `sha256:889652f9aeb4df65b1301ee04f0c6d65a38ec2c93abd03988171acfaea6a67ac` — this is a **prebuilt image published by the EU org itself**, not something built here; it corresponds to the `main`-branch source above per the repo's own `docker/docker-compose.yaml` |
| Backend version reported at boot | `v0.9.3-SNAPSHOT`, Spring Boot `4.0.1`, Java `17.0.19` (see boot log below) |
| What ran | The **verifier backend only** (`av-srv-verifier-endpoint` container), on `127.0.0.1:18080`, with the repo's own committed dev `keystore.jks` and `docker-compose.yaml` env vars (`VERIFIER_ACCESS_CERTIFICATE_SIGNING_ALGORITHM=ES512`, `VERIFIER_ALWAYSACCEPTWALLETRESPONSE=true`, etc., copied verbatim from `docker/docker-compose.yaml`) |
| What did NOT run | The `verifier-ui` and `haproxy` companion containers from `docker-compose.yaml` (not needed — the backend's own REST/OpenAPI surface is the thing being diffed) |
| Full wallet/mdoc round-trip | **Not attempted.** Producing a real, verifier-accepted ISO `mso_mdoc` device response requires a signed mdoc credential and an mdoc device-auth implementation — out of scope for this task per the brief ("do NOT... build an issuer"). What was captured is the **request side** (`POST /ui/presentations`, the signed request object it returns) and the **poll side** (`GET /ui/presentations/{id}`), which is exactly the surface `spikes/m2-handoff/server.mjs` implements and the surface the audit questioned. |

### One real blocker hit and resolved (recorded per the brief's "documented blocker is a legitimate finding" rule)

First run attempt failed:

```
Caused by: java.io.FileNotFoundException: /certs/keystore.jks (Permission denied)
```

Root cause: SELinux is Enforcing on this host; a plain bind mount (`-v host:container`) of
`keystore.jks` is not relabeled for the container's context, so the JVM inside the container gets
`EACCES` even though the file is host-world-readable. Fixed by adding the `:z` SELinux relabel
option to the mount (`-v ".../keystore.jks:/certs/keystore.jks:ro,z"`); no toolchain install, no
Dockerfile change, no escalation needed. Recorded because it is exactly the class of "missing X"
blocker the brief asked to document if hit — this one turned out fixable in five minutes but ate
real effort-budget before the fix was found, and would recur for anyone else on an SELinux-enforcing
host following the project's own README verbatim.

Successful boot (excerpt):
```
2026-08-31T05:41:32.471Z INFO ... Starting VerifierEndpointApplicationKt v0.9.3-SNAPSHOT using Java 17.0.19
2026-08-31T05:41:35.368Z INFO ... Netty started on port 8080 (http)
2026-08-31T05:41:35.382Z INFO ... Started VerifierEndpointApplicationKt in 3.293 seconds
```

---

## FINDING 1 — the DCQL block IS decorative, confirmed against the real verifier's own request-object builder

`spikes/m2-handoff/server.mjs` (~line 254) builds a `dcql_query` for `format: 'mso_mdoc'`,
`doctype eu.europa.ec.av.1`, claim `age_over_18`, and *separately* a sibling `zkagent` member
carrying the chiproof challenge — the credential actually checked at `/wallet/direct_post` is
`zkagent/1`, never the mdoc. This was the orchestrator's starting premise; it holds.

**Real evidence, not just re-reading our own code**: a live `POST /ui/presentations` was sent to
the running reference verifier with exactly this DCQL shape:

```
POST http://localhost:18080/ui/presentations
{
  "dcql_query": {"credentials": [{"id": "proof_of_age", "format": "mso_mdoc",
    "meta": {"doctype_value": "eu.europa.ec.av.1"},
    "claims": [{"path": ["eu.europa.ec.av.1", "age_over_18"]}]}]},
  "nonce": "conformance-probe-nonce-1",
  "response_mode": "direct_post"
}
```

Response: `200 OK`, with a real ES512-signed JWS request object. Decoding it (payload only):

```json
{
  "response_uri": "http://localhost:18080/wallet/direct_post/BL-WsnhIa9JbDzZTQz9236LbU_...",
  "aud": "https://self-issued.me/v2",
  "response_type": "vp_token",
  "state": "BL-WsnhIa9JbDzZTQz9236LbU_...",
  "dcql_query": { "credentials": [{"id": "proof_of_age", "format": "mso_mdoc",
    "meta": {"doctype_value": "eu.europa.ec.av.1"},
    "claims": [{"path": ["eu.europa.ec.av.1", "age_over_18"]}]}] },
  "iat": 1788154933,
  "nonce": "conformance-probe-nonce-1",
  "client_id": "Verifier",
  "client_metadata": { "vp_formats_supported": { "dc+sd-jwt": {...}, "mso_mdoc": {...} } },
  "response_mode": "direct_post"
}
```

The reference verifier accepted the AV doctype/claim with **no server-side validation against a
registry** — `dcql_query` is opaque pass-through at `POST /ui/presentations` time (source: no
validation call on `doctype_value`/claim paths found in
`InitTransaction.kt`/`VerifierApi.kt`, and empirically, the malformed-by-AV-Blueprint-standards
request above was accepted and echoed verbatim). This means a request carrying our `zkagent`
sibling field alongside a syntactically valid but never-checked `dcql_query` would *also* be
accepted by this backend's `/ui/presentations` endpoint — the reference verifier's init step is
not, itself, the enforcement point. The enforcement point is the **wallet**, which must read
`dcql_query`, find `mso_mdoc`/`eu.europa.ec.av.1`, and select a matching credential to answer
with — and a wallet holding only a chiproof `zkagent/1` credential has nothing to select. This
confirms the audit's claim precisely: **the DCQL block is not decorative because the verifier
rejects it — it is decorative because no real wallet could ever answer it, and our own fake
wallet routes around it by reading the `zkagent` member instead** (see
`spikes/m2-handoff/scripts/fake-wallet.mjs`, which never reads `dcql_query` at all).

---

## FINDING 2 — a second, independent incompatibility found in the source: `client_id`/`aud`

This was not something the audit or `spikes/m2-handoff/README.md` flagged, and it is not about
DCQL. It means fixing the credential format alone would **not** be sufficient for interop.

Source: `av-srv-verifier-endpoint/src/main/kotlin/eu/europa/ec/eudi/verifier/endpoint/domain/VerifierConfig.kt:228-278`:

```kotlin
sealed interface VerifierId {
    val originalClientId: OriginalClientId
    val accessCertificate: AccessCertificate
    val clientId: ClientId

    data class PreRegistered(...) : VerifierId { override val clientId = originalClientId }
    data class X509SanDns(...) : VerifierId { override val clientId = "x509_san_dns:$originalClientId" }
    data class X509Hash(...) : VerifierId { override val clientId = "x509_hash:$originalClientId" }
}
```

There is **no `RedirectUri` variant.** `client_id` in the real request object is a *configured
identifier* (`"Verifier"` in the dev compose, a `PreRegistered` value), never derived from
`response_uri`. `aud` is hardcoded, not derived either — `RequestObject.kt:43`:

```kotlin
val aud = listOf("https://self-issued.me/v2")
```

(the SIOPv2 legacy self-issued audience convention). A compatibility flag does exist —
`VerifierConfig.kt:301-318`, `redirectUriClientIdInMdocDeviceAuthHandover: Boolean = false` — but
its own doc comment says exactly why it's off by default:

> "Compatibility switch for mso_mdoc device authentication: when true, the OpenID4VP device-auth
> handover is computed with the client identifier in `redirect_uri:<response_uri>` form instead
> of the verifier's own `VerifierId.clientId`. This is required for OpenID4VP 1.0 wallets that use
> the `redirect_uri` client identifier prefix... but it is incompatible with pre-registered / x509
> wallets... Defaults to `false` (standard OpenID4VP behavior)."

`spikes/m2-handoff/server.mjs` (~line 220s) does the opposite of "standard OpenID4VP behavior" as
this reference project defines it: it sets `client_id_scheme: 'redirect_uri'`, `client_id:
responseUri` unconditionally, and never emits `aud` at all. Both are drawn from `docs/logs/M2-CAPTURE.md`
Finding 1, which recorded `client_id_scheme: redirect_uri` as *the AV Blueprint's* stated
mechanism — that capture was accurate to the **Blueprint's own documentation**, but this reference
verifier's actual code treats `redirect_uri`-scheme client identification as a **legacy
compatibility mode for older wallets**, off by default, and structurally absent from its own
`VerifierId` type. This is a real divergence between what the Blueprint docs say and what the
Blueprint's own reference backend does by default — recorded, not resolved, and escalated below.

---

## FINDING 3 — the response endpoint is path-templated, not state-routed

Source: `WalletApi.kt:160`:
```kotlin
const val WALLET_RESPONSE_PATH = "/wallet/direct_post/{requestId}"
```
vs. `spikes/m2-handoff/server.mjs`'s single fixed `POST /wallet/direct_post`, which
disambiguates transactions by a `state` field inside the POSTed body. Functionally this is not an
OpenID4VP violation — the spec does not constrain the shape of `response_uri`, and our spike's
`state` field is exactly the CSRF/session-binding value OpenID4VP itself expects to be checked —
but it means our `response_uri` (`.../wallet/direct_post`) does not match the URI the reference
verifier actually hands wallets (`.../wallet/direct_post/{requestId}`), and any wallet that treats
`response_uri` as an opaque per-transaction URL (rather than parsing `state` out of a shared one)
would have nowhere correct to POST to. **Classified (b)**: a spike simplification, not a spec
violation, but one a real integration would need to fix to be indistinguishable from the
reference shape.

`GET /wallet/request.jwt/{requestId}` matched exactly (`WalletApi.kt:157`,
`REQUEST_JWT_PATH = "/wallet/request.jwt/{requestId}"`) — no divergence there.

---

## FINDING 4 — poll endpoint: real backend returns 400 pre-submission, ours returns 200/`pending`

Live capture:
```
GET /ui/presentations/{transactionId}   (before any wallet response posted)
→ 400 Bad Request, empty body
```//from `VerifierApi.kt` log: "Presentation should be in Submitted state but is in
`RequestObjectRetrieved`" (server-side log, not returned to the client — the client sees only an
empty 400).

`spikes/m2-handoff/server.mjs`'s `GET /ui/presentations/{transactionId}` instead returns
`200 {"status":"pending"}` before submission, and `200 {"status":"done","verdict":...}` after.
This is a genuine shape difference at the polling contract: our spike's page-side JS polls on
`s.status === 'done'`, which would break against the real backend's pre-submission `400`s (it
would need to treat "not 200" as "still pending," not as an error). **Classified (b)**: spike
simplification — reasonable UX choice, incompatible wire shape.

---

## FINDING 5 — HTTP status code on transaction creation: 200 vs. 201

Real: `POST /ui/presentations` → `200 OK` with a `Transaction-Id` response header (see openapi.json,
path `/ui/presentations`, `responses.200`). Ours: `201`. Cosmetic — **classified (a)**, not
interop-relevant (no client here is shown to branch on the exact 2xx code), recorded for
completeness of the diff, not as a real finding.

---

## Comparison table

| Wire element | `spikes/m2-handoff/server.mjs` | Reference (`av-srv-verifier-endpoint` @ `787089b`) | Class |
|---|---|---|---|
| Transaction create | `POST /ui/presentations` → `201` | `POST /ui/presentations` → `200`, `Transaction-Id` header | (a) cosmetic |
| DCQL block | Present, shape-matched, **never checked against the actual credential presented** | Present, accepted **unvalidated** at init time — enforcement is pushed to the wallet's credential-selection step, which a `zkagent`-only wallet cannot pass | (c) — this is the incompatibility the whole task exists to confirm |
| Credential actually verified | `zkagent/1` in a sibling `zkagent` field | An ISO `mso_mdoc`, doctype `eu.europa.ec.av.1`, selected via the DCQL the wallet parsed | (c) |
| `client_id` | `= response_uri` (redirect_uri scheme, unconditional) | Configured `VerifierId` (`PreRegistered`/`X509SanDns`/`X509Hash`); `redirect_uri` form only exists as an off-by-default mdoc-handover compat flag, never the top-level `client_id` | (c) |
| `aud` | absent | `"https://self-issued.me/v2"` (hardcoded, SIOPv2 legacy) | (b)/(c) borderline — absence could fail wallet validation |
| `client_metadata` | absent | Present: `vp_formats_supported` for `dc+sd-jwt` and `mso_mdoc` | (b) |
| Request-object signing | ES256 (our `jws.mjs`), matches CAPTURE's stated default | ES512 in this dev compose (verifier-configurable, not a spec constant either way) | (a) — both are legal `alg` choices, config not spec |
| `GET /wallet/request.jwt/{requestId}` | Matches | Matches (`WalletApi.kt:157`) | — no divergence |
| Wallet response endpoint | Fixed `POST /wallet/direct_post`, `state` in body | `POST /wallet/direct_post/{requestId}` (path-templated) (`WalletApi.kt:160`) | (b) |
| Response body encoding | JSON or form, `vp_token` + `state` | Per OpenAPI: `application/x-www-form-urlencoded`, `vp_token`(+`state`) — same field names, path differs as above | (a)/(b) |
| Poll before submission | `200 {"status":"pending"}` | `400`, empty body (server log: presentation not yet `Submitted`) | (b) |
| Poll after submission | `200 {"status":"done","verdict":{...chiproof shape...}}` | `200`, verifier's own `WalletResponse` shape (mdoc claims + `TrustInfo` when `alwaysAcceptWalletResponse=true`) — structurally unrelated to chiproof's `{ok,allowed,reason}` | (a) different domains, expected |
| Response-code / access-token gating on poll | None | `response_code` query param supported (`VerifierApi.kt:119`) for cross-device flows; not required same-device | (a) not exercised here (same-device only, matches M2-CAPTURE's chosen primary flow) |

---

## FINDING 6 — the EU's own ZK-native age-proof path exists, and it is a **third**, uncomposed wire shape

`av-lib-android-zkp-age-icao` (cloned, read-only, commit `5f1d806`) is an **experimental Android
library, published by the same GitHub org**, that runs a Noir/Barretenberg ZK circuit directly
over an ICAO DG1+SOD+COM triple read from an ePassport/ID card (`README.md`), producing:

```json
{ "data": { "age_over_18": true, "age_over_21": true, "age_over_65": false }, "proof": "..." }
```

No issuer round-trip, no online masterlist call visible in the public API (`ZkpIcao.prove()`
takes the raw chip files and returns a proof + claims map). This is architecturally the same
shape as `docs/product/learnings.md` §6.10-6.11 describes for zkPassport/Track Z — same engine
family too (SRS fetched from "Aztec's server," i.e. Barretenberg, matching zkPassport's own
toolkit per learnings.md §6.10) — built by the EU itself, dated 2026-05-21, `0.0.3-SNAPSHOT`,
explicitly labeled experimental with **hard cryptographic parameter constraints** (RSA-4096 CSC,
RSA-3072 DSC, RSA PKCS#1v1.5/SHA-256 DSC-sig, RSASSA-PSS/SHA-256 SOD-sig, fixed
1600-byte TBSCertificate — i.e. it works for a subset of real-world issuer key configurations,
not all of them).

**Searched this repo's Kotlin sources and markdown for `openid4vp`, `dcql`, `mso_mdoc`,
`OpenId4Vp` — zero matches.** This library is not wired into the `av-srv-verifier-endpoint` /
`av-web-verifier-ui` OpenID4VP flow examined above. It outputs its own bespoke
`{data, proof}` JSON, not a DCQL-selectable credential format, not an `mso_mdoc`. So even inside
the EU's own reference-implementation family, there are **two disconnected age-verification wire
shapes today**: the mdoc/OpenID4VP one this file diffs above, and this ZK one, with no published
adapter between them.

---

## FINDING 7 (answers the task's load-bearing question)

**What would it actually take for zkagent to be answerable by a real EU AV wallet, or for a real
EU verifier to accept a zkagent presentation, as things stand in this codebase?**

Stated plainly, per the brief's "do not soften it" instruction:

1. **`zkagent/1`-in-a-sibling-field is not viable for EU-wallet interop, full stop** — not
   because of a spec technicality but because of Finding 1's mechanism: the DCQL block tells a
   real wallet what credential to present, and a real wallet holding no `mso_mdoc` credential of
   doctype `eu.europa.ec.av.1` has nothing to select. `av-srv-verifier-endpoint` itself doesn't
   enforce the match at init time (Finding 1), so a `zkagent`-carrying request *would be accepted
   by the reference verifier's backend* — the rejection happens one hop earlier, inside any real
   wallet's credential-selection UI, which this project has never built or needed to build. There
   is no configuration flag or protocol trick that closes this gap without either (a) a real
   wallet that understands `zkagent/1`, which no EU-ecosystem wallet does, or (b) zkagent emitting
   something a real wallet already holds, which is the mdoc question below.
2. **Interop does require emitting a genuine `mso_mdoc`** — Finding 1 makes this direct: DCQL
   selects by `format`+`doctype_value`, and the only format any deployed EU wallet holds today is
   the mdoc/SD-JWT-VC family. `docs/product/learnings.md` §6.11 already answered "convert passport
   to mdoc?" with **no** — a converted credential needs a *new* signature from somewhere, and
   whoever signs it is an issuer, which is exactly the central adversary zkagent's NO-GO #3 rules
   out. Nothing found in this conformance pass overturns that; if anything Finding 6 sharpens it:
   the EU's own ZK-native age-proof library (`av-lib-android-zkp-age-icao`) is the one credible
   *issuer-free* road to a wallet-presentable credential — it proves age directly from the
   ICAO-signed chip data, with no separate issuer step — but (a) it is EU-published, experimental,
   `0.0.3-SNAPSHOT`, constrained to specific RSA key sizes, and (b) per the grep above, **it is not
   itself wired into the OpenID4VP/DCQL wire** that a real wallet or verifier speaks. So even
   picking up this library would only solve "prove age without an issuer" — it would not, by
   itself, produce a DCQL-selectable, wallet-presentable credential; that adapter does not exist
   yet, in this codebase or (as far as this pass found) in the EU's own published repos either.
3. Separately from the credential-format question, **Finding 2's `client_id`/`aud` mismatch is a
   real, independent blocker** even for someone who did build a genuine mdoc path: this reference
   verifier's default (`PreRegistered`/x509 `VerifierId`, hardcoded `aud`) is not what
   `spikes/m2-handoff/server.mjs` or `docs/logs/M2-CAPTURE.md` assumed. Any actual interop attempt
   needs to re-derive the *current* OpenID4VP client-identification model from source, not from
   the Blueprint's own docs, which this pass found to describe an older/legacy shape the
   reference backend treats as a compatibility fallback, not the default.

**Bottom line**: zkagent's mode-A/mode-B wire, as built, is validation-grade evidence-plug
plumbing that proves internal consistency (the 15/15 green tests) and nothing about EU-wallet
interop. Nothing here changes that scope — `docs/logs/M2-CAPTURE.md`'s own "NOT established"
section already said interop was never claimed — but this pass turns "we haven't checked" into
"we checked, twice over, against real source and a real running instance, and it does not
interoperate, for two independent reasons (credential format, client identification), with a
credible-but-unfinished third-party path (Finding 6) that would only close one of the two."

---

## What this establishes and does NOT establish

**Established (cited, dated, evidence pasted above):**
1. The reference verifier backend (`av-srv-verifier-endpoint` @ `787089b`) runs locally via its
   own published Docker image and documented config; its real `POST /ui/presentations` endpoint,
   real signed request-object shape, and real polling-endpoint status codes were captured
   directly, not inferred from docs (Findings 1, 3, 4, 5, comparison table).
2. `spikes/m2-handoff/server.mjs`'s DCQL block is confirmed decorative by the actual mechanism
   (wallet-side credential selection, not verifier-side enforcement) rather than by assumption
   (Finding 1).
3. A second, previously undocumented incompatibility exists in `client_id`/`aud` construction,
   found only by reading `VerifierConfig.kt` and `RequestObject.kt` source, not from any doc
   (Finding 2).
4. The EU org's own published repos contain an issuer-free ZK age-proof library over ICAO
   documents, architecturally close to zkagent's own Track Z, but not wired into the
   OpenID4VP/DCQL wire examined here (Finding 6).

**NOT established — do not state these anywhere:**
- That fixing the DCQL/credential-format and `client_id`/`aud` issues found here would be
  *sufficient* for a real wallet to accept a zkagent presentation — only the request side and
  polling side were exercised; the full mdoc device-response verification path
  (`PostWalletResponse.kt`, mdoc device-auth handover, trust-chain validation) was read for the
  `client_id` question (Finding 2) but never exercised end-to-end, and nothing here proves what
  else that path would reject.
- That `av-web-verifier-ui` (the companion frontend) or `av-app-android-wallet-ui` (a real
  wallet app) behave identically to the backend examined — neither was run (see PENDING).
- That the EU Blueprint's own technical-specification docs (fetched in `M2-CAPTURE.md`) are wrong
  — Finding 2 shows the *reference backend's default* diverges from what the docs describe as the
  mechanism, which is a documentation/implementation gap inside the EU project, not evidence
  against the Blueprint spec itself.
- Any claim that zkagent is, or is close to being, "zero-knowledge" — v1 remains captcha-grade;
  Finding 6 is cited only to answer the interop question, per NO-GO #7's standing constraint.
- Nothing about UK OSA gates (Persona/k-ID) is revisited here; `M2-CAPTURE.md` Finding 3 stands
  unchanged — this file is scoped to the EU AV Blueprint conformance question only.

---

## PENDING

- [ ] `av-web-verifier-ui` (companion frontend) was cloned but never run — the backend's REST
      surface alone was sufficient to answer the DCQL and `client_id` questions; running the UI
      would mainly add browser-side QR/app-link rendering, not new wire evidence.
- [ ] A full mdoc device-response round-trip (producing a real verifier-accepted `vp_token`) was
      not attempted — needs a real or synthetic signed mdoc credential and mdoc device-auth
      implementation, explicitly out of scope per the task brief ("no issuer").
- [ ] `docs/logs/M2-CAPTURE.md` Finding 7's packet-capture gap is **still open** — this file adds
      real HTTP request/response evidence against a *locally run* reference instance, which is
      not the same as a capture against a live production EU AV deployment (none is known to be
      live yet — `av-doc-technical-specification` is a reference-implementation project, not a
      confirmed production rollout as of this pass).
- [ ] `docs/index.md` needs a row for this file — not edited here by rule (orchestrator
      regenerates it).
- [ ] Whether `av-lib-android-zkp-age-icao` (Finding 6) ever gets wired into the EU's own
      OpenID4VP wire is a live open question upstream, not something this project controls; worth
      a periodic recheck given its direct relevance to zkagent's Track Z (`docs/product/learnings.md`
      §6.11's "watch item, reopened only if Google adds RSA" — this is a *different* engine
      (Barretenberg, not longfellow) already doing RSA over ICAO documents today, which is new
      information for that watch item, not previously known).
