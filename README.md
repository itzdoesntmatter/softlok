# Overplay

Per-app daily screen-time limits with a long override code and a deliberate recovery delay.

[Source](https://github.com/itzdoesntmatter/overplay) · [Issues](https://github.com/itzdoesntmatter/overplay/issues) · [Threat model](docs/THREAT-MODEL.md)

Overplay is a local-only Android app. Pick the apps that eat your day, give each one a daily allowance, and set a long random code you will not remember. When the allowance runs out, a full-screen block appears. Extra time costs the code, so the easy path is to close the app.

> **Status:** early development. Core flows pass unit tests, lint, and a Compose UI test on an Android 16 emulator. Physical-device testing across OEMs is still needed.

## Features

- Per-app daily limits, reset at local midnight
- Full-screen block when an app reaches its allowance
- App-generated 24-digit override code, stored only as a salted PBKDF2 hash
- Extra time in 5, 15, 30, or 60 minute steps
- 48-hour delayed code recovery if you forget it
- No account, backend, analytics, ads, or network permission

## Permissions

Overplay needs one special Android permission and explains it in the app before use:

- **Accessibility** detects the foreground package and draws the block overlay.

The accessibility service is configured with `canRetrieveWindowContent="false"`. It reads package names only, never screen content, text, or keystrokes. Android does not let a normal app enforce limits without this capability.

## Limitations

An ordinary Android app cannot stop its owner from:

- uninstalling it
- clearing its app data
- revoking accessibility
- force-stopping it
- changing the device clock, which affects recovery timing

Overplay is friction, not an unbreakable lock. See [docs/THREAT-MODEL.md](docs/THREAT-MODEL.md) for the full list.

Accessibility-based blockers also face Google Play policy requirements. Initial releases target GitHub and F-Droid.

On Android 13 and newer, a sideloaded build may need **Allow restricted settings** in the app's system settings before the accessibility service can be enabled.

## Build

Requirements:

- JDK 17 or newer
- Android SDK with platform **37.2** and build-tools **36.0.0**
- `ANDROID_HOME` or `local.properties` pointing at the SDK

```sh
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
./gradlew :app:lintDebug
```

The debug APK is written to `app/build/outputs/apk/debug/`. Release builds are minified with R8 and need a private signing key that is not committed.

## Project layout

```text
app/src/main/java/app/overplay/
├── data/       Room rules and usage, DataStore security state
├── domain/     code hashing, limits, time formatting
├── service/    accessibility monitor and block overlay
└── ui/         Compose screens and view model
```

## License

[GNU Affero General Public License v3.0 or later](LICENSE).
