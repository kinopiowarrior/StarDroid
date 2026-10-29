# StarDroid 1.1 — build 85

This candidate addresses the community reports following build 84.

- Detects English, Spanish, German, French and Russian interface language from the imported game text. **App Options → Interface language → Automatic (game files)** enables detection; explicit language choices remain available. Original game text and voices continue to come from the player's archives.
- Physical mouse right-clicks on resources and enemies now issue contextual gather/attack orders. Hovering a unit no longer gets mistaken for an active targeting cursor that must be cancelled.
- Transport selections retain **Unload** when some selected transports are empty. Load and Unload availability reflects the eligible selected transports.
- Weapon kills update the unit's displayed kill count, including tank turret kills, fighter kills credited to the parent, transport passengers and Spawn Broodlings. Counts saturate at 255, including Archon merges.
- Hallucination lifetime bars decrease proportionally. The selected-unit readout shows remaining lifetime, including hallucinations of spellcasters. Ordinary caster energy remains unchanged.
- Newly produced units temporarily appear in the portrait while their ready speech plays. Campaign transmissions take priority; another selection dismisses the ready portrait.
- Rescuable units retain their original sprite colors after joining the player, including turret graphics and saved games. Ordinary ownership transfers still change colors.
- Initially burrowed map and trigger-created units start burrowed immediately. A following trigger Order can unburrow and move them without first playing a visible burrow animation or losing the order.
- Revealed traps no longer repeatedly close and reopen merely because they cannot fire at a nearby target outside their fixed weapon arc. They still close when the threat leaves and reopen for a later target.
- Zerg mission 3 is correctly titled **The New Dominion** throughout the campaign catalog.
- **Game Options → Video Options → Interface sizes** has separate command-button and control-group controls. Existing group size is preserved on upgrade. Group buttons support 75–150%; command buttons retain their 100–120% range.
- **Video Options → Smooth game graphics** switches battlefield bitmap filtering. Off uses sharp nearest-neighbor scaling; the interface artwork also explicitly avoids filtering.
- A projectile animation briefly extending beyond a map edge no longer crashes the simulation while looking up fog visibility.

## Computer opponents

- Expansion selection prioritizes nearby resource clusters by ground travel. Depot placement accounts for the entire mineral line, nearby gas, legal footprints and terrain connectivity.
- Inaccessible resources require a transport. The AI keeps the depot on the resource island, requests transport technology when necessary, and retains the builder assignment until the landed worker can actually start construction.
- Terran AI builds bunkers and missile turrets, repairs damaged structures with a bounded number of nearby SCVs, and retains defensive Marines. Protoss AI builds Photon Cannons and retains defensive Zealots. These use ordinary resources, prerequisites and construction time.
- Nearby available combat units respond promptly to attacks on workers. Responding units are released from offensive-wave reservations so the attack manager does not immediately redirect them.
- Assault transports gather larger loads, normally at least 75% of capacity. Delayed trips can depart half full, and a final small group has a bounded fallback wait. Cargo is measured by transport space. Single-worker construction trips remain possible, and transports can return for subsequent groups.

## Supply report

The reported two-supply Infested Terran could not be reproduced with the tested classic archives. No speculative supply-cost change was made. Tests verify one supply during production and completion, queued production across a save/load, and exact release on cancellation, ordinary death, transport death and suicide. The three-unit queue uses three supply once completed.

## Compatibility and verification

Android version code is **85**; replay/nearby simulation compatibility is **86**, and room compatibility is **62**. Multiplayer participants must install this build together. Older StarDroid recordings retain their files but require the matching older application; the version gate prevents silent simulation divergence. The save format remains unchanged.

See [build 85 verification](QA-BUILD85.md) and the machine-readable [community report](../validation/community85.json). These are host and build checks, not a claim of complete campaign, UMS or EUD compatibility.
