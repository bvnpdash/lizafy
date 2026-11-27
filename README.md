# Clackwell
**by Zlitch**

A native Kotlin Android keyboard (`InputMethodService`) styled as a mechanical typewriter.
No third-party libraries, no internet permission.

## Install the debug APK
If you installed an earlier "Typewriter Keyboard" test build, uninstall it: Clackwell has a new app ID.

1. Copy `clackwell-debug.apk` to the phone, open it, and allow installing from that source.
2. Open **Clackwell** → *1. Turn on* → enable it.
   Android warns that any third-party keyboard could collect what you type. This is its standard warning.
3. *2. Choose it as your keyboard*, then type in the test box or any app.

Requires Android 6.0 (API 23) or newer. Key sounds are silent when the phone is on silent or vibrate.

## Build on GitHub
1. Create an empty repository and upload **the contents** of this folder, including the hidden `.github` folder.
2. Every push runs **Build APK** (also startable from the Actions tab with *Run workflow*).
3. Open the finished run and download `clackwell-debug-apk` under *Artifacts* (sign-in needed).
   It arrives as a zip containing `app-debug.apk`.

Each GitHub run signs with a new debug key, so uninstall the previous build before installing the next.

## Build
**Android Studio:** open this folder, let it sync, then Build → Build APK(s).

**Command line** (JDK 17+, Android SDK with platform 35; set `ANDROID_HOME`):

    ./gradlew assembleDebug        # app/build/outputs/apk/debug/app-debug.apk

For a release build, create your own signing key and use `./gradlew assembleRelease`.

**Without the Google SDK** (Ubuntu 24.04, how the supplied APK was built):

    sudo apt-get install aapt apksigner zipalign dalvik-exchange android-sdk-platform-23 kotlin default-jdk zip
    ./build-offline.sh             # build/clackwell-debug.apk

APKs from different build routes are signed with different keys: uninstall one before installing another.

**Logic tests:** `tools/tests/run.sh`

## Code map (`app/src/main/java/com/zlitch/clackwell`)
| File | Purpose |
|---|---|
| ClackwellIME | Input connection, Enter actions, delete, cursor, language, panels |
| KeyboardView | Multi-touch, rollover, long press, cursor drag, layout, animation loop |
| KeyArtist, Icons, KeyboardTheme | Mechanism, chrome keycaps, shadows, five themes |
| TypingLogic | Shift/Caps, deletion, Enter rules, key spring, sound variation |
| SoundEngine, Haptics | SoundPool on an audio thread; vibration |
| Layouts, ToolbarView, Panels, SettingsActivity, Prefs | Layouts, toolbar, emoji and clipboard, settings |

## Sounds
All samples are synthesised by `tools/generate_sounds.py` (needs NumPy); they are not recordings.
To use real recordings, replace files in `app/src/main/res/raw` keeping the names
`<style>_<event>_<take>.wav`. Keep them short, with no silence at the start. Use only audio you have rights to.

Key sounds don't request audio focus, so music keeps playing. They are muted in silent/vibrate mode and calls.

## Using it
- Shift: tap for one capital, double-tap for Caps Lock.
- Long-press a top-row letter for its number; long-press other vowels and some consonants for accents.
- Hold or swipe the space bar, then drag, to move the cursor.
- Hold backspace: speeds up, then deletes whole words.
- The globe sits in the strip under the keys, opposite the full stop (long-press it for more punctuation).
- The default "White and silver" theme follows the design reference.
- Globe: cycles the layouts ticked in settings; with only one, switches keyboard. Hold for the picker.
- Toolbar: change case of selected text, clipboard, emoji, theme, settings, hide.

## Suggestions, voice and feel
- Suggestions: offline completion and one-edit spelling fixes, English/French/German/Spanish. Tap to apply;
  nothing is replaced automatically, and nothing is learned. Off in password, email and web-address fields.
  The grid button on the left opens the tools. Word list licence: `app/src/main/assets/dict/NOTICE.txt`.
- Microphone: switches to an installed voice-typing keyboard (for example Google voice typing). This app
  records no audio; that app may use the internet.
- Each keystroke throws a type bar; space, backspace and Enter move the mechanism. On phones with
  vibration-strength control, each key gives a two-stage tick-and-strike pulse.

## Credits
- Word lists: derived from wordfreq by Robyn Speer, CC BY-SA 4.0 (`app/src/main/assets/dict/NOTICE.txt`).
- Lettering: Noto Serif, SIL Open Font License 1.1 (`app/src/main/assets/fonts/NOTICE.txt`).

## Privacy
Only permission: `VIBRATE`. Typed text is never stored or sent. Text around the cursor is read for
delete, capitals, double-space, case change and cursor control. The clipboard is read only when its
panel is opened; entries stay in memory. Settings and recent emoji are saved privately; backup is off.

## Testing status
Done: compiles (Kotlin 1.3 offline route and Kotlin 2.0.21 compiler); APK signature and alignment verify;
logic tests pass; touch handling and drawing were exercised on a desktop stand-in for Android's classes.

Not done: **no run on a real phone or emulator**, so IME behaviour in real apps, audio latency, haptics,
frame rate, memory and battery are unmeasured. The Gradle build has not been run.

Device checklist: enable and switch to the keyboard; type fast with two thumbs in WhatsApp, Chrome and a
notes app; try search/send/multiline Enter; delete emoji and a selection; password, number and email
fields; rotate; change theme, size and sound style while the keyboard is open; silent mode; music playing.

## Not included
Autocorrect, next-word prediction and swipe typing; non-Latin layouts (only English, French, German, Spanish);
per-key TalkBack support; emoji search and skin-tone picker.
