# Proposal: keep dictating while the screen is locked

Issue: https://github.com/bsamsonov/mobile-local-dictation/issues/7

## User story (P1)
As a user, I dictate a long note, lock the phone with the power button (or let the screen time out) and keep talking;
after unlocking, everything I said is in the note.

## Why
Hiding the keyboard released the microphone, and a screen lock hides the keyboard. Long dictation meant keeping the
screen on.

## What changes
- A screen lock (power button or timeout) during dictation does not stop it. While the screen is locked the app runs a
  foreground service of type `microphone` with a notification ("Dictating…", Stop action).
- Text recognized while locked is held in memory and inserted into the same app after unlock; dictation then goes on.
- If after unlock the user is in another app, dictation stops and the held text is copied to the clipboard.
- Hiding the keyboard in any other way still stops dictation.

## Out of scope
Typing into the field while the screen is locked; a setting to turn this off.

## Success criteria
- 1 minute of speech with the screen locked is inserted after unlock without lost phrases (SC-002).
- The notification is visible while locked and its Stop action stops dictation.
