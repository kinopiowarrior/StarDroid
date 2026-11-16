# StarDroid 1.9.11 validation — build 192

Build 192 fixes the Java map validation path rejecting protected MPQ maps that the native engine can load. The reported Nearby Multiplayer error is “Mission archive has invalid bounds”. Recovered original Poker Defense maps from the same NovaY2 title family reproduce the failure; the screenshot's exact map version was not supplied.

Protected map layouts can contain misleading archive metadata and unused decoy block entries. The corrected map-transfer path preserves the complete bounded original file and validates the selected scenario against actual file bounds. It does not edit, repair or repack the user's map, and does not expand the transfer or save limits. Native parsing, simulation and rendering remain unchanged.

## Current evidence

- `map-archive1911.json`: focused production archive validation, protected layouts, selected scenario data and malformed-boundary regressions.
- `network-map1911.json`: the actual Java host/join map path for three original Poker Defense maps and Weave, including cached and uncached map transfer, byte-preserving map cache and save round-trips, invalid-map rollback, and retained 2–8-peer and protocol regression suites.
- `map-original1911.json`: 24 Java checks and six native extractions using original Poker Defense 7.1.4, 7.1.7 and 7SOLO files. All three reproduce the old Java rejection while native scenario reading succeeds. The corrected Java route accepts each file unchanged, and the native reader extracts the same scenario from the resulting map cache.
- `normal1911-review.json`: independent review binds the focused reports, the narrow production change, unchanged engine and native libraries, and preserved historical evidence.
- `normal1911-native.json`: all 927 compiled native inputs are reproduced and matched to the original published 1.9.9/build190 receipt. All six libraries and the complete native inventory match the pinned 1.9.10 source ZIP byte-for-byte. No native rebuild is performed.
- `normal1911-android-package.json` and `normal1911-signed-package.json`: actual Java 8, resources, DEX and APK packaging; version 1.9.11/code 192, existing app/certificate identity, v2/v3 signatures, 16 KiB ZIP/ELF alignment and unchanged signed payload.

Engine/protocol remain 174, saves remain schema 28, and valid imports since 1.9 remain accepted without reimport. The normal build's earlier import, graphics and zoom fixes are retained. Historical evidence below is preserved rather than relabeled as current testing.

## Limits

The exact map version shown in the screenshot was not supplied. Tests exercise recovered original maps from the same title family and authored archive cases. This corrects the reproduced map admission failure, not all EUD behavior or every protected-map format.

Network and storage checks use host adapters. No physical Android or two-phone multiplayer test was performed. Android lint was not run, and no 32-bit ABI was added. Original game data, imported artwork and tested custom maps remain external; only Weave is bundled.

## Reproduce the focused checks

Use the published 1.9.10 source ZIP and its matching prepared tree, the pinned JDK, a C++ compiler and a compatible `org.json` JAR. Supply the three original maps separately in `/external/original-poker-maps`: `[EUD]Poker Defense 7.1.4.scx`, `[EUD]Poker Defense 7.1.7.scx` and `[EUD]PokerDefense7SOLO.scx`. The original-map runner pins their hashes; the maps are not included in the source ZIP. Keep test output directories empty before running.

```sh
python3 tests/run_map_archive1911.py --output /external/map-archive1911 --json-jar /external/json.jar --baseline-root /external/normal1910
python3 tests/run_map_original1911.py --baseline-root /external/normal1910 --baseline-source /external/StarDroid-1.9.10-source.zip --maps /external/original-poker-maps --json-jar /external/json.jar --output /external/map-original1911
python3 tests/run_network_map1911.py --java-home .tools/jdk-17.0.21+9 --json-jar /external/json.jar --output /external/network-map1911 --map '/external/original-poker-maps/[EUD]Poker Defense 7.1.4.scx' --map '/external/original-poker-maps/[EUD]Poker Defense 7.1.7.scx' --map '/external/original-poker-maps/[EUD]PokerDefense7SOLO.scx' --map app/src/main/assets/native/weave.scx
python3 tests/run_normal1911_review.py --baseline-source /external/StarDroid-1.9.10-source.zip
```

See [build instructions](BUILD-1.9.11.txt) for source verification, packaging, signing and sealing.

---

# StarDroid 1.9.10 validation — build 191 (historical)

Build 191 distinguishes StarDroid developer source and its authored test files from actual StarCraft game data. The screenshot's “Invalid or unsupported patch” text comes from a read-only patch-version diagnostic, not the importer or a launch gate. The selected file was not supplied, so this release does not claim to have reproduced that person's exact input.

Source downloads are recognized by matching StarDroid identifiers in the app build file and Android manifest under the same directory. The game-data import paths reject that project with a specific explanation. A separate exact size and SHA-256 check rejects the three known synthetic core MPQs shipped in the developer source before replacing imported content. It does not require particular patch members or reject other archives merely because their version is unrecognized.

