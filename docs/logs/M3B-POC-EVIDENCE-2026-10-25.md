# M3b — opening riskiest-assumption POC (Pixel 6a, 2026-10-25) — PRE-REGISTERED, RESULTS EMPTY

**Status**: pre-registration skeleton for milestones.md §6.9.1 (D109(f)), written BEFORE any device
run. Every device result cell below is empty (`—`) on purpose. The criteria, the planted negatives and
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

| Question about the document | US passport expected | NL ID card expected |
|---|---|---|
| expires more than 3 months from today | — | — |
| expires more than 6 months from today | — | — |
| expires more than 12 months from today | — | — |
| holder is at least 15 / 16 / 18 / 21 / 60 / 65 | — / — / — / — / — / — | — / — / — / — / — / — |

## 6 — Results: criteria 1 and 2 (both documents)

Read back from `adb logcat -d` lines tagged `M3b probe:` (verdicts only) and the Diagnostics-tab report.

| Group (from the `group ...` lines) | US passport `pos_confirmed / neg_excluded / non_vacuous` | NL ID card |
|---|---|---|
| field_reads | — | — |
| expiry | — | — |
| dob_range | — | — |
| document_number | — | — |
| nationality | — | — |
| name | — | — |
| unreadable | — | — |
| integrity | — | — |
| `summary checks / pass / fail / all_groups_non_vacuous` | — | — |

| Per-check "today" answers (observe lines) vs section 5 | US passport | NL ID card |
|---|---|---|
| `expiry_beyond_3mo.today` / `6mo` / `12mo` | — | — |
| `dob_at_least_N.today` for the six ages | — | — |

| Name observations (criterion 2) | US passport | NL ID card |
|---|---|---|
| `mrz_form` | — | — |
| `name_field length_known / at_capacity` | — | — |
| `uses_angle_filler / uses_space_separator / contains_hyphen / all_ascii / has_secondary` | — | — |
| order the typed name matched (`given_first` / `surname_first`) | — | — |
| `name_rule_exercised case / hyphen / accent / angle_filler` | — | — |
| ß / Ø / Æ / apostrophe observed on the real name | — | — |
| `secondary_identifier present` | — | — |

| Read-to-boolean timing (`read_to_boolean_us`, microseconds): min / median / max across checks | US passport | NL ID card |
|---|---|---|
| all checks | — | — |

| Failed checks (id and `actual` only) | US passport | NL ID card |
|---|---|---|
| — | — | — |

## 7 — Results: criteria 3 and 4

Scanner lines read: `M3b tier-C: ...` (gate and answer verdicts) and `M3b: holder setting ...`. Verifier lines read:
the demo's stdout, `tx created`, `request_uri GET`, `verdict` (the third absent means the verifier saw nothing).

| Run | App build | Setting | Request | App verdict line | Verifier `request_uri GET` seen | Verifier `verdict` line | Pass? |
|---|---|---|---|---|---|---|---|
| 4a | ON | OFF | tier C, `127.0.0.1` | — | — | — | — |
| 4b | ON | OFF | tier A, `127.0.0.1` | — | — | — | — |
| 3a | ON | ON | tier C, `127.0.0.1` | — | — | — | — |
| 3b | ON | ON | tier C, `localhost` | — | — | — | — |
| 3c | OFF | ON | tier C, `127.0.0.1` | — | — | — | — |

| Detail | Result |
|---|---|
| 3a presentation non-empty, verdict `ok` / `allowed` / `reason` / `answered=` booleans | — |
| 3a app read-to-boolean time (`M3b tier-C: answered ... read_to_boolean_us`) | — |
| 3b: gate cause in the app log, and `chip_read=false mint=false` | — |
| 4a: gate cause in the app log, and `chip_read=false mint=false` | — |
| 3c: gate cause in the app log | — |
| `localhost` reachable from the phone through `adb reverse` (3b non-vacuity) | — |

## 8 — Verdict (filled after the run)

| Criterion | Pre-registered pass condition | Result |
|---|---|---|
| 1 | every group non-vacuous on BOTH documents, zero failed checks | — |
| 2 | observations recorded on BOTH documents; unobservable edge cases reported as such | — |
| 3 | 3a answered, 3b refused (not_pinned, fetched and verified first), 3c refused (operator switch) | — |
| 4 | 4a refused (holder setting), 3a answered, 4b answered | — |

Findings that fall out of the run go to `.claude/remember/findings.md`, never into this file as prose.
