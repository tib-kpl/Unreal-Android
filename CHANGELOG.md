# Changelog

Changes of this fork ([tib-kpl/Unreal-Android](https://github.com/tib-kpl/Unreal-Android)).
Upstream changes are listed in [Andiweli/Unreal-Android](https://github.com/Andiweli/Unreal-Android/releases).

## 2.4.3

- R3 (right-stick click) shows the objective/hint in both v200 and Gold.
  Existing installs upgrade an empty R3 or the old shipped CenterView default
  once, preserving other personal R3 actions and subsequent remapping.
  B remains crouch and Select remains the translator.

## 2.4.2

- Readable Gold menus and HUD at high resolutions: adaptive canvas size,
  restored large French fonts and correct accented glyphs in the stock fonts.
- Classic menu/HUD size and gamma controls in Android settings, applied when
  returning to the game. Gamma lifts shadow detail separately from brightness.
- Missing French texts are downloaded when starting Gold, including Return to
  Na Pali maps. Existing retail localization files are preserved.
- Loading a save restores map translations from the original map name, and
  updates cached translator messages and hints when their source text matches.
- Import the player's actual v200 bindings from its active Unreal.ini, while
  preserving buttons already customized in Gold. L3 retains its ordinary
  binding (crouch by default) alongside its modifier combinations.

## 2.4.1

- Android settings gear is now available with both v200 and Gold game files,
  including during the v200 intro, without taking focus from the controller.
- v200 touch controls support automatic/always/hidden mode, opacity and layout
  editing/reset. Its settings also provide game language selection, a restart
  and access to the game data manager.
- “Use my Unreal folder” uses an existing v200 folder directly, without copying
  its data. The selected path is remembered; ZIP import remains available.

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
