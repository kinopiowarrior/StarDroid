<img src="docs/artwork/pylon-launcher.svg" align="right" width="104" alt="StarDroid pylon icon">

# StarDroid

**Classic StarCraft and Brood War on Android.**

**Latest release: 1.5.2 · Android 8.0+ · ARM64 and x86-64**

[Download APK](https://github.com/kinopiowarrior/StarDroid/releases/download/v1.5.2/StarDroid-1.5.2.apk) · [Release notes](https://github.com/kinopiowarrior/StarDroid/releases/tag/v1.5.2) · [Source ZIP](https://github.com/kinopiowarrior/StarDroid/releases/download/v1.5.2/StarDroid-1.5.2-source.zip) · [Discord](https://discord.gg/F2FnjUrtQ)

## New in 1.5.2

- Inactive traps stay hidden until they reveal themselves. Covers all six floor and wall trap types.
- Medic abilities use the original bottom-row positions: **Heal · Restoration · Optical Flare**. Heal uses its original **A** hotkey, and Restoration has the correct cast icon.
- Verified **Patriot’s Blood**: Heal and Restoration are enabled; Optical Flare (Blind) is disabled by the mission.
- Includes the 1.5.1 fix allowing selection boxes to continue across the HUD and select units beneath it.

[1.5.2 changes and validation](docs/CHANGES-1.5.2.md).

## Included from 1.5

Version 1.5 includes **UMS support and common EUD support**, plus the changes developed since the public 1.1.1 release.

### Custom maps and EUD

- **Use Map Settings** respects scenario slots, races, teams, alliances, custom unit data, technology restrictions, and triggers.
- Common EUD features include supported memory conditions/actions, dynamic triggers, and live changes to units, data tables, command buttons, wireframes, animations, terrain, and camera behavior.
- Improved map-defined names, text refresh, objectives, shared vision, stacking-map behavior, protected archives, and newer terrain movement/placement data.
- **Map keys** supplies touch keyboard and mouse controls defined by a map. **EUD options** provides timing and wireframe compatibility choices.
- Solo scenarios can advance to an already imported next map after victory. EUD support remains partial; not every map or Windows-specific extension works.

### Map browser and multiplayer

- Thumbnail popups show enlarged minimaps and scrollable descriptions. Map/ZIP imports show progress and a final result.
- **List options** adds map-type filters, sorting, imported-map deletion, and a count of the listed maps.
- Better Classic/Brood War map classification, non-UMS player-color selection, and clearer team labels.
- Non-UMS **Match Options** combines Hero Mode with expanded larva selection, one-larva morphing, initial worker harvesting, and smart casting. Hosts choose the shared rules before starting.

### AI and gameplay

- Added **Original** CPU difficulty based on the imported game scripts.
- Improved native skirmish attack waves, distant expansion defense, and idle transport return.
- Score screens show bot difficulty after the race, such as **Terran (Normal)**.
- Fixed SCV construction/repair commands, repeated Lurker unburrowing, Drone selection after canceled construction, and several campaign/scenario behaviors.

### Interface, languages, and audio

- Portrait taps center the camera; minimap rendering/input stays consistent across zoom levels.
- Improved large-text layouts, HUD input handling, prerequisites, and campaign command visibility. Fixed detached effects showing through fog.
- Added Simplified Chinese, Traditional Chinese, Korean, and Japanese, bringing the interface to **nine languages**.
- Added **WAV49/codec 49** audio support, improved text decoding, and prevented missing or damaged optional sounds from interrupting scenario triggers.

### Patch identification

**App Options → Game-data patch** now identifies recognized classic patch-data families from **1.02 through 1.17.0**, including repacked archives with matching contents. Detection is offline and shows a range when several versions share the same data.

[Full 1.5 changes and compatibility notes](docs/CHANGES-1.5.md)

## Features

- StarCraft and Brood War campaigns, briefings, and cinematics.
- Custom games with computer opponents and optional Hero Mode.
- Local multiplayer over Bluetooth, with optional Wi-Fi Direct.
- Touch, mouse, keyboard, and gamepad controls.
- Saves, replays, and map imports from `.scm`, `.scx`, and ZIP files.

## Install

1. Download and open **[StarDroid-1.5.2.apk](https://github.com/kinopiowarrior/StarDroid/releases/download/v1.5.2/StarDroid-1.5.2.apk)**.
2. Import your own `StarDat.mpq`, `BrooDat.mpq`, and `Patch_rt.mpq`.
3. Import `StarCraft.mpq` and `BroodWar.mpq` from the original installation media for campaigns and movies.

To update, install over the existing app **without uninstalling**. Use the same version on all multiplayer devices.

**Game data is not included.** This is an unofficial fan project, not affiliated with Blizzard Entertainment.

[Player guide](docs/PLAYER-GUIDE.md) · [Download checksums](https://github.com/kinopiowarrior/StarDroid/releases/download/v1.5.2/SHA256SUMS.txt) · [Report a bug](https://github.com/kinopiowarrior/StarDroid/issues/new/choose)

## Development

The **[1.5.2 source ZIP](https://github.com/kinopiowarrior/StarDroid/releases/download/v1.5.2/StarDroid-1.5.2-source.zip)** contains the matching code, build instructions, and validation records. The repository code snapshot remains at 1.1.1.

Version 1.5.2 builds on 1.5.1 with trap-visibility and Medic command-card fixes, plus rebuilt ARM64/x86-64 native libraries. See the matching source ZIP for build instructions and validation records.

## Credits

Put together by **ToadWarrior**, using [OpenBW](https://github.com/OpenBW/openbw) and community projects. See [third-party credits](THIRD_PARTY.md) and [contributing](CONTRIBUTING.md).

StarCraft and Brood War belong to Blizzard Entertainment.

