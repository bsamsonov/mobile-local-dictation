# Proposal: document behavior that exists but is not in the spec

Issue: none (spec completion; no code changes).

## Why
Two behaviors are implemented and visible to the user but missing from the spec: the restart after a final result
waits for a possible late second result, and the settings screen has a "Try it" field.

## What changes
- Continuous dictation: a scenario for a late second result of the same phrase (it is inserted, not lost).
- Settings: a requirement for the "Try it" field.

## Out of scope
Code changes. Exact timings stay in design.md, not in requirements.

## Success criteria
`openspec validate --all` passes; the requirements match `DictationIME` and `MainActivity`.
