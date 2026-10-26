#!/usr/bin/env bash
# TEST-ONLY deploy kit for milestones.md section 6.9.2 item 23(d), for a LIVE nginx host (AlmaLinux 8, root).
# Runs apps/demo behind real TLS to test request-signing key discovery from an arbitrary HTTPS origin. NOT a hosted
# verifier (NO-GO #3): bring it up, run the test, tear it down. Host names are runtime variables, never stored in the repo.
#
# System paths this script may write (and nothing else):
#   /etc/nginx/conf.d/zkagent-tls-test.conf        the ONE nginx file (generated from the two *.tmpl files)
#   /etc/letsencrypt/{live,archive}/zkagent-tls-test, /etc/letsencrypt/renewal/zkagent-tls-test.conf   the ONE certbot lineage
#   $WORK_DIR (default /opt/zkagent-tls-test)      keys, acme webroot, self-signed cert, demo copy, logs, pidfile
# It never edits nginx.conf or another conf.d file, never touches the firewall or another certbot lineage, never uses
# the certbot nginx plugin, never restarts nginx (reload only, and only after `nginx -t` passes).
#
# Usage:  GOOD_HOST=a.example MISMATCH_HOST=b.example BADCERT_HOST=c.example [CERT_EMAIL=you@example] [WORK_DIR=..] [DEMO_PORT=8787] \
#           ./run.sh plan|preflight|stage1|stage2|up|status|teardown|verify-clean
set -euo pipefail

KIT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO="$(cd "$KIT/../../.." && pwd)"
WORK_DIR="${WORK_DIR:-/opt/zkagent-tls-test}"
DEMO_PORT="${DEMO_PORT:-8787}"
CONF=/etc/nginx/conf.d/zkagent-tls-test.conf
CERT_NAME=zkagent-tls-test
LE=/etc/letsencrypt
KID="vps-test-1"
PLAN=0
STEP_N=0

die() { echo "ERROR: $*" >&2; exit 1; }

# ---- input validation (values end up in an nginx file and in rm -rf, so they are checked strictly) -------------------
validate_common() {
  [[ "$WORK_DIR" =~ ^/[A-Za-z0-9_./-]+$ ]] || die "WORK_DIR must be an absolute path of [A-Za-z0-9_./-]"
  [ "$(basename "$WORK_DIR")" = zkagent-tls-test ] || die "WORK_DIR must end in /zkagent-tls-test (teardown deletes it)"
  [[ "$DEMO_PORT" =~ ^[0-9]{2,5}$ ]] && [ "$DEMO_PORT" -ge 1024 ] && [ "$DEMO_PORT" -le 65535 ] || die "DEMO_PORT must be 1024-65535"
}
validate_hosts() {
  local v n
  for n in GOOD_HOST MISMATCH_HOST BADCERT_HOST; do
    v="${!n:-}"
    [ -n "$v" ] || die "set $n (a DNS name whose A record points at this box)"
    [[ "$v" =~ ^[a-z0-9]([a-z0-9-]*[a-z0-9])?(\.[a-z0-9]([a-z0-9-]*[a-z0-9])?)+$ ]] || die "$n is not a plain lowercase DNS name"
  done
  [ "$GOOD_HOST" != "$MISMATCH_HOST" ] && [ "$GOOD_HOST" != "$BADCERT_HOST" ] && [ "$MISMATCH_HOST" != "$BADCERT_HOST" ] \
    || die "the three host names must be different"
  [ -z "${CERT_EMAIL:-}" ] || [[ "$CERT_EMAIL" =~ ^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+$ ]] || die "CERT_EMAIL is not a plain e-mail address"
}

# ---- step runner: announce what will change, then do it (or only print it, in plan mode) -------------------------------
step() {
  local desc="$1"; shift
  STEP_N=$((STEP_N + 1))
  printf '[%02d] %s\n       $ %s\n' "$STEP_N" "$desc" "$*"
  if [ "$PLAN" = 1 ]; then describe "$1"; else "$@"; fi
}
# The literal commands behind each helper, printed by `plan` (kept next to the helpers they describe).
describe() {
  local mail="--register-unsafely-without-email"; [ -z "${CERT_EMAIL:-}" ] || mail="-m $CERT_EMAIL --no-eff-email"
  case "$1" in
  make_workdir) cat <<EOT
         mkdir -p $WORK_DIR/{acme,wk-good,wk-mismatch,logs,src,selfsigned,private,data}; touch $WORK_DIR/.zkagent-tls-test
         chmod 755 (dirs nginx reads) / chmod 700 selfsigned private; chown nobody:nobody $WORK_DIR/data
