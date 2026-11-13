# Proposal: the spoken words for a comma and a period become marks

## User story (P2)
As a user dictating in Russian, I say "запятая" or "точка" and get the mark, so I can punctuate myself where the
engine does not.

## Why
The specification assumed the engine turns spoken punctuation into characters. The Russian on-device model does not:
a trace (2026-11-13) shows "магазин запятая, потом вернулись домой. Точка." The engine's own punctuation depends on
pauses and context and is sometimes missing in a long phrase.

## What changes
- With voice commands on, "запятая", "точка" and "точка с запятой" become ",", "." and ";", attached to the word
  before them; the engine's own mark and space around the word are absorbed; the word after a period is capitalised.
- "точка зрения" (point of view) stays text. Other case forms ("точку", "запятой") stay text.
- A phrase that starts with such a mark is attached to the text before it without a space; if that text already ends
  with a mark, the spoken one is dropped (a comma said after a long pause cannot replace the period the engine put).

## Out of scope
Question and exclamation marks, dashes, quotes; other languages.

## Success criteria
- "привет запятая как дела точка" gives "привет, как дела."
