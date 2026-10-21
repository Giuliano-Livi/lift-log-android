# Lift Log for Android

Workout logger for a 4-day Push / Pull / Legs / Upper plan, packaged as an Android app.
The app shows `web/lift-log.html` (the same page as the Claude artifact) in a WebView and
works fully offline. Logs are stored on the phone.

## Install on your phone
1. Open the latest release of this repository on your phone and download `LiftLog.apk`.
2. Open the downloaded file. If Android asks, allow your browser to install unknown apps.
3. Later builds install over the old one and keep your logs.

Every push to `main` builds a new APK automatically (Actions > Build APK).

## Watch app (Wear OS, e.g. Galaxy Watch 7)
`LiftLog-Watch.apk` logs sets, runs the workout clock and rest timer, and syncs with the phone app
(same package name and signing key, Wear OS Data Layer).

- Watch to phone: each saved set is queued and applied the next time the phone app is open.
- Phone to watch: previous sets, rest times and today's sessions are sent whenever the phone app saves.

Install once with ADB over Wi-Fi: on the watch enable Developer options (Settings > About watch >
Software information > tap Software version 5 times), then turn on ADB debugging and Wireless
debugging. From a computer with Android platform-tools:
`adb pair IP:PAIRPORT` (code from the watch), `adb connect IP:PORT`, `adb install LiftLog-Watch.apk`.

## Body data
The Body tab tracks weight, body fat and tape measurements. Data comes in three ways:
- typed in by hand;
- Health Connect (Android app only): weight and body fat written by Samsung Health, Zepp Life and others;
- CSV import (date, weight, optional body fat). Xiaomi Home does not share scale data with other Android
  apps, so export it to CSV first (for example with SmartScaleConnect) and import the file.

## Phone features
- Screen stays on while a workout is running.
- Vibration when rest is over (plus a beep if sound is on).
- History > Export CSV opens Android's share sheet (Drive, email, WhatsApp...).

Uninstalling the app deletes its logs, so export a CSV first.

## Changing the page
Edit `web/lift-log.html`, then `python3 tools/build_page.py` regenerates
`app/src/main/assets/index.html` (the workflow also runs it).

## Building locally
Android Studio, or JDK 17 + Android SDK 34 + Gradle 8.7+: `gradle assembleRelease`.

`app/liftlog.keystore` is a personal sideload signing key kept in the repo so every build can
update the installed app. Keep the repository private.
