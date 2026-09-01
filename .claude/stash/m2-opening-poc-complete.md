# Session Stash — M2 prep + opening POC complete (2026-08-31)

Project: zkagent · Owner: hamr · Host: Fedora 44

New session, continued from `d27-d28-zkagent-npm-dropped.md` after `/clear`.
Owner rule #2 held throughout and was restated three times: the
orchestrator session never codes or edits docs itself; every `Agent` call
must pass `model: sonnet` explicitly (omitting it silently inherits Fable);
forking skills like `/code-review` run on the parent model, so they were
never invoked directly — a Sonnet `quality-assurance` agent did review work
instead.

## Summary

M2 prep and M2's opening riskiest-assumption POCs are done, with real-device
evidence from the Pixel 6a on both fronts (verifier handoff and document
scan). Sequence: hardened chiproof's adopter gate (`--ignore-scripts`,
`typescript@5`, from a cross-session warning) → PRD v1.14 (D9
`document_number`; D29 closes Q18 by accepting and stating clone replay
rather than mitigating it; frames M2 as capture-first) → `M2-CAPTURE.md`
surveying the real landscape (EU AV Blueprint = OpenID4VP/DCQL mdoc over the
Digital Credentials API with an `av://` link fallback and `direct_post`; UK
OSA age gates are IDV-vendor iframes, not credential flows; zkPassport is an
HTTPS link + WebSocket bridge, not a wallet protocol) → `spikes/m2-handoff`,
a Blueprint-shaped fake verifier + fake wallet with an ES256-signed request
object, 15/15 passing → PRD v1.15 (D30: `sig-ed25519/1` becomes the default
mode-B evidence; mdoc explainer added as zk-challenges §17) → device half
of the handoff spike run on the Pixel 6a: `av://` tap → verify →
`direct_post` → `allowed:true`; a tampered request was correctly refused;
the Digital Credentials API is live on Chrome 151 with a real consent gate
shown, and fails `NotAllowedError` with no credential provider registered
→ chiproof 0.3.0 (`PlugCtx.zktag` plus `binds.zktag`; per-tier
`evidence.require`; 125/125, unpublished) → zk-challenges §18 documenting
the ed25519 vouch → PRD v1.16 (D9×D29 interaction: mode-B uniqueness only
holds where `chip_auth` is true) → `M2-CONFORMANCE.md`, run against the
EU's real reference verifier (`av-srv-verifier-endpoint` @ `787089b`, in
Docker): our DCQL block is decorative to it, its `client_id`/`aud` model is
`PreRegistered`/x509-based rather than `redirect_uri`-based, and zkagent
cannot interoperate with EU wallets as designed — mdoc implies an issuer,
which is NO-GO #3 → `M2-EU-ZKP-SPIKE.md` against the EU's
`av-lib-android-zkp-age-icao` (@ `5f1d806`): Apache-2.0 and clean, but it
hard-compiles an RSA-3072 DSC ABI that excludes both owner documents
(RSA-2048), has no nonce, uses hour-granular `current_date`, verifies
Android-only, and ships no verification key — NO-GO as-is, with a GO-IF
list recorded → learnings doc gained a "state of EU age verification"
section (§3/§4) → code review (Sonnet `quality-assurance` agent, medium
effort) found one HIGH: the handoff spike's `sig-ed25519/1` evidence wasn't
bound to the zktag — fixed, migrated onto the single 0.3.0 instance,
17/17 → owner CONFIRMED the exact signed layout (see Key decisions) →
orchestrator ruling recorded on masterlist negative handling (integrity
failure vs. well-formed-but-excluded issuer are different verdicts; PRD's
M0 row marked superseded, the M2 row now carries the rule) → `spikes/m2-scan`
(a fork of the M0 app with MRZ persistence designed out) plus owner taps on
both real documents, written up in `M2-SCAN-EVIDENCE.md`: TEST 1 (zktag
survives uninstall/reinstall, both documents, all candidates identical),
TEST 2 (masterlist of 588 entries loads and checks in 585ms on-device;
half-load correctly returns `ok:false`; a real CSCA-removed masterlist
correctly excludes both documents), TEST 3 (mode A emits no zktag after a
mode-B scan, both documents) — all three passed on both documents → 13
learnings recorded → this stash.

