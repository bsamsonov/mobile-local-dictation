# Proposal: offer to download the model of the selected language

Issue: https://github.com/bsamsonov/mobile-local-dictation/issues/2
Depends on: add-language-picker.

## User story (P2)
As a user who picked a language without a model, I am offered to download it right away instead of getting an error while dictating.

## Why
Picking a language without a model leads to a dictation error; the download button has to be found by hand.

## What changes
- After picking a language without an installed model, the app offers to download it and shows the progress.
- The same offer if the engine reports that the model needs an update (if it can report that at all).
- The keyboard's "model not installed" error offers to open the settings.

## Out of scope
Deleting models; storage management.

## Success criteria
- No "model not installed" error after picking a language from the list.
- Progress or the "scheduled" state is visible until the model is installed.

## Open questions
- [NEEDS CLARIFICATION] Does SODA report an outdated model? `RecognitionSupport` only has
  installed/pending/supported/online. If not, add a manual "update model" action (call `triggerModelDownload` again).
