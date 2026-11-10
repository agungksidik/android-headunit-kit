#!/bin/sh
# Back up the state of an Android head unit before changing anything.
#
# Usage:  UNIT=<unit-ip>:5555 sh scripts/backup.sh [output-dir]
#
# Needs: adb on this computer, the unit parked with ACC on and reachable over Wi-Fi ADB.
# Writes into ./backup-<date>/ unless an output directory is given:
#   getprop, package lists, settings (system, secure, global), meminfo, load,
#   the vendor ini files in /data/nwdappconfig/app/ and /cache, DSP and keyboard preference files
#   (when they exist), and an empty changes.txt to log every later change with its undo.
# Nothing on the unit is modified.
set -e

if [ -z "$UNIT" ]; then
  echo "Set UNIT, for example: UNIT=<unit-ip>:5555 sh scripts/backup.sh" >&2
  exit 2
fi
ADB="adb -s $UNIT"
OUT="${1:-./backup-$(date +%Y-%m-%d)}"

adb connect "$UNIT" >/dev/null 2>&1 || true
$ADB shell echo ok >/dev/null

mkdir -p "$OUT/vendor"
echo "Backing up $UNIT into $OUT"

$ADB shell getprop > "$OUT/getprop.txt"
$ADB shell pm list packages -f > "$OUT/packages-all.txt"
$ADB shell pm list packages -d > "$OUT/packages-disabled.txt"
$ADB shell pm list packages -3 > "$OUT/packages-user.txt"
for ns in system secure global; do
  $ADB shell settings list "$ns" > "$OUT/settings-$ns.txt"
done
$ADB shell dumpsys meminfo > "$OUT/meminfo.txt"
$ADB shell 'cat /proc/loadavg; cat /proc/uptime' > "$OUT/load-uptime.txt"
$ADB shell dumpsys activity recents 2>/dev/null | grep mRecentsComponent > "$OUT/recents-component.txt" || true
$ADB shell dumpsys webviewupdate 2>/dev/null > "$OUT/webviewupdate.txt" || true

# Vendor config files. The directory exists only on NWD-style firmware.
$ADB shell 'ls -ln /data/nwdappconfig/app/ 2>/dev/null' > "$OUT/vendor/ls-nwdappconfig.txt" || true
$ADB shell 'ls -ln /cache/ 2>/dev/null' > "$OUT/vendor/ls-cache.txt" || true
FILES=$($ADB shell 'ls /data/nwdappconfig/app/*.ini /cache/*.ini 2>/dev/null' | tr -d '\r' || true)
for f in $FILES; do
  name=$(echo "$f" | sed 's|^/||; s|/|_|g')
  $ADB shell "cat '$f'" > "$OUT/vendor/$name"
  echo "  saved $f"
done

# Optional preference files (root is needed; skipped silently when missing).
for f in /data/data/com.nwd.audioset/shared_prefs/DspData.xml \
         /data/user_de/0/helium314.keyboard/shared_prefs/helium314.keyboard_preferences.xml; do
  name=$(echo "$f" | sed 's|^/||; s|/|_|g')
  if $ADB shell "[ -f '$f' ]" 2>/dev/null; then
    $ADB shell "cat '$f'" > "$OUT/vendor/$name"
    echo "  saved $f"
  fi
done

if [ ! -f "$OUT/changes.txt" ]; then
  echo "# changes.txt: one line per change, with the undo command. Started $(date +%Y-%m-%d)." > "$OUT/changes.txt"
fi

echo "Done. Keep $OUT somewhere safe. Log every later change in $OUT/changes.txt."
