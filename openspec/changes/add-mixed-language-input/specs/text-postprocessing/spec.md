# Delta for Text Post-processing

## ADDED Requirements

### Requirement: User term dictionary
The app SHALL replace whole words that match entries of the user dictionary (case-insensitive) with the entry's
replacement, e.g. "кубернетес" (Kubernetes spelled in Cyrillic) with "Kubernetes".

#### Scenario: Term replaced
- **GIVEN** the dictionary maps "кубернетес" to "Kubernetes"
- **WHEN** the result is "задеплоить в кубернетес" ("deploy to kubernetes")
- **THEN** the text is "задеплоить в Kubernetes"
