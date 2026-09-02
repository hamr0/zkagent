# /remember — run report (2026-09-02)

## Sources
- Stashes consolidated: **5** (m2-freeze-audit-and-mitigations, m2-handoff-e2e-and-attester-binding,
  m2-log-view-and-blocking-dialogs, m2-d58-refactor-complete, m2-fence-review-release).
  `.processed` 15 → 20.
- Friction: **ran**, exit 0, over `~/.claude/projects/` (77 project dirs). 34 clusters, all 1-session.

## Facts
- 45 → 65 (measured from `git show HEAD`, not from an agent's self-report — its own summary said 46
  before, which was wrong by one).
- 2 folded lessons merged into existing lines rather than duplicated (X509 validity dates, `/clear`
  does not stop background agents). 1 existing line shortened. 1 existing line corrected for staleness
  (see Corrections).
- Length gate: **0 lines over 180 non-exempt**. Two lines sit at exactly 180 and pass. No exemptions claimed.

## Episodes
- 5 new appended; 5 oldest folded (lessons → facts) and deleted, per the cap.
- **Defect found and repaired**: the merge deleted 7, not 5, leaving 8. The two extra
  (`M1b probe + chiproof 0.2.0 release`, `D27/D28 decisions, zkagent npm dropped`) were not in the
  fold-list, so their lessons were never converted to facts first. Both restored verbatim from git
  HEAD, byte-identical. Count now **10**.

## Antigens
- Clusters routed: 5 → ag-001, 15 → drop, 14 → new:<theme>.
- All 14 new themes were 1-session, so `friction.cjs count` wrote none of them.
- ag-001: before 8 sessions → after 8. `newConversations: 0` — all 5 matched clusters were re-scans of
  conversations already in the entry's evidence set (2 of them carry ag-001's own recorded quotes).
  No promotion, no escalation.
- Invariants: `I6-new: EQUAL`, `I7: 0 mismatch(es)`.

## Ledger
```
ag-001  "never claim done/validated without a real run"   hot, 8 sessions, 0 recurrences since adoption
```
No entry needs a user decision.

## Corrections applied this run (owner-authorized)
1. Stale fact repaired: `.claude/remember`/`.claude/stash` stay tracked EXCEPT `last-review.md` and
   `fix-ledger.md`, gitignored in `67697ff` because tracking them made every `/branch-review`
   invalidate itself. The run's own session invalidated this fact; nothing in the stashes contradicted
   it, so neither extraction nor merge had cause to touch it.
2. Same stale claim corrected in `CLAUDE.md`'s trailing line (outside all managed markers).
3. Two wrongly-deleted episodes restored (above).

## Not done / deviations
- `AGENT_RULES` section in CLAUDE.md left **pointer-only**. The spec's verbatim block was applied and
  then reverted on owner instruction; the section is deliberately trimmed here. Reported upstream.
- Docs: `due` reported 12 changed since 699534bd (REORG IS DUE). `index-flat` was run —
  `docs/index.md` (27 rows) and `docs/log.md` regenerated, all 27 claimed line counts verified against
  `wc -l`, 0 mismatches. A full `/docs-builder reorg` is still DUE and was not run.

## Files changed
`.claude/remember/MEMORY.md`, `.claude/remember/.processed`, `.claude/remember/ledger.json` (no-op
rewrite), `.claude/remember/report.md`, `CLAUDE.md`, `docs/index.md`, `docs/log.md`.
Nothing committed.
