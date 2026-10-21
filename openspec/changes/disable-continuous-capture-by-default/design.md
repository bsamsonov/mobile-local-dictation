# Design

## What was tried
1. Continuous capture (`AudioFeeder`, `EXTRA_AUDIO_SOURCE`) was on by default to avoid losing words while a new session
   starts. On the Galaxy S23 (SODA) logcat shows, for every session with it:
   `InterruptedIOException: read interrupted by close() on another thread` in the engine → `onError(ERROR_CLIENT)` →
   10–50 ms later the engine logs `#handleFinalResult`. The final result never reaches the app; the app inserts the
   last partial ("Новая стро"). 9 of 9 sessions in one run, all sessions in another.
2. Waiting up to 400 ms after `ERROR_CLIENT` for a late `onResults` (without recreating the recognizer): the late
   result arrived in 1 of 6 cases; the framework drops results after an error.
3. Without continuous capture, in the same run, all sessions ended with `onResults` or `ERROR_NO_MATCH`.

## Decision
Off by default. The price is the short gap (about 200–300 ms) between sessions when the engine ends a session at a
pause; words said exactly then may be lost. The switch stays for testing on other engines.
