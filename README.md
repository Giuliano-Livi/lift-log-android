# Lift Log for Android

Workout logger for a 4-day Push / Pull / Legs / Upper plan, packaged as an Android app.
The app shows `web/lift-log.html` (the same page as the Claude artifact) in a WebView and
works fully offline. Logs are stored on the phone.

## Install on your phone
1. Open the latest release of this repository on your phone and download `LiftLog.apk`.
2. Open the downloaded file. If Android asks, allow your browser to install unknown apps.
3. Later builds install over the old one and keep your logs.

Every push to `main` builds a new APK automatically (Actions > Build APK).

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
