# Changelog

Changes of this fork ([tib-kpl/Unreal-Android](https://github.com/tib-kpl/Unreal-Android)).
Upstream changes are listed in [Andiweli/Unreal-Android](https://github.com/Andiweli/Unreal-Android/releases).

## 2.4.0

Based on upstream Unreal Android 2.3.0, with its stability and performance fixes.

- Unreal Gold 226 support with a second native engine (Unreal and Return to Na
  Pali); the launcher picks the engine from the game data, v200 data is
  unchanged. See [docs/UNREAL_GOLD.md](docs/UNREAL_GOLD.md).
- Download and installation of the Unreal Gold disc hosted by OldUnreal, with
  the choice of language and of the location (internal storage, SD card or any
  folder), or use of an existing Unreal Gold folder.
- Android settings gear in the Gold game: on-screen controller (mode, opacity,
  layout), game language, game menu and game data location.
- Classic Unreal menus by default, enlarged on HD screens; Unreal Gold's UMenu
  windows are available in the settings. New Game offers both campaigns.
- Physical controllers with the v200 bindings and stick response; RetroTouch
  on-screen controller in menus and in game.
- Gold audio settings migrated to the Android audio driver; saving and loading
  fixed on 64-bit devices; full mesh detail.
- Smooth intro flyby, and its start message centred in every language.
- Releases are named after the app version and the build number.
