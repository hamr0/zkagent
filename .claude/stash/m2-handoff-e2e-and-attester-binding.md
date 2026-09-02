# Session Stash — M2 scanner: first end-to-end handoff + D31-D44 (2026-09-01)

Project: zkagent · Owner: hamr · Branch: `m2-build` (tree clean at session end)

Session started from a prior stash (`m2-build-p256-plug-and-scanner-app.md`)
after two silent no-report runs root-caused to a UI write path with no
logging. Goal this session: get the M2 scanner's §6.2 item 8 handoff
working end-to-end against the `spikes/m2-handoff` verifier on the Pixel
6a with real documents, and record the decisions that fell out. Owner rule
reconfirmed: main session never codes or edits docs; every code/doc change
went through a spawned agent, and no feature was built before its PRD line
existed (NO-GO #10).

## Commits (all on `m2-build`)

- `9f60489` fix(apps/scanner): handoff off main thread, response_uri from
  request object, asserted device_key report
- `89e9c53` docs(prd): v1.19 — D31-D37, §6.2 items 13-14, Q28/Q29 closed,
  item 11 unstaled
- `505ed0c` docs(prd): v1.20 — D38 per-origin attester keys + first-sight
  binding; bake in 2026-09-01 implementation decisions
- `51c6b01` feat(chiproof,spikes): any-of evidence groups (D31) +
  first-sight attester binding (D38)
- `08a6788` docs(prd): v1.21 — D39 per-(origin,zktag) key isolation, D40 no
  issuer policy at A/B
- `315eaa2` feat(apps/scanner): D33/D34/D37/D38/D39 — request
  verification, mode preselect, isolated per-(origin,zktag) attester keys
- `f3a793b` docs(prd): v1.22 — D41 linkability class is per-plug and
  evidence-based, D42 scope granularity
- `c585ed9` docs(prd): v1.23 — D43 blocking dialogs, D44 timestamped log
  view, Q31 re-enrolment gap

PRD went v1.18 → v1.23 in this session.

## Bugs found, all only by real-device runs (the through-line)

Each was invisible to green test suites. Shared shape: two sides of a
contract, each internally self-consistent, disagreeing about one detail
neither could see alone.

1. `NetworkOnMainThreadException` — `mintAndMaybeHandoff` did
   HttpURLConnection work inline in `BiometricPrompt.onAuthenticationSucceeded`,
   which runs on the main thread. §6.2 item 8's handoff had NEVER executed
   inside the scanner before 2026-09-01. Fixed by moving to a background
   `Thread{}` (same idiom as `runMasterlistProbe`). The sign survives the
   hop because the key is per-use auth (`setUserAuthenticationParameters(0, …)`)
   bound to the `CryptoObject`, not a time window.
2. `response_uri`/`state` were read from `zkagent.challenge`; they live at
   the TOP LEVEL of the OpenID4VP request object. Fixed → `direct_post`
   returned HTTP 200 `{"accepted":true}` for the first time.
3. Scope mismatch — scanner signed scope `127.0.0.1` (host of verified
   origin, D37) while `spikes/m2-handoff/server.mjs` had
   `SCOPE_DOMAIN = 'm2-handoff.test'` hardcoded (pre-D37). Every
   real-device P-256 signature failed `sig_invalid` while the spike suite
   stayed green, because `scripts/fake-wallet.mjs` and
   `tests/tier-b.test.mjs` imported the same constant. Fixed: server
   derives from `BIND_HOST`; wallet and tests now derive scope
   independently via `new URL(...).hostname`, so a future disagreement
   fails a test instead of a scan.
4. Cross-document key leak — with D38's per-origin key, NL card then US
   passport at the SAME origin both minted `key_id=c303cf3f731b5307`; the
   site could see two pseudonyms sharing one device key. Led to D39.
5. (Staging, not code) `adb reverse` tunnels die whenever the adb server
   restarts or USB drops; symptom is Chrome hanging, not an error.
   Happened 3+ times. Two-second check: `adb reverse --list` empty.
6. (Staging) `adb shell am start -d 'av://...?a=1&b=2'` — the device-side
   shell eats the bare `&` and truncates the URL. Wrap the whole remote
   command in double quotes. `HandoffClient.parseAvLink` dropped it
   silently; now logs before returning null.

## Device evidence (Pixel 6a, real NL ID card + US passport, real Chrome av:// tap)

- 11:38–11:39 NL mode B: `allowed=true reason=evidence-verified
  evidence=["sig-p256/1"] attester=bound_first_sight` (tx
  `HVLKlhbUl9arsCsA`). App: `device_key: created this mint`,
  `key_scope: http://127.0.0.1:8787`, `evidence_type: sig-p256/1
  key_id=c303cf3f731b5307`.
- 11:40 NL again: `attester=matched` (tx `mclZCT8UvxhIbMrA`),
  `device_key: reused existing alias`, same key_id. FIRST observation of
  mode-B uniqueness — same document AND same device.
- 11:43 US passport: `allowed=true attester=bound_first_sight` (tx
  `qxvO-_6v8PtU9qfZ`), `access_protocol: BAC`, `chip_auth: absent`
  (clone-replayable, declared not hidden), same key_id
  `c303cf3f731b5307` → the leak that produced D39.
- 12:40 / 12:42 after D39: NL `key_id=9aa88722553a42ec`, US
  `key_id=a0b15dc66c4245f8` — two documents at one site now mint two
  independent keys. Both refused `attester_key_mismatch` (tx
  `Cxn0dXWz8nlJfVX3`, `MstvPR4zJGK4VoSG`) because the in-memory store
  still held the old bindings — correct behaviour, staging artefact.
- After verifier restart (clean store): both NL and US
  `allowed=true attester=bound_first_sight`. D39 confirmed on hardware.

## Decisions D31–D44 (all owner-approved 2026-09-01)

- D31 verifier accepts ANY ONE of an operator-configured attester-sig set
  (needs any-of in chiproof's `evidence.require`, which was all-of).
  Supersedes D30's single-plug framing.
- D32 attester-sig plugs are the reference default, not privileged — an
  operator may require `zk-passport/1` or any registered plug. Clarifies
  D24.
- D33 scanner preselects and locks the mode from the request's
  `zkagent.tier` (§6.2 item 13).
- D34 scanner verifies the request-object JWS before trusting ANY field
  (§6.2 item 14); refuse, never warn-and-continue. Stricter than D20's
  floor.
- D35 value-free report may survive Activity recreation in memory only.
- D36 device orders key capabilities by fixed preference, falls through
  only on failure — never chooses to downgrade. Closed Q28. (P-256 and
  Ed25519 are equivalent strength; the meaningful axis is
  `security_level`.)
- D37 request trust is origin-bound (EU AV Annex A shape), not
  authority-bound: `client_id`/`request_uri`/`response_uri` one origin;
  signing key from `https://<origin>/.well-known/zkagent-verifier` over
  TLS; no central allow-list at A/B; tier C may use an operator-curated
  list; OS-level trust for requester→app stated as a v1 limitation;
  `av://` hijack → App Links follow-up. Closed Q29.
- D38 per-origin attester keys + verifier binds key→zktag on first sight
  (`attester_bound_first_sight` / `attester_key_mismatch`); item carries
  `pubkey`, key_id recomputed and compared.
- D39 attester key isolated per (origin, zktag) — narrows D38. Rule: a
  key's scope must be at least as narrow as the identity it signs for.
  Owner accepted the tradeoff of losing "one device, two documents" fraud
  detection: "this is not our place to judge/police and that's a
  borderline creepy/surveillance"; it wouldn't work anyway since zkagent
  never binds presenter to document holder.
- D40 no issuer/country attribute or filter at tiers A/B ("id is id
  doesn't matter where it's from"); CSCA trust-anchor curation is the
  legitimate mechanism and remains permitted; tier C exception.
- D41 linkability class is a per-plug, evidence-based property measured
  from the payload, never inferred from technology category. `sig-*/1`
  stays `'signer'`. `'device'` = one value, same at every site,
  permanently. Play Integrity recorded as a worked example, explicitly
  NOT a class assignment (M1's spike found no cross-site device-unique
  fields).
- D42 signing scope = origin HOST only; D34/D37 consistency check = full
  scheme+host+port. Closed Q30. Flagged unfixed: host vs registrable
  domain decides whether subdomains share a pseudonym.
- D43 (§6.2 item 15) errors that leave the app waiting on the user MUST be
  modal dialogs with an OK, then an explicit state transition (keep MRZ on
  access failure per F3, else reset). Snackbar only for transient
  no-state-change events. Rule: transient UI for transient facts, blocking
  UI for state that requires the user to act.
- D44 (§6.2 item 16) per-scan report moves to its own timestamped log
  view; same value-free content; an additional CONSUMER of the single
  `emitReport()` path, never a second write site; in-memory only, cleared
  on wipe; timestamps display-only, never in a proof path.

## Open

- Q31 re-enrolment after StrongBox key loss (factory reset / reinstall) —
  user permanently refused `attester_key_mismatch` at every site that
  knows them. Four options listed, none recommended; genuine tension,
  since anything letting a NEW key claim an EXISTING zktag is the
  impersonation first-sight binding prevents.
- Host vs registrable domain (D42 note) — production only.
- `av://` → verified https App Links (D37 follow-up).
- D43/D44 are through the scope gate but NOT BUILT — next build task, one
  coder.
- FR12 linkability taxonomy gap noted but `'signer'` confirmed by D41.

## Staging recipe (for the next session)

Verifier: `cd spikes/m2-handoff && LINK_SCHEME=av nohup node server.mjs >
<scratchpad>/handoff.log 2>&1 &` — logs each tx creation and verdict,
value-free. Restart clears the in-memory attester store (bindings do not
survive).

Device: `adb reverse tcp:8787 tcp:8787` (re-do after ANY adb restart),
`adb shell svc power stayon usb`, `adb shell am force-stop
com.zkagent.scanner`.

Owner flow (page TTL is 120s, so pre-type the MRZ first): scanner → type
MRZ → leave radio alone → Chrome `http://127.0.0.1:8787/` → "Verify your
age (mode B)" → tap `av://` link → radio jumps to B and greys → Lock → tap
document → PIN → back to Chrome.

Debug: long-press the KEY TEST button (debug builds only) exports the
current attester public key + key_id to `filesDir`; read via `adb shell
run-as com.zkagent.scanner cat files/attester_pub.pem`. Never logged.

Build: `JAVA_HOME=/home/hamr/opt/jdk-21.0.12.1+1 ./gradlew
:app:assembleRegularDebug` in `apps/scanner` (system java-25 has no
javac).

Test counts at session end: chiproof 191/191, spike 23/23, scanner 56/56
unit (58 after D39).

## Process notes / lessons

- Two subagent self-reports were wrong and caught by independently
  re-running: a "23/23" spike count reported from a run made mid-edit
  before the last change (real: 16 pass / 7 fail), and this session's own
  miscitation of NO-GO #9 (which is "no secrets/test keys in the tree",
  NOT on-device persistence — the correct anchors are §6.2 item 6/F1 and
  D35). The drafting agent flagged the orchestrator's error rather than
  following it.
- A test that reads its expected value from the code under test can only
  ever agree with it — the fake wallet importing the server's scope
  constant is why bug 3 survived to reach a real device.
- Verifier now logs every transaction creation and verdict (value-free: no
  zktag, nonce, pubkey, sig, state), so the owner no longer has to report
  what the browser said.
- Owner rule reconfirmed: main session never codes or edits docs; every
  code/doc change went through a spawned agent, and no feature was built
  before its PRD line existed (NO-GO #10).
