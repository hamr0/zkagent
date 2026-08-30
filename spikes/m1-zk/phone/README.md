# M1-zk phone spike: can the Pixel 6a prove these circuits? (2026-08-30)

Goal: measure ZK **proving time and peak memory ON the owner's Pixel 6a** for
the four real-document circuits already proved on desktop (`sig-check/dsc`
RSA-4096, `sig-check/id-data` RSA-2048, `data-check/integrity`, `age`), per
the main `../README.md`. Time-boxed ~2.5h.

**Headline: no phone measurement was obtained.** `adb devices` returned empty
for the entire session (checked repeatedly, including after ~20 minutes of
other work) — the device never appeared over adb. Per instructions, this
report covers what could be done instead: (A) binary-level feasibility
analysis of running `bb` natively on Android, (B) research on the
zkmopro/native-embedding alternative, and (C) a WASM-vs-native ratio measured
on the desktop, as the best available proxy for a phone estimate.

**No PII left the machine.** No witness file was ever pushed to a phone
(there was no connected device to push to), so the "push to
`/data/local/tmp`, then delete" step in the brief never applied. All work
below used the existing desktop `out/`/`vendor/target/` artifacts in place.

## A. Native `bb` on the phone directly — blocked, not run

1. **No device connected.** `adb devices -l` → `List of devices attached`
   (empty) every time it was checked. Cannot push, chmod, or run anything on
   the phone this session.
2. **Binary analysis of bb 5.0.0's own release assets** (from
   `https://api.github.com/repos/AztecProtocol/aztec-packages/releases/tags/v5.0.0`),
   done to know what *would* happen if the device came back:
   - `barretenberg-arm64-linux.tar.gz` (8.3MB) contains a `bb` executable.
     `file`/`readelf` confirm: **dynamically linked against glibc**
     (`NEEDED libc.so.6`, interpreter `/lib/ld-linux-aarch64.so.1`, symbols up
     to `GLIBC_2.34`). Android's bionic userspace has neither that dynamic
     linker path nor `libc.so.6` — **this build will not run under
     `adb shell` as-is** (predicted from the ELF headers, not verified on a
     device since none was available).
   - `barretenberg-static-arm64-android.tar.gz` (16.3MB download) is **not an
     executable at all** — it's `libbb-external.a`, a 66MB static library
     (55,918 symbols via `nm`) exposing a C++ `bb::bbapi` surface
     (`BBApiRequest`, `AvmProve::execute`, `AvmVerify::execute`, full
     UltraHonk/`acir_format` internals). This is the embeddable core meant to
     be linked into an Android app via JNI (the same class of artifact
     zkmopro-style tooling consumes) — not a CLI you can `chmod +x` and run.
   - There is **no plain "bb-for-Android-shell" executable published**
     upstream at v5.0.0.
