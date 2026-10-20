# Delta for Dictation

## ADDED Requirements

### Requirement: Mixed-language recognition
When mixed input is on, the keyboard SHALL request recognition that allows English terms inside Russian speech
(language switching and/or biasing, as decided in design.md), and SHALL keep working with the primary language only
if the engine does not support it.

#### Scenario: Term in Latin letters
- **WHEN** the user says a Russian sentence containing "Kubernetes" with mixed input on
- **THEN** "Kubernetes" is written in Latin letters
