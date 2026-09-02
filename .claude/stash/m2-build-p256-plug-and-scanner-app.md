# Session Stash — M2 build: sig-p256/1 plug + scanner app (2026-08-31)

Project: zkagent · Owner: hamr · Host: Fedora 44

New session, continued from `m2-build-ed25519-key-test.md` after `/clear`,
same calendar day. Owner rule #2 held: the orchestrator never coded or
edited docs itself; every `Agent` call passed `model: sonnet` explicitly.
Nothing was committed this session — the owner commits on explicit word
only.

## Summary

The owner said "cont from here do 1 and 2" — (1) add the P-256 plug to
chiproof, (2) build the M2 app through §6.2 items 1-11.

Arc 1, chiproof attester-sig plug family (done): discovery before building
found `sig-ed25519/1` did not exist in chiproof at all — D30 named it and
§6.2 item 9 states its byte layout, but grep found it only in the PRD. A v2
reference implementation did already exist at
`spikes/m2-handoff/sig-ed25519-plug.mjs` (owner-confirmed 2026-08-31). The
closest shipped library plug was `signed-receipt/1` — Ed25519 but an older
layout with no domain prefix and no zktag binding. Because §6.2 item 9
requires the verifier accept both algorithms, the agent built the whole
family, not just P-256. New `packages/chiproof/src/plugs/attester-sig.js`,
`src/index.js` now exports `sigEd25519`, `sigEd25519Message`, `sigP256`,
`sigP256Message`; new `tests/integration/attester-sig.test.js` (39 tests);
README/context-doc registry entries updated; root `CHANGELOG.md` updated;
`package.json` bumped 0.3.0 → 0.4.0. Not published. Byte layout:
`preimage = utf8(PLUG_TYPE + "\n") ‖ sha256(canonical(claim)) ‖
base64urlDecode(nonce) ‖ utf8(scopeDomain) ‖ utf8(zktag)`. `sig-ed25519/1`
signs `sha256(preimage)`; `sig-p256/1` signs raw `preimage` via
`crypto.sign('sha256', preimage, key)` (P-256 sigs DER-encoded, no
IEEE-P1363/raw r‖s support). `item.data = { key_id, sig }` for both. Both
`binds:{nonce,claim,scope,zktag:true}`, `linkability:'signer'`,
`tierCeiling:'B'`. Orchestrator-recommended, not owner-decided: applying
SHA-256 in each algorithm's native place (Ed25519 gets the digest; P-256
gets the preimage, since Android's `SHA256withECDSA` hashes its own
input) — flagged in code comments, README and context doc.

Orchestrator independently verified: chiproof's `sigEd25519Message`
produces bytes byte-identical to the spike's `sigMessage` — both
`d13394ddd7a3c2fbba3c5f0419ee4d05cda16f0fb31df9e566ecdf23d334c9b3`, MATCH
true. `npm run typecheck` clean, `node --test` → 165 tests, 165 pass, 0
fail (126 pre-existing + 39 new), run by the orchestrator itself. The
orchestrator also verified four real Pixel 6a StrongBox artifacts from the
predecessor session
(`/home/hamr/.claude/jobs/e8664a28/tmp/m2-session-poc/`:
`kt-pubkey-c-authbound.der`+`kt-sig-c-authbound.bin`,
`kt-pubkey-c.der`+`kt-sig-c.bin`, `kt-pubkey-d.der`+`kt-sig-d.bin`,
`pubkey-doc2.der`+`sig-doc2.bin`, message `msg.txt` =
"m2-session-poc/1 attester-key liveness check") all verify through the
plug's exact call `cryptoVerify('sha256', message, pubkey, sigBytes)`, and
a one-bit-tampered message is rejected. This proves DER/SPKI/prehash-
convention compatibility between Android Keystore and the plug — but not
the app's message construction, since the phone signed a liveness string,
not the item-9 preimage. `spikes/m2-handoff` regression check against
chiproof 0.4.0: 17 tests, 17 pass, 0 fail.

