# Keyboard

## Problem
Typing lags, and sometimes a letter turns into a number.

## Symptoms
- Each text field takes a second to show the keyboard.
- When the unit stalls, a normal key press is read as a long press and types the number or symbol on that key.

## Cause
With RAM nearly full, Android stops the keyboard in the background and cold-starts it for each text field. Several input methods were also enabled on the tested unit (a Chinese vendor keyboard, Gboard, Google Pinyin, Zhuyin and Google voice typing). Long-press timeouts of 300 to 400 ms fire during short stalls.

## Fix
### 1. Install HeliBoard from its GitHub release
HeliBoard (`helium314.keyboard`) is open source and smaller than Gboard. Get the release URL and digest, compare the checksum, then install:
```
gh api repos/HeliBorg/HeliBoard/releases/latest
shasum -a 256 HeliBoard-*-release.apk
adb install -r HeliBoard-*-release.apk
adb shell ime enable helium314.keyboard/.latin.LatinIME
adb shell ime set helium314.keyboard/.latin.LatinIME
adb shell cmd package compile -m speed -f helium314.keyboard
```
Install only if the checksum matches the release digest.

### 2. Turn the popup off and lengthen the long press
Preferences live in `/data/user_de/0/helium314.keyboard/shared_prefs/helium314.keyboard_preferences.xml`. Open HeliBoard once first so the file exists. Edit it while the app is stopped:
```
adb shell "cp /data/user_de/0/helium314.keyboard/shared_prefs/helium314.keyboard_preferences.xml /data/local/tmp/heli_prefs.bak"
adb shell "am force-stop helium314.keyboard"
adb pull /data/user_de/0/helium314.keyboard/shared_prefs/helium314.keyboard_preferences.xml
```
In the pulled file set `popup_on` to `false` and `key_longpress_timeout` to `700` (the default was 300). If a key is missing, add a line such as `<int name="key_longpress_timeout" value="700" />` before `</map>`. Push it back and restore the owner:
```
adb push helium314.keyboard_preferences.xml /data/local/tmp/heli_prefs.new
adb shell "cat /data/local/tmp/heli_prefs.new > /data/user_de/0/helium314.keyboard/shared_prefs/helium314.keyboard_preferences.xml"
adb shell 'U=$(stat -c %u /data/user_de/0/helium314.keyboard); chown $U:$U /data/user_de/0/helium314.keyboard/shared_prefs/helium314.keyboard_preferences.xml'
adb shell "ime set helium314.keyboard/.latin.LatinIME"
```
Force-stopping the active keyboard makes Android fall back to Gboard, so run `ime set` again at the end.

### 3. System long-press delay
```
adb shell "settings put secure long_press_timeout 600"
```
The tested unit had 400.

### 4. Remove the keyboards you do not use
```
adb shell ime disable com.google.android.inputmethod.pinyin/<service>
adb shell pm disable-user --user 0 com.google.android.inputmethod.pinyin
adb shell pm disable-user --user 0 com.google.android.apps.inputmethod.zhuyin
```
List input methods with `adb shell ime list -a`. Keep Gboard installed as a fallback.

## Check it worked
```
adb shell settings get secure default_input_method
adb shell settings get secure long_press_timeout
```
The first prints `helium314.keyboard/.latin.LatinIME`, the second prints 600. Tap a text field: the keyboard appears quickly and a normal tap no longer types numbers.

## Rollback
```
adb shell settings put secure long_press_timeout 400
adb shell ime set com.google.android.inputmethod.latin/com.android.inputmethod.latin.LatinIME
adb shell pm enable <package>
adb shell cmd package compile --reset helium314.keyboard
adb uninstall helium314.keyboard
```
Restore the preference file from `/data/local/tmp/heli_prefs.bak` if you want the old HeliBoard settings.

## Safety
- Download only from the official release and check the digest.
- Keep one working keyboard enabled at all times, or you cannot type a Wi-Fi password.
