---
type: reference
title: zkagent — Install, Test and Reuse Guide
status: stable
sources: [README.md, apps/scanner/README.md, apps/demo/README.md, apps/scanner/app/build.gradle.kts, apps/scanner/gradle/libs.versions.toml, apps/scanner/scripts/operator-json-negative.sh, apps/demo/scripts/tierc-poc-request.mjs, apps/demo/scripts/fake-wallet.mjs, apps/demo/scripts/redirect-proxy.mjs, .github/workflows/ci.yml, CHANGELOG.md, docs/product/customer-guide.md, docs/product/reuse-map.md]
---

# zkagent — Install, Test and Reuse Guide

For someone who clones this repo cold and wants to build the Android scanner, install it on a phone,
run the local test website, run every automated test, walk the manual phone checks, and reuse the
parts. Every command below was read from the repo's own scripts, build files and CI config.
Written 2026-10-28 for v0.9.0 and updated 2026-10-29 for the released v0.9.1 (where a screen string
differs between them, the 0.9.1 wording is given and flagged).

Nothing here needs a Mac, a paid Apple account or Google Play: the project is Android-first and
iOS is a non-goal.

## 0. What you are about to run

| Piece | Where | What it is |
|---|---|---|
| Scanner app | `apps/scanner` | Kotlin Android app, id `com.zkagent.scanner`. Reads the chip over NFC, answers a yes/no question. |
| Demo site | `apps/demo` | Plain Node website that plays the requesting site. Runs on your computer, reached from the phone over USB. |
| `chiproof` | `packages/chiproof` | The verifier library the demo is built on (also on npm). |

The phone reaches the demo at `http://127.0.0.1:8787` through `adb reverse`. Only a **debug** build
of the app may talk to that address (cleartext HTTP to loopback). The **release** build allows no
cleartext at all, so it cannot reach the local demo — see section 8.

## 1. Prerequisites

Developed on Fedora Linux (x86_64). Any Linux or macOS with the tools below should work; Windows is
untested.

| Need | Version, from the repo | Check |
|---|---|---|
| JDK | **17**. `app/build.gradle.kts` sets `sourceCompatibility`/`targetCompatibility` to Java 17 and Kotlin `jvmTarget` 17 | `"$JAVA_HOME/bin/java" -version` |
| Gradle | 9.5.1, supplied by the checked-in wrapper `apps/scanner/gradlew` (do not install Gradle yourself) | `cd apps/scanner && ./gradlew --version` |
| Android Gradle Plugin / Kotlin | 8.13.0 / 2.2.20 (`apps/scanner/gradle/libs.versions.toml`); downloaded by the wrapper build | n/a |
| Android SDK | platform **android-36** (`compileSdk`/`targetSdk` 36, `minSdk` 30 = Android 11 or newer) plus a build-tools release for `apksigner` (the project used 36.1.0) | `ls "$ANDROID_HOME/platforms"` |
| adb | Android platform-tools | `adb --version` |
| Node.js | **>= 20** (`packages/chiproof/package.json` `engines`; CI tests Node 20 and 22). The demo has no `engines` field; it was run on Node 22 | `node -v` |
| npm | the one that ships with Node | `npm -v` |
| Phone | Android 11+ with **NFC**. Developed on a Pixel 6a, Android 17, stock ROM, locked bootloader | n/a |
| A chip document | a passport or ID card with a chip, for the real-document tests. The owner used a US passport and an NL ID card | n/a |

### 1.1 JDK 17 and `JAVA_HOME`

Gradle runs on whatever `java` it finds, and this build needs 17. A newer default JDK (this host's
default is 25) is not the one the project was built with. Point `JAVA_HOME` at a 17 explicitly:

```
# Option A: you already have a JDK 17 installed (Fedora: sudo dnf install java-17-openjdk-devel)
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk

# Option B: reuse the JDK 17 that an earlier Gradle run auto-provisioned
ls ~/.gradle/jdks/                       # e.g. eclipse_adoptium-17-amd64-linux.2
export JAVA_HOME=$HOME/.gradle/jdks/eclipse_adoptium-17-amd64-linux.2

"$JAVA_HOME/bin/java" -version           # must say 17
```

### 1.2 Android SDK and `ANDROID_HOME`

Easiest: install Android Studio once, or the command-line tools, and let it create `~/Android/Sdk`.

```
export ANDROID_HOME=$HOME/Android/Sdk
export PATH=$ANDROID_HOME/platform-tools:$PATH
# only if the command-line tools are installed (cmdline-tools/latest/bin/sdkmanager):
"$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" "platforms;android-36" "platform-tools" "build-tools;36.1.0"
```

Gradle also reads the SDK path from `apps/scanner/local.properties` (`sdk.dir=...`, gitignored,
Android Studio writes it). The `ANDROID_HOME` variable is enough if that file is absent.

### 1.3 Enable USB debugging on the phone

1. Settings > About phone > tap **Build number** seven times (enables Developer options).
2. Settings > System > Developer options > turn on **USB debugging**.
3. Plug the phone in, accept the "Allow USB debugging?" prompt on the phone.
4. Check:

```
adb devices                  # exactly one line ending in "device" (not "unauthorized")
                             # if the list is empty: adb kill-server && adb start-server
adb shell getprop ro.build.version.release
```

Optional, keeps the screen awake while on USB (the project docs use it during sessions):
`adb shell svc power stayon usb`.

### 1.4 NFC

Turn NFC on in the phone's settings (the exact menu path varies by vendor). The chip reader is at the
back of the phone: hold the document flat against the back and move it slowly until the app reacts.

## 2. Get the code

```
git clone https://github.com/hamr0/zkagent.git
cd zkagent
```

Real passport material must never enter git. `fixtures/real/`, `*.mrz`, `*.dg1`, `*.sod` and
`secrets/` are gitignored; keep any such files there and nowhere else.

## 3. Install route (i): the signed release APK

