# apps/demo/deploy — test-only TLS kit (milestones.md §6.9.2 item 23(d))

Runs `apps/demo` behind a CA-valid certificate on a VPS **you** own, for ONE test: can the scanner, on the release
network configuration (system CAs, no cleartext), find a request-signing key from an arbitrary HTTPS origin?
This is not a hosted verifier (NO-GO #3, D76): bring it up, run the test, take it down. No host name or IP is
written anywhere in the repo; the name is the `VERIFIER_HOST` variable, given at run time.

## What the scanner does (so you know what is being tested)

For a link `av://authorize?client_id=…&request_uri=https://HOST/…`, `RequestTrust.resolveVerifierKey` fetches
`GET https://HOST:443/.well-known/zkagent-verifier` (a JWK `{kty:EC, crv:P-256, x, y, kid}` or a `{keys:[…]}` set),
over the platform TLS stack with system CAs only. Then it fetches the request object and checks its ES256 signature
against that key. A bad certificate, a missing key or a non-matching signature each refuse the link.

## The three sites (one host name, one demo process)

| Site | Certificate | Well-known key | The phone must |
|---|---|---|---|
| `https://HOST` | Let's Encrypt (CA-valid) | matches the signing key | ANSWER the age check |
| `https://HOST:8444` | Let's Encrypt (CA-valid) | a different key, same `kid` | refuse (signature) |
| `https://HOST:8445` | Caddy's internal CA (untrusted) | matches | refuse (certificate) |

The signing key is generated fresh on the VPS (the repo's dev key is public, so it is never used here).

## Run it

Prerequisites on the VPS: DNS for `HOST` points at it; ports 80, 443, 8444, 8445 are open to the internet; `node` 22,
`npm`, `openssl`, `curl`, and `caddy` 2.x are installed (distro package, or https://caddyserver.com/docs/install);
`sudo` works (Caddy binds 80/443).

```
# 1. from the repo root on your machine: copy only what the demo needs (VPS = your ssh target)
git archive HEAD apps/demo packages/chiproof | ssh "$VPS" 'mkdir -p ~/zkagent-tls-test/src && tar -x -C ~/zkagent-tls-test/src'

# 2. on the VPS
cd ~/zkagent-tls-test/src/apps/demo/deploy
export VERIFIER_HOST=HOSTNAME
sudo env VERIFIER_HOST=$VERIFIER_HOST WORK_DIR=$HOME/zkagent-tls-test caddy validate --config Caddyfile --adapter caddyfile
./run.sh up          # keys, demo (127.0.0.1:8787, plain HTTP, PUBLIC_SCHEME=https), then Caddy via sudo
./run.sh status

# 3. from any machine with node 22: server-side preflight (expects OK, OK, OK)
VERIFIER_HOST=HOSTNAME node check.mjs

# 4. one single-use link per phone test (120 s lifetime; make it right before the phone step)
VERIFIER_HOST=HOSTNAME node check.mjs link good        # also: mismatch, badcert

# 5. when done
./run.sh purge       # stops the demo (by exact pid) and Caddy, deletes keys, store, logs
```

`run.sh down` stops without deleting. Caddy's certificate store stays under root's data directory; `purge` tells you
where. Delete the DNS record afterwards if it was created for this test.

## Phone side

The right APK is a release variant, not a debug build (the debug build has a cleartext exception for loopback; it
also trusts system CAs, but the point is the release configuration). Build it debug-signed, no keystore needed:

```
cd apps/scanner
export JAVA_HOME=$HOME/.gradle/jdks/eclipse_adoptium-17-amd64-linux.2
KEYSTORE_FILE=$HOME/.android/debug.keystore KEYSTORE_PASSWORD=android KEY_ALIAS=androiddebugkey KEY_PASSWORD=android \
  ./gradlew --offline :app:assembleRegularRelease
```

Verify the network configuration with `aapt2 dump resources` (the file name is obfuscated in release) then
`aapt2 dump xmltree --file <that path> app-regular-release.apk`: expect only `base-config cleartextTrafficPermitted=false`
and `certificates src="system"`. Open each link with `adb shell am start -a android.intent.action.VIEW -d '<link>' -p com.zkagent.scanner`
after `adb shell am force-stop com.zkagent.scanner`.