The patch diagnostic also identifies those exact test files if they were already imported, and tells the player to import StarCraft game files. Native patch reading and compatibility rules are unchanged. Valid modified patches keep their existing handling. Import guidance and errors are translated across the nine built-in interface languages.

## Current evidence

- `source-import1910.json`: source-project and exact test-fixture rejection through the production importer, with retained ordinary file and archive behavior.
- `patch-diagnosis1910.json`: production Java diagnostic and actual native patch reader; supplied original Classic archives; production conversion and MPQ writing from a complete official Classic CASC tree; exact developer fixtures and modified, missing, empty, cancelled and changed inputs. The converted core contains 2,881 files and is 208,678,764 bytes. The original and converted patches are identified without changing native code.
- `retained-import1910.json`: current import classes and retained launch, generation, transaction, cancellation, rollback, raw-file and ZIP archive behavior. Valid imports since 1.9 remain usable.
- `normal1910-review.json`: independent review binds the focused reports and confirms the narrow production changes and unchanged historical evidence.
- `normal1910-native.json`: all 927 compiled native inputs are reproduced and matched to the published 1.9.9/build190 receipt. All six libraries and the complete native inventory match the pinned 1.9.9 source ZIP byte-for-byte. No native rebuild is performed.
- `normal1910-android-package.json` and `normal1910-signed-package.json`: actual Java 8, resources, DEX and APK packaging; version 1.9.10/code 191, existing app/certificate identity, v2/v3 signatures, 16 KiB ZIP/ELF alignment and unchanged signed payload.

Engine/protocol remain 174, saves remain schema 28, and valid imports since 1.9 remain accepted without reimport. The normal build's earlier import, graphics and zoom fixes are retained. Historical evidence below is preserved rather than relabeled as current testing.

## Limits

The reporter's selected file was not supplied. An authored test patch reproduces the old diagnostic, but that does not prove it was their input. The new source check recognizes StarDroid project structure and the three exact known fixtures; it is not a general game-data authenticity check or expanded patch support.

Tests use host document-provider adapters and the actual native probe through a host process. No physical Android import or phone file-picker test was performed. The diagnostic alone does not establish whether a game can run. Android lint was not run, and no 32-bit ABI was added. Original game data and imported artwork remain external; only Weave is bundled.

## Reproduce the focused checks

Use the published 1.9.9 source ZIP and its matching prepared tree, the pinned JDK, a C++ compiler and a compatible `org.json` JAR. The native diagnostic check requires separately supplied original Classic MPQs and a decoded complete Classic tree from an official Remastered installation. Keep these files outside the source tree. Test output directories must be empty.

```sh
python3 tests/run_patch_diagnosis1910.py --baseline-source /external/StarDroid-1.9.9-source.zip --classic /external/classic --patch /external/patch_rt.mpq --stardat /external/StarDat.mpq --broodat /external/BrooDat.mpq --output /external/patch-diagnosis1910 --json-jar /external/json.jar
python3 tests/run_source_import1910.py --jdk .tools/jdk-17.0.21+9 --json-jar /external/json.jar --source-zip /external/StarDroid-1.9.9-source.zip --output /external/source-import1910 --core /external/patch_rt.mpq --core /external/StarDat.mpq --core /external/BrooDat.mpq
python3 tests/run_retained_import1910.py --json-jar /external/json.jar --output /external/retained-import1910
python3 tests/run_normal1910_review.py --baseline-source /external/StarDroid-1.9.9-source.zip
```

See [build instructions](BUILD-1.9.10.txt) for source verification, packaging, signing and sealing.

---

# StarDroid 1.9.9 validation — build 190 (historical)

Build 190 fixes installation-folder routing and hardens Classic fallback selection during Remastered imports.

The folder picker previously required `Data` and `.build.info` directly inside the selected folder. It now performs bounded discovery, routes a single complete installation to the existing Remastered importer, and stages loose Classic content through the existing atomic game-content transaction. Multiple complete installations are rejected before copying. The picker explains both supported layouts in all nine interface languages.

Native Classic selection now waits until the final priority winner is known before rejecting conflicting fallback candidates. A higher-priority localized or SD candidate can supersede a conflicting lower-priority fallback. Conflicts that remain at the selected priority are still rejected; archive integrity, path checks and cancellation remain enforced.

## Current evidence

