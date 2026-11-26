# Releasing

Release keys are never committed. CI builds the unsigned debug APK only.

## One-time signing setup

Generate a keystore outside version control:

```sh
mkdir -p keystore
keytool -genkeypair -v \
  -keystore keystore/softlok-release.jks \
  -alias softlok \
  -keyalg RSA -keysize 4096 -validity 10000
```

Create `keystore.properties` in the project root:

```properties
storeFile=keystore/softlok-release.jks
storePassword=...
keyAlias=softlok
keyPassword=...
```

`keystore.properties`, `*.jks`, and `*.keystore` are ignored by Git.

**Back up the keystore and its passwords somewhere safe.** Losing them means future releases cannot update installed copies; users would have to uninstall and lose their data.

## Build

```sh
./gradlew :app:assembleRelease
```

The signed APK is `app/build/outputs/apk/release/app-release.apk`.

Verify it:

```sh
$ANDROID_HOME/build-tools/36.0.0/apksigner verify --print-certs \
  app/build/outputs/apk/release/app-release.apk
```

## Publish

```sh
gh release create v0.1.0 \
  app/build/outputs/apk/release/app-release.apk#softlok-0.1.0.apk \
  --title "Softlok v0.1.0" \
  --notes-file release-notes.md \
  --prerelease
```

Keep the `versionCode` increasing for every published build.
