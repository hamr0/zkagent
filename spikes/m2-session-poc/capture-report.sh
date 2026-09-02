#!/usr/bin/env bash
# M2 scan report capture. Extracts ONLY the delimited M2 SCAN REPORT block
# from logcat, never the whole buffer (which can contain unrelated app data).
# The report itself is PII-free by construction — see MainActivity.kt / M0Probe.kt.
set -uo pipefail
ADB="$HOME/Android/Sdk/platform-tools/adb"
OUT="${1:-/tmp/claude-1000/-home-hamr-PycharmProjects-zkagent/m2-scan-report.txt}"

if [ -z "$("$ADB" devices | sed -n '2p')" ]; then
  echo "no device on USB — replug the phone and unlock it" >&2
  exit 1
fi

"$ADB" logcat -d -s MainActivity \
  | sed -n '/===== M2 SCAN REPORT =====/,/===== END M2 SCAN REPORT =====/p' \
  | tee "$OUT"

echo
echo "(saved to $OUT)"
