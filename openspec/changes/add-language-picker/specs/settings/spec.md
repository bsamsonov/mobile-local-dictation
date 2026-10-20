# Delta for Settings

## MODIFIED Requirements

### Requirement: Recognition language and model
The settings screen SHALL let the user pick the recognition language from a drop-down list of the languages the
on-device engine supports, each marked "installed", "downloading" or "available for download"; check whether the model
is installed; and request its download. If the engine does not report support, a text field SHALL be shown instead.
The default language for a new install SHALL be the system language if it is supported on-device, otherwise the first
installed one. (Was: a text field, default `ru-RU`.)

#### Scenario: Check the model
- **WHEN** the user taps the check button
- **THEN** the screen shows the installed, downloading, downloadable and online-only languages

#### Scenario: Download scheduled
- **WHEN** the user requests a download and the system defers it
- **THEN** the screen says the download is scheduled (it often waits for Wi-Fi or charging)

#### Scenario: Pick a language
- **WHEN** the user opens the language list
- **THEN** every supported language is shown with its model state

#### Scenario: Saved language kept
- **GIVEN** the user has `ru-RU` saved
- **WHEN** the app is updated
- **THEN** `ru-RU` stays selected
