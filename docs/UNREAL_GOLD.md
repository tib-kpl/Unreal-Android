# Unreal Gold 226 branch

`unreal-gold-226` adds a second native engine (`libUnreal226.so`) for Unreal
Gold 226, alongside the original v200 engine (`libUnreal.so`). The launcher
selects the engine from the game data. Keep this branch separate from `main`
until the Gold compatibility work is ready. The branch includes upstream's
Unreal 2.3.0 fixes and the Automotive minimum SDK 24 update.

The launcher can download and install the Gold disc hosted by OldUnreal,
choose the language and installation folder, or use an existing Gold folder.
The settings gear offers language, game menu, game data location, and touch
controller preferences. The classic HUD menu is the default; the Gold UMenu
windows remain available in settings. This supports 226 data, not the 227 patch.

## Classic menu compatibility

The classic console is `UnrealGoldAndroid.ClassicConsole`, a small subclass
of the original `UPakConsole`. It keeps UPak's mission state and transition
handling while routing ordinary menu input to `Engine.Console`. Using
`Engine.Console` directly loses UPak's `NextMap` and transition controls.
The original game packages are kept intact.

The native engine loads `UPakSinglePlayer` before the first classic menu opens
and fixes its inherited start map to `Intro1.unr`. The game menu copies this
value in `PostBeginPlay`, so patching it on the following tick is too late.
The map is resolved through `Core.System.Paths` rather than a Windows path.
The patched class is rooted so its defaults survive garbage collection.

`[Android] ClassicMenu` selects logical canvas scaling for the classic menu
and HUD; the Windows menu uses its own GUI scale. Controller joystick copies
are ignored when SDL already emits a normalized controller event, preventing
duplicate input in both gameplay and classic menus. A selects and B returns
to the parent menu.

Gold's GUID generator uses fixed-width integers and asserts a 16-byte UUID.
The old `unsigned long` fields overflowed `FGuid` on arm64 during game saves.

## Physical controllers

Gold consumes Android controller keys and axes directly, matching v200 rather
than routing each input through several legacy SDL joystick paths. This covers
built-in, USB and Bluetooth pads, Z/RZ or RX/RY right sticks, analogue triggers
including BRAKE/GAS, digital-only triggers and D-pad buttons/hats. Events are
queued to the engine thread. Pause, focus loss, menu/level changes and device
removal release held actions; Start is a single menu toggle.

`Android226User.ini` receives the v200 controller bindings once. The migration
replaces stock PC Gold bindings, fills missing ones, and preserves custom
bindings, mouse sensitivity and player settings. Classic Customize Controls
captures raw controller buttons, triggers and stick directions while ordinary
menu navigation keeps A/B and arrow shortcuts. Original `User.ini` and
`DefUser.ini` remain untouched. Later control remapping is retained.

| Control | v200 / Gold action |
| --- | --- |
| Left stick | Move / strafe |
| Right stick | Look, with the v200 linear response and smoothing |
| A / B | Jump / crouch; select / back in menus |
| X / Y | Grab / walk; N / Y in menu prompts |
| L1 / R1 | Previous / next weapon |
| L2 / R2 | Alternate / primary fire |
| Select | Translator; back in menus |
| Start / Mode | Open / close menu |
| R3 | Center view |
| L3 + A / L3 + Y | Toggle infinite ammo / health, as in v200 |
| D-pad left / right | Previous / next weapon |
| D-pad up / down | Previous / next inventory item |

The default stick deadzones are 0.08 left and 0.10 right; trigger deadzone is
0.12. Movement and triggers use the v200 press/release hysteresis. Right-stick
mouse deltas use the v200 11.5 / 9.0 scales and sensitivity factor, without
adding digital turn/look commands. Pushing up looks up. The game's **Invert
Mouse** option also applies to the stick. Optional `AndroidNative*` settings
are read from `[NSDLDrv.NSDLClient]` in `Android226.ini`.

The Thor built-in controller was exercised for every button, both sticks,
mid-travel triggers, modifier chords, menu navigation and held fire across a
menu transition. Bluetooth uses the same normalization; a physical Bluetooth
pad and OUYA still need device testing.

The classic new-game path was checked through Intro1, Intro2, InterIntro and
A confirmation into the playable DuskFalls map. Gold save/load was checked on arm64. Windows package paths are resolved through
the configured search paths, and POSIX save URLs are parsed as local files
instead of remote hosts (which previously attempted to load `Index.unr`).

## Audio and menu stability

Gold's Windows Galaxy audio configuration is migrated once into
`[Audio.GenericAudioSubsystem]`. Android otherwise opened an 8000 Hz mono
device with zero effect voices, zero volume and digital music disabled.
The migration preserves Galaxy volumes/rate and any working Android settings,
including deliberate mute. Existing all-zero native defaults are repaired.
Music and menu effects were verified separately with non-silent PCM output;
the Thor uses 22050 Hz stereo and 16 effects voices from its Gold settings.

Controller input uses exclusive Android ownership after the first native event,
as in v200, suppressing even SDL joystick copies that lack a controller mapping.
Menu sticks must return to centre after opening a menu. Stick and D-pad held
states are combined independently, and repeated key-down filtering always
preserves key-up. Holding a stick while opening a menu, combining stick/D-pad
directions, then leaving the controller idle were checked on the Thor. The
reported intermittent scrolling still needs observation in ordinary play.

`tools/check_apk_contents.py` verifies both engines and all five required native
libraries in each ABI, plus the Gold console package. Run it on debug as well
as release APKs before installing them. It also runs in CI before signing.

## Gold mesh compatibility

Gold 226 actors do not declare the v400 LODBias property. Its native field
therefore starts at zero, which used to reduce every LOD mesh to its minimum
vertex count, even at close range. The dispersion pistol rendered with only
10 of its 195 vertices. The renderer now uses neutral bias 1.0 for that absent
value, retaining normal distance-based detail. This fixes the shared rendering
path for weapons, player previews, creatures and cinematic objects.

## Building

The Gradle build packages both engines and both Android ABIs. Run from `source`:

```text
gradle assembleNormalDebug
gradle assembleNormalRelease assembleAutomotiveRelease
```

Run `python tools/test_gold_config.py` with `JAVA_HOME` and `ANDROID_HOME` set
to check all 27 v200 profile entries and preservation of custom settings. CI
runs the same checks before building.

After changing the console adapter source, rebuild its bundled package with
an unmodified Unreal Gold 226 installation containing UCC and its DLLs:

```powershell
./tools/build_gold_console.ps1 -GameDirectory <Gold226Directory>
```

The adapter source is under `source/app/src/main/gold_console/Classes` and the
compiled package under `source/app/src/main/assets/gold_patches/System`.
Only this adapter is copied into the selected game folder at launch.

## Remaining validation

This is experimental work. A complete campaign playthrough, online play and
physical-device OUYA validation remain outstanding. The rewritten UPak native
functions may differ from the PC implementation.