- `folder-import199.json`: reproduces both Classic-folder and wrapped-installation failures from 1.9.8, then executes the current folder routing, provider reads, bounded staging, Classic transactions, cancellation, worker lifecycle and retained Remastered document checks.
- `casc-import199.json`: mocked-CASC precedence/conflict tests, retained strict importer checks and a complete import from a separately supplied official installation using actual CASC decoding.
- `casc-baseline199.json`: the exact reported 1.9.3 importer completes namespace selection and reads its first payload from the supplied official data. Independent review confirms that importer matches 1.9.8.
- `retained-import199.json`: valid imports from 1.9 onward remain accepted; incomplete imports remain blocked. Existing receipts, launch guards, transactions, rollback and content preservation are checked.
- `archive-regression199.json`: retained ZIP, actual JNI RAR and raw-MPQ routing, cancellation and rollback after the shared installer refactor.
- `normal199-review.json`: independent review binds the five import reports and verifies production scope, unchanged generated gameplay/rendering sources and historical records.
- `normal199-android-build.json` and `normal199-native.json`: both Android ABIs compiled from frozen native inputs; current library hashes and prepared source provenance verified.
- `normal199-android-package.json` and `normal199-signed-package.json`: actual Java 8, resources, DEX and APK packaging; version 1.9.9/code 190, existing app/certificate identity, v2/v3 signatures, 16 KiB ZIP/ELF alignment and unchanged signed payload.

Engine/protocol remain 174, saves remain schema 28, and valid imports since 1.9 remain accepted without reimport. Graphics and zoom fixes through 1.9.8 are retained. Historical evidence below is preserved rather than rerun or relabeled.

## Limits

The exact reported RAR was not supplied. The recovered official installation passed baseline selection, so its checks do not reproduce the reported error. The order-dependent conflict reproduction uses case aliases at a mocked CASC API boundary; the current CASC library can deduplicate those aliases before enumeration. This is defensive importer hardening, not proof that the reporter's archive now works.

The folder tests use production Java methods with host document-provider adapters. No physical Android import or real phone file-picker test was performed. Android lint was not run. Original game data and imported artwork remain external; only Weave is bundled.

## Reproduce the focused checks

Use the published 1.9.8 source ZIP and its matching prepared tree, the published 1.9.3 source ZIP for the historical native probe, the pinned JDK, a C++ compiler, and a compatible `org.json` JAR. The native check requires a separately supplied complete game installation. Test output directories must be empty; the archive regression keeps mutated fixtures outside the source tree.

```sh
python3 tests/run_casc_baseline199.py /external/StarCraft --baseline-source /external/StarDroid-1.9.3-source.zip --work /external/casc-baseline199
python3 tests/run_casc_import199.py /external/StarCraft --baseline-source /external/StarDroid-1.9.8-source.zip --work /external/casc-import199
python3 tests/run_folder_import199.py --baseline /external/normal198 --json-jar /external/json.jar --output /external/folder-import199
python3 tests/run_retained_import199.py --json-jar /external/json.jar --output /external/retained-import199
python3 tests/run_archive_regression199.py --jdk .tools/jdk-17.0.21+9 --json-jar /external/json.jar --output /external/archive-regression199
python3 tests/run_normal199_review.py --baseline-source /external/StarDroid-1.9.8-source.zip
```

---

# StarDroid 1.9.8 validation — build 189 (historical)

Build 189 corrects flashing and displaced shadows during Remastered burrowing and unburrowing.

Specific Infested Terran, Defiler, Drone, Hydralisk and Lurker shadow frames retain Classic coordinate offsets alongside HD-sized artwork. Applying the shared HD canvas to these offsets displaced the rendered shadow. Their IDs, frame ranges, flags, offsets and dimensions must match the known pattern before the renderer uses the corresponding Classic anchor for a local HD pose copy. HD alpha remains intact. Ordinary shadows, unit bodies and effects retain their existing placement, and the cached source pose is not changed.

## Current evidence

- `normal198-shadow-state.json`: real engine burrow/unburrow timelines, shadow metadata eligibility, mirror/frozen-Y placement and immutable cached poses.
- `normal198-render.json`: baseline/candidate full-engine temporal CPU/GLES captures, including cold/warm caches and retained rendering behavior.
- `normal198-android-build.json` and `normal198-native.json`: both Android ABIs compiled from frozen native inputs reproduced from shipped archives and patches; library hashes and prepared source provenance verified.
- `normal198-android-package.json` and `normal198-signed-package.json`: actual Java 8, resources, DEX and APK packaging; version 1.9.8/code 189, existing app/certificate identity, v2/v3 signatures, 16 KiB ZIP/ELF alignment and unchanged signed payload.

Engine/protocol remain 174, saves remain schema 28, and valid imports since 1.9 remain accepted. The 1.9.7 burrow-marker/Dragoon-death fix and 1.9.6 zoom limits are retained. Historical evidence below is preserved rather than rerun or relabeled.

Host rendering checks are not a physical phone test or a phone-FPS measurement. Android lint was not run. Original game data and imported artwork remain external; only Weave is bundled.

