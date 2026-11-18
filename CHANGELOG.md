# Changelog

Newest first.

## 2026-11-18

- Sound presets are about 2 dB louder than the first version (level +2 on every built-in preset except Flat, which stays 0, and Night, which is unchanged). Anti-clip now keeps bands at +10 dB or lower, and the headroom badge is green up to +8 dB, amber above +8 and red above +11. Saved user presets are unchanged.
- Swipe right to left on Home to open All Apps, with a slide transition; swipe left to right in All Apps (or Back) to return.
- Custom Sound preset row: a fade and a chevron button on each side where more cards are hidden (the left one only after scrolling); a tap scrolls by one card.
- Player controls reordered (shuffle and repeat together, then previous, play, next, then Sound). The Sound control is one pill with the EQ icon and the preset name. The volume popup is a fully opaque card with an accent border, shadow and a larger bar and number.
- Custom Sound fixes from the first test on the unit: stepper names no longer cut (text shrinks to fit), the bottom bar fits, the level label is drawn inside the graph, nothing is sent and no preset is highlighted until the first change ("Not applied yet"), a one-time dialog explains the `hidden_api_blacklist_exemptions` command when Android blocks the chip call, and the volume popup is opaque.
- Genre presets: Koplo, Dangdut, Akustik, Rock, Jazz, DJ / EDM, Pop and Bicara. The preset row scrolls sideways and keeps the active card in view, and the presets sheet groups styles, genres and own presets.
- Custom Sound: our own DSP screen in the launcher (dock "DSP"). Preset cards with mini curves, a curve graph, five steppers, a 32-band mode, anti-clip, smart loudness, A/B, listening position, up to 10 saved presets, and a resend of the last setting after boot. It sends the factory 32-band packet to the DSP chip through the same call as the factory DSP app.
- Volume popup drawn by this app whenever the MCU volume changes (overlay window with the SYSTEM_ALERT_WINDOW permission, in-app fallback), because the factory popup stops with the factory launcher.
- Own launcher entries "Music" and "Sound" with their own icons, so they show in All Apps, the tile picker and the factory launcher's app page.
- A Sound button with the active preset name in the Custom Music Player's bottom bar.

## 2026-11-13

- Build flavors: `publicEdition` (default) and `owner`, with the same application id. The owner flavor can add `launcher/app/src/owner/res` (ignored by git) to compile in a different car picture. The public build works without that folder.
- Car image: a new, more realistic built-in rear view of a white compact SUV (gradient fills, tail light bar with L-shaped clusters, licence plate, tyres), and Settings, "Car image" to choose your own PNG or go back to the built-in drawing. A PNG can also be pushed to the app's files directory with root ADB.
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
