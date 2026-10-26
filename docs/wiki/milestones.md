---
type: reference
title: zkagent — milestones and build scope
status: stable
sources: [docs/archive/zkagent-prd.md]
---

# Milestones and build scope

Normative summary of PRD §6 (milestones) and §7 (riskiest-assumption register). Per-run
evidence narratives, commit hashes, test counts, and line-number anchors are condensed to
pointers — see the named evidence doc under `docs/logs/` or the findings entry under
`.claude/remember/findings.md`. Decisions (`Dn`) and open questions (`Qn`) are not repeated
here — see `docs/wiki/decisions.md` and `docs/wiki/questions.md`.

## 6. Milestone table

Rung 1 (age verification) unless noted. One milestone at a time; each works alone before the
next integrates. (zkagent-prd.md:855-865)

| M | Deliverable | Checkpoint | Status |
|---|---|---|---|
| **M0** — POC at the riskiest assumption | Throwaway spike, Pixel 6a/Android 17, forked JMRTD reader; owner's US passport + NL ID card; BAC/PACE as the chip announces; read DG1+SOD (+DG14/DG15); verify SOD against the BSI masterlist; derive a candidate zktag per stable field; two mandatory planted negatives (flipped DG1 byte, masterlist missing issuing CSCA) (zkagent-prd.md:855) | Two scan runs/document, matching candidates; both negatives fire; four timing marks; masterlist certs-parsed == certs-declared; chip field/AA/CA inventory recorded; no PII in the evidence doc. Thrown away, never shipped. Evidence: `docs/logs/M0-EVIDENCE.md` (zkagent-prd.md:855) | Superseded 2026-08-31 — negative (ii)'s wording corrected: a well-formed masterlist lacking the issuing CSCA is `ok:true, allowed:false` (a real no), not `ok:false`; `ok:false` is reserved for masterlist *integrity* failures. See M2 row's two-bucket rule. (zkagent-prd.md:855) |
| **M1** — Verifier SDK core | `chiproof` verifier core: never-throw verdict; challenge nonce + signed challenges; tier negotiation; FR10 trust list; evidence slot with bare mode, `zk-passport/1`, `signed-receipt/1` plugs; masterlist stays on the phone. Spec: `docs/product/m1-verifier-core-spec.md` (zkagent-prd.md:856) | Full negative matrix (replay, expired/unsigned tier-C challenge, tier mismatch, untrusted client, missing/misbound evidence, plug-throws ⇒ `ok:false`) each paired with a non-vacuity pass; zero runtime deps; zk plug verifies real proofs and rejects planted negatives (zkagent-prd.md:856) | Done |
| **M1b** — Mode-A unlinkability probe | Black-box byte comparison of N mode-A presentations, same device/holder/service, including a planted positive control (zkagent-prd.md:857) | No field differs across presentations except those proven independent of holder/device; a planted stable field must fail the check. Blocks M3. Answers a linkability question. Evidence: `docs/logs/M1B-EVIDENCE.md` (zkagent-prd.md:857) | Ran 2026-08-30; passes with one disclosed leak bucket; block on M3 lifted (zkagent-prd.md:857) |
| **M2** — Scanner app (rewrite, not graduate) | Real app: Keystore/StrongBox, biometric gate, QR/app-link handoff; ships BARE (`evidence: []`) as its fixed evidence set — no on-device ZK prover; `zk-passport/1` stays verifier-side; mode-B roundtrip exercises the attester-key evidence plug end to end against the M1 core. Opens with a riskiest-assumption POC: capture real-world age-verification request flows, build a test verifier to the observed shape, and run the web→app→web handoff over the mechanism the captures show is real (zkagent-prd.md:858) | End-to-end on real device against local verifier; zktag stability across reinstall+re-scan; mode A emits no zktag even after a mode-B presentation. Half-loaded masterlist ⇒ read refused (`ok:false`), never a pass. Two-bucket rule for the masterlist checkpoint (integrity failure ⇒ `ok:false`; well-formed list lacking the issuing CSCA ⇒ `ok:true, allowed:false`). Build scope for the rewrite itself: §6.2 below (zkagent-prd.md:858) | **Closed/complete, 2026-09-03, v0.5.0.** §6.2 items 1–25 all built and device-confirmed (item 19 WITHDRAWN by D71a, number reserved); findings #18–#21 closed; every row in the Exit criteria table below is device-confirmed or FIXED-IN-<sha> device-confirmed. D66–D71 recorded. First release under D72's lockstep versioning rule. |
| **M3** — Demo (tier A/B only, vanilla) | Web page: "prove you're over 18" (tier A) and "prove you're a unique adult human" (tier B); responsive (mandatory); no tier C (zkagent-prd.md:859) | Live flow. Tier B: second scan from same document rejected as duplicate zktag. Tier A: two presentations indistinguishable. Demo site is its own adopter with its own store — the SDK still stores nothing (zkagent-prd.md:859) | COMPLETE — released as v0.6.0 (2026-09-05), exit rows all confirmed on that build (see docs/logs/M3-EXIT-ROWS-v0.6.0-2026-09-05.md); owner signed off 2026-09-05. M3b PARKED (D108); §6.6 Play track PARKED (D83). |
| **M3b** — Mode C / tier-C KYC demo | Tier-C attributed disclosure (booleans over identifying fields from a published verb list, D19), gated by a pin list (D20, amended: exact-hostname pin list, D109); scanner tier-C support (lifts §6.2 item 13's refusal); demo page extension over M3 | TBD — see §6.4 decision points and §6.9's complete (parked) design | **IN BUILD (approved 2026-10-25, D109(h)).** Was PARKED by D108 (2026-10-24). The owner revived M3b (D109(a)) and approved §6.9 as amended on 2026-10-25 (D109(h)), which satisfies NO-GO #10 for that amended scope: tier C is gated by the `operator.json` switch plus an exact-hostname pin list, all five item-8 predicates are allowed to every pinned site (no per-site question permissions, no caps in this build), and the holder has one app setting, "Allow additional verifications", OFF by default. Q34 CLOSED; Q11's tier-C residual stays open. Not built yet: the scanner still refuses every tier-C request until the build lands.
| **M4** — Agent layer (rung 2) | Delegation certs, RFC 9421 middleware (FR8), per-serial revocation, phone→agent cert handoff (zkagent-prd.md:860) | Agent request accepted with valid chain; killed by zktag-block; single-use serial burns once; signature verifies against an off-the-shelf RFC 9421 verifier with no zkagent patches; cert reaches a headless agent host with no zkagent-run server in the path (zkagent-prd.md:860) | **Backlog, not planned (D108).** Not designed here; the owner's agent-delegation work now lives in the IETF individual Internet-Draft `draft-hamr-oauth-agent-delegation` (see `backlog.md`). Previously "Frozen until rung 1 ships"; rung 1 has shipped (v0.7.1) but nothing is scheduled.
| **M5** — Blocklist/appeal (rung 2) | Signed blocklist format, adopter store interface, prove-control-of-zktag appeal (zkagent-prd.md:861) | Store pattern fails closed, never silently falls back to in-memory (zkagent-prd.md:861) | **Backlog, not planned (D108).** Same as M4.

## 6.1 M0 go/no-go table

Written before the run; enforces that the test must be able to fail and a confirming result is
audited for harness confounds before being believed. (zkagent-prd.md:867-899)

| Observation | Meaning | Consequence |
|---|---|---|
| Phone never establishes BAC/PACE with a document KYC apps read | Harness/library problem, not a chip problem | Stop; debug the spike first |
| Access works, DG1+SOD read, passive auth passes on genuine document | Baseline holds | Continue |
| Passive auth fails on a genuine document | Assume harness bug first | Debug before recording |
| Either planted negative does not fire | Checker cannot say "no"; every positive result is void | Stop; fix the negative first |
| Issuing CSCA absent from the BSI list | Coverage risk live for that country | Try ICAO master list; if still absent, record "issuer-free is weaker than assumed" |
| A candidate differs on rescan | Field not stable | Excluded from the derivation-field decision |
| Neither AA nor CA on a document | Clone-replay undetectable for that document | Mode A unaffected; mode B for that document is captcha-grade, clone-replayable, disclosed as such |
| Only one of AA/CA present | Sufficient | Mode B uses whichever exists; the chip decides |
| Full read > 10s wall-clock | UX question, not a stop | Recorded for M2, not an M0 failure |

(zkagent-prd.md:869-897)

## 6.2 M2 build scope (v1.17, owner-approved)

Scope gate for the M2 rewrite (per the PRD's scope-gate rule) — nothing here is built until it
is written here. Opening POCs pass on both documents: `docs/logs/M2-SCAN-EVIDENCE.md`,
`M2-CAPTURE.md`, `M2-CONFORMANCE.md`. (zkagent-prd.md:901-905)

16 numbered items, all carried below (source: 16 items, §6.2.1–§6.2.16). Per-item amendment
history (decisions Dnn, evidence commit hashes, "owner-agreed" annotations) is condensed to a
pointer at the end of each row. Items 17–21 were added 2026-09-03 (D67), after the D65 freeze
lift, unfreezing six previously-deferred UX questions (Q39/Q40/Q43/Q44/Q45/Q48) — Q40 was closed
by wording sign-off rather than added as an item; the other five map one Qn to one new item. Items
22–24 were added 2026-09-03 (D70), from the same device session, before build started on them (scope
gate, NO-GO #10). Item 19 was WITHDRAWN 2026-09-03 (D71a) after device observation — the number
stays reserved, not reused. Item 25 was added 2026-09-03 (D71b), from the same later exchange,
before build started on it (scope gate, NO-GO #10).

1. **Device key.** MUST generate an Android Keystore keypair (StrongBox-backed where available), the app's own attester key, signing the challenge binding for mode-B presentations. MUST NOT ever leave the Keystore. MUST NOT feed zktag derivation. MUST select, at first run [amended: at first mint per verified request origin], the strongest key algorithm the device supports, and MUST report which algorithm was selected; the verifier (item 9) MUST accept more than one signature algorithm. Keystore alias is derived from BOTH the verified request origin AND the zktag (not origin alone) — a key's scope must be at least as narrow as the identity it signs for. MUST require biometric (or device-credential) authorization before minting; MUST NOT gate the chip read itself. See `docs/logs/M2-SESSION-POC.md` F2; decisions.md D30/D36/D38/D39. (zkagent-prd.md:906-971)
2. *(item 2 is folded into item 1's amendment chain in the source — no independent item 2 text; item numbering continues at 3.)*
3. **Always read, conditionally mint.** MUST gate zktag emission in-app on `passiveAuth.ok && passiveAuth.allowed === true`, on top of whatever the verifier enforces via `evidence.require`/tier. A masterlist real-no MUST derive and emit no zktag. (zkagent-prd.md:972-975)
4. **Mode capture.** MUST read presentation mode from one source of truth at chip-session start and bind it into session state; MUST NOT re-read a UI control later. Superseded: the mode radio is REMOVED — mode is DERIVED from a verified handoff's tier, or mode A by default with no pending request; nothing left for the user to set. F5 (mode-radio bug) closed by construction. (zkagent-prd.md:976-998; decisions.md D51)
5. **No document-field rendering.** MUST NOT render DG1/MRZ/any personal field on any screen, any mode; `ResultActivity` MUST be removed, not deprioritized. Mode A screens show verdict only. No MRZ persistence to disk. (zkagent-prd.md:999-1002)
6. **Session-state lifecycle.** MUST wipe MRZ/session state in `onStop()`, never `onPause()`. MUST keep typed MRZ fields on an access-establishment failure (`SW 0x6300`→`0x6985`) for retry; wipe only on successful read or `onStop()`. Value-free report text MAY be retained in-memory across Activity recreation, never to disk. `onStop()` wipe reaffirmed (not relaxed) after a live-run friction report; the actual friction was fixed instead by item 15's transient-failure bucket. (zkagent-prd.md:1003-1020; decisions.md D35/D51)
7. **Masterlist.** MUST bundle the full BSI CMS SignedData and verify the CMS signature and signer chain at load, before the integrity check; a signature/chain failure is an integrity failure ⇒ `ok:false`. MUST apply the two-bucket rule on top: integrity failure ⇒ `ok:false`; well-formed CMS-verified list lacking the issuing CSCA ⇒ `ok:true, allowed:false`. (zkagent-prd.md:1021-1029)
8. **Handoff.** MUST implement `av://` app-link + `direct_post` as primary, QR as cross-device fallback. Digital Credentials API support is a spike-gated stretch — MUST NOT ship in-scope unless its provider-registration spike passes first. EU wallet/mdoc interop MUST NOT be attempted. Confirmed exercised end-to-end 2026-09-01; see `docs/logs/M2-CAPTURE.md`, `M2-CONFORMANCE.md`. **Amended 2026-09-03 (decisions.md D69, supersedes D68 part b):** the cross-device QR fallback carries NO in-app scanner dependency of any kind. The verifier spike renders `app_link_av` as an actual QR image (`<img>` data URI, npm `qrcode`) on its own page; the person scans it with whatever camera app they already have, and that app's own `av://` VIEW intent lands directly on the scanner's existing intent filter — the SAME code path a same-device link uses. The `play-services-code-scanner` in-app scanner tried under D68(b) the same day was removed after a device test showed it still runs in a Play services process, pulls Google's data-transport telemetry into the merged manifest, and downloads its module from Google on first use. `launchQrScan`/`qrScanner` and the "Scan QR" button are gone; a one-line non-interactive hint text takes the button's place. The manual-paste field (`handoff_manual_input`/`applyPendingHandoffText`) is unaffected. Device-proven twice (12:19:28, 12:19:38) — `docs/logs/M2-DEVICE-SESSION-2026-09-03-EVIDENCE.md` check 7. (zkagent-prd.md:1030-1045)
9. **Evidence.** Mode A MUST ship bare (`evidence: []`). Mode-B roundtrip MUST exercise the attester-key evidence plug matching whatever algorithm item 1 selected on that device, as the reference default. Verifier MUST accept more than one signature algorithm (`sig-ed25519/1`, `sig-p256/1`) as an any-of set, not chiproof's prior all-of `evidence.require`; which plug was used MUST be recorded in the verdict. Signed-layout details and algorithm-preference logging: `.well-known`/report-line conventions per decisions.md D31/D32/D36. **Finding #21 (2026-09-03, FIXED-IN-40737a2):** mode A's bare `evidence: []` MUST was written but never reached in code — `MintGate` folded every mode A outcome into "not met", so a mode A holder with a verified pending handoff never actually sent a bare tier-A presentation anywhere. Fixed: `MintGate.actionFor` + `MainActivity.presentBareA`/`presentBareAOnBackground` now build and `direct_post` the bare presentation for that case; see findings.md #21. (zkagent-prd.md:1046-1082)
10. **Network config.** MUST ship a real `network_security_config` (cleartext disabled). Release build permits NO cleartext; debug build permits exactly one cleartext exception, scoped to `10.0.2.2`/localhost, for the local test verifier only. (zkagent-prd.md:1083-1086)
11. **Non-goals (M2 build).** MUST NOT include: iOS; an on-device ZK prover; EU wallet/mdoc interop; rung-2 delegation; the Credential Manager provider (unless item 8's spike passes); any change to `chiproof` beyond what the app needs; no in-app camera/QR scanning dependency (decisions.md D69 — the cross-device QR route is verifier-renders-QR + any camera app + the `av://` app link, never a scanner library bundled into this app). Pinned dependency: `chiproof 0.4.0` (in-repo). A P-256 evidence plug (`sig-p256/1`) addition to `chiproof` is permitted as required by item 1's device-capability reality — it shipped in `chiproof@0.4.0`. Every other non-goal stands unchanged. (zkagent-prd.md:1087-1114)
12. **Riskiest-assumption POC for the build itself.** Compose StrongBox key generation + biometric prompt + PACE chip read in one foreground-dispatch NFC session; pass = the IsoDep session survives the biometric UI interruption and the read completes. Run on the Pixel 6a with both documents. (zkagent-prd.md:1115-1120)
13. **Mode preselection from a pending handoff request.** When a handoff request is pending at mode-capture time, the app MUST set the mode from the request object's tier field and MUST disable manual override; if the tier is absent or not one of A/B/C, the app MUST fail loudly (log + report) — no default. An inbound `av://` link arriving while a session is already locked/reading is refused up front (mitigation, not the ownership fix — see `.claude/remember/findings.md` #10). Superseded: with the mode radio removed (item 4), this item's MECHANISM (lock a radio) is gone but its REQUIREMENT (fail loudly on absent/invalid tier, no default; tier C refused) is unchanged, now guarding the derivation step. (zkagent-prd.md:1121-1149)
14. **Request-object JWS verification before trusting any field.** MUST verify the request object's JWS signature against a trusted request-signer key before trusting any field (nonce, `response_uri`, `state`, tier, `evidence_required`); verification failure or no matching signer MUST refuse the handoff, never warn-and-continue — stricter than the spec-level floor by the M2 app's own choice. Trusted-signer set is origin-derived: `client_id`/`request_uri`/`response_uri` MUST all resolve to one HTTPS origin before anything else (mismatch ⇒ refuse); the request-signer key MUST then be fetched over TLS from a well-known path under that origin. M2-scope exception: `spikes/m2-handoff` runs on plain HTTP, so the scanner ships one build-time pinned dev-only request-signer key. (zkagent-prd.md:1150-1177)
15. **Blocking acknowledgment for outcomes that leave the app waiting on the user.** Any outcome ending a scan attempt and requiring user action MUST be a modal dialog (value-free reason + OK), never a self-dismissing Snackbar; dismissal performs the state transition (keep-MRZ on access-establishment failure, reset otherwise). Applies to mint-path failures too (key generation, missing verified request/origin, no usable device key/signature, biometric error), not only read/access/handoff failures. A pending handoff is cleared on every definitive delivery outcome; a tap/mint against an EXPIRED-but-pending session is refused up front. A third bucket — transient chip-communication failure (tag lost mid-read) — also keeps MRZ/mode for a no-re-entry retry; classification is conservative, falling through to RESET when ambiguous. A real bug (classification order) was found and fixed: TRANSIENT is now classified first from exception evidence, independent of execution phase, in a pure `FailureTransition` object with its own test. A successful, delivered-and-accepted presentation also self-confirms via the same blocking-modal mechanism, outcome-only wording. Exact dialog/Result strings for each bucket are owner-approved — see Exit criteria row below for current text. A value-free MRZ-input-change diagnostic (salted, in-memory-only, never persisted) was added to make retry behavior observable. (zkagent-prd.md:1178-1348; decisions.md D43, D50, D51, D52, D53, D54, D55, D56)
16. **Per-scan report log view.** The value-free report MUST also accumulate into a separate in-app log view (tab or equivalent), each entry timestamped; MUST NOT change report content beyond the required plain-language disclosure summary (below); MUST route through the single `emitReport()` write path, never a second write site; in-memory only for the session, cleared only on app-process end (not per-scan wipe). Each entry MUST render a four-field plain-language block — `Result`, `Sent`, `Shared`, `Identity` — plus a subordinate `▸ technical:` line with the complete unmodified report text, under a title line (timestamp + verified site, or the fixed "Local scan (no site)" label — also used for a failed request-object verification, never an unverified origin). `Identity` reads "new — minted fresh for this site" or "known — recognized only here from previous visit" per the per-(origin, zktag) key state. `Shared` renders as a list of `<predicate>: <boolean>` lines (literal `true`/`false`, never "yes"/"no"), threshold and answer read from the verified request/actual outcome, never hardcoded; an empty list renders plain "nothing shared" wording. Log lists newest-first (display order only; storage order unaffected); exactly one entry per scan attempt (terminal outcome replaces the in-progress entry, never appends a duplicate). Mode line removed from the plain block (redundant with Sent/Shared/Identity, and could misleadingly imply mode governs what is read rather than what is sent); chip-authenticity three-state line (verified / not supported / failed) stays, with "not supported" never rendered as "false". A real pane-visibility bug was found and fixed: `loading_layout`/`main_layout`/`log_layout` overlapping `FrameLayout` siblings could let the log pane cover the MRZ form, stranding the user mid-correction; fix is a single function writing all three visibilities, with the pane decision in a pure unit-tested object. (zkagent-prd.md:1349-1637; decisions.md D44-D56)
17. **Switch to scan pane on handoff intent (ENHANCEMENT).** MUST switch the visible tab to the scan pane when an incoming `av://` handoff intent arrives, regardless of which tab is currently visible. Acceptance: launching the app via an `av://` link while the Log tab is visible switches display to the scan pane before the read begins. See Q39; decisions.md D67.
18. **Collapsed log entries by default (ENHANCEMENT).** MUST collapse each per-scan log entry (item 16's four-field block plus technical line) by default, behind a per-entry toggle to expand it; MUST NOT change the block's content, only its default visibility. Acceptance: a newly-added log entry renders collapsed; tapping it reveals the full block. See Q43; decisions.md D67.
19. ~~**Dim a completed run (ENHANCEMENT).** MUST visually dim a log entry once its scan attempt has reached a terminal outcome, to distinguish a done run from one still in progress. Acceptance: a terminal-outcome entry renders visibly dimmed relative to an in-progress one.~~ **WITHDRAWN (D71a), 2026-09-03.** Built (`1bc345b`), device-observed 2026-09-03 PM as uniform grey with no scan in flight (every entry terminal); owner chose "drop dimming" over the two offered alternatives. Number reserved, not reused, not renumbered. See Q44 (closed, not wanted); decisions.md D67, D71(a).
20. **Verify-vs-local scan control (ENHANCEMENT).** MUST provide one control distinguishing a "verify" scan (against a pending handoff request) from a "scan local" one (no site involved); shape decided by the owner at build time — the coder proposes, owner approves. Acceptance: owner approves a specific control design before it ships. See Q45; decisions.md D67.
21. **Launcher-distinguishable app (ENHANCEMENT).** MUST make the scanner distinguishable from the project's other two reader apps on the device launcher, via app label and icon; shape decided by the owner at build time — the coder proposes, owner approves. Acceptance: owner approves a specific label/icon before it ships. See Q48; decisions.md D67.
22. **Outcome glyph on collapsed log entries (ENHANCEMENT).** Each collapsed per-scan log entry's title line MUST carry a quick-review glyph — pass (minted/verified), fail (refused/failed), or pending (in progress) — derived from the same terminal-outcome state item 19 already dims on; MUST NOT introduce a new state or render any document data. Acceptance: three log entries in the three states show three distinct glyphs while collapsed. See Q43/Q44; decisions.md D70(a).
23. **Log survives app close (ENHANCEMENT).** The value-free per-scan log (item 16) MUST persist across process death and app restart, in app-private storage, subject to the D59 20-entry cap, with a user-facing "Clear log" control; MUST NOT persist MRZ or session state (item 6 unchanged) — only what the log pane already shows (D46's value-free entries). Promotes D64's deferred Option B; the in-flight-mint-loss case (finding #16/Q38) stays "accept and disclose" — persistence does not change it. Acceptance: force-stop the app after a scan, relaunch it, the entry is still there; "Clear log" empties it. See Q38; decisions.md D70(b).
24. **Visible version stamp (ENHANCEMENT).** The scan pane MUST show the app's `versionName` plus the short git SHA it was built from, and the log entry's `▸ technical:` line MUST include the same stamp. Motivated by the three on-device reader apps (M0 spike, M2 session POC, scanner) being indistinguishable by version; only the scanner resolves `av://` (confirmed via `pm query-activities` 2026-09-03), so link routing was never at risk — this is a build-provenance aid, not a security fix. Acceptance: the on-screen stamp matches `git rev-parse --short` of the built commit. See decisions.md D70(c).
25. **Mode sentence in the terminal outcome dialog (ENHANCEMENT).** The blocking terminal outcome dialog (item 15) MUST carry one sentence naming the mode of the presentation it reports — "Mode A, anonymous" or "Mode B, recognisable to this site" (same wording as the post-lock status line); a bare local scan (no pending handoff, mode A by derivation per item 4) gets the "Mode A, anonymous" sentence. Rationale: the status line, the PIN/biometric prompt (mode B only, names the site), and the log entry's `Identity` line already state the mode — the owner wants the outcome dialog to state it too. Acceptance: a mode-A outcome dialog and a mode-B outcome dialog each show their respective sentence; a bare local scan's dialog shows the mode-A sentence. See decisions.md D71(b).

## Exit criteria (M2)

| Check | Pass | Status |
|---|---|---|
| Three `M2-SCAN-EVIDENCE.md` checkpoints re-run on the real build, both documents | Reinstall zktag stability; masterlist two-bucket rule with both negatives; mode A emits no zktag after a mode-B presentation (zkagent-prd.md:1639) | RE-RUN on the real build 2026-09-03 PM (`7f25f40`): reinstall stability PASS (NL), masterlist bucket (i) PASS on device + bucket (ii) unit-proven `3f65290`, mode A after mode B emits no zktag PASS — see PM evidence page (`docs/logs/M2-DEVICE-SESSION-2026-09-03-PM-EVIDENCE.md`) |
| Handoff roundtrip | Passes against `spikes/m2-handoff` over `av://`/`direct_post`, including a mode-B presentation under the any-of evidence set (zkagent-prd.md:1640) | Confirmed 2026-09-01; mode A bare roundtrip device-confirmed 2026-09-03 PM 13:54 (finding #21 closed) — see PM evidence page |
| Mode-radio bug (F5) | Closed: mode radio removed entirely, mode derived — eliminates the bug class by construction (zkagent-prd.md:1641) | Closed |
| Mode preselection (item 13) | Absent/invalid tier fails loudly, no default, now guarding derivation; tier C remains refused (zkagent-prd.md:1642) | Implemented |
| Request-object verification (item 14) | Unsigned/unverifiable request object refused (log + report) at every tier, not only C (zkagent-prd.md:1643) | Implemented |
| Blocking acknowledgment (item 15) | Every waiting outcome (read/access/handoff/mint-path failure, transient chip-comm failure, successful delivery) surfaces as a modal, dismiss-on-OK only; strings owner-approved per bucket (see `docs/logs/M2-D50-D53-EVIDENCE.md`); device-confirmed 2026-09-02 (`docs/logs/M2-D55-D56-EVIDENCE.md`) for happy-path minting and the input-change diagnostic — the specific Log-tab-then-retap stranding sequence was not itself reproduced in that run (zkagent-prd.md:1644) | Device-confirmed, one sub-case unreproduced |
| Log view (item 16) | Newest-first, exactly one entry per scan, four-field plain-language block + technical line, per-(origin,zktag) Identity wording, boolean `Shared` list, no Mode line, three-state chip-authenticity line, single pane-visibility writer; device-confirmed 2026-09-02 for prerequisite behaviors only — the pane-overlap mechanism itself not directly reproduced-and-resolved this run (`docs/logs/M2-D55-D56-EVIDENCE.md`) (zkagent-prd.md:1645) | Device-confirmed, one sub-case unreproduced |
| Switch to scan pane on handoff intent (item 17, D67/Q39) | An incoming, admitted `av://` handoff intent switches the visible tab to Scan before the read begins, regardless of current tab; a refused (`HandoffAdmission`) intent leaves the tab alone | Device-confirmed 2026-09-03 PM (second pass, ~15:10): owner sat on Log tab, an `av://` link fired, app switched to Scan on its own — see PM evidence page section 13.3 |
| Collapsed log entries by default (item 18, D67/Q43) | Each newly-added log entry renders collapsed; a per-entry tap reveals the unmodified full block; content itself unchanged | Device-confirmed 2026-09-03 PM: owner tapped an older collapsed entry and it expanded, in the same glance as section 13's other checks — see PM evidence page section 13 addendum |
| Dim a completed run (item 19, D67/Q44) | ~~A log entry whose scan attempt has reached a terminal outcome renders visibly dimmed relative to an in-progress one~~ | **WITHDRAWN (D71a).** Device-observed as built 2026-09-03 PM, owner decision pending: with no scan in flight every entry is terminal so all render equally dimmed (60% alpha, `ReportLog.rendered`) — contrast was visible only during a live scan; owner offered "keep as built" vs. "newest entry stays bright" and chose "drop dimming" instead — see PM evidence page section 13.5. Withdrawn (D71a), code removed in `5cf39bf` — `ReportLog`/`MainActivity`/`ReportLogStore` dimming path deleted, every log entry now renders in the log view's normal text colour; owner device-confirmed 2026-09-03 PM. |
| Verify-vs-local scan control (item 20) | Owner ruled (D68): no new control — the existing scan-action button's verb ("Verify"/"Scan", locked "Tap and verify"/"Tap and scan") is a pure projection of handoff state, driven by the lock-time `authorizedHandoff` snapshot so a foreign handoff resolving after a bare lock cannot flip it; unit-tested for every `HandoffState` and both `handoffDrivenLock` values, including an explicit REFUSED-handoff → "Scan" case | Device-confirmed 2026-09-03 PM (second pass): button read "Verify" with a verified link pending, "Scan" with none — see PM evidence page section 13.1. Locked-verb wording ("Tap and verify"/"Tap and scan") owner-confirmed 2026-09-03 ~15:30 (D71d), no change. |
| Launcher-distinguishable app (item 21) | Adaptive icon (solid brand-colour background, monochrome white glyph foreground + themed-icon layer) and a distinct release label ("zkagent Scanner") replace the inherited passportreader icon/label; debug builds carry their own label suffix and a corner badge so a side-by-side release+debug install is also distinguishable. See Q48; decisions.md D67. | Built, device-confirmed by owner eye 2026-09-03 PM ~12:5x |
| Outcome glyph on collapsed log entries (item 22, D70(a)) | Three log entries in the pass/fail/pending states each render a distinct glyph while collapsed, derived from item 19's existing terminal-outcome state; no new state, no document data | BUILT-IN-`1c5fef4`; owner reported seeing glyphs 2026-09-03 PM, not independently re-verified this session. Glyph rule for an honest under-threshold outcome (`over_threshold:false`, verifier `allowed=false under_threshold` → ✗) owner-reviewed and confirmed 2026-09-03 ~15:30 (D71c), no change. |
| Log survives app close (item 23, D70(b)) | Force-stopping the app after a scan and relaunching still shows the entry; "Clear log" empties it; MRZ/session state (item 6) still does not persist | Device-confirmed 2026-09-03 PM — `loaded persisted log from disk (entries=N)` observed across three separate app instances (1→2→3); "Clear log" device-confirmed second pass (~15:10): clear, swipe away, reopen → log empty — see PM evidence page section 13.4 |
| Visible version stamp (item 24, D70(c)) | The scan-pane stamp and the log entry's technical-line stamp both match `git rev-parse --short` of the commit the running build was compiled from | FIXED-IN-fb0e75f, device-confirmed 2026-09-03 PM (second pass): stamp visible on screen on `fb0e75f`, after a device FAIL on `2525267`/`039fee7`/`7f25f40` was root-caused to the pane-container `FrameLayout` consuming all weighted height and pushing the stamp off-screen (fixed same day) — see PM evidence page section 13.2 |
| Single `RegularActivity` instance across launch sources (finding #19, D70(d)) | A Chrome-launched and a camera-app-launched `av://` link both land in and reuse the same single `RegularActivity` instance, with no invisible second instance left blocked by BAL hardening | FIXED-IN-039fee7, device re-checked 2026-09-03 PM: no second pid/instance ever handles a tag read on `7f25f40`; a superfluous BAL-blocked launch attempt against an invisible `ActivityRecord` still logs on every NFC intent (unrelated side effect, not a second instance) — see PM evidence page check 2 |
| Logged lock early-exits (finding #20) | Both `lockModeAndArm` early-exit guards (incomplete MRZ fields; handoff still verifying) produce a matching `Log` call, so a real "Verify" tap is never indistinguishable from a hang | FIXED-IN-8c063ec, device-confirmed 2026-09-03 PM 13:58:13 (`lock refused — document fields incomplete`) |
| Mode A delivers a bare presentation (finding #21) | A mode-A holder with a verified pending handoff sends a bare tier-A presentation (no zktag, no device key, no biometric prompt); item 9's "ships bare" MUST is actually reached, item 15's modal covers this outcome | FIXED-IN-40737a2, device-confirmed 2026-09-03 PM 13:54 (`verdict: PASS (bare presentation sent)`, verifier `ok=true allowed=true reason=no-evidence-required evidence=[]`) — reproduced failing first on `039fee7` at 13:27, then fixed build confirmed on `7f25f40` |
| Mode sentence in the terminal outcome dialog (item 25, D71b) | A mode-A outcome dialog and a mode-B outcome dialog each show their respective sentence ("Mode A, anonymous" / "Mode B, recognisable to this site"); a bare local scan's dialog shows the mode-A sentence | BUILT-IN-`a61bcc8` (`OutcomeText.withModeSentence`, `SessionDisplay.modeLabel`), device-confirmed 2026-09-03 PM: a bare local scan on the NL ID card produced an outcome dialog reading "This scan was Mode A, anonymous.", matching the mode A shown on the scan-pane status line and the log entry — see PM evidence page section 14. |

(zkagent-prd.md:1637-1646)

## 6.3 M3 build scope (owner-approved 2026-09-03, D73–D75)

Scope gate for M3 (NO-GO #10) — nothing here is built until the owner approves it. Written
2026-09-03 per D73: the owner split M3 into a vanilla A/B-only demo (this section) and a
separate future mode-C build, M3b (§6.4), rather than building both in one pass. Owner intent,
verbatim: "keep m3 a/b vanilla, mode c as m3b, write prd first" — M3 is meant as "a mockup run
for any operator to test the app rightaway, needs to be clean and precise." All decision points DP1–DP7 raised during drafting on 2026-09-03 were resolved by the owner the
same day and are recorded inline with their attributions (D73/D74); DP7 (§6.5 S3's scan-pane
relocation) and the Play Store listing question (§6.6) were both resolved same-day by D75. No item
here may be built ahead of owner sign-off (D73 is a record of the split, not an approval of
§6.3's content) — but per D75, §6.3/§6.5/§6.6 are now owner-approved as of 2026-09-03.

1. **Two flows, one page.** MUST present exactly two actions on one responsive page: "prove
   you're over 18" (tier A) and "prove you're a unique adult human" (tier B). Each MUST be a
   single clean tap/click with no multi-step wizard. MUST NOT offer a tier-C action or any
   control implying one exists (see item 9, MUST NOT).
2. **Vanilla stack.** MUST ship with no frontend framework and no build step — plain HTML/CSS/JS
   served by a plain Node process, matching the throwaway spike's shape (`spikes/m2-handoff`)
   but as real, kept code, not a spike. M3 keeps BOTH handoff paths the M2 spike already proved —
   the same-device app link and the cross-device QR — so "no deps beyond `chiproof`" becomes "no
   deps beyond `chiproof` and the QR image encoder already in use" (the `qrcode` npm dep is
   permitted for that image, exactly as in `spikes/m2-handoff`). The local node (item 7/DP5) is
   the ONLY thing that runs the page. (owner, 2026-09-03: "same qr used in local
   127.0.0.1:8788 and same device, both")
3. **Demo is its own adopter, its own store.** The demo page MUST run its own persistent store
   for nonces, the attester-key binding, and zktags-seen (dedupe/blocklist state) — `chiproof`
   itself stores nothing (D3, FR3). `InMemoryNonceStore`/`InMemoryAttesterStore` are
   test-only (chiproof README) and MUST NOT be used once M3 is reachable from a real phone in a
   persistent operator-facing run. Store technology is a flat JSON file with atomic
   temp+rename write. ADDITIONALLY the page MUST display what the app sent back (the
   verdict/presentation payload the verifier received, as returned by `chiproof` — never PII,
   there is none in tier A/B) and the store's relevant state for this transaction (e.g. "zktag
   already seen at this site: yes/no" for tier B); this display requirement is also its own row
   in the exit-criteria table below. (owner, 2026-09-03: "yes, it should display on screen,
   part of display what gets sent back")
4. **Tier-B duplicate rejection.** A second tier-B scan of the same document at this site MUST
   surface an "already registered" outcome on the page (not a silent no-op, not a generic
   error). "Same document" is already settled by existing decisions, not new to M3: the zktag
   is `HMAC(secret, verified domain)` where `secret` derives from the document number (D9,
   FR11) — two presentations of the same document at this site always produce the same zktag,
   which is what the demo's store keys duplicate-detection on. MUST disclose the chip_auth
   caveat (D29) on the page or in its README: a document without chip authentication
   (`chip_auth: false`, e.g. the US passport) is clone-replayable — a cloned document mints the
   identical zktag as the genuine holder's, so "unique adult human" is only as strong as
   `chip_auth` allows.
5. **Tier-A indistinguishability shown (DP3 resolved).** The page MUST render a per-field table
   comparing two consecutive tier-A presentations: one row per field, two value columns (scan 1,
   scan 2), and a third column reading "same" or "differs" per row. Only the fresh nonce and the
   challenge's issued-at and expires-at timestamps may ever land in the "differs" column — the
   page MUST say so in words next to the table (e.g. "only the nonce and the challenge timestamps
   differ between scans; every other field matches"). Tier-A presentations carry no signature by
   design (D27, unlinkable), so no signature row exists. A simple header above the table drives a
   two-state machine: "Scan 1 done — waiting for scan 2" until the second presentation arrives,
   then "Both scans received." (owner, 2026-09-03: "field table, and be clear scan 1 done, waiting
   scan 2 on the page simple header"; D77, 2026-09-04)
6. **Fixed threshold: 18 only (DP4 resolved by D74).** M3 MUST hardcode/request threshold 18
   only. The demo runs against the released scanner source unmodified, sideloaded as the debug
   variant (`app-regular-debug.apk`, D76) (item 13) — the preset
   threshold list, per-origin threshold lock, and the exact-hostname exception allowlist are
   scanner-side items and are NOT M3 scope; see §6.5 items S1–S2. Q11 is CLOSED by D74. (owner,
   2026-09-03: "agreed" to the threshold-policy proposal recorded in full at D74)
7. **Hosting.** The demo is a local Node process an operator runs on their own machine, reached
   from the phone at `http://127.0.0.1:8787` via USB `adb reverse tcp:8787 tcp:8787` against the
   sideloaded debug build (`app-regular-debug.apk`), both handoff paths kept (same-device `av://`
   link and QR) — no LAN-IP reachability is required or in scope (D76 supersedes the earlier
   LAN-IP pattern). NO online hosting is in scope for M3; the "public host" option is removed
   entirely. D38's per-origin key binding still applies: the origin MUST be stable across the
   operator's session — a changing origin resets every bound key. Why: the scanner's network
   security config trusts only `system` CAs in both debug and release builds, so a plain
   LAN-IP `http://` origin and a self-signed HTTPS cert are both refused by the released APK —
   `adb reverse` onto the debug build's own localhost cleartext allowance is what actually works
   without a scanner change (D76). (owner, 2026-09-03: "this a node you run locally for testing,
   no online hosting, it's for anyone to download the app from playstore and run it")
8. **Trust list (FR10) — moved to §6.5 by D78; M3 relies on attester-key binding, see D78.**
9. **MUST NOT.** No tier C (M3b only); no ZK-marketing language (NO-GO #7, D1); no PII
   displayed, ever, on any screen; no zkagent-run server anywhere in the path — the demo's
   server is the ADOPTER's server, run by whoever operates M3, never a service zkagent itself
   hosts on the operator's behalf (NO-GO #3); no threshold picker (item 6); no store fallback to
   an in-memory store once the demo is running in a persistent/operator-facing mode — fail
   closed, not silently in-memory (M5's fail-closed rule, D3, applied early).
10. **Opening riskiest-assumption POC.** Before any of the easy parts (page styling, README) are
    built: duplicate-zktag rejection against the persistent store (item 3) survives a server
    restart, on both real documents (US passport, NL ID card), plus one handoff exercised the way
    item 7 specifies (D76). Pass = both documents mint on first scan, both are refused as "already
    registered" on a second scan, the refusal still holds after the server process is killed and
    restarted, and the handoff completes end-to-end from the phone's own browser at
    `http://127.0.0.1:8787` over `adb reverse`, against the sideloaded debug build.
11. **Exit criteria.** See the table below this list; mirrors §6.2's Exit criteria shape (one row
    per checkpoint, device-confirmed column).
12. **Versioning and location.** M3 ships under D72's lockstep versioning (one repo version,
    bumped alongside `chiproof` and the scanner at every release). The demo lives at
    `apps/demo/`, created by moving `spikes/m2-handoff` there (`git mv`, preserving history) and
    then evolving it; the spike README's THROWAWAY label MUST be removed on the move. (owner,
    2026-09-03: "apps/demo")
13. **"Test right away" means concretely.** A README with a run recipe of 10 lines or fewer:
    install adb, enable USB debugging, install the APK, `adb reverse tcp:8787 tcp:8787`,
    `npm install`, `npm start`, open `http://127.0.0.1:8787` in the phone's browser (D76); the
    page MUST work against the released scanner source unmodified, sideloaded as the debug
    variant (`app-regular-debug.apk`, D76) — no scanner-side
    (`apps/scanner`) changes required for M3. If any item above is found to need a scanner
    change while building, that need MUST be flagged back to the owner before work continues
    (scope-gate escalation, not a silent scanner edit).

Play Store listing: NOT an M3 item — see §6.6 (owner, 2026-09-03).

### Exit criteria (M3, draft)

| Check | Pass | Status |
|---|---|---|
| Opening POC (item 10) | Duplicate-zktag rejection survives a server restart, both documents; handoff completes end-to-end from the phone's own browser at `http://127.0.0.1:8787` over `adb reverse`, against the sideloaded debug build | Passed, device-confirmed 2026-09-04 — [M3-POC-EVIDENCE-2026-09-04.md](../logs/M3-POC-EVIDENCE-2026-09-04.md). Re-confirmed on v0.6.0 build 2026-09-05 (store re-read as 2 zktags/2 bindings across the restart) — [M3-EXIT-ROWS-v0.6.0-2026-09-05.md](../logs/M3-EXIT-ROWS-v0.6.0-2026-09-05.md) |
| Tier-B duplicate rejection (item 4) | Second scan of the same document at this site shows "already registered"; chip_auth caveat disclosed | Passed, device-confirmed 2026-09-04 — page string owner-confirmed ("Already registered at this site" block shown); the pre-restart same-document repeat (no server restart in between) remains node-test-only, not device-run — [M3-POC-EVIDENCE-2026-09-04.md](../logs/M3-POC-EVIDENCE-2026-09-04.md). Re-confirmed on v0.6.0 build 2026-09-05, both documents, post-restart (`already_registered=true` both NL and US) — [M3-EXIT-ROWS-v0.6.0-2026-09-05.md](../logs/M3-EXIT-ROWS-v0.6.0-2026-09-05.md) |
| Payload/store display (item 3) | Page displays what the app sent back (verdict/presentation payload from `chiproof`) and the store's relevant state for the transaction (e.g. zktag-already-seen yes/no) | Passed, device-confirmed 2026-09-04 — [M3-POC-EVIDENCE-2026-09-04.md](../logs/M3-POC-EVIDENCE-2026-09-04.md). Not re-read on the v0.6.0 build 2026-09-05 — page strings are page-side and were only reviewed visually by the owner that session, not independently re-confirmed by the orchestrator — [M3-EXIT-ROWS-v0.6.0-2026-09-05.md](../logs/M3-EXIT-ROWS-v0.6.0-2026-09-05.md) |
| Tier-A indistinguishability (item 5) | Page-visible comparison of two tier-A presentations shows no distinguishing field | Passed, device-confirmed 2026-09-04 (3 fields differ as specified by D77: `challenge.nonce`, `challenge.issued_at`, `challenge.expires_at` — no signature field exists in this build, by design, D27) — [M3-POC-EVIDENCE-2026-09-04.md](../logs/M3-POC-EVIDENCE-2026-09-04.md). Re-confirmed on v0.6.0 build 2026-09-05 via `compare.mjs`'s `diffPresentations` on the server's stored presentations: same 3-field diff, no regression — [M3-EXIT-ROWS-v0.6.0-2026-09-05.md](../logs/M3-EXIT-ROWS-v0.6.0-2026-09-05.md) |
| Fixed threshold (item 6) | No threshold picker present; threshold 18 enforced | Passed — `THRESHOLD` env removed, hardcoded 18; device runs 2026-09-04 show `threshold=18` on every transaction — [M3-POC-EVIDENCE-2026-09-04.md](../logs/M3-POC-EVIDENCE-2026-09-04.md). Now also backed app-side by §6.5 S1's preset list `{15,16,18,21,60,65}` and per-origin first-seen lock, device-confirmed 2026-09-05 — [M3-SCANNER-S1-EVIDENCE-2026-09-05.md](../logs/M3-SCANNER-S1-EVIDENCE-2026-09-05.md). Re-confirmed on v0.6.0 build 2026-09-05, including S1's lock surviving an in-place version upgrade (not just a force-stop/reopen) — [M3-EXIT-ROWS-v0.6.0-2026-09-05.md](../logs/M3-EXIT-ROWS-v0.6.0-2026-09-05.md) |
| Hosting (item 7) | Demo reachable from the phone's own browser at `http://127.0.0.1:8787` via `adb reverse`, against the sideloaded debug build (D76) | Passed, device-confirmed 2026-09-04 — [M3-POC-EVIDENCE-2026-09-04.md](../logs/M3-POC-EVIDENCE-2026-09-04.md). Re-confirmed on v0.6.0 build 2026-09-05 — [M3-EXIT-ROWS-v0.6.0-2026-09-05.md](../logs/M3-EXIT-ROWS-v0.6.0-2026-09-05.md) |
| Trust list (item 8) | Demo verifier rejects a client identity other than the pinned scanner package+cert digest | Moved to §6.5 S4 by D78 (not an M3 criterion) — no package-name/cert-digest check exists in `apps/demo` or `chiproof`, and the OpenID4VP wire carries no such field; enforceable only through a device-attestation evidence plug (Play Integrity / Key Attestation), built alongside S4; M3 relies on attester-key binding (D38/D39) instead, a disclosed limit — [M3-POC-EVIDENCE-2026-09-04.md](../logs/M3-POC-EVIDENCE-2026-09-04.md) |
| No scanner changes (item 13) | Demo works against the released scanner APK unmodified | **CLOSED, 2026-09-05.** Owner chose option (a): `v0.6.0` was cut incorporating §6.5 S1–S3/D79, re-baselining M3's "released APK" to `v0.6.0` rather than re-running against the untouched `v0.5.0` build. The exit-row re-run against the actual released build — item 10 (restart), item 4 (duplicate rejection, both documents), item 5 (tier-A diff), item 6 (threshold incl. S1), item 7 (hosting) — has now been carried out against the build at the `v0.6.0` release tag (`de97c82`); all re-confirmed. There is no signed release keystore in the repo or on disk, so "the released scanner APK" means, and has only ever meant, the debug build sideloaded at the release tag's commit — stated as a fact for the owner, not treated as a gap. Item 3 (payload/store display) is page-side and was not independently re-read this pass. See [M3-EXIT-ROWS-v0.6.0-2026-09-05.md](../logs/M3-EXIT-ROWS-v0.6.0-2026-09-05.md). |

## 6.4 M3b scope — placeholder (D73)

Not a build scope list — a list of what must be decided before any M3b item can be written into
this PRD (NO-GO #10 applies to M3b exactly as it does to M3). M3b is rung 1 (disclosure, not
delegation) — it does not touch the rung-2 agent layer (backlog, D108).

**Status (2026-10-25, D109(h)): IN BUILD (approved 2026-10-25, D109(h)).** Was PARKED by D108
(2026-10-24). All items below were ruled (D89–D106), with D107 recording a further round of owner
rulings on points the first §6.9 draft raised. The build-scope design at §6.9 was approved by the
owner on 2026-10-25 as amended (D109(h)), which satisfies NO-GO #10 for that amended scope and
nothing wider. Nothing of tier C is built yet; the scanner still refuses every tier-C request
until the build lands. See `backlog.md`.

**D109 amendment (2026-10-25), approved as amended by D109(h):** the owner revived M3b and reduced
its scope. Items 3-6 below that rested on operator-signed membership statements (the statement
format, its signing tool, its lifetime bound and the lifetime-knob wording) are SUPERSEDED by
D109(b); the tier-C gate is now a build-time pin list of exact hostnames in `operator.json`
(D109(g)1-2). D109(h) further DEFERS the per-presentation cap, the shared ceiling and the
per-origin lock (D91, D93, D107(c)-(e)) and replaces the holder consent list (D89, D107(a)) with
one setting. The decision text below is kept as the historical record, not rewritten.

Must be decided before M3b is scoped:

1. **DECIDED — verb vocabulary (D90, 2026-09-07; closes Q34's vocabulary half), name-match
   comparison rule (D101, 2026-09-26) and nationality exclusion-set cap (D102, 2026-09-26) both
   now ruled.** All four candidates ship in v1: expiry-bucket booleans (`expiry > 3mo` / `> 6mo` /
   `> 1yr`); name-match (true/false against a site-supplied name, Q22's no-similarity-scores rule
   unchanged); DOB-range (boolean); nationality in two boolean modes (exclusion — not in a
   site-named set — and confirmation — matches one site-named nationality); and document-number
   match (boolean true/false only, the value itself never disclosed, since `document_number` is
   mode-B's zktag derivation field, D9). See decisions.md D90.
   **Name-match comparison (D101):** case-insensitive, hyphen-insensitive, strips accents (`José`
   → `JOSE`), treats the MRZ `<` filler as a separator; still a hard boolean only. Rationale: DG1/
   MRZ data carries no diacritics, so without stripping accents most accented names would falsely
   fail against a document that never had the accent. Disclosed limitation, flagged as the
   orchestrator's recommendation rather than owner-ruled: MRZ field truncation can make a
   sufficiently long, correctly-typed name fail to match — no "match the truncated prefix" rule
   was adopted. No further normalization (e.g. `ß`→`SS`, `Ø`→`O`, transliteration tables) is
   adopted; any such case is an OPEN edge case for M3b's own POC. See decisions.md D101.
   **Nationality exclusion cap (D102):** an exclusion set may name at most 10 countries; a request
   naming more than 10 MUST fail loudly (no mint) — without a cap, naming "every country but one"
   is a confirmation predicate disguised as an exclusion. See decisions.md D102.
2. **DECIDED — per-tier limits and cumulative-disclosure cost (D91, 2026-09-07; closes Q34's
   limits half).** Flat app-side hard cap of 3 tier-C predicates per presentation, no per-domain
   operator knob (the cap rides on D20's existing pinning gate rather than adding a new one, and
   living in code rather than operator config means a rogue/misconfigured `operator.json` cannot
   widen it). Repeated presentations: a per-origin LIFETIME predicate set, capped at 3, mirroring
   D74's per-origin lock/refuse persisted-record shape exactly — same storage class, not a new
   mechanism; same disclosed-not-mitigated limit as D74 (per-install, resets on reinstall, doesn't
   cover one owner running many origins). This closes the accumulation question but does not
   close item 4 below — see decisions.md D91. D91(d)'s re-ask reading is now CONFIRMED
   (2026-09-07, owner: "same 3 or subset of it") — a later request from a locked origin is
   answered when every predicate it asks is drawn from the locked set (the full set or any
   subset of it), and refused outright if it introduces any predicate outside that set; a subset
   re-ask discloses nothing new since the site already received those answers on the origin's
   first visit, and the flat cap of three is unchanged either way. Separately, D93 (2026-09-07,
   owner: "leaning towards #3") settles how this per-origin lock is KEYED when one holder
   presents two different documents at the same origin: per-document records, but the
   three-predicate ceiling counts across all of that holder's documents at that origin, not per
   document — the cap protects what a site learns about a PERSON, and only the device (never the
   site, which cannot tell the two presentations are the same holder under mode-B unlinkability)
   is positioned to enforce that. The per-origin tally record must store predicate names only,
   never the zktags/document identifiers asked against, so the record itself cannot become a
   cross-document correlation. D93 does not touch D38 (signing-key binding stays origin + zktag,
   per document) — a separate tally record, not a key. See decisions.md D91, D93; D38, D74.
3. **DECIDED — issuer-key pinning mechanism (D94, 2026-09-07/08); closes Q49 together with
   D89.** An operator's trusted tier-C challenge-issuer public keys are pinned via `operator.json`
   (§6.7) at build time, validated and baked in exactly like the existing verifier hostname
   allowlist — build-time because D81 model A already puts operator trust there for every other
   knob; the check is fully offline. D94 also corrects a misreading raised in the same exchange:
   routing (`av://` VIEW intent) carries no key at all; per D20 the SITE signs its challenge and
   the site's own public key is its identity; the APP holds the pinned list and verifies that
   signature offline; per D19/D20 tier C is REFUSED, never downgraded, if the signing key isn't
   pinned — nothing is published for a verifier to read. Q49's two halves (holder-vs-verifier-list,
   closed by D89; pinning mechanism/UX, closed by D94) are now BOTH closed — **Q49 is fully
   CLOSED.** See decisions.md D19, D20, D81, D89, D94; questions.md Q49.
4. **Q11 for tier C — boundary-list MECHANISM AND CONTENTS both now fixed (D95, D97); the
   information-bearing residual stays OPEN, unchanged in kind.** D95 (2026-09-07/08) ruled that
   DOB-range/expiry-bucket boundaries come from a FIXED, published list — the same shape as D74's
   preset threshold list — never chosen by the requesting verifier; a boundary not on the list
   fails loudly (no mint), mirroring D74 exactly. D97 (2026-09-26) then ruled the list's actual
   contents, which D95 had left explicitly open: expiry buckets are exactly `{3 months, 6 months,
   1 year}`; DOB-range boundaries are exactly the six ages `{15, 16, 18, 21, 60, 65}` — the same
   list D74 already uses for tier-A/B thresholds, reused rather than separately invented. (In the
   live exchange the owner first named 31 as a candidate DOB boundary and corrected it to 21 on
   being asked to confirm; 31 was never adopted — the six-age list above is final.) **Consequence,
   stated as an observation and not further ruled on:** because tier-C DOB-range now uses the
   identical ages D74 already uses for tier-A/B thresholds, a tier-C DOB-range predicate answers
   the same kind of question tier A/B already answers, now attached to an attributed tier-C
   presentation rather than an unlinkable tier-A one. Even with both lists fixed, three predicates
   still yield three bits about a date of birth, on top of D91's flat cap of 3 and D93's shared
   per-origin ceiling — those bound frequency, not whether the three bits are informative. Q11 for
   tier C remains OPEN on that residual point only; the boundary-selection mechanism and the list
   contents are both now DECIDED. See decisions.md D74, D91, D93, D95, D97; questions.md Q11.
5. **DECIDED — capability switch and per-verifier authorization (D96, 2026-09-07/08); three of
   D96's four open points now ruled (D98-D100, 2026-09-26), one still open.** Two D96 rulings.
   (a) Tier C stays REFUSED by default — §6.2 item 13's current behaviour is unchanged for any
   build that doesn't opt in; an operator's build enables tier-C support via a new `operator.json`
   knob, alongside D94's pinned issuer keys. (b) Per-verifier authorization is an operator-signed,
   SHORT-LIVED membership statement: the verifier fetches its own statement and includes it in its
   request; the app verifies it offline against the operator key already pinned by D94 — no live
   app-to-operator API call, which the owner's first instinct proposed and D96 rejects (it would
   create exactly the site-visit tracking channel this project exists to avoid, breaks under no
   network, and buys no security over build-time pinning since D81 model A already gives the
   operator full build control). Add/revoke a verifier without a rebuild; expiry does the
   revoking.
   **Lifetime (D98, DECIDED; range widened by D103, 2026-09-27):** owner-chosen per statement,
   bounded between 1 week and 6 months (was 1 week to 1 month under D98). This range IS the
   revocation window — a dropped verifier's already-issued statement keeps working until it
   expires, up to six months worst case — a disclosed consequence, not mitigated, the same shape
   as D74/D91/D93. Clarification confirmed in the exchange: this is the lifetime of the SITE's
   permission to ask, not the validity of any holder's answer; D103 separately recorded that a
   distinct "how long may a site rely on an answer it already has" validity period (option B) was
   considered and NOT adopted. **Enforcement of the ceiling (D105, DECIDED — REFUSE, 2026-09-28):**
   a statement claiming a lifetime over the six-month ceiling is refused outright — never clamped
   down to six months, never accepted as claimed — consistent with D100's refused-never-downgraded
   rule and D19/D20; clamping would silently accept a statement the operator's own signing process
   should never have produced, hiding a misconfigured or compromised signer. **Enforcement of the
   floor (D106, DECIDED — ACCEPT, 2026-09-28):** a statement claiming a lifetime shorter than the
   one-week floor is ACCEPTED, not refused or clamped up — a shorter lifetime only makes revocation
   faster, carrying no holder-side risk to guard against; the floor exists to bound operator
   renewal burden, not to protect holders, so there is nothing to refuse. **Enforcement of the
   bound is therefore now fully DECIDED, both halves:** over the ceiling is refused (D105); under
   the floor is accepted (D106) — an intentional asymmetry, since the ceiling protects holders and
   the floor does not.
   **Binding (D99, DECIDED — YES):** the statement MUST bind both the verifier's origin and its
   challenge-signing key; an unbound statement is a bearer token any site could replay.
   **Expired statement (D100, DECIDED — REFUSE):** never downgraded, matching D19/D20.
   **Format (ii) remains OPEN** — not addressed by this pass; left for the owner or M3b's POC.
   **Default (D104, 2026-09-28, forward requirement for M3b, not built today):** when the
   statement-lifetime knob is actually added to `operator.json`'s schema (§6.7) and to
   customer-guide §7.2's walkthrough, the reference `operator.json` MUST default it to 1 week —
   the short end of D103's range, not the 6-month ceiling — and §7.2's prose MUST say plainly that
   a longer choice is a slower ban: a site the operator drops can keep asking tier-C questions
   until its current statement expires, up to 6 months at the range's maximum. D103's range itself
   is unchanged; operators keep the full choice, D104 only sets the default and a documentation
   requirement. Neither `operator.json` nor §7.2 is edited today — there is no tier-C knob in
   either file yet, and this item does not itself satisfy NO-GO #10's scope gate.
   See decisions.md D19, D20, D76, D81, D94, D96, D98, D99, D100, D103, D104, D105, D106; milestones.md §6.2 item 13, §6.7.
6. **DECIDED — "preapproved list" meaning (D89, 2026-09-07).** The preapproved list is a
   HOLDER-side consent list — what the holder is willing to disclose, matching D19's framing that
   tiers are defined by what the holder is told — not a VERIFIER-side request list. D20's issuer
   pinning remains the separate, unaffected gate on which sites may reach tier C at all. How a
   request is matched against this consent list (all-or-nothing, no partial answers) is decided
   by D92. See decisions.md D89, D92.

**All six §6.4 items are now ruled (D89–D96), with a further round of rulings (D97–D102,
2026-09-26) closing most of what those six left open.** Item 1's name-match comparison rule and
nationality exclusion cap are now ruled (D101, D102). Item 4 (Q11 for tier C)'s boundary-list
mechanism AND contents are both now fixed (D95, D97); the residual "three predicates are still
three bits" observation stays open, unchanged in kind — not a gap left by this pass, a fact
about the predicate shape itself. Item 5 (D96)'s lifetime, binding and expired-statement points
are now ruled (D98-D100); its enforcement-of-bounds sub-point is now also fully ruled (D105 the
ceiling, D106 the floor) — only its statement-format point (ii) remains explicitly OPEN for the
owner or M3b's own POC. That does NOT mean M3b is scoped: **NO-GO
#10 still requires an owner-approved M3b scope section, written into this PRD, before any M3b
code is written** — no such scope section exists yet, and none is written by this pass.

**M3b's opening riskiest-assumption POC candidate (SUPERSEDED: the POC as approved is §6.9.1, retargeted by D109(f) and amended by D109(h)):** a live tier-C
presentation carrying exactly one verb from item 1's now-decided vocabulary, refused end-to-end
when the requesting site's issuer key is unpinned (D20's "refused, not downgraded, if unpinned"
rule), and accepted end-to-end when it is pinned — on both real documents.

## 6.5 Scanner follow-ups (post-M2, owner-approved 2026-09-03, D74) — not in M3, built before/alongside M3b

Scanner-side (`apps/scanner`) items resolved same-day as §6.3's DP4 and by separate owner UI
feedback. None of these are M3 scope (§6.3 item 13 — M3 runs against the released scanner APK
as-is); all are scope-gated (NO-GO #10) into the PRD here so they may be built ahead of or
alongside M3b without a further gate check.

**Status at close (D108, 2026-10-24):** S1, S2 and S3 are built and device-confirmed (shipped in v0.6.0; evidence `docs/logs/M3-SCANNER-S1-EVIDENCE-2026-09-05.md`, `docs/logs/M3-SCANNER-S2-S3-EVIDENCE-2026-09-05.md`). S4 is NOT built and is parked in `backlog.md`.

1. **S1 (ENHANCEMENT, D74) — preset threshold list, per-origin lock, named exceptions.** The app
   MUST carry a fixed, published preset threshold list — `{15, 16, 18, 21, 60, 65}` — living in
   the app/spec, never chosen by the verifier. A verifier's requested threshold MUST be one of
   this list; any other value MUST fail loudly (no mint), extending §6.2 item 13's fail-loudly
   rule from "tier absent/invalid" to "threshold not on the list." The app MUST lock the FIRST
   threshold it sees from a given origin and MUST refuse (loudly, in-app; the site learns nothing)
   any later request from that SAME origin for a DIFFERENT threshold — the same per-origin binding
   shape as D38's key binding. The app MUST also carry an exact-hostname exception allowlist (no
   wildcards) of sites permitted to ask more than one threshold; membership is an app-side
   decision, never the verifier's own choice (a rogue verifier could otherwise just ask for
   whatever it wants — an unrestricted "over 43" request). Touches `RequestTrust.thresholdOf`
   (Q35's parse path) and needs a small per-origin persisted record — same persistence class
   already built for item 23's log store (D70(b)), not a new storage mechanism. Riskiest
   assumption to POC first: does the per-origin lock need to survive reinstall? Answer: NO — the
   lock is per-install state, like every other on-device record in this app, and this MUST be
   disclosed rather than treated as a gap to close (reinstalling the app resets the lock, exactly
   as it resets the log and any bound key). Limitation to state alongside this item, not hidden: a
   site that legitimately needs two thresholds must run two separate origins; a single owner
   operating many origins to route around the lock is not covered by this item, the same
   disclosed-not-mitigated shape every other mode-A limitation in this PRD already takes. Applies
   in mode A too — the per-origin memory is on-device state, never anything that crosses the wire.

   **As built (`ThresholdPolicy`).** The lock is keyed by hostname only — not the full
   `scheme://host:port` origin D37's consistency check uses — mirroring D38's attester-binding key
   shape on the verifier side. The check runs at request verification, on both the `av://` intent
   path and the paste path, before the S2 question line renders or Scan/Verify can arm. A refusal
   never becomes `pendingHandoff`: it shows the same non-wiping blocking notice (#12) S3 uses for
   paste refusals, writes a value-free Log-tab entry, and sets the last-scan line to
   `refused ✗` — nothing reaches the site. The lock is written only on first-seen admit, never on
   a refusal. It persists as a separate field in the item-23 log store file (old snapshots load);
   "Clear log" does not clear it. Per-install, as stated above — an uninstall resets it, disclosed.
   `NAMED_EXCEPTIONS` ships empty (no site has been granted the multi-threshold exception). On the
   demo side, `apps/demo` gained a test-only `?threshold=` query parameter on `POST
   /ui/presentations` (the preset list plus `43` for the not-a-preset negative test) so the S1
   negatives could be exercised without a second demo instance; the page's own default threshold
   (18) is unchanged.

   Status: built 2026-09-05, device-confirmed, commit `def7b64`.
   [M3-SCANNER-S1-EVIDENCE-2026-09-05.md](../logs/M3-SCANNER-S1-EVIDENCE-2026-09-05.md) — **S1 is
   CLOSED.** §6.5 S1–S3 are now all closed; S4 remains blocked on an attestation plug (see below).
2. **S2 (ENHANCEMENT, D74) — the question shown before the tap.** The app MUST show the exact
   question being asked — e.g. "This website asks if you are over 18" — above the Verify button,
   before the user taps it. The question text MUST be sourced from the signed request object
   (Q35's parse path — the same threshold/tier fields item 1 above locks against), never a fixed
   hardcoded string, so the shown question always matches what is actually about to be sent. A
   bare local scan with no pending verified request MUST show "Local scan (no site)" instead, per
   D46's existing wording for that case. This extends D47's disclosure requirement to the
   pre-action moment, not just the post-outcome dialog.

   Status: built 2026-09-05, device-confirmed (see evidence doc), uncommitted at time of writing. [M3-SCANNER-S2-S3-EVIDENCE-2026-09-05.md](../logs/M3-SCANNER-S2-S3-EVIDENCE-2026-09-05.md)
3. **S3 (ENHANCEMENT, owner UI feedback 2026-09-03) — scan-pane cleanup.** Owner, verbatim: "big
   blob of text top screen, and below a place for manual av:// paste and it still reads the result
   below it, clean all that up." Read against the actual layout
   (`apps/scanner/app/src/main/res/layout/activity_main.xml`,
   `apps/scanner/app/src/main/java/com/tananaev/passportreader/MainActivity.kt`) this identifies
   three concrete elements: (a) the "blob of text" is `@+id/description`
   (`@string/info_scan_passport`), a five-sentence paragraph combining form instructions with
   §6.2 item 5's no-document-field-storage disclosure — it renders at full length on every screen
   view, not just first run; (b) the manual paste field is `@+id/handoff_manual_input` inside the
   `handoff_container` block (`@string/handoff_manual_hint`, "…or paste an av:// link /
   request_uri") — per the layout's own comment this is the documented fallback-of-the-fallback
   for the cross-device QR path (D69), not a dev/debug-only affordance, which matters for where it
   may be relocated to; (c) `@+id/report_view` (the value-free verdict/report, §6.2 item 5) sits
   directly below `handoff_container` in the same vertical `ScrollView`, so its screen position
   shifts whenever the handoff block's height changes (e.g. `handoff_status` text growing) —
   this is the "reads the result below it" complaint. **DP7 resolved** (owner, 2026-09-03: "ok,
   make it cleaner then, move it up above scan/verify and above it what this website is asking to
   verify and then once pasted, wthout active av it shows verify button replacing scan, or easier,
   it always overrides whatever is there"). Scan-pane order, top to bottom: (1) the S2 question
   line (item 2 above) — "This website asks if you are over 18," sourced from the signed request,
   or "Local scan (no site)" (D46) when nothing is pending; (2) the manual `av://` / request_uri
   paste field, kept — per (b) it is D69's documented fallback-of-the-fallback, not disposable —
   moved UP to sit directly under the question line; (3) the single Scan/Verify button (item 20's
   verb rule unchanged: "Tap and verify" when a request is pending, "Tap and scan" bare); (4) —
   REVERSED by D79(a), 2026-09-05: the full value-free `report_view` is removed from the scan pane
   entirely, not merely repositioned. In its place, ONE height-stable summary line sits directly
   under the Scan/Verify button: `Last scan: <origin hostname>, over <threshold>: <true|false>,
   delivered ✓` for an accepted presentation; `refused ✗` / `not sent ✗` / `read failed ✗` replace
   `delivered ✓` for the corresponding non-accepted outcomes; a local scan (no pending request)
   reads `Last scan: Local scan (no site), chip check passed ✓|failed ✗`. This line is derived
   from the same log entry the Log tab renders (D46, value-free content) — never a second writer
   of scan results — persists across app restarts with the log itself (item 23) and clears with
   "Clear log," and is hidden from the moment a new `av://` link is captured until that session
   writes its own log entry (both refinements owner-approved together, D79(a): "yes, do both").
   The Log tab is now the ONLY reader of full scan reports, and the Diagnostics tab (added by this
   same build, see below) is the ONLY reader of probe output — probes never leak into the scan
   pane or the Log tab. Rationale for "delivered" over a bare checkmark: a bare ✓ reads as the
   site's own approval, which is never the app's to show (customer-guide.md, "Two answers, two
   places"). The old `description` text block (a) is gone from the pane entirely; the item 5
   no-storage disclosure it carried moves to a persistent but non-primary location (an info/About
   affordance), not deleted, since D1/NO-GO #7's disclosure obligations don't lapse.

   Paste semantics — history (owner, 2026-09-03, superseded same day by the rule below): "it always
   overrides whatever is there"; "once pasted it nullify whatever is there"; "if that would cause a
   loop hole to your no link mid-read then we can ask user to close the app and reopen it for a
   fresh paste." These quotes described the earlier always-replaces-on-paste design (bare field,
   refused-mid-read via a D43 dialog, close-and-reopen). They are kept here as history only — the
   rule actually approved is the one below, from owner question 2026-09-03 ("what's the final
   sane/safe choice of link pasting that is deliberate? i see few solutions, one is a button paste
   link that changes into text and paste then press verify, that's a deliberate clear/reset, or
   paste itself is an active reset, or restart app if an active link paste would be dimmed...") and
   approval "yes both, write them in" (D75).

   Paste semantics (final, D75): the pane carries a **"Paste link" button**, not a bare text field.
   Idle or pending state: tapping it reveals the paste field; the pasted `av://`/request_uri is
   applied deliberately — the S2 question line (item 2 above) updates from the pasted request, any
   previously pending request is discarded, and the Scan/Verify button becomes "Tap and verify"
   (item 20's verb rule). This is the deliberate clear/reset; a bare always-visible field is
   rejected because an accidental clipboard paste could silently replace a real pending request
   from the site the user is on. Locked / read-in-progress state: the "Paste link" button is
   **dimmed/disabled** with a one-line non-interactive hint: "Finish this scan, or close and reopen
   the app to paste." No dialog, no abort control; in-flight work is never cancelled (item 13's
   admission guard, finding #10's mitigation, `HandoffAdmission`, unchanged). Dimming replaces the
   earlier D43-dialog wording because nothing happened that needs acknowledging. "Paste itself
   resets" (a bare field acting on paste) is explicitly rejected: it is the only variant where the
   app changes state without a deliberate press.

   References so nothing already decided is silently undone: §6.2 item 4 (mode capture, no
   re-added control), item 13 (admission guard / `HandoffAdmission`, the LOCKED-paste refusal
   above), item 17 (switch to scan pane on handoff intent), item 20 (verify-vs-local control and
   button verb), item 25 (mode sentence in the outcome dialog); decisions.md D43 (blocking
   dialogs), D46 ("Local scan (no site)" wording), D47 (disclosure), D52 (dialog strings); finding
   #10 (the mint-path race this reuses the admission guard against) — none of those mechanisms are
   altered by this cleanup beyond what is stated above.

   **Delivery wording (D79(b), 2026-09-05, closes finding #22).** Owner: "delivered for both A/B.
   Logs say Delivered (minted) / Delivered (bare presentation sent) and PASS (read); that way the
   UI front isn't front-loaded with confusing detail and logs carry details." The raw report
   `verdict:` line is `DELIVERED (minted)` for tier B, `DELIVERED (bare presentation sent)` for
   tier A; `PASS (read)` is reserved ONLY for the chip sanity check with no site involved (the
   local-scan case); every non-accepted delivery outcome prints `REFUSED`/`NOT SENT` with the
   reason (unchanged from the `VerifierRefusal` classifier above). The post-scan blocking dialog
   for both tiers reads "Delivered — the website shows the result." — replacing "ID scanned
   successfully" everywhere it appeared — and the D71/item 25 mode-disclosure sentence still
   follows it, unchanged. This closes finding #22 (the hardcoded "verdict: PASS" line) by
   replacing the vocabulary itself, not by patching the four other response-handling branches
   finding #22 named as untouched — those branches now emit `REFUSED`/`NOT SENT` under this same
   rule.

   **Two FIXes (D79(c), device-confirmed 2026-09-05).** (i) After an access-establishment failure
   (wrong MRZ details typed in, BAC `SW 6982`) the MRZ text fields stay populated, the admission
   lock releases so Scan/Verify re-arms, and the pending handoff link survives — unchanged from
   transient chip-loss (card lifted mid-read) behaviour, which already worked this way. Device
   evidence: multiple wrong entries in a row, each re-arming the button and keeping the pending
   link, followed by a final corrected entry that delivered successfully — server verdict
   `allowed=true attester=matched` against a fresh store. (ii) Diagnostics probe output (the
   masterlist check and device-key self-test) is routed by report channel to the Diagnostics pane
   only, never to the scan pane's last-scan line or the Log tab — same single-writer
   `applyReportText()` fan-out named in the S2/S3 evidence doc, extended to keep the two channels
   disjoint under the D79(a) rewrite above.

   **Scope note (D79(d), 2026-09-05, owner).** "ok write it into S3 then S1 next, i just want to
   avoid the feat creep." **S3 is CLOSED as of this amendment** — no further scan-pane UI work
   proceeds without a new PRD entry (NO-GO #10). Next item to build is S1 (preset threshold
   list/lock/exceptions, still Planned per the table below).

   Status: built 2026-09-05, device-confirmed (see evidence doc), uncommitted at time of writing. Two FIXes delivered alongside (non-2xx `direct_post` misreport; `error_read` dialog wording). D79(c)'s two further FIXes (wrong-details re-arm; Diagnostics routing) are also device-confirmed 2026-09-05; D79(a)'s last-scan line and D79(b)'s delivery wording are recorded here ahead of the code that will implement them (NO-GO #10 scope gate); D79(d) closes S3. [M3-SCANNER-S2-S3-EVIDENCE-2026-09-05.md](../logs/M3-SCANNER-S2-S3-EVIDENCE-2026-09-05.md)

4. **S4 (ENHANCEMENT, D78) — client trust list (FR10) via attestation plug.** Moved here from
   §6.3 item 8 by D78: a package name + signing-cert digest is not carried by the OpenID4VP wire,
   so the only source of both is a device-attestation token (Play Integrity / Key Attestation) —
   the client-identity trust list is enforceable only through an attestation evidence plug, not
   as a wire-contract change to the existing scanner/verifier exchange. The verifier pins the
   package name and signing-cert digest as read FROM that attestation token; this item is built
   together with the first attestation plug, not before it. Play App Signing re-signs the APK
   (D75, §6.6 deliverable 2), so the Play-distributed build's digest differs from local builds —
   any verifier config built under this item MUST account for both once the Play digest is known.
   Until an attestation plug exists, M3's client identity is the attester key bound on first sight
   (D38/D39) — a per-device, per-site identity, weaker than FR10's per-app one; this is M3's
   disclosed limit, not a defect to silently work around.

   **Status (D108): NOT BUILT — backlog.** It is gated on the first attestation plug, which was never built (the reference app ships bare, D27). Until then the client-identity limit above stands, disclosed. See `backlog.md`.

Play Store listing: see §6.6.

## 6.6 Play Store closed-testing track — **PARKED (D83, 2026-09-06)**

**Superseded by D83:** the Play developer account referenced below was closed for inactivity,
and the owner declined to open a new one ("no point of playstore ... building adb sideloading
is so easy and any dev knows how to do that"). Items 1–6 below are PARKED indefinitely — not
deleted, kept as a record and as a path an operator or a future owner decision may revive. Item
7 (release-signing keystore) stays **DONE** and remains load-bearing: it now signs the
GitHub-released APK instead (see §6.8 row 7). Distribution is GitHub Releases + sideload. See
decisions.md D83.

This track remains **showcase-only** (not M3's reachability answer): M3 reaches the scanner via
`adb reverse` against a sideloaded debug build (D76), and nothing here changes that. How a
Play-installed (non-sideloaded) user would reach a verifier over HTTPS at all — the demo's plain
Node process has no public HTTPS origin, and D76 rejected zkagent hosting one itself (NO-GO #3)
— is not solved by M3 and was parked as **Q50** (questions.md), now CLOSED by D83 (moot — there
is no Play-installed owner's build). Owner, 2026-09-04: "playstore will be clearer when we get
to it."

Item ordering: item 7 (release-signing keystore) precedes item 1 (first upload) — the Play
upload key is derived from the local keystore, so the keystore must exist first. **D81
(2026-09-05, owner) closed Q51 points (1) and (2): distribution model (A) confirmed** — items
1–4 below are the owner's own showcase-listing deliverables only, not a requirement on
operators (who make their own listing/hosting decisions for their own package). **D82
(2026-09-06, owner) closed Q51 point (3): the owner confirmed the showcase Play listing goes
ahead, closed testing track only** — "do we need to build ours on play anyways as a way of
showcasing without sideloading and knowing what stores ask for?", confirmed "#2 yes." Q51 is
now fully CLOSED and this section is **LIVE (ungated)**: item 7 (release-signing keystore) is
the first task, then item 1 (closed-track upload), then item 2 (record the Play digest). See
questions.md Q51; decisions.md D81, D82.

1. First upload goes to a **closed testing track**, never production, using the current release
   line (v0.5.0 or later under D72 lockstep). Production is a separate, later owner decision.
   **Status: PARKED (D83, 2026-09-06)** — the Play developer account was closed for inactivity
   and the owner declined to open a new one; no upload happens. The public privacy policy
   (`docs/product/privacy-policy.md`) and the owner's internal Play Console crib sheet
   (`docs/product/play-listing.md`, data safety form reasoning, permission justifications, store
   listing text, screenshot rule, post-upload recording checklist) were already written and are
   kept — the privacy policy as the reference build's honest data-handling statement (now linked
   from README.md), the crib sheet as reference-only material for an operator who does list on a
   store (banner added, D83(e)). The console upload never happened.
2. Deliverable 1: the **Play App Signing certificate digest**. Rationale: FR10 makes the scanner's
   signing-cert digest its identity in every verifier's trust list (D17); Play App Signing
   re-signs the APK, so the Play-distributed build has a DIFFERENT digest from local builds —
   every verifier config, including the M3 demo (§6.3 item 8), MUST pin the Play digest (alongside
   or instead of the local one, owner to decide when known). Record the digest in the evidence
   log, never assume.
3. Deliverable 2: the **list of review objections/requirements** Google raises (data safety form,
   NFC permission, identity/sensitive-data scrutiny, target API level), recorded as evidence and
   fed into the PRD before M3b.
4. Note: new personal developer accounts must run a closed test with a minimum tester count over a
   fixed period before production access is granted — starting early costs nothing; the exact
   numbers are Google's current policy and MUST be checked at the time, not copied from this doc.
5. MUST NOT: no production release from this item; no store listing text that markets v1 as
   zero-knowledge (NO-GO #7, D1); no PII in screenshots (use the demo's value-free report view).
6. Riskiest assumption to test first: that a passport-NFC-reading app with no backend passes
   closed-track review at all.
7. **Release-signing keystore (D80).** Android APK signing uses a Java keystore (`.jks`/`.p12`)
   holding ONE key pair plus a self-signed X.509 certificate; APK signature schemes support RSA,
   DSA, and EC P-256 — NOT Ed25519. Scope: one key per app identity (package), not per device or
   user; every update must be signed by the same key or Android refuses the install; losing the
   key means the installed app can never be updated, only replaced under a new package name. Why
   it matters: the SHA-256 of that certificate is the app's identity — the "package + cert digest"
   of FR10/D17 that a verifier trust list pins under §6.5 S4; every debug build so far is signed
   with the SDK's throwaway debug key, so its digest is meaningless; v0.6.0's
   `assembleRegularRelease` produced only `app-regular-release-unsigned.apk` because no keystore
   exists anywhere (recorded in `docs/logs/M3-EXIT-ROWS-v0.6.0-2026-09-05.md`). Play App Signing:
   Google holds the app-signing key, the developer keeps an upload key; the Play-distributed APK
   carries Google's digest, which is why item 2 above says the Play digest is unknown until a real
   upload. Requirements (MUST): generate one EC P-256 keystore with `keytool`; store it under the
   already-gitignored `secrets/` directory (`.gitignore` line 13) with an offline backup; feed it
   to the build only via the existing `KEYSTORE_FILE`/`KEYSTORE_PASSWORD`/`KEY_ALIAS`/
   `KEY_PASSWORD` env vars (`apps/scanner/app/build.gradle.kts` `signingConfigs`, unchanged); never
   commit the keystore or passwords; record the local cert digest in the evidence log and PRD when
   generated; from the next release, `assembleRegularRelease` MUST produce a signed APK, and the
   release evidence MUST record the digest. This local key is distinct from the Play upload key
   (item 2) and from the device attester keys in AndroidKeyStore (D38) — three different keys,
   three different purposes. Status: PRD-gated, not built. **Reframed by D81 (2026-09-05):**
   this item is now (a) a documented per-operator keystore recipe (the same `keytool`/`secrets/`/
   env-var procedure above, followed by each operator for their own build/keystore/fingerprint)
   and (b) the owner's own showcase keystore, generated the same way — neither is a shared
   secret; the requirements above (MUST) apply to whoever runs them, operator or owner.
   **Status: DONE, both parts.** Part (a) recipe written — `docs/product/customer-guide.md` §7.1
   (`35013fa`). Part (b) — the owner generated the showcase keystore 2026-09-06
   (`secrets/zkagent-showcase.p12`, EC P-256, alias `zkagent-showcase`, gitignored), read its
   certificate digest (`1f6bceae0ffe9c2b326f2aab2202f0bdf3df7e5bdd8fac50aba2e1d318407264`), ran
   `assembleRegularRelease --offline` to produce the project's first **signed** release APK
   (`app-regular-release.apk`, `com.zkagent.scanner` versionCode 4 / versionName 0.6.1), and
   independently verified the signature and digest with `apksigner verify` — both readings agree.
   See `docs/logs/M3-KEYSTORE-EVIDENCE-2026-09-06.md`. This obligation now carries forward as a
   standing MUST on `/release`: **from the next release, `assembleRegularRelease` MUST produce a
   signed APK and release evidence MUST record the digest** — no dedicated release-checklist doc
   exists in this repo to also carry that MUST (checked: no `RELEASING`/`CONTRIBUTING` doc, no
   checklist section in `CHANGELOG.md` or `docs/wiki/history.md`), so it stands recorded here, in
   D80, and in the evidence log only.

## 6.7 Operator knobs (D81, scoped by D82) — released in v0.7.0

Purpose: an operator-configuration layer (D81 point 2) so UI/knob adjustments do not require
forking or editing source, consistent with the one-upstream, Apache-2.0 distribution model (A)
confirmed by D81 point 1. Owner, D82: "this is apache (whatever it allows) open source
distributed as is with some knobs and operator filling like attestation, list of approved
entities, ages. operators can change knobs preferably not core code."

**Mechanism (D82, owner-endorsed): one build-time `config.json` (name provisional), bundled into
the APK at BUILD time.** Changing it means rebuilding the app; the file is NOT fetched at runtime
from any server — a runtime fetch would reintroduce a hosted dependency (NO-GO #3) and a
remotely-controllable allowlist. Operators edit the file, never source.

**Knob list (D82; all build-time):**

1. **Tier mode** — A+B, or B only.
2. **Verifier hostname allowlist** — exact hostnames, no wildcards (D74). This is the "list of
   approved entities" / "accepted av links" knob. The `av://` link FORMAT itself is defined by
   chiproof, versioned with it under D72 lockstep, identical for every operator, and is NOT a
   knob — operators pick up format changes on rebuild.
3. **Threshold list** — subset of the fixed list `{15,16,18,21,60,65}` (D74).
4. **Attestation / evidence plug selection** — which plug the build wires in; default none (the
   reference build ships bare, D27 unchanged). Build-time because it is code wiring and plugs
   like Play Integrity are per-app.
5. **Hostnames allowed tier C** — field reserved, MUST stay empty until M3b exists (D73);
   scoping it is M3b's job, not this item's.
6. **Question wording and branding.**
7. **Signing — NOT a config knob.** The keystore is a build input the operator supplies
   (D80/D81); the config may reference it but it lives outside the JSON.

Future operator guide item: where an operator needs more than one threshold at one registrable
domain (e.g. a state government site serving both seniors and minors), the pattern is one
question per hostname — each subdomain listed separately in knob 2's allowlist, each locking its
own threshold under D74 — not a new knob. This case is parked as Q52 (DEFERRED, follows broad
adoption), not scoped further here.

### 6.7.1 Current state (read from source, 2026-09-06; "now" column added post-POC)

**Now (post-`962ac96`)**: knobs 2 (verifier hostname allowlist), 3 (threshold list), and 6-branding
(`app_name`) are config-driven from `apps/scanner/operator.json`, single-writer each, POC
device-verified. Knob 1 (tier mode) has a gate (`OperatorPolicy.isTierAllowed`) but is unit-tested
only, not yet device-checked in a real refusal round. Knobs 4 (evidence-plug selection) and 5
(tier-C hostnames) remain validate-only — the schema accepts/rejects a value (rules 6/7) but no
code path consumes an evidence-plug choice or a non-empty tier-C list yet.

| Knob | Hardcoded today at | Writers |
|---|---|---|
| 1. Tier mode | Doesn't exist — `RequestTrust.tierOf`/`TierOutcome` (`MainActivity.kt:750-751`) accept "A" or "B" unconditionally; no B-only gate anywhere | 0 (no gate to own) |
| 2. Verifier hostname allowlist | Doesn't exist as a general accept-list — any request whose JWS verifies (item 14) is accepted from any origin. The only adjacent hostname set is `ThresholdPolicy.NAMED_EXCEPTIONS` (`ThresholdPolicy.kt:46`, ships `emptySet()`), which is the *multi-threshold* exemption list (D74 rule 3), a different knob than "approved entities" | 1 writer, but wrong knob |
| 3. Threshold list | `ThresholdPolicy.PRESETS = setOf(15,16,18,21,60,65)` — `ThresholdPolicy.kt:40` | 1 (test file independently re-spells the same six ints, per AGENT_RULES "independent expectation," not a second production writer) |
| 4. Evidence plug selection | Doesn't exist as an operator choice. `DeviceKey.PREFERRED_EVIDENCE_TYPE = "sig-ed25519/1"` (`DeviceKey.kt:169`) is the device's own algorithm-preference order (ed25519 vs p256, a capability fallback), not a plug picker; tier A always ships bare (`evidence: []`, D27, hardcoded in `MintGate.kt`/item 9); no attestation plug (Play Integrity etc.) exists in the tree to select | 1 writer for the preference constant; the bare-vs-plug choice is hardcoded control flow, not data |
| 5. Hostnames allowed tier C | Doesn't exist — tier C is refused outright, fail-loud, at `RequestTrust`/item 13; there is no field to reserve yet | 0 |
| 6. Question wording | Not a stored string — computed at runtime, `SessionDisplay.kt:202`: `"This website asks if you are over ${verified.threshold}"` | 1 (the template expression) |
| 6. Branding (app name) | `apps/scanner/app/src/main/res/values/strings.xml:5`, `app_name = "zkagent Scanner"` | 1 |
| 7. Signing | Not a knob (by design) — `KEYSTORE_FILE`/`KEYSTORE_PASSWORD`/`KEY_ALIAS`/`KEY_PASSWORD` env vars, `apps/scanner/app/build.gradle.kts` `signingConfigs` (~line 76) | 1 |

Also found, relevant to the mechanism: `buildFeatures.buildConfig = true` is already on
(`build.gradle.kts` ~line 121); one `buildConfigField` already exists (`GIT_SHA`, item 24); one
product flavor ("regular", `flavorDimensions`/`productFlavors` ~lines 90-95) already exists. No
`resValue`, no per-operator flavor, no `assets/operator.json`-shaped file anywhere today.
`apps/demo` (verifier side) reads its own knobs from env vars today (`LINK_SCHEME`, `THRESHOLD`
hardcoded to 18, `SCOPE_DOMAIN`, attester/request-signer keys) — a separate, later
chiproof/demo-side config concern, noted but out of §6.7's scope per this task's instructions.

### 6.7.2 Mechanism

**Recommend (A): `apps/scanner/operator.json`, read by Gradle at configure time, emitted as
`buildConfigField`/`resValue` constants — no file ships inside the APK, no on-device parsing.**
Rejected: **(B) bundled `assets/operator.json` parsed at app startup** — adds a runtime JSON
schema-validation path (a new on-device failure surface item 4's `MasterlistVerifier`/`M0Probe`
lesson already warns against: corrupt input must refuse, never silently default) for something
Gradle can already resolve once, at build time, with a failed build instead of a failed boot.
**(C) one product flavor per operator** — multiplies build variants for what is really just
constant substitution; the existing single "regular" flavor stays, `operator.json` supplies its
values. (A) keeps every constraint below true with the least new surface: no runtime fetch
(NO-GO #3), one writer per knob (Gradle → `BuildConfig`/`R.string`, never re-read from a file at
runtime), and a bad file is a build failure, never a silent default.

### 6.7.3 Schema v1 (D84-approved)

```json
{
  "schema_version": 1,
  "operator": { "name": "Acme Verify", "contact_url": "https://acme.example/contact" },
  "tiers": "A+B",
  "thresholds": [15, 18, 21],
  "verifiers": ["state.gov", "127.0.0.1"],
  "multi_threshold_verifiers": [],
  "evidence_plug": "none",
  "tier_c_verifiers": [],
  "strings": { "app_name": "Acme Age Check" }
}
```
`tiers`: enum `"A+B" | "B"`. `thresholds`: non-empty subset of `{15,16,18,21,60,65}` (D74).
`verifiers`: exact **hostnames** (D84 point 1), not full origins — no scheme, no port, no
wildcards; matches the app's existing D74/D38 host-only key shape (`ThresholdPolicy.hostnameOf`),
so `http://127.0.0.1:8787` (the M3 dev origin, D76) is expressed as `"127.0.0.1"`. **Designed, NOT built (D109(i)1, in build):** `verifiers` may alternatively be the string `"any"` (age verification open to any site); see §6.9.2 item 23 and rule 12 below.
`multi_threshold_verifiers`: exact hostnames (same shape as `verifiers`), default `[]` — this
**is** D74 rule 2's named-exceptions list, already in code as `ThresholdPolicy.NAMED_EXCEPTIONS`
(`ThresholdPolicy.kt:46`, ships empty); exposing it as a knob is the model-A-correct reading of
D74 rule 2's "membership is the app's decision, never the site's own" — under D81's per-operator
distribution, the operator's own build IS "the app." Q52 (multi-threshold-per-registrable-domain)
stays DEFERRED; this field is the existing single-hostname exception mechanism, not a new
capability. `evidence_plug`: enum `"none"` only today — `"sig-ed25519/1"`/`"sig-p256/1"` are
device-capability fallbacks the app already always tries, not a build choice; no attestation plug
exists in the repo to name as a second enum value yet, so the schema reserves the field but
validates against a list of exactly one until one ships. `tier_c_verifiers`: MUST be `[]`; a
non-empty array fails the build until M3b exists (D73). **Designed, NOT built (D109, approved
2026-10-25 by D109(h), in build; the code and the build rules above are unchanged today):** under the amended M3b design
(§6.9.2 items 1-2, D109(g)1) this field would become the tier-C pin list, exact bare hostnames
only (the `verifiers` shape rule: no scheme, port, wildcard, comma, quote or backslash), with no
key pin, next to a separate tier-C on/off switch whose exact shape is settled in code. Rule 7
below would be relaxed from "non-empty fails" to that shape rule, and rules 10 and 11 would add
the two consistency failures. Membership statements no longer exist in the design. `strings`: `app_name` only, in every
schema version — **not deferred to v2, permanently scoped this way (D84 point 3)**: the
question-line template (`SessionDisplay.kt:202`) stays computed in code because D74 rule 3 makes
its exact wording a user-protection requirement, not branding — an operator may rename their app,
never rephrase or soften the disclosure sentence. Signing is **NOT** in this file (D80/D81) — it
stays in `secrets/`/env vars. The reference `operator.json` is **committed** (D84 point 2) — its
`thresholds`/`verifiers` are today's already-public hardcoded values.

### 6.7.4 Validation rules (build fails on any of these)

1. `schema_version` missing or not `1`. 2. `tiers` not exactly `"A+B"` or `"B"`. 3. `thresholds`
empty, or containing a value outside `{15,16,18,21,60,65}` (D74). 4. `verifiers` empty, containing
a wildcard, a scheme, or a port. 5. `multi_threshold_verifiers` containing a wildcard, a scheme,
or a port (same shape rule as `verifiers`; empty is valid, the default). 6. `evidence_plug` not
`"none"` or a plug id that actually exists in `chiproof`'s registry at build time. 7.
`tier_c_verifiers` non-empty. 8. Any unknown top-level or nested key, including any key under
`strings` other than `app_name` (typo/scope-creep protection — never silently ignored). 9. File
missing entirely, and the build is not explicitly the reference build's own committed default.

**Designed additions, NOT built (D109(g)7, approved 2026-10-25 by D109(h), in build; rules 1-9 are not renumbered):**
rule 7 would be relaxed to the shape rule above (it forces `tier_c_verifiers` empty today);
10. the tier-C switch is on and `tier_c_verifiers` is empty; 11. `tier_c_verifiers` is non-empty
and the tier-C switch is off. Both are build failures, so the two knobs can never disagree
silently.

**Designed addition, NOT built (D109(i)1, in build; rules 1-11 are not renumbered):** 12.
`verifiers` is neither a non-empty array of bare hostnames nor exactly the string `"any"`, or the
array form contains the entry `"any"` (rule 4 keeps the array shape violations). See §6.9.2 item 23.

### 6.7.5 Riskiest assumption + POC (not built) — the allowlist gate is the target

The riskiest assumption is not the threshold list (already enforced in code, `ThresholdPolicy`) —
it is the **verifier hostname allowlist itself, which does not exist today**: any request whose
JWS verifies (item 14) is currently accepted from any origin, with no hostname check at all.
Candidate: *`operator.json`'s `verifiers` list can drive a NEW, single-writer allowlist check —
refusing an unlisted hostname loudly, the same way item 13's tier-absent case already fails loud
— built from `BuildConfig` without touching `ThresholdPolicy`'s existing D74 enforcement.* POC
shape: (1) a unit truth table for the new check (listed hostname → admit; unlisted → refuse
loudly, no mint; `multi_threshold_verifiers` membership orthogonal to this check, D74 rule 1 is
unconditional per §6.7's cross-reference) — proves the logic in isolation, same discipline as
`ThresholdPolicyTest`. (2) A real device run against `apps/demo`: one build with the demo's
hostname (`127.0.0.1`) in `verifiers`, one build without it — the listed build completes a
handoff normally, the unlisted build refuses the request in-app before any read/mint, the site
learns nothing (mirrors D74 rule 1/2's own refusal shape). (3) The build-fails-on-bad-file half
kept from the original candidate: a malformed `operator.json` (e.g. an out-of-list threshold or a
wildcarded hostname) fails `assembleRegularDebug` before `:app:compileRegularDebugKotlin`, never
ships a default. Pass = all three observed: unit table green, both device runs show the correct
admit/refuse behavior, and the bad-file build fails closed.

### 6.7.6 Interaction with existing decisions

D74's lock (first-seen threshold per hostname, refuse a change) stays in `ThresholdPolicy`
exactly as built — the config only supplies which values are legal, never how the lock behaves;
`multi_threshold_verifiers` is D74 rule 2's existing exception list, exposed, not reimplemented.
Q52 stays deferred (D84; multi-threshold-per-registrable-domain is an allowlist-authoring
pattern, not a schema change). The owner's own showcase build becomes just one `operator.json`
among many (D81) — its `thresholds`/`verifiers` are today's hardcoded values, committed as the
reference config (D84 point 2). `docs/product/customer-guide.md` §7.2 now carries the
`operator.json` walkthrough.

### 6.7.7 Decided (D84)

1. **`verifiers` is hostname-only.** Ruled — matches the code's existing D74/D38 convention.
2. **The reference `operator.json` is committed.** Ruled — it is today's already-public values,
   not a secret.
3. **`strings` in schema v1 is `app_name` only, permanently — not a v2 deferral.** Ruled, with
   the owner's own added reasoning: D74 rule 3 makes the question line's exact wording a
   user-protection requirement, so it stays computed in code (`SessionDisplay.kt:202`), never a
   configurable string, in any schema version.
4. **`apps/demo`'s verifier-side config is deferred to a later round.** Ruled, owner: "if this
   round is already big" — §6.7 stays scanner-scoped; the demo's env-var knobs are untouched.

**Status: released in v0.7.0.** POC + validation pass device-verified 2026-09-06 (G1 pre-fetch
gate, G3 tier mode, G4 multi-threshold all confirmed on device against apps/demo; evidence log).
Close-out pass the same evening: accept (delivered) path device-confirmed on the fixed build for
the first time; the `DroppedOutcomeRelay` duplicate-notice fix stays unit-proven only, not
device-reproduced (0 of 14 cold launches triggered the platform double-instance relaunch); the
negative script now covers all 9 validation rules; customer-guide §7.2 written. The release-signed
gated build (§6.8 row 5) is now **DONE**: `v0.7.0`'s `app-regular-release.apk` verified with
`apksigner`, certificate SHA-256 digest `1f6bceae0ffe9c2b326f2aab2202f0bdf3df7e5bdd8fac50aba2e1d318407264`
matches the showcase keystore. Remaining: apps/demo operator config deferred (D84).

See `docs/logs/M3-S67-POC-EVIDENCE-2026-09-06.md`.

State: schema v1 and D84 are approved; the §6.7.5 POC is built and device-verified (`feat/s67-poc`
`962ac96`, evidence log above). ENHANCEMENT candidate, sequenced after §6.6 items 7/1 unless the
owner reorders. See decisions.md D81, D82, D84; questions.md Q51, Q52.

## 6.8 Android release list (complements CI/publish) — owner-approved 2026-09-06, ENHANCEMENT

`ci.yml`/`publish.yml` (LIBRARY_CONVENTIONS §5) gate only `packages/chiproof`; neither runs
`apps/scanner` or `apps/demo`. This list covers what those two workflows do not, until CI is
extended to them (candidate rows below, PRD-gated for BUILD and requiring owner sign-off on any
`.github/` edit per AGENT_RULES). Run by hand as part of Phase 1 of `/release`.

1. Scanner unit tests green: `JAVA_HOME=<JDK17> ./gradlew :app:testRegularDebugUnitTest`; parse
   the pass/fail count from the JUnit XML output, never from prose.
2. Demo tests green: `apps/demo && npm test`; same caveat.
3. Lockstep version (D72): `packages/chiproof/package.json` + lockfile, `apps/demo`'s lockfile,
   scanner `versionCode`/`versionName`, and one `CHANGELOG.md` section all agree.
4. Signed release APK: `assembleRegularRelease` with the four env vars from `pass`
   (`KEYSTORE_FILE`/`KEYSTORE_PASSWORD`/`KEY_ALIAS`/`KEY_PASSWORD`); output MUST be
   `app-regular-release.apk`, never `-unsigned.apk` (D80 MUST).
5. Digest check: `apksigner verify --print-certs` digest ==
   `1f6bceae0ffe9c2b326f2aab2202f0bdf3df7e5bdd8fac50aba2e1d318407264` (showcase cert, customer-guide
   §7.1; `M3-KEYSTORE-EVIDENCE-2026-09-06.md`). A mismatch is a **STOP** — wrong keystore means an
   un-updatable install.
6. Release evidence row: tag, APK sha256, digest, test counts — recorded in `docs/logs/` or the
   release's `CHANGELOG.md` section, matching how v0.6.1's release recorded its own evidence.
7. **GitHub Release (D83 — §6.6 PARKED, Play superseded):** attach the signed APK, its sha256,
   and the cert digest to the `vX.Y.Z` release; installers verify with `apksigner`. Run by hand
   inside `/release`'s hand-back sequence (the sequence is push, tag, publish.yml, then
   `gh release create vX.Y.Z app-regular-release.apk --notes …` — not run as part of writing
   this doc).

(a) This list is run by hand inside `/release` until CI covers rows 1–2 and 4–5. (b) Owner intent:
once the Android use case has succeeded (a signed GitHub-released build has been installed and
used by a third party), fold this into
LIBRARY_CONVENTIONS as a new section for app packages — a hamr0-repo change, not zkagent's; noted
here only, not actioned.

## 6.9 M3b scope (IN BUILD — approved 2026-10-25, D109(h))

> **IN BUILD (approved 2026-10-25, D109(h)).** Was PARKED by D108 (2026-10-24). The owner reviewed a
> plain-language summary of the tier-C plan on 2026-10-25 and APPROVED it with amendments (D109(h)),
> which satisfies NO-GO #10 for the scope as amended below and nothing wider. Nothing in this
> section is built yet; the scanner still refuses every tier-C request until the build lands.

Written 2026-09-28 now that every §6.4 decision point (D89–D106), plus a further round of owner
rulings on the items the first draft still left to the owner (D107), is ruled. This section drafts
what those rulings imply for a buildable M3b scope, in §6.2/§6.3's numbered MUST/MUST NOT house
format, so the owner has a single section to approve or amend rather than reconstructing scope
from nineteen decisions by hand. The owner approved it as amended on 2026-10-25 (D109(h)); §6.4
remains the decision record behind it.
**Revised 2026-09-28 (fix pass):** corrects eight internal item-numbering errors, two
items misdescribing `chiproof`'s role, and adds D107's five rulings (consent-list UI, the
membership-statement signing tool, per-site lock visibility, the per-origin store's uncapped
storage requirement, and the one-phone-per-person assumption) — see §6.9.3's traceability table
and the CHANGELOG entry for the fix pass's full change list. That revision did not grant approval,
and neither did the 2026-10-24 close-out (D108), which only parked the section. Approval came on
2026-10-25 (D109(h)).

**Amended 2026-10-25 (D109, owner rulings; approved with further amendments by D109(h)).** The owner
revived M3b (tier C) and cut the design down: the membership-statement subsystem (items 3, 4, 5, 6)
is DROPPED, the authorization gate is a simple pin list (items 1 and 2), and item 8's whole
vocabulary is built. Five new items (18-22) fold in four small fixes and a status procedure. Item
numbers are stable; dropped numbers are reserved, the way §6.2 item 19 was withdrawn.
**D109(h) (approval, 2026-10-25) then DEFERS items 10, 11 and 12 (per-site question caps and the
lock view) and REPLACES item 7's per-predicate consent list with one app setting, "Allow
additional verifications" (OFF by default); item 13 simplifies accordingly. Their numbers are
reserved and their text is kept as the design for later (`backlog.md`).** The pre-D109
text of the dropped items is in git history (commit 7075932 and earlier) and in decisions.md
D94-D107, which are annotated, not rewritten.
**D109(i) (2026-10-25, owner) then OPENS age verification (tiers A and B) to any site, adds
the `"verifiers": "any"` form (new item 23, §6.7.4 rule 12), and shares ONE age lock per site across
tiers A, B and C (items 8, 9, 13; limitation 17(k) rewritten). Tier C stays gated by the pin list,
the operator switch and the holder setting, unchanged.** **D109(j) (2026-10-25, owner) then rules the
two points D109(i) left: the real-TLS test point runs on an owner VPS (item 23(d)), and sites named in
`multi_threshold_verifiers` are exempt from the shared age lock for tier C as well (items 9, 13,
17(k)).**

Components referenced below: **scanner** (`apps/scanner`), **chiproof**
(`packages/chiproof`), **demo** (`apps/demo`), **operator.json** (`apps/scanner/operator.json`,
schema §6.7.3), **docs** (this file / customer-guide.md).

### 6.9.1 Opening riskiest-assumption POC (RETARGETED by D109(f); written first, before any other item)

Per standing project practice (every module opens with a POC on real devices/documents aimed at
its riskiest assumption before the easy parts get built, MEMORY.md), M3b's POC is retargeted.
**The old target is gone.** The pre-D109 POC tested the two-layer statement mechanism (pinning gate
via statements, statement replay, statement expiry). Items 3-6 are DROPPED, so two of its three
criteria have nothing to test, and the third (the pin gate) is now a plain list.

**Candidate riskiest assumption now (argued, for the owner to confirm):** *the five item-8
predicates can be evaluated correctly from the DG1/MRZ fields the scanner reads off a REAL chip,
on both of the owner's documents, with name-match the weakest.* Why this and not the pin gate:

- The pin gate is low risk. It is a hostname-list check of the same shape as the `verifiers`
  allowlist, whose own POC already passed on device (§6.7.5, 16 PASS lines), and the origin and
  ES256 JAR checks under it are built and device-confirmed (D34/D37). New crypto: none.
- The field reads are the part nobody has ever done on a real chip. A source search of the scanner
  finds names and date of birth referenced only in `MainActivity`, and no date-of-expiry or
  nationality field read anywhere. So two of the five predicate inputs are new reads, and the
  other three are known only from tier A/B use.
- Name-match compares a human-typed name to a machine-readable-zone name that is ASCII-only,
  transliterated, `<`-padded and length-truncated (D101 itself flags this and left the ß/Ø cases
  open). It is the likeliest predicate to be wrong on a real document and the likeliest to be wrong
  silently, as a false `false`. The M2 lesson applies directly: a path never run live hides a HIGH
  gap (mode A, #21), and a parser validated only on synthetic fixtures misses real wrapper layers.
- A false answer here is a compliance error for a KYC-style site, not a UX blemish.

**Pass/fail criteria, pre-registered.** Each negative is planted and paired with a non-vacuity
pass on the SAME document in the SAME run (the negative must be shown to actually exclude
something, not merely fail to be exercised):

1. **Field reads and booleans on real chips (both documents, mandatory).** For each document and
   each of the five predicates (expiry bucket at each of the three fixed buckets, name-match,
   DOB-range at each fixed age, nationality in exclusion mode and in confirmation mode,
   document-number match): the required field is read non-empty; a true-input case returns
   `true`; a planted-wrong-input case returns `false`. Pairs: name (the holder's own name typed
   as a person would, versus a one-character-changed name); document number (right versus one
   digit off); nationality (exclusion set without versus with the holder's own nationality;
   confirmation right versus wrong); expiry and DOB (a boundary on each side of the true value).
   **An unreadable or missing field MUST produce a refusal, never `false`:** a planted negative
   (flipped DG1 byte, the M0 negative reused) MUST give `ok:false`/refused, distinct from a real
   `false` answer, so "no" and "could not tell" are never conflated.
2. **Name-match normalisation observed on real MRZ data (both documents).** Record per document,
   by field name and verdict only: whether the name field is at its length limit (truncated or
   not), the filler and separator pattern, and for each D101 rule (case, hyphen, accent-strip,
   `<` as separator) whether it was exercised by the real name. The rules' correctness on the
   edge cases a real document does not contain (ß, Ø, over-long names) is covered by a SYNTHETIC
   unit table, labelled synthetic and not counted as real-chip evidence. ß/Ø normalisation stays
   unruled; the POC reports "observed" or "not observable on these documents" and does not rule.
   **Owner ruling (D109(g)6):** "not observable on these two documents" is ACCEPTED, covered by
   the synthetic table clearly labelled synthetic; no third document is sourced.
3. **Pin-list gate negative (one document is enough: the gate fires before any chip read).** With
   the build's switch on and `127.0.0.1` pinned: a single-predicate tier-C request from `127.0.0.1`
   is ACCEPTED end to end (positive control: the verifier receives a non-empty presentation with
   `ok:true`); the same request from `localhost` (a second hostname served by the same spike,
   which the scanner already treats as a dev-key origin, and which is in `verifiers` for this
   test) is REFUSED in-app before any read or mint, and the verifier sees nothing. **Amended
   (D109(i)1): once item 23 is built, `verifiers` may be `"any"`, and then `localhost` is ANSWERED
   for age verification (tiers A and B) without being listed, but it is still REFUSED for tier C,
   because tier C is gated only by the pin list, the operator switch and the holder setting, never by
   `verifiers`. This criterion therefore holds for TIER C ONLY: it is the tier-C request from
   `localhost`, not any age request, that must be refused, and the build under test keeps
   `localhost` out of `tier_c_verifiers`. Before item 23 is built the criterion is unchanged and
   `localhost` is listed in `verifiers`.** Because both
   hostnames use the same dev key and the same code path, the only difference is the pin, so the
   refusal is keyed on the pin and not on an incidental difference. Second negative control: a
   build with the OPERATOR switch OFF refuses even `127.0.0.1` with the existing §6.2 item 13
   tier-C refusal, proving the operator switch is a separate gate (the holder setting is
   criterion 4). The pin is by exact hostname only (D109(g)1), so there is no
   key-pin case.

4. **The single holder switch (D109(h)): "Allow additional verifications" (one document is enough:
   the gate fires before any chip read).** With the build's operator switch on and `127.0.0.1`
   pinned, and the holder setting OFF (the default): a single-predicate tier-C request from
   `127.0.0.1` is REFUSED in-app before any read or mint, and the verifier sees nothing. With the
   holder setting turned ON in the same run, the identical request is ANSWERED end to end (the
   verifier receives a non-empty presentation with `ok:true`); that paired positive makes the
   refusal non-vacuous, so the refusal is keyed on the setting and not on an incidental
   difference. A tier-A request from the same origin with the setting OFF is still answered, so
   the setting gates tier C only. There is no per-predicate case and no cap case: all five
   predicates are allowed to every pinned site in this build (D109(h)).

**Run 2026-10-25: PASSED** (criteria 1-4 all met against their pre-registered rules; Pixel 6a, APKs built from `2720daa`) — see `docs/logs/M3B-POC-EVIDENCE-2026-10-25.md`. Name order is RULED (D109(k): two labelled fields, `given_names` and `surname`, so no order exists; finding #26 item 1 closed); UX findings in `.claude/remember/findings.md` #27.

Criteria 1 and 2 MUST run on BOTH real documents the owner holds (US passport, NL ID card);
criteria 3 and 4 MAY run once. **No PII or document values go into any evidence artifact:** record
field names, verdicts and timings only (including read-to-boolean timing per predicate). The
holder's true name and number are typed on the device by the owner and are never saved, logged
(the scanner and the test verifier MUST NOT log the site-supplied value being tested), or
screenshotted; no screen showing a raw field is accessibility-snapshotted. The evidence document
is `docs/logs/` by the house naming, recording verdicts only.

**Also surfaced by this POC, not resolved by this document:** the ß/Ø normalisation edge cases
D101 left open are observations the POC should report where the real data touches them, not
rulings. The nationality code namespace (D109(g)3: ISO alpha-3 as written in the MRZ, ICAO special
codes distinct and never mapped) and the expiry-bucket month arithmetic (D109(g)4: calendar months
with end-of-month clamping) are RULED; the POC checks the real data against them.

### 6.9.2 Scope items

1. **Tier-C capability switch (`operator.json`, D96(1)). KEPT (D109(b)).** `operator.json` schema
   gains a new `tier_c: { "enabled": false }`-shaped knob (exact field name/shape settled in code,
   consistent with the existing `tiers`/`evidence_plug` enum knobs in §6.7.3). Default MUST be
   `false`/absent for every existing and reference build — §6.2 item 13's tier-C refusal is
   UNCHANGED for any build that does not explicitly opt in. Only an opted-in build's scanner may
   reach any of items 2 and 7-13 below; an unopted build MUST continue to fail loudly on a tier-C
   request exactly as it does today. **Consistency rules (CONFIRMED, D109(g)7):** a build with the
   switch on and an empty `tier_c_verifiers` (new §6.7.4 rule 10), or with a non-empty
   `tier_c_verifiers` and the switch off (new §6.7.4 rule 11), MUST fail the build, so the two
   knobs can never disagree silently. Touches:
   **operator.json** (schema), **scanner** (`RequestTrust`/item-13 gate). Tag: ENHANCEMENT.
   Implements: D96(1), D73.
2. **Tier-C pin list (`operator.json`). KEPT and SIMPLIFIED (D109(b); amends D94).** The build's
   `operator.json` pins the exact sites allowed to ask tier C. **This no longer depends on
   membership statements** — it is a plain list, checked fully offline, baked in at build time
   and validated like `verifiers` (a malformed entry fails the build, never ships a silent
   default). A tier-C request from an origin not on the list is refused before any chip read or
   mint, the site learns nothing, and the refusal is not downgraded to A/B (D19/D20). **What is pinned is what the scanner can already verify today:** the request object is an ES256 JAR whose
   origin must match `client_id`, `request_uri` and `response_uri` (`RequestTrust`, D34/D37), and
   whose signing key is resolved from that origin. **Pin type (RULED, D109(g)1): exact hostnames
   only.** Reuse the already-reserved `tier_c_verifiers` field (today rule 7 forces it empty),
   with the same bare-hostname validation as `verifiers` (D84/D86: no scheme, port, wildcard,
   comma, quote, backslash). Each pinned host SHOULD also appear in `verifiers` (a build whose `verifiers` is `"any"`, item 23, needs no listing; `verifiers` never gates tier C, only this pin list does). **Consistency
   rules (CONFIRMED, D109(g)7):** the tier-C switch on with an empty list fails the build (new
   §6.7.4 rule 10), and a non-empty list with the switch off fails the build (new rule 11). Pro:
   no new field, no new key format, a site's key rotation costs nothing, smallest change. Con,
   disclosed at 17(i) and kept (D109(g)2): the gate trusts whoever controls that hostname's DNS and
   TLS, because the key is fetched from the origin at request time. **Identity departure
   ACCEPTED (D109(g)2):** D20/D94 say the issuer's public key is its identity; for tier-C pinning
   identity is the exact hostname, consistent with D37 for tiers A/B (the key is resolved from the
   origin).
   **Rejected alternative (D109(g)1), noted only: "K", hostname plus the site's request-signing
   key** (a JWK thumbprint per entry baked into the build). It would survive a DNS or TLS hijack and
   match D20/D94's wording, but needs a new entry shape and validation rules and a new APK for
   every site key rotation. The owner chose not to build it; D109(h)6 keeps it as a PROPOSAL / open
   question in `backlog.md` (not built, not scheduled).

   **What a pinned site may ask (D109(h)2):** all five item-8 predicates, with no per-site question
   permission and no cap in this build. An unlisted site is refused before any chip read.
   Allowlist entries that name which questions a site may ask are LATER (`backlog.md`).

   The pin list is the ONLY tier-C authorization: there is no authority behind
   it and no operator-side onboarding process for third-party sites (owner: skipped, 17(j)); adding
   or dropping a site is an edit of `operator.json` and a rebuild. Per D94's routing correction,
   which stands: nothing is read from the `av://` link and nothing is published by the operator
   for a verifier to fetch. Touches: **operator.json** (schema; rule 7 relaxed, new rules 10 and 11
   appended), **scanner** (`OperatorPolicy`). Tag: ENHANCEMENT. Implements: D94 (amended), D96(1),
   D20.
3. ~~Membership-statement signing tool~~ **DROPPED (D109).** Number reserved, as §6.2 item 19 was.
   There are no membership statements, so nothing needs a tool to sign them. Supersedes D107(b).
4. ~~Membership statement verification, app-side~~ **DROPPED (D109).** Number reserved. Its
   job (binding to origin and signing key, D99) is covered by what already exists: the JAR's
   origin-consistency check and key resolution (`RequestTrust`), plus item 2's pin. Supersedes
   D96(2), D99 and D100 as applied to statements.
5. ~~Membership-statement lifetime bound~~ **DROPPED (D109).** Number reserved. Supersedes D98,
   D103, D105, D106.
6. ~~Reference default and guide wording for the lifetime knob~~ **DROPPED (D109).** Number
   reserved. Supersedes D104. The customer guide's tier-C section instead states the pin-list
   limitations in item 17(h)-(j).
7. **One holder setting: "Allow additional verifications" (D109(h); REPLACES the per-predicate
   consent list of D89/D107(a)).** The Settings tab carries ONE switch, labelled "Allow additional
   verifications". **It is OFF by default** (D107(a)'s reasoning, kept: a pre-set "yes" means
   agreeing before looking, which is not consent). **Off:** the app answers age verification
   (tiers A and B) only and refuses every tier-C request, from any site, before any chip read or
   mint. **On:** the app answers tier-C requests from pinned sites (item 2), and only those; the
   switch never widens the pin list. The holder can flip it at any time. It is stored as one boolean
   in a small app-side settings record (same persistence class as `ThresholdPolicy`'s per-origin
   record); a reinstall returns it to OFF (17(a)). Owner reasoning, recorded (D109(h)3): per-question
   and per-site holder controls are over-complicated; "basic age verification on, extra
   verification via the app" keeps the choice binary and still lets a holder who does not want
   extra verification say no. **Per-scan disclosure is unchanged and still the real consent
   moment:** the existing S2 question line (§6.5 item 2; `SessionDisplay.questionTextFor`) shows,
   above the Verify button and before the biometric confirmation, the exact question taken from the
   signed request ("This website asks if you are over 18", with tier B appending the recognition
   wording). **Tier C extends it:** when the verified request is tier C, the same line shows one
   plain-words line per predicate asked (for example expiry beyond 6 months, date of birth
   consistent with at least 21, nationality outside a named set), built from the signed request in
   code and never from a fixed string, so what is shown is what will be sent. The line names the
   question and MUST NOT echo a site-supplied name or document number (item 16(a)); exact wording
   is settled in code and, like the age sentence, is not operator-brandable (D84 point 3). The
   setting is DISTINCT from item 1 (the operator switch) and item 2 (the pin list): a tier-C request
   is answered only if the operator switch is on, the origin is pinned, AND the holder setting is
   on. **Considered and NOT built (D109(h)):** one checkbox per question (D107(a)), per-site
   permissions and per-presentation caps; all are parked as "Per-site tier-C question permissions
   and limits" in `backlog.md`. **Superseded by this item:** D107(a)'s per-question checkboxes and
   its "unticking a locked predicate" consequence (there is no lock now, item 10). D89's holder-side
   principle (consent is the holder's, not the verifier's or the operator's) STANDS in its
   simplest form. Touches: **scanner** (one settings boolean plus a Settings-tab switch; extend the
   question line for tier C). Tag: ENHANCEMENT. Implements: D89 (simplified), D109(h)3.
8. **Tier-C verb vocabulary (D90, D101, D102).** The scanner's tier-C predicate set is exactly:
   (a) expiry-bucket booleans (item 9's fixed buckets); (b) name-match — boolean only, **two fields (D109(k)):
   the site sends `given_names` and `surname`, each compared against its own MRZ identifier (given
   names against the secondary identifier, surname against the primary), and BOTH must match; never
   a single combined string, so there is no name order for a site to know or get wrong. Given names
   must match IN FULL, as on the document ("John" against "JOHN PAUL" is `false`). Each side is
   normalized per D101 (case-insensitive, accent-stripped, MRZ `<` treated as a separator) with the
   hyphen treated as a separator equal to `<` ("Jean-Pierre" equals `JEAN<PIERRE`), after the
   site's names are transliterated by the ICAO 9303 Part 3 rules: ß to SS, Ø to OE, Æ to AE,
   apostrophe dropped; every other mapping "per ICAO 9303 Part 3 table, verify at build". The
   transliteration is covered by a synthetic table, labelled synthetic (D109(g)6). Orchestrator
   default, announced to owner 2026-10-25, not objected (D109(k)1-4)**; no similarity score (Q22
   unchanged); (c) DOB-range boolean (item 9's fixed ages, and the site's shared age lock, item 9);
   (d) nationality, exclusion mode (site names a set, MUST NOT exceed 10 countries per D102, else
   fail loudly/no mint) and confirmation mode (site names one nationality, boolean); **codes
   (D109(g)3): ISO 3166 alpha-3 exactly as written in the MRZ; ICAO special codes (for example
   `D` for Germany, others in ICAO 9303 Part 3) are their own distinct values, never mapped;** (e)
   document-number match — boolean true/false ONLY, the number itself MUST NEVER be disclosed as a
   value (it is mode-B's zktag derivation field, D9 — disclosing it as a value would hand a site a
   stable cross-site identifier). No sixth predicate and no vocabulary extension ships without a
   further owner ruling. **All five are allowed to every pinned site (D109(h)2): no per-site
   question permissions and no cap on how many a request may name.** Touches: **scanner** (predicate evaluators), **chiproof** (item 14,
   predicate-set schema on the wire). Tag: ENHANCEMENT. Implements: D90, D101, D102; Q22, Q34
   (closed).
9. **Fixed boundary lists (D95, D97).** Expiry-bucket and DOB-range boundaries come from a FIXED,
   published list, never chosen by the requesting verifier, mirroring D74's discipline exactly: a
   request naming a boundary not on the list MUST fail loudly (no mint). Expiry buckets are exactly
   `{3 months, 6 months, 1 year}`, computed as 3, 6 or 12 calendar months with end-of-month clamping
   against the coarsened `current_date` (D109(g)4). DOB-range boundaries are exactly the six ages D74 already uses
   for tier A/B, `{15, 16, 18, 21, 60, 65}` — reused verbatim, not separately invented. Touches:
   **scanner** (new `TierCBoundaryPolicy`-shaped object, sibling to `ThresholdPolicy`). Tag:
   ENHANCEMENT. Implements: D95, D97.
   **Shared age lock across tiers (D109(i)3; owner chose option A).** There is ONE age boundary
   per site in total, across tiers A, B and C. The per-hostname map D74 built for tier A/B
   thresholds (`ThresholdPolicy`, `originThresholdLocks`; hostname-keyed, uncapped, non-evicting) is
   the SAME map the tier-C date-of-birth-range predicate reads and writes; there is no second map.
   Rules: (1) whichever age a site asks first, through a tier A/B threshold or a tier-C
   date-of-birth-range boundary, is its locked age; (2) a later tier-C date-of-birth-range boundary
   MUST equal the locked age, else the WHOLE request is refused (item 13), and a later tier A/B
   threshold MUST equal it too (D74, as built); (3) the same age asked again gets the same bit, which
   is the existing lock and not a new reply cache (D109(i)1); (4) a tier-C request that names two
   different date-of-birth-range boundaries is refused whole, except at a hostname named in
   `multi_threshold_verifiers` (rule 6; orchestrator default, not owner-ruled, the consistent reading
   of D109(j)2: allowed for named sites); (5) for tier C the lock is written only
   after item 13's all-or-nothing check has passed, so a request refused for any reason (holder
   setting off, off-lock boundary, any failed predicate) does not burn the site's one age; for
   tier A/B it is written wherever `ThresholdPolicy` writes it today (orchestrator default, not
   owner-ruled: a refused tier-C request does not set the lock); (6) **EXEMPTION (D109(j)2, owner:
   "extra multi-age should be allowed"):** hostnames in `multi_threshold_verifiers` (D74 rule 2) are
   exempt from the shared age lock exactly as D74 already exempts them for tier A/B, and now for the
   tier-C date-of-birth-range predicate too: a named site may use several ages, both for age
   verification and for tier C, and two different DOB-range boundaries in one request are allowed
   for it. Every other hostname keeps rules 1-5. The reference build's list is empty. An
   operator-named site can therefore narrow the holder's age down (17(k)). The
   lock is per hostname, so many hostnames are not covered (17(b)). Expiry buckets are NOT part of
   this lock (17(k)). **Pass criteria:** (a) a unit truth table over the shared map: first ask tier B
   at 18, then tier-C DOB-range 18, answered; then tier-C DOB-range 21, refused whole; first ask
   tier-C DOB-range 21, then tier A at 18, refused; two different DOB-range boundaries in one
   request, refused whole; a refused tier-C request leaves no lock; **exemption rows (D109(j)2):** a
   hostname in `multi_threshold_verifiers` is allowed a tier-C DOB-range at 18 and then at 21 (both
   answered), allowed tier B at 18 then tier-C DOB-range 21, and allowed two different DOB-range
   boundaries in one request; **non-vacuity pair:** the same sequence from a hostname NOT in the list
   is refused at the second age (the named site is allowed two ages, the unnamed site is refused the
   second), with the list holding at least one entry for the exempt rows and a count above 0 on the
   unnamed-site refusals, so the exemption is shown to be keyed on the list; with a negative control that turns the table red
   when the tier-C path stops consulting the shared map (and a count above 0 on the refusals, so the
   guard is shown to exclude something); (b) one device run on a pinned origin (`127.0.0.1`, setting
   ON): tier-C DOB-range at the first boundary answered, a second tier-C request at a different
   boundary refused with only a generic "refused" at the verifier, the first boundary asked again
   answered. Implements: D74 (extended), D109(i)3.
10. **DEFERRED (D109(h)). Number reserved; the text below is kept as the design for later (`backlog.md`: "Per-site tier-C question permissions and limits"). Not built in this build.** **Per-presentation cap and per-origin lifetime predicate set (D91, D107(d)).** A flat, app-side
    hard cap of THREE tier-C predicates per presentation applies to every opted-in build, with NO
    per-operator knob to raise it (D91(a) — the cap rides on item 1's opt-in and item 2's pin list, not a separate
    configurable ceiling, so a rogue/misconfigured `operator.json` cannot widen it). The app records
    the exact predicate set an origin's first tier-C request asks (≤3) as a per-origin LIFETIME
    record, the same persisted-record shape D74's threshold lock uses (the same shape §6.5 S1's
    `ThresholdPolicy` already builds for tier A/B thresholds) applied to a predicate SET instead of
    a single value. A later request from that same origin MUST be answered when every predicate it
    asks is drawn from the locked set (the full set or any subset of it) and MUST be refused
    outright if it introduces any predicate outside that set (D91(d), confirmed). **Storage MUST
    (D107(d), build constraint from the orchestrator's code check):** this per-origin record MUST
    live in its OWN store that never evicts — NOT inside `ReportLog`/`ReportLogStore`, which caps
    at 20 entries and evicts the oldest (`ReportLog.MAX_ENTRIES = 20`, `ReportLog.kt`/
    `ReportLogStore.kt`). `ThresholdPolicy.kt`'s own separate, uncapped, non-evicting store is the
    precedent to follow, not the log. Why this matters, stated so it is not silently lost: if a
    site's tier-C lock lived in the capped log, the lock would silently roll off after 20 further
    scans of ANY kind — exactly the reset the owner explicitly ruled out in D107(c) below (no
    reset control, ever, for this record). Touches: **scanner** (new per-origin predicate-set
    store, uncapped). Tag: ENHANCEMENT. Implements: D91, D107(d).
11. **DEFERRED (D109(h)). Number reserved; the text below is kept as the design for later. Not built in this build.** **Shared per-person ceiling across a holder's documents (D93, D107(e)).** The per-origin lock
    (item 10) is keyed by origin + DOCUMENT (a separate record per document), but the 3-predicate
    ceiling is SHARED across all of a holder's documents at that origin — a site MUST NOT be able
    to collect three predicates from one document and a further three from a second document at
    the same origin. The tally record MUST store predicate NAMES only and MUST NOT store the
    zktags or any other document identifier asked against (D93(c)) — storing identifiers would
    itself create a durable cross-document correlation, the exact privacy leak this cap exists to
    prevent. Same storage MUST as item 10 (D107(d)): this record lives in its own uncapped store,
    never the capped log. **UI requirement (D93(e)):** scanning a second document at an origin
    whose shared ceiling is already exhausted MUST show an explanation of why it is refused (e.g.
    "this site has already asked its limit of tier-C questions for you") rather than a
    bare/unexplained refusal that reads as a broken app — D93(e) named this as a UI requirement to
    be written into a scope item once M3b is scoped; this item is that write-in. **Stated
    assumption (D107(e)):** the shared ceiling treats every document scanned on ONE install as ONE
    person — "one phone per person." Disclosed consequence, added to item 17's limitations: two
    people sharing one phone share one limit of three predicates at each site; the app has no way
    to tell them apart. Touches: **scanner** (per-origin store, refusal UI copy). Tag: ENHANCEMENT.
    Implements: D93, D107(d), D107(e).
12. **DEFERRED (D109(h)). Number reserved; the text below is kept as the design for later. Not built in this build (with no lock there is nothing to show).** **Per-site lock visibility, Log tab, view-only, NO reset control (D107(c), NEW).** Items 10 and
    11's per-origin/per-person locked predicate sets MUST be visible to the holder in the existing
    Log tab (view-only — the holder can see which sites have locked which predicates and how many
    of the cap-of-3 they have used) and MUST NOT carry any reset/clear control of any kind. Owner
    reasoning, recorded: a reset button would let a site socially engineer a holder — e.g. a
    refusal message reading "Verification failed — open zkagent, go to Log, reset this site, then
    try again" — handing that site three FRESH tier-C questions on demand and defeating D91's
    lifetime lock entirely. Reinstalling the app remains the ONLY way to reset any of this state,
    already disclosed at item 17(a). Touches: **scanner** (Log tab UI, read-only rendering of items
    10/11's store — no new write path). Tag: ENHANCEMENT, NEW. Implements: D107(c).
13. **All-or-nothing answering (D92, simplified by D109(h)).** With the holder setting (item 7)
    OFF, the app refuses the tier-C request outright. With it ON, the app answers EVERY listed
    predicate, or refuses the WHOLE request if any listed predicate cannot be evaluated (a field
    that is unreadable or missing, a boundary off item 9's lists, an out-of-vocabulary predicate,
    an exclusion set over item 8's 10-country cap, a date-of-birth-range boundary that is not the site's shared locked age or two different such boundaries in one request, D109(i)3; **this off-lock refusal does NOT apply to a hostname named in `multi_threshold_verifiers`, D109(j)2, item 9 rule 6; all the other refusal causes still do**). Never a partial answer, never an optional or
    droppable predicate, never a per-predicate opt-out control. A holder's declining pattern is
    itself potentially holder-stable, cross-site information (D92's own reasoning), so the only
    signal a site may ever receive is "refused" (generic, no reason, D109(g)5) or "answered
    everything asked". "Cannot tell" stays distinct from a real `false` (§6.9.1 criterion 1).
    The per-origin locked-set and consent-list match that D92 originally described are gone with
    items 10-12 (deferred) and the old item 7. Touches: **scanner** (the gate, upstream of mint).
    Tag: ENHANCEMENT. Implements: D92 (amended by D109(h)). **D109 re-check:** the item never
    depended on membership statements. A request reaches it only after items 1 and 2 have admitted
    the requesting origin; D92's own remark that "the operator is the one who decides who is on the
    list" refers to D20's gate, which is now item 2's pin list.
14. **`chiproof` changes — the SITE's own verifier library, not the holder's app. Adjusted by D109(d).**
    Correction of the prior draft's mistake, kept: `chiproof` (`packages/chiproof`) is the VERIFIER
    SDK that runs on a requesting SITE's own server (e.g. `apps/demo`'s `server.mjs`) — it is never
    part of the holder's Android scanner, which has no `chiproof` dependency at all. `chiproof`
    gains: (a) tier-C challenge issuance (`issueChallenge({ tier: 'C', predicates: [...] })`,
    mirroring today's tier-A/B shape; tier-C challenges are signed, D20, which the M1 core already
    enforces) and tier-C presentation verification — validating an incoming presentation's
    predicate-name/boolean pairs against item 8's fixed vocabulary and item 9's fixed boundary
    lists (no per-presentation cap in this build, D109(h)2), returning `ok:false` for an off-list boundary or an
    out-of-vocabulary predicate, mirroring the existing never-throw verdict contract (D4). **The
    name-match predicate's request shape carries two labelled fields, `given_names` and `surname`
    (D109(k)1), never one combined string; the exact wire encoding is still decided at build.** (b)
    ~~A helper to embed an operator-issued membership statement into the site's request~~
    **DROPPED (D109)** with items 3-6: there are no statements. The letter is reserved. **`chiproof`
    has no knowledge of the pin list:** the pin list lives only in the holder's app build (item 2);
    a site that is not on a given build's list is simply refused by that build, and the site
    learns nothing. Site-side enforcement of the same vocabulary and boundary lists the scanner enforces
    holder-side is the point of (a): a dishonest scanner build must not be the only place these
    caps are checked. **D110 (2026-10-26):** the app now sends the site ONE generic refusal
    (OpenID4VP `error=access_denied`, plus `state`) for a refused tier-C request that passed the
    app's own gates (item 2's pin and the operator switch, and a verified JWS); `chiproof` therefore
    also verifies that response as a distinct, explicit "refused" verdict (`ok:true, allowed:false`,
    never a pass, never a throw; any other `error` value is `ok:false, allowed:null`), carrying no
    reason. Touches: **chiproof** (`packages/chiproof/src`, new tier-C validation module).
    Tag: ENHANCEMENT. Implements: D19, D20, D90, D95, D97, D110.
15. **Demo page extension over M3 (§6 table's M3b row). Adjusted by D109(d).** `apps/demo` gains a
    third action — "prove specific attributes" (tier C) — alongside M3's existing tier-A/B actions,
    gated to a demo-side `operator.json`-equivalent (or the existing env-var config, per D84 point
    4's deferral of the demo's own config layer — this item does NOT reopen that deferral, it only
    extends the demo's tier-A/B page pattern to tier C using whatever config mechanism is live when
    this item is built). **D109:** the demo no longer issues itself a membership statement (item 3
    is DROPPED). Instead the demo is simply a hostname (`127.0.0.1`, the D76 dev origin) on the
    `tier_c_verifiers` pin list of the scanner build used for the demo, and signs its requests
    with the existing dev request-signing key (loopback-only, D109(g)8: the tier-C demo stays
    `http://127.0.0.1:8787` with the dev key; the real-TLS well-known key fetch stays untested and
    disclosed, and no https tier-C path is claimed). The page
    MUST display, per predicate asked: the predicate name and the boolean answer. It MUST NOT
    display any PII or raw field value at any point (standing rule, item 16 below). **Resolved
    (D109(g)5):** the pre-D109 draft also asked the page to show the app's per-origin lock state and
    to show "refused — over the 3-predicate cap" or "refused — outside your consented list" as
    distinct outcomes (both mechanisms are now deferred or replaced, D109(h); the rule stands for
    any refusal reason, for example the holder setting being off). D92 says a site only ever learns "refused" or "answered everything", so the
    demo's verifier cannot know either. The page therefore shows ONE generic refusal outcome
    (distinct from a successful disclosure, mirroring M3 item 4's "already registered" pattern);
    the reasons and the lock state are shown in the app only. D92 is UNCHANGED. **D110 (2026-10-26):** the generic
    "refused" outcome is now something the site really receives (the app posts `access_denied`,
    item 14), so the page can tell "refused" from "no answer" (a request that expired with nothing
    posted, for example a holder who walked away); the three outcomes on the page are answered,
    refused, and no answer. The page keeps the
    demo's own server-side per-origin state only. The demo's verifier and its logs MUST record predicate names and verdicts only,
    never the site-supplied name or number being tested. **Name match on the demo page (D109(k)):
    the demo's input is two labelled fields, given names and surname, so the page never asks for or
    implies a name order.** Touches: **demo** (`apps/demo/server.mjs`
    and its page), **chiproof** (item 14's tier-C shape). Tag: ENHANCEMENT. Implements: the §6
    table's M3b deliverable line ("demo page extension over M3").
16. **Standing rules carried forward, unchanged, restated because they govern new UI/paths.** (a)
    No screen renders a raw document field, DG1/MRZ value, or the document number's actual value at
    any tier, on any screen (§6.2 item 5, unchanged; tier-C's document-number predicate (item 8e)
    is boolean-only for exactly this reason). (b) Every tier-C result write MUST route through the
    scanner's single report-writer discipline (`emitReport`, §6.2 item 16) — no second write site.
    (c) A non-2xx `direct_post` response MUST map to an explicit user-facing refusal and MUST NEVER
    be read as PASS/success (the M3/S2-S3 finding #22 lesson, D79(b), carried forward explicitly
    since tier-C adds new response-handling branches). (d) A blocking modal dialog is used only for
    an outcome requiring user action (§6.2 item 15, D43); a transient/no-state-change fact uses a
    Snackbar; the two are never blended for a tier-C outcome. Touches: **scanner**, **demo**. Tag:
    carried standing rule, not new scope. Implements: §6.2 items 5/15/16; D43, D79(b); finding #22.
17. **Disclosed limitations (MUST be stated in customer-guide.md and/or in-app, not silently
    carried). Adjusted by D109(d).** (a) **Per-install reset** — the holder setting (item 7) is
    per-install state and returns to OFF on an uninstall/reinstall, the same disclosed-not-mitigated
    shape as D74. (The per-origin records of items 10-11, whose only reset was a reinstall per
    D107(c), are deferred with them.) (b) **Multi-origin not covered** — a single owner
    operating many origins to route around any per-origin lock is not covered by any item above,
    same disclosed shape. **With open age verification (item 23, D109(i)1) this is more exposed: no list limits which hostnames may ask, so an operator running many hostnames can ask a different age on each and combine the answers; the lock is per hostname. Disclosed, not mitigated.** (c) ~~**Revocation window**~~ **DROPPED (D109).** The window was the
    lifetime of a membership statement; statements no longer exist (items 3-6 DROPPED). Its
    successor is (h) below. Letter (c) is reserved. (d) **MRZ name-match truncation** — a
    sufficiently long, correctly-typed name can fail item 8(b)'s match against a document whose MRZ
    field truncated it; flagged as the orchestrator's own recommendation from D101, NOT an owner
    ruling, and stays a stated, disclosed limitation rather than a "MUST fix." **Narrowed by D109(k)
    (open note, not a ruling):** with two fields and the strict full-given-names match, a
    truncated given-names field cannot match the full names a site types, so the answer is `false`;
    the guide MUST say a very long name may read as "no match" for this reason. (e) **Q11
    residual** — even against item 9's fixed boundary lists, DOB-range predicates still yield
    bits of information about a holder's date of birth; since the shared age lock (item 9, D109(i)3) each site gets one boundary, so one bit at its single locked age (across many hostnames see (b)); nothing resolves Q11 itself. (f) **Document-number linkage residual (D90 item 5)** — a `true`
    document-number-match answer confirms to the asking site that this holder is the person behind
    a number the site already held; bounded to a number the site already knew going in, not a new
    value extracted from the device, but a real linkage nonetheless. (g) **One phone per person
    (D107(e)) — DEFERRED with item 11 (D109(h)); no shared ceiling exists in this build, so the
    assumption has no effect today.** It returns if item 11 is ever built. **(h) NEW (D109): the pin list changes only by
    rebuilding the app.** A site added to or dropped from item 2's list reaches a holder only when
    that holder installs the new build. Distribution is a signed APK on GitHub Releases plus
    sideload (D83), with no auto-update, so an older install keeps its old list for as long as the
    holder keeps it. This replaces the old bounded revocation window (at most 6 months) with an
    unbounded one for any holder who never updates; there is no per-site expiry and no remote
    revocation. Disclosed, not mitigated. **(i) NEW (D109): a pin is only as strong as what it
    pins.** Item 2 pins exact hostnames only (D109(g)1-2), so the gate trusts whoever controls that
    hostname's DNS and TLS, because the request-signing key is fetched from the origin at request
    time (`RequestTrust`); a hijacked or lapsed hostname passes the gate. A key pin would have closed
    that but was considered and rejected (D109(g)1); the DNS/TLS trust limitation is kept as
    ruled (D109(g)2). The guide MUST say the pin is by hostname only. **(j) NEW (D109): there is no authority behind the list.** Nothing vouches that a
    pinned site is legitimate beyond the operator having put it there; the operator-side process
    for vetting and adding third-party sites is out of scope (owner: skipped), so the list is a
    hand-edited `operator.json` field. **(k) NEW (D109(h)8), REWRITTEN (D109(i)3): no caps, so a pinned site can still
    narrow some facts down over time, but NOT age.** The shared age lock (item 9) allows one age
    boundary per site in total across tiers, so a site can no longer ask 18, then 21, then 60 to place
    the holder in an age bracket: age bracketing is PREVENTED (per hostname only, see (b)), **with
    one exception (D109(j)2): a site the operator names in `multi_threshold_verifiers` is exempt from
    the lock and CAN ask several ages, for age verification and for the tier-C date-of-birth-range
    predicate, so an operator-named multi-age site can narrow the holder's age down. That is the
    operator's explicit grant; the reference build's list is empty.** What
    remains, with no per-presentation cap, no per-origin predicate-set lock and no shared ceiling: a
    pinned site can repeat tier-C requests across scans and confirm guesses of a name, a document
    number or a nationality again and again; and it can combine the expiry buckets (3, 6 and 12
    calendar months are three different boundaries and are not locked) to bracket the holder's
    document expiry date, so that is not prevented. Limited to pinned sites (an unlisted site is
    refused before any read) and bounded by the holder switch (item 7), which the holder can turn
    off; caps and per-site question permissions are deferred (`backlog.md`). Disclosed, not
    mitigated. The guide MUST say so plainly, including the multi-age exception. **(l) NEW (D109(i)): real-TLS key discovery for an
    arbitrary site is untested.** With open age verification the scanner resolves a site's
    request-signing key from its origin at request time, for any site. That path has only ever been
    exercised against the loopback dev key (`http://127.0.0.1:8787`, D76, D109(g)8); fetching a
    well-known key over real TLS from an arbitrary site has never been run. Until item 23's test
    point has run, the guide says the open form is untested against real HTTPS sites. Disclosed.
    **(m) NEW (D109(i)): with open age verification any site can ask an age check.** The
    protections are the per-site threshold lock (item 9), origin binding (the request is a signed JAR
    bound to its origin, D34/D37) and the holder's per-scan biometric confirmation, which shows the
    requesting site and the question (item 7). No operator list limits which sites may ask; an
    operator who wants one uses the exact-list form of `verifiers`. Tier C is NOT open: it keeps its
    pin list, the operator switch and the holder setting. The guide MUST say so. Touches: **docs** (customer-guide.md tier-C section, once
    written), **scanner** (in-app disclosure text, Settings tab, Log tab). Tag: documentation
    MUST, not code. Implements: D74 (disclosed-limit convention), D101 (truncation), Q11 (residual), D90 item 5 (document-number residual), D109(d) (h)-(j), D109(h)8 (k), D109(i) (b), (e), (k), (l), (m).
18. **Link-once: handle each incoming link only once (D109(e), FIX).** Today
    `MainActivity.onCreate` calls `handleIncomingIntent(intent)` unconditionally (about line 541),
    with no consume-once guard, and `onNewIntent` does the same after `setIntent`. So an Activity
    recreation (a locale or display-size change, or a process-death restore) replays the intent:
    a malformed link re-fires the D87 "That link looks broken" Snackbar, and a valid `av://` link
    re-runs `beginHandoffVerification`. The scanner MUST handle each distinct incoming link at most
    once per task. **A bare `savedInstanceState == null` guard is WRONG:** process-death restore
    must still re-read the intent to resume, and `onSaveInstanceState` does not save the handoff
    (it saves the report, log, generation counters and tab only), so a null-bundle guard would
    drop a legitimate resume. Proposed shape (the implementer may choose, the contract is the
    pass criteria below): decide in a pure class (name TBD, sibling to `AvLinkRules`) whether an
    intent has already been consumed, by clearing its data after handling or by saving a handled
    flag keyed to the `request_uri`; `MainActivity` stays thin because `android.net.Uri` and
    `Intent` are non-functional under this module's JVM unit-test stubs (D87's own note). Pass
    criteria: (1) a unit truth table for the pure decision (first sight handled; same key again
    ignored; a different link handled; absent key handled once); with a negative control that
    turns the table red when the guard is removed; (2) device: a truncated link plus a forced
    recreation (`adb shell wm density <n>`, then reset) shows the Snackbar once, not twice; (3)
    device: a valid link mid-flow, then a forced recreation, does not start a second
    verification; (4) device: a process-death restore (kill the process while a handoff is in
    flight, per the existing recreation-test method) still resumes the flow. Recorded as a `change`
    in the fix ledger. Touches: **scanner** (`MainActivity` intent handling, one new pure class and
    test). Tag: FIX. Implements: D87 (guard completeness).
19. **Operator-config negative cases for comma/quote/backslash in `multi_threshold_verifiers`
    (D109(e), ENHANCEMENT, test only).** Add 1-3 cases to `apps/scanner/scripts/operator-json-negative.sh`
    that put a comma, a double quote and a backslash in a `multi_threshold_verifiers` entry (the
    `isBareHostname` call at `build.gradle.kts` about line 217), each expecting the build to fail
    naming the rule. D86's cases 13-15 cover the same three characters on `verifiers` only. **The
    expected rule is `rule 5`, not rule 4:** that is what the code emits for this list (lines
    217-219) and what the existing case 9 expects (CONFIRMED, D109(g)9). Pass criteria: each case exits non-zero
    AND the build output contains `rule 5`; the good reference file is restored afterwards (the
    script's existing `trap`); a negative control confirms a case would FAIL if `isBareHostname`
    stopped rejecting that character. Recorded in the ledger as an `idea`. Touches: **scanner**
    (script only). Tag: ENHANCEMENT.
20. **README full rewrite (D109(e), ENHANCEMENT, documentation).** The owner approved a draft; it
    is provided at build time, so this item only names it. Constraints that apply to whatever
    arrives: it MUST describe tier C as built only once tier C is built (until then the README
    keeps "designed and parked" wording and the D109 revival is stated as in-build, not as done);
    MUST NOT describe v1 or tier C as zero-knowledge; MUST keep the statement that
    `draft-hamr-oauth-agent-delegation` is an individual submission with no IETF endorsement; MUST
    NOT bring the rung-2 agent layer into scope. README line 25 (the stale version line) is
    superseded by this rewrite, but item 21 fixes it first if the rewrite lands later. Touches:
    **docs** (`README.md`). Tag: ENHANCEMENT.
21. **Version-line fixes (D109(e), FIX, documentation).** v0.8.0 shipped 2026-10-24 (chiproof@0.8.0
    on npm, signed APK on GitHub Releases). Correct, in place: `docs/product/reuse-map.md` — both
    rows that say `chiproof@0.7.1` (the verdict-core row and the npm-publish-workflow row, lines 19
    and 30), and the "`package.json` description still says no hardware evidence backs it (stale)"
    caveat in the verdict-core row (that description was fixed by `f9dadce`; verify the live
    description before removing the caveat); `docs/product/zkagent-prd.md` — the status block
    (lines 9-17: "current release v0.7.1 ... the next release is 0.8.0"), and the same stale
    version in lines 68, 174, 175 and 179; `README.md` line 25; plus the sibling lines found by
    search: `docs/wiki/milestones.md` line 29 ("rung 1 has shipped (v0.7.1)") and
    `apps/scanner/README.md` line 4. **Scope CONFIRMED by the owner (D109(g)10)**, including all of
    these extra lines. Historical
    records (`history.md`, stashes, findings, CHANGELOG) are NOT edited. Pass criterion: a search
    of the live docs for `0.7.1` returns only historical rows. Touches: **docs**. Tag: FIX.
22. **Status flip on owner approval (D109, a STEP). DONE 2026-10-25 (D109(h)).** On the owner's
    approval of this section as amended (D109(h)), one docs pass flipped these from PARKED to
    IN BUILD, each with the approval date and D109(h): (1) the §6 milestone table M3b row; (2) the
    §6.9 banner; (3) `backlog.md` item 1 (marked revived, no longer parked); (4) the PRD status
    block and the §6.4 status paragraph; (5) the customer guide's tier-C wording ("approved, in
    build, not yet built"). **Left for build time:** the README (item 20) and the version lines
    (item 21). Tier C is never described as built before it is. Touches: **docs**. Tag:
    ENHANCEMENT (procedure).
23. **Open age verification (D109(i)1, ENHANCEMENT).** Today `operator.json` `verifiers` is a
    REQUIRED non-empty array of exact bare hostnames that gates tiers A and B (D84; §6.7.3; rule 4).
    This item gives it a second, mutually exclusive form. **Shape chosen: `"verifiers": "any"`, the
    lower-case string `any`, as an alternative to the array.** `verifiers` is therefore EITHER (a) the
    existing non-empty array of exact bare hostnames, unchanged and still valid for an operator who
    wants a closed build, OR (b) the string `"any"`: the scanner answers age verification (tiers A and
    B, over the operator's own `thresholds` subset) for a request from any hostname. A string was
    chosen over a wildcard entry because the shape rule bans wildcards (rule 4; D84, D86), and over an
    array entry because `any` is a legal hostname label: **an array that contains the entry `"any"`
    fails the build (new rule 12)** so nobody reads `["any"]` as the open form. Its encoding in
    `BuildConfig` is settled in code; it MUST NOT be representable as a hostname-list entry (D86's
    comma-split hazard, `OperatorPolicy.parseHostnameList`). **New validation rule, §6.7.4 rule 12
    (the next free number; rules 1-11 are not renumbered):** `verifiers` is neither a non-empty array
    of bare hostnames nor exactly the string `"any"` (for example `"*"`, `"ANY"`, `"any "`, the empty
    string, a number, an object, `null`), or the array form contains the entry `"any"`. Array shape
    violations (empty array, wildcard, scheme, port) stay rule 4.
    **What does NOT change.** (1) D74's per-hostname threshold lock: the first-seen threshold is
    locked per hostname, a different threshold is refused, the same threshold asked again gets the
    same bit. This is the anti-bracketing guarantee and "reuse existing replies to the same website"
    in the owner's words; no reply cache is invented. (2) Tier C stays gated by `tier_c_verifiers`
    (item 2), the operator switch (item 1) and the holder setting (item 7); `verifiers` never gates
    tier C and `"any"` never widens the pin list. (3) The request checks (ES256 JAR bound to its
    origin, D34/D37) and the per-scan confirmation. "Open" means not allowlisted, not
    unauthenticated. (4) `multi_threshold_verifiers` stays an independent exact-hostname list.
    **Reference build.** `apps/scanner/operator.json` changes from `"verifiers": ["127.0.0.1"]` to
    `"verifiers": "any"` (its `thresholds` are already all six); `tier_c_verifiers` stays `[]` and
    the tier-C switch stays off (item 1), so the showcase answers an age check for any site and
    refuses all of tier C. A device-test build turns tier C on separately.
    **Docs.** `customer-guide.md` (the operator.json knobs table, "Verifier hostnames", "What a
    refused site's users see", §5, and the tier-C limits paragraph) is updated now, marked designed
    and not built, and flips to built when this item lands. The root `README.md` wording rides on
    item 20 at build time and is not edited now; `apps/scanner/README.md`'s operator.json lines are
    updated when this is built.
    **Tests (pass criteria).** (a) Unit: a truth table on the existing pure allowlist decision
    (`"any"`: unlisted, listed and never-seen hostnames all admitted; array form: listed admitted,
    unlisted refused loudly with no mint; `multi_threshold_verifiers` orthogonal), with a negative
    control that turns it red when the `"any"` branch is removed or the array branch stops
    checking the list (and a count above 0 on the refusals). (b) Negative script: add cases to
    `apps/scanner/scripts/operator-json-negative.sh`, each exiting non-zero AND naming `rule 12`:
    `"*"`, `"ANY"`, `""`, `5`, and the array `["any"]`; plus a positive case that `"any"` builds
    clean, and the script's existing `trap` restores the reference file. A negative control confirms
    the cases fail if rule 12 is removed. Invalid array forms still fail with their old rules (a
    wildcard array entry stays `rule 4`). (c) Device, with a build that has `"verifiers": "any"`,
    tier C on, `tier_c_verifiers: ["127.0.0.1"]` and the holder setting ON: a tier-A request from
    `localhost` (NOT listed anywhere) is ANSWERED end to end; a tier-C request from `localhost` is
    REFUSED in-app before any read or mint and the verifier sees nothing; a tier-C request from
    `127.0.0.1` is answered. Paired non-vacuity pass: with the array form (`["127.0.0.1"]`) the same
    `localhost` tier-A request is refused, so the open answer is shown to be keyed on `"any"`. See
    §6.9.1 criterion 3 for how this interacts with the pin-gate criterion. (d) **Test point
    (pre-registered; the riskiest NEW assumption of this item): real-TLS key discovery for an
    arbitrary site.** Open age verification only helps real sites if the scanner can resolve a
    request-signing key from an arbitrary HTTPS origin; that has only ever run against the loopback
    dev key (17(l)). Judged riskier than anything else this item adds (the form, the rule and the
    reference switch are small and unit-proven), so it is run FIRST in this item's build. It is a
    test point of this item, NOT the §6.9.1 opening POC, which stays aimed at the predicates read off
    a real chip (the riskiest assumption of M3b as a whole). **Host (D109(j)1, owner: "i got two vps
    that we can reuse"): one of the owner's own VPSs, not a tunnel; owner-run, test-only, torn down
    or left unused afterwards; NOT a project-hosted verifier and never referenced by the app or the
    docs as a default, so NO-GO #3 holds. The owner supplies the hostname at test time; no hostname
    or IP is written in the docs, and the evidence doc names it generically ("owner VPS, CA-valid
    cert").** Pass: from that test-only HTTPS host with a CA-valid certificate (not a project-run
    hosted verifier, which would reintroduce NO-GO #3 and stays rejected, D76), a signed tier-A or B request is accepted and answered end to end on the
    release network configuration (system CAs, zero cleartext exceptions); paired negatives on the
    same run: an invalid-certificate host is refused, and a host whose served key does not match the
    request's signature is refused. Record verdicts and timings only. If it fails, STOP and report a
    finding to the owner; do not paper over it. If the owner's VPS turns out to be unavailable at
    test time, the item ships with 17(l) as written and the gap is reported. Touches: **operator.json** (schema; reference file),
    **scanner** (`OperatorPolicy`, `build.gradle.kts` validation, `BuildConfig` generation), the
    negative script, **docs**. Tag: ENHANCEMENT. Implements: D109(i)1; amends D84 point 1; leaves
    D74 unchanged.

### 6.9.3 Traceability — decision → item

| Decision | Item(s) |
|---|---|
| D89 | 7 (SIMPLIFIED by D109(h)3: one holder setting replaces the consent list) |
| D90 | 8, 14 |
| D91 | 10 (DEFERRED by D109(h)); 14 no longer enforces a cap |
| D92 | 13 (simplified by D109(h)3; and item 15, via D109(g)5) |
| D93 | 11 (DEFERRED by D109(h)) |
| D94 | 2 (AMENDED by D109(b), (g)1-2: pin list by exact hostname only) |
| D95 | 9, 14 |
| D96 | (1) switch: 1. (2) statements: SUPERSEDED by D109(b), no item. 14 (a) only |
| D97 | 9, 14 |
| D98 | SUPERSEDED (D109(b)); former items 5/17(c) DROPPED |
| D99 | SUPERSEDED (D109(b)); former item 4 DROPPED |
| D100 | SUPERSEDED (D109(b)); former items 4/5 DROPPED |
| D101 | 8, 17(d), §6.9.1 (name order and fields refined by D109(k)) |
| D102 | 8 |
| D103 | SUPERSEDED (D109(b)); former items 5/6 DROPPED |
| D104 | SUPERSEDED (D109(b)); former item 6 DROPPED |
| D105 | SUPERSEDED (D109(b)); former item 5 DROPPED |
| D106 | SUPERSEDED (D109(b)); former item 5 DROPPED |
| D107(a) | 7 (REPLACED by D109(h)3: the per-question checkboxes are not built; the default-off principle carries to the single switch) |
| D107(b) | SUPERSEDED (D109(b)); former item 3 DROPPED; item 15 no longer a consumer |
| D107(c) | 12 (DEFERRED by D109(h)) |
| D107(d) | 10, 11 (DEFERRED by D109(h)) |
| D107(e) | 11, 17(g) (DEFERRED by D109(h)) |
| D109(a) | the whole section (revives M3b; banner flip is item 22, done by D109(h)) |
| D109(b) | 1, 2, and the DROPPED items 3-6 |
| D109(c) | 8, 9 |
| D109(d) | 7, 9-17 as adjusted (7, 10-13 further changed by D109(h)); 17(c) DROPPED, 17(h), 17(i) new |
| D109(e) | 18, 19, 20, 21, 22 |
| D109(f) | §6.9.1 |
| D109(k) | 8(b) (two fields, full given-names match, hyphen-as-separator, ICAO 9303 Part 3 transliteration); 14 (request shape); 15 (two input fields); 17(d) (narrowed); §6.9.1; §6.9.4; customer-guide §4 |
| D109(h) | 1, 2 (gate unchanged; all five predicates to every pinned site); 7 (single switch); 8; 10-12 (DEFERRED, numbers reserved); 13 (simplified); 14; 15; 17(a), (e), (g), (k) NEW; §6.9.1 criterion 4; 22 (status flip, done); `backlog.md` items 8 and 9 |
| D109(i)1 | 23 (open age verification, `"verifiers": "any"`); §6.7.3, §6.7.4 rule 12; §6.9.1 criterion 3 (reworded, tier C only); 17(b), 17(l), 17(m) |
| D109(i)2 | 1, 2, 7 (restates D109(h)1 and (h)3; consistent, nothing new); `backlog.md` item 10 (registration and signing for allowlisted sites, later) |
| D109(i)3 | 8(c), 9 (shared age lock), 13 (off-lock refusal); 17(b), (e), (k) REWRITTEN; customer-guide §4 and §5 |
| D109(j)1 | 23(d) (real-TLS test point on an owner VPS; no hostname recorded); 17(l) |
| D109(j)2 | 9 (rule 6 exemption, exemption row and non-vacuity pair), 13 (off-lock refusal does not apply to named sites), 17(k); customer-guide §4 and §5 |
| D74 (per-hostname threshold lock) | 9 (now shared with tier C, D109(i)3); 23 (UNCHANGED; the anti-bracketing guarantee for open age verification) |
| D84 (`verifiers` required allowlist) | 23 (AMENDED by D109(i)1: second form `"any"`; hostname-only array form unchanged) |
| D109(g)1 | 2 (hostname-only pin; key pin rejected), §6.9.1 criterion 3, §6.7.3 |
| D109(g)2 | 2, 17(i), D20/D94 identity wording (accepted departure) |
| D109(g)3 | 8(d), §6.9.1 |
| D109(g)4 | 9, §6.9.1 |
| D109(g)5 | 15 (D92 unchanged) |
| D109(g)6 | §6.9.1 criterion 2, 17(d) |
| D109(g)7 | 1, 2, §6.7.4 rules 10-11 |
| D109(g)8 | 15 |
| D109(g)9 | 19 |
| D109(g)10 | 21 |
| §6 table M3b deliverable line | 15 |
| §6.2 item 5/15/16, D43, D79(b), finding #22 | 16 |
| D74/D91/D93 disclosed-limit convention | 17(a), 17(b) (D91/D93 deferred, D109(h)) |
| Q11 (residual, still open) | 17(e), 17(k) |
| D9 (document-number as zktag derivation field) | 8(e), 17(f) |
| D87 (malformed `av://` guard) | 18 (link-once is its sibling guard) |
| D84/D86 (`operator.json` validation, comma/quote/backslash) | 2, 19 |
| D72 (lockstep versioning) | 21 |

Every ruled decision D89-D107 still lands in at least one row above, either as an item or as an
explicit SUPERSEDED entry; none is orphaned.

### 6.9.4 OPEN — not ruled, not invented here

**All ten owner questions raised by D109 were answered "all recommended" on 2026-10-25 and are
recorded as D109(g)1-10 (decisions.md).** The numbering below is the old local numbering, kept so
earlier references still resolve. Nothing in this list is a pending owner question.

Resolved by D109(b) (kept so a reader of the old list is not confused): the **membership
statement wire format** and **the location of item 3's signing tool** are no longer OPEN, because
items 3-6 are DROPPED. The **`chiproof` evidence-plug-versus-module question for the statement
helper** (item 14(b)) is likewise gone with it.

**D109(h) (2026-10-25, owner approval with amendments) answered the plain-language summary's
seven points and approved the scope; they are recorded in decisions.md D109(h) and applied in
items 2, 7, 10-13, 17 and §6.9.1. Nothing from them is a pending owner question.** Two items
the owner sent to "later" or to the backlog are NOT ruled and NOT built: per-site question
permissions and limits (`backlog.md` item 8) and the request-signing key-thumbprint pin
(`backlog.md` item 9, a PROPOSAL / open question).

**D109(i) (2026-10-25, owner: open age verification, the gate for everything else, the shared age
lock) is recorded in decisions.md D109(i) and applied in items 8, 9, 13, 17 and the new item 23.
The two points it left were answered the same day and are recorded as D109(j) (decisions.md):
(1) the real-TLS test host is one of the owner's own VPSs (item 23(d); no hostname recorded in the
docs); (2) `multi_threshold_verifiers` sites are exempt from the shared age lock for tier-C
date-of-birth questions too (items 9, 13, 17(k)). Neither is OPEN any more. Two small defaults
that D109(j) did not rule are marked in decisions.md as "orchestrator default, not owner-ruled"
(a refused tier-C request does not set the age lock; two different DOB boundaries in one request
are refused whole except for named multi-age sites); they are not pending questions.**

Resolved by D109(g), 2026-10-25 ("all recommended"):

1. **Pin type** — RESOLVED, D109(g)1: exact hostnames only, in `tier_c_verifiers`; no key pin
   (option K rejected, kept as a noted rejected alternative in item 2).
2. **Departure from D20/D94's "the key is the identity" wording** — RESOLVED, D109(g)2: accepted;
   identity is the exact hostname, as D37 does for tiers A/B; limitation 17(i) kept.
3. **Nationality code namespace** — RESOLVED, D109(g)3: ISO 3166 alpha-3 as written in the MRZ;
   ICAO special codes are distinct values, never mapped.
4. **Expiry-bucket arithmetic** — RESOLVED, D109(g)4: calendar months, end-of-month clamping.
5. **Item 15 versus D92** — RESOLVED, D109(g)5: one generic refusal on the demo page; reasons in
   the app only; D92 unchanged.
6. **A third document for ß/Ø and truncation** — RESOLVED, D109(g)6: "not observable on these two
   documents" accepted; synthetic table, labelled synthetic; no third document.
7. **Consistency rules between items 1 and 2** — RESOLVED, D109(g)7: confirmed as build failures,
   new §6.7.4 rules 10 and 11.
8. **Loopback-only tier-C demo** — RESOLVED, D109(g)8: stays `http://127.0.0.1:8787` with the dev
   key; the real-TLS key fetch stays untested and disclosed.
9. **Item 19's expected rule number** — RESOLVED, D109(g)9: `rule 5`.
10. **Item 21's version-line scope** — RESOLVED, D109(g)10: includes the extra 0.7.1 lines. The
    item 20 README draft arriving at build time is as already stated in item 20.

Standing open points, NOT owner questions and unchanged by D109 (no ruling is pending; each is
handled by a disclosed limitation or by the POC reporting what it observes):

- **Name order** — RULED, D109(k) (two labelled fields, `given_names` and `surname`; no order
  exists). No longer OPEN. Finding #26 item 1 closed.
- **Normalisation beyond the D109(k)4 transliteration (narrowed)** — D109(k)4 (orchestrator
  default, announced to owner, not objected) adopts ICAO 9303 Part 3 transliteration: ß to SS,
  Ø to OE, Æ to AE, apostrophe dropped. Still OPEN: the full ICAO table beyond those four
  ("verify at build"), including whether Ø is written O or OE and how the German umlauts are
  written (D101 strips them to the base letter; ICAO allows a two-letter form), and the
  synthetic table's coverage.
- **MRZ name-match truncation behaviour (narrowed)** — D101 left it unruled (orchestrator
  recommendation only); item 17(d) states it as disclosed, not fixed. Open note from D109(k)5, not
  a ruling: with strict full-given-names matching a truncated given-names field reads `false`.
  Whether a truncated field should instead match as a prefix is undecided. D109(g)6 settles only
  the evidence handling, not the behaviour.
- **First-given-name-only mode** — not built; a possible later owner change (D109(k)2).
- **Q11's residual** (DOB-range predicates are still bits about a date of birth, even from a
  fixed list, and with no caps in this build a pinned site can accumulate them across requests) —
  no item closes it; items 17(e) and 17(k) state it as a disclosed limitation.
- **`apps/demo`'s own operator-config mechanism for tier C** — D84 point 4 deferred the demo's
  verifier-side config; item 15 does not reopen that deferral. The demo is a hostname on the
  build's pin list (item 2), so the only config that matters for it is the scanner build's
  `operator.json`.

### 6.9.5 Branch loose end (NOT an M3b item — pre-merge item for `feat/s68-rulings`) — CLOSED 2026-10-24

Finding #23 / D87's malformed-`av://` guard (the `Log.w` + non-blocking Snackbar refusal for a
malformed VIEW intent) was recorded as FIXED on 2026-09-07 with "code + unit tests"; **that claim was
false until commit `b3a03f0`** (the guard's own commit, `90ba7f3`, added no tests, and no device
check had been run). Closed 2026-10-24 (D108 step 2) with three proofs, recorded in
`.claude/remember/findings.md` #23:

1. **Unit.** The accept/reject rules are the pure `AvLinkRules.decide`; `AvLinkRulesTest` is an 11-test
   truth table (scanner suite 495 tests, 0 failures). A negative control turned 7 of the 11 red; the
   4 that stayed green are accept-path and lazy-read tests. The tests cover the rules only, not
   `android.net.Uri` parsing.
2. **Wiring trace.** VIEW intent → `onCreate` (`MainActivity.kt:541`) or `onNewIntent` (1027/1030) →
   `handleIncomingIntent` (1033) → `HandoffClient.parseAvLink` (`HandoffClient.kt:43`) →
   `AvLinkRules.decide` → null → `Log.w` (1111) + Snackbar (1112).
3. **Device (owner's Pixel 6a, 2026-10-24, debug build of the branch, `com.zkagent.scanner`).**
   Link `av://authorize?client_id=site.example&` fired by `adb am start` with the package specified.
   Run 1 (21:47) is invalid as a visual check (logs fired, but the screen was off/locked, last wake
   about 105 s after the intent). Run 2 (21:51, awake): logs for cold and warm cases, but the owner saw
   the notice only once, an unexplained single observation (no evidence of a defect). Run 3 (21:53,
   awake): the owner saw the notice in both the cold and the warm case. A wrong-host link never reaches the
   app (the manifest filter only accepts host `authorize`), so `WRONG_HOST` is defence in depth,
   unit-tested only.

This sat on the current branch's own history and was never an M3b task.

### 6.9.6 Size estimate

**Re-estimated 2026-10-25 for the D109 scope, then for D109(h), then for D109(i).** **16 numbered units**: the
§6.9.1 POC plus 15 live scope items (items 1, 2, 7, 8, 9, 13-17, the new items 18-21, and the new item 23, open age verification, D109(i)). Items
3, 4, 5 and 6 are DROPPED and items 10, 11 and 12 are DEFERRED (D109(h)), all with their numbers
reserved; item 22 is a procedure (the status flip, done), not build work, so none of those is
counted. The section counted 18 units before D109, 18 after D109, 15 after D109(h), and 16 after D109(i) (+1 unit: item 23).

- **Gone (D109):** item 4 (app-side membership-statement verifier), item 3 (the signing tool) and
  items 5/6 (lifetime bound and wording): the one unit whose size could not be bounded and a whole
  new standalone artifact. Item 2 shrinks to "activate the already-reserved `tier_c_verifiers`
  hostname list" (a rule-7 relaxation plus consistency rules), reusing the `verifiers` validation
  and `OperatorPolicy` parsing as they are.
- **Gone (D109(h)):** items 10/11 (per-origin lifetime predicate set, shared cross-document
  ceiling, own uncapped store) and item 12 (the read-only Log tab lock view), which were the
  largest remaining units. Item 7 shrinks from a new Settings tab of per-question checkboxes to one
  boolean and one switch, plus extending the question line for tier C. Item 13 shrinks to a
  switch check plus an all-or-refuse evaluation.
- **Still the largest:** item 8 (five predicate evaluators, all built, plus two fields the scanner
  does not read today, nationality and date of expiry, see §6.9.1); item 14 (`chiproof` tier-C
  challenge issuance and verification, a public contract the moment `chiproof` republishes, D72).
- **New, small (D109(i)):** item 23 (one schema alternative, one validation rule, five negative-script cases, one unit table, one device pair, and the real-TLS test point, which is the only part whose size cannot be bounded in advance, because it needs a test host and has never been run). The shared age lock adds a few lines to items 8, 9 and 13 and one unit table; it reuses the existing map and adds no store.
- **New, small:** item 18 (link-once guard: one pure class plus a unit test plus three device
  checks), item 19 (1-3 shell cases), item 21 (text edits). Item 20 (README rewrite) is
  documentation whose draft the owner supplies.
- **Small (unchanged or shrunk):** items 1, 7, 9, 13, 16, 17 (a knob, a switch, a fixed list, a
  gate, restatements).

Net: the unit count drops from 18 to 15 and the build surface is materially smaller than the
pre-D109 draft. No percentage is given: this is a judgement from comparing item sizes, and the
repo holds no effort data to ground a number. The POC retarget (§6.9.1) does not shrink it: it adds
the first reading of expiry and nationality from real chips.


### 6.9.7 Build order (D109(l))

Owner-approved 2026-10-25 ("agreed, update prd with the order"). Items 3-6 are dropped, 10-12 deferred, item 22 is done; every other live item is in exactly one chunk. **Rule:** each chunk is committed and device-checked before the next starts.

1. **Chunk 1, tier C in the app.** Items 1 (operator switch) and 2 (hostname pin), both partly built in the POC at `2720daa`; 7 (the real "Allow additional verifications" switch in a Settings location, replacing the POC's Diagnostics-tab placement); 8 (all five predicates, two-field name match, ICAO transliteration including the German umlaut forms per ICAO 9303 Part 3); 9 (fixed boundaries plus the shared one-age-per-site lock, with the multi-threshold exemption); 13 (all-or-nothing); 16 (standing rules); and finding #27's UX gaps (plain wording, no separate test controls in the real flow, a completion message). *Device check:* one tier-C request carrying several predicates, answered from one scan.
2. **Chunk 2, the site side.** Item 14 (chiproof verifies tier-C answers; replaces the POC-local `zkagent-poc-tierc/0` wire and `apps/demo/tierc-poc.mjs`) and item 15 (the demo page's tier-C section: two name fields, one generic refusal, loopback only). **D110 (2026-10-26) adds the app's one generic `access_denied` refusal to the site for a gated tier-C refusal** (scanner, small; see decisions.md D110); the page shows it as the one "refused" outcome. *Device check:* end to end through the demo page.
3. **Chunk 3, open age verification.** Item 23 (`verifiers: "any"`, rule 12, reference build switched), including 23(d), the real-TLS test on the owner's VPS. *Device check:* an unlisted site gets an age answer but no tier-C answer.
4. **Chunk 4, leftovers and release.** Items 18 (link-once, device check), 19 (negative cases, rule 5), 17 (disclosed limitations in the guide and in-app), 20 (README rewrite; an approved draft exists), 21 (version lines). Then /self-review, /branch-review, /release as **0.9.0** (lockstep, D72: chiproof, demo, scanner versionName 0.9.0 / versionCode 8).

**Owner supplies:** the phone and both documents at the chunk 1-3 checks; the VPS for chunk 3 (hostname given at test time, never written in docs); the `pass` unlock plus a go for merge, tag and publish at release.

**Placement notes:** item 16 (standing rules) sits in chunk 1 because it governs the new app UI and paths; item 17 sits in chunk 4 with the guide, but its in-app disclosures are drafted as the chunk 1-3 UI lands; item 19 sits in chunk 4 though it touches `operator.json` validation (rule 5) that chunk 3 also changes, so run its cases again after chunk 3.

## 7. Riskiest-assumption register (what M0 must answer)

1. **Issuer-free derivation works** — chip's stable data is readable, verifiable against a public masterlist, yields the same secret every scan. Checked 2026-08-30: issuer-free ZK proofs over passport SODs are published and shipping elsewhere; what's novel is only the combination with zkagent's disclosure/tier model. (zkagent-prd.md:1684)
2. **JMRTD + the owner's documents + the Pixel 6a cooperate**, including PACE for the NL card (PACE-only since 2022). Prior: both documents already read by commercial KYC NFC apps, so a miss points at the harness first. (zkagent-prd.md:1685)
3. **Masterlist coverage** — the owner's issuing country's CSCA cert is present and current in the free public lists. (zkagent-prd.md:1686)
4. **Attestation verification is implementable within the project's dependency rules.** M1 POC 2026-08-29: holds for parsing/chain verification, stdlib-only, zero deps. Play Integrity decode requires a Google server call per check, stdlib-only client verified 2026-08-30. (zkagent-prd.md:1687)
5. **Derivation-field choice** — document number (rotates ~10-yearly at renewal) vs. personal number where present; decided on evidence after M0 reports what the chip actually contains. (zkagent-prd.md:1688)
6. **Not issuer-free — attestation has an issuer.** Identity is issuer-free (the government already issued the document); attestation is not. The scope gate forbids zkagent running an issuer, not depending on someone else's. Correct phrasing: issuer-free identity, vendor-rooted attestation — never claim more. (zkagent-prd.md:1689-1691)
7. **Attestation choice risks contradicting the product's own audience** — Play Integrity fails permanently for de-Googled/custom-ROM devices (six rejected outright during the M0 hardware search); mitigable via hardware key attestation (needs no Play Services). Accepted for v1 by decision, tracked as an open question. (zkagent-prd.md:1692)
8. **The attestation payload may itself be the identifier mode A promises not to emit.** Confirmed by M1 POC 2026-08-29: the raw attestation chain contains a stable per-device intermediate on both StrongBox and TEE paths, plus stable verifiedBootKey/verifiedBootHash/patch levels — tier A cannot carry the raw chain. Resolved for v1: the Play Integrity token carries no device-unique field (measured). (zkagent-prd.md:1693)

(zkagent-prd.md:1684-1693)
