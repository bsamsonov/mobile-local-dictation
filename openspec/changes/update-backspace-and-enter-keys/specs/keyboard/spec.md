## MODIFIED Requirements

### Requirement: Line break key
The enter key (↵) SHALL work as on the AOSP keyboard: in a multi-line field without an editor action it SHALL type a
line break as text; in a field with an editor action (send, search, next...) it SHALL perform that action; otherwise
it SHALL send `KEYCODE_ENTER`. A phrase shown underlined SHALL be fixed as shown first, and its words SHALL NOT be
typed again when the engine finishes the phrase. The same result by voice is described in text-postprocessing
("new line").

#### Scenario: New line in a note
- **GIVEN** the cursor is in a multi-line note
- **WHEN** the user taps ↵
- **THEN** a line break is inserted at the cursor

#### Scenario: Enter right after dictation
- **GIVEN** a dictated phrase is still shown underlined
- **WHEN** the user taps ↵ and the engine finishes the phrase later
- **THEN** the phrase stays before the line break once and is not typed again after it

### Requirement: Space and backspace
The space key SHALL insert a space at the cursor. The backspace key SHALL send `KEYCODE_DEL` on touch and, while it
is held down, repeat it after the system key repeat timeout every system key repeat delay; after 20 repeats it SHALL
delete two characters per repeat.

#### Scenario: Delete a character
- **WHEN** the user taps ⌫
- **THEN** the character before the cursor is deleted

#### Scenario: Hold backspace
- **WHEN** the user holds ⌫
- **THEN** characters keep being deleted until the key is released, faster after a while
