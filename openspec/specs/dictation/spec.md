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
The keyboard SHALL show partial results as composing text and commit the final result at the cursor,
adding a leading space and a capital letter based on the text before the cursor.

#### Scenario: Phrase inserted
- **WHEN** the user says a phrase and pauses
- **THEN** the phrase replaces the composing text at the cursor

### Requirement: Continuous dictation
The keyboard SHALL restart recognition after every pause until the user taps Stop, says "stop" or hides
the keyboard. Auto-start when the keyboard opens SHALL fire after a 250 ms debounce.

#### Scenario: Next sentence after a pause
- **WHEN** the user pauses and keeps talking
- **THEN** a new session starts and the next sentence is recognized

#### Scenario: Keyboard hidden
- **WHEN** the keyboard is hidden during dictation
- **THEN** the microphone is released and the composing text is kept

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

#### Scenario: Recognizer busy
- **WHEN** the engine returns `RECOGNIZER_BUSY`
- **THEN** the keyboard recreates the recognizer and retries after a delay

### Requirement: Log privacy
The app SHALL NOT write recognized text to logs; logs MAY contain events and text lengths.

#### Scenario: Debug log
- **WHEN** a partial result arrives
- **THEN** only its length is logged
