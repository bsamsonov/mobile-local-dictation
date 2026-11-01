# Delta for Dictation

## ADDED Requirements

### Requirement: Audio source
Dictation SHALL record from the source chosen with the microphone key.
With the headset source the app SHALL select a connected Bluetooth headset (SCO or LE Audio) as the communication
device before the first session, wait for the link at most 1.5 s, and record from that headset's input. It SHALL
release the communication device as soon as dictation stops for any reason (key, voice command, notification, keyboard
hidden, error). With the phone source it SHALL record from the built-in microphone.

#### Scenario: Dictation through earbuds
- **GIVEN** the microphone key shows 🎧 and Bluetooth earbuds are connected
- **WHEN** the user starts dictation
- **THEN** speech is recorded from the earbuds' microphone and the status line names the headset

#### Scenario: Link released after stop
- **GIVEN** dictation runs through the earbuds
- **WHEN** dictation stops
- **THEN** the app no longer holds the headset as the communication device and music plays in normal quality

### Requirement: Headset fallback
If the headset source is chosen and no headset is available, dictation SHALL use the phone microphone and the status
line SHALL say so. When a headset connects or disconnects during dictation, the app SHALL switch the route and keep
dictating.

#### Scenario: No headset
- **GIVEN** the microphone key shows 🎧 and no headset is connected
- **WHEN** the user starts dictation
- **THEN** dictation runs from the phone microphone and the status line says no headset is connected

#### Scenario: Earbuds connected during dictation
- **GIVEN** the key shows 🎧 and dictation runs from the phone microphone because no headset was connected
- **WHEN** earbuds connect
- **THEN** dictation continues from the earbuds' microphone
