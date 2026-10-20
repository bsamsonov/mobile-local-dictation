# Delta for Dictation

## MODIFIED Requirements

### Requirement: Continuous dictation
The keyboard SHALL request a segmented session (Android 13+). If the engine ends the session at a pause anyway, the
keyboard SHALL start a new session after every final result or silence, until the user taps Stop, says "stop" or hides
the keyboard. The restart after a final result SHALL wait briefly so that a late second result for the same phrase is
still inserted. Auto-start when the keyboard opens SHALL fire after a 250 ms debounce.

#### Scenario: Next sentence after a pause
- **WHEN** the user pauses and keeps talking
- **THEN** a new session starts and the next sentence is recognized

#### Scenario: Keyboard hidden
- **WHEN** the keyboard is hidden during dictation
- **THEN** the microphone is released and the composing text is kept

#### Scenario: Late second result
- **GIVEN** the engine delivered a final result for a phrase
- **WHEN** it delivers a second result for the same phrase shortly after
- **THEN** the second result is inserted too, and only then a new session starts