## Reproduce the focused checks

These checks require a verified, prepared 1.9.7 source tree, the published source ZIP, a C++ compiler, Mesa/EGL/GLES libraries and headers, and original game data kept outside this source tree. `classic-core` is the prepared Classic runtime data and `remastered-test-pack` contains the authored HD test assets. The supplied tests record their input hashes.

```sh
python3 tests/run_normal198_render.py --baseline-root /external/normal197 --baseline-source /external/StarDroid-1.9.7-source.zip --include /external/gles-headers --core /external/classic-core --hd-pack /external/remastered-test-pack
python3 tests/run_normal198_shadow_state.py --baseline-root /external/normal197 --baseline-source /external/StarDroid-1.9.7-source.zip --core /external/classic-core --hd-pack /external/remastered-test-pack
```

---

# StarDroid 1.9.7 validation — build 188 (historical)

Build 188 fixes Remastered Zerg burrow markers and Dragoon death rendering.

Some authored frames contain visible baked artwork but an entirely transparent optional lighting layer. The runtime now keeps that baked artwork and remembers the completed material lookup. Frames with usable lighting data continue to use it; truly empty frames remain empty. No animation timeline, simulation, visibility rule or imported data was changed.

## Current evidence

- `normal197-render.json`: authored Remastered frame decoding and CPU/GLES rendering, including blank lighting layers, usable lighting data and caching. The same gate compares retained effects/fog captures and full Classic CPU/GPU pixels against 1.9.6.
- `normal197-burrow.json`: focused burrow marker visibility and rendering checks.
- `normal197-death.json`: actual classic Dragoon death timelines compared before and after the runtime change, alongside the authored Remastered frame checks above.
- `normal197-android-build.json` and `normal197-native.json`: both Android ABIs compiled from frozen native inputs reproduced from shipped archives and patches; library hashes and prepared source provenance verified.
- `normal197-android-package.json` and `normal197-signed-package.json`: actual Java 8, resources, DEX and APK packaging; version 1.9.7/code 188, existing app/certificate identity, v2/v3 signatures, 16 KiB ZIP/ELF alignment and unchanged signed payload.

Engine/protocol stay 174, saves stay schema 28, and valid imports since 1.9 remain accepted. The minimum/maximum zoom controls from 1.9.6 are retained. Historical evidence below is preserved rather than rerun or relabeled.

Host rendering checks are not a physical phone test or a phone-FPS measurement. Android lint was not run. Original game data and imported artwork remain external; only Weave is bundled.

## Reproduce the focused checks

These checks require a verified, prepared 1.9.6 source tree, the published source ZIP, a C++ compiler, Mesa/EGL/GLES libraries and headers, and original game data kept outside this source tree. `classic-core` is the prepared Classic runtime data; `remastered-frames` contains the original authored animation files; `remastered-test-pack` is the prepared HD test pack. The supplied tests record their input hashes.

```sh
python3 tests/run_normal197_render.py --baseline-root /external/normal196 --baseline-source /external/StarDroid-1.9.6-source.zip --include /external/gles-headers --ndk .tools/android-ndk-r27d --core /external/classic-core --authored /external/remastered-frames
python3 tests/run_normal197_burrow.py --baseline-root /external/normal196 --baseline-source /external/StarDroid-1.9.6-source.zip --core /external/classic-core --hd-pack /external/remastered-test-pack
python3 tests/run_normal197_death.py --baseline-root /external/normal196 --baseline-source /external/StarDroid-1.9.6-source.zip --core /external/classic-core
```

---

# StarDroid 1.9.6 validation — build 187 (historical)

Build 187 replaces the zoom toggle and fixed-view option with minimum and maximum zoom percentages.

Run `python3 tests/run_normal196_zoom.py` for the focused checks.

## Current evidence

- `normal196-zoom.json`: focused execution of zoom limits, migration, input routes and the side-by-side settings layout, with hashes binding the tested source.
- `normal196-native.json`: exact reuse of all six build 184 native libraries and all compiled native inputs, including reproduced generated source, checked against the published 1.9.5 source archive.
- `normal196-android-package.json` and `normal196-signed-package.json`: actual Java 8, resources, DEX and APK packaging; version 1.9.6/code 187, existing app/certificate identity, v2/v3 signatures, 16 KiB ZIP/ELF alignment and unchanged signed payload.

Engine/protocol stay 174, saves stay schema 28, and valid imports since 1.9 remain accepted. Native code and libraries are unchanged from 1.9.5. Historical evidence below is preserved rather than rerun or relabeled.

Host Java and layout checks are not a physical phone test. Android lint was not run. Original game data and imported artwork remain external; only Weave is bundled.

---

# StarDroid 1.9.5 validation — build 184 (historical)

