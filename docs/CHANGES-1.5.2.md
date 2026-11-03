# StarDroid 1.5.2

Version **1.5.2**, Android build **122**, fixes inactive trap artwork and the Medic command card.

## Traps

- Restores the closed floor or wall cover for inactive traps, concealing the brown base that previously gave away their positions.
- Covers all six types: Floor Gun Trap, Floor Missile Trap, Wall Missile Trap, Wall Flame Trap, Right Wall Missile Trap, and Right Wall Flame Trap.
- Keeps normal reveal, retraction, attacks, and destruction behavior. Hidden traps remain unavailable for selection and targeting.

The brown shapes were part of the map terrain. The earlier rendering rule hid the entire inactive trap sprite, including its cover. The original artwork includes a closed frame; the fix restores that visual cover rather than removing actual corpses.

## Medic

The command card now matches the original Brood War positions:

| Left | Middle | Right |
| --- | --- | --- |
| Move | Stop | — |
| Patrol | Hold Position | — |
| Heal | Restoration | Optical Flare |

- Fixed Heal colliding with Patrol and spilling into the top-right slot.
- Restored the original Restoration cast icon; Academy research keeps its own icon.
- Corrected Heal's original hotkey to **A**. Restoration remains **R**; Optical Flare remains **F**. Grid hotkeys follow their corrected positions.
- Scenario restrictions still determine which abilities appear. Custom EUD button positions retain priority.

**Patriot’s Blood**, the mission where Duran kills Stukov, enables Heal and Restoration but disables Optical Flare (Blind). Its bottom-right slot should be empty. This was verified directly from the original mission's technology settings, not inferred from the screenshot.

Positions were checked against the original 1.16.1 button table; hotkeys were checked against [Blizzard's Terran hotkey reference](https://classic.battle.net/scc/terran/hk.shtml).

## Updating

Install `StarDroid-1.5.2.apk` over the existing app without uninstalling. The release uses the same application ID and signing identity as 1.5.1. The 1.5.1 selection-box fix is retained. Game data is not included.

Use the attached `StarDroid-1.5.2-source.zip` for matching source, rebuilt native libraries, build instructions, and validation records. The repository's application-code snapshot is older; GitHub's automatic source archives do not contain this complete build.

## Validation

Passed **982 trap checks** using original game data: both trap locations from the reported mission, all six trap types, closed-cover pixels, initial disabled state, activation and weapon damage, retraction, destruction, visibility/fog/shroud, troop drawing order, and save/load continuation. Rendering leaves the serialized simulation state unchanged.

Medic validation passed **122 native checks**, **495 Android button-binding checks** across 17 native command cards, and **34 original-button icon comparisons**. It covers mission restrictions, permitted/researched combinations, original and grid hotkeys, and custom EUD positions.

The retained selection fix passed **1,368 Java routing checks** and **76 native selection checks** on this build. Clean native reconstruction matched all 494 prepared files. Both Android architectures were rebuilt; APK version, signing identity, payloads, ZIP integrity, and 16 KiB alignment were verified.

These are targeted host tests, including original-data rendering and simulation, plus Android packaging checks. No physical Android device or full campaign playthrough was tested. See `validation/traps122.json`, `validation/medic122.json`, `validation/selection121.json`, and `validation/android-build1.5.2.json` in the matching source ZIP.
