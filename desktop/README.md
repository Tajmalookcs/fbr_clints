# FBR Clients for Windows

Compose for Desktop app with the same features as the phone (see `docs/DESIGN.md` §8–9).

- Code shared with the phone: `../shared` (logic) and `../shared-ui` (small Compose parts).
- Data: `%APPDATA%\FBR Clients\data.fbrpc` (encrypted; key locked to the Windows user with DPAPI), `settings.json` (PIN hash and sync password locked the same way).
- Build the installer: `./gradlew :desktop:packageMsi` (on Windows; CI does this). Run from code: `./gradlew :desktop:run`.
- Tests: `./gradlew :desktop:test`. With `SCREENSHOTS_DIR` set, `ScreenshotTest` draws the main screens with made-up data.