Build 184 fixes the HD/GPU fallback for remembered fog shadows and adds per-player statistics in App Options.

## Current evidence

- `normal195-fog.json`: production fog rendering, pixel correctness and GPU-readback regression checks.
- `normal195-stats.json`: statistics aggregation, persistence, profile isolation and duplicate-result checks.
- `normal195-mission.json`: actual Agent of the Swarm save/load fog state and fallback routing, with original assets kept outside the release.
- `normal195-effects.json`: retained GPU rendering and effect regressions against the current renderer.
- `normal195-review.json`: independent review of the feature, renderer and compatibility boundaries.
- `normal195-integration.json`: current gate binding, changed-source inventory and compatibility against published build 183.
- `normal195-android-build.json` and `normal195-native.json`: both Android ABIs compiled from frozen native inputs reproduced from shipped archives and patches; library hashes and prepared engine provenance checked.
- `normal195-android-package.json` and `normal195-signed-package.json`: actual Java 8, resources, DEX and APK packaging; version 1.9.5/code 184, existing app/certificate identity, v2/v3 signatures, 16 KiB ZIP/ELF alignment and unchanged signed payload.

Engine/protocol stay 174, saves stay schema 28, and valid imports since 1.9 remain accepted. Historical evidence below is preserved rather than rerun or relabeled.

Host rendering and Java checks are not a physical phone test or a phone-FPS measurement. Android lint was not run. Original game data and imported artwork remain external; only Weave is bundled.

---

# StarDroid 1.9.4 validation — build 183 (historical)

Build 183 preserves Select Larvae on researching or upgrading Hatcheries, Lairs and Hives. Spore Colony requirement text omits the selected Creep Colony while retaining real requirements.

## Current evidence

- `normal194-larva.json`: production command selection and research/upgrade regression checks.
- `normal194-spore.json`: production colony requirement text and prerequisite regression checks.
- `normal194-review.json`: independent source review, focused regression evidence and reproduction of the published baseline by reversing both patches.
- `normal194-integration.json`: current gate binding, changed-source inventory and compatibility against published build 182.
- `normal194-android-build.json` and `normal194-native.json`: both Android ABIs compiled from frozen native inputs reproduced from shipped archives and patches; library hashes and prepared engine provenance checked.
- `normal194-android-package.json` and `normal194-signed-package.json`: actual Java 8, resources, DEX and APK packaging; version 1.9.4/code 183, existing app/certificate identity, v2/v3 signatures, 16 KiB ZIP/ELF alignment and unchanged signed payload.

Engine/protocol stay 174, saves stay schema 28, and valid imports since 1.9 remain accepted. Historical evidence below is preserved rather than rerun or relabeled.

Host regression checks are not a physical phone test. Android lint was not run. Original game data and imported artwork remain external; only Weave is bundled.

---

# StarDroid 1.9.3 validation — build 182 (historical)

Build 182 limits the Large unit group icons option and its effective HUD behavior to Widescreen. Both widescreen modes allow it. With Widescreen off, the option is hidden and normal icons are used; the saved preference is retained. A stale hidden-option callback cannot change it.

## Current evidence

- `normal193-ui.json`: focused execution and review of the production option branch and HUD assignment, including saved preferences and widescreen transitions.
- `normal193-integration.json`: current gate binding, exact changed-source inventory and compatibility checks against published build 181.
- `normal193-native.json`: exact reuse of all six build 180 native libraries and every compiled native input, including generated source. No native rebuild is performed.
- `normal193-android-package.json` and `normal193-signed-package.json`: actual Java 8, resources, DEX and APK packaging; version 1.9.3/code 182, the existing app/certificate identity, v2/v3 signatures, 16 KiB ZIP/ELF alignment and unchanged signed payload.

Side-margin button alignment remains unchanged from build 181. The Disruption Web lighting correction remains byte-identical to build 180. Engine/protocol stay 174, saves stay schema 28, and valid imports since 1.9 remain accepted. Historical evidence below is preserved rather than rerun or relabeled.

Host UI checks are not a physical phone test. Android lint was not run. Original game data and imported artwork remain external; only Weave is bundled.

---

# StarDroid 1.9.2 build 181 validation (historical)

Build 181 changes only touch-control layout and Android version metadata. Selection and move/attack buttons preserve their normal vertical bounds when translated into side margins, including the lower selection-button setting. Group controls avoid the reserved touch-button space.

## Current evidence

- `margin181-controls.json`: executed production layout, hit-rectangle, availability and control-group interaction checks, with layout-only visual captures.
- `margin181-review.json`: independent source and visual layout review.
- `margin181-integration.json`: exact changed-source inventory, fresh gate binding and preservation of unrelated behavior.
- `margin181-native.json`: exact reuse of all six published build 180 native libraries, with every compiled native input checked against the pinned build 180 receipt. Native source is reproduced for comparison; native libraries are not rebuilt.
- `margin181-android-package.json` and `margin181-signed-package.json`: actual Java 8, resources, DEX and APK packaging; code 181, existing application/certificate identity, v2/v3 signatures, 16 KiB ZIP/ELF alignment and unchanged signed payload.