## State of the repo

Branch `m2-prep`, branched from `main` at `f13c03d` (confirmed via
`git merge-base`). 15 commits ahead of `main`, all pushed; `HEAD` is
`c74bb6d` ("docs: learnings from the M2 opening session (2026-08-31)").
`main` itself is untouched this session — still at `f13c03d`. No merge has
happened yet; merging `m2-prep` → `main` will need an admin bypass of the
1-review branch-protection rule, same unresolved, unsatisfiable-for-a-
solo-maintainer issue flagged every session it comes up.

Working tree carries only pre-existing dirt, unchanged in kind from the
predecessor stash: `.claude/remember/AGENT_RULES.md`, `CLAUDE.md`,
`docs/log.md` modified; `.claude/remember/{.processed, MEMORY.md,
ledger.json, friction/, report.md}` and `.claude/stash/*.md` untracked.

`packages/chiproof` is at 0.3.0 in the working tree but **unpublished** to
npm — publishing is explicitly the owner's call and is blocked on cleaning
up a fixture inconsistency first (see Gotchas).

## Key decisions

Owner unless noted:

- **D9**: the field distinguishing individuals under mode B is
  `document_number` (not a derived hash of the full MRZ or DG1).
- **D29** (closes Q18): chip-cloning / clone replay is accepted and stated
  as a known limitation rather than mitigated; `chip_auth` continues to be
  reported per D21. Recorded in PRD v1.14, and its interaction with D9 was
  clarified in v1.16: mode-B uniqueness only holds where `chip_auth` is
  true — a cloned chip without chip authentication can defeat the
  uniqueness guarantee, and that is now explicit in the PRD rather than
  implicit.
- **D30**: `sig-ed25519/1` becomes the *default* mode-B evidence plug,
  amending D27 (mode A still ships bare, unaffected). Tier ceiling is B,
  orchestrator-recommended and owner-vetoable.
- **`sig-ed25519/1` signed layout, CONFIRMED by the owner**: Ed25519 over
  `sha256(utf8("sig-ed25519/1\n") ‖ sha256(canonical(claim)) ‖
  base64urlDecode(nonce) ‖ utf8(scopeDomain) ‖ utf8(zktag))` — nonce enters
  as raw bytes, per the already-shipped `signed-receipt/1` plug's own
  convention (ruling from 2026-08-30, carried forward as the reference).
- "Close both chiproof gaps" (the adopter-gate hardening plus the zktag-
  binding gap the code review found) → chiproof 0.3.0.
- M2's POC targets were set as the Blueprint app-link path (primary) plus
  a Digital Credentials API experiment (secondary); the first real M2 spike
  combined both the handoff and the reinstall/masterlist scan work into one
  session rather than sequencing them.
- Everything committed and pushed to `m2-prep`; no merge to `main` yet.
- The short-lived upstream zkPassport cron watcher from the predecessor
  stash fired once more as a leftover before expiring — harmless, no
  action taken.
- `zkagent` npm package name stays parked, not reopened.

Orchestrator rulings this session (vetoable, not yet owner-confirmed):

- Masterlist negative handling is two distinct buckets: a masterlist
  integrity failure (corrupt/truncated/unparseable file) is `ok:false`;
  a well-formed masterlist that simply lacks the presenting issuer is
  `ok:true, allowed:false`. PRD's M0-era row on this is marked superseded;
  the M2 row is now the source of truth.
- The nonce-encoding reference for `sig-ed25519/1` is the shipped
  `signed-receipt/1` plug's convention, not chiproof's own test fixture
  (which was found inconsistent — see Gotchas).

## Findings