Arc 2, M2 scanner app (built, partially verified): new top-level
`apps/scanner/` (orchestrator's call, not the owner's, explicitly flagged
as trivially reversible), package `com.zkagent.scanner`, built from
`spikes/m2-scan` and `spikes/m2-session-poc` without touching either.
Files include `MainActivity.kt`, `DeviceKey.kt`, `MasterlistVerifier.kt`,
`EvidenceSigner.kt`, `Canonical.kt`, `HandoffClient.kt`, `QrCapture.kt`,
`MintGate.kt` (added later), plus tests under `app/src/test/`. No
`ResultActivity` (item 5). The build agent found and fixed a defect in its
own work mid-flight: its first `EvidenceSigner.kt` applied
`sha256(preimage)` uniformly to both algorithms; once the parallel agent
published the real `sig-p256/1` layout it rewrote against fresh known
vectors from `attester-sig.js`. Orchestrator-verified independently: fresh
`--rerun-tasks` unit run = 16 tests, 0 failures, 0 errors (later 21 after
`MintGateTest`). Release APK's packaged `res/8G.xml` (resource
0x7f140004, obfuscated name) contains only `base-config
cleartextTrafficPermitted=false` with no domain-config; debug APK carries
the `10.0.2.2`/`localhost`/`127.0.0.1` exception plus `base-config false`.
Item 10 verified against packaged resources via `aapt2 dump xmltree`, not
source. Orchestrator ran the zero-tap masterlist probe on-device itself:
`MasterlistVerifier: CMS integrity OK: signature verified against "CSCA
Master List Signer", chained to the PINNED "csca-germany" root (byte
match, not name match)`; `full_load: OK declared=588 parsed=588
(1041ms)`; `NEGATIVE half_loaded: REFUSED (ok:false) — CMS parse failed:
CMSException: IOException reading content. (good)`. The 588/588 matches
M0's prior measurement. MRZ persistence scare, cleared: the app came up
showing a document number in the MRZ field; after `am force-stop` +
relaunch the field returned to its hint — live in-memory state of a
running process, not disk persistence. F1 holds.

Device runs this session (Pixel 6a, `34011JEGR02358`, US passport): RUN 1
at 21:38, mode B, first run in a fresh process: PASS. Report verbatim:
`mode: B`; `access_protocol: BAC`; `chip_auth (D21 payload field):
absent`; `master_list: CMS-verified OK, declared=588 parsed=588`;
`passive_auth: ok=true allowed=true reason=SOD signature verified to a
trusted CSCA`; `mint: OK`; `device_key_algorithm: P256_HARDWARE
(STRONGBOX, sig_alg=SHA256withECDSA)`; `evidence_type: sig-p256/1
key_id=6307febce68ac08b`; `zktag_sha256_prefix (value-free, never the raw
zktag): aebd09e089ad`; `scope_domain: reference-app.test`; `verdict: PASS
(minted)`. Closed items 1, 2, 3, 9 in their live-tap form. The owner
selected Mode B then pressed lock, and executed mode was B — one live
observation of item 4's structural F5 fix behaving correctly (an
observation, not a proof). Owner reported the run took ~40 seconds
wall-clock; device-side portion was 7s (21:38:21→21:38:28), remainder was
MRZ typing and PIN entry. Q16 datapoint. Key matrix from run 1, verbatim:
`matrix a1: FAILED InvalidAlgorithmParameterException: Unsupported
StrongBox EC: ed25519`; `matrix a2: OK level=STRONGBOX`; `matrix b1:
FAILED NullPointerException: Attempt to invoke interface method
'java.lang.String java.security.PublicKey.getAlgorithm()' on a null
object reference`; `matrix b2: OK level=TEE`; `matrix c: OK
level=STRONGBOX`; `matrix d: OK level=TEE`; `Signature.SHA256withECDSA
provider 'SC' rejected key: InvalidKeyException`;
`Signature.SHA256withECDSA provider 'AndroidOpenSSL' rejected key:
InvalidKeyException`. `a1` is new and better evidence for F2 — StrongBox
now rejects Ed25519 with an explicit "Unsupported StrongBox EC" rather
than the predecessor session's silent substitution of a P-256 key. The F2
provider-shadowing finding is confirmed still live.

RUN at 21:41:33 (mode A) and RUN at 21:44:57 (mode B with pending `av://`
handoff): both logged `PACE unavailable (CardServiceException)`, `CA
unavailable (CardServiceException)`, the MasterlistVerifier CMS line —
then nothing. No report, no DeviceKey lines, no exception, no crash,
process alive. Logcat was cleared at 21:43:53 so the 21:44 case is not
log rotation. The verifier received nothing at all — no `GET
/wallet/request.jwt/{id}`, no `POST /wallet/direct_post`; transaction
`WlifDM47tbEatlnd` stayed `"pending"`. The owner then reported: "verdict
not shown as popup, below text yes" — a verdict was rendered on screen
while nothing was logged. This single detail is what made the bug
diagnosable; the log alone said the runs had died silently.

