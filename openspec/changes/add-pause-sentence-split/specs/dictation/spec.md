# Delta for Dictation

## ADDED Requirements

### Requirement: Sentence split at long pauses
When the option is on and the pause before a phrase is longer than the threshold, the keyboard SHALL end the previous
sentence with a period (unless it already ends with `.`, `?`, `!` or a line break) and SHALL capitalize the new phrase.
Shorter pauses SHALL keep joining phrases into one sentence.

#### Scenario: Long pause
- **GIVEN** the text before the cursor is "The build is green"
- **WHEN** the user pauses longer than the threshold and says "deploy it"
- **THEN** the text is "The build is green. Deploy it"

#### Scenario: Short pause
- **GIVEN** the text before the cursor is "The build is green"
- **WHEN** the user pauses briefly and says "and fast"
- **THEN** the text is "The build is green and fast"
