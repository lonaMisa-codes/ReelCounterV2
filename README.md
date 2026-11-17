# Reel Counter

Tiny Android app (no dependencies, ~600 lines). An accessibility service watches Instagram Reels and YouTube Shorts:

- a pill at the top shows reels this run and total today
- every N reels (default 50) a full-screen break appears: your count, a prompt, and a Continue button that unlocks after a delay that grows each time
- the app screen shows today's count, last 7 days and settings

## Build
Push to GitHub, run the "Build APK" workflow, download `ReelCounter-debug` from the run's artifacts. Or locally: `gradle assembleDebug`.

## Setup on the phone
Install the APK, open it, tap "Open Accessibility settings", enable Reel Counter. On Android 13+ you may first need App info > three dots > Allow restricted settings.

## If counting stops working
Instagram/YouTube occasionally rename their internal view IDs. Edit the `KEYS` lists at the top of `ReelService.kt`.