Arc 3, root cause and evidence-capture fix (done, awaiting the owner's
next tap): root cause, confirmed by code inspection —
`MainActivity.continueAfterRead()`'s `!mayMint` branch set
`reportView.text` directly with no `Log` call. Verbatim: `if (!mayMint) {
reportView.text = baseReport + "\nmint_gate: NOT MET — evidence: [] ..."; 
return }`. This explains every fact at once — mode A can never mint so it
always took that branch; `DeviceKey.ensureKey()` is only invoked inside
the `mayMint` branch, explaining zero DeviceKey lines; the handoff
fetch/POST only happens inside `mintAndMaybeHandoff`, reached only after
a mint, explaining why the verifier saw nothing. Nothing stalled — both
runs completed and reported to the screen only.

The orchestrator's own hypothesis (reused-Keystore-key regression of
predecessor finding 6) was tested on-device zero-tap and not reproduced:
a new "RUN DEVICE KEY SELF-TEST" button called `ensureKey()` twice in one
process, plus forced reuse-of-persisted-key and post-`pm clear`
fresh-generate runs; every generate, reuse and `initSignature` succeeded,
`auth_mode=PER_USE` both times, no `UserNotAuthenticatedException`, no
NPE. `DeviceKey.kt` already carries the POC's read-back-real-auth-mode
self-heal; it did not regress.

Fixes made: a single `emitReport()` is now the only place `reportView.text`
is written, and it unconditionally logs a `===== M2 REPORT (value-free)
=====` block; explicit logging at every handoff step (pending-handoff,
request_uri fetched + HTTP status, JWS/plain-JSON parse outcome,
direct_post URL + HTTP status + verdict body); the previously-unguarded
`DeviceKey.ensureKey()` background call wrapped in try/catch with logging;
`onSaveInstanceState`/restore of the report text only (in-memory Bundle,
never disk); new `MintGate.kt` extracting the mint-gate boolean as a pure
unit-tested function; `matrix b1`'s NPE now reports `Unsupported TEE EC:
ed25519_ec_curve (...)`; `HandoffClient.fetchRequest` now throws typed
`HandoffHttpException` carrying the real HTTP status on non-2xx instead
of parsing an error body as the request, and `postDirectPost` returns
`DirectPostResult(httpStatus, body)`.

Important correction the fix produced: `wipeSession()` never touched
`reportView`. The orchestrator had told the owner `onStop` was
over-wiping the verdict; that was wrong. What actually erased the
on-screen report was Activity re-creation with no saved state. Item 6 was
already compliant.

Orchestrator-verified after the fix: fresh `--rerun-tasks` = 21 tests, 0
failures, 0 errors; only two `reportView.text` sites remain in
`MainActivity.kt` (line 203, the savedInstanceState restore of
already-logged text; line 377, inside `emitReport`), so the
render-without-log path is structurally gone.

Staged and left ready for the owner: fixed APK installed, `adb reverse
tcp:8787 tcp:8787` active, verifier up (started with `LINK_SCHEME=av`,
listening `http://127.0.0.1:8787`), mode-B transaction `WcyIsB1z4BPDOBzI`
created, and the app armed — `M2 stage: pendingHandoff captured from av://
intent` logged at 22:13:45. The owner's next tap was never taken; the
session ended before it.

## State of the repo

Branch `m2-build`, still 4 commits ahead of `main` (`5c9dc6a`, `e0a9ea7`,
`db9d5eb`, `3d6a1b2`) — no new commits this session. Uncommitted: `M
CHANGELOG.md`, `M packages/chiproof/README.md`, `M
packages/chiproof/chiproof.context.md`, `M
packages/chiproof/package-lock.json`, `M packages/chiproof/package.json`,
`M packages/chiproof/src/index.js`, `?? apps/`, `??
packages/chiproof/src/plugs/attester-sig.js`, `??
packages/chiproof/tests/integration/attester-sig.test.js`, plus the same
pre-existing `.claude/` and `CLAUDE.md`/`docs/log.md` dirt as the
predecessor stash.

