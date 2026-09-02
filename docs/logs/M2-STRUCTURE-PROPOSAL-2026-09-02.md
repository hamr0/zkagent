# apps/scanner structure proposal (DESIGN PROPOSAL, precedes a refactor)

Read-only. No source, doc, or commit was touched producing this report.
Anchored to the same pinned tree as the audit (`HEAD=2cd1e00`, porcelain
unchanged). I read the audit revision 4 in full, `CLAUDE.md`,
`.claude/remember/AGENT_RULES.md`, `.claude/remember/MEMORY.md`, and — new
this pass, not carried from the audit — the **full, current**
`MainActivity.kt` (all 1901 lines), `DeviceKey.kt`'s two object-scoped
fields and their four touching functions, `PaneVisibility.kt`,
`MrzChangeTracker.kt`, `ReportLog.kt`'s `append`/`clear`/`restore`, and
`RegularActivity.kt`. Every `file:line` below marked **VERIFIED** was
checked against that read, this pass, independently of the audit's own
citation. Anything marked **INFERRED** is carried from the audit's own
(already source-verified) citation without a second independent read in
this pass — mostly the pure/at-ceiling modules (`FailureTransition`,
`MintGate`, `MintConfirmation`, `RequestTrust`, `EvidenceSigner`,
`HandoffClient`), where re-reading would not change a placement decision.

---

## Answer up front

**6 clusters.** Not 2, not 3. The owner's "two or three classes, everything
eventually fits" hypothesis **holds for the part of the code that is
actually broken** (MainActivity's own mutable state resolves cleanly into
**3** classes) but **does not hold as literally stated** once the two
already-correct, already-single-owner modules (`DeviceKey`, `ReportLog`)
are counted — and inside `DeviceKey` itself, the two module-fields the
audit lumped together as "object-scoped, self-contained" turn out, on
reading the four functions that touch them, to be **two disjoint closed
sets, not one** (see Cluster 5 vs 6 below — this is the one place I
disagree with the audit's implicit grouping, detailed in §8).

The owner's own words — "a class for scan and another for errors" — also
don't match the shape that falls out. There is no closed set of mutable
state that corresponds to "errors": `FailureTransition` is already a pure
classifier with **no state of its own** (input: an exception; output: an
enum) — it's a function, not a stateful class, and it's already at ceiling
per (d). The real third MainActivity-level cluster is **Report/Log**, not
"errors." §8 addresses this diagagreement.

---

## 1. Cluster derivation

**Method.** Fields are nodes. A function is a hyperedge connecting every
field it reads or writes (per the audit's **State join** section,
cross-checked against my own read of `MainActivity.kt` — every citation
below I re-verified at the line given). Two fields are in the same
cluster iff some function's hyperedge touches both. I then apply one
filter the task requires explicitly: a function counts toward clustering
only when its touch is **decision-shaping** (a guard reads it, or it is
written as part of the same state-transition statement block) — not when
it is a pure **pass-through read for display text** with no write-back
and no branch on it. `emitReport`, `siteTitleFor`, `chipAuthLabel` are
disqualified from cluster membership by this filter (and by the task's
explicit instruction) — they are utilities called BY every cluster, they
do not themselves close over any subset of table (a); including them
would collapse everything into one component, which is exactly the
"hubs at the centre" failure mode the task warns against. One field read
this filter excludes: `ReadTask.onPostExecute`'s read of `verifiedRequest`
(`:1103`, VERIFIED) is a formatting argument to `siteTitleFor(...)`, feeding
`emitReport`'s `DisclosureSummary`, not a decision, and not stored back
anywhere — it does not bridge Cluster 1 and Cluster 2 despite
`onPostExecute` also writing `readInProgress` (`:1075`, VERIFIED) two
lines earlier in the same function body. Flagged explicitly rather than
silently applying the filter, per the task's citation requirement.

### Cluster 1 — Session/Handoff/Mode/Lock (7 of (a)'s 16 rows)

**State closed over:** `pendingHandoff` (row 3), `verifiedRequest` (row
2), `lockedMode` (row 4), `handoffStatus.text` (row 5),
`lockButton.isEnabled` (row 6), `modeStatusView.text` (row 10),
`lastMrzHash` (row 12, satellite — see below).

**Functions in it** (all VERIFIED against my own read of `MainActivity.kt`
this pass): `refreshModeStatus` (`:371-382`, reads `pendingHandoff`
`:379`/`verifiedRequest` `:372`, writes `modeStatusView.text` `:373`);
`lockModeAndArm` (`:411-502`, reads `lockedMode` `:412`,
`pendingHandoff` `:421`, `verifiedRequest` `:424`, writes `lockedMode`
`:498`, `lockButton.isEnabled` `:499`, `modeStatusView.text` `:500`);
`beginHandoffVerification` (`:604-620`, writes `pendingHandoff` `:605`,
`verifiedRequest` `:606`, `handoffStatus.text` `:607`,
`lockButton.isEnabled` `:609`); `applyHandoffVerificationOutcome`
(`:671-716`, reads `pendingHandoff` `:672`, writes `verifiedRequest`
`:678`, `handoffStatus.text` `:680`/`:708`, `lockButton.isEnabled`
`:689`); `showBlockingOutcomeDialog` (`:875-889`, writes `pendingHandoff`
`:883`, `verifiedRequest` `:884` — the audit's row-2/3 "third writer"
finding, confirmed: this is a genuinely distinct writer, not a call into
one of the other four); `wipeSession` (`:729-750`, writes `lockedMode`
`:734`, `lockButton.isEnabled` `:735`, `lastMrzHash` `:740`, and calls
`refreshModeStatus` `:736` which itself reads `pendingHandoff`/
`verifiedRequest`); `continueAfterRead`'s background block (`:1281-1282`,
reads `pendingHandoff`/`verifiedRequest` — the audit's corrected
cross-thread finding, confirmed by reading the enclosing `Thread {`
opened at `:1262`, which the two reads sit textually inside); `handleIncomingIntent`
(`:555` reads `lockedMode` — confirmed the audit's false-positive
correction: the `:547` `Log.i` string containing the substring
"pendingHandoff" is a log message, not a field touch — this function
neither reads nor writes `pendingHandoff`/`verifiedRequest`, only
`lockedMode`/`lastMrzHash`); `mintAndMaybeHandoff` (`:1470-1471` reads
`pendingHandoff`/`verifiedRequest` on the background thread it runs on,
`:1637-1638` writes both inside the `runOnUiThread` at `:1636`).

