# Changelog

All notable changes to the app are listed here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and versions follow
[Semantic Versioning](https://semver.org/).

## [Unreleased]

### Changed

- `SHA256SUMS` on each release lists the manual as well as the APK.

## [0.3.0] - 2026-09-18

### Added

- A user manual as a PDF, from unpacking the Knob to updating its firmware, attached to each release as `teetotum-manual-<version>.pdf`.
- Home, the first page in the menu and the one the app opens on, with a card for each feature.
- Status over Bluetooth shows the Knob's firmware, its card, how long it has run, how many Wi-Fi networks it sees and when it read them.
- Help describes how the menu works.
- About, in the menu and as a page with a card each, gathers help, imprint, privacy, changelog and libraries.
- In the menu the pages sit indented under Home and About, and the arrow beside each folds them away.
- Settings show an icon before each card and setting title.
- Corner marks on the camera frame show how large the code should appear.
- A monochrome launcher icon, for phones set to themed icons.
- Plugins over Bluetooth sends a plugin from the catalogue, or a signed `.wasm` file of your own, to the Knob, and shows the percent sent as the Knob does.
- Plugins over Bluetooth lists the plugins on the Knob and deletes one you sent, after asking, while Receive is open on the Knob.
- Sending or deleting a plugin pairs the phone with the Knob first; the phone asks once.
- A Bluetooth connection that does not come about is tried up to three times before the app says so.
- Firmware over Bluetooth sends a signed `.tfw` firmware file to the Knob, which checks it, switches to it and restarts; the page shows the firmware's version before sending, and the percent sent, the rate and the time still to go while it sends.
- Firmware over Bluetooth warns before sending a file the Knob already runs, or an older release than it runs.
- Help says where to download a signed firmware file: each firmware release carries one.
- Help points to the app template, the app's shell shared on its own for other apps.
- Settings choose the start page, Home or the page last open, and whether the plugin catalogue is read from GitHub only on tap, as by default, or as soon as the page opens.

### Changed

- The back gesture leads to the page above: About from the pages under it, Home from any other.
- Settings is the last page in the menu, just above Exit.
- Help, imprint, privacy and changelog show a card with an icon for each section.
- Help lists its cards in the order of the menu.

### Fixed

- In landscape the camera stays square and **Close camera** sits beside it instead of off screen.
- Turning the phone no longer closes the camera.

The pages over Bluetooth need firmware 0.4.0 or later.

## [0.2.0] - 2026-09-17

### Added

- An app icon in the look of the knob's favicon.
- Help describes the whole app; the imprint names the author and links the app's and the Knob's repositories.
- A menu, opened from the top left: Card over Wi-Fi, help, imprint, privacy, changelog, the libraries the app uses and exit.
- Settings, also opened from the top right, with a theme in the system's or GitHub's colours or in the knob's red, light or dark as the phone is set.
- Share files from any app to TeeToTum: they are offered for the folder you open, with the same question before replacing.

### Changed

- The camera opens only when you tap Scan code.
- All buttons look like the action buttons on the libraries page, including those in dialogs.
- Icons take the theme's accent colour, like the selected option in the settings.
- Menu entries have the same rounded corners as the buttons.

### Fixed

- The camera preview no longer covers the hint above it.
- Files from providers without a last-modified column, such as shared media, can be sent.

## [0.1.0] - 2026-09-16

First release, Android only.

### Added

- Join the Knob from the QR code of **Card over Wi-Fi**.
- Browse the card's folders; download files into `Download/TeeToTum`.
- Upload files, with a question before replacing; create folders; delete files and empty folders.

Needs firmware 0.3.3 or later.