## Key decisions

- Orchestrator-recommended (not owner-decided): apply SHA-256 in each
  algorithm's native place — Ed25519 signs `sha256(preimage)`, P-256
  signs the raw preimage because Android's `SHA256withECDSA` hashes its
  own input. Flagged in code comments, README, and context doc.
- Orchestrator's call, not the owner's, explicitly flagged as trivially
  reversible: the M2 app lives at top-level `apps/scanner/`.
- Because §6.2 item 9 requires the verifier accept both algorithms, the
  build covered the whole `sig-*/1` plug family, not just P-256.
- The device key is the D30 attester key only (carried forward, unchanged
  this session).

## Findings

1. `sig-ed25519/1` did not exist anywhere in chiproof before this
   session — only named in the PRD (D30, §6.2 item 9) — despite a v2
   reference implementation already existing at
   `spikes/m2-handoff/sig-ed25519-plug.mjs` (owner-confirmed 2026-08-31).
2. **`MainActivity.continueAfterRead()`'s `!mayMint` branch wrote
   `reportView.text` directly with no logging call**, which single-
   handedly explains three separate symptoms that looked unrelated: mode
   A's total silence (it can never mint), zero `DeviceKey` lines in that
   branch (key setup only happens inside `mayMint`), and the verifier
   receiving nothing (handoff fetch/POST only happens inside
   `mintAndMaybeHandoff`, reached only after a mint). Nothing stalled —
   the app completed and reported to the screen only, invisibly to logs.
3. `matrix a1` is new, stronger evidence for F2: StrongBox now rejects
   Ed25519 with an explicit `InvalidAlgorithmParameterException:
   Unsupported StrongBox EC: ed25519`, rather than the predecessor
   session's silent substitution of a P-256 key.
4. The orchestrator's own hypothesis for the silent-run bug (a reused-
   Keystore-key regression of predecessor finding 6) was tested on-device
   zero-tap and not reproduced — the real cause was the unlogged
   `reportView.text` write, not key handling.
5. `wipeSession()` never touched `reportView` — an earlier orchestrator
   claim that `onStop` was over-wiping the verdict was wrong. The actual
   cause of a vanished on-screen report was Activity re-creation with no
   saved state; item 6 (wipe on access failure, keep state otherwise) was
   already compliant.
6. Release APK's packaged `res/8G.xml` (obfuscated name for
   `network_security_config.xml`, resource 0x7f140004) contains only
   `base-config cleartextTrafficPermitted=false` with no domain-config;
   the debug APK carries an added `10.0.2.2`/`localhost`/`127.0.0.1`
   exception. Must be checked via `aapt2 dump xmltree` against the
   packaged APK, not by reading source, since resource names get
   obfuscated in release builds.
