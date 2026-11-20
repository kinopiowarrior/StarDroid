<img src="docs/artwork/pylon-launcher.svg" align="right" width="104" alt="StarDroid pylon icon">

# StarDroid

**StarCraft and Brood War on Android, with Classic, Remastered and optional Carbot graphics.**

**Latest release: 1.9.13 (build 194) · Android 8.0+ · ARM64 and x86-64**

See [1.9.13 changes](docs/CHANGES-1.9.13.md) and [build instructions](BUILD-1.9.13.txt).

[Download APK](https://github.com/kinopiowarrior/StarDroid/releases/download/v1.9.13/StarDroid-1.9.13.apk) · [Release notes](https://github.com/kinopiowarrior/StarDroid/releases/tag/v1.9.13) · [Developer source](https://github.com/kinopiowarrior/StarDroid/releases/download/v1.9.13/StarDroid-1.9.13-source.zip) · [Discord](https://discord.gg/F2FnjUrtQ)

## Features

- StarCraft and Brood War campaigns, briefings, cinematics, saves and replays.
- Classic, Remastered and Carbot artwork for supported battlefield and interface graphics.
- CPU/GPU rendering options, zoom, widescreen menus, enlarged group icons and an optional FPS overlay.
- Custom games with computer opponents, optional Hero Mode and local non-UMS observers.
- Local multiplayer over Bluetooth, with optional Wi-Fi Direct.
- **UMS support and common EUD support.** EUD compatibility is partial; not every map or Windows-specific extension works.
- Touch, mouse, keyboard and gamepad controls, with guides in the Controls menu.
- Nine built-in interface languages, additional text and voices from installed language packs, map filtering, and ZIP/RAR imports.

## New in 1.9.13

- SCVs repair the rescued battlecruiser on Norad II and stop silently when minerals or gas run out.
- Text and speech can be selected independently across all 13 supported game locales.
- Fixed larva spawning at the unit limit, siege and cloak commands issued to dying units, and Lurker force fire.

For Nearby Multiplayer, update every device to 1.9.13.

## Included from 1.9.12

- Race-specific announcements, skirmish AI improvements and clearer Nearby connection diagnostics. [Full changes](docs/CHANGES-1.9.12.md)

## Included from 1.9.11

- Fixed protected EUD maps such as Poker Defense being rejected in Nearby Multiplayer with “Mission archive has invalid bounds”.
- Keeps the original map unchanged; editing or repacking the map is not needed.

## Included from 1.9.10

- Developer source downloads now receive a clear explanation when selected as game data.
- Test files from the developer source are rejected before they can replace working imported data.
- The patch diagnostic identifies known developer test data already imported and explains how to replace it.
- Clearer guidance explains which StarCraft files to import and what the patch diagnostic means.

## Included from 1.9.9

- Hardened Classic fallback selection against file-order conflicts during Remastered imports.
- Installation folder now detects Classic MPQ collections and complete Remastered installations inside the selected folder.

## Included from 1.9.8

- Fixed flashing and displaced shadows during burrowing and unburrowing in Remastered graphics.

## Included from 1.9.7

- Restored visible burrow markers for Zerg units in Remastered.
- Fixed the skipped Dragoon death animation in Remastered.

## Included from 1.9.6

- Replaced the zoom toggle and View Zoom with side-by-side **Minimum zoom** and **Maximum zoom** percentages.
- Touch, mouse and controller zoom stay within your chosen limits. Set both percentages alike for a fixed zoom.

## Included from 1.9.5

- Fixed HD/GPU slowdown when viewing remembered buildings in fog after loading a save.
- Added **Player statistics** in App Options for results, game time, combat totals, gathered resources and campaign progress.

Install over the current app without uninstalling. Imports from 1.9 onward remain valid.

## Included from 1.9.4

- Select Larvae remains available while a Hatchery, Lair or Hive researches or upgrades.
- Spore Colony requirements no longer list the Creep Colony being morphed.

## Included from 1.9.3

**Large unit group icons** appears and takes effect only while Widescreen is enabled. Turning Widescreen off restores normal icons; the saved choice is remembered for next time.

## Included from 1.9.2

Build 181 keeps the selection and move/attack buttons at their original heights when moved into side margins. The Enhanced Lighting fix for Disruption Web is retained. Install over 1.9.2 without uninstalling; no reimport is needed.

- Corrected localized menu labels and Cyrillic game text.
- Keyboard entry supports standard Android input methods and overlays the game without shrinking its viewport.
- Added optional touch controls in unused side margins when enough space is available.
- Improved Remastered group wireframes and control-group replacement.
- Corrected Protoss warp-in, Disruption Web and Dark Swarm effects in Remastered.
- Localized campaign mission names. Campaign unlocking remains in App Options.

Install over the existing app without uninstalling. Data already imported since 1.9 remains valid; no reimport is required.

## Included from 1.9.1

- Fixed false “Not enough storage” errors when importing maps.
- Fixed worker build-menu Cancel for SCVs, Probes and Drones in Classic and Remastered.

Install over 1.9 without uninstalling. **Data already imported for 1.9 remains valid.** Saves, replays, profiles, campaign progress, maps and settings are kept.

See [1.9.1 release notes](https://github.com/kinopiowarrior/StarDroid/releases/tag/v1.9.1).

## Included from 1.9

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

1. Install **[StarDroid-1.9.13.apk](https://github.com/kinopiowarrior/StarDroid/releases/download/v1.9.13/StarDroid-1.9.13.apk)**. To update, install over the existing app **without uninstalling**.
2. Import game data if prompted, through **App Options → Import game files**. Choose **Installation folder** or **Files or archives**. Data already imported for 1.9 does not need another import.
3. Import maps as `.scm`, `.scx`, ZIP or RAR files. Use the same app version on every multiplayer device.

**Use your installed StarCraft game files.** The StarDroid developer source ZIP and GitHub “Source code” downloads contain app code, not playable game data. Do not import them into the app.

**Classic files:** import `StarDat.mpq`, `BrooDat.mpq` and `Patch_rt.mpq`. Import `StarCraft.mpq` and `BroodWar.mpq` from the original installation media for campaigns and movies.

**Remastered / Carbot files:** import the installation folder, or a ZIP/RAR containing the **Data** folder and its matching **.build.info** file together. A complete installation can also supply Classic gameplay data, both campaigns, menus, portraits, music and the included movies. Use the normal game-data importer; separate original MPQs are not required for a fresh complete import.

A fresh Remastered import creates the compatible Classic `patch_rt.mpq` from the installation's effective game data. Existing imports made since 1.9 remain valid, including converted Classic content and graphics. Later imports retain the normal preservation rules. Import a separate legacy `patch_rt.mpq` to replace the patch explicitly.

**Adding Carbot graphics:** if the Carbot option is missing, re-import your complete installation data once. The option appears only when the imported data contains usable Carbot assets.

**Game data is not included.** This is an unofficial fan project, not affiliated with Blizzard Entertainment.

[Player guide](docs/PLAYER-GUIDE.md) · [1.9 changes](docs/CHANGES-1.9.md) · [Checksums](https://github.com/kinopiowarrior/StarDroid/releases/download/v1.9.13/StarDroid-1.9.13-SHA256SUMS.txt) · [Report a bug](https://github.com/kinopiowarrior/StarDroid/issues/new/choose)

## Development

Download **[StarDroid-1.9.13-source.zip](https://github.com/kinopiowarrior/StarDroid/releases/download/v1.9.13/StarDroid-1.9.13-source.zip)** for the matching complete app source, build instructions and validation records. GitHub's automatic “Source code” archives contain the older repository code snapshot; use the attached source ZIP to build 1.9.13. This is for developers; it does not supply game data.

## Credits

Put together by **ToadWarrior**, using [OpenBW](https://github.com/OpenBW/openbw) and community projects. See [third-party credits](THIRD_PARTY.md) and [contributing](CONTRIBUTING.md).

StarCraft and Brood War belong to Blizzard Entertainment. StarCraft Cartooned artwork is by Carbot Animations.
