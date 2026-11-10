#!/bin/sh
# Disable packages one at a time, with a health check after each, and write an undo file.
#
# Usage:  UNIT=<unit-ip>:5555 HEALTH_PKGS="<pkg> <pkg>" sh scripts/debloat.sh <list-file> [--dry-run] [--yes]
#
# list-file: one package name per line; blank lines and lines starting with # are ignored.
# HEALTH_PKGS: space-separated packages that must keep running (camera app, CarPlay/Android Auto app,
#   launcher). After each disable the script checks them with pidof. If one is down, the package just
#   disabled is enabled again and the script stops.
# Each package gets its own pm disable-user --user 0 call. Nothing is uninstalled.
# Undo file: ./debloat-undo-<date>.sh (pm enable lines). Run it with the same UNIT set.
# A few packages are refused on purpose (see PROTECTED below). Read docs/debloat.md first.
set -e

LIST=""
DRY=0
YES=0
for a in "$@"; do
  case "$a" in
    --dry-run) DRY=1 ;;
    --yes) YES=1 ;;
    *) LIST="$a" ;;
  esac
done
if [ -z "$UNIT" ] || [ -z "$LIST" ] || [ ! -f "$LIST" ]; then
  echo "Usage: UNIT=<unit-ip>:5555 HEALTH_PKGS=\"<pkg> <pkg>\" sh scripts/debloat.sh <list-file> [--dry-run] [--yes]" >&2
  exit 2
fi
ADB="adb -s $UNIT"
UNDO="./debloat-undo-$(date +%Y-%m-%d).sh"
PROTECTED="com.android.launcher3 com.android.webview com.google.android.gms com.android.vending com.android.settings android"

adb connect "$UNIT" >/dev/null 2>&1 || true
$ADB shell echo ok >/dev/null

if [ -z "$HEALTH_PKGS" ]; then
  echo "Warning: HEALTH_PKGS is empty, so no health check runs after each change." >&2
fi

health_ok() {
  for p in $HEALTH_PKGS; do
    if ! $ADB shell "pidof $p" </dev/null >/dev/null 2>&1; then
      echo "  health check: $p is DOWN"
      return 1
    fi
  done
  return 0
}

if [ "$DRY" -eq 0 ] && [ ! -f "$UNDO" ]; then
  {
    echo "#!/bin/sh"
    echo "# Undo for scripts/debloat.sh, created $(date +%Y-%m-%d). Needs UNIT set."
    echo "ADB=\"adb -s \$UNIT\""
  } > "$UNDO"
fi

if [ "$DRY" -eq 0 ] && [ "$YES" -ne 1 ]; then
  echo "Packages to disable:"
  grep -v '^[[:space:]]*\(#\|$\)' "$LIST" | sed 's/^/  /'
  printf "Disable them one by one now? [y/N] "
  read ans
  [ "$ans" = "y" ] || { echo "Cancelled."; exit 1; }
fi

if [ -n "$HEALTH_PKGS" ] && ! health_ok; then
  echo "A health package is not running before any change. Fix that first." >&2
  exit 1
fi

TMP=$(mktemp)
trap 'rm -f "$TMP"' EXIT
grep -v '^[[:space:]]*\(#\|$\)' "$LIST" > "$TMP" || true

while read -r pkg rest; do
  case " $PROTECTED " in
    *" $pkg "*) echo "$pkg: protected, skipped"; continue ;;
  esac
  if ! $ADB shell "pm list packages" </dev/null | tr -d '\r' | grep -qx "package:$pkg"; then
    echo "$pkg: not installed, skipped"
    continue
  fi
  if $ADB shell "pm list packages -d" </dev/null | tr -d '\r' | grep -qx "package:$pkg"; then
    echo "$pkg: already disabled, skipped"
    continue
  fi
  if [ "$DRY" -eq 1 ]; then
    echo "$pkg: would disable"
    continue
  fi
  $ADB shell "pm disable-user --user 0 $pkg" </dev/null | tr -d '\r'
  echo "\$ADB shell pm enable $pkg" >> "$UNDO"
  sleep 3
  if [ -n "$HEALTH_PKGS" ] && ! health_ok; then
    echo "$pkg: enabling it again and stopping."
    $ADB shell "pm enable $pkg" </dev/null | tr -d '\r'
    sed -i.bak "\$d" "$UNDO" && rm -f "$UNDO.bak"
    exit 1
  fi
  echo "$pkg: disabled, health ok"
done < "$TMP"

echo "Done. Undo file: $UNDO"
