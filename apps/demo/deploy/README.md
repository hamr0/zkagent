# apps/demo/deploy - test-only TLS kit (milestones.md section 6.9.2 item 23(d))

Runs `apps/demo` behind real TLS on a VPS **you** own, for ONE test: can the scanner, on the release network
configuration (system CAs, no cleartext), find a request-signing key from an arbitrary HTTPS origin? This is not a
hosted verifier (NO-GO #3, D76): bring it up, run the test, take it down. No host name or IP is written anywhere in the
repo; the three names are the variables `GOOD_HOST`, `MISMATCH_HOST`, `BADCERT_HOST`, given at run time.

It is built for a **live nginx host** (AlmaLinux 8, root, nginx and certbot already running other sites). It adds
exactly two things to the system and removes exactly those two on `teardown`.

## What the scanner does (so you know what is being tested)

For a link `av://authorize?client_id=...&request_uri=https://HOST/...`, `RequestTrust.resolveVerifierKey` fetches
`GET https://HOST:443/.well-known/zkagent-verifier` (a JWK `{kty:EC, crv:P-256, x, y, kid}` or a `{keys:[...]}` set),
over the platform TLS stack with system CAs only. Then it fetches the request object and checks its ES256 signature
against that key. A bad certificate, a missing key or a non-matching signature each refuse the link.

## The three sites (three subdomains, all on 443, one demo process)

| Site | Certificate | Well-known key | The phone must |
|---|---|---|---|
| `https://GOOD_HOST` | Let's Encrypt (CA-valid) | matches the signing key | ANSWER the age check |
| `https://MISMATCH_HOST` | Let's Encrypt (CA-valid) | a different key, same `kid` | refuse (signature) |
| `https://BADCERT_HOST` | self-signed (openssl, untrusted) | matches | refuse (certificate) |

The signing key is generated fresh on the VPS (the repo's dev key is public, so it is never used here). The demo runs as
`nobody`, bound to 127.0.0.1 only; nginx proxies to it and passes the Host header through.

## What it touches on the box (and nothing else)

- ONE nginx file: `/etc/nginx/conf.d/zkagent-tls-test.conf`, generated from `nginx-80.conf.tmpl` and
  `nginx-443.conf.tmpl` (placeholders only). No `default_server`; `nginx.conf` and other conf.d files are never edited.
  `nginx -t` runs before every reload; if it fails our file is deleted and nginx is not reloaded. Reload, never restart.
- ONE certbot lineage: `--cert-name zkagent-tls-test` (GOOD_HOST + MISMATCH_HOST), via `certbot certonly --webroot`
  (no nginx plugin, no hooks), so `/etc/letsencrypt/{live,archive}/zkagent-tls-test` and
  `/etc/letsencrypt/renewal/zkagent-tls-test.conf`. certbot's own log for it goes to the work dir. While it exists, the
  box's normal certbot renew timer would also renew it; teardown deletes it.
- ONE work dir, default `/opt/zkagent-tls-test` (must be named `zkagent-tls-test`): keys, acme webroot, self-signed cert,
  demo copy and its npm install, demo store, logs, pidfile.
- Not touched: firewall (80/443 are already open; no other port is used), other sites, other lineages, system trust store.
- Network use: Let's Encrypt, and the npm registry for `npm install --omit=dev --ignore-scripts` of the demo copy.

## Run it

Needs on the VPS: DNS A records for the three names pointing at it (add them first; Let's Encrypt rate limits apply, so
do not retry blindly), `nginx`, `certbot`, `openssl`, `curl`, `node` 22, `npm`, `setpriv`, and root.

```
# 1. from the repo root on your machine (committed state; layout apps/demo + packages/chiproof is kept):
git archive HEAD apps/demo packages/chiproof | ssh root@VPS 'mkdir -p /root/zkagent-kit && tar -x -C /root/zkagent-kit'
# then on the VPS:
export GOOD_HOST=... MISMATCH_HOST=... BADCERT_HOST=... CERT_EMAIL=you@example   # CERT_EMAIL optional
#    optional: WORK_DIR=/opt/zkagent-tls-test  DEMO_PORT=8787  PUBLIC_IP=<if behind NAT>
cd /root/zkagent-kit/apps/demo/deploy

# 2. read-only look first
./run.sh plan            # every command it would run, nothing executed
./run.sh preflight       # DNS, free port, no leftovers, nginx -t; changes nothing

# 3. bring up (each step prints what it changes before doing it)
./run.sh stage1          # work dir, keys, self-signed cert, port-80 nginx file, Let's Encrypt cert
./run.sh stage2          # demo copy + process, full nginx file with the 443 blocks
#    (./run.sh up = stage1 + stage2)

# 4. server-side check from any machine with node 22 (expects OK, OK, OK)
node check.mjs

# 5. one single-use link per phone test (120 s lifetime; make it right before the phone step)
node check.mjs link good        # also: mismatch, badcert

# 6. when done
./run.sh teardown
./run.sh verify-clean    # proof: no conf file, no lineage, no renewal conf, no work dir, port free, nginx -t OK
```

`./run.sh status` shows what currently exists. Delete the three DNS records afterwards. If stage1 or stage2 fails, run
`teardown` then `verify-clean`; both are safe to run any number of times.

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
