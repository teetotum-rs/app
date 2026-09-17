# Changelog

All notable changes to the app are listed here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and versions follow
[Semantic Versioning](https://semver.org/).

## [Unreleased]

### Added

- Home, the first page in the menu and the one the app opens on, with a card for each feature.
- About, in the menu and as a page with a card each, gathers help, imprint, privacy, changelog and libraries.
- In the menu the pages sit indented under Home and About, and the arrow beside each folds them away.
- Settings show an icon before each card and setting title.
- Corner marks on the camera frame show how large the code should appear.

### Changed

- The back gesture leads to the page above: About from the pages under it, Home from any other.
- Settings is the last page in the menu, just above Exit.

### Fixed

- In landscape the camera stays square and **Close camera** sits beside it instead of off screen.
- Turning the phone no longer closes the camera.

## [0.2.0] - 2026-09-17

### Added

- An app icon in the look of the knob's favicon.
- Help describes the whole app; the imprint names the author and links the app's and the Knob's
  repositories.
- A menu, opened from the top left: Card over Wi-Fi, help, imprint, privacy, changelog, the libraries the app uses and exit.
- Settings, also opened from the top right, with a theme in the system's or GitHub's colours or in
  the knob's red, light or dark as the phone is set.
- Share files from any app to TeeToTum: they are offered for the folder you open, with the same
  question before replacing.

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
