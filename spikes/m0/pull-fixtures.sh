#!/usr/bin/env bash
# M1 ZK-fixture pull. Pulls the app's private external files dir — which
# holds the RAW DG1/SOD bytes M1SodProbe.kt writes (PII-bearing: DG1 = MRZ)
# plus the PII-free .sod.txt reports — into a gitignored local path.
# NEVER commit anything pulled here; spikes/m1-zk/fixtures/real/ is
# gitignored specifically for this (verify with `git check-ignore -v`).
set -uo pipefail
ADB="$HOME/Android/Sdk/platform-tools/adb"
SRC="/sdcard/Android/data/com.tananaev.passportreader/files/"
DEST="/home/hamr/PycharmProjects/zkagent/spikes/m1-zk/fixtures/real/"

if [ -z "$("$ADB" devices | sed -n '2p')" ]; then
  echo "no device on USB — replug the phone and unlock it" >&2
  exit 1
fi

mkdir -p "$DEST"

"$ADB" pull "$SRC" "$DEST"

# adb pull of a directory nests it one level (DEST/files/...) whether or not
# SRC has a trailing slash; flatten so fixtures land directly under DEST.
if [ -d "$DEST/files" ]; then
  mv "$DEST"/files/* "$DEST"/ 2>/dev/null
  rmdir "$DEST/files" 2>/dev/null
fi

echo
echo "ls -la $DEST"
ls -la "$DEST"

echo
for f in "$DEST"*.sod.txt; do
  [ -f "$f" ] || continue
  echo "----- $f -----"
  cat "$f"
  echo
done
