# Session Stash — M1b D26, chiproof 0.2.0, publish-template backport (2026-08-30, late evening)

Project: zkagent · Owner: hamr · Host: Fedora 44

Continuation after a `/clear` from the chiproof-0.1.0 release session. Owner
rule #2 stands: the main session orchestrates only — no coding, no doc
edits; Sonnet agents code/doc and escalate every decision to the
orchestrator.

## Summary

Three workstreams landed this session. The publish-template backport
(prepack rename, adopter-gate CI, type fixes) merged to `main` as a 9-commit
branch and triggered an unplanned 0.2.0 release when the adopter gate
exposed real type bugs in the published 0.1.0 package. M1b, the mode-A
unlinkability probe, ran to completion on real NL/US documents and found
the DSC/id_data verification-key selector (`vk_sha256`) is a real
cross-site-stable, document-dependent leak; owner ruled D26 to disclose the
bucket rather than try to hide it. Two upstream issues were filed against
zkPassport's public repos with owner approval.

## State of the repo

Branch `main` = `f5194d2` (pushed; fast-forward merge of the 9-commit
branch + docs commit `e9bbb41` + version bump `f5194d2`). Two more
branch-protection bypasses flagged this session (plus tag pushes) — the
1-review rule remains unsatisfiable for a sole maintainer.

Commits on the merged branch, in order:
- `1ac4d3d` prepack rename
- `b9e3ee1` publish.yml adopter gate
- `c52bc48` ci.yml adopter gate
- `e18bc59` fix verify() Verdict types
- `f99218f` tier-A quickstart
- `a586552` LIBRARY_CONVENTIONS sync
- `1946b24` fix issueChallenge types
- `9d75622` CI regression lines with `@ts-expect-error`
- `d7b8e83` 0.1.1 bump (superseded, see below)

Then: `e9bbb41` docs commit (`M1B-EVIDENCE.md`, `spikes/m1b-unlink/`), and
`f5194d2` the final 0.2.0 bump.

Tag `chiproof-v0.2.0` at `f5194d2`. Tag `chiproof-v0.1.1` was created then
deleted (owner ordered 0.2.0 instead, mid-publish). Run `33329675573`
(0.1.1) was cancelled before reaching npm; run `33329790824` (0.2.0)
succeeded.

npm: `chiproof@0.1.0` remains published with the broken types (deprecation
offered to the owner, not decided). `chiproof@0.2.0` is now on the
registry, verified end-to-end by re-downloading the tarball: 9 `.d.ts`
files including the new `Verdict` and `IssueChallengeOptions` typedefs.

Worktree and branch for the publish-template backport were removed after
merge.

Still dirty pre-existing, carried forward untouched: `.claude/remember/
AGENT_RULES.md` (unconfirmed rewrite), `CLAUDE.md`, `docs/log.md`,
`.claude/remember/{MEMORY.md, ledger.json, friction/, .processed,
report.md}`, stash files.

QA re-verification (independent agent) confirmed: branch integrity, test
suite, adopter gate, tarball diff vs. registry, and M1b presentations
including a bit-flip negative (`zk_proof_invalid`) — all checked before the
type fixes were built, not after.

## Key decisions

- **D26** (owner ruling): disclose the DSC/id_data verification-key bucket
  rather than attempt to hide or generalise it away. PRD advanced to v1.12:
  D26 added, Q15 closed, Q27 opened (current_date coarsening trade-off),
  FR9/FR12/D19/M1b row updated, `M1B-EVIDENCE.md` retitled "read by owner …
  pass with disclosed bucket", learnings entry added, docs index
  regenerated. M3 is unblocked by this decision.
- Owner ordered the release renamed from 0.1.1 to 0.2.0 mid-publish once it
  was clear the fix touched public type surface, not just a patch-level
  correction.
- chiproof@0.1.0 deprecation on npm is left as an open owner call, not
  decided this session.