The Disruption Web Enhanced Lighting correction is byte-identical to build 180. Engine/protocol remain 174 and saves remain schema 28. Imports from 1.9 onward remain valid. Historical reports below are retained for their unchanged sources; they are not relabeled as 181 executions.

Host layout tests do not establish physical Android touch behavior. No phone test or Android lint run is claimed. Only Weave is bundled; original game data and imported artwork remain external.

---

# StarDroid 1.9.2 build 180 validation (historical)

Build 180 corrects Disruption Web turning dark with Enhanced Lighting enabled. Its authored diffuse artwork is already illuminated; a per-draw policy now bypasses surface lighting for this exact Web image/modifier/remap combination, including warm material-cache entries. Other effects retain their existing lighting policy. The replacement retains the earlier 1.9.2 changes and installs over build 179 using the same application identity and certificate.

## Current evidence

- `webfix180-effects.json`: focused reproduction and rendering checks for the corrected effect, with its actual source hashes and test boundaries.
- `webfix180-review.json`: independent source and visual review.
- `webfix180-integration.json`: exact changed-source inventory and compatibility checks against published build 179.

The earlier Java, input, localization, campaign and import reports below are retained evidence for unchanged sources. They were not rerun or relabeled as build 180 results. Host rendering checks do not establish behavior on every phone GPU.

The build 179 Enhanced Lighting check established CPU/GPU agreement but missed both renderers making Web dark. That pass did not establish correct lit appearance. The build 180 appearance oracle and visual review supersede that claim; the historical receipts remain unchanged.

## Build and compatibility

`webfix180-android-build.json` binds both rebuilt Android ABIs to frozen sources freshly reproduced from the shipped archives and patches. `webfix180-native.json` verifies native provenance and unchanged local networking. Engine/protocol remain 174 and saves remain schema 28.

`webfix180-android-package.json` binds Java 8, resources, DEX and APK contents to the frozen sources. `webfix180-signed-package.json` verifies version 1.9.2/code 180, the existing owner certificate, v2/v3 signatures, 16 KiB ZIP/ELF alignment and unchanged signed payload. `scripts/seal_webfix180.py` rejects stale gates, changed historical receipts and unrelated source edits before creating the matching source and checksums.

No import revision is raised. Data imported since 1.9 remains valid. Only Weave is bundled; original game data and imported artwork are excluded. No physical phone or phone GPU test is claimed. Android lint was not run.

---

# StarDroid 1.9.2 build 179 validation (historical)

The final build retains the earlier 1.9.2 fixes, removes the additional Player ID unlock control, preserves game data imported since 1.9, and repairs Remastered effects. The previous development build was code 178; code 179 installs over it using the same application identity and certificate.

## Current evidence

- `final-app192.json`: unlock access only in App Options and preservation of profile progress.
- `final-import192.json`: valid 1.9-era import receipts remain valid; required data checks still apply to incomplete or invalid imports.
- `final-effects192.json`: exact native effect reproduction, rendering assertions and capture scope for Protoss warp-in, Disruption Web and Dark Swarm. It checks the authored warp texture and transparent cloud composition across the tested CPU/GLES paths.
- `final-retained192.json`: fresh retained text, keyboard and controls checks without rewriting the historical build 178 reports.
- `final-integration192.json` and `final-review192.json`: assembled-source checks and independent review.

Each report records the actual source hashes and test boundaries. Host render captures do not establish phone GPU behavior, and host input adapters do not establish physical third-party keyboard behavior.

## Build and compatibility

`final-android-build192.json` binds both Android ABI builds to source freshly reproduced from the shipped archives and patches. `final-native192.json` checks native provenance and unchanged local networking. Engine/protocol remain 174 and saves remain schema 28.

`final-android-package192.json` binds Java 8, resources, DEX and APK contents to the frozen sources. `final-signed-package192.json` verifies version 1.9.2/code 179, the existing owner certificate, v2/v3 signatures, 16 KiB ZIP/ELF alignment and unchanged signed payload. `scripts/seal_final192.py` rejects stale gates and unrelated source edits before creating matching source and checksums.

No import revision is raised. Data already imported since 1.9 remains valid. Only Weave is bundled; original game data and imported artwork are excluded. No physical phone, third-party keyboard installation, phone GPU, battery or two-phone test is claimed. Android lint was not run. Historical validation below remains historical evidence.

---

# StarDroid 1.9.2 development build 178 validation (historical)

