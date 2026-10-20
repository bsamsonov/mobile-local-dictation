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
The settings screen SHALL let the user set the recognition language (default `ru-RU`), check whether its on-device
model is installed, and request the model download from the system service.

#### Scenario: Check the model
- **WHEN** the user taps the check button
- **THEN** the screen shows the installed, downloading, downloadable and online-only languages

#### Scenario: Download scheduled
- **WHEN** the user requests a download and the system defers it
- **THEN** the screen says the download is scheduled (it often waits for Wi-Fi or charging)

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
