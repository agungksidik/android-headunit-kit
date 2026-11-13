# Instructions for AI coding agents

This repository is a kit for slow Android car head units: guides in `docs/`, scripts in `scripts/` and a launcher app in `launcher/`. These instructions apply to Claude Code, Codex, Gemini CLI and similar agents.

## When the user asks you to clean up or speed up their head unit

Work through the guides in this order. Read a guide completely before running anything from it.

1. `docs/README.md`: safety rules.
2. `docs/access-and-backup.md`: connect, identify the firmware, run `scripts/backup.sh`.
3. `docs/security.md`: tell the user what ADB over Wi-Fi means on this firmware.
4. `docs/google-updates.md`: only if Play services or Play Store updates fail.
5. `docs/debloat.md`: one package per call, health check after each.
6. `docs/keyboard.md`, `docs/background-play.md`, `docs/eq-tuning.md`: when the user has that symptom.
7. `docs/launcher.md` and `docs/music-player.md`: only if the user wants the launcher.
8. `docs/offline-maps.md`, `docs/dvr-and-load.md`: information for the user; change nothing unasked.

## Safety rules

- The car must be parked with ACC on. With the key off the unit sleeps and drops Wi-Fi.
- Back up before the first change. Do not skip it, even for a small change.
- Never disable or force-stop: the reverse or 360 camera app, the CarPlay or Android Auto app, CAN, MCU and kernel services, Bluetooth phone, radio, the DSP app, the steering-key service. Keep `com.android.launcher3` when `dumpsys activity recents | grep mRecentsComponent` names it.
- Never restart the unit unless the user confirmed it. The cameras are off for 40 to 60 seconds.
- Do not tap or screenshot the unit's screen while the user is using it. Ask for a quiet minute first.
- Download only from a project's official GitHub release and compare the checksum with the release digest. Never install Google Play services or Play Store APKs from mirror sites.
- Do not edit the DSP preset the user uses. Tune an unused preset and record the old values.
- Do not write secrets, IP addresses, Wi-Fi names, email addresses, the user's location or local file paths into any file in this repository or into commit messages. Use placeholders such as `<unit-ip>`.

## Ask before each category

Ask the user, and wait for a yes, before each of these: Google update unblock, debloat (and again for each app they might use), keyboard change, EQ change, background whitelist changes, installing any app, restarting, turning Wi-Fi ADB off. Say in one or two sentences what changes, what could go wrong and how to undo it.

For debloat, list the candidate packages with the reason for each and let the user remove any from the list before you start.

## Log every change

Keep one `changes.txt` next to the backup. One line per change:

```
<date time> <area>: <what changed, old value -> new value>. Undo: <exact command>
```

Write the line before the next change, not at the end. At the end show the user the file.

## Commands

- Use plain `adb shell` with `-s <unit-ip>:5555` when several devices are connected.
- Send `reboot` as its own call. A shell call longer than about 120 seconds times out.
- Run one package per call when disabling. Run the health check from `docs/debloat.md` after each one. If a health package is down, enable the last package again and stop.
- After a Play update, high load for a while is dex2oat. Wait before judging.
- Read "Free RAM" from `dumpsys meminfo`, not the "free" column of `free -m`.

## If the firmware differs

The guides were tested on one unit. If `/data/nwdappconfig/app/` is missing, the firmware is not NWD style. Do not apply the vendor hooks. Find the equivalent by searching the vendor apps and `services.jar` for the symptom text, tell the user what you found, and ask before changing anything.

## Working on the launcher (`launcher/`)

- Plain Java 17, no Kotlin, no AndroidX. Framework views only.
- Build and test from `launcher/`: `JAVA_HOME=<jdk17> ./gradlew testPublicEditionReleaseUnitTest assemblePublicEditionRelease --console=plain -q`.
- Pure logic (queue, search, natural order, weather codes, ADB framing) has JUnit tests. Add a test when you change it.
- All user-facing text goes into `res/values/strings.xml` (English) and `res/values-in/strings.xml` (Indonesian).
- Code, comments, identifiers and docs are English, in a plain factual tone.
- Do not run `adb install` or change a unit without the user's go-ahead.
- Do not commit or push unless asked.

## Reporting

When you finish, tell the user: what you changed, what you verified and how, what you left alone, and how to roll back each change.