Normal StarDroid 1.9.2 / Android code 178 starts from the delivered 1.9.1 source. `normal-baseline192.json` compares that archive against the published 1.9.1 source: all production Java, assets, resources, native libraries, vendors and patches are byte-identical. Six packaging/documentation files differ; the published wording is retained here. Release packaging uses the published archive; both exact verified archives are accepted and the selected hash is recorded.

## Current fixes

- `normal-i18n192.json`: localized menu shortcut parsing and Cyrillic text decoding.
- `normal-keyboard192.json`: standard Android editor behavior, third-party IME entry, composition, selection, deletion, focus/lifecycle and keyboard overlay policy.
- `normal-controls192.json`: conditional side-margin controls, input coordinates, control-group replacement and selected-group presentation.
- `normal-campaign192.json`: localized mission titles, profile unlock access and preservation of earned progress.
- `normal-integration192.json`: combined current-source presentation/input regressions.
- `normal-reimport192.json`: current import receipts, transactions, launch guards and rollback/recovery.
- `normal-review192.json`: independent review of the final changes and remaining limits.

Each report states its exact exercised scope and source hashes. A passing host adapter does not establish physical Android or OEM keyboard behavior. Test-only synthetic data and caller-owned art are kept outside the shipped app.

## Packaging and compatibility

`normal-native192.json` records exact native provenance. The package script requires either byte-identical 1.9.1 native inputs/libraries or an explicit fresh `normal-android-build192.json` from all frozen Android native inputs. The final manifest records which method was used. Existing local multiplayer implementation, engine/protocol 174 and save schema 28 remain unchanged.

`normal-android-package192.json` binds Java 8, resources, DEX and unsigned APK payload to the current source. `normal-signed-package192.json` checks the existing application identity/certificate, version 1.9.2/code 178, v2/v3 signatures, 16 KiB ZIP/ELF alignment and unchanged payload. `scripts/seal_normal192.py` rejects stale/failed gates, unrelated existing-source edits, unreviewed new app files and changed historical receipts before packaging the matching source and checksums.

Data imported for 1.9/1.9.1 remains valid. Only Weave is bundled. No physical phone, third-party keyboard installation, GPU, battery or two-phone test is claimed. Android lint was not run. Historical validation below remains historical evidence.

---

# StarDroid 1.9.1 validation

Regular StarDroid 1.9.1 / Android code 176 is based on the exact published 1.9 source archive, SHA-256 `dee7ef6e8fcc33f78f55000eabd077877a31e4df4aa47d4c3ee25b9bfa970c50`.

## Map import

`validation/import-storage191.json` executes the exact 1.9 map worker and reproduces the reported insufficient-space message on a nonexistent `no_backup/archive-work` path while its parent has ample free space. Creating the folder makes the old worker succeed. The corrected worker passes 88 checks covering fresh SCM/SCX/ZIP imports, real JNI RAR4/RAR5/solid RAR decoding, recreated work directories, separate destinations, blocked paths, provider and CRC failures, cleanup, preservation of existing maps, and retained low/zero/unknown-space/overflow protection. The existing staging suite passes 33 checks, and `validation/archive-imports191.json` passes 60 archive cases / 211 checks.

The map payloads in this storage test are synthetic and its map validator is controlled. The reporter's exact map and Android document provider were not available; these results do not claim a phone test or full map playthrough.

## Command cancellation

`validation/cancel191.json` reproduces the old failure and passes 341 JVM checks, including 81 worker-route repetitions, plus 529 native cancellation checks. The retained authored-command pointer suite also passes 190 checks. SCVs, Probes and Drones cover basic/advanced menus, placement, repetition without movement, stale selections, malformed/authored rows, and retained construction/refund behavior. Classic and Remastered share the tested binding/dispatch methods; this is not a separate renderer test. The fix gives only the HUD-generated Cancel button a local identity, flushes pending selection input, checks that a construction submenu or placement mode is still active, then invokes the existing Escape path. Native/EUD commands retain their row/token validation. Press/release matching distinguishes local Cancel from a native Cancel action. The same dispatch is used by Classic and Remastered HUDs.

## Compatibility and artifacts

`validation/reimport191.json` reruns 1.9 import receipt, transaction, launch-guard and recovery checks against current Java sources. Existing 1.9 data receipts remain valid; no extraction revision is bumped. `validation/source-reproduction191.json` reproduces the original prepared engine tree and verifies unchanged native source and binary hashes. All six native libraries remain byte-identical to 1.9. Engine and multiplayer protocol are still 174; saves remain schema 28.

`validation/android-package191.json` binds Java 8 compilation, resource/DEX packaging and APK contents to the source. `validation/signed-package191.json` verifies the existing update certificate, application/version metadata, v2/v3 signatures, 16 KiB ZIP/ELF alignment and unchanged signed payload. Only Weave is bundled. `scripts/seal_release191.py` verifies current gates and source hashes, preserves historical reports, and packages the complete matching source.

