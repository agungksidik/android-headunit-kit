# Debloat

## Problem
The unit has about 3.8 GB of RAM and many apps that nobody uses. Memory pressure makes everything slow.

## Symptoms
- Little free RAM, swap in use, apps restarting when you return to them.
- Load average 9 to 11 on 8 cores at idle.

Measured on the tested unit 10 minutes after boot: 3,866 MB RAM, 57 MB free, 762 MB in swap. Largest users (RSS): vendor DSP app 229 MB, vendor music app 227 MB, YouTube 205 MB, vendor Home 163 MB, Play Store about 420 MB across four processes, Play services about 440 MB across four processes, the Google app (search) 262 MB, Maps 130 MB, Gboard 121 MB, a second launcher (`com.android.launcher3`) 72 MB.

## Reading memory correctly
Use the "Free RAM" line of `adb shell dumpsys meminfo`, which includes cached processes. The "free" column of `free -m` counts only completely unused pages and looks alarming on a healthy system.

## Method
1. Back up ([Access and backup](access-and-backup.md)).
2. Pick one package. Disable it:
```
adb shell pm disable-user --user 0 <package>
```
3. Run a health check:
```
for p in <camera-app> <carplay-app> <launcher>; do adb shell pidof $p >/dev/null || echo "$p DOWN"; done
```
4. Test reverse gear (or the camera app), CarPlay or Android Auto, the radio and the steering buttons before the next package.
5. Write the package and its undo (`pm enable <package>`) into `changes.txt`.

The script does steps 2, 3 and 5 for a list file, one package per call, and stops and re-enables a package if a health package goes down:
```
UNIT=<unit-ip>:5555 HEALTH_PKGS="<camera-app> <carplay-app> <launcher>" sh scripts/debloat.sh my-list.txt
```
Use `--dry-run` first.

## List used on the tested unit
Disabled with `pm disable-user --user 0`:

| Package | Reason |
|---|---|
| `com.google.android.googlequicksearchbox` | Google app, 262 MB RSS, no use on a head unit |
| `com.android.chrome` | Owner's choice. WebView is a separate package (`com.android.webview`, check `dumpsys webviewupdate`), so apps that embed web pages keep working |
| `com.netflix.mediaclient` | Owner's choice |
| `com.spotify.music` | Owner's choice |
| `com.nwd.appmall` | Vendor app store, not used |
| `com.nwd.cpumonitor` | Vendor test tool |
| `com.nwd.complete.test` | Vendor factory test |
| `com.google.android.apps.pdfviewer` | Not used |
| `com.google.android.inputmethod.pinyin`, `com.google.android.apps.inputmethod.zhuyin` | Chinese keyboards (see [Keyboard](keyboard.md)) |

Other candidates found but not applied in the tested run: `iflytek` keyboard, `local.activate.test`, `check.appver`, `guidebookskin`. Ask whoever uses the unit about every app they might want (YouTube, Chrome, Netflix, Spotify and NFC apps were owner decisions).

Kept: text-to-speech, Maps, GMS, Play Store, every camera, CAN, radio, Bluetooth, DSP and CarPlay or Android Auto package, and `com.android.launcher3`.

## Never disable
- The reverse or 360 camera app, the CarPlay or Android Auto app (ZLink, CarbitLink, AutoKit), CAN, MCU and kernel services, Bluetooth phone, radio, the DSP app, the steering-key service.
- `com.android.launcher3` when it provides Recents. Check:
```
adb shell "dumpsys activity recents | grep mRecentsComponent"
```
If the component is `com.android.launcher3/...Quickstep...`, disabling it breaks the Recents screen.
- WebView (`com.android.webview`) and GMS or Play Store unless you have a reason and a rollback.

## Check it worked
`dumpsys meminfo` shows more "Free RAM" after the unit has been idle for ten minutes. The health packages are still running.

## Rollback
```
adb shell pm enable <package>
```
The script writes `debloat-undo-<date>.sh` with one `pm enable` line per package it disabled.

## Safety
- One package per call. A batch makes it impossible to see which one broke something.
- A package that is disabled is still on the disk, so nothing is lost.
- Right after a Play update, high load is dex2oat work, not a fault. Wait before judging.
