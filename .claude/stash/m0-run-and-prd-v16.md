# Session Stash — M0 run + PRD v1.6 (2026-08-29)

Project: zkagent · Owner: hamr · Host: Fedora 44

## Summary

M0 (the riskiest-assumption spike) was run for the first time against real
documents and is complete. PRD went v1.4 → v1.5 (before the run: M0 row
rewritten so it could fail, §6.1 go/no-go table) → v1.6 (after the run:
three-tier disclosure model as shape only, signed challenges,
always-read/conditionally-mint, §1.1 glossary).

All committed and pushed to `main`:
- `eec7d1f` — M0 run + PRD v1.5 + spike
- `a4652fa` — Apache-2.0 LICENSE + NOTICE for the spike
- `a75c921` — PRD v1.6 + glossary

Each push was an admin bypass of `main`'s branch protection (1 review +
linear history) — this is the 3rd–5th bypass on this repo, still unresolved.
Flag every time; never treat prior bypasses as standing consent.

## State of the repo

- `spikes/m0/` — fork of `tananaev/passport-reader` (Kotlin, JMRTD 0.7.18,
  SCUBA 0.0.18, SpongyCastle), cloned 2026-08-29 (upstream last push
  2026-08-01).
- Removed from the fork: google flavour, Firebase, Play Ads/Review,
  `app/google-services.json` (upstream's Firebase keys), `PRIVACY.md`,
  DG2 photo read, `jnbis` dependency.
- Pinned `compileSdk`/`targetSdk` 36.
- Added `M0Probe.kt`: Timeline timings; `Verdict(ok, allowed, reason)` that
  cannot represent `ok:false` + `allowed:false`; `loadMasterList` counting
  declared vs. parsed with `excludeAnchorFor` negative; `passiveAuth` with
  three outcomes; `tryActiveAuth` via doAA/DG15; `deriveCandidates` =
  `HMAC-SHA256(SHA256(field), "example.test")` for `document_number`,
  `optional_data`, `dg1_full`, `dg14_ca_key`, `dg15_aa_key`; `tamperedDg1`.
- Added stage logging in `MainActivity` read path.
- Package name still `com.tananaev.passportreader` — must change before any
  distribution.
- Upstream ships no LICENSE file despite README saying Apache-2.0 — recorded
  in `spikes/m0/NOTICE`.
- Spike is throwaway, never graduated.
- Results doc: `docs/logs/M0-EVIDENCE.md` (11 findings, 277 lines).

## Key decisions

### Device/documents (corrected this session)
- Owner has a Pixel 6a (bluejay), stock Android 17 — final OS for this
  device, security patches end July 2027 (Play Integrity strong-integrity
  grace → ~mid-2028). Google skipped the Aug-2026 patch for Pixel 6/7
  (watch item).
- Documents: owner's US passport (primary) and NL identity card (2021+
  model, has CAN on front top-right). There is NO Dutch passport — an
  earlier assumption was wrong.

### PRD v1.6 decisions (shape only, mechanics deferred to M1/M2)
- **D19 — three tiers**: A anonymous (one boolean, default, open to any
  requester), B pseudonymous (A + domain-scoped zktag, open because
  HMAC(secret, domain) means a site can only compute its own — safety by
  arithmetic), C attributed (identifying booleans, gated to challenge
  issuers whose key the app build pins at tier C; refused not downgraded
  from unpinned keys). Holder sees tier wording before every tap. Operator
  surface = published verb vocabulary (question types we write, never a
  registry of askers) with verbs on/off — "asked" never "captured". Field
  count rejected as the knob.
- **D20 — signed challenges**: issuer public key = identity, pinned per
  build with tier ceiling (`trustedChallengeIssuers: [{pubkey, maxTier}]`);
  challenge = `{nonce, tier, verbs, threshold, max_scan_age, issued_at,
  expires_at, key_id, sig}`; signature not encryption; unsigned accepted at
  A/B, refused at C; supersedes the split-nonce sketch.
- **D21 — always read, conditionally mint**: `chip_auth: passed|absent`
  travels in B/C only, verifier enforces requester's acceptance policy; tier
  A never carries the flag.
- **Q19** parked (freshness floor vs. D10's one-bit rule — a mint date is a
  fingerprint).
- **Q20** resolved in principle by D20 (mechanics at M1: key format,
  encoding, expiry precedence, white-label key shipping).
- **Q21** deferred (how an authority admits a bank to tier C — delegation,
  rung-2-shaped, D18).
- **Q22** (tier-C verb list; exact booleans only, no similarity scores;
  candidates `name_matches`, `nationality_equals`, `document_type_equals`,
  `fresh_under(N)`).
- **§1.1 glossary**: challenge nonce (requester's, single-use, all tiers,
  cannot recognise anyone) / secret (chip-derived, phone-resident, never
  sent, never minted in A) / zktag (pseudonym, B/C only, one domain). One
  line: nonce proves the request is fresh; zktag proves the person is the
  same.