No physical Android, GPU rendering, battery or two-phone test was performed. Android lint was not run. Older validation below is retained historical evidence, not a claim that every old suite was rerun for this patch.

---

# StarDroid 1.9 validation

Version 1.9 / Android code 175 is a Java-only import-policy release. Current
checks are recorded in `validation/reimport175.json`,
`validation/android-package175.json` and `validation/signed-package175.json`.
The package is compiled with JDK 17 / Java 8 output, SDK 35 and Build Tools 35,
then signed with the existing owner certificate. APK v2/v3 signatures, 16 KiB
ZIP and ELF load-segment alignment, version metadata, all payload hashes and
the Weave-only bundled map inventory are checked.

The import regression passes 16 transaction cases, 11 core-refresh cases,
19 launch-guard checks, 34 journal checks and 19 existing raw/ZIP import cases
(100 checks). It exercises the production Java
receipt/transaction/import code with synthetic data and a controlled CASC
extractor, plus the actual Activity/Surface guard methods extracted into a
minimal host adapter. Old Classic and extracted-graphics installs stay blocked
until the required imports succeed; partial imports, cancellation, rollback,
recovery and refreshed data moved between storage roots are covered. It does
not claim real CASC decoding or Android UI rendering.

All six native libraries, native sources, patches, JNI bridge and multiplayer
protocol implementations are byte-identical to delivered 1.8.31. The packager
checks both the pinned 1.8.31 source archive and signed APK. Simulation and
multiplayer remain build 174, and saves remain schema 28. Native test results
below are retained evidence for those exact binaries; those tests were not
rerun for this Java-only release.

No physical Android, real storage-provider import, GPU or two-phone test was
performed for 1.9. Android lint was not run.

# StarDroid 1.8.31 validation (inherited)

This release fixes player-removal list invalidation reported in issue #9.
Native simulation and both multiplayer protocol versions are 174. Save schema
remains 28. The baseline is the exact delivered 1.8.30 source archive. Older
numbered tests and reports remain historical evidence.

## Original Norad II reproduction

`validation/norad174.json` records the actual `array::at` failure in both
1.8.1/code143 and 1.8.30/code173. Native stacks show defeat cleanup following a
transferred turret into the neutral player's list, eventually reading its end
marker as a unit. Exact failing harness sources and logs are archived under
`validation/norad174/`.

The corrected engine passes four controlled scenarios using the original
mission: 12,400 frames idle; the same approach/natural-defeat sequence at 5,102;
bunker loading, unloading and destruction followed by defeat at 6,404; and
original victory after controlled Raynor/Dropship delivery at 6,750. All three
outcome scenarios also pass exact fresh-save/load continuation and result
frame comparisons. CPU world rendering runs throughout.

Raynor relocation/protection, temporary Norad protection during cargo tests,
and two test-created Dropships are explicit harness assists. The map, triggers,
AI and production gameplay code are unchanged. These are targeted original
mission regressions, not an unassisted campaign playthrough. The reporter's
exact save, commands and imported archive bytes were unavailable.

## Removal and compatibility gates

`validation/player-removal174.json` covers linked-unit transfer order, departure
and defeat cleanup, existing neutral units and other players, unit identity and
count conservation, empty lists, and neutral-owner termination.

`validation/compatibility174.json` covers saves and replays exported by the
actual immutable 171 engine and continued under 174, plus 174 recordings and save
roundtrips. Ordinary and EUD fixtures exercise simulation and seek parity.

`validation/protocol174.json` tests the actual Java handshake handlers:
matching 174 peers proceed, and incompatible versions are rejected before play.
These tests do not exercise Bluetooth or Wi-Fi hardware.

## Android and source artifacts

`validation/android-build174.json` binds both rebuilt native ABIs to unchanged
source inputs and the pinned NDK r27d/API26 compiler.
`validation/android-package174.json` binds the Java/resource/DEX compilation and
APK payload to those native libraries. Exactly one map is packaged: Weave.
`validation/signed-package174.json` checks the existing signer, v2/v3 signatures,
application/version IDs, unchanged payload, and 16 KiB ZIP/ELF alignment.
`scripts/seal_release174.py` checks current gates, source inventories and final
APK payload before creating the source ZIP and delivery checksums.

The existing Poker Defense support and unfinished hidden-training allocation
limitation remain as documented in 1.8.30. Its prior map play tests were not rerun
or relabeled as new evidence for this narrow cleanup fix.

No physical Android, GPU, battery, full campaign playthrough, or two-phone radio
test was performed. Android lint was not run. No ARM32 or older Android support
was added.
