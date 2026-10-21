# Delta for Dictation

## MODIFIED Requirements

### Requirement: Continuous microphone capture
On Android 13+, when the option is on (default: off), the keyboard SHALL record the microphone itself and feed the audio
to the engine (`EXTRA_AUDIO_SOURCE`), replaying up to 1.8 s of audio from the end of the previous session into the new one.
If the engine does not read the stream for 3 s, the keyboard SHALL fall back to the engine's own microphone.

#### Scenario: Default
- **GIVEN** the user never changed the option
- **WHEN** dictation starts
- **THEN** the engine records the microphone itself

#### Scenario: Words between sessions
- **GIVEN** the option is on
- **WHEN** the user speaks while a new session is starting
- **THEN** those words reach the new session and are not lost
