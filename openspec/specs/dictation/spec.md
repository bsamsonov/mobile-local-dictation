# Dictation Specification

## Purpose
Voice input through the keyboard (IME): on-device recognition, text at the cursor, continuous dictation without losing words.

## Requirements

### Requirement: On-device recognition only
The keyboard SHALL recognize speech only with the device's on-device recognizer
(`SpeechRecognizer.createOnDeviceSpeechRecognizer`, with `EXTRA_PREFER_OFFLINE`) and SHALL NOT fall back to cloud recognition.

#### Scenario: No on-device engine
- **GIVEN** the device has no on-device recognition service
- **WHEN** the user starts dictation
- **THEN** the keyboard shows an error that points to the diagnostics and does not record audio

### Requirement: Offline operation
With the model for the selected language installed, dictation SHALL work with no network connection: recognition,
spoken punctuation, voice commands, digits and all keyboard keys. The app SHALL NOT request the `INTERNET` permission.
If the model is missing, the keyboard SHALL say so and point to the settings screen instead of using the network.

#### Scenario: Airplane mode
- **GIVEN** airplane mode is on and the `ru-RU` model is installed
- **WHEN** the user dictates "first line, new line, second line"
- **THEN** the text is recognized and inserted as two lines

#### Scenario: Model not installed
- **GIVEN** the model for the selected language is not installed
- **WHEN** the user starts dictation
- **THEN** the keyboard shows that the model is missing and suggests downloading it in the app

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

### Requirement: Continuous microphone capture
On Android 13+, when the option is on (default), the keyboard SHALL record the microphone itself and feed the audio
to the engine (`EXTRA_AUDIO_SOURCE`), replaying up to 1.8 s of audio from the end of the previous session into the new one.
If the engine does not read the stream for 3 s, the keyboard SHALL fall back to the engine's own microphone.

#### Scenario: Words between sessions
- **WHEN** the user speaks while a new session is starting
- **THEN** those words reach the new session and are not lost

### Requirement: Final text from a failed session
If a session ends with an error after partial results, the keyboard SHALL treat the last partial result as final
(apply post-processing and the "stop" command).

#### Scenario: Engine error after a command
- **GIVEN** the partial result is "new line"
- **WHEN** the session ends with `ERROR_CLIENT`
- **THEN** a line break is inserted, not the words

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

### Requirement: Log privacy
The app SHALL NOT write recognized text to logs; logs MAY contain events and text lengths.

#### Scenario: Debug log
- **WHEN** a partial result arrives
- **THEN** only its length is logged

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
