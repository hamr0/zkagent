<!-- DOCS_INDEX:START -->
Docs map: `docs/index.md` — every doc in this project, with line counts.
Search this corpus instead of reading it whole: `/docs-builder search <query words>`
<!-- DOCS_INDEX:END -->

<!-- MEMORY:START -->
@.claude/remember/MEMORY.md
<!-- MEMORY:END -->

<!-- AGENT_RULES:START -->
Standards guide (read when designing/building something new, not hot context):
.claude/remember/AGENT_RULES.md
<!-- AGENT_RULES:END -->

Repo-specific: `.claude/remember/` and `.claude/stash/` are tracked here by design (LIBRARY_CONVENTIONS §7 exemption), except `.claude/remember/last-review.md` and `fix-ledger.md`, which are gitignored since tracking them made `/branch-review` invalidate itself (writing them dirties the tree pre-Phase-0; committing them moves HEAD past the recorded SHA, failing Phase 0.5).
