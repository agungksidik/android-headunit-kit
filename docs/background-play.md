# Background play

## Problem
Music apps stop or restart when you switch to another screen.

## Symptoms
- NewPipe or another player stops when the screen changes.
- An app is killed by the vendor's background manager.
- YouTube restarts itself in the background.

## Cause
The firmware has its own app killer. Its whitelist is `/data/nwdappconfig/app/BackgroundManager.ini`, one `white:<package>` line per allowed app, read at boot. Android's battery optimizations (deviceidle, appops) add further limits.

## Fix
### NewPipe (YouTube without a Google login)
NewPipe works by bypassing YouTube's terms of service. Get it from its official GitHub release (`TeamNewPipe/NewPipe`), compare the checksum with the release digest, then:
```
adb install -r NewPipe_*.apk
adb shell pm grant org.schabi.newpipe android.permission.POST_NOTIFICATIONS
```
In NewPipe's settings choose the background player for the "minimize on exit" behaviour. The stored preference is `minimize_on_exit_key = minimize_on_exit_background_key`; on the tested unit it was already set.

### Vendor app killer whitelist
```
adb shell "cp /data/nwdappconfig/app/BackgroundManager.ini /data/local/tmp/BackgroundManager.ini.bak"
adb shell "tail -c 1 /data/nwdappconfig/app/BackgroundManager.ini | od -c"
```
If the last character is not a newline, add one. Then:
```
adb shell "echo 'white:org.schabi.newpipe' >> /data/nwdappconfig/app/BackgroundManager.ini"
adb shell "echo 'white:com.agung.headhome' >> /data/nwdappconfig/app/BackgroundManager.ini"
```
Use the package names of the apps you want to keep alive. The file is read at boot, so restart the unit parked.

### Android battery rules
```
adb shell dumpsys deviceidle whitelist +org.schabi.newpipe
adb shell cmd appops set org.schabi.newpipe RUN_ANY_IN_BACKGROUND allow
```

### YouTube
YouTube restarts itself in the background because Play services wakes it. appops `ignore` did not stop that on the tested unit (it opened normally but kept restarting), so the restriction was reverted:
```
adb shell cmd appops set com.google.android.youtube RUN_ANY_IN_BACKGROUND allow
adb shell cmd appops set com.google.android.youtube RUN_IN_BACKGROUND allow
```

## Check it worked
Start playback in NewPipe, press Home, open another app. The audio continues and a media notification stays. After a reboot `grep white: /data/nwdappconfig/app/BackgroundManager.ini` still lists the package.

## Rollback
```
adb shell "cat /data/local/tmp/BackgroundManager.ini.bak > /data/nwdappconfig/app/BackgroundManager.ini"
adb shell dumpsys deviceidle whitelist -org.schabi.newpipe
adb uninstall org.schabi.newpipe
```

## Safety
- Keep a copy of the ini file before editing and keep its owner and mode.
- Whitelisting many apps brings the memory problem back. Add only what you use.
