# Delta for Keyboard

## MODIFIED Requirements

### Requirement: Layout
The keyboard SHALL show a status line and two rows of keys.
Row 1: switch keyboard (⌨), Dictate/Stop (wide), backspace (⌫).
Row 2: settings (gear icon), microphone (📱/🎧), `,`, `.`, `?`, space (wide), enter (↵).

#### Scenario: Keyboard opened
- **WHEN** the user switches to "On-device voice input"
- **THEN** both rows of keys and the status line with the recognition language are shown

## ADDED Requirements

### Requirement: Microphone key
The microphone key SHALL show the selected audio source — 📱 phone microphone or 🎧 Bluetooth headset — and switch to
the other one when tapped. The choice SHALL be saved. If dictation is running, it SHALL continue from the new source.
The key SHALL have the accessible label "Microphone: phone" or "Microphone: headset".

#### Scenario: Switch to the headset while dictating
- **GIVEN** dictation is running from the phone microphone and earbuds are connected
- **WHEN** the user taps 📱
- **THEN** the key shows 🎧, dictation goes on, and the status line names the headset