1. NFC foreground dispatch on Android pauses and resumes on every tag
   read — session state must be wiped in `onStop`, not `onPause`, or the
   first tap of a new session silently reuses stale state. (The owner's
   first tap this session failed for exactly this reason before the fix.)
2. Wiping session state on *any* failed attempt (not just a genuine new
   session) means a single mistyped MRZ that trips PACE-then-BAC fallback
   (`0x6300` → `0x6985`) forces a full retype rather than a retry — a real
   UX cost of the naive wipe-on-any-attempt approach, worth revisiting in
   the M2 proper build.
3. The M0-inherited `ResultActivity`, still present in the `m2-scan` fork,
   renders DG1 directly on screen. An accessibility snapshot taken during
   navigation pulled partial field text into two local agent transcripts.
   This was bounded: nothing reached the repo or git history, no values
   were recorded in any doc, and the M2-proper build design has no such
   screen (mode A shows only a verdict). Still, this is the concrete
   reason `ResultActivity` needs to be removed, not just deprioritized, in
   the real M2 build.
4. Reliable phone navigation for evidence capture: `am force-stop` +
   monkey relaunch + `dumpsys window mCurrentFocus` check for
   `RegularActivity` *before* any snapshot. Plain BACK opened the
   notification shade instead of navigating within the app; a bare
   relaunch without force-stop resumed onto the stale `ResultActivity`
   rather than starting fresh. `monkey -c LAUNCHER` resumes an existing
   task rather than starting a new activity — also worth remembering for
   future device evidence sessions.
5. The mode radio button displayed "B" once while a scan actually ran in
   mode A. The owner did not touch the control between scans. A code read
   afterward found no cause. This is an **open, unexplained bug** — not
   fixed, not understood, flagged for the M2 proper build to reproduce and
   trace.
6. Two agents drove the phone concurrently once this session — a duplicate
   was spawned without an `ListAgents` check first. The second agent
   caught the collision itself by noticing screenshot/state files it had
   not created. No corruption resulted, but it's the same class of mistake
   flagged in the auto-memory `clear-keeps-agents-running.md` note.
7. One agent hit a 429 "Fable 5 requires usage credits" error mid-task;
   root cause was an omitted `model` field on its `Agent` call, which
   silently defaults to Fable rather than Sonnet.
8. Self-consistency is not conformance: the `m2-handoff` spike's own
   15/15 and 17/17 green test runs said nothing about interoperating with
   a real EU verifier — the DCQL block it sent was accepted but decorative
   to the real reference implementation, and the `client_id`/`aud` trust
   model differed structurally (x509/PreRegistered vs. redirect_uri).
9. An EU-published ZK age library existing and being permissively licensed
   does not make it adoptable — parameter coverage (DSC key size, ABI),
   nonce binding, and off-device verifiability are the decisive axes, and
   they are orthogonal to license cleanliness. `av-lib-android-zkp-age-icao`
   passed the license check and failed on all three of those axes for both
   owner documents.
10. npm trusted-publisher bindings are per-package (carried forward from
    the predecessor stash, reconfirmed as still relevant to the pending
    0.3.0 publish decision).

## Open items / next steps

- Merge `m2-prep` → `main` (will require an admin bypass of the
  unsatisfiable 1-review branch-protection rule — flag it at merge time).
- Publish chiproof 0.3.0 — owner's call, currently blocked by the fixture
  nonce-encoding inconsistency (fix `sig-ed25519-zktag-plug.js` first, see
  Gotchas).
