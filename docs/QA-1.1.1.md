# StarDroid 1.1.1 verification

Public release of the cumulative build 84–87 fixes, with Android versionCode 88
and versionName 1.1.1. Build 87's game code, native libraries, save schema and
multiplayer/replay compatibility identifiers are unchanged. Version metadata,
release documentation and the matching compiled Android resource cache are
updated for publication.

Detailed functional results and limitations:

- [Community and AI checks](QA-BUILD85.md)
- [Queen and lifetime checks](QA-BUILD86.md)
- [Shared graphics smoothing checks](QA-BUILD87.md)

Release checks verify the package/version, current DEX and native libraries,
the existing signing certificate, APK signatures, ZIP/library alignment and
the source archive. Android resources are freshly compiled using SDK 35, with
input hashes matched to the local source before packaging.

No physical Android testing or complete campaign playthrough is claimed.
Infested Terran double supply remains unreproduced. An actual Spanish game
installation has not been tested.

See `validation/release111.json` and the release's `SHA256SUMS.txt` for the
published package checks and downloadable file hashes.
