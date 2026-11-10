# Launcher (Head Unit Home)

## Problem
The factory Home is heavy and the unit is slow. This launcher is a light replacement: plain Java, no AndroidX, about 120 KB APK. The factory Home stays installed as a fallback.

## Symptoms
- The factory Home takes a long time to draw and uses about 160 MB of RAM.
- You want speed, a music card, four shortcuts and a dock on one screen.

## Features
### Home
- Clock (24 h) and date in the device language.
- Weather from Open-Meteo (no key). The last result is cached and shown at once on start. It refreshes every 30 minutes while Home is visible, at most once per 10 minutes, and when the first GPS fix arrives. The place name comes from the platform Geocoder with administrative prefixes such as "Kecamatan" removed. When the cache is older than 60 minutes a small "updated HH:mm" line appears.
- GPS speed from `GPS_PROVIDER` (1 s updates). It shows "--" when there has been no fix for 5 s.
- The road animation scrolls its center line in proportion to the speed and stops at 0 km/h.
- Turn tilt: above 10 km/h a smoothed bearing change of more than 4 degrees per second tilts the car and the road to the left or right.
- Music card: shows another app only while that app is playing (through its media session, which needs notification access). Otherwise it shows the built-in USB player.
- Volume row on the music card: minus and plus buttons and a level bar that drive the unit's MCU volume. Holding a button repeats every 150 ms.
- Edit and Restart buttons. Restart asks for confirmation and warns when the car is moving.
- Four shortcut tiles (defaults: CarPlay, Android Auto, YouTube, 360 camera). Long-press a tile, or tap it in edit mode, to pick any launchable app.
- Dock: Home, Music, Phone, DSP, Settings, Apps.

### Settings
- Default launcher: lists every Home activity and sets the choice with `cmd package set-home-activity` and `setprop persist.nwd.launcher.default`.
- Android settings, reset shortcuts, rescan music, resume playback automatically (on by default), restart head unit and version.

### All apps
- Six-column grid of every launchable app. The list is cached and rebuilt when a package is added, removed or changed.

### Split screen
- All activities are resizeable. Below 900 dp of width Home switches to a compact layout (clock and weather, road and car, music card, one row of four tiles, dock). Dock labels are hidden below 560 dp.

### Icon and splash
- Adaptive launcher icon with a monochrome variant, and an Android 12 splash screen with the same dark background.

The USB player is described in [Music player](music-player.md).

## How it talks to the factory firmware

| Purpose | Hook |
|---|---|
| Home key | The factory firmware sends the key to the package in system property `persist.nwd.launcher.default`. `cmd package set-home-activity` alone does not change it. |
| Volume | Read `Settings.System` keys `mcu_system_volume` and `mcu_max_volume`. Set it by broadcasting `com.nwd.action.ACTION_REQUEST_SET_SYSTEM_VOLUME` with the int extra `extra_volume`. |
| USB music | Drives are mounted at `/mnt/media_rw/<drive>`. They are not under `/storage` and MediaStore does not list them, so the app reads the files directly. |
| Restart and set default launcher | A small built-in ADB client (`LocalAdb`) talks to the unit's own adbd on `127.0.0.1:5555` and runs `shell:reboot` or the set-home commands. The RSA key is generated on first use and kept in the app's private files. |
| Factory apps | Dock shortcuts open the factory music, phone and audio-settings activities (`.home_horizontalActivity`). |

## Build
```
cd launcher
JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew testReleaseUnitTest assembleRelease
```
The APK is written to `launcher/app/build/outputs/apk/release/app-release.apk` (signed with the debug key). Set `sdk.dir` in `launcher/local.properties` to your Android SDK.

## Install
```
adb install -r launcher/app/build/outputs/apk/release/app-release.apk
adb shell pm grant com.agung.headhome android.permission.ACCESS_FINE_LOCATION
adb shell pm grant com.agung.headhome android.permission.POST_NOTIFICATIONS
adb shell cmd notification allow_listener com.agung.headhome/com.agung.headhome.MediaListener
adb shell cmd package set-home-activity com.agung.headhome/.MainActivity
adb shell setprop persist.nwd.launcher.default com.agung.headhome
```
The first Home press right after installing can still open the factory Home; the second opens the launcher.

## Check it worked
Press Home: the launcher opens. Reboot and press Home again. `adb shell getprop persist.nwd.launcher.default` prints `com.agung.headhome`.

## Rollback
```
adb shell cmd package set-home-activity com.android.launcher/com.android.launcher2.Launcher
adb shell setprop persist.nwd.launcher.default com.android.launcher
```

## Known limits
- Speed and turns come from GPS only. Expect a delay of 1 to 2 s, and turns need more than about 10 km/h. There is no CAN box.
- Restart and the default launcher setting use the unit's local ADB service on port 5555. See [Security](security.md).
- The USB index refresh compares only the root's last modified time and entry count. Changes deep inside a drive need "Rescan music" in Settings.
- Steering-wheel buttons and split screen have not been verified on the unit yet.

## Safety
- Restart is allowed at any speed. Do not use it when you may need the reverse camera.
- Keep the factory Home installed so you can go back.
