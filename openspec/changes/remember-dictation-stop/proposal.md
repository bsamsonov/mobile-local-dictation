# Proposal: remember that dictation was stopped

Issue: https://github.com/bsamsonov/mobile-local-dictation/issues/5

## User story (P1)
As a user, when I stop dictation, it stays stopped after I lock and unlock the phone; when I switch to this keyboard
from another one, dictation starts by itself.

## Why
Auto-start fires every time the keyboard view starts, so after a screen lock/unlock the microphone turned on again
although the user had stopped it.

## What changes
- A stop by the user (Stop key or spoken "stop") is saved in the preferences.
- While it is saved, auto-start does not fire (screen lock/unlock, other fields, other apps).
- Tapping Dictate clears it; switching to this keyboard from another keyboard clears it too.

## Out of scope
Stops caused by errors (they are not saved: the next keyboard start retries).

## Success criteria
- Stop, lock, unlock: dictation does not start (10 of 10 tries).
- Stop, switch to Samsung Keyboard, switch back: dictation starts.
