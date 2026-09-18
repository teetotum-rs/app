TeeToTum is the phone app for the TeeToTum Knob. **Home** shows what it can do; each feature has its
section below.

## Menu

The button at the top left opens the menu with every page of the app.

- Tap a page to open it. The menu closes, and next time it marks the page you are on.
- **Home** and **About** have their pages indented under them. The arrow at the end of each folds those pages away or shows them again; the menu keeps that while the app is open.
- **Settings** opens the settings, as the gear at the top right does.
- **Exit**, below the line, closes the app.
- The cross at the top, a tap beside the menu, a swipe to the left or the back gesture close it and leave the page as it was.

## Getting around

- **Home** shows a card for each feature, **About** one for each page about the app; tap a card to open it.
- The back gesture leads to the page above: from a page under **About** to **About**, from any other to **Home**.

## Connect

On the Knob, open **Card over Wi-Fi**. In the app, choose **Card over Wi-Fi** on **Home** or in the menu, tap **Scan code**
and point the camera at the code on the Knob's screen; the phone joins the network the Knob offers and shows the card.
**Close camera** stops scanning. If joining fails, **Scan again** opens the camera straight away.

The Knob needs firmware 0.3.3 or later.

## Card over Wi-Fi

- Tap a folder to open it; **Up** or the back gesture goes to the folder above.
- Tap a file to download it into `Download/TeeToTum` on the phone.
- **Upload files** sends files from the phone into the open folder, asking before a file of the same name is replaced.
- **New folder** makes a folder in the open one.
- Hold a file or an empty folder to delete it.

## From other apps

Share files to TeeToTum from any app. Once the card shows, they are offered for the folder you
open: **Send here** uploads them into it, **Cancel** drops them.

## Status over Bluetooth

Choose **Status over Bluetooth** on **Home** or in the menu. The app asks for Bluetooth, finds the
Knob nearby and shows its firmware, the size of its card, how long it has run and how many Wi-Fi
networks it sees. **Read at** is the phone's date and time of that reading, since the Knob has no clock;
**Read again** fetches everything anew. The Knob's screen can show anything meanwhile.

## Plugins over Bluetooth

Choose **Plugins over Bluetooth** on **Home** or in the menu. The page lists the plugins in the catalogue;
**Your own plugin** at the end takes a signed `.wasm` file from the phone with **Choose file**. On the
Knob, open **Settings > Receive**, then tap **Send to Knob**. The app checks the plugin and shows how
much is sent; the Knob restarts and asks whether to install it.

**On the Knob** at the top lists the plugins the Knob holds, read when the page opens; **Read again**
reads them anew. A plugin you sent has a **Delete** button, a built-in one has none. Open
**Settings > Receive** on the Knob first, then tap **Delete** and confirm; the Knob deletes the plugin
and restarts.

## Knob settings over Bluetooth

Choose **Knob settings over Bluetooth** on **Home** or in the menu. The page reads the Knob's
**Theme**, **Brightness**, **Clicks** and **Orientation** when it opens. A change goes to the Knob at
once: it shows there and stays after a restart, as if you had set it with **OK** in the Knob's
**Settings**. The first change asks to pair the phone with the Knob; accept it. **Read again** reads
the settings anew, for instance after you changed them on the Knob.

## Firmware over Bluetooth

Choose **Firmware over Bluetooth** on **Home** or in the menu, then **Choose file** and pick a signed
firmware file, `.tfw`. The app shows the firmware's name, version and size. On the Knob, open
**Settings > Receive**, then tap **Send to Knob**. Sending takes a few minutes; the screen stays
on meanwhile, and the page shows how much is sent and how long it will still take. The Knob writes the
firmware beside the one it runs, checks the signature, switches and restarts. A transfer that breaks
off, or a file not signed with the project's key, changes nothing.

## About

- **Help** is this page.
- **Imprint** and **Privacy** say who makes the app and what it does with your data.
- **Changelog** lists what changed in each version.
- **Libraries** names the open-source libraries the app is built on, with their licences.

## Settings

**Start page** opens the app on **Home**, or on the page you left, apart from **Settings**. A code
or files shared from another app still open **Card over Wi-Fi**.

**Theme** sets the app's colours: the phone's own, GitHub's, or the Knob's red. Each follows the
phone's light or dark mode.

**Plugin catalogue** reads the catalogue only after you tap **Load catalogue** on **Plugins over
Bluetooth**, or as soon as that page opens.

**Exit** at the end of the menu closes the app.
