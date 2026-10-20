# Proposal: pick the language from a list

Issue: https://github.com/bsamsonov/mobile-local-dictation/issues/1

## User story (P2)
As a user, I pick the dictation language from a list and see right away whether it works offline.

## Why
Typing a BCP 47 tag by hand is awkward and error-prone; it is not visible which languages work offline.

## What changes
- The recognition language field becomes a drop-down list of the languages the on-device engine supports,
  each marked with its model state.
- Default language for a new install: the system language if it is supported on-device, otherwise the first installed one.

## Out of scope
Several languages at once (see add-mixed-language-input).

## Success criteria
- Picking a language takes at most 2 taps, with no tag typing.
- The model state shown for each language matches the engine's lists.

## Impact
Specs: settings. Code: `MainActivity`, `Speech`, strings (en, ru).
