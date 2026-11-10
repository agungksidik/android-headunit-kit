# Guides

One guide per topic. Each has the problem, the symptoms, the fix as copy-paste ADB shell commands, how to check it worked, how to roll back and safety notes.

Commands assume one unit is connected over ADB. With several devices add `-s <unit-ip>:5555` after `adb`.

## Read first

1. [Access and backup](access-and-backup.md): connect, identify the firmware, back up. Do this before anything else.
2. [Security](security.md): ADB over Wi-Fi gives root on this firmware.

## Guides

| Guide | Problem it solves |
|---|---|
| [Google updates](google-updates.md) | "Update Google Play services" loops, Play Store updates fail with "Install the APK has been banned" |
| [Debloat](debloat.md) | Slow unit, little free RAM |
| [Keyboard](keyboard.md) | Typing lags, a long press types a number instead of a letter |
| [EQ tuning](eq-tuning.md) | Bass distorts at high volume |
| [Background play](background-play.md) | Music apps stop when the screen changes |
| [Launcher](launcher.md) | A light Home screen with speed, music and shortcuts |
| [Music player](music-player.md) | Playing USB music inside the launcher |
| [Offline maps](offline-maps.md) | Navigation without mobile data |
| [DVR and load](dvr-and-load.md) | High CPU load, 360 camera loop recording |

## Safety rules

- Park the car with ACC on. With the key off the unit sleeps and drops Wi-Fi within seconds.
- Back up first, then change one thing at a time. Write every change and its undo into one `changes.txt`.
- Never disable or force-stop: the reverse or 360 camera app, the CarPlay or Android Auto app (for example ZLink, CarbitLink, AutoKit), CAN, MCU and kernel services, Bluetooth phone, radio, the DSP app, the steering-key service. Also keep `com.android.launcher3` when it provides Recents (see [Debloat](debloat.md)).
- Do not restart the unit when you may need to reverse: the cameras are off for 40 to 60 seconds.
- Download apps only from the project's official GitHub release and compare the checksum with the release digest. Do not install Google Play services or Play Store APKs from mirror sites.
- Do not tap the screen while someone is using it. Ask for a quiet minute first.
- These guides were tested on one unit (see the root README). Other firmware may use different files. If `/data/nwdappconfig/app/` is missing, find the vendor's equivalent before applying a vendor hook.

## Scripts

`../scripts/` has `backup.sh`, `unblock-google-updates.sh`, `rollback-google-updates.sh` and `debloat.sh`. Each prints its usage at the top of the file and needs `UNIT=<unit-ip>:5555`.
