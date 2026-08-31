#!/usr/bin/env bash
# M2 masterlist-probe capture (TEST 2 desk half — full load + half-load
# negative). Extracts ONLY the delimited M2 MASTERLIST REPORT block.
# PII-free by construction — see M2MasterlistProbe.kt.
set -uo pipefail
ADB="$HOME/Android/Sdk/platform-tools/adb"
OUT="${1:-/tmp/claude-1000/-home-hamr-PycharmProjects-zkagent/m2-masterlist-report.txt}"

if [ -z "$("$ADB" devices | sed -n '2p')" ]; then
  echo "no device on USB — replug the phone and unlock it" >&2
  exit 1
fi

"$ADB" logcat -d -s M2Masterlist \
  | sed -n '/===== M2 MASTERLIST REPORT BEGIN =====/,/===== M2 MASTERLIST REPORT END =====/p' \
  | tee "$OUT"

echo
echo "(saved to $OUT)"
