# Proposal: align the dictation spec with how the engine really behaves

Issue: none (spec correction; the code already works this way).

## Why
The first plan assumed the engine would keep one long segmented session and return clean, final results.
Testing on the phone showed otherwise: the on-device engine (SODA) ignores segmented sessions and ends a session
on every pause, capitalizes every utterance, and sometimes fails several times in a row. The code was changed to
work around this, but the spec still describes the original plan. This change makes the spec match the code.
The reasoning is in design.md.

## What changes
- Continuous dictation: a segmented session is requested first; if the engine ends the session at a pause anyway,
  the keyboard restarts it.
- Text at the cursor: the capital letter the engine puts on every utterance is removed mid-sentence.
- Recovery: after 5 errors in a row dictation stops with a message instead of retrying forever.

## Out of scope
Code changes.

## Success criteria
`openspec validate --all` passes; every MODIFIED requirement matches `DictationIME` and `Speech`.
