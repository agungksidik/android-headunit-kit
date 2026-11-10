# Android Head Unit Kit

A kit that makes a slow Android car head unit light and usable: Google updates unblocked, debloat, a faster keyboard, EQ tuning, background play, offline maps, and a Custom Home launcher with a Custom Music Player for USB drives. It contains guides with copy-paste ADB commands, shell scripts that back up and undo, and the source of the launcher app.

**Built from scratch.** Custom Home and Custom Music Player are original apps written for this kit: plain Java, no third-party launcher or player, no ads, no tracking. The only network use is the Open-Meteo weather request and the unit's own ADB on 127.0.0.1. The guides (debloat, Google updates, EQ, keyboard, background play) are the author's own tested steps on the unit.

## What is in the kit

| Part | What it does |
|---|---|
| [docs/access-and-backup.md](docs/access-and-backup.md) | Connect with ADB, identify the firmware, back up first |
| [docs/security.md](docs/security.md) | ADB over Wi-Fi gives root on this firmware |
| [docs/google-updates.md](docs/google-updates.md) | Lift the Google update ban, stop the update storm |
| [docs/debloat.md](docs/debloat.md) | Free RAM: what to disable, what never to disable |
| [docs/keyboard.md](docs/keyboard.md) | HeliBoard, long-press timing, no autocorrect surprises |
| [docs/eq-tuning.md](docs/eq-tuning.md) | Stop bass distortion in the vendor DSP |
| [docs/background-play.md](docs/background-play.md) | Keep NewPipe and other players alive in the background |
| [docs/launcher.md](docs/launcher.md) | Custom Home (the launcher): features, install, rollback |
| [docs/music-player.md](docs/music-player.md) | Custom Music Player: USB music inside the launcher |
| [docs/offline-maps.md](docs/offline-maps.md) | Navigation without mobile data |
| [docs/dvr-and-load.md](docs/dvr-and-load.md) | 360 DVR loop recording and CPU load |
| [scripts/](scripts/) | `backup.sh`, `unblock-google-updates.sh`, `rollback-google-updates.sh`, `debloat.sh` |
| [launcher/](launcher/) | Android Studio style Gradle project of the launcher (plain Java) |
| [AGENTS.md](AGENTS.md) | Instructions for AI coding agents that work on a head unit |

The guide index with the safety rules is [docs/README.md](docs/README.md).

## Tested on

Orca Standard G3 (Android 13, Allwinner T527, factory firmware by NWD), 1280x720 landscape, touch. Other Chinese units with similar firmware may differ: file names, package names and vendor hooks can change. Check the firmware first (see the access guide).

## Quick start

1. Park the car with ACC on and put the unit on your own Wi-Fi hotspot.
2. Connect and back up first:
```
adb connect <unit-ip>:5555
UNIT=<unit-ip>:5555 sh scripts/backup.sh
```
3. Read [docs/README.md](docs/README.md), then do one guide at a time. Log every change and its undo in `backup-<date>/changes.txt`.
4. Optional: build and install the launcher (see [docs/launcher.md](docs/launcher.md)):
```
cd launcher
JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew testReleaseUnitTest assembleRelease
```

## Screenshots

### Before and after

| Factory Home | Custom Home |
|---|---|
| <img src="docs/screenshots/00-before-vendor-home.png" width="480" alt="Factory Home"> | <img src="docs/screenshots/01-home.png" width="480" alt="Custom Home"> |

| Factory Music Player | Custom Music Player |
|---|---|
| <img src="docs/screenshots/00b-before-vendor-music.png" width="480" alt="Factory Music Player"> | <img src="docs/screenshots/02-player.png" width="480" alt="Custom Music Player"> |

### Gallery

| Custom Home | Custom Music Player |
|---|---|
| <img src="docs/screenshots/01-home.png" width="480" alt="Custom Home"> | <img src="docs/screenshots/02-player.png" width="480" alt="Custom Music Player"> |

| Search | Settings |
|---|---|
| <img src="docs/screenshots/03-search.png" width="480" alt="Search across folders and songs"> | <img src="docs/screenshots/04-settings.png" width="480" alt="Settings"> |

| All apps |
|---|
| <img src="docs/screenshots/05-apps.png" width="480" alt="All apps"> |

- Custom Home: clock, weather, speed with road and car, music card, four tiles and the dock.
- Custom Music Player: folders, songs and the vinyl card with the now-playing bar.
- Search: folders first, then songs, across the whole USB library.
- Settings: default launcher, shortcuts, music rescan, restart and version.
- All apps: six-column grid of every launchable app.

Screenshots were taken on the tested unit before the car image was replaced by the original drawing in this repo.

## Credits

- Weather data: [Open-Meteo](https://open-meteo.com/) (CC BY 4.0). Attribution: "Weather data by Open-Meteo.com".

## License

MIT, see [LICENSE](LICENSE).
