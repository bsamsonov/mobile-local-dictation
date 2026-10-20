# Proposal: split dictation into sentences at long pauses

Issue: https://github.com/bsamsonov/mobile-local-dictation/issues/4

## User story (P1)
As a user, I pause after a sentence and the next phrase starts a new sentence, without saying "period".

## Why
The engine ends a session on every pause; today the next phrase is joined with a space and its first letter is
lowercased, so a dictated text becomes one long sentence unless every period is spoken.

## What changes
- A pause longer than a threshold ends the sentence: a period is added if the phrase does not already end with
  punctuation, and the next phrase starts with a capital letter.
- Short pauses (breathing inside a sentence) keep the current behavior.
- A switch in the settings; the threshold is tuned on the phone.

## Out of scope
Question or exclamation detection from intonation.

## Success criteria
On a fixed text of 20 sentences dictated with natural pauses: at least 90 % of sentence boundaries are correct,
at most 1 false split.

## Open questions
- [NEEDS CLARIFICATION] Pause threshold: measured from `onEndOfSpeech` to the next `onBeginningOfSpeech`, or from
  the final result timestamp? Starting value 1.2 s.
