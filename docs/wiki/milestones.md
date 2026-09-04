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
| **M3** — Demo (tier A/B only, vanilla) | Web page: "prove you're over 18" (tier A) and "prove you're a unique adult human" (tier B); responsive (mandatory); no tier C (zkagent-prd.md:859) | Live flow. Tier B: second scan from same document rejected as duplicate zktag. Tier A: two presentations indistinguishable. Demo site is its own adopter with its own store — the SDK still stores nothing (zkagent-prd.md:859) | Scope gate CLOSED — §6.3 owner-approved 2026-09-03 (D73–D75); not started |
| **M3b** — Mode C / tier-C KYC demo | Tier-C attributed disclosure (booleans over identifying fields from a published verb list, D19), pinned-issuer gated (D20); scanner tier-C support (lifts §6.2 item 13's refusal); demo page extension over M3 | TBD — see §6.4 decision points | Not started — PRD-gated (D73); blocked on Q34 (verb vocabulary); Q11 CLOSED by D74; scanner follow-ups §6.5 (S1–S3) precede it; §6.6 Play track also precedes it |
| **M4** — Agent layer (rung 2) | Delegation certs, RFC 9421 middleware (FR8), per-serial revocation, phone→agent cert handoff (zkagent-prd.md:860) | Agent request accepted with valid chain; killed by zktag-block; single-use serial burns once; signature verifies against an off-the-shelf RFC 9421 verifier with no zkagent patches; cert reaches a headless agent host with no zkagent-run server in the path (zkagent-prd.md:860) | Frozen until rung 1 ships |
| **M5** — Blocklist/appeal (rung 2) | Signed blocklist format, adopter store interface, prove-control-of-zktag appeal (zkagent-prd.md:861) | Store pattern fails closed, never silently falls back to in-memory (zkagent-prd.md:861) | Frozen until rung 1 ships |

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
   scan 2), and a third column reading "same" or "differs" per row. Only the fresh nonce and its
   signature may ever land in the "differs" column — the page MUST say so in words next to the
   table (e.g. "only the nonce and signature differ between scans; every other field matches").
   A simple header above the table drives a two-state machine: "Scan 1 done — waiting for scan 2"
   until the second presentation arrives, then "Both scans received." (owner, 2026-09-03: "field
   table, and be clear scan 1 done, waiting scan 2 on the page simple header")
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
8. **Trust list (FR10).** The demo's verifier config MUST pin the M2 scanner's package name and
   signing-cert digest as its one accepted client identity — no other client is accepted.
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
| Opening POC (item 10) | Duplicate-zktag rejection survives a server restart, both documents; handoff completes end-to-end from the phone's own browser at `http://127.0.0.1:8787` over `adb reverse`, against the sideloaded debug build | Passed, device-confirmed 2026-09-04 — [M3-POC-EVIDENCE-2026-09-04.md](../logs/M3-POC-EVIDENCE-2026-09-04.md) |
| Tier-B duplicate rejection (item 4) | Second scan of the same document at this site shows "already registered"; chip_auth caveat disclosed | POC pass post-restart; pre-restart same-document repeat and page string not yet device-verified — [M3-POC-EVIDENCE-2026-09-04.md](../logs/M3-POC-EVIDENCE-2026-09-04.md) |
| Payload/store display (item 3) | Page displays what the app sent back (verdict/presentation payload from `chiproof`) and the store's relevant state for the transaction (e.g. zktag-already-seen yes/no) | Not run |
| Tier-A indistinguishability (item 5) | Page-visible comparison of two tier-A presentations shows no distinguishing field | Not run |
| Fixed threshold (item 6) | No threshold picker present; threshold 18 enforced | Not run |
| Hosting (item 7) | Demo reachable from the phone's own browser at `http://127.0.0.1:8787` via `adb reverse`, against the sideloaded debug build (D76) | Passed, device-confirmed 2026-09-04 — [M3-POC-EVIDENCE-2026-09-04.md](../logs/M3-POC-EVIDENCE-2026-09-04.md) |
| Trust list (item 8) | Demo verifier rejects a client identity other than the pinned scanner package+cert digest | Not run |
| No scanner changes (item 13) | Demo works against the released scanner APK unmodified | Not run |

## 6.4 M3b scope — placeholder (D73)

Not a build scope list — a list of what must be decided before any M3b item can be written into
this PRD (NO-GO #10 applies to M3b exactly as it does to M3). M3b is rung 1 (disclosure, not
delegation) — it does not touch the frozen rung-2 agent layer.

Must be decided before M3b is scoped:

1. **Verb vocabulary (Q34).** Which tier-C fields/predicates are offered — candidates named by
   the owner: name-match (true/false against a holder-supplied name), expiry-bucket booleans
   (`expiry > 3mo` / `> 6mo` / `> 1yr`), and open candidates raised but not committed:
   nationality, DOB-range. No similarity scores, ever (Q22's standing rule carries over).
2. **Per-tier limits and cumulative-disclosure cost (Q34).** How many tier-C predicates one
   presentation may carry, and whether/how repeated presentations across sites compound
   disclosure risk.
3. **Pinning UX (D20).** How an operator's issuer key actually gets pinned into a real
   deployment — D20 settles the mechanism at the protocol level
   (`trustedChallengeIssuers: [{pubkey, maxTier}]`) but not the UX of getting a key into that
   list for a live operator. Not currently covered by any open question — tracked as new **Q49**
   (questions.md).
4. **Q11 for tier C.** The binary-search date-of-birth risk (Q11) is scoped to M3/mode B today;
   it must be re-evaluated for tier C's expiry-bucket predicates before M3b is written, since
   multiple boolean buckets over one date field raise the same probing risk in a different shape.
5. **Scanner tier-C support.** §6.2 item 13 currently REFUSES any tier other than A/B outright.
   M3b requires lifting that refusal for tier C specifically — a scanner-side change, which is
   itself a §6.2-scope decision the owner has not yet made.
6. **"Preapproved list" meaning.** The owner described "user can also choose different things on
   a, b or kyc from preapproved list" — this needs to be resolved as either a HOLDER-side consent
   list (what the holder is willing to disclose, matching D19's framing that tiers are defined by
   what the holder is told) or a VERIFIER-side request list (what the site is allowed to ask
   for, matching D20's issuer pinning) — these are different UX and different trust boundaries,
   not interchangeable.

**M3b's opening riskiest-assumption POC candidate (not yet owner-approved):** a live tier-C
presentation carrying exactly one verb from item 1's eventual vocabulary, refused end-to-end when
the requesting site's issuer key is unpinned (D20's "refused, not downgraded, if unpinned" rule),
and accepted end-to-end when it is pinned — on both real documents.

## 6.5 Scanner follow-ups (post-M2, owner-approved 2026-09-03, D74) — not in M3, built before/alongside M3b

Scanner-side (`apps/scanner`) items resolved same-day as §6.3's DP4 and by separate owner UI
feedback. None of these are M3 scope (§6.3 item 13 — M3 runs against the released scanner APK
as-is); all are scope-gated (NO-GO #10) into the PRD here so they may be built ahead of or
alongside M3b without a further gate check.

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
2. **S2 (ENHANCEMENT, D74) — the question shown before the tap.** The app MUST show the exact
   question being asked — e.g. "This website asks if you are over 18" — above the Verify button,
   before the user taps it. The question text MUST be sourced from the signed request object
   (Q35's parse path — the same threshold/tier fields item 1 above locks against), never a fixed
   hardcoded string, so the shown question always matches what is actually about to be sent. A
   bare local scan with no pending verified request MUST show "Local scan (no site)" instead, per
   D46's existing wording for that case. This extends D47's disclosure requirement to the
   pre-action moment, not just the post-outcome dialog.
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
   verb rule unchanged: "Tap and verify" when a request is pending, "Tap and scan" bare); (4) the
   report/result view (`report_view`, §6.2 item 5) in a FIXED position below the button,
   independent of the handoff block's height, resolving (c) above. The old `description` text
   block (a) is gone from the pane entirely; the item 5 no-storage disclosure it carried moves to
   a persistent but non-primary location (an info/About affordance), not deleted, since D1/NO-GO
   #7's disclosure obligations don't lapse.

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

Play Store listing: see §6.6.

## 6.6 Play Store closed-testing track (owner-approved 2026-09-03, D75) — its own item, not M3/M3b; opens after §6.3 item 10's POC passes

This track remains **showcase-only** (not M3's reachability answer): M3 reaches the scanner via
`adb reverse` against a sideloaded debug build (D76), and nothing here changes that. How a
Play-installed (non-sideloaded) user would reach a verifier over HTTPS at all — the demo's plain
Node process has no public HTTPS origin, and D76 rejected zkagent hosting one itself (NO-GO #3)
— is not solved by M3 and is parked as **Q50** (questions.md), open. Owner, 2026-09-04:
"playstore will be clearer when we get to it."

1. First upload goes to a **closed testing track**, never production, using the current release
   line (v0.5.0 or later under D72 lockstep). Production is a separate, later owner decision.
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