Design is closed for rung 1 (D18). Next step = M1, the verifier core (Node,
`chiproof` package), where D19/D20/D21 get mechanics against tests. Owner
said "M1" is the trigger.

## Findings

M0 results (`docs/logs/M0-EVIDENCE.md`, 11 findings): 4 valid runs (US ×2,
NL ×2), 8 planted negatives, all 8 fired.

- **US passport**: BAC only (no EF.CardAccess); SOD covers DG1, DG2, DG11,
  DG12; NO DG14, NO DG15 (`SELECT EF.DG15` → `6A82`) → no chip authenticity
  at all → mode B clone-replayable; `optional_data` non-empty (unexplained).
- **NL ID card**: PACE; SOD covers DG1, DG2, DG3, DG14, DG15; Chip
  Authentication succeeded, Active Authentication succeeded (EC key,
  SHA256withECDSA) → clone-detectable.
- All candidates byte-identical across rescans per document; no collisions
  across documents. NL rescan is the stronger test (AA/CA inject
  per-session randomness).
- Master list bundled in the fork = BSI German Master List, 588 certs, 116
  countries, US 8, NL 10 — risk #3 retired for both docs; declared==parsed
  on every run.
- Timings measured: clean tap ~2.5 s (US) / ~3.3 s (NL); BAC setup varied
  363 ms → 5,537 ms across taps (alignment).

### Method findings
- (a) Run 1's negative 2 matched the string "United States" which never
  appears in the US CSCA DN ("OU=U.S. Department of State MRTD CA … C=US"),
  excluded nothing, reported a plausible pass — run 1 voided under PRD
  §6.1. Guard now matches the document's real issuer DN and asserts
  excluded>0.
- (b) Upstream `doPassiveAuth` wraps everything in one catch →
  `passiveAuthSuccess=false`, conflating "forged" with "undecidable" (PRD §3
  violation in the wild); carry to M1 tests.

### NOT established
Renewal stability (D9's real question, untestable), mode-A unlinkability
(no attestation in M0), coverage, performance distribution, anything about
attestation/StrongBox/Play Integrity.

## Open items / next steps

- **D9 derivation field**: chip-bound fields defeat cloning but don't exist
  on the US passport → choosing one narrows `acceptedDocuments`;
  `document_number` exists everywhere but rotates at renewal.
- **D19 doc-count question**: one document per app vs. several — recommended
  several allowed, mode-B adopter narrows `acceptedDocuments`.
- Whether mode B requires chip auth.
- `zkagent@0.0.0` npm placeholder still mislicensed MIT (gated by NO-GO #8).
- Branch-protection bypass workflow (unresolved, 3rd–5th bypass on this
  repo).
- **Next step**: M1, the verifier core (Node, `chiproof` package) — D19/D20/
  D21 get mechanics against tests. Owner said "M1" is the trigger.

## Gotchas

- `grep` on this host is ugrep 7.5.0.
- Never write PII (document numbers, DOB, names) anywhere in
  repo/evidence/logs — field names, hashes, verdicts, timings only.
- Commit only when owner explicitly asks.
- `git add -A` sweeps in pre-existing untracked `.claude/remember/` and
  `.claude/stash/` files — unstage them.
- Docs index: `node ~/.claude/commands/docs-builder/docs-builder.cjs
  index-flat` from repo root after any doc edit (it appends to
  `docs/log.md`).
- Owner's global memory file at
  `~/.claude/projects/-home-hamr-PycharmProjects-zkagent/memory/m0-device-and-documents.md`
  records the device/document facts.
- Phone drops off adb when screen locks — keep screen on, replug.
- Shell cwd resets between Bash calls — use absolute paths.

## Recovery commands

Toolchain (rootless):
- Temurin JDK 21 at `~/opt/jdk-21.0.12.1+1` (Fedora 44 only packages JDK
  25/26; `sudo dnf install java-21-openjdk-devel` does NOT exist — don't
  retry).
- Android SDK at `~/Android/Sdk` (platform-tools 37.0.1,
  `platforms;android-36`, `build-tools;36.1.0`; platform 37 is
  preview-channel only).

Build:
```
export JAVA_HOME=~/opt/jdk-21.0.12.1+1 ANDROID_HOME=~/Android/Sdk
cd spikes/m0
./gradlew --no-daemon :app:assembleRegularDebug
```

Install:
```
~/Android/Sdk/platform-tools/adb install -r app/build/outputs/apk/regular/debug/app-regular-debug.apk
```

Capture (run from repo root; extracts only the delimited M0 REPORT block
from logcat; run `adb logcat -c` before each tap):
```
./spikes/m0/capture-report.sh [outfile]
```
