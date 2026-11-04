# HD2 battlefield sprites for Android

`1.5.4-hd2-preview3` uses 29 of the 31 HD2 animations in the supplied `filezz.zip`. The five unit bodies from preview 2 keep their original 2× detail. This build adds Larva, the Infested Command Center, birth and death animations, construction art, hit effects, scenery and shadows.

## Install and try it

1. Install the APK over preview 2. It uses the same Android application ID, release signing identity and version code (124) as StarDroid 1.5.4.
2. Open **App Options → Battlefield sprites → Import HD2 sprite ZIP** and select `filezz.zip` again. Preview 2 saved only the five entries it could use. A successful re-import of the supplied ZIP selects HD2 and reports **29 of 29**. Existing five-file imports still work, and Classic/HD2 selection persists across restarts.
3. Return to the game menu and start a mission. Look at the Larva beside the starting Hatchery in **Among the Ruins**, the first original Zerg mission. **Backwater Station**, the second original Terran mission, contains an Infested Command Center. **First Strike** still shows the preview 2 Dragoons.

## Included files

Each number below is the three-digit portion of `HD2/anim/main_NNN.anim` inside the ZIP. The ANIM files include their texture data. A ZIP with any subset of the supported entries also works.

| Graphics | IDs | What they draw |
| --- | --- | --- |
| Units and building | `000`, `005`, `013`, `036`, `063`, `122`, `228` | Scourge, Broodling, Defiler, Larva, Infested Command Center, Dragoon, Ghost |
| Birth, death and remnants | `002`, `003`, `004`, `007`, `024`, `045` | Scourge birth, death and explosion, Broodling remnants, Egg remnants, Overlord death |
| Construction and weapon effects | `325`, `527`, `545` | Large Terran construction, Gauss Rifle hit, Hallucination hit |
| Map scenery | `723`, `747`, `758`, `862` | Badlands, Installation, Twilight and Desert scenery |
| Shadows | `001`, `006`, `081`, `100`, `145`, `177`, `202`, `348`, `893` | Scourge, Broodling, Greater Spire, Spore Colony, Reaver, Cybernetics Core, Stargate, Mineral Field Type 1 and a Desert doodad |

`361` (small Lockdown field) and `508` (unused Venom hit) use special palette effects. They continue through the classic renderer. A shadow file upgrades the shadow of the named object. For example, the Reaver gets an HD2 shadow while its body uses classic artwork.

Your existing `StarDat.mpq`, `BrooDat.mpq` and `Patch_rt.mpq` supply the classic game data. `StarCraft.mpq` and `BroodWar.mpq` supply their respective campaigns. For these HD2 graphics, the extracted ZIP is the only extra input.

## Rendering

The live renderer draws the imported sprites in their original image order. Supported shadows use the imported alpha mask to darken what is underneath, including other HD2 sprites. Maximum coverage uses an approximation of the classic shadow strength. A remembered HD2 building uses its last observed appearance while under fog. On loading a save that has no saved HD presentation context, a remembered building uses the saved classic imagery until it is seen again.

With a 640×480 logical viewport, HD2 can produce a 1280×960 Android bitmap. Controls, camera and selection use the same logical coordinates. The complete supplied pack occupies about **47 MiB** of resident sprite data. A device texture or memory limit can select a lower-resolution fallback. EUD maps use the classic rendering path. Terrain tiles, portraits and menus use classic assets.

## Validation

The host tests rendered all 29 images individually and checked that each changed the game framebuffer. In Among the Ruins, the starting Larva advanced through 25 distinct poses. In Backwater Station, the Infested Command Center retained its observed pose and color after moving under fog. A separate test checked all 1,490 output pixels of a Scourge shadow drawn over an HD2 sprite.

The importer passed 345 checks, including upgrades from a five-file pack and recovery from failed imports. The source ZIP includes the engine patches, importer, native libraries, build instructions and test records. Frame rate and memory use on a physical Android device remain unmeasured.
