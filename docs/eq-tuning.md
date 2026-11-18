# EQ tuning

## Problem
Bass distorts at high volume.

## Symptoms
- Crackle or buzz on bass-heavy tracks above about volume 25 (of 40).
- The vendor DSP app shows every band boosted by +4 to +6 dB.

## Cause
- Loudness is on by default in the DSP app (`key_bass_loud` is the Loudness switch).
- Boosting every band is a volume boost: the stock amplifier clips at high volume.
- Sub-bass (20 to 40 Hz) pushes small speakers past their limit. The tested setup has 6.5 inch component speakers in front, coaxial speakers behind and no subwoofer.

## Fix
Tune a preset that is not yours. The DSP app (`com.nwd.audioset`) has presets such as Custom and Custom Two. Ask which one is in use and leave it alone; record the old values of the one you edit.

1. Loudness off.
2. Bring 20 to 40 Hz down.
3. Keep 63 to 80 Hz.
4. Set the rest to a smooth curve.

### The 32-band curve used on the tested unit
The DSP shows 32 band cells from 20 Hz to 20 kHz, slider range -12 to +12 dB, Q 2.0 on all bands. Cell 1 is 20 Hz and cell 32 is 20 kHz. "Factory Custom" is what the Custom preset held on the tested unit before tuning. The curve was set for 6.5 inch component speakers in front and coaxial speakers behind; adjust it for yours.

| Hz | Tuned (dB) | Factory Custom (dB) | Reason |
|---|---|---|---|
| 20 | 0 | 0 | Sub-bass cut: no subwoofer, and these frequencies cause clipping |
| 25 | 0 | 0 | Sub-bass cut: no subwoofer, and these frequencies cause clipping |
| 32 | 1 | 2 | Sub-bass cut: no subwoofer, and these frequencies cause clipping |
| 40 | 3 | 2 | Bass punch |
| 50 | 5 | 1 | Bass punch |
| 63 | 6 | 1 | Bass punch, peak at 63 to 80 Hz |
| 80 | 6 | 0 | Bass punch, peak at 63 to 80 Hz |
| 100 | 5 | -1 | Bass punch |
| 125 | 4 | -1 | Bass punch |
| 160 | 3 | -1 | Cabin boom and mud cut |
| 200 | 2 | -1 | Cabin boom and mud cut |
| 250 | 2 | -1 | Cabin boom and mud cut |
| 315 | 3 | 1 | Vocals, flat at +3 to +4 dB |
| 400 | 3 | 1 | Vocals, flat at +3 to +4 dB |
| 500 | 4 | 2 | Vocals, flat at +3 to +4 dB |
| 630 | 4 | 2 | Vocals, flat at +3 to +4 dB |
| 800 | 4 | 2 | Vocals, flat at +3 to +4 dB |
| 1k | 4 | 1 | Vocals, flat at +3 to +4 dB |
| 1.2k | 4 | 1 | Vocals, flat at +3 to +4 dB |
| 1.6k | 4 | 1 | Vocals, flat at +3 to +4 dB |
| 2k | 3 | 1 | Softened for bright tweeters |
| 2.5k | 3 | 0 | Softened for bright tweeters |
| 3.1k | 2 | 0 | Softened for bright tweeters, dip at 3.1k |
| 4k | 3 | 0 | Softened for bright tweeters |
| 5k | 4 | 0 | Detail and air |
| 6.3k | 4 | 0 | Detail and air |
| 8k | 5 | 1 | Detail and air |
| 10k | 5 | 2 | Detail and air |
| 12.5k | 5 | 3 | Detail and air |
| 16k | 4 | 4 | Gentle roll-off |
| 18k | 3 | 6 | Gentle roll-off |
| 20k | 2 | 8 | Gentle roll-off |

As one list: `0,0,1,3,5,6,6,5,4,3,2,2,3,3,4,4,4,4,4,4,3,3,2,3,4,4,5,5,5,4,3,2`.

### Entering values
The EQ cells cannot be typed into. Tap a band's gain cell, then use the + and - buttons. Coordinates on a 1280x720 screen (tested unit):
- Band cell i (0 to 31): x = 40 + 39 * i, y = 398.
- + button: (1160, 509). - button: (1160, 577).
- Preset buttons: Custom (538, 578), Custom Two (736, 578). Loudness: (945, 578).
```
adb shell input tap 538 578        # select the preset you are tuning
adb shell input tap $((40 + 39 * 0)) 398   # select band 1
adb shell input tap 1160 577       # one step down
adb shell screencap -p /sdcard/dsp.png && adb pull /sdcard/dsp.png
```
Take a screenshot after each group of changes and compare it with the table. Do not edit `DspData.xml`; it uses a different scale.

## Check it worked
Play a bass-heavy track at the highest volume you use. The crackle is gone. The screenshot shows the values from the table and Loudness off.

## Rollback
Enter the old values you recorded in `changes.txt`, or select your other preset. The tested unit's Custom Two preset was never changed.

## Safety
- Do not touch the screen while the car is moving. Tune parked.
- If a source is quiet after the change, raise the volume instead of boosting every band. Per-source gains also exist in the factory menu (`mcu_gain_*` settings).

## Custom Sound