3. **Static build from source via NDK** — the NDK is present
   (`~/android-sdk/ndk/27.0.12077973`), satisfying the precondition to
   attempt this. **Not attempted.** Building barretenberg (a large C++/CMake
   project) from source for a new target is realistically a multi-hour,
   likely multi-GB effort (full toolchain, dependency fetch, compile) —
   flagged per instruction ("do NOT download >1GB / start a multi-hour build
   without reporting first — STOP and ask") rather than guessed at.

**Fork needing a decision**: is it worth budgeting a multi-hour session to
cross-compile `bb` from source for Android arm64 (bionic/musl-static), given
the NDK is already present? Not started.

## B. zkmopro / noir_rs — researched, not built

- zkmopro (`mopro-ffi`) documents bindings for "Circom, Halo2, and Noir" and
  ships Android target support via `mopro-cli`. Its own docs page (fetched)
  does not confirm UltraHonk specifically, nor give concrete build-time/size
  numbers.
- The `libbb-external.a` found in step A.2 is functionally the same kind of
  artifact a mopro-style integration would link against: a real path exists
  (an NDK-cross-compiled small C++/JNI harness calling
  `bb::bbapi::UltraHonk*::execute`), but it requires reverse-engineering the
  undistributed `bbapi` C++ header (not shipped in the release tarball) and
  successfully linking the full 66MB static lib's transitive symbol
  requirements. Estimated effort: several hours, genuinely uncertain (could
  hit undefined-symbol/ABI issues that add more hours) — **not started**, per
  the same "don't start a multi-hour build without reporting first" rule.

**Fork needing a decision**: pursue the native-JNI-harness path (real
proving-time number, but open-ended build risk) vs. accept a JS/WASM-in-app
path (Approach C below — known-working today, slower) as the production
architecture, at least for an interim milestone.

## C. bb.js WASM timing on desktop — ran, real numbers

`@aztec/bb.js@5.0.0` was already installed in `spikes/m1-zk/run/node_modules`
(same version as native `bb`). By default `Barretenberg.new()` spawns the
native `bb` binary over a Unix socket when one is available on desktop (which
would silently defeat the purpose) — the script explicitly forced
`BackendType.Wasm` to get a genuine WASM measurement. Circuit: NL
`sig-check/id-data` (`tbs_1000/rsa/pkcs/2048/sha256`), same compiled bytecode
and real witness (`nl_witness.gz`) as the desktop-native run in the main
README's section 4.

Script: `/home/hamr/PycharmProjects/zkagent/spikes/m1-zk/phone/wasm_time_iddata.mjs`
(a copy had to be run from `spikes/m1-zk/run/` for Node's ESM resolver to
find `node_modules`; that copy was deleted after the run — the canonical copy
lives under `phone/`). Run with `timeout 280 node --max-old-space-size=4096`.

**Note on a first failed attempt**: an earlier run of the same script hung
for 13+ minutes at near-zero CPU (`ep_poll`, RSS ~540MB, non-progressing) —
killed and re-run. The second run completed cleanly in under 8 seconds;
suspected cause is a one-time CRS cache write race (`~/.bb-crs/*.dat` mtimes
matched the hang's start time) rather than anything about the WASM path
itself, but this was not root-caused given the time-box — flagging as an
observed flakiness, not a confirmed mechanism.

**Verbatim timings (successful run, bb.js's own internal logging + wall-clock)**:

```
generateProof (WASM): 5478.0 ms   (includes on-the-fly VK computation —
                                    bb.js warns "computing verification key
                                    while proving" since no precomputed VK
                                    was passed)
verifyProof   (WASM): 1677.0 ms   (also recomputes VK internally)
api init (wasm compile + CRS load from disk cache): 550.2 ms
TOTAL wall: 7773.8 ms
peak memory (bb.js's own reporting): ~235 MiB
proof size: 14,656 bytes, 2 public inputs (matches native)
verify result: true
```

**WASM vs. native ratio, same machine, same circuit, same witness** (native
numbers from the main `../README.md` section 4, NL id-data row):

| Step | Native (bb 5.0.0, x86_64) | WASM (bb.js 5.0.0, x86_64) | Ratio |
|---|---|---|---|
| Proving (write_vk + prove, apples-to-apples since WASM run computed both together) | 1.18s + 1.27s = 2.45s | 5.478s | **~2.2x slower** |
| Peak RSS during proving | ~202,516 KB (~198 MiB, `bb prove` alone) | ~235 MiB | **~1.2x more** |

This ratio is the deliverable of Approach C: **a JS/WASM in-app path (e.g. a
React Native or WebView-hosted bb.js) should be estimated at roughly 2x the
native proving time and ~1.2x the native peak memory**, measured on identical
x86_64 desktop hardware — not a native-ARM number, and not validated on
arm64 at all. Treat as a rough multiplier, not a phone-specific prediction:
WASM-vs-native ratios are not guaranteed to transfer across architectures
(arm64 WASM JITs and arm64 native codegen do not necessarily scale the same
way relative to each other as x86_64 WASM vs. x86_64 native does).

## Per-circuit table: phone vs. desktop

| Circuit | Desktop native (from main README) | Phone native | Phone WASM proxy (via desktop ratio) |
|---|---|---|---|
| `sig-check/dsc` RSA-4096 (NL, tbs_1000) | prove 4.54s, write_vk 2.68s, ~498MB peak RSS | **not measured — no device** | **not measured** — extrapolating the ~2.2x/~1.2x desktop WASM ratio would suggest ~15.9s / ~600MB if the same ratio held, but this is a guess built on an unverified cross-architecture assumption, not a measurement |
| `sig-check/id-data` RSA-2048 (NL, tbs_1000) | prove 1.27s, write_vk 1.18s, ~203MB peak RSS | **not measured — no device** | **measured (WASM, desktop)**: 5.478s prove (incl. VK), ~235MB peak |
| `data-check/integrity` | prove 0.96s, write_vk 0.47s, ~151MB peak RSS | **not measured — no device** | **not measured** |
| `compare/age/standard` | prove 0.89-0.94s, ~160MB peak RSS (VK shared) | **not measured — no device** | **not measured** |

Only the `id-data` circuit got an actual (WASM, desktop) run per the task's
Approach C scope; the other three circuits' WASM timings were not run this
session (time-boxed) — the ratio row above is the only cross-circuit
evidence available, and applying it to the other three is explicitly an
unverified extrapolation, not a second measurement.

## What was NOT run (explicit)

- **Anything on the actual phone** — no device connected via adb this entire
  session. This is the single biggest gap: Q23 is not answered.
- Building `bb` from source for Android (NDK present, not attempted —
  multi-hour risk, flagged for a decision).
- A native-ARM JNI/mopro-style harness against `libbb-external.a` (undistributed
  header, multi-hour risk, flagged for a decision).
- WASM timing for `sig-check/dsc`, `data-check/integrity`, and `compare/age`
  (only `sig-check/id-data` was run, per Approach C's literal "for the
  id-data circuit" scope).
- Any push of witness/bytecode files to a phone (none was reachable) — so the
  "delete pushed witnesses from `/data/local/tmp`" step has nothing to
  confirm; nothing was ever there.
- Root-causing the one hung WASM run (killed after 13+ min, re-run succeeded).

## Downloaded artifacts / disk

`spikes/m1-zk/phone/dl/` (126MB: `bb` arm64-linux glibc binary + extracted
static libs, gitignored via a new `.gitignore` line
`spikes/m1-zk/phone/dl/`) — kept locally for reference in case the phone
comes back online this session or a follow-up wants to inspect them further;
not committed.

## Forks/decisions needing the coordinator

1. **Q23 is unanswered.** The one number the task asked for (real
   phone-native proving time/memory) was not obtainable this session because
   the Pixel 6a never appeared on `adb devices`. Needs either: (a) the owner
   reconnecting/unlocking the device and a follow-up session re-running this
   exact plan, or (b) a decision to proceed on WASM-ratio estimates alone
   (not recommended given the explicit cross-arch caveat above).
2. **Build-or-not**: cross-compiling `bb` from source for Android (NDK
   available) vs. writing a JNI harness against the official
   `libbb-external.a` vs. accepting the WASM-in-app path's ~2.2x/~1.2x
   overhead as good enough — none attempted, all multi-hour-risk, needs a
   go/no-go before anyone spends the time.
3. **The one hung WASM run** (13+ min idle, killed) is unexplained — flagging
   rather than asserting a root cause it can't back up.
