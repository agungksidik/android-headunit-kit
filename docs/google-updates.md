# Google updates

## Problem
Google Play services and Play Store cannot update on NWD-style firmware.

## Symptoms
- An "Update Google Play services" message that never finishes.
- Play Store updates fail with `INSTALL_FAILED_INTERNAL_ERROR: Install the APK has been banned`.
- Maps asks for updated Play services.

## Cause
`services.jar` (PackageManagerService.limitUpdateApks) reads `${ro.nwd.config.path}/app/LimitUpdateGmsApkList.ini`, which is `/data/nwdappconfig/app/LimitUpdateGmsApkList.ini`. Any package listed in it fails to install with the "banned" message. A copy of the file also sits in `/cache/`. The factory copy in `/system/config/app/` is empty. On the tested unit the live file listed `com.android.vending` and `com.google.android.gms`, and its timestamp is earlier than the unit's first setup, so it was most likely written by the vendor's activation app.

Reasons for the ban are not documented. Likely ones are device certification, memory use and the fact that CarPlay and Android Auto do not need Play services. Lifting the ban means accepting that Google may show "device not certified" and that RAM use rises.

## Fix
Back up first ([Access and backup](access-and-backup.md)), then either run the script:
```
UNIT=<unit-ip>:5555 sh scripts/unblock-google-updates.sh
```
or do it by hand:
```
adb shell "ls -ln /data/nwdappconfig/app/LimitUpdateGmsApkList.ini /cache/LimitUpdateGmsApkList.ini"
adb shell "cat /data/nwdappconfig/app/LimitUpdateGmsApkList.ini"
adb pull /data/nwdappconfig/app/LimitUpdateGmsApkList.ini
adb shell ": > /data/nwdappconfig/app/LimitUpdateGmsApkList.ini; : > /cache/LimitUpdateGmsApkList.ini"
adb reboot
```
After the unit is back:
1. `adb shell "logcat -d | grep -i 'apk limit'"` shows `no any apk limit update gms apk`.
2. Open Play Store, then press Home. Play Store installs its self-update only after it is closed (log: `SU: Self-update ready to be installed, waiting for market to close`).
3. Play services then updates by itself, usually within 5 to 20 minutes. It is followed by heavy dex2oat work, so the load average rises to about 12 to 18 for a while. This is normal; wait for it.

On the tested unit Play Store went from 46.8.29 to 54.0.09 and Play services from 25.24.31 to 26.43.30. The ban list stayed empty across the reboot, but the activation app touches the `/cache` copy (chmod), so re-check after later reboots.

## Stop the update storm
To keep Play Store from updating in the background:
```
adb shell "am force-stop com.android.vending"
adb shell "cmd appops set com.android.vending RUN_ANY_IN_BACKGROUND ignore"
adb shell "cmd appops set com.android.vending RUN_IN_BACKGROUND ignore"
adb shell "am set-standby-bucket com.android.vending restricted"
```
Undo:
```
adb shell "cmd appops set com.android.vending RUN_ANY_IN_BACKGROUND allow"
adb shell "cmd appops set com.android.vending RUN_IN_BACKGROUND allow"
adb shell "am set-standby-bucket com.android.vending active"
```

## Check it worked
```
adb shell "dumpsys package com.android.vending | grep versionName"
adb shell "dumpsys package com.google.android.gms | grep versionName"
```
Both versions are higher than before. The camera app, CarPlay or Android Auto app, DSP app and your launcher are still running (`pidof <package>`).

## Rollback
```
UNIT=<unit-ip>:5555 sh scripts/rollback-google-updates.sh          # restores the ban list
UNIT=<unit-ip>:5555 sh scripts/rollback-google-updates.sh --full   # also removes Play updates
```
By hand: restore both files from the backup (same owner and mode, see the `ls -ln` output you saved), run `pm uninstall-system-updates com.google.android.gms` and `pm uninstall-system-updates com.android.vending` if you want the factory versions back, then `adb reboot`.

## Safety
- The reboot turns the cameras off for 40 to 60 seconds. Do it parked.
- Camera, CarPlay, Android Auto, radio and DSP do not depend on Play services, but check them after the update.
- Do not install Play services or Play Store APKs from mirror sites. The update comes from Google through Play Store itself.
