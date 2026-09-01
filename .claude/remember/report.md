# /remember consolidation report — 2026-09-01

## Stashes
- Processed this run: **5** (`d27-d28-zkagent-npm-dropped`, `m1b-d26-chiproof-0.2.0`,
  `m2-opening-poc-complete`, `m2-build-ed25519-key-test`, `m2-build-p256-plug-and-scanner-app`)
- Manifest `.claude/remember/.processed` now lists 15 of 15 stashes — backlog clear.

## Facts
- 26 → **45** (19 new; 4 existing lines merged/rewritten in place, 4 shortened during the gate pass)
- Merged in place: rung-1 status (M1b/M2 progress), npm-package fact (zkagent package dropped
  entirely), npm-404 fact (second cause added: no trusted-publisher binding), `/clear`+ListAgents
  fact (now also "wait for the running agent's reply").
- 2 lessons from aged-out episodes folded into Facts before deletion (verify hardware claims
  against the actual use case; design-only sessions accumulate unresolved surface).

## Mechanical length check
- `awk` overrun check (>180 chars, excluding the 100-char-backtick-literal exemption): **0 lines**
- Longest fact line: 180 chars. No exemptions were needed or claimed.

## Episodes
- 5 new, 0 duplicates found, **10 kept** (cap), 5 folded-then-deleted:
  Founding session, De-platforming M0, Android-first hardware decision, PRD v1.3 (zktag rename),
  PRD v1.4 (disclosure modes / chiproof / device purchase).

## Antigens (rendered from ledger, not hand-written)
- High confidence (loaded hot): **1** — ag-001
- Medium: 0 · Low: 0 · Newly promoted to hot this run: **0**
- `friction.cjs check`: I7 0 mismatches; **I6-new EQUAL** (render byte-equal to MEMORY.md)

## Friction
- 96 raw candidates → 34 clusters. Labels: 17 `drop`, 5 `ag-001`, 12 `new:`.
- All 12 `new:` themes sat at 1 distinct session each → written nowhere (correct: the ledger
  tracks recurrence; a single occurrence has none yet). They resurface on a later run if real.

## Ledger
```
ledger: ag-001 "never claim done/validated/tested without a real run"  hot, 0 recurrences since 2026-08-25
```
- ag-001: 8 sessions, unchanged. Its 5 matched clusters were all already-counted conversations
  (session-hash identity), so sessions/last_seen/recurred_while_hot were correctly not incremented.
- No escalations. No entries expired (hot never expires by age; no `observing` entries exist).

## Docs
- `docs-builder due`: **18 docs changed since 4eb92f7d — REORG IS DUE.**
- Auto re-index ran: `docs/index.md` regenerated (17 rows: 5 product, 11 logs, 1 archive);
  output identical to the committed file, so no diff. `docs/log.md` was already modified pre-run.

## Files updated
- `.claude/remember/MEMORY.md` (Facts + Episodes rewritten; Antigens rendered from ledger)
- `.claude/remember/ledger.json` (count applied; backup at job-temp before the run)
- `.claude/remember/.processed` (+5)
- `CLAUDE.md` — both managed sections already present and correct; no change needed.
- AGENT_RULES.md already existed — left untouched (user-owned).
