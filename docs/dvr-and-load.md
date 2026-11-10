# DVR and load

## Problem
High CPU load and a 360 camera that records to a USB drive.

## Symptoms
- Load average of 6 to 9 on 8 cores at idle.
- 12 to 18 during updates.
- A USB drive that is always busy, or wears out.

## Facts from the tested unit
- The 360 camera DVR costs about 0.6 of one core continuously and a small amount of RAM. It was left on.
- Typical idle load is 6 to 9, higher right after a Play update because of dex2oat. It settles afterwards.
- One USB drive may be the 360 DVR loop-recording drive (a folder named `ivicar` on the drive). The music player scans all drives under `/mnt/media_rw` and ignores drives with no audio, so the DVR drive adds nothing to the music library.

## Fix
- Do not judge load right after an update. Wait for dex2oat to finish.
- Check what is running:
```
adb shell cat /proc/loadavg
adb shell "top -b -n 1 | head -n 20"
adb shell dumpsys meminfo | grep -i "Free RAM"
```
- Use a separate USB drive for music and another for the DVR.
- For the DVR drive use storage made for continuous writing ("high endurance" cards or drives). Loop recording rewrites the same space all day, and ordinary cards wear out.

## Check it worked
`/proc/loadavg` settles after updates and background work. The DVR drive keeps its recent clips.

## Rollback
Nothing to roll back. If you turn the DVR off in the vendor app, turn it on again from the same screen.

## Safety
- The DVR is part of the car's camera system. Do not disable the 360 camera app.
- Do not remove the DVR drive while it is recording.
