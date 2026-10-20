# Local Dictation

An Android voice keyboard (IME) that recognizes speech **only on the device** and types the text at the
cursor in any app. No cloud, no network: it uses the on-device recognizer that ships with the phone
(`SpeechRecognizer.createOnDeviceSpeechRecognizer`, Android 12+).

Built and tested on a Samsung Galaxy S23 (Android 16), where the on-device engine is Google's
Android System Intelligence (SODA). Russian is the main use case; other languages work if the
on-device model for them is installed.

## Features

- Dictation into any text field; partial results appear as composing text, final results are committed.
- Continuous dictation: after each pause the recognition session restarts automatically until you press Stop.
- Continuous microphone capture (Android 13+): the app records the mic itself and feeds the audio to the
  engine (`EXTRA_AUDIO_SOURCE`) with a short replay buffer, so words spoken while a new session starts are not lost.
- Recovery from a stalled or busy recognizer (watchdog, recreate + back-off).
- Keys: switch keyboard ⌨, settings ⚙, backspace, `,` `.` `?`, space, enter.
- Punctuation by voice ("comma", "period") is done by the engine itself (formatting, Android 13+).
- Voice commands "new line" / "enter" / "new paragraph" (English and Russian) insert line breaks.
- Russian post-processing (`TextPost`): numbers as digits,
  "stop" at the end of a phrase stops dictation.
- Settings screen: microphone permission, enabling the IME, checking and downloading the on-device
  language model, diagnostics of which recognition service the firmware uses.
- UI in English and Russian (per-app language on Android 13+).

## Limitations

- Depends on the firmware: the device must provide an on-device recognition service and a model for your language.
  Samsung's own engine is not available to third-party apps.
- The engine ends a session on every pause and does not support segmented sessions, so the app restarts sessions;
  continuous capture hides most of the gap, but rare glitches are possible.
- Digit conversion and the "stop" command are implemented for Russian only.

## Build

No Gradle: `build.sh` uses the SDK tools directly (aapt2, javac, d8, apksigner).

Requirements: JDK 17+, Android SDK with `platforms;android-35` and `build-tools;35.0.0`
(`ANDROID_HOME` or `~/Android/Sdk`; override the build-tools version with `BUILD_TOOLS_VERSION`).

```bash
./build.sh test       # TextPost unit tests (plain JDK)
./build.sh            # -> build/local-dictation.apk (signed with a generated debug key)
./build.sh install    # build + adb install -r
```

## Setup on the phone

1. Install the APK and open **Local Dictation**.
2. Grant microphone access.
3. Enable the keyboard ("On-device voice input") in system settings.
4. Check on-device languages; download the model for your language if it is not installed.
5. In any text field switch to "On-device voice input" (on Samsung: Settings → General management →
   Keyboard list and default → "Keyboard button on navigation bar"). The ⌨ key returns to the previous
   keyboard; long press opens the keyboard picker.

## Code

| File | Role |
|---|---|
| `DictationIME.java` | The keyboard: recognizer lifecycle, auto-restart, watchdog, text insertion |
| `AudioFeeder.java` | Continuous mic capture with a ring buffer, fed to the engine through a pipe |
| `MainActivity.java` | Settings, permissions, model check/download, diagnostics |
| `Speech.java` | Preferences and the `RecognizerIntent` |
| `TextPost.java` | Russian post-processing (pure Java, tested in `test/`) |

## Troubleshooting

Logs: `adb logcat -v time LocalDictation:D SodaSpeechRecognizer:I AndroidRuntime:E '*:S'`
(the app logs only events and text lengths, never the recognized text).
If the recognition service hangs: `adb shell am force-stop com.google.android.as`.

## License

MIT
