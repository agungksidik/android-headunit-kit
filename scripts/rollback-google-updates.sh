#!/bin/sh
# Put the Google update ban list back and optionally remove Play services and Play Store updates.
#
# Usage:  UNIT=<unit-ip>:5555 sh scripts/rollback-google-updates.sh [--full] [--yes] [backup-dir]
#
#   (no flag)  restore the ban list files from <backup-dir>/unblock/ (default ./backup-<date>)
#   --full     also run pm uninstall-system-updates for com.google.android.gms and com.android.vending
#   --yes      do not ask for confirmation
# Without a backup the ban list is rewritten with the two known entries.
# The unit reboots at the end (cameras off for 40 to 60 seconds): run parked with ACC on.
set -e

FULL=0
YES=0
while [ "$1" = "--full" ] || [ "$1" = "--yes" ]; do
  [ "$1" = "--full" ] && FULL=1
  [ "$1" = "--yes" ] && YES=1
  shift
done
if [ -z "$UNIT" ]; then
  echo "Set UNIT, for example: UNIT=<unit-ip>:5555 sh scripts/rollback-google-updates.sh" >&2
  exit 2
fi
ADB="adb -s $UNIT"
DIR="${1:-./backup-$(date +%Y-%m-%d)}"
F_DATA=/data/nwdappconfig/app/LimitUpdateGmsApkList.ini
F_CACHE=/cache/LimitUpdateGmsApkList.ini
DEFAULT_LIST="com.android.vending
com.google.android.gms"

adb connect "$UNIT" >/dev/null 2>&1 || true
$ADB shell echo ok >/dev/null

if [ "$YES" -ne 1 ]; then
  if [ "$FULL" -eq 1 ]; then
    echo "This restores the ban list AND removes Play services / Play Store updates, then reboots."
  else
    echo "This restores the ban list, then reboots."
  fi
  printf "Continue? [y/N] "
  read ans
  [ "$ans" = "y" ] || { echo "Cancelled."; exit 1; }
fi

restore() { # tag file
  tag="$1"; file="$2"
  if [ -f "$DIR/unblock/$tag.ini" ]; then
    $ADB push "$DIR/unblock/$tag.ini" /data/local/tmp/ban-$tag.ini >/dev/null
    $ADB shell "cat /data/local/tmp/ban-$tag.ini > $file; rm /data/local/tmp/ban-$tag.ini"
    if [ -f "$DIR/unblock/$tag.meta" ]; then
      meta=$(cat "$DIR/unblock/$tag.meta")
      owner="${meta% *}"
      mode="${meta#* }"
      $ADB shell "chown $owner $file; chmod $mode $file"
    fi
    echo "Restored $file from backup"
  else
    printf '%s' "$DEFAULT_LIST" | $ADB shell "cat > $file"
    echo "No backup for $file: wrote the default two entries (check owner and mode with ls -ln)"
  fi
}

restore data "$F_DATA"
restore cache "$F_CACHE"
$ADB shell "cat $F_DATA"

if [ "$FULL" -eq 1 ]; then
  $ADB shell "pm uninstall-system-updates com.google.android.gms; pm uninstall-system-updates com.android.vending" || true
fi

echo "$(date '+%Y-%m-%d %H:%M') ROLLBACK of google update unblock (full=$FULL)." >> "$DIR/changes.txt" 2>/dev/null || true
echo "Rebooting."
$ADB reboot || true
echo "Rollback sent. The unit is back in about a minute."
