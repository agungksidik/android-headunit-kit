#!/bin/sh
# Lift the Google update ban on NWD-style firmware by emptying the ban list files.
#
# Usage:  UNIT=<unit-ip>:5555 sh scripts/unblock-google-updates.sh [--yes] [backup-dir]
#
# What it does:
#   1. Backs up /data/nwdappconfig/app/LimitUpdateGmsApkList.ini and /cache/LimitUpdateGmsApkList.ini
#      (content, owner, mode) into <backup-dir>/unblock/ (default ./backup-<date>).
#   2. Empties both files.
#   3. Reboots the unit and waits for it to come back.
#   4. Prints the log line that confirms the list is empty.
# Afterwards: open Play Store once, then press Home. See docs/google-updates.md.
# Undo: sh scripts/rollback-google-updates.sh [backup-dir]
# Run parked with ACC on. The unit restarts and the cameras are off for 40 to 60 seconds.
set -e

YES=0
if [ "$1" = "--yes" ]; then YES=1; shift; fi
if [ -z "$UNIT" ]; then
  echo "Set UNIT, for example: UNIT=<unit-ip>:5555 sh scripts/unblock-google-updates.sh" >&2
  exit 2
fi
ADB="adb -s $UNIT"
DIR="${1:-./backup-$(date +%Y-%m-%d)}"
F_DATA=/data/nwdappconfig/app/LimitUpdateGmsApkList.ini
F_CACHE=/cache/LimitUpdateGmsApkList.ini
HOST="${UNIT%:*}"
PORT="${UNIT##*:}"

adb connect "$UNIT" >/dev/null 2>&1 || true
if ! $ADB shell "[ -d /data/nwdappconfig/app ]"; then
  echo "/data/nwdappconfig/app is missing: this is not NWD-style firmware. Stop and read docs/access-and-backup.md." >&2
  exit 1
fi

echo "Current ban list in $F_DATA:"
$ADB shell "cat $F_DATA 2>/dev/null; echo" || true

if [ "$YES" -ne 1 ]; then
  printf "Empty both ban list files and reboot the unit now? [y/N] "
  read ans
  [ "$ans" = "y" ] || { echo "Cancelled."; exit 1; }
fi

mkdir -p "$DIR/unblock"
for pair in "data:$F_DATA" "cache:$F_CACHE"; do
  tag="${pair%%:*}"
  file="${pair#*:}"
  if $ADB shell "[ -f $file ]"; then
    $ADB shell "cat $file" > "$DIR/unblock/$tag.ini"
    $ADB shell "stat -c '%u:%g %a' $file" | tr -d '\r' > "$DIR/unblock/$tag.meta"
    echo "Backed up $file ($(cat "$DIR/unblock/$tag.meta"))"
  else
    echo "$file does not exist, nothing to back up"
  fi
done

$ADB shell ": > $F_DATA; [ -f $F_CACHE ] && : > $F_CACHE; true"
echo "Ban list emptied."
echo "$(date '+%Y-%m-%d %H:%M') UNBLOCK: emptied $F_DATA and $F_CACHE. Undo: sh scripts/rollback-google-updates.sh $DIR" >> "$DIR/changes.txt"

echo "Rebooting. The unit is down for about a minute."
$ADB reboot || true
sleep 20
n=0
until nc -z -w 2 "$HOST" "$PORT" >/dev/null 2>&1; do
  n=$((n + 1))
  if [ "$n" -gt 60 ]; then echo "Unit did not come back in 3 minutes." >&2; exit 1; fi
  sleep 3
done
sleep 10
adb connect "$UNIT" >/dev/null 2>&1 || true
echo "Unit is back. Log check:"
$ADB shell "logcat -d | grep -i 'apk limit' | tail -n 3" || true
echo "Next: open Play Store, then press Home so the self-update can install. Wait 5 to 20 minutes."
