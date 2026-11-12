# StarDroid 1.9

Version 1.9 brings together the updates developed since the public 1.8.1 release and requires a fresh game-data import for every installation.

## Required data refresh

Install over the existing app without uninstalling or clearing its storage. The app blocks play and shows import instructions until the required imports complete successfully.

- **Classic:** re-import `StarDat.mpq`, `BrooDat.mpq` and `Patch_rt.mpq`. You can select them together or import each file separately. Import the campaign/media archives too if you need to refresh their content.
- **Remastered / Carbot:** re-import the complete installation folder, or a ZIP/RAR containing `Data` and its matching `.build.info`. This refreshes the converted Classic content and graphics together.
- **Separate graphics packs:** re-import the three Classic files and the graphics pack.

Maps, replays and player-backup imports do not satisfy the game-data requirement. Canceling or failing an import does not mark it complete. Successful imports clear the requirement; it is not repeated on every launch.

Player IDs, campaign progress, saves, replays, imported maps and settings are retained. Game data is not included with the app.

## Included updates since 1.8.1

- Classic battlefield GPU rendering when GPU is selected, automatic Remastered rendering optimizations, and an optional top-center FPS counter. CPU rendering remains available.
- Local observer mode for non-UMS skirmishes, persistent match preferences, and improved replay browsing and results screens.
- Campaign menu progression, placement previews, unit panels, command buttons, portraits and hover-animation fixes.
- Improved complete-installation imports, ZIP/RAR handling, language assets and imported-map browsing. Weave is the only bundled map.
- Skirmish elimination notices and continued observation after a network player loses while opponents remain.
- Bounded common EUD support and exact pointer selection for map-authored buttons, including Poker Defense's Hero Mixer pages and purchases.
- Fixed the Norad II defeat crash caused by moving linked turrets during player cleanup (issue #9).

EUD support remains partial. Poker Defense's repeated hero purchases can still leave unfinished hidden training allocations; a complete playthrough is not claimed. Windows executable or DLL code is not run.

## Compatibility and verification

Android 8.0 or later; ARM64 and x86-64. Use 1.9 on every multiplayer device. Install over the existing app using the same signing identity.

The 1.9 change adds the data-refresh requirement to 1.8.31. Its native engine libraries remain byte-for-byte identical to 1.8.31: engine/network version 174 and save schema 28. Previously supported save and replay formats remain supported. Re-importing different or modified game files can affect old matches; use the same original data when continuing them.

The matching source ZIP contains focused import tests, packaging checks and historical engine/regression reports. No physical Android device or complete campaign/EUD playthrough was tested for this release.

Download **StarDroid-1.9-source.zip** from the release for the matching app source. GitHub's automatic source archives contain the older repository snapshot. APK, source ZIP and validation-report checksums are in **SHA256SUMS.txt**.
