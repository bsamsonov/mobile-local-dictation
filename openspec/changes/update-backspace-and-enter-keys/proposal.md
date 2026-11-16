# Proposal: backspace that repeats while held, Enter as on a regular keyboard

## User story (P2)
As a user, I hold ⌫ to delete a run of text, as on any keyboard; ↵ after dictated text does not type the last words twice.

## Why
- ⌫ deleted one character per tap only.
- ↵ sent a `KEYCODE_ENTER` key event. In Samsung Notes a key event right after dictated text often typed the last words
  once more (it happens with other keyboards too, less often). The AOSP keyboard (LatinIME) types a line break as text.

## What changes
- ⌫: one character on touch; held down, repeats after the system key repeat timeout (the long-press timeout) every
  key repeat delay (50 ms); after 20 repeats two characters per repeat (as LatinIME).
- ↵: a line break as text in a multi-line field; the field's action (send, search, next...) where it has one;
  `KEYCODE_ENTER` otherwise.
- Keys that edit the text while a phrase is shown underlined fix the shown words; only what the engine adds later
  is typed (see the fix "do not type the last phrase twice").

## Success criteria
- Holding ⌫ deletes continuously and speeds up.
- Dictate, pause, ↵ in Samsung Notes: no words typed twice.
