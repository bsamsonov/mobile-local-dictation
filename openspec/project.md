# Project: Local Dictation

## Purpose
An Android voice keyboard (IME) that recognizes speech only on the device and types the text at the cursor
in any app. The primary user dictates notes in Russian on a Galaxy S23 (Android 16, system language en-US),
sometimes with English technical terms.

## Principles (constitution)
These do not change; every change is checked against them.
1. On-device only: audio and text never leave the phone; the app has no `INTERNET` permission.
2. Never lose dictated words silently: inserting a partial result is better than dropping it.
3. Simple build: SDK tools without Gradle; builds from a clean clone in CI.
4. Post-processing is pure Java with unit tests; every new rule comes with a test.
5. Recognized text is never written to logs.
6. UI strings live in resources (en + ru); non-English text in code only in allowlisted functional-data files.
7. Every release is signed with the same key; an APK signed with another key does not install over the current one.

## Technical context
- Java without Gradle: `build.sh` (aapt2, javac, d8, apksigner); minSdk 31, targetSdk 35; CI on GitHub Actions.
- The firmware picks the engine. On the S23 it is Google's Android System Intelligence (SODA), reached through
  `SpeechRecognizer.createOnDeviceSpeechRecognizer`. Samsung's own engine is not available to third-party apps.
- Engine limits: no segmented sessions; a session ends on every pause; often `ERROR_CLIENT` (SODA_ERROR)
  arrives instead of a result; only one session at a time.
- Post-processing: `TextPost`, tested without the Android SDK: `./build.sh test`.

## Offline operation
| Works offline (no network at all) | Needs network once |
|---|---|
| Speech recognition by the installed on-device model | Downloading a language model (done by the system service, not by the app; it may wait for Wi-Fi or charging) |
| Spoken punctuation (engine formatting, Android 13+) | |
| Line-break commands, digits, "stop" (`TextPost`) | |
| All keyboard keys, settings, diagnostics, model status check | |

Languages that the engine supports only online cannot be used: the app never falls back to cloud recognition.

## Success criteria (cross-cutting)
| ID | Metric | How to check |
|---|---|---|
| SC-001 | No network: no `INTERNET` permission | manifest |
| SC-002 | Words lost after a pause: at most 1 per 20 phrases | manual run on a fixed text |
| SC-003 | Sessions that end with a result (not an error): at least 80 % | logcat: `results:` vs `error CLIENT` |
| SC-004 | End of phrase to final text: at most 1 s | logcat |
| SC-005 | Dictation works in airplane mode with the model installed | manual run on the phone |

## Non-goals
Cloud recognition; a full letter keyboard; a bundled engine (Whisper, Vosk); Google Play release (for now).

## Workflow (OpenSpec)
- `specs/<capability>/spec.md` is the source of truth and describes what is implemented now.
  It is not edited directly for new work.
- New work is a change folder `changes/<verb-noun>/`: `proposal.md` (what and why: prioritized user story,
  what changes, what is out of scope, measurable success criteria, open questions marked
  `[NEEDS CLARIFICATION]` that are resolved before tasks start), `design.md` (how, only when non-trivial),
  `tasks.md` (checklist with a verification for each item), and a delta `specs/<capability>/spec.md`
  (`## ADDED|MODIFIED|REMOVED Requirements`).
- Archiving: once a change is implemented and verified on the phone, its deltas are merged into `specs/`
  and the folder moves to `changes/archive/YYYY-MM-DD-<name>/`.
  CLI: `npx @fission-ai/openspec archive <name>`; validation: `npx @fission-ai/openspec validate --all`.
- Every change links to an issue of this repository, except spec-only corrections (no code change).
- Bug fixes that restore behavior the spec already describes go straight to code, without a change folder.
  When the spec itself turns out wrong (the plan did not work on the device), the fix is a change with
  `MODIFIED` deltas and a design.md that records what was tried and why it changed.
- CI runs `openspec validate --all` on every push, next to the unit tests and the APK build.
