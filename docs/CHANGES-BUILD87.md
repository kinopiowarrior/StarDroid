# StarDroid 1.1 build 87

One **Smooth graphics (game + UI)** option now controls bitmap filtering across
the game. Build 86 applied that option only to the battlefield.

- ON: smooth interpolation for terrain and units, main/campaign/custom menus,
  HUD and minimap, command/control-group buttons, wireframes, bitmap text,
  unit and briefing portraits, cinematics, map/replay thumbnails, story/results
  screens and bitmap cursors.
- OFF: nearest-neighbor sampling for the same artwork, preserving sharp pixels.
- Changes take effect immediately, including cached buttons and resized fonts.
- New installs default to ON. Existing saved on/off choices are preserved and
  now apply to every game screen. There is no separate UI smoothing setting.

This controls bitmap sampling. It does not remove detail or shading already in
the imported artwork, or change Android's system dialogs and text rasterizer.
The option label is updated in English, Russian, German, Spanish and French.

All Queen/lifetime and community fixes from builds 85 and 86 are retained.
VersionName remains 1.1; versionCode is 87. Native simulation, replay/nearby
compatibility (87), room protocol (63) and save schema (4) are unchanged from
build 86. The verified native libraries are reused byte-for-byte.

See [verification](QA-BUILD87.md) for coverage and device-testing limits.
