# Tasks

## 1. Research
- [ ] 1.1 Find out whether the engine reports an outdated model (logs, `RecognitionSupport`, download callbacks) — check: findings written to design.md

## 2. Download offer
- [ ] 2.1 Dialog after picking a language without a model — check: pick a non-installed language on the phone
- [ ] 2.2 Progress via `ModelDownloadListener` (API 34), "scheduled" and "error" states — check: download a small model
- [ ] 2.3 "Update model" action if 1.1 finds no "outdated" state — check: the action triggers the download again
- [ ] 2.4 The keyboard's "model not installed" error leads to the settings — check: dictate in a language without a model
