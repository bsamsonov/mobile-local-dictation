# Text Post-processing Specification

## Purpose
Turn voice commands and numbers into formatting that the recognition engine does not produce. Runs locally, offline.
Russian phrases below are functional data (what the user says), not documentation text.

## Requirements

### Requirement: Line break commands
When voice commands are on, the app SHALL replace "новая строка", "новую строку", "с новой строки", "перевод строки",
"энтер" (Russian for "new line" / "enter"), "new line", "newline", "enter" with a line break, and
"новый абзац", "с нового абзаца" (Russian for "new paragraph"), "new paragraph" with an empty line,
capitalizing the next line. This applies to any recognition language.

#### Scenario: English command
- **WHEN** the result is "Hello. New line. how are you"
- **THEN** the text is "Hello.\nHow are you"

#### Scenario: Russian paragraph command
- **WHEN** the result is "Первый. Новый абзац. второй"
- **THEN** the text is "Первый.\n\nВторой"

### Requirement: Stop command
For Russian, if a phrase ends with the word "стоп" ("stop"), the app SHALL stop dictation and SHALL NOT insert the word.

#### Scenario: Stop at the end
- **WHEN** the result is "Всё готово. Стоп."
- **THEN** "Всё готово." is inserted and dictation stops

### Requirement: Numbers as digits
For Russian, when the option is on, the app SHALL convert cardinal number words to digits; single "один"…"девять"
(one…nine) SHALL stay words; case forms and ordinals are not handled.

#### Scenario: Compound number
- **WHEN** the result is "двадцать пять" (twenty-five)
- **THEN** the text is "25"

### Requirement: Spoken punctuation
The app SHALL ask the engine for formatting (`EXTRA_ENABLE_FORMATTING`, Android 13+) so that spoken punctuation marks
become characters; the option can be turned off. This is done by the on-device model, offline.

#### Scenario: Spoken comma
- **WHEN** the user says "запятая" (comma)
- **THEN** the engine returns ","
