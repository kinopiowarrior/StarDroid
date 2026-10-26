# StarDroid 1.1 — build 84

## Gameplay fixes
- Dunes of Shakuras: the SuiR script now honors its nearest-enemy attack mode, including Dark Templar heroes and workers, and can issue later orders to surviving units. Strategic attack mode is preserved.
- Protoss Assimilator: show remaining gas in the resource readout; keep shields with the building's HP details.
- AI detectors: select the nearest eligible detector for an attack area, count responders already on their way, and reuse the same responder when the attacker moves. Other detectors remain available for attacks elsewhere.

## Build and compatibility
- Version name: 1.1; Android version code: 84.
- Package: org.openbw.androidtest; Android 8.0 / API 26 or later.
- Architectures: arm64-v8a and x86_64.
- Engine/replay and multiplayer protocol IDs were advanced for the AI changes. Multiplayer peers must use matching builds.
- Native source commit: 816e0786b05c267b45620276bcc9c208c128243c.
- Native build: https://github.com/kinopiowarrior/StarDroid/actions/runs/40384383418
- APK build and lint: https://github.com/kinopiowarrior/StarDroid/actions/runs/40385626140
- Distributed APK is locally signed with the certificate used by earlier releases. Private signing material is excluded from this source archive.

## Validation
- Detector regression suite and Dunes script regression suite passed.
- Both Android native architectures compiled successfully.
- APK assembly and Android release lint passed.
- Final APK v2/v3 signatures, certificate identity, ZIP alignment, package/version fields, and all four native-library SHA-256 hashes verified.
- Full Dunes mission playthrough, on-device behavior, and the Assimilator HUD were not verified in-game. The repository's asset-dependent HUD test requires original game data.

## Rebuild
Follow README.md and scripts/build.py. The pinned native source archive and patch stack are included. The build scripts also support preparing the native tree on Windows; Android release packaging was verified on Linux.
The archive includes the matching prebuilt native libraries and NATIVE-BUILD-84.json. Original StarCraft game data and release signing keys are not included.
