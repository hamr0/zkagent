#!/usr/bin/env bash
# M1 integrity report capture. Extracts ONLY the delimited M1 INTEGRITY REPORT
# block from logcat, never the whole buffer (which can contain unrelated app
# data). The report contains raw, still-encrypted Play Integrity tokens
# (opaque JWE/JWS blobs) plus request hashes and timings — no PII, but treat
# the tokens as sensitive bearer material until proven otherwise (that's the
# question this probe exists to answer). See M1IntegrityProbe.kt.
set -uo pipefail
ADB="$HOME/Android/Sdk/platform-tools/adb"
TS="$(date +%Y%m%dT%H%M%S)"
OUT="${1:-/home/hamr/PycharmProjects/zkagent/spikes/m1-integrity/fixtures/real/integrity-$TS.txt}"

if [ -z "$("$ADB" devices | sed -n '2p')" ]; then
  echo "no device on USB — replug the phone and unlock it" >&2
  exit 1
fi

mkdir -p "$(dirname "$OUT")"

"$ADB" logcat -d -s M1Integrity \
  | sed -n '/===== M1 INTEGRITY REPORT BEGIN =====/,/===== M1 INTEGRITY REPORT END =====/p' \
  | tee "$OUT"

echo
echo "(saved to $OUT)"
