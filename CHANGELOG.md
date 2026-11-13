# Changelog

Newest first.

## 2026-11-13

- Six customizable shortcut tiles instead of four: a 2 by 3 grid in the full layout and one row of six in the compact layout. The added slots default to Maps and the 360 camera. Saved choices in the first four slots are kept. Reset shortcuts restores all six defaults (CarPlay, Android Auto, YouTube, NewPipe, Maps, 360 camera).

## 2026-11-10

- Kit restructure: the repository is now the Android Head Unit Kit. The app moved to `launcher/`, and `docs/` (guides for access and backup, Google updates, debloat, keyboard, EQ, background play, offline maps, DVR and load, security, launcher, music player), `scripts/` (backup, unblock and rollback of Google updates, debloat) and `AGENTS.md` were added. The vendor car image was replaced by an original vector drawing, and screenshots were removed until they are retaken.
- Search field without autocorrect: the player search uses a filter input type with no suggestions, and the search key hides the keyboard.
- Player redesign: vinyl card with spinning record and tonearm, search across folders and songs, larger text, seekable progress bar, and gradient card backgrounds across the app.
- Weather place name shortening: administrative prefixes such as "Kecamatan" and "Kabupaten" are removed, and the city label may use two lines.
- The Home music card shows another app only while that app is playing. Otherwise it belongs to the built-in player.
- Split screen and weather: all activities are resizeable, Home has a compact layout below 900 dp, and the clock card shows Open-Meteo weather with a cache.
- Built-in USB music player and volume: folder and song browser, four repeat modes, shuffle within a folder, MediaSession, resume, and a volume row on the music card that drives the MCU volume.
- Settings and apps: Settings screen with a default launcher picker, a larger all-apps grid that opens from a cache, and Settings in the dock instead of Radio.
- App icon and Android 12 splash screen.
- v0.1: first Home with clock, GPS speed, animated road, music card, four tiles, restart button and dock.