7. Chiproof's `sigEd25519Message` byte output matches the standalone
   spike's `sigMessage` byte-for-byte
   (`d13394ddd7a3c2fbba3c5f0419ee4d05cda16f0fb31df9e566ecdf23d334c9b3`
   both sides), and the plug's verify path (`cryptoVerify('sha256',
   message, pubkey, sigBytes)`) accepts four real Pixel 6a StrongBox
   signature/pubkey pairs from the predecessor session and rejects a
   one-bit-tampered message — confirms DER/SPKI/prehash-convention
   compatibility between Android Keystore and the plug, but not the
   app's actual message construction (the phone signed a liveness
   string, not the item-9 preimage).

## Open items / next steps

- The signing seam is still unproven end-to-end: run 1 proved the app
  selected `sig-p256/1` and minted; it did not prove the produced bytes
  match what chiproof verifies. Only the staged mode-B handoff run (left
  armed, owner's tap never taken) closes this.
- Item 8's handoff leg beyond intent capture — no request fetch or
  `direct_post` has ever succeeded.
- Why the 21:44 mode-B run failed its mint gate is still unknown: either
  passive auth did not return `ok && allowed`, or the executed mode was
  not B. There is no log distinguishing them, and the second possibility
  is F5's exact signature. F5 remains OPEN — the build agent made it
  structurally impossible to hide rather than root-causing it (could not
  reproduce F5 either).
- Masterlist bucket 2 (well-formed CMS-verified list lacking the issuing
  CSCA ⇒ `ok:true, allowed:false`) is untested. All four
  `MasterlistVerifierTest` cases and both on-device probes hit bucket 1
  (integrity ⇒ `ok:false`) only. It cannot be faked by deleting a CSCA —
  that breaks the CMS signature and correctly becomes a bucket-1 failure.
  Testing it properly needs a synthetic CMS masterlist signed by a test
  key pinned only in a test build, containing a CSCA subset excluding the
  US. Not yet built.
- Remaining device runs: mode A after mode B (no zktag), reinstall zktag
  stability, NL ID card, QR fallback path.
- Fingerprint/strong-biometric path still untested (zero prints
  enrolled).
- Incoming request-object (JWS) signatures are not verified — no owner-
  approved `trustedChallengeIssuers` pinning surface exists. Largest open
  hole in the handoff path.
- The `csca-germany` CMS trust anchor
  (`assets/csca-germany-root.der`) is carried forward from the M2
  opening session's BSI provenance check, not re-verified by this build.
- `zxing-core` added as a dependency (QR decode-only, system camera
  capture, no live in-app scanner) — needs owner sign-off.
- `Dn` numbers still unassigned for: the F2 algorithm-agility decision,
  `sig-p256/1`, and the four/five §6.2 build decisions.
- The shipped `sig-*/1` layout binds the claim hash, which is wider than
  D30's literal "nonce + scope" wording — chiproof's plug contract
  refuses at registration any plug not declaring `binds.claim === true`.
  The spike flagged this for owner confirmation and it is still open.
- Owner confirmation wanted on the in-memory `onSaveInstanceState` report
  restore (adjacent to item 6's wipe requirement).
- `apps/` as a top-level location — orchestrator's call, owner has not
  confirmed.
- chiproof 0.4.0 is not published; merge of `m2-build` → `main` still
  pending and will need an admin bypass.

## Gotchas

- The Bash tool's cwd persists between calls; several commands failed
  with `cd: no such file or directory` after a previous call left the
  shell inside a subdirectory. Use absolute paths or re-`cd` from the
  repo root.
- `pkill -f "node server.mjs"` killed the calling shell (exit 144); use
  `setsid nohup` to start the verifier and avoid `pkill` patterns that
  match the invoking command.
- Release-build resources are obfuscated: `res/xml/network_security_config.xml`
  ships as `res/8G.xml`. Resolve it via `aapt2 dump resources <apk> |
  grep 0x7f140004`, not by filename.
- Three app packages are now installed side by side sharing the activity
  name `com.tananaev.passportreader.RegularActivity`:
  `com.zkagent.m2sessionpoc`, `com.tananaev.passportreader`, and
  `com.zkagent.scanner`. Always compare the package.
- The device log is extremely chatty (`WeatherData`, `CHRE`, `AOC`,
  `pixel-thermal`); filter by pid or tag.
- `mcp__baremobile__snapshot` returns "Entire tree pruned away" when the
  screen is off — wake with `KEYCODE_WAKEUP` + `wm dismiss-keyguard`
  first.
- Firing the handoff with `adb shell am start -a android.intent.action.VIEW
  -d '<av://...>'` exercises the same `av://` handler the QR path would
  invoke, so item 8's primary mechanism can be tested without the camera.

## Recovery commands

```
cd packages/chiproof && npm install --ignore-scripts && npm run typecheck && node --test   # 165 tests
cd spikes/m2-handoff && npm install --ignore-scripts && node --test                        # 17 tests
```

App (Android, real device required):
```
export JAVA_HOME=$HOME/opt/jdk-21.0.12.1+1 ANDROID_HOME=$HOME/Android/Sdk
cd apps/scanner && ./gradlew :app:testRegularDebugUnitTest --rerun-tasks :app:assembleRegularDebug
adb install -r app/build/outputs/apk/regular/debug/app-regular-debug.apk
adb shell svc power stayon usb
```

Re-arm a handoff run:
```
cd spikes/m2-handoff && (LINK_SCHEME=av setsid nohup node server.mjs > /tmp/handoff.log 2>&1 &)
adb reverse tcp:8787 tcp:8787
curl -s -X POST http://127.0.0.1:8787/ui/presentations -H 'Content-Type: application/json' -d '{"mode":"B"}'
adb shell am start -a android.intent.action.VIEW -d '<app_link from that response>'
```

PRD: `docs/product/zkagent-prd.md` v1.18.

Predecessor stash: `.claude/stash/m2-build-ed25519-key-test.md`.
