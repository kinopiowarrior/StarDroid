# StarDroid 1.5.3

## Kerrigan’s hatch in Agent of the Swarm

In the fourth original Zerg mission, protecting the Chrysalis until the countdown reached zero could still end in defeat. Kerrigan would emerge, but the game had already started the “Chrysalis has been destroyed” sequence.

The mission deliberately kills the Chrysalis during the hatch. It waits one second before clearing the protection objective and creating Kerrigan. StarDroid counted the Chrysalis as gone as soon as the script issued the kill order. That let the loss trigger start during the same trigger pass.

Version 1.5.3 corrects when the **Command** trigger counts a unit with a pending death order. The unit stays in the count until the simulation processes its death, matching the original engine’s timing. The mission can then advance to the objective to infest or destroy Raynor’s Command Center. Early destruction of the Chrysalis or the later death of Kerrigan still ends the mission in defeat.

## Updating

Install `StarDroid-1.5.3.apk` over the existing app. To retry this mission, load a save from before the hatch or restart the mission. A save made after the old engine started the defeat sequence retains that state.

Use 1.5.3 on all multiplayer devices. StarDroid replays recorded under older simulation rules require the corresponding older version for playback. Campaign save format stays at version 22.

## Validation

Tested on the original campaign map with the normal mission loader and simulation. The test shortened the initial defense countdown to its last 31 seconds. The warning, hatch, waits, transmissions, objectives and result triggers then ran normally.

- Reproduced the reported sequence on 1.5.2. The Chrysalis received its scripted kill order, Kerrigan spawned, and the destruction trigger delivered defeat.
- Loaded a pre-hatch save created by 1.5.2 in 1.5.3 and reached Kerrigan’s new objective. Saving during the hatch and loading it again produced the same continuation.
- Confirmed defeat for early Chrysalis destruction and for Kerrigan’s death. Destroying Raynor’s Command Center after the hatch produced victory.
- Passed 419 trigger checks and 34 multiplayer compatibility checks. Verified current StarDroid replay playback and rejection of incompatible recordings.

The attached `StarDroid-1.5.3-source.zip` contains the matching source, ARM64 and x86-64 libraries, build instructions and test reports. Mission tests ran on the host. Physical Android gameplay testing is pending.
