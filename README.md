# Lift Log for Android

Workout logger with a built-in 4-day Push / Pull / Legs / Upper plan and your own plans, packaged as an
Android app. The app shows `web/lift-log.html` (the same page as the Claude artifact) in a WebView and
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

On the watch, also:
- a tile (swipe from the watch face, add "Lift Log"): today's workout with a Start button, or the running
  workout with Continue;
- a complication for watch faces that accept them ("Lift Log rest timer"): the rest countdown while
  resting, the workout clock during a workout, otherwise today's day. The watch face draws the countdown.

Install once with ADB over Wi-Fi: on the watch enable Developer options (Settings > About watch >
Software information > tap Software version 5 times), then turn on ADB debugging and Wireless
debugging. From a computer with Android platform-tools:
`adb pair IP:PAIRPORT` (code from the watch), `adb connect IP:PORT`, `adb install LiftLog-Watch.apk`.

## Plans and exercise library
- Workout > Plans & exercises: switch plans, copy the built-in plan to edit it, or build a new one
  (1 to 7 days, up to 20 exercises a day, with sets, rep range and rest for each exercise).
  Changes save as you go. The watch follows the plan in use.
- The built-in plan follows the weekday; your own plans go round in order (the day after the last one
  you trained).
- Exercise library: about 300 exercises, searchable and filterable by muscle and equipment. Each has
  start and finish drawings (animated on the workout cards), step-by-step instructions and a link
  that looks the exercise up on JEFIT.
- Records, weight suggestions and progress belong to each exercise, so they carry over between plans.
  Deleting a plan you trained with only hides it; its sessions stay in History.

## Progress
- Muscles: a front and back body map coloured by sets this week against the plan, or by the plan's weekly
  sets (10–20 hard sets a week is the usual growth range). Tapping a muscle lists the plan's exercises for
  it and, when it gets under 10 sets, suggestions from the library.
- Strength and body weight: estimated 1RM per kg of body weight for the main lifts over the last 8 weeks,
  also as a chart metric and next to each record.
- Monthly report: sessions, records, lifts, body and diet for a month, shareable as an image.

## Diet
Body > Diet keeps one line per day: calories, protein, carbs and fat. Food logging stays in Macros (or
any other app); Lift Log only needs the day's totals, from:
- a screenshot of the day's summary in Macros, read on the phone (ML Kit) and shown for review;
- Health Connect nutrition (apps that write it there, such as Macros Premium, MyFitnessPal, Samsung Health);
- typing the four numbers.

Combined with the scale weight and the training log, it shows:
- maintenance calories over the last 4 weeks (average intake adjusted by the weight trend, ~7,700 kcal
  per kg), once there are 10+ logged days and 4+ weigh-ins over 10+ days;
- a daily calorie target for the goal (lose fat, maintain, build muscle) at the chosen pace;
- a protein target from body weight (1.6–2.2 g/kg) and how many days it was reached;
- calories on training vs rest days, and records per session after eating at maintenance vs in a deficit;
- diet in the weekly overview, and deload alerts that suggest a diet break first when lifts drop during
  a big deficit.

## Body data
The Body tab tracks weight, body fat and tape measurements. Data comes in three ways:
- typed in by hand;
- a Xiaomi Home report image (shared from the app as a picture): read on the phone with ML Kit text
  recognition, then shown for review before saving (weight, body fat, muscle, skeletal muscle, water,
  visceral fat, BMR, bone mass, protein);
- Health Connect (Android app only): weight and body fat written by Samsung Health, Zepp Life and others;
- CSV import (date, weight, optional body fat). Xiaomi Home does not share scale data with other Android
  apps, so export it to CSV first (for example with SmartScaleConnect) and import the file.

## Phone features
- Screen stays on while a workout is running.
- Vibration when rest is over (plus a beep if sound is on).
- History > Export CSV opens Android's share sheet (Drive, email, WhatsApp...).

## Your data
- The app keeps everything in its own files (`liftlog-data.json`), written atomically on each save.
  Installing a new version over the app keeps it.
- A copy is written automatically, a few seconds after each change, to
  `Download/LiftLog/LiftLog-backup.json` (or a place you choose, such as Google Drive). It survives
  uninstalling the app. After a reinstall, the Workout tab offers "Restore from backup file".
- History > Your data shows when the last backup was made and lets you restore, back up now or move
  the backup. The web version can export and import the same file.

## Changing the page
Edit `web/lift-log.html`, then `python3 tools/build_page.py` regenerates
`app/src/main/assets/index.html` and copies `web/exlib.js` and the drawings in `web/ex/` next to it
(the workflow also runs it).

The exercise library (`web/exlib.js`, `web/ex/*.webp`) is generated by `tools/build_exlib.py` from
the Everkinetic data set (see the script for the steps).

## Credits
Exercise drawings and instructions: [Everkinetic](https://github.com/everkinetic/data) by Greg Priday,
licensed [CC BY-SA 4.0](https://creativecommons.org/licenses/by-sa/4.0/). The drawings in `web/ex/` are
adapted (cropped, recoloured to light lines, start and finish positions placed side by side) and are
shared under the same licence. JEFIT is only linked to (a web search per exercise); none of its pictures
or data are included.

Font: Poppins, SIL Open Font License (`app/src/main/assets/OFL-Poppins.txt`).

## Building locally
Android Studio, or JDK 17 + Android SDK 34 + Gradle 8.7+: `gradle assembleRelease`.

`app/liftlog.keystore` is a personal sideload signing key kept in the repo so every build can
update the installed app. Keep the repository private.