**Why `lastMrzHash` is IN this cluster, not its own singleton:** it is
never read by any guard's decision (the guards table's own row 2 says so
explicitly for `lockModeAndArm`'s entry guard) — but its only two writers,
`handleIncomingIntent` (`:580`) and `wipeSession` (`:740`), are both
already inside this cluster for their OTHER writes/reads (`lockedMode`).
By the connected-components rule stated above, that makes it part of this
component whether or not I like the coupling — I flag it as a **satellite**
(single-purpose, diagnostic-only per (d), consequence NONE per row 12) so
the ownership proposal in §2 can decide whether to fold it fully in or
keep it a separately-named slot the same owner exposes.

**Guards this boundary rests on:** guards table rows 1, 2, 4, 5, 6 (all
five multi-field guards in the audit involve at least two fields from this
cluster; row 6 is the promoted (a) finding that `showBlockingOutcomeDialog`
is a third writer).

### Cluster 2 — Pane / read-in-flight lifecycle (3 of 16 rows)

**State closed over:** `TabLayout.selectedTabPosition` (row 1, framework
+ app trigger), `readInProgress` (row 11), `mainLayout`/`logLayout`/
`loadingLayout` `.visibility` (row 15).

**Functions in it:** `showPane` (`:911-916`, VERIFIED — reads
`readInProgress` and `tabLayout.selectedTabPosition` via
`PaneVisibility.choosePane` at `:912`, writes all three `.visibility`
fields at `:913-915`); `startSession` (`:919-923`, writes `readInProgress`
`:920`, calls `showPane` `:921`); `ReadTask.onPostExecute`'s own
`readInProgress` write (`:1075`, first statement, VERIFIED — its separate
`verifiedRequest` read at `:1103` is excluded per the filter above); the
tab-listener's three overrides (`:267`/`:268`/`:272`, VERIFIED —
`onTabSelected`/`onTabReselected` both call `showPane()`, confirming the
audit's call-graph finding that `onTabReselected` — the actual D55/F1 bug
method — is a real, separate framework-invoked root, not folded into
`onCreate`).

**Guards:** none of the audit's 6 guard rows touches this cluster's
fields — F1 (a2) is not a guard-conflict, it's a **timing** conflict
(framework restore vs. app-computed pane), which is exactly why §3
(lifecycle) rather than §2 (guards) is where F1 gets fixed.

### Cluster 3 — Report / Log display (3 of 16 rows)

**State closed over:** `reportView.text` (row 7), `lastReportText`
(row 8), `logView.text` (row 9). (`ReportLog.entries` itself is Cluster 6,
below — `MainActivity` only calls `ReportLog`'s public entry points, per
the audit's finding, confirmed: no `entries`/`pendingIndexByAttempt`
access appears anywhere in `MainActivity.kt` outside `emitReport`'s
`reportLog.append` call `:779` and `onCreate`/`onSaveInstanceState`'s
`reportLog.restore`/`.rendered`/`.entriesSnapshot` calls.)

