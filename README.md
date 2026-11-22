# Lizafy (formerly Doodlify)
Your Vivo. Your Little Illustrated World.

Android Studio project for the Lizafy companion app, with the full illustration pack bundled inside (155 images).

## Build the APK
**Option A, Android Studio (Meerkat 2024.3.2 or newer):**
1. File > Open > select this `lizafy` folder. Let Gradle sync (needs internet).
2. Build > Build APK(s). The file is `app/build/outputs/apk/debug/app-debug.apk`.
3. Copy it to the phone and install (allow "install unknown apps" for your file manager).

**Option B, command line:** `./gradlew assembleDebug` (JDK 17 and the Android SDK installed).

**Option C, no Android Studio:** push this folder to a GitHub repo. The included workflow (`.github/workflows/build.yml`) builds the APK; download it from the Actions run under Artifacts.

## Status
- The code has NOT been compiled or run. The environment it was written in cannot download the Android SDK, so expect to fix a compile error or two on first build.
- Not tested on a Vivo V50.
- Fonts are not bundled (download blocked). Add Nunito, Quicksand, Fredoka, Patrick Hand and Caveat (OFL) to `res/font` to use them.

## What the app does
Home: apply a full look (home and lock wallpaper). Icons: browse, search, save PNG/WebP. Wallpapers: preview and set home, lock or both. Flip Cards: save to Pictures/Lizafy, then pick them in system settings. Moods: live battery mood art and weather art. Device: compatibility report, with live wallpaper check.

It does not change app icons, system UI, Origin Island, AOD or the keyboard.
