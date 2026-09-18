# TeeToTum app

A phone app for the [TeeToTum](https://github.com/teetotum-rs/firmware) Knob: it reads the card
in the Knob over Wi-Fi.

On the Knob, open **Card over Wi-Fi**. The app scans the QR code on the screen, joins the
network the Knob offers and shows the card: browse folders, download and upload files, create
folders, delete. Files shared from another app are offered for the folder you open.

The app needs firmware 0.3.3 or later, where a folder answers as JSON.

## Install

Download `teetotum-app-vX.Y.Z.apk` from the
[releases](https://github.com/teetotum-rs/app/releases) and open it on the phone; Android asks
once to allow installs from the browser or file manager. `SHA256SUMS` next to it lists the
checksum.

## Status

Early. Only Android is built and tested; the code is laid out as Compose Multiplatform, with
everything that is not Android-specific in `shared`.

## Building

JDK 21 or later and the Android SDK (API 37). Point `local.properties` at the SDK
(`sdk.dir=...`), then:

```
./gradlew :shared:testAndroidHostTest   # unit tests
./gradlew :androidApp:installDebug      # build and install on a connected phone
./gradlew :androidApp:assembleRelease   # shrunk with R8, unsigned unless a key is given
./gradlew detekt :androidApp:lintDebug  # linters, as CI runs them; --auto-correct fixes formatting
```

`git config core.hooksPath tools/hooks` runs the linters before every push.

A signed release build reads its key from the environment: `TEETOTUM_APP_KEYSTORE` (a PKCS12
file), `TEETOTUM_APP_KEYSTORE_PASSWORD` and `TEETOTUM_APP_KEY_ALIAS`. Pushing a tag `vX.Y.Z` that
matches `versionName` builds and signs the APK in CI and attaches it to a GitHub release; the key
lives in the `release` environment, which only tags `v*` can use.

Android 10 (API 29) or later.

## Licence

Licensed under either of [Apache License, Version 2.0](LICENSE-APACHE) or
[MIT license](LICENSE-MIT) at your option.
