#!/usr/bin/env bash
# TEST-ONLY deploy kit for milestones.md §6.9.2 item 23(d): run apps/demo behind a CA-valid TLS certificate on a VPS the
# owner controls, to test real-TLS request-signing key discovery from an arbitrary HTTPS origin. NOT a hosted verifier
# (NO-GO #3): bring it up for the test, tear it down after. The host name is a runtime variable and is never stored in
# the repo. Usage:   VERIFIER_HOST=<name> ./run.sh keys|up|status|down|purge
set -euo pipefail

: "${VERIFIER_HOST:?set VERIFIER_HOST to the DNS name that points at this VPS (the name is never committed)}"
KIT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
WORK_DIR="${WORK_DIR:-$HOME/zkagent-tls-test}"      # keys, store, logs, pidfile; removed by `purge`
DEMO_DIR="${DEMO_DIR:-$KIT/..}"                     # apps/demo of the copy of the repo on this VPS
KID="vps-test-1"
export VERIFIER_HOST WORK_DIR

b64u() { base64 -w0 | tr '+/' '-_' | tr -d '='; }

# JWK for a P-256 private key PEM: the SPKI DER ends with the uncompressed point 04||X||Y, so the last 64 bytes are X||Y.
jwk() {
  local xy; xy="$(mktemp)"
  openssl pkey -in "$1" -pubout -outform DER | tail -c 64 > "$xy"
  printf '{"kty":"EC","crv":"P-256","x":"%s","y":"%s","kid":"%s"}\n' \
    "$(head -c 32 "$xy" | b64u)" "$(tail -c 32 "$xy" | b64u)" "$KID"
  rm -f "$xy"
}

keys() {
  umask 077
  mkdir -p "$WORK_DIR/wk-good/.well-known" "$WORK_DIR/wk-mismatch/.well-known"
  [ -f "$WORK_DIR/signer.pem" ] || openssl genpkey -algorithm EC -pkeyopt ec_paramgen_curve:P-256 -out "$WORK_DIR/signer.pem"
  [ -f "$WORK_DIR/other.pem" ]  || openssl genpkey -algorithm EC -pkeyopt ec_paramgen_curve:P-256 -out "$WORK_DIR/other.pem"
  jwk "$WORK_DIR/signer.pem" > "$WORK_DIR/wk-good/.well-known/zkagent-verifier"      # matches what signs requests
  jwk "$WORK_DIR/other.pem"  > "$WORK_DIR/wk-mismatch/.well-known/zkagent-verifier"  # does NOT: same kid, other key
  chmod -R a+rX "$WORK_DIR/wk-good" "$WORK_DIR/wk-mismatch"                           # the proxy reads these (as root)
  echo "keys ready in $WORK_DIR (fresh P-256 pair for signing, a second pair for the mismatch site)"
}

up() {
  keys
  [ -d "$DEMO_DIR/node_modules" ] || (cd "$DEMO_DIR" && npm install --no-audit --no-fund)
  if [ -f "$WORK_DIR/demo.pid" ] && kill -0 "$(cat "$WORK_DIR/demo.pid")" 2>/dev/null; then
    echo "demo already running (pid $(cat "$WORK_DIR/demo.pid"))"
  else
    (
      cd "$DEMO_DIR"
      PUBLIC_SCHEME=https SCOPE_DOMAIN="$VERIFIER_HOST" BIND_HOST=127.0.0.1 PORT=8787 \
      REQUEST_SIGNER_KID="$KID" REQUEST_SIGNER_PRIVKEY_PEM="$(cat "$WORK_DIR/signer.pem")" \
      DEMO_STORE_PATH="$WORK_DIR/store.json" \
        nohup node server.mjs > "$WORK_DIR/demo.log" 2>&1 &
      echo $! > "$WORK_DIR/demo.pid"
    )
    echo "demo started (pid $(cat "$WORK_DIR/demo.pid")), log $WORK_DIR/demo.log"
  fi
  # Ports 80 and 443 need root: run Caddy through sudo unless already root.
  local as_root=(); [ "$(id -u)" -eq 0 ] || as_root=(sudo)
  "${as_root[@]}" env VERIFIER_HOST="$VERIFIER_HOST" WORK_DIR="$WORK_DIR" \
    caddy start --config "$KIT/Caddyfile" --adapter caddyfile
  echo "caddy started. Ports 80, 443, 8444 and 8445 must be open to the internet; the first certificate takes a few seconds."
}

status() {
  [ -f "$WORK_DIR/demo.pid" ] && kill -0 "$(cat "$WORK_DIR/demo.pid")" 2>/dev/null \
    && echo "demo: running (pid $(cat "$WORK_DIR/demo.pid"))" || echo "demo: not running"
  curl -sS -o /dev/null -w 'caddy admin: HTTP %{http_code}\n' http://localhost:2019/config/ || echo "caddy: not running"
}

down() {
  if [ -f "$WORK_DIR/demo.pid" ]; then
    pid="$(cat "$WORK_DIR/demo.pid")"
    # kill by exact pid, and only if that pid really is our server
    if tr '\0' ' ' < "/proc/$pid/cmdline" 2>/dev/null | grep -q 'node server.mjs'; then kill "$pid"; echo "demo stopped (pid $pid)"; fi
    rm -f "$WORK_DIR/demo.pid"
  fi
  caddy stop 2>/dev/null && echo "caddy stopped" || echo "caddy was not running"
}

purge() {
  down
  rm -rf "$WORK_DIR"
  echo "removed $WORK_DIR. Caddy's own certificate store is left in root's data directory (sudo ls ~root/.local/share/caddy);"
  echo "delete it yourself if you want the host fully clean."
}

case "${1:-}" in
  keys) keys ;; up) up ;; status) status ;; down) down ;; purge) purge ;;
  *) echo "usage: VERIFIER_HOST=<name> $0 keys|up|status|down|purge" >&2; exit 2 ;;
esac