EOT
;;
  make_keys) cat <<EOT
         openssl genpkey -algorithm EC -pkeyopt ec_paramgen_curve:P-256 -out $WORK_DIR/private/signer.pem   (and other.pem)
         openssl pkey -in <pem> -pubout -outform DER | tail -c 64  ->  $WORK_DIR/wk-good/zkagent-verifier, $WORK_DIR/wk-mismatch/zkagent-verifier (JWK, kid $KID)
EOT
;;
  make_selfsigned) cat <<EOT
         openssl req -x509 -newkey ec -pkeyopt ec_paramgen_curve:prime256v1 -nodes -days 3 -subj /CN=$BADCERT_HOST -addext subjectAltName=DNS:$BADCERT_HOST -keyout $WORK_DIR/selfsigned/key.pem -out $WORK_DIR/selfsigned/cert.pem
EOT
;;
  install_conf) cat <<EOT
         sed (placeholders only) <templates> > $WORK_DIR/conf.rendered
         install -m 644 $WORK_DIR/conf.rendered $CONF
         nginx -t   # on failure: rm -f $CONF and exit 1, no reload
         systemctl reload nginx
EOT
;;
  get_cert) cat <<EOT
         certbot certonly --webroot -w $WORK_DIR/acme --cert-name $CERT_NAME -d $GOOD_HOST -d $MISMATCH_HOST --non-interactive --agree-tos $mail --logs-dir $WORK_DIR/logs/certbot
EOT
;;
  sync_src) echo "         (cd <repo> && tar --exclude=node_modules --exclude=apps/demo/data --exclude=apps/demo/android -cf - apps/demo packages/chiproof) | tar -xf - -C $WORK_DIR/src; chmod -R a+rX $WORK_DIR/src" ;;
  demo_install) echo "         (cd $WORK_DIR/src/apps/demo && npm install --omit=dev --ignore-scripts --no-audit --no-fund)" ;;
  start_demo) cat <<EOT
         ss -ltn   # abort if :$DEMO_PORT is listening
         PUBLIC_SCHEME=https SCOPE_DOMAIN=$GOOD_HOST BIND_HOST=127.0.0.1 PORT=$DEMO_PORT REQUEST_SIGNER_KID=$KID REQUEST_SIGNER_PRIVKEY_PEM=<from $WORK_DIR/private/signer.pem> DEMO_STORE_PATH=$WORK_DIR/data/store.json \\
           nohup setpriv --reuid=nobody --regid=nobody --clear-groups node server.mjs > $WORK_DIR/logs/demo.log 2>&1 &   (pid -> $WORK_DIR/demo.pid)
         curl -s http://127.0.0.1:$DEMO_PORT/   # up to 10 s
EOT
;;
  stop_demo) echo "         kill <pid from $WORK_DIR/demo.pid>   # only if /proc/<pid>/cmdline starts with 'node server.mjs'; rm $WORK_DIR/demo.pid" ;;
  remove_conf_and_reload) echo "         rm -f $CONF; nginx -t && systemctl reload nginx" ;;
  delete_lineage) echo "         certbot delete --cert-name $CERT_NAME --non-interactive --logs-dir <tmpdir>" ;;
  remove_workdir) echo "         rm -rf $WORK_DIR   # only if $WORK_DIR/.zkagent-tls-test exists" ;;
  esac
}
need_root() { [ "$PLAN" = 1 ] || [ "$(id -u)" -eq 0 ] || die "run as root (it edits /etc/nginx/conf.d and runs certbot)"; }

# ---- helpers (each is one logical step) ---------------------------------------------------------------------------------
b64u() { base64 -w0 | tr '+/' '-_' | tr -d '='; }

# JWK for a P-256 private key PEM: the SPKI DER ends with the uncompressed point 04||X||Y, so the last 64 bytes are X||Y.
make_jwk() {  # $1 = pem, $2 = output file
  local xy; xy="$(mktemp)"
  openssl pkey -in "$1" -pubout -outform DER | tail -c 64 > "$xy"
  printf '{"kty":"EC","crv":"P-256","x":"%s","y":"%s","kid":"%s"}\n' \
    "$(head -c 32 "$xy" | b64u)" "$(tail -c 32 "$xy" | b64u)" "$KID" > "$2"
  rm -f "$xy"
  chmod 644 "$2"
}

