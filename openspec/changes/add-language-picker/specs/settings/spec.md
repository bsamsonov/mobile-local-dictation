# Delta for Settings

## MODIFIED Requirements

### Requirement: Recognition language and model
The settings screen SHALL let the user pick the recognition language from a drop-down list of the languages the
on-device engine supports: installed first, then downloading, then downloadable, each with a state marker; the state
of the selected language SHALL be shown under the list. The screen SHALL let the user check whether the model is
installed and request its download. If the engine does not report its languages, a text field for a BCP 47 tag SHALL
be shown instead. The default language SHALL be `en-US` until the user picks one. Only the user's pick SHALL be saved,
and it SHALL be kept across app updates and reinstalls over the current build.

#### Scenario: Check the model
- **WHEN** the user taps the check button
- **THEN** the screen shows the installed, downloading, downloadable and online-only languages

#### Scenario: Download scheduled
- **WHEN** the user requests a download and the system defers it
- **THEN** the screen says the download is scheduled (it often waits for Wi-Fi or charging)

#### Scenario: Pick a language
- **WHEN** the user opens the language list
- **THEN** every on-device language is shown with its model state, installed ones first

#### Scenario: Default language
- **GIVEN** the user has never picked a language
- **THEN** `en-US` is used

#### Scenario: Picked language kept
- **GIVEN** the user picked `ru-RU`
- **WHEN** the app is rebuilt and installed over the current build
- **THEN** `ru-RU` stays selected
