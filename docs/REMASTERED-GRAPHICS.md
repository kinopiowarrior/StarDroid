# Remastered graphics: asset reader prototype

This is the first implementation stage, built against the supplied StarDroid 1.5 source. It adds a standalone native sprite reader and a developer inspection tool. It does **not** enable Remastered graphics in the Android app, change the simulation, or produce a new playable APK.

## Implemented

- A dependency-free C++17 reader for single-entry HD2 (2×) and HD (4×) ANIM sprite containers.
- Frame rectangles, signed offsets, layer names and texture descriptors, with the HD-coordinate convention preserved and converted for HD2 previews.
- Embedded DDS BC1/DXT1 and BC3/DXT5 texture decoding to straight-alpha RGBA, plus the ANIM-specific `BMP ` byte-mask encoding. This mask is not a Windows BMP file.
- Diffuse frame extraction and optional team-color mask composition for an unlit preview. Empty frames remain empty and are reported without inventing image dimensions.
- Bounds checks, input/allocation limits, explicit unsupported-format errors, and synthetic decoder/CLI tests.
- JSON inspection output and an optional PAM image export that retains alpha.

Metadata parsing validates table and payload ranges. Pixel payloads are decoded when requested; inspecting one frame is not verification of every other layer. The implementation has not yet been checked against the user's Remastered files.

Default limits are 128 MiB per input, 16 million pixels per decoded image, 32 million working pixels per composed preview, 16,384 pixels per texture dimension, and 8,192 frames. These are development-reader limits, not an Android texture-cache budget. Invalid data is rejected before pixel access or allocation.

## Run without game files

From the source root, with Python 3 and a C++17 compiler installed:

```sh
python3 tests/run_remastered_assets.py
python3 tests/run_remastered_assets.py --sanitize \
  --output-dir build-native/remastered-assets-sanitized \
  --report validation/remastered-assets-sanitized.json
```

The tests generate their own small textures and ANIM containers. They contain no Blizzard artwork. The runner also builds `build-native/remastered-assets/remastered-inspect`.

The sanitizer run enables AddressSanitizer and UndefinedBehaviorSanitizer. LeakSanitizer is disabled because the development workspace's process supervisor is incompatible with it; leak testing is not claimed.

## Inspect an extracted sprite

Use a copy extracted from your own installation, kept outside the repository:

```sh
build-native/remastered-assets/remastered-inspect /path/to/main_000.anim
build-native/remastered-assets/remastered-inspect /path/to/main_000.anim \
  --frame 0 --team-color ff0000 --preview /path/to/frame.pam
```

Frame indices start at zero. The JSON reports texture-pixel offsets and crop dimensions; these are not yet verified world-space unit anchors. PAM stores four-channel RGBA and can be opened or converted by image tools supporting Netpbm PAM. The tool refuses to overwrite the input with a preview, and refuses to export an invalid zero-sized PAM for an empty frame.

## Files needed for the next stage

For the first sprite test, provide two or three extracted files from **`HD2/anim/main_*.anim`**, retaining their original paths. Include the game build or `.build.info` if available. These ANIM files normally contain their own texture layers. A sprite with visible player colors and directional frames is particularly useful. Root **`anim/main_*.anim`** files are the higher-resolution 4× variant.

For a later terrain stage, useful samples are **`HD2/tileset/*.dds.vr4`** and the corresponding tileset tables. Terrain containers are not supported by this first reader.

If no files have been extracted, the source is the installed game's **entire `Data` directory, together with `.build.info`** from the installation root. Preserve `Data/config`, indices, and the original folder structure. A single `data.xxx` chunk is not a standalone archive. Use an installation with its Remastered graphics downloaded. For a large installation, a folder listing and `.build.info` can establish what is available before transferring the complete data.

Keep supplied assets and generated previews private and outside version control. The code does not download game files, extract CASC storage, or bundle artwork.

## Still to implement

- CASC import/extraction and graphics-pack caching on Android.
- Matching an engine image and frame to the correct sprite, including `images.rel` references and classic GRP anchors.
- Drawing HD sprites in the correct depth and fog order, with a separate render resolution and safe classic fallback for EUD-modified graphics.
- Terrain, interface artwork, wireframes, portraits, video, real-time lighting, and the other rendering effects.
- Real-file decoding checks, actual render comparisons, and Android memory/performance measurements.

SD ANIM, multi-entry/reference containers, other DDS codecs, DX10 textures, cubemaps, and volume textures are rejected explicitly. Extra lighting layers may be decoded if they use a supported texture encoding, but this prototype does not apply their lighting semantics.

The [renderer integration plan](REMASTERED_RENDERER_PLAN.md) identifies the existing native/Java paths and the next acceptance checks. A successful sprite preview is not a claim of playable Remastered support.

## Format references

The reader is independently implemented from format facts; no third-party implementation or game data is vendored by this change.

- [Animosity `anim.rs`](https://github.com/neivv/animosity/blob/895eaccfc1ba49a5815799e25041926df23bd46b/src/anim.rs): ANIM container/texture/frame layouts and embedded texture encodings.
- [Animosity `frame_export.rs`](https://github.com/neivv/animosity/blob/895eaccfc1ba49a5815799e25041926df23bd46b/src/frame_export.rs): HD2 coordinate conversion and signed-offset rounding.
- [Animosity format overview](https://github.com/neivv/animosity/blob/895eaccfc1ba49a5815799e25041926df23bd46b/Readme.md): layer meanings and dependencies on classic frame geometry.
- [SCR-Graphics sprite rendering](https://github.com/saintofidiocy/SCR-Graphics/blob/0e73040f7343489486cdfdabad56a67e0e870ec9/shaders/sprite.txt): diffuse/team-color preview behavior.
- [CascLib storage documentation](https://www.zezula.net/en/casc/main.html): installation files that must be retained for extraction.

Recorded synthetic checks apply only to the files and hashes listed in their validation reports. They do not validate a real installation, hardware display, full scene, or Android renderer.
