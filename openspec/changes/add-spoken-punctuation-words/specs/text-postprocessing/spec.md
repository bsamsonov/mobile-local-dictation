# Delta for Text Post-processing

## MODIFIED Requirements

### Requirement: Spoken punctuation
The app SHALL ask the engine for formatting (`EXTRA_ENABLE_FORMATTING`, Android 13+) so that the engine punctuates the
text; the option can be turned off. This is done by the on-device model, offline. When voice commands are on, the app
SHALL replace the spoken words "запятая", "точка", "точка с запятой" (comma, period, semicolon) with the mark,
attached to the word before it, absorbing the engine's own mark next to the word and capitalizing the word after a
period. "точка зрения" (point of view) SHALL stay text. A mark at the start of a phrase SHALL be attached to the text
before it, or dropped if that text already ends with a mark.

#### Scenario: Spoken comma
- **WHEN** the result is "привет запятая, как дела"
- **THEN** the text is "привет, как дела"

#### Scenario: Spoken period
- **WHEN** the result is "первое точка второе"
- **THEN** the text is "первое. Второе"

#### Scenario: Period said after the engine's period
- **GIVEN** the text before the cursor ends with "."
- **WHEN** the result is "Точка."
- **THEN** nothing is inserted

#### Scenario: Not a command
- **WHEN** the result is "моя точка зрения"
- **THEN** the text is "моя точка зрения"