Use this to try the shipped app. It does **not** work against the local demo (section 8); it carries
the tier-C-off Settings check (section 9, scenario P1) and any real HTTPS site you pin yourself.

1. Download `app-regular-release.apk` from the GitHub Releases page of the repo (`v0.9.1`):
   https://github.com/hamr0/zkagent/releases
2. Verify it before installing. The signer certificate below is from the release notes and `CHANGELOG.md`; the APK hash is in the v0.9.1 release notes:

```
sha256sum app-regular-release.apk
# expect the sha256 given in the v0.9.1 release notes (the v0.9.0 APK was 3a2576d1df8e424974284b0c3d2a2e17c78eac248bd18bafb82aac4739c080ab)

"$ANDROID_HOME"/build-tools/36.1.0/apksigner verify --print-certs app-regular-release.apk
# expect "Signer #1 certificate SHA-256 digest:" 1f6bceae0ffe9c2b326f2aab2202f0bdf3df7e5bdd8fac50aba2e1d318407264
# (signer CN=zkagent showcase). Use whichever build-tools directory you have.

"$ANDROID_HOME"/build-tools/36.1.0/aapt2 dump badging app-regular-release.apk | head -1
# expect package: name='com.zkagent.scanner' versionCode='9' versionName='0.9.1'
```

3. Sideload:

```
adb install app-regular-release.apk       # exit 0 and "Success"
adb shell dumpsys package com.zkagent.scanner | grep -E "versionName|versionCode="
```

If you get `INSTALL_FAILED_UPDATE_INCOMPATIBLE`, a build signed by a different key is already
installed: `adb uninstall com.zkagent.scanner` (this wipes app data) and install again.

## 4. Install route (ii): build from source

All Gradle commands run from `apps/scanner`. Set the environment once per shell:

```
export JAVA_HOME=$HOME/.gradle/jdks/eclipse_adoptium-17-amd64-linux.2   # or your own JDK 17
export ANDROID_HOME=$HOME/Android/Sdk
cd apps/scanner
```

The first build downloads Gradle, AGP and dependencies, so it needs network. After that,
`--offline` works and is faster.

### 4.1 Debug build (this is the one that talks to the local demo)

```
./gradlew :app:assembleRegularDebug
# output: app/build/outputs/apk/regular/debug/app-regular-debug.apk
adb install -r app/build/outputs/apk/regular/debug/app-regular-debug.apk
```

Debug builds are signed with the automatic Android debug key and allow cleartext HTTP only to
`127.0.0.1`, `localhost` and `10.0.2.2`.

### 4.2 Release build with your own keystore

Release builds are signed only if `KEYSTORE_FILE` is set; without it you get
`app-regular-release-unsigned.apk`, which Android will not install. The four variables
(read by `signingConfigs` in `app/build.gradle.kts`):

| Variable | Meaning |
|---|---|
| `KEYSTORE_FILE` | path to your `.p12` / `.jks` keystore |
| `KEYSTORE_PASSWORD` | keystore password |
| `KEY_ALIAS` | key alias inside it |
| `KEY_PASSWORD` | key password |

Make a keystore once (interactive prompts; never pass a password on the command line), keep it
under the gitignored `secrets/`, and back it up offline. The full recipe, including reading the
certificate digest, is `docs/product/customer-guide.md` section 7.1:

```
mkdir -p ../../secrets
keytool -genkeypair -v -keystore ../../secrets/mine.p12 -storetype PKCS12 \
  -alias mine -keyalg EC -groupname secp256r1 -sigalg SHA256withECDSA \
  -validity 10950 -dname "CN=my operator"
```

Build and verify:

```
KEYSTORE_FILE=$PWD/../../secrets/mine.p12 KEYSTORE_PASSWORD='...' KEY_ALIAS=mine KEY_PASSWORD='...' \
  ./gradlew :app:assembleRegularRelease
# output: app/build/outputs/apk/regular/release/app-regular-release.apk
"$ANDROID_HOME"/build-tools/36.1.0/apksigner verify --print-certs app/build/outputs/apk/regular/release/app-regular-release.apk
adb install -r app/build/outputs/apk/regular/release/app-regular-release.apk
```

### 4.3 `operator.json`: who the app will talk to

`apps/scanner/operator.json` is read and validated by Gradle at configure time. Nothing ships inside
the APK except what it turns into build constants, and a bad file fails the build. The committed file
is the showcase config: `"verifiers": "any"` (age verification open to any site), tier C off. What the
switches mean, the full schema and the twelve validation rules are in
`docs/product/customer-guide.md` section 7.2 and `docs/wiki/milestones.md` section 6.7; do not copy
them here. The ones you will touch:

- `verifiers`: `"any"`, or an exact-hostname allowlist (`["127.0.0.1", "localhost"]`).
- `tier_c.enabled` plus `tier_c_verifiers`: tier C (yes/no checks on document fields) is reachable
  only when the switch is on **and** the site's exact hostname is in this list **and** the holder
  turns on "Allow additional verifications" in the app's Settings (off by default).

To build a variant **without editing the committed file**, point Gradle at another file with
`-PoperatorJson=<path>` (absolute, or relative to `apps/scanner/`):

```
# tier C ON, only 127.0.0.1 pinned (for local tier-C tests):
./gradlew :app:assembleRegularDebug -PoperatorJson=poc/operator.tierc-on.json
# tier C OFF, 127.0.0.1 and localhost allowed:
./gradlew :app:assembleRegularDebug -PoperatorJson=poc/operator.tierc-off.json
```

`git status --short` stays clean after either; the committed `operator.json` is untouched.

### 4.4 Switching between debug and release

Debug and release are signed by different keys, so Android refuses an in-place switch. Always:

```
adb uninstall com.zkagent.scanner      # "Failure [not installed]" is fine
adb install <the apk>
```

