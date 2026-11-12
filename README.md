<img src="docs/artwork/pylon-launcher.svg" align="right" width="104" alt="StarDroid pylon icon">

# StarDroid

**StarCraft and Brood War on Android, with Classic, Remastered and optional Carbot graphics.**

**Latest release: 1.9 · Android 8.0+ · ARM64 and x86-64**

[Download APK](https://github.com/kinopiowarrior/StarDroid/releases/download/v1.9/StarDroid-1.9.apk) · [Release notes](https://github.com/kinopiowarrior/StarDroid/releases/tag/v1.9) · [Source ZIP](https://github.com/kinopiowarrior/StarDroid/releases/download/v1.9/StarDroid-1.9-source.zip) · [Discord](https://discord.gg/F2FnjUrtQ)

## Features

- StarCraft and Brood War campaigns, briefings, cinematics, saves and replays.
- Classic, Remastered and Carbot artwork for supported battlefield and interface graphics.
- CPU/GPU rendering options, zoom, widescreen menus, enlarged group icons and an optional FPS overlay.
- Custom games with computer opponents, optional Hero Mode and local non-UMS observers.
- Local multiplayer over Bluetooth, with optional Wi-Fi Direct.
- **UMS support and common EUD support.** EUD compatibility is partial; not every map or Windows-specific extension works.
- Touch, mouse, keyboard and gamepad controls, with guides in the Controls menu.
- Nine built-in interface languages, additional text and voices from installed language packs, map filtering, and ZIP/RAR imports.

## New in 1.9

**Everyone must re-import game data after updating.** The app prompts you and blocks play until the required imports succeed. Install over the existing app; your saves, replays, profiles, campaign progress, maps and settings are kept.

- GPU rendering for Classic graphics and automatic Remastered rendering optimizations.
- Local observer mode for non-UMS skirmishes, an FPS counter, and replay/results improvements.
- Improved installation and ZIP/RAR imports, language handling and map browsing.
- More common EUD support, including tested Poker Defense command pages and hero purchases. Compatibility remains partial.
- Campaign, controls and interface fixes, including the Norad II defeat crash.

See [1.9 changes](docs/CHANGES-1.9.md). Weave is the only bundled map; import other maps separately.

## Graphics

Remastered support covers units, terrain, menus, briefings, portraits and campaign artwork. Carbot / StarCraft Cartooned is available when its assets are included in your imported data.

In **App Options**, Classic / Remastered choices appear only when Remastered graphics are installed. The Remastered / Carbot choice appears only when usable Carbot data is installed. The existing SD/HD switch uses your selected HD artwork, including in supported multiplayer matches.

## Install

1. Install **[StarDroid-1.9.apk](https://github.com/kinopiowarrior/StarDroid/releases/download/v1.9/StarDroid-1.9.apk)**. To update, install over the existing app **without uninstalling**.
2. Re-import your game data through the required-import prompt or **App Options → Import game files**. Choose **Installation folder** or **Files or archives**.
3. Import maps as `.scm`, `.scx`, ZIP or RAR files. Use 1.9 on every multiplayer device.

**Classic files:** import `StarDat.mpq`, `BrooDat.mpq` and `Patch_rt.mpq`. Import `StarCraft.mpq` and `BroodWar.mpq` from the original installation media for campaigns and movies.

**Remastered / Carbot files:** import the installation folder, or a ZIP/RAR containing the **Data** folder and its matching **.build.info** file together. A complete installation can also supply Classic gameplay data, both campaigns, menus, portraits, music and the included movies. Use the normal game-data importer; separate original MPQs are not required for a fresh complete import.

A fresh Remastered import creates the compatible Classic `patch_rt.mpq` from the installation's effective game data. The required 1.9 refresh regenerates converted Classic content as well as graphics. Later imports retain the normal preservation rules. Import a separate legacy `patch_rt.mpq` to replace the patch explicitly.

**Adding Carbot graphics:** if the Carbot option is missing, re-import your complete installation data once. The option appears only when the imported data contains usable Carbot assets.

**Game data is not included.** This is an unofficial fan project, not affiliated with Blizzard Entertainment.

[Player guide](docs/PLAYER-GUIDE.md) · [1.9 changes](docs/CHANGES-1.9.md) · [Checksums](https://github.com/kinopiowarrior/StarDroid/releases/download/v1.9/SHA256SUMS.txt) · [Report a bug](https://github.com/kinopiowarrior/StarDroid/issues/new/choose)

## Development

Download **[StarDroid-1.9-source.zip](https://github.com/kinopiowarrior/StarDroid/releases/download/v1.9/StarDroid-1.9-source.zip)** for the matching complete app source, build instructions and validation records. GitHub's automatic “Source code” archives contain the older repository code snapshot; use the attached source ZIP to build 1.9.

## Credits

Put together by **ToadWarrior**, using [OpenBW](https://github.com/OpenBW/openbw) and community projects. See [third-party credits](THIRD_PARTY.md) and [contributing](CONTRIBUTING.md).

StarCraft and Brood War belong to Blizzard Entertainment. StarCraft Cartooned artwork is by Carbot Animations.

