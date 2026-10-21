<img width="2172" height="724" alt="image" src="https://github.com/Andiweli/Unreal-Android/blob/main/images/unreal-header.jpg" />

# Unreal Android

This fork includes [Unreal Gold 226 support](docs/UNREAL_GOLD.md) alongside
the original Unreal v200 engine. The launcher selects the engine from the game data.

> [!NOTE]
> **Fork pour la traduction française** de [Andiweli/Unreal-Android](https://github.com/Andiweli/Unreal-Android).
> - Menus, HUD, messages et textes des cartes en français sur Unreal v1.200, avec un choix de la langue au premier lancement (puis appui long sur l'icône > « Langue du jeu »).
> - La traduction communautaire d'[OldUnreal](https://github.com/OldUnreal/Unreal-Localization) est téléchargée par l'app sur l'appareil ; elle n'est pas incluse dans ce dépôt.
> - Les lettres accentuées utilisent les glyphes des polices d'origine (ordre CP850) ; les grandes polices, sans accents, affichent la lettre simple.
> - Les liens de téléchargement plus bas mènent aux versions officielles, sans ces changements.
> - Prise en charge d'**Unreal Gold 226** (Unreal + Return to Na Pali) avec un second moteur, téléchargement des fichiers depuis OldUnreal et réglages Android : voir [Unreal Gold 226](#-unreal-gold-226-this-fork). Les versions de ce fork sont publiées dans ses [releases](https://github.com/tib-kpl/Unreal-Android/releases).
>
> **Fork for the French translation** of [Andiweli/Unreal-Android](https://github.com/Andiweli/Unreal-Android).
> French menus, HUD, messages and map texts on Unreal v1.200, with a language choice at first start (then long-press the icon > "Game language").
> The app downloads [OldUnreal](https://github.com/OldUnreal/Unreal-Localization)'s community translation on the device; it is not part of this repository.
> The download links below lead to the upstream builds, without these changes.
> This fork also runs **Unreal Gold 226** (Unreal + Return to Na Pali) with a second engine, can download the Gold files from OldUnreal, and adds Android settings: see [Unreal Gold 226](#-unreal-gold-226-this-fork). Its builds are published in this fork's [releases](https://github.com/tib-kpl/Unreal-Android/releases).

<p align="left">
  <a href="README.md">README</a>
  &nbsp;|&nbsp;
  <a href="ROADMAP.md">ROADMAP</a>
  &nbsp;|&nbsp;
  <a href="https://github.com/Andiweli/Unreal-Android/releases">DOWNLOAD</a>
  &nbsp;|&nbsp;
  <a href="https://github.com/Andiweli/Unreal-Android/releases/tag/v1.6.1">OUYA VERSION</a>
</p>

![OS](https://img.shields.io/badge/up%20to-Android%2016-green)
![Architecture](https://img.shields.io/badge/architecture-32/64bit-orange.svg)
![AI](https://img.shields.io/badge/AI-assisted%20coding-6e7781)
![Controller](https://img.shields.io/badge/Controls-Gamepad/Touch/Keyb-blueviolet)
![Multiplayer](https://img.shields.io/badge/Multiplayer-local%20WiFi-blueviolet)

[![Join The Lair on Discord](https://img.shields.io/badge/Discord-The%20Lair-5865F2?logo=discord&logoColor=white)](https://discord.gg/AB3WVBqD5G)
[![Support via PayPal](https://img.shields.io/badge/Support%20via-PayPal-0070BA?logo=paypal&logoColor=white)](https://paypal.me/andiweli)


> [!NOTE]
> Unofficial fan port.  
> No game data included. Requires legally obtained Unreal v1.200 or Unreal Gold 226 game files.  
> This project is not official and is not endorsed by Epic Games.


> [!IMPORTANT]
> Video games on smartphones are great, but not user-friendly if they require more than two thumbs to control. For this reason, this port is designed for controller input and does only offer basic touchscreen controls.
>
> This project is for preservation, experimentation and personal use only.  
> Unreal, Unreal Engine and related trademarks are owned by Epic Games.  
> This project is not affiliated with or endorsed by Epic Games.

---

## ▣ Screenshots

<img width="1920" height="1080" alt="unreal0" src="https://github.com/user-attachments/assets/d169d5e7-128f-465c-8a0a-fc133e896c11" />
<img width="1920" height="1080" alt="unreal1" src="https://github.com/user-attachments/assets/bff35f68-d400-4bea-af73-b65446853bda" />
<img width="1920" height="1080" alt="unreal2" src="https://github.com/user-attachments/assets/0658ea17-b7fc-40e9-bbee-1c038b24f852" />
<img width="1920" height="1080" alt="unreal3" src="https://github.com/user-attachments/assets/258ba31f-c790-46fa-bb57-1bff54a8db0c" />

---
 
## ◈ Features

- Android support from Android 4.x (OUYA) up to Android 16
- Game data selection – use an existing Unreal folder directly without copying, or import a ZIP into the app's data folder
- Android Storage Access Fixed – SAF support added for modern Android versions where direct SD/file access is restricted
- Legacy storage behavior friendly for old sideload devices (place game data on your microSD/Unreal folder)
- Local WiFi multiplayer and botmatches are available
- Added touch controls featuring *RetroTouch* for use without a controller
- Added keyboard support (tested with Chromebook)

> [!NOTE]
> If you are experiencing problems with your player moving or turning slowly, please go to OPTIONS and Customize Controls.

## ◈ Unreal Gold 226 (this fork)

The app runs two native engines side by side: the original Unreal v200 engine,
and a second engine based on the Android port of the Unreal Tournament v400
engine, adapted for Unreal Gold 226. The launcher reads the version of the game
data and starts the matching engine, so existing v200 installations keep
working unchanged.

- **Unreal and Return to Na Pali** – both campaigns of Unreal Gold start from
  the menu; Return to Na Pali's native functions (path finding) were rewritten
  for Android.
- **Download from OldUnreal** – the launcher can download the Unreal Gold disc
  hosted by OldUnreal, check it, and install only the game data. You choose the
  game language (English, French, German, Spanish, Italian) and the location:
  internal storage, SD card, or any folder (Android "All files access" is then
  requested). An existing Unreal Gold folder can also be used in place.
- **Android settings gear (v200 and Gold)** – in game, the gear at the top right opens the
  on-screen controller settings (automatic/always/hidden, opacity, layout
  editing and reset), the game language, the game menu, and the game data
  manager. The v200 language choices are automatic, French and English;
  the game menu choice applies to Gold.
- **Use an existing v200 folder** – choose “Use my Unreal folder” to play
  directly from internal storage or microSD, without copying the data into
  the app folder. The selected location is remembered for the next launch.
  The folder must be writable for settings and saves. ZIP import still
  extracts into the app folder.
- **Menus** – the classic Unreal menu, enlarged on HD screens as with v200, is
  the default. Unreal Gold's windowed UMenu remains available in the settings.
- **Controllers and touch** – physical controllers use the v200 bindings and
  stick response; the RetroTouch on-screen controller is available in menus and
  in game, and stays out of the way in the intro and the cutscenes.
- **Readable Gold interface** – classic menus and the HUD scale with the
  resolution, and French retains the large menu fonts. The Android settings
  provide interface size and gamma controls, applied when returning to the
  game. Gamma reveals shadow detail separately from the brightness setting.
- **French game texts** – missing French menus, translator messages and hints
  are downloaded on startup, including Return to Na Pali. Existing retail
  translations are preserved; loading an older save restores map translations
  and matching cached translator texts.
- **Personal v200 controls** – Gold imports the active v200 bindings once,
  preserving any buttons already customized in Gold. L3 can crouch as well as
  serve as the modifier for the existing combinations. R3 (right-stick click)
  shows the objective/hint in both v200 and Gold; B crouches and Select opens
  the translator. Existing installs receive the R3 shortcut once, preserving
  other personal R3 actions and later remapping.
- **Audio, saves and rendering** – Gold's audio settings are migrated to the
  Android audio driver, saving and loading work on 64-bit devices, meshes keep
  their full detail, and the intro flyby is smooth with its start message
  centred in every language.
- **32 and 64 bit** – both engines are built for armeabi-v7a and arm64-v8a.

Details and remaining limitations: [docs/UNREAL_GOLD.md](docs/UNREAL_GOLD.md).
Changes by version: [CHANGELOG.md](CHANGELOG.md).

## ▣ Requirements

- Android 8.0 or newer for the regular Android build.
- Chromebook with at least 4GB RAM and a powerful CPU (eg. MediaTek Kompano 520)
- OpenGL ES 2.0 capable GPU.
- A compatible device.
- Android-compatible game controller recommended.
- Unreal [v200 retail game data](https://archive.org/search?tab=all&query=unreal+v1.200), or Unreal Gold 226 game data (this fork can download it from OldUnreal).

> [!IMPORTANT]
> ## Installation procedere
> 1. Copy the required Unreal game .zip file to your microSD card or extract it to a folder like "Downloads".  
> 2. At first start, choose “Use my Unreal folder” to use the extracted files in place, or import the .zip file into the app folder.<br>
> 3. Installation takes a while, when finished the game starts.
>
> With this fork, the first screen also offers **Download and install Unreal Gold (OldUnreal)**:
> choose the language and the location, then the game starts once the download
> (about 680 MB) and the installation are finished.

Required folders:

```text
Unreal/
├── System/
├── Maps/
├── Textures/
├── Sounds/
├── Shared/
└── Music/
```

---

## ▣ Game data notes

Game data is not bundled with this repository.

You need to provide your own legal copy of Unreal 1998.  
The Android installer checks for the required folders:

```text
System
Maps
Textures
Sounds
Music
```

If these folders are missing, the game will not start and the installer screen will ask you to select a valid folder or ZIP file.

---

## ❤️ Support

If you enjoy this project and would like to support my work, you can make a small contribution via PayPal.

Your support helps me spend more time maintaining existing projects, fixing bugs, improving compatibility, and working on new features.

[![Support via PayPal](https://img.shields.io/badge/Support%20via-PayPal-0070BA?logo=paypal\&logoColor=white)](https://paypal.me/andiweli)

[![Join The Lair on Discord](https://img.shields.io/badge/Discord-The%20Lair-5865F2?logo=discord&logoColor=white)](https://discord.gg/AB3WVBqD5G)

Thank you for your support!

---

## ◎ Legal

Unreal, Unreal Engine and related names, assets and trademarks are property of Epic Games.
Portions of the materials used are trademarks and/or copyrighted works of Epic Games, Inc.

This repository does **not** include commercial game data.  
You must own a legal copy of Unreal to use this port.
This material is not official and is not endorsed by Epic.

All rights reserved by Epic.

Do not use this project for commercial purposes.
