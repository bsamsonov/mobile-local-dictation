# Proposal: an obvious way out of the settings and a sign that changes are saved

Issue: https://github.com/bsamsonov/mobile-local-dictation/issues/6

## User story (P2)
As a user, I see how to leave the settings and that my changes (e.g. the language) are already saved.

## Why
The only way out was the system Back button, and nothing showed that a change was saved, because settings are saved
on every change and there is no Save button.

## What changes
- An up arrow in the title bar and a Done button in a bar that stays at the bottom while scrolling; both close the screen.
- A short "Saved" message after picking a language or changing an option.

## Out of scope
A Save/Cancel flow: settings keep being saved at once.

## Success criteria
- The way out is visible without scrolling.
- Every change shows "Saved".
