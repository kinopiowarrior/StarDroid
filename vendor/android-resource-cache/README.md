This cache contains only compiled StarDroid launcher/theme resources and its
Android manifest, compiled by Android SDK 35 AAPT2 for version 1.1.1 (88).
No StarCraft assets, application code, native libraries or signing material are
included. All six input hashes match the current source tree.

`source-sha256.json` pins every input, the compiler workflow/artifact, the cache
and the generated R class. `scripts/cached_android_resources.py` rejects changed/added/deleted
inputs. It changes only the compiled manifest's integer versionCode; a changed
versionName requires a normal AAPT rebuild.

This is an optional Windows packaging fallback for hosts where AAPT/zipalign
crash while writing ZIP archives. Standard Linux/Gradle builds compile resources
normally. With the fallback, Java and native code still compile from current
source, D8 creates fresh DEX, and the SDK independently verifies ZIP alignment
and the finished APK signature.