- The 12-repo publish-template rollout (bareloop, knowless, litectx,
  pulselog, barebrowse, bareagent, baremobile, 8een, flightlog, mailproof,
  bareguard, bareloop-close) was handed off to a peer session named "ci";
  liteagents is exempt (untyped), bareloop-patients/* are fixtures, not
  targets.
- Upstream asks were posted from the owner's account only after explicit
  approval: `zkpassport/circuits#154` (7 asks — dedicated nonce input;
  class-hiding or documented buckets; current_date granularity;
  recursion/aggregation plans; machine-readable vk manifest;
  NONE-nullifier-type vs. `nullifier_secret=0` semantics since their PR
  #152 may supersede our convention and our plug may need migration; audit
  status + advisory mapping since their PR #96 has unpublished external
  audit notes roughly a year old) and a comment on
  `zkpassport-packages#246` (utils package is unlicensed, independently
  reconfirmed `license:null`).

## Findings

1. The hamr0 repo already had the adopter-gate template and prepack
   conventions committed and pushed (`939d40f`, `89a4103`) before this
   session picked the thread back up — a pre-`/clear` agent had chiproof's
   sync in progress on branch `ci-adopter-gate` already. The orchestrator
   mistakenly spawned a duplicate agent for the same work; it was stopped
   once discovered. Lesson reinforced: wait for a running agent's reply
   before spawning a new one for the same task.
2. The adopter gate found real bugs in chiproof@0.1.0's published types:
   `verify()` and `issueChallenge()` were both JSDoc-annotated as `object`,
   so TypeScript adopters got `TS2339` on `verdict.ok` / `challenge.nonce`,
   and `tier:'Z'` compiled silently with no error. Fixed JSDoc-only —
   `Verdict`, new `IssueChallengeOptions`, and `Challenge` typedefs added —
   zero runtime lines changed, verified by a comment-filtered diff. 116/116
   tests pass including real ZK artefacts copied into the worktree. The
   regression was proven to actually bite: reverting the fix fails CI.
3. M1b ran 7 full `zk-passport/1` presentations and 1 bare-mode baseline on
   real NL/US documents. All four proof stages were re-proved with fresh
   salts each time (all stages take salts) and all verified by chiproof in
   12–32 ms.
4. Two detector methods were used: a structured 32-byte-field diff, and the
   8een §7.3 longest-common-run method with planted controls. The
   longest-common-run detector initially missed an 8/16-byte planted
   control; a constant-mask fix caught it at 32 bytes. An early "unmasked
   floor" reading turned out to be a 271-byte header artefact, not a real
   leak.
5. All salted commitments, the nullifier, and the subscope were confirmed
   fresh per presentation — no cross-presentation stability there.
6. `current_date` is a Unix-seconds public `u64` that gates `max_scan_age`.
   A first reading of it as "stable" across presentations was itself an
   artefact of a shared `Date.now()` call across the runs in the same
   script; a time-separated run corrected this and showed it varies
   normally.
7. The one real cross-site-stable, document-dependent field is the
   `dsc`/`id_data` verification-key selector, `vk_sha256` — this identifies
   the DSC circuit class (a bucket of TBS-template size × key type ×
   hash algorithm). The zkPassport family has 284 `dsc` and 282 `id_data`
   circuit variants; the NL document lands in `tbs_1000`, the US document
   in `tbs_1600`.
8. Leak-closure validation was run with three scripts
   (`leak-strip-verify.mjs`, `leak-trial-verify.mjs`,
   `leak-doc-mask-diff.mjs`): dropping `vk_sha256` breaks verification
   outright once more than one key is pinned (`zk_unknown_circuit` — there
   is no trial-selection code in the plug to fall back on), and a trial
   `bb verify` sweep recovers the correct bucket deterministically in
   roughly 50–90 ms. Raw proof bytes themselves carry no NL/US fingerprint:
   0 of 2,752 byte positions were constant across 3 runs per document.
9. PACE and BAC are not observable anywhere in the proofs; bare mode
   reveals nothing beyond the verdict.

## Open items / next steps

- Q25 (bare vs. `zk-passport/1` in the reference app) is still open and
  blocks M2.
- Q27 (current_date coarsening trade-off) is newly open.
- chiproof@0.1.0 deprecation on npm is undecided.
- `zkagent@0.0.0` npm placeholder is still MIT-mislicensed (NO-GO #8),
  still open, still not urgent.
- Watch `zkpassport/circuits#154` and `zkpassport-packages#246` for
  replies.
- The 12-repo publish-template rollout is in progress with peer session
  "ci" — not this session's responsibility to track further.
- A possible plug migration to the NONE nullifier type is pending
  upstream's answer on `nullifier_secret=0` semantics vs. their PR #152.
- Carried over, still open: Q18 (chip cloning) and the M0 MRZ-persistence
  defect.
- `AGENT_RULES.md` rewrite is still unconfirmed.

## Gotchas

- Check `ListAgents` AND wait for a running agent's status reply before
  spawning a new agent for the same task — this session repeated the
  mistake from the last one.
- A resumed pre-`/clear` worktree agent may be unresumable after it
  completes (the worktree isn't recorded); its committed branch survives
  regardless — spawn a fresh agent into the same worktree rather than
  trying to resume the old one.
- Gitignored artefacts don't exist inside a fresh worktree — `cp -r` them
  in to unskip tests that depend on them.
- npm publish cancellation is race-able — check the registry before
  assuming a cancelled workflow run never reached npm.
- Adopter-gate quickstarts must mirror `chiproof.context.md` and
  dereference its returns, not restate them.
- `grep` on this host is ugrep 7.5.0.
- Regenerate the docs index after any doc edit.
- Commit only on owner request; never `git add -A`.

## Recovery commands

```
cd packages/chiproof && npm install && npm run typecheck && node --test
```

Re-prove the real artefacts: `node spikes/m1-zk/run/reprove-age-nonce.mjs`.

M1b probes: `node spikes/m1b-unlink/probe.mjs`.

Publish: `gh workflow run publish.yml --ref main`.

Toolchain: `bb` at `~/opt/bb` (5.0.0), `nargo` at `~/opt/noir`.
