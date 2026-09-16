# TeeToTum app

A phone app for the [TeeToTum](https://github.com/teetotum-rs/firmware) Knob: it reads the card
in the Knob over Wi-Fi.

On the Knob, open **Card over Wi-Fi**. The app scans the QR code on the screen, joins the
network the Knob offers and shows the card: browse folders, download and upload files, create
folders, delete.

The app needs firmware 0.3.3 or later, where a folder answers as JSON.

## Status

Early. Only Android is built and tested; the code is laid out as Compose Multiplatform, with
everything that is not Android-specific in `shared`.

## Building

JDK 17 or later and the Android SDK (API 37). Point `local.properties` at the SDK
(`sdk.dir=...`), then:

```
./gradlew :shared:testAndroidHostTest   # unit tests
./gradlew :androidApp:installDebug      # build and install on a connected phone
```

Android 10 (API 29) or later.

## Licence

Licensed under either of [Apache License, Version 2.0](LICENSE-APACHE) or
[MIT license](LICENSE-MIT) at your option.
