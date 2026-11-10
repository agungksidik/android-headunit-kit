# Access and backup

## Problem
Every other guide changes system files or settings. You need a working ADB connection and a copy of the original state to return to.

## Symptoms that you need this guide
- You have never connected to the unit with `adb`.
- A change went wrong and you have no record of the old value.

## Connect
1. Park the car, ACC on. Put the unit on your own Wi-Fi hotspot (see [Security](security.md)).
2. Enable developer options and ADB over Wi-Fi in the unit's settings. The port used by the tested unit is 5555.
3. From your computer:
```
adb connect <unit-ip>:5555
adb shell id
```
On the tested unit `id` shows uid 0 (root) and no confirmation prompt appears.

Two practical limits:
- A single `adb shell` call that takes longer than about 120 seconds times out. Send `reboot` as its own call, then wait with a loop such as `until nc -z <unit-ip> 5555; do sleep 3; done`.
- `uiautomator dump` works for finding controls in vendor apps. `screencap -p /sdcard/s.png` plus `adb pull /sdcard/s.png` takes a screenshot.

## Identify the firmware
```
adb shell getprop ro.build.fingerprint
adb shell getprop ro.build.display.id
adb shell ls /data/nwdappconfig/app/
```
The tested firmware reports `t527_demo_car`, an Allwinner demo board fingerprint, so the unit is not Google certified. If `/data/nwdappconfig/app/` exists the firmware is NWD style and the vendor hooks in these guides apply. If it does not exist, look for the vendor's equivalent (search the vendor apps and `services.jar` for the symptom text) before using any hook.

## Back up
```
UNIT=<unit-ip>:5555 sh scripts/backup.sh
```
The script writes `./backup-<date>/` with `getprop`, package lists, `settings list system|secure|global`, `dumpsys meminfo`, the vendor `.ini` files in `/data/nwdappconfig/app/` and `/cache`, and an empty `changes.txt`.

Manual equivalent for the most important pieces:
```
adb shell getprop > getprop.txt
adb shell pm list packages -f > packages.txt
adb shell settings list secure > settings-secure.txt
adb shell dumpsys meminfo > meminfo.txt
adb shell cat /data/nwdappconfig/app/LimitUpdateGmsApkList.ini > LimitUpdateGmsApkList.ini
```

## Log every change
Append one line per change to `changes.txt`, with the date, what you changed and the undo command, for example:
```
2026-11-10 keyboard: settings put secure long_press_timeout 400 -> 600. Undo: settings put secure long_press_timeout 400
```

## Check it worked
`backup-<date>/` contains non-empty `getprop.txt` and `packages-all.txt`, and `changes.txt` exists.

## Rollback
Nothing to roll back: the backup only reads from the unit.

## Safety
- Keep the backup folder off the unit.
- The backup contains device identifiers and settings. Do not publish it.