- Build M2 proper: Keystore/StrongBox-backed key, a biometric gate, no
  DG1-rendering screen anywhere in the flow, mode captured at scan time
  (not read later from a possibly-stale UI control — see finding #5),
  session state kept on access failure rather than wiped, QR rendering for
  the non-Blueprint path, and migrate the `m2-handoff` Android app onto a
  real `network_security_config` build.
- Root-cause the mode-radio display bug (finding #5) — currently open,
  unexplained, not reproduced under controlled conditions yet.
- Spike Credential Manager provider registration for the Digital
  Credentials API path (currently only probed as far as the consent gate
  with no provider registered).
- Q16 (scan cadence) and D10's ceiling still need real UX evidence, not
  yet gathered.
- The 12-repo prepack/adopter-gate rollout referenced in the predecessor
  stash is still orphaned — the peer "ci" session that was coordinating it
  is gone; a separate session ("barelo") is auditing other repos but this
  rollout itself has not restarted.
- Q26 remains open (not addressed this session).
- Renewal stability of `document_number` (D9) as a long-term identifier is
  still untestable with only two documents on hand.
- BSI masterlist ZIP provenance is still unverified.
- M0-EVIDENCE doc's still-pending items are unchanged from prior stashes.
- `.claude/remember/AGENT_RULES.md`'s rewrite is still unconfirmed by the
  owner.
- Rung 2 (agent delegation, M4/M5) remains frozen until rung-1 ships.

## Gotchas

- chiproof's own test fixture `sig-ed25519-zktag-plug.js` encodes the
  nonce as `utf8(nonce)`, which is inconsistent with the CONFIRMED
  production layout (`base64urlDecode(nonce)`, raw bytes). This is a real
  discrepancy between test fixture and shipped behavior, flagged for
  cleanup before 0.3.0 is published — do not copy the fixture's encoding
  as a reference.
- An accessibility snapshot of a screen that renders DG1 fields can leak
  partial document data into an agent's own transcript even when nothing
  touches git — treat any screen that renders raw document fields as
  unsafe to snapshot, not just unsafe to commit.
- Reliable on-device navigation for evidence capture needs `am force-stop`
  + relaunch + an explicit `dumpsys window mCurrentFocus` check — a bare
  BACK press or bare relaunch can silently land you on the wrong activity
  and produce evidence that looks right but was taken from a stale screen.
- `ListAgents` before spawning is not optional even mid-session without a
  `/clear` in between — two agents drove the same phone concurrently once
  this session from a plain spawn-without-checking mistake.
- Omitting `model: sonnet` on an `Agent` call silently inherits Fable, not
  an error at call time — the failure surfaces later as a 429 usage-credit
  error, which looks unrelated to the actual cause.
- `grep` on this host is ugrep 7.5.0; bounded-repetition regexes fail
  slowly and look like a hang — parse structured data with python3
  instead.
- Owner rule #2 still stands, restated three times this session: the
  orchestrator never codes or edits docs; every `Agent` call passes
  `model: sonnet` explicitly; forking skills like `/code-review` run on
  the parent model and must not be invoked directly — use a Sonnet
  `quality-assurance` agent instead.

## Recovery commands

```
cd packages/chiproof && npm install && npm run typecheck && node --test   # 125 tests
cd spikes/m2-handoff && npm install && node --test                        # 17 tests
```

`spikes/m2-scan` (Android, real device required):
```
export JAVA_HOME=$HOME/opt/jdk-21.0.12.1+1 ANDROID_HOME=$HOME/Android/Sdk
./gradlew :app:assembleRegularDebug
```

Device evidence captures for this session live under
`/home/hamr/.claude/jobs/e8664a28/tmp/` — this is job-temp storage and will
be cleaned; `M2-SCAN-EVIDENCE.md` carries everything needed from it, don't
rely on the raw captures surviving.

Docs index: `node ~/.claude/commands/docs-builder/docs-builder.cjs
index-flat`.

PRD: `docs/product/zkagent-prd.md` v1.16.

Evidence docs: `docs/logs/M2-*.md` (`M2-CAPTURE.md`, `M2-CONFORMANCE.md`,
`M2-EU-ZKP-SPIKE.md`, `M2-SCAN-EVIDENCE.md`).

Confirm branch point: `git merge-base main m2-prep` (should print
`f13c03d`).

Predecessor stash: `.claude/stash/d27-d28-zkagent-npm-dropped.md`.
