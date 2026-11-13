# Design: continuous session

## How the engine was observed
`./build.sh trace` installs a separate package with `TraceService`. It plays a 16 kHz PCM file to
`createOnDeviceSpeechRecognizer` through `AudioFeeder`, with the same session loop as the keyboard, and logs every
event with its text (tag `LDTrace`). The test sound is synthesized Russian speech with pauses of 0.8-3 s
(`debug/make-test-audio.sh`). Phone: Galaxy S23, Android 16, Android System Intelligence, ru-RU. Traces are in
`test/traces`.

## Findings
1. **Segmented request + own audio** (the behaviour before this change): at every pause `onEndOfSpeech` twice, then
   `ERROR_CLIENT`; 0 final results in 14 phrases with formatting on, 3 of 14 with formatting off. The last partial
   result may be incomplete: "Новая стро." for "новая строка", 2 of 2 times. An `ERROR_CLIENT` can arrive up to 1.2 s
   after `onResults`, i.e. inside the next session.
2. **Ordinary request + own audio**: one session for the whole file, 278 s with 20 s and 45 s of silence, no error and
   no `onResults` until the audio ends. `onEndOfSpeech`/`onBeginningOfSpeech` come at every pause and mean nothing
   more than a pause.
3. In that session a partial result with **two hypotheses** (formatted, raw) is a finished phrase. The partial
   results after it (the first one within 2 ms if there is text) carry only the text after that phrase. Before that,
   a partial result with one hypothesis holds everything not finished yet, often two sentences, and is revised as a
   whole ("Второй пункт." becomes "Второй пункт посмотреть, что происходит с короткими кусками.").
4. With formatting off some finished phrases come with one hypothesis only; the next phrase then starts as close as
   550 ms after the last partial result, but always after `onEndOfSpeech`.
5. Closing the audio pipe (Stop) makes the engine finish the phrase in progress (two hypotheses) and send `onResults`
   without text. `stopListening()` alone gives `ERROR_CLIENT`.
6. With formatting off the engine turns the spoken "новая строка" into a line break itself.

## Decisions
- **Ordinary session whenever the keyboard feeds the audio.** It removes the restarts and with them the lost words at
  session borders, the cut-off last partial result and most errors. Alternative considered: keep segmented sessions
  and repair their event stream. Rejected: the final text is simply not delivered there.
- **Two hypotheses mark a finished phrase** only in such a session. With the engine's own microphone no such partial
  results were recorded, so the old rules stay there.
- **`PhraseTracker`**, plain Java, owns these rules; the keyboard only types what it decides. Recorded traces are
  replayed against it in tests, so a new failure can be turned into a test from a trace.
- **One recognizer per session, events tagged by recognizer.** A late error of an old session can no longer end the
  current one. Recreating was already done after every `ERROR_CLIENT`, i.e. after almost every session.
- **No "speech began, no text" stall check with own audio.** A real stall there (no text although the user speaks) is
  not detected yet; it needs the sound level from `AudioFeeder`. Left for a later change if it happens.

## Risks
- The marker (two hypotheses) is observed behaviour of one engine version, not a documented contract. If it
  disappears, the "new phrase after the end of speech" rule still separates phrases (finding 4).
- Real speech may differ from synthesized speech; the phone check in the tasks covers that.
