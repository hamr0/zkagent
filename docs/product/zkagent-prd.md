# zkagent — PRD v1.37 — 2026-09-02, owner-approved

**Status**: Draft. D1–D8 signed 2026-07-26; D9 closed 2026-08-31 on M0 evidence — the mode-B derivation field is the **document number**. **No promise in this document survives a miss at M0 — which ran 2026-08-29 and held (`docs/logs/M0-EVIDENCE.md`).**
**Project**: `zkagent` · **Published package**: `chiproof` · **Owner**: hamr · **Repo**: zkagent (sibling of 8een)
**Parent standards**: `AGENT_RULES.md` (POC-first, dependency hierarchy, prove-don't-assert, security invariants). When anything here disagrees with AGENT_RULES, AGENT_RULES wins.
**Version history**: §15. This revision (v1.37 — 2026-09-02, owner-approved) records **D57**, a
FREEZE on new §6.2 items/enhancements after the M2 scanner reached ~4,780 unreviewed LOC across
seven isolated agent rounds, and confirms **D55/D56 DEVICE-CONFIRMED** against a read-only
ownership audit and a targeted device run — see `docs/logs/M2-OWNERSHIP-AUDIT-2026-09-02.md` and
`docs/logs/M2-D55-D56-EVIDENCE.md`. **D57, the freeze (owner decision, 2026-09-02):** no new §6.2
item or enhancement lands until the ownership refactor's exit criterion is met — every mutable
UI/session field has a named single writer, every async writer is fenced, and
`.claude/remember/findings.md` (the new durable findings log, seeded from a read-only ownership
audit run this session) carries no OPEN entry of consequence HIGH. Recording a decision *about* a
fix is not itself new scope (NO-GO #10 intact) — the freeze names what is NOT proceeding, it does
not add a feature. **D55/D56 device confirmation:** `docs/logs/M2-D55-D56-EVIDENCE.md` captures both
documents minting cleanly through the D56 diagnostic (first-attempt and CHANGED variants observed)
and a deliberate wrong-digit reproduction of D55's failure/retry shape; the evidence doc states
plainly what it did NOT establish — the specific Log-tab-then-retap stranding sequence D55's fix
targets was not exercised in this device run (no `UNCHANGED` line appears in either capture), so
D55's fix is confirmed as a working code path, not yet as a reproduced-then-resolved regression test
against the exact original failure. **Q38, Q39, Q40 opened (§11)** from three owner UX observations
on the same 2026-09-02 device run that surfaced D55/D56, each deferred under D57 rather than
designed now: Q38 (log lifetime vs. D45/NO-GO #9), Q39 (an incoming-handoff tab-switch, a distinct
decision from the auto-switch-on-completion D55 already rejected), and Q40 (the disabled Lock
button reading as "stuck," landing on `lockButton.isEnabled` — a field named directly in the
ownership audit as a four-writer, no-single-owner seam that **MUST NOT be touched before the
structure pass lands**). **Addendum, same day:** Q41 opened — a read-only adversarial analysis found the `av://` intent-handling path (`.claude/remember/findings.md` #10; overlaps findings #2/#3) is EXPLOITABLE (consequence HIGH), not applied by fix commit `4969a20`; see §11 Q41. **Second addendum, same day:** Q42 opened — a second-session review (orchestrator-verified at source, `.claude/remember/findings.md` #11) found the biometric prompt (`MainActivity.kt:1391-1393`) shows no origin/site/tier, a consent defect independent of Q41 that survives every one of Q41's mitigation options; see §11 Q42. **Third addendum, same day (commit `730ef09`):** Q41/Q42 are MITIGATED — `HandoffAdmission.mayAdmitInboundHandoff` gates the `av://` branch and the biometric prompt now names the site (`Authorize presentation to %1$s`) — but both stay OPEN for the ownership fix (lock-time snapshot / SessionState), and the guard is to be removed when that lands. **Fourth addendum, same day:** **D58** records the ownership refactor's execution order (Report/Log, then Pane, then the lock-time request snapshot that removes the `HandoffAdmission` guard, then a re-derived Session boundary), with the pass-2 six-cluster result as its basis and a verbatim condition that the snapshot step MUST land. §6.2 (exit-criteria table, items 15/16 confirmed), §10 (D57 added, D58 added, D55/D56
annotated device-confirmed), §11 (Q38/Q39/Q40 opened, Q41/Q42 opened, Q41/Q42 mitigation status noted), §15 are annotated.

The prior revision (v1.36 — 2026-09-02, owner-approved) records **D55** and
**D56**, both from a real bug found on the owner's live Pixel 6a run, root-caused by direct code
inspection and corroborated by logcat. **D55, the bug:**
`apps/scanner/app/src/main/res/layout/activity_main.xml` places `loading_layout`, `main_layout` and
`log_layout` as overlapping siblings inside ONE `FrameLayout`, in that XML order — in a FrameLayout
later children draw on top, and both `main_layout` and `log_layout` are `match_parent` ScrollViews
with no background, so `log_layout` COVERS `main_layout` whenever both are VISIBLE. Two independent
code paths write those visibilities and neither knows about the third view: `MainActivity.kt:253-258`
(item 16/D44's tab listener) owns main<->log and explicitly leaves loading alone — its own comment at
`:250` calls that "an edge case not covered by items 15/16," exactly the assumption that failed;
`MainActivity.kt:857-858` (`startSession`) and `:1007-1008` (`ReadTask.onPostExecute`, item 15's
completion handler) own main<->loading and never touch log. **Failure sequence on hardware:** a read
fails -> the user opens the Log tab to see why -> taps the card again -> `onPostExecute` sets
`main_layout = VISIBLE` while `log_layout` is still VISIBLE -> the log paints over the MRZ form, the
tab indicator still reads "Log," `onTabReselected` is EMPTY (`:259`) so re-tapping does nothing, and
nothing in the file ever calls `selectTab` — the user cannot reach the document-number field to
correct it; the tag-intent path faithfully re-reads the fields on every tap, it just re-reads the
STALE value, re-derives the same wrong key, and PACE then BAC both reject it again. Device evidence
(owner's run, 2026-09-01 23:52:35/:41/:51, 23:53:04, pid 9948, one handoff captured 23:52:09): four
consecutive `PACE unavailable (AccessDeniedException)` -> `org.jmrtd.AccessDeniedException` failures,
spaced 6/10/11 seconds apart — too fast for a retype, which is itself the evidence the retries could
not have carried corrected details; contrast pid 9683, where an identical failure at 23:50:13 was
followed by a SUCCESSFUL PACE read at 23:50:46, because the Log tab was never opened that run.
**Corrects D54's own causal reading, supersede-in-place (D54's text and its classification-order fix
are UNAFFECTED, kept, not deleted):** D54 attributed the unbounded run of identical access failures to
a user "who does not change them," i.e. to user behaviour; that reading was incomplete — the app was
structurally PREVENTING the correction it was asking for. **Fix, owner-approved, as requirements:**
all THREE views' visibility writes MUST go through ONE function that sets all three on every call,
making the both-visible state unrepresentable rather than merely fixed — the same single-write-site
discipline `emitReport` already enforces for `reportView` (item 16), for the same reason. The pane
DECISION MUST live in a pure, Android-free object with its own unit test (the `FailureTransition`
precedent, item 15/D54) — this module runs with `unitTests.isReturnDefaultValues = true`, so
`View.visibility` is a non-functional stub and this invariant is NOT otherwise assertable in this
suite, the same limitation already recorded for `SpannableStringBuilder` (item 16). `onTabReselected`
MUST become idempotent, not empty. The read-in-progress flag MUST be cleared on EVERY exit path of
the completion handler, including the failure branch's early return. A completed read MUST NOT
auto-switch tabs — considered and REJECTED by the owner, since it would lose the user's place in the
log after every scan; tab selection stays wherever the user put it. `onCreate` MUST call the function
once after tab state and the restored log are in place. **Why the tests didn't catch it — a DIFFERENT
blindness from D54's:** D54's tests were a correct test of the wrong property; here the property is
not expressible in the suite at all, since Android framework view state is stubbed under
`isReturnDefaultValues = true`. The remedy is therefore structural — move the logic where it can be
tested, make the bad state unrepresentable — not additional assertions. **D56, a new diagnostic,
owner-approved:** the tag-intent path MUST log whether the three MRZ field values CHANGED since the
previous read attempt in the same process — value-free by design, because the existing logs could not
answer the one question that mattered here (did the owner's corrected details actually reach the
app), which cost an hour of code inspection to answer instead. Approved shape:
`M2 stage: MRZ input UNCHANGED since previous attempt (doc_len=9 dob_ok=true exp_ok=true)` / the same
line with `CHANGED`, plus a distinct first-attempt-this-session variant. MUST NOT log field values,
any character of them, or render the comparison hash anywhere; MUST NOT write it to `reportView`,
`ReportLog`, `onSaveInstanceState`, or disk. MUST hold the hash in memory only, SALTED with a
per-process random value generated at start and never persisted — an unsalted truncated digest of a
short document number is trivially brute-forceable and would itself be PII; MUST reset the stored
hash wherever the MRZ is cleared (`wipeSession`'s `!keepMrzAndMode` branch, item 6) so the next
attempt reads correctly as a first attempt. §6.2 (items 15, 16, exit-criteria table), §10 (D54
corrected in place, D55 and D56 added), §15 are annotated.
The prior revision (v1.35 — 2026-09-01, owner-approved) records **D54**,
from a live run where the owner hit five consecutive read failures on the Pixel 6a, then asked for
shorter failure copy. **Finding, tied to item 15/D43's access-establishment bucket:** all five were
`org.jmrtd.AccessDeniedException: Mutual authentication failed`, preceded by `PACE unavailable
(AccessDeniedException)`. Diagnostic distinction recorded because it is genuinely useful and was
not obvious: `PACE unavailable (CardServiceException)` means the chip does not support PACE and the
reader fell back to BAC; `PACE unavailable (AccessDeniedException)` means PACE WAS available, was
attempted, and the MRZ-derived key was REJECTED — both protocols failing on key rejection points at
wrong typed details, not a chip or code fault. Verified: the D43-D53 scanner commit touched no
MRZ/BAC/PACE key-construction code, so this was not a regression. **UX consequence, the actionable
part:** an access-establishment failure KEEPS the typed details by design (F3/D43, "for
correction") — but a user who does not change them re-derives the same wrong key on every retry,
producing an unbounded run of identical failures, so the message must make the required action
unmistakable, and must stay distinct from the transient-failure message since the two demand
different actions (correct your details vs. hold the card still). **Approved strings, superseding
those approved earlier today (D51's lineage, kept, not deleted) — shortened because the owner
skimmed past the longer wording five times on a real device, itself the evidence for shortening:**
access-establishment dialog (`strings.xml` `error_read`) → `Couldn't read — check your details and
try again.`; access-establishment `Result` line → `Couldn't read — check your details`;
transient-failure dialog → `Couldn't read — keep the card at the top of your phone.`; transient
`Result` line → `Couldn't read — card moved`. **MUST:** these remain TWO separate strings and MUST
NOT be merged into one shared failure message — the user action differs between the two buckets,
and merging would discard the distinction D51's third bucket exists to draw. Everything else
previously approved stands unchanged: the session-expiry pair, `ID scanned successfully`, the
`Identity` pair, the `claim_proof:` note, and the three chip-auth strings. **A real bug, found on
the same run, verified directly in the code:** the two failure classifications are evaluated in
the WRONG ORDER, so a physical card slip can be reported as a data-entry problem.
`MainActivity.kt` ~900-912's `try` around the access-establishment phase (`sendSelectApplet` /
`EF_COM` probe / `doBAC`) catches ANY `Exception` and sets `accessFailure = true`
unconditionally — classification by CODE PATH, not by evidence in the exception; `MainActivity.kt`
~1014's transient classifier is gated behind it (`!accessFailure &&
FailureTransition.isTransientChipCommunicationFailure(result)`), so it can only fire once access
has already succeeded — a tag-loss during access establishment is misclassified as a data-entry
problem, and the user is told to check typed details that were correct. **Why the tests didn't
catch it, the transferable lesson:** `FailureTransitionTest` asserts the keep/reset MAPPING, which
was correct — both buckets keep MRZ+mode, so the STATE TRANSITION is identical either way; only the
MESSAGE differs. A test suite that pins state transitions cannot see a bug that only changes which
correct-transition message is shown. **Fix, owner-approved, as requirements:** transient MUST be
classified FIRST, from evidence in the exception, independent of which phase was executing;
`accessFailure` MUST be narrowed to a genuine access DENIAL (`SW 0x6300`->`0x6985`,
`org.jmrtd.AccessDeniedException`) classified from the exception, never the code path; the
precedence MUST live in the pure `FailureTransition` object with its own test, not an `if` ordering
in the completion handler; an unclassifiable exception still falls to RESET (unchanged); the state
transitions themselves are UNCHANGED — only classification and therefore the message changes.
§6.2 (item 15, exit-criteria table), §10 (D54 amended) are annotated.
The prior revision (v1.34 — 2026-09-01, owner-approved) records **D53**,
from the owner's string review of the implemented build (2026-09-01). **Change 1, supersedes part
of D51:** the `Mode` line is REMOVED from the plain-language log block; D51's chip-authenticity
half is UNCHANGED. Reasoning: `Sent`, `Shared`, and `Identity` already convey what mode A/B mean in
plain language with no glossary needed (`Mode B — recognisable to this site` merely restates
`Identity`; `Mode A — anonymous` restates `Sent: nothing left this device`) — a redundant label.
Owner: "no scary business for non tech savvy they may think we know and transfer more than what we
do." Also: D21's "always read, conditionally mint" means the chip is read IDENTICALLY in both
modes — mode governs only what is SENT, never what is read — so a plain-language "mode A" risks
being misread as "we read less," which is false. Mode stays in `▸ technical:` and the on-screen
derived-mode display (D51) is unaffected; only the plain-block line goes. **Change 2, wording only,
supersedes the implementer's proposal:** chip-authenticity strings, owner-approved — VERIFIED:
`Verified — this document's chip proved it is genuine`; NOT_SUPPORTED: `Not supported — this
document has no chip authenticity check`; FAILED: `Not verified — the chip check did not pass`. The
implementer's clone-explicit proposal was rejected as alarming on every US-passport scan; the
three-state distinction and the stated-not-hidden clone-replay position are UNCHANGED — only the
phrasing changed. **Approvals recorded, previously pending:** D51's transient-failure dialog
(`Reading was interrupted — hold the document still against your phone and try again.`) and Result
line (`Read interrupted — the document moved or the connection dropped`); D52's success dialog
(`ID scanned successfully`, owner's own wording, deliberately not matched to the `Result` line);
D50's session-expiry pair, D48's `Identity` pair, and the `claim_proof:` note remain as previously
approved. **Accepted implementation judgements, not new decisions:** a mode-A/bare scan does not
receive D52's success confirmation (consistent with D52's accepted-delivery-only rule); no distinct
mid-read "hold still" progress state was added (deferred, not refused, since it needs restructuring
the read task's progress reporting, noted as available future UX work). §6.2 (items 15, 16,
exit-criteria table), §10 (D53 added) are annotated.
The prior revision (v1.33 — 2026-09-01, owner-approved) records **D52**,
from three further rounds of Pixel 6a scans (2026-09-01). **Positive finding:** four consecutive
transactions all succeeded (`ok=true allowed=true reason=evidence-verified evidence=["sig-p256/1"]
attester=matched`) — D38's first-sight attester binding now holds REPEATEDLY on hardware, a
returning document at the same origin recognised as the same key across four presentations,
strengthening D51's single-observation confirmation. **The decision, amends item 15/D43:** a
successful, DELIVERED AND ACCEPTED presentation MUST be surfaced as a blocking modal with an
acknowledge action, using the same mechanism as D43's failure dialogs — one dialog path for both
terminal outcome classes. Found by the owner using the build: a successful mint today only calls
`emitReport`, with NO dialog and NO transient UI — D43 made the app LOUD about every failure and
SILENT about success, so the one outcome a user most wants confirmed is the only one that does not
confirm itself; in a mode-B handoff the user must return to the browser, and nothing told them the
presentation had been accepted, even though D43's own general rule ("blocking UI for state that
requires the user to act") already covers this case once success is recognised as requiring action
too. Owner chose MINIMAL wording — outcome only, MUST NOT restate the disclosure (age predicate,
site, identity state), which already lives in the log entry (item 16) and the report. Dismissal
follows D43's existing non-access-failure reset branch, no separate post-success policy. Only
`Accepted` delivery qualifies — a signed-but-undelivered presentation (rejected by the verifier, no
`response_uri`, or a transport failure) MUST NOT render as success and keeps its failure treatment;
this is why the implementation's four-way delivery-outcome split exists, and collapsing it would
let an undelivered presentation read as verified. A mode-A / bare local scan completing with no
delivery is a terminal outcome but NOT a "verified by the site" success; if it confirms at all, its
wording MUST be honest that nothing was sent. Exact strings NOT yet owner-approved. §6.2 (item 15,
exit-criteria table), §10 (D52 added) are annotated.
The prior revision (v1.32 — 2026-09-01, owner-approved) records **D51**,
from the owner's live run of the current build on the Pixel 6a. **Positive finding:** a scan
produced `attester=matched` — the first `matched` of the session, confirming D38's first-sight
attester binding on real hardware (a returning document at the same origin re-presented the same
key and was recognised), alongside D50's D39 confirmation. **Evidence, from a separate scan in the
same run:** a mid-read failure, `net.sf.scuba.smartcards.CardServiceException: Tag was lost` inside
`DefaultFileSystem.readBinary`, surfacing as `IOException: Unexpected exception` — the card
physically moved during the read; the verifier confirms a transaction was created and never
received a presentation. **Three owner-approved amendments and one declined alternative:**
**(1)** item 15/D43 gains a THIRD failure-transition bucket — a TRANSIENT chip-communication
failure (tag lost / link dropped mid-read) MUST keep the MRZ and mode, like the
access-establishment bucket, so the user can hold the document still and retry with no re-entry;
the general rule stated is whether the ENTERED DATA IS STILL GOOD (wrong for access-establishment,
merely interrupted for a mid-read tag loss); the pending handoff MUST survive this retry, with
D50's session-expiry refusal taking precedence if the session expired meanwhile; classification
MUST be conservative — an unclassifiable exception falls through to RESET, since a wrong "keep"
leaves document data on screen the user did not expect, worse than a wrong "reset." Dialog wording
not yet owner-approved. **(2)** the mode radio is REMOVED; mode is now DERIVED — a verified
handoff's tier determines it, a bare local scan with no verified request is mode A by definition,
and the radio control is replaced by plain text showing the derived mode (amends items 4, 13/D33,
14/D34). This eliminates F5's bug class by construction (a control that can disagree with the
executed mode cannot disagree if it is not a control) and removes the last way to violate item 4's
one-source-of-truth requirement. D33/D34's "sets and locks the mode radio" MECHANISM is superseded
by derivation; the REQUIREMENT it enforced — an absent/invalid tier fails loudly, no default — now
guards the derivation instead and is UNCHANGED; tier C remains refused in this build. **(3)** item
16/D47/D49 gains mode and chip-authenticity status in the plain-language log block, alongside
Result/Sent/Shared/Identity; `chip_auth` stays unchanged in the `▸ technical:` line. Chip
authenticity has THREE states — verified, NOT SUPPORTED by this document, and failed — and the
absent case MUST read honestly and MUST NOT render as "false," tying to the project's standing,
stated (not hidden) position that a document without chip authentication is clone-replayable (the
US passport is exactly this case) and mode-B uniqueness only holds where `chip_auth` is true.
Exact strings not yet owner-approved. **(4) Declined, recorded because the rejection matters:** the
owner was asked whether to relax item 6/F1's `onStop()` MRZ wipe so entered details survive an
app-switch, and DECLINED — privacy behaviour is REAFFIRMED unchanged; the friction was the tag-loss
reset (fixed by (1) above), not the app-switch, and retaining document data in memory while
backgrounded would weaken F1's posture for convenience. Recorded so this alternative is not
re-proposed as an obvious improvement later. §6.2 (items 4, 6, 13, 15, 16, exit-criteria table),
§10 (D51 added) are annotated.
The prior revision (v1.31 — 2026-09-01, owner-approved) does two things: it
corrects D50's defect-3 causal claim, which was wrong, and it fixes several stale
cross-references found in a same-day conflict sweep across this branch's many stacked
supersede-in-place amendments (v1.23 through v1.30). **Correction to D50, defect 3:** the owner's
original claim — that a successful mint left the handoff pending and the mode locked to a
still-spent session, inviting a doomed second tap, and that this caused two `SW=0x6982` chip-access
failures — is NOT supported by the code. Verified directly at HEAD: `MainActivity.kt:1033-1034`
(pre-existing, already present before any of this session's work) clears `pendingHandoff = null`
and `verifiedRequest = null` once a handoff "definitively completed or failed" — on ALL delivery
outcomes, not only success — and `wipeSession(keepMrzAndMode = false)` clears `lockedMode` after
every completed read. The app therefore did NOT leave a consumed session pending, and a stray tap
could not have reached the chip read through a stale session. The two observed
`AccessDeniedException SW=0x6982` failures were genuine chip-access failures after the owner
re-typed the MRZ and re-locked — the dialog's message was accurate and the app's STATE was not
wrong. The logcat evidence quoted in D50 stands, accurate; the mechanism inferred from it was not,
and is corrected on sight rather than left to look authoritative, per this project's own standard.
**What defect 3 actually is:** new protection, not a bug fix. Nothing previously checked handoff
session expiry at any point in the code — a session could age out (still formally "pending," never
cleared by mere elapsed time, only cleared on definitive completion per the finding above) during a
physical chip read and fail at `direct_post` with no prior warning to the user. That gap is real
and the up-front-refusal fix stands on its own merits, independent of the corrected mechanism. The
"same class as this branch's scope-constant/threshold-constant findings" comparison drawn in D50 no
longer applies to this defect — those were two sides silently agreeing on a shared wrong constant;
this is a genuinely missing check, not a coincidental agreement — and is corrected accordingly.
**Q37 resolved by implementation, not design debate:** the challenge expiry
(`zkagent.challenge.expires_at`) is reachable from the already-verified request object with no
verifier round-trip, and "consumed" needs no separate detection because clearing already removes a
used session from state the moment it is used (per the finding above) — closed, §11. **Conflict
sweep (Task 2), findings and fixes:** (1) §10's D48 row still referenced the superseded **Q33** by
number in its "Interim sourcing" clause after Q33 was split into Q35/Q36 at v1.29 — corrected in
place to name Q35 (request-carried threshold) and Q36 (real computed answer) specifically. (2) The
§6.2 item 16 code block under D47 (the two owner-approved worked-example log entries) predates
D48's predicate/answer requirement, D49's boolean-list format, and D50's newest-first/single-entry
rules — it is annotated as the historical, still-correct source for `Result`/`Sent`/`Identity`/the
title-line format, with the `Shared` line and entry-count/ordering details now governed by the
later amendments and the Exit-criteria row, so it cannot be mistaken for the current full
rendering. (3) Checked and found consistent, no fix needed: item 16's original "MUST NOT change
report content" clause (explicitly superseded by D46) and original clear-on-session-wipe rule
(explicitly superseded by D45) are both unambiguous; the stacked top-revision-narrative paragraphs
(v1.16 through v1.30) form a consistent, non-duplicated chain with exactly one "This revision" at
any time; D48's threshold-from-request MUST and Q35's framing are coherent; the Exit-criteria rows
for items 15 and 16 matched the current state of those items (item 15's row is corrected under this
same revision, see below). §6.2 (items 15, 16, exit-criteria table), §10 (D50 defect-3 corrected,
D48 cross-reference corrected), §11 (Q37 closed) are annotated.
The prior revision (v1.30 — 2026-09-01, owner-approved) records **D50**,
from the owner's live run of §6.2 items 15/16 on the Pixel 6a with both real documents
(2026-09-01). **Positive finding first:** both documents minted successfully
(`ok=true allowed=true reason=evidence-verified evidence=["sig-p256/1"]`), each producing
`attester=bound_first_sight` with a DIFFERENT key — the first device-level confirmation of D39's
per-(origin, zktag) key isolation on real hardware. **Three defects, owner-approved to fix now,
inside items 15/16 (amendments, not new items, not new questions):** (1) the log view MUST list
the newest entry first — a rendering-order change only, the stored order still round-trips through
`onSaveInstanceState` (D35) unchanged. (2) the mint gate's biometric-authorization request and the
scan's terminal outcome each call `emitReport`, producing two log entries per scan (a stale
"In progress" entry that never resolves) — fixed by requiring exactly ONE log entry per scan
attempt, the terminal outcome REPLACING the in-progress entry in the log accumulator, with every
`emitReport` call still reaching logcat (the single-write-path invariant is UNCHANGED, this must
not be fixed by suppressing a write); an in-progress entry with no terminal outcome (backgrounded
mid-scan) MUST still be shown, never silently erased. (3) the substantive one: after a successful
mint the app leaves the handoff pending and the mode locked to a session whose nonce is already
spent, then invites a second, doomed tap — observed live (mint at 22:00:30, then two
`AccessDeniedException SW=0x6982` chip-access failures at 22:01:16/22:01:37; verifier log confirms
nothing reached it between the two attempts) — another instance of two internally-consistent sides
(app's "handoff pending" vs. verifier's "nonce spent") disagreeing about something neither can see
alone, the same class as this branch's scope-constant and threshold-constant findings. Fixed by
amending item 15/D43: the pending handoff and its verified request are CLEARED once a presentation
is delivered and accepted (`direct_post` 2xx); a tap/mint arriving with a mode locked to a handoff
but no usable session is refused UP FRONT with a blocking dialog, ideally before any tap, stating
the verifier session is no longer valid, with D43's non-access-failure reset on dismissal; an
EXPIRED session (challenge expiry passed) is equally unusable, not only a consumed one; the
access-establishment-failure path (`SW 0x6300`→`0x6985` keeps MRZ, F3) is UNCHANGED — it behaved
correctly in this run. Dead-session dialog wording is NOT yet owner-approved (owner's stated intent:
"verifier session expired or something") — implementer's chosen strings return for approval like
every other user-facing string. **Opens Q37** (§11, not resolved, no approach chosen): whether
"consumed" and "expired" can be distinguished device-side without a verifier round-trip, and where
the challenge expiry is reachable from. §6.2 (items 15, 16, exit-criteria table), §10 (D50 added),
§11 (Q37 opened) are annotated.
The prior revision (v1.29 — 2026-09-01, owner-approved) corrects **Q33**,
which was opened on an incomplete reading of the code, and splits it into two fresh questions,
**Q35** (descendant of Q33, part a) and **Q36** (descendant of Q33, part b) — Q33's own text is
kept in place and marked superseded, not deleted, per this doc's convention (matching how D42
handled Q29's descendant Q30). Three code findings, verified directly against the live source,
correct Q33's premise: **(1)** the request-object threshold Q33 said was absent already exists and
is signed and nonce-bound — `chiproof`'s `issueChallenge` places `threshold` inside the challenge
(`packages/chiproof/src/challenge.js:73-76,152-175`), riding in the ES256-signed request object at
`zkagent.challenge.threshold`, asserted equal to 18 by the spike's own test
(`spikes/m2-handoff/tests/roundtrip.test.mjs:86`), and the nonce is minted over
`(tier, verbs, threshold, max_scan_age, expires_at)` so any post-mint edit returns `nonce_forged`
(`packages/chiproof/src/challenge.js:225-240`) — yet the scanner parses that same object at
`MainActivity.kt:1197-1198` and reads ONLY `nonce`, and the comment at `MainActivity.kt:1191`
asserting the challenge "carries only nonce/tier/expiry" is itself a defect, factually wrong.
**(2)** D11's enforcement already exists and has been running all along —
`packages/chiproof/src/index.js:233-236` rejects `threshold_mismatch` when `claim.threshold`
diverges from the challenge's or the verifier's configured threshold, and `under_threshold` when
`claim.over_threshold !== true`; nothing about this area requires building enforcement. **(3)** the
2026-09-01 device runs that returned `allowed=true` did so only because two independently
hardcoded constants happened to agree — the scanner's `threshold = 18` (`MainActivity.kt:1181`)
and chiproof's default of 18 which the spike verifier inherits by passing none
(`packages/chiproof/src/index.js:76`; `spikes/m2-handoff/server.mjs:143`) — the same shape as this
branch's other scope-constant bugs, except worse: nothing in the code expresses the coupling at
all, so the two sides agree by coincidence rather than by a shared import. Owner: "Split it into
two." **Q35** is scoped as a one-line read (no protocol change, no new field, no verifier work)
that closes D48's unmet threshold-from-request MUST; **Q36** remains genuine open design work
(computing a real DOB-vs-threshold answer) with nothing chosen. D48's Q33 cross-reference is
superseded in place to point at Q35 specifically. No §6.2 item added — owner's standing instruction
is that these promote to §6.2 only when he decides to build them inside M2. §10 (D48
cross-reference superseded), §11 (Q33 superseded/split, Q35/Q36 opened) are annotated.
The prior revision (v1.28 — 2026-09-01, owner-approved) records **D49**,
amending D48's `Shared` specification twice, and appends a structural clarification to the still-
OPEN **Q34** without closing it. Owner: "true/false always #2 agreed #3 questions answers, same
shape \"age > 18: true, expiry > 3 months: false, expired: true\"." **First amendment:** the answer
half of each `Shared` line MUST be the literal boolean `true`/`false`, never "yes"/"no" — it is the
direct mirror of the signed predicate boolean, so the log cannot drift from the payload by
paraphrasing it; D48's `age above 18: yes` example is superseded by `age > 18: true`, reconciling
the doc TO the implementation (which already rendered booleans), not the reverse. **Second
amendment:** `Shared` MUST render as a LIST of `<predicate>: <boolean>` lines, one per disclosed
claim — not one formatted sentence — followed by the existing negation line; today's list holds
exactly one element (the age predicate); an empty list (mode A, an unmet mint gate, a refusal, any
non-delivered outcome) MUST render the plain "nothing shared" wording, never an empty label or
stray colon; the list MUST NOT be populated with any claim beyond the one that exists today —
expiry and every other attribute remain **Q34**, unbuilt. The predicate shape accommodates both
comparison form (`age > 18`, `expiry > 3 months`) and bare boolean form (`expired`), per the
owner's three worked examples. Also recorded, owner-approved and not a new decision: the
`▸ technical:` block's compliance note is approved verbatim as `claim_proof: self-asserted by the
device — not independently proven (D24)`, set only where a claim was actually signed — currently
the only place the log states the claim is unverified, tying to **Q33**. **Q34 append, question
stays OPEN:** the owner's examples settle the rendering/data SHAPE for multiple claims (a list of
predicate→boolean pairs, the same shape as today's single age claim) but decide nothing about
WHICH claims exist, their buckets, which tiers may carry them, or cumulative-disclosure cost — all
of that remains open, still needing its own design pass and riskiest-assumption POC. Implementation
fact recorded: `DisclosureSummary.shared` modelled one claim as a single string, which would have
needed reshaping rather than extending once Q34 lands, so the list shape is adopted now — a
structural change only, adding no claim. §6.2 (item 16, exit-criteria table), §10 (D49 added), §11
(Q34 appended) are annotated.
The prior revision (v1.27 — 2026-09-01, owner-approved) opens two new
questions and clarifies D48, without deciding either question. **Q33** records a code-inspection
finding: `apps/scanner/.../MainActivity.kt:1181-1182` hardcodes `threshold = 18` and asserts
`over_threshold: true` unconditionally on every mint — the chip's date of birth is used only to
derive the BAC/PACE access key, no DOB-versus-threshold comparison exists, and no request object
carries a `threshold` field — so D11's threshold-comparison requirement is unimplemented and the
device evidence captured 2026-09-01 is not evidence about age; both app and verifier behaved
correctly by their own contracts (the same self-consistent-but-wrong shape as this branch's other
cross-contract bugs). Owner: "Both — question now, item when you decide to build it" — recorded as
an open question now, promoted to a §6.2 item only if/when the owner decides to build it inside M2;
no §6.2 item added this revision. Cross-referenced from D48, since D48's requirement that `Shared`'s
threshold come from the verified request object is currently unmet as a direct consequence. **Q34**
records a new owner direction, not a decision: "i expect to land all things that comes with the id
make it available, expiry date ie. > 3 months > 6 months > 1 year and other things that are usually
verified/requested across mode a, b and c" — a general claim vocabulary beyond age, expressed as
bucketed/predicate claims. Recorded with four considerations for whoever answers it (generalizing
D11's reject-wrong-predicate rule, the data-minimisation reasoning of D40/Q11 applying per claim and
compounding across claims, FR6's anonymity-set framing, and per-tier disclosure limits across modes
A/B/C), needing its own design pass and riskiest-assumption POC before anything is built — nothing
chosen. **D48 clarified, not changed:** owner explained the `Shared` line's purpose — "what i meant
is to surface what questions was asked and how it was answered above 18: true note where above 18 is
requester and true was the answer, combined with not known and known that's a complete pic of
request to the user" — it is a question→answer record of the exchange that, with `Identity`'s
known/new state, gives the user the complete picture of a request. Interim sourcing recorded, tied
to Q33: until real per-request threshold/answer evaluation lands, both halves of `Shared` MUST be
rendered from the actual signed claim map (never a separately-typed string), so the log stays
faithful by construction and becomes correct automatically once Q33 is resolved. §10 (D48
annotated) and §11 (Q33, Q34 opened) are annotated.
The prior revision (v1.26 — 2026-09-01, owner-approved) records **D48**,
closing the residual D47 left open and adding one substantive requirement, in the same session.
Owner: "new — minted fresh for this site, known - recognized only here from previous visit (or
shorter), age above 18 yes shared" and "agreed on 1 and 2 and 3 above." **Identity residual
closed:** the reused-key case now has owner-confirmed copy, **"known — recognized only here from
previous visit"**, alongside the already-confirmed new-key copy; "only here" is load-bearing — the
plain-language statement of D38/D39's per-(origin, zktag) key isolation — and MUST NOT be
simplified out. **New `Shared` requirement:** the line MUST render the actual disclosed predicate
and its actual answer (`age above <threshold>: <answer> — and nothing else.`), with the threshold
read from the verified request object (not hardcoded) and the answer the actual asserted value
(never assumed true); any non-disclosing path MUST say so plainly and MUST NOT render an age claim
— restating D47's outcome-accuracy rule at the field most likely to violate it. The disclosed age
predicate is explicitly carved out of item 5's forbidden-fields list — it is what the user chose to
present, not a document field — while the rest of item 5 stays UNCHANGED. Three implementation
clarifications also recorded, owner-approved, not separately numbered: the `▸ technical:` line
carries the full unmodified report text; the two debug-only probe buttons render a distinct
"Diagnostic OK/failed" summary under the no-site label; and the no-site label also covers a failed
request-object verification, never rendering an unverified origin as a trusted site name (D37 at
the UI layer). §6.2 (item 16, exit-criteria table), §10 (D48 added), §15 are annotated.
The prior revision (v1.25 — 2026-09-01, owner-approved) closes **Q32** in one
owner decision, **D47**, made per NO-GO #10 (PRD amended first, `apps/scanner/` code brought in
line second). Both halves of Q32 are resolved. First, the no-site title: owner CONFIRMED
**"Local scan (no site)"** as the exact wording for a bare mode-A entry's title — the flag in D46
noting it was "a specification made here, not itself owner-confirmed wording" is superseded, not
deleted; that string is now owner-confirmed. Second, the disclosure-summary shape: owner chose,
from three concrete renderings, a **plain-language-first, technical-detail-subordinate** shape — a
four-field block (`Result` / `Sent` / `Shared` / `Identity`) under the existing title line, followed
by a subordinate `▸ technical:` line carrying the machine-shaped detail (mode, evidence plug,
`key_id`, `chip_auth`, transaction id) that item 16/D46 already required to exist. `Identity` is the
plain-language restatement of the D38/D39 per-(origin, zktag) attester-key state (new key/alias
minted for this pairing vs. an existing one reused); only the "new" case's exact copy is
owner-approved, the "reused" case's copy is left at the same register, unconfirmed verbatim.
Stated as a REQUIREMENT: the four lines MUST be accurate per actual outcome, not a fixed template —
a success, a refusal, a masterlist "no", an unmet mint gate, an access-establishment failure, and a
bare mode-A read must each read as what they are, never overstate what was sent, and never read as
success on a failure path; mode A MUST state plainly that nothing left the device (`evidence: []`,
D27). Everything D46 already fixed as UNCHANGED and binding — the value-free constraint (item 5),
the single-`emitReport()`-write-path invariant, the display-only timestamp, and the
accessibility-snapshot note — remains UNCHANGED under this amendment too. §6.2 (item 16, exit-criteria
table), §10 (D47 added), and §11 (Q32 closed) are annotated.
The prior revision (v1.24 — 2026-09-01, owner-approved) records two further
owner decisions from the same session, made per NO-GO #10 (the scope gate — PRD amended first,
`apps/scanner/` code corrected to match second; uncommitted app work already implements a wider
shape than the prior PRD text, which this revision now catches up to). **D45** amends §6.2 item 16
in the part the owner did not intend: item 16 as written both said the log "lists the reports of
successive scans in the session" and required it "cleared whenever a session wipe occurs that does
not keep MRZ/mode" (item 6) — self-contradicting, since `MainActivity.kt` calls
`wipeSession(keepMrzAndMode = false)` at the completed-read call site on EVERY completed read,
including a successful one, so the literal rule wiped the log on the very success path it existed
to record and successive scans never accumulated. Owner chose accumulation: the log's lifetime is
now decoupled from `wipeSession()`'s per-scan `!keepMrzAndMode` branch — a per-scan wipe MUST NOT
clear it. Retention is otherwise unchanged and restated: in-memory only, never persisted to disk,
survives Activity recreation via `onSaveInstanceState` (D35), gone only when the app process is
gone. **D46** records the owner's second, scope-widening call ("logs should be safe and not a
source of threat, but should be there for user to know how it went, what went out and the result,
how much it disclosed, successful or not"; "titled by timestamp, titled by website"): each log
entry MUST carry, besides its existing display-only local wall-clock timestamp, a title
identifying the verified request origin/`scope_domain` (D37, D42) the scan was for, with a bare
local scan (no verified handoff, mode A) titled by the fixed value-free label "Local scan (no
site)"; and the log MUST be legible to a non-engineer about outcome — what went out, to whom, what
was disclosed, whether it succeeded — which SUPERSEDES item 16's "MUST NOT change report content"
clause to the extent that a value-free disclosure summary is now REQUIRED, while the rest of that
clause (no MRZ/names/document fields/key bytes/signatures/nonces/fingerprints/chain contents, the
single-`emitReport()`-write-path invariant, and the accessibility-snapshot note) stays UNCHANGED
and binding; the origin/site name is explicitly NOT a document field. Opens **Q32** (§11): the
exact shape/wording of the disclosure summary is not owner-decided, stated at the level the owner
gave it. Also records a small, non-numbered clarification to **D43**: the three failure classes
item 15 names are confirmed EXAMPLES of its general rule, not an exhaustive list — mint-path
failures (key generation, missing verified request/origin/document-number, no usable device key or
signature, biometric/device-credential error) are in scope and the implementation's wider coverage
is kept. §6.2 (items 15, 16, exit-criteria table), §10 (D43 annotated, D45/D46 added), and §11
(Q32 opened) are annotated.
The prior revision (v1.23 — 2026-09-01, owner-approved) recorded two owner
decisions from a live run where a mistyped document number produced a transient overlay the owner
could not act on. **D43** ("errors that leave the app waiting on the user MUST block until
acknowledged") — owner: "when wrong data in, it is not pop up to dismiss but overlay notification
that disappears, i should get pop up then ok then it resets. i just updated number then tapped
again and worked." Any outcome that ends a scan attempt and requires user action MUST be surfaced
as a modal dialog carrying the value-free reason and an OK action; on dismissal the app performs
the state transition explicitly — keeping the MRZ focused for correction on an access-establishment
failure (F3's `keepMrzAndMode`), or resetting the session on every other failure — matching the
existing rule rather than adding a second policy. Transient (Snackbar) UI stays correct for
purely informational events that change no state (e.g. "QR capture cancelled"). General rule:
transient UI for transient facts, blocking UI for state that requires the user to act. Same
recurring defect class this session already fixed twice (a UI-only write with no log, a silent
`?: return null` in intent parsing): a state change whose only notification is transient or absent.
Dialog text stays value-free, same constraint as the report (§6.2 item 5). New §6.2 item 15.
**D44** ("the per-scan report moves to its own log view, timestamped") — owner: "the feedback of
what happened every scan at the bottom of the app should go to another tab as logs, same output
with timestamp." The value-free report moves to a separate in-app log view accumulating successive
scans' reports, each entry timestamp-prefixed (local wall-clock, display-only, not a proof/evidence
field — contrast D28's midnight-UTC `current_date` coarsening, which is a payload field). Content is
unchanged — the same value-free lines `emitReport()` already produces; the single-write invariant
survives, the log view is an additional consumer of that one path, never a second write site.
In-memory only for the session, never persisted to disk — governed by §6.2 item 6/F1 (MRZ
persistence removed) and D35 (in-memory-only retention across Activity recreation), not NO-GO #9
(which is about secrets/test keys in the tree, not on-device persistence); the same principle NO-GO
#1 states for our servers ("we store nothing server-side, ever") — this is the device-side analogue.
D35's in-memory retention across Activity recreation extends to the accumulated log, which is
cleared whenever a session wipe occurs that does not keep MRZ/mode (§6.2 item 6), so the log's
lifetime never exceeds the session it describes. Value-free by construction, so the accessibility-snapshot caution does not apply to this
view — stated explicitly so the log view is never later treated as a place to add raw fields. New
§6.2 item 16. Also records, as a stated limitation rather than a decision: **first-sight attester
binding (D38/D39) has no re-enrolment mechanism** — a user who factory-resets, reinstalls, or
otherwise loses their StrongBox keys presents a new key for an already-bound `(origin, zktag)` and
is refused `attester_key_mismatch` at every site that already knows them, permanently. Observed as a
real refusal (transactions `Cxn0dXWz8nlJfVX3`, `MstvPR4zJGK4VoSG`, 12:42) when a key-scoping change
invalidated existing bindings — that instance was a staging artifact, but the mechanism is real.
Recorded alongside D29's chip-cloning limitation and D38's TOFU note (FR12, §10); opens **Q31**
(§11) — how re-enrolment should work, options listed not decided (operator-side manual unbinding, a
binding TTL, a device-held recovery secret, or accepting it as permanent) — any mechanism letting a
NEW key claim an EXISTING zktag is exactly the attack first-sight binding exists to prevent, so this
is a genuine tension, not an oversight. §6.2 (new items 15, 16, exit-criteria table), §10 (D38
annotated, D43, D44 added), and §11 (Q31 opened) are annotated.
The prior revision (v1.22 — 2026-09-01, owner-approved) records two owner
decisions closing both items v1.21 left open. **D41** ("leave it") closes the FR12 linkability-class
escalation: `sig-ed25519/1`/`sig-p256/1` keep `linkability: 'signer'`, on the rationale that under
D39 each key is scoped to `(origin, zktag)` — not a stable per-device value, but a fingerprint of one
(device, site, document) triple, exactly the pseudonym the verifier already holds; `'device'` is
reserved for a value stable at every site, permanently (the archetype being a raw key-attestation
chain, D22). The owner then generalised the question — does the class track the technology or the
plug — and D41 records the answer for every future plug: linkability class is measured from what a
plug's payload actually exposes, never inferred from its category. Verified in code: `zk-passport/1`
→ `'none'` (`packages/chiproof/src/plugs/zk-passport.js`), with the one disclosed exception being
D26's `vk_sha256` circuit-class bucket; `sig-ed25519/1`/`sig-p256/1`/`signed-receipt/1` → `'signer'`
(`packages/chiproof/src/plugs/attester-sig.js`, `packages/chiproof/src/plugs/signed-receipt.js`); a
hypothetical, unbuilt `key-attestation/1` → `'device'`. Play Integrity is worked as the test case:
intuition says `'device'`, but M1's own spike (`docs/logs/M1-Q23-EVIDENCE.md`) found no device-unique
field across sites — most likely `'signer'` or arguably `'none'`, explicitly NOT a class assignment,
since that evidence answered a different question; any future Play Integrity plug's class comes from
a fresh probe of its own payload. **D42** ("domain") closes **Q30**: the zktag/evidence signing scope
stays host-only (`scope_domain`, D5/FR2/D38/D39) while D37's origin-consistency check stays the full
origin (scheme+host+port) — confirmed as deliberate, not accidental, matching what
`MainActivity.kt`'s `scopeDomain = URI(verified.origin).host` and `RequestTrust.kt`'s
scheme+host+port comparison already do. Flagged, not fixed: "host" and "registrable domain" differ
for a real multi-subdomain deployment (`a.example.com` vs `b.example.com` share a registrable domain
but are different hosts, hence different pseudonym scopes under the current code); recommended
reading is host (subdomains stay distinct scopes, the conservative default), recorded as a note for
a production deployment since it does not bite at M2's single `127.0.0.1` origin. FR12 and §11 Q30
are annotated; §10 gains D41, D42.
The prior revision (v1.21 — 2026-09-01, owner-approved) records **D39** — a
live run (11:43) with D38's per-origin attester key found the owner scanning an NL ID card then a
US passport at the same origin mint the *same* key, because the attester-key store binds by
`(scope, zktag)` while the key itself was keyed by scope alone; owner: "yeah, isolate, that's a
small leak that this id have two ids" — the Keystore alias now derives from origin AND zktag
(§6.2 item 1), narrowing D38, not reversing it: a key's scope must be at least as narrow as the
identity it signs for. The owner explicitly declined a fraud-detection capability this would have
enabled (spotting one device presenting two documents with different age verdicts) as "not our
place to judge/police" and "borderline creepy/surveillance"; the orchestrator adds the supporting
point that the signal would be false-positive-heavy anyway, since zkagent never binds presenter to
document holder. Also records **D40** — no issuer/country attribute or filter at tiers A/B ("id is
id doesn't matter where it's from... mode C of kyc should have that but others shouldnt"),
distinguished from CSCA trust-anchor curation (§6.2 item 7), which remains a legitimate,
attribute-free operational choice; tier C may carry issuer information per D37's existing
carve-out. Two escalations left open by v1.20/v1.19 are addressed: FR12's `sig-*/1` linkability
taxonomy gap stays at `'signer'`/tier-ceiling-B (orchestrator-recommended, pending owner veto) —
D39 narrows the key further but does not resolve which of the three values fits; and a new
scope-granularity note (§11 Q30, orchestrator flag, pending owner confirmation) records that the
signed scope is host-only while D37's origin-consistency check is scheme+host+port, deliberately —
a pseudonym/key should survive a port change or http→https upgrade, while the security check must
stay exact. §6.2 item 1, FR12, §10, and §11 are annotated.
The prior revision (v1.20 — 2026-09-01, owner-approved) records **D38** — the
first end-to-end mode-B run (10:19–10:20) reached the verifier with a real StrongBox P-256
signature and got `sig_unknown_key`, because `sig-*/1`'s `linkability: 'signer'` classing (D30,
FR12) assumed an operator-pinned attester key list, and nothing said how a verifier learns a
*phone's* self-generated key. Owner: "agree b+c" — **mode-B attester keys are per-origin** (the
Keystore alias is now derived from the verified request's `scope_domain`/D37, not one global
device key, §6.2 item 1) **and the verifier binds key→zktag on first sight** (a pluggable
attester-key store keyed by `(scope, zktag)`, TOFU by default, operator-pinned lists still
supported as an alternative, FR12); item.data gains `pubkey` (`key_id` MUST be recomputed and
compared, never trusted as claimed). A single global device key would have been a stable
cross-site identifier riding inside every mode-B presentation regardless of the zktag's own
domain scoping — the same shape of bug D22/Q23 found in the raw attestation chain, undetected
until this run. `sig-*/1`'s linkability classification does not cleanly fit FR12's three-value
taxonomy under D38 and is recorded as an open escalation, not resolved by invention; stays
`'signer'`/tier-ceiling-B for now. Stated limitation, composing with D29's chip-cloning
limitation without weakening it: TOFU means a clone presenting first, on a different device,
binds the wrong device to that zktag. Also records seven small amendments the owner approved
today ("agree, keep it as is") that v1.19 had not yet written down: D31/chiproof — a presentation
with two alternatives where one is invalid fails whole, no first-valid-wins masking; chiproof
stays 0.4.0 (unpublished, additive); item 14 — a verification failure refuses that handoff and
falls back to manual scanning, it does not halt the session; item 13 — the mode radio disables
the instant a handoff is captured, before verification completes; item 14/D37 — the well-known
path serves a single JWK or a JWKS, first P-256 key, `kid` matched when present on both sides,
mismatched-when-present is a hard refusal; a `testImplementation`-only `org.json:json` dependency
(AGP's unit-test stub silently no-ops, which would have let JSON-parsing tests lie); and the
`evidence_plug: device_preference=…/used=…` and `evidence_required: any-of[…]|absent` report
lines are log-only, never a behavioural input (D36). §6.2 items 1, 9, 11, 13, 14 and FR12 are
annotated; §10 gains D38.
The prior revision (v1.19 — 2026-09-01, owner-approved) records seven
owner decisions from a live mode-B handoff run on the Pixel 6a / NL ID card, real Chrome `av://`
tap → spike verifier (`direct_post` HTTP 200 `accepted:true`, but verdict `evidence_required_missing`
because `spikes/m2-handoff/server.mjs` still required `sig-ed25519/1` only while the device sent
`sig-p256/1` per F2): **D31** — the verifier accepts any one of an operator-configured set of
attester-sig evidence plugs (`sig-ed25519/1`, `sig-p256/1`), superseding D30's single-required-plug
framing for mode B and opening an any-of/alternatives semantic gap in chiproof's `evidence.require`
(currently all-of, `packages/chiproof/src/evidence.js:184`) — opened, then closed same-day by D36;
**D32** — the attester-sig plugs are the reference default only, not privileged; an
operator may configure any registered chiproof evidence plug (e.g. `zk-passport/1`) as its mode-B
requirement, per D24, still gated by the per-tier linkability rule; **D33** — the scanner
preselects and locks the presentation mode from a pending handoff request's `zkagent.tier`,
disabling manual override, failing loudly on an absent/invalid tier (new §6.2 item 13); **D34** —
the scanner verifies the request object's JWS against a pinned/provisioned trusted-signer set
before trusting any field inside it, refusing (never warning-and-continue) on failure — closes the
escalation in `HandoffClient.kt`'s class doc, narrows D20 for this build specifically (new §6.2
item 14), opened Q29 (trusted-signer provisioning), closed same-day by D37; **D35** — the value-free
report MAY survive Activity recreation in-memory (already implemented; approval recorded); **D36**
— a device never chooses to downgrade evidence/key strength, only falls through to the next
preference on failure of the preferred one (closes Q28; `DeviceKey`'s `winnerPreference` already
implements this); **D37** — request trust is origin-bound, not authority-bound: the scanner MUST
enforce `client_id`/`request_uri`/`response_uri` origin consistency and fetch the request-signer
key over TLS from a well-known path under that origin (closes Q29; EU AV-profile-shaped, Annex A
TLS/Web PKI root of trust), with tier-C operator-curated allow-lists and OS-level trust for
direction 2 (the requester trusting the app) recorded as a stated, not mitigated, limitation
alongside chip cloning (D29). §6.2 items 4 (F5 closed, not reproduced), 6, 8, 9, 11 (chiproof pin
and `sig-p256/1` naming status corrected), and 14 (origin binding + well-known key path, D37) are
annotated; the exit-criteria table gains two rows.
Evidence: commit `9f60489` (handoff off the main thread — item 8 had never executed in the scanner
before 2026-09-01 — and `response_uri`/`state` read from the request object's top level, not
`zkagent.challenge`).
The prior revision (v1.18 — 2026-08-31, owner-approved) resolves F2 (the
M2 build's own riskiest-assumption POC, `docs/logs/M2-SESSION-POC.md`) as **algorithm agility**:
the app selects the strongest key algorithm the device supports and reports which, the verifier
accepts more than one signature algorithm, and the adopter/operator chooses by their own
priorities exactly as the evidence slot itself works (D24) — §6.2 items 1, 9, and 11 are amended
accordingly (candidate decision, `Dn` pending; no `Dn` assigned this revision). The prior
revision (v1.17 — 2026-08-31, owner-approved) writes the M2
build scope into the PRD per NO-GO #10, now that M2's opening riskiest-assumption POCs are all
PASS on both documents (`docs/logs/M2-SCAN-EVIDENCE.md`, `M2-CAPTURE.md`, `M2-CONFORMANCE.md`) —
new §6.2, referenced from the M2 row, as twelve MUST/MUST NOT items covering the device key,
biometric gate, the always-read/conditionally-mint app-side gate, mode capture, the no-DG1-
rendering rule, session-state lifecycle, the masterlist two-bucket rule, handoff scope, evidence
defaults (`sig-ed25519/1` layout stated once), network config, explicit non-goals, and a proposed
riskiest-assumption POC for the build itself — the four items that shipped as candidate decisions
(masterlist CMS bundling, network-config debug/release split, the build's own POC, and the device
key's D30-attester-key role) were settled by the owner on 2026-08-31 (§15). The prior revision (v1.16, draft) records an interaction between two already-closed decisions, not a new decision (owner-approved 2026-08-31): D9 (mode-B derivation field = `document_number`, which lives in DG1) and D29 (mode B accepts non-chip-auth documents) combine so that mode-B uniqueness and blocking are forgeable for any document presenting with `chip_auth: false` — a cloned chip replays DG1 verbatim and mints the identical zktag as the genuine holder, inheriting their pseudonymous reputation and any block placed on them; the guarantee holds only where `chip_auth: true` (D21). The D9 and D29 rows are annotated, Q18's closure note is cross-referenced, and FR11 gains the one-place conditional-uniqueness statement. **Same revision, owner-approved 2026-08-31 (orchestrator-recommended):** reconciled the §6 M0 row's "master list with the issuing CSCA removed → MUST yield `ok:false`" wording against what M0 actually implemented and evidenced (`docs/logs/M0-EVIDENCE.md` Finding 5) and what the §6 M2 row already states for the on-device masterlist checkpoint (`docs/logs/M2-SCAN-EVIDENCE.md` TEST 2) — a masterlist *integrity* failure (truncated/half-loaded, certs-parsed ≠ certs-declared, unparsable) ⇒ `ok:false` (could not check), while a well-formed, integrity-checked masterlist that simply lacks the issuing CSCA ⇒ `ok:true, allowed:false` (a real no, issuer-untrusted), because a missing issuer in the adopter's own pinned trust is the adopter's answer, not a verifier failure. The M0 row's negative (ii) is annotated as superseded and the M2 row's masterlist line gains the explicit two-bucket statement; FR10 and D21 were checked as candidate homes for a third clause and neither fits (FR10 is the client trust list, D21 is chip authenticity, not masterlist/CSCA trust), so no clause was added there. v1.15 records owner decision D30 (2026-08-31): `sig-ed25519/1` — an attester-held Ed25519 signature over the challenge binding — becomes the DEFAULT evidence delivery for mode-B presentations, amending (not reversing) D27: mode A and the default presentation stay bare, mode B ships with live proof the evidence slot works; FR12 gains the registry entry (linkability class 'signer', tier ceiling B — ceiling orchestrator-recommended, owner may veto), and the D27 and M2 rows are annotated. v1.14 settled two owner decisions on 2026-08-31: D9 closes — the mode-B zktag derivation field is the **document number**, on M0 evidence (`docs/logs/M0-EVIDENCE.md` Findings 3, 10, 11): present on every ICAO 9303 document (maximum `acceptedDocuments` coverage, D14), deterministic and collision-free per M0, rotating at document renewal (~10-yearly) — accepted as within the captcha-grade bar, with renewal stability itself still unmeasured; and D29 closes Q18 — mode B accepts documents without chip authentication, the verifier reports `chip_auth` per D21, and every claim states explicitly that clone-replay of non-chip-auth documents is within the captcha-grade bar (adopters may tighten; the reference posture does not; mode A unaffected). The status line, D9 row, Q18, FR11, and the M2 row (which gains its riskiest-assumption POC opener) are updated accordingly. v1.13 settled two owner decisions on 2026-08-30 (late evening): D27 closes Q25 — the M2 reference scanner app ships bare (`evidence: []`) as its one fixed evidence set (FR6), captcha-grade and honest per NO-GO #5, avoiding an unvetted mobile prover and the D26 disclosed bucket; and D28 closes Q27 — `current_date` is coarsened to day granularity client-side (midnight-UTC of the scan day), eliminating second-level scan-session correlation across sites at the cost of a 1-day floor on `max_scan_age`. The M2 milestone row, FR12's `zk-passport/1` entry, and Q25/Q27 are updated accordingly. v1.12 settled owner decision D26 on 2026-08-30 (late evening), on evidence in `docs/logs/M1B-EVIDENCE.md` §4–§5 and the leak-closure spikes: disclose the `zk-passport/1` circuit-class bucket (`vk_sha256`) in tier A rather than trying to remove it; Q15 closes on this evidence; Q27 opens (whether to coarsen `current_date` to day granularity); FR9/FR12/D19 and the M1b milestone row are updated to record the run and the disclosure. v1.11 settled owner decision D25 on 2026-08-30 (late), on evidence in `docs/product/learnings.md` §3 (entry "zkPassport age circuit cannot be nonce-bound while keeping a stable nullifier"): `zk-passport/1` ships in M1 tier-A-only, the challenge nonce carried via `service_subscope`; Q26 opened (tier B/C ZK evidence needs a circuit exposing both a stable nullifier and a fresh nonce); the M2 row moves from D23-era wording to D24 evidence-set language; FR12 gets a one-clause `zk-passport/1` annotation. v1.10 settled owner decision D24 on 2026-08-30 (late), on evidence that Play Integrity tokens are non-borrowable — decoding is tied to the app developer's own Google Cloud project (non-transferable per ToS, per-app quota): the core ships with an evidence slot, v1 works with it empty, and what fills it is the adopter's choice; D1 amended, D23 superseded in the specific claim that Play Integrity is a shared voucher, FR12 (evidence registry) added, the M1 row now names its deliverable as a spec, and Q25 opened. v1.9 settled Q23 on owner decision D23: v1 attestation is voucher-grade (Play Integrity), D1 stands, and ZK over the passport is a named second track with written gates rather than a maybe; Q24 (de-Googled devices) opened and accepted for v1; risks 4/7/8 and D2 annotated; the M1 row updated. v1.8 relaxed tier A's same-site unlinkability promise to a non-goal (D22), keeping cross-site unlinkability as the requirement; re-framed Q23 around "nothing stable across sites"; and queued a Play Integrity spike. v1.7 recorded the M1 POC run on hardware key attestation: risk #4 holds, risk #8 confirmed, Q23 opened, masterlist verification moved to the phone, and the M1 row rewritten. v1.6 recorded the post-M0 disclosure model — three tiers, signed challenges, always-read/conditionally-mint — **as decisions of shape only; mechanics are deferred to M1/M2 (D19–D21, Q21)**. v1.5 rewrote the M0 row so the spike can fail, named the documents and the access protocol, and added the M0 go/no-go table (§6.1). v1.4 restructured the document into rungs, split disclosure into two modes (D13), and narrowed FR6.
**Companion**: `docs/product/zkagent-design.md` — the design and disclosure model: how the read works, what each mode emits, the legal posture, and the operator's configuration surface. Description, not commitment; **this PRD wins on any conflict**.

**One-liner**: Read the chip in a government-issued document, verify the government's own signature on it, and answer exactly one question about the holder — *over 18?* or *seen here before?* — disclosing nothing else, storing nothing anywhere, with no issuer, CA, wallet or server of ours in the path.

**Claim discipline (standing, applies to every sentence anyone writes about this project)**: the project name may be aspirational; the *claims* may not. v1 is attested selective disclosure, not zero-knowledge. Nothing shipped, published, committed or spoken may describe v1 *as a product* as a zero-knowledge proof (NO-GO #5, NO-GO #7) *(amended 2026-08-30, D24/D25)*: the sole exception is naming `zk-passport/1`'s own content — one evidence type in the FR12 registry, validation-grade, tier A only — where "zero-knowledge proof" describes what that evidence type carries, never the product, the app, or the tag. See §2.1 for the plain statement of why.

---

## 1. Problem

Two gaps, one root cause, one chip that closes both.

**Agent accountability.** Agent traffic passed human traffic and every emerging auth standard (Web Bot Auth, Visa TAP, Google AP2, OAuth on-behalf-of drafts) roots trust in a **vendor** ("OpenAI's agent") or a **custodial account** (an IdP login — free, infinite, ban-proof). The IETF's own drafts name the gap: the `sub` claim is "routinely overloaded… without a standard classification mechanism"; delegation chains have no anchored origin. The personhood-credentials literature (arXiv 2408.07892, 2501.09674) calls for exactly the missing piece — a unique, privacy-preserving human root — but assumes an *issuer* nobody has stood up.

**Age verification.** Present demand, legal deadlines in several jurisdictions, and incumbent solutions (ID upload, credit-card check, face estimation) that are expensive, privacy-hostile, or both. The EU's own answer requires a wallet, an attestation provider, and a batch-issuance round trip — infrastructure that must exist before a user can prove anything.

**The root cause is the same: everyone assumes an issuer.** zkagent supplies the root issuer-free — the government already issued the credential and it is in the holder's pocket. The chip **is** the attestation provider.

## 1.1 Glossary — three objects that must never share a name

The conversation history of this project has used "nonce" for all three of these. They are different
objects with different lifetimes, and the mode-A claim depends on keeping them apart.

| Term | What it is | Made by | Lifetime | Present in | Can it recognise the holder? |
|---|---|---|---|---|---|
| **challenge nonce** | a random single-use number inside the requester's signed challenge (FR4) | the **requester**, fresh per request | one request, then spent | **all tiers — A, B, C** | **No.** It is the requester's number, not the holder's; nothing in it derives from the chip. Reuse is rejected by the verifier, so it cannot be used to correlate two visits — the replay protection and the unlinkability protection are the same mechanism |
| **secret** | material derived from chip data, held in the phone's secure hardware (D10) | the **app**, at scan time | until the operator's ceiling (30–180 days), then re-scan | B and C only — **never minted in A** | Never leaves the phone; never transmitted to anyone |
| **zktag** | `HMAC(secret, requester's verified domain)` — the pseudonym (FR2, FR11) | the **app**, per presentation | stable for the life of the secret; same site ⇒ same tag | **B and C only** | **Yes — at that one domain only.** This is what makes tier B "recognisable here"; another domain computes an unrelated tag |

**One line to remember**: the *nonce* proves the **request** is fresh; the *zktag* proves the **person** is
the same. Tier A wants the first and refuses the second. Anything called "minted id" in earlier notes
means the secret + zktag pair, not the challenge nonce.

## 2. What v1 is

```
SCANNER APP (thin native Android app; the ONLY native piece)
  1. NFC-read the chip                  (vetted lib: JMRTD — never our parsing)
  2. Verify the government signature    (public masterlist — ICAO PKD / BSI; no CA of ours)
  3. Evaluate the verifier's request against the chip contents
  4. Attest that unmodified code ran steps 1–3     (root chosen at Q14)
  5. Hand the answer to the web flow    (QR / app link)

  ── MODE A · ANONYMOUS (default) ─────────────────────────────────
     out: { claims: { over_threshold: bool }, attestation, challenge }
     NO identifier of any kind. Two presentations by the same holder to the
     same service are unlinkable. Nothing is derived, nothing is cached that
     could become an identity. This is the age-verification mode. (D13)

  ── MODE B · PSEUDONYMOUS (opt-in, requested in the challenge) ───
     secret = KDF(chip stable data)     (never leaves device; Keystore/StrongBox,
                                         biometric-gated, max age enforced — D10)
     zktag  = HMAC(secret, verified service domain)
     out: { zktag, claims{…}, attestation, challenge }
     Linkable WITHIN one service — that is the point: dedupe, blocklist,
     "have I seen this human before." Unlinkable ACROSS services. (D13)

VERIFIER SDK (Node, stateless — the npm package `chiproof`, services install it)
  verify attestation → verify challenge nonce (single-use)
  → check client against adopter trust list (FR10)
  → mode B only: check adopter-supplied blocklist
  → verdict { ok, allowed, reason }

AGENT LAYER — RUNG 2, see §4. Not v1.
```

- Chip data never leaves the phone. In mode A a service sees one bit and a signature chain; in mode B it additionally sees a pseudonym scoped to its own domain.
- Mode B: same human + same service = same zktag, forever (deterministic). Different services = unlinkable zktags. Ban the zktag → every agent of that human dies at that service, with no re-mint (no second passport).
- Forging requires beating hardware attestation or forging a government chip — far above the captcha bar this product promises. **Not above a bank's bar. Never claim otherwise.**

### 2.1 Why this is not zero-knowledge, stated plainly

A zero-knowledge proof lets the verifier check the mathematics itself; the proof carries the guarantee. zkagent is structurally different:

| | What the verifier does |
|---|---|
| ZK | "Here is a proof. Check it yourself." |
| zkagent | "Here is an answer. A hardware vendor attests that unmodified code computed it." |

The verifier never checks a proof — it checks an attestation that unmodified code ran, then believes the number that code produced. Break the attestation and the claim collapses; with ZK, breaking attestation buys you nothing because the mathematics still has to hold. The *privacy outcome* in mode A is comparable (one bit crosses the wire). The *trust model* is not. D1 and NO-GO #7 forbid ZK circuits *of ours* in v1 — not built, not vendored, not scaffolded *(amended 2026-08-30, D24/D25)*: third-party ZK proofs may enter only as evidence (D24/D25) — the `zk-passport/1` plug in the FR12 registry, validation-grade, tier A only, verifying zkPassport/Barretenberg circuits that are not ours, with Track Z's gates governing any security claim about them.

**Uniqueness is not a ZK property and never was.** ZK gives selective disclosure with proof. Uniqueness comes from the credential being scarce — one passport per person. The two pull against each other: a ZK age proof is unlinkable by construction, which makes it *useless* for "have I seen this person before." 8een cannot do uniqueness, by construction. zkagent can. That is the strongest argument for zkagent existing as a separate project at all.

## 3. The invariant (inherited from 8een, adopted verbatim)

**`ok` (did the checker manage to check) is separate from `allowed` (what the answer was), and `ok:false` ⇒ `allowed:null`, never `false`.**

A broken verifier saying "no" is indistinguishable from a working one — it would turn away every legitimate human while looking healthy. Concretely:

| Condition | Verdict |
|---|---|
| holder under the requested threshold | `ok:true, allowed:false` (real no) |
| zktag on blocklist (mode B) | `ok:true, allowed:false` (real no) |
| attestation invalid / nonce replayed / client not on trust list | `ok:true, allowed:false` (real no) |
| masterlist half-loaded, blocklist store unreachable, attestation root unreachable | `ok:false, allowed:null` — never a "no" |

Corollary (8een's recurring bug shape, found 7+ times there): **never trust a health check, a config value, or a client-supplied field.** The masterlist is a PEM list — assume it can silently half-load (19-in-file-17-parsed) and prove it can't.

## 4. Rungs — what ships when, and what is deliberately not yet built

The PRD previously described one product with five milestones. It is two rungs, and only the first is v1. Everything in rung 2 is real, decided, and **not being built yet**; it stays in this document so it is not re-invented, not because it is in scope.

| Rung | Contents | Milestones | Status |
|---|---|---|---|
| **1 — the core** | Chip read, government-signature verification, mode A age bit, mode B zktag, attestation, verifier SDK, demo. Published as `chiproof`. | M0–M3 | **This is v1.** Nothing else is. |
| **2 — the agent layer** | Delegation certs, RFC 9421 request signing, per-serial revocation, blocklist + pseudonymous appeal. | M4–M5 | Decided, specified (FR5/FR8, Q8), **not started**. Requires rung 1 shipped and at least one real adopter. |

**Rung 1 ships both modes together.** The zktag is the novel bit and it does not get deferred — but it is opt-in per presentation (D13), so the age wedge does not carry it.

**Track Z (parallel, gated by D23): ZK over the passport** — spikes/m1-zk; no milestone until the gates hold.

**The core is borrowable.** Anyone — including a government — may embed `chiproof` in their own app. We deliver the core; wrap it in your app or use ours. This is a one-sided sale (the adopter brings their own users) and is the single largest mitigation available for the two-sided-market problem in §14. It has two hard prerequisites, both now requirements: the derivation must be a **published spec** (FR11), or two clients fork the identity space; and the verifier must decide **which clients it accepts** (FR10), or the openness that makes the core borrowable also makes it forgeable.

## 5. Goals / Non-goals

**Goals (rung 1)**
- G1: A holder proves one attribute from a government chip, Android-first, with no issuer, no account, no PII handled by anyone.
- G2: In mode A, a service learns exactly one bit and cannot link two presentations by the same holder. Measured, not asserted (FR9).
- G3: In mode B, one human ↦ one stable zktag per service, unlinkable across services.
- G4: A service adopts with one npm install, zero PII handling, and its own trust list.

**Goals (rung 2, not v1)**
- G5: Agent delegation certs + RFC 9421 request verification riding the zktag.
- G6: Blocklist + pseudonymous appeal, all state adopter-supplied.

**Non-goals (v1) — the scope-creep magnets, named explicitly**
- ZK circuits (D1 — future tier; not built, not vendored, not scaffolded).
- iOS (deferred until demand justifies $99/yr and a Mac or cloud-Mac build path; design must not preclude App Attest).
- Money/payments, legal-grade identity, one-person-one-vote (k-bound makes it dishonest — NO-GO #5).
- Becoming an EU wallet, an attestation provider, or an eIDAS-conformant component. zkagent may share privacy *properties* with the EU Age Verification Solution (§12) — it is not part of that ecosystem and must never imply certification. zk8een remains the bridge if one is ever needed.
- **Non-chip documents.** The readable set is **ICAO 9303-compliant chip documents** — anything carrying an SOD signature chain verifiable against a public masterlist. **Explicitly excluded and not "later":** US driving-licence PDF417 barcodes and equivalents (AAMVA-encoded text with *no verifiable signature*, so step 2 has nothing to check and anyone can print one); photos; photocopies; OCR. Signed mobile credentials (US mDL) may qualify eventually but are a separate read path.
- **Predicates beyond a single threshold per presentation — in tiers A and B.** Tier A discloses exactly one bit; tier B adds exactly one pseudonym. No nationality, no residency, no under-18 spaces, no "and also". Identifying predicates exist only in **tier C** (D19), come only from the published verb list, and every new verb is a spec revision plus owner sign-off (NO-GO #10). Each added verb leaks anonymity bits; the tier model exists so that cost never touches tier A.
- **Unifying identities across documents.** Explicitly rejected as a goal (owner, 2026-08-07). A holder with two documents holds two identities and that is fine. See D9 and NO-GO #5.
- **signedreply / attestation-ledger / reputation integration** — separate product; zkagent must stand alone.
- **Federated or shared blocklist service** — rung 2 defines the signed blocklist *format* and adopter store interface only. We run no list, host no reputation, publish no trust list.
- **Browser extension, desktop scanner, or any second client** — one scanner app, one verifier SDK, one demo page. Nothing else.

## 6. Milestones — small buckets, each with a checkpoint

| M | Rung | Deliverable | Checkpoint (evidence, not prose) |
|---|---|---|---|
| **M0 — POC at the riskiest assumption** | 1 | Throwaway spike on the owner's **Pixel 6a, stock Android 17** (D2; last OS for this device, security patches to 2027-07 — the attestation horizon for M1–M2 is that date plus the 12-month strong-integrity grace). Vehicle: a fork of an existing open-source JMRTD Android reader (non-telemetry build flavour only, DG2 read removed), not a from-scratch app. **Documents: the owner's US passport (primary) and NL identity card (second document, PACE-only since 2022).** Access protocol is **whatever the chip announces** — BAC or PACE, negotiated by the library, never hardcoded per country; the MRZ key (document number, DOB, expiry) is **typed by hand** in M0, never stored, never in source. Read **DG1 + SOD only**, plus DG14/DG15 to probe CA/AA. Verify SOD against the **BSI-published all-country master list** (public ZIP) and assert the issuing CSCA — and any link certs — is present. Derive a **candidate zktag per stable field** (document number; DG1 optional-data field; full-DG1 hash; AA public-key hash if DG15 exists; CA public-key hash if DG14 exists) against a fixed test domain; **rescan → each candidate identical**; second document → every candidate different. **Planted negatives, mandatory**: (i) flip one DG1 byte → passive auth MUST fail; (ii) master list with the issuing CSCA removed → MUST yield `ok:false`, never a `no`. **Superseded 2026-08-31 (owner-approved 2026-08-31, orchestrator-recommended) — see §6 M2 row and `docs/logs/M0-EVIDENCE.md` Finding 5:** this negative's wording was wrong; a well-formed masterlist that simply lacks the issuing CSCA is a real no (`ok:true, allowed:false`, issuer-untrusted, as M0 actually implemented and evidenced), not `ok:false` — `ok:false` is reserved for masterlist *integrity* failures (truncated/half-loaded, certs-parsed ≠ certs-declared, unparsable). **Report which data groups and fields each chip actually contains, and which of AA / CA each supports** (feeds D9, D14, Q12, Q18) | Two scan runs per document logged with matching candidates; both negatives observed to fire; timings measured at four marks (tag→access established, →DG1+SOD read, →PA verified, →derived); master-list certs-parsed asserted equal to certs-declared in the CMS; chip field inventory and AA/CA support recorded per document; **no PII value in the evidence doc — field names and hashes only.** Evidence doc `docs/logs/M0-EVIDENCE.md`. POC is thrown away, never shipped |
| **M1 — Verifier SDK core** | 1 | `chiproof` verifier core: never-throw verdict; challenge nonce (port 8een `challenge.js`) + D20 signed challenges; tier negotiation (D19/D22); FR10 trust list; **evidence slot (D24) with `require: []` bare mode and the `zk-passport/1` plug driven by the real proofs from spikes/m1-zk; `signed-receipt/1` plug**; masterlist stays on the phone. Spec: docs/product/m1-verifier-core-spec.md. Packaging per LIBRARY_CONVENTIONS: JSDoc→`.d.ts` typecheck gate, `chiproof.context.md` adopter contract, CI. | Full negative matrix (replayed nonce, expired/unsigned tier-C challenge, tier mismatch, untrusted client, missing required evidence, evidence bound to another nonce, evidence for another claim, plug throwing ⇒ `ok:false` not `allowed:false`, device-class evidence in tier A refused) each paired with a non-vacuity pass; zero runtime deps for the core; the zk plug verifies tonight's real NL/US proofs and rejects the planted-negative proofs. |
| **M1b — Mode-A unlinkability probe** | 1 | Black-box byte comparison of N mode-A presentations from the same device, same holder, same service, borrowing 8een §7.3 method **including a planted positive control** | No field differs across presentations except those proven independent of holder and device. **A planted stable field must make the check fail** — a guard you have not watched fire is not a guard. Blocks M3. Answers Q15. Revised by D22: fields stable across presentations to the same site are acceptable; fields stable across sites are not. **Ran 2026-08-30 — evidence docs/logs/M1B-EVIDENCE.md; passes under D22 with one disclosed bucket (D26); blocks lifted for M3.** |
| **M2 — Scanner app (rewrite, not graduate)** | 1 | Real app: Keystore/StrongBox, biometric gate, QR/app-link handoff; ships **BARE (`evidence: []`) as its one fixed evidence set** (FR6, D27 closes Q25) — no on-device ZK prover; `zk-passport/1` stays a verifier-side plug exercised against real desktop-generated proofs (D25), not built into the reference app; the evidence slot itself exercised end-to-end against the M1 core — the mode-B roundtrip exercises `sig-ed25519/1` as the reference default evidence (D30). **Opens with its riskiest-assumption POC** (owner-approved 2026-08-31, per the standing per-module rule): (a) capture 1–2 real-world age-verification request flows from live deployments (the EU AV Blueprint flow shape plus a live UK OSA-era site) and record the observed wire shape **including the invocation mechanism each flow actually uses** — redirect, iframe/postMessage, QR, or the Android Digital Credentials API (Credential Manager / `navigator.credentials.get`, OpenID4VP-shaped per the Blueprint); (b) build the test verifier website to that observed shape, not an invented one; (c) run the web→app→web handoff roundtrip on the Pixel 6a against it **over whichever mechanism the captures show is the real-world shape** (QR/app-link stays the named fallback and the cross-device path) — before any of the easy parts are built | End-to-end on real device against local verifier; zktag stability across app reinstall + re-scan measured; mode A confirmed to emit no zktag even after a mode-B presentation on the same device. Half-loaded masterlist on the phone ⇒ read refused (`ok:false`), never a pass. Two-bucket rule for the on-device masterlist checkpoint (owner-approved 2026-08-31 (orchestrator-recommended)): masterlist *integrity* failure ⇒ `ok:false` (could not check); a well-formed, integrity-checked masterlist that lacks the issuing CSCA ⇒ `ok:true, allowed:false` (a real no, issuer-untrusted) — exactly what `spikes/m2-scan` implements and `docs/logs/M2-SCAN-EVIDENCE.md` TEST 2 evidences. **Build scope for the rewrite itself, written per NO-GO #10 before the build starts: §6.2.** |
| **M3 — Demo** | 1 | Web page: "prove you're over 18" (mode A) and "prove you're a unique adult human" (mode B); responsive (mandatory) | Live flow. Mode B: second scan from same passport rejected as duplicate zktag (uniqueness shown, not asserted). Mode A: two presentations indistinguishable. *Clarification vs NO-GO #1: the demo site is its own adopter and keeps its own store — the SDK still stores nothing* |
| **M4 — Agent layer** | 2 | Delegation certs, RFC 9421 middleware (FR8), per-serial revocation, phone→agent cert handoff (D-Q9) | Agent request accepted with valid chain; killed by zktag-block; single-use serial burns once; signature verifies against an off-the-shelf RFC 9421 verifier with no zkagent-specific patches; cert reaches a headless agent host with no zkagent-run server in the path |
| **M5 — Blocklist/appeal** | 2 | Signed blocklist format, adopter store interface, prove-control-of-zktag appeal | Replay 8een's store pattern: fails closed, never silently falls back to in-memory |

One milestone at a time. Each works alone before the next integrates.

### 6.1 M0 go/no-go — what each outcome means, written before the run

| Observation | Meaning | Consequence |
|---|---|---|
| Phone never establishes BAC/PACE with a document that KYC apps have read | Harness or library problem, not a chip problem | Stop. Debug the spike (typed MRZ, library version, NFC settings) before believing anything else |
| Access works, DG1 + SOD read, passive auth passes on the genuine document | Baseline holds | Continue |
| Passive auth fails on a genuine document | Assume harness bug first (digest algorithm, chain building) — a real finding only after the genuine path is shown to work on the other document | Debug before recording |
| Either planted negative does **not** fire | The checker cannot say "no" — every positive result above is void | Stop. Fix the negative before any result is written down |
| Issuing CSCA absent from the BSI list | Risk #3 is live for that country | Try the ICAO master list (terms form). If absent there too, record it: "issuer-free" is weaker than assumed for that issuer, and this goes in every claim |
| A candidate differs on rescan | Field is not stable — excluded from D9 | Record which; D9 chooses among the stable ones |
| Neither AA nor CA on a document | Clone-replay is not detectable for that document | Q18 resolves per D14: mode A unaffected; mode B for that document is **captcha-grade, clone-replayable, and the claim says so** |
| Only one of AA / CA present | Sufficient for Q18 on that document | Mode B uses whichever exists; the chip decides, never the code |
| Full read > 10 s wall-clock | UX question, not a stop | Recorded for M2 (Q16); not an M0 failure |

The rule this table enforces (AGENT_RULES): the test must be able to fail, and a result that confirms what we hoped is audited for harness confounds before it is believed.

### 6.2 M2 build scope (v1.17 — 2026-08-31, owner-approved)

The M2 opening POCs (§6 M2 row) all PASS on both documents (`docs/logs/M2-SCAN-EVIDENCE.md`,
`M2-CAPTURE.md`, `M2-CONFORMANCE.md`). This section is the SCOPE gate for the rewrite itself
(NO-GO #10) — nothing below is built until it is written here.

1. **Device key.** MUST generate an Android Keystore keypair (StrongBox-backed where available)
   at first run; this is the app's own attester key (D30, FR12) — it signs the challenge binding
   for mode-B presentations. MUST NOT ever leave the Keystore. MUST NOT feed zktag derivation —
   D9's derivation input is chip data (`document_number`) only; a device-stable signing key
   entering derivation would reopen Q23's per-device linkability problem D22 closed. (Per-module
   memory rule: CPU/enclave material never enters identity derivation.) (role confirmed by owner
   2026-08-31: this key is the D30 attester key and nothing else)
   **Amended 2026-08-31, owner decision (algorithm agility, F2 resolved — original single-algorithm
   clause superseded, not deleted; see `docs/logs/M2-SESSION-POC.md` F2 for the evidence and PRD
   §15/§6.2 item 9 for the paired evidence-registry change):** the app MUST select, at first run,
   **the strongest key algorithm the device actually supports**, and MUST report which algorithm
   it selected as part of the attester-key state it exposes. The verifier side (item 9) MUST
   accept more than one signature algorithm rather than assuming a single fixed one. Evidence
   basis, stated once: Ed25519 is unavailable as an Android Keystore key on the Pixel 6a at
   either security level, by either entry point (`docs/logs/M2-SESSION-POC.md` F2 — a dedicated
   KEY TEST confirmed this is a hardware/platform gap, not a provider quirk); P-256 in StrongBox
   is available on this device, per-use-auth-bindable (proven live, biometric/device-credential
   bound), and its signatures verify off-device (independently confirmed by the orchestrator with
   `openssl`). Default posture: **hardware-backed P-256 where StrongBox exists** — it is the
   algorithm Android guarantees at that level on hardware like this — and **software Ed25519
   only where the adopter prefers algorithm uniformity over hardware custody**, with that trade
   (software-extractable key vs. hardware-confined key) stated plainly to the adopter making the
   choice. This is a **candidate decision, `Dn` pending** — no decision number is assigned yet.
   The plug this requires in `chiproof` is referred to only as a **P-256 evidence plug**
   (candidate name `sig-p256/1`); no other plug name is invented here.
   **Amended 2026-09-01, owner decision (D38, "agree b+c" — original amendment above kept, not
   deleted):** "at first run" above is superseded — the app generates **one keypair per verified
   request origin (`scope_domain`, D37)**, not one global keypair for the device. The Keystore
   alias is derived from `scope_domain`; the first mint at a new origin generates that origin's
   key (StrongBox where available, same F2/D36 algorithm-preference order per key); a later mint
   at an already-seen origin reuses its key. `device_key: created this mint | reused existing
   alias` (item 9/report lines) now refers to the per-origin key, not a single device-wide one.
   **Why:** the first end-to-end mode-B run (2026-09-01, 10:19–10:20) reached the verifier with a
   real StrongBox P-256 signature and got `sig_unknown_key` (`packages/chiproof/src/plugs/attester-sig.js:130`)
   — nothing in the PRD said how a verifier ever learns a *phone's* self-generated key, since
   `sig-*/1`'s `linkability: 'signer'` classing (D30, FR12) assumed an operator-pinned attester
   list, which fits a government/vendor attester but not a device generating its own key. A single
   global device key would also have been a **stable cross-site identifier riding inside a mode-B
   presentation regardless of the zktag being domain-scoped** — the same shape of bug D22/Q23
   found in the raw attestation chain, undetected until this run. Per-origin generation closes it
   the same way the zktag itself is closed: by construction, not by policy. MUST require biometric (or device-credential fallback) authorization
   before minting — i.e. before zktag emission or Keystore-key signing — per D21 ("always read,
   conditionally mint"). MUST NOT gate the chip read itself; D21 reads unconditionally.
   **Amended 2026-09-01, owner decision (D39, "yeah, isolate" — D38's per-origin scheme above kept,
   narrowed not reversed):** the Keystore alias is derived from BOTH the verified request origin
   (`scope_domain`, D37) AND the zktag, not from origin alone — two documents presented at the same
   origin now mint two unrelated keys. **Why:** a live run (2026-09-01, 11:43) scanned an NL ID card
   then a US passport at the same origin under D38's per-origin key; both minted with the identical
   key (`key_id=c303cf3f731b5307`, `reused existing alias`) because the attester-key store binds
   `(scope, zktag)` (FR12) while the key itself was keyed by scope only — a site could observe that
   two different pseudonyms shared one device key, learning the two identities are the same phone.
   General rule: **a key's scope must be at least as narrow as the identity it signs for**; a
   coarser key leaks the finer identifier, the same shape of bug D22/Q23 found in the raw
   attestation chain and D38 found in a single global device key. Cost, stated plainly: one
   StrongBox key per (site, document), generated on first use at that pairing; per-origin
   (site-only) keys already minted under D38 are left in the Keystore, not deleted or migrated.
   **Considered tradeoff:** isolation removes a verifier's ability to notice that one device
   presented two different documents (e.g. one below and one above an age threshold). Owner:
   "counterargument is someone trying to use ids are not theirs and both answer differently once
   below age one above age, i think this is not our place to judge/police and that's a borderline
   creepy/surveillance." Supporting technical point (orchestrator-supplied): the capability would
   not have solved borrowed-document use anyway — zkagent never binds the presenter to the document
   holder (no biometric match against the document photo; the device credential authenticates the
   *device owner*, not the document subject) — so "same device, two documents" has legitimate
   explanations (dual nationality, a shared family device) and would be a false-positive-heavy
   signal bought at a real privacy cost.
3. **Always read, conditionally mint.** MUST gate zktag emission in-app on
   `passiveAuth.ok && passiveAuth.allowed === true` (F7, D21, owner-confirmed 2026-08-31), on top
   of whatever the verifier enforces via `evidence.require`/tier. A masterlist real-no (§6.1,
   `ok:true, allowed:false`) MUST derive and emit no zktag.
4. **Mode capture.** MUST read presentation mode from one source of truth at the instant a chip
   session begins and bind it into that session's state; MUST NOT re-read a UI control later to
   decide mode. F5 (mode-radio bug: UI showed Mode B, executed mode was A, root cause not found
   in `MainActivity.kt` as written) is OPEN — M2 MUST either root-cause it or restructure capture
   so it is structurally impossible, not assume this rewrite fixes it by construction.
   **Closed 2026-09-01: not reproduced under the structural fix; the earlier observation is
   attributed to the default-A radio state being left selected, not a capture bug.** **See item 13
   below (D33): when a handoff request is pending, mode is no longer read from the RadioGroup at
   all — it is preset and locked from the request's `zkagent.tier`.**
   **Amended 2026-09-01, owner decision (D51 — the mode radio is REMOVED; mode is DERIVED, not
   chosen; original clauses above kept, not deleted):** mode is no longer a user choice at all. A
   verified handoff's tier determines it; a bare local scan with no verified request is mode A by
   definition. The RadioGroup control is replaced by plain TEXT displaying the derived mode — there
   is nothing left for the user to set. **Security reasoning, stated because it is the general
   rule:** this eliminates F5's entire bug class BY CONSTRUCTION — a UI control that can disagree
   with the executed mode cannot disagree with it if it is not a control — and it closes the last
   remaining way to violate this item's one-source-of-truth requirement (mode read once, at chip
   session start, never re-read from UI afterward). **What survives, UNCHANGED and still binding:**
   item 13/D33's requirement that a pending request with an ABSENT or INVALID tier fails LOUDLY
   with no default now guards the DERIVATION rather than a preselect, but the requirement itself is
   untouched; tier C remains refused in this build (unchanged). D33/D34's "sets and locks the mode
   radio" MECHANISM is superseded by derivation — the control it locked no longer exists — but the
   REQUIREMENT that mechanism enforced is not superseded, see item 13's own amendment below.
5. **No document-field rendering.** MUST NOT render DG1/MRZ/any personal field on any screen, any
   mode (F4 — the M0-inherited `ResultActivity` leaked partial DG1 text into an accessibility
   snapshot this session). `ResultActivity` MUST be removed, not deprioritized. Mode A screens
   show verdict only. No MRZ persistence to disk (F1, stash finding #3).
6. **Session-state lifecycle.** MUST wipe MRZ/session state in `onStop()`, never `onPause()` (F2 —
   NFC foreground dispatch pauses/resumes the still-visible activity before delivering the tag;
   an `onPause` wipe clears state mid-read). MUST keep typed MRZ fields on an access failure
   (PACE→BAC fallback, `SW 0x6300`→`0x6985`) so a mistyped key is a retry, not a full retype (F3);
   wipe only on a successful read or `onStop()`.
   **Amended 2026-09-01, owner decision (D35):** the last value-free report text MAY be retained
   in-memory across Activity recreation (`onSaveInstanceState` Bundle), never persisted to disk.
   Already implemented; this records approval, not new behavior. MRZ/session-material wipe rules
   above are unchanged.
   **Considered and DECLINED 2026-09-01, owner decision (D51 — item 6/F1's `onStop()` wipe is
   REAFFIRMED, not relaxed):** the owner was asked whether to relax the `onStop()` MRZ wipe above
   so entered details would survive an app-switch (having found the reset annoying in a live run).
   He DECLINED: the wipe rule above stays exactly as written, unchanged. **Reasoning, recorded so
   this alternative is not re-proposed as an obvious improvement later:** the actual friction was
   the tag-loss reset case, not the app-switch case — fixed instead by D51's item 15 amendment
   (a transient chip-communication failure now keeps the MRZ/mode for retry). Retaining document
   data in memory while the app is backgrounded, purely for convenience, would weaken F1's privacy
   posture for a problem that had a better, narrower fix.
7. **Masterlist.** MUST bundle the full BSI CMS SignedData (`DE_ML_*.ml`) and verify the CMS
   signature and signer chain (`CSCA Master List Signer` ← `csca-germany`) at load, before the
   integrity check; a signature/chain failure is an integrity failure ⇒ `ok:false` (owner decision
   2026-08-31, overriding the draft's raw-eContent recommendation; rationale: turns the one-time
   manual provenance check in `M2-SCAN-EVIDENCE.md` into a check on every load, using CMS parsing
   the app already carries via JMRTD/BouncyCastle). MUST apply the two-bucket rule (§6 M2 row,
   owner-approved 2026-08-31) on top of this: integrity failure (CMS signature/chain failure
   included) ⇒ `ok:false`; well-formed, CMS-verified list lacking the issuing CSCA ⇒
   `ok:true, allowed:false`.
8. **Handoff.** MUST implement `av://` app-link + `direct_post` as primary (`M2-CAPTURE.md`
   Finding 1/Recommendation), QR as the cross-device fallback. Digital Credentials API support is
   a spike-gated stretch — MUST NOT ship as in-scope unless the Credential Manager
   provider-registration spike (open item, `m2-opening-poc-complete.md`) passes first. EU
   wallet/mdoc interop MUST NOT be attempted (NO-GO #3; `M2-CONFORMANCE.md` Findings 1, 2, 7 —
   confirmed non-interoperable by design, not by omission).
   **Confirmed exercised end-to-end 2026-09-01, commit `9f60489`** (real Chrome `av://` tap, NL ID
   card, mode B, Pixel 6a) — the handoff call had never previously executed inside the scanner
   before this run. Two defects found and fixed in that commit: (a) the handoff ran on the main
   thread inside `BiometricPrompt.onAuthenticationSucceeded`, raising
   `NetworkOnMainThreadException` on the `request_uri` fetch — now runs on a background thread,
   same idiom as the masterlist probe; the per-use-auth key stays valid across the hop because it
   is bound to the `CryptoObject`, not a time window; (b) `response_uri`/`state` were being read
   from `zkagent.challenge` instead of the request object's top level, where they actually live —
   fixed to read top-level. See item 14 below (D34) for the still-open JWS-verification gap this
   run also surfaced.
9. **Evidence.** Mode A MUST ship bare (`evidence: []`, D27). Mode-B roundtrip MUST exercise the
   attester-key evidence plug matching whatever algorithm item 1 selected on that device (D30)
   as the reference default. Signed layout, stated once (per algorithm — see item 1's amendment
   for why more than one now exists):
   `Ed25519( sha256( utf8("sig-ed25519/1\n") ‖ sha256(canonical(claim)) ‖
   base64urlDecode(nonce) ‖ utf8(scopeDomain) ‖ utf8(zktag) ) )` — nonce bytes are
   base64url-decoded, not utf8; do not copy chiproof's pre-0.3.0 test-fixture encoding, which was
   found inconsistent with this shipped layout.
   **Amended 2026-08-31, owner decision (algorithm agility, F2 resolved — original single-algorithm
   clause kept above, not deleted):** the verifier MUST accept **more than one signature
   algorithm** for the attester-key evidence — `sig-ed25519/1` where the app selected software
   Ed25519, and the candidate **P-256 evidence plug** (`sig-p256/1`, candidate decision, `Dn`
   pending) where the app selected hardware-backed P-256, per item 1's amended device-capability
   selection. Which algorithm a given presentation used MUST be reported alongside the evidence,
   not inferred by the verifier. This is the same pattern the evidence slot itself already uses
   (D24): the adopter/operator chooses by their own priorities, and the core supports the choice
   rather than picking for them.
   **Amended 2026-09-01, owner decision (D31, "accept more than one algorithm" made enforceable —
   original amendment above kept, not deleted):** the prior paragraph named the requirement but not
   the mechanism, and the gap was live: a real mode-B run on 2026-09-01 reached
   `evidence_required_missing` because `spikes/m2-handoff/server.mjs` required `sig-ed25519/1`
   only while the device sent `sig-p256/1`. The verifier's evidence requirement MUST be an any-of
   set (`sig-ed25519/1` OR `sig-p256/1`, or whatever the operator configures per D32), not chiproof's
   current all-of `evidence.require` (`packages/chiproof/src/evidence.js:184`). The request object's
   `evidence_required` field carries the same alternatives shape so the app knows what it may send.
   Which plug in the accepted set was actually used MUST be recorded in the verdict (unchanged from
   the paragraph above). **Open design question, not decided — Q28 (§11):** may a device pick the
   weaker of the accepted alternatives, or must it always offer its strongest? Recommended default:
   the verifier lists its accepted set in preference order, accepts any member, and records which
   was used in the verdict — no enforcement of "strongest offered" at the protocol level.
   **Owner-agreed 2026-09-01 ("agree, keep it as is") — logging convention, not a behavioural
   requirement:** the report carries `evidence_plug: device_preference=… used=…` (the device's own
   D36 preference order versus which plug it actually sent — differing only when the preferred
   combo failed on this device) and `evidence_required: any-of[…]|absent` (a log-only parse of the
   request object's D31 alternatives shape). Both are diagnostic text for the evidence doc; per
   D36, nothing reads them back to decide what the device does.
10. **Network config.** MUST ship a real `network_security_config` (cleartext disabled). MUST
    define a debug/release split (owner decision 2026-08-31): release build permits NO cleartext;
    debug build permits exactly one cleartext exception, scoped to `10.0.2.2`/localhost, for the
    local test verifier only.
11. **Non-goals (M2 build).** MUST NOT include: iOS; an on-device ZK prover; EU wallet/mdoc
    interop; rung-2 delegation; the Credential Manager provider (unless its spike passes, item 8);
    any change to `chiproof` beyond what the app needs. Pinned dependency: `chiproof 0.4.0`
    (in-repo, unpublished; published when M2 lands) — the scanner and `spikes/m2-handoff` both
    build against the in-repo `packages/chiproof`, the latter via `file:../../packages/chiproof`.
    **Amended 2026-08-31, owner decision (original clause above kept visible, not deleted):**
    adding a **P-256 evidence plug** to `chiproof` (`sig-p256/1` — see item 1) **is now permitted**
    — it is required by item 1's device-capability reality (`docs/logs/M2-SESSION-POC.md` F2: Ed25519 is unavailable as an
    AndroidKeyStore key on the Pixel 6a, at either security level, by either entry point), not
    scope creep against this item's original "no change beyond what the app needs" bar; the
    P-256 plug *is* what the app needs. Every other non-goal in this item stands unchanged: no
    iOS, no on-device ZK prover, no EU wallet/mdoc interop, no rung-2 delegation, no Credential
    Manager provider unless item 8's spike passes, and no `chiproof` change beyond this one
    addition.
    **Updated 2026-09-01:** `sig-p256/1` is no longer a candidate name awaiting a `Dn` — it shipped
    in `chiproof@0.4.0` (commit `0550c10`, "sig-ed25519/1 + sig-p256/1 attester-key plugs") on this
    item's own F2 resolution, and its role as a co-equal reference-default alternative to
    `sig-ed25519/1` (not a single required plug) is now governed by D31/D32/D36.
    **Owner-agreed 2026-09-01 ("agree, keep it as is") — pin unchanged, not a new decision:**
    `chiproof` stays at 0.4.0, unpublished, in-repo; D31's alternatives-group semantic and D38's
    attester-key store (both landed today) are additive changes to the evidence slot, not a
    version bump on their own — no breaking change to a published contract, since nothing has
    published yet. A dependency also lands today outside `chiproof`: the scanner's unit-test
    target adds `org.json:json:20240303` as `testImplementation` only
    (`apps/scanner/gradle/libs.versions.toml`) — the plain-JVM unit-test runner otherwise links
    AGP's `org.json` **stub**, whose methods return silent defaults rather than throwing or
    parsing, which would have let JSON-shaped tests (D37's origin/JWKS parsing, item 14's
    verification logic) pass while testing nothing. Ships in no APK; test-only.
12. **Riskiest-assumption POC for the build itself** (per-module rule: POC the riskiest
    assumption before the easy parts; owner decision 2026-08-31): compose StrongBox key
    generation + biometric prompt + PACE chip read in one foreground-dispatch NFC session; pass =
    the IsoDep session survives the biometric UI interruption and the read completes. Run on the
    Pixel 6a with both documents — this is the one untested interaction the items above assume
    works together.
13. **Mode preselection from a pending handoff request** (new 2026-09-01, owner decision D33,
    "B yes, app should preselect"). When a handoff request (`av://`/QR) is pending at the moment
    mode capture happens (item 4), the app MUST set the mode from the request object's
    `zkagent.tier` field and MUST disable the mode radio for user override; consent stays the
    Lock + biometric/credential gate (item 2), unchanged. If no handoff is pending, manual
    selection works as today. If the pending request's tier is absent or not one of A/B/C, the app
    MUST fail loudly (log + report) — no default.
    **Owner-agreed 2026-09-01 ("agree, keep it as is") — implementation detail, not a new
    decision:** the mode radio MUST disable the instant a handoff is captured, before request
    verification completes — a pending, not-yet-verified request already rules out manual
    selection, so there is no window where the user can pick a mode the request might override.
    Once verification succeeds, the app sets and shows the verified tier. `lockedMode` remains
    written from exactly one call site (`lockModeAndArm`) regardless of which path fed it.
    **Exception, same-day addendum 2026-09-02 (commit `730ef09`, supersedes "the instant a handoff
    is captured" in place — original clause above kept, not deleted):** this "captured the instant
    it arrives" admission now has an exception — an incoming `av://` link arriving while a session
    is already locked or a read is in progress is refused up front by
    `HandoffAdmission.mayAdmitInboundHandoff` and never reaches this mode-preselect step at all; see
    `.claude/remember/findings.md` #10, §11 Q41. MITIGATION only, not the ownership fix — the guard
    is removed once the lock-time snapshot / SessionState structure lands.
    **Amended 2026-09-01, owner decision (D51 — the mode radio this item preselects/locks no
    longer exists; original clauses above kept, not deleted):** see item 4's D51 amendment — the
    RadioGroup is removed entirely and replaced by plain text showing the derived mode. This item's
    MECHANISM ("sets and locks the mode radio for user override") is superseded, since there is no
    longer a radio to lock. This item's REQUIREMENT is NOT superseded and remains binding, now
    guarding the derivation step instead of a preselect: a pending request whose `zkagent.tier` is
    ABSENT or not one of A/B/C MUST still fail LOUDLY (log + report), with no default; when no
    handoff is pending, the derived mode is A by definition (no manual selection exists to fall
    back to). Tier C remains refused in this build, unchanged.
14. **Request-object JWS verification before trusting any field** (new 2026-09-01, owner decision
    D34, "C yes, it should verify"). The scanner MUST verify the request object's JWS signature
    against a pinned/provisioned set of trusted request-signer keys before trusting ANY field
    inside it — nonce, `response_uri`, `state`, tier, `evidence_required` — closing the escalation
    recorded in `HandoffClient.kt`'s class doc ("this client does NOT pin a request-signer key").
    Verification failure, or no matching trusted signer, MUST refuse the handoff (log + report),
    never warn-and-continue. **This is stricter than D20's spec-level floor** (D20 permits unsigned
    challenges at tiers A/B, sealed only by the nonce HMAC) — a build MAY require more than the
    floor, and this is the M2 reference app's own choice, not a change to D20 itself.
    **Amended 2026-09-01, owner decision (D37, closes Q29 — original clause above kept, not
    deleted):** the trusted-signer set is not an arbitrary pinned list — it is origin-derived.
    The scanner MUST enforce that `client_id`, `request_uri`, and `response_uri` all resolve to
    one and the same HTTPS origin (scheme+host+port) before anything else; a mismatch MUST
    refuse (log + report) — this is D37's origin-binding MUST, closing the `av://`-hijack half of
    Q29. The request-signer public key MUST then be fetched over TLS from a well-known path under
    that same origin (`https://<origin>/.well-known/zkagent-verifier`, D37) and the JWS verified
    against it; a fetch failure or signature mismatch MUST refuse. **M2-scope exception (D37):**
    `spikes/m2-handoff` runs on plain `http://127.0.0.1`, so the scanner ships one build-time
    pinned dev request-signer key, labelled dev-only, in place of the well-known fetch; the
    "no production trust store yet" disclosure stays until a real TLS origin exists.
    **Owner-agreed 2026-09-01 ("agree, keep it as is") — implementation details, not new
    decisions:** (i) on verification failure the app refuses that handoff and falls back to
    manual/no-handoff scanning (item 13's "if no handoff is pending, manual selection works as
    today" path) — it does not halt the app session; the user can still complete a manual scan.
    (ii) the well-known path (D37) serves either a single JWK object or a standard JWKS
    (`{"keys":[...]}`); the scanner uses the first EC/P-256 entry found; a `kid` present on both
    the JWS header and the resolved key MUST match, and a mismatch is a hard refusal — a `kid`
    absent on either side is not itself a refusal reason (there is nothing to compare).

15. **Blocking acknowledgment for outcomes that leave the app waiting on the user** (new
    2026-09-01, owner decision D43, "when wrong data in, it is not pop up to dismiss but overlay
    notification that disappears, i should get pop up then ok then it resets"). Any outcome that
    ends a scan attempt and requires user action MUST be surfaced as a modal dialog carrying the
    value-free reason (item 5's constraint — no MRZ/PII) and an acknowledge (OK) action; the app
    MUST NOT rely on a Snackbar or other self-dismissing UI for these. On dismissal the app
    performs the state transition explicitly: on an access-establishment failure (PACE/BAC
    `SW 0x6300`→`0x6985`) it keeps the MRZ focused for correction (item 6's `keepMrzAndMode`,
    F3, unchanged); on every other failure it resets the session — the same `keepMrzAndMode`
    branch already used for the wipe, not a second policy. Transient (Snackbar) UI remains correct
    for purely informational events that require no action and change no state (e.g. "QR capture
    cancelled"). General rule: transient UI for transient facts, blocking UI for state that
    requires the user to act — an error that leaves the app waiting must not be able to go
    unnoticed.
    **Owner-agreed 2026-09-01 ("keep it as is") — clarifies D43's scope, not a new decision:** the
    three failure classes named above (read failure, access-establishment failure, handoff
    refusal) are EXAMPLES of item 15's general rule — "an error that leaves the app waiting must
    not be able to go unnoticed" — not an exhaustive list. The implementation additionally applies
    blocking acknowledgment to **mint-path failures**: key generation failure, a missing verified
    request or an unparseable origin/document-number field, no usable device key or signature, and
    a biometric/device-credential error. These are IN SCOPE and KEPT — any outcome that leaves the
    app waiting on the user, wherever it occurs in the read-then-mint pipeline, is covered by this
    item, not only the three named examples.
    **Amended 2026-09-01, owner decision (D50 — consumed/expired handoff session; original clauses
    above kept, not deleted):** a live run found that after a successful mint, the app left the
    handoff pending and the mode locked to a session whose nonce was already spent, then invited a
    second tap that could not possibly succeed — observed as two `AccessDeniedException
    SW=0x6982 SECURITY STATUS NOT SATISFIED` chip-access failures after a successful mint, with the
    verifier log confirming nothing reached it between the two attempts. The dialog's message was
    accurate for what it caught (a chip-access failure), but the STATE was wrong — the app had set
    up a failure it could have predicted before any tap. **MUST:** the pending handoff and its
    verified request are CLEARED once a presentation has been delivered and accepted (`direct_post`
    2xx), so a spent session cannot be reused. **MUST:** a tap or mint attempt arriving with a mode
    locked to a handoff but no usable session (consumed OR expired — a challenge past its expiry is
    equally unusable, not only a spent one) is refused UP FRONT with a blocking dialog stating the
    verifier session is no longer valid, ideally before the user is asked to tap, with D43's
    existing non-access-failure reset on dismissal (this is a refusal, not an access-establishment
    failure, so it does NOT keep the MRZ per F3). **UNCHANGED, restated:** the access-establishment
    failure path itself (`SW 0x6300`→`0x6985`, keeps MRZ, F3) behaved correctly in this run and is
    not touched by this amendment. **Dialog wording NOT yet owner-approved:** owner's stated intent
    is "verifier session expired or something" — the implementer's chosen exact strings return for
    approval, as with every other user-facing string this session. **Open, not decided — see Q37
    (§11):** whether "consumed" and "expired" can be distinguished device-side without a verifier
    round-trip, and where the challenge expiry is reachable from; no approach proposed here.
    **Corrected 2026-09-01 (original D50 text above kept, not deleted):** the causal claim above —
    that a successful mint left the handoff pending on an already-spent session, inviting a doomed
    second tap — is not supported by the code. `MainActivity.kt:1033-1034` (pre-existing, already
    present before this session's work) clears `pendingHandoff`/`verifiedRequest` on ALL delivery
    outcomes, and `wipeSession(keepMrzAndMode = false)` clears `lockedMode` after every completed
    read; a consumed session could not have been left reachable for a stray tap. The two observed
    `SW=0x6982` failures were genuine chip-access failures after the owner re-typed the MRZ and
    re-locked — the dialog was accurate and the state was not wrong. **What this item actually
    requires, restated:** NEW protection against a session AGING OUT (past its challenge expiry)
    while still formally pending — never cleared by elapsed time alone, only on definitive
    completion — so a document tap after expiry-but-before-delivery would otherwise fail at
    `direct_post` with no prior warning. The two MUSTs above stand, reframed: clearing the pending
    handoff on an accepted delivery (`direct_post` 2xx) is confirmed ALREADY the case in existing
    code, not a new fix; refusing a tap/mint UP FRONT against an EXPIRED session is the genuinely
    new protection this item adds. **Q37 (§11) is now CLOSED, resolved by implementation, not
    design debate:** the challenge expiry (`zkagent.challenge.expires_at`) is reachable from the
    already-verified request object with no verifier round-trip; "consumed" needs no separate
    detection because clearing already removes a used session from state the moment it is used.
    **Amended 2026-09-01, owner decision (D51 — a third failure-transition bucket: transient
    chip-communication failure; original clauses above kept, not deleted):** evidence from a live
    run: `net.sf.scuba.smartcards.CardServiceException: Tag was lost` inside
    `DefaultFileSystem.readBinary`, surfacing as `IOException: Unexpected exception` — the document
    physically moved during the read; the verifier confirms a transaction was created and never
    received a presentation. **MUST:** a TRANSIENT chip-communication failure (tag lost mid-read,
    or the NFC link otherwise drops before the read completes) MUST keep the MRZ and mode, exactly
    like the access-establishment bucket (item 6/F3), so the user can hold the document still and
    retry with NO re-entry — a third bucket alongside the existing two (access-establishment keeps;
    everything else resets). **General rule, stated because it generalises:** the discriminator
    across all three buckets is whether THE ENTERED DATA IS STILL GOOD. An access-establishment
    failure means the details may be wrong; a mid-read tag loss means the details were right and
    the read was merely physically interrupted — resetting in that case discards correct input for
    a physical mishap that has nothing to do with the data entered. **MUST:** the pending handoff
    MUST survive this retry — the user should not need to re-tap the site's link — and D50's
    session-expiry refusal takes precedence if the session expires during the retry window.
    **MUST be conservative:** classification of an ambiguous or unrecognised exception MUST fall
    through to the RESET bucket, never to this new KEEP bucket — a wrong "keep" leaves document
    data on screen the user did not expect (a privacy-relevant mistake), which is worse than a
    wrong "reset" (a UX-only mistake); this new bucket is a narrow, conservatively-classified
    carve-out from the general reset rule, not a default. **Dialog wording NOT yet
    owner-approved** — the implementer's chosen string returns for approval like every other
    user-facing string this session.
    **Strings owner-approved 2026-09-01 (D53):** the dialog above reads `Reading was interrupted —
    hold the document still against your phone and try again.`; the corresponding `Result` line
    reads `Read interrupted — the document moved or the connection dropped`.
    **Superseded 2026-09-01, owner decision (D54 — shortened after the owner skimmed past the
    D53 wording five times on a real device, itself the evidence for shortening; original D53
    strings above kept, not deleted):** dialog → `Couldn't read — keep the card at the top of your
    phone.`; `Result` line → `Couldn't read — card moved`. **MUST remain a separate string from the
    access-establishment failure message below and MUST NOT be merged with it** — the two demand
    different user actions (hold the card still vs. correct your details), and merging would
    discard the distinction this third bucket (D51) exists to draw.
    **Amended 2026-09-01, owner decision (D52 — a successful mint MUST confirm itself with a
    blocking modal; original clauses above kept, not deleted):** found by the owner using the
    build, not by inspection: a successful mint today only calls `emitReport`
    (`verdict: PASS (minted)`), updating the report text and adding a log entry — there is NO
    dialog and NO transient UI on success. **The asymmetry, stated explicitly:** D43 made this app
    LOUD about every failure (a modal requiring acknowledgement) and SILENT about success, so the
    one outcome a user most wants confirmed is the only one that does not confirm itself. D43's own
    general rule — "blocking UI for state that requires the user to act; transient UI for transient
    facts" — was read as though success required no action; in a mode-B handoff it does, since the
    user must return to the browser and nothing told them the presentation had been accepted.
    **MUST:** a successful, DELIVERED AND ACCEPTED presentation MUST be surfaced as a blocking
    modal with an acknowledge action, using the SAME mechanism as D43's failure dialogs — one
    dialog path handling both terminal outcome classes, not a forked near-duplicate. **MUST,
    minimal wording (owner's choice):** the confirmation states the OUTCOME ONLY; it MUST NOT
    restate the disclosure (age predicate, site, identity state) — that detail already lives in the
    log entry (item 16) and the report. **MUST:** dismissal follows D43's existing
    non-access-failure branch — the same session reset every other terminal outcome uses; no
    separate post-success policy. **MUST:** only `Accepted` delivery qualifies as this success
    case — a signed-but-undelivered presentation (rejected by the verifier, no `response_uri`, or a
    transport failure) MUST NOT render as a success and keeps its existing failure treatment; this
    distinction is why the implementation's four-way delivery-outcome split exists, and collapsing
    it would let an undelivered presentation read as verified. **MUST:** a mode-A / bare local scan
    completing with no delivery is a terminal outcome but is NOT a "verified by the site" success;
    if it confirms at all, its wording MUST be honest that nothing was sent. **Dialog wording NOT
    yet owner-approved** — the implementer's chosen string returns for approval like every other
    user-facing string this session.
    **String owner-approved 2026-09-01 (D53), replacing the implementer's proposal:** the success
    dialog reads `ID scanned successfully` — the owner's own wording. The `Result` log line for
    this outcome is deliberately NOT changed to match this dialog text; the dialog and the log line
    differ on purpose. **Also recorded, accepted implementation judgements, not new decisions:** a
    mode-A/bare scan does NOT receive this success confirmation, consistent with D52's rule that
    only an accepted delivery is a "verified by the site" success; no distinct mid-read "hold
    still" progress state was added — deferred, not refused, since it requires restructuring the
    read task's progress reporting, noted as available future UX work.
    **Amended 2026-09-01, owner decision (D54 — access-establishment failure diagnosis and
    shortened strings, for item 6/F3's / this item's access-establishment bucket):** a live run hit
    five consecutive `org.jmrtd.AccessDeniedException: Mutual authentication failed` failures,
    preceded by `PACE unavailable (AccessDeniedException)`. **Diagnostic distinction, recorded
    because it is genuinely useful and was not obvious:** `PACE unavailable
    (CardServiceException)` means the chip does not support PACE, and the reader falls back to
    BAC; `PACE unavailable (AccessDeniedException)` means PACE WAS available, was attempted, and
    the MRZ-derived key was REJECTED — both protocols failing on key rejection identifies wrong
    typed details, not a chip or code fault. **Verified not a regression:** the D43-D53 scanner
    commit touched no MRZ/BAC/PACE key-construction code. **UX consequence, the actionable part:**
    an access-establishment failure KEEPS the typed details by design (F3/D43, "for correction"),
    but a user who does not change them re-derives the same wrong key on every retry, producing an
    unbounded run of identical failures — the message MUST make the required action unmistakable,
    and MUST stay distinct from the transient-failure message (see D54 note above on this item),
    since the two demand different actions. **Corrected 2026-09-02 (D55, this causal reading kept
    above, not deleted):** "a user who does not change them" is INCOMPLETE as an explanation of the
    run this decision was made from — D55 found the app's own overlapping-pane bug (item 16) could
    make the typed details unreachable to correct, so at least part of the observed run reflects a
    UI the user could not act on, not an unchanging user; this decision's diagnostic distinction and
    string choices are unaffected. **Strings owner-approved, replacing the previously
    unrecorded default copy** (`strings.xml` `error_read` previously read "Could not establish a
    chip session with the typed details — check them and tap again", shortened for the same reason
    as the transient-failure string): dialog → `Couldn't read — check your details and try again.`;
    `Result` line → `Couldn't read — check your details`. **MUST remain a separate string from the
    transient-failure message and MUST NOT be merged with it** — same reasoning as above, stated
    from this side of the pair.
    **Real bug found on the same run, verified directly in the code:** the two failure
    classifications above are evaluated in the WRONG ORDER, so a physical card slip can be reported
    as a data-entry problem. `MainActivity.kt` ~900-912's `try` around the access-establishment
    phase (`sendSelectApplet` / `EF_COM` probe / `doBAC`) catches ANY `Exception` and sets
    `accessFailure = true` unconditionally — classification by CODE PATH, not by evidence in the
    exception. `MainActivity.kt` ~1014's transient classifier is gated behind it
    (`!accessFailure && FailureTransition.isTransientChipCommunicationFailure(result)`), so it can
    only fire once access has already SUCCEEDED — a tag-loss occurring DURING access establishment
    is labelled an access-establishment failure, and the user is told to check typed details that
    were correct; the transient bucket misses the identical slip a moment earlier and only catches
    it during the DG read. **Why the tests did not catch it, the transferable lesson:**
    `FailureTransitionTest` asserts the keep/reset MAPPING, and that mapping was correct — both
    buckets keep MRZ+mode, so the STATE TRANSITION is identical either way; what differs is the
    MESSAGE, the advice given to the user. A test suite that pins state transitions cannot see a
    bug that only changes which correct-transition message is shown; sending a user to re-check
    correct data because their card slipped is the actual defect. **Fix, owner-approved,
    requirements:** transient MUST be classified FIRST, from evidence in the exception, independent
    of which phase was executing — a tag-loss is a tag-loss whether it occurs during applet-select,
    BAC, or a DG read. `accessFailure` MUST be narrowed to a genuine access DENIAL — the documented
    `SW 0x6300`->`0x6985` conditions and `org.jmrtd.AccessDeniedException` (what a rejected PACE or
    BAC key actually raises) — classified from the exception, never from the code path. The
    precedence (transient wins when both could match) MUST live in the pure `FailureTransition`
    object with its own test, not as an `if` ordering buried in the read task's completion handler.
    An exception that cannot be confidently categorised still falls to the RESET bucket — the
    existing conservative default is unchanged. **The state transitions themselves are UNCHANGED:**
    buckets 1 and 2 keep MRZ+mode, bucket 3 resets — only classification, and therefore the
    message, changes.
    **Amended 2026-09-02, owner decision (D55 — read-in-progress flag clearing; item 16 carries this
    decision's pane-visibility bug and fix in full, this item records only the completion-handler
    half; original clauses above kept, not deleted):** found alongside D55's pane-visibility bug
    (item 16) on the same live run — a real bug, root-caused by direct code inspection. The
    read-in-progress flag MUST be cleared on EVERY exit path of the completion handler this item's
    blocking dialog is dismissed from, including the failure branch's early return; a flag left set
    on an early return is part of the same "app leaves itself in a state it never intended" class as
    item 16's overlapping-pane bug found in the same run. **This also CORRECTS D54's own causal
    reading, supersede-in-place (D54's text and its classification-order fix are UNAFFECTED, kept,
    not deleted):** D54 attributed the run of five identical access failures on 2026-09-01 to a user
    "who does not change them," i.e. to user behaviour alone. That reading was incomplete — see item
    16/D55 for the full mechanism: the app's own log-tab pane covering the MRZ form structurally
    PREVENTED the correction D54 was asking for, on at least one of the runs that motivated D54's
    diagnostic. See item 16 for the layout/`FrameLayout`/tab-listener root cause and the
    single-visibility-writer fix, which this item's read-in-progress-flag fix ships alongside.
    **Amended 2026-09-02, owner decision (D56 — value-free MRZ-input-change diagnostic):** the
    tag-intent path MUST log whether the three MRZ field values CHANGED since the previous read
    attempt in the same process — value-free by design (item 5), because the existing logs could not
    answer the one question that mattered while D55's bug was live: whether the owner's corrected
    details actually reached the app. That gap cost an hour of code inspection to answer instead.
    **Approved output shape:** `M2 stage: MRZ input UNCHANGED since previous attempt (doc_len=9
    dob_ok=true exp_ok=true)` / the same line with `CHANGED`, plus a distinct
    first-attempt-this-session variant. **MUST NOT:** log the field values or any character of them;
    render the comparison hash anywhere; write it to `reportView`, `ReportLog`,
    `onSaveInstanceState`, or disk. **MUST:** hold the hash in memory only; SALT it with a
    per-process random value generated at start and never persisted — an unsalted truncated digest
    of a short document number is trivially brute-forceable and would itself be PII; reset the
    stored hash wherever the MRZ is cleared (`wipeSession`'s `!keepMrzAndMode` branch, item 6) so the
    next attempt reads correctly as a first attempt.
16. **Per-scan report log view** (new 2026-09-01, owner decision D44, "the feedback of what
    happened every scan at the bottom of the app should go to another tab as logs, same output
    with timestamp"). The value-free report currently rendered into `reportView` MUST also
    accumulate into a separate in-app log view (a tab or equivalent navigation; the exact widget
    is an implementation detail — **annotated 2026-09-02, D55: this discretion is exactly the seam
    the pane-visibility bug at the end of this item exploited; see D55 there for the fix that now
    constrains it**) that lists the reports of successive scans in the session, each
    entry prefixed with a local wall-clock timestamp. MUST NOT change report content — the same
    value-free lines `emitReport()` already produces (item 5's grep-provable constraint: no MRZ,
    names, key bytes, fingerprints, chain contents). MUST route through the existing single
    `emitReport()` write path — the log view is an additional consumer of that one call site,
    never a second write site. In-memory only for the session — governed by item 6/F1 (MRZ
    persistence removed) and D35 (in-memory-only retention across Activity recreation), not NO-GO #9
    (secrets/test keys, not on-device persistence) — MUST NOT be persisted to disk; D35's in-memory retention across Activity recreation extends to the accumulated log,
    and the log MUST be cleared whenever a session wipe occurs that does not keep MRZ/mode (item
    6). The timestamp is display-only and MUST NOT enter any proof/evidence path (contrast D28's
    midnight-UTC `current_date` coarsening, which is a payload field). The accessibility-snapshot
    caution on raw-field screens does not apply here because the content is value-free by
    construction — noted explicitly so the log view is never later treated as a place to add raw
    fields.
    **Amended 2026-09-01, owner decision (D45 — original clause above kept, not deleted):** the
    clause above is self-contradicting as written — it says the log "lists the reports of
    successive scans in the session" while also requiring it be "cleared whenever a session wipe
    occurs that does not keep MRZ/mode" (item 6). **Why this is wrong, verified in code:**
    `MainActivity.kt` calls `wipeSession(keepMrzAndMode = false)` at the completed-read call site
    on EVERY completed read, including a successful one — so the literal clear rule wiped the log
    on the very success path it existed to record, and successive scans never accumulated; at most
    one entry survived at a time. Owner chose accumulation over the literal clear rule: **the
    log's lifetime is now decoupled from `wipeSession()`'s per-scan `!keepMrzAndMode` branch — a
    per-scan session wipe, successful or not, MUST NOT clear the log.** Everything else about
    retention in the clause above is UNCHANGED and restated so it is not lost: in-memory only for
    the session, never persisted to disk (item 6/F1, D35), surviving Activity recreation via
    `onSaveInstanceState` (D35), gone only when the app process is gone.
    **Amended 2026-09-01, owner decision (D46 — original clause above kept, not deleted):** owner:
    "logs should be safe and not a source of threat, but should be there for user to know how it
    went, what went out and the result, how much it disclosed, successful or not" and "titled by
    timestamp, titled by website." Each log entry MUST carry, in addition to its existing local
    wall-clock timestamp (display-only, unchanged — MUST NOT enter any proof/evidence path,
    contrast D28's midnight-UTC `current_date` coarsening which IS a payload field), a title
    identifying the SITE the scan was for: the verified request origin / `scope_domain` (D37,
    D42). A scan with no verified handoff (mode A, no pending handoff request — a bare local scan)
    MUST use the fixed, value-free label **"Local scan (no site)"** rather than a blank field or a
    fabricated origin (specification recorded here as the implementation-level string; not itself
    owner-confirmed wording — flagged in §11 Q32). The log MUST also be legible to a non-engineer
    about outcome: what went out, to whom (the site title above), what was disclosed, and whether
    the scan succeeded. **This SUPERSEDES the clause above's "MUST NOT change report content" to
    the extent that a value-free disclosure summary answering those questions is now REQUIRED** —
    the rest of that clause stands UNCHANGED and remains binding: no MRZ, names, document fields,
    key bytes, signatures, nonces, fingerprints, or chain contents (item 5's grep-provable
    constraint). The origin/site name required above is NOT a document field and is safe to show —
    stated explicitly so it is never confused with one, and so this amendment is never read as
    license to add any other raw field. The single-write-path constraint in the clause above is
    UNCHANGED and remains binding: every report write MUST still route through the one logged
    `emitReport()` call site; the log view is an additional consumer of it, never a second write
    site — this is the same defect class this session already fixed once (a UI-only write with no
    logging made a completed run look identical to a hang). The accessibility-snapshot note in the
    clause above is UNCHANGED and restated: value-free by construction, so the caution does not
    apply to this view — stated explicitly so the log view is never later treated as a place to
    add raw fields, now or under this disclosure-summary requirement. **Open, not decided — Q32
    (§11):** the exact shape/wording of the disclosure summary (e.g. a fixed set of labeled fields
    vs. free text) is not specified by the owner; this item states the requirement at the level
    the owner gave it.
    **Amended 2026-09-01, owner decision (D47 — closes Q32; original clauses above kept, not
    deleted):** owner approved the exact disclosure-summary shape, ending Q32. Each log entry MUST
    render as a four-field plain-language block — **`Result`**, **`Sent`**, **`Shared`**,
    **`Identity`** — followed by a subordinate **`▸ technical:`** line carrying the existing
    machine-shaped detail (mode, evidence plug, `key_id`, `chip_auth`, transaction id), all under
    the entry's title line (timestamp + verified site, or "Local scan (no site)"). Owner-approved
    rendering, verbatim, for the two worked examples:

    ```
    14:22:07 · 127.0.0.1:8787

    Result    Verified — the site accepted you
    Sent      a site-only pseudonym + proof you're over 18
    Shared    your age threshold, and nothing else.
              Not your name, date of birth, document
              number, or nationality.
    Identity  new — minted fresh for this site

    ▸ technical: mode B · evidence sig-p256/1 ·
      key_id c303cf3f… · chip_auth true · tx HVLKlhbU…

    14:19:41 · Local scan (no site)

    Result    Read OK — nothing sent
    Sent      nothing left this device
    Shared    nothing

    ▸ technical: mode A · evidence [] (D27)
    ```
    **Annotation, added 2026-09-01 in a conflict sweep, not a new decision:** the block above is
    the original D47-owner-approved rendering and remains the correct source for the
    `Result`/`Sent`/`Identity` field text, the title-line format, and the `▸ technical:` line's
    existence — it predates D48's predicate/answer requirement, D49's boolean-list `Shared` format,
    and D50's newest-first/single-entry-per-scan rules, so its `Shared` line and its
    ordering/entry-count are historical, not the current rendering; the Exit-criteria row for item
    16 is the current, authoritative full shape.

    `Identity` is the plain-language restatement of the D38/D39 per-(origin, zktag) attester-key
    state: whether this presentation created a new key/alias for this (origin, zktag) pair or
    reused one already bound from a prior visit. Only the "new" copy above is owner-approved
    verbatim ("new — minted fresh for this site"); the "reused" case's exact copy is not yet
    owner-specified — implement it at this same plain-language register and confirm wording before
    or during item 16's implementation (residual, not reopening Q32 as a numbered question).

    **REQUIREMENT, not decoration:** the plain-language lines MUST be accurate per actual outcome,
    not a fixed template blindly filled in. A success, a request/handoff refusal, a masterlist
    "no" (issuer not on the trust list), an unmet mint gate (item 12 failure classes), an
    access-establishment failure, and a bare mode-A read are genuinely different disclosures and
    MUST read differently — each stating plainly and correctly what happened, not a reworded copy
    of the success case. The log MUST NOT claim something was sent when nothing left the device,
    and MUST NOT read as success on any failure path. Mode A MUST state plainly that nothing left
    the device (`evidence: []`, D27) — the second worked example above ("Read OK — nothing sent" /
    "Sent: nothing left this device" / "Shared: nothing") is the reference case for this.

    Everything D46 stated as UNCHANGED remains UNCHANGED and binding, restated once more so it is
    not lost under this amendment: the value-free constraint (item 5 — no MRZ, names, document
    fields, date of birth, document number, nationality, key bytes, raw signatures, nonces,
    fingerprints, or chain contents); the origin/site name is explicitly NOT a document field and
    stays safe to show; the single-`emitReport()`-write-path invariant — the log view (including
    this richer rendering) is an additional consumer of that one call site, never a second write
    site, and if an entry needs fields the current single string does not carry, the fix is to
    extend what flows through that ONE call site, never to add a second write path; the timestamp
    stays display-only and MUST NOT enter any proof/evidence path; and the accessibility-snapshot
    note stays UNCHANGED — value-free by construction, so the caution on raw-field screens does not
    apply, restated so this richer rendering is never later treated as a place to add raw fields.

    **The no-site label is now CONFIRMED, closing the last open half of Q32:** owner confirmed
    **"Local scan (no site)"** as the exact wording (2026-09-01) — this supersedes the flag in the
    D46 paragraph above stating it was "a specification made here, not itself owner-confirmed
    wording"; that flag is superseded, not deleted, and no longer applies.
    **Amended 2026-09-01, owner decision (D48 — closes the D47 residual; original clauses above
    kept, not deleted):** owner: "new — minted fresh for this site, known - recognized only here
    from previous visit (or shorter), age above 18 yes shared" and "agreed on 1 and 2 and 3 above."
    **`Identity` reused-key wording is now CONFIRMED**, closing D47's open residual ("the 'reused'
    case's exact copy is not yet owner-specified"): the two confirmed strings are newly minted key
    — **"new — minted fresh for this site"** (unchanged, confirmed at D47) — and reused key —
    **"known — recognized only here from previous visit"**. **"Only here" is load-bearing, not
    decorative**: it is the plain-language statement of D38/D39's per-(origin, zktag) key
    isolation — this site recognizes the returning user, and no other site can, because the key is
    scoped to (origin, zktag), not to the device. Implementers MUST NOT simplify or shorten this
    phrase out. **`Shared` REQUIREMENT (new, substantive):** D47's worked example rendered `Shared`
    as the fixed sentence "your age threshold, and nothing else." — that was illustrative
    formatting, not the requirement itself. `Shared` MUST instead state the actual disclosed
    predicate and its actual answer, in the shape `age above <threshold>: <answer> — and nothing
    else.`, followed by the existing negation line ("Not your name, date of birth, document
    number, or nationality."). Owner's worked value ("age above 18 yes shared") →
    `age above 18: yes — and nothing else.`. Three sub-requirements: (1) the threshold number MUST
    be read from the verified request object at presentation time, NOT hardcoded — 18 is the value
    the current test request happens to carry, not a protocol constant; a request asking a
    different threshold renders that number. (2) the answer MUST be the actual value asserted for
    that scan, never assumed true — a scan that asserted `no` or failed the predicate MUST render
    that outcome, not a blind "yes." (3) on any path where nothing was disclosed (mode A, an unmet
    mint gate, a refusal, any failure), `Shared` MUST say so plainly and MUST NOT render an age
    claim at all — this is D47's outcome-accuracy rule, restated here because the `Shared` line is
    exactly where it is easiest to violate by defaulting to the success template. **The disclosed
    age predicate is explicitly NOT a document field** under item 5's forbidden-fields list — it is
    the claim the user deliberately chose to present, and showing it back to the user in their own
    on-device log is the point of the feature, not a leak; item 5's constraint on raw document
    fields (MRZ, names, DOB, document number, nationality, key bytes, signatures, nonces,
    fingerprints, chain contents) is otherwise UNCHANGED and remains binding — this age-predicate
    exception is narrow and specific to this one field, not a general opening.
    **Three implementation clarifications, owner-approved ("agreed on 1 and 2 and 3 above"),
    recorded as clarifications not new decisions:** (a) the `▸ technical:` line carries the
    complete, unmodified existing report text, indented, rather than the terse one-line summary
    shown in D47's worked example — no debugging detail is lost; the terse form in D47 was
    illustrative formatting only. (b) the two debug-only probe buttons (masterlist self-test,
    device-key self-test) render as a distinct "Diagnostic OK/failed" summary titled under the
    no-site label — they are not scans, disclose nothing to any site, and MUST NOT be rendered as
    if they were a scan outcome. (c) `siteTitleFor()` (or equivalent) MUST render the fixed
    **"Local scan (no site)"** label for a handoff whose request-object verification FAILED, not
    only for a bare mode-A scan with no pending request — an unverified or attacker-claimed origin
    MUST NOT ever be rendered as a trusted site name in the log title. This is D37's
    origin-verification requirement enforced at the UI layer, stated here as a security property of
    the log view, not merely a UI/cosmetic detail.
    **Amended 2026-09-01, owner decision (D49 — amends D48's `Shared` specification; original
    clauses above kept, not deleted):** owner: "true/false always #2 agreed #3 questions answers,
    same shape \"age > 18: true, expiry > 3 months: false, expired: true\"." **Boolean literal, not
    paraphrase:** the answer half of each `Shared` line MUST be the literal boolean `true`/`false`
    — never "yes"/"no" — as the direct mirror of the signed predicate boolean; the log MUST NOT
    paraphrase the payload. D48's worked example `age above 18: yes` is superseded (kept above, not
    deleted) by `age > 18: true`; the doc is reconciled TO the implementation, which already
    rendered booleans, not the reverse. **List, not sentence:** `Shared` MUST render as a list of
    `<predicate>: <boolean>` lines, one per disclosed claim, rather than one formatted sentence; the
    negation line ("Not your name, date of birth, document number, or nationality.") follows the
    list. **Predicate shape accommodates both forms** shown in the owner's three worked examples —
    comparison predicates (`age > 18`, `expiry > 3 months`) and bare boolean predicates with no
    operator (`expired`). **Constraints for today's single-element list:** the list holds exactly
    one element (the age predicate) until Q34 is resolved; an empty list (mode A, an unmet mint
    gate, a refusal, or any other non-delivered outcome) MUST render the plain "nothing shared"
    wording already required by D47/D48's outcome-accuracy rule, and MUST NOT render an empty label
    or a stray colon; the list MUST NOT be populated with any claim beyond the one that exists
    today — expiry and every other attribute remain **Q34** (§11), unbuilt. **Also recorded,
    owner-approved, not a new decision:** the `▸ technical:` block's compliance note is approved
    verbatim as `claim_proof: self-asserted by the device — not independently proven (D24)`, set
    only on outcomes where a claim was actually signed; this is currently the only place the log
    states the claim is unverified, tying directly to **Q33** (§11).
    **Amended 2026-09-01, owner decision (D50 — log ordering and duplicate in-progress entries;
    original clauses above kept, not deleted):** a live run on the Pixel 6a found two defects.
    **(1) Ordering:** the log view MUST list the NEWEST entry first. This is a RENDERING-order
    change only — the stored order MUST keep round-tripping correctly through
    `onSaveInstanceState` (D35 retention) exactly as before; ordering is a display property, not a
    storage one. **(2) Duplicate in-progress entry:** the mint gate calls `emitReport` when it
    requests biometric authorization, producing an entry reading `Result  In progress`; the
    terminal outcome then calls `emitReport` again, APPENDING a second entry — one scan produces
    two entries and the first never resolves. **MUST:** exactly ONE log entry per scan attempt; the
    terminal outcome REPLACES that scan's in-progress entry. **MUST NOT** be fixed by suppressing
    the in-progress `emitReport` call — every write MUST still reach logcat, since an unlogged
    UI-only write is exactly the defect this item's single-write-path invariant exists to prevent;
    the replacement belongs in the log accumulator, not in withholding the write. **Edge case,
    required:** an in-progress entry with NO terminal outcome (e.g. app backgrounded mid-scan) MUST
    still be shown — a genuinely interrupted scan MUST NOT be silently erased.
    **Amended 2026-09-01, owner decision (D51 — mode and chip-authenticity status join the
    plain-language block; original clauses above kept, not deleted):** each log entry MUST state,
    in plain language alongside `Result`/`Sent`/`Shared`/`Identity`, both the presentation MODE
    (now derived, D51's item 4 amendment) and the document's CHIP-AUTHENTICITY status; `chip_auth`
    itself stays unchanged in the `▸ technical:` line — this is a plain-language restatement
    alongside it, not a replacement of it. **Critical constraint:** chip authenticity has THREE
    states, not two — **verified**, **NOT SUPPORTED by this document**, and **failed** — and the
    absent/not-supported case MUST read honestly as its own state and MUST NOT be rendered as
    "false," which would misrepresent an absent capability as a failed check. This ties to the
    project's standing, stated-not-hidden position: a document without chip authentication is
    clone-replayable (the US passport is exactly this case, per M0 evidence), and mode-B uniqueness
    and blocking only hold where `chip_auth` is true (D21, D29, FR11) — the log MUST make this
    visible to the user in plain language, not bury it only in the technical line. **Exact strings
    NOT yet owner-approved** — the implementer's chosen wording for all three states returns for
    approval like every other user-facing string this session.
    **Amended 2026-09-01, owner decision (D53 — supersedes the MODE half of D51 above; the
    chip-authenticity half is UNCHANGED; original D51 clauses above kept, not deleted):** the
    `Mode` line is REMOVED from the plain-language block. **Reasoning, kept as the general
    principle:** `Sent`, `Shared`, and `Identity` already convey what mode A/B mean in plain
    language, with no glossary needed — `Mode B — recognisable to this site` merely restates
    `Identity`; `Mode A — anonymous` restates `Sent: nothing left this device`; the mode line was
    redundant. Owner: "no scary business for non tech savvy they may think we know and transfer
    more than what we do." **Standing fact that makes a plain-language mode label actively
    misleading:** D21's "always read, conditionally mint" means the chip is read IDENTICALLY in
    mode A and mode B — mode governs only what is SENT, never what is read — so a plain "mode A"
    risks being read as "we read less," which is false. **UNCHANGED:** mode stays in the
    `▸ technical:` line, and the on-screen derived-mode text (D51's replacement for the removed
    radio, item 4) is unaffected — only the plain-block `Mode` line is removed. **The
    chip-authenticity half of D51 STANDS, unchanged**, including the three-state rule (verified /
    not supported / failed) and that `absent`/`not supported` MUST NOT render as `false`.
    **Exact strings now OWNER-APPROVED, replacing the implementer's rejected proposal:** VERIFIED
    → `Verified — this document's chip proved it is genuine`; NOT_SUPPORTED → `Not supported —
    this document has no chip authenticity check`; FAILED → `Not verified — the chip check did not
    pass`. **Wording change only:** the three-state distinction and the project's stated-not-hidden
    clone-replay position (a document without chip authentication is clone-replayable, US passport
    is exactly this case) are UNCHANGED; the clone-replay limitation continues to be stated in the
    PRD and in `chip_auth` in the technical line — what changed is that the plain block no longer
    repeats an alarming phrase on every scan of a document that structurally cannot support the
    check. The implementer's original clone-explicit proposal was rejected by the owner as alarming
    to a non-technical reader, given it would appear on every US passport scan.
    **Amended 2026-09-02, owner decision (D55 — pane-visibility bug and fix; original item-16 clause
    above kept, not deleted, including its "a tab or equivalent navigation; the exact widget is an
    implementation detail" discretion — this is exactly the discretion the bug exploited):** a real
    bug found on the owner's live Pixel 6a run, root-caused by direct code inspection and
    corroborated by logcat. `apps/scanner/app/src/main/res/layout/activity_main.xml` places
    `loading_layout`, `main_layout` and `log_layout` as overlapping siblings inside ONE
    `FrameLayout`, in that XML order — in a FrameLayout later children draw on top, and
    `main_layout`/`log_layout` are both `match_parent` ScrollViews with no background, so
    `log_layout` COVERS `main_layout` whenever both are VISIBLE. Two independent code paths write
    those visibilities and neither knows about the third view: `MainActivity.kt:253-258` (this
    item's D44 tab listener) owns main<->log and explicitly leaves loading alone — its own comment
    at `:250` calls that "an edge case not covered by items 15/16," which is exactly the assumption
    that failed; `MainActivity.kt:857-858` (`startSession`) and `:1007-1008` (`ReadTask.onPostExecute`,
    item 15's completion handler) own main<->loading and never touch log. **Failure sequence:** a
    read fails -> the user opens the Log tab to see why -> taps the card again -> `onPostExecute`
    sets `main_layout = VISIBLE` while `log_layout` is still VISIBLE -> the log paints over the MRZ
    form; the tab indicator still reads "Log," `onTabReselected` is EMPTY (`:259`) so re-tapping does
    nothing, and nothing in the file ever calls `selectTab` — the user cannot reach the
    document-number field to correct it; see item 15/D56 for the diagnostic this cost an hour to
    work out without. **Fix, owner-approved, as requirements:** all THREE views' visibility writes
    MUST go through ONE function that sets all three on every call, making the both-visible state
    unrepresentable, the same single-write-site discipline `emitReport` already enforces for
    `reportView`, for the same reason. The pane DECISION MUST live in a pure, Android-free object
    with its own unit test (the `FailureTransition` precedent, item 15/D54) — this module runs with
    `unitTests.isReturnDefaultValues = true`, so `View.visibility` is a non-functional stub and a
    visibility invariant is NOT otherwise assertable in this suite, the same limitation already
    recorded above for `SpannableStringBuilder`. `onTabReselected` MUST become idempotent, not empty.
    A completed read MUST NOT auto-switch tabs — considered and REJECTED by the owner, since it
    would lose the user's place in the log after every scan; tab selection stays wherever the user
    put it. `onCreate` MUST call the function once after tab state and the restored log (D35) are in
    place. **Why the tests didn't catch it — a DIFFERENT blindness from D54's:** D54's tests were a
    correct test of the wrong property; here the property is not expressible in the suite at all,
    since Android framework view state is stubbed under `isReturnDefaultValues = true`. The remedy is
    therefore structural — move the logic where it can be tested, make the bad state
    unrepresentable — not additional assertions.

**Exit criteria**

| Check | Pass |
|---|---|
| Three `M2-SCAN-EVIDENCE.md` checkpoints, re-run on the real build, Pixel 6a, both documents | Reinstall zktag stability; on-device masterlist two-bucket rule with both negatives; mode A emits no zktag after a mode-B presentation |
| Handoff roundtrip | Passes against the `spikes/m2-handoff` verifier over `av://`/`direct_post`, including a mode-B presentation accepted under D31's any-of evidence set |
| Mode-radio bug (F5) | **Closed 2026-09-01**: not reproduced under the structural fix (item 4); attributed to the default-A radio state, not a capture bug. **Superseded 2026-09-01 (D51): the mode radio is removed entirely — mode is derived (verified handoff tier, or mode A by default with no pending request) and shown as plain text, eliminating this entire bug class by construction, not merely fixing the observed instance** |
| Mode preselection (item 13) | A pending handoff request's `zkagent.tier` sets and locks the mode radio; an absent/invalid tier fails loudly, no default. **Superseded 2026-09-01 (D51): there is no longer a mode radio to lock — mode is DERIVED from the verified tier (or mode A by default), shown as plain text; the absent/invalid-tier-fails-loudly-no-default requirement is UNCHANGED and now guards the derivation step; tier C remains refused** |
| Request-object verification (item 14) | An unsigned or unverifiable request object is refused (log + report) at every tier, not only C |
| Blocking acknowledgment (item 15) | An outcome ending a scan attempt — read failure, access-establishment failure, handoff refusal, **and mint-path failures** (key generation, missing verified request/origin/document-number, no usable device key or signature, biometric/device-credential error) — the named classes are examples, not exhaustive (D43 clarification) — is shown as a modal dialog, not a Snackbar, and only dismisses on OK; the state transition (keep-MRZ vs reset) happens on dismissal. **A pending handoff/request is already cleared on every definitive delivery outcome, confirmed pre-existing behavior; a tap/mint against a handoff session that has EXPIRED (past its challenge expiry) while still formally pending is refused UP FRONT with a blocking dialog before any tap, as NEW protection against a silent `direct_post` failure — not, as originally recorded, a fix for a consumed session being left reachable (corrected 2026-09-01, D50); the access-establishment-failure path itself is unchanged.** **A third bucket — transient chip-communication failure (tag lost / link dropped mid-read) — also keeps MRZ/mode for a no-re-entry retry, with the pending handoff surviving the retry and D50's expiry refusal taking precedence if the session expires meanwhile; classification is conservative, falling through to RESET when unclear (D51).** **A successful, DELIVERED AND ACCEPTED presentation also acknowledges itself via the same blocking-modal mechanism, minimal outcome-only wording (no disclosure restated), dismissal following the same non-access-failure reset — a signed-but-undelivered presentation MUST NOT render as this success case and keeps its failure treatment; a mode-A/bare scan with no delivery is not a "verified by the site" success (D52).** **Strings owner-approved (D53, transient-failure and success wording SUPERSEDED by D54 below — original D53 text kept, not deleted): transient-failure dialog `Reading was interrupted — hold the document still against your phone and try again.` / Result `Read interrupted — the document moved or the connection dropped`; success dialog `ID scanned successfully` (Result line deliberately left unmatched)** **Shortened strings owner-approved (D54), superseding D53's transient-failure wording and additionally recording the access-establishment pair for the first time: transient-failure dialog `Couldn't read — keep the card at the top of your phone.` / Result `Couldn't read — card moved`; access-establishment dialog `Couldn't read — check your details and try again.` / Result `Couldn't read — check your details` — these two MUST remain separate strings, never merged, since the two failure classes demand different user actions.** **Classification precedence (D54 bug fix): TRANSIENT is classified FIRST, from exception evidence, independent of which phase was executing; `accessFailure` requires a genuine access DENIAL (`SW 0x6300`->`0x6985`, `org.jmrtd.AccessDeniedException`) from the exception, never from code-path position; precedence logic lives in `FailureTransition` with its own test, not an `if` ordering in the completion handler; an unclassifiable exception still falls to RESET; state transitions (keep/keep/reset across the three buckets) are unchanged — only which message is shown changes** **The read-in-progress flag is now cleared on EVERY exit path of the completion handler, including the failure branch's early return (D55); D54's causal reading of the five-failure run is CORRECTED in place — the app was structurally preventing the correction it demanded, not merely met with an unchanging user, see item 16/D55 for the mechanism.** **A value-free MRZ-input-change diagnostic now logs UNCHANGED/CHANGED/first-attempt only (`M2 stage: MRZ input UNCHANGED since previous attempt (doc_len=9 dob_ok=true exp_ok=true)`), a salted per-process in-memory hash only, never persisted, reset on `wipeSession`'s `!keepMrzAndMode` branch (D56)** **DEVICE-CONFIRMED 2026-09-02 (D57):
`docs/logs/M2-D55-D56-EVIDENCE.md` — both documents mint cleanly with the D56 diagnostic correctly
reporting first-attempt and CHANGED, and a deliberate wrong-digit reproduction shows the
access-establishment failure/retry classification working end to end; the evidence doc states
plainly that the specific Log-tab-then-retap stranding sequence D55's fix targets was NOT exercised
in this run (no `UNCHANGED` capture) — D55 is confirmed as a working code path, not yet as a
reproduced-then-resolved regression test against its original failure mode** |
| Log view (item 16) | Successive scan reports accumulate, timestamped, **newest first** (D50, rendering order only — stored order still round-trips via D35), in the log view **for the life of the app session**; a per-scan session wipe (successful or not) MUST NOT clear it (D45) — the log is gone only when the app process ends. **Exactly ONE entry per scan attempt — a terminal outcome REPLACES that scan's in-progress entry, never appends a second one; an in-progress entry with no terminal outcome is still shown, not erased; every `emitReport` write still reaches logcat (D50)**. Each entry is titled by the verified site (`scope_domain`, D37/D42) or the owner-CONFIRMED "Local scan (no site)" label for mode A **or a failed request-object verification** (D46, D47, D48c), and renders as the four-field plain-language block `Result`/`Sent`/`Shared`/`Identity` plus a subordinate `▸ technical:` line carrying the complete unmodified report text (D47, D48a). `Identity` reads **"new — minted fresh for this site"** or **"known — recognized only here from previous visit"** per the D38/D39 per-(origin,zktag) key state (D48). `Shared` renders as a LIST of `<predicate>: <boolean>` lines (today exactly one: `age > 18: true`/`false`, comparison or bare-boolean predicate form, literal `true`/`false` never "yes"/"no", D49), followed by the negation line, with predicate/answer taken from the verified request and the actual scan outcome, never hardcoded or assumed (D48/D49); an empty list renders plain "nothing shared" wording, never an empty label or stray colon (D49) — each entry accurate to the actual outcome (a success, a refusal, a masterlist "no", an unmet mint gate, an access-establishment failure, a bare mode-A read, a diagnostic probe (D48b), and a refused consumed/expired handoff attempt (D50) each read distinctly), never claiming disclosure that didn't happen or reading as success on a failure path — while staying value-free except for the disclosed claim predicate(s) themselves (D46/D48/D49, item 5). **NO LONGER states a plain-language Mode line (D53 supersedes the mode half of D51 — redundant with Sent/Shared/Identity, and misleading given D21's always-read/conditionally-mint rule); mode stays in `▸ technical:` and in the on-screen derived-mode display (item 4) only.** **Chip-authenticity three-state rule STANDS (D51, unchanged) with owner-approved strings (D53): verified → `Verified — this document's chip proved it is genuine`; not supported → `Not supported — this document has no chip authenticity check`; failed → `Not verified — the chip check did not pass`; never rendering "not supported" as "false"** **Pane visibility across `loading_layout`/`main_layout`/`log_layout` is now written by ONE function on every call, making the both-visible overlap state unrepresentable; the bug was `log_layout` painting over `main_layout` as overlapping `FrameLayout` siblings, discovered via the D44 tab listener leaving `loading_layout` unowned; `onTabReselected` is now idempotent; a completed read does NOT auto-switch tabs (owner considered and declined); the pane-decision logic lives in a pure, Android-free, unit-tested object, since `View.visibility` is a non-functional stub under this module's `isReturnDefaultValues = true` test config (D55)** **DEVICE-CONFIRMED 2026-09-02 (D57): see item 15's row above and
`docs/logs/M2-D55-D56-EVIDENCE.md` for the same caveat — the pane-overlap mechanism this item's fix
targets was not directly reproduced-and-resolved on device this session, only its two
prerequisite behaviors (clean happy-path minting, D56's diagnostic) were confirmed** |

## 7. Riskiest-assumption register (what M0 must answer)

1. **Issuer-free derivation works**: the chip's stable data is readable, verifiable against a public masterlist, and yields the same secret on every scan. **Checked 2026-08-30**: issuer-free ZK proofs over passport SODs are published and shipping (zkPassport, Rarimo, Self); the "unpublished" claim is withdrawn. What remains novel is only the combination with our disclosure/tier model.
2. **JMRTD + the owner's actual documents (US passport, NL ID card) + the Pixel 6a** actually cooperate on this desk, this month — including **PACE** for the NL card, which has been PACE-only since 2022. Prior: both documents have been read by commercial KYC NFC apps, so a miss points at our harness before it points at the chip.
3. **Masterlist coverage**: the owner's issuing country's CSCA cert is present and current in the free public lists.
4. **Attestation verification is implementable within our dependency rules** — whichever root Q14 selects. **M1 POC 2026-08-29: holds for parsing and chain verification — stdlib only, 163-line DER walker, zero deps.** Play Integrity decode requires a Google server call per check (stdlib-only client verified 2026-08-30).
5. **Derivation-field choice** (D9): document number (changes at renewal → zktag rotates ~10-yearly) vs personal number where present. M0 reports what the chip actually contains; D9 is taken after, on evidence.
6. **We are not issuer-free — attestation has an issuer.** "No CA, no issuer" is true of *identity* (the government already issued the document) and false of *attestation*. NO-GO #3 forbids *us* running an issuer and is silent on depending on someone else's. The size of that dependency is decided by Q14:
   - **Play Integrity** — a live Google *service*: Play Console registration, Play Services on device, quotas, revocable. No fallback if access is denied.
   - **Hardware key attestation** — a vendor-signed *certificate root* the verifier pins. No runtime service call, no registration, no quota, no gatekeeper. A CA relationship, not a dependency on someone's uptime or goodwill.

   Correct phrasing either way: **issuer-free identity, vendor-rooted attestation.** Never claim more.
7. **Attestation choice decides whether the product contradicts its own audience.** Anonymous, no-PII personhood proof appeals disproportionately to people running GrapheneOS, CalyxOS and de-Googled devices. Under Play Integrity those users fail permanently, by design. Observed concretely during the M0 hardware search: six custom-ROM devices rejected outright. **Mitigable, and the mitigation is Q14** — key attestation needs no Play Services and GrapheneOS supports it deliberately. **D23 2026-08-30: accepted for v1; tracked as Q24.**
8. **The attestation may itself be the identifier that mode A promises not to emit** (Q15). Mode A's guarantee is only as good as the attestation payload: a device-unique attestation key, an unshared certificate intermediate, an OS/patch-level string, or a precise timestamp each reintroduce linkability through the back door. **Mode A is a claim about the whole payload, not about our fields.** Unmeasured until M1b; until then mode A is a design intent, not a property. **M1 POC 2026-08-29: confirmed. The raw attestation chain contains a stable per-device intermediate on both StrongBox (factory keybox) and TEE (RKP, 13-day cert) paths, plus stable verifiedBootKey/verifiedBootHash/patch levels. Tier A cannot carry the raw chain. Resolution is open — see Q23.** **Resolved for v1 by D23: Play Integrity token carries no device-unique field (measured).**

## 8. Requirements

- **FR1 Scanner** — vetted-lib chip read; local SOD verification; no telemetry, no account, no network call except masterlist refresh. Mode B derivation happens in the enclave and the secret carries an enclave-enforced max age (D10) — never app-side date arithmetic. An expired secret blocks mode-B presentation and cert issuance alike. **Mode A derives no secret and caches no identity-bearing material.**
- **FR2 zktag (mode B only)** — `HMAC(secret, verified-domain)`; the domain is computed client-side (FIDO-style origin binding) and never accepted from the server.
- **FR3 Verifier** — stateless; never-throw; verdict `{ok, allowed, reason}`; all stores adopter-supplied and failing closed. The verifier routes evidence to registered plugs and enforces the ok/valid separation; it never judges evidence itself.
- **FR4 Challenge** — HMAC self-authenticating nonce, single-use spend, atomic store shape (Redis `SET NX PX`) — 8een piece-2 design reused. The challenge also carries the **mode request**, the threshold, and any freshness requirement (D10).
- **FR5 Delegation (rung 2)** — VC-shaped cert, per-agent serial, individually revocable, expiry mandatory.
- **FR6 Uniformity — narrowed (v1.4, D15).** All presentations from **one client build in one mode** MUST be byte-shape identical: fixed field set, fixed sizes, fixed version string, no per-device assurance-tier metadata, no per-user optional fields, coarse timestamps. **Cross-client distinguishability is accepted, and is the mechanism** — package name and signing-certificate digest are visible by design because FR10's trust list works by reading exactly those. Consequence, written down rather than discovered: **the anonymity set is "users of this client build in this mode," not "all zkagent users."** A client with 10,000 users offers a set three orders of magnitude smaller than one with 10 million. An adopter that trusts many clients enlarges its users' sets; an adopter that trusts one shrinks it, and should be told so. Not retrofittable. Must be measured (FR9), not asserted. Evidence set is part of the build's fixed shape.
- **FR7 Responsive** — any web surface is responsive, mobile-first (AGENT_RULES hard requirement).
- **FR8 RFC 9421 mapping (rung 2)** — agent requests signed with `alg="ed25519"`; `keyid` = thumbprint of the agent public key. **RFC 9421's own `tag` signature parameter is reserved for protocol labelling and MUST NOT carry the zktag** — the zktag rides only inside the delegation cert. Signature `expires` MUST be ≤ cert `expiry`; the verifier enforces the earlier. The cert travels in its own header, never in `keyid`. A conformant off-the-shelf 9421 verifier must accept our signature without zkagent-specific patches; zkagent adds fields, it does not alter the base.
- **FR9 Unlinkability budget (new, v1.4)** — mode A's no-identifier guarantee is a property of the **entire emitted payload including the attestation**, not of zkagent's own fields. Every field that crosses the wire in mode A must be shown independent of holder and device, by black-box byte comparison with a planted positive control (8een §7.3). Anything that cannot be shown independent must be removed, coarsened, or the mode-A claim withdrawn. **Blocks M3.** Revised by D22: the criterion is stability across *sites*, not across presentations. M1b ran 2026-08-30 (docs/logs/M1B-EVIDENCE.md): passes with one disclosed bucket (D26).
- **FR10 Trust list, adopter-held (new, v1.4, D17)** — the verifier is configured with the client identities it accepts: `trustedClients: [{ name, package, certDigest, specVersion }]`. Attestation reports package name + signing-cert digest; that pair *is* the client's identity, and a modified APK must be re-signed, changing the digest. **We publish no list and run no registry** (NO-GO #3) — the adopter curates. Open core, curated trust: being open-source does not make a client trusted. A client not on the list is rejected even if its source is identical.
- **FR11 Published derivation spec (new, v1.4, D16)** — the mode-B derivation is a **versioned public specification** (`zkagent-derivation/1`), not an implementation detail. The chip-data input is the **document number** (D9, closed 2026-08-31). `zktag = HMAC(KDF(chip data), domain)` mentions no application, so the same document + same domain MUST produce the same zktag whichever conformant client computed it. This is what makes a borrowable core survivable: an adopter can trust two clients and still block one human once. If two implementations disagree, the identity space forks and blocking silently breaks. The spec version travels in the payload and in every trust-list entry. **Uniqueness is conditional, not absolute (noted 2026-08-31, interaction of D9 and D29):** `document_number` lives in DG1, which passive authentication cannot distinguish from a byte-for-byte clone (Q18) — a document presenting without chip authentication yields the identical zktag for the genuine holder and any clone of them, inheriting the holder's pseudonymous reputation and any block placed on it. The "one human, one zktag" guarantee — and with it blocking — holds only where the verdict's `chip_auth` field (D21) reads `true`. An adopter that requires unforgeable uniqueness must gate on `chip_auth: true` and accept the coverage loss it costs (D29); the reference posture does not gate on it.
- **FR12 Evidence-type registry (new, D24)** — published, versioned; each entry: data schema, binding rule (nonce, claim, scope), who can verify, linkability class ('none' | 'signer' | 'device'), tier ceiling. Adding a type changes the registry and adds a plug; the core does not change. `zk-passport/1`: tier ceiling A (D25); nonce via `service_subscope`. **Disclosure (D26)**: `zk-passport/1` reveals the document circuit class — DSC profile ≈ issuing country/document generation — stable across sites; bare mode reveals nothing. **Granularity (D28)**: `current_date` is client-coarsened to midnight-UTC of the scan day before it feeds the circuit, so the effective floor on `max_scan_age` is 1 day — an adopter configuring a tighter window gets day-level effective precision, not tighter enforcement. The M2 reference app ships bare, not with `zk-passport/1` (D27). `sig-ed25519/1` (new, D30): an attester-held Ed25519 key signs the challenge binding — claim-hash + nonce + scope + zktag (`binds.zktag`, chiproof ≥0.3.0), settled byte layout (owner-confirmed 2026-08-31): `Ed25519( sha256( utf8("sig-ed25519/1\n") ‖ sha256(canonical(claim)) ‖ base64urlDecode(nonce) ‖ utf8(scopeDomain) ‖ utf8(zktag) ) )`; the nonce-bytes convention (base64url-decoded, not utf8) is shared with `signed-receipt/1` (ruling 2026-08-30); any attester generates its own keypair — no CA, no mdoc machinery, the 8een signed-proof shape streamlined; the verifier checks the signature against the operator-pinned attester pubkey. Linkability class 'signer' — the attester pubkey is stable per attester. **Tier ceiling B**: a signer key stable across sites would break tier A's cross-site bar (D22/FR9); tier-A use would require per-site attester keys and is out of scope for the default. The ceiling is orchestrator-recommended, not owner-decided — the owner can veto it. **Amended 2026-09-01 (D38, "agree b+c"):** `item.data` for `sig-ed25519/1`/`sig-p256/1` is `{ key_id, pubkey, sig }` — the presentation now carries the public key itself, not just a `key_id` into a list the verifier is assumed to already hold; `key_id` remains the hash of `pubkey` and the verifier MUST recompute it and compare, never trust the claimed `key_id` alone. Verifier-side trust for these plugs is a pluggable **attester-key store**, keyed by `(scope, zktag)`: an unknown `zktag` binds the presented key on first valid presentation (`reason: attester_bound_first_sight`, trust-on-first-use — the app's own per-origin key generation, §6.2 item 1, is what makes "first sight" meaningful per site); a known `zktag` presenting a different key MUST fail `valid:false, reason: attester_key_mismatch`. The pre-D38 **operator-pinned key list stays supported as an alternative store** — that is what the spike used until this run, and what an operator with enrolled/registered attesters (a government, a KYC vendor) would still use instead of TOFU. The store itself is operator-run, same as every other adopter-held store in this PRD (blocklist, trust list, nonce store) — NO-GO #3 unchanged: we run neither store. **Linkability class, escalated rather than assumed:** D38's TOFU-bound, per-origin key does not cleanly match either alternative in FR12's three-value taxonomy — it is not 'signer' in the original sense (a persistent third-party attester identity an operator pre-registers), and it is not 'device' as FR12 defines it for `key-attestation/1` (a cross-site-stable hardware chain, which is exactly what tier ceiling B/C exists to contain). A TOFU per-origin key is closer to the zktag's own trust shape — self-asserted, scoped to one relationship, unlinkable across sites by construction (item 1's per-origin generation) — which none of the three values names. **Recorded here as unresolved, kept at `linkability: 'signer'` / tier ceiling B unchanged for now** (the tier ceiling was already the conservative bound and stays correct either way); a fourth taxonomy value, or a redefinition of 'signer', is an owner call, not invented here. **D39 (2026-09-01)** narrows the same key one level further — per `(origin, zktag)` rather than per origin alone (§6.2 item 1) — which moves it closer still to the zktag's own self-asserted, unlinkable-by-construction shape, but does not resolve which of the three taxonomy values fits. **Orchestrator recommendation, pending owner veto:** keep `linkability: 'signer'` and tier ceiling B unchanged — a per-(origin,zktag) key is strictly narrower than a stable signer identity, and is clearly not `'device'` (which denotes a stable hardware fingerprint, e.g. `key-attestation/1`'s raw chain, unaffected by either origin or zktag); neither existing value is made wrong by D39, so inventing a fourth is not required to ship correctly. The escalation stays open for a future taxonomy revision, not for a tier-ceiling change. **Closed 2026-09-01 (D41, owner: "leave it"):** `sig-ed25519/1`/`sig-p256/1` keep `linkability: 'signer'`, tier ceiling B unchanged, no fourth value invented. Rationale folded in here: under D39 each key is scoped to `(origin, zktag)`, so it is not a stable per-device value — it is a fingerprint of one (device, site, document) triple, exactly the pseudonym the verifier already holds under a different name; `'device'` is reserved for a value that is the same at every site, permanently (the archetype being a raw key-attestation chain, D22 — a stable per-device identifier). **D41 also settles the general question the owner raised** (does the class track the technology a plug uses, or the plug itself): linkability class is a property of the plug, measured from what its payload actually exposes, never inferred from the category of technology behind it — binds every future registry entry, not only this one. Verified in code as of this revision: `zk-passport/1` → `'none'` (`packages/chiproof/src/plugs/zk-passport.js`), with D26's disclosed `vk_sha256` circuit-class bucket as the one named exception (cross-site-stable, document-dependent, disclosed not hidden); `sig-ed25519/1`/`sig-p256/1`/`signed-receipt/1` → `'signer'` (`packages/chiproof/src/plugs/attester-sig.js`, `packages/chiproof/src/plugs/signed-receipt.js`); a hypothetical, unbuilt `key-attestation/1` → `'device'`, the archetype. The test for `'device'` is: one value, the same at every site, persistent — hardware provenance alone does not qualify a plug for it. **Worked example, Play Integrity:** intuition says `'device'` because the verdict is hardware-rooted, but M1's own spike (2026-08-30, `docs/logs/M1-Q23-EVIDENCE.md`, tokens captured twice on the Pixel 6a and diffed) found no device-unique field in the decoded verdict across sites — on that evidence it would NOT be `'device'`; most likely `'signer'` (Google's key signs the verdict) or arguably `'none'`. This is explicitly **not** a class assignment — no Play Integrity plug is built — because that evidence was gathered to answer a different question (D23/Q23's cross-site bar), not to classify a registry entry; any future Play Integrity plug's class MUST come from a fresh probe of its own payload, not from this paragraph.

## 9. NO-GO table — check before proposing any feature

| # | NO-GO | Why |
|---|---|---|
| 1 | **We store nothing server-side. Ever.** No identity, no chip data, no zktags-at-rest, no logs of who verified | Statelessness is the security argument, not a limitation (8een NO-GO #7 lineage) |
| 2 | **No custom security-critical code**: chip parsing, attestation parsing, crypto — vetted libs / platform APIs / stdlib only | AGENT_RULES; 8een NO-GO #8. If the answer is "write our own X" and X is cryptographic or parses untrusted input, the answer is wrong |
| 3 | **No CA, no issuer, no enrollment server, and no trust list run by us** | Issuer-free is the product. The day we run an issuer — or a registry of blessed clients — we've rebuilt the thing we set out to kill |
| 4 | **No unmasking capability** — not escrowed, not quorum-gated, not "for emergencies." Max penalty = exclusion | A capability that exists can be compelled. Owner decision, final |
| 5 | **Never claim "one human = one zktag"** — always "at most k (k = documents held, ~1–3)". Never claim more than captcha-grade. Never describe v1 as replay-safe, sybil-proof or zero-knowledge beyond what a measurement showed | Overclaim is the death of a trust product; 8een's evidence-doc discipline applies |
| 6 | **No web-NFC scanner** — the scan is native, period | Platform wall (NDEF-only browsers), not a preference |
| 7 | **No ZK circuits of ours in v1, and v1 is never described as zero-knowledge; third-party ZK proofs may enter only as an evidence plug (D24), validation-grade, tier A only (D25), with Track Z's gates governing any security claim** *(amended 2026-08-30, D24/D25)* | D1. Captcha-grade bar; every line must have a purpose today |
| 8 | **No npm publish until the package is standalone-usable** — placeholder reservation only; publishing is a deliberate manual owner step | zk8een binary-distribution lesson, verbatim |
| 9 | **No secrets/test keys in the tree** — runtime-generated, temp dirs only | AGENT_RULES + 8een PRD §10 |
| 10 | **No feature enters a milestone unless it's in this PRD first.** New idea → PRD change → owner sign-off → build. Mid-milestone additions are refused by default, including owner-tempting ones ("while we're in there…") | The scope gate. This project's conversation history generates ideas faster than any team could build them; the PRD is the filter, not the collector |
| 11 | **No stable identifier in mode A** — not a zktag, not a device id, not a "rate-limit key", not a hashed anything, not "just for fraud detection" | This is where the pressure will come from, and it will sound reasonable every time. Mode A's entire value is that the field does not exist to be leaked, subpoenaed or correlated. An adopter that needs to recognise a returning holder must request mode B and be seen to request it (D13) |

## 10. Owner decisions

| D | Decision |
|---|---|
| D1 | v1 trust root = government chip signature + OS attestation. No ZK circuits in v1; ZK is a named future tier (amended 2026-08-30 by D24: ZK proofs are allowed in v1 as an evidence plug, validation-grade; no ZK circuits are *ours*; Track Z gates still govern any security claim.) |
| D2 | *(amended 2026-08-02, device settled 2026-08-07)* Native thin scanner app, **Android first** (owner's own app wrapping JMRTD); everything else web. Google Play $25 one-time vs Apple $99/yr, builds on the owner's Fedora box with no Mac, test builds sideload free. iOS revisited only when demand justifies the cost. **Development device: Pixel, stock ROM. All other vendors ruled out for M0–M2.** Reasoning recorded so it is not relitigated: NFC Type A/B is *not* the discriminator (baseline on every phone-class NFC controller), and the extended-length-APDU variance that drives commercial ID vendors' device blocklists does not apply to us because we never read DG2 — DG1 + SOD fit in short APDUs with chaining. The real discriminator is attestation quality (Q14b), where Pixel gives the reference implementation, guaranteed StrongBox, stock ROM and the longest update runway. Huawei and China-market ROMs are excluded outright (no Play Services; a non-Google attestation root). **Rationale is debuggability, not capability** — on a Pixel a failure means our code is wrong; on an unpredictable OEM it means our code is wrong *or* the vendor's Keymaster is. A non-Pixel second device is an M2 concern (testing only on Pixel silently encodes Pixel assumptions), not an M0 one. App Attest is understood to follow the same voucher model as Play Integrity (Apple-issued per-key cert under a shared CA) — **unverified**, to be measured if iOS is ever taken up |
| D3 | Stateless, 8een-style: blocklist/nonce/trust stores adopter-supplied; we store nothing |
| D4 | `ok`/`allowed` invariant adopted verbatim (§3) |
| D5 | *(amended 2026-08-02)* `zktag = HMAC(chip-derived secret, verified service domain)` — client-side scope binding. Named `zktag`, not `tag`, to avoid a silent collision with RFC 9421's own `tag` signature parameter |
| D6 | New repo; 8een reused as lessons + `challenge.js` pattern + verdict/test discipline; 8een repo untouched |
| D7 | *(amended 2026-08-07 → superseded in part by D12)* Name: **zkagent** as the project. Verifier SDK ships via npm; scanner app via Play |
| D8 | Issuer-free derivation is the named riskiest assumption; M0 targets it before anything else is built |
| D9 | *(closed 2026-08-31, owner decision, on evidence in `docs/logs/M0-EVIDENCE.md` Findings 3, 10, 11)* **The mode-B derivation field is the document number.** It exists on every ICAO 9303 document, so the greedy `acceptedDocuments` default (D14) loses nothing by construction; M0 showed it deterministic across rescans and collision-free across the two documents; it rotates at document renewal (~10-yearly), accepted as within the captcha-grade bar — a blocked holder re-enters at renewal. Rejected: the optional-data/personal number (merges a holder's documents into one identity — the explicit non-goal of the 2026-08-07 narrowing); the full-DG1 hash (same renewal rotation, plus rotates on any issuer field correction); the chip keys (DG14/DG15 — absent on the US passport per Finding 4, so requiring them narrows `acceptedDocuments` by construction). Caveat kept: renewal stability itself is unmeasured — no renewed document has been scanned. **Narrowed 2026-08-07** (stands): cross-document unification is a *non-goal*, so a personal number's ability to merge a passport and an ID card into one identity was a cost to be avoided, not a feature to be sought. **Interaction noted 2026-08-31 (not a reversal):** the chosen field lives in DG1, which passive authentication cannot distinguish from a clone (Q18); combined with D29, mode-B uniqueness and blocking hold only where `chip_auth: true` (D21) — see D29, FR11 |
| D10 | *(2026-08-03, revised 2026-08-07)* **The mode-B derived secret has an enclave-enforced maximum age; a fresh scan is required to renew it.** Default 30 days; the client build may configure 30/60/90/180 and MUST NOT exceed 180. Rationale unchanged: indefinite caching meant a single borrowed scan bought a permanent identity the document's owner could neither detect nor revoke; a ceiling converts one-time possession into recurring possession. **Freshness is negotiated, not fingerprinted:** a verifier that needs fresher may state `max_scan_age_days` in the challenge, and the presentation answers with **one bit** — never an age in days, which would be a fingerprint (FR6). Accepted cost: the document must stay accessible; a lost document degrades to no-renewal after the ceiling. **Mode A is unaffected — it caches no secret** |
| D11 | *(2026-08-03)* **Age threshold is configurable; the output stays one bit.** Adopted verbatim from 8een D6 (`src/index.js:195-217`, `src/verdict.js:103-117`): the adopter sets `threshold` (default 18), the SDK requires claim `age_over_${threshold}`, and the verdict carries a single `over_threshold` boolean. Rejected alternative: emitting a ladder of thresholds (13/16/18/21) at once — that narrows the holder's age to a bucket and roughly doubles the entropy disclosed, where one requested threshold discloses exactly one bit. **A proof of a threshold other than the one requested MUST be rejected, not accepted as close enough** (8een `src/verdict.js:211`). Fixed allowed set caps binary-search probing; see Q11 |
| D12 | *(2026-08-07)* **Project and package names split: project `zkagent`, published package `chiproof`.** Precedent: the owner's own `8een` project / `zk8een` package. The project name carries the direction of travel; the package name says what it does — it reads a chip and proves something about it. `chiproof` verified available on npm 2026-08-07 (404 on the registry); reserve early — 8een lost the bare name to npm's typo-squat filter with no appeal. **Reserved 2026-08-07** — `chiproof@0.0.0` published by the owner as a manual step (NO-GO #8), source at `packages/chiproof/`: two files, no code, README stating plainly that it does nothing and that this is not zero-knowledge. **Licensed Apache-2.0, matching the repo**, correcting the standing defect in the `zkagent@0.0.0` placeholder, which is published as MIT and must be fixed or deprecated when NO-GO #8 is next revisited |
| D13 | *(2026-08-07)* **Disclosure has two modes, and the verifier must ask for the one it needs.** **Mode A (anonymous, default)** emits one bit and no identifier; two presentations by the same holder to the same service are unlinkable. **Mode B (pseudonymous, opt-in)** additionally emits the domain-scoped zktag, which is linkable within that service and unlinkable across services. Rationale: uniqueness and unlinkability are in direct tension and only one leg of the product needs each. Age verification needs no pseudonym and emitting one is a pure privacy regression; agent accountability is *defined* by recognising a returning human. This also aligns the age leg with the EU Age Verification Blueprint's own privacy properties (§12) without adopting its infrastructure. **Rules:** mode B MUST be requested explicitly in the challenge and MUST NOT be inferred, defaulted to, or silently upgraded; the app SHOULD show the holder which is being asked ("this site only learns you are over 18" vs "this site can recognise you again"); a mode-A payload MUST be byte-shape identical whether or not that device has ever made a mode-B presentation (FR6). **The rung-2 agent layer is mode B only, structurally** — a delegation cert hangs off a persistent human root, so an agent that cannot be recognised cannot be revoked, and mode A emits nothing to bind a cert to. Agent delegation cannot be built on the anonymous path; do not attempt it |
| D14 | *(2026-08-07)* **Accepted document types are adopter-configurable, and the default is greedy.** `acceptedDocuments` defaults to every ICAO 9303 document the client can read; an adopter narrows it. Reach is the default because **`k` has no cost in mode A** — with no identifier emitted, a holder with three documents is not three identities, they are three ways to answer the same question. `k` is a real cost only in mode B, where it bounds the uniqueness claim; a mode-B adopter that needs k≈1 narrows to `['passport']` and knowingly trades reach for it. Consequence: NO-GO #5's "at most k" wording is a **mode-B** claim and must not be stated as a general limitation of the product, nor omitted from any mode-B pitch |
| D15 | *(2026-08-07)* **FR6 is narrowed, not retired.** Uniformity is required *within* a client build and mode; cross-client distinguishability is accepted because the trust list (FR10) works by reading exactly the package name and signing-cert digest that distinguish clients. This is safe only because of FR11: since the derivation is a published spec, two clients reading the same document produce the same zktag — so a visible client identity partitions the *anonymity set*, not the *identity space*. The anonymity-set cost is stated in FR6 and must be measured, not assumed |
| D16 | *(2026-08-07)* **The derivation is a published, versioned specification** (FR11) — a prerequisite of the borrowable core, not a nice-to-have |
| D17 | *(2026-08-07)* **The trust list is held by the adopter, never by us** (FR10). Worked example, recorded because it is the model: a national police force publishes once — package `nl.politie.id`, cert digest `sha256:CC:DD:…`, spec version `zkagent-derivation/1`. A webshop configures `trustedClients: [official, politie]` alongside `threshold`, `acceptedDocuments`, `mode` and `masterlist`. At verify time the SDK validates the attestation signature, extracts package + digest, and accepts only what is listed. A malicious clone is rejected even though the source is open. No API key, no contract, no relationship with us — and no registry for us to run, be compelled to alter, or be blamed for |
| D18 | *(2026-08-07)* **Sequencing: the agent layer is not designed, discussed or specified further until the age-verification leg is finished.** Rung 2 stays as written — decided, bounded, not started — and reopening it before rung 1 ships is refused by default, including on a passing owner request. Rationale from this session's evidence rather than from principle: Q18 (chip cloning defeating mode-B uniqueness, and with it D10's entire rationale) sat undetected through four PRD revisions and surfaced only when the read path had to be written out concretely for the design companion. More design is currently producing more surface, not more certainty. This PRD carries 18 decisions, 11 requirements and 9 open questions against zero lines of code; the agent layer would add a third document's worth of each. **M0 first** |

| D19 | *(2026-08-29, shape only — mechanics deferred)* **Disclosure has three tiers, and the tier is what the holder is told, not what the app decides.** Every presentation shows the holder what is being asked before the tap; the tier sets the wording. **Tier A — anonymous, the default**: one boolean, no identifier; screen says *"this site learns one fact and nothing that follows you to other sites."* (revised by D22) Open to any requester. **For `zk-passport/1` specifically (D26): "this site learns one fact, plus roughly what kind of document proved it, and nothing that follows you as a person to other sites"** — bare mode keeps the original wording. **Tier B — pseudonymous**: tier A plus the domain-scoped zktag; screen says *"this site can recognise you again — here only."* Open to any requester, because the pseudonym is `HMAC(secret, domain)` and a site can only ever compute its own (FR2) — B's safety is arithmetic, not judgment. **Tier C — attributed**: booleans over identifying fields (name-matches, nationality-equals, …); screen lists each in plain words: *"this site is checking who you are."* **Gated: accepted only from a challenge issuer whose key the app build pins at tier C** (D20). A tier-C challenge from an unpinned key is **refused, not downgraded** — a silent downgrade would let a requester probe. The verbs a requester may ask are a **published, versioned vocabulary we write** (like FR11) baked into the app: a list of question *types*, never a registry of *askers* (NO-GO #3). The operator's configuration surface is that vocabulary with verbs switched on or off — **verbs an operator may *ask*, never data anyone *captures*: nothing is retained by anyone at any tier (NO-GO #1).** Rationale: field *count* is the wrong knob — two verbs can be fully anonymous or a full identification; what varies is whether the answer identifies the holder, so that is the axis. KYC and document-signing flows live in tier C and are **separate products borrowing the core (§4)**, not modes of this one. Supersedes the "mode A / mode B" wording where the two conflict; D13's rules for A and B are unchanged |
| D20 | *(2026-08-29, shape only — mechanics deferred)* **Challenges are signed by their issuer; the issuer's public key is its identity.** Resolves the principle of Q20 without a registry: a requester (or an authority acting for many requesters, e.g. a government for every site legally bound to check age) generates a keypair once, signs each challenge, and gives the public key to the app builds it wants to accept it. An app build pins issuer keys **with a tier ceiling** (`trustedChallengeIssuers: [{ pubkey, maxTier }]`) — the same local-trust shape as FR10, pointed at challenges instead of clients. Uniqueness needs no allocation: a keypair is a name nobody hands out. The challenge object carries `nonce, tier, verbs, threshold, max_scan_age, issued_at, expires_at, key_id, sig`; the app verifies `sig` before reading the chip, the verifier re-checks it on return. **Signature, not encryption** — the point is that anyone holding the public key can check origin. The *response* shape stays fixed regardless of which issuer asked (FR6). Supersedes the split-nonce sketch (a shared secret format achieving the same rejection with weaker guarantees). Unsigned challenges: accepted at tiers A and B (nothing to protect that the tier does not already protect), refused at C *(amended 2026-08-30: the nonce HMAC seals every challenge field, so unsigned tier A/B challenges are tamper-evident — a holder cannot edit `max_scan_age`, `threshold`, `tier` or `expires_at`)* |
| D21 | *(2026-08-29, evidence-backed — M0 Findings 4, 9)* **Always read, conditionally mint; chip authenticity is the requester's constraint, reported in tiers B/C only.** The app reads whatever the document offers — BAC or PACE, with or without AA/CA — with no mode selection up front. Minting a zktag is the gate, and the **verifier** enforces the policy, not the app alone (a modified app could mint regardless): the tier-B/C payload carries `chip_auth: passed | absent`, and the requester configures whether it accepts a document that cannot prove it is the original (M0 showed the US passport cannot; the NL identity card can). This is D14's `acceptedDocuments` trade-off made concrete and unintuitive. **Tier A never emits `chip_auth`** — it has nothing to impersonate and the flag would partition anonymous holders into document classes (FR6/FR9). A tier-A payload is byte-identical whether or not minting could have happened |
| D22 | *(2026-08-30, owner decision)* **Tier A's same-site unlinkability promise is relaxed from requirement to non-goal; cross-site unlinkability is the requirement.** Reasoning (owner): a site the holder returns to already links visits through cookies, IP and browser fingerprint; promising that the *same* site cannot recognise a return costs fresh-per-presentation cryptography (ZK proofs) and buys little. What must hold is that **nothing in the payload is stable across sites** — a global identifier would let one site that knows the holder (a bank, a shop with a delivery address) deanonymise them on every other site, which is the profiling this project exists to prevent. Consequences: D19's tier-A wording changes from "this site learns one fact and cannot recognise you" to **"this site learns one fact and nothing that follows you to other sites"**; FR9's black-box comparison keeps its method but its pass criterion becomes "no field stable across sites" rather than "no field stable across presentations"; M1b's checkpoint is updated accordingly. The ZK-proof-of-passport route (fresh, unlinkable proofs even to the same site — the shape of zkPassport/OpenPassport-class projects) was considered on 2026-08-30 and set aside as unnecessary for this goal, not as infeasible; D1 stands |
| D23 | *(2026-08-30, owner decision, on evidence in `docs/logs/M1-Q23-EVIDENCE.md` and `docs/product/zk-due-diligence.md`)* **(superseded by D24, 2026-08-30 — Play Integrity is not borrowable: decoding is tied to the app developer's Cloud project; only an adopter-run decode can exist, as a `signed-receipt`. We run no relay — NO-GO #3 stands.)** **v1 attestation is voucher-grade (Play Integrity); D1 stands; ZK over the passport is a named second track with written gates, not a maybe.** Rationale: Play Integrity measured clean on the cross-site bar (no device-unique field in the decoded verdict); the ZK composition verified on desktop for both real documents, but every passport-capable ZK component is pre-1.0 or unaudited — Noir/Barretenberg disclosed a forged-proof soundness bug in 2026-03 and a second critical in 2026-07, zkPassport has no published audit and a closed mobile app, no non-crypto organisation has shipped ZK-over-passport. A v1 security claim cannot honestly rest on that today. **Gates for the ZK track (all must hold before D1 is revisited):** (1) Barretenberg/UltraHonk at a stable release with a published core audit; (2) an independent audit of the exact circuits zkagent would use (DSC→CSCA, SOD signature, DG1 integrity, age); (3) a measured proving time on a mid-range phone under a UX ceiling set in the PRD at that time; (4) a nullifier path that is chain-free with known operators, or none; (5) an open-source on-device prover. Engine note (2026-08-30): longfellow-zk has no RSA support and no passport path (docs/product/learnings.md §3, longfellow entry, and §6.11); Track Z's engine is Barretenberg unless that changes. **What carries over unchanged when the track lands:** the tier model (D19), signed challenges (D20), the verifier core, the trust list — only the voucher swaps. **v1 claims (precise):** no identification at any tier; nothing stable across sites; same-site recognition only in tier B by request; Google decodes every check and is a dependency (risk 6); de-Googled devices are excluded in v1 (Q24). The word "zero-knowledge" is not used for v1 (design doc §5) |

| D24 | *(2026-08-30 (late), owner decision, on evidence in `docs/product/learnings.md` §3 (longfellow entry) and §6.11, and §2 (Play Integrity non-borrowability finding) — Play Integrity tokens can only be decoded by the app developer's own Cloud project — non-transferable per ToS, per-app quota, i.e. non-borrowable)* **The core ships with an evidence slot; v1 works with the slot empty; what fills it is the adopter's choice.** The presentation is `{spec, tier, claim, challenge, zktag?, evidence[]}`; the claim/challenge/zktag shapes are fixed by us (D11, D20, FR11); `evidence` is a list of `{type, version, data}` envelopes. Each evidence type is a plug with one contract — `verifyEvidence(item, ctx) → {ok, valid, reason}`, never throws, `ok:false` ⇒ verdict `ok:false, allowed:null`, `valid:false` ⇒ `allowed:false` (§3 invariant one level up) — and MUST bind the nonce, the claim and the scope; a type that cannot bind is rejected at registration. The adopter sets policy, not code: `evidence.require[]` (empty = bare mode, captcha-grade, knowingly), `evidence.accept[]`; unknown types are ignored (no probing by unknown evidence); types whose registry entry carries a linkability class other than 'none' are refused in tier A by the core. Registry of evidence types is published and versioned by us (FR12) like the verb vocabulary; initial entries: `zk-passport/1` (a ZK proof of gov-signed passport + threshold; verifiable by anyone offline; linkability none; engine replaceable under FR11 and the fixed claim shape — validation-grade until Track Z's gates), `signed-receipt/1` (a party the adopter trusts signs hash(claim)‖nonce — e.g. a government or a client build's developer vouching for that build's output, incl. an adopter-run Play Integrity decode; verifiable with that party's public key; linkable at the signer, disclosed), `app-attest/1` (iOS, later; Apple-rooted chain verifiable by anyone), `key-attestation/1` (Android hardware chain; linkability 'device' → tiers B/C only). Precedent: WebAuthn attestation formats with `none` as a first-class format. Bare mode is the 8een shape minus the proof: unidentifiable, nothing stable across sites, nonce fresh per challenge regardless of evidence; the difference from 8een — the claim itself is not proven — is stated in every claim. A build ships one fixed evidence set (FR6: the anonymity set is 'users of this build with this evidence set') |

| D25 | *(2026-08-30 (late), owner decision, on evidence in `docs/product/learnings.md` §3, entry "zkPassport age circuit cannot be nonce-bound while keeping a stable nullifier")* **`zk-passport/1` ships in M1 as the proof that the evidence slot is generic, with tier ceiling A only.** The zkPassport age circuit exposes no nonce input; its only free public field, `service_subscope`, feeds the nullifier. Therefore the plug carries the challenge nonce in `service_subscope` (registry rule: first 31 bytes of sha256(nonce) as a BN254 field), which makes the nullifier per-request — ideal for tier A (unlinkable by construction), unusable as a tier-B/C zktag. The claim binds via the pinned `param_commitment` for the configured threshold; scope via `service_scope`. Tier B/C with ZK evidence is deferred to Track Z as Q26. No circuit is forked (D1 as amended stands: no circuits are ours). Consequence for the slot: the core did not change — the plug declares what it can bind and its ceiling; the registry enforces it. That is the genericity claim, demonstrated with two real plugs of different kinds (`signed-receipt/1` in-process, `zk-passport/1` external) plus adversarial test-only plugs. Measured: `bb verify` 5.0.0 ≈0.035 s for the four-stage composition on the real NL document |

| D26 | *(2026-08-30 (late), owner decision, on evidence in `docs/logs/M1B-EVIDENCE.md` §4–§5 and `spikes/m1b-unlink/leak-*.mjs`)* **Disclose the circuit-class bucket in tier A for `zk-passport/1`.** M1b found every salted commitment, nullifier and subscope fresh per presentation; the only cross-site-stable document-dependent fields are `dsc.vk_sha256`/`id_data.vk_sha256`, which encode the Document Signer certificate's circuit class (TBS-length bucket, RSA/ECDSA + key size, hash; the zkPassport family has 284 dsc / 282 id_data variants; NL and US land in different classes). Leak-closure validation (2026-08-30) shows the bucket cannot be hidden by a wire-format change: dropping the field with more than one pinned key makes the plug fail `zk_unknown_circuit`, and a verifier trying pinned keys recovers the class deterministically in ~50–90 ms per trial; raw proof bytes carried no NL/US fingerprint of their own (0/2,752 constant positions, 3 runs/doc) — **the bucket is the shape of the proof, not a field we send.** Rejected/deferred removal options: a single circuit covering every class is a zkPassport circuit change, not ours (NO-GO #7) — recorded as an upstream ask under Track Z; an adopter pinning one class removes the bucket only by restricting the service to that class. Consequences: `vk_sha256` stays on the wire; FR12's `zk-passport/1` registry entry gains a disclosure line; D19's tier-A wording for `zk-passport/1` gains a variant sentence (bare mode keeps the original wording). PACE/BAC are not observable in the proof. Closes Q15. Opens Q27 (`current_date` granularity, unresolved) |

| D27 | *(2026-08-30 (late evening), owner decision, closes Q25)* **The M2 reference scanner app ships bare (`evidence: []`) as its one fixed evidence set (FR6).** Rationale (owner choice on the presented trade-off): captcha-grade and honest per NO-GO #5; no unvetted mobile prover in the shipped app — on-device Noir/bb proving time and RAM are unmeasured (M1-Q23-EVIDENCE §6) and every passport-capable ZK component is pre-1.0/unaudited (D23 rationale stands); avoids carrying the D26 disclosed bucket into the reference app. `zk-passport/1` remains a verifier-side plug, exercised against real desktop-generated proofs (D25); measuring on-device proving is a possible later spike, not an M2 gate. Amended by D30: mode-B presentations carry `sig-ed25519/1` as the reference default; mode A stays bare |
| D28 | *(2026-08-30 (late evening), owner decision, closes Q27)* **`current_date` is coarsened to day granularity client-side.** The prover feeds midnight-UTC of the scan day to the age circuit (the field is a `pub u64` the client controls — no circuit or chiproof change needed; the plug's `max_scan_age` comparison logic is unchanged). Consequence: the effective `max_scan_age` floor is 1 day — an adopter configuring a tighter window gets day-level effective precision. Second-level scan-session correlation across sites is eliminated. The upstream granularity ask remains filed (circuits#154) for a spec-level answer |
| D29 | *(2026-08-31, owner decision, closes Q18, on evidence in `docs/logs/M0-EVIDENCE.md` Finding 9)* **Mode B accepts documents without chip authentication.** M0 measured the split: the US passport carries neither AA nor CA (clone-replayable), the NL ID card carries both (clone-detectable). The verifier reports `chip_auth` true/false in the verdict per D21, and every claim and README wording states explicitly that clone-replay of non-chip-auth documents is within the captcha-grade bar. Adopters may tighten (require `chip_auth`) via their own config; the reference posture does not. Mode-B-scoped: mode A is unaffected — no identifier is emitted, so there is nothing to impersonate. Interaction with D10: Q18 was the open risk undermining the 30-day-expiry rationale; that risk is now accepted-and-stated, not mitigated. **Interaction noted 2026-08-31 (not a reversal):** the clone-replayable field this closure accepts is D9's own derivation field (`document_number`, in DG1) — a cloned document without chip authentication mints the identical zktag to the genuine holder's, inheriting their pseudonymous reputation and any block placed on it. Mode-B uniqueness and blocking are guaranteed only where `chip_auth: true`; an adopter needing clone-resistant blocking must require it and accept the coverage loss (D21, FR11) |
| D30 | *(2026-08-31, owner decision)* **`sig-ed25519/1` is the DEFAULT evidence delivery for mode-B presentations.** Owner's words: "ed25519 can be default delivery with mode B, since every attestor can create their own private key — live proof instead of complete vanilla." This AMENDS, not reverses, D27: the M2 reference app's mode-A / default presentation remains bare (`evidence: []`); mode-B presentations carry `sig-ed25519/1` as the reference default — the evidence slot ships with live proof that it works end-to-end, not permanently vanilla. The evidence: an attester-held Ed25519 key signs the challenge binding (claim-hash + nonce + scope + zktag; layout settled 2026-08-31, see FR12). Rationale: demonstrates the D24 slot with vetted, boring crypto — Ed25519 is in the Node stdlib (zero runtime deps, NO-GO #2 clean), and every operator/attester is self-sovereign over its own key: no CA, no mdoc machinery, the 8een signed-proof shape streamlined. Linkability class 'signer', tier ceiling B — see FR12; the ceiling is orchestrator-recommended (a stable signer key across sites would break tier A's cross-site bar), and the owner can veto it |
| D31 | *(2026-09-01, owner decision, on evidence from a live mode-B run on the Pixel 6a / NL ID card, real Chrome `av://` tap → spike verifier: `direct_post` reached HTTP 200 `accepted:true`, but the verdict was `evidence_required_missing`, because `spikes/m2-handoff/server.mjs` still required `sig-ed25519/1` only (D30) while the device — which cannot generate Ed25519 in AndroidKeyStore, F2 — sent `sig-p256/1`)* **The verifier accepts any one of an operator-configured set of attester-sig evidence plugs, not a single fixed one.** Owner's words: "A yes, either." Supersedes D30's single-required-plug framing for mode B: `sig-ed25519/1` and `sig-p256/1` become co-equal reference-default alternatives; the device picks the strongest it supports (F2, §6.2 item 1) and the verifier accepts whichever arrives, provided it is in the operator's accepted set. This requires an any-of/alternatives semantic that chiproof's evidence slot does not have today — `evidence.require` (`packages/chiproof/src/evidence.js:184`) is all-of: every listed key must be `seen` or the presentation is `evidence_required_missing`. chiproof 0.4.0 is unpublished, so the contract change (an alternatives group in `evidence.require`, and the matching `evidence_required` shape in the request object) is cheap now. The verdict MUST record which plug in the accepted set was actually used (§6.2 item 9, unchanged). **Opened Q28** (§11), closed same-day by D36. **Amended 2026-09-01, owner-agreed ("agree, keep it as is") — a stated implementation constraint, not a policy change:** an alternatives group is satisfied by the first present member that verifies, but a group is **not** satisfied by masking — if a presentation carries more than one member of the same alternatives group and any present member is invalid, the whole presentation fails, it does not fall back to a different valid member (`packages/chiproof/tests/integration/evidence-alternatives.test.js`). A real device sends exactly one plug per D36 (it never offers more than its single preferred, non-downgraded choice); two-present is malformed or hostile input, not a legitimate negotiation |
| D32 | *(2026-09-01, owner decision, clarifies D24 — does not introduce a new mechanism)* **The attester-sig plugs are the reference default, not a privileged mode-B requirement.** Owner's words (verbatim-ish): "that signature can be changed/replaced by operators by anything else like zkpassport, or anything they want." D24's evidence-slot design already permits this; this decision records it explicitly so it is not re-litigated: an operator/verifier MAY configure any registered chiproof evidence plug (e.g. `zk-passport/1`) or set of alternatives (D31) as its mode-B requirement, in place of or alongside `sig-ed25519/1`/`sig-p256/1`. Constraint unchanged: whatever the operator chooses still passes chiproof's per-tier linkability gate — tier A: linkability none (`evidence.js`'s tier-A-refuses-linkability check); a 'signer'-class plug (D30's tier ceiling B) or a 'device'-class plug cannot be substituted into tier A regardless of operator preference |
| D33 | *(2026-09-01, owner decision)* **The scanner preselects and locks the presentation mode from a pending handoff request, rather than leaving it to manual selection.** Owner's words: "B yes, app should preselect." New §6.2 item 13: when a handoff request is pending at the moment mode capture happens (§6.2 item 4), the app sets the mode from the request object's `zkagent.tier` field and disables the mode radio for user override; user consent remains the Lock + biometric/credential gate (item 2), unchanged. If no handoff is pending, manual selection works as today. If the pending request's tier is absent or not one of A/B/C, the app MUST fail loudly (log + report) rather than default to any mode |
| D34 | *(2026-09-01, owner decision)* **The scanner verifies the request object's JWS signature against a pinned/provisioned set of trusted request-signer keys before trusting any field inside it** (nonce, `response_uri`, `state`, tier, `evidence_required`). Owner's words: "C yes, it should verify." New §6.2 item 14. Closes the escalation recorded in `HandoffClient.kt`'s class doc ("this client does NOT pin a request-signer key — there is no owner-approved key/config surface for `trustedChallengeIssuers` in this build"). Verification failure, or no matching trusted signer, MUST refuse the handoff (log + report) — never a warning-and-continue. **This narrows D20 for the M2 reference app specifically**: D20 permits unsigned challenges at tiers A/B as a spec-level floor (the nonce HMAC alone seals the fields); the M2 build's own policy is stricter — it requires a verified signature at every tier, not only C. D20 itself is unchanged as the spec floor; a build MAY require more than the floor. **Opens Q29** (§11) — provisioning/rotation of the trusted-signer set on-device is unresolved; recommended default (not yet owner-approved): a build-time pinned dev key for the spike verifier only, with an explicit "no production trust store yet" disclosure alongside every claim this app makes |
| D35 | *(2026-09-01, owner decision)* **The last value-free report text MAY be retained in-memory across Activity recreation** (`onSaveInstanceState` Bundle), never persisted to disk. Owner's words: "D yes." Already implemented (§6.2 item 6); this records approval rather than describing new behavior. MRZ/session-material wipe rules (`onStop()`, not `onPause()`) are unchanged |
| D36 | *(2026-09-01, owner decision, closes Q28)* **The device orders its own key/evidence capabilities by a fixed strength preference and attempts them in that order; it never chooses to downgrade.** Owner's words: "why would code/phone that is mechanical choose to downgrade? it could if one that it can do fails … you should query phone first for what it can do and use it as first pref." This is what `DeviceKey`'s KEY-TEST / `winnerPreference` (`apps/scanner/app/src/main/java/com/tananaev/passportreader/DeviceKey.kt:82`) already does: the device queries its own capabilities first, tries its preferred combo, and falls through to the next only when generation/use of the preferred one *fails on this device* — never as a choice among successes. If the operator's accepted set (D31) contains nothing the device can produce, the presentation fails loudly with the reason; there is no negotiation round-trip. The verifier still accepts any member of the operator's set and records which was used (D31, unchanged) — it cannot enforce "must offer strongest" because it cannot observe device capability without attestation, and attestation is excluded from mode-B evidence (a raw chain is a per-device identifier, D22). Note: P-256 and Ed25519 are equivalent-strength (~128-bit) — the "downgrade" between them is nominal, not a security regression; the meaningful strength axis is `security_level` (STRONGBOX > TEE > software), which the report already asserts (per-module memory rule: assert what came back, don't just confirm success) |
| D37 | *(2026-09-01, owner decision, closes Q29)* **Request trust is origin-bound (EU AV-profile shape), not authority-bound.** Owner's words: "agree, verifier is not our issue, unless it's mode c like kyc and that's operator bound curated list, and i also agree with os level and to cover av:// to ensure requesting website is the same." **Origin binding (closes the av:// hijack half of Q29):** the trust anchor for a handoff request is the requesting site's HTTPS origin, not an authority key. The scanner MUST enforce that `client_id`, `request_uri`, and `response_uri` resolve to one and the same origin (scheme+host+port); `scope_domain` (the zktag scope) is derived from that origin and shown to the user at consent; a mismatch MUST refuse (log + report). This is what "cover av:// to ensure requesting website is the same" means: a hijacked or relayed `av://` tap can only ever route the answer back to the origin that issued the request. **Key discovery (closes D34's provisioning gap, the other half of Q29):** the verifier publishes its request-signing public key at a well-known path under that same origin — `https://<origin>/.well-known/zkagent-verifier` (no prior PRD-named path; exact path is an implementation detail, this is the recorded one) — the scanner fetches it over TLS and verifies the request-object JWS against it; a fetch failure or signature mismatch MUST refuse. Root of trust is TLS/Web PKI, matching the EU Age Verification profile (Annex A: `client_id_scheme` MUST be `redirect_uri`, "client authentication is not required", TLS + Web PKI as the trust root — §12 already cites this profile). **"Verifier is not our issue":** anyone may ask; there is no zkagent registrar or central allow-list at tiers A/B (NO-GO #3 unaffected — this is a bind, not a registry). Rationale: what an asker receives is already scoped to its own origin (mode A: nothing stable; mode B: zktag scoped to `scope_domain`, D5/FR2), so restricting *who* may ask protects nothing — the property that matters is that the *answer* goes back to who the user is answering, which origin binding gives directly. **Tier C exception:** for KYC-like tier C, the relying operator MAY require its own curated allow-list of request signers (the EU Android AV app's `PreregisteredVerifier(clientId, verifierApi, legalName)` shape) — this is operator-bound configuration, per D19/D20's tier-C pinning, not a zkagent-central list. **Direction 2 accepted as a stated limitation, not solved:** the owner accepts OS-level trust for v1 on the mirror question (the requester trusting the app/device is genuine) — same posture as the EU AV profile ("reader authentication is not required and out of scope"; OS/browser are trusted components). No Play Integrity (D24 history — non-borrowable), no raw attestation chain (D22 — per-device identifier). An operator that cannot accept trusting the app selects a plug the verifier checks itself (`zk-passport/1`) per D32. Record alongside the existing chip-cloning limitation (D29) as a second named, accepted gap — not mitigated, disclosed. **`av://` hijack note, recorded not mitigated:** Annex A mandates the `av://` custom scheme; on Android any app may register it (the EU reference app registers host `*`). Damage is bounded by origin binding above to the request itself (no personal data crosses in a hijacked request) plus direction-2 exposure. Mitigation path, follow-up not an M2 MUST: verified HTTPS App Links (`assetlinks.json`) as the primary link once zkagent has a domain; `av://` kept for EU-profile compatibility. **M2 spike default:** `spikes/m2-handoff` runs on plain `http://127.0.0.1` (no TLS), so the scanner ships one build-time pinned dev request-signer key, labelled dev-only; the well-known fetch above is the production path, not exercised by the spike. The "no production trust store yet" disclosure (D34/Q29) stays until a real TLS origin exists |
| D38 | *(2026-09-01, owner decision, on evidence from the first end-to-end mode-B run, 10:19–10:20: a real StrongBox P-256 signature reached the verifier and got `sig_unknown_key`, `packages/chiproof/src/plugs/attester-sig.js:130` — `sig-*/1`'s `linkability: 'signer'` classing assumed an operator-pinned attester key list, and nothing in the PRD said how a verifier ever learns a phone's own self-generated key)* **Mode-B attester keys are per-origin, and the verifier binds key→zktag on first sight.** Owner's words: "agree b+c." Three options were put to the owner: (a) accept any key whose pubkey travels in the presentation — rejected, one global `key_id` is stable across every site the phone presents to and breaks D22's cross-site bar even though the zktag itself stays domain-scoped; (b) operator first-sight binding (TOFU) — the relying site records key→zktag on first presentation and requires the same key thereafter, so mode-B uniqueness now means same document AND same device; (c) per-origin device keys — the scanner derives the Keystore alias from the verified request origin (`scope_domain`, D37), so `key_id` is per-site exactly as the zktag already is. **Decision: (b)+(c), both.** Mechanism recorded at §6.2 item 1 (c: per-origin keypair generation) and FR12 (b: the attester-key store, `attester_bound_first_sight` / `attester_key_mismatch`, with the pre-existing operator-pinned list kept as an alternative store). **Stated limitation, composes with D29's chip-cloning limitation, not solved by it:** first-sight binding is trust-on-first-use — a first presentation from a cloned chip on a *different* device binds the wrong device to that zktag, and every later genuine presentation from the real device then fails as a key mismatch. This narrows the clone-replay window D29 already accepts (from D29's original constant claim rate) to a competition for who presents first, rather than closing it — it does not weaken D29's `chip_auth: true` guarantee, and does not extend the same-document, same-device claim to a document without chip authentication. **Spike note, dev shortcut not a design:** for the 2026-09-01 test the owner approved hand-pinning the Pixel's real captured public key into the spike verifier via an env override (`ATTESTER_P256_KEY_ID`/`ATTESTER_P256_PUBKEY_PEM`, `spikes/m2-handoff/server.mjs`, keys sourced from `spikes/m2-handoff/dev-attester-key-p256.mjs`) — this is the rejected option (a) taken as a one-off, dev-only, never committed with a real device's key, on the owner's own machine to unblock today's run, not a substitute for (b)/(c). **Recorded limitation, orchestrator-flagged 2026-09-01, no decision made — see Q31 (§11):** first-sight binding has no re-enrolment mechanism. A user who factory-resets their phone, reinstalls the app, or otherwise loses their StrongBox keys presents a new key for an already-bound `(origin, zktag)` and is refused `attester_key_mismatch` at every site that already knows them, permanently, with no path back. Observed as a real refusal (transactions `Cxn0dXWz8nlJfVX3`, `MstvPR4zJGK4VoSG`, 12:42) when a key-scoping change (D39) invalidated existing bindings; that instance was a staging artifact, but the mechanism is real and would affect genuine users. Composes with D29's chip-cloning limitation as a second named, permanent gap — not mitigated, disclosed. Any mechanism that lets a NEW key claim an EXISTING zktag is exactly the attack first-sight binding exists to prevent, so this is a genuine tension, not an oversight to fix casually |
| D39 | *(2026-09-01, owner decision, on evidence from a live run 11:43: with D38's per-origin key, the owner scanned an NL ID card then a US passport at the SAME origin; both minted with the same key (`key_id=c303cf3f731b5307`, "reused existing alias"), and the verifier logged `attester=bound_first_sight` twice, because the attester-key store binds `(scope, zktag)` (FR12) while the key itself was keyed by scope alone)* **The attester key is isolated per `(origin, zktag)`, not per origin — D38 is narrowed, not reversed.** Owner: "yeah, isolate, that's a small leak that this id have two ids." Consequence of the leak: a single site could observe that two different pseudonyms shared one device key, learning those two identities are the same phone. General rule this follows: **a key's scope must be at least as narrow as the identity it signs for** — a coarser key leaks the finer identifier, the same shape of bug D22/Q23 found in the raw attestation chain and D38 found in a single global device key, now found one level down in a single per-origin key. Mechanism, recorded at §6.2 item 1: the Keystore alias is derived from BOTH the verified request origin (`scope_domain`, D37) and the zktag, so two documents presented at one site mint two unrelated keys. **Cost, stated plainly:** one StrongBox key per (site, document), generated on first use; old per-origin (site-only) keys already in the Keystore under D38 are left in place, not deleted or migrated. **Considered tradeoff, recorded because it is deliberate:** isolation removes a verifier's ability to notice that one device presented two different documents (e.g. one below and one above an age threshold). Owner's words: "counterargument is someone trying to use ids are not theirs and both answer differently once below age one above age, i think this is not our place to judge/police and that's a borderline creepy/surveillance." **Supporting technical point (orchestrator-supplied):** the capability would not have solved borrowed-document use anyway — zkagent never binds the presenter to the document holder (no biometric match against the document photo; the device credential authenticates the *device owner*, not the document subject) — so "same device, two documents" has legitimate explanations (dual nationality, a shared family device) and would be a false-positive-heavy signal bought at a real privacy cost. Linkability class and tier ceiling for `sig-*/1` are unaffected — see FR12's amended paragraph |
| D40 | *(2026-09-01, owner decision)* **No issuer/country policy at tiers A and B; trust-anchor curation is the legitimate mechanism.** Owner, asked whether an operator may restrict which countries' documents it accepts (e.g. "i don't accept any IDs that are not US if age verification is US"): "id is id doesn't matter where it's from, maybe mode C of kyc should have that but others shouldnt." At tiers A/B, zkagent's presentation and verdict MUST NOT carry, and the protocol MUST NOT provide, an issuer-country attribute or a country-based accept/reject filter. Rationale: (1) the disclosure is the product — tiers A/B disclose a boolean over a threshold and nothing else; an issuer-country field is an additional attribute about the person and breaks that promise (data-minimisation at the FR level, the same principle behind D26's disclose-don't-hide stance on the circuit-class bucket); (2) differing legal thresholds are already handled by the *claim/threshold* being an operator parameter (D11), not by the document's origin; (3) nationality-based refusal is legally fraught in the EU and is a policy zkagent should not encode. **Distinguished from what already exists and remains permitted:** an operator inevitably chooses which CSCA trust anchors it loads into its masterlist (§6.2 item 7) — a verifier that has not loaded a given country's CSCA cannot validate that country's documents. That is trust-anchor curation, a risk/operational decision that discloses no attribute and is already how the masterlist works; nothing here forbids it. What is forbidden is a protocol-level country field or filter that discloses or acts on issuer identity as an attribute. **Tier C exception:** tier C (KYC-like), where identity is disclosed anyway and an operator-curated allow-list already applies per D37, MAY carry issuer information — consistent with D37's existing tier-C carve-out for operator-bound configuration. |
| D41 | *(2026-09-01, owner decision, closes the FR12 `sig-*/1` linkability escalation left open by D38/D39)* **`sig-ed25519/1`/`sig-p256/1` keep `linkability: 'signer'`, tier ceiling B unchanged.** Owner: "leave it." Rationale, folded into FR12: under D39 each key is scoped to `(origin, zktag)`, so it is not a stable per-device value — it is a fingerprint of one (device, site, document) triple, which is exactly the pseudonym the verifier already holds. `'device'` denotes a value that is the same at every site, permanently (the archetype being a raw key-attestation chain — a stable per-device identifier, D22). **Generalising question, owner-raised, settled here for every future plug:** does the linkability class depend on which attestation a plug uses (would Play Integrity be `'device'`, zkPassport `'signer'`)? Answer: **the class is a property of the plug, measured from what its payload actually exposes, never inferred from the category of technology it uses.** Current declarations, verified in code: `zk-passport/1` → `'none'` (`packages/chiproof/src/plugs/zk-passport.js`), with D26's disclosed `vk_sha256` circuit-class bucket as the one named exception — cross-site-stable, document-dependent, disclosed not hidden; `sig-ed25519/1`/`sig-p256/1`/`signed-receipt/1` → `'signer'` (`packages/chiproof/src/plugs/attester-sig.js`, `packages/chiproof/src/plugs/signed-receipt.js`); a hypothetical, unbuilt `key-attestation/1` → `'device'`, the archetype. The test for `'device'`: one value, the same at every site, persistent — hardware provenance alone does not make a plug `'device'`. **Play Integrity, worked as the test case, NOT a class assignment:** intuition says `'device'`, but M1's own spike (2026-08-30, `docs/logs/M1-Q23-EVIDENCE.md`, tokens captured twice on the Pixel 6a and diffed) found no device-unique field in the decoded verdict across sites — it passed the cross-site bar, which is what made D23 briefly viable before D24 superseded it on the non-transferability finding. On that evidence it would NOT be `'device'`; most likely `'signer'` (Google's key signs the verdict) or arguably `'none'`. This is explicitly not a class assignment — no Play Integrity plug is built, and the M1 evidence was gathered to answer a different question (D23/Q23's cross-site bar); any future Play Integrity plug's class MUST be set from a fresh probe of its own payload. |
| D42 | *(2026-09-01, owner decision, closes Q29's descendant Q30 — scope granularity)* **The zktag/evidence signing scope is host-only; D37's origin-consistency check stays the full origin (scheme+host+port).** Owner: "domain." The zktag domain scope (`scope_domain`, D5/FR2) and the D38/D39 attester-key alias derive from the request origin's **host only** (today `127.0.0.1`; in production `example.com`), while D34/D37's origin-consistency check across `client_id`/`request_uri`/`response_uri` uses the **full origin: scheme + host + port**. Rationale: a site that upgrades http→https or changes port is still the same site — binding the pseudonym to the full origin would silently reset every returning user's identity (and, under D39, mint a fresh attester key) for a change unrelated to who they are; the consistency check, by contrast, is a security claim about one request object and must be exact, since `https://example.com` and `http://example.com` are genuinely different origins. The implementation already behaves this way — `MainActivity.kt` derives `scopeDomain` via `URI(verified.origin).host`; `RequestTrust.kt`'s `originOf`/`resolveVerifierKey` compare scheme+host+port; the spike's `SCOPE_DOMAIN` derives from `BIND_HOST` (`spikes/m2-handoff/server.mjs`) — D42 records the split as deliberate, not accidental, which was the point of Q30. **Flagged, not fixed:** "host" and "registrable domain" are not the same thing for a real deployment — `a.example.com` and `b.example.com` are different hosts but one registrable domain, and the choice determines whether subdomains share a pseudonym. The current code uses host; recommended reading is host (subdomains stay distinct scopes — the conservative reading), recorded as a note for a production deployment since it does not bite at M2 scope with a single `127.0.0.1` origin. Closes **Q30** (§11). |
| D43 | *(2026-09-01, owner decision)* **Any outcome that ends a scan attempt and requires user action MUST be surfaced as a modal dialog with an acknowledge (OK) action, never a self-dismissing Snackbar.** Owner, after a live run where a mistyped document number produced a transient overlay: "when wrong data in, it is not pop up to dismiss but overlay notification that disappears, i should get pop up then ok then it resets. i just updated number then tapped again and worked." §6.2 item 6 / F3 deliberately keeps the MRZ and locked mode on an access-establishment failure (PACE/BAC `SW 0x6300`→`0x6985`) so the user can fix a typo and retry (`wipeSession(keepMrzAndMode = accessFailure)`) — the app enters a "waiting for you to correct and re-tap" state whose only announcement, today, is a Snackbar that fades in a few seconds, indistinguishable from a finished or hung app once it does. New §6.2 item 15: the dialog carries the value-free reason and an OK action; on dismissal the app performs the state transition explicitly — keeping the MRZ focused for correction (access-establishment failure) or resetting the session (every other failure) — matching the existing `keepMrzAndMode` rule rather than a second policy. Transient (Snackbar) UI stays correct for purely informational events that change no state (e.g. "QR capture cancelled"). General rule: transient UI for transient facts, blocking UI for state that requires the user to act. Cross-references the recurring defect class this session already fixed twice (a UI-only write with no log, `apps/scanner/.../MainActivity.kt`; a silent `?: return null` in intent parsing): the shared failure shape is a state change whose only notification is transient or absent. Dialog text stays value-free — same constraint as the report (item 5) |
| D44 | *(2026-09-01, owner decision)* **The per-scan report moves to its own log view, timestamped.** Owner: "the feedback of what happened every scan at the bottom of the app should go to another tab as logs, same output with timestamp." New §6.2 item 16: the value-free report currently rendered into `reportView` also accumulates into a separate in-app log view (a tab or equivalent navigation, exact widget left to implementation), each entry prefixed with a local wall-clock timestamp. Content is unchanged — the same value-free lines `emitReport()` already produces (item 5's grep-provable constraint: no MRZ, names, key bytes, fingerprints, chain contents); a log view must not become a reason to add richer detail. The single-write invariant survives: every report write still goes through the one logged `emitReport()` path (this session's fix for the earlier silent-report defect), and the log view is an additional consumer of it, never a second write site. In-memory only for the session, never persisted to disk — governed by item 6/F1 (MRZ persistence removed) and D35, not NO-GO #9 (secrets/test keys, not on-device persistence); D35's in-memory retention across Activity recreation extends to the accumulated log, and the log is cleared whenever a session wipe occurs that does not keep MRZ/mode (item 6), so the log's lifetime never exceeds the session it describes. Timestamps are display-only and do not enter any proof/evidence path — contrast D28's midnight-UTC coarsening of `current_date`, which is a payload field. The accessibility-snapshot caution on raw-field screens does not apply here because the content is value-free by construction, stated explicitly so the log view is never later treated as a place to add raw fields |
| D45 | *(2026-09-01, owner decision, amends D44/item 16 — original D44 text kept, not deleted)* **The log view's lifetime is decoupled from `wipeSession()`'s per-scan wipe; it accumulates for the life of the app session, not one scan.** D44 as written said the log "lists the reports of successive scans in the session" while item 16 also said it MUST be cleared "whenever a session wipe occurs that does not keep MRZ/mode" (item 6) — self-contradicting. **Verified in code:** `MainActivity.kt` calls `wipeSession(keepMrzAndMode = false)` at the completed-read call site on EVERY completed read, including a successful one, so implemented literally the log held exactly one scan and reset on the next — successive scans never accumulated. Owner chose accumulation over the literal clear rule. Retention is otherwise unchanged from D44: in-memory only for the session, never persisted to disk (item 6/F1; NO-GO #9 does not apply — that NO-GO governs secrets/test keys in the tree, not on-device persistence), surviving Activity recreation via `onSaveInstanceState` (D35), gone only when the app process is gone — D45 removes the per-scan wipe as a clearing trigger, it does not make the log durable across process death |
| D46 | *(2026-09-01, owner decision, amends D44/item 16 — original D44 text kept, not deleted)* **Each log entry MUST be titled by the site it was for and MUST legibly summarize disclosure outcome, not just echo the unlabeled report text.** Owner: "logs should be safe and not a source of threat, but should be there for user to know how it went, what went out and the result, how much it disclosed, successful or not" and "titled by timestamp, titled by website." Each entry MUST carry, besides its existing local wall-clock timestamp (display-only, unchanged, MUST NOT enter any proof/evidence path — contrast D28's midnight-UTC `current_date` coarsening, a payload field), a title identifying the verified request origin/`scope_domain` (D37, D42) the scan was for; a bare local scan with no verified handoff (mode A, no pending request) MUST use the fixed, value-free label **"Local scan (no site)"** rather than a blank field or a fabricated origin — this exact string is a specification made here, not itself owner-confirmed wording (flagged, §11 Q32). The log MUST be legible to a non-engineer about outcome: what went out, to whom, what was disclosed, whether it succeeded. **This SUPERSEDES D44/item 16's "MUST NOT change report content" clause to the extent that a value-free disclosure summary is now REQUIRED**; the rest of that clause — no MRZ, names, document fields, key bytes, signatures, nonces, fingerprints, or chain contents (item 5's grep-provable constraint), the single-`emitReport()`-write-path invariant, and the accessibility-snapshot note — is UNCHANGED and remains binding. The origin/site name is explicitly NOT a document field and is safe to show — stated so it is never later confused with one, and so this decision is never read as license to add any other raw field. **Open, not decided — Q32 (§11):** the exact shape/wording of the disclosure summary is not owner-specified; this decision states the requirement at the level the owner gave it |
| D47 | *(2026-09-01, owner decision, closes Q32, amends D46/item 16 — original D46 text kept, not deleted)* **The disclosure-summary shape is a four-field plain-language block — `Result` / `Sent` / `Shared` / `Identity` — under the entry title, followed by a subordinate `▸ technical:` line; the no-site title label is CONFIRMED.** Owner approved, from three concrete renderings put to them, "plain summary first, technical detail subordinate," with this exact worked shape (a mode-B success, and a mode-A bare read): `14:22:07 · 127.0.0.1:8787` / `Result Verified — the site accepted you` / `Sent a site-only pseudonym + proof you're over 18` / `Shared your age threshold, and nothing else. Not your name, date of birth, document number, or nationality.` / `Identity new — minted fresh for this site` / `▸ technical: mode B · evidence sig-p256/1 · key_id c303cf3f… · chip_auth true · tx HVLKlhbU…` — and `14:19:41 · Local scan (no site)` / `Result Read OK — nothing sent` / `Sent nothing left this device` / `Shared nothing` / `▸ technical: mode A · evidence [] (D27)` (full verbatim block recorded at §6.2 item 16). **Title line** stays timestamp + verified `scope_domain` (D37/D42), or, for a bare mode-A scan, the label **"Local scan (no site)"** — this label, flagged in D46 as "a specification made here, not itself owner-confirmed wording," is now owner-CONFIRMED verbatim; that D46 flag is superseded, not deleted, and no longer applies. **`Identity`** is the plain-language restatement of the D38/D39 per-(origin, zktag) attester-key state — whether this presentation minted a new key/alias for this (origin, zktag) pair or reused one already bound from a prior visit at that site. Only the "new" case's copy above is owner-approved verbatim; the "reused" case's exact copy is not yet owner-specified and stays at this same register pending confirmation (does not reopen Q32 as a numbered question). **REQUIREMENT, not decoration:** the four plain-language lines MUST be accurate to the actual outcome, not a fixed template — a success, a request/handoff refusal, a masterlist "no," an unmet mint gate (item 12 failure classes), an access-establishment failure, and a bare mode-A read are genuinely different disclosures and MUST read differently; the log MUST NOT claim something was sent when nothing left the device, and MUST NOT read as success on a failure path; mode A MUST state plainly that nothing left the device (`evidence: []`, D27) — the second worked example above is the reference case. Everything D46 held UNCHANGED remains UNCHANGED under this amendment: the value-free constraint (item 5 — no MRZ, names, document fields, date of birth, document number, nationality, key bytes, raw signatures, nonces, fingerprints, or chain contents); the origin/site name is explicitly NOT a document field; the single-`emitReport()`-write-path invariant (the log view — including this richer rendering — is an additional consumer of that one call site, never a second write site; a field the current single string does not carry is added by extending what flows through that ONE call site, never by a second write path); the timestamp stays display-only, never in a proof/evidence path; and the accessibility-snapshot note stands — value-free by construction, never a place to add raw fields. Closes **Q32** (§11). |
| D48 | *(2026-09-01, owner decision, closes the D47 residual, amends D47/item 16 — original D47 text kept, not deleted)* **The `Identity` field's reused-key wording is now owner-confirmed, and `Shared` MUST render the actual disclosed claim and its answer.** Owner: "new — minted fresh for this site, known - recognized only here from previous visit (or shorter), age above 18 yes shared." Two confirmed `Identity` strings: newly minted key — **"new — minted fresh for this site"** (unchanged, confirmed at D47); reused key — **"known — recognized only here from previous visit"**. The phrase **"only here" is load-bearing, not decorative** — it is the plain-language statement of D38/D39's per-(origin, zktag) key isolation: this site recognizes the returning user, and no other site can, because the key is scoped to (origin, zktag) not to the device. Implementers MUST NOT simplify or shorten this phrase out. This closes the residual D47 left open ("the 'reused' case's exact copy is not yet owner-specified"); that flag is superseded, not deleted. **`Shared` REQUIREMENT (new, substantive):** D47's example rendered `Shared` as the fixed sentence "your age threshold, and nothing else." — that was illustrative, not the requirement. `Shared` MUST instead state the actual disclosed predicate and its actual answer, in the shape `age above <threshold>: <answer> — and nothing else.`, followed by the existing negation line ("Not your name, date of birth, document number, or nationality."). Owner's worked value: "age above 18 yes shared" → `age above 18: yes — and nothing else.` Three sub-requirements: (1) the threshold number MUST be read from the verified request object at presentation time, NOT hardcoded — 18 is the value the current test request happens to carry, not a protocol constant; a request asking a different threshold renders that number. (2) the answer MUST be the actual value asserted for that scan, never assumed true — a scan that asserted `no`/failed the predicate MUST render that outcome, not a blind "yes." (3) on any path where nothing was disclosed (mode A, an unmet mint gate, a refusal, any failure), `Shared` MUST say so plainly and MUST NOT render an age claim at all — this is D47's outcome-accuracy rule (§6.2 item 16), restated here because the `Shared` line is exactly where it is easiest to violate by defaulting to the success template. **The disclosed age predicate is explicitly NOT a document field** under item 5's forbidden-fields list — it is the claim the user deliberately chose to present, and showing it back to the user in their own on-device log is the point of the feature, not a leak; item 5's constraint on raw document fields (MRZ, names, DOB, document number, nationality, key bytes, signatures, nonces, fingerprints, chain contents) is otherwise UNCHANGED and remains binding — the age-predicate exception is narrow and specific to this one field, not a general opening. **Three implementation clarifications, owner-approved ("agreed on 1 and 2 and 3 above"), recorded as clarifications not new decisions:** (a) the `▸ technical:` line carries the complete, unmodified existing report text, indented, rather than the terse one-line summary shown in D47's worked example — no debugging detail is lost, the terse form in D47 was illustrative formatting only; (b) the two debug-only probe buttons (masterlist self-test, device-key self-test) render as a distinct "Diagnostic OK/failed" summary titled under the no-site label — they are not scans, disclose nothing to any site, and MUST NOT be rendered as if they were a scan outcome; (c) `siteTitleFor()` (or equivalent) MUST render the fixed **"Local scan (no site)"** label for a handoff whose request-object verification FAILED, not only for a bare mode-A scan with no pending request — an unverified or attacker-claimed origin MUST NOT ever be rendered as a trusted site name in the log title. This is D37's origin-verification requirement enforced at the UI layer, stated here as a security property of the log view, not merely a UI/cosmetic detail. **Cross-reference, added 2026-09-01, not a new decision — see Q33 (§11):** the threshold-MUST in sub-requirement (1) above is currently UNMET by the app — `MainActivity.kt` asserts a hardcoded `threshold = 18` and an unconditional `true` answer rather than reading the threshold from the verified request object or computing the answer from the chip's DOB; flagged here so the unmet MUST is traceable to its cause rather than looking like an oversight. **Superseded 2026-09-01, not a new decision:** Q33 was split (§11) — this cross-reference now points at **Q35** specifically (the scanner-side fix reading `zkagent.challenge.threshold`, already present/signed/nonce-bound), not at Q36 (computing a real DOB-vs-threshold answer, a separate and still-undecided question). **Clarified 2026-09-01, not a new decision — what `Shared` is FOR:** owner: "what i meant is to surface what questions was asked and how it was answered above 18: true note where above 18 is requester and true was the answer, combined with not known and known that's a complete pic of request to the user." `Shared` is a QUESTION→ANSWER record of the exchange — the predicate the requester asked and the answer this presentation asserted — which, together with `Identity`'s known/new state, gives the user a complete picture of the request: who asked what, what was answered, and whether they were recognized. **Interim sourcing, tied to Q33, not a substitute for the request-object MUST above:** until a request-carried threshold and a real computed answer exist (Q33), both halves of the `Shared` line MUST be rendered from the actual signed claim map the app sends — never from a separately-typed string — so the log is by construction a faithful record of what was sent to that site, and becomes correct automatically once real per-request evaluation lands. **Cross-reference corrected 2026-09-01, conflict sweep, not a new decision:** Q33 was split into Q35/Q36 at v1.29; the two "(Q33)" mentions immediately above now read as **Q35** (the request-carried threshold, still open) for the threshold half and **Q36** (the real computed answer, still open) for the answer half. |
| D49 | *(2026-09-01, owner decision, amends D48/item 16 — original D48 text kept, not deleted)* **`Shared` renders a LIST of predicate→boolean pairs, one per line, each answer a literal `true`/`false`, never `yes`/`no`; predicates use comparison (`attr > value`) or bare-boolean form.** Owner: "true/false always #2 agreed #3 questions answers, same shape \"age > 18: true, expiry > 3 months: false, expired: true\"." Two changes to D48's `Shared` specification, both superseding D48's illustrative example text (kept, not deleted) rather than its underlying requirement: (1) **Boolean literal, not paraphrase:** the answer half of each line MUST be the literal boolean `true` or `false` — never "yes"/"no" — because it is the direct mirror of the signed `over_threshold` (or future predicate) boolean; the log MUST NOT paraphrase the payload. D48's worked example `age above 18: yes` is superseded by `age > 18: true`; noted for the record that this reconciles the doc TO the implementation, which already rendered booleans, not the reverse. (2) **List, not sentence:** `Shared` MUST render as a list of `<predicate>: <boolean>` lines, one per disclosed claim, rather than one formatted sentence; the negation line ("Not your name, date of birth, document number, or nationality.") follows the list. **Predicate shape accommodates both forms shown in the owner's three examples** — comparison predicates (`age > 18`, `expiry > 3 months`) and bare boolean predicates with no operator (`expired`). **Constraints for today's single-element list:** the list holds exactly one element (the age predicate) until Q34 is resolved; an empty list (mode A, an unmet mint gate, a refusal, or any other non-delivered outcome) MUST render the plain "nothing shared" wording already required by D47/D48's outcome-accuracy rule, and MUST NOT render an empty label or a stray colon; the list MUST NOT be populated with any claim beyond the one that exists today — expiry and every other attribute remain **Q34** (§11), unbuilt. **Also recorded, owner-approved, not a new decision:** the `▸ technical:` block's compliance note is approved verbatim as `claim_proof: self-asserted by the device — not independently proven (D24)`, set only on outcomes where a claim was actually signed; this is currently the only place the log states the claim is unverified, tying directly to **Q33** (§11). |
| D50 | *(2026-09-01, owner decision, from a live run on the Pixel 6a with both real documents, amends item 15/D43 and item 16/D44 — original text of both items kept, not deleted)* **Positive finding: D39's per-(origin, zktag) key isolation is confirmed on real hardware for the first time.** Both documents minted successfully (`ok=true allowed=true reason=evidence-verified evidence=["sig-p256/1"]`), each producing `attester=bound_first_sight` with a DIFFERENT key. **Three defects found in the same run, owner-approved to fix now as amendments to items 15/16 (not new items, not new open questions):** **(1) Log ordering (item 16):** the log view MUST list the newest entry first — a rendering-order change only; the stored order MUST keep round-tripping correctly through `onSaveInstanceState` (D35) unchanged. **(2) Duplicate in-progress entry (item 16):** the mint gate's biometric-authorization request calls `emitReport` (producing a `Result  In progress` entry) and the terminal outcome calls `emitReport` again, appending a second entry that never resolves the first. MUST: exactly ONE log entry per scan attempt, with the terminal outcome REPLACING that scan's in-progress entry in the log accumulator. MUST NOT be fixed by suppressing the in-progress `emitReport` call — every write MUST still reach logcat, since an unlogged UI-only write is exactly the defect item 16's single-write-path invariant exists to prevent. Required edge case: an in-progress entry with no terminal outcome (app backgrounded mid-scan) MUST still be shown, never silently erased. **(3) Consumed/expired handoff session (item 15), the substantive defect:** after a successful mint at 22:00:30 (`direct_post` 200, verdict PASS/minted), two subsequent taps failed at 22:01:16 and 22:01:37 with `AccessDeniedException SW=0x6982 SECURITY STATUS NOT SATISFIED` from `BACProtocol.doBAC` — a chip-access failure. The verifier log confirms no transaction was created between the two mints and no refusal was recorded server-side — nothing ever reached the verifier. The dialog's message was accurate for what actually failed, but the STATE was wrong: the app had left the handoff pending and the mode locked to an already-spent single-use nonce, inviting a doomed second tap. Same shape as this branch's scope-constant and threshold-constant findings — two internally-consistent sides (app's "handoff pending" vs. verifier's "nonce spent") disagreeing about something neither side can see alone. MUST: the pending handoff and its verified request are CLEARED once a presentation is delivered and accepted (`direct_post` 2xx), so a spent session cannot be reused. MUST: a tap or mint arriving with a mode locked to a handoff but no usable session (consumed OR expired — an expired challenge is equally unusable) is refused UP FRONT with a blocking dialog, ideally before any tap, stating the verifier session is no longer valid, with D43's existing non-access-failure reset on dismissal. UNCHANGED, restated: the access-establishment-failure path (`SW 0x6300`→`0x6985`, keeps MRZ, F3) behaved correctly in this run and is not touched. **Dialog wording NOT yet owner-approved** — owner's stated intent: "verifier session expired or something"; implementer's chosen exact strings return for approval like every other user-facing string. **Opens Q37 (§11), not resolved, no approach chosen:** whether "consumed" and "expired" can be distinguished device-side without a verifier round-trip, and where the challenge expiry is reachable from. **CORRECTED 2026-09-01 (original defect-3 text above kept, not deleted):** the causal claim above — that a successful mint left the handoff pending on an already-spent session — is not supported by the code. `MainActivity.kt:1033-1034` (pre-existing, already present before this session's work) clears `pendingHandoff`/`verifiedRequest` on ALL delivery outcomes, not only success, and `wipeSession(keepMrzAndMode = false)` clears `lockedMode` after every completed read — a consumed session could not have been left reachable for a stray tap. The two `SW=0x6982` failures observed were genuine chip-access failures after the owner re-typed the MRZ and re-locked; the dialog was accurate and the app's state was not wrong. The logcat evidence above stands; the interpretation of it does not. **What defect 3 actually is: NEW protection, not a bug fix** — nothing previously checked handoff session expiry at any point, so a session could age out (still formally pending — clearing only happens on definitive completion, never on elapsed time alone) during a physical chip read and fail at `direct_post` with no prior warning; that gap is real and the fix stands on its own merits. The "same class as this branch's scope-constant/threshold-constant findings" comparison drawn above no longer applies — those were two sides silently agreeing on a shared wrong constant, this is a genuinely missing check, not a coincidental agreement. **Q37 is now CLOSED**, resolved by implementation: the challenge expiry (`zkagent.challenge.expires_at`) is reachable from the already-verified request object with no verifier round-trip, and "consumed" needs no separate detection since clearing already removes a used session from state the moment it is used — see §11. |
| D51 | *(2026-09-01, owner decision, from a live run on the Pixel 6a, amends items 4, 6/F1, 13/D33, 14/D34, 15/D43, and 16/D47/D49 — original text of all kept, not deleted)* **Positive finding: D38's first-sight attester binding confirmed `matched` on real hardware for the first time** — a returning document at the same origin re-presented the same key and was recognised (`attester=matched`), alongside D50's D39 confirmation in the same session. **Evidence for the amendments below:** a separate scan failed mid-read with `net.sf.scuba.smartcards.CardServiceException: Tag was lost` inside `DefaultFileSystem.readBinary`, surfacing as `IOException: Unexpected exception` — the document physically moved during the read; the verifier confirms a transaction was created and never received a presentation. **Three owner-approved amendments:** **(1) Third failure-transition bucket (item 15/D43):** a TRANSIENT chip-communication failure (tag lost / link dropped mid-read) MUST keep the MRZ and mode, like the access-establishment bucket, so the user can retry with no re-entry. General rule: the discriminator is whether THE ENTERED DATA IS STILL GOOD — wrong for access-establishment, merely interrupted for tag loss; resetting in that case discards correct input for a physical mishap. The pending handoff MUST survive the retry; D50's session-expiry refusal takes precedence if the session expires meanwhile. Classification MUST be conservative — an unclassifiable exception falls through to RESET, since a wrong "keep" leaves document data on screen unexpectedly, worse than a wrong "reset." Dialog wording not yet owner-approved. **(2) Mode radio removed; mode is DERIVED (items 4, 13/D33, 14/D34):** mode is no longer a user choice — a verified handoff's tier determines it, a bare local scan with no verified request is mode A by definition, and the control is replaced by plain text showing the derived mode. This eliminates F5's bug class by construction (a control that can disagree with the executed mode cannot disagree if it is not a control) and removes the last way to violate item 4's one-source-of-truth requirement. D33/D34's "sets and locks the mode radio" MECHANISM is superseded by derivation; the REQUIREMENT it enforced — an absent/invalid tier fails loudly, no default — is NOT superseded, now guarding the derivation instead; tier C remains refused. **(3) Mode and chip-authenticity status in the log block (item 16/D47/D49):** each entry MUST state, in plain language alongside Result/Sent/Shared/Identity, both the mode and the chip-authenticity status; `chip_auth` stays unchanged in `▸ technical:`. Chip authenticity has THREE states — verified, NOT SUPPORTED by this document, and failed — and the absent case MUST read honestly and MUST NOT render as "false." Ties to the project's stated-not-hidden position that a document without chip authentication is clone-replayable (the US passport is exactly this case) and mode-B uniqueness only holds where `chip_auth` is true. Exact strings not yet owner-approved. **One alternative considered and DECLINED, recorded because the rejection matters:** **(4) item 6/F1's `onStop()` MRZ wipe is REAFFIRMED, not relaxed.** Asked whether to relax it so entered details survive an app-switch (found annoying live), the owner declined — the wipe rule stays exactly as written. Reasoning: the actual friction was the tag-loss reset, fixed instead by (1) above; retaining document data in memory while backgrounded for convenience would weaken F1's privacy posture for a problem that had a better, narrower fix. Recorded so this alternative is not re-proposed later as an obvious improvement. |
| D52 | *(2026-09-01, owner decision, from three further Pixel 6a scan rounds, amends item 15/D43 — original text kept, not deleted)* **Positive finding: D38's first-sight attester binding now holds REPEATEDLY on hardware.** Four consecutive transactions all succeeded (`ok=true allowed=true reason=evidence-verified evidence=["sig-p256/1"] attester=matched`) — a returning document at the same origin recognised as the same key across four presentations, strengthening D51's single-observation confirmation. **A successful mint MUST confirm itself with a blocking modal.** Found by the owner using the build: a successful mint only calls `emitReport` (`verdict: PASS (minted)`) — no dialog, no transient UI. **The asymmetry:** D43 made the app LOUD about every failure and SILENT about success, so the one outcome a user most wants confirmed is the only one that does not confirm itself; D43's general rule ("blocking UI for state that requires the user to act") was read as though success required no action, but in a mode-B handoff the user must return to the browser and nothing told them the presentation had been accepted. **MUST:** a successful, DELIVERED AND ACCEPTED presentation MUST be surfaced as a blocking modal with an acknowledge action, using the SAME mechanism as D43's failure dialogs — one dialog path, not a forked near-duplicate. **MUST, minimal wording (owner's choice):** outcome only — MUST NOT restate the disclosure (age predicate, site, identity state), already in the log entry (item 16) and the report. **MUST:** dismissal follows D43's existing non-access-failure reset branch, no separate post-success policy. **MUST:** only `Accepted` delivery qualifies — a signed-but-undelivered presentation (verifier rejection, no `response_uri`, transport failure) MUST NOT render as success and keeps its failure treatment; this is why the implementation's four-way delivery-outcome split exists, and collapsing it would let an undelivered presentation read as verified. **MUST:** a mode-A/bare local scan with no delivery is a terminal outcome but NOT a "verified by the site" success; if it confirms at all, its wording MUST be honest that nothing was sent. **Dialog wording NOT yet owner-approved** — implementer's chosen string returns for approval like every other user-facing string this session. |
| D53 | *(2026-09-01, owner decision, from the owner's string review of the implemented build, amends item 16/D51 (mode half only) and approves previously-pending strings across items 15/16 — original text of D51 kept, not deleted)* **Change 1, supersedes the MODE half of D51:** the `Mode` line is REMOVED from the plain-language log block; D51's chip-authenticity half is UNCHANGED. Reasoning, kept as the general principle: `Sent`, `Shared`, and `Identity` already convey what mode A/B mean in plain language with no glossary needed (`Mode B — recognisable to this site` merely restates `Identity`; `Mode A — anonymous` restates `Sent: nothing left this device`) — redundant. Owner: "no scary business for non tech savvy they may think we know and transfer more than what we do." Standing fact that makes a plain mode label actively misleading: D21's always-read/conditionally-mint rule means the chip is read identically in both modes — mode governs only what is SENT, never what is read — so "mode A" in plain language risks being misread as "we read less," which is false. UNCHANGED: mode stays in `▸ technical:` and in the on-screen derived-mode display (D51, item 4). **Change 2, wording only, supersedes the implementer's proposal:** chip-authenticity strings, owner-approved — VERIFIED → `Verified — this document's chip proved it is genuine`; NOT_SUPPORTED → `Not supported — this document has no chip authenticity check`; FAILED → `Not verified — the chip check did not pass`. The implementer's clone-explicit proposal was rejected as alarming on every US-passport scan; the three-state distinction and the stated-not-hidden clone-replay position are UNCHANGED — wording only changed. **Approvals recorded, previously pending:** D51's transient-failure dialog (`Reading was interrupted — hold the document still against your phone and try again.`) and Result line (`Read interrupted — the document moved or the connection dropped`); D52's success dialog (`ID scanned successfully`, owner's own wording, deliberately not matched to the `Result` line); D50's session-expiry pair, D48's `Identity` pair, and the `claim_proof:` note remain as previously approved. **Accepted implementation judgements, not new decisions:** a mode-A/bare scan does not receive D52's success confirmation, consistent with D52's accepted-delivery-only rule; no distinct mid-read "hold still" progress state was added — deferred, not refused, requires restructuring the read task's progress reporting, noted as available future UX work. |
| D54 | *(2026-09-01, owner decision, from a live run with five consecutive read failures, amends item 15's access-establishment and transient-failure buckets — original text kept, not deleted)* **Diagnostic finding:** five failures were all `org.jmrtd.AccessDeniedException: Mutual authentication failed`, preceded by `PACE unavailable (AccessDeniedException)`. Distinction recorded because genuinely useful, not obvious: `PACE unavailable (CardServiceException)` = chip doesn't support PACE, falls back to BAC; `PACE unavailable (AccessDeniedException)` = PACE was attempted and the MRZ-derived key was REJECTED — both protocols failing on key rejection points at wrong typed details, not a chip/code fault. Verified not a regression: the D43-D53 commit touched no MRZ/BAC/PACE key-construction code. **UX consequence:** an access-establishment failure keeps typed details by design (F3/D43) so the user can correct them, but a user who doesn't change them re-derives the same wrong key every retry, producing an unbounded run of identical failures — the message MUST make the required action unmistakable and MUST stay distinct from the transient-failure message, since the two demand different actions (correct details vs. hold the card still). **Approved strings, superseding D51-lineage wording approved earlier the same day, shortened because the owner skimmed past the longer copy five times on a real device (the evidence for shortening):** access-establishment dialog → `Couldn't read — check your details and try again.`; access-establishment `Result` → `Couldn't read — check your details`; transient-failure dialog → `Couldn't read — keep the card at the top of your phone.`; transient `Result` → `Couldn't read — card moved`. **MUST remain two separate strings, MUST NOT be merged** — merging would discard the distinction D51's third bucket exists to draw. Everything else previously approved (session-expiry pair, `ID scanned successfully`, `Identity` pair, `claim_proof:` note, three chip-auth strings) stands unchanged. **Real bug found on the same run, verified directly in the code, also folded into this decision:** the two failure classifications are evaluated in the WRONG ORDER — `MainActivity.kt` ~900-912's `try` around the access-establishment phase catches ANY `Exception` and sets `accessFailure = true` unconditionally (classification by code path, not exception evidence); `MainActivity.kt` ~1014's transient classifier is gated behind it (`!accessFailure && FailureTransition.isTransientChipCommunicationFailure(result)`), so it can only fire once access has already succeeded. Consequence: a tag-loss DURING access establishment is misreported as a data-entry problem, sending the user to re-check correct typed details. **Why tests missed it:** `FailureTransitionTest` correctly asserts the keep/reset state-transition mapping (both buckets keep MRZ+mode), which is unchanged by this bug — only the MESSAGE differs, and a test pinning state transitions cannot see a message-selection bug. **Fix, owner-approved:** classify TRANSIENT first, from exception evidence, independent of phase; narrow `accessFailure` to a genuine access denial (`SW 0x6300`->`0x6985`, `org.jmrtd.AccessDeniedException`) from the exception, never the code path; move the precedence into the pure `FailureTransition` object with its own test, not an `if` ordering in the completion handler; unclassifiable exceptions still fall to RESET (unchanged); state transitions themselves are unchanged, only classification/messaging changes. **Corrected 2026-09-02 (D55, original text above kept, not deleted):** the causal reading in this decision's own "UX consequence" clause — that the run of five identical failures traces to "a user who does not change them," i.e. to user behaviour — is INCOMPLETE. D55 found the app was structurally PREVENTING the correction this decision asked for: an overlapping-`FrameLayout` pane bug (three views, two unaware writers) let the in-app log tab cover the MRZ form after a failure, with no way back to it (`onTabReselected` empty, no `selectTab` call anywhere), so a user who opened the Log tab to see why a read failed could not reach the field to fix it — the retries were not evidence of an unchanging user, they were evidence of a UI the user could not act on. This decision's diagnostic distinction (`PACE unavailable (AccessDeniedException)` vs `(CardServiceException)`) and its classification-order bug fix are UNAFFECTED and stand; only the causal explanation of the repeat-failure run is amended. See D55 for the mechanism and fix. |
| D55 | *(2026-09-02, owner decision, from a live Pixel 6a run, amends items 15 and 16 — original text of both kept, not deleted; corrects D54's causal reading in place, D54's diagnostic and classification-order fix unaffected)* **The bug, root-caused by direct code inspection, corroborated by logcat:** `apps/scanner/app/src/main/res/layout/activity_main.xml` places `loading_layout`, `main_layout` and `log_layout` as overlapping siblings in ONE `FrameLayout` — later children draw on top, and `main_layout`/`log_layout` are both `match_parent` ScrollViews with no background, so `log_layout` COVERS `main_layout` whenever both are VISIBLE. `MainActivity.kt:253-258` (item 16/D44's tab listener) owns main<->log and explicitly leaves loading alone (its own comment at `:250`: "an edge case not covered by items 15/16" — exactly the assumption that failed); `MainActivity.kt:857-858`/`:1007-1008` (item 15's completion handler) own main<->loading and never touch log. **Failure sequence:** a read fails -> user opens the Log tab -> taps the card again -> `onPostExecute` sets `main_layout = VISIBLE` while `log_layout` stays VISIBLE -> the log paints over the MRZ form; `onTabReselected` is empty, nothing calls `selectTab`, so the user cannot reach the document-number field to correct it — the tag-intent path re-reads the STALE MRZ on every subsequent tap, re-deriving the same wrong key, and PACE then BAC reject it again. Device evidence: four consecutive `PACE unavailable (AccessDeniedException)` failures spaced 6/10/11 seconds apart (too fast for a retype); contrast an identical failure in an earlier process that was followed by a SUCCESSFUL PACE read because the Log tab was never opened. **Fix, owner-approved, as requirements:** all three views' visibility writes MUST go through ONE function that sets all three on every call, making the both-visible state unrepresentable — the same single-write-site discipline `emitReport` already enforces for `reportView`, for the same reason; the pane decision MUST live in a pure, Android-free object with its own unit test (the `FailureTransition` precedent), since `View.visibility` is a non-functional stub under this module's `unitTests.isReturnDefaultValues = true`, the same limitation already recorded for `SpannableStringBuilder`; `onTabReselected` MUST become idempotent, not empty; the read-in-progress flag MUST be cleared on every exit path of the completion handler, including the failure branch's early return; a completed read MUST NOT auto-switch tabs — considered and REJECTED, since it would lose the user's place in the log after every scan; `onCreate` MUST call the function once after tab state and the restored log are in place. **Why tests didn't catch it, a DIFFERENT blindness from D54's:** D54's tests were a correct test of the wrong property; here the property isn't expressible in the suite at all — the remedy is structural (move the logic where it's testable, make the bad state unrepresentable), not more assertions. |
| D56 | *(2026-09-02, owner decision, amends item 15, a new value-free diagnostic)* The tag-intent path MUST log whether the three MRZ field values CHANGED since the previous read attempt in the same process. Rationale: the existing logs are value-free by design and could not answer the one question that mattered while D55's bug was live — whether the owner's corrected details actually reached the app — costing an hour of code inspection to answer instead. Approved shape: `M2 stage: MRZ input UNCHANGED since previous attempt (doc_len=9 dob_ok=true exp_ok=true)` / the same line with `CHANGED`, plus a distinct first-attempt-this-session variant. MUST NOT log the field values or any character of them, render the comparison hash anywhere, or write it to `reportView`, `ReportLog`, `onSaveInstanceState`, or disk. MUST hold the hash in memory only, SALTED with a per-process random value generated at start and never persisted (an unsalted truncated digest of a short document number is trivially brute-forceable and would itself be PII); MUST reset the stored hash wherever the MRZ is cleared (`wipeSession`'s `!keepMrzAndMode` branch) so the next attempt reads correctly as a first attempt. |
| D57 | *(2026-09-02, owner decision — FREEZE, not a feature)* After the M2 scanner reached ~4,780 unreviewed LOC across seven isolated agent rounds, and the same-session D55 pane-visibility bug (a real user-facing bug that shipped and stranded a user, root-caused only by direct code inspection) demonstrated the cost of that backlog, the owner froze new §6.2 items and enhancements and adopted a rule set for every subsequent agent spawn: the entry gate is FIX vs ENHANCEMENT, never size (LOC is never a gate); every agent spawn carries history for the state it touches (prior reports, relevant git history, and any matching entries from `.claude/remember/findings.md`); one writer per piece of mutable state; every async writer is fenced; a finding an agent is not fixing goes into its report and is forwarded via a DURABLE FILE, never a code comment — the durable file is `.claude/remember/findings.md` (new this session, seeded from a read-only ownership audit, `docs/logs/M2-OWNERSHIP-AUDIT-2026-09-02.md`); read-only audit precedes refactor. **MUST NOT:** any new §6.2 item, enhancement, or UX change (including Q38/Q39/Q40 below) lands before the freeze's exit criterion is met. **Exit criterion, all three required:** (1) every mutable UI/session field named in the ownership audit has a named single writer; (2) every async writer (the five unfenced `Thread{}` sites the audit found, `.claude/remember/findings.md` #5) is fenced against Activity lifecycle; (3) `.claude/remember/findings.md` has no OPEN entry of consequence HIGH. **Recording this decision is not itself new scope** — NO-GO #10's scope gate (nothing enters a milestone unless first written into the PRD) is satisfied by this row and stays intact; a freeze forecloses scope, it does not add any. Applies to every module/agent working `apps/scanner`, not only the fields the audit named explicitly. |
| D58 | *(2026-09-02, owner-approved)* **The ownership refactor (D57's exit criterion) executes in this order:** (1) **Report/Log cluster** — smallest, most closed, lowest consequence; proves the single-owner pattern cheaply; includes a named `restoreReport`/`restoreLog` sibling of `emitReport` closing the `:288` doc/code mismatch (`.claude/remember/findings.md` #7). (2) **Pane cluster** — fixes the confirmed rotation bug (`.claude/remember/findings.md` #1) by construction: tab index persisted, `TabLayout` selection DRIVEN FROM pane state, not read by it; confined to `onSaveInstanceState`/`onCreate` — MUST NOT touch `onNewIntent`, which is Q39's separate product decision. (3) **Lock-time snapshot of the verified request**, threaded as a parameter — closes the three-writer `pendingHandoff`/`verifiedRequest` race (`.claude/remember/findings.md` #2/#3) by construction; deletes the cross-thread reads at `continueAfterRead`/`mintAndMaybeHandoff`; after it lands the `HandoffAdmission` guard (`.claude/remember/findings.md` #10) becomes redundant and is REMOVED. (4) **Re-derive the Session boundary** on the corrected call/state graph before deciding whether Session/Handoff/Lock is one class or two. **Rationale recorded:** change ordering follows risk-of-the-change and dependency, not the audit's bug ranking; the `730ef09` guard closed remote induction of the race, so the snapshot (step 3) is defence-in-depth for an ownership defect rather than the only thing standing in front of an exploit. **CONDITION, verbatim: the snapshot MUST land — if the sequence stalls after step 2 the ownership bug is live and the record must say so.** **Pass-2 result, recorded as the basis for this ordering:** 6 clusters found — 3 in `MainActivity` (Session, Pane, Report/Log) and 3 already-correct (`ReportLog` internals, `DeviceKey.lastMintAlias`, `DeviceKey.softwareEd25519Store`); the owner's two-or-three-cluster hypothesis holds for the broken part and not as literally stated; "a class for errors" has no corresponding mutable state (`FailureTransition` is a stateless classifier already at ceiling). The structure proposal itself lives in the session scratchpad — it is NOT copied into this document until step 4 re-derives it. |

**Resolved and closed** (kept as one-liners; full reasoning is in the version history and session stashes): **Q2** — Apple entitlement moot for M0. **Q5** — Android is primary (D2). **Q6** — iOS deferred (D2). **Q9** — phone→agent cert handoff ships all three paths (QR ~400–550 bytes in one static QR with no fountain coding; LAN POST; user-moved file, which leaks the zktag to whatever routes it and must be documented rather than blocked). The cert carries the agent's *public* key and is signed by the phone, so integrity is free and the channel needs neither confidentiality nor authentication. No zkagent-run server in any path.

## 11. Open questions (resolve at the milestone that hits them, on evidence)

- **Q7 (M2)** — device-assurance tier vs FR6 uniformity. StrongBox-backed Keystore and `MEETS_STRONG_INTEGRITY` exist on some devices (Pixel 3+, Galaxy S21+) and not on most of the Android base. Exposing which tier a client achieved is exactly the fingerprinting FR6 forbids and it shrinks the anonymity set. Either (a) accept a small fingerprinting cost for a stronger signal, or (b) stay uniform and enforce one global hardware bar — permissive by default (NFC + TEE-backed Keystore + device integrity), treating StrongBox as an unreported bonus. Note the Pixel requirement is a **dev-device** requirement, not a user requirement.
- **Q8 (M4, rung 2)** — RFC 9421 mapping details not fixed by FR8. (a) Which header carries the delegation cert — a zkagent-specific one, or does Web Bot Auth's `Signature-Agent` fit? *Its exact semantics are unverified; check before adopting.* (b) The cert is sent **inline** per request (~400–550 bytes base64); confirm against real adopter header limits. **URL-reference is rejected for v1**: hosting it anywhere we control violates NO-GO #1/#3, adopter-side hosting is merely caching what inline already delivered, and agent-side hosting assumes the agent runs a web server. Web Bot Auth's directory-URL pattern does not transfer — it serves bot *operators* with many rotating keys, not one user's single agent. (c) Which covered components are mandatory in the signature base — at minimum `@method`, `@authority`, `@path`, plus `content-digest` when there is a body. Decide against a live off-the-shelf verifier, not on paper.
- **Q11 (M3, mode B only)** — age-threshold probing. D11 lets the adopter choose `N`; an adopter free to choose any `N` and re-ask can binary-search the holder's exact date of birth in ~7 queries. **D13 shrinks this considerably**: in mode A there is no identifier to accumulate answers against, so probing requires the site to correlate presentations by its own account or session — a threat it already had. The question is therefore now: is a fixed allowed set (13/16/18/21, capping resolution at 5 buckets) still needed in mode A, or only in mode B? And should distinct thresholds be rate-limited per zktag in mode B? Decide before M3 goes public.
- **Q12 (M0/M3)** — **which** ICAO documents actually read. D14 settles the *policy* (greedy default); this question is now purely empirical. Many EU national ID cards are ICAO 9303 eMRTDs readable by the same JMRTD path; others (e.g. the German Personalausweis eID function) use a different protocol stack entirely. Needs per-country verification. Until M0 reports, do not state coverage numbers in any pitch.
- **Q13 (M3, strategic)** — **adoption: who asks first.** See §14. Nobody requests "zkagent" by name; adopters request an age check or an RFC 9421 signature, and zkagent is one optional answer riding on top. The borrowable core (§4) changes the shape of this question — the first adopter may be a *client* builder rather than a verifier. Log the first-adopter path as evidence, not assertion.
- **Q14 (M1, then M2)** — **attestation root: Play Integrity, hardware key attestation, or both.** Key attestation returns a certificate chain rooted in a vendor hardware root, asserting key-in-hardware (TEE/StrongBox), verified-boot state and root-of-trust key, OS version and patch level, and the **attestation application ID** (package name + signing-cert digest — the field FR10 depends on). It needs **no runtime service call, no Play Console registration, no quota, and no Play Services on device**, so it works on GrapheneOS and de-Googled builds (risk 7). Likely answer is **both**: key attestation primary, Play Integrity as an optional bonus where Play Services exists. **Three things must be verified before this becomes a decision:** (a) *keybox extraction* — attestation keys have been pulled from real devices and circulated; how far that degrades the guarantee today needs a current check; (b) *vendor implementation quality* — not every OEM implements attestation correctly and some fall back to software attestation, bounding the trustworthy device set; (c) *revocation* — a published revocation list must be fetched and honoured, reintroducing a small live dependency. Key attestation proves key origin, app identity and boot state; it does **not** cover active runtime tampering the way Play Integrity's rolled-up verdicts attempt to. **Also folds in the former Q1**: attestation verification needs CBOR + X.509 parsing of untrusted input, AGENT_RULES demands a vetted lib, and 8een tradition demands zero runtime deps — one well-vetted, dependency-light library may be the honest exception. Decide at M1 against the External Dependency Checklist, with M0 device evidence in hand. **M1 POC 2026-08-29: parsing/verification feasible in stdlib; the linkability of the chain itself is now Q23.** **Sub-item (d), 2026-08-29: pinned-but-expired root policy — the Pixel 6a's StrongBox chain roots in the Google root that expired 2026-05-24; a date-correct verifier rejects it today. Unresolved.**
- **Q15 (M1b) — does the attestation defeat mode A?** *(new, v1.4 — the blocker for the entire mode-A claim.)* Named suspects, each of which must be shown independent of holder and device or removed: the attested key itself (must be freshly generated per presentation, never reused); the certificate intermediate (must be a **batch** attestation key shared across a large device population — a device-unique intermediate is a permanent identifier); OS version and patch level in the attestation extension (a fingerprint bucket at best — coarsen or drop); attestation-ID fields such as serial or IMEI (**must never be requested**); precise timestamps (the EU AVS blurs `ValidityInfo` clock fields for exactly this reason — do the same); total payload length. Until measured at M1b, **mode A is a design intent and must be described as one.** **Closed by D26, 2026-08-30 (late), on M1b evidence (`docs/logs/M1B-EVIDENCE.md` §4–§5): every salted field is fresh per presentation; the only cross-site-stable document-dependent field is the circuit-class bucket (`vk_sha256`), disclosed by owner decision rather than removed.**
- **Q16 (M2) — mode-A scan cadence.** Purest form is re-scan per presentation: nothing cached, nothing to steal, matching the "no storage" goal exactly, at the cost of tapping a document to the phone every time. The alternative caches the *verified claim* (not a secret) under the D10 ceiling, which is far less dangerous than caching an identity but reintroduces borrowed-document risk. Decide on real UX evidence from M2, not on principle.
- **Q17 (before any age pitch) — legal sufficiency in a named jurisdiction.** **Positioning fixed by the owner 2026-08-07: the project is not contesting the regulatory requirement and is not seeking certification. The point being demonstrated is that the privacy properties the rules reach for can be obtained with far less machinery than the official route requires.** That framing is deliberate and it lowers what must be proven — a demonstration must be *honest*, not *certified*. It does not remove the question, it defers it: whether a mode-A presentation may legally satisfy an age-verification duty anywhere is a compliance question, not a cryptographic one. v1.4 improves the *technical* argument materially — mode A matches the EU Age Verification Blueprint's stated privacy properties (single-use, no persistent identifier, one bit, no issuer learning the destination) without a wallet, an attestation provider or batch issuance. It does **not** make zkagent a certified provider, and a rule demanding certification makes technical equivalence irrelevant. **Must be checked against a named jurisdiction before the framing shifts from demonstration to pitch.** Use 8een's `docs/02-evidence/EU-STACK-AUDIT.md` method: adversarial refutation, every claim pinned to file and commit, retractions written down, checkers instructed to default to REFUTED on thin evidence.
- **Q18 (closed by D29) — chip cloning vs the uniqueness claim.** *(new, v1.4 — surfaced while writing the design companion; load-bearing for mode B only.)* Verifying the SOD is **passive authentication**: it proves the data was signed by the issuing government, not that the chip presenting it is the original. A dumped data set replayed from a cloned or emulated chip would pass §2 steps 1–2 and mint **the same zktag as the genuine document** — which breaks the uniqueness and blocking guarantees at their root, since a blocked human could re-present from a clone. The defence is the chip's own challenge-response — **Active Authentication (AA)** or **Chip Authentication (CA)** — which proves the chip holds a private key it never releases. Neither is universally present: AA is optional in ICAO 9303 and omitted by some issuers on privacy grounds; CA arrives with EAC/PACE-era documents. **M0 must report which of AA/CA the owner's document actually supports** (add to the chip field inventory). Then decide: require chip authentication for mode B and accept the coverage loss, or accept clone-replay as within the captcha-grade bar and **say so in the claim**. **Mode A is unaffected** — with no identifier emitted there is nothing to impersonate, and a cloned chip proves an age that was true of the original holder anyway. **Closed 2026-08-31: M0 Finding 9 measured the split (US passport: no AA/CA, clone-replayable; NL ID card: both, clone-detectable); the owner took the second branch — mode B accepts non-chip-auth documents, `chip_auth` is reported per D21, and the claim says so (D29).** **Follow-on interaction noted 2026-08-31:** the accepted branch means D9's own derivation field (`document_number`) is exactly the one this closure makes forgeable without chip auth — see FR11 for the conditional-uniqueness statement and D9/D29 for the annotated decisions.

- **Q19 (parked — owner raised 2026-08-29, not designed) — freshness as a range, not a ceiling.**
  D10 fixes a *ceiling* on the age of the mode-B secret (operator-configured, 30–180 days). The owner
  raised the mirror case: a **requester-stated floor** — a bank asking for a scan no older than 24
  hours — meaning freshness would be a range negotiated per presentation, and the presentation would
  have to convey *when* the secret was minted. **This is in tension with D10's own rule that freshness
  answers with one bit, never an age in days, because a precise mint date is a fingerprint (FR6/FR9).**
  Do not design this before rung 1 ships. When it is taken up, the question is whether a coarse bucket
  ("fresher than 24h": yes/no) can serve the bank case without reintroducing linkability.

- **Q20 (resolved in principle by D20; mechanics at M1) — operator identity in a borrowable core.**
  Answered without a registry: the issuer's signing key *is* its identity, pinned per app build with a
  tier ceiling. What M1 must still fix: key format and signature suite (ed25519 is the default
  assumption), challenge encoding, expiry precedence between `expires_at` and the nonce store's TTL,
  and how a white-label build ships its pinned keys. Reconcile with FR2 (domain binding) and FR10
  (client trust list) in code, not prose.

- **Q21 (deferred — rung-2-shaped, do not design before rung 1 ships) — how an authority admits a
  requester to tier C.** A government that signs challenges for every site bound to check age has two
  ways to let a bank ask tier-C questions: sign that bank's challenges itself (a live signing service
  — the government's infrastructure, not ours, but a runtime dependency for the bank), or issue the
  bank a key with a certificate saying "may ask tier C" (delegation — the exact machinery of rung 2,
  FR5). The second is cleaner and is the one D18 forbids designing now. v1 ships only the direct case:
  requester = issuer, or issuer signs each challenge itself.

- **Q22 (before tier C is built) — the tier-C verb vocabulary.** Which identifying predicates exist at
  all, each as an exact boolean against a single requester-supplied value, one attempt per challenge.
  **No similarity scores, ever** — a percentage is a continuous channel that binary-searches the real
  value in a handful of re-asks (Q11's attack with far more entropy). Candidates so far: `name_matches`,
  `nationality_equals`, `document_type_equals`, `fresh_under(N)` (the last is Q19's one-bit answer and
  may belong in tier A). Decide against the first real tier-C adopter, not on speculation.

- **Q23 (resolved by D23) — tier A and attestation are in
  direct conflict.** FR10 needs the attestation to prove client identity; the attestation chain
  identifies the device. The requirement, after D22, is narrower than "unlinkable": **nothing
  stable across sites may appear in the payload.** The raw attestation chain fails this (per-device
  cert 1, identical on every site — M1 POC finding 5–6). Options on the table, none chosen:
  (1) tier A drops device attestation and accepts a weaker client-trust story; (2) attestation is
  verified by a party that never sees the holder's presentation (splits the verifier, reintroduces
  a live dependency); (3) Play Integrity: Google returns a signed verdict about app + device with,
  by design, no per-device key in the token — Google sees every check instead of the site.
  **Spike queued 2026-08-30**: capture two tokens per site for two "sites" on the Pixel 6a, decode,
  diff, list anything stable; prerequisites (Play Console registration, decryption route) recorded
  in the spike README. (4) withdraw the cross-site claim — last resort, would contradict §1's
  premise. (5) ZK proof over the passport SOD, no attestation at all — set aside by D22 as
  unnecessary for the stated goal, kept here as the known upgrade path if (3) fails. Decide on
  evidence, not argument; M1b's method applies to whichever option is picked.

  **Evidence 2026-08-30 (docs/logs/M1-Q23-EVIDENCE.md):** option (3) Play Integrity measured on
  the Pixel 6a — no device-unique field in the decoded verdict, stable fields are app-build
  identity and enums, per-device opt-ins absent; Google decodes every check. Option (5) ZK over
  the passport measured on desktop with zkPassport circuits against both real documents
  (RSA-2048/SHA-256 SODs, RSA-4096 CSCAs from the full BSI list): full DSC→SOD→DG1→age composition
  verifies offline, ~16 s / ~546 MB / 59 KB per document, proofs fresh per run, scoped nullifier;
  DG1 binding requires the integrity circuit. Not run: phone proving, OPRF nullifier (live
  dependency), recursive aggregation. **Decision still open — owner's call; D1 would be reversed
  by (5).**

- **Q24 (open, v1 accepted)** — de-Googled devices (GrapheneOS, /e/OS, AOSP) have no D22-compliant
  attestation route in v1 — Play Integrity fails by construction and raw key attestation is a global
  identifier. Accepted for v1 by D23; revisit with the ZK track (which needs no voucher) or option (2)
  split verifier. Risk 7 updated accordingly. D24 makes bare mode available to de-Googled devices;
  evidence-free tier A works everywhere.

- **Q25 (closed by D27)** — Play Integrity for our own reference app: with no relay, our app carries no Play
  Integrity evidence; a `signed-receipt` from us would make us the signer (NO-GO #3). Accepted: our
  reference app ships bare or with `zk-passport/1`. **Closed 2026-08-30 (late evening): the reference app ships bare (D27).**

- **Q26 (open, gated behind Track Z)** — tier B/C ZK evidence needs a passport circuit exposing BOTH
  a stable scope-nullifier AND a fresh challenge nonce as public inputs; zkPassport's current family
  offers one or the other (D25). Options recorded, none chosen: (a) upstream request/PR to zkPassport
  for a `pub nonce` field; (b) a different circuit family; (c) accept per-request nullifiers at B/C and
  derive the zktag outside the proof — which makes the zktag unproven, captcha-grade again. Gated
  behind Track Z's existing gates (D23).

- **Q27 (closed by D28)** — `current_date` granularity.
  M1b confirmed the `zk-passport/1` age circuit's `current_date` is a Unix-epoch-seconds `u64`, and
  the chiproof plug's `max_scan_age` check gates on that exact field (empirically: `maxScanAge:1` →
  `zk_scan_too_old`, `maxScanAge:null` → verified). Coarsening it to day granularity is possible
  without breaking `max_scan_age`'s pass/fail logic in principle, but caps how tight an adopter's
  `max_scan_age` window can usefully be — a real trade-off, not a free fix. Not decided: coarsen to
  day granularity vs. keep tight scan-age precision. **Closed 2026-08-30 (late evening): coarsen to day granularity, midnight-UTC (D28); effective `max_scan_age` floor is 1 day.**

- **Q28 (closed by D36) — may a device offer the weaker of an accepted evidence-alternatives set, or
  must it always offer its strongest?** D31 makes the mode-B attester-sig requirement an any-of set
  (`sig-ed25519/1` OR `sig-p256/1`, or whatever an operator configures per D32) rather than one fixed
  plug. Question was whether a device that *could* produce the stronger of two accepted alternatives
  may instead present the weaker one, and whether the verifier should care. **Closed 2026-09-01: no
  choice exists to make — the device queries its own capabilities first, orders them by fixed
  strength preference, and falls through only on failure of the preferred combo, never voluntarily
  (D36); the verifier cannot enforce "must offer strongest" without device attestation, which is
  excluded (D22).**
- **Q29 (closed by D37) — how are `trustedChallengeIssuers` (the request-signer keys the app pins)
  provisioned and rotated on-device?** D34 requires the scanner to verify every request object's
  JWS against a pinned/provisioned trusted-signer set before trusting any field inside it, but no
  key/config surface for this existed in the build. **Closed 2026-09-01: the trust anchor is the
  requesting site's HTTPS origin, not an arbitrary pinned list (D37).** The scanner enforces
  `client_id`/`request_uri`/`response_uri` origin consistency, then fetches the request-signer
  public key over TLS from a well-known path under that origin
  (`https://<origin>/.well-known/zkagent-verifier`) — TLS/Web PKI as the root of trust, matching
  the EU Age Verification profile's Annex A (`client_id_scheme: redirect_uri`, "client
  authentication is not required"). Tier C may layer an operator-curated allow-list on top
  (D37). M2 scope keeps one build-time pinned dev key for the non-TLS spike verifier until a real
  TLS origin exists — "no production trust store yet" stays disclosed until then.

- **Q30 (closed by D42) — scope granularity: the signed
  scope is host-only, D37's origin-consistency check is scheme+host+port.** The zktag's domain
  scope (`scope_domain`, D5/FR2) and the D38/D39 attester-key alias are derived from the request's
  **host** only, while D37's origin-consistency MUST (`client_id`/`request_uri`/`response_uri`
  resolve to one origin) checks the full **scheme+host+port**. Orchestrator recommendation: keep
  the difference deliberately — a site's pseudonym and attester key should survive a port change or
  an http→https upgrade (the *relationship* with the site has not changed), while the
  origin-consistency security check must stay exact (a mismatched port or scheme is exactly the
  kind of hijack D37 exists to catch — collapsing the two would either weaken the security check or
  needlessly fragment the pseudonym/key on a benign infra change). **Closed 2026-09-01: owner
  confirmed ("domain") — host-only signing scope, full-origin consistency check, exactly as
  recommended (D42).** D42 also flags, without fixing, that "host" and "registrable domain" diverge
  for a real multi-subdomain deployment; recommended reading is host (subdomains stay distinct
  scopes), a note for production rather than an M2 blocker.
- **Q31 (open, orchestrator-flagged 2026-09-01) — re-enrolment after key loss under first-sight
  attester binding (D38/D39).** First-sight (TOFU) binding has no re-enrolment mechanism: a user
  who factory-resets their phone, reinstalls the app, or otherwise loses their StrongBox keys
  presents a new key for an already-bound `(origin, zktag)` and is refused `attester_key_mismatch`
  at every site that already knows them — permanently, with no path back. Observed as a real
  refusal (transactions `Cxn0dXWz8nlJfVX3`, `MstvPR4zJGK4VoSG`, 12:42) when a key-scoping change
  (D39) invalidated existing bindings; that instance was a staging artifact, but the mechanism is
  real and would affect genuine users. **Options, listed not decided:** operator-side manual
  unbinding; a binding TTL after which a new key may bind; a device-held recovery secret; or
  accepting it as permanent. No recommendation made beyond noting the tension: any mechanism that
  lets a NEW key claim an EXISTING zktag is exactly the attack first-sight binding exists to
  prevent, so this is a genuine tension, not an oversight to fix casually.
- **Q32 (closed by D47)** — exact shape of the log view's disclosure
  summary (D46, §6.2 item 16). The owner requires each log entry to be legible to a
  non-engineer about outcome — what went out, to whom, what was disclosed, whether it succeeded —
  but did not specify the format: a fixed set of labeled fields (e.g. `site: … | mode: … |
  result: … | disclosed: …`), a short free-text sentence generated from the existing report lines,
  or something else. Also open within the same decision: the fixed no-site label for a bare local
  scan is specified in this revision as **"Local scan (no site)"**, a string chosen here to fill
  the gap, not itself owner-confirmed — the owner may want different wording. **Closed 2026-09-01:
  owner chose, from three concrete renderings put to them, a plain-language-first /
  technical-detail-subordinate four-field block (`Result`/`Sent`/`Shared`/`Identity`) plus a
  subordinate `▸ technical:` line (D47, full worked examples at §6.2 item 16); and CONFIRMED "Local
  scan (no site)" as the exact no-site label wording, closing both halves of this question.**

- **Q33 (superseded 2026-09-01, split into Q35/Q36 — original text below kept, not deleted) — the
  scanner asserts an age claim it never computes; the
  D11 threshold-comparison requirement is unimplemented.** Verified by direct code inspection:
  `apps/scanner/.../MainActivity.kt:1181-1182` sets `val threshold = 18` as a bare local constant
  and `val claim = mapOf("over_threshold" to true, "threshold" to threshold)` asserts `true`
  UNCONDITIONALLY on every mint, regardless of the document's actual date of birth. The chip's DOB
  is used ONLY to derive the BAC/PACE access key; no DOB-versus-threshold comparison exists
  anywhere in the app. `spikes/m2-handoff/server.mjs`'s request object carries no `threshold`
  field, and neither `RequestTrust.kt` nor `HandoffClient.kt` parse one. **Consequence, stated
  plainly:** the device evidence captured 2026-09-01 (`allowed=true reason=evidence-verified`) is
  NOT evidence about age — both the app and the verifier behaved correctly by their own contracts
  (the verifier checks the signature and the evidence binding, not the truth of the claim inside),
  the same self-consistent-but-wrong shape as this branch's other cross-contract bugs. **What the
  PRD already requires, not yet built:** D11 — the adopter sets `threshold`, the claim is
  `age_over_${threshold}`, the verdict is one bit, and a proof of a threshold OTHER than the one
  requested MUST be rejected. §6.2's sixteen items never asked the scanner to implement this, so
  this is a gap between an existing decision and the build, not a violation of M2's written scope.
  **D48's requirement that `Shared`'s threshold come from the verified request object is currently
  UNMET as a direct consequence** — see D48's cross-reference. **Owner's tracking decision,
  verbatim: "Both — question now, item when you decide to build it."** Recorded as an open question
  now; to be promoted to a numbered §6.2 item only if and when the owner decides to build it inside
  M2 — no §6.2 item added this revision.
  **SUPERSEDED 2026-09-01:** this question's premise that the request-carried threshold and D11
  enforcement were absent was incomplete — both already exist in code (see Q35's finding 1/2
  below). Split into **Q35** (the scanner-side one-line fix, request-threshold not read) and
  **Q36** (computing a real DOB-vs-threshold answer, genuine open design work) — see below.

- **Q35 (open, owner-flagged 2026-09-01, descendant of Q33, part a) — the scanner must read the
  already-present, signed, nonce-bound `zkagent.challenge.threshold` instead of its hardcoded
  `18`.** Three code findings, verified directly against the live source, corrected Q33's premise
  and produced this narrower question. **Finding 1 — the threshold is already carried, signed, and
  nonce-bound in the request object:** `chiproof`'s `issueChallenge` places `threshold` inside the
  challenge (`packages/chiproof/src/challenge.js:73-76,152-175`), which rides in the ES256-signed
  request object at `zkagent.challenge.threshold`; the spike's own test asserts it equals 18
  (`spikes/m2-handoff/tests/roundtrip.test.mjs:86`); it is additionally nonce-bound — chiproof
  mints the nonce over `(tier, verbs, threshold, max_scan_age, expires_at)`, so any post-mint edit
  returns `nonce_forged` (`packages/chiproof/src/challenge.js:225-240`). The scanner parses that
  same challenge object at `MainActivity.kt:1197-1198` and reads ONLY `nonce`; the comment at
  `MainActivity.kt:1191` asserting the challenge "carries only nonce/tier/expiry" is itself a
  defect, factually wrong, and is folded into this question. **Finding 2 — D11's enforcement
  already exists and has been running all along:** `packages/chiproof/src/index.js:233-236` rejects
  `threshold_mismatch` when the presented `claim.threshold` differs from either the challenge's
  threshold or the verifier's configured one, and rejects `under_threshold` when
  `claim.over_threshold !== true`. Nothing here requires building enforcement — it is scanner-side
  wiring only. **Finding 3 — the consequence:** the 2026-09-01 device runs that returned
  `allowed=true` did so only because two independently hardcoded constants happened to agree — the
  scanner's `threshold = 18` (`MainActivity.kt:1181`) and chiproof's default of 18, which the spike
  verifier inherits by passing none (`packages/chiproof/src/index.js:76`;
  `spikes/m2-handoff/server.mjs:143`). Change either constant and every scan returns
  `threshold_mismatch`. Same shape as this branch's other scope-constant bugs (a test and a server
  agreeing because they read one constant), except worse — here the two sides agree BY COINCIDENCE
  rather than by a shared import, so nothing in the code expresses the coupling at all. **Scope,
  stated honestly:** this is a one-line read plus a test — no protocol change, no new request-object
  field, no verifier work — and it closes **D48**'s currently-unmet threshold-from-request MUST (see
  D48's superseded cross-reference, §10). **Rejected approach, recorded so it is not re-proposed:** a
  previous attempt added a sibling `zkagent.threshold` field and was REVERTED, because a sibling
  field would be JWS-signed but NOT nonce-bound — the challenge-carried `zkagent.challenge.threshold`
  is the correct and only source. **Owner's tracking decision, verbatim: "Both — question now, item
  when you decide to build it."** Recorded as an open question now; promoted to a numbered §6.2 item
  only if and when the owner decides to build it inside M2 — no §6.2 item added this revision.

- **Q36 (open, owner-flagged 2026-09-01, descendant of Q33, part b) — compute a real answer from the
  chip's DOB rather than asserting `true` unconditionally.** Genuine open design work, nothing
  chosen. The verifier-side plumbing for a `false` answer already exists (`under_threshold`, Q35
  finding 2), so the open work is entirely device-side: where the DOB-vs-threshold comparison lives
  in the read/mint pipeline; how it interacts with item 3's mint gate (D21: always read chip data,
  conditionally mint evidence); what the user is shown on a `false` outcome (an under-threshold
  result is not a failure to be hidden, but D47/D48/D49's outcome-accuracy rule still applies —
  `Shared` must render the true `false`, never a blind `true`); and how the comparison interacts
  with D28's midnight-UTC `current_date` coarsening (the comparison must use the same coarsened
  date the evidence payload uses, or the log and the proof could disagree). No approach proposed or
  chosen here — recorded as open design work requiring its own pass before anything is built.
  **Owner's tracking decision, verbatim: "Both — question now, item when you decide to build it."**
  Recorded as an open question now; promoted to a numbered §6.2 item only if and when the owner
  decides to build it inside M2 — no §6.2 item added this revision.

- **Q34 (open, owner-raised 2026-09-01) — a general claim vocabulary beyond age.** Owner: "i expect
  to land all things that comes with the id make it available, expiry date ie. > 3 months > 6
  months > 1 year and other things that are usually verified/requested across mode a, b and c."
  Not a decision, nothing built: whether/how zkagent should eventually support the attributes sites
  commonly request, expressed as bucketed/predicate claims rather than raw values — the owner's
  worked example is document expiry rendered as `> 3 months` / `> 6 months` / `> 1 year` rather than
  a date. Considerations for whoever answers this, none decided here: (a) this generalizes D11's
  single configurable threshold into a claim vocabulary, and D11's proof-of-the-wrong-predicate-
  MUST-be-rejected rule would need to generalize with it; (b) each additional predicate discloses
  additional bits — the data-minimisation reasoning behind D40 (no issuer-country attribute) and Q11
  (threshold-probing) applies to every new claim, and likely harder, since multiple predicates
  combined can narrow a person far faster than any one alone; (c) it interacts with FR6's
  anonymity-set framing, since a build shipping more claims partitions its users more finely; (d)
  the owner scoped it across modes A, B, and C, so the per-tier disclosure limits need working out
  per claim, not once for the whole vocabulary. Needs its own design pass and, per the project's
  standing rule, its own riskiest-assumption POC before anything is built.
  **Structural point settled 2026-09-01 (D49), question STILL OPEN:** owner's examples ("age > 18:
  true, expiry > 3 months: false, expired: true") answer HOW multiple claims would be rendered — a
  list of predicate→boolean pairs, the same shape as today's single age claim — but decide NOTHING
  about WHICH claims exist, what buckets they use, which tiers may carry them, or the cumulative-
  disclosure cost of combining them; the claim vocabulary, its per-tier limits, and its
  cumulative-disclosure analysis all remain open and still require their own design pass and
  riskiest-assumption POC before anything is built. **Implementation fact, not a decision:**
  `DisclosureSummary.shared` modelled one claim as a single string, which would have needed
  reshaping rather than extending once this question lands — the list shape (D49) is adopted now,
  while the list has one element and a live test suite around it, rather than later under pressure
  alongside new claim logic; this is a structural change only and adds no claim.

- **Q37 (closed, resolved by implementation 2026-09-01)** — can "consumed" and "expired" handoff
  sessions be distinguished device-side without a verifier round-trip, and where is the challenge
  expiry reachable from? D50 requires refusing a tap/mint against a handoff session that is
  either consumed (single-use nonce already spent) or expired (past the challenge's expiry) — both
  are equally unusable — but whether the device can tell WHICH of the two happened without asking
  the verifier, and where in the existing request/challenge object the expiry timestamp is actually
  reachable from, is not resolved here. No approach proposed or chosen — recorded as open,
  implementer-flagged, pending its own answer before or during D50's implementation.
  **Resolved 2026-09-01, by code fact not design choice:** the challenge expiry
  (`zkagent.challenge.expires_at`) is reachable directly from the already-verified request object,
  with no verifier round-trip needed. Separately, "consumed" needs no device-side detection at
  all — D50's corrected understanding (§10, §6.2 item 15) established that a used handoff session
  is already cleared from app state (`pendingHandoff`/`verifiedRequest` set to null) the moment it
  is used, by pre-existing code, so there is nothing left to distinguish: an expired-but-not-yet-
  used session is caught by the new expiry check, and a used session is simply gone, not present to
  be mistaken for anything else.

- **Q38 (opened 2026-09-02, deferred under D57) — does the in-app log need to survive app close?**
  Owner observation from the 2026-09-02 device run: `ReportLog` is in-memory only (D45: "the log
  accumulates for the app session" — lifetime decoupled from `wipeSession()`, gone only when the
  process ends); closing the app loses every entry. **The design tension, stated not resolved:**
  D45 already chose session-only lifetime deliberately, and NO-GO #9 (no on-device persistence of
  secrets/test keys) does not by itself forbid persisting a value-free log — the log carries no
  secret, only the same disclosure-summary strings already shown on screen — so the boundary that
  would need to be drawn precisely, before any implementation, is exactly what NO-GO #9 covers and
  does not cover. No approach proposed or chosen. Blocked by D57 until the ownership refactor's
  exit criterion is met — `ReportLog`'s write path (`emitReport`, item 16) is exactly the kind of
  single-writer state the freeze is protecting from a fifth uncoordinated change.

- **Q39 (opened 2026-09-02, deferred under D57) — should an incoming handoff switch the visible
  tab?** Owner observation from the same run: Chrome relaunches the app via `singleTop` +
  `onNewIntent` on its last-used tab, which after a scan is often the Log tab — a fresh incoming
  `av://` handoff can arrive with the MRZ form not visible. **Distinguished explicitly from an
  already-decided, adjacent question:** D55 already considered and REJECTED auto-switching tabs
  when a *read completes* ("owner considered and REJECTED... since it would lose the user's place
  in the log after every scan") — that decision stands, is NOT reopened here, and does not answer
  this one. Switching tabs on an *incoming handoff intent* is a different trigger with a different
  cost/benefit (nothing to lose the user's place in, since no read is in progress yet) and has not
  been asked or decided. No approach proposed or chosen. Blocked by D57 — `onNewIntent`/tab-state
  ownership sits inside the same rotation/tab-restore seam flagged as `.claude/remember/
  findings.md` #1 (audit finding, TabLayout selection vs. pane-visibility race), which the freeze's
  exit criterion requires resolving structurally before adding a new tab-switch trigger on top.

- **Q40 (opened 2026-09-02, deferred under D57) — "stuck"-reading Lock button after an
  access-establishment failure.** Owner observation: after a read fails and the Lock button is
  disabled (F3/D43's keep-MRZ-for-correction path), the disabled state reads to a user as the app
  being stuck, not as "correct your details and the button will re-enable"; owner wants copy closer
  to "Tap and scan." **The ownership seam this lands on, named explicitly so it is not
  under-scoped:** `lockButton.isEnabled` has FOUR independent write sites and the 2026-09-02
  read-only ownership audit found no single read site for it at all
  (`.claude/remember/findings.md` #9; `docs/logs/M2-OWNERSHIP-AUDIT-2026-09-02.md`, State join §) —
  it sits at the join between the session-field cluster and the handoff cluster (`pendingHandoff`/
  `verifiedRequest`, findings #2/#3, the audit's own headline "one writer per piece of mutable
  state" violation). No wording or mechanism proposed or chosen. **MUST NOT be touched before the
  structure pass lands** — D57's exit criterion names a single named writer for every mutable
  UI/session field as a precondition, and this field is exactly the kind of state a UX-only change
  would otherwise touch without resolving.

- **Q41 (opened 2026-09-02, EXPLOITABLE, consequence HIGH, deferred under D57 pending owner ruling)** — see `.claude/remember/findings.md` #10: the `av://` intent-handling path (no `lockedMode`/`readInProgress` guard on `handleIncomingIntent`'s `av://` branch, non-volatile multi-writer `pendingHandoff`/`verifiedRequest`) lets any on-device app hijack a session mid-read or mid-biometric-prompt, binding the user's real biometric authorization and real chip read to an attacker-controlled origin; four mitigation options are recorded there, none applied, and whether option (a) (a contained guard) proceeds ahead of the full ownership refactor is not yet owner-ruled. **Same-day addendum:** MITIGATED in commit `730ef09` — `HandoffAdmission.mayAdmitInboundHandoff` now gates the `av://` branch (mitigation option (a)) — but remains OPEN for the ownership fix (lock-time snapshot / SessionState); the guard is to be removed when that lands. **Second same-day addendum:** the refusal path was further trimmed in commit `26f67ac` per `.claude/remember/findings.md` #13 — it no longer calls `emitReport` (Log.e + Snackbar only), closing the unbounded-append vector that refusal path had introduced; `ReportLog.entries` itself remains unbounded and OPEN for the refactor.

- **Q42 (opened 2026-09-02, consequence HIGH — consent defect, independent of and surviving every Q41/`.claude/remember/findings.md` #10 mitigation, deferred under D57 pending owner ruling)** — see `.claude/remember/findings.md` #11: the biometric/device-credential authorization prompt (`MainActivity.kt:1391-1393`, `strings.xml:21-22`) is built from two static strings only — no origin, site, or tier shown — even though `promptAndMint` already has `site`/`scopeDomain` in scope at its call site (`:1369`) and uses neither; the signature is cryptographically bound to `scopeDomain` but the human authorization is bound to nothing, so even on the ordinary single-legitimate-request happy path (no concurrency, no hijack) the user cannot tell whose request they just authorized. Mitigation recorded, not applied: render `site` in the prompt subtitle — both values already in scope, near-free, a FIX under D57's gate; sequencing ahead of the full ownership refactor not yet owner-ruled. **Same-day addendum:** MITIGATED in commit `730ef09` — the biometric prompt title now renders the site via `MintPromptText`/`strings.xml` `biometric_prompt_title_for_site` ("Authorize presentation to %1$s") — but remains OPEN for the ownership fix (lock-time snapshot / SessionState); the guard is to be removed when that lands.

## 12. Grounding (why this isn't a dart in the dark)

- Personhood credentials called for, issuer assumed, none stood up: arXiv **2408.07892** (OpenAI/Microsoft/Harvard et al.); delegation-to-agents follow-up: arXiv **2501.09674** (MIT et al.)
- IETF pipes without a water source: `draft-oauth-ai-agents-on-behalf-of-user`, `draft-klrc-aiagent-auth`, AIP `draft-prakash-aip`, WIMSE — delegation chains rooted in custodial IdP accounts; `sub`-overloading and chain-splicing named as open gaps
- Transport converged on RFC 9421 (Web Bot Auth live at Cloudflare edge since 2026-03; Visa TAP same base) — zkagent attaches as fields and competes with nothing. RFC 9421 signs the *message* (survives CDN/proxy TLS termination) but explicitly puts key **trust** out of scope: `keyid` is opaque and "determining trustworthiness is out of scope for this document." **That gap is the zkagent insertion point** — and it cuts both ways: because the RFC defines no delegation or trust mechanism at all, we are free to define one, and obliged to (FR8/FR10/Q8).
- Deployed precedent for attestation-backed anonymous auth at captcha-grade: Apple Private Access Tokens
- Prior art for document-derived proofs (ZK variant, web3-aimed, no agent story): zkPassport, Self, Anon-Aadhaar — de-risks the chip side, validates the gap on the agent side
- Trust root: ICAO Doc 9303 (signed chip + SOD), public CSCA masterlists (ICAO PKD, BSI). Document numbers change on renewal (drives D9)
- **EU Age Verification Blueprint** (`ageverification.dev`, `eu-digital-identity-wallet/av-doc-technical-specification`) — checked 2026-08-07, and it settles the linkability question in the opposite direction from a common assumption: **linkability is not required by the EU approach, it is the thing the EU approach engineers against.** *"An Age Verification App SHALL use a Proof of Age attestation only once and then remove it from the batch of the issued attestations"* (§4.2); *"An Attestation Provider SHALL support batch issuance"* (§4.3); *"SHALL set the timestamp included in the `ValidityInfo` structure with a precision that limits the linkability information"* (§4.3); and the design principle *"Domain-specific identifiers, or pseudonyms, are used to enable users to avoid relying on the same unique identifier when interacting with online services"* (§2.4). The batch machinery exists because their wallet must round-trip to an attestation provider and cannot do ZK; **zkagent has no issuer in the path, so per-presentation freshness is free and needs no batching** — which is why D13 mode A can meet the property directly. Also confirms our hardware posture: *"An Age Verification App SHALL rely on the device's native cryptographic hardware capabilities, such as the Secure Enclave on iOS, or the Trusted Execution Environment (TEE) and Strongbox on Android"* (§4.2). **Not verified and not to be claimed:** that meeting these properties confers any legal standing (Q17). Cross-reference 8een's `EU-STACK-AUDIT.md` for the ZK-is-`SHOULD`-not-`SHALL` finding and the `FallbackToFullDisclosure` default.

## 13. Success criteria

- S1: M0 evidence shows same-zktag-twice from a real document, all numbers measured (no guessed timings — 8een's 10×-wrong lesson).
- S2: M1b shows two mode-A presentations byte-identical, with a planted stable field proven to break the check.
- S3: A service integrates the verifier with zero PII handling and one dependency decision documented.
- S4: The demo shows a mode-B ban that survives identity reset (new bots, new keys, new IP — same human blocked).
- S5: Every milestone has an evidence doc with deviations and retractions recorded, 8een-style.

## 14. Adoption risk (named, because it outranks the crypto risks)

The dominant risk is not a break in the chain — it is that no one installs the verifier.

- **Two-sided market.** Sites will not check for a signal users do not carry; users will not carry a signal no site checks. Nothing in the cryptography solves this.
- **The borrowable core is the real mitigation** (§4). An adopter who embeds `chiproof` in their own app brings their own users, which converts a two-sided problem into a one-sided sale. It works only if FR10 and FR11 hold: a trust list the adopter controls, and a derivation spec that keeps two clients from forking the identity space.
- **Partial mitigation:** integration cost is near-zero for a site already verifying RFC 9421 — header present ⇒ extra signal, header absent ⇒ unchanged behaviour. Low cost to adopt is not the same as a reason to adopt.
- **The two legs have different timing.** Agent accountability is forecast demand. Age verification is present demand with legal deadlines and incumbent solutions that are expensive and privacy-hostile. zkagent is **one product with two positionings**, not two products: same app, same chip read, same SDK — D13 makes the difference a mode flag rather than a fork. Rung 1 serves the age wedge; rung 2 is the agent bet. Sequencing already hedges the speculative leg.
- **M3 is a demo, not evidence of demand.** The demo site is its own adopter. It proves the flow works; it proves nothing about whether anyone wants it.
- **Standing warning (NO-GO #10 lineage):** this document is the filter, not the collector. v1.4 removed four resolved questions and folded one into another; it still carries 9 open questions, 17 decisions and 11 requirements for a project with zero lines of code. **M0 has not been run. Nothing here is evidence.**

## 15. Version history

| Version | Date | Change |
|---|---|---|
| v1.37 — 2026-09-02, owner-approved | 2026-09-02 | D57 — FREEZE, not a feature: after the M2 scanner reached ~4,780 unreviewed LOC across seven isolated agent rounds, and the D55 pane-visibility bug (root-caused only by direct code inspection) demonstrated the cost, the owner froze new §6.2 items/enhancements until every mutable UI/session field has a named single writer, every async writer is fenced, and the new durable findings log (`.claude/remember/findings.md`, seeded from a read-only ownership audit, `docs/logs/M2-OWNERSHIP-AUDIT-2026-09-02.md`) carries no OPEN entry of consequence HIGH; adopts a rule set for every future agent spawn (entry gate is FIX vs ENHANCEMENT never size, prior-state history carried on every spawn, one writer per state, every async writer fenced, findings forwarded via the durable file not code comments, read-only audit precedes refactor). D55/D56 amended DEVICE-CONFIRMED against `docs/logs/M2-D55-D56-EVIDENCE.md` — both documents mint cleanly through D56's diagnostic and a deliberate wrong-digit reproduction confirms the access-establishment classification, but the evidence doc states plainly that D55's specific Log-tab-then-retap stranding sequence was not exercised (no `UNCHANGED` capture) — confirmed as a working code path, not yet as a reproduced-then-resolved regression test. Three new open questions from the same device run's UX observations, all deferred under D57: Q38 (log lifetime vs. D45/NO-GO #9), Q39 (incoming-handoff tab switch, distinguished from the auto-switch-on-completion D55 already rejected), Q40 (Lock-button "stuck" wording, landing on `lockButton.isEnabled` — an audit-named four-writer field that MUST NOT be touched before the structure pass lands). **Addendum, same day:** Q41 opened — a read-only adversarial analysis (chain verified at source by the orchestrator, `.claude/remember/findings.md` #10) found the `av://` intent-handling path EXPLOITABLE, consequence HIGH, not applied by fix commit `4969a20`. **Second addendum, same day:** Q42 opened — a second-session review (orchestrator-verified at source, `.claude/remember/findings.md` #11) found the biometric prompt shows no origin/site/tier, a consent defect independent of Q41 that survives every one of Q41's mitigation options. **Third addendum, same day:** Q41 and Q42 are MITIGATED in commit `730ef09` (owner-approved strings: prompt title "Authorize presentation to %1$s", Snackbar "Ignored a site request that arrived mid-scan.", log Result line "Refused — another site's request arrived mid-session and was ignored") — both remain OPEN for the ownership fix (lock-time snapshot / SessionState), and the guard is removed when that lands. **Fourth addendum, same day:** D58 sets the ownership refactor's execution order — Report/Log cluster, then Pane cluster, then the lock-time verified-request snapshot (which removes the `HandoffAdmission` guard on landing), then a re-derived Session boundary — recorded with the pass-2 six-cluster basis and a verbatim MUST-land condition on the snapshot step. Recording D57/D58/Q41/Q42 is not new scope (NO-GO #10 intact) — it forecloses scope, adding none. §6.2 (exit-criteria table, items 15/16 confirmed), §10 (D57 added, D58 added, D55/D56 annotated device-confirmed), §11 (Q38/Q39/Q40/Q41/Q42 opened, Q41/Q42 mitigation noted), §15 are annotated. |
| v1.36 — 2026-09-02, owner-approved | 2026-09-02 | D55 and D56 (amend items 15 and 16, original text of both kept) — a real bug found on the owner's live Pixel 6a run, root-caused by direct code inspection and corroborated by logcat: `activity_main.xml` places `loading_layout`/`main_layout`/`log_layout` as overlapping siblings in one `FrameLayout` (later children draw on top), and two independent code paths write their visibilities with neither aware of the third view — item 16/D44's tab listener owns main<->log and explicitly leaves loading alone ("an edge case not covered by items 15/16," per its own code comment), item 15's completion handler owns main<->loading and never touches log. Result: opening the Log tab after a failure then retapping the card left the log painted over the MRZ form with no way back (`onTabReselected` empty, nothing calls `selectTab`), so the user could not reach the field to correct it — four consecutive `AccessDeniedException` failures spaced 6-11 seconds apart, too fast for a retype, are the evidence the retries never carried corrected details. This CORRECTS D54's causal reading in place (D54's diagnostic and classification-order fix are unaffected): D54 attributed the earlier five-failure run to a user "who does not change them"; that reading was incomplete, since the app was structurally preventing the correction it demanded. Fix (D55): all three views' visibility writes go through one function setting all three on every call (both-visible made unrepresentable, same discipline as `emitReport`), the pane decision lives in a pure Android-free object with its own unit test (`View.visibility` is a non-functional stub under this module's `isReturnDefaultValues = true`), `onTabReselected` becomes idempotent, the read-in-progress flag clears on every exit path of the completion handler including the failure branch's early return, a completed read does not auto-switch tabs (considered and declined — would lose the user's place in the log), `onCreate` calls the function once after tab/log state is restored. D56: a new value-free diagnostic — the tag-intent path logs whether the three MRZ fields CHANGED since the previous attempt in-process (`M2 stage: MRZ input UNCHANGED/CHANGED since previous attempt (doc_len=9 dob_ok=true exp_ok=true)`, plus a first-attempt variant), never logging field values or a renderable hash, holding a per-process-salted hash in memory only, reset on `wipeSession`'s `!keepMrzAndMode` branch — motivated by this session's own hour of code inspection needed to answer a question the existing value-free logs could not. §6.2 (items 15, 16, exit-criteria table), §10 (D54 corrected in place, D55 and D56 added), §15 are annotated. |
| v1.35 — 2026-09-01, owner-approved | 2026-09-01 | D54 (amends item 15's access-establishment and transient-failure buckets, original text kept) — diagnostic finding from five consecutive `AccessDeniedException` read failures: `PACE unavailable (AccessDeniedException)` means PACE was attempted and the MRZ-derived key was rejected (wrong typed details), distinct from `PACE unavailable (CardServiceException)` (chip lacks PACE support); verified not a regression in the D43-D53 commit. UX consequence: an access-establishment failure keeps typed details for correction, but an unchanging retry re-derives the same wrong key indefinitely, so the message must make the required action unmistakable and stay distinct from the transient-failure message. Shortens and supersedes D51-lineage strings (kept, not deleted): access-establishment dialog `Couldn't read — check your details and try again.` / Result `Couldn't read — check your details`; transient-failure dialog `Couldn't read — keep the card at the top of your phone.` / Result `Couldn't read — card moved` — the two MUST stay separate, never merged. Everything else previously approved stands unchanged. Also folds in a real bug found the same run: the transient and access-establishment classifications ran in the WRONG ORDER (`accessFailure` set unconditionally by code path in `MainActivity.kt` ~900-912, gating the transient check at ~1014), so a card slip during access establishment was misreported as a data-entry problem — state transitions (keep/keep/reset) were unaffected, only the message was wrong, which is why `FailureTransitionTest`'s state-mapping assertions didn't catch it. Fix: classify transient first from exception evidence regardless of phase, narrow `accessFailure` to genuine `SW 0x6300`->`0x6985`/`AccessDeniedException` denial, move precedence into `FailureTransition` with its own test. §6.2 (item 15, exit-criteria table), §10 (D54 amended) are annotated. |
| v1.34 — 2026-09-01, owner-approved | 2026-09-01 | D53 (amends item 16/D51's mode half, original text kept; approves previously-pending strings) — the plain-language `Mode` line is removed from the log block (redundant with Sent/Shared/Identity, and misleading given D21's always-read/conditionally-mint rule); mode stays in `▸ technical:` and the on-screen derived-mode display. Chip-authenticity three-state rule (D51) stands unchanged; owner-approved wording replaces the implementer's alarming clone-explicit proposal: `Verified — this document's chip proved it is genuine` / `Not supported — this document has no chip authenticity check` / `Not verified — the chip check did not pass`. Approves previously-pending strings: D51's transient-failure dialog and Result line, D52's success dialog (`ID scanned successfully`, deliberately not matched to its Result line). Records two accepted implementation judgements (not new decisions): mode-A/bare scans skip D52's success confirmation; a distinct mid-read progress state is deferred, not refused. §6.2 (items 15, 16, exit-criteria table), §10 (D53 added) are annotated. |
| v1.33 — 2026-09-01, owner-approved | 2026-09-01 | D52 (amends item 15/D43, original text kept) — positive finding, D38's first-sight attester binding holds repeatedly on hardware across four consecutive successful transactions (`attester=matched`), strengthening D51. Decision: a successful, delivered-and-accepted presentation MUST confirm itself with a blocking modal, same mechanism as D43's failure dialogs (one dialog path, not a forked near-duplicate) — found by the owner using the build (success previously had no dialog/transient UI at all, the asymmetric silent case D43's own rule should have covered). Minimal, outcome-only wording, no disclosure restated (already in item 16's log entry/report); dismissal follows D43's existing non-access-failure reset; only `Accepted` delivery qualifies (a signed-but-undelivered presentation keeps its failure treatment, preserving the four-way delivery-outcome split); a mode-A/bare scan with no delivery is not a "verified by the site" success. Dialog wording not yet owner-approved. §6.2 (item 15, exit-criteria table), §10 (D52 added) are annotated. |
| v1.32 — 2026-09-01, owner-approved | 2026-09-01 | D51 (amends items 4, 6/F1, 13/D33, 14/D34, 15/D43, 16/D47/D49, original text kept) — from a live Pixel 6a run: positive finding, D38's first-sight attester binding confirmed `matched` on real hardware; evidence of a mid-read `CardServiceException: Tag was lost`/`IOException` chip-communication failure. Three amendments: (1) item 15 gains a third failure-transition bucket — transient chip-communication failure keeps MRZ/mode for a no-re-entry retry, pending handoff survives, D50 expiry refusal still takes precedence, classification conservative (falls through to reset when unclear); (2) the mode radio is removed, mode is derived from a verified handoff's tier or defaults to mode A, shown as plain text — eliminates F5's bug class by construction, item 13/D33's absent/invalid-tier-fails-loudly requirement is unchanged and now guards the derivation, tier C still refused; (3) item 16's log block gains plain-language mode and three-state chip-authenticity status (verified / not supported / failed, never rendered as false), `chip_auth` unchanged in `▸ technical:`. One alternative declined: item 6/F1's `onStop()` MRZ wipe is reaffirmed unchanged, relaxing it for app-switch convenience was considered and rejected in favor of the narrower item-15 fix. Dialog/status wording not yet owner-approved throughout. §6.2 (items 4, 6, 13, 15, 16, exit-criteria table), §10 (D51 added) are annotated. |
| v1.31 — 2026-09-01, owner-approved | 2026-09-01 | Corrects D50's defect-3 causal claim, which was wrong: code inspection (`MainActivity.kt:1033-1034`, pre-existing) shows `pendingHandoff`/`verifiedRequest` already clear on every definitive delivery outcome and `lockedMode` clears on every completed read, so a consumed session could not have been left reachable; the two observed `SW=0x6982` failures were genuine chip-access failures, dialog accurate, state not wrong. Reframes defect 3 as NEW protection against a session aging past its challenge expiry while still formally pending (never cleared by elapsed time alone). Closes **Q37** by implementation fact (expiry reachable from the verified request object, no verifier round-trip; "consumed" needs no separate detection since clearing already happens on use). Conflict sweep (owner-requested): corrects a stale "(Q33)" cross-reference in D48's row to Q35/Q36; annotates the D47 worked-example code block in §6.2 item 16 as historical for its `Shared`/ordering/entry-count details, current shape being the Exit-criteria row; confirms no other conflicts in item 16's D44→D45→D46→D47→D49→D50 chain, the top-revision-narrative stack, or D48/Q35's framing. §6.2 (items 15, 16, exit-criteria table), §10 (D50 defect-3 corrected, D48 cross-reference corrected), §11 (Q37 closed) are annotated. |
| v1.30 — 2026-09-01, owner-approved | 2026-09-01 | D50 (amends item 15/D43 and item 16/D44, original text kept) — from a live Pixel 6a run with both real documents: positive finding, D39's per-(origin,zktag) key isolation confirmed on real hardware (two mints, two different attester keys); three owner-approved defect fixes: (1) log view lists newest entry first (rendering order only, storage via D35 unchanged); (2) exactly one log entry per scan attempt, terminal outcome replaces the in-progress entry rather than appending a second (every `emitReport` write still reaches logcat, single-write-path invariant unchanged; an entry with no terminal outcome still shown); (3) the substantive fix — a pending handoff/request is cleared once a presentation is delivered and accepted (`direct_post` 2xx), and a tap/mint against a consumed or expired handoff session is refused up front with a blocking dialog before any tap rather than left to surface as a chip-access failure; access-establishment-failure path (F3) unchanged. Dialog wording not yet owner-approved. Opens **Q37** (§11) — device-side consumed-vs-expired distinguishability and challenge-expiry reachability, unresolved, no approach chosen. §6.2 (items 15, 16, exit-criteria table), §10 (D50 added), §11 (Q37 opened) are annotated. |
| v1.29 — 2026-09-01, owner-approved | 2026-09-01 | Corrects **Q33** (opened on an incomplete reading) and splits it, per owner ("Split it into two"), into **Q35** (descendant of Q33, part a — the scanner must read the already-present, signed, nonce-bound `zkagent.challenge.threshold` (`packages/chiproof/src/challenge.js:73-76,152-175`) instead of its hardcoded `18` (`MainActivity.kt:1181`); D11 enforcement already exists (`packages/chiproof/src/index.js:233-236`); the two hardcoded 18s only coincidentally agreed; scoped as a one-line scanner read, no protocol/verifier work, closes D48's unmet threshold-from-request MUST; records a reverted sibling-field approach so it is not re-proposed) and **Q36** (descendant of Q33, part b — computing a real DOB-vs-threshold answer, genuine open design work, nothing chosen). Q33's own text is kept in place, marked superseded not deleted (matching D42's Q29→Q30 descendant convention). D48's Q33 cross-reference (§10) superseded in place to point at Q35 specifically. No §6.2 item added — promotion happens only if/when the owner decides to build inside M2. §10 (D48 cross-reference superseded), §11 (Q33 superseded/split, Q35/Q36 opened) are annotated. |
| v1.28 — 2026-09-01, owner-approved | 2026-09-01 | D49 (amends D48/item 16, original text kept) — `Shared` answers MUST be the literal boolean `true`/`false`, never "yes"/"no" (reconciles doc to already-boolean implementation); `Shared` MUST render as a LIST of `<predicate>: <boolean>` lines (comparison form `age > 18` or bare-boolean form `expired`), followed by the existing negation line — today exactly one element, empty list renders plain "nothing shared", never an empty label/stray colon; list MUST NOT be populated beyond today's one claim (expiry etc. remain Q34, unbuilt). Also records owner-approved `▸ technical:` compliance note `claim_proof: self-asserted by the device — not independently proven (D24)`, tying to Q33. Appends a structural clarification to **Q34** (still OPEN, not closed): the owner's examples settle the multi-claim rendering shape only, not the claim vocabulary/tiers/disclosure cost, which remain open with their own design pass and POC required; records the `DisclosureSummary.shared` reshaping-now-vs-later implementation rationale as a structural fact, not a decision. §6.2 (item 16, exit-criteria table), §10 (D49 added), §11 (Q34 appended) are annotated. |
| v1.27 — 2026-09-01, owner-approved | 2026-09-01 | Opens **Q33** — code-inspection finding: `MainActivity.kt:1181-1182` hardcodes `threshold = 18` and asserts `over_threshold: true` unconditionally, no DOB-vs-threshold comparison exists, no request carries a threshold — D11's threshold-comparison requirement is unimplemented, D48's `Shared`-threshold-from-request MUST is unmet as a consequence; owner: "Both — question now, item when you decide to build it," recorded as a question only, no §6.2 item added. Opens **Q34** — owner direction (not decided): a general claim vocabulary beyond age (e.g. document expiry as `> 3 months`/`> 6 months`/`> 1 year`), needing its own design pass and riskiest-assumption POC; four open considerations recorded (D11 generalization, D40/Q11 data-minimisation compounding across claims, FR6 anonymity-set, per-tier limits across modes A/B/C). Clarifies **D48** (not a new decision): `Shared` is a question→answer record of the exchange, which with `Identity` gives the user the complete picture of a request; interim sourcing recorded — both halves of `Shared` MUST render from the actual signed claim map until real per-request evaluation lands (tied to Q33). §10 (D48 annotated), §11 (Q33, Q34 opened) are annotated. |
| v1.26 — 2026-09-01, owner-approved | 2026-09-01 | D48 (closes D47 residual, amends D47/item 16, original text kept) — `Identity` reused-key wording confirmed: **"known — recognized only here from previous visit"** (paired with the already-confirmed "new — minted fresh for this site"); "only here" is load-bearing, the plain-language statement of D38/D39's per-(origin,zktag) key isolation, MUST NOT be simplified out. New substantive requirement: `Shared` MUST render the actual disclosed predicate/answer as `age above <threshold>: <answer> — and nothing else.`, threshold read from the verified request (not hardcoded), answer the actual asserted value (never assumed true); any non-disclosing path states so plainly and renders no age claim. The disclosed age predicate is carved out of item 5's forbidden-fields list as the one thing the user chose to disclose; rest of item 5 UNCHANGED. Three owner-approved implementation clarifications recorded (not separately numbered): `▸ technical:` line carries the full unmodified report text; debug-only probe buttons render a distinct "Diagnostic OK/failed" summary under the no-site label; the no-site label also covers a failed request-object verification (D37 at the UI layer). §6.2 (item 16, exit-criteria table), §10 (D48 added) are annotated. |
| v1.25 — 2026-09-01, owner-approved | 2026-09-01 | D47 (closes Q32, amends D46/item 16, original text kept) — owner-approved four-field plain-language disclosure block (`Result`/`Sent`/`Shared`/`Identity`) plus a subordinate `▸ technical:` line, per the two worked examples (mode-B success, mode-A bare read); `Identity` restates the D38/D39 per-(origin,zktag) key state (new vs. reused, only the "new" copy owner-verbatim). Stated as a REQUIREMENT that the plain-language lines be accurate per actual outcome — never overstating what was disclosed, never reading as success on a failure path, mode A required to state plainly that nothing left the device (`evidence: []`, D27). Owner also CONFIRMED "Local scan (no site)" as the exact no-site label wording, superseding D46's unconfirmed-wording flag. Value-free constraint (item 5), single-`emitReport()`-write-path invariant, display-only timestamp, and accessibility-snapshot note all restated UNCHANGED. §6.2 (item 16, exit-criteria table), §10 (D47 added), §11 (Q32 closed) are annotated. |
| v1.24 — 2026-09-01, owner-approved | 2026-09-01 | D45 (amends D44/item 16, original text kept) — the log view's lifetime is decoupled from `wipeSession()`'s per-scan `!keepMrzAndMode` branch; it now accumulates for the life of the app session, not one scan. Corrects a self-contradiction found by inspecting the code: `MainActivity.kt` calls `wipeSession(keepMrzAndMode = false)` on every completed read including a successful one, so the literal original clear rule wiped the log on its own success path and successive scans never accumulated. Retention otherwise unchanged (in-memory only, never persisted, survives Activity recreation via D35, gone only when the process is gone). D46 (amends D44/item 16, original text kept) — each log entry MUST carry a title identifying the verified site (`scope_domain`, D37/D42), or the fixed value-free label "Local scan (no site)" for a bare mode-A scan, and MUST be legible to a non-engineer about outcome (what went out, to whom, what was disclosed, success/failure); SUPERSEDES item 16's "MUST NOT change report content" clause to the extent a value-free disclosure summary is now required, while the value-free constraint (item 5), single-`emitReport()`-write-path invariant, and accessibility-snapshot note all stand unchanged. Opens **Q32** (§11): exact disclosure-summary shape/wording not owner-specified. Also records a non-numbered clarification to D43/item 15: the three named failure classes are examples of its general rule, not exhaustive — mint-path failures are in scope and the implementation's wider coverage is kept. §6.2 (items 15, 16, exit-criteria table), §10 (D43 annotated, D45/D46 added), §11 (Q32 opened) are annotated. |
| v1.23 — 2026-09-01, owner-approved | 2026-09-01 | D43 (owner: "when wrong data in, it is not pop up to dismiss but overlay notification that disappears, i should get pop up then ok then it resets") — any outcome that ends a scan attempt and requires user action MUST be a modal dialog with an OK action, not a self-dismissing Snackbar; on dismissal the app keeps the MRZ focused for correction (access-establishment failure, F3's `keepMrzAndMode`) or resets the session (every other failure) — the existing rule, not a second policy. Transient UI stays correct for informational, no-state-change events. New §6.2 item 15. D44 (owner: "the feedback of what happened every scan at the bottom of the app should go to another tab as logs, same output with timestamp") — the value-free report moves to (accumulates in) a separate in-app log view, timestamped, content unchanged, routed through the existing single `emitReport()` write path, in-memory only — governed by item 6/F1 and D35, not NO-GO #9 (secrets/test keys, not on-device persistence) — cleared on any wipe that does not keep MRZ/mode. New §6.2 item 16. Also records, as a stated limitation not a decision: first-sight attester binding (D38/D39) has no re-enrolment mechanism — a lost/reset device is refused `attester_key_mismatch` permanently at every site that knows it; observed as a real refusal (transactions `Cxn0dXWz8nlJfVX3`, `MstvPR4zJGK4VoSG`, 12:42), a staging artifact but a real mechanism. Opens **Q31** (§11): re-enrolment options listed, not decided. §6.2 (new items 15/16, exit-criteria table), §10 (D38 annotated, D43/D44 added), §11 (Q31) annotated. |
| v1.22 — 2026-09-01, owner-approved | 2026-09-01 | D41 (owner: "leave it") closes the FR12 `sig-*/1` linkability-class escalation left open by D38/D39: `sig-ed25519/1`/`sig-p256/1` keep `linkability: 'signer'`, tier ceiling B unchanged — under D39 each key is scoped to `(origin, zktag)`, a fingerprint of one (device, site, document) triple, not a stable per-device value; `'device'` is reserved for a value the same at every site, permanently. Generalises to every future plug: linkability class is a property of the plug, measured from its payload, never inferred from its technology category — cited in code, `zk-passport/1` → `'none'` (D26's `vk_sha256` bucket the one disclosed exception), `sig-*/1`/`signed-receipt/1` → `'signer'`, a hypothetical `key-attestation/1` → `'device'`. Play Integrity worked as a test case, not a class assignment: M1's spike found no device-unique field across sites, so it would NOT be `'device'` — most likely `'signer'` or `'none'` — but any future plug's class MUST come from a fresh probe of its own payload. D42 (owner: "domain") closes **Q30**: the zktag/evidence signing scope stays host-only while D37's origin-consistency check stays the full origin (scheme+host+port) — deliberate, matching what `MainActivity.kt`/`RequestTrust.kt` already implement; flags, without fixing, that "host" and "registrable domain" diverge for a real multi-subdomain deployment, recommending host (subdomains stay distinct scopes) as a production-deployment note, not an M2 blocker. FR12 and §11 Q30 annotated; §10 gains D41, D42. |
| v1.21 — 2026-09-01, owner-approved | 2026-09-01 | D39 (owner: "yeah, isolate") — a live run (11:43) with D38's per-origin attester key found the owner scanning an NL ID card then a US passport at the SAME origin mint the same key, because the attester-key store binds `(scope, zktag)` while the key itself was keyed by scope alone — a site could learn two pseudonyms share one device. Fix: the Keystore alias now derives from origin AND zktag (§6.2 item 1), narrowing D38 not reversing it — general rule, a key's scope must be at least as narrow as the identity it signs for. Cost: one StrongBox key per (site, document); old per-origin keys are left in place, not migrated/deleted. Owner explicitly declined the fraud-detection capability isolation removes (spotting one device presenting two documents with differing age verdicts) as "not our place to judge/police" / "borderline creepy/surveillance"; orchestrator-added technical support: zkagent never binds presenter to document holder, so the signal would be false-positive-heavy regardless. FR12 amended: D39 narrows the `sig-*/1` linkability-taxonomy escalation further but does not resolve it — orchestrator recommends keeping `'signer'`/tier-ceiling-B, pending owner veto. Also records D40 — no issuer/country attribute or accept/reject filter at tiers A/B ("id is id doesn't matter where it's from... mode C of kyc should have that but others shouldnt"), distinguished from CSCA trust-anchor curation (§6.2 item 7, unaffected, remains legitimate); tier C may carry issuer info per D37's existing carve-out. New Q30 (§11, orchestrator flag, pending owner confirmation): the signed scope is host-only while D37's origin-consistency check is scheme+host+port — recommended as deliberate (pseudonym/key survive a port/scheme change; the security check stays exact), not yet owner-ruled. §6.2 item 1, FR12, §10, §11 annotated; §10 gains D39, D40. |
| v1.20 — 2026-09-01, owner-approved | 2026-09-01 | D38 (owner: "agree b+c") — the first end-to-end mode-B run got `sig_unknown_key` because `sig-*/1`'s operator-pinned-list assumption (D30/FR12) never covered a phone's self-generated key; a single global device key would also have been a stable cross-site identifier inside every mode-B presentation (D22/Q23-shaped bug, undetected until this run). Fix: per-origin Keystore alias derived from `scope_domain`/D37 (§6.2 item 1), plus a verifier-side attester-key store keyed by `(scope, zktag)` that binds on first valid presentation (`attester_bound_first_sight`) and rejects a later mismatch (`attester_key_mismatch`) — operator-pinned lists remain a supported alternative store (FR12); `item.data` gains `pubkey`, `key_id` MUST be recomputed and compared. Linkability class for `sig-*/1` does not cleanly fit FR12's 'none'/'signer'/'device' taxonomy under D38 — recorded as an open escalation, kept at 'signer'/tier-ceiling-B unchanged rather than inventing a fourth value. Stated limitation, composes with D29's chip-cloning limitation without weakening it: TOFU means a clone presenting first on a different device binds the wrong device. Spike note: today's run used an env-override hand-pinned dev key, never committed, not a design. Also bakes in seven owner-agreed ("agree, keep it as is") implementation amendments v1.19 had not recorded: D31/chiproof two-alternatives-one-invalid fails whole, no masking; chiproof pin stays 0.4.0 unpublished/additive; item 14 failure falls back to manual scanning rather than halting the session; item 13 mode radio disables the instant a handoff is captured, before verification completes; item 14/D37 well-known path serves a JWK or JWKS, first P-256 key, `kid` matched when present on both sides, mismatched-when-present is a hard refusal; `testImplementation`-only `org.json:json` test dependency (AGP's stub silently no-ops); and `evidence_plug`/`evidence_required` report lines are log-only, never a behavioural input (D36). §6.2 items 1, 9, 11, 13, 14 and FR12 annotated; §10 gains D38. |
| v1.19 — 2026-09-01, owner-approved | 2026-09-01 | Seven owner decisions from a live mode-B handoff run on the Pixel 6a / NL ID card (real Chrome `av://` tap, `direct_post` HTTP 200 `accepted:true`, verdict `evidence_required_missing`). D31: verifier accepts any one of an operator-configured attester-sig evidence set (supersedes D30's single-required-plug framing), needs an any-of semantic chiproof's `evidence.require` does not have today (currently all-of); opened, then closed same-day, Q28. D32: attester-sig plugs are the reference default only, not privileged — an operator may configure any registered evidence plug (D24 clarified, not changed). D33: scanner preselects/locks mode from a pending handoff request's `zkagent.tier`, fails loudly on absent/invalid tier — new §6.2 item 13. D34: scanner verifies the request object's JWS against a pinned trusted-signer set before trusting any field, refuses on failure — new §6.2 item 14, narrows D20 for this build specifically, closes the `HandoffClient.kt` escalation, opened then closed same-day, Q29. D35: value-free report MAY survive Activity recreation in-memory (approval of already-implemented behavior). D36: closes Q28 — a device never chooses to downgrade key/evidence strength, only falls through to the next preference on failure of the preferred one; the verifier cannot enforce "must offer strongest" without attestation, which is excluded (D22). D37: closes Q29 — request trust is origin-bound (EU AV-profile shape: Annex A TLS/Web PKI root, `client_id_scheme: redirect_uri`), not authority-bound; scanner MUST enforce `client_id`/`request_uri`/`response_uri` origin consistency and fetch the request-signer key over TLS from a well-known path under that origin; tier-C operator-curated allow-lists permitted (D19/D20 shape); OS-level trust for direction 2 (requester trusting the app) accepted as a stated limitation, not mitigated, alongside chip cloning (D29); `av://` hijack risk bounded by origin binding, verified HTTPS App Links recorded as a follow-up; M2 spike keeps one dev-only pinned key pending a real TLS origin. §6.2 items 4 (F5 closed), 6, 8, 9 annotated; item 11 corrected — `sig-p256/1` naming no longer "candidate, Dn pending" (shipped in chiproof 0.4.0, now governed by D31/D32/D36), pin text changed from a stale published-npm claim (`chiproof@0.3.0`) to `chiproof 0.4.0` (in-repo, unpublished); item 14 amended with D37's origin-binding MUST and well-known key path. Exit-criteria table gains two rows. Evidence: commit `9f60489` (handoff off main thread, `response_uri`/`state` read from the request object's top level). |
| v1.18 — 2026-08-31, owner-approved | 2026-08-31 | F2 resolved (`docs/logs/M2-SESSION-POC.md` PENDING → RESOLVED): algorithm agility — support both P-256 and Ed25519, operator chooses by device capability, same pattern as the evidence slot (D24). §6.2 item 1 amended: app selects/reports the strongest key algorithm the device supports; default hardware-backed P-256 where StrongBox exists, software Ed25519 only where the adopter prefers algorithm uniformity over hardware custody. §6.2 item 9 amended: verifier accepts more than one signature algorithm. §6.2 item 11 amended: a P-256 evidence plug in `chiproof` (candidate name `sig-p256/1`) is now permitted, not scope creep — required by item 1's device-capability reality. Candidate decision, `Dn` pending; no `Dn` assigned this revision. |
| v1.17 — 2026-08-31, owner-approved | 2026-08-31 | M2 build scope written (NO-GO #10): new §6.2, twelve MUST/MUST NOT items (device key custody, biometric gate before minting, app-side passiveAuth mint gate, mode captured at scan time not re-read from UI, no DG1/MRZ rendering + ResultActivity removed, onStop-not-onPause + keep-state-on-access-failure lifecycle, masterlist two-bucket rule, av://+direct_post handoff scope with DC-API/EU-wallet exclusions, bare/sig-ed25519-1 evidence defaults with the signed layout stated once, network_security_config, explicit non-goals, build's own riskiest-assumption POC proposal) plus an exit-criteria table. Four candidate decisions in §6.2 (items 1, 7, 10, 12) were settled by the owner on 2026-08-31 and await Dn numbering. |
| v1.16 (draft) | 2026-08-31 | Interaction recorded, not a new decision: D9's derivation field (`document_number`, in DG1) is forgeable without chip auth per D29 — mode-B uniqueness/blocking hold only where `chip_auth: true` (D21). D9 and D29 rows annotated, Q18 closure cross-referenced, FR11 gains the conditional-uniqueness statement. Owner-approved 2026-08-31 (orchestrator-recommended): CSCA-absent-from-a-well-formed-masterlist is a real no (`ok:true, allowed:false`), not `ok:false` — reserved for masterlist integrity failure; M0 row's negative (ii) annotated as superseded, M2 row's masterlist line gets the explicit two-bucket statement; FR10/D21 checked and skipped as homes for a third clause |
| v1.15 (draft) | 2026-08-31 | D30 — `sig-ed25519/1` default mode-B evidence delivery (amends D27, mode A stays bare); FR12 entry added (linkability 'signer', tier ceiling B, ceiling vetoable); D27 and M2 rows annotated |
| v1.14 (draft) | 2026-08-31 | D9 closed — derivation field = document number (M0 Findings 3, 10, 11); D29 closes Q18 — mode B accepts non-chip-auth documents, clone-replay stated as in-bar; FR11 annotated; M2 row gains its riskiest-assumption POC opener; status line updated |
| v1.13 (draft) | 2026-08-30 (late evening) | D27 M2 ships bare (Q25 closed); D28 current_date day granularity (Q27 closed) |
| v1.12 (draft) | 2026-08-30 (late evening) | D26 bucket disclosure; Q15 closed; Q27 opened; M1b ran; FR9/FR12/D19 updated |
| v1.11 (draft) | 2026-08-30 (late) | D25 zk-passport/1 tier-A-only (no nonce input in the circuit); Q26; M2 row to D24 evidence-set language; FR12 annotation; D20 seal amendment; ZK wording harmonised with D24/D25 (NO-GO #7 reworded) |
| v1.10 (draft) | 2026-08-30 (late) | D24 evidence slot; D1 amended; D23 superseded (Play Integrity non-borrowable); FR12 registry; M1 row → spec; Q25 |
| v1.9 (draft) | 2026-08-30 | D23 resolves Q23 (voucher-grade v1, gated ZK track); Q24 added; risks 4/7/8 and D2 annotated; M1 row updated |
| v1.8 (draft) | 2026-08-30 | D22 relaxes tier-A same-site unlinkability to a non-goal; Q23 re-framed to "nothing stable across sites"; Play Integrity spike queued; ZK-passport route recorded as set-aside |
| v1.8 (draft) | 2026-08-30 (later) | Q23 evidence recorded for options (3) and (5); risk #1 claim withdrawn |
| v1.7 (draft) | 2026-08-29 | M1 POC run: risk #4 holds, risk #8 confirmed; Q23 opened; masterlist moved to the phone; M1 row rewritten |
| v1.0 | 2026-07-26 | Initial. D1–D8 signed, NO-GO table, M0–M5, riskiest-assumption register |
| v1.1 | 2026-07-26 | M0 de-platformed — cheapest working NFC path, not iPhone-gated. Q2 moot; Q6 added |
| v1.2 | 2026-08-02 | D2 flipped to Android-first; `tag` → `zktag` throughout (RFC 9421 collision); FR8 added; Q7, Q8 added; Q5/Q6 resolved |
| v1.3 | 2026-08-03 | D10 (secret expiry), D11 (configurable threshold, one bit); Q9 resolved; Q8 narrowed to inline cert; §13 adoption risk; Q11–Q14; risk items 6–7; ICAO-scope non-goal rewritten |
| **v1.6** | **2026-08-29** | **Post-M0 disclosure model recorded as shape, mechanics deferred.** New §1.1 glossary separating *challenge nonce* / *secret* / *zktag*, which the project's own notes had been conflating under "nonce". D19 three tiers (A anonymous/default/open; B pseudonymous/open because domain-scoped; C attributed/gated by pinned issuer keys, refused-not-downgraded from unpinned keys); the operator surface is a published verb vocabulary with verbs switched on/off — *asked*, never *captured*. D20 signed challenges; issuer key = identity, pinned per build with a tier ceiling; resolves Q20 in principle. D21 always-read/conditionally-mint with `chip_auth` reported in B/C only, verifier-enforced (M0 Findings 4, 9). Predicates non-goal narrowed to tiers A/B. New Q21 (authority→requester admission, rung-2-shaped, deferred) and Q22 (tier-C verb list, no similarity scores). **Nothing here is built; M1 is next and reopens these against code** |
| **v1.5** | **2026-08-28** | **M0 row rewritten so the spike can fail**: planted negatives (DG1 byte flip, CSCA removed ⇒ `ok:false`) mandatory; documents named (US passport + NL ID card); access protocol negotiated, never per-country; MRZ typed, never stored; DG1 + SOD only, DG14/DG15 probed; per-field zktag candidates so D9 is decided on a table; BSI all-country master list pinned, certs-parsed = certs-declared assertion; PII-free evidence rule; evidence path moved to `docs/logs/` after the docs reorg; Pixel/Samsung wording aligned with D2. New §6.1 go/no-go table. Risk #2 names PACE and the two documents |
| **v1.4** | **2026-08-07** | **Restructured into rungs (§4). D12 package name `chiproof`. D13 two disclosure modes — mode A anonymous is now the default and the age leg carries no pseudonym. D14 greedy `acceptedDocuments` (k is a mode-B cost only). D15 FR6 narrowed rather than retired. D16/FR11 published derivation spec. D17/FR10 adopter-held trust list. D10 revised to a configurable ceiling with one-bit freshness negotiation. New FR9 unlinkability budget + M1b probe + Q15 (attestation as covert identifier — blocks the mode-A claim). New NO-GO #11. Cross-document unification made an explicit non-goal; D9 narrowed accordingly. Q1 folded into Q14; Q2/Q5/Q6/Q9 closed out of §11. EU Age Verification Blueprint checked and cited in §12. Legal posture fixed as demonstration-not-certification (Q17). New Q18 — passive authentication does not prove the chip is the original, so a clone could mint the same zktag; M0 must report AA/CA support. Companion design doc added at `docs/product/zkagent-design.md`** |
