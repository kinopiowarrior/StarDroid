# Build 87 verification

Baseline: build 86 source commit `3fb0d6b` in this checkout.

- Audited all 54 Java bitmap drawing sites. Every site uses the shared graphics
  paint or copies its sampling flags while retaining its own blend effects.
- 41 targeted graphics checks execute production artwork, menu, font, movie and
  portrait code with an AWT Canvas stand-in. They verify changed menu pixels,
  reversible toggling, menu/HUD button caches, resized font caches, unchanged
  text layout, unit/static/briefing portrait routes, and retained alpha/tint/mask effects.
- 9,314 production Java routing checks passed, including default ON, saved
  ON/OFF values, the single settings toggle, both persistence directions,
  mouse, keyboard, touch and gamepad regressions.
- Menu recovery checks passed: 3,627 font checks, 41 asset checks and 13 fallback
  button/action checks, plus interface metadata validation.
- Reviewed the generated sharp and smooth main-menu samples at 1.5x scale.
  They verify host rendering behavior, not Android GPU output.
- Android lint 8.7.3: zero errors, eleven warnings on current Java/resources
  and compiled classes.
- Fixed the Windows packager's ZIP alignment metadata. All 260 checks across
  65 payload sizes passed; SDK zipalign verifies the actual APK both before and
  after signing. This catches layout changes introduced by the signer.

The APK uses freshly compiled Java/DEX and verified unchanged build 86 native
libraries for ARM64 and x86-64. The six Android resource inputs still match the
resource cache; the current language asset is packaged from source. See
`vendor/android-resource-cache/README.md` for the Windows packaging method.
`validation/release87.json` records final signing, alignment, compilation,
static-analysis and artifact checks.

No physical Android device is available. Device playtesting and Android-specific
visual output remain unverified. Native gameplay tests were not repeated because
this change affects only Java presentation and its preference; build 86's native
source and binaries are unchanged.

Reproduce the affected host checks with JDK 17 on PATH:

```
python tests/run_desktop_input11.py
python tests/run_menu_recovery.py CORE_MPQ_DIRECTORY
python tests/run_graphics87.py
python tests/test_cached_resources87.py
```

On Windows use UTF-8 Python mode and MinGW g++ for the menu asset extractor.
The graphics harness uses the fixtures prepared by the menu recovery test.
