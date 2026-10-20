# Settings Specification

## Purpose
The setup, options and diagnostics screen: permissions, enabling the keyboard, recognition language and model.

## Requirements

### Requirement: First-run setup
The settings screen SHALL show the state of the microphone permission and of enabling and selecting the keyboard,
with a button to fix each one.

#### Scenario: No permission
- **WHEN** microphone access is not granted
- **THEN** the screen shows it and offers a button to grant the permission

### Requirement: Recognition language and model
The settings screen SHALL let the user pick the recognition language from a drop-down list of the languages the
on-device engine supports: installed first, then downloading, then downloadable, each with a state marker; the state
of the selected language SHALL be shown under the list. The screen SHALL let the user check whether the model is
installed and request its download. If the engine does not report its languages, a text field for a BCP 47 tag SHALL
be shown instead. The default language SHALL be `en-US` until the user picks one. Only the user's pick SHALL be saved,
and it SHALL be kept across app updates and reinstalls over the current build.

#### Scenario: Check the model
- **WHEN** the user taps the check button
- **THEN** the screen shows the installed, downloading, downloadable and online-only languages

#### Scenario: Download scheduled
- **WHEN** the user requests a download and the system defers it
- **THEN** the screen says the download is scheduled (it often waits for Wi-Fi or charging)

#### Scenario: Pick a language
- **WHEN** the user opens the language list
- **THEN** every on-device language is shown with its model state, installed ones first

#### Scenario: Default language
- **GIVEN** the user has never picked a language
- **THEN** `en-US` is used

#### Scenario: Picked language kept
- **GIVEN** the user picked `ru-RU`
- **WHEN** the app is rebuilt and installed over the current build
- **THEN** `ru-RU` stays selected

### Requirement: Options
The settings screen SHALL contain switches for: auto-start, engine formatting (punctuation), voice commands, digits,
continuous microphone capture.

#### Scenario: Switch saved
- **WHEN** the user changes a switch
- **THEN** the next dictation session uses the new value

### Requirement: UI language
The UI SHALL follow the system language (English, Russian) and SHALL support the per-app language on Android 13+.

#### Scenario: English system
- **GIVEN** the system language is en-US and no app language is set
- **THEN** the UI is in English

### Requirement: Diagnostics
The settings screen SHALL show the installed recognition services and which of them is the on-device one.

#### Scenario: Diagnostics
- **WHEN** the user opens the diagnostics
- **THEN** the on-device service from the firmware config and all installed RecognitionService components are listed

### Requirement: Try-it field
The settings screen SHALL have a multi-line text field with a short instruction, so the user can switch to the voice
keyboard and test dictation without leaving the app.

#### Scenario: Test dictation in the app
- **WHEN** the user taps the "Try it" field and switches to "On-device voice input"
- **THEN** the voice keyboard opens and dictated text appears in that field
