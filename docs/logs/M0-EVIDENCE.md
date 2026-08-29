# M0 — Evidence

**Status**: **US passport — read twice, both planted negatives fired, VALID.**
**NL identity card — read twice, both planted negatives fired on both runs, VALID.**
No mode-A/mode-B claim is tested here; M0 tests the read, the government-signature check, the
stability of the derivation input, and which authenticity checks each chip supports. Nothing more.

**Rule for this file (PRD v1.5)**: no PII values, ever. Field names, counts, hashes, verdicts
and timings only. Document numbers, names and dates of birth appear nowhere in this repo.

---

## SETUP — toolchain (2026-08-29)

| Component | Version | Note |
|---|---|---|
| Host | Fedora 44 | |
| JDK | Temurin 21.0.12.1 at `~/opt/jdk-21.0.12.1+1` | Fedora 44 packages only JDK 25/26; AGP is well-trodden on 17/21, so a user-local JDK 21 was installed. **No root used** — `sudo dnf install java-21-openjdk-devel` fails on this distro, the package does not exist. |
| Android SDK | `~/Android/Sdk` — platform-tools 37.0.1, platform android-36, build-tools 36.1.0 | |
| Device | Pixel 6a (`bluejay`), stock Android 17, NFC on | Final OS for this device; security patches end 2027-07 |
| Spike | `spikes/m0/` — fork of [tananaev/passport-reader](https://github.com/tananaev/passport-reader) @ master (2026-08-01) | JMRTD 0.7.18, SCUBA 0.0.18, BouncyCastle/SpongyCastle |

**Deviations from upstream, deliberate:**
1. `google` flavour, Firebase (analytics + crashlytics), Play Ads/Review deleted — FR1 forbids telemetry.
2. `compileSdk`/`targetSdk` 37 → 36; platform 37 is preview-channel only. Cosmetic.
3. **DG2 (facial image) read removed entirely** — never needed, slowest read, most sensitive object.
4. `jnbis` (fingerprint-image decoding) dropped with it.

---

## MEASURED — master list (2026-08-29, desk, no chip)

`app/src/main/assets/masterList`, 899,665 bytes (upstream refresh 2026-07-01), parsed on the
desktop with `cryptography` 46.0.4:

| Quantity | Value |
|---|---|
| Certificates parsed | **588** |
| Distinct issuing countries | **116** |
| `C=US` CSCA certificates | **8** |
| `C=NL` CSCA certificates | **10** |

**Finding 1 — the bundled list is the BSI German Master List.** 588 certificates matches BSI's
2026-05-28 publication exactly. The PRD's choice of the BSI list is satisfied by the asset already
in the fork. *To do before any published claim depends on provenance*: fetch the BSI ZIP and diff.

**Finding 2 — PRD risk #3 retired for both M0 documents.** US and NL CSCAs are present in the free
public list. In-app confirmation followed at run time (below): the document signer chained to a US
CSCA in that list.

---

## MEASURED — US passport, two scans (2026-08-29 18:32 and 18:36)

Both runs are separate app processes (pid 19719, then 19981) and separate physical taps.

| | Run 1 | Run 2 |
|---|---|---|
| Access protocol | **BAC** | **BAC** |
| Data groups in SOD | DG1, DG2, DG11, DG12 | identical |
| Chip Authentication (DG14) | **absent** | absent |
| Active Authentication (DG15) | **absent** (`6A82 FILE NOT FOUND`) | absent |
| Master list declared / parsed | 588 / 588, consistent | 588 / 588, consistent |
| Passive auth, genuine | `ok=true allowed=true` | `ok=true allowed=true` |
| NEGATIVE 1 — DG1 byte flipped | `ok=true allowed=false` → **FIRED** | **FIRED** |
| NEGATIVE 2 — issuing CSCA removed | *invalid, see Finding 5* | excluded=8, kept=580 → `ok=true allowed=false` (`CertPathValidatorException`) → **FIRED** |
| Total elapsed | 7,756 ms | 2,531 ms |

**Run 1 is void by PRD §6.1** (a planted negative did not fire). **Run 2 is valid**, and run 1's
chip-level observations are corroborated by run 2 rather than relied on.

### Finding 3 — the derivation input is stable across scans (S1, partial)

All three candidate tags were **byte-identical across the two taps and two app processes**:

| Candidate | Tag (HMAC over `example.test`) |
|---|---|
| `document_number` | `1e8f3d88…0477c559` |
| `optional_data` | `83679423…9d287900` |
| `dg1_full` | `5c8ffe5d…06218b27` |

This is the M0 headline for one document — **and it is the weakest of the M0 results**, because
DG1 is static signed data: any correct read reproduces it. It demonstrates the read is deterministic
and the derivation is not accidentally salted; it does **not** demonstrate anything about renewal
(D9's real question) or across documents. `dg14_ca_key` and `dg15_aa_key` candidates did not exist —
see Finding 4.

**Still open for D9**: `optional_data` returned a non-empty value on a US passport. Whether it is
meaningful, constant filler, or issuer-specific is unknown, and stability across two scans of the
*same* document proves nothing about stability across renewal. Do not pick a derivation field on
this evidence alone.

### Finding 4 — the US passport supports neither AA nor CA (Q18, load-bearing)

The SOD declares hashes for DG1, DG2, DG11 and DG12 only. `SELECT EF.DG15` returns
`6A82 FILE NOT FOUND`; there is no DG14. **The chip therefore offers no challenge-response of any
kind.** Passive authentication proves the *data* is US-signed; nothing proves the *chip* is the
original.

**Consequence, per D14 and Q18** — a cloned or emulated chip carrying a dumped data set would
produce the identical zktag as the genuine document:
- **Mode A is unaffected.** No identifier is emitted, nothing to impersonate, and a clone proves an
  age that was true of the original holder.
- **Mode B on a US passport is clone-replayable.** Uniqueness and blocking are defeated by a clone.
  This is inside the captcha-grade bar the PRD promises, but **it must be stated in the claim**, not
  discovered by an adopter. Whether an EU document differs is the next test.

### Finding 5 — a planted negative silently excluded nothing (method, not chip)

Run 1's negative 2 removed CSCAs whose subject DN contained the literal `"United States"`. The US
CSCA DN is `OU=U.S. Department of State MRTD CA, …, C=US` — the string never appears, so **zero
certificates were excluded** (`kept=588`) and passive auth passed for the honest reason that the
anchor was still present. The guard reported a pass while testing nothing.

**Fixed two ways**: the exclusion now matches the *actual* issuer DN of this document's signer
(country-agnostic, works for any issuer), and the run is marked INVALID unless the exclusion
provably removed at least one certificate. Run 2 shows `excluded=8`.

**Why this matters beyond the spike**: the failure produced a *plausible pass*. An assertion that
silently matches nothing is indistinguishable from an assertion that holds. Carry to M1: every
negative test must assert that its precondition took effect.

### Finding 6 — upstream conflates "no" with "couldn't check"

Upstream `doPassiveAuth()` wraps digest comparison, master-list load, path validation and signature
check in one `catch (e: Exception) { Log.w(...) }`, leaving `passiveAuthSuccess = false`. "Forged"
and "undecidable" become the same value — the failure PRD §3 exists to forbid, in real third-party
code with 451 stars. Replaced by `M0Probe.passiveAuth`, whose `Verdict(ok, allowed, reason)` cannot
represent `ok:false, allowed:false`. **Carry to M1**: cite this in the SDK's own tests.

### Finding 7 — timings, and a 3× variance worth not over-reading

| Mark | Run 1 | Run 2 |
|---|---|---|
| access established (BAC) | 5,537 ms | 363 ms |
| DG1 + SOD read | 6,445 ms | 1,294 ms |
| CA probed | 6,477 ms | 1,320 ms |
| AA probed | 6,523 ms | 1,363 ms |
| passive auth verified | 7,309 ms | 2,125 ms |
| derived | 7,756 ms | 2,531 ms |

**Measured, not guessed.** The honest reading: a clean tap completes in **~2.5 s**, and BAC setup
dominates the variance (363 ms vs 5,537 ms) — almost certainly antenna alignment and retries, not
computation. Verification after the read is cheap and stable: passive auth ~760–790 ms, derivation
~400 ms in both runs. **Two runs is not a distribution**; no percentile claim may be made from this.
Q16 (scan cadence) needs M2 UX data, not these numbers.

---

## MEASURED — NL identity card, one scan (2026-08-29 19:04)

| | US passport (run 2) | NL identity card |
|---|---|---|
| Access protocol | **BAC** | **PACE** |
| Data groups in SOD | DG1, DG2, DG11, DG12 | **DG1, DG2, DG3, DG14, DG15** |
| Chip Authentication (DG14) | absent | **present, succeeded** |
| Active Authentication (DG15) | absent (`6A82`) | **succeeded** (EC key, `SHA256withECDSA`) |
| Master list declared / parsed | 588 / 588 | 588 / 588, consistent |
| Passive auth, genuine | `ok=true allowed=true` | `ok=true allowed=true` |
| NEGATIVE 1 — DG1 byte flipped | FIRED | **FIRED** |
| NEGATIVE 2 — issuing CSCA removed | excluded=8 → FIRED | **excluded=2, kept=586 → FIRED** |
| Candidate fields available | 3 | **5** |
| Total elapsed | 2,531 ms | 3,322 ms |

**Valid run**: both planted negatives fired, and negative 2 provably removed 2 certificates.

### Finding 8 — one code path, two protocols, no per-country logic (D2/Q12)

The same build read a BAC-only US passport and a PACE NL identity card **with no country-specific
code, no configuration change, and no rebuild**. The protocol was chosen by what the chip advertised
(`EF.CardAccess` present ⇒ PACE; absent ⇒ BAC). This is the scalability property the owner asked for,
observed rather than asserted — though on exactly two documents from two issuers, which is not
coverage. PRD's rule stands: **no coverage numbers in any pitch.**

### Finding 9 — the NL card carries the chip-authenticity checks the US passport lacks (Q18)

DG14 **and** DG15 are both present and both protocols succeeded: Chip Authentication completed, and
Active Authentication signed a fresh 8-byte challenge with an EC key the chip never releases. **For
this document, a cloned data set would be detected** — the clone cannot produce the signature.

**Q18 therefore resolves per-document, not per-product**, and the split is stark between two
documents the same person holds:

| | US passport | NL identity card |
|---|---|---|
| Mode A (age bit) | unaffected | unaffected |
| Mode B (pseudonym) | **clone-replayable** — no challenge-response exists | **clone-detectable** via AA/CA |

The design consequence is now evidence-backed: **mode B cannot promise clone-resistance uniformly.**
Either the adopter requires chip authentication and loses every US passport holder (`acceptedDocuments`
narrowing, D14), or it accepts clone-replay and says so. This is a real configuration trade-off for
adopters, not a footnote — and it is the opposite of the intuition that a passport is the "stronger"
document.

### Finding 10 — no candidate collides across the two documents (G3, partial)

All three shared candidate fields produce **different tags for the two documents**; no collisions.
The NL card additionally offers `dg14_ca_key` and `dg15_aa_key`, which the US passport cannot.

| Candidate | US passport | NL identity card |
|---|---|---|
| `document_number` | `1e8f3d88…` | `fa305d88…` |
| `optional_data` | `83679423…` | `46a31810…` |
| `dg1_full` | `5c8ffe5d…` | `37b2a6c4…` |
| `dg14_ca_key` | — (absent) | `47562303…` |
| `dg15_aa_key` | — (absent) | `007677e8…` |

**What this does and does not show.** It shows two documents held by one person yield distinct
derivation inputs — i.e. `k`>1 for this holder, exactly as D14 predicts, and no accidental merging of
the two into one identity. It does **not** show unlinkability, which is a mode-A property about the
whole payload and is measured at M1b, not here.

**A constraint for D9 and FR11 that this exposes**: a derivation field must exist on every accepted
document. `dg14_ca_key`/`dg15_aa_key` are attractive (chip-bound, defeats cloning) but **do not exist
on a US passport**, so choosing one narrows `acceptedDocuments` by construction. Conversely
`document_number` exists everywhere but rotates at renewal. D9 remains open, now with the trade-off
measured rather than theorised.

### Finding 11 — the NL card's candidates are stable across scans, including the chip-bound ones

Second NL scan (19:07, pid 20858, separate tap and process): **all five candidates byte-identical**
to the 19:04 run; still no collision with either US passport run.

| Candidate | NL scan 1 | NL scan 2 |
|---|---|---|
| `document_number` | `fa305d88…` | identical |
| `optional_data` | `46a31810…` | identical |
| `dg1_full` | `37b2a6c4…` | identical |
| `dg14_ca_key` | `47562303…` | identical |
| `dg15_aa_key` | `007677e8…` | identical |

**This test could have failed, and that is why it was worth running.** Active Authentication signs a
fresh random 8-byte challenge on every read, and Chip Authentication performs an ephemeral key
agreement — so both runs contain per-session randomness. Had a candidate been derived from session
material rather than from the chip's static public key, the two runs would differ. They do not: the
derivation reads the *stored public key*, not the session. Timings corroborate that both runs did the
full work (CA ~410 ms, AA ~205 ms in both).

**Scan matrix complete**: 2 documents × 2 scans, 4 valid runs, 8 planted negatives, all 8 fired.

---

## What M0 did and did not establish

**Established (measured, reproduced):**
1. A single build reads both a BAC-only US passport and a PACE NL identity card, protocol chosen by
   the chip, no per-country code (Finding 8).
2. Government signature verification works against a free public all-country list, with the
   declared/parsed count asserted equal on every run (Findings 1, 2).
3. The verifier can say **no** for the two reasons that matter — tampered data and an untrusted
   issuer — and both were proven by guards that were watched firing (Findings 5, 11).
4. The derivation input is deterministic per document and distinct across two documents held by one
   person (Findings 3, 10, 11).
5. Chip authenticity support differs *per document*, not per product, and the split runs opposite to
   intuition (Findings 4, 9).

**NOT established — do not state these anywhere:**
- **Renewal stability.** D9's actual question. No document in hand has been renewed; nothing here
  bears on whether a zktag survives renewal. Untestable with the current documents.
- **Unlinkability / mode A.** No attestation was involved in M0 at all. Mode A remains a design
  intent until M1b measures the whole payload (FR9, Q15).
- **Coverage.** Two documents from two issuers. No coverage claim, no percentage, no country list.
- **Performance distribution.** Four runs. `~2.5–3.3 s` clean-tap total is an observation, not a
  percentile, and BAC setup was seen to vary 15× (363 ms → 5,537 ms).
- **Anything about attestation, StrongBox, or Play Integrity.** Untouched by M0; Q14 is unchanged.

**The riskiest assumption (§7 item 1) is partially retired**: chip data is readable, verifiable
against a public masterlist, and yields the same derivation input on every scan — *on unrenewed
documents*. The renewal leg of that assumption is untouched and remains the open half.

---

## PENDING

- [ ] Explanation of the non-empty `optional_data` on the US passport
- [ ] BSI ZIP fetched directly and diffed against the bundled asset (provenance)
- [ ] Renewal stability — cannot be tested with documents in hand; D9's real question stays open