**Functions in it:** `emitReport` (`:768-782`, VERIFIED — writes all
three, `:769`/`:770`/`:780`, single well-scoped function, doc at
`:753-758` calls itself "the ONE place"); `onCreate`'s restore branch
(`:286-289` writes `lastReportText`/`reportView.text`, `:293-295` writes
`logView.text` — VERIFIED, and this is the doc/code mismatch the audit
flags at row 7: `:288` is a direct `reportView.text = text` that does
**not** go through `emitReport`, contradicting `emitReport`'s own "ONE
place" doc comment at `:753-758`, and — because it bypasses `emitReport`
entirely — it also skips the `Log.i` at `:781`, so this restore is
genuinely invisible in logcat, which is the exact class of bug `emitReport`
was built to prevent in the first place).

### Cluster 4 — ReportLog's own internal state (1 of 16 rows: row 14)

**State closed over:** `ReportLog.entries`, `ReportLog.pendingIndexByAttempt`
— both `private`, both touched only inside `ReportLog.kt` itself.
**Functions in it (all inside `ReportLog.kt`, none in `MainActivity`):**
`append` (`:198-211`, VERIFIED against my own read this pass — reads/writes
both fields together, the D46 "intentionally coupled" replace-vs-append
logic), `clear` (`:217-220`, writes both — VERIFIED zero call sites in
`MainActivity.kt` this pass, confirming the audit's A4 finding still
holds), `entriesSnapshot`/`restore` (`:225`/`234`, read/write `entries`,
`restore` also clears `pendingIndexByAttempt`). Already a correctly
closed, single-owner class. **Already at ceiling — do not move.**

### Cluster 5 — `DeviceKey.lastMintAlias` (1 of 16 rows: row 13)

**State closed over:** `lastMintAlias` alone (`@Volatile`, `DeviceKey.kt:162-163`,
VERIFIED this pass).
**Functions in it:** `ensureKey` (`DeviceKey.kt:381-383`, VERIFIED —
`if (alias != PROBE_ALIAS) lastMintAlias = alias`), `exportDevAttesterPublicKeyIfPresent`
(`:564-566`, VERIFIED — `val alias = lastMintAlias ?: PROBE_ALIAS`).
Debug-only consumer, release-inert per the audit's row-13 consequence
label. **Already at ceiling — do not move.**

### Cluster 6 — `DeviceKey.softwareEd25519Store` (1 of 16 rows: row 16)

**State closed over:** `softwareEd25519Store` alone (`DeviceKey.kt:598`,
VERIFIED this pass — no `@Volatile`, matching the audit's e2 asymmetry
note).
**Functions in it:** `ensureSoftwareEd25519` (`:600-610`, VERIFIED),
`signSoftware` (`:632-638`, VERIFIED). **This is disjoint from Cluster 5**
— I read all four of `DeviceKey`'s functions that touch either field
(`ensureKey`, `exportDevAttesterPublicKeyIfPresent`,
`ensureSoftwareEd25519`, `signSoftware`) and confirmed no function touches
both fields. The audit's own prose ("both are Kotlin `object`-scoped
fields... untouched by Activity recreation entirely") is accurate as far
as it goes (a2, revision-4 changelog item 1) but does not claim they share
a closed function set — it only ever discusses their **recreation
survival**, never their **write-set**, and never merges them in the (a)
table or the state join. My read of the four functions is what
establishes they are two clusters, not one; nothing in the audit
contradicts this, it simply never asked the question. Dead in production
(row 16's own consequence: "unreachable" — `preferSoftwareUniformity` is
never passed `true`). **Already at ceiling — do not move**, but note as
its own cluster rather than folded into Cluster 5.

**Cross-check:** 7 + 3 + 3 + 1 + 1 + 1 = 16 rows. All 16 of (a)'s rows are
accounted for in exactly one cluster.

---

## 2. Ownership proposal per cluster

### Cluster 1 → `SessionState` (new class, replaces 7 scattered fields)

Owns: `pendingHandoff`, `verifiedRequest`, `lockedMode`,
`handoffStatus` (as a value, not a `TextView` — the class stays
Android-free; `MainActivity` renders whatever it returns), `lockEnabled`
(a `Boolean`, likewise rendered by the Activity), `modeStatusText` (same),
`lastMrzHash`.

**Exactly one writer function per field, all on `SessionState`:**
- `capture(handoff)` — the only path that sets `pendingHandoff` and nulls
  `verifiedRequest`/derived display text — replaces `beginHandoffVerification`'s
  direct writes at `:605-607`.
- `applyVerified(handoff, request)` / `applyRefused(handoff, reason)` —
  replace `applyHandoffVerificationOutcome`'s writes at `:678`/`:680`/`:689`/
  `:708`, including the staleness check currently at `:672`
  (`pendingHandoff !== handoff`) — this check moves INTO the class as an
  argument-vs-current-state comparison, not a field read scattered at the
  call site.
