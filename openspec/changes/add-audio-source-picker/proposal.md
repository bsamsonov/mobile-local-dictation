# Proposal: choose the microphone — phone or Bluetooth headset

## User story (P1)
As a user with Bluetooth earbuds, I tap one key on the voice keyboard to dictate through the earbuds' microphone, and
tap it again to go back to the phone's microphone.

## Why
The app recorded with the `VOICE_RECOGNITION` source and left routing to the system. A Bluetooth headset connected for
music (A2DP) has no microphone path; its microphone works only over SCO (or LE Audio), which the system opens only when
an app asks for it. So the earbuds' microphone was used rarely and unpredictably.

## What changes
- A key in row 2 of the keyboard shows the selected microphone (📱 phone, 🎧 headset) and switches between them. The
  choice is saved.
- With 🎧, dictation selects the headset as the communication device (`AudioManager.setCommunicationDevice`, SCO or
  LE Audio), waits until the link is up (at most 1.5 s), and records from the headset's input. The link is released
  as soon as dictation stops, so music returns to normal quality.
- With 📱, the app records from the built-in microphone even if a headset is connected.
- If 🎧 is selected and no headset is available, dictation uses the phone microphone and says so in the status line;
  when a headset connects or disconnects during dictation, the route is switched without stopping dictation.

## Out of scope
Wired and USB headsets (the system already routes them); choosing between several Bluetooth headsets.

## Success criteria
- With 🎧 and connected earbuds, speech is recognized with the phone in another room; the status line names the headset.
- With 📱, the earbuds' microphone is not used; music keeps A2DP quality while dictating.
