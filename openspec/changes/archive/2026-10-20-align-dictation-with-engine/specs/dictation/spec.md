# Delta for Dictation

## MODIFIED Requirements

### Requirement: Text at the cursor
The keyboard SHALL show partial results as composing text and commit the final result at the cursor, adding a leading
space when the text before the cursor does not end with whitespace. The first letter SHALL be capitalized at the start
of the field, after `.`, `!`, `?`, `…` or after a line break. Elsewhere, a capital letter that the engine put on the
first word SHALL be lowered, unless the second letter is also uppercase (an acronym).

#### Scenario: Phrase inserted
- **WHEN** the user says a phrase and pauses
- **THEN** the phrase replaces the composing text at the cursor

#### Scenario: Sentence continued after a pause
- **GIVEN** the text before the cursor is "I went to the"
- **WHEN** the engine returns "Store and back"
- **THEN** the text is "I went to the store and back"

#### Scenario: New sentence
- **GIVEN** the text before the cursor is "Done."
- **WHEN** the engine returns "next step"
- **THEN** the text is "Done. Next step"

#### Scenario: Acronym kept
- **GIVEN** the text before the cursor is "we use"
- **WHEN** the engine returns "API keys"
- **THEN** the text is "we use API keys"

### Requirement: Continuous dictation
The keyboard SHALL request a segmented session (Android 13+). If the engine ends the session at a pause anyway, the
keyboard SHALL start a new session after every final result or silence, until the user taps Stop, says "stop" or hides
the keyboard. Auto-start when the keyboard opens SHALL fire after a 250 ms debounce.

#### Scenario: Next sentence after a pause
- **WHEN** the user pauses and keeps talking
- **THEN** a new session starts and the next sentence is recognized

#### Scenario: Keyboard hidden
- **WHEN** the keyboard is hidden during dictation
- **THEN** the microphone is released and the composing text is kept

### Requirement: Recovery
The keyboard SHALL recover from a stalled or busy recognizer: recreate it if it is not ready within 4 s or if there is
no partial result 7 s after speech begins; on `RECOGNIZER_BUSY` it SHALL retry with a growing delay, up to 20 times.
On other errors it SHALL recreate the recognizer when needed and retry; after 5 errors in a row it SHALL stop dictation
and show the error. A successful result SHALL reset the count.

#### Scenario: Recognizer busy
- **WHEN** the engine returns `RECOGNIZER_BUSY`
- **THEN** the keyboard recreates the recognizer and retries after a delay

#### Scenario: Repeated errors
- **WHEN** the engine returns an error 5 times in a row without a result
- **THEN** dictation stops and the status line shows the error and asks to tap Dictate again
