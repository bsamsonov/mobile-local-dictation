# Keyboard Keys Specification

## Purpose
The keys of the voice keyboard: start and stop dictation, edit and punctuate by hand, insert line breaks,
open the settings and return to the regular keyboard. All keys work offline.

## Requirements

### Requirement: Layout
The keyboard SHALL show a status line and two rows of keys.
Row 1: switch keyboard (⌨), Dictate/Stop (wide), backspace (⌫).
Row 2: settings (gear icon), `,`, `.`, `?`, space (wide), enter (↵).

#### Scenario: Keyboard opened
- **WHEN** the user switches to "On-device voice input"
- **THEN** both rows of keys and the status line with the recognition language are shown

### Requirement: Dictate / Stop key
The Dictate key SHALL start continuous dictation; while dictating it SHALL show "Stop" and stop dictation when tapped.

#### Scenario: Stop by key
- **GIVEN** dictation is running
- **WHEN** the user taps Stop
- **THEN** recognition stops, the microphone is released and the key shows "Dictate"

### Requirement: Return to the regular keyboard
The switch key (⌨) SHALL stop dictation and switch to the previously used keyboard; if there is none, it SHALL open the
system keyboard picker. A long press SHALL always open the system keyboard picker.

#### Scenario: Back to the previous keyboard
- **GIVEN** the user came from Samsung Keyboard
- **WHEN** the user taps ⌨
- **THEN** dictation stops and Samsung Keyboard is shown

#### Scenario: Keyboard picker
- **WHEN** the user long-presses ⌨
- **THEN** the system keyboard picker opens

### Requirement: Settings key
The gear key SHALL stop dictation and open the app's settings screen. It SHALL have the accessible label "Settings".

#### Scenario: Open settings
- **WHEN** the user taps the gear
- **THEN** dictation stops and the settings screen opens

### Requirement: Line break key
The enter key (↵) SHALL send `KEYCODE_ENTER` to the focused field, so the field inserts a line break
(or performs its own action, e.g. send, in single-line fields). The same result by voice is described in
text-postprocessing ("new line").

#### Scenario: New line in a note
- **GIVEN** the cursor is in a multi-line note
- **WHEN** the user taps ↵
- **THEN** a line break is inserted at the cursor

### Requirement: Punctuation keys
The `,` `.` `?` keys SHALL finish the composing text and insert the character at the cursor as is, without adding a space.

#### Scenario: Comma by hand
- **GIVEN** the text before the cursor is "Hello"
- **WHEN** the user taps `,`
- **THEN** the text is "Hello,"

### Requirement: Space and backspace
The space key SHALL insert a space at the cursor. The backspace key SHALL send `KEYCODE_DEL`.

#### Scenario: Delete a character
- **WHEN** the user taps ⌫
- **THEN** the character before the cursor is deleted

### Requirement: Status line
The status line SHALL show the current state (idle with the language, connecting, listening, recognizing, stopped)
and errors in plain words, including a missing microphone permission, a missing on-device engine and a missing model.

#### Scenario: No microphone permission
- **GIVEN** microphone access is not granted
- **WHEN** the user taps Dictate
- **THEN** the status line asks to open the app and grant the permission
