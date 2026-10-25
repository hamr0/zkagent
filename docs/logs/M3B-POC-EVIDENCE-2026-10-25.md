# M3b — opening riskiest-assumption POC (Pixel 6a, 2026-10-25) — PRE-REGISTERED, RESULTS FILLED 2026-10-25: PASSED

**Status**: pre-registration for milestones.md §6.9.1 (D109(f)), written BEFORE any device
run; device results (sections 6-10) were filled in after the 2026-10-25 run and are marked as such. The cells were empty (`—`) on purpose until then. The criteria, the planted negatives and
their paired passes are fixed here first; the owner runs the device steps; results are read back from
logcat and the demo verifier's stdout and entered afterwards. A cell that is filled in later with
something other than what was pre-registered is a FINDING, not an edit to the criterion.

**Branch / commit**: `feat/m3b-tier-c`, built on `96bdb3b`. The code commit carries this file; the
APK's version stamp (Scan tab footer, and `M2 REPORT` lines) shows that commit's short SHA with no
`-dirty` suffix when the APK was built from a clean tree. Commit SHA: *recorded in the report that
delivered this file*.

**Rule for this file (carried from `M3-POC-EVIDENCE-2026-09-04.md` and its predecessors)**: no PII, ever.
Field NAMES, booleans, lengths, check ids, HTTP statuses and timings only. No name, document number, date,
nationality value, nonce or zktag appears here. The two real documents are "the US passport" and "the NL ID
card" and nothing more. The Diagnostics tab of the scanner holds typed name/nationality while the probe is armed
and the Scan tab holds the document number and dates: neither is ever accessibility-snapshotted or screenshotted.

---

## 1 — Scope: what the POC built, and what it deliberately did not

Aimed at the riskiest assumption (§6.9.1): **the five item-8 predicates can be evaluated correctly from the
DG1/MRZ fields the scanner reads off a REAL chip, on both documents, with name-match the weakest.**

Built (the minimum criteria 1-4 need; nothing else from §6.9.2):

| Piece | Where | Purpose |
|---|---|---|
| Five predicate evaluators, three-way answer (`true` / `false` / `cannot tell`) | `TierCPredicates.kt` | criterion 1-2 |
| JMRTD `MRZInfo` to fields adapter (JMRTD parses the chip; this app parses nothing) | `MrzFields.kt` | criterion 1-2 |
| Probe battery, verdict lines only, with planted negatives and paired passes | `TierCProbe.kt`, `TierCProbeInputs.kt` | criterion 1-2 |
| Tier-C gate: operator switch + exact-hostname pin + holder setting | `TierCGate.kt`, `OperatorPolicy.kt` | criterion 3-4 |
| `operator.json` `tier_c.enabled` + `tier_c_verifiers` (rules 7 relaxed, 10, 11) | `build.gradle.kts` | criterion 3 |
| Holder setting "Allow additional verifications", OFF by default (on the Diagnostics tab: no Settings tab exists) | `HolderSettings`, `activity_main.xml` | criterion 4 |
| Minimal tier-C request/answer, ONE predicate shape (expiry bucket), POC-local wire `zkagent-poc-tierc/0` | `TierCRequest.kt`, `apps/demo/tierc-poc.mjs` | the "answered end to end" positive of 3 and 4 |

NOT built (still the later M3b build): items 3-6 (dropped), 10-12 (deferred), name/number/nationality/DOB
predicates in a REQUEST (they are evaluated by the probe only), shared age lock (items 9, 23), `chiproof`
tier-C schema (item 14), demo page tier-C control (item 15), biometric confirmation for tier C, a Settings tab.

