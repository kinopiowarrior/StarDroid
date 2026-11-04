# HD2 unit sprite preview for Android

This development APK, `1.5.4-hd2-preview1`, adds five unit bodies to the live StarDroid 1.5.4 battlefield renderer. It uses the same Android application ID, signing identity and version code (124) as 1.5.4. Existing game data and save formats are compatible. Device installation and performance still need handset testing.

## Try it

1. Install the preview APK. Open **App Options → Unit graphics → Import HD2 sprite ZIP**.
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

The renderer filters the HD2 artwork to the current battlefield resolution and maps it to the current tileset palette. The five bodies take about 11.4 MiB of cached pose data together. Frame direction, animation and team colors follow the engine. The game draws each body in its usual position among shadows, other units, effects and the interface. EUD maps and unsupported image effects use the classic renderer.

Other units, buildings, terrain, portraits and menus use classic artwork. The earlier desktop 2× scene used a separate full-color render target. A higher-resolution Android render target and full Remastered lighting remain future work.

## Verification

The Java importer was exercised with the supplied ZIP, partial packs, damaged archives, failed replacement and saved mode selection. All five real files were decoded and used in the production native render path. Tests covered a Dragoon in the original **First Strike** mission, owner colors, animation, horizontal mirroring, overlap, clipping, hidden and cloaked units, EUD fallback, missing files, and save continuation. The gameplay tests ran on the host using original game archives.

See `validation/hd2-import.json` and `validation/hd2-live.json` for the inputs and checks. The source ZIP includes the Android build and verification records. Game artwork and game archives are supplied by the player.
