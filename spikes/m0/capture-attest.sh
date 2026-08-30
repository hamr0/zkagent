#!/usr/bin/env bash
# M1 attest report capture. Extracts ONLY the delimited M1 ATTEST REPORT block
# from logcat, never the whole buffer (which can contain unrelated app data).
# The report itself contains attestation certs (public key material + device
# attributes), never PII — see M1AttestProbe.kt.
set -uo pipefail
ADB="$HOME/Android/Sdk/platform-tools/adb"
TS="$(date +%Y%m%dT%H%M%S)"
OUT="${1:-/home/hamr/PycharmProjects/zkagent/spikes/m1-attest/fixtures/real/attest-$TS.txt}"

if [ -z "$("$ADB" devices | sed -n '2p')" ]; then
  echo "no device on USB — replug the phone and unlock it" >&2
  exit 1
fi

mkdir -p "$(dirname "$OUT")"

"$ADB" logcat -d -s M1Attest \
  | sed -n '/===== M1 ATTEST REPORT BEGIN =====/,/===== M1 ATTEST REPORT END =====/p' \
  | tee "$OUT"

echo
echo "(saved to $OUT)"
