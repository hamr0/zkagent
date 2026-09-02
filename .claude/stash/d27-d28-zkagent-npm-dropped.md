# Session Stash — D27/D28, zkagent npm dropped (2026-08-30, late evening → night)

Project: zkagent · Owner: hamr · Host: Fedora 44

Tail continuation of the same session as `m1b-d26-chiproof-0.2.0.md` — no
`/clear` in between. Owner rule #2 held throughout: the orchestrator session
never codes or edits docs itself; Sonnet agents did all writes and escalated
every decision back.

## Summary

Two open questions were closed by the owner (D27: M2 reference app ships
bare, no zk-passport/1 evidence plug at launch; D28: coarsen `current_date`
to midnight-UTC client-side, floor `max_scan_age` at 1 day), recorded in PRD
v1.13. Deprecation notices for `chiproof@0.1.0` and `zkagent@0.0.0` were
considered and dropped as unnecessary. Then the owner decided to drop the
`zkagent` npm package entirely — only `chiproof` ships as a package from
this project. An attempt to publish a `zkagent@0.0.1` Apache-2.0 placeholder
ran into a GitHub Actions dispatch-input bug and then three straight npm
E404s; an A/B test with a byte-identical known-good workflow exonerated
`publish.yml` and isolated the real cause to npm's per-package
trusted-publisher binding, which only exists for `chiproof`. The owner then
dropped the whole placeholder effort and every commit from it was reverted.
A final post-revert dispatch confirmed the pipeline is clean and idempotent.
Session tail also set up a short-lived cron watcher for two upstream
zkPassport GitHub threads.

## State of the repo

Branch `main` = `f13c03d`, pushed. Commit sequence this stash's window
(after `9f72bbb`, PRD v1.13):

- `9f72bbb` — D27/D28 recorded in PRD v1.13 (pushed)
- `fc5f1db` — `packages/zkagent-placeholder` (0.0.1, Apache-2.0) +
  `publish.yml` gained a `workflow_dispatch` `package` input
- `b5b2321` — fix: `inputs` context moved out of top-level `defaults.run`
  (was rejected at dispatch with HTTP 422)
- `b1a0d1d` — A/B test commit: `publish.yml` made byte-identical to the
  version that published `chiproof@0.2.0`, only `working-directory` changed
- `9ac6522`, `ae71333`, `f13c03d` — reverts of `b1a0d1d`, `b5b2321`,
  `fc5f1db` respectively, all pushed; `main` now sits at `f13c03d`

`publish.yml` is back to the `chiproof`-hardcoded `f5194d2` version,
byte-identical to before the placeholder attempt — confirmed, not assumed.
`packages/` contains only `chiproof`; no `zkagent-placeholder` directory
remains on `main`.

Roughly nine flagged branch-protection bypass pushes today in total (across
this stash and its predecessor), all owner-authorised — branch protection's
1-review rule is still unsatisfiable for a sole maintainer and remains
flagged, unresolved.

Working tree carries only pre-existing dirt, unchanged from the predecessor
stash: `.claude/remember/AGENT_RULES.md` (unconfirmed rewrite from a prior
session), `CLAUDE.md`, `docs/log.md` modified; `.claude/remember/{MEMORY.md,
ledger.json, friction/, .processed, report.md}` untracked; stash files
untracked pending this write.

npm registry state: `chiproof@0.2.0` is latest and confirmed served;
`zkagent@0.0.0` stays published, parked, and MIT-mislicensed (accepted, not
being re-raised — see Key decisions). No `zkagent@0.0.1` was ever
successfully published; the placeholder never reached the registry.

A session-only cron watcher was created (~09:43 daily, 7-day expiry) polling
`zkpassport/circuits#154` and a `zkpassport-packages#246` issue for replies.
It dies with this session unless recreated or moved to `/schedule`.

## Key decisions

Owner, 2026-08-30 (late):

- **D27** (closes Q25): the M2 reference/scanner app ships BARE —
  `evidence: []`, no `zk-passport/1` plug wired in at launch. The plug stays
  a verifier-side option, proven generic by D25's tier-A design, not a
  consumer of it by default. An on-device proving spike is optional future
  work, explicitly not a gate on M2.
- **D28** (closes Q27): `current_date` is coarsened client-side to
  midnight-UTC before it enters any proof/evidence path; `max_scan_age`'s
  floor becomes 1 day (was allowed finer-grained). No circuit change, no
  plug-contract change — this is purely an input-hygiene decision on the
  caller side. Both D27 and D28 are recorded in PRD v1.13 (`9f72bbb`,
  pushed).
- Deprecation notices for `chiproof@0.1.0` and `zkagent@0.0.0` were
  considered and explicitly dropped: npm's `latest` dist-tag already routes
  new installs correctly, and zero known adopters are pinned to either old
  version — a deprecation notice would add noise with no protective value
  right now.
- **zkagent npm package DROPPED entirely.** Owner's own words: "npm
  package is chiproof and that's it." `zkagent@0.0.0` stays published and
  parked exactly as-is (MIT mislicense accepted as a known, non-urgent
  defect; do not re-raise it — captured in auto-memory as
  `zkagent-npm-name-parked.md`). The `zkagent` name itself stays reserved on
  npm for a possible future agentic-auth product — reserving the name was
  never in question, only whether a real package ships under it now.

