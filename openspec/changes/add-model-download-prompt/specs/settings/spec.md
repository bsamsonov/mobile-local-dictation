# Delta for Settings

## ADDED Requirements

### Requirement: Model download offer
If the selected language has no installed on-device model, or the engine reports that the model is outdated, the app
SHALL offer to download it and SHALL show the progress, the "scheduled" state and errors.

#### Scenario: Language without a model
- **WHEN** the user picks a language whose model is available but not installed
- **THEN** the app offers to download it
- **AND** shows the progress until the model is installed
