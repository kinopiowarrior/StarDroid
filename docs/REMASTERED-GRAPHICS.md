# Remastered graphics: HD2 prototype

For the playable Android preview based on StarDroid 1.5.4, see [installation and supported unit files](HD2-ANDROID-PREVIEW.md). Preview 3 renders 29 supplied HD2 images in Android gameplay, including the five high-resolution unit bodies from preview 2.

The following reader and host-scene results were recorded during the earlier 1.5.2 prototype using assets from StarCraft build **1.23.10.13515**.

The reader and its synthetic tests are standalone. Engine-backed checks require the exact [1.5.2 source ZIP](https://github.com/kinopiowarrior/StarDroid/releases/download/v1.5.2/StarDroid-1.5.2-source.zip), SHA-256 `c8a7bf291ccf71dca22c559b5f98f15398bf876f125ade3e14e030355517db96`. The older application code on GitHub main is not that source snapshot. Pass the extracted source directory through `--source-root` to engine-backed test runners.

## Implemented

- A dependency-free C++17 reader for single-entry HD2 (2×) and HD (4×) ANIM sprite containers.
- Frame rectangles, signed offsets, layer names and texture descriptors, with the HD-coordinate convention preserved and converted for HD2 previews.
- Embedded DDS BC1/DXT1 and BC3/DXT5 texture decoding to straight-alpha RGBA, plus the ANIM-specific `BMP ` byte-mask encoding. This mask is not a Windows BMP file.
- Diffuse frame extraction and optional team-color mask composition for an unlit preview. Empty frames remain empty and are reported without inventing image dimensions.
- Bounds checks, input/allocation limits, explicit unsupported-format errors, and synthetic decoder/CLI tests.
- JSON inspection output and an optional PAM image export that retains alpha.
- `DecodedSprite`, which retains decoded diffuse/team-color atlases and frame metadata without retaining the input ANIM. Renderers can sample the atlases directly; animation does not require repeated decompression.

Metadata parsing validates table and payload ranges. Pixel payloads are decoded when requested; inspecting one frame is not verification of every other layer. The real-file validation separately checks every supplied frame and every present layer. Independent texture comparisons cover all 89 layers in the 31 supplied animations, totaling 42,461,360 pixels.

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

## Validation with supplied files

The optimized and AddressSanitizer/UndefinedBehaviorSanitizer runs passed **655 synthetic checks**, then validated **31 HD2 files, 1,993 frames and 89 texture layers**. Every decoded layer matched Pillow exactly; every frame crop and red/blue mask composition matched the independent NumPy reference. The reports retain source hashes and input hashes, not artwork or installation credentials.

```sh
python3 tests/run_remastered_real_assets.py \
  --samples /private/HD2/anim --game-build 1.23.10.13515 \
  --previews /private/hd2-previews
python3 tests/run_remastered_real_assets.py \
  --samples /private/HD2/anim --game-build 1.23.10.13515 --sanitize \
  --output-dir build-native/remastered-real-assets-sanitized \
  --report validation/remastered-real-assets-sanitized.json
```

These checks require Python 3.9+, Pillow and NumPy. `--previews` is optional and must point outside the repository. The largest supplied diffuse/team-color pair occupies 15,073,280 decoded pixel bytes. This is a single-asset measurement, not an Android cache or performance result.

Reports: [geometry](../validation/remastered-geometry.json), [host scene](../validation/remastered-scene.json), [synthetic](../validation/remastered-assets.json), [synthetic sanitizers](../validation/remastered-assets-sanitized.json), [real files](../validation/remastered-real-assets.json), [real-file sanitizers](../validation/remastered-real-assets-sanitized.json).

## Inspect an extracted sprite

Use a copy extracted from your own installation, kept outside the repository:

```sh
build-native/remastered-assets/remastered-inspect /path/to/main_000.anim
build-native/remastered-assets/remastered-inspect /path/to/main_000.anim \
  --frame 0 --team-color ff0000 --preview /path/to/frame.pam
```

Frame indices start at zero. The JSON reports texture-pixel offsets and crop dimensions. Cropping alone does not place a sprite in the world: the prototype also uses the ANIM canvas, image origin and flip state. Some ANIM canvases differ from the classic GRP canvas, including Broodling and Ghost. PAM stores four-channel RGBA and can be opened or converted by image tools supporting Netpbm PAM. The tool refuses to overwrite the input with a preview, and refuses to export an invalid zero-sized PAM for an empty frame.

## Supplied data and remaining coverage

The 31 supplied `HD2/anim/main_*.anim` files are sufficient for this stage. They include Scourge, Broodling, Defiler, Larva, Dragoon, Ghost and several effects/building images. No complete game installation needs to be transferred for these checks.

All 31 animation frame counts match their corresponding classic image entries, covering 1,993 frames. This validates counts for the direct sample mappings, not support for every `images.rel` reference or every unit in the game. Use the ANIM canvas when calculating HD frame origins; it need not equal four times the classic GRP canvas. The separate geometry check records both sizes and mirrored placement. A retail HD game capture is still needed as a visual positioning oracle.

A complete sprite import will need the remaining `HD2/anim` files and applicable image-reference data. A later terrain stage needs `HD2/tileset/*.dds.vr4` and corresponding tileset tables. Terrain containers are not supported here.

Supplied assets, installation metadata and generated captures stay outside version control. The code does not download game files or bundle artwork.

## Follow-up work

- Direct CASC extraction, broader graphics-pack support and on-device performance measurements.
- Full image/frame mapping, including `images.rel` references, and comparison of the prototype's HD anchor convention against retail captures.
- Expand higher-resolution Android artwork beyond the 29 verified images. Preview 1 reduced those sprites to classic resolution and palette. Preview 2 preserves their individual HD2 color samples.
- Terrain, interface artwork, wireframes, portraits, video, real-time lighting, and the other rendering effects.
- Retail HD capture comparisons and Android memory/performance measurements.

SD ANIM, multi-entry/reference containers, other DDS codecs, DX10 textures, cubemaps, and volume textures are rejected explicitly. Extra lighting layers may be decoded if they use a supported texture encoding, but this prototype does not apply their lighting semantics.

The [renderer integration plan](REMASTERED_RENDERER_PLAN.md) identifies the existing native/Java paths and the next acceptance checks. A successful sprite preview is not a claim of playable Remastered support.

## Format references

The reader is independently implemented from format facts; no third-party implementation or game data is vendored by this change.

- [Animosity `anim.rs`](https://github.com/neivv/animosity/blob/895eaccfc1ba49a5815799e25041926df23bd46b/src/anim.rs): ANIM container/texture/frame layouts and embedded texture encodings.
- [Animosity `frame_export.rs`](https://github.com/neivv/animosity/blob/895eaccfc1ba49a5815799e25041926df23bd46b/src/frame_export.rs): HD2 coordinate conversion and signed-offset rounding.
- [Animosity format overview](https://github.com/neivv/animosity/blob/895eaccfc1ba49a5815799e25041926df23bd46b/Readme.md): layer meanings and dependencies on classic frame geometry.
- [SCR-Graphics ANIM placement](https://github.com/saintofidiocy/SCR-Graphics/blob/0e73040f7343489486cdfdabad56a67e0e870ec9/anim.c): ANIM-canvas origins for HD sprites.
- [scmapanalyzer HD2 parser](https://github.com/marianogappa/scmapanalyzer/blob/dc1cfc0152db16c2f9630912debc89249f4a4a51/tools/hdbake/anim.go): independent ANIM-canvas positioning reference.
- [SCR-Graphics sprite rendering](https://github.com/saintofidiocy/SCR-Graphics/blob/0e73040f7343489486cdfdabad56a67e0e870ec9/shaders/sprite.txt): diffuse/team-color preview behavior.
- [CascLib storage documentation](https://www.zezula.net/en/casc/main.html): installation files that must be retained for extraction.

Validation reports bind their results to the listed source and input hashes. Synthetic tests, real-file decoding, geometry checks and host scenes are separate evidence; none establish full Android renderer compatibility or device performance.
