# Proposal: one continuous recognition session, phrases typed exactly once

## User story (P1)
As a user, I dictate for minutes with pauses; every phrase I said is in the note once: nothing is missing and
nothing is repeated.

## Why
Text was still lost and repeated from time to time (also after `fix-phrase-overwritten-by-next`, which this change
replaces), and the spoken "new line" sometimes came out as cut-off words (issue #8). The engine's events were recorded
with their text by a new trace tool that plays a sound file to the engine; `design.md` has the findings. In short:

- With a segmented session and the keyboard's own audio, the engine ended the session at every pause with
  `ERROR_CLIENT` and almost never delivered a final result. The typed text was the last partial result that happened to
  arrive (so "new li." instead of "new line"), each restart cost the beginning of the next phrase, and a late error of
  the old session could end the new one, losing what it had heard.
- A partial result that carries a finished phrase was taken for ordinary text in progress. Depending on timing the
  phrase was replaced by the next partial result (lost) or typed together with text that was typed again (repeated).

## What changes
- With its own audio capture the keyboard asks for an ordinary (not segmented) session. The engine then keeps one
  session across pauses, for minutes, and revises punctuation and words of the text in progress with more context.
- In such a session a partial result with two or more hypotheses is a finished phrase: it is typed once and the text
  after it starts a new phrase.
- A new phrase is also recognized when the text stops continuing the previous one after the engine reported the end of
  speech (or after 600 ms), which covers finished phrases the engine does not mark.
- Every session gets its own recognizer; events of a replaced recognizer are ignored.
- The stall check "no text 7 s after speech began" applies only to the engine's own microphone: with the keyboard's
  audio the engine reports speech after every pause, and the check restarted healthy sessions in silence.
- The fallback to the engine's own microphone happens only when the engine really does not read the audio (the pipe is
  full), not when it closes the pipe at the end of a session. A pipe closed without any event restarts the session.
- A line break alone, which the engine returns for a spoken "new line" when formatting is off, is typed.
- Development tools: `./build.sh trace` (events with text for a sound file, separate package) and a test sound file
  the keyboard plays instead of the microphone; recorded traces are replayed in `./build.sh test`.

## Out of scope
- Periods the engine puts at pauses (fewer now, because a phrase is revised until it is finished; a separate change if
  it is still annoying).
- Sessions with the engine's own microphone keep the segmented request and the rules for a short final result.

## Success criteria
- The recorded 4.6-minute trace (36 phrases, pauses up to 45 s) replays to 36 phrases, none lost or repeated.
- Dictating to the phone for 2 minutes with pauses of 1-3 s loses and repeats nothing.
