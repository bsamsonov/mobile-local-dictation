# Delta for Settings

## ADDED Requirements

### Requirement: Leaving the settings
The settings screen SHALL show an up arrow in the title bar and a Done button that stays visible at the bottom while
the screen scrolls; both SHALL close the screen. Because settings are saved on every change, the screen SHALL show a
short "Saved" message after the user picks a language or changes an option.

#### Scenario: Done
- **WHEN** the user taps Done or the up arrow
- **THEN** the settings screen closes

#### Scenario: Change confirmed
- **WHEN** the user picks a language or changes an option
- **THEN** a short "Saved" message appears and the change is kept after reopening the screen
