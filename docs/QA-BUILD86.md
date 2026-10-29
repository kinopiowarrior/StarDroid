# Build 86 verification

Baseline: the verified build 85 source archive, original source commit
`e2208ce7f85a6ab9d81b5b0e5f5d66f59f2a1814`. Archive SHA-256:
`350960b615b8b6c76a24d1ee720fbfd955c90ab1577d3006fbd7866fb4a5ebcb`.

Completed checks:

- 19,831 native Queen/lifetime checks using the user's original core archives.
  These exercise command cards, rejection of direct Parasite research commands,
  paid Spawn Broodlings research through completion, scenario permissions,
  energy costs, both summoned Broodlings' 1,800-frame countdown and exact expiry,
  save/load at half life, 1,350-frame hallucinations and real spellcaster energy.
- 9,311 production Java input/settings checks, including smoothing on for an
  empty preference store, preserving existing on/off choices, and toggling the
  battlefield paint in both directions.
- 118,099 replay checks across three scenarios, including seeking, checkpoint
  eviction/restoration, AI state and intermediate rendering.
- 1,430 nearby protocol assertions.
- 3,627 font checks, 41 menu asset checks and 13 fallback button/action checks.
  These use original menu data and an AWT/Android Canvas stand-in.
- Detector and Dunes nearest-target regression suites passed.
- Standalone Android lint 8.7.3 analyzed the current Java sources, resources and
  compiled classes: zero errors and eleven warnings.
- Independent packaging checks verified that the cached manifest changed only
  its versionCode, resource IDs remained consistent, and stored ZIP entries
  meet their alignment requirements before and after signing.
- Clean reconstruction from the pinned upstream source and the full patch stack
  reproduced all 39 compiled native source files byte-for-byte.
- Both ARM64 and x86-64 native libraries rebuilt with NDK r27d. Current Java
  compiled against Android SDK 35 and passed D8.

Packaging:

The Windows AAPT and zipalign writer processes crashed. The release therefore
uses the exact-match Android resource cache documented in
`vendor/android-resource-cache/README.md`. All six current resource inputs
match the verified prior release. Fresh Java/DEX and native libraries are
packaged locally; only the compiled manifest versionCode is updated in the
cached resources. SDK zipalign independently checks the final ZIP, and
apksigner verifies its v2/v3 signatures. No remote CI workflow was installed.

Physical Android playtesting is not available in this environment. Host checks
verify logic, settings and render routing; they are not a claim of visual or
gameplay testing on an Android device. See `validation/release86.json` for
artifact hashes, alignment, signing and the final static-analysis result.

Reproduce the main native test after preparing sources:

```
python tests/build_native_host.py queen86 --skip-prepare
build-native/host/native-queen86 CORE_MPQ_DIRECTORY
```

On Windows use MinGW g++/gcc and the `.exe` suffix. The host build script selects
the bundled Windows mmap adapter only on Windows; Android retains platform mmap.
Use Python UTF-8 mode and JDK 17 for the Java regression scripts.

For optional Windows release packaging, set `JAVA_HOME`, `ANDROID_HOME`,
`STARDROID_ANDROID_TOOLS` and the signing variables in `app/build.gradle`, then:

```
python scripts/build_native_engine.py arm64-v8a
python scripts/build_native_engine.py x86_64 --skip-prepare
python scripts/package_android_windows.py --resource-cache
```

The ordinary Linux build remains available through `scripts/build.py`.
