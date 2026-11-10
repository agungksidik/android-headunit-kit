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
