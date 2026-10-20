# Proposal: pick the language from a list

Issue: https://github.com/bsamsonov/mobile-local-dictation/issues/1

## User story (P2)
As a user, I pick the dictation language from a list, see right away whether it works offline, and my choice is kept.

## Why
Typing a BCP 47 tag by hand is awkward and error-prone; it is not visible which languages work offline.

## What changes
- The recognition language field becomes a drop-down list of the languages the on-device engine supports,
  each marked with its model state (installed, downloading, downloadable).
- The default language is English (`en-US`) until the user picks one.
- Only the user's own pick is saved; the app never writes a language by itself, so the choice survives
  updates and reinstalls over the current build.
- The text field stays as a fallback when the engine does not report its languages.

## Out of scope
Several languages at once (see add-mixed-language-input).

## Success criteria
- Picking a language takes at most 2 taps, with no tag typing.
- The model state shown for each language matches the engine's lists.
- A picked language is still selected after `./build.sh install` over the current build.
