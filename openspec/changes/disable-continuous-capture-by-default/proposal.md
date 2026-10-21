# Proposal: continuous microphone capture off by default

Issue: https://github.com/bsamsonov/mobile-local-dictation/issues/8

## User story (P1)
As a user, I say "новая строка" ("new line") and get a line break, not the cut-off text "новая стро.".

## Why
With continuous capture the engine ends every session with `ERROR_CLIENT` before the final result, and the app has to
insert the last partial result, which often ends mid-word. Without it the engine delivers final results.

## What changes
- Continuous microphone capture is off by default; the switch stays in the settings for testing.

## Out of scope
Fixing the engine; a different way to avoid the gap between sessions.

## Success criteria
- SC-003: at least 80 % of sessions end with a result (logcat `results:` vs `error CLIENT`).
- "новая строка" said 10 times gives 10 line breaks.
