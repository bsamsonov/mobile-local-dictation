# Delta for Dictation

## MODIFIED Requirements

### Requirement: Continuous dictation
With its own microphone capture the keyboard SHALL request an ordinary (not segmented) session, which the engine keeps
across pauses; with the engine's own microphone it SHALL request a segmented session (Android 13+). Whenever a session
ends (final result, silence or error), the keyboard SHALL start a new one, until the user taps Stop, says "stop" or
hides the keyboard. The restart after a final result SHALL wait briefly so that a late second result for the same
phrase is still inserted. Every session SHALL use its own recognizer, and events of a replaced recognizer SHALL be
ignored. Auto-start when the keyboard opens SHALL fire after a 250 ms debounce.

#### Scenario: Next sentence after a pause
- **WHEN** the user pauses and keeps talking
- **THEN** the next sentence is recognized, in the same session or in a new one

#### Scenario: Keyboard hidden
- **WHEN** the keyboard is hidden during dictation
- **THEN** the microphone is released and the composing text is kept

#### Scenario: Late second result
- **GIVEN** the engine delivered a final result for a phrase
- **WHEN** it delivers a second result for the same phrase shortly after
- **THEN** the second result is inserted too, and only then a new session starts

#### Scenario: Late error of the previous session
- **GIVEN** a session ended and a new one is running
- **WHEN** the engine reports an error for the ended session
- **THEN** the running session goes on and nothing is typed

### Requirement: Continuous microphone capture
On Android 13+, when the option is on (default), the keyboard SHALL record the microphone itself and feed the audio
to the engine (`EXTRA_AUDIO_SOURCE`), replaying up to 1.8 s of audio from the end of the previous session into the new one.
If the engine does not read the stream (the pipe stays full) for 3 s, the keyboard SHALL fall back to the engine's own
microphone. If the engine closes the stream without ending the session, the keyboard SHALL treat the text in progress
as final and start a new session with its own capture.

#### Scenario: Words between sessions
- **WHEN** the user speaks while a new session is starting
- **THEN** those words reach the new session and are not lost

#### Scenario: Stream closed at the end of a session
- **WHEN** the engine closes the stream and reports the end of the session
- **THEN** the next session still uses the keyboard's own capture

### Requirement: Recovery
The keyboard SHALL recover from a stalled or busy recognizer: recreate it if it is not ready within 4 s or, with the
engine's own microphone, if there is no partial result 7 s after speech begins; on `RECOGNIZER_BUSY` it SHALL retry
with a growing delay, up to 20 times. On other errors it SHALL recreate the recognizer when needed and retry; after 5
errors in a row it SHALL stop dictation and show the error. A successful result SHALL reset the count.

#### Scenario: Recognizer busy
- **WHEN** the engine returns `RECOGNIZER_BUSY`
- **THEN** the keyboard recreates the recognizer and retries after a delay

#### Scenario: Repeated errors
- **WHEN** the engine returns an error 5 times in a row without a result
- **THEN** dictation stops and the status line shows the error and asks to tap Dictate again

#### Scenario: Silence with own capture
- **GIVEN** the keyboard feeds its own audio
- **WHEN** the user is silent for a minute
- **THEN** the session is not restarted

### Requirement: Final text from a failed session
If a session ends with an error after partial results, also after Stop, the keyboard SHALL treat the text in progress
as final (apply post-processing and the "stop" command) and type it once.

#### Scenario: Engine error after a command
- **GIVEN** the partial result is "new line"
- **WHEN** the session ends with `ERROR_CLIENT`
- **THEN** a line break is inserted, not the words

## ADDED Requirements

### Requirement: Finished phrase in a continuous session
In an ordinary session with the keyboard's own capture, a partial result with two or more hypotheses SHALL be treated
as a finished phrase: its first hypothesis is committed (post-processing and the "stop" command apply) in place of
the composing text, and the following partial results start a new phrase. A final result that only repeats a phrase
typed this way within 1.5 s SHALL be dropped; a final result without text SHALL commit the text in progress.

#### Scenario: Phrase finished, the next one already started
- **GIVEN** the composing text is "I went home. And then"
- **WHEN** the engine sends a partial result with the hypotheses "I went home." and "i went home", then "And then"
- **THEN** "I went home." is committed once and "And then" is shown after it

#### Scenario: Stop in the middle of a phrase
- **GIVEN** the composing text is "and then I"
- **WHEN** the user taps Stop and the engine sends a final result without text
- **THEN** "and then I" stays in the field as typed text

### Requirement: Phrase closed without a final result
If a partial result does not continue the previous partial of the same session (less than half of the shorter text
is a common prefix, ignoring case, spaces and punctuation) and it comes after the engine reported the end of speech
for the previous text or at least 600 ms after the previous partial, the keyboard SHALL treat the previous partial
as final (apply post-processing and the "stop" command) before showing the new one. Committing a phrase in the middle
of a live session SHALL NOT move the point from which audio is replayed into the next session.

#### Scenario: Next phrase after a pause
- **GIVEN** the composing text is "I went home"
- **WHEN** after 1 s of silence the engine sends the partial "and then" with no final result in between
- **THEN** "I went home" is committed and "and then" is shown after it

#### Scenario: Revision of the same phrase
- **GIVEN** the composing text is "hello world"
- **WHEN** 100 ms later, with no end of speech in between, the engine sends the partial "yellow world"
- **THEN** the composing text becomes "yellow world" and nothing is committed

### Requirement: Final result shorter than the shown text
In a session with the engine's own microphone, if the final result for a phrase has less than 60% of the letters and
digits of the last partial result shown for it, the keyboard SHALL commit that partial result instead. Partial results
that arrive after the final result of a session SHALL be ignored. A final result that arrives within 1.5 s after text
was typed from a partial result, with no partial in between, and whose text is contained in it SHALL be dropped.

#### Scenario: Final covers only a piece
- **GIVEN** the composing text is "if I start talking then pause"
- **WHEN** the engine's final result is "pause", followed by a late partial "if I start talking then" and an error
- **THEN** "if I start talking then pause" is in the field once

#### Scenario: Normal correction kept
- **GIVEN** the composing text is "hello how how are you"
- **WHEN** the engine's final result is "Hello, how are you."
- **THEN** "Hello, how are you." is committed

### Requirement: Line break from the engine
A recognition result that consists of a line break only SHALL be typed as a line break.

#### Scenario: Engine's own "new line"
- **WHEN** the engine returns a line break as the text of a phrase
- **THEN** a line break is inserted
