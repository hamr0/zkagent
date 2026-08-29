# M0 spike — throwaway

**This is not zkagent.** It is a modified copy of someone else's app, used once to
answer the questions in PRD §6 (M0) with measurements instead of argument.
**It is thrown away, never graduated, never shipped** (AGENT_RULES: never ship the POC).

Results: [`docs/logs/M0-EVIDENCE.md`](../../docs/logs/M0-EVIDENCE.md).

## Provenance and licence

Vendored from **[tananaev/passport-reader](https://github.com/tananaev/passport-reader)**
@ `master`, cloned 2026-08-29 (upstream's last push 2026-08-01). Upstream is authored by
Anton Tananaev and stated by its README to be **Apache-2.0**; upstream ships no `LICENSE`
file, so no licence text could be copied here — resolve that before this code is
distributed in any form. Package name and namespace are still upstream's
(`com.tananaev.passportreader`); they must be changed before anything is distributed.

Upstream's own third-party dependencies (JMRTD, SCUBA, SpongyCastle — LGPL/MIT-style)
are unchanged and unvendored; they resolve from Maven.

## What was changed, and why

| Change | Reason |
|---|---|
| `google` product flavour, Firebase analytics + crashlytics, Play Ads/Review **deleted** | FR1 forbids telemetry; a spike that phones home is unacceptable even as a throwaway |
| `app/google-services.json` **deleted** | Upstream's own Firebase project keys. Dead after the flavour was removed, and not ours to carry |
| `PRIVACY.md` **deleted** | Upstream's Play-listing privacy policy; describes their app, not this spike, and would be misleading here |
| **DG2 (facial image) read removed entirely** | Never needed, slowest read, most sensitive object on the chip |
| `jnbis` dependency dropped | Only used for decoding the images we no longer read |
| `compileSdk`/`targetSdk` 37 → 36 | Platform 37 is SDK-preview-channel only; 36 is stable and runs on Android 17 |
| **`M0Probe.kt` added** | The actual instrumentation — see below |
| Stage logging added to the read path | A failed read must say *where* it failed. Added after run 0 produced a silent stop |

## What `M0Probe.kt` measures

- **Timings** at four marks (access established → DG1+SOD read → CA/AA probed → passive auth → derived).
- **`Verdict(ok, allowed, reason)`** — PRD §3's invariant, made unconstructible in the
  wrong shape: `ok:false` cannot carry `allowed:false`. Upstream collapses every failure
  into `false`, which is the bug this exists to avoid (evidence doc, Finding 6).
- **Master-list load** counting certificates *declared* against certificates *parsed*, so a
  silent half-load cannot pass.
- **AA / CA probes** — reports which authenticity mechanism each chip actually supports (Q18).
- **zktag candidates**, one per potentially-stable field, HMAC'd against a fixed test domain
  so D9 is decided on a table rather than an argument. The KDF here is *not* the published
  spec (FR11) — it is a stand-in for comparing field stability.
- **Two planted negatives**: a flipped DG1 byte must produce `allowed:false`, and removing
  the document's own CSCA from the trust list must stop producing `allowed:true`. The
  second one **asserts that it actually excluded a certificate** — the first version matched
  a country string that never appears in the DN, excluded nothing, and reported a pass
  (evidence doc, Finding 5).

## Privacy rules this spike follows

- The MRZ key (document number, date of birth, expiry) is **typed by hand every run**,
  never stored, never hardcoded, never committed.
- Nothing derived from a real chip is written to disk inside the repo.
- Only field *names*, counts, hashes, verdicts and timings are logged — never values.

## Running it

```bash
export JAVA_HOME=~/opt/jdk-21.0.12.1+1 ANDROID_HOME=~/Android/Sdk
./gradlew :app:assembleRegularDebug
~/Android/Sdk/platform-tools/adb install -r app/build/outputs/apk/regular/debug/app-regular-debug.apk
~/Android/Sdk/platform-tools/adb logcat -c        # clear, so the next report is unambiguous
# tap the document, then:
./capture-report.sh
```

`capture-report.sh` extracts only the delimited `M0 REPORT` block, never the whole logcat
buffer.
