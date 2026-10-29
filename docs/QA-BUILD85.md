# Build 85 verification

Tests use caller-owned classic game archives. Archives, extracted artwork, temporary saves and rendered captures stay outside the source distribution.

## Completed checks

- Native regression harness: mixed transport selections in both orders for all three races; hallucination lifetime/expiry and real caster energy; kill credit/HUD/cap; Infested Terran production and supply lifecycle; ready portraits with actual audio playback; rescue color save/load; native physical-mouse contextual orders; map-edge projectile visibility.
- Trap tests cover all six floor/wall types, plus all four fixed wall types losing their firing angle, losing their target and reopening for a later target.
- Actual campaign terrain and properties from missions **206, 301 and 302**: preplaced burrowed units and Create-with-properties followed immediately by Order. There are 13, 25 and 30 burrow-property actions respectively in the tested maps.
- All **64 campaign maps**, across **144 computer owners**, load and tick without AI errors. Mission-specific transport and upgrade restrictions remain enforced. This is an initialization smoke test, not full mission completion.
- AI tests: nearest natural for all three races; an actual island worker ferry and construction; bounded SCV repairs with HP recovery; prompt worker-defense reservations; all three transports in campaign and skirmish modes, first load at least 75%, repeat trips and deterministic saved continuation.
- **60,000 frames per race** on medium skirmish difficulty: paid economy, expansion, upgrades, advanced units, repeated attack waves, Terran/Protoss static defenses and exact 96-frame continuation after loading a saved game.
- **9,308 Java routing checks**, including mouse/gamepad transitions, hover-versus-targeting right-click, new size controls, smoothing, and non-overlapping display-option hit areas.
- **118,039 replay checks** in three seek scenarios, including intermediate rendering and bounded checkpoint memory; **1,430 multiplayer protocol assertions** including mismatched versions, delays, pauses, desync and disconnects.
- Five-language synthetic text-table detection and translated labels; malformed-table fallback. Two available real core-data sets both identified as English.
- Existing detector dispatch, Dunes SuiR and refinery resource-HUD regressions. Original menu assets and font fallback: **3,686 font checks**, **41 asset checks**, and **13 fallback button labels/actions**.
- Clean native preparation reproduces the patched engine files exactly. ARM64 and x86-64 native compilation, Android release assembly, Android lint, signature validation, 16-KiB native-library alignment and ZIP alignment passed. The signing certificate matches the saved StarDroid release key.

## Practical limits

No physical Android device, Bluetooth/Wi-Fi radio or Spanish installation was available for this run. Android event delivery, driver behavior, on-device rendering/performance and actual Spanish archive variants still need device testing. Synthetic Spanish strings exercise the detector; they do not establish compatibility with every translated release or mixed-language installation.

Infested Terran supply remained correct in the tested cases. A save or precise reproduction from an affected build would be needed to establish the cause of the reported intermittent double count.

## Reproduce

With JDK 17 and a host C++ compiler installed:

```sh
python3 tests/run_community85.py /path/to/core /path/to/StarCraft.mpq /path/to/BroodWar.mpq
python3 tests/build_native_host.py replay82 --skip-prepare
build-native/host/native-replay82 /path/to/core app/src/main/assets/native/weave.scx
python3 tests/run_menu_recovery.py /path/to/core
```

The core directory must contain `STARDAT.MPQ`, `BROODAT.MPQ` and `patch_rt.mpq`. The community runner writes detailed local logs and `validation/community85.json` without copying the game archives into the report.

On a device, check a Spanish import in Automatic language mode; right-click minerals, owned gas and enemies with a physical mouse; mix empty/loaded transports; change each interface size independently and restart the app; toggle smoothing at a fractional zoom; and play the reported campaign ambushes and a medium island skirmish. Check portrait/audio synchronization and rescue colors during those games.
