# StarDroid 1.9.11

- Fixed protected EUD maps such as Poker Defense being rejected in Nearby Multiplayer with “Mission archive has invalid bounds”.
- Keeps the original map unchanged; no map editing or repacking is needed.
- Retains map-size limits and checks against actual file bounds.

The tested Poker Defense maps use protected MPQ layouts that the game engine can read, but the Java map-transfer check rejected. This update corrects that check. EUD compatibility remains partial.

Install the APK over your current app without uninstalling, and use the same release on every multiplayer device. **Valid game data imported since 1.9 remains usable; no reimport is needed.** All normal-build fixes through 1.9.10 remain included.

See [validation](../VALIDATION.md) for tested scope and device-testing limits.
