# Tasks

## 1. Language list
- [ ] 1.1 Get supported/installed/downloadable languages via `checkRecognitionSupport` — check: the list on the phone
- [ ] 1.2 Drop-down with state labels, strings in en and ru — check: en/ru screenshots
- [ ] 1.3 Fall back to the text field if the engine does not report support (Android < 13 or an error) — check: forced error

## 2. Default language
- [ ] 2.1 Derive the default language from the system locale — check: unit test of the selection logic
- [ ] 2.2 Keep an already saved language — check: updating over the current build keeps ru-RU
