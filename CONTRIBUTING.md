# Contributing

Thanks for helping. Keep changes small and explain the user-visible behavior they affect.

## Before you start

- Open an issue for a behavior change or a new permission. Permissions are the highest-risk part of this app.
- Do not add an SDK that collects data, requires internet access, or ships closed-source binaries.
- Read [docs/THREAT-MODEL.md](docs/THREAT-MODEL.md). Do not describe a bypass as impossible.

## Build and check

```sh
./gradlew :app:testDebugUnitTest
./gradlew :app:lintDebug
./gradlew :app:assembleDebug
```

Changes to time accounting, code verification, recovery, or rule protection need a test.

## Style

- Kotlin official style; keep the existing package layout.
- Compose for normal UI. The block overlay uses plain Views because it runs from an accessibility service.
- No new dependency for a few lines of code.
- Keep strings in `res/values/strings.xml`.
- Comment only the reason the code cannot show.

## Commits and pull requests

- One logical change per pull request.
- Describe how you tested on a physical device, including OEM and Android version.
- Do not commit signing keys, `local.properties`, or personal device data.

By contributing, you agree that your contribution is licensed under AGPL-3.0-or-later.
