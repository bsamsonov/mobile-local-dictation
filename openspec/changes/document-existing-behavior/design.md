# Design: restart delay after a final result

The engine may deliver a second `onResults` for the same utterance shortly after the first one. Restarting the session
right away cancels that result and starts a cancel/busy cascade during which speech is lost.

- The restart waits 200 ms; a late `onResults` inserts its text and reschedules the restart.
- Observed delay of the second result on a Galaxy S23: up to about 90 ms. A longer wait means a longer gap with no
  listening, so the value is kept close to the observed delay.
- The number is an implementation detail: it lives here and in `DictationIME`, not in the requirement, so tuning it
  does not need a spec change.
