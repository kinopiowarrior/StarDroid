# StarDroid 1.1 build 86

- Queen's Nest no longer offers Parasite research. Queens have Parasite and
  Infestation from the start. Spawn Broodlings and Ensnare retain their research
  requirements, and campaign/custom-map technology permissions still apply.
- Broodlings display a maximum lifetime of 180 points instead of 1,800 raw
  frames. Hallucinations likewise show 135 instead of 1,350. Numeric displays use
  the original game's frame-to-point conversion; battlefield bars retain frame
  precision.
- Broodling lifetime remains the original 1,800 simulation frames, approximately
  75.6 seconds at the default Fastest setting of 42 ms/frame. It was already
  correct. Hallucinations retain 1,350 frames. These are lifetime points, not
  ordinary spell energy or seconds at every speed.
- Restored the pre-build-85 Android Paint behavior for shared UI artwork:
  main menus, dialogs, HUD graphics, wireframes, portraits, briefing overlays,
  thumbnails and result screens. The battlefield smoothing toggle uses its own
  Paint and no longer changes this shared UI behavior.
- Smooth game graphics defaults to ON for new installs. Existing saved choices
  remain in effect.

Queen spell costs remain Parasite 75, Ensnare 75, and Spawn Broodlings 150 energy.
All build 85 community fixes are retained.

Application version: 1.1, versionCode: 86. Simulation/nearby compatibility: 87;
room protocol compatibility: 63. Multiplayer peers must use this build together.
The replay compatibility guard rejects replays recorded under the prior
simulation identifier. Save-state schema remains 4.

Original behavior references:

- [Blizzard Queen guide](https://classic.battle.net/scc/zerg/units/queen.shtml)
- [Blizzard Broodling guide](https://classic.battle.net/scc/zerg/units/broodling.shtml)
- [Original status display reconstruction](https://github.com/BoomerangAide/GPTP/blob/master/GPTP/hooks/interface/status_display/advanced/status_base_text.cpp):
  timer divided by ten, maxima 180/135.
- [Original spell impact reconstruction](https://github.com/BoomerangAide/GPTP/blob/master/GPTP/hooks/weapons/wpnspellhit.cpp):
  Broodling removal timer 1,800.
- [Original unit update reconstruction](https://github.com/BoomerangAide/GPTP/blob/master/GPTP/hooks/update_unit_state.cpp):
  removal timer decremented once per simulation update.

The older strategy pages give inconsistent real-time durations; the original
timer and game speed determine actual duration. No extra lifetime was added.
