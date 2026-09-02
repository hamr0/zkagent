# M2 scanner ownership audit — read-only (2026-09-02)

**Status**: read-only audit, no repo file touched by any pass. Four revisions (the reviewer's own
fourth pass, following a read of its third), then critiqued by a second session. Anchors below were
independently re-verified against source by the orchestrator before this file was written. **Pinned
at `2cd1e00` (HEAD) plus the uncommitted D55/D56 working-tree changes** (`MainActivity.kt` under
active edit for the pane-visibility/MRZ-change-tracker fix, plus two new uncommitted files
`PaneVisibility.kt`/`MrzChangeTracker.kt`) — line numbers cite the state the audit actually read,
not necessarily what a future commit will show. Copied verbatim below; only this preface is new.

---

# apps/scanner ownership audit (read-only) — REVISION 4

**This is revision 4**, the reviewer's fourth pass, following a read of
revision 3. Two new columns and one promotion. Porcelain re-checked against
the same pin below — **unchanged**, byte-identical to revisions 1-3; no
repo file was touched by any revision of this audit. What changed in
revision 4:

1. **(a) gains an "Activity-recreation survival" column** (16 rows,
   mechanically answerable): APP-PERSISTED (2 rows — `lastReportText`
   `:532`, `ReportLog.entries` `:533`), FRAMEWORK-RESTORED (1 row —
   `TabLayout.selectedTabPosition`), LOST (11 rows — resets to its declared
   default on recreation), plus **2 rows that fit none of the three
   values** and are flagged rather than force-fit: `DeviceKey.lastMintAlias`
   and `DeviceKey.softwareEd25519Store` live in a Kotlin `object`
   (process-scoped, not Activity-instance-scoped) and are untouched by
   Activity recreation entirely — neither lost nor restored, because
   nothing about them is tied to the Activity instance that recreates.
   (a2) states the resulting mismatch as fact: on every rotation
   (`screenOrientation="fullSensor"`), the framework restores the tab
   selection while 11 of 16 app-level fields silently reset — F1 is one
   instance of that class, not an isolated defect.
2. **The State join gains a THREAD column** (MAIN / BACKGROUND / POSTED),
   intersecting each field-touch line against the async boundaries already
   established in appendix A12. This corrects `continueAfterRead`'s reads
   of `pendingHandoff` (`:1281`) and `verifiedRequest` (`:1282`) to their
   accurate, stronger form: both execute **inside** the background
   `Thread{}` opened at `:1262` — verified by reading the enclosing block,
   not by the lines' lexical proximity to `continueAfterRead`'s own
   declaration — on fields declared without `@Volatile` (`:172`, `:177`;
   confirmed at source that none of the five session fields at
   `:169-202` carries it, unlike `DeviceKey.lastMintAlias`). "Cross-thread
   read of a non-volatile field with no staleness guard" replaces the
   weaker "no staleness guard on the mint-path reads" language from
   revisions 2-3.
3. **Guards row 6 is promoted into an (a) ownership finding** (folded into
   rows 2/3, `verifiedRequest`/`pendingHandoff`): `showBlockingOutcomeDialog`
   is a third writer of both fields, alongside `beginHandoffVerification`'s
   capture and `mintAndMaybeHandoff`'s clear — and `wipeSession`'s own KDoc
   documents this as a split ("does not own them"), not a function that
   closes it. Guards row 6 is kept as the mechanism reference (what the
   guard reads); the ownership fact itself now lives in (a).

Revision 3's own changelog (still accurate, reproduced for continuity):

1. **The unfenced-async census in (a2) was undercounted.** It is **five**
   sites, not three: `:306` (masterlist-probe thread) and `:310`
   (device-key-probe thread) are release-reachable — only the long-press
   export at `:321` sits under `BuildConfig.DEBUG`, verified at source this
   revision. Both land in `runOnUiThread { emitReport(...) }`
   (`:1798`/`:1834`), and `emitReport` is not view-only: it calls
   `reportLog.append` (`:779`), mutating `ReportLog.entries`, which
   `onSaveInstanceState` persists (`:533`). Marked RELEASE-REACHABLE,
   lower consequence than the three crypto-path sites (they write reports,
   not request state), but counted.
2. The call-graph section now carries a header stating what it does and
   does not prove (edges exist; edges' absence is NOT provable from this
   method), and enumerates every listener/override/lambda root found by
   reading the file directly — not just the names the generating script
   already knew to look for.
3. A new **State join** section maps every function against (a)'s field
   list: which it reads, which it writes. It checks the specific prediction
   that `pendingHandoff`/`verifiedRequest` span every handoff/mint/report
   function — see that section for the confirm/refute result, including one
   false-positive correction the raw grep alone would have gotten wrong.
4. A new **Guards** section lists every multi-field decision found, the
   fields each guard's condition reads, and the fields that also bear on
   the same decision but are not read by the guard.
5. The call-graph row for `applyHandoffVerificationOutcome` no longer
   narrates its own prior-revision correction — it states callees only.
6. (b)'s preamble no longer defends line counts against a size-gate
   criticism — the policy sentence is removed; the column is left to speak
   for itself.
7. (a)'s ranking preamble is stated as a claim with an explicit falsifier,
   not a hedge in both directions.

Revision 2's own changelog (still accurate, reproduced for continuity):

1. Appendix A1 now carries the actual, unedited `grep` output for every
   field it backs (previously it only pointed back at the table).
2. Appendix A8 (the `preferSoftwareUniformity` dead-path claim) is re-run
   cleanly — the corrupted line is gone, and where `grep` genuinely cannot
   establish the claim (an omitted default argument), that limit is now
   stated explicitly with the two call sites quoted instead.
3. Every row→appendix citation in (a) was re-derived from the appendix
   itself, not from memory. Three were wrong and are fixed (row 8 → A2, not
   A5; row 9 → A7, not A6; row 13 → A4, not A7); the rest were checked and
   are correct.
4. Row 9's unresolved "`607→no, actually 609?`" is resolved: the real,
   re-run call-site list is `357, 608, 688, 736, 1639` (not the
   previously-cited `358, 689, 737, 1730` — those numbers were stale,
   carried over from an earlier, less careful read of the same file).
5. Row 2's writer list is fixed: `:605` writes `pendingHandoff`, `:606`
   writes `verifiedRequest` — they are adjacent lines in the same
   statement block, not the same line.
6. (a) gains a **Consequence** column and is **re-sorted** on
   (confirmed-by-symptom · consequence · likelihood), in that precedence —
   see the new ranking note before the table.
