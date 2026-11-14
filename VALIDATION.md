# StarDroid 1.9.2 validation — replacement build 180

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
