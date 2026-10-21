# Delta for Dictation

## ADDED Requirements

### Requirement: Dictation while the screen is locked
When the screen is locked (power button or screen timeout) during dictation, the keyboard SHALL keep dictating.
While dictating, the app SHALL run a foreground service of type `microphone` with a notification that has a Stop action.
Text recognized while the screen is locked SHALL NOT be typed into any field; it SHALL be held and inserted at the
cursor when input returns to the same app after unlock, and dictation SHALL go on there. If input returns to another
app, dictation SHALL stop and the held text SHALL be copied to the clipboard.

#### Scenario: Lock with the power button
- **GIVEN** the user dictates into a note
- **WHEN** the user presses the power button and keeps talking, then unlocks the phone
- **THEN** the phrases said while locked are in the note and dictation continues

#### Scenario: Stop from the notification
- **GIVEN** dictation runs with the screen locked
- **WHEN** the user taps Stop in the notification
- **THEN** dictation stops and the microphone is released; the held text is inserted after unlock

#### Scenario: Another app after unlock
- **GIVEN** text was recognized while the screen was locked
- **WHEN** after unlock the user opens a field in another app
- **THEN** dictation stops and the held text is in the clipboard

## MODIFIED Requirements

### Requirement: Continuous dictation
The keyboard SHALL request a segmented session (Android 13+). If the engine ends the session at a pause anyway, the
keyboard SHALL start a new session after every final result or silence, until the user taps Stop, says "stop" or hides
the keyboard (a screen lock does not count as hiding). The restart after a final result SHALL wait briefly so that a
late second result for the same phrase is still inserted. Auto-start when the keyboard opens SHALL fire after a 250 ms
debounce.

#### Scenario: Next sentence after a pause
- **WHEN** the user pauses and keeps talking
- **THEN** a new session starts and the next sentence is recognized

#### Scenario: Keyboard hidden
- **WHEN** the keyboard is hidden during dictation while the screen is on
- **THEN** the microphone is released and the composing text is kept

#### Scenario: Late second result
- **GIVEN** the engine delivered a final result for a phrase
- **WHEN** it delivers a second result for the same phrase shortly after
- **THEN** the second result is inserted too, and only then a new session starts