make_workdir() {
  [ ! -e "$WORK_DIR" ] || [ -f "$WORK_DIR/.zkagent-tls-test" ] || die "$WORK_DIR exists and is not ours; refusing to use it"
  mkdir -p "$WORK_DIR"
  chmod 755 "$WORK_DIR"
  : > "$WORK_DIR/.zkagent-tls-test"
  mkdir -p "$WORK_DIR"/{acme,wk-good,wk-mismatch,logs,src,selfsigned,private,data}
  chmod 755 "$WORK_DIR"/{acme,wk-good,wk-mismatch,logs,src}
  chmod 700 "$WORK_DIR"/{selfsigned,private}
  chown nobody:nobody "$WORK_DIR/data"                      # the demo's store lives here; the demo runs as nobody
  echo "$DEMO_PORT" > "$WORK_DIR/demo.port"
}

make_keys() {
  openssl genpkey -algorithm EC -pkeyopt ec_paramgen_curve:P-256 -out "$WORK_DIR/private/signer.pem"
  openssl genpkey -algorithm EC -pkeyopt ec_paramgen_curve:P-256 -out "$WORK_DIR/private/other.pem"
  chmod 600 "$WORK_DIR"/private/*.pem
  make_jwk "$WORK_DIR/private/signer.pem" "$WORK_DIR/wk-good/zkagent-verifier"      # matches what signs requests
  make_jwk "$WORK_DIR/private/other.pem"  "$WORK_DIR/wk-mismatch/zkagent-verifier"  # does NOT: same kid, other key
}

make_selfsigned() {
  openssl req -x509 -newkey ec -pkeyopt ec_paramgen_curve:prime256v1 -nodes -days 3 \
    -subj "/CN=$BADCERT_HOST" -addext "subjectAltName=DNS:$BADCERT_HOST" \
    -keyout "$WORK_DIR/selfsigned/key.pem" -out "$WORK_DIR/selfsigned/cert.pem"
  chmod 600 "$WORK_DIR/selfsigned/key.pem"
}

render() {  # template files on the command line, rendered to stdout
  sed -e "s|@GOOD_HOST@|$GOOD_HOST|g" -e "s|@MISMATCH_HOST@|$MISMATCH_HOST|g" -e "s|@BADCERT_HOST@|$BADCERT_HOST|g" \
      -e "s|@WORK_DIR@|$WORK_DIR|g" -e "s|@DEMO_PORT@|$DEMO_PORT|g" "$@"
}

# Install our one nginx file, test the whole config, and reload only if the test passes. On a failed test our file is
# deleted and nothing is reloaded, so the live sites never see a bad config.
install_conf() {  # template files
  render "$@" > "$WORK_DIR/conf.rendered"
  install -m 644 "$WORK_DIR/conf.rendered" "$CONF"
  if ! nginx -t; then
    rm -f "$CONF"
    die "nginx -t failed with our file; it was removed and nginx was NOT reloaded"
  fi
  systemctl reload nginx
}

get_cert() {
  local mail=(--register-unsafely-without-email)
  [ -z "${CERT_EMAIL:-}" ] || mail=(-m "$CERT_EMAIL" --no-eff-email)
  certbot certonly --webroot -w "$WORK_DIR/acme" --cert-name "$CERT_NAME" -d "$GOOD_HOST" -d "$MISMATCH_HOST" \
    --non-interactive --agree-tos "${mail[@]}" --logs-dir "$WORK_DIR/logs/certbot"
}

sync_src() {  # the demo and chiproof (its file: dependency) keep their relative layout under $WORK_DIR/src
  [ -d "$REPO/apps/demo" ] && [ -d "$REPO/packages/chiproof" ] || die "need apps/demo and packages/chiproof next to this kit"
  (cd "$REPO" && tar --exclude=node_modules --exclude=apps/demo/data --exclude=apps/demo/android -cf - apps/demo packages/chiproof) \
    | tar -xf - -C "$WORK_DIR/src"
  chmod -R a+rX "$WORK_DIR/src"
}

demo_install() { (cd "$WORK_DIR/src/apps/demo" && npm install --omit=dev --ignore-scripts --no-audit --no-fund); chmod -R a+rX "$WORK_DIR/src"; }

port_listeners() { ss -ltn | awk -v p=":$1" 'NR>1 && $4 ~ (p "$")'; }

start_demo() {
  [ -z "$(port_listeners "$DEMO_PORT")" ] || die "port $DEMO_PORT is already in use; set DEMO_PORT to a free port"
  (
    cd "$WORK_DIR/src/apps/demo"
    # unprivileged (nobody, no supplementary groups), loopback only; fresh keys from $WORK_DIR, never the repo dev key
    PUBLIC_SCHEME=https SCOPE_DOMAIN="$GOOD_HOST" BIND_HOST=127.0.0.1 PORT="$DEMO_PORT" \
    REQUEST_SIGNER_KID="$KID" REQUEST_SIGNER_PRIVKEY_PEM="$(cat "$WORK_DIR/private/signer.pem")" \
    DEMO_STORE_PATH="$WORK_DIR/data/store.json" \
      nohup setpriv --reuid=nobody --regid=nobody --clear-groups node server.mjs > "$WORK_DIR/logs/demo.log" 2>&1 &
    echo $! > "$WORK_DIR/demo.pid"
  )
  local i code
  for i in 1 2 3 4 5 6 7 8 9 10; do
    code="$(curl -s -o /dev/null -w '%{http_code}' "http://127.0.0.1:$DEMO_PORT/" || true)"
    [ "$code" != 000 ] && { echo "demo answering on 127.0.0.1:$DEMO_PORT (HTTP $code), pid $(cat "$WORK_DIR/demo.pid")"; return 0; }
    sleep 1
  done
  die "demo did not answer within 10 s; see $WORK_DIR/logs/demo.log"
}

# Kill by exact pid, and only if /proc says that pid really is our server.
stop_demo() {
  local pid
  [ -f "$WORK_DIR/demo.pid" ] || { echo "no pidfile; demo not started by this kit"; return 0; }
  pid="$(cat "$WORK_DIR/demo.pid")"
  if [[ "$pid" =~ ^[0-9]+$ ]] && [ -r "/proc/$pid/cmdline" ] && tr '\0' ' ' < "/proc/$pid/cmdline" | grep -q '^node server.mjs'; then
    kill "$pid"; echo "demo stopped (pid $pid)"
  else
    echo "pid $pid is not our demo (or already gone); nothing killed"
  fi
  rm -f "$WORK_DIR/demo.pid"
}

remove_conf_and_reload() {
  if [ -e "$CONF" ]; then
    rm -f "$CONF"
    if nginx -t; then systemctl reload nginx; else echo "WARNING: nginx -t fails even without our file; NOT reloading. Fix nginx separately." >&2; TEARDOWN_BAD=1; fi
  else
    echo "no $CONF; nothing to reload"
  fi
}

delete_lineage() {
  if [ -e "$LE/renewal/$CERT_NAME.conf" ] || [ -e "$LE/live/$CERT_NAME" ] || [ -e "$LE/archive/$CERT_NAME" ]; then
    local ld; ld="$(mktemp -d)"
    certbot delete --cert-name "$CERT_NAME" --non-interactive --logs-dir "$ld" || TEARDOWN_BAD=1
    rm -rf "$ld"
  else
    echo "no lineage $CERT_NAME; nothing to delete"
  fi
}

remove_workdir() {
  if [ -d "$WORK_DIR" ]; then
    [ -f "$WORK_DIR/.zkagent-tls-test" ] || die "$WORK_DIR has no marker file; not deleting it"
    rm -rf "$WORK_DIR"
  else
    echo "no $WORK_DIR; nothing to remove"
  fi
}

# ---- subcommands ---------------------------------------------------------------------------------------------------------
preflight_checks_text() {
  cat <<EOF
preflight (read-only; changes nothing):
  - running as root; tools present: nginx certbot openssl curl node npm setpriv ss tar; user 'nobody' exists
  - DNS A records of the three names resolve to this box's public IP (IP from 'ip route get', or PUBLIC_IP); warns on AAAA
  - demo port $DEMO_PORT is free ('ss -ltn')
  - no existing $CONF, no lineage $CERT_NAME under $LE, no $WORK_DIR
  - none of the three names already appears in another file under /etc/nginx
  - 'nginx -t' passes right now
EOF
}

do_preflight() {
  set +e   # checks report failures themselves
  local bad=0 ok="OK  " no="FAIL"
  chk() { if [ "$1" = 0 ]; then echo "$ok $2"; else echo "$no $2"; bad=1; fi; }
  [ "$(id -u)" -eq 0 ]; chk $? "running as root"
  local t missing=""
  for t in nginx certbot openssl curl node npm setpriv ss tar; do command -v "$t" >/dev/null || missing="$missing $t"; done
  [ -z "$missing" ]; chk $? "tools present${missing:+ (missing:$missing)}"
  id nobody >/dev/null 2>&1; chk $? "user nobody exists"
  local ip="${PUBLIC_IP:-$(ip route get 1.1.1.1 2>/dev/null | awk '{for(i=1;i<=NF;i++) if($i=="src"){print $(i+1); exit}}')}"
  echo "     this box's address: ${ip:-unknown} (override with PUBLIC_IP if behind NAT)"
  local h a6
  for h in "$GOOD_HOST" "$MISMATCH_HOST" "$BADCERT_HOST"; do
    local got; got="$(getent ahostsv4 "$h" 2>/dev/null | awk '{print $1}' | sort -u | tr '\n' ' ')"
    [ -n "$ip" ] && [ "${got% }" = "$ip" ]; chk $? "DNS A $h -> ${got:-nothing}"
    a6="$(getent ahostsv6 "$h" 2>/dev/null | awk '$1 !~ /^::ffff:/ {print $1}' | sort -u | tr '\n' ' ')"
    [ -z "$a6" ] || echo "WARN $h also has AAAA ($a6): Let's Encrypt prefers IPv6; make sure nginx answers there or remove it"
  done
  [ -z "$(port_listeners "$DEMO_PORT")" ]; chk $? "port $DEMO_PORT free"
  [ ! -e "$CONF" ]; chk $? "no $CONF"
  [ ! -e "$LE/renewal/$CERT_NAME.conf" ] && [ ! -e "$LE/live/$CERT_NAME" ] && [ ! -e "$LE/archive/$CERT_NAME" ]; chk $? "no certbot lineage $CERT_NAME"
  [ ! -e "$WORK_DIR" ]; chk $? "no $WORK_DIR"
  local hit=""
  for h in "$GOOD_HOST" "$MISMATCH_HOST" "$BADCERT_HOST"; do
    hit="$hit$(grep -rlF -- "$h" /etc/nginx 2>/dev/null || true)"
  done
  [ -z "$hit" ]; chk $? "names not already used in /etc/nginx${hit:+ ($hit)}"
  nginx -t >/dev/null 2>&1; chk $? "nginx -t passes now"
  [ "$bad" = 0 ] && echo "preflight: all clear" || { echo "preflight: NOT clear"; return 1; }
}

stage1_steps() {
  echo "== stage 1: work dir, keys, self-signed cert, port-80 nginx file, Let's Encrypt cert =="
  step "create the work dir (marker, acme webroot, key dirs, data dir owned by nobody)" make_workdir
  step "generate two fresh P-256 keys (signing key, mismatch key) and write both well-known JWK files" make_keys
  step "make the self-signed cert for $BADCERT_HOST (3 days, never trusted by phones)" make_selfsigned
  step "install $CONF with ONLY the port-80 blocks (acme challenge) for $GOOD_HOST and $MISMATCH_HOST; nginx -t; reload" \
    install_conf "$KIT/nginx-80.conf.tmpl"
  step "request ONE certbot lineage '$CERT_NAME' via webroot (no nginx plugin, no hooks)" get_cert
}

stage2_steps() {
  echo "== stage 2: demo copy, demo process, full nginx file (adds the 443 blocks) =="
  step "copy apps/demo and packages/chiproof (no node_modules, no dev data) into $WORK_DIR/src" sync_src
  step "npm install --omit=dev in the demo copy (network: npm registry)" demo_install
  step "check port $DEMO_PORT is free, start the demo as nobody on 127.0.0.1:$DEMO_PORT, wait for it" start_demo
  step "rewrite $CONF with port-80 + 443 blocks; nginx -t; reload" \
    install_conf "$KIT/nginx-80.conf.tmpl" "$KIT/nginx-443.conf.tmpl"
}

TEARDOWN_BAD=0
teardown_steps() {
  echo "== teardown =="
  step "stop the demo (exact pid, verified against /proc/<pid>/cmdline)" stop_demo
  step "remove $CONF; nginx -t; reload (skipped if the file is already gone)" remove_conf_and_reload
  step "delete certbot lineage '$CERT_NAME' (live, archive and renewal conf of that lineage only)" delete_lineage
  step "remove $WORK_DIR (only if it carries our marker file)" remove_workdir
}

do_verify_clean() {
  set +e
  local bad=0 ok="OK  " no="FAIL" ld
  chk() { if [ "$1" = 0 ]; then echo "$ok $2"; else echo "$no $2"; bad=1; fi; }
  local port="$DEMO_PORT"; [ ! -f "$WORK_DIR/demo.port" ] || port="$(cat "$WORK_DIR/demo.port")"
  [ ! -e "$CONF" ]; chk $? "no $CONF"
  ld="$(mktemp -d)"
  ! certbot certificates --logs-dir "$ld" 2>/dev/null | grep -q "Certificate Name: $CERT_NAME"; chk $? "no lineage '$CERT_NAME' in 'certbot certificates'"
  rm -rf "$ld"
  [ ! -e "$LE/renewal/$CERT_NAME.conf" ] && [ ! -e "$LE/live/$CERT_NAME" ] && [ ! -e "$LE/archive/$CERT_NAME" ]; chk $? "no renewal conf, live or archive dir for $CERT_NAME"
  [ ! -e "$WORK_DIR" ]; chk $? "no $WORK_DIR"
  [ -z "$(port_listeners "$port")" ]; chk $? "nothing listening on port $port"
  nginx -t 2>&1 | sed 's/^/     /'; [ "${PIPESTATUS[0]}" = 0 ]; chk $? "nginx -t OK"
  [ "$bad" = 0 ] && echo "verify-clean: the box is clean" || { echo "verify-clean: NOT clean"; return 1; }
}

do_status() {
  echo "conf:    $([ -e "$CONF" ] && echo present || echo absent) ($CONF)"
  echo "lineage: $([ -e "$LE/renewal/$CERT_NAME.conf" ] && echo present || echo absent) ($CERT_NAME)"
  echo "workdir: $([ -d "$WORK_DIR" ] && echo present || echo absent) ($WORK_DIR)"
  if [ -f "$WORK_DIR/demo.pid" ] && [ -r "/proc/$(cat "$WORK_DIR/demo.pid")/cmdline" ] && tr '\0' ' ' < "/proc/$(cat "$WORK_DIR/demo.pid")/cmdline" | grep -q '^node server.mjs'; then
    echo "demo:    running (pid $(cat "$WORK_DIR/demo.pid"))"
  else echo "demo:    not running"; fi
}

on_exit() {
  local rc=$?
  [ "$rc" = 0 ] || [ "$PLAN" = 1 ] || case "${1:-}" in stage1|stage2|up) echo "FAILED (exit $rc). Partial state may exist; './run.sh teardown' removes it and 'verify-clean' proves it." >&2 ;; esac
}
validate_common
trap 'on_exit "${1:-}"' EXIT
case "${1:-}" in
  plan)
    validate_hosts; PLAN=1
    preflight_checks_text; stage1_steps; stage2_steps; teardown_steps
    echo "== verify-clean (read-only) =="
    echo "  no $CONF; no lineage '$CERT_NAME' in 'certbot certificates'; no renewal conf; no $WORK_DIR; nothing on port $DEMO_PORT; nginx -t OK"
    ;;
  preflight) validate_hosts; preflight_checks_text; do_preflight ;;
  stage1)    validate_hosts; need_root; stage1_steps ;;
  stage2)    validate_hosts; need_root; stage2_steps; echo "up. Next: node check.mjs, then the phone, then ./run.sh teardown" ;;
  up)        validate_hosts; need_root; stage1_steps; stage2_steps; echo "up. Next: node check.mjs, then the phone, then ./run.sh teardown" ;;
  status)    do_status ;;
  teardown)  need_root; teardown_steps; [ "$TEARDOWN_BAD" = 0 ] || die "teardown finished with warnings above; run verify-clean" ;;
  verify-clean) do_verify_clean ;;
  *) echo "usage: GOOD_HOST=.. MISMATCH_HOST=.. BADCERT_HOST=.. $0 plan|preflight|stage1|stage2|up|status|teardown|verify-clean" >&2; exit 2 ;;
esac
