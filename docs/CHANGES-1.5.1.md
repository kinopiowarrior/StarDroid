# StarDroid 1.5.1

Version **1.5.1**, Android build **121**, fixes drag selection across the game interface.

- A selection box started on the battlefield stays active when the pointer moves over the HUD, minimap, or other in-game controls.
- Releasing the drag over the UI completes the box selection. Units inside the rectangle can be selected even when a UI panel covers them.
- The same behavior applies to touch, mouse, and controller selection drags.
- Taps and clicks that start on the UI remain UI actions. They do not select a unit underneath the panel or start a battlefield selection drag.

This is a targeted update to 1.5. The application identity and existing simulation, replay, multiplayer, and save-format versions are unchanged.

Install `StarDroid-1.5.1.apk` over the existing app to retain imported data, settings, and saves. The release uses the same signing identity as 1.5.

Validation passed **1,368 input-routing checks** using production Java methods with synthetic Android/HUD adapters, plus **76 native selection-predicate checks** using synthetic unit and visibility state. Coverage includes three viewport widths, four zoom levels, HUD-origin taps, release over UI, final lift coordinates, cancellation, and fog/cloak restrictions. The new harness also reproduces the original 1.5 failure.

Both native architectures were rebuilt. APK version, signing identity, ZIP integrity, and 16 KiB alignment were verified. Validation details are recorded in `validation/selection121.json`, `validation/native-build121.json`, and `validation/android-build1.5.1.json` in the matching source ZIP. These checks do not constitute a physical Android device test or a full game-data/rendering test.
