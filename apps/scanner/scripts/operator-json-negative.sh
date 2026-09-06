#!/usr/bin/env bash
# §6.7 POC (D82/D84, milestones.md §6.7.5) — proves the Gradle-level half of
# the operator.json validation surface (app/build.gradle.kts's
# loadOperatorConfig): each deliberately-broken variant below MUST fail
# :app:assembleRegularDebug AND name the violated rule in stderr. Restores
# the good, committed reference file afterward, whether or not a case
# fails. Read exit codes off bare commands only (AGENT_RULES) — never off a
# pipeline's exit code, which would report the LAST command in the pipe.
#
# Run from apps/scanner/ or anywhere; paths below are relative to this
# script's own location.
set -u

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SCANNER_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
OPERATOR_JSON="$SCANNER_DIR/operator.json"
GOOD_BACKUP="$(mktemp)"
STDERR_LOG="$(mktemp)"

cleanup() {
    cp "$GOOD_BACKUP" "$OPERATOR_JSON"
    rm -f "$GOOD_BACKUP" "$STDERR_LOG"
}
trap cleanup EXIT

cp "$OPERATOR_JSON" "$GOOD_BACKUP"

FAILED=0

# case, description, expected-rule-substring, json-body
run_case() {
    local name="$1"
    local expect="$2"
    local body="$3"

    printf '%s\n' "$body" > "$OPERATOR_JSON"

    (
        cd "$SCANNER_DIR" && \
        JAVA_HOME="${JAVA_HOME:-$HOME/.gradle/jdks/eclipse_adoptium-17-amd64-linux.2}" \
        ANDROID_HOME="${ANDROID_HOME:-$HOME/Android/Sdk}" \
        ./gradlew :app:assembleRegularDebug --offline -q
    ) > "$STDERR_LOG" 2>&1
    local exit_code=$?

    local matched="no"
    if grep -q "$expect" "$STDERR_LOG"; then
        matched="yes"
    fi

    if [ "$exit_code" -ne 0 ] && [ "$matched" = "yes" ]; then
        echo "PASS  $name -> exit=$exit_code, found '$expect'"
    else
        echo "FAIL  $name -> exit=$exit_code, found-rule=$matched (expected '$expect')"
        echo "----- build output for $name -----"
        cat "$STDERR_LOG"
        echo "-----------------------------------"
        FAILED=1
    fi
}

# 1. Unknown top-level key (rule 8)
run_case "unknown-key" "rule 8" '{
  "schema_version": 1,
  "operator": {"name": "x", "contact_url": "https://x.example"},
  "tiers": "A+B",
  "thresholds": [18],
  "verifiers": ["127.0.0.1"],
  "multi_threshold_verifiers": [],
  "evidence_plug": "none",
  "tier_c_verifiers": [],
  "strings": {"app_name": "x"},
  "bogus_extra_field": true
}'

# 2. Threshold 43 not on the D74 list (rule 3)
run_case "bad-threshold-43" "rule 3" '{
  "schema_version": 1,
  "operator": {"name": "x", "contact_url": "https://x.example"},
  "tiers": "A+B",
  "thresholds": [43],
  "verifiers": ["127.0.0.1"],
  "multi_threshold_verifiers": [],
  "evidence_plug": "none",
  "tier_c_verifiers": [],
  "strings": {"app_name": "x"}
}'

# 3. tier_c_verifiers non-empty (rule 7)
run_case "nonempty-tier-c-verifiers" "rule 7" '{
  "schema_version": 1,
  "operator": {"name": "x", "contact_url": "https://x.example"},
  "tiers": "A+B",
  "thresholds": [18],
  "verifiers": ["127.0.0.1"],
  "multi_threshold_verifiers": [],
  "evidence_plug": "none",
  "tier_c_verifiers": ["kyc.example"],
  "strings": {"app_name": "x"}
}'

# 4. Wildcard hostname (rule 4)
run_case "wildcard-hostname" "rule 4" '{
  "schema_version": 1,
  "operator": {"name": "x", "contact_url": "https://x.example"},
  "tiers": "A+B",
  "thresholds": [18],
  "verifiers": ["*.example.com"],
  "multi_threshold_verifiers": [],
  "evidence_plug": "none",
  "tier_c_verifiers": [],
  "strings": {"app_name": "x"}
}'

# 5. Port-suffixed verifiers entry (rule 4) — G6 (2026-09-06 validation
# pass): confirms a real-origin-shaped entry like "127.0.0.1:8787" (the
# exact string M3's dev origin looks like) is rejected at BUILD time, not
# just at runtime match time (OperatorPolicyTest's
# "a port-suffixed hostname never matches a bare allowlist entry").
run_case "port-suffixed-verifier" "rule 4" '{
  "schema_version": 1,
  "operator": {"name": "x", "contact_url": "https://x.example"},
  "tiers": "A+B",
  "thresholds": [18],
  "verifiers": ["127.0.0.1:8787"],
  "multi_threshold_verifiers": [],
  "evidence_plug": "none",
  "tier_c_verifiers": [],
  "strings": {"app_name": "x"}
}'

# 6. Empty thresholds (rule 3)
run_case "empty-thresholds" "rule 3" '{
  "schema_version": 1,
  "operator": {"name": "x", "contact_url": "https://x.example"},
  "tiers": "A+B",
  "thresholds": [],
  "verifiers": ["127.0.0.1"],
  "multi_threshold_verifiers": [],
  "evidence_plug": "none",
  "tier_c_verifiers": [],
  "strings": {"app_name": "x"}
}'

# Restore and prove the good file builds clean again (belt-and-suspenders —
# `cleanup` on EXIT also does this, but a mid-script restore lets this
# script assert the positive case too).
cp "$GOOD_BACKUP" "$OPERATOR_JSON"
(
    cd "$SCANNER_DIR" && \
    JAVA_HOME="${JAVA_HOME:-$HOME/.gradle/jdks/eclipse_adoptium-17-amd64-linux.2}" \
    ANDROID_HOME="${ANDROID_HOME:-$HOME/Android/Sdk}" \
    ./gradlew :app:assembleRegularDebug --offline -q
) > "$STDERR_LOG" 2>&1
restore_exit=$?
if [ "$restore_exit" -eq 0 ]; then
    echo "PASS  restored-good-file -> exit=$restore_exit"
else
    echo "FAIL  restored-good-file -> exit=$restore_exit"
    cat "$STDERR_LOG"
    FAILED=1
fi

if [ "$FAILED" -ne 0 ]; then
    echo "operator-json-negative.sh: ONE OR MORE CASES FAILED"
    exit 1
fi
echo "operator-json-negative.sh: all cases passed"
exit 0
