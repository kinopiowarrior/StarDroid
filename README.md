<img src="docs/artwork/pylon-launcher.svg" align="right" width="104" alt="StarDroid pylon icon">

# StarDroid

**Classic StarCraft and Brood War on Android, with touch controls and local multiplayer.**

StarDroid adapts the OpenBW engine for Android devices. Play campaigns, set up custom battles against computer opponents, or host a nearby multiplayer match. The interface uses the classic artwork, fonts, music, and sounds imported from your own game files.

**Latest release 1.1.1 · Android 8.0+ · ARM64 and x86-64**

Put together by **ToadWarrior**.

[Download the APK](https://github.com/kinopiowarrior/StarDroid/releases/download/v1.1.1/StarDroid-1.1.1.apk) · [Release notes](https://github.com/kinopiowarrior/StarDroid/releases/tag/v1.1.1) · [Download source](https://github.com/kinopiowarrior/StarDroid/releases/download/v1.1.1/StarDroid-1.1.1-source.zip) · [Discord community](https://discord.gg/F2FnjUrtQ) · [Player guide](docs/PLAYER-GUIDE.md) · [Build from source](docs/BUILDING.md) · [Report a bug](https://github.com/kinopiowarrior/StarDroid/issues/new/choose)

Version **1.1.1** improves AI, campaign scripts, mouse commands, transports, unit displays and language detection. It adds separate button-size controls and one graphics-smoothing setting for the battlefield and UI. Install `StarDroid-1.1.1.apk` over the existing app. [Released changes](docs/CHANGES-1.1.1.md).

**Diagnostics / credits** remains available in the app.

## Features

- **Campaigns:** StarCraft and Brood War campaign selection, briefings, mission progression, and imported cinematics.
- **Custom games:** Terran, Zerg, and Protoss; Easy, Medium, and Hard computers; Melee, Free for All, and configurable teams.
- **Local multiplayer:** up to eight player slots, limited by the map and connected devices, over Bluetooth with optional Wi-Fi Direct.
- **Lobbies:** map previews, player names, races, computer/open/closed slots, starting-slot arrangement, teams, game speed, chat, and a five-second countdown.
- **Hero Mode:** six buildable hero forms per race, with one of each allowed per player while alive or queued. Computers can use them too.
- **Touch controls:** tap selection, drag and hold gestures, camera panning, pinch zoom, optional control groups, and building confirmation controls that follow placement.
- **Save and load:** single-player saves and host-managed multiplayer saves.
- **Map importing:** `.scm`, `.scx`, and ZIP collections, with scrollable selection and thumbnails.

StarDroid is an unofficial fan project. **Game data is not included.** You need your own classic StarCraft and Brood War files. This project is not affiliated with or endorsed by Blizzard Entertainment.

## Install

1. Download **[StarDroid-1.1.1.apk](https://github.com/kinopiowarrior/StarDroid/releases/download/v1.1.1/StarDroid-1.1.1.apk)** on your Android device. The source-code ZIP is for development, not installation.
2. Open the APK on your Android device. If Android asks, allow the browser or file manager you are using to install apps from that source.
3. Launch **StarDroid**. The import screen appears automatically when required files are missing.
4. Tap **Import game files**, choose the files below, and let importing finish. You can select multiple files if your file picker supports it.
5. Check **Installed content**, then return to the game menu.

For an update, install the APK **over the existing app without uninstalling**. This preserves imported content, player IDs, saves, and settings when the signing identity matches. Previous OpenBW Android installations from this project use the same application identity.

### Required game files

Copy these from your own classic installation or installation media to a location accessible to Android's file picker.

| Files | Purpose |
| --- | --- |
| `StarDat.mpq`, `BrooDat.mpq`, `Patch_rt.mpq` | All three are required by this build, including when using original StarCraft rules. |
| `StarCraft.mpq` | Original StarCraft campaign and movie content from installation media. |
| `BroodWar.mpq` | Brood War campaign and movie content from installation media. |
| Original `Install.exe` archives | Also accepted as sources of installation-media content. |
| Previously exported mission ZIPs | Supported for importing compatible campaign bundles. |

Capitalization of the three required MPQ filenames does not matter. Duplicate download suffixes such as `StarDat(1).mpq` are accepted. Import the original archives; you do not need to unpack their internal files. The core MPQs alone do not supply every campaign mission and movie. **Installed content** shows what is available and what is missing. The same import screen is available later through **App Options**.

This release targets classic game data. A modern launcher installation or Remastered-only assets are not a documented substitute for these files. No game-file downloads are provided here.

### Device requirements

- Android **8.0 / API 26 or later**.
- A **64-bit ARM** (`arm64-v8a`) or **x86-64** (`x86_64`) Android environment. There are no 32-bit ARM libraries in this APK.
- Enough free space for imported archives and campaign/media content; these are much larger than the APK.
- Bluetooth for local multiplayer. Wi-Fi Direct is optional and depends on Android version and hardware support.

The [download checksum](https://github.com/kinopiowarrior/StarDroid/releases/download/v1.1.1/SHA256SUMS.txt) records the SHA-256 of the published APK and source ZIP.

## Quick start

### Campaign

Choose **Single Player**, select the expansion and player ID as prompted, then choose a campaign and an available mission. Import the corresponding installation-media archive if a mission or movie is missing.

### Custom game

Open **Custom Game**, choose or import a map, and set the game type, speed, difficulty, and races. Tap unused slots to add computers or close them. In **Teams**, give allies the same team number. Toggle **Hero Mode** above the map preview if you want buildable heroes, then start.

| Mode | Rules in StarDroid |
| --- | --- |
| Melee | Human players start opposed to other players; computer players are allied with one another. |
| Free for All | Every occupied slot is an opposing side, including each computer. |
| Teams | Players and computers with the same team number share vision and victory. Each player still controls their own army and resources. |

### Local multiplayer

1. Install the **same release build** and compatible game data on every device. The simulation compatibility check requires matching builds.
2. Open **Multiplayer**, choose a player name and expansion, then select **Bluetooth + Wi-Fi Direct** or **Bluetooth**.
3. On the host, choose **Host Game**, select a map and settings, and create the lobby.
4. On other devices, choose **Join Game**, select the host, and join. Accept Android pairing prompts and confirm that the displayed pairing codes match.
5. The host arranges slots and teams, adds computers, and starts when players and map transfers are ready. A five-second countdown precedes gameplay.

The host sends the map to joining devices; every device still needs its own game data. Capacity depends on the map's start locations and device connection support. This is local device-to-device multiplayer, not Battle.net or an internet matchmaking service.

See the [player guide](docs/PLAYER-GUIDE.md#local-multiplayer) for lobby controls, permissions, and resuming a saved match.

## Touch controls

These are the defaults for a fresh installation. Existing preferences are retained when updating.

| Action | Gesture |
| --- | --- |
| Select a friendly unit or building | Tap it. |
| Select units of the same type | Double-tap a unit. |
| Select a group | Hold briefly on the battlefield, then drag a selection box. |
| Move the camera | Drag immediately with one finger, or pan with two fingers. |
| Zoom | Pinch with two fingers. **Allow zoom out** is on by default. |
| Order an army across open ground | Tap the destination. Army ground taps use **attack move** by default; the command-mode button switches between attack move and move. |
| Use a command or ability | Tap its icon, then tap a target if required. Workers use context-sensitive movement and gathering orders. |
| Use the minimap | Tap or drag to move the camera. **Minimap hold issues orders** is off by default; enable it in Camera settings if you want holding to issue an order. |
| Place a building | Choose the building, position its preview, then tap **Build** or **Cancel**. |
| Read a command description | Hold its icon. A description hold does not execute that command when released. |
| Cancel a queued unit | Tap its production-queue icon. |
| Cancel targeting or open the menu | Use Android **Back**. |

**Build** and **Cancel** stay below the building preview. Controls underneath them cannot be pressed while those buttons are active. While dragging a preview, hold near an edge to scroll. Drag beneath the command bar to scroll down; lift your finger to stop before confirming. You can disable placement edge scrolling with **Camera settings → Scroll while placing**.

Open **Menu → Options → Controls** to change selection timing, drag behavior, camera sensitivity, descriptions, ground-tap orders, and placement. Version 1.0.1 turns minimap hold-to-order off for fresh settings and presets; updates preserve any saved choice. To change an existing installation, open **Camera settings → Minimap hold issues orders**. Enable optional control groups in **Selection settings**: hold a group to assign units, tap to recall, and double-tap to center the camera. See the [full controls guide](docs/PLAYER-GUIDE.md#controls-and-settings).

## Hero Mode

Enable **Hero Mode** in a custom-game or multiplayer setup. Train heroes at the race's ground-upgrade building:

| Race | Producer | Six hero forms |
| --- | --- | --- |
| Terran | Engineering Bay | Raynor (Marine), Raynor (Vulture), Sarah Kerrigan, Hyperion, Duke (Siege Tank), Samir Duran |
| Zerg | Evolution Chamber | Infested Duran, Infested Kerrigan, Hunter Killer, Torrasque, Devouring One, Kukulza (Mutalisk) |
| Protoss | Forge | Fenix (Zealot), Fenix (Dragoon), Zeratul, Tassadar, Gantrithor, Artanis |

Technology requirements still apply. One living or queued instance of each hero form is allowed per player; it can be rebuilt after death. Training times follow base-unit counterparts. Heroes and upgrades share a producer, so additional upgrade buildings allow separate production. Computers can build heroes at all difficulties.

Tassadar and Zeratul can merge into their hero Archon. Both hero slots remain occupied until the Archon dies. Hero Mode is optional and does not change campaign hero availability. Prices are initial balance choices; the [hero chart](docs/campaign-heroes.json) includes costs, stats, and base-unit comparisons.

## Saves and troubleshooting

- Use the in-game **Save Game** and **Load Game** options for single player. In multiplayer, the host saves the shared match; **Multiplayer → Load Saved** reopens its lobby.
- Keep the host app active during multiplayer. There is no host migration or mid-match reconnect flow.
- If a device cannot find the host, check Bluetooth permissions and pairing prompts, use **Search Again**, or try Bluetooth-only mode. Older Android versions can require Location permission and Location services for discovery.
- For missing content, check **App Options → Installed content** and reimport the relevant original archive.
- If an APK will not install over an existing app, check CPU architecture and signing identity. Independently signed builds cannot replace the distributed APK in place.
- Use **Load Replay** to watch, import, export, or delete replays. See [replay controls and compatibility](docs/PLAYER-GUIDE.md#replays).

More details: [player guide](docs/PLAYER-GUIDE.md) · [current source changes](docs/CHANGES-1.1.1.md) · [1.0 changes](docs/FIXES-1.0.md) · [validation record](docs/QA-1.1.1.md).

## Development

This branch contains **1.1.1 (build 88)**; see [release notes](docs/CHANGES-1.1.1.md) and [verification](docs/QA-1.1.1.md). The [build 86 Queen/lifetime fixes](docs/CHANGES-BUILD86.md) and [build 85 community fixes](docs/CHANGES-BUILD85.md) are retained. The earlier **build 83** fixed replay layouts for both current and older imports, added working timeline seeking, and refined pixel controls and campaign transitions. See [1.1 changes](docs/CHANGES-1.1.md), [build 82 changes](docs/CHANGES-1.0.12.md), [build 81 changes](docs/CHANGES-1.0.11.md), [build 80 changes](docs/CHANGES-1.0.10.md), and the [OpenSnowstorm review](docs/OPENSNOWSTORM-REVIEW.md).

The current Android app uses a native C++ engine, JNI, Android Canvas, and AudioTrack. The older web implementation remains as historical source and is not shipped in the APK.

On an x86-64 Linux development machine with Python 3.10+, JDK 17, Git, and `patch` installed:

```sh
python3 scripts/build.py
```

The build downloads pinned Android tools, prepares the patched engine, and writes **`dist/StarDroid-1.1.1.apk`**. Downloads and native compilation need additional disk space and time. Without an explicitly configured release key, local builds use Android's development signing key. See [build instructions](docs/BUILDING.md) for signing, faster rebuilds, and tests.

Automated checks cover upgrade progression, command cards, touch routing, saves, multiplayer protocol behavior, UI layouts, and screen-edge rendering with AddressSanitizer and guarded buffers. These do not replace physical-device checks; installation, radio interoperability, touch, audio, and full campaign playthroughs need broader device testing. See [VALIDATION.md](VALIDATION.md) for exact coverage and limitations.

## Community

Join the [StarDroid Discord community](https://discord.gg/F2FnjUrtQ) to discuss the game, share feedback, and get help with installation and controls.

For reproducible bugs, please [open a GitHub issue](https://github.com/kinopiowarrior/StarDroid/issues/new/choose) with your device, Android version, and steps to reproduce the problem.

## Contributing and credits

Bug reports should include the device, Android version, exact release/build, mode, map or mission, and reproduction steps. For multiplayer, include host/join roles and connection mode. See [CONTRIBUTING.md](CONTRIBUTING.md).

**Put together by ToadWarrior.** Built on [OpenBW](https://github.com/OpenBW/openbw) and the [heiner/openbw fork](https://github.com/heiner/openbw), with libsmacker, PyMS format references/listfiles, and the community map Weave. See [THIRD_PARTY.md](THIRD_PARTY.md) for component credits and license notices, and [UPSTREAM.json](UPSTREAM.json) for the pinned engine revision.

StarCraft and Brood War belong to Blizzard Entertainment. Their game data is supplied by the player and is not included in this repository or its APK releases.
