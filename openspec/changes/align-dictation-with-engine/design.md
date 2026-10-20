# Design: what we tried and what we do instead

| Plan | What happened on the phone | What the code does now |
|---|---|---|
| One segmented session (`EXTRA_SEGMENTED_SESSION`, Android 13+) returns each phrase via `onSegmentResults` | SODA ignores it: the session ends at every pause, only one session at a time | Still requests it (engines that support it keep one session); otherwise restarts the session after every final result or silence error |
| Engine capitalization is right | The engine starts every utterance with a capital letter, so a sentence split by a pause got a capital in the middle | Capital letter only after `.` `!` `?` `…`, a line break or at the start of the field; otherwise an engine capital is lowered unless the word looks like an acronym (second letter is uppercase). Known cost: a proper name right after a pause mid-sentence is lowered too |
| Errors are rare and retrying is enough | `ERROR_CLIENT` / `SERVER_DISCONNECTED` can repeat; endless retries keep the mic busy with no result | Recreate the recognizer and retry; after 5 errors in a row stop and show the error. A result resets the count |

Alternatives considered and dropped:
- Keep only the restart approach and remove the segmented-session request: kept it because it costs nothing and helps
  on engines that support it.
- Do not touch capitalization: rejected, the text needed editing after every pause.