The tier-C wire (`zkagent.predicates` in the request, `zkagent-poc-tierc/0` answer, the demo's own verdict) is a
POC-local choice, not a ruling on item 14. "Verifier receives a non-empty presentation with `ok:true`" in
criteria 3-4 is satisfied by the demo's POC verifier (`ok:true, allowed:true`), not by `chiproof`.

## 2 — Expectation sources (so no expectation is read from the code under test)

| Input | Where the truth comes from |
|---|---|
| Document number, date of birth, date of expiry | typed by the owner on the Scan tab (needed for BAC/PACE anyway); the chip value is compared to it |
| Name | typed by the owner on the Diagnostics tab "as a person would" |
| Nationality code | typed by the owner on the Diagnostics tab, exactly as printed in the MRZ |
| Wrong inputs | produced by `mutateOneCharacter` / `mutateLastDigitOrCharacter` and fixed decoy codes, never by asking the evaluator |
| Expiry / DOB pairs | `today` shifted 5 days either side of the typed date's bucket or birthday boundary, so each pair is "a boundary on each side of the true value"; the exact day, leap day and end-of-month cases are the hand-computed synthetic unit table (labelled synthetic, not real-chip evidence) |
| "Today" answers (info only) | the owner writes the expected `true`/`false` per bucket and per age in section 5 BEFORE the run |

## 3 — Software evidence (filled: not a device result)

| Item | Result |
|---|---|
| Scanner JVM unit tests (`:app:testRegularDebugUnitTest --rerun-tasks`, parsed from the JUnit XML) | 559 run, 0 failures, 0 errors, 0 skipped (baseline 495; +64) |
| `:app:assembleRegularDebug` | exit 0 |
| `apps/scanner/scripts/operator-json-negative.sh` | exit 0, 21 PASS (baseline 16; case 3 now expects rule 11 because rule 7 is relaxed; +4 negatives, +1 positive) |
| `apps/demo` `npm test` | 53 pass, 0 fail (baseline 41; +12) |
| Sabotage control (non-strict expiry comparison, dropped exclusion cap, dropped pin check) | 13 of 56 tier-C tests turned red; restored byte-identical, suite green again |

## 4 — Pre-registered criteria (§6.9.1, verbatim intent) with planted negatives and paired passes

Every negative below is paired with a non-vacuity pass on the SAME document in the SAME run, and the probe's own
`group ... neg_excluded=N` line must show N above 0 with `pos_confirmed` above 0. A probe plant that did not change
its input is reported as a FAILED check (`name.plant_effective`, `docnum.plant_effective`), so a green run cannot
mean "the guard matched nothing".

### Criterion 1 — field reads and booleans on real chips (both documents, mandatory)

| Group | Positive (confirmed) | Planted negative (must exclude) | Pairing |
|---|---|---|---|
| `field_reads` | five fields read non-empty; chip expiry and DOB equal what was typed | chip value must NOT equal typed value plus one day | same fields |
| `expiry` (3, 6, 12 months) | `today` 5 days before the boundary: `true` | `today` 5 days after: `false` | per bucket, same chip read |
| `dob_range` (15, 16, 18, 21, 60, 65) | `today` 5 days after the birthday: `true` | 5 days before: `false` | per age |
| `document_number` | typed number: `true` | last digit changed by one: `false` | `docnum.plant_effective` proves the change |
| `nationality` | confirmation with typed code `true`; exclusion set WITHOUT own code `true` | confirmation with a decoy `false`; exclusion set WITH own code `false`; set over 10 entries is a REFUSAL | decoys differ from own code by construction |
| `name` | typed name `true` under at least one of the two orders; case, `<`-separator and hyphen variants `true` | one-character-changed name `false` under EVERY order | `name.plant_effective` proves the change |
| `unreadable` | uncorrupted expiry is NOT a refusal | expiry with last character replaced by `X`: refusal for all three buckets; missing nationality, name, DOB: refusal | a refusal is `cannot_tell`, never `false` |
| `integrity` | the real document is answerable | the M0 negative (`M0Probe.tamperedDg1`, one DG1 bit flipped) through the same passive-auth code is NOT answerable: no boolean is produced | distinct from a real `false` |

**An unreadable or missing field MUST produce a refusal, never `false`.** The M0 tamper gives
`ok=true, allowed=false` (a real "no" from passive auth); the predicate layer treats that as "no boolean at all".

### Criterion 2 — name-match normalisation observed on real MRZ data (both documents)

Recorded as booleans per document: `mrz_form`, `name_field length_known`, `at_capacity`,
`uses_angle_filler`, `uses_space_separator`, `contains_hyphen`, `all_ascii`, `has_secondary`; and per D101 rule
whether the real name EXERCISED it (`name_rule_exercised case/hyphen/accent/angle_filler`); and which of the two
orders (`given_first`, `surname_first`) the typed name matched.

Pre-registered reading, not a ruling: D101 and §6.9.1 do not say whether a site's name string is given-names-first
(as typed) or surname-first (as the MRZ prints it). The probe evaluates BOTH and reports both; the order, the
apostrophe, ß/Ø/Æ transliteration and hyphen-as-separator-versus-deleted are reported as observed or "not observable
on these documents" (D109(g)6: accepted). The synthetic unit table pins what the code does today for the edge cases and
is labelled synthetic.

### Criterion 3 — pin-list gate negative (one document is enough: the gate fires before any chip read)

Build ON: switch on, `127.0.0.1` pinned, `verifiers` = `127.0.0.1` and `localhost`, `localhost` NOT pinned.

| Run | Request | Setting | Expected |
|---|---|---|---|
| 3a (positive control, = 4 paired positive) | tier C, expiry 3 months, from `127.0.0.1` | ON | ACCEPTED end to end; verifier: non-empty presentation, `ok:true` |
| 3b (the negative) | the same request from `localhost` | ON | REFUSED in the app (`cause=not_pinned`) before any read or mint; verifier sees nothing |
| 3c (second control) | tier C from `127.0.0.1` on the build with the OPERATOR switch OFF | ON | REFUSED with the existing "not supported in this build" wording (`cause=operator_switch_off`); verifier sees nothing |

3b is non-vacuous only if the verifier log shows `request_uri GET` for that transaction (the app really reached and
verified the `localhost` request) and the app log shows `cause=not_pinned`. A fetch failure for `localhost` would be a
different refusal and would NOT count as the pin refusing; it would be a finding.

### Criterion 4 — the single holder switch (one document is enough)

| Run | Request | Setting | Expected |
|---|---|---|---|
| 4a (the negative) | tier C, expiry 3 months, from `127.0.0.1` | OFF (default) | REFUSED in the app (`cause=holder_setting_off`) before any read or mint; verifier sees nothing |
| 4b (setting gates tier C only) | tier A from `127.0.0.1` | OFF | ANSWERED; verifier `ok:true` |
| 3a (the paired positive, identical request to 4a) | tier C, expiry 3 months, from `127.0.0.1` | ON, same app run | ANSWERED; verifier `ok:true` |

## 5 — Owner-declared expectations for the "today" lines (fill BEFORE the run; booleans only)

**Not pre-filled by the owner before the run.** The owner was asked to fill this table and did not; every cell is
left blank on purpose. No expectation has been reconstructed or invented after the fact, so the "today"
answers (which are info-only, not a pass condition) have no pre-registered expected value to be compared against.

| Question about the document | US passport expected | NL ID card expected |
|---|---|---|
| expires more than 3 months from today | not pre-filled | not pre-filled |
| expires more than 6 months from today | not pre-filled | not pre-filled |
| expires more than 12 months from today | not pre-filled | not pre-filled |
| holder is at least 15 / 16 / 18 / 21 / 60 / 65 | not pre-filled | not pre-filled |

## 6 — Results: criteria 1 and 2 (both documents)

Source: value-free verdict lines captured by the orchestrator while reading the device and demo output live on
2026-10-25 (US passport armed 13:24:44, run 13:24:57; NL ID card armed 13:30:18, run 13:30:25). The phone's logcat ring
buffer has since rolled over, so the captured lines are the record; the raw `adb logcat -d` is no longer
available to re-read. Build: both APKs built from `2720daa` (see section 9).

| Group (from the `group ...` lines) | US passport `pos_confirmed / neg_excluded / non_vacuous` | NL ID card |
|---|---|---|
| field_reads | 7 / 2 / true | 7 / 2 / true |
| expiry | 3 / 3 / true | 3 / 3 / true |
| dob_range | 6 / 6 / true | 6 / 6 / true |
| document_number | 2 / 1 / true | 2 / 1 / true |
| nationality | 2 / 3 / true | 2 / 3 / true |
| name | 6 / 1 / true | 6 / 1 / true |
| unreadable | 1 / 6 / true | 1 / 6 / true |
| integrity | 1 / 1 / true | 1 / 1 / true |
| `observe` (observe-only group, by design; not a criterion-1 group) | 0 / 0 / false | not separately recorded for NL; the US line shows the same by design |
| `summary checks / pass / fail / all_groups_non_vacuous` | 60 / 60 / 0 / true | 60 / 60 / 0 / true |

Notable check lines (US passport run): `nat.exclusion_over_cap_refused` expected `cannot_tell`, actual
`cannot_tell(exclusion_set_size)`; `expiry_{3,6,12}mo.corrupted_field_is_refusal` gave `cannot_tell(expiry_unreadable)`;
missing nationality, name and DOB gave `cannot_tell(*_unreadable)`; `integrity.tampered_dg1_not_answerable`
expected `false`, actual `false`. Path: the US passport reported "PACE unavailable (CardServiceException)" and fell back to
BAC (normal for this passport); the NL ID card used PACE (no such line).

| Per-check "today" answers (observe lines) vs section 5 | US passport | NL ID card |
|---|---|---|
| `expiry_beyond_3mo.today` / `6mo` / `12mo` | not captured in the verdict-line record (info-only); no section-5 expectation exists to compare to | same |
| `dob_at_least_N.today` for the six ages | not captured in the verdict-line record (info-only); no section-5 expectation exists to compare to | same |

| Name observations (criterion 2) | US passport | NL ID card |
|---|---|---|
| `mrz_form` | TD3 | TD1 |
| `name_field length_known / at_capacity` | true / false | true / false |
| `uses_angle_filler / uses_space_separator / contains_hyphen / all_ascii / has_secondary` | true / false / false / true / true | true / false / false / true / true |
| order the typed name matched (`given_first` / `surname_first`) | `given_first` result=true; `surname_first` result=false | `given_first` result=true; `surname_first` result=false |
| `name_rule_exercised case / hyphen / accent / angle_filler` | true / false / false / true | true / false / false / true |
| ß / Ø / Æ / apostrophe observed on the real name | not observable on this document (all_ascii=true; no hyphen, no accent). The record carries no per-character flag for ß/Ø/Æ/apostrophe beyond these booleans, so the claim rests on them | same |
| `secondary_identifier present` | true | true |

Accepted as "not observable on these two documents" under D109(g)6; the synthetic unit table (labelled synthetic) is the
only coverage of those edge cases.

| Read-to-boolean timing (`read_to_boolean_us`, microseconds): min / median / max across checks | US passport | NL ID card |
|---|---|---|
| all checks | min and median not captured; "mostly 1-60 us"; max 359 us (the one-character-changed name check) | not captured (the record states only the 60/60 summary) |

| Failed checks (id and `actual` only) | US passport | NL ID card |
|---|---|---|
| none (fail=0) | none | none |

## 7 — Results: criteria 3 and 4

Scanner lines read: `M3b tier-C: ...` (gate and answer verdicts) and `M3b: holder setting ...`. Verifier lines read:
the demo's stdout, `tx created`, `request_uri GET`, `verdict` (the third absent means the verifier saw nothing).

| Run | App build | Setting | Request | App verdict line | Verifier `request_uri GET` seen | Verifier `verdict` line | Pass? |
|---|---|---|---|---|---|---|---|
| 4a (tx `_SiH0nYiMnb5vrQa`) | ON | OFF | tier C, `127.0.0.1` | 14:59:25.264 REFUSED before any read or mint, `cause=holder_setting_off chip_read=false mint=false` | yes (`tx created mode=C predicates=expiry_beyond:3`; `request_uri GET`) | none | PASS |
| 4b (tx `P7rv4US8IHqg3bPc`) | ON | OFF | tier A, `127.0.0.1` | 15:03:53.640 `direct_post http_status=200`, DELIVERED (bare presentation sent) | yes | `verdict tier=A threshold=18 ok=true allowed=true reason=no-evidence-required evidence=[] attester=n/a` | PASS |
| 3a (tx `6uBD2D2KDr_AXsh9`) | ON | ON | tier C, `127.0.0.1` | 15:06:19.315 `gate=allow predicates=1`; 15:06:24.865 answered `expiry_beyond months=3 result=true`; 15:06:24.873 `http_status=200`, DELIVERED (tier-C answer sent) | yes | `verdict tier=C ok=true allowed=true reason=answers-received answered=expiry_beyond:3=true` | PASS |
| 3b (tx `YQKGfUHjalA3_3iJ`) | ON | ON | tier C, `localhost` | 15:08:34.579 REFUSED before any read or mint, `cause=not_pinned chip_read=false mint=false` | yes (`tx created` and `request_uri GET` present) | none | PASS |
| 3c (tx `njh3hYn_P5_QCKFu`) | OFF | ON | tier C, `127.0.0.1` | 16:52:14.594 REFUSED before any read or mint, `cause=operator_switch_off chip_read=false mint=false` | yes (`tx created` and `request_uri GET`) | none | PASS |

| Detail | Result |
|---|---|
| 3a presentation non-empty, verdict `ok` / `allowed` / `reason` / `answered=` booleans | verdict `ok=true`, `allowed=true`, `reason=answers-received`, `answered=expiry_beyond:3=true`. The record states the answer was received and accepted; it does not state a byte count for the presentation, so "non-empty" is evidenced by `answers-received` plus the answered boolean, not by a size |
| 3a app read-to-boolean time (`M3b tier-C: answered ... read_to_boolean_us`) | 269 us |
| 3b: gate cause in the app log, and `chip_read=false mint=false` | `cause=not_pinned`, `chip_read=false mint=false`; request object verified first (15:07:24.772, `origin=http://localhost:8787 signature_verified=true key_kind=dev-pinned`); dialog: "This site is not approved to ask additional verifications. Nothing was read and nothing was sent." |
| 4a: gate cause in the app log, and `chip_read=false mint=false` | `cause=holder_setting_off`, `chip_read=false mint=false`; request object verified first (14:58:58.353, `origin=http://127.0.0.1:8787 signature_verified=true key_kind=dev-pinned`); dialog: "Additional verifications are turned off. Nothing was read and nothing was sent." |
| 3c: gate cause in the app log | `cause=operator_switch_off`, `chip_read=false mint=false`; request object verified first (16:51:42.300). The dialog showed "handoff: REFUSED — tier C requested, not supported in this build (no tier-C flow)" (the pre-registered "existing wording"; see UX finding in section 10) |
| `localhost` reachable from the phone through `adb reverse` (3b non-vacuity) | yes: the demo log shows `request_uri GET` for the `localhost` transaction, and the app log shows the request object verified for `origin=http://localhost:8787` before the `not_pinned` refusal. This is not a fetch failure |

Holder-setting state: the log shows `allow_additional_verifications=true` at 15:05:20.350, after 4a (14:59) and 4b (15:03) ran with the
setting OFF. Whether 4a and 3a ran inside one app process (the criterion's "same app run") is not stated in the captured record.

## 8 — Verdict (filled after the run)

**Overall: the POC PASSED all four pre-registered criteria, each judged against the pass rule written in this file.**
One qualification is stated rather than hidden: the criterion-1 rule says "every group non-vacuous"; the `observe`
group reads `non_vacuous=false` by design (it is observe-only, and not one of the eight criterion-1 groups in section 4),
and the run's own `all_groups_non_vacuous=true` summary agrees. Other evidence gaps, none of which breaks a rule: the
"today" lines were not captured, section 5 was not pre-filled, per-check timing min and median were not captured, and the
"same app run" wording of 4a/3a is unconfirmed (section 7).

| Criterion | Pre-registered pass condition | Result |
|---|---|---|
| 1 | every group non-vacuous on BOTH documents, zero failed checks | PASS. All eight groups non-vacuous on both documents (pos_confirmed and neg_excluded above 0 everywhere); 60 checks, 60 pass, 0 fail on each document |
| 2 | observations recorded on BOTH documents; unobservable edge cases reported as such | PASS. All booleans recorded for TD3 (US passport) and TD1 (NL ID card); ß/Ø/Æ/apostrophe reported as not observable on these documents |
| 3 | 3a answered, 3b refused (not_pinned, fetched and verified first), 3c refused (operator switch) | PASS. 3a answered (`ok=true`), 3b `cause=not_pinned` with request fetched and verified and `request_uri GET` seen, 3c `cause=operator_switch_off` |
| 4 | 4a refused (holder setting), 3a answered, 4b answered | PASS. 4a `cause=holder_setting_off`, 3a answered, 4b answered (tier A, `ok=true`) |

## 9 — Device, build and run facts

| Item | Value |
|---|---|
| Device | Pixel 6a, Android 17 |
| Security patch / build | 2026-10-05 / CP3A.261005.002.A1 |
| APK (tier-C switch ON) sha256 | `f56dde7a9d77fca1d70e42f529528a9623f4f72e4ccf66b95996c19cabf771f4` |
| APK (tier-C switch OFF) sha256 | `74ff5f8b9b1cfb0d4bcd6d161e733994dcdad2b294b5701c496546076b5bb652` |
| Built from | `2720daa` (the opening-POC commit; the header's `96bdb3b` is the base it was built on) |
| Run date | 2026-10-25, orchestrator reading verdict lines live, device run by the owner |

### Attempts that did NOT count

| Attempt | Why not counted |
|---|---|
| First 4a attempt, tx `okK5SHDJ4MSlLkx-` | The session expired before the lock: "Verification session expired". Correct behaviour; the owner was too slow for the 120 s TTL. 4a was re-run (tx `_SiH0nYiMnb5vrQa`) and that run counts |
| 15:01 two plain local scans (one "Couldn't read", one "Read OK") before the 4b request was sent | Plain scans with no request; no gate or predicate was exercised |
| 13:28 NL ID card scan | An unarmed plain read ("Read OK"); the probe was not armed, so it produced no criterion-1/2 lines. The armed NL run is 13:30:18/13:30:25 |

### Pre-run incident (not a POC failure and not an M3b defect)

13:05-13:07: the US passport BAC read was refused with `AccessDeniedException SW=0x6982` (mutual authentication failed). The owner
had typed a wrong date. Diagnosed by an A/B: a v0.8.0 (`942c9a5`) debug build was installed and failed identically, so the cause was not
the M3b code. The owner then found the mistyped date, and both documents read OK on v0.8.0, before the M3b run above.

### Observations (reported, not rulings)

- The typed name matched given-names-first on both documents and did NOT match surname-first (both results identical across the two documents).
- US passport is TD3; NL ID card is TD1. The US passport fell back from PACE to BAC; the NL ID card used PACE.
- ß/Ø/Æ and the apostrophe are not observable (`all_ascii=true`, no hyphen, no accent on either name).
- Timings: US passport read-to-boolean mostly 1-60 us, max 359 us; 3a tier-C answer 269 us. NL timings not captured.

## 10 — Open items and findings for the real build

**Open owner decision (informed by this result, not ruled here): name order.** The evidence shows the typed
(given-names-first) form matches and the surname-first form does not, on both documents. D101 still does not say which
order a site's name string uses. Left OPEN for the owner (also finding #26 item 1).

UX findings, owner-observed, for the real build and NOT POC failures. Quoted verbatim where the owner said it:

1. No confirmation pop-up after a probe run: "no conf pop up msg" (the report is only on the Diagnostics tab and in the log).
2. Probe inputs sit on the Diagnostics tab, away from the Scan-tab details: "why did you put it far and not same place"; "one scan for all".
3. Developer wording on the operator-switch-off refusal (3c): "handoff: REFUSED — tier C requested, not supported in this build (no tier-C flow)".
4. The 120 s request TTL is tight for human-paced device testing (it expired once, on the first 4a attempt).

Recorded as one finding in `.claude/remember/findings.md` (#27). Other findings that fall out of the run go there, never into this file as prose.