- `lock(mode)` — replaces `lockModeAndArm`'s write at `:498-500`. The
  MRZ-non-empty / expiry / tier-outcome checks in `lockModeAndArm`
  (`:413-487`) stay in `MainActivity` (they read `EditText`s, not
  `SessionState` fields, and produce UI Snackbars/dialogs) — only the
  final `lockedMode = mode` / `modeStatusView.text = ...` writes move.
- `clearForOutcome()` — replaces the THREE current writers of
  `pendingHandoff`/`verifiedRequest` that are not `capture`/`applyVerified`:
  `showBlockingOutcomeDialog`'s dismissal handler (`:883-884`),
  `mintAndMaybeHandoff`'s post-`direct_post` clear (`:1637-1638`), and
  `wipeSession`'s `lockedMode = null` (`:734`) — **this is the collapse
  the task asks for explicitly**: today three functions in three different
  parts of the file independently null the same two fields, with only
  `wipeSession`'s own KDoc (per the audit) admitting it does not own them.
  After this move, all three call `sessionState.clearForOutcome()` and none
  touches a field directly.
- `noteMrzAttempt(hash)` / `resetMrz()` — the two current `lastMrzHash`
  writers (`handleIncomingIntent:580`, `wipeSession:740`).

**Reads:** every current reader (`refreshModeStatus`, `lockModeAndArm`'s
guard, `continueAfterRead`'s background block, `mintAndMaybeHandoff`'s
background block, `handleIncomingIntent`'s tag guard) calls a
`SessionState` **snapshot accessor**, never touches a backing field.

**#2/#3 specifically — what owns `verifiedRequest`/`pendingHandoff`, what
thread, what visibility contract:** `SessionState` owns both, as a single
`@Synchronized`-guarded pair (or one `data class Snapshot(handoff, request,
mode, ...)` behind an `@Volatile` reference, swapped atomically on every
write — cheaper than per-field locking and removes the "co-written but not
atomically" shape the audit's † note describes for rows 2/3). Every
current writer (`capture`, `applyVerified`, `clearForOutcome`) becomes a
method that takes the lock (or does one CAS on the `@Volatile` snapshot
reference) and writes both fields together — **the field pair can no
longer be observed half-written**, which is not true today (`:605`/`:606`
are two separate statements, not atomic). **Thread:** `beginHandoffVerification`'s
background verify thread (`:611`) and `continueAfterRead`'s zktag/key
thread (`:1262`) and `mintAndMaybeHandoff`'s mint thread (`:1414`) ALL
read through the same accessor — since the accessor is a single volatile
reference read/CAS, this closes the audit's row-2/3 finding directly: a
cross-thread read of a non-`@Volatile` field with no staleness guard
becomes a cross-thread read of a volatile reference, which IS
memory-visibility-safe (each read observes either the fully-old or
fully-new snapshot, never a torn combination) — **this is the fix for the
HIGH-consequence, unconfirmed row 2/3 finding**, not merely a relabeling.
Superseded-verification detection (`applyHandoffVerificationOutcome`'s
`pendingHandoff !== handoff` check) becomes `sessionState.applyVerified`
comparing its own current snapshot's handoff identity to the argument —
same logic, same guarantee, now inside the one function that can make it
atomic with the write.

### Cluster 2 → `PaneState` (new class, thin — decision already extracted)

Owns: `readInProgress`. Does **not** own `TabLayout.selectedTabPosition`
(framework-owned; `PaneState` takes it as a read-only parameter, exactly
as `PaneVisibility.choosePane` already does) or the three `.visibility`
fields (those stay on the real `View`s — a POJO cannot own a `View`
property without becoming Android-bound again, which would un-do
`PaneVisibility`'s own reason for existing per its class doc).

**Single writer:** `setReadInProgress(Boolean)` — replaces `startSession`'s
`:920` write and `ReadTask.onPostExecute`'s `:1075` write. `showPane()`
stays in `MainActivity` (it must touch real `View`s), reads
`paneState.readInProgress` and `tabLayout.selectedTabPosition` and calls
the already-tested `PaneVisibility.choosePane` — unchanged from today,
just reading through `PaneState` instead of a bare field.

**This is deliberately the smallest of the three new classes** — it is
one `Boolean` with two writers. It earns its own class anyway because
Cluster-2's actual defect (F1) is a **lifecycle** problem, not an
ownership problem (`readInProgress`'s own writer discipline is already
clean per row 11's "mitigated" note) — see §3.

### Cluster 3 → keep `emitReport` as the single writer; fix the one leak

**No new class.** `reportView.text`/`lastReportText`/`logView.text` are
already owned by one function (`emitReport`) with 39 of its 40+ call
sites correct. The ONE defect is `onCreate`'s restore branch (`:286-289`,
`:293-295`) writing directly instead of through `emitReport`. **Fix:**
add a second, explicitly-named entry point on the SAME class discipline —
`emitReport`'s doc already distinguishes "terminal outcome" writes from
restoration; make that a real second function, `restoreReport(text)` /
`restoreLog(entries)`, that lives next to `emitReport`, calls the SAME
`Log.i` at the end (closing the doc/code mismatch the audit's row 7
found), and is the ONLY other writer of these three fields. This keeps
the "ONE place" claim literally true (one place per event category:
terminal/progress vs. restore) rather than aspirationally true. **Do not**
fold this into `SessionState` or `PaneState` — it shares no field with
either cluster (confirmed by the connected-components derivation above);
inventing a shared owner for unconnected state would violate the
derivation this whole proposal rests on.

### Clusters 4/5/6 → do nothing

`ReportLog`, `DeviceKey.lastMintAlias`, `DeviceKey.softwareEd25519Store`
are each already a single closed set with a single owner. Per the task's
own instruction, moving an at-ceiling module is losing ground and must
justify itself — I find no justification for touching any of the three.

---

## 3. Lifecycle proposal

**Recreation (F1's class, 11 LOST vs 1 FRAMEWORK-RESTORED vs 2
APP-PERSISTED vs 2 object-scoped).** This is an ownership question in
disguise: today `showPane()` reads one FRAMEWORK-RESTORED input
(`tabLayout.selectedTabPosition`) and one LOST input (`readInProgress`,
via the soon-to-be `PaneState`) from `onCreate`, before the framework's
own tab restore has landed (`onCreate` runs before `onPostCreate`,
confirmed by the audit's F1 entry and unchanged by anything I read this
pass). The fix that follows from Cluster 2's ownership (not a patch):
`PaneState` is the ONE place `readInProgress` lives — give it a real
recreation contract too, by making `MainActivity` persist the tab index
itself the same way `STATE_LAST_REPORT`/`STATE_LOG_ENTRIES` already are
(F2, confirmed no ordering defect there — the two `Bundle` reads at
`:286`/`:293` run synchronously before `showPane()` at `:301`). Add
`STATE_TAB_INDEX` to `onSaveInstanceState`, restore it into `PaneState`
(or a plain local) in `onCreate` BEFORE the `showPane()` call at `:301`,
and stop relying on `TabLayout`'s own restore for the pane decision
entirely — `showPane()` reads the app's own persisted index, not
`tabLayout.selectedTabPosition`, closing F1 by construction (the same
"unrepresentable, not merely avoided" move D55 already made for the
three-`.visibility` race) rather than by re-timing a listener.
`SessionState`'s 7 fields are correctly LOST today (per row 4/12's
consequence being MEDIUM/NONE) and I am **not** proposing they become
APP-PERSISTED — a recreated Activity re-showing "Mode: A" and an empty
form while a handoff was mid-verification is arguably correct behavior
(the handoff itself is server-side state the app never owned), and
persisting a partially-verified `pendingHandoff`/`verifiedRequest` pair
across a process-death-and-restore is a new capability, not a bug fix —
flagged as **out of scope** for this proposal, an owner call if wanted.

**Async cancellation (five unfenced sites, F5).** This is the one place
ownership alone does not fix the defect — cancellation is a **lifecycle**
concern orthogonal to which class owns a field (the audit says this
explicitly). Proposal: `SessionState` becomes the natural place to attach
a monotonically-increasing "session generation" counter (bumped on every
`capture`/`clearForOutcome`), and every one of the five background chains
((i) `:611` verify thread, (ii) `:1262` zktag thread, (iii) `:1414` mint
thread, (iv) `:306` masterlist probe, (v) `:310` device-key probe) that
currently just calls `runOnUiThread { ... }` unconditionally captures the
generation at launch and checks it's still current before writing through
`SessionState`/`emitReport` inside that `runOnUiThread` block. This does
NOT require `onDestroy()`/`AsyncTask.cancel()` (the audit is right that
zero cancellation discipline exists) — it is a cheaper, ownership-shaped
guard: a stale write is REFUSED by the owner, not merely delivered late.
(iv)/(v) (the release-reachable report-only probes) get the same
generation check on `ReportLog`'s side if the owner wants F5's low-severity
half closed too — optional, since their consequence is "a diagnostic-probe
report surviving into the next instance's log," not request-state
corruption.

---

## 4. Complete placement table

| Function / class | Home | Notes |
|---|---|---|
| `lockedMode` field | `SessionState` | Cluster 1 |
| `pendingHandoff` field | `SessionState` | Cluster 1 |
| `verifiedRequest` field | `SessionState` | Cluster 1 |
| `lastMrzHash` field | `SessionState` | Cluster 1, satellite |
| `handoffStatus` text value | `SessionState` (computed), rendered by `MainActivity` | Cluster 1 |
| `lockButton.isEnabled` value | `SessionState` (computed), rendered by `MainActivity` | Cluster 1 |
| `modeStatusView` text value | `SessionState` (computed), rendered by `MainActivity` | Cluster 1 |
| `refreshModeStatus` | split: decision → `SessionState.modeStatusText()`; the one-line `modeStatusView.text = ...` write stays in `MainActivity` | Cluster 1 |
| `lockModeAndArm` | stays in `MainActivity` (reads 3 `EditText`s, shows Snackbars/dialogs) — final 3-field write delegates to `sessionState.lock(mode)` | Cluster 1 |
| `beginHandoffVerification` | stays in `MainActivity` (starts a `Thread`) — writes delegate to `sessionState.capture(handoff)` | Cluster 1 |
| `verifyPendingHandoff` | **stays where it is** — pure orchestration over `RequestTrust`/`HandoffClient`, touches no (a) field | no cluster |
| `applyHandoffVerificationOutcome` | stays in `MainActivity` (shows dialogs) — writes delegate to `sessionState.applyVerified`/`.applyRefused` | Cluster 1 |
| `wipeSession` | stays in `MainActivity` (clears 3 `EditText`s — a View concern) — `lockedMode`/`lastMrzHash`/`lockButton` writes delegate to `sessionState.clearForOutcome()` | Cluster 1 |
| `showBlockingOutcomeDialog` | stays in `MainActivity` (shows `AlertDialog`) — the `pendingHandoff`/`verifiedRequest` writes at `:883-884` delegate to `sessionState.clearForOutcome()`, called from the SAME place `wipeSession`'s call already is (no new call site) | Cluster 1 — this is the guard-row-6 promotion, closed |
| `continueAfterRead`'s background reads (`:1281-1282`) | reads via `sessionState` snapshot accessor | Cluster 1 |
| `mintAndMaybeHandoff`'s reads/writes | reads/writes via `sessionState` snapshot accessor / `.clearForOutcome()` | Cluster 1 |
| `tierOutcomeFor` | **stays where it is** — pure, private, no (a) field | no cluster |
| `TierOutcome` sealed class | **stays where it is** | no cluster |
| `TabLayout.selectedTabPosition` (read) | `MainActivity.showPane()` reads it directly (framework-owned) — proposal in §3 replaces this read with a persisted index | Cluster 2 |
| `readInProgress` field | `PaneState` | Cluster 2 |
| `mainLayout`/`logLayout`/`loadingLayout` `.visibility` | **stays on the real `View`s in `MainActivity`** — `showPane()` remains the one function that writes them | Cluster 2 |
| `showPane` | stays in `MainActivity` (touches real `View`s) — reads `paneState.readInProgress` instead of a bare field | Cluster 2 |
| `startSession` | stays in `MainActivity` — `readInProgress` write delegates to `paneState.setReadInProgress(true)` | Cluster 2 |
| `ReadTask.onPostExecute`'s `readInProgress` write | delegates to `paneState.setReadInProgress(false)` | Cluster 2 |
| `PaneVisibility.choosePane` | **stays where it is — already at ceiling** | Cluster 2, no move |
| `reportView.text`/`lastReportText`/`logView.text` fields | stay on `MainActivity` (or a thin `ReportView` holder if the owner wants the doc/code split literalized) | Cluster 3 |
| `emitReport` | **stays where it is** | Cluster 3, no move |
| `onCreate`'s restore branch (`:286-289`, `:293-295`) | extract to a named `restoreReport`/`restoreLog` sibling of `emitReport`, same file | Cluster 3 — this is the row-7 doc/code mismatch fix |
| `logTitleSizePx` | **stays where it is** — pure-ish helper, single caller (`emitReport`) | no cluster (utility) |
| `siteTitleFor` | **stays where it is** — utility, deliberately excluded from clustering (see method note) | no cluster (utility) |
| `chipAuthLabel` | **stays where it is** — utility | no cluster (utility) |
| `onSaveInstanceState` | stays in `MainActivity` — add `STATE_TAB_INDEX` per §3 | touches Clusters 2 & 3's persisted halves |
| `handleIncomingIntent` | stays in `MainActivity` (reads `Intent`, `EditText`s) — `lockedMode` read and `lastMrzHash` write delegate to `sessionState` | Cluster 1 |
| `applyPendingHandoffText` | **stays where it is** — no (a) field | no cluster |
| `armNfcDispatch`, `onResume`, `onPause`, `onNewIntent` | **stay where they are** | Cluster 1 consumers only (read `lockedMode`/`sessionState`) |
| `ReadTask` (`doInBackground`) | **stays where it is** — Android/JMRTD-bound, touches no (a) field directly (writes only its own instance-local `chipAuthStatus`/`passiveAuthVerdict`/etc, not table (a) fields) | no cluster |
| `promptAndMint` | **stays where it is** — parameter-threading function, touches no (a) field directly | no cluster |
| `BiometricPrompt` callbacks | **stay where they are** | Cluster 1 consumer (launches the mint thread) |
| `diagnosticSummary`, `runMasterlistProbe`, `runDeviceKeyProbe` | **stay where they are** — release-reachable per F5(iv)/(v), write only through `emitReport`/`ReportLog` | Cluster 3 consumers only |
| `convertDate`, `loadDate` | **stay where they are** — pure/View helpers, no (a) field | no cluster |
| `FailureTransition`, `MintGate`, `MintConfirmation`, `RequestTrust`, `EvidenceSigner`, `HandoffClient`, `MasterlistVerifier`, `M0Probe`, `Canonical`, `QrCapture`, `ImageUtil` | **stay where they are — all already at ceiling or explicitly out of this pass's scope (Android-bound I/O)** | no cluster, no move |
| `DeviceKey` (all functions except the two field-touching pairs above) | **stays where it is** | no cluster (Keystore-bound) |
| `RegularActivity` | **stays where it is** — 3-line concrete-class shim, VERIFIED this pass (`class RegularActivity : MainActivity()`, nothing else in the file) | no cluster |

**Silence audit:** every function named in the audit's (b) inventory and
call-graph section appears in this table exactly once (cross-checked
against the audit's (b) section headings and the state-join's own "every
other function... touches none of (a)'s fields" closing list).

---

## 5. Testability delta

| (d) invariant, currently unassertable half | Made assertable by this structure? | How |
|---|---|---|
| `TabLayout.selectedTabPosition` restore-ordering (F1) | **YES, if §3's persisted-index fix is taken** | Once `showPane()` no longer reads `tabLayout.selectedTabPosition` at all, the ordering race disappears by construction rather than needing an instrumented/Espresso recreate test — `PaneState`'s inputs become 100% app-owned (`readInProgress` + the persisted tab index), so `PaneVisibilityTest`'s existing exhaustive truth table already covers the fixed behavior with **zero new test infrastructure**. If §3 is NOT taken, this stays exactly where (d) leaves it — unassertable under `isReturnDefaultValues=true`, needs instrumentation. |
| Cross-thread `verifiedRequest`/`pendingHandoff` staleness (rows 2/3) | **Partially.** The `@Volatile`-snapshot fix in §2 is assertable in a plain JVM test **if `SessionState` itself is extracted as a POJO with no `View`/`Context` dependency** (same pattern as `PaneVisibility`) — a test can drive two threads calling `capture`/`clearForOutcome` concurrently and assert no torn read is ever observed. The APPLICATION of `SessionState`'s output to real `TextView`s stays untestable under this suite's config, same ceiling as every other View-touching function. |
| `emitReport` "ONE place" doc/code mismatch (row 7) | **YES.** Once `onCreate`'s restore path is a named, doc'd sibling function (`restoreReport`), a test can assert BOTH functions log (`Log.i` presence isn't unit-testable under this stub config either, but the STRUCTURAL claim — "exactly two named write paths, not one write path plus a silent bypass" — becomes a code-review-visible, not merely doc-visible, fact). Not a full close, but removes the doc/code contradiction the audit flagged. |
| `lockedMode` write-order guarantee (D38/D39, "zktag derived BEFORE `ensureKey`") | **No change.** This was already flagged in (d) as needing a typed-signature fix, unrelated to which class owns `lockedMode` — out of this proposal's scope. |
| Async-cancellation (F5) | **No — orthogonal to ownership**, per §3's own framing. The generation-counter guard is testable in isolation (pure integer comparison, POJO) but the fact that a real stale write is REFUSED end-to-end needs a device run, same as today. |
| Everything already at ceiling ((d)'s YES rows: `PaneVisibility`, `FailureTransition`, `MintGate`, `MintConfirmation`, `RequestTrust`, `EvidenceSigner`, alias derivation) | **Unchanged — correctly untouched.** | This proposal moves nothing already at ceiling; their tests stay exactly as valuable as they are today. |

---

## 6. Sequencing

Most-writers/highest-consequence first, per the audit's own sort (rows
2/3 are HIGH consequence and the most-written fields in the whole table):

1. **Extract `SessionState` as a plain, Android-free class** (same pattern
   as `PaneVisibility`/`MrzChangeTracker`) with the 7 fields and the 4
   writer methods from §2, but **do not wire it into `MainActivity` yet**.
   Write unit tests against it directly (concurrent `capture`/
   `clearForOutcome` races, the superseded-handoff check). "Verified" here
   means: the new test suite passes in isolation — no device needed, this
   step touches no `MainActivity` code path yet.
2. **Wire `SessionState` into `MainActivity`, replacing the 7 fields' 15
   scattered write statements one write-site at a time**, starting with
   the 3-writer `pendingHandoff`/`verifiedRequest` pair (rows 2/3 —
   highest consequence) and ending with `lastMrzHash` (lowest
   consequence). "Verified" per step: the existing device baseline (both
   happy paths — mode A bare scan, mode B verified-handoff mint — **plus
   the D55 correction path**, i.e. tab-tap-after-failed-read, per the
   task's own instruction) still passes on the Pixel 6a against both real
   documents, since none of `SessionState`'s external behavior should
   observably change — this is a refactor, and the device run is the
   regression gate, not a new-feature validation.
3. **Extract `PaneState`** (trivial — one field, two writers) and fix F1
   via the persisted-tab-index change from §3 in the SAME step (they are
   the same class's story). "Verified": a real device rotation
   (`fullSensor` per F8) mid-scan and mid-Log-tab, both landing back on
   the correct pane — this is F1's own confirmed repro shape, so this is
   the one step with a pre-existing, named regression to re-run.
4. **Extract the `restoreReport`/`restoreLog` sibling for `emitReport`**
   (lowest consequence, LOW per row 7) — smallest, safest step, can
   happen any time after step 1, does not depend on 2 or 3.
5. **Async-cancellation generation counter (§3), optional, owner's call**
   — F5 has no confirmed live symptom (e2), so this is the one step this
   proposal does NOT sequence as urgent; it is listed last because it is
   the one item where "verified" cannot mean "matches an observed
   regression" (there is no observed regression to re-run against) — it
   can only mean "a deliberately-injected stale-write test (two handoffs
   in flight, delayed second one) is refused post-fix and was NOT
   refused pre-fix," the device-run analogue of (e2)'s own open question.

Steps 1 and 4 need no device. Steps 2, 3, and 5 do — consistent with (d)'s
own finding that the View-application half of nearly every invariant here
is device-only.

---

## 7. What this does NOT fix, and what's unverifiable until a device run

- **Does not fix** F3 (Fragment/`DatePickerDialog` restoration timing) —
  untouched by any cluster, (e1) leaves it unverified and this proposal
  does not change that.
- **Does not fix** F4 (whether a second NFC tap can re-enter
  `handleIncomingIntent` mid-`IsoDep`-session) — `SessionState` does not
  add a "read already in flight" check to the guard at `:555`/`:561-563`;
  I considered adding one (since `PaneState.readInProgress` already tracks
  exactly this) and am **flagging it as a candidate**, not proposing it,
  because the audit explicitly left open whether the underlying race is
  even physically reachable (e1) — adding a guard against an unconfirmed
  race is exactly the kind of unjustified change AGENT_RULES' "surgical
  changes only" rule warns against.
- **Does not fix** the `DeviceKey.softwareEd25519Store` missing
  `@Volatile` asymmetry (e2) — dead code path, correctly left alone.
- **Does not resolve** whether `M0Probe`/`M2MasterlistProbe` belong in
  `src/main` despite being labeled "THROWAWAY" (e2) — orthogonal to
  ownership, an owner call.
- **Unverifiable until a device run:** every claim in §2 about the
  `@Volatile`-snapshot fix closing the row-2/3 race (memory visibility is
  a real-JVM/real-thread-scheduler property, not something the
  `isReturnDefaultValues=true` unit config can observe even after this
  refactor); the §3 F1 fix (needs the actual `fullSensor` rotation
  the audit's F8 finding establishes as common, not rare); step 5's
  generation-counter guard (same reason). **Everything else in this
  proposal that does NOT touch a `View` or a background `Thread`** — the
  pure `SessionState`/`PaneState` extraction itself — is unit-testable
  without a device, per §5's ceiling table.

---

## 8. What I disagreed with in the audit, with evidence

**One disagreement, on a question the audit never actually asked.** The
audit's revision-4 changelog (item 1) and its (a) rows 13/16 both
describe `DeviceKey.lastMintAlias` and `DeviceKey.softwareEd25519Store`
as sharing the same classification — "object-scoped... untouched by
Activity recreation" — and nowhere claims they share a function set; the
state-join section's closing paragraph even lists them together as
"written only from inside their own owning class." That sentence is true
but, read as a clustering claim (which the state-join section is
explicitly about), it undersells the finding: I read all four functions
that touch either field (`DeviceKey.kt:381-383`, `:564-566`, `:600-610`,
`:632-638`, all VERIFIED this pass) and confirmed **zero overlap** —
`ensureKey`/`exportDevAttesterPublicKeyIfPresent` never reference
`softwareEd25519Store`, and `ensureSoftwareEd25519`/`signSoftware` never
reference `lastMintAlias`. They are two independent one-field state
machines that happen to sit in the same `object DeviceKey`, not one
two-field state machine. This is the difference between my cluster count
(6) and what a reader skimming only the audit's (a) table's two
"NEITHER of the three" rows might reasonably guess (5, treating both as
one "DeviceKey misc state" bucket). Practical consequence: **none** for
placement (both stay in `DeviceKey`, untouched, either way) — the
disagreement matters only for answering the task's literal question
("how many clusters"), not for §2's ownership proposal.

No other disagreement found. Every other citation I independently
re-verified against `MainActivity.kt`, `PaneVisibility.kt`,
`MrzChangeTracker.kt`, `ReportLog.kt`, and `RegularActivity.kt` matched
the audit exactly.