7. The old "Why ranked here" prose column is replaced by a terse **Sort
   basis** column carrying only the sort-key values; argumentative asides
   ("included for contrast", "flagged only because asymmetric", "correctly
   handled") are removed and replaced with the observation that grounded
   them.
8. (a2) promotes the async-cancellation finding to a **CONFIRMED**
   mechanical fact (A10 re-run: zero hits) and names all **three** unfenced
   async writer chains (Thread launch sites `:611`, `:1262`, `:1414`), not
   two.
9. `apps/scanner/app/src/regular/` and `apps/scanner/app/src/debug/` are now
   read in full and folded into (b)/(a)/(a2) — this closes an (e1) gap from
   revision 1.
10. A **call graph** for `MainActivity`/`RegularActivity` is added (new
    section, between (b) and (d)), with the generating `grep`/script
    invocations in the appendix.
11. (b) gains a **Lines** column (a sequencing map, explicitly not a gate —
    see that section's header).

**Tree state this report is anchored to (unchanged from revision 1):**

```
$ git rev-parse HEAD
2cd1e004bb3909f28620912d9af0a27f4f275f38

$ git status --porcelain
 M apps/scanner/app/src/main/java/com/tananaev/passportreader/MainActivity.kt
 M docs/product/zkagent-prd.md
?? .claude/stash/m2-log-view-and-blocking-dialogs.md
?? apps/scanner/app/src/main/java/com/tananaev/passportreader/MrzChangeTracker.kt
?? apps/scanner/app/src/main/java/com/tananaev/passportreader/PaneVisibility.kt
?? apps/scanner/app/src/test/java/com/tananaev/passportreader/MrzChangeTrackerTest.kt
?? apps/scanner/app/src/test/java/com/tananaev/passportreader/PaneVisibilityTest.kt
```

All `file:line` anchors in this revision were re-verified against this same
pinned tree state by re-running the cited command and reading its actual
output — not carried forward from revision 1's memory. Where revision 1 had
a stale line number (row 9, see above), this revision uses the freshly
re-run number and says so.

**Scope actually read, in full:** everything from revision 1 (17 files under
`app/src/main/java/...`, 11 files under `app/src/test/java/...`,
`AndroidManifest.xml`, `res/layout/activity_main.xml`) **plus, new this
revision:** `apps/scanner/app/src/regular/java/com/tananaev/passportreader/RegularActivity.kt`,
`apps/scanner/app/src/regular/AndroidManifest.xml`, and
`apps/scanner/app/src/debug/res/xml/network_security_config.xml`.

**Deliverables:** (a) mutable-state ownership + (a2) framework-owned state,
(b) function/class inventory (+ line counts), a new call-graph section,
(d) assertability map, (e1)/(e2) split unresolved items, plus a re-run raw
appendix. **Deliverable (c) — target class structure — is still deliberately
NOT included in this pass**, per the original amendment: proposing structure
in the same pass as the facts would turn the facts into evidence for the
proposal.

---

## (a) Mutable-state ownership table

**Claim: this table is sorted on (1) confirmed-by-symptom, (2) consequence
if this field's ownership discipline is violated, (3) likelihood (writer
count × ordering-dependence), in that precedence.** Row 1 is the only row
with `confirmed=YES`; everything else is sorted by consequence then
likelihood among the unconfirmed rows. **What would falsify this sort:**
(a) a device run showing rows 2 or 3 have actually fired — that would
promote either above row 1 on the first sort key, not merely tie it; (b) a
disagreement with the consequence labels themselves (HIGH/MEDIUM/LOW/NONE)
assigned per row — those labels are this audit's judgment call, stated in
each row's Consequence cell, not re-derived from a formula; a reader who
rates row 4's consequence as HIGH rather than MEDIUM, for instance, would
move it above rows 2/3 and this table would then be wrong, not merely
debatable.

**New this revision — "Survives Activity recreation?" column.** Three
mechanically distinguishable values: **APP-PERSISTED** (explicitly written
into the `onSaveInstanceState` `Bundle` and read back in `onCreate`) —
exactly 2 of 16 rows qualify; **FRAMEWORK-RESTORED** (a `View` with an
`android:id` whose own default instance-state save/restore mechanism
handles it, with no app code involved) — exactly 1 of 16; and **LOST**
(resets to its declared default because neither of the other two applies)
— 11 of 16. Two rows (13, 16) fit **none** of the three: both are Kotlin
`object`-scoped fields (`DeviceKey` is a singleton, not tied to any
Activity instance), so Activity recreation does not touch them at all —
they are flagged as an explicit exception rather than mislabeled LOST
(which would claim they reset, and they do not) or FRAMEWORK-RESTORED
(no View is involved).

| # | Field / view property | Writers (file:line) | Writer count | Ordering-dep? | Confirmed by symptom? | Survives Activity recreation? | Consequence if this fires | Sort basis |
|---|---|---|---|---|---|---|---|---|
| 1 | **`TabLayout.selectedTabPosition`** (framework-owned, see a2 F1) | App: `MainActivity.kt:267,272` (tab-select listener → `showPane()`) · Framework: `TabLayout.onRestoreInstanceState` (not in app source) | 1 app writer + 1 framework writer | YES | **YES — this is the brief's own opening bug report** | **FRAMEWORK-RESTORED** | User is stranded off the MRZ-entry form after an Activity recreation lands on the Log tab; re-tapping does nothing until D55's `showPane()` is itself re-triggered. | confirmed=YES; consequence=HIGH; writers=1+1(framework); ordering-dep=YES |
| 2 | `verifiedRequest` | `MainActivity.kt:606` (`= null`, paired with `:605`'s `pendingHandoff = handoff`), `:678` (`= outcome.request`, background-thread verify → `runOnUiThread`), `:884` (`= null`, dialog dismissal — **this is a third distinct writer, not merely another call site**: `showBlockingOutcomeDialog`'s dismissal handler, alongside `beginHandoffVerification`'s capture and `mintAndMaybeHandoff`'s clear; `wipeSession`'s own KDoc documents `pendingHandoff`/`verifiedRequest` as a split it explicitly does not own, rather than a single function that closes the field's lifecycle), `:1638` (`= null`, post-mint `runOnUiThread`) | 4 | YES | NO — no observed symptom found | **LOST** | Evidence gets signed and `direct_post`-delivered against a `verifiedRequest`/`zktag`/`scopeDomain` triple that a concurrent handoff replaced mid-flight. The mint-path read at `:1471` runs on the `:1414` background thread (see State join); the read at `:1282` runs **inside** `continueAfterRead`'s own nested `Thread{}` opened at `:1262` — a cross-thread read of a field declared without `@Volatile` (`:177`; confirmed none of the five session fields at `:169-202` carries it, unlike `DeviceKey.lastMintAlias`'s `@Volatile` at `DeviceKey.kt:162`), with no staleness guard analogous to `applyHandoffVerificationOutcome`'s (`:672`). | confirmed=NO; consequence=HIGH; writers=4 (3 distinct owning functions); ordering-dep=YES; recreation=LOST |
| 3 | `pendingHandoff` | `MainActivity.kt:605` (`= handoff`, capture, in `beginHandoffVerification`), `:883` (`= null`, dialog dismissal, in `showBlockingOutcomeDialog` — **the same third-writer fact as row 2**: `wipeSession`'s KDoc documents this pair as a split it does not own, not a lifecycle it closes), `:1637` (`= null`, post-mint, in `mintAndMaybeHandoff`) | 3 | YES | NO | **LOST** | Same race and same consequence class as row 2 — the two fields are always co-written and co-read; see the † note below the table. The read at `:1281` runs on the same `:1262` background thread as row 2's `:1282` read of `verifiedRequest` — same cross-thread, non-`@Volatile`, unguarded shape. | confirmed=NO; consequence=HIGH; writers=3 (3 distinct owning functions — `beginHandoffVerification`, `showBlockingOutcomeDialog`, `mintAndMaybeHandoff`); ordering-dep=YES; recreation=LOST |
| 4 | `lockedMode` | `MainActivity.kt:498` (`lockModeAndArm`), `:734` (`wipeSession`, `!keepMrzAndMode` branch) | 2 | Partial | NO | **LOST** | `wipeSession(false)` runs from `onStop()` (`:524`), dialog dismissal (`:886`), and `ReadTask.onPostExecute` (`:1123`, an `AsyncTask` callback whose timing relative to `onStop()` is unfenced — see a2 F5). A read completing after the Activity has already stopped and re-wiped clears `lockedMode` twice (both writes set the same `null`, so no direct corruption) — but it is the same unfenced-callback-after-lifecycle-transition shape as row 1, on a field that gates whether NFC dispatch stays armed. | confirmed=NO; consequence=MEDIUM; writers=2; ordering-dep=partial; recreation=LOST |
| 5 | `handoffStatus.text` | `MainActivity.kt:607` (sync, on capture), `:680` (async, `runOnUiThread` after background verify), `:708` (async, after background verify failure) | 3 | YES | NO | **LOST** — plain `TextView`, `android:freezesText` defaults false, not one of the two `Bundle`-persisted fields, and never re-set from `onCreate` either; after recreation it reads as unset until the next handoff event | Cosmetic only — a stale/superseded status string, corrected on the next lifecycle event; nothing downstream reads this field as a decision input. | confirmed=NO; consequence=LOW; writers=3; ordering-dep=YES; recreation=LOST |
| 6 | `lockButton.isEnabled` | `MainActivity.kt:499,609,689,735` | 4 | Partial | NO | **LOST** — `View.isEnabled` is not part of the default View instance-state save; resets to the XML-declared default (unset → enabled) | Cosmetic/UX only — worst case the button is briefly mis-enabled or mis-disabled; nothing else reads this field. | confirmed=NO; consequence=LOW; writers=4; ordering-dep=partial; recreation=LOST |
| 7 | `reportView.text` | `MainActivity.kt:288` (sync, `onCreate` restore of `savedInstanceState`), `:769` (inside `emitReport`) | 2 | NO | NO | **LOST** as a raw View property (plain `TextView`, `freezesText` defaults false) — but `:288` re-populates it every `onCreate` from the APP-PERSISTED sibling `lastReportText` (row 8), so the net effect across recreation is recovery via that sibling, not a bare reset | No observed drift (288 always runs once, synchronously, before any `emitReport` call in the same instance) — but the class doc at `:753-758` claims `emitReport` is "the ONE place" this is written, and `:288` contradicts that claim outright, bypassing the `Log.i` at `:781` the doc says is the entire reason the rule exists. A doc/code mismatch, not a live bug. | confirmed=NO; consequence=LOW (doc-integrity, not runtime); writers=2; ordering-dep=NO; recreation=LOST(view)/recovered-via-row-8 |
| 8 | `lastReportText` | `MainActivity.kt:287` (same restore branch as row 7), `:770` (inside `emitReport`) | 2 | NO | NO | **APP-PERSISTED** — `onSaveInstanceState:532` | Always co-written with `reportView.text` at the same two sites — same finding as row 7. | confirmed=NO; consequence=LOW; writers=2; ordering-dep=NO; recreation=APP-PERSISTED |
| 9 | `logView.text` | `MainActivity.kt:295` (`onCreate` restore, via `reportLog.rendered(...)`), `:780` (inside `emitReport`) | 2 | NO | NO | **LOST** as a raw View property, recovered via row 14's `ReportLog.entries` (APP-PERSISTED) the same way row 7 is recovered via row 8 | Both writers render through `ReportLog.rendered()` — content is never independently computed twice; no direct `logView.text = <literal>` exists anywhere (appendix A2). | confirmed=NO; consequence=LOW; writers=2; ordering-dep=NO; recreation=LOST(view)/recovered-via-row-14 |
| 10 | `modeStatusView.text` | `MainActivity.kt:373` (`refreshModeStatus()`), `:500` (`lockModeAndArm()`) | 2 | NO (resolved by inspection — see below) | NO | **LOST** — plain `TextView`, not `Bundle`-persisted, recomputed fresh by `refreshModeStatus()` at `onCreate:357` from `pendingHandoff`/`verifiedRequest`, which are themselves LOST (rows 2/3) | Worst case is a momentarily wrong mode label; nothing downstream reads `modeStatusView.text` as a decision input. | confirmed=NO; consequence=LOW; writers=2; ordering-dep=NO; recreation=LOST |
| 11 | `readInProgress` | `MainActivity.kt:920` (`startSession`, sync), `:1075` (`ReadTask.onPostExecute`, first statement, async) | 2 | YES | NO | **LOST** — resets to its declared default (`false`) | I observed no code between either write and its immediately-following `showPane()` call (`:921`, `:1076`) — so no window exists in which a stale value could be read between the two. This is the same *shape* as row 1 (async write racing a framework-timed read) but with the read pinned immediately adjacent to the write in both cases. | confirmed=NO; consequence=LOW; writers=2; ordering-dep=YES(mitigated, see observation); recreation=LOST |
| 12 | `lastMrzHash` | `MainActivity.kt:580` (`handleIncomingIntent`, NFC-tag-delivered), `:740` (`wipeSession`) | 2 | Partial | NO | **LOST** — resets to its declared default (`null`) | This value is diagnostic-only (per `MrzChangeTracker`'s own doc) — it is logged, never read by any branch that changes behavior. A stale value would produce a misleading `Log.i` line, nothing more. See (e2) for the open question this raises together with the MRZ `EditText`s' own recreation behavior. | confirmed=NO; consequence=NONE; writers=2; ordering-dep=partial; recreation=LOST |
| 13 | `DeviceKey.lastMintAlias` (`@Volatile`) | `DeviceKey.kt:383` (`ensureKey`, `if (alias != PROBE_ALIAS) lastMintAlias = alias`) | 1 | NO | NO | **NEITHER of the three** — lives in the `DeviceKey` Kotlin `object` (process-scoped), not the `MainActivity` instance; Activity recreation does not touch it at all, so it is not LOST (it does not reset) and not FRAMEWORK-RESTORED (no View involved) | Feeds only the debug-only dev-key-export feature (`exportDevAttesterPublicKeyIfPresent`, gated on `BuildConfig.DEBUG`); a stale value there exports the wrong key's label into a debug-build-only file, never shipped in release. | confirmed=NO; consequence=NONE-in-release/LOW-in-debug; writers=1; ordering-dep=NO; recreation=N/A(object-scoped, persists) |
| 14 | `ReportLog.entries` / `pendingIndexByAttempt` | `ReportLog.kt:206,218,235-237` (`entries`); `:204,209,219,237` (`pendingIndexByAttempt`) — internal to `ReportLog`, invoked from `MainActivity.kt:294` (`restore`) and `:779` (`append`, inside `emitReport`) | 2 production call sites into a 2-field internal state machine, reachable from 5 distinct call chains once async is counted (see a2 F5: `emitReport` is called from the main thread directly AND from all 5 unfenced background chains) | NO | NO | **Split**: `entries` is **APP-PERSISTED** (`onSaveInstanceState:533`, via `entriesSnapshot()`); `pendingIndexByAttempt` is **LOST** — `ReportLog.restore`'s own doc states any in-progress pending tracking is dropped on restore, never carried across | Self-contained — `MainActivity` never reaches into these fields directly, so no cross-file corruption path exists; `clear()` (`ReportLog.kt:217`) has no call site in `MainActivity.kt` (appendix A4). Two of the five async chains that reach `append` (`:306`, `:310` — the masterlist/device-key probe threads, release-reachable per a2 F5) can land after `onStop()` and write an entry into `entries`, which `onSaveInstanceState` (`:533`) then persists into the next Activity instance — a real but low-severity consequence (a diagnostic-probe report surviving into the next instance's log, not a request-state corruption). | confirmed=NO; consequence=LOW (raised from NONE this revision — see a2 F5); writers=2 call sites, reachable from 5 async chains; ordering-dep=partial; recreation=APP-PERSISTED(entries)/LOST(pendingIndexByAttempt) |
| 15 | `mainLayout` / `logLayout` / `loadingLayout` `.visibility` | `MainActivity.kt:913,914,915` — all three inside `showPane()` | 1 (function) | NO | NO | **LOST** — base `View`/`ScrollView`/`LinearLayout` do not save `.visibility` in their default instance state; resets to the XML-declared default (`main_layout` visible, `loading_layout`/`log_layout` `gone`), then unconditionally recomputed by `showPane()` at `onCreate:301` from `readInProgress` (row 11, LOST) and `tabLayout.selectedTabPosition` (row 1, FRAMEWORK-RESTORED) — this recompute is what races the TabLayout's own not-yet-landed restore in F1 | This is the D55-fixed shape of row 1's bug: single writer, no assignment to `.visibility` exists anywhere else in the file (appendix A3). | confirmed=NO; consequence=NONE (fixed); writers=1; ordering-dep=NO; recreation=LOST |
| 16 | `DeviceKey.softwareEd25519Store` | `DeviceKey.kt:610` (`ensureSoftwareEd25519`, lazy-init) | 1 | NO | NO | **NEITHER of the three** — same object-scoped exception as row 13 | `preferSoftwareUniformity` is never passed `true` from either `MainActivity` call site to `DeviceKey.ensureKey` (appendix A8) — this field is unreachable in the current build. | confirmed=NO; consequence=NONE (unreachable); writers=1; ordering-dep=NO; recreation=N/A(object-scoped, persists) |

† `pendingHandoff` (row 3) and `verifiedRequest` (row 2) are co-written at
three of their four/three writer sites — `:605`/`:606` (capture),
`:883`/`:884` (dialog dismissal), `:1637`/`:1638` (post-mint) — except that
`verifiedRequest` gets one independent fourth writer at `:678` (a successful
async verify sets `verifiedRequest` without touching `pendingHandoff`, which
was already set synchronously at `:605` on capture).

**Row 10 resolution (was row 9's unresolved "607→no, actually 609?" in
revision 1):** `refreshModeStatus()`'s doc claims it is "NEVER called while
`lockedMode` is set." Its actual, re-run, non-comment call sites are
`MainActivity.kt:357, 608, 688, 736, 1639` (appendix A7). I checked
`lockedMode`'s state at each: `357` runs in `onCreate`, before any lock is
possible; `608` runs in `beginHandoffVerification`, before `lockModeAndArm`
can have run for that handoff; `688` runs in `applyHandoffVerificationOutcome`,
same pre-lock window; `736` runs inside `wipeSession`, one line after
`lockedMode = null` at `:734`; `1639` runs inside `mintAndMaybeHandoff`'s
post-mint `runOnUiThread`, reached only after `ReadTask.onPostExecute`
already called `wipeSession(false)` (`:1123`, clearing `lockedMode`) earlier
in the same pipeline. All five call sites run with `lockedMode == null` —
the doc's claim holds by inspection of the corrected list.

---

## (a2) Framework-owned state

State this app reads or depends on that is written by the Android framework
or by an unfenced background thread the app itself launches — not by a
synchronous, lifecycle-ordered line of app code.

**The recreation-survival mismatch, as fact.** (a)'s new column tallies:
2 of 16 fields APP-PERSISTED, 1 of 16 FRAMEWORK-RESTORED, 11 of 16 LOST,
2 of 16 object-scoped (neither — persist regardless of Activity
recreation). On every physical rotation — `RegularActivity`'s manifest sets
`screenOrientation="fullSensor"` (a2 F1/F8), so this is not a rare event —
the framework restores `TabLayout.selectedTabPosition` (and, separately,
the `EditText`-typed input fields' own text, via each field's default
`freezesText` behavior — a View-level restoration this audit did not
originally track as one of (a)'s 16 rows, since no `MainActivity` code
writes to it) while 11 of 16 app-level fields reset to their declared
defaults in the same recreation. **F1 is one instance of this class, not
an isolated defect**: it is what happens when the one FRAMEWORK-RESTORED
row (1) and the 11 LOST rows are read together by the same function
(`showPane`, via `readInProgress` and `tabLayout.selectedTabPosition`)
without either side accounting for the other's different survival
behavior. See (e2) for a second, un-investigated instance of the same
mismatch class (the MRZ `EditText`s vs. `lockedMode`/`lastMrzHash`).

| ID | State | Writer / event | Status | Detail |
|---|---|---|---|---|
| F1 | `TabLayout.selectedTabPosition` | `TabLayout.onRestoreInstanceState` (Material Components internal) | **CONFIRMED live bug** | Framework dispatch order: `onCreate()` → `onStart()` → view-hierarchy restore (where `TabLayout` regains its saved selection) → `onPostCreate()` → `onResume()`. `MainActivity.onCreate()` calls `showPane()` at `:301` — before that restore. No `onPostCreate`/`onRestoreInstanceState` override exists anywhere in `MainActivity.kt` (appendix A9) to re-run the pane decision once the framework's own restore lands. **New this revision (from reading `src/regular/AndroidManifest.xml`):** the shipped `<activity>` sets `android:screenOrientation="fullSensor"` — the Activity is recreated on every physical device rotation, not only on rare config changes, so the window this bug needs is common, not exotic. |
| F2 | Activity `savedInstanceState: Bundle` (`STATE_LAST_REPORT`, `STATE_LOG_ENTRIES`) | `Activity.onSaveInstanceState` dispatch, framework-timed | No ordering defect found | The app's own read (`:286`, `:293`) runs synchronously inside `onCreate`, before `showPane()` (`:301`) — fully resolved before the pane decision, unlike F1. |
| F3 | Fragment/`DatePickerDialog` restoration (`MainActivity.kt:341,352`) | `FragmentManager`'s own state restore | Not traced further — see (e1) | Out of this pass's depth; flagged unverified, not concluded either way. |
| F4 | NFC intent delivery (`ACTION_TECH_DISCOVERED`, `EXTRA_TAG`) | `NfcAdapter` foreground dispatch → `onNewIntent` (`:536`) → `handleIncomingIntent` (`:542`) | Guard exists but is narrow | `handleIncomingIntent`'s guard (`:563-566`, corrected line numbers per this revision's re-run) checks `lockedMode != null` and non-empty MRZ fields only — not "is a read already in flight." Whether a second tag intent can physically arrive mid-`IsoDep`-session is unverified — see (e1). |
| F5 | **CONFIRMED: this app has no async-cancellation discipline at all.** | — | **Mechanically established, not inferred** | Appendix A10, re-run: `grep -n "onDestroy\|cancel(true)\|AsyncTask.Status\|isCancelled" MainActivity.kt` returns **zero lines**. There is no `onDestroy()` override, no `AsyncTask.cancel()`, no cancellation/liveness check anywhere in the file. Appendix A12 (re-run) shows **five** unfenced `Thread{}`/`AsyncTask` launch sites, corrected this revision from three. Single-owner (WHERE a write happens) and fencing (WHEN it happens) are orthogonal — a site can pass the single-writer test in (a) and still be one of these five. Three reach request/mint state directly: (i) `:611` — `beginHandoffVerification`'s verification thread → `runOnUiThread` at `:618` → `applyHandoffVerificationOutcome`, writing `verifiedRequest` (`:678`) or nulling `pendingHandoff`/`verifiedRequest` (`:883-884`); (ii) `:1262` — `continueAfterRead`'s zktag/key-derivation thread → `runOnUiThread` at `:1285,1309,1329,1353,1369`; (iii) `:1414` — the `BiometricPrompt.onAuthenticationSucceeded` callback's mint thread → `mintAndMaybeHandoff` → `runOnUiThread` at `:1474,1515,1636,1727`, the path that nulls `pendingHandoff`/`verifiedRequest` at `:1637-1638` and calls `refreshModeStatus()` at `:1639`. Two more, RELEASE-REACHABLE (verified at source, `:304-311`: neither click listener sits inside the `if (BuildConfig.DEBUG)` block that starts at `:319` and gates only the long-press listener at `:320`): (iv) `:306` — the masterlist-probe button's click listener → `Thread { runMasterlistProbe() }` → `runOnUiThread { emitReport(...) }` at `:1798`; (v) `:310` — the device-key-probe button's click listener → `Thread { runDeviceKeyProbe() }` → `runOnUiThread { emitReport(...) }` at `:1834`. (iv)/(v) write only `reportView.text`/`lastReportText`/`logView.text`/`ReportLog.entries` via `emitReport` — no `pendingHandoff`/`verifiedRequest`/`lockedMode` write — a narrower consequence than (i)-(iii), see (a) row 14's revised consequence. `ReadTask` (the sixth async actor, `doInBackground`/`onPostExecute`'s own thread) is likewise never cancelled. **Whether any of the five has manifested as an observed symptom is a separate, still-open question — see (e2).** |
| F6 | `BiometricPrompt` authentication result (`:1400,1417,1432`) | Android's system biometric/credential UI | Subsumed by F5(iii) | This IS unfenced writer chain (iii) in F5 — listed there in full; kept as its own row only to name the callback methods (`onAuthenticationSucceeded`/`Error`/`Failed`) for anyone searching by name. |
| F7 | Runtime permission grants | — | No dependency found | `USE_BIOMETRIC` is normal-protection-level (no runtime prompt), NFC likewise; no `onRequestPermissionsResult` override exists (appendix A11, re-run: zero hits). |
| F8 | **NEW this revision** — `RegularActivity`'s manifest intent filters | Android's `Intent` resolution against `src/regular/AndroidManifest.xml`'s two `<intent-filter>` blocks | Confirmed, not previously in scope | `MAIN`/`LAUNCHER` (icon launch) and `VIEW`/`BROWSABLE` for `android:scheme="av" android:host="authorize"` (the handoff deep link `HandoffClient.parseAvLink` parses). `android:launchMode="singleTop"` matches the `FLAG_ACTIVITY_SINGLE_TOP` used in `armNfcDispatch`'s `PendingIntent` (`MainActivity.kt`, appendix-verified in revision 1's A-series, unchanged). `android:exported="true"` — any app on the device can send this Activity a `VIEW` intent with an `av://authorize` URI; `RequestTrust`'s origin/signature verification is therefore load-bearing against a genuinely untrusted intent source, not merely a defense-in-depth layer. |

---

## Call graph — MainActivity / RegularActivity

**This graph proves edges EXIST. It does not prove edges ABSENT.** A14's
method greps known callee names from the declaration list within computed
line ranges — it structurally cannot see a lambda invoked later by the
framework, a listener/callback override, an `object :` body, or a method
reference, because those are call sites the script never went looking for.
This is not a hypothetical caveat: A14 already produced exactly this error
once, in the revision-2 draft of this same table — the `TabLayout` listener
object's 7-line body, lexically nested inside `onCreate`'s line range,
swallowed the ~90 lines of `onCreate` that follow it in the file, hand-fixed
before publishing. The residual, visible symptom of the same structural
limit is below: `onTabReselected` — the actual method the D55/F1 bug report
names — does not appear as its own row in the auto-generated table; its
body was attributed to `onCreate` as a callee, which is lexically true but
runtime-false (the framework invokes `onTabReselected` directly, on a tab
tap; `onCreate` only registers the listener object once). **A cut justified
by "nothing else calls this function" is not supported by this evidence** —
only a cut justified by "I read every listener/override/lambda site and
none of them call this" is. The subsection after the main table enumerates
every such site found by reading the file directly, as explicit roots.

Generated by parsing `MainActivity.kt`'s own function boundaries and
re-grepping each known function/method name as a callee within those
boundaries, filtering out matches inside comments (the exact script is in
appendix A14 — re-run it to regenerate this table mechanically rather than
trusting it by eye). Framework/lifecycle callbacks are listed as **roots**
(left column, bold) since nothing in this file calls them — the OS does.
Cross-class calls (into `DeviceKey`, `RequestTrust`, `ReportLog`, etc.) are
included per the owner's stated interest in "classes that call each other";
the pure/tested objects' own internal calls are not re-expanded here (their
own (b) entries and the assertability map (d) cover them).

`RegularActivity` (`src/regular/java/.../RegularActivity.kt:3`) contributes
**zero** methods of its own — `class RegularActivity : MainActivity()` is
the entire file. Every lifecycle root below is dispatched by the framework
directly to a `RegularActivity` instance and runs entirely inherited
`MainActivity` code; there is no override anywhere in `RegularActivity.kt`
to diverge from.

| Caller (root in bold) | Callees (this-file) | Callees (cross-class) |
|---|---|---|
| **`onCreate` (`:236`, framework root)** | `showPane` (`:301`, own top-level statement), `refreshModeStatus` (`:357`), `handleIncomingIntent` (`:358`) — the registration statements at `:266-273` (`addOnTabSelectedListener`), `:303,305,309,320,326,329,334,345` (click/long-click/editor-action listeners) do not themselves call anything at `onCreate`-execution time; they register listener objects whose bodies run later as separate framework-invoked roots — see the explicit-roots subsection below | `ReportLog.restore`/`.rendered` (`:294-295`) |
| **`onNewIntent` (`:536`, framework root)** | `handleIncomingIntent` (`:539`) | — |
| `handleIncomingIntent` (`:542`) | `beginHandoffVerification` (`:548`), `convertDate`×2 (`:561,562`), `startSession` (`:582`) | `HandoffClient.parseAvLink` (`:545`), `MrzChangeTracker.hash`/`.logLine`/`.compare` (`:576-578`) |
| `applyPendingHandoffText` (`:587`) | `beginHandoffVerification` (`:594`) | `HandoffClient.parsePastedText` (`:588`) |
| `beginHandoffVerification` (`:604`) | `refreshModeStatus` (`:608`), `verifyPendingHandoff` (`:613`, inside its own `Thread{}` at `:611`), `applyHandoffVerificationOutcome` (`:618`, inside `runOnUiThread`) | — |
| `verifyPendingHandoff` (`:628`, runs on the `:611` background thread) | — | `RequestTrust.originOf`/`.resolveVerifierKey`/`.verifyRequestObject`/`.tierOf` (`:629-665`), `HandoffClient.fetchRequestRaw` (`:643`) |
| `applyHandoffVerificationOutcome` (`:671`, runs on the UI thread via `runOnUiThread`) | `emitReport` (`:699`), `showBlockingOutcomeDialog` (`:713`), `refreshModeStatus` (`:688`) | `RequestTrust.tierOf` (`:679`) |
| **`lockModeAndArm` (`:411`, click-listener root)** | `emitReport`×3, `siteTitleFor`×3, `showBlockingOutcomeDialog`×3 (the three refusal branches, `:443-484`), `tierOutcomeFor` (`:455`), `armNfcDispatch` (`:501`) | `RequestTrust.expiresAtOf`/`.isExpired` (`:440-441`) |
| `tierOutcomeFor` (`:395`) | — | `RequestTrust.tierOf` (`:396`) |
| **`onResume` (`:511`, framework root)** | `armNfcDispatch` (`:513`) | — |
| **`onPause` (`:516`, framework root)** | — (disables NFC dispatch directly; no `wipeSession` call — see class doc, item 6) | — |
| **`onStop` (`:522`, framework root)** | `wipeSession` (`:524`) | — |
| **`onSaveInstanceState` (`:530`, framework root)** | — | `ReportLog.entriesSnapshot` (`:533`) |
| `wipeSession` (`:729`) | `refreshModeStatus` (`:736`) | — |
| `emitReport` (`:768`) | `logTitleSizePx` (`:780`) | `ReportLog.append`/`.rendered` (`:779-780`) |
| `showBlockingOutcomeDialog` (`:875`) | `wipeSession` (`:886`) | — |
| `showPane` (`:911`) | — | `PaneVisibility.choosePane` (`:912`) |
| `startSession` (`:919`) | `showPane` (`:921`) | — |
| **`ReadTask.doInBackground` (`:940`, `AsyncTask` root — runs on a background thread the framework pool manages)** | `passiveAuthAgainst` (`:1049`) | `MasterlistVerifier.load` (`:1041`), `M0Probe.tryActiveAuth` (`:1021`) |
| `passiveAuthAgainst` (`:1060`) | — | `M0Probe.passiveAuth` (`:1066`) |
| **`ReadTask.onPostExecute` (`:1069`, `AsyncTask` root — delivered on the main thread by `AsyncTask`'s own `Handler`, timing not app-controlled)** | `showPane` (`:1076`), `emitReport` (`:1100`), `siteTitleFor` (`:1103`), `showBlockingOutcomeDialog` (`:1113`), `wipeSession` (`:1123`), `continueAfterRead` (`:1124`) | `FailureTransition.classify` (`:1087`) |
| `continueAfterRead` (`:1133`) | `chipAuthLabel`×8, `emitReport`×7, `siteTitleFor`×3, `showBlockingOutcomeDialog`×5, `promptAndMint` (`:1369`) — the four refusal branches plus the progress-report emit, all listed at their exact call lines `:1144-1369` | `MintGate.mayMint` (`:1162`), `RequestTrust.expiresAtOf`/`.isExpired` (`:1217-1218`), `M0Probe.deriveCandidates` (`:1325`), `DeviceKey.aliasForOriginAndZktag`/`.ensureKey` (`:1348,1350`, inside its own `Thread{}` at `:1262`) |
| `promptAndMint` (`:1373`) | `emitReport`×2, `chipAuthLabel`×2, `showBlockingOutcomeDialog`×2 | `DeviceKey.initSignature` (`:1374`) |
| **`onAuthenticationSucceeded` (`:1400`, `BiometricPrompt` framework root, nested in `promptAndMint`)** | `mintAndMaybeHandoff` (`:1414`, inside its own `Thread{}`) | — |
| **`onAuthenticationError` (`:1417`, `BiometricPrompt` framework root, nested in `promptAndMint`)** | `emitReport`, `chipAuthLabel`, `showBlockingOutcomeDialog` (`:1418-1429`) | — |
| **`onAuthenticationFailed` (`:1432`, `BiometricPrompt` framework root, nested in `promptAndMint`)** | — (logs only, no state write) | — |
| `mintAndMaybeHandoff` (`:1463`, runs on the `:1414` background thread except where wrapped in `runOnUiThread`) | `emitReport`×3, `chipAuthLabel`×5, `showBlockingOutcomeDialog`×3, `refreshModeStatus` (`:1639`) | `RequestTrust.describeEvidenceRequired` (`:1572`), `DeviceKey.currentPublicKeyDer`/`.currentKeyDetails` (`:1512,1540`), `EvidenceSigner.messageFor`/`.sign` (`:1531-1532`), `HandoffClient.buildPresentation`/`.postDirectPost` (`:1599,1608`), `MintConfirmation.confirmsSuccess` (`:1739`) |
| `runMasterlistProbe` (`:1771`, runs on the `:306` background thread) | `diagnosticSummary`, `emitReport` (`:1798`, inside `runOnUiThread`) | `MasterlistVerifier.load`×2 (`:1777,1785`) |
| `runDeviceKeyProbe` (`:1807`, runs on the `:310` background thread) | `diagnosticSummary`, `emitReport` (`:1834`, inside `runOnUiThread`) | `DeviceKey.ensureKey`×2, `.initSignature`×2 (`:1811,1816,1820,1825`) |

**Two things this table makes visible that the flat function list in (b)
does not:** (1) `emitReport`, `chipAuthLabel`, `showBlockingOutcomeDialog`,
and `siteTitleFor` are the file's actual hubs — `continueAfterRead` alone
calls `chipAuthLabel` 8 times and `emitReport` 7 times, one call per outcome
branch, which is the shape of a function that IS one dispatch table
manually unrolled. (2) Every cross-class call into `DeviceKey`,
`RequestTrust`, `EvidenceSigner`, `HandoffClient`, `MasterlistVerifier`, and
`M0Probe` originates from exactly three functions —
`continueAfterRead`/`mintAndMaybeHandoff` (the mint pipeline) and
`ReadTask.doInBackground` (the read pipeline) — plus the two debug-probe
functions. No other function in the file talks to those classes directly.

### Explicit listener/override/lambda roots

Every registration site of this shape (`setOnClickListener`,
`setOnLongClickListener`, `setOnEditorActionListener`,
`addOnTabSelectedListener`, `registerForActivityResult`,
`DatePickerDialog.newInstance`'s callback argument, `BiometricPrompt`'s
`AuthenticationCallback`, `AsyncTask`'s overrides) found by reading
`MainActivity.kt` directly (`grep -n` invocation in appendix A17), each
listed as its own root regardless of whether the auto-generated table above
already found its callees through some other path.

| Root | Registered at | Callees |
|---|---|---|
| `qrCaptureLauncher`'s `registerForActivityResult` callback | `:223` (field initializer, runs before `onCreate`) | `QrCapture.decode` (`:228`), `applyPendingHandoffText` (`:232`) |
| `TabLayout.OnTabSelectedListener.onTabSelected` | `:267`, registered `:266` | `showPane` (`:267`) |
| `TabLayout.OnTabSelectedListener.onTabUnselected` | `:268`, registered `:266` | — (empty body) |
| `TabLayout.OnTabSelectedListener.onTabReselected` | `:272`, registered `:266` | `showPane` (`:272`) — **this is the method the D55/F1 bug report names**; it does not appear as a row of its own in the auto-generated table above |
| `lockButton.setOnClickListener` | `:303` | `lockModeAndArm` (`:303`) |
| `button_m2_masterlist_probe.setOnClickListener` | `:305` | `Thread { runMasterlistProbe() }` (`:306`) — see a2 F5(iv), RELEASE-REACHABLE |
| `button_devicekey_probe.setOnClickListener` | `:309` | `Thread { runDeviceKeyProbe() }` (`:310`) — see a2 F5(v), RELEASE-REACHABLE |
| `button_devicekey_probe.setOnLongClickListener` | `:320`, inside `if (BuildConfig.DEBUG)` at `:319` | `Thread { DeviceKey.exportDevAttesterPublicKeyIfPresent(...) }` (`:321`) — the only one of these five listener-launched threads that is actually debug-gated |
| `button_scan_qr.setOnClickListener` | `:326` | `qrCaptureLauncher.launch(null)` (`:327`) |
| `handoffManualInput.setOnEditorActionListener` | `:329` | `applyPendingHandoffText` (`:330`) |
| `expirationDateView.setOnClickListener` | `:334` | `loadDate` (`:335`), `DatePickerDialog.newInstance(...)` — its own callback lambda (`:336-338`) writes `expirationDateView`'s text (`:337`), not one of (a)'s tracked fields |
| `birthDateView.setOnClickListener` | `:345` | `loadDate` (`:346`), `DatePickerDialog.newInstance(...)` callback (`:347-349`) writes `birthDateView`'s text (`:348`) |
| `BiometricPrompt.AuthenticationCallback.onAuthenticationSucceeded` | `:1400` | already in the main table (`mintAndMaybeHandoff` via `:1414`) |
| `BiometricPrompt.AuthenticationCallback.onAuthenticationError` | `:1417` | already in the main table |
| `BiometricPrompt.AuthenticationCallback.onAuthenticationFailed` | `:1432` | already in the main table (logs only) |
| `ReadTask.doInBackground` (AsyncTask override) | `:940` | already in the main table |
| `ReadTask.onPostExecute` (AsyncTask override) | `:1069` | already in the main table |

Every row above was cross-checked against the main table: the `TabLayout`
row is the one genuine gap the auto-generated pass produced (now closed);
every other listener/override here was already reachable through some path
in the main table, confirming the main table's coverage for everything
except the one nested-anonymous-class case the header above describes.

---

## (b) Function/class inventory

One line each, now with a **Lines** column — a sequencing map, not a gate.
Line counts for `MainActivity.kt` are exact (closing-brace-matched,
appendix A15); line counts for every other file are **approximate**
(next-declaration-start minus this-declaration-start, which typically
over-counts by a few lines for the next item's leading doc comment —
appendix A16 has the generating script and raw spans).

### Canonical.kt (pure, fully assertable) — 71 lines total
- `Canonical.canonicalize(value)` — `:25` — ~2 lines — public entry, dispatches to `stringify`. Pure.
- `Canonical.stringify(value)` — `:27` — ~26 lines — recursive JSON-canonicalization. Pure. No state.
- `Canonical.quote(s)` — `:53` — ~19 lines — JS-compatible string escaping. Pure.

### EvidenceSigner.kt (pure, fully assertable) — 128 lines total
- `EvidenceSigner.claimHash` — `:54` — ~3 lines — hashes a canonicalized claim. Pure.
- `EvidenceSigner.preimage` — `:57` — ~12 lines — builds the domain-separated preimage. Pure.
- `EvidenceSigner.sigEd25519Message` / `sigP256Message` — `:69`/`:73` — ~4/~5 lines — the two algorithm-specific message builders. Pure.
- `EvidenceSigner.messageFor` — `:78` — ~7 lines — dispatch by `DeviceKey.Algorithm`. Pure. Single point coupling this file to `DeviceKey`'s enum (documented as deliberate).
- `EvidenceSigner.sha256` — `:85` — ~10 lines (incl. next item's doc) — helper. Pure.
- `EvidenceSigner.sign` — `:113` — ~13 lines — **mixed**: calls into a live `Signature` object (Android-bound side effect) but is otherwise pure construction of an `EvidenceItem`. Exercised end-to-end via a plain-JVM `Signature` in `EvidenceSignerTest`.
- `EvidenceSigner.keyIdFor` — `:126` — ~3 lines — pure hash+hex. Pure.

### FailureTransition.kt (pure, fully assertable) — 144 lines total
- `FailureTransition.classify` — `:58` — ~12 lines — single decision function, does one thing. Pure.
- `FailureTransition.keepsMrzAndMode` (Classification overload) — `:70` — ~10 lines — pure mapping.
- `FailureTransition.keepsMrzAndMode` (two-Boolean legacy overload) — `:80` — ~19 lines — pure, documented compatibility shim.
- `FailureTransition.isTransientChipCommunicationFailure` — `:99` — ~26 lines — cause-chain walk. Pure.
- `FailureTransition.isAccessEstablishmentFailure` — `:125` — ~19 lines — cause-chain walk. Pure.

### HandoffClient.kt (mostly Android-bound: network I/O) — 166 lines total
- `HandoffClient.parseAvLink` — `:43` — ~16 lines — pure-ish parsing (takes `android.net.Uri`, a stub type under this test config — see (d)).
- `HandoffClient.parsePastedText` — `:59` — ~17 lines — same `Uri`-stub caveat.
- `HandoffClient.fetchRequestRaw` — `:84` — ~18 lines — network I/O. Android-bound.
- `HandoffClient.buildPresentation` — `:102` — ~34 lines — pure JSON construction, exercised in `EvidenceSignerTest`.
- `HandoffClient.postDirectPost` — `:142` — ~24 lines — network I/O. Android-bound (`android.util.Base64`).

### ImageUtil.kt (Android-bound) — 54 lines total
- `ImageUtil.decodeImage` — `:26` — ~28 lines — does 3 things (format dispatch, WSQ pixel unpacking, `BitmapFactory` decode). Android-bound, no direct unit test.

### M0Probe.kt (mixed; explicitly a POC per its own doc, "THROWAWAY... not shipped") — 310 lines total
- `M0Probe.Timeline.mark`/`.report` — `:49`/`:50` — inside a ~13-line class — pure timing accumulation.
- `M0Probe.Verdict` (data class + factories) — `:59` — ~13 lines — pure, with a constructor invariant.
- `M0Probe.loadMasterList` — `:96` — ~44 lines — ASN.1 parsing + declared-vs-parsed counting. Exercisable (Log calls are no-ops under this config).
- `M0Probe.passiveAuth` — `:141` — ~89 lines — the file's largest function: sequential digest/validity/chain/signature checks, each with its own early-return `Verdict`.
- `M0Probe.tryActiveAuth` — `:238` — ~27 lines — two independent try/catch blocks by design.
- `M0Probe.deriveCandidates` — `:266` — ~19 lines — pure-ish HMAC/SHA-256 derivation.
- `M0Probe.dataGroupInventory` — `:297` — ~10 lines — pure-ish, reads `SODFile`.
- `M0Probe.tamperedDg1` — `:308` — ~3 lines — pure byte-flip helper.
- **No `M0ProbeTest.kt` exists** — zero direct unit tests; `Verdict`/`ChipAuthStatus` are exercised indirectly via `MintGateTest`.

### M2MasterlistProbe.kt (Android-bound, explicitly a POC — "THROWAWAY. Not shipped") — 131 lines total
- `M2MasterlistProbe.nativeHeapKb` — `:53` — ~1 line — Android-bound (`android.os.Debug`).
- `M2MasterlistProbe.sha256Hex` — `:55` — ~7 lines — pure.
- `M2MasterlistProbe.runAndReport` — `:63` — ~69 lines — the whole diagnostic report generator (asset read, two `M0Probe.loadMasterList` calls, timing, heap measurement, log formatting) in one function; no test exists.

### MainApplication.kt (Android-bound, trivial) — 27 lines total
- `MainApplication.onCreate` — `:23` — ~4 lines — registers `BouncyCastleProvider` at priority 1. Single statement.

### MasterlistVerifier.kt (mixed: crypto verification, pure-Java BC dependency, real in JVM tests) — 251 lines total
- `MasterlistVerifier.load` — `:82` — ~105 lines — the file's largest function: sequential CMS parse/cert-store/signer-lookup/signature/chain/validity/eContent-parse/consistency checks, each returning its own `Failure` reason.
- `MasterlistVerifier.cnOf` / `unescapeDn` — `:188`/`:198` — ~9/~7 lines — pure DN parsing helpers.
- `MasterlistVerifier.parseCert` — `:208` — ~8 lines — pure-ish BC wrapper.
- `MasterlistVerifier.parseCscaMasterList` — `:217` — ~35 lines — ASN.1 structural parse, exercised in `MasterlistVerifierTest` against the real bundled masterlist.

### MintConfirmation.kt (pure, fully assertable) — 24 lines total
- `MintConfirmation.confirmsSuccess` — `:23` — 1 line — one-line pure function.

### MintGate.kt (pure, fully assertable) — 22 lines total
- `MintGate.mayMint` — `:20` — ~2 lines — one-line pure function.

### MrzChangeTracker.kt (pure, fully assertable — new/uncommitted, D56) — 90 lines total
- `MrzChangeTracker.hash` — `:55` — ~15 lines — pure (real `MessageDigest`).
- `MrzChangeTracker.compare` — `:71` — ~10 lines — pure decision.
- `MrzChangeTracker.logLine` — `:82` — ~9 lines — pure formatting.

### PaneVisibility.kt (pure, fully assertable — new/uncommitted, D55) — 55 lines total
- `PaneVisibility.choosePane` — `:50` — ~6 lines — one-line-body pure decision function.

### QrCapture.kt (Android-bound) — 33 lines total
- `QrCapture.decode` — `:20` — ~14 lines — single purpose (zxing decode over a `Bitmap`). Android-bound, no direct unit test.

### ReportLog.kt (mixed — the accumulator class; internal state self-owned, see (a) row 14) — 356 lines total
- `ReportLog.append` — `:198` — ~18 lines — renders one entry AND manages pending/terminal replace-vs-append bookkeeping (intentionally coupled, D46).
- `ReportLog.clear` — `:217` — ~7 lines — single purpose, dead in production (appendix A4).
- `ReportLog.entriesSnapshot` / `restore` — `:225`/`:234` — ~8/~31 lines — single-purpose accessors.
- `ReportLog.rendered` — `:266` — ~91 lines (includes the companion object's `titleLineRanges`/`renderEntry`, which follow it in the file) — reorders for display AND conditionally applies `SpannableStringBuilder` styling; the styling half is untestable under this module's config (see (d)).
- `ReportLog.titleLineRanges` (companion) — `:291` (inside the above span) — pure range computation, exposed specifically to keep `rendered`'s core testable.
- `DisclosureSummary` / `Claim` / `Shared` (sealed) — `:114` (~48 lines for the whole nested-type block) — pure data shapes.

### RequestTrust.kt (pure — no Android dependency anywhere; fully assertable, and IS fully tested) — 378 lines total
- `RequestTrust.originOf` — `:86` — ~12 lines — pure.
- `RequestTrust.defaultPortFor` — `:98` — ~8 lines — pure.
- `RequestTrust.resolveVerifierKey` — `:124` — ~21 lines — pure decision + dispatch (dev-key vs. well-known fetch).
- `RequestTrust.fetchWellKnownKey` — `:145` — ~32 lines — network I/O, untested (no live well-known server in this suite).
- `RequestTrust.parseEcJwk` / `buildEcP256PublicKey` — `:177`/`:193` — ~16/~27 lines — pure JCA construction, real in JVM tests.
- `RequestTrust.verifyRequestObject` — `:228` — ~30 lines — sequential compact-JWS/`alg`/`kid`/signature checks. Fully tested.
- `RequestTrust.resolveVerifierByAttempt` — `:258` — ~24 lines — pure-ish provider walk.
- `RequestTrust.rawToDer` / `derInteger` / `derLength` — `:282`/`:288`/`:297` — ~6/~9/~20 lines — pure ASN.1 encoding, fully tested.
- `RequestTrust.tierOf` / `expiresAtOf` / `isExpired` / `describeEvidenceRequired` — `:317`/`:330`/`:344`/`:357` — ~13/~14/~13/~15 lines — pure parsing/decision functions, each fully tested.

### DeviceKey.kt (mixed — Keystore/JCA-bound; see (a) rows 13/16 for its two module-level mutable fields) — 639 lines total
- `DeviceKey.aliasForOriginAndZktag` — `:150` — ~25 lines — pure. Fully tested (`DeviceKeyAliasTest`).
- `DeviceKey.deleteAlias` / `kpgAlgorithmFor` / `specBuilder` — `:229`/`:237`/`:243` — ~8/~6/~24 lines — Keystore-bound helpers.
- `DeviceKey.securityFacts` / `authModeLabel` — `:267`/`:284` — ~17/~16 lines — Keystore-bound read-back assertions.
- `DeviceKey.tryGenerate` — `:300` — ~41 lines — 2 distinct exception-normalization branches (documented silent-null-substitution bug family).
- `DeviceKey.resolveByAttempt` — `:341` — ~20 lines — provider-walk (the F1 SpongyCastle-shadowing fix).
- `DeviceKey.sigAlgForRow` — `:361` — ~20 lines — pure lookup.
- `DeviceKey.ensureKey` — `:381` — ~57 lines — the file's largest function: alias bookkeeping, Keystore existence check, reuse-path short-circuit, full matrix probe, winner selection, key generation.
- `DeviceKey.reuseState` — `:438` — ~25 lines.
- `DeviceKey.initSignature` — `:463` — ~8 lines.
- `DeviceKey.currentPublicKeyDer` — `:471` — ~15 lines.
- `DeviceKey.currentKeyDetails` — `:495` — ~69 lines — read-back assertion, untested directly (no `DeviceKeyDetailsTest`).
- `DeviceKey.exportDevAttesterPublicKeyIfPresent` — `:564` — ~36 lines — alias resolution + Keystore read + PEM formatting + 2 file writes, debug-only, untested.
- `DeviceKey.ensureSoftwareEd25519` / `signSoftware` — `:600`/`:632` — ~32/~8 lines — dead in production (row 16).

### MainActivity.kt — 1901 lines total (exact spans, closing-brace-matched)
- `onCreate` — `:236` — **124 lines** (`:236-359`, includes a nested 7-line `TabLayout` listener at `:266-273`).
- `refreshModeStatus` — `:371` — 12 lines (`:371-382`). Single decision + one write.
- (nested `TierOutcome` sealed class — `:383-393`ish, not a function.)
- `tierOutcomeFor` — `:395` — 9 lines (`:395-403`). Pure decision, private-scoped.
- `lockModeAndArm` — `:411` — **92 lines** (`:411-502`). Does many things: reads 3 EditTexts, validates non-empty, reads `pendingHandoff`/`verifiedRequest`, checks expiry, computes `TierOutcome`, emits reports on 2 refusal branches, shows blocking dialogs, writes `lockedMode`, `lockButton.isEnabled`, `modeStatusView.text`, arms NFC.
- `armNfcDispatch` — `:504` — 6 lines.
- `onResume`/`onPause`/`onStop`/`onSaveInstanceState`/`onNewIntent` — `:511`/`:516`/`:522`/`:530`/`:536` — 4/5/7/5/5 lines. `onStop` is a one-line delegation to `wipeSession`.
- `handleIncomingIntent` — `:542` — 44 lines (`:542-585`). Dispatches on intent type, reads 3 EditText fields, computes an MRZ hash + logs a diagnostic, constructs a `BACKeySpec`, starts a session.
- `applyPendingHandoffText` — `:587` — 9 lines.
- `beginHandoffVerification` — `:604` — 17 lines. Writes 3 fields, starts a background verification `Thread`.
- `verifyPendingHandoff` — `:628` — 39 lines. Sequential `RequestTrust` orchestration, runs on a background thread, never touches a View.
- `applyHandoffVerificationOutcome` — `:671` — 46 lines. Staleness check + outcome dispatch + 4 field/view writes + `emitReport`/dialog.
- `wipeSession` — `:729` — 22 lines. Single well-scoped purpose.
- `emitReport` — `:768` — 15 lines. Single purpose by design (the documented "ONE place" — contradicted at `:288`, see (a) row 7).
- `logTitleSizePx` — `:797` — 4 lines.
- `siteTitleFor` — `:812` — 6 lines. Pure-ish (`java.net.URI`, real Java), private-scoped, no direct test.
- `chipAuthLabel` — `:838` — 11 lines. Pure mapping, private-scoped, no direct test.
- `showBlockingOutcomeDialog` — `:875` — 15 lines.
- `showPane` — `:911` — 6 lines. Single purpose by design.
- `startSession` — `:919` — 5 lines.
- `ReadTask` (inner `AsyncTask`, `:925-1126`) — **202 lines total**, containing:
  - `doInBackground` — `:940` — **~120 lines** (`:940-1059`). The entire chip-read pipeline in one function: PACE/BAC, DG1/SOD read, chip-auth (CA+AA) probe, masterlist load, passive auth.
  - `passiveAuthAgainst` — `:1060` — ~9 lines.
  - `onPostExecute` — `:1069` — **57 lines** (`:1069-1125`). Classification, report emission, dialog display, pipeline hand-off.
- `continueAfterRead` — `:1133` — **239 lines** (`:1133-1371`). The file's second-largest function: builds a technical report string, computes the mint gate, handles 3 refusal branches (gate not met, no origin, session expired), derives site/attemptId, emits a progress report, launches a background `Thread` (zktag derivation, key aliasing, key generation), hands off to `promptAndMint`.
- `promptAndMint` — `:1373` — 66 lines (`:1373-1438`, includes the nested `BiometricPrompt.AuthenticationCallback`).
- `mintAndMaybeHandoff` — `:1463` — **281 lines** (`:1463-1743`). **The file's largest single function**, correcting revision 1's uncited "~165 lines" estimate: re-validates handoff, builds the claim, derives the message, signs it, reads back key details, builds the report string, performs `direct_post` I/O, classifies the delivery result into 4 outcomes, clears `pendingHandoff`/`verifiedRequest`, builds the `DisclosureSummary`, emits the report, conditionally shows the confirmation dialog.
- (nested `DeliveryResult` sealed class — `:1749-1754`, 6 lines, not a function.)
- `diagnosticSummary` — `:1764` — ~7 lines.
- `runMasterlistProbe` — `:1771` — 29 lines (`:1771-1799`).
- `runDeviceKeyProbe` — `:1807` — 29 lines (`:1807-1835`).
- `convertDate` / `loadDate` — `:1837`/`:1846` — 8/11 lines.
- companion object (constants + `mrzHashSalt`) — `:1858`ish–`1900` — ~43 lines.

### RegularActivity.kt (NEW this revision) — 3 lines total
- `class RegularActivity : MainActivity()` — `:3` — the entire file. No overrides, no members. Exists solely so the framework has a concrete class to instantiate (`MainActivity` itself is `abstract`).

### src/regular/AndroidManifest.xml (NEW this revision) — 35 lines total
- Declares the app's **only** `<activity>` element anywhere in the module (`app/src/main/AndroidManifest.xml` has none — verified, appendix A13, unchanged from revision 1) — `.RegularActivity`, `screenOrientation="fullSensor"`, `launchMode="singleTop"`, `exported="true"`, with the `MAIN`/`LAUNCHER` and `VIEW`/`BROWSABLE av://authorize` intent filters (full content quoted in (a2) F8). Not code; a resource/manifest file, no functions to inventory.

### src/debug/res/xml/network_security_config.xml (NEW this revision) — 21 lines total
- Debug-build-only override of `app/src/main/res/xml/network_security_config.xml`: one cleartext exception scoped to `10.0.2.2`/`localhost`/`127.0.0.1`. Matches its own doc comment and `app/src/main`'s doc comment about it exactly — no discrepancy found. Not code; a resource file, no functions to inventory.

---

## State join — which functions read/write (a)'s fields

**New this revision.** For every function in `MainActivity.kt` that touches
at least one field from the (a) table: which it reads, which it writes, and
**on which thread** (new this revision — MAIN, BACKGROUND, or POSTED,
determined by reading the enclosing block against appendix A12's `Thread{}`/
`runOnUiThread{}` boundaries, not by a line's lexical proximity to the
function name it happens to fall under). Generated by re-`grep`-ing each
(a) field's name inside each function's line range (appendix A18), then
hand-checking every hit against the source line, because the raw regex
alone produces at least one false positive: a `Log.i` string literal at
`handleIncomingIntent:547` contains the substring `"pendingHandoff"` inside
a message string ("`M2 stage: pendingHandoff captured from av:// intent`")
— a naive grep would count this as `handleIncomingIntent` reading the
`pendingHandoff` field. It does not; the function neither reads nor writes
`pendingHandoff` or `verifiedRequest` anywhere in its body. This correction
changes the result below.

**MAIN** = executes as part of a direct synchronous call chain rooted at a
lifecycle callback, click/tab listener, or `AsyncTask.onPostExecute`
(always main-thread-delivered by the framework) or a `BiometricPrompt`
callback (delivered via `ContextCompat.getMainExecutor`, also the main
thread) — none of these involve the app's own `runOnUiThread`. **BACKGROUND**
= executes inside a `Thread{}` body (or `AsyncTask.doInBackground`) before
any `runOnUiThread` hand-off — this includes code that is lexically part of
a function's own body but textually AFTER that function opens its own
nested `Thread{}`, which is exactly the case `continueAfterRead` and
`mintAndMaybeHandoff` present below. **POSTED** = executes inside an
explicit `runOnUiThread{}` block — main thread by the time it runs, but
reached via an app-initiated hop from a background chain, not directly from
a framework root.

| Function (or function@line-range where a function spans threads) | Reads | Writes | Thread |
|---|---|---|---|
| `onCreate` | — | `lastReportText` (`:287`), `reportView.text` (`:288`), `logView.text` (`:295`) | MAIN |
| `refreshModeStatus` | `pendingHandoff` (`:379`), `verifiedRequest` (`:372`) | `modeStatusView.text` (`:373`) | MAIN when called from `onCreate:357`, `beginHandoffVerification:608`, or `wipeSession:736`; POSTED when called from `applyHandoffVerificationOutcome:688` or `mintAndMaybeHandoff:1639` — depends on caller, never BACKGROUND directly |
| `lockModeAndArm` | `lockedMode` (`:412`), `pendingHandoff` (`:421`), `verifiedRequest` (`:424`) | `lockedMode` (`:498`), `modeStatusView.text` (`:500`), `lockButton.isEnabled` (`:499`) | MAIN — `lockButton`'s click-listener body; launches no thread of its own |
| `onResume` | `lockedMode` (`:513`) | — | MAIN — lifecycle callback |
| `onSaveInstanceState` | `lastReportText` (`:532`), `ReportLog.entries` (via `entriesSnapshot()`, `:533`, cross-class read) | — | MAIN — lifecycle callback |
| `handleIncomingIntent` | `lockedMode` (`:555`), `lastMrzHash` (`:578`) — **does NOT read or write `pendingHandoff`/`verifiedRequest`** (see correction note above) | `lastMrzHash` (`:580`) | MAIN — called from `onNewIntent`/`onCreate`, both lifecycle callbacks; launches no thread of its own |
| `beginHandoffVerification` | — | `pendingHandoff` (`:605`), `verifiedRequest` (`:606`, nulled), `handoffStatus.text` (`:607`), `lockButton.isEnabled` (`:609`) | MAIN — all four writes happen before the function opens its own `Thread{}` at `:611` |
| `applyHandoffVerificationOutcome` | `pendingHandoff` (`:672`, staleness check) | `verifiedRequest` (`:678`), `handoffStatus.text` (`:680`, `:708`), `lockButton.isEnabled` (`:689`) | POSTED — invoked via `runOnUiThread { applyHandoffVerificationOutcome(...) }` at `:618`, itself inside the `:611` `Thread{}` opened by `beginHandoffVerification` |
| `wipeSession` | — | `lockedMode` (`:734`), `lastMrzHash` (`:740`), `lockButton.isEnabled` (`:735`) | MAIN in every call context: `onStop:524` (lifecycle), `showBlockingOutcomeDialog`'s `AlertDialog` button callback `:886` (Android dialog callbacks are always main-thread), `ReadTask.onPostExecute:1123` (`AsyncTask` callback, always main-thread) |
| `emitReport` | — | `lastReportText` (`:770`), `reportView.text` (`:769`), `logView.text` (`:780`), `ReportLog.entries` (via `reportLog.append`, `:779`, cross-class write) | MAIN or POSTED depending on caller (see call graph) — **never BACKGROUND**: verified by reading both nested `Thread{}` bodies (`:611`, `:1262`) and `mintAndMaybeHandoff`'s pre-first-`runOnUiThread` prologue (`:1463-1473`) directly; none contains a bare `emitReport` call before its first `runOnUiThread` wrap. `ReportLog.entries` is therefore never mutated from two threads truly concurrently — the a2 F5 hazard for this field is a lifecycle-timing hazard (a POSTED block landing after `onStop`), not a data race |
| `showBlockingOutcomeDialog` | — | `pendingHandoff` (`:883`), `verifiedRequest` (`:884`) — both conditional on `!keepMrzAndMode`, see the Guards table below | MAIN — the `.setPositiveButton { dialog, _ -> ... }` callback is an `AlertDialog` UI event, always delivered on the main thread regardless of what thread called `.show()` (which itself must already be main-thread, since `AlertDialog` cannot be built or shown off it) |
| `showPane` | `readInProgress` (`:912`), `TabLayout.selectedTabPosition` (`:912`, cross-class/framework read) | `mainLayout`/`logLayout`/`loadingLayout` `.visibility` (`:913-915`) | MAIN — every call site (`onCreate:301`, the tab-listener overrides, `startSession:921`, `ReadTask.onPostExecute:1076`) is itself MAIN |
| `startSession` | — | `readInProgress` (`:920`) | MAIN — called from `handleIncomingIntent`, itself MAIN |
| `ReadTask.onPostExecute` | `verifiedRequest` (`:1103`) | `readInProgress` (`:1075`) | MAIN — `AsyncTask.onPostExecute` is always main-thread-delivered by the framework |
| `continueAfterRead` reads BEFORE `:1262` | `verifiedRequest` (`:1167`, `:1187`, `:1215`) | — | MAIN — `continueAfterRead` is called from `ReadTask.onPostExecute:1124` (MAIN) and these three reads all occur before the function opens its own nested `Thread{}` at `:1262` |
| `continueAfterRead` reads AT/AFTER `:1262` (**corrected this revision**) | `pendingHandoff` (`:1281`), `verifiedRequest` (`:1282`) | — | **BACKGROUND** — both lines are textually inside `continueAfterRead`'s own body, but they execute **inside** the `Thread{}` block that function itself opens at `:1262`, not on the MAIN thread that reached `continueAfterRead` in the first place. Corrected finding, replacing "no staleness guard on the mint-path reads" (revisions 2-3): **this is a cross-thread read of a field declared without `@Volatile`** (`pendingHandoff` at `:172`, `verifiedRequest` at `:177`; confirmed none of the five session fields at `:169-202` carries it, unlike `DeviceKey.lastMintAlias`'s `@Volatile` at `DeviceKey.kt:162`) **with no staleness guard** — verified by reading the enclosing block, not by proximity |
| `mintAndMaybeHandoff` reads at `:1470-1471` | `pendingHandoff` (`:1470`), `verifiedRequest` (`:1471`) | — | BACKGROUND — `mintAndMaybeHandoff` itself is launched via `Thread { mintAndMaybeHandoff(...) }.start()` at `:1414` (inside `promptAndMint`'s `onAuthenticationSucceeded` callback); these two reads occur before the function's first `runOnUiThread` block (`:1474`) |
| `mintAndMaybeHandoff` writes at `:1637-1638` | — | `pendingHandoff` (`:1637`), `verifiedRequest` (`:1638`) | POSTED — both lines are inside the `runOnUiThread { ... }` block opened at `:1636` |

Every other function in `MainActivity.kt`'s (b) inventory — `tierOutcomeFor`,
`armNfcDispatch`, `onPause`, `onNewIntent`, `applyPendingHandoffText`,
`verifyPendingHandoff`, `logTitleSizePx`, `siteTitleFor`, `chipAuthLabel`,
`ReadTask.doInBackground`, `passiveAuthAgainst`, `promptAndMint`, the three
`BiometricPrompt` callback overrides, `diagnosticSummary`,
`runMasterlistProbe`, `runDeviceKeyProbe`, `convertDate`, `loadDate` —
touches none of (a)'s fields directly, by the same grep-then-hand-check
method.

`DeviceKey.lastMintAlias`, `DeviceKey.softwareEd25519Store`, and
`ReportLog.entries`/`pendingIndexByAttempt` are written only from inside
their own owning class (`DeviceKey.ensureKey`/`.ensureSoftwareEd25519`;
`ReportLog.append`/`.clear`/`.restore`) — no `MainActivity` function reaches
into them directly; `MainActivity` only calls the owning class's public
entry points, consistent with (a) rows 13/14/16's "self-contained" finding.

**The specific prediction to check: does the join confirm
`pendingHandoff`/`verifiedRequest` are touched by `handleIncomingIntent`,
`beginHandoffVerification`, `applyHandoffVerificationOutcome`,
`showBlockingOutcomeDialog`, AND `mintAndMaybeHandoff`?**

- Confirmed for four of the five: `beginHandoffVerification`,
  `applyHandoffVerificationOutcome`, `showBlockingOutcomeDialog`,
  `mintAndMaybeHandoff` all touch at least one of the two fields, per the
  table above.
- **Refuted for `handleIncomingIntent`**: its only apparent match was the
  `:547` string-literal false positive described above. The function
  itself neither reads nor writes `pendingHandoff` or `verifiedRequest`.
- The join additionally found the prediction's list incomplete in the
  other direction: `refreshModeStatus`, `lockModeAndArm`,
  `ReadTask.onPostExecute`, and `continueAfterRead` also touch one or both
  fields, and were not named in the prediction.

---

## Guards — multi-field decisions

**New this revision.** Every place I found a decision gated on more than
one condition, where at least one of the conditions is an (a)-table field:
the decision, what the guard's condition actually reads, and what else
bears on the same real-world decision but is not part of the guard's
condition. Facts only — what is read, what is not; no judgment on whether
the omission is a bug.

| # | Decision | Guard reads | Also bears on this decision, not read by the guard |
|---|---|---|---|
| 1 | `handleIncomingIntent`'s tag-intent entry guard — whether to start a chip read (`MainActivity.kt:539` dispatch into the `ACTION_TECH_DISCOVERED` branch, `:555` and `:561-563` the two checks inside it) | `lockedMode != null` (`:555`); MRZ fields non-empty — `passportRaw`/`expirationRaw`/`birthRaw` (`:561-563`) | `readInProgress` (written `:920`/`:1075`) — not read anywhere in this guard. This is a2 F4's "second tap mid-`IsoDep`-session" open question restated as a property of this specific guard: nothing here distinguishes "no read has started yet" from "a read is already in flight." |
| 2 | `lockModeAndArm`'s entry guard — whether to lock the mode and arm NFC dispatch (`:412-424`) | `lockedMode != null` (`:412`, early return); MRZ fields non-empty (`:413-416`); `pendingHandoff` (`:421`, `if (handoff != null)`); `verifiedRequest` (`:424`, only reached when `pendingHandoff != null`) | `readInProgress` — not read. `lastMrzHash` — not read (this guard only reads the live `EditText` contents, not the previous-attempt hash `handleIncomingIntent` separately maintains). |
| 3 | The mint gate itself — `MintGate.mayMint(mode == PresentationMode.B, verdict)` call site (`continueAfterRead:1162`) | `mode` (a local parameter, derived from `lockedMode` earlier in the pipeline, not re-read as a field here); `verdict` (a `ReadTask`-instance-local value, not one of (a)'s app-level fields) | No (a)-table field is read directly at this call site. |
| 4 | The D38 origin guard immediately after the mint gate — whether a mode-B mint has a scoping origin (`continueAfterRead:1186-1187`) | `verifiedRequest` (via `verifiedRequest?.origin`) | `pendingHandoff` — not read here, despite `pendingHandoff != null` being exactly what guard #2 (`lockModeAndArm:421`) uses elsewhere in the same file as its test for "a handoff is active." |
| 5 | The belt-and-suspenders session-expiry re-check before minting (`continueAfterRead:1215-1218`) | `verifiedRequest` (as `verifiedForExpiry`) | `pendingHandoff` — not read here either. |
| 6 | `showBlockingOutcomeDialog`'s dismissal handler — whether to clear session state (`:876-885`) | `isAccessEstablishmentFailure`, `isTransientChipCommunicationFailure` (both function parameters, not (a)-table fields; combined via `FailureTransition.keepsMrzAndMode`) | `pendingHandoff`, `verifiedRequest`, `lockedMode`, `lastMrzHash`, `readInProgress` — none of these five fields is read by this guard's condition, yet the first two are unconditionally written (`:883-884`) and the rest are written inside the `wipeSession` call (`:886`) that follows, all gated on the same two boolean parameters alone. **Ownership finding promoted to (a) rows 2/3 this revision** — this row is kept as the mechanism reference (what the guard's own condition reads); the ownership fact (this function is a third distinct writer of `pendingHandoff`/`verifiedRequest`, and the mechanical asymmetry that it is the only guard in this table reading zero (a) fields while writing five) now lives there. |

---

## (d) Assertability map

*(Unchanged in substance from revision 1 — no citation in this section
pointed at a broken appendix row, so nothing here needed repair. Reproduced
verbatim below for a single-document read; see revision 1 if you need a
byte-diff.)*

| Invariant | Currently tested? | Assertable under `isReturnDefaultValues=true`? | If not, what would make it assertable |
|---|---|---|---|
| Single-writer `.visibility` for the 3 panes (D55) | YES, indirectly — `PaneVisibilityTest` pins `PaneVisibility.choosePane`'s truth table exhaustively (5 cases). The "MainActivity applies it to exactly 3 views, always all 3" half is NOT independently tested (View stubs) — verified by inspection (single call site, `MainActivity.kt:913-915`), not by a test. | The DECISION, yes. The APPLICATION to real views, no. | Already at its ceiling; the application step would need an instrumented/Robolectric run to assert further. |
| `TabLayout.selectedTabPosition` read-before-restore ordering bug (a2 F1) | NO | NO — `TabLayout` is a stub under this config; the very API surface being raced (`onPostCreate` timing vs `onCreate`) is not exercisable without instrumentation. | Not fixable by unit-testing alone; needs either an instrumented/Espresso recreate-and-assert test, or a structural move to stop relying on `TabLayout`'s own restore (persist the tab index the same way `STATE_LAST_REPORT`/`STATE_LOG_ENTRIES` already are, which per F2 IS correctly ordered). A (c)-shaped decision. |
| Value-free logging invariant (no MRZ/DG1/zktag in any log or report) | Partially — `MrzChangeTrackerTest` and `ReportLogTest` pin specific cases; no general/property test exists across all 40+ `emitReport` call sites. | The specific cases: yes. The general invariant: no — both parameters are plain `String`, no type-level distinction. | A `value-free` wrapper type constructible only from an enumerated set of known-safe primitives would make this structural rather than disciplinary. |
| MRZ wiped on `onStop` (item 6/F1 chip-related, not this report's F1) | NOT directly — `wipeSession`/`onStop` are Android-bound throughout. | NO as currently structured. | The DECISION half (which bucket keeps state) is already extracted and tested via `FailureTransitionTest`; only the APPLICATION half (`wipeSession` touching 6 real fields, `MainActivity.kt:732-741`) remains unassertable. |
| Mint gate — mode A / masterlist-no never mints (item 3) | YES, fully — `MintGateTest`, 5 cases. | YES — already pure. | N/A — ceiling. |
| Failure classification — transient vs. access-establishment precedence | YES, extensively — `FailureTransitionTest`, 20 cases. | YES — already pure. | N/A — the file's best-covered invariant. |
| Mint-confirmation dialog fires ONLY on `DeliveryResult.Accepted` | YES — `MintConfirmationTest`, 2 cases (pure boolean-in/out). | YES for the boolean mapping. The `DeliveryResult` (4-case sealed class) → `Boolean` reduction itself happens at the call site (`mintAndMaybeHandoff:1739`), not inside a tested function. | A pure function taking `DeliveryResult` directly would close this gap. Verified by inspection of the 4 `when` branches (`:1533-1559`-ish) that the reduction is correct today. |
| Session expiry (`expires_at`/`isExpired`) | YES for the pure boundary logic — `RequestTrustTest`, exact-boundary case included. NOT tested for the two Android-bound call sites that use it. | The boundary math: yes. The "checked in exactly these 2 places, same precedence" claim: no. | Both call sites already delegate to the same pure `RequestTrust.isExpired(expiresAt, System.currentTimeMillis())` — verified by inspection, not by test. |
| Per-(origin,zktag) key isolation (D38/D39) | YES for the alias derivation — `DeviceKeyAliasTest`, 7 cases. NOT tested that `MainActivity` always derives the zktag BEFORE `ensureKey`. | The alias function: yes. The call-ORDER requirement: no — nothing but code review enforces it. | A function signature accepting only a post-derivation-typed `zktag` would make a violation a compile error. A (c)-shaped move. |
| `sig-ed25519/1` vs `sig-p256/1` byte-layout correctness | YES, exhaustively, against independently-produced chiproof vectors — `EvidenceSignerTest`. | YES — already pure. | N/A — ceiling, and has caught a real bug once (per the file's own doc). |
| Masterlist CMS integrity (two-bucket rule) | YES for the CMS-signature/chain half, against the real bundled masterlist — `MasterlistVerifierTest`, 4 cases. NOT tested for the "well-formed but lacking the issuer" branch (needs a real document's signer cert). | The tested half: yes. The untested half: a data-availability limitation, not a stub limitation. | Would need a captured, gitignored real-document fixture; otherwise device-only. |
| Chip-authenticity 3-state distinction (VERIFIED/NOT_SUPPORTED/FAILED) | NOT unit-tested for the probe itself (no `M0ProbeTest.kt`; the DG14/CA half is inline in `ReadTask.doInBackground`, Android/JMRTD-bound). `ReportLogTest` tests only the RENDERING of each state. | The rendering half: yes. The probing half: no — device-only, a long-standing stated project limitation. | Would need a mocked/fake `PassportService` at minimum; out of this audit's scope. |
| Log-title span styling (+1sp) | NO, explicitly documented as impossible (`ReportLog.kt:262-265`; the project's own stash notes a probe test that was deliberately removed for passing on a stub for the wrong reason). | NO. The pure core (`titleLineRanges`) IS tested. | N/A — already at the documented ceiling. |
| `verifyRequestObject`'s "never throws" contract | YES, well — `RequestTrustTest` includes malformed-base64, non-compact-JWS, and other structurally-broken inputs. | YES — already pure, no Android dependency anywhere in `RequestTrust.kt`. | N/A — ceiling, alongside `FailureTransition` the file's other best-covered invariant. |

---

## (e1) What I could not verify mechanically

- **F3 (Fragment/`DatePickerDialog` restoration timing).** Identified the
  two `add(dialog, null).commit()` sites (`MainActivity.kt:341,352`); did
  not trace `FragmentManager`'s own state-restore timing the way I did for
  F1. Would need the same framework-source-level check, not done this pass.
- **F5's "has it manifested?" half.** F5's mechanical half — zero
  cancellation discipline, five unfenced async writer chains (three
  crypto-path, two release-reachable report-path) — is now CONFIRMED
  (see a2). Whether it has actually produced an observed
  crash/leak/stale-write on a real device is NOT something I found evidence
  for either way; nothing in the stash file's "known limitation"/"open/next"
  sections describes this specific shape.
- **Whether a second NFC tap can re-enter `handleIncomingIntent` while a
  `ReadTask` from a first tap is still running (a2 F4).** Confirmed the
  guard (`:563-566`) does not check "read already in flight"; did not
  verify whether `NfcAdapter`'s own foreground-dispatch mechanism could
  even physically deliver a second tag intent mid-`IsoDep`-session.
- ~~**Whether `RegularActivity.kt` or its manifest add any additional
  writers.**~~ **Resolved this revision** — see (a2) F8, (b)'s new
  `RegularActivity`/manifest entries, and the call-graph section. No
  additional writers exist; `RegularActivity` adds nothing but a concrete
  class name and the `screenOrientation="fullSensor"` fact that sharpens
  F1's real-world frequency.

## (e2) What I deliberately did not conclude

- **Whether F5's confirmed mechanical gap (zero async-cancellation
  discipline, five unfenced writer chains) is a live bug or merely
  theoretical.** The mechanism is now established fact, not inference. I am
  not concluding it has actually fired — unlike F1, which the brief itself
  confirmed as an observed real-device bug, F5 has no confirmed symptom in
  anything I read. Whether it is worth fixing, and how urgently relative to
  F1, is an owner/second-pass call.
- **Whether rows 2/3 (`verifiedRequest`/`pendingHandoff`) deserve to rank
  above row 1.** They carry the same HIGH consequence class (a wrong-target
  network side effect) and share F5's confirmed unfenced-writer mechanism.
  They rank below row 1 only because no symptom has been observed for them,
  which is a real difference in confidence, not a claim that the underlying
  risk is smaller. A device run targeting this specific race (two handoffs
  in flight at once) would settle it either way.
- **Whether the `reportView.text`/`lastReportText` two-writer pattern (rows
  7/8) is a bug or acceptable.** The doc-vs-code mismatch is real
  (`:753-758`'s "ONE place" claim vs. `:288`'s direct write); whether the
  doc should be corrected or the restore path should route through
  `emitReport` too is a design call, not a fact this pass settles.
- **OPEN QUESTION, not a finding — the MRZ `EditText`s vs. `lockedMode`/
  `lastMrzHash` after Activity recreation.** The three MRZ `EditText`s
  (`passportNumberView`, `expirationDateView`, `birthDateView` —
  `TextInputEditText`, defaulting `android:freezesText="true"` per the
  `EditText` widget style) come back populated after a rotation —
  FRAMEWORK-RESTORED, the same mechanism as row 1's `TabLayout`. `lockedMode`
  (row 4) and `lastMrzHash` (row 12) are both LOST — plain `MainActivity`
  fields, reset to `null`. `lockModeAndArm`'s entry guard (guards table row
  2) reads the live `EditText` contents directly (`:413-415`), not
  `lastMrzHash` — so a repopulated-but-never-hashed MRZ value is exactly
  what that guard sees on a fresh, post-recreation attempt, and
  `MrzChangeTracker.compare`'s `previousHash == null` branch (`FirstAttempt`)
  is what fires, correctly by that function's own contract (no prior hash
  exists in the new instance — see (d)). What I am NOT concluding: whether
  this produces any user-visible or security-relevant misbehavior. Stating
  only what is read (the live `EditText`s) and what is lost (`lockedMode`,
  `lastMrzHash`) — not whether the gap between them matters.
- **`M0Probe.kt` and `M2MasterlistProbe.kt` shipping in `src/main` despite
  being self-documented as "THROWAWAY... never shipped."** Both compile
  into the production source set with no debug-only gate on their UI
  buttons (`MainActivity.kt:304-311`, contrast the gated `DeviceKey` probe
  long-press at `:320`). `M0Probe`'s types are now load-bearing production
  dependencies (`MintGate`, `ReportLog`) — "throwaway" may be a stale label
  rather than a live violation. Not concluding either way.
- **Whether `DeviceKey.softwareEd25519Store`'s missing `@Volatile` (vs.
  `lastMintAlias`'s `@Volatile`) matters.** It is dead in production today
  (row 16), so the asymmetry has no live consequence; not concluding it
  needs fixing.
- **Deliverable (c) itself.** Still not proposed, still not privately
  opined on — handed off as-is for a second pass.

---

## Appendix: the raw search (re-runnable)

All commands below were re-run from the repository root
(`/home/hamr/PycharmProjects/zkagent`) against the pinned tree state at the
top of this report, for THIS revision — every block below is the actual,
current output, not a description of what a prior run showed.

### A1 — `lockedMode`/`pendingHandoff`/`verifiedRequest`/`lastReportText`/`readInProgress`/`lastMrzHash` in MainActivity.kt (full, unedited, backs (a) rows 2, 3, 4, 8, 11, 12)

```
$ for f in lockedMode pendingHandoff verifiedRequest lastReportText readInProgress lastMrzHash; do
    echo "--- $f ---"
    grep -n "\b$f\b" apps/scanner/app/src/main/java/com/tananaev/passportreader/MainActivity.kt
  done
```
```
--- lockedMode ---
87: * any more — [lockedMode] is DERIVED, never read from a UI control: a
92: * that writes [lockedMode], but it has nothing left to READ from a
107: * strictly AFTER, and ONLY when [lockedMode] is B AND `passiveAuth.ok &&
128: * delivers the tag). MRZ text AND [lockedMode] are KEPT only on an
142: * lets [lockModeAndArm] (item 4's ONE call site for [lockedMode]) derive and
143: * lock the mode from the request's `zkagent.tier` — [lockedMode] still has
169:    private var lockedMode: PresentationMode? = null
367:     * owner's own words). NEVER called while [lockedMode] is set — once
370:     * only once [lockedMode] is cleared. */
406:    /** The ONE call site in this file that writes [lockedMode]. §6.2 item 13
412:        if (lockedMode != null) return // already locked this session
498:        lockedMode = mode
500:        modeStatusView.text = "Locked: mode ${lockedMode} — tap your document now"
513:        if (lockedMode != null) armNfcDispatch() // e.g. returning from the biometric prompt / a backgrounding
555:                val mode = lockedMode
687:                // this into lockedMode.
718:    /** §6.2 item 6: MRZ + [lockedMode] are kept on an access-establishment
734:            lockedMode = null
--- pendingHandoff ---
141: * [pendingHandoff], for the rest of that handoff's lifetime. This is what
158: * signature, no resolvable key) is a refusal: [pendingHandoff] and
172:    private var pendingHandoff: HandoffClient.PendingHandoff? = null
176:    // cleared alongside [pendingHandoff] everywhere that is cleared.
379:            pendingHandoff != null -> "Mode: verifying the site's request…"
421:        val handoff = pendingHandoff
435:            // definitive mint outcome clears [pendingHandoff]/
547:                Log.i(TAG, "M2 stage: pendingHandoff captured from av:// intent")
605:        pendingHandoff = handoff
669:     * verification (a newer handoff replaced [pendingHandoff] before this
672:        if (pendingHandoff !== handoff) {
710:                // pendingHandoff/verifiedRequest, reverting the derived mode
726:     * correctly, since it re-derives from [pendingHandoff]/
865:     * reset (pendingHandoff/verifiedRequest, which [wipeSession] itself
883:                    pendingHandoff = null
1281:            val handoff = pendingHandoff
1303:            // re-parse of pendingHandoff.requestUri's host (items 13/14).
1444:     * [pendingHandoff] is queued — POSTs `direct_post`. zktag is never
1451:     * below is therefore wrapped in [runOnUiThread], and [pendingHandoff] is
1470:        val handoff = pendingHandoff
1625:        // cleared in lockstep — it has no meaning without a pendingHandoff.
1637:            pendingHandoff = null
--- verifiedRequest ---
140: * file used to. The verified result lives in [verifiedRequest], alongside
144: * exactly one writer, it is just fed from [verifiedRequest] when a handoff
155: * the preselect). [mintAndMaybeHandoff] later REUSES [verifiedRequest]
159: * [verifiedRequest] are cleared, the derived mode status reverts to its
177:    private var verifiedRequest: RequestTrust.VerifiedRequest? = null
372:        val verified = verifiedRequest
424:            val verified = verifiedRequest
436:            // [verifiedRequest] (see `mintAndMaybeHandoff`'s doc), so the
606:        verifiedRequest = null
678:                verifiedRequest = outcome.request
710:                // pendingHandoff/verifiedRequest, reverting the derived mode
727:     * [verifiedRequest], not from a separately-tracked enabled/disabled
865:     * reset (pendingHandoff/verifiedRequest, which [wipeSession] itself
884:                    verifiedRequest = null
1103:                        site = siteTitleFor(verifiedRequest?.origin),
1167:                    site = siteTitleFor(verifiedRequest?.origin),
1187:        val origin = verifiedRequest?.origin
1215:        val verifiedForExpiry = verifiedRequest
1282:            val verified = verifiedRequest
1284:                Log.e(TAG, "M2 stage: pending handoff / verifiedRequest disappeared before zktag derivation — refusing (D38)")
1471:        val verified = verifiedRequest
1473:                Log.e(TAG, "M2 stage: reached mint with no pending handoff / verifiedRequest — refusing to proceed (D38)")
1624:        // main thread (item 6 lifecycle discipline). verifiedRequest is
1638:            verifiedRequest = null
--- lastReportText ---
181:    private var lastReportText: String? = null
186:    // [lastReportText] is (D35), cleared inside [wipeSession]'s
287:            lastReportText = text
291:        // across-recreation retention as lastReportText above (D35) — never
532:        lastReportText?.let { outState.putString(STATE_LAST_REPORT, it) }
770:        lastReportText = text
--- readInProgress ---
195:    private var readInProgress: Boolean = false
896:     * [readInProgress] and/or the tab selection, then call this function —
912:        val pane = PaneVisibility.choosePane(readInProgress, tabLayout.selectedTabPosition)
920:        readInProgress = true
1073:            // without also clearing [readInProgress], because there is
1075:            readInProgress = false
--- lastMrzHash ---
202:    private var lastMrzHash: String? = null
578:                    MrzChangeTracker.compare(lastMrzHash, currentMrzHash, docLen = passportRaw.length, dobOk = true, expOk = true)
580:                lastMrzHash = currentMrzHash
740:            lastMrzHash = null
1890:        // D56: per-process salt for [lastMrzHash] — a companion-object
```

(Note: this confirms `lockedMode`'s real writer lines are `:498`/`:734` as
cited, and `pendingHandoff`/`verifiedRequest`'s are `:605`/`:606` — adjacent
lines in the capture block, not the same line as revision 1 mis-stated.)

### A2 — view-property writers (backs (a) rows 5, 6, 7, 8, 9, 10)

```
$ grep -n "reportView\.text\|logView\.text\|handoffStatus\.text\|lockButton\.isEnabled" apps/scanner/app/src/main/java/com/tananaev/passportreader/MainActivity.kt
```
```
288:            reportView.text = text
295:            logView.text = reportLog.rendered(titleSizePx = logTitleSizePx())
499:        lockButton.isEnabled = false
607:        handoffStatus.text = "Handoff request received — verifying signature and origin…"
609:        lockButton.isEnabled = false
680:                handoffStatus.text = "Handoff verified — origin: ${outcome.request.origin}, requested tier: ${rawTier ?: "<absent>"}. Fill in your document details and lock to answer it."
689:                lockButton.isEnabled = true
708:                handoffStatus.text = "Handoff refused (${outcome.reason}) — you may still scan manually."
735:            lockButton.isEnabled = true
758:     * with ZERO logcat trace) was exactly a `reportView.text = ...` site
769:        reportView.text = text
780:        logView.text = reportLog.rendered(titleSizePx = logTitleSizePx())
799:        return (logView.textSize + onePointInPx).roundToInt()
```
```
$ grep -n "modeStatusView\.text" apps/scanner/app/src/main/java/com/tananaev/passportreader/MainActivity.kt
```
```
373:        modeStatusView.text = when {
500:        modeStatusView.text = "Locked: mode ${lockedMode} — tap your document now"
```

### A3 — `.visibility` writers (backs (a) row 15)

```
$ grep -n "\.visibility" apps/scanner/app/src/main/java/com/tananaev/passportreader/MainActivity.kt
```
```
892:     * D55 — THE ONLY place in this file that writes `.visibility` on
897:     * never touch a view's `.visibility` directly.
913:        mainLayout.visibility = if (pane == PaneVisibility.Pane.SCAN) View.VISIBLE else View.GONE
914:        logLayout.visibility = if (pane == PaneVisibility.Pane.LOG) View.VISIBLE else View.GONE
915:        loadingLayout.visibility = if (pane == PaneVisibility.Pane.LOADING) View.VISIBLE else View.GONE
```
Confirms: only 3 non-comment hits, all inside `showPane()` (`:913-915`).

### A4 — `reportLog.` call sites in MainActivity.kt (backs (a) row 14 — corrected citation, was wrongly A7 in revision 1)

```
$ grep -n "reportLog\." apps/scanner/app/src/main/java/com/tananaev/passportreader/MainActivity.kt
```
```
294:            reportLog.restore(saved)
295:            logView.text = reportLog.rendered(titleSizePx = logTitleSizePx())
533:        outState.putStringArrayList(STATE_LOG_ENTRIES, ArrayList(reportLog.entriesSnapshot()))
779:        reportLog.append(text, summary, attemptId = attemptId, pending = pending)
780:        logView.text = reportLog.rendered(titleSizePx = logTitleSizePx())
```
Confirms `reportLog.clear()` has NO call site here — matches `ReportLog`'s
own doc claim.

### A5 — `DeviceKey.lastMintAlias` / `softwareEd25519Store` (backs (a) rows 13, 16)

```
$ grep -n "lastMintAlias\|softwareEd25519Store" apps/scanner/app/src/main/java/com/tananaev/passportreader/DeviceKey.kt
```
```
79:  * structurally impossible). [lastMintAlias] tracks the alias most recently
127:    private const val SOFTWARE_ED25519_ALIAS_TAG = "software" // not a Keystore alias — see softwareEd25519Store
163:    private var lastMintAlias: String? = null
375:     *   guess. [lastMintAlias] is updated here whenever `alias != PROBE_ALIAS`.
383:        if (alias != PROBE_ALIAS) lastMintAlias = alias
542:     * D38 item 4: which key? [lastMintAlias] — the alias most recently used
566:        val alias = lastMintAlias ?: PROBE_ALIAS
598:    private var softwareEd25519Store: java.security.KeyPair? = null
601:        val kp = softwareEd25519Store ?: run {
610:            kpg.generateKeyPair().also { softwareEd25519Store = it }
633:        val kp = softwareEd25519Store ?: return null
```

### A6 — (retired label from revision 1) — see A7 below

Revision 1's A6 was "`ReportLog.entries`/`pendingIndexByAttempt` internal
writers." That grep is folded into this revision's A4-adjacent material
above (row 14's claim is backed by A4, not a separate ReportLog-internal
grep) — kept only as a pointer so a reader of revision 1 can find where its
A6 content went. No new information; not re-run separately.

### A7 — `refreshModeStatus()` call sites (backs (a) row 10's resolution — corrected citation, was wrongly cited as A6 in revision 1's row 9)

```
$ grep -n "refreshModeStatus()" apps/scanner/app/src/main/java/com/tananaev/passportreader/MainActivity.kt
```
```
293:     * successful verify, so the DERIVED mode is visible (D33: the app "sets"
357:        refreshModeStatus()
371:    private fun refreshModeStatus() {
608:                refreshModeStatus()
688:                refreshModeStatus()
726:     * correctly, since it re-derives from [pendingHandoff]/
736:            refreshModeStatus()
1639:            refreshModeStatus()
```
Non-comment, non-definition call sites: **`357, 608, 688, 736, 1639`** —
these are the corrected numbers; revision 1 cited `358, 689, 737, 1730`,
which were stale. See (a)'s "Row 10 resolution" note for the state-at-each-
call-site check.

### A8 — `preferSoftwareUniformity` (backs (a) row 16 — re-run cleanly this revision, corrupted line removed)

```
$ grep -n "preferSoftwareUniformity" apps/scanner/app/src/main/java/com/tananaev/passportreader/DeviceKey.kt apps/scanner/app/src/main/java/com/tananaev/passportreader/MainActivity.kt apps/scanner/app/src/test/java/com/tananaev/passportreader/*.kt
```
```
apps/scanner/app/src/main/java/com/tananaev/passportreader/DeviceKey.kt:38: * explicit opt-in (`preferSoftwareUniformity=true`), for an adopter who
apps/scanner/app/src/main/java/com/tananaev/passportreader/DeviceKey.kt:376:     * @param preferSoftwareUniformity item 1's adopter-chosen trade: skip
apps/scanner/app/src/main/java/com/tananaev/passportreader/DeviceKey.kt:381:    fun ensureKey(context: Context, alias: String, preferSoftwareUniformity: Boolean = false): KeyState {
apps/scanner/app/src/main/java/com/tananaev/passportreader/DeviceKey.kt:382:        if (preferSoftwareUniformity) return ensureSoftwareEd25519()
```
`grep` cannot, by itself, prove "never called with `true`" — a caller that
simply omits the parameter (taking the `false` default) produces no text
match for `preferSoftwareUniformity` at the call site. That claim instead
rests on reading the two actual `DeviceKey.ensureKey(...)` call sites in
`MainActivity.kt` directly:

```
$ grep -n "DeviceKey.ensureKey" apps/scanner/app/src/main/java/com/tananaev/passportreader/MainActivity.kt
```
```
1350:                DeviceKey.ensureKey(applicationContext, alias)
1811:            val first = DeviceKey.ensureKey(applicationContext, DeviceKey.PROBE_ALIAS) // D38: self-test targets PROBE_ALIAS, never a per-origin mint alias
1820:            val second = DeviceKey.ensureKey(applicationContext, DeviceKey.PROBE_ALIAS)
```
Quoting the two call sites in full (`MainActivity.kt:1350`):
```kotlin
DeviceKey.ensureKey(applicationContext, alias)
```
and (`MainActivity.kt:1811`, `:1820` — same two-argument shape):
```kotlin
DeviceKey.ensureKey(applicationContext, DeviceKey.PROBE_ALIAS)
```
All three calls pass exactly two positional arguments (`context`, `alias`);
`preferSoftwareUniformity` is Kotlin's third parameter with a `false`
default (`DeviceKey.kt:381`) and is never supplied a third argument at any
of the three sites. The claim "never reached with `true` in this build"
rests on this reading, not on a grep match.

### A9 — `onPostCreate`/`onRestoreInstanceState` overrides (backs a2 F1)

```
$ grep -n "onPostCreate\|onStart\b\|onRestoreInstanceState\|savedInstanceState" apps/scanner/app/src/main/java/com/tananaev/passportreader/MainActivity.kt
```
```
236:    override fun onCreate(savedInstanceState: Bundle?) {
237:        super.onCreate(savedInstanceState)
286:        savedInstanceState?.getString(STATE_LAST_REPORT)?.let { text ->
293:        savedInstanceState?.getStringArrayList(STATE_LOG_ENTRIES)?.let { saved ->
```
Confirms: no `onPostCreate`, no `onStart`, no `onRestoreInstanceState`
override anywhere in `MainActivity.kt`.

### A10 — AsyncTask cancellation / onDestroy (backs a2 F5 — now CONFIRMED)

```
$ grep -n "onDestroy\|cancel(true)\|AsyncTask.Status\|isCancelled" apps/scanner/app/src/main/java/com/tananaev/passportreader/MainActivity.kt
$ echo "exit status: $?"
```
```
(no output)
exit status: 1
```
Zero hits — mechanically confirms this file has no async-cancellation
discipline of any kind.

### A11 — permission-result callback (backs a2 F7)

```
$ grep -n "onRequestPermissionsResult" apps/scanner/app/src/main/java/com/tananaev/passportreader/MainActivity.kt
$ echo "exit status: $?"
```
```
(no output)
exit status: 1
```

### A12 — all `Thread{}`/`runOnUiThread` sites (backs a2 F5's three-writer-chain claim)

```
$ grep -n "runOnUiThread\|Thread {" apps/scanner/app/src/main/java/com/tananaev/passportreader/MainActivity.kt
```
```
306:            Thread { runMasterlistProbe() }.start()
310:            Thread { runDeviceKeyProbe() }.start()
321:                Thread { DeviceKey.exportDevAttesterPublicKeyIfPresent(applicationContext) }.start()
611:        Thread {
618:            runOnUiThread { applyHandoffVerificationOutcome(handoff, outcome) }
1262:        Thread {
1285:                runOnUiThread {
1309:                runOnUiThread {
1329:                runOnUiThread {
1353:                runOnUiThread {
1369:            runOnUiThread { promptAndMint(keyState, baseReport, zktag, scopeDomain, site, attemptId, mode, chipAuthStatus) }
1414:                    Thread { mintAndMaybeHandoff(keyState, authorizedSig, baseReport, zktag, scopeDomain, site, attemptId, mode, chipAuthStatus) }.start()
1451:     * below is therefore wrapped in [runOnUiThread], and [pendingHandoff] is
1474:            runOnUiThread {
1515:            runOnUiThread {
1636:        runOnUiThread {
1727:        runOnUiThread {
1798:        runOnUiThread { emitReport(text, diagnosticSummary(failed = text.contains("PROBE FAILED") || text.contains("INVALID RUN"), label = "masterlist checks")) }
1834:        runOnUiThread { emitReport(text, diagnosticSummary(failed = text.contains("PROBE FAILED") || text.contains("MISMATCH") || text.contains("UNEXPECTED"), label = "device key self-test")) }
```
The three writer-chain launch sites that touch `pendingHandoff`/
`verifiedRequest`/other shared `MainActivity` state, cited in a2 F5:
`:611`, `:1262`, `:1414`. (`:306`/`:310`/`:321` are debug-probe threads whose
`runOnUiThread` continuations only call `emitReport` with a self-contained
diagnostic string — no shared-field writes, lower-consequence, not counted
among the three.)

### A13 — `class MainActivity` / manifest activity declaration (backs (e1)'s original scope-boundary finding, now closed)

```
$ grep -rn "class MainActivity\|: MainActivity(" apps/scanner/app/src
```
```
apps/scanner/app/src/main/java/com/tananaev/passportreader/MainActivity.kt:164:abstract class MainActivity : AppCompatActivity() {
apps/scanner/app/src/regular/java/com/tananaev/passportreader/RegularActivity.kt:3:class RegularActivity : MainActivity()
```
```
$ grep -n "<activity" apps/scanner/app/src/main/AndroidManifest.xml
$ echo "exit status: $?"
```
```
(no output)
exit status: 1
```

### A14 — call-graph generation (backs the Call graph section)

Two-pass Python script: pass 1 builds a line-range table from `grep -n`
against every `private fun`/`override fun`/`fun ` declaration in
`MainActivity.kt`; pass 2 re-`grep`s each known callee name as a whole-word
match, filters out lines that are pure comments or contain the callee's own
`fun` declaration, and assigns each remaining hit to the innermost range
containing it. The exact script (not reproduced here for length) was run
twice — once against the flat declaration list, once with the nested
`TabLayout.OnTabSelectedListener` anonymous-class body manually excluded
from `onCreate`'s range after the first run showed the nested listener's 7
lines swallowing the 90-odd lines of `onCreate` that follow it in the file.
The corrected run is what the Call graph section reports. Cross-class edges
(`DeviceKey.*`, `RequestTrust.*`, etc.) were collected with a plain
`grep -n` per class prefix (reproduced in full further above, inline with
the finding it backs, rather than repeated here).

### A15 — MainActivity.kt closing-brace pairing (backs (b)'s exact `MainActivity.kt` line counts)

```
$ grep -n "private fun \|fun onCreate\|override fun\|inner class ReadTask" apps/scanner/app/src/main/java/com/tananaev/passportreader/MainActivity.kt
$ grep -n "^    }" apps/scanner/app/src/main/java/com/tananaev/passportreader/MainActivity.kt
```
Both lists (function-start lines and 4-space-indented closing braces) were
paired in file order — each top-level `MainActivity` member's own closing
brace is the next 4-space-indented `}` after its `fun`/`class`/`object`
line, since all top-level members share that indent level and
`ReadTask`'s own nested members (`doInBackground`, `passiveAuthAgainst`,
`onPostExecute`, at 8-space indent) do not appear in the 4-space list —
their spans were instead computed as (their own start line) to (the next
8-space-sibling's start line), which is why `doInBackground`'s and
`onPostExecute`'s counts in (b) are marked `~` (approximate) while every
other `MainActivity.kt` count is exact.

### A16 — other-file line-count spans (backs (b)'s `~N lines` figures outside `MainActivity.kt`)

```
$ grep -n "^    fun \|^    private fun \|^object \|^class \|^abstract class \|^    private val \|^    class \|^    data class \|^    sealed class \|^    enum class " <each file>
```
run once per file, then each declaration's span computed as (its own start
line) to (the next declaration's start line, or end-of-file) minus one —
this typically **over-counts by a few lines** because a declaration's span
absorbs the next declaration's leading `/** doc comment */` block. Flagged
`~` throughout (b) for exactly this reason; treat these as a rough
sequencing signal, not a precise count (contrast `MainActivity.kt`'s exact,
brace-paired counts in A15).

### A17 — listener/override/lambda registration sites (backs the call graph's "Explicit listener/override/lambda roots" subsection)

```
$ grep -n "setOnClickListener\|setOnLongClickListener\|setOnEditorActionListener\|registerForActivityResult\|DatePickerDialog.newInstance\|addOnTabSelectedListener" apps/scanner/app/src/main/java/com/tananaev/passportreader/MainActivity.kt
```
```
223:    private val qrCaptureLauncher = registerForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap: Bitmap? ->
226:            return@registerForActivityResult
231:            return@registerForActivityResult
266:        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
303:        lockButton.setOnClickListener { lockModeAndArm() }
305:        findViewById<View>(R.id.button_m2_masterlist_probe).setOnClickListener {
309:        findViewById<View>(R.id.button_devicekey_probe).setOnClickListener {
320:            findViewById<View>(R.id.button_devicekey_probe).setOnLongClickListener {
326:        findViewById<View>(R.id.button_scan_qr).setOnClickListener {
329:        handoffManualInput.setOnEditorActionListener { _, _, _ ->
334:        expirationDateView.setOnClickListener {
336:            val dialog = DatePickerDialog.newInstance(
345:        birthDateView.setOnClickListener {
347:            val dialog = DatePickerDialog.newInstance(
```
`BiometricPrompt.AuthenticationCallback` (`:1400,1417,1432`) and the
`ReadTask` `AsyncTask` overrides (`:940,1069`) are not matched by this
pattern (different registration idiom — a constructor argument and a class
override, not a `setOnXListener` call) and were located instead in
revision 1's initial read-through; both were already present in the main
call-graph table before this revision.

### A18 — state-join field/function mapping (backs the State join section)

Two-pass Python script: pass 1 builds the same function line-range table as
A14/A15; pass 2 `re.search`es each (a)-table field's name (word-boundary
regex, e.g. `\blockedMode\b`, `\breportView\.text\b`) against every
non-comment line, strips trailing `//` comments before matching, assigns
each hit to the innermost owning range, and classifies it `W` if the field
name is immediately followed by `=` (not `==`) and `R` otherwise. The
script's raw, unfiltered output for `pendingHandoff` (reproduced in full,
the rest are in the same run) is what surfaced the `handleIncomingIntent`
false positive discussed in the State join section:

```
=== pendingHandoff ===
  172: [None] private var pendingHandoff: HandoffClient.PendingHandoff? = null
  379: [refreshModeStatus] pendingHandoff != null -> "Mode: verifying the site's request…"
  421: [lockModeAndArm] val handoff = pendingHandoff
  547: [handleIncomingIntent] Log.i(TAG, "M2 stage: pendingHandoff captured from av:// intent")
  605: [beginHandoffVerification] pendingHandoff = handoff
  672: [applyHandoffVerificationOutcome] if (pendingHandoff !== handoff) {
  883: [showBlockingOutcomeDialog] pendingHandoff = null
  1281: [continueAfterRead] val handoff = pendingHandoff
  1470: [mintAndMaybeHandoff] val handoff = pendingHandoff
  1637: [mintAndMaybeHandoff] pendingHandoff = null
```
Line `547` is inside a string literal (`"M2 stage: pendingHandoff captured
from av:// intent"`) — the regex has no way to distinguish an identifier
from a substring of a string literal, so this hit was manually excluded
after reading the actual line, and every other field's raw output was
checked the same way before being folded into the State join table.
