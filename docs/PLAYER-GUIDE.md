# StarDroid player guide

[Back to the project](../README.md)

## Importing and updating

Follow the [installation instructions](../README.md#install) to import the three core MPQs and the campaign/media archives. **App Options** is also the screen shown when game files are missing. It has one import action, the file guide, and **Installed content** counts.

Importing copies content into the selected app storage. Keep the original archives elsewhere: uninstalling StarDroid or clearing Android app data removes local imports, settings, and saves, including app-specific SD-card files. Android backup is disabled for this app. Update by installing the matching signed APK over the existing app.

Use **Import maps / ZIP…** in the custom-game or host map selector for `.scm`, `.scx`, and ZIP map collections. Campaign bundles belong in **Import game files**. The included community map is **Weave**; additional game maps are not bundled.

## Interface language

Open **App Options → Interface language**. **Automatic (game files)** detects the imported game text language; choose a specific language to override it. You can select **English**, **Deutsch**,
**Русский**, **Español** or **Français**. All five UI languages are available
with any imported game data. Import Russian MPQs through **Import game files**;
duplicate filename suffixes such as `(1)` are accepted. The setting is saved and
changes the port's menus, settings and interface labels. Voices, original unit
names and campaign text follow the imported game data; the setting does not
translate recordings. Technical diagnostic details remain available.

## Controls and settings

Open **Menu → Options → Controls** during a match. Its **Gesture guide** reflects your current preferences. The [README gesture table](../README.md#touch-controls) describes fresh-install defaults.

### Selection

**Original larvae selection** is on by default under **Selection settings**.
The Select Larvae command selects only larvae attached to the selected Hatchery,
Lair or Hive, up to three. Turn it off to select up to twelve larvae across your
buildings. Selection hold, double-tap and drag tolerances are grouped under
**Timing & accuracy**.

**One larva morph per press**, in the same Selection settings, is off by default.
Turn it on to morph one selected larva with each tap or hotkey press. Remaining
larvae keep their morph buttons; three quick presses can morph three larvae.
Zerglings and Scourge still hatch as pairs from one larva. Hydralisk, Mutalisk and
building morphs are unaffected. This is separate from Original larvae selection.

- **Drag to pan / hold to select** is the default. Start dragging immediately to move the camera; hold briefly first to draw a selection box. The default selection hold is about 180 ms.
- You can switch to **Drag to select / two fingers pan** under **Selection settings**.
- **Hold tap always selects** helps distinguish selection from a ground order.
- The selection-mode button arms selection when you need it. It can be moved to the other side or hidden.
- Double-tap selects units of the same type. Its timing window and selection tap tolerance are adjustable.

### Camera

- Two fingers always pan, even when pinch zoom is disabled.
- Pinch zoom and **Allow zoom out** are on by default. Existing saved preferences take priority when updating.
- Tap or drag the minimap to move the camera. **Minimap hold issues orders** is off by default in 1.0.1, including when applying a preset. Enable it in **Camera settings** if you want holding to issue an order; its default hold duration is about 450 ms. Updating preserves any existing saved choice.
- **Camera settings** includes pan speed, pinch sensitivity, reverse drag, and minimap hold timing.

### Widescreen

In **Menu → Options → Video Options**, choose **Off**, **On**, or
**+ over camera area** on the Widescreen row. The third choice includes the
phone's camera cutout area in the game display. The camera hole can cover pixels
in that area; regular On keeps the safe inset. App Options retains safe margins.

### Orders and descriptions

With an army selected, tapping open ground issues attack move by default. Use the attack/move toggle for ordinary movement, or choose commands directly from the command card. Workers use context-sensitive orders.

For explicit command-button control, enable **Disable ground-tap orders** in **Command settings**. It is unchecked by default. Hold a command icon to read its description; a description hold does not issue that command on release. Lower **Description hold** if the popup feels slow. A finger can use a short hold without hover-capable hardware.

Tap a production-queue icon to cancel that queued unit. Android **Back** cancels targeting, dismisses a dialog, or opens the game menu.

### Building placement

Choose a building, position the preview, and tap **Build**. **Cancel** abandons placement. **Build buttons follow placement** keeps both buttons below the preview, including when they overlap the interface. Covered controls cannot receive taps.

While dragging a building preview, hold your finger near an edge to scroll the
map. You can keep dragging beneath the command bar or over the minimap to scroll
down; the drag does not press those controls. The preview stays in the visible
world. Lift your finger to stop, then tap **Build** or **Cancel**. Scrolling works
in all eight directions, uses the camera pan-speed setting, and has the same
speed at different display rates. Scrolling starts farther from the top and side
edges. **Camera settings → Scroll while placing** enables or disables placement
edge scrolling and is on by default. Your choice is saved. Small one-finger and
two-finger pans preserve fractional movement.

Turn **Build buttons follow placement** off for a fixed button position. **Command settings** also controls the finger offset and confirmation; with confirmation disabled, releasing your finger places the building.

### Control groups and interface size

Enable **Show control-group buttons** under **Selection settings**. Tap an empty numbered group to assign the current selection; hold any group to replace its assignment. Tap an assigned group to recall it, and double-tap to center the camera. The small number shows how many units are assigned. A physical keyboard can use Ctrl+number to assign and number to recall.

Interface-size options adjust resources, command buttons, control groups, descriptions, and selection/move controls. **Command buttons** and **Control groups** have separate size controls. **Video Options → Smooth graphics (game + UI)** controls filtering across the battlefield, menus, HUD, portraits and other game artwork. It defaults to ON for new installs; turn it off for sharp pixels throughout. Hover and held-command descriptions can appear alongside command errors or target prompts, with space between the two. Control-group buttons retain their current size on upgrade and can be adjusted from 75% to 150%. They wrap when necessary to avoid overlapping resources. Sound options retain mute and separate effects/music volume sliders. Set Music to **0** to turn music off. The former music-enable switch is removed; an existing disabled setting becomes volume 0 once. Main-menu music continues without restarting when you change expansions. Music follows your race automatically. **Next track** skips to the next in-game theme while music is audible; it does not interrupt title, briefing or victory/defeat music. There is no manual track picker or sound-preview control. With the original music imports available, Brood War campaigns, custom games, and Nearby matches play four themes for each race: the expansion theme and original first theme share one complete file, followed by the original second and third themes. Missing expansion music falls back to the imported original soundtrack. **Miscellaneous → Cheats** contains single-player controls including **Invincibility** and **Force victory (end match)**. Invincibility protects your units from combat damage, leaves scripted mission deaths intact, and resets after loading or starting a game.

### Mouse and keyboard

Open **Controls → Mouse & keyboard** in the in-game menu. Its settings and mouse/keyboard help share one screen. These controls work alongside touch and gamepad controls.

- **Left click** selects a unit; drag a selection box to select several. **Double-click** or **Ctrl-click** selects units of the same type. **Shift-click** adds to the selection.
- **Right click** gives a context-sensitive move, attack, gather or interaction order. Hold **Shift** to queue orders. Left click confirms a targeted command or building placement; right click or **Escape** cancels it.
- Moving the pointer to a screen edge or dragging with the **middle mouse button** moves the camera. The **wheel** zooms the battlefield and scrolls menu lists.
- Choose **Original** command hotkeys or **Grid** keys: **Q W E** across the top row, **A S D** across the middle, and **Z X C** across the bottom. Tooltips show the active hotkey in brackets, with the key highlighted. **Arrow keys** move the camera. **Ctrl+0–9** assigns a control group; **Shift+0–9** adds the current selection to it; **0–9** recalls it. Press the same number twice quickly to center the camera on that group.
- **Space** jumps to the latest alert; **Tab** toggles minimap terrain. Hover over or hold the terrain button to see its description; unit markers remain visible when terrain is hidden. **Escape** cancels or goes back; **F10** opens the game menu. **Enter** opens Nearby multiplayer chat.
- Menu navigation buttons, including those in-game, show a highlighted letter or a key in brackets. Press it to open another menu, go back or switch screens. Settings, speed choices, lobby slots and race choices, imports/exports, Save Replay and in-place replay controls have no menu shortcut or letter highlight. App Options and the battlefield MENU button keep their normal labels. The briefing room’s **Replay** button also has its original highlighted shortcut (**R** in English). Navigation shortcuts stay the same in Original and Grid command modes; gameplay command hotkeys are unchanged. Tooltip borders match your race: Terran blue, Zerg red or Protoss yellow.

**Original** hotkeys are the default. This screen also lets you turn mouse edge scrolling, wheel zoom, middle-button camera dragging and arrow-key camera movement on or off, and adjust camera speed. Wheel scrolling in menu lists remains available when battlefield wheel zoom is off.

Typing a player name or chat message does not issue game commands. Losing focus or disconnecting an input device clears held input, so the camera or a modifier key cannot remain stuck.

### Gamepad controls

Open **Controls → Gamepad controls** in the in-game menu. By default, the left stick moves the pointer, the right stick/D-pad pans the camera, A/Cross selects, B/Circle issues right-click orders, X/Square targets an attack, and Y/Triangle cancels. Hold A/Cross while moving the pointer to box-select. Left shoulder slows the pointer for precision; triggers choose a control group, right shoulder recalls it, and left-stick press assigns it. Start opens the game menu.

The settings use the same original game panels and fonts as the other settings. You can reassign all buttons, swap sticks, change speeds and adjust the dead zone. Touch controls remain available, including when the gamepad is disabled. See [all defaults and alternatives](CHANGES-1.0.10.md#control-groups-and-controllers).

### Cache storage

The cache holds working copies of imported content. Build 80 removes empty archive space and prepares movies only when played. Existing movie caches are reclaimed automatically. With the tested classic archives, normal content cache is about 235 MB plus the active mission; a movie temporarily needs extra space while opening. Your original imports and saves are preserved.

### Imported data on an SD card

Open **App Options → Game data storage → Change data storage** from the main menu. Select a detected writable removable SD card or internal storage. Existing imported archives, campaign content and custom maps are copied and verified before switching locations and removing the old copies. Future imports use the selected location. Saves, replays, profiles, settings and working caches stay internal.

The SD-card folder is `Android/data/org.openbw.androidtest/files/game-data`. Android removes app-specific files when the app is uninstalled. Keep your original archives separately. Moving requires free space for the copied files plus a small reserve and is unavailable during a match, replay or multiplayer connection. If the selected card is removed, reinsert it before playing or moving the data back. The app does not silently start using a different folder.

### Saving and campaign progress

Use the in-game **Save Game** and **Load Saved** controls. Save-file import and export are not provided.

From build 81 onward, a campaign victory adds a green pixel check beside that mission for the Player ID that started it. Each ID has its own progress. Loading a save retains its original ID, including when continuing to the next mission. Deleting an ID and creating another with the same name starts fresh. Earlier versions did not store completion history, so old victories cannot be marked automatically.

The mission scrollbar, arrows, OK and Cancel have wider touch targets. Menu scrollbars use red pixel triangle arrows, a rectangular track and a hollow red circle thumb. Campaign characters and their episode labels appear after the menu slides into place.

### Unlimited supply

**Menu → Options → Miscellaneous → Cheats → Unlimited supply (200)** removes the need for supply buildings up to the normal 200-supply limit. Unit costs, technology and production time still apply. It affects only your player in single player, resets when loading or starting a game, and disables replay recording for that match. Supply buildings still work normally when the cheat is switched off.

### Replays

Build 81 adds **Load Replay** in the campaign-selection menu. Use **Save Replay** on the results screen of a newly started custom or nearby match to keep a local recording. The replay list uses the original load-menu artwork when available, shows map/player information, and includes **Delete** with a confirmation. **Import Replay** accepts a supported `.rep` file; **Export** shares the selected replay.

During playback, pause or resume, choose 1×–8× speed, switch between everyone's view and individual players, or restart. In build 82, tap or drag the red timeline to seek forward or backward. The display shows “Seeking…” while the engine catches up. Your pause and speed settings are retained. Playback is read-only: orders, cheats and game saves are disabled.

Use the same StarDroid build and game data for StarDroid recordings. Older computer recordings without AI settings, campaign recordings, resumed saves and cheated matches are not supported. Classic human-only Melee/Free-for-All files are accepted, but original-executable parity has not been verified. Remastered extensions and other game types are rejected with an explanation.

The campaign-fix revision of **1.1** changes simulation rules. Earlier StarDroid recordings need the APK that recorded them; their files are retained. Both nearby multiplayer devices must use this revised APK, even when both display version 1.1.

Older interface imports may not include the original load-menu artwork. The replay browser fits the available original menu layout automatically, including its filename list, scrollbar and information window. Import and Export sit side by side, with **Delete** centered below them at the bottom. Replay buttons use transparent backgrounds and the original menu font. Importing the original StarCraft or Brood War CD archive again supplies the load-menu assets automatically. Saving or exporting a replay shows an in-game red confirmation with OK.

### Campaign objects and voices

Move a worker into contact with a carryable mission object to pick it up. A
worker already carrying minerals, gas or another object must drop that cargo
first. Carried objects survive saves and drop when their carrier dies. Build 77
fixes neutral-owned objectives such as the psi emitter in Reign of Fire.
Mission dialogue has its own audio channel and overlapping transmission lines
queue; game effects cannot replace it. Leaving the mission or loading a save
intentionally resets the old world's audio.

With movable units selected, a normal tap on a visible beacon sends them to its center and keeps the selection. Explicit selection gestures still select objects. Minerals, unoccupied geysers and protected campaign objects retain their original protection when starting a map. Mission triggers may deliberately change protection; ordinary refineries and destructible mission structures remain attackable. A save made before this fix can retain the old object flags, so restart the affected mission to apply the corrected initialization.

Reign of Fire explicitly plays the Terran rescue cue when its triggers give the normal Zerglings and Drones to the player. Hunter Killers use the normal Zerg rescue cue. This authored difference is preserved; normal rescue cues follow the rescued unit's race.

## Local multiplayer

### Preparing devices

Use the same release APK and compatible classic data on every device. Enable Bluetooth and grant the requested nearby-device permissions. The combined mode also needs Wi-Fi Direct support and any permissions Android requests for it. Older Android versions can require Location permission and the system Location switch for discovery.

### Hosting

1. Open **Multiplayer**, select or create a player name, and choose the expansion. Brood War is the default expansion choice.
2. Select a connection mode, then **Host Game**.
3. Choose a map, speed, difficulty, game type, and Hero Mode. This screen shows player capacity; slot editing is in the lobby.
4. Create the lobby. Accept Android's discoverability/connection prompts and compare the pairing codes as devices join.
5. Tap unoccupied slots to choose **Player**, **Computer**, or **Closed**. Tap an occupied player's name, then a destination slot to move that player. Slot order affects map starting positions.
6. Set races and, in Teams mode, team numbers. Matching numbers share vision and victory, with separate armies and resources. Joining players can change their own race.
7. Tap the chat input to type and **Send** to chat. Start when the lobby reports readiness; the five-second countdown leads into the match.

### Joining

Open **Multiplayer**, choose your name and the matching expansion/connection mode, then **Join Game**. Select the host and tap **Join**. Use **Search Again** if it has just opened its lobby. Accept Android pairing prompts and confirm only matching in-app codes. Wait for the map transfer, choose your race, and wait for the host to start.

The selected map transfers automatically. Core game archives do not transfer. The combined mode begins over Bluetooth and can switch connections to Wi-Fi Direct on supported devices. Try Bluetooth-only mode if Wi-Fi Direct is unreliable.

There are up to eight map slots, including the host and computers. Connected-device capacity also depends on hardware and Android radio support. There is no Battle.net integration, internet lobby service, host migration, or mid-match rejoin flow. Keep the host active during play.

### Multiplayer saves

The host uses **Save Game** from the match menu. To continue later, the host chooses **Multiplayer → Load Saved**, selects the save, and opens its resumed lobby. Other devices rejoin, and the host arranges players in the saved human slots before starting. Saved match settings and computer slots are retained.

Use the same release build and compatible game data to resume. Saves are stored on the host device; this is not cloud saving or automatic reconnect.

## Hero Mode

Enable Hero Mode for a custom or multiplayer match. Heroes train at Engineering Bays, Evolution Chambers, and Forges, alongside their normal upgrades. Their command-card positions stay fixed.

Each player may have one of each hero form alive or queued, across all producers. A hero can be rebuilt after death. Training occupies its producer, so build extra upgrade buildings to train heroes and upgrade separately. Computers can use heroes on Easy, Medium, and Hard.

Heroes still require their relevant technology. Infested Duran needs a Lair or Hive and a Hydralisk Den; Infested Kerrigan needs a Hive and Queen's Nest. Tassadar and Zeratul can merge, but neither can be rebuilt until the resulting hero Archon dies. The [hero chart](campaign-heroes.json) records the roster, prices, and comparison stats. These optional rules do not change campaign units.

## Troubleshooting

| Problem | Check |
| --- | --- |
| Import screen keeps appearing | All three core archives must show as installed. Let each import finish. |
| Missing mission or movie | Import the corresponding original installation-media archive and check Installed content. |
| Grey upgrade icon | Read the description for the next level's technology requirement; also check resources, power, and whether another producer is researching it. |
| Camera moves instead of box selection | Hold before dragging, or change the drag preference. |
| Accidental ground orders | Use the selection-mode button, hold to select, or disable ground-tap orders. |
| Host is not visible | Check discovery permissions, Bluetooth discoverability, connection mode, and Search Again. |
| Wi-Fi Direct connection fails | Retry with Bluetooth-only mode on the devices. |
| APK update rejected | Check architecture and signing identity. Avoid uninstalling while troubleshooting if you need saved data. |

For unresolved problems, [report a bug](../../../issues/new/choose) with the device, Android version, release build, map or mission, settings, and steps. Do not attach MPQs or installation archives.

## Reporting an unexpected close

Version 1.0 includes local diagnostic evidence and native call stacks. After
the app closes unexpectedly, reopen it and choose **Credits** from the main menu, then
**Copy** in Diagnostics / credits. Share that text with your bug report.

Android 11+ can report previous process exits, including native/Java crashes,
ANRs, and low-memory kills. An update or requested stop is not itself a crash;
a SIGKILL without a specific reason does not identify memory pressure or a
vendor battery policy. Current memory figures are labeled separately from
historical exit records. Saved Java/renderer reports retain their timestamp.
Reports are local and are not sent automatically.

On Android 12+, the report also attempts to read the last native crash's
faulting thread and backtrace. Android can discard old traces, so copy the
report promptly. An update may retrieve a retained crash from the previous
build without another reproduction. The decoded stack stays local across
restarts; raw memory dumps and app log buffers are not copied.

Technical reference: [Android native exit traces](https://developer.android.com/reference/android/app/ApplicationExitInfo#getTraceInputStream()).

## Large map packs

Use **Custom Game → Import maps/ZIP** for nested ZIP packs with up to 10,000
`.scm`/`.scx` maps in one import. The limits are 8 GiB of uncompressed ZIP data
(including ignored files), 64 MiB per map and 50,000 ZIP entries. The app streams
files to storage and validates them in the background, reports progress, skips
invalid maps and deduplicates identical maps. Keep enough free storage for the
uncompressed pack plus at least 64 MiB. Nearby map-transfer limits are unchanged.

Computer players can ferry ground attackers with Dropships, Shuttles and
Overlords with Ventral Sacs when terrain blocks their route. The same controller
runs in all campaign missions and custom games. It respects mission unit and
upgrade restrictions, transport capacity and ownership, and resumes saved trips.