## Findings

1. GitHub Actions: the `inputs` context is valid only at job level, not in
   a top-level `defaults.run` block — using it there is rejected at
   dispatch time with HTTP 422, before any job even queues. Fixed in
   `b5b2321` by moving the reference down to job scope.
2. Three consecutive `zkagent-placeholder` publish runs failed with npm
   E404 on `PUT https://registry.npmjs.org/zkagent`, despite OIDC
   provenance signing completing without error — the failure is on the
   registry accepting the publish, not on the workflow producing it.
3. A/B test (`b1a0d1d`): made `publish.yml` byte-identical to the exact
   workflow version that had just published `chiproof@0.2.0` successfully,
   changing only `working-directory` to point at the placeholder package.
   This ALSO failed E404 — which exonerates `publish.yml` as a suspect by
   direct experiment, not by inspection.
4. Root cause, confirmed: npm trusted-publisher bindings are per-package,
   not per-repo or per-workflow. The owner's OIDC trusted-publisher binding
   exists on `chiproof` only; `zkagent` on npm has never had one
   configured, so any workflow — including a provably identical one —
   gets E404 trying to publish under that name. This generalizes last
   session's `publish.yaml`-vs-`publish.yml` finding: an E404 on PUT under
   OIDC can have two distinct causes (workflow filename mismatch, or simply
   no binding at all for that package name) and needs to be diagnosed as
   such rather than assumed to be the same bug recurring.
5. Post-revert verification: a final `gh workflow run publish.yml`
   dispatch (run `33331488778`) succeeded and hit the idempotent
   already-published skip path for `chiproof@0.2.0` — confirming the
   pipeline is green and `publish.yml` on `main` is fully restored to its
   pre-experiment behavior, not just visually identical.
6. A subagent's completion message for the A/B-test commit carried a
   security-classifier warning. The orchestrator independently re-read the
   actual commit diff before pushing rather than trusting the subagent's
   own account of what it did; the diff was clean, containing only the two
   intended lines (the `working-directory` change and nothing else).

## Open items / next steps

- **M2 (scanner app) is the next milestone**, and is explicitly a rewrite,
  not a graduation of the M0 spike app. It should start with its own
  riskiest-assumption POC on the Pixel 6a before any of the easier scanner
  code gets built (per the project's standing per-module POC-first rule).
  It must feed `current_date` as midnight-UTC into any evidence/proof path
  per D28, and ships bare per D27. The M0 spike app's known
  MRZ-persistence defect needs to be designed out in the rewrite, not
  carried forward.
- A 12-repo prepack/adopter-gate rollout is pending, coordinated with a
  peer session referred to as "ci" — not started in this stash's window.
- The zkPassport upstream watch (circuits#154, packages#246) is
  session-only and will silently stop working when this session ends;
  recreate the cron or move it to `/schedule` for durability if the owner
  wants it to survive.
- Q18 (chip cloning, undermines D10's 30-day-expiry rationale for mode B)
  is still an open design risk, carried over unresolved.
- `.claude/remember/AGENT_RULES.md`'s rewrite is still unconfirmed by the
  owner.
- Rung 2 (agent delegation, M4-M5) remains frozen until rung-1 ships.
- Branch-protection's unsatisfiable 1-review rule for a sole maintainer is
  still unresolved and still being flagged on every bypass.

## Gotchas

- npm trusted publishing bindings are per-package: a binding on one
  package does nothing for a different package name, even from an
  identical workflow file. An E404 on PUT means "no matching binding for
  THIS package," not necessarily "workflow is broken" — check which
  package the binding actually covers before assuming a config bug.
- GitHub Actions' `inputs` context is job-level only — it cannot be
  referenced in a top-level `defaults.run`, and using it there fails at
  dispatch time (422) before any job runs, not partway through a job.
- An A/B test with a byte-identical known-good workflow, changing only the
  one suspected variable, is the fast and conclusive way to exonerate or
  indict a workflow file — inspection alone left `publish.yml` a plausible
  suspect until the experiment ruled it out.
- Always verify a subagent's commit yourself when its own completion
  message carries a classifier warning — don't rely on the subagent's
  self-report of what it pushed.
- A deprecation notice on an old package version only has value if someone
  is actually pinned to it; check for known adopters before spending the
  effort.
- Owner rule #2 still stands: orchestrator never codes or edits docs
  itself.
- `grep` on this host is ugrep 7.5.0; bounded-repetition regexes fail
  slowly and look like a hang.

## Recovery commands

```
cd packages/chiproof && npm install && npm run typecheck && node --test
```

Publish (idempotent, skips if already at latest): `gh workflow run
publish.yml --ref main`.

PRD: `docs/archive/zkagent-prd.md` v1.13.

Confirm `publish.yml` matches its pre-experiment state: `git show
f5194d2:.github/workflows/publish.yml` vs. current `main`.

Confirm no placeholder package remains: `ls packages/` (should show only
`chiproof`).

Stash predecessor: `.claude/stash/m1b-d26-chiproof-0.2.0.md`.
