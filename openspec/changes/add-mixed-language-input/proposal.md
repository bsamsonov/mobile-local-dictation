# Proposal: English terms inside Russian speech

Issue: https://github.com/bsamsonov/mobile-local-dictation/issues/3

## User story (P1 for the author)
As a Russian-speaking user, I say a Russian sentence such as "deploy to Kubernetes" and get the English term in Latin
letters without editing.

## Why
The author dictates in Russian with English terms; the Russian model writes them in Cyrillic or garbles them.

## What changes
Terms spoken in English inside Russian dictation are written in Latin letters without making the Russian text worse.

## Out of scope
Fully bilingual dictation (whole English sentences inside Russian ones); translation.

## Success criteria
On a fixed set of 30 phrases: at least 80 % of English terms in Latin letters; the error rate on Russian words grows by at most 5 %.

## Open questions
- [NEEDS CLARIFICATION] Does on-device language switching work for the ru-RU + en-US pair on SODA?
