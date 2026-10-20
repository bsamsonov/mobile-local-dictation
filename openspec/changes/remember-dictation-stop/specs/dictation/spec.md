# Delta for Dictation

## MODIFIED Requirements

### Requirement: Continuous dictation
The keyboard SHALL restart recognition after every pause until the user taps Stop, says "stop" or hides
the keyboard. Auto-start when the keyboard opens SHALL fire after a 250 ms debounce.

#### Scenario: Next sentence after a pause
- **WHEN** the user pauses and keeps talking
- **THEN** a new session starts and the next sentence is recognized

#### Scenario: Keyboard hidden
- **WHEN** the keyboard is hidden during dictation
- **THEN** the microphone is released and the composing text is kept

## ADDED Requirements

### Requirement: Remembered stop
A stop by the user (Stop key or spoken "stop") SHALL be saved, and auto-start SHALL NOT fire while it is saved,
including after a screen lock/unlock and in other fields and apps. Tapping Dictate SHALL clear it. Switching to this
keyboard from another keyboard SHALL clear it, so dictation auto-starts. Stops caused by errors SHALL NOT be saved.

#### Scenario: Lock and unlock after a stop
- **GIVEN** the user stopped dictation with the Stop key
- **WHEN** the user locks and unlocks the phone and the keyboard is shown again
- **THEN** dictation does not start and the key shows "Dictate"

#### Scenario: Back from another keyboard
- **GIVEN** the user stopped dictation and switched to Samsung Keyboard
- **WHEN** the user switches back to this keyboard
- **THEN** dictation starts automatically
