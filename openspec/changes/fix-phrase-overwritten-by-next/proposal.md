# Proposal: a phrase is no longer overwritten by the next one

## User story (P1)
As a user, I say a sentence, pause and keep talking; the sentence I said stays in the note.

## Why
Sometimes, after a pause, the recognized sentence (or its last words) disappeared and only the new, shorter phrase
remained. The log shows why: within one session the engine closes a phrase internally and starts the next one without
sending a final (or segment) result. Partial results go to the composing text, so the first partial of the new phrase
replaced the previous phrase, which was never committed.

Example from the log: partials of 7, 9, 15, 15, 15 characters, then 1.2 s of silence, then a partial of 7 characters
with no result in between. Normal revisions of a phrase, which can also be shorter, came 60-120 ms apart.

A second cause, seen on 2026-11-01 ("если я начинаю говорить" erased): the partial text grew to 43 characters over a
pause, then the engine's final result had only 8 characters. Committing it replaced the whole composing text.

## What changes
- If a partial result comes at least 600 ms after the previous one and does not continue it (less than half of the
  shorter text is a common prefix, ignoring case, spaces and punctuation), the previous partial is committed as final
  (post-processing and "stop" apply) before the new phrase is shown.
- If the engine's final result has less than 60% of the letters of the last partial shown for the phrase, the partial
  is committed instead. A later final within 1.5 s whose text is already inside that committed partial is dropped, so
  nothing is typed twice.
- A phrase committed in the middle of a live session no longer moves the audio replay point for the next session, so
  the next session does not re-recognize audio that is already typed.

## Out of scope
Recovering words the engine itself cut off (issue #8, "new line" truncated to "new li…").

## Success criteria
- Dictating with pauses for 2 minutes loses no phrase that was visible as composing text.