This wipes the app's data (holder setting back to OFF, age locks cleared, Log empty), which is also
the clean start the phone tests want. The version/commit footer at the bottom of the scan screen
shows which build is running (for example `0.9.1 850f96f`; a `-dirty` suffix means built from a
tree with local changes).

## 5. Run the local test site (apps/demo)

```
cd apps/demo
npm install                       # installs chiproof (from ../../packages/chiproof) and qrcode
npm start                         # = node server.mjs, listens on http://127.0.0.1:8787
```

In another terminal, with the phone plugged in and the **debug** app installed:

```
adb reverse tcp:8787 tcp:8787     # phone's 127.0.0.1:8787 now reaches this computer
adb reverse --list                # expect: tcp:8787 tcp:8787
```

Open `http://127.0.0.1:8787` in the **phone's** Chrome. Tap "Prove you're over 18" (tier A) or
"Prove you're a unique adult human" (tier B), then tap the link shown (or scan the QR with a second
phone's ordinary camera app). The scanner opens, asks for the document fields, you tap Scan and hold
the document to the back of the phone, and the page updates by itself. The only supported origin is
`http://127.0.0.1:8787`; a LAN IP or self-signed HTTPS is refused by the app by design.

### 5.1 Environment variables

All optional. Full table with meanings: `apps/demo/README.md` "Config (env)".

| Variable | Default | Use |
|---|---|---|
| `PORT` | `8787` | listen port |
| `BIND_HOST` | `127.0.0.1` | listen address |
| `DEMO_STORE_PATH` | `apps/demo/data/store.json` | the persistent store (nonces, zktag dedupe). Delete the file, with the server stopped, to reset |
| `TIERC_POC_ALLOW_RAW` | unset | set to `1` to let `tierc-poc-request.mjs --raw` send unvalidated predicates (device tests of the app's whole-request refusal). Test hook only |
| `LINK_SCHEME`, `APP_LINK_BASE`, `SCOPE_DOMAIN`, `PUBLIC_SCHEME` | `av`, placeholder, `127.0.0.1`, `http` | link shape and origin; leave alone for the local recipe |
| `CHALLENGE_SECRET`, `ATTESTER_*`, `REQUEST_SIGNER_*` | dev-only baked-in keys | a real deployment supplies its own |

A throwaway run with a fresh store on another port:

```
PORT=8788 DEMO_STORE_PATH=/tmp/zkagent-demo-store.json node server.mjs
```

### 5.2 Driving the demo without a browser: `tierc-poc-request.mjs`

Creates one transaction on the running demo, prints the `av://` link, can launch it on the phone,
and can wait for the verdict. Run it from `apps/demo`. Never prints a document value, name or number.

```
node scripts/tierc-poc-request.mjs --tier C --host 127.0.0.1 --months 3 --ttl 300 --launch --wait 60
node scripts/tierc-poc-request.mjs --tier A --host 127.0.0.1 --ttl 300 --launch
```

| Flag | Meaning |
|---|---|
| `--tier A\|B\|C` | request tier (default `C`) |
| `--host H` | the Host the link points at; decides the request origin (default `127.0.0.1`). Tier C is accepted on loopback only |
| `--port N` | demo port to talk to (default `8787`; point it at the proxy or another demo) |
| `--ttl SECONDS` | request lifetime (default 120, the verifier clamps at 600). Use 300 for human-paced scans |
| `--launch` | force-stops the app, then `adb shell am start -a android.intent.action.VIEW -d '<link>' -p com.zkagent.scanner`; prints the exact commands. `ADB=/path/adb` picks another adb |
| `--wait SECONDS` | poll until the app answers; prints `RESULT status=done ok=.. allowed=.. reason=.. answered=.. refused=..`, or `status=expired` / `status=pending` |
| `--months 3\|6\|12` | tier C: expiry bucket (this is the default question if no other is given) |
| `--age 15\|16\|18\|21\|60\|65` | tier C: date-of-birth range; a comma list `18,21` asks two ages |
| `--nationality-is CODE` | tier C: "is the nationality X?" (ISO alpha-3 or an ICAO special code such as `D`) |
| `--nationality-not-in A,B` | tier C: "is it none of these?" (at most 10 codes) |
| `--given-names "..." --surname "..."` | tier C: name match (both required together) |
| `--doc-number "..."` | tier C: document-number match |
| `--raw '<json array>'` | tier C: send these predicates unvalidated; needs the demo started with `TIERC_POC_ALLOW_RAW=1` |

Put names and numbers in the environment (`TIERC_GIVEN_NAMES`, `TIERC_SURNAME`, `TIERC_DOC_NUMBER`)
instead of flags, to keep them out of shell history and the process list.

The site's own state is the source of truth, not the phone. For any transaction id the script prints:

```
curl -s http://127.0.0.1:8787/ui/presentations/<transactionId>        # "status":"pending" | "done" | "expired"
```

### 5.3 Phone-free runs: `fake-wallet.mjs`

Plays the phone headlessly (fetch request, check its signature, build a presentation, POST, poll):

```
cd apps/demo
node scripts/fake-wallet.mjs --base http://127.0.0.1:8787 --tier A --mode valid
node scripts/fake-wallet.mjs --tier B --mode wrongkey
```

Tier A modes: `valid` (default), `tamper`, `expired`. Tier B modes: `valid`, `wrongkey`, `missing`,
`firstsight`. See the header of the script for what each proves. Tier-B zktags here are synthetic.

### 5.4 The redirect proxy: `redirect-proxy.mjs`

A test helper (not product) for the redirect checks in section 9, scenario P7. It listens on the port the
phone reaches and forwards to the real demo on another port, except that it answers the app's
`POST /wallet/direct_post` (and with `--redirect-get` also `GET /wallet/request.jwt/*`) with a
3xx redirect. It logs lengths only.

```
cd apps/demo
PORT=8788 DEMO_STORE_PATH=/tmp/zkagent-demo-8788.json TIERC_POC_ALLOW_RAW=1 node server.mjs &
node scripts/redirect-proxy.mjs --listen 8787 --upstream 8788 --code 302 [--landing /redirect-landing] [--redirect-get]
```

A correct app never makes the proxy print a `LANDING` line (that line means the client followed the
redirect). Details: `apps/demo/README.md` "Redirect proxy".

## 6. Run the automated tests

None of these needs a phone.

### 6.1 Scanner unit tests

```
export JAVA_HOME=...  ANDROID_HOME=...        # section 4
cd apps/scanner
./gradlew :app:testRegularDebugUnitTest --offline ; echo "gradle exit=$?"
```

Drop `--offline` on the very first run. Do not run this at the same time as another Gradle task, or
as the operator-json script below. **Count from the JUnit XML, not from the console summary**:

```
python3 - <<'EOF'
import glob, xml.etree.ElementTree as E
t = f = e = s = n = 0
for p in glob.glob('app/build/test-results/testRegularDebugUnitTest/TEST-*.xml'):
    r = E.parse(p).getroot(); n += 1
    t += int(r.get('tests')); f += int(r.get('failures')); e += int(r.get('errors')); s += int(r.get('skipped'))
print(f"files={n} tests={t} failures={f} errors={e} skipped={s}")
EOF
```

Expect `failures=0 errors=0 skipped=0`. Delete `app/build/test-results/` first if you want the count to
cover only this run (stale XML from earlier runs is summed too).

### 6.2 chiproof tests and typecheck

CI (`.github/workflows/ci.yml`) runs exactly this on Node 20 and 22:

```
cd packages/chiproof
npm ci
npm run typecheck          # tsc --noEmit
npm run build:types        # tsc (writes the generated .d.ts; not committed)
npm test                   # node --test
```

Read the `# tests / # pass / # fail` lines at the end of `npm test`.

### 6.3 Demo tests

```
cd apps/demo
npm install
npm test                   # node --test tests/*.test.mjs
```

Same `# pass / # fail` lines. The tests start the server in-process (`startServer`), so they do not need `npm start` to be running.

### 6.4 `operator-json-negative.sh` (slow, rewrites a tracked file)

```
apps/scanner/scripts/operator-json-negative.sh
```

Builds `:app:assembleRegularDebug` against deliberately bad `operator.json` variants and positive
ones, expecting each bad one to fail naming its rule. It takes about 3 minutes.
**WARNING: it rewrites the tracked `apps/scanner/operator.json` while it runs** (and restores it on
exit, even on failure). Never run it alongside another Gradle build, and do not interrupt it. Afterwards:

```
git status --short                       # must not list apps/scanner/operator.json
```

A good run ends with `all cases passed` and every case line starting `PASS` (33 `PASS`, 0 `FAIL`
when last counted). If it was killed mid-run, restore with `git checkout -- apps/scanner/operator.json`.

## 7. Phone test: setup

The manual checks prove what unit tests cannot: that the real screens say the right thing and that
the **site's own state** agrees with what the phone claims. Do them in this order; set up once.

### 7.1 Rules for the person holding the phone

- The app blocks screenshots. Do **not** work around that. Never take a screenshot, `screencap`,
  `uiautomator dump` or an accessibility snapshot of any screen showing document fields: such a
  screen can leak into an AI agent's transcript or your notes.
- The tester reads the screen aloud; the operator cross-checks logcat and the site. A log line alone
  is not a pass for a colour or a wording.
- Never commit real document data. Record field **names**, verdicts and timings only.
- Present step lists as actions only; read the expectations after the step, not during.

### 7.2 Helper functions

Bash state does not persist between tool calls in some setups, so put this in a file and `source`
it at the start of each shell. Adjust `R` and the JDK path.

```
# ~/zkagent-phone.sh
R=$HOME/PycharmProjects/zkagent                         # your clone
W=${TMPDIR:-/tmp}/zkagent-phone-check                   # scratch dir for logs; safe to delete afterwards
PKG=com.zkagent.scanner
export JAVA_HOME=$HOME/.gradle/jdks/eclipse_adoptium-17-amd64-linux.2 ANDROID_HOME=$HOME/Android/Sdk
mkdir -p "$W"
cap_start() {   # $1 = label. Starts a logcat capture and proves it is alive; the last number printed must be 1.
  adb logcat -c
  adb logcat -v time -s MainActivity RequestTrust HandoffClient AndroidRuntime > "$W/logcat-$1.txt" &
  echo $! > "$W/logcat.pid"; sleep 1
  adb shell log -t MainActivity "capture-check-$1"; sleep 1
  grep -c "capture-check-$1" "$W/logcat-$1.txt"
}
cap_stop() { kill "$(cat "$W/logcat.pid")"; }
demo_start() {  # $1 = port (default 8787), $2 = label. Fresh store each run. Raw hook ON.
  ( cd "$R/apps/demo" && PORT="${1:-8787}" TIERC_POC_ALLOW_RAW=1 DEMO_STORE_PATH="$W/store-$2.json" node server.mjs > "$W/demo-$2.log" 2>&1 & echo $! > "$W/demo.pid" )
  sleep 2; curl -s -o /dev/null -w '%{http_code}\n' "http://127.0.0.1:${1:-8787}/"   # expect 200
}
demo_stop() { kill "$(cat "$W/demo.pid")"; }   # exact pid, never by process name
```

The app writes these logcat tags: `MainActivity`, `RequestTrust`, `HandoffClient`. The "Last scan"
line on screen is **not** logged; only a human reading the screen can confirm it.

Kill background processes by the exact pid in the `.pid` files, never with `pkill node` or similar
(a name match can take out your own shell).

### 7.3 Build once, up front

The app under test for phase A is the released APK; for phase B a debug tier-C-on build. Build it
before starting anything else (no other Gradle task may run during the tests):

```
source ~/zkagent-phone.sh
cd $R/apps/scanner && ./gradlew :app:assembleRegularDebug -PoperatorJson=poc/operator.tierc-on.json --offline ; echo "gradle exit=$?"
cp app/build/outputs/apk/regular/debug/app-regular-debug.apk $W/app-regular-debug-tierc-on.apk
sha256sum $W/app-regular-debug-tierc-on.apk ; git -C $R status --short      # operator.json must not be listed
```

Release-build evidence and debug-build evidence are different evidence: scenarios P2 to P7 can only
run on debug (they need the local demo), so they say nothing about the released APK beyond what P1
covers.

### 7.4 Site-side check of the D118 rule (no phone)

D118: a tier-C request may contain at most one `nationality_is` and one `nationality_not_in`. The
verifier library refuses more at creation:

```
demo_start 8787 pre
curl -s -w ' http=%{http_code}\n' -X POST -H 'content-type: application/json' \
  -d '{"mode":"C","predicates":[{"name":"nationality_is","code":"USA"},{"name":"nationality_is","code":"NLD"}]}' \
  http://127.0.0.1:8787/ui/presentations
# expect {"error":"nationality_asked_more_than_once"} http=400   (same with two nationality_not_in)
curl -s -w ' http=%{http_code}\n' -X POST -H 'content-type: application/json' \
  -d '{"mode":"C","predicates":[{"name":"nationality_is","code":"USA"},{"name":"nationality_not_in","codes":["CUB","RUS"]}]}' \
  http://127.0.0.1:8787/ui/presentations
# expect http=201
curl -s -w ' http=%{http_code}\n' -X POST -H 'content-type: application/json' \
  -d '{"mode":"C","predicates":[{"name":"document_number_is","number":"AB1"},{"name":"document_number_is","number":"AB2"}]}' \
  http://127.0.0.1:8787/ui/presentations
# expect {"error":"document_number_asked_more_than_once"} http=400   (D119: one document-number test per request)
demo_stop
```

## 8. Troubleshooting, up front

| Symptom | Cause and fix |
|---|---|
| App says it cannot reach the site, or nothing reaches the demo, on the **release** APK | Expected. Release has no cleartext exception (not even for `127.0.0.1`). Use a debug build for the local demo, or a real HTTPS origin (`apps/demo/deploy/README.md`) |
| `INSTALL_FAILED_UPDATE_INCOMPATIBLE` | A build signed by another key is installed. `adb uninstall com.zkagent.scanner`, then install. This wipes app data |
| Page loads on the computer but not in the phone's Chrome | `adb reverse` was lost (any USB replug or `adb kill-server` drops it). Redo `adb reverse tcp:8787 tcp:8787` and check `adb reverse --list` |
| Every scan says "request could not be verified" or "The website did not accept the answer" on a real site | The site's `request_uri` or `response_uri` sits behind a redirect (http to https, apex to www, trailing slash). The app follows no redirects (#52); serve both at their final URL |
| `Error: listen EADDRINUSE ... 8787` | Another demo (or the proxy) is on 8787. Find it with `ss -ltnp 'sport = :8787'` and stop that exact pid, or start the demo with another `PORT` |
| `adb devices` empty or `unauthorized` | Reconnect, accept the debugging prompt, `adb kill-server && adb start-server` |
| Gradle: `Unsupported class file major version` or a Java error | Wrong JDK. Check `"$JAVA_HOME/bin/java" -version` says 17 |
| Gradle: `SDK location not found` | Set `ANDROID_HOME`, or create `apps/scanner/local.properties` with `sdk.dir=/path/to/Sdk` |
| Gradle `--offline` fails with a missing artifact | Run once online first so the caches fill |
| `adb shell am start` link opens a blank screen or only half a link | The `&` in the link was eaten by the shell. Single-quote the whole link, force-stop first, and always pass `-p com.zkagent.scanner`. `--launch` already does all three |
| A relaunch shows the old question | Force-stop the app first: `adb shell am force-stop com.zkagent.scanner` |
| Request refused as expired | The request lifetime (default 120 s) ran out; pass `--ttl 300` and scan promptly |
| The app says "Sent, but the site rejected the response (HTTP 409)" on a first try | That link was already answered (a second scan of the same link). Make a new request |
| `grep` with bounded repetition (`{n,m}`) hangs on some hosts | Fedora here shipped ugrep; parse structured data with `python3 -I` instead |

## 9. Phone test: scenarios

Common to every scenario: start logcat capture (`cap_start <label>`), force-stop the app, make the
request only after the tester says "go" (requests expire), and stop the capture afterwards. The
`--launch` flag of `tierc-poc-request.mjs` force-stops the app and runs the quoted `am start`.

By hand, the launch is:

```
adb shell am force-stop com.zkagent.scanner
adb shell am start -a android.intent.action.VIEW -d '<av:// link, whole link in single quotes>' -p com.zkagent.scanner
```

Run order that minimises reinstalls: P1 on the released APK, then install debug, then P2 to P5, then P7 (there is no P6).

| # | Scenario | Build | Needs |
|---|---|---|---|
| P1 | Tier-C-off Settings screen | release | phone only |
| P2 | Green delivered line, tier A | debug tier-C-on | demo, a real document |
| P3 | Post-read refusal (HTTP 409) | debug tier-C-on | demo, a real document |
| P4 | Tier-C refusal on arrival (holder setting OFF) | debug tier-C-on | demo |
| P5 | D118: two of a kind refused, one of each answered | debug tier-C-on | demo with the raw hook, a real document for the control |
| P7 | Redirects are not followed | debug tier-C-on | demo plus `redirect-proxy.mjs` |

### Phase A: released APK

```
source ~/zkagent-phone.sh
adb uninstall $PKG
adb install /path/to/app-regular-release.apk ; echo "install exit=$?"
adb shell dumpsys package $PKG | grep -E "versionName|versionCode=|DEBUGGABLE"    # 0.9.1, 9, no DEBUGGABLE
cap_start A
adb shell am force-stop $PKG
```

#### P1. Tier-C-off Settings screen

Owner actions
1. Open the app from the launcher.
2. Read aloud the footer version text at the bottom of the screen.
3. Tap the right arrow in the top bar (Log).
4. Tap the right arrow again (Settings).
5. Read aloud everything on the Settings page, top to bottom. Say whether any switch or toggle is visible.
6. Scroll to the bottom. Tap "Diagnostics". Read aloud what appears. Tap "Diagnostics" again.
7. Tap the left arrow twice.

Expected
- Footer: `0.9.1` and a commit stamp (`850f96f` for the release; no `-dirty`).
- Top bar: no tab strip; title "Settings" centred; no right arrow on the Settings page.
- The text reads exactly: "Additional verifications are not available in this version of the app."
- No switch labelled "Allow additional verifications" anywhere.
- Diagnostics is collapsed at the bottom; opening it shows two probe buttons; tapping again folds it.
- Step 7 returns to the Scan page.
- `$W/logcat-A.txt` has no `AndroidRuntime` lines.

Pass: the exact sentence is read, no switch visible, version reads 0.9.1, no crash.

(An optional TalkBack pass over the three pages is in the project history; it was not run for 0.9.0 or 0.9.1
and stays disclosed as not checked.)

`cap_stop` when done.

### Phase B: debug build, tier C on, `127.0.0.1` pinned

```
source ~/zkagent-phone.sh
adb uninstall $PKG
adb install $W/app-regular-debug-tierc-on.apk ; echo "install exit=$?"
adb shell dumpsys package $PKG | grep -E "versionName|versionCode=|DEBUGGABLE"   # DEBUGGABLE present
adb reverse tcp:8787 tcp:8787 ; adb reverse --list                               # tcp:8787 tcp:8787
demo_start 8787 B                                                                # prints 200
```

Fresh install state: the holder setting "Allow additional verifications" is OFF, no age locks, empty Log.

#### P2. Green delivered line (tier A, real document)

`cap_start 2`. Wait for "go", then:

```
cd $R/apps/demo && node scripts/tierc-poc-request.mjs --tier A --host 127.0.0.1 --ttl 300 --launch | tee $W/req-2.out
```

Note the `transaction=` id as TX. Logcat should show `pendingHandoff captured from av:// intent`; the demo log
one `request_uri GET transactionId=TX`. Tell the tester "proceed".

Owner actions
1. Read aloud the question in the navy box.
2. Type the document number, date of birth and expiry date into the three fields.
3. Tap the big scan button.
4. Hold the document to the back of the phone until the app reacts.
5. Read aloud the popup text. Tap OK.
6. Read aloud the line directly under the scan button, and say what colour it is.

Expected on screen
- Question: "This website asks if you are over 18".
- Popup (not red): "Delivered — the website shows the result."
- Last-scan line, teal/green with a check: "Last scan: 127.0.0.1:8787, ... delivered ✓" ("over 18: true" may precede "delivered").

Expected logcat and site
- Logcat: `direct_post -> http://127.0.0.1:8787/wallet/direct_post`, `http_status=200`, `terminal outcome dialog shown: Delivered`.
- Demo log: `verdict transactionId=TX tier=A threshold=18 ok=true allowed=true`.
- `curl -s http://127.0.0.1:8787/ui/presentations/TX` shows `"status":"done"` and `allowed:true`.

Pass: wording AND green AND "delivered ✓" AND the site shows `done`. A green line the site does not back is a fail.

#### P3. Post-read refusal: the site says "already used" (HTTP 409)

Make the site mark the transaction done after the app has fetched the request but before the app
posts its answer, by posting an undecodable `vp_token` for that request's `state`. The app's real
answer then gets HTTP 409 `already_responded`.

`cap_start 3`. After "go":

```
cd $R/apps/demo && node scripts/tierc-poc-request.mjs --tier A --host 127.0.0.1 --ttl 300 --launch | tee $W/req-3.out
TX3=$(sed -n 's/^transaction=\([^ ]*\) .*/\1/p' $W/req-3.out)
RID=$(sed -n "s/.*request\.jwt%2F\([A-Za-z0-9_-]*\).*/\1/p" $W/req-3.out)
# wait until $W/demo-B.log shows: request_uri GET transactionId=$TX3   (the app has the request)
curl -s -w ' http=%{http_code}\n' -X POST -d "state=$RID&vp_token=AAAA" http://127.0.0.1:8787/wallet/direct_post   # expect {"accepted":true} http=200
curl -s http://127.0.0.1:8787/ui/presentations/$TX3                                                              # expect "status":"done" BEFORE the tester scans
```

Only then tell the tester "proceed".

Owner actions
1. Type the document number, date of birth and expiry date into the three fields.
2. Tap the big scan button.
3. Hold the document to the back of the phone until the app reacts.
4. Read aloud the popup text. Tap OK.
5. Read aloud the line under the scan button, and its colour.
6. Tap the right arrow (Log). Read aloud the title and result line of the newest entry.

Expected on screen
- Red popup: "This link was already used — reopen the link from the site."
- Last-scan line RED with a cross, naming the site: "Last scan: 127.0.0.1:8787, ... refused ✗". It must not be grey "No scans yet." and must not say "delivered".
- Log newest entry: "Sent, but the site rejected the response (HTTP 409)".

Expected logcat and site
- Logcat: the chip read stages, then `handoff direct_post response NON-2xx http_status=409`, then `terminal outcome dialog shown: This link was already used`.
- Demo log: only the poison's verdict line (`reason=vp_token_undecodable`), none from the app's own post.

Pass: red refused line with the site name, the 409 popup, and logcat 409 after a completed read.

#### P4. Tier-C refusal on arrival (holder setting OFF)

The setting is OFF on a fresh install, so a valid tier-C request is refused before any read.
`cap_start 4`. After "go":

```
cd $R/apps/demo && node scripts/tierc-poc-request.mjs --tier C --host 127.0.0.1 --months 3 --ttl 300 --launch --wait 30 | tee $W/req-4.out
```

Owner actions
1. Read aloud the popup text. Tap OK.
2. Read aloud the line under the scan button, and its colour.
3. Tap the right arrow twice to reach Settings. Read aloud the switch and the text under it.
4. Turn "Allow additional verifications" ON. Return to the Scan page.

Expected on screen
- Red popup: "Additional verifications are turned off. You can turn them on in Settings. Nothing was read. The website was only told that the request was refused."
- Last-scan line red: "Last: website request refused ✗".
- Settings shows the switch "Allow additional verifications" (and a three-part help text) in this build.

Expected logcat and site
- Logcat: `REFUSED on arrival (D112), before any read or mint — cause=holder_setting_off`, `generic refusal posted cause=holder_setting_off http_status=200`.
- Helper: `RESULT status=done ok=true allowed=false reason=refused_by_holder ... refused=true`; the demo log has the matching `verdict ... tier=C ... refused=true`.

The holder setting is now ON; P5 needs it.

#### P5. D118: two of a kind refused, one of each answered

The exact reason (`nationality_asked_more_than_once`) is in logcat and, since the 0.9.1 fix (#53), in
the Log page entry in plain words. The popup stays one generic message, and the website is only ever
told a generic "refused".

`cap_start 5`. Holder setting ON. Each request waits for "go".

**5a. Two `nationality_is`**

```
cd $R/apps/demo && node scripts/tierc-poc-request.mjs --tier C --host 127.0.0.1 --ttl 300 \
  --raw '[{"name":"nationality_is","code":"USA"},{"name":"nationality_is","code":"NLD"}]' --launch --wait 40 | tee $W/req-5a.out
```

Owner actions
1. Read aloud the popup text. Tap OK.
2. Read aloud the line under the scan button, and its colour.
3. Tap the right arrow (Log). Read aloud the title and result line of the newest entry.

Expected on screen
- Red popup: "This website asked something this app does not answer. Nothing was read. The website was only told that the request was refused."
- Last-scan line red: "Last: website request refused ✗".
- Log newest entry (0.9.1 and later): "Refused: the website asked something this app does not answer (nationality was asked more than once). Refusal sent to the website (HTTP 200)." On 0.9.0 the parenthesis is absent.
- No document-fields prompt, no card prompt.

Expected logcat and site
- Logcat: `REFUSED on arrival (D112), before any read or mint — cause=request_not_answerable reason=nationality_asked_more_than_once chip_read=false mint=false`; `generic refusal posted cause=request_not_answerable http_status=200`; no `admitted predicates` and no `answered predicate` line after the capture mark.
- Helper: `RESULT status=done ok=true allowed=false reason=refused_by_holder answered=none refused=true`.
- Demo log: exactly one `verdict transactionId=... tier=C ... refused=true` for this request, and `grep -c nationality_asked $W/demo-B.log` is 0 (the site sees only the generic refusal).

**5b. Two `nationality_not_in`**: same as 5a with
`--raw '[{"name":"nationality_not_in","codes":["CUB"]},{"name":"nationality_not_in","codes":["RUS"]}]'`.

**5c. Control: one of each is admitted and answered**

```
cd $R/apps/demo && node scripts/tierc-poc-request.mjs --tier C --host 127.0.0.1 --nationality-is USA --nationality-not-in CUB,RUS --ttl 300 --launch --wait 90 | tee $W/req-5c.out
```

(Use a nationality the document holder actually has for the `--nationality-is` code that should answer yes; the expected `true` below assumes a US passport.)

Owner actions
1. Read aloud the question box (every bullet).
2. Type the document number, date of birth and expiry date into the three fields.
3. Tap the big scan button.
4. Hold the document to the back of the phone until the app reacts.
5. Read aloud the popup text. Tap OK.
6. Read aloud the line under the scan button and its colour.

Expected on screen: two bullets, "Whether your nationality is USA" and "Whether your nationality is none of: CUB, RUS";
popup "Done. 2 yes or no answers were sent to the website. Nothing else left your phone. Go back to the website to continue.";
green last-scan line "Last scan: 127.0.0.1:8787, delivered ✓".

Expected logcat and site: `admitted predicates=2 names=nationality_is,nationality_not_in`; two `answered predicate=... result=true`; `direct_post http_status=200`;
helper `RESULT status=done ok=true allowed=true ... answered=nationality_is=true,nationality_not_in=true refused=false`.

Pass (P5): 5a and 5b both refused on arrival with `chip_read=false`, `refused=true` at the site and no reason word in the demo log; 5c admitted and answered
(proves the refusal is not a blanket tier-C failure). Fail: either two-of-a-kind request is admitted, or 5c is refused.

#### P7. Redirects are never followed (findings #52 and #53; needs the proxy)

Background: before 0.9.1 the app followed a 301/302/303 on its POSTs as a body-less GET and read the landing page's 200 as
delivery. It now sets `instanceFollowRedirects = false` on all four of its site calls, so any 3xx is treated as "not delivered".
The proxy (section 5.4) stands in for a site or CDN that redirects.

Move the demo to 8788 and put the proxy on 8787 (`adb reverse` stays as is):

```
source ~/zkagent-phone.sh
cap_stop ; demo_stop
demo_start 8788 7
cd $R/apps/demo && node scripts/redirect-proxy.mjs --listen 8787 --upstream 8788 --code 302 > $W/proxy-7a.log 2>&1 & echo $! > $W/proxy.pid
sleep 1
curl -s -i -X POST -d x=1 http://127.0.0.1:8787/wallet/direct_post | head -3     # expect HTTP/1.1 302 and location: /redirect-landing
curl -s -o /dev/null -w '%{http_code}\n' http://127.0.0.1:8787/                  # expect 200 (forwarded to the demo)
cap_start 7a
```

Holder setting stays ON from P4. To change the redirect code or add `--redirect-get`: `kill "$(cat $W/proxy.pid)"`, restart with the new flags, `cap_stop; cap_start <label>`.

**7a. 302 on the tier-C refusal POST.** After "go":

```
cd $R/apps/demo && node scripts/tierc-poc-request.mjs --tier C --host 127.0.0.1 --ttl 300 \
  --raw '[{"name":"nationality_is","code":"USA"},{"name":"nationality_is","code":"NLD"}]' --launch --wait 40 | tee $W/req-7a.out
```

(Any gated refusal would do; this one is just convenient.)

Owner actions: read the popup aloud and tap OK; read the line under the scan button and its colour; tap the right arrow (Log) and read the newest entry.

Expected
- Popup ends "Nothing was read and nothing was sent." It must not say "The website was only told ...".
- Log (0.9.1): "Refused: the website asked something this app does not answer (nationality was asked more than once). Refusal could not be sent (the website answered HTTP 302)."
- Last-scan line red.
- Logcat: `generic refusal posted cause=request_not_answerable http_status=302`.
- Proxy log: one `REDIRECTED POST /wallet/direct_post ... -> 302`, and **no** `LANDING` line.
- Site: helper prints `RESULT status=pending`; the demo log has no `verdict ... refused=true` for this request; `curl .../ui/presentations/<TX>` shows `"status":"pending"`.

Pass: the app does not claim the website was told, and the site is `pending`. Fail: "told" or "sent to the website" with the site `pending`, or a `LANDING GET` in the proxy log.

**7b. 307 on the same POST.** Restart the proxy with `--code 307`; repeat 7a. Same expectations with `HTTP 307`.

**7c. 302 on the answer POST (tier A, real document).** Restart the proxy with `--code 302`. After "go":

```
cd $R/apps/demo && node scripts/tierc-poc-request.mjs --tier A --host 127.0.0.1 --ttl 300 --launch | tee $W/req-7c.out
```

Confirm `request_uri GET` for the request in the demo log, then tell the tester "proceed". Owner actions: type the three document fields; tap
the scan button; hold the document to the phone; read the popup and tap OK; read the line under the scan button and its colour.

Expected
- Red popup: "The website did not accept the answer — reopen the link from the site." (0.9.1 wording, finding #54, released in 0.9.1; the 0.9.0 and first-fix wording was "Verifier refused: HTTP 302".)
- Last-scan line red, never "Delivered" and never green. The Log entry names the HTTP status.
- Proxy log: `REDIRECTED POST /wallet/direct_post`, no `LANDING`.
- Site: `curl .../ui/presentations/<TX>` shows `"status":"pending"` (the presentation never arrived).

Pass: no "Delivered", no green, site `pending`.

**7d. 302 on the request_uri GET.** Restart the proxy with `--code 302 --redirect-get`. After "go", make a tier-A request as in 7c.

Expected
- Popup: "This website's request could not be verified, so it was not answered. Nothing was read and nothing was sent."
- Proxy log: `REDIRECTED GET /wallet/request.jwt/...`, no `LANDING` (the app must not reach the redirect target at all).
- Site: `pending`.

### Teardown

```
source ~/zkagent-phone.sh
cap_stop 2>/dev/null
kill "$(cat $W/proxy.pid)" 2>/dev/null ; demo_stop 2>/dev/null          # exact pids from the .pid files
adb reverse --remove tcp:8787 ; adb reverse --list                      # expect empty
adb uninstall $PKG
adb install /path/to/app-regular-release.apk                            # end state: the released APK
git -C $R status --short                                                # operator.json must not be listed
pgrep -af "server.mjs|redirect-proxy|logcat" | grep -v pgrep            # expect nothing from this run
```

Privacy check before you share any of `$W`: search it for your name and your document number (expect 0
hits), then delete it (`rm -rf "$W"`). The only values in it should be country codes a **site** asked
about, which are not document values.

### Writing it up

Record each scenario as PASS, FAIL or NOT RUN, with: build (release/debug), what the tester read, the
logcat lines, the site-side state, and the build's sha256. State the limits plainly: debug-build
evidence is not release-build evidence; a tier-C refusal after the read has no deterministic trigger
with real documents; the TalkBack check needs its own run. An example of the shape:
`docs/logs/PHONE-CHECK-0.9.0-AND-0.9.1-FIX-2026-10-28.md`.

## 10. Reuse pointers

- **`docs/product/reuse-map.md`**: one page listing every liftable part (verdict core, evidence plugs,
  chip and masterlist check, secure-hardware key, handoff, config gate, publish workflow), each with
  its maturity, the evidence behind it and caveats. Start here if you want to take a piece.
- **`packages/chiproof/README.md`**: the verifier library on its own: quickstart, the tiers, the evidence
  slot and plugs, the never-throw contract (`ok:false` forces `allowed:null`, `valid:false` forces
  `allowed:false`). Install with `npm install chiproof`. Its `chiproof.context.md` is the long form.
- **`apps/demo/deploy/README.md`**: a test-only kit to run the demo behind real TLS on a server you own,
  to check that the scanner finds a request-signing key from a real HTTPS origin on the release network
  configuration. It is not a hosted verifier; bring it up, test, take it down.
- **`docs/product/customer-guide.md`**: the manual: what every screen and popup means, the tier rules,
  the two answers (the app's delivery status versus the site's decision), building and signing your own
  release (7.1) and the full `operator.json` reference (7.2).

## 11. Where things are

| Need | File |
|---|---|
| Latest release notes, APK digests | `CHANGELOG.md` |
| Scanner build, config and item map | `apps/scanner/README.md` |
| Demo site config and wire steps | `apps/demo/README.md` |
| Device-test evidence records | `docs/logs/` |
| Decisions and the scope gate | `docs/wiki/decisions.md`, `docs/product/zkagent-prd.md` |
