# HD2 unit sprite preview for Android

This development APK, `1.5.4-hd2-preview2`, draws five imported unit bodies at their original HD2 sprite resolution during StarDroid 1.5.4 gameplay. It uses the same Android application ID, signing identity and version code (124) as 1.5.4. Existing game data and save formats are compatible. Device installation and performance still need handset testing.

## Try it

1. Install the preview APK over 1.5.4 or preview 1. If the five sprites were already imported and HD2 is selected, start a mission. For a new import, open **App Options → Unit graphics → Import HD2 sprite ZIP**.
2. Select `filezz.zip` directly. The importer reads the `HD2/anim/` directory inside the ZIP. A successful import selects HD2 and shows the number of installed sprites.
3. Return to the game. The original Protoss campaign’s first mission, **First Strike**, contains Dragoons. Under **Unit graphics**, **Use classic unit sprites** and **Use HD2 unit sprites** switch the artwork without reimporting.

## Files the preview reads

| ZIP entry | Unit body | Animation frames |
| --- | --- | ---: |
| `HD2/anim/main_000.anim` | Scourge | 85 |
| `HD2/anim/main_005.anim` | Broodling | 204 |
| `HD2/anim/main_013.anim` | Defiler | 289 |
| `HD2/anim/main_122.anim` | Dragoon | 415 |
| `HD2/anim/main_228.anim` | Ghost | 229 |

These files contain their own textures and team-color masks. The supplied ZIP contains all five, and an archive containing any subset also works. A successful import replaces the selected pack. Omitted bodies use their classic art. The importer extracts the recognized files to private app storage and remembers the selected graphics mode between launches. The importer accepts ZIPs up to 128 MiB and individual supported files up to 16 MiB.

StarDroid still uses **StarDat.mpq**, **BrooDat.mpq**, and **Patch_rt.mpq** for its classic game data. **StarCraft.mpq** provides the original campaign and **BroodWar.mpq** the expansion campaign. Existing imports can be used directly. This preview uses extracted ANIM files. A `.build.info` file describes the installation build and is unnecessary for importing these sprites.

## What appears in game

Preview 1 averaged a 2×2 block of HD2 artwork into each classic world pixel, then mapped the result to the tileset palette. At higher zoom, the Dragoon could look blocky and lose fine shading. Preview 2 retains each texture sample and its team-color mask. A logical 640×480 viewport can now produce a 1280×960 Android bitmap with the same camera framing and touch coordinates. The five decoded sprite atlases and frame metadata occupy about 24.1 MiB. Frame direction, animation and team colors follow the engine. The game draws each body in its usual position among shadows, other units, effects and the interface. EUD maps and unsupported image effects use the classic renderer. When a classic effect reads the color behind it, the effect uses an indexed approximation for its covered pixels.

Android uses the existing **Smooth graphics** setting for the final display scale. A device texture or memory limit can select the lower-resolution fallback. The high-density path draws the world once per displayed frame and writes to the Android bitmap using its actual row stride.

Other units, buildings, terrain, selection circles, portraits and menus use classic artwork. The five HD2 bodies use the imported diffuse artwork and team colors. Full Remastered lighting and additional HD assets remain future work.

## Verification

The same placed Dragoon in *First Strike* was captured in preview 1 and preview 2 with identical camera position, animation pose and zoom. With a 640×480 logical viewport, preview 2 produced a 1280×960 output. In the Dragoon’s region, 1,045 logical pixels contained distinct higher-resolution samples. Comparing the rendered normal, mirrored and alternate-team-color poses against an independent decode of `main_122.anim` produced exact RGB matches for 3,370 opaque and 771 partly transparent texels per pose.

The selected Dragoon’s 31×7 health and shield gauge appeared in its original position, covering 868 output texels. The drag-selection border, foreground unit coverage, camera and pointer mapping, padded Android-style row stride and single simulation/presentation timing were checked separately. The tests use the production native render and bitmap presentation code on the host. Handset installation, frame time and memory pressure remain device checks.

The 1.5.4 importer continues to use the same files and persisted mode. The Android package is signed with the established certificate and contains both ARM64 and x86-64 native libraries. Game artwork is supplied by the player.

Test records include `validation/hd2-live.json`, `validation/hd2-density2.json` and `validation/hd2-texels.json`. Earlier host-scene and preview 1 reports retain their original scope.