Custom Sound is the sound screen of the Custom Home app (dock "DSP", or the "Sound" entry in All Apps). It writes to the same DSP chip as the factory DSP app and replaces the tapping in [Entering values](#entering-values) with a curve, steppers and presets.

How it works:
- Five simple controls (Level, Bass, Clarity, Vocal, Treble) shape a 32-band curve. "32-band mode" shows one bar per band with a selected band, plus and minus per band, "All bands" (moves the whole curve) and "Flatten".
- Gains are whole dB from -12 to +12, the same scale as the factory app. The 32 centers are the table above and Q is 2.0 on every band.
- Every change sends all 32 bands in one packet after 120 ms, through the same call the factory app uses (`android.os.Hardware.setDSP_ak7604_status` with a hex string). The chip value is `(int)((12 + dB) * 30 / 24)`: 0 dB is 15 and +6 dB is 22. The 30 comes from `dsp3_eq_max` in `/data/nwdappconfig/app/UartConfig.ini` when that file is readable.
- Presets: styles Flat High, Punchy, Vocal, Flat and Night, and genres Koplo, Dangdut, Akustik, Rock, Jazz, DJ / EDM, Pop and Serba Bisa are built in (table below). "Save as..." stores up to 10 own presets (rename and delete in the "Presets" sheet) as JSON in the app's files. The last setting is sent again after every boot.
- Anti-clip: at volume 22 and above the bands below 125 Hz go down 1 dB per 2 volume steps (at most 4 dB), and no band stays above +10 dB. Smart loudness adds +2 dB at 50 to 125 Hz at volume 12 and below. Both are applied only when sending; they are not saved into a preset. The volume is the MCU volume.
Built-in presets (Level, Bass, Clarity, Vocal, Treble; the curve math is the same for all, and every curve stays at +11 dB or lower, and anti-clip limits what is sent to +10):

| Preset | Level | Bass | Clarity | Vocal | Treble | Character |
|---|---|---|---|---|---|---|
| Flat High | 8 | 0 | 0 | 0 | 0 | Flat +8, loud |
| Punchy | 5 | 3 | 1 | 1 | 2 | Bass and sparkle |
| Vocal | 5 | 1 | 1 | 3 | 1 | Clear voices |
| Flat | 0 | 0 | 0 | 0 | 0 | As is |
| Night | 2 | 2 | 1 | 1 | -2 | Easy on the ears |
| Koplo | 5 | 4 | 2 | 3 | 2 | Kendang and flute |
| Dangdut | 5 | 3 | 2 | 3 | 1 | Warm, clear vocals |
| Akustik | 5 | 1 | 2 | 2 | 3 | Plucking detail |
| Rock | 5 | 4 | 2 | 0 | 4 | Bass and cymbals |
| Jazz | 4 | 2 | 1 | 1 | 2 | Soft and warm |
| DJ / EDM | 4 | 5 | 2 | 0 | 4 | Hardest bass |
| Pop | 5 | 2 | 1 | 2 | 2 | Balanced, bright |
| Serba Bisa | 5 | 3 | 2 | 2 | 2 | Fits every genre |

Serba Bisa ("fits every genre") is made for bass you can feel at low volume and no distortion at high volume, and it carries its own volume behavior: the 40 to 125 Hz bands get +3 dB at volume 8 or lower, sliding down to 0 at volume 20, and anti-clip always applies (bands below 125 Hz cut from volume 22, every band at +10 dB or lower). It ignores the two switches; while it is active the screen shows them as on and disabled. The curve is sent again when the MCU volume changes. A saved setting that used the old preset Bicara becomes Serba Bisa.

The levels are a little higher than in the first version so the presets are not too quiet; Flat stays at 0 as the reference. The headroom badge is green up to +8 dB, amber above +8 and red above +11. The preset row scrolls sideways; the "Presets" sheet lists the built-ins grouped as styles and genres, then your own presets.

- A/B sends a flat curve while B is active.
- Listening position sends the factory fader and balance values: Driver (3, 11), Front (3, 7), All (7, 7), Rear (12, 7). Driver assumes a right-hand-drive car (it is the factory "right" preset); Front is not a factory preset.
- Custom Sound sends the factory loudness flag as off, because it has its own smart loudness.

Notes and limits:
- The first packet after start also turns the factory loudness off. Your factory presets are not changed, and the factory DSP app can still be opened with "Factory DSP".
- If the DSP does not answer, a toast "DSP not responding" appears and the screen keeps its state. Android blocks the reflection call to a hidden framework method (the log says "Accessing hidden method ... denied"). Allow it once from a computer, then open Custom Sound again:
```
adb shell 'settings put global hidden_api_blacklist_exemptions "Landroid/os/Hardware;"'
```
The app shows a one-time dialog with this command when it sees the block. With this setting the chip accepted the packets on the tested unit.
- Nothing is sent, and no preset is highlighted, until you pick a preset or change a control for the first time. The boot resend only happens after that.
- The packet building is tested against a copy of the decompiled factory functions. Whether the chip accepts packets from a normal app has to be verified on the unit.
- Rollback: select your factory preset in the factory DSP app (it sends its own values again), or reboot the unit and do not open Custom Sound. Delete `sound_state.json` in the app's files (or clear the app data) to stop the boot resend.
