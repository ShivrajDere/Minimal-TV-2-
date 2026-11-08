# Minimal TV Launcher (Google TV / Android TV)

Clean, minimal home screen for a Sony Bravia X75K (Google TV) or any Android TV 8.0+ device.
Free, no ads, no accounts, no analytics, no third-party libraries.

## What's inside
- **Home**: big clock, date, *Photos* + every app installed on the TV. Top-right icons: Wi-Fi status (OK = Wi-Fi settings), Look, Settings.
- **Move apps**: hold OK on an app > *Move*, then use Left/Right and press OK to drop. Order is saved. (Same menu has App info / uninstall and Reset order.)
- **Animated wallpapers**: Aurora, Bokeh, and Photos (slideshow with slow zoom + crossfade).
- **5 themes**: Midnight, Forest, Sunset, Mono, Ocean (top-right *Look* icon).
- **Own volume animation**: while any launcher screen is open, volume changes show a small animated pill (bounce-in, filling bar, counting number, pulsing speaker waves).
- **Wireless photo upload**: *Photos* > *Upload* (top right). Open the shown address on your phone (same Wi-Fi), type the PIN, pick photos. They appear in *Photos*.
- **Low data / low battery**: no internet calls at all. The upload page is a local server that only runs while the Upload screen is open. Wallpaper animation pauses when the launcher is not on screen.

## Get the APK (free, ~5 minutes, no Android Studio needed)
1. Create a free GitHub account, make a new **private** repository, and upload this whole folder (drag and drop in the browser works).
2. Open the **Actions** tab > **Build APK** > **Run workflow**.
3. When it finishes (about 3-4 minutes), open the run and download **MinimalTV-apk**. Unzip it: inside is `app-release.apk`.

Alternative: open this folder in Android Studio > *Build > Build APK(s)*.

## Install on the Sony TV
1. On the TV install **Downloader** (Play Store), and enable *Settings > Device Preferences > Security & restrictions > Unknown sources* for Downloader.
2. Put `app-release.apk` on any link/cloud drive and open that link in Downloader, or copy it to a USB stick and use a file manager app.
3. Install. "Minimal TV" now appears in your apps.

## Making it the home screen (optional)
Google TV does not offer a "default launcher" picker. Your normal Google TV home keeps working, and Minimal TV simply opens from the apps list. To make the Home button open it, use ADB from a PC or phone on the same Wi-Fi (Settings > Device Preferences > About > tap *Build* 7 times > Developer options > enable *Network debugging*):

```
adb connect <TV-IP>:5555
adb shell cmd package set-home-activity com.minimal.tvlauncher/.MainActivity
```
To go back: `adb shell cmd package set-home-activity com.google.android.apps.tv.launcherx/.home.HomeActivity` (or reset via Settings > Apps > Minimal TV > Clear defaults).

## Limits to know
- Android does not let normal apps hide the system volume bar on other apps; this launcher replaces it only on its own screens. If your remote sends volume over IR straight to the TV (not through Android), the TV's own bar can still appear.
- Volume control only changes the TV's volume if the TV exposes it to Android (true for the built-in speakers; with an HDMI-ARC soundbar it depends on CEC).
- Photos are stored privately inside the app (nothing is added to other apps' galleries). Uninstalling removes them.
