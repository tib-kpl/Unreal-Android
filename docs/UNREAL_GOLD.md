# Unreal Gold 226 support

`main` ships a second native engine (`libUnreal226.so`) for Unreal Gold 226,
alongside the original v200 engine (`libUnreal.so`). The launcher selects the
engine from the game data. Both engines are maintained together on `main`.
The integration includes upstream's Unreal 2.3.0 fixes and the Automotive
minimum SDK 24 update.

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

`Android226User.ini` receives the shipped v200 controller defaults once. When
v200 data is available, a second migration imports its actual bindings from
`System/Unreal.ini` (the v200 engine's active input configuration), with
`User.ini` as a fallback. Gold bindings that already differ from the defaults
are preserved, along with sensitivity and player settings. Classic Customize Controls
captures raw controller buttons, triggers and stick directions while ordinary
menu navigation keeps A/B and arrow shortcuts. Original `User.ini` and
`DefUser.ini` remain untouched. Later control remapping is retained.

The following defaults apply when no personal v200 profile is available:

| Control | Gold fallback action |
| --- | --- |
| Left stick | Move / strafe |
| Right stick | Look, with the v200 linear response and smoothing |
| A / B | Jump / crouch; select / back in menus |
| X / Y | Grab / walk; N / Y in menu prompts |
| L1 / R1 | Previous / next inventory item |
| L2 / R2 | Alternate / primary fire |
| Select | Translator; back in menus |
| Start / Mode | Open / close menu |
| R3 | Objective / hint (`ActivateHint`) |
| L3 | Its configured action, crouch by default |
| L3 + A / L3 + Y | Toggle infinite ammo / health, as in v200 |
| D-pad left / right | Previous / next inventory item |
| D-pad up / down | Previous / next weapon |

R3 (right-stick click) opens the objective/hint in both v200 and Gold. A
one-time upgrade fills an unassigned R3 or replaces the previous shipped
CenterView default, including existing Gold profiles. Other personal R3
actions and subsequent remapping are preserved. B remains crouch and Select
remains the translator.

Weapon and inventory cycling actions are exchanged once using each install's
current physical bindings. For example, a personal v200 profile with inventory
on L1/R1 now cycles weapons there, and its previous weapon buttons cycle items.
The table above describes the shipped fallback; importing a personal profile
before or after its upgrade does not exchange the actions a second time.

## Player view compatibility

Retail 226 PlayerPawn does not set the v400 `bViewTarget` default. The native
player tick must still update PlayerPawn eye height and preserve ViewRotation.
Otherwise it overwrites the aim with body Rotation (limited to about 17 degrees)
and leaves the eyes at standing height even when bDuck and bIsCrouching are set.
The compatibility path applies to player pawns; ordinary non-view pawns retain
the existing behaviour.

On the Thor, a held right stick reached the original bounds (-90 to about
99 degrees), and a held B lowered EyeHeight from 23 to near 0 before returning
to standing on release. The same native path handles existing saves and both
Unreal and Return to Na Pali.

## Camera comfort and full screen

Android settings expose sideways camera tilt from 0% (disabled) to 100%
(original effect), defaulting to 25% in both engines. Changes apply on return
to gameplay. Only a living first-person walking view is adjusted; flybys,
interpolated cinematics, third-person and spectator views retain their roll.

Both game activities clear SDL's late `FLAG_FORCE_NOT_FULLSCREEN` window flag
and restore immersive mode when the system bars reappear, as well as on focus
and resume. Android's transient swipe gestures remain available.

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

## Intro flyby

The v400 pawn rotation pulled the camera roll towards zero, then banked it on
the per-frame acceleration, while the intro camera followed its interpolation
path. Both fought the roll set by the path every frame, so the flyby shook in
places. Interpolating pawns now keep the path roll, as Unreal does. The first
intro frame, drawn from the PlayerStart before the flyby attaches, is not
shown. `IntroNullHud` places its start message for the width of the English
text; the engine centres it for every language.

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

## Shared Android settings and v200 data folders

The Android settings gear is also shown when using the original v200 engine,
including during its intro. Touch mode, opacity and layout editing/reset apply
to both engines. v200 offers its own automatic/French/English language choices
and can restart or open the data manager from this screen; Gold retains its
classic/UMenu choice and five game languages.

“Use my Unreal folder” selects existing v200 data in place, using the same
local-file access as Gold. The launcher remembers this path across process
restarts, checks its required files again and falls back to the usual locations
if it is unavailable. Configurations and saves are written in the selected
folder, which must be writable. ZIP import still extracts into app storage.

Validated on the Thor with a separate v200 test copy: gear during the intro,
touch mode, layout editing, restart, data manager, selection of an external
folder and reuse of that folder after force-stopping the app.

## High resolution display and gamma

The classic canvas uses an automatic logical height of 360, so UI text retains
its relative size as resolution changes. Android settings offer 75–125 % size;
the native bounds keep the original menus within the viewport. Old generated
fixed scales migrate to automatic sizing, while explicit custom scales remain.
The windowed UMenu keeps its own GUI scale; its gameplay HUD uses the adaptive
canvas too. Size changes apply on return from settings, on the engine thread.

French previously replaced every large/big canvas font with MedFont. The engine
now keeps the intended font size: small/medium French accents use the stock DOS
glyph positions, while unsupported accents in decorative fonts use plain
letters. Measurement and rendering use the same glyph mapping.

Gold's gamma setting ranges from 0.75 to 3.0, default 1.6. It applies a power
curve to RGB after brightness, preserving black and alpha coverage. Gamma 1.0
retains the previous brightness response. Returning from Android settings
updates it without restarting or touching gameplay progress.

Classic menu labels, values and help all use the same stock MedFont and size
across Game, Multiplayer and their submenus. Drawing, StrLen and TextSize use
the same face; titles are centred using their measured width rather than the
old decorative-font character estimate. Size adjustment covers 50–125%.
Native projected coronas and lens flares keep physical pixel
coordinates, while scripted HUD icons use the logical canvas; increasing HUD
size no longer moves or enlarges world light effects.

## French map texts and saves

At Gold startup, French selection checks the localization files for installed
maps in both Maps and Maps/UPak, plus the Gold UI packages. Missing files are
downloaded from OldUnreal's localization repository; existing retail files
remain intact. An unavailable download is reported and startup can continue.

SaveN packages retain the original map URL. Their localized actor properties
are reloaded using that original map name instead of searching for SaveN.frt.
Cached translator messages/hints that match a translated source string are
updated too, allowing older English saves to resume with French map texts.

On the Thor test copy: confirmed B and L3 crouch press/release, import of a
v200 R3 objective binding, French actor messages and restoration from a Vortex2
save, automatic download of a missing DuskFalls.frt, and live gamma adjustment.
