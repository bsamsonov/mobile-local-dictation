# Delta for Dictation

## ADDED Requirements

### Requirement: Phrase closed without a final result
If a partial result comes at least 600 ms after the previous partial of the same session and does not continue it
(less than half of the shorter text is a common prefix, ignoring case, spaces and punctuation), the keyboard SHALL
treat the previous partial as final (apply post-processing and the "stop" command) before showing the new one.
Committing a phrase in the middle of a live session SHALL NOT move the point from which audio is replayed into the
next session.

#### Scenario: Next phrase after a pause
- **GIVEN** the composing text is "I went home"
- **WHEN** after 1 s of silence the engine sends the partial "and then" with no final result in between
- **THEN** "I went home" is committed and "and then" is shown after it

#### Scenario: Revision of the same phrase
- **GIVEN** the composing text is "hello world"
- **WHEN** 100 ms later the engine sends the partial "hello word"
- **THEN** the composing text becomes "hello word" and nothing is committed

### Requirement: Final result shorter than the shown text
If the engine's final result for a phrase has less than 60% of the letters and digits of the last partial result shown
for it, the keyboard SHALL commit that partial result instead. A final result that arrives within 1.5 s after this,
with no partial in between, and whose text is contained in the committed partial SHALL be dropped.

#### Scenario: Final covers only the beginning
- **GIVEN** the composing text is "if I start talking then pause"
- **WHEN** the engine's final result is "if I s"
- **THEN** "if I start talking then pause" is committed

#### Scenario: Normal correction kept
- **GIVEN** the composing text is "hello how how are you"
- **WHEN** the engine's final result is "Hello, how are you."
- **THEN** "Hello, how are you." is committed
