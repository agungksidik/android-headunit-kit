# Security

## Problem
On the tested firmware the ADB service on port 5555 runs as root and accepted a freshly generated key with no confirmation prompt. Anyone on the same Wi-Fi network can open a root shell on the unit.

## Symptoms
- `adb connect <unit-ip>:5555` works from a computer that was never authorized.
- `adb shell id` shows uid 0.

## What this means
- A phone hotspot shared with passengers, or a public Wi-Fi network, gives every device on it the same access.
- The launcher in this kit also uses the local ADB service on `127.0.0.1:5555` for its restart button and for setting the default launcher. That path stays on the unit itself.

## Fix
Use one of these:
1. Keep the unit only on your own hotspot, protected by a strong password, and do not share that hotspot.
2. Turn Wi-Fi ADB off when you are not working on the unit. Only do this if you have another way back in, for example USB debugging or the vendor settings screen:
```
adb shell "setprop service.adb.tcp.port -1; stop adbd; start adbd"
```
This also disables the launcher's restart button and its default-launcher picker, because they use the local ADB service. Turn it back on from your other access path with `setprop service.adb.tcp.port 5555` followed by `stop adbd; start adbd`.

## Check it worked
From a computer on the same network, `adb connect <unit-ip>:5555` fails after step 2.

## Rollback
Set the property back to 5555 and restart adbd as shown above.

## Safety
- Test the other way in before you turn Wi-Fi ADB off.
- Whether the property survives a reboot depends on the firmware. Check after the next restart.
