# StarDroid 1.5

Version 1.5 brings together **UMS support and common EUD support**, plus the map, AI, control, and interface changes developed since the public 1.1.1 release.

## UMS and common EUD support

- **Use Map Settings** follows the scenario's player slots, races, teams, alliances, placed units, custom statistics, technology restrictions, and triggers.
- Added a shared EUD runtime for supported memory conditions and actions, masked writes, dynamic trigger lists, and live edits to game data. Supported edits can affect units, resources, technologies, locations, command buttons, wireframes, animations, terrain, and camera behavior.
- Map-defined names now appear throughout the relevant unit displays, including names for resources, beacons, crystals, and flags. Improved trigger text refresh, objectives, leaderboards, shared vision, and the lights-off behavior used by stacking maps.
- **Map keys** provides touch controls for keys defined by an EUD map. Tap for a press, hold to keep a key down, and use **Release all** to clear held keys. It also includes map mouse controls and chat input.
- **EUD options** offers Classic/Modern timing and Classic/64-bit wireframe layouts for maps that need them. These are compatibility choices, not graphics-quality upgrades. The optional **Continue after EUD errors** setting is off by default; skipping unsupported operations can still break a map's logic.
- Improved protected-map loading, custom text handling, scenario actions, and stock AI scripts. Solo UMS scenarios can follow **Set Next Scenario** after victory when the next map has already been imported.
- Added support for more terrain pieces and corrected their movement and placement data, addressing blocked paths and failed unit creation in affected maps.

EUD compatibility is still partial. Arbitrary Windows code/DLL execution and full Remastered graphics are not supported. Some maps, including the unresolved Keys Movement and Bounce Ball cases, still have compatibility problems.

## Map browser and multiplayer setup

- Tap a map thumbnail for a themed popup with an enlarged minimap and scrollable description.
- Map and ZIP imports now show progress and a final result, including partial successes, instead of leaving a stale “reading map data” message.
- **List options** filters melee, UMS, EUD, and unclassified maps; sorts by name, import date, file size, player count, or map dimensions; and manages imported-map deletion. The displayed count reflects the current list.
- Improved Classic/Brood War classification using map content. Classic shows confirmed compatible maps; Brood War also keeps unclassified maps available.
- Added player-color dropdowns outside UMS. Default force labels now read **Team**, while custom scenario team names are preserved.
- Multiplayer expansion selection now comes before Player ID selection.

## Match options and AI

- Non-UMS **Match Options** groups Hero Mode with optional expanded larva selection, one-larva-per-press morphing, initial worker harvesting, and smart casting with one eligible caster per cast. Multiplayer hosts set the shared rules before the match; saves and replays retain them.
- Added **Original** CPU difficulty, using the imported race and expansion AI scripts without the additional policies used by Easy, Normal, and Hard.
- Improved native skirmish attacks: surplus Terran forces join attacks, surviving waves recover from stale or unreachable targets, and towns avoid recruiting the same units twice.
- Improved defense of distant and unfinished expansions, including responses to attacks on multiple bases.
- Idle, empty Dropships, Shuttles, and Overlords return to a surviving base after delivery or evasion without interrupting active transport jobs.
- The score screen shows a skirmish computer's difficulty after its race, such as **Terran (Normal)**, including defeated players and saved/replayed matches.

## Gameplay, controls, and presentation

- SCV touch commands can resume unfinished construction or repair eligible damaged units and buildings. Stale builder assignments no longer block a replacement SCV.
- Fixed repeated Lurker unburrow commands and improved scripted burrowed ambush behavior.
- Canceling Zerg construction selects the returned Drone, including the Extractor case.
- Campaign command cards hide scenario-forbidden commands while keeping temporarily unavailable, permitted commands visible. Improved prerequisite descriptions and hiding of upgrades already being researched.
- Tap the selected unit portrait to center the camera. Minimap rendering and input now remain consistent when battlefield zoom changes.
- Improved large-text layouts, HUD input shielding, and zoom locking. Removed character fade-in from campaign selection.
- Fixed detached effects, such as afterimages and projectiles, appearing through fog without current visibility.
- Corrected several scenario details, including transported-unit trigger handling, resource-object pickup, Capture the Flag timing, and Queen infestation score accounting.

## Languages and map audio

- Added **Simplified Chinese, Traditional Chinese, Korean, and Japanese**, alongside English, Russian, German, Spanish, and French.
- Improved legacy text decoding, automatic Korean EUD text handling, Unicode fallback, wrapping, and localized archive filenames.
- Added **GSM 6.10 WAV49 (codec 49)** support and broader handling of valid WAV files, while retaining Ogg playback.
- Stock sounds referenced by maps can be found in the imported game archives. Missing or damaged optional sounds no longer interrupt scenario triggers or transmissions; genuinely missing audio is not recreated.

## Installed game-data patch

- **App Options → Game-data patch** identifies recognized classic patch-data families from **1.02 through 1.17.0**, separately from the StarDroid app version.
- Detection works offline and checks both archive fingerprints and decoded contents, allowing recognized data to be identified even after repacking.
- Shared data is shown as a version range when an exact patch cannot be distinguished. Unknown or modified data is not given a guessed version.

## Updating and source

Install **StarDroid-1.5.apk** over your existing app without uninstalling. Game data is not included. Use the same app version on all multiplayer devices; older incompatible replays require the build that recorded them.

Version 1.5 is **build 120**, with the same gameplay as test119 under the new version number. For the matching source, build instructions, and validation records, download **StarDroid-1.5-source.zip** from this release. Separate test-map packs are not included.

The source records targeted native, UI, save/replay, multiplayer-simulation, and packaging checks. These do not establish complete compatibility with every map or full physical-device, radio, and campaign testing.

[Player guide](https://github.com/kinopiowarrior/StarDroid/blob/main/docs/PLAYER-GUIDE.md) · [Discord](https://discord.gg/F2FnjUrtQ)
