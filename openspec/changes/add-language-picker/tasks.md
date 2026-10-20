# Tasks

## 1. Language list
- [x] 1.1 Get supported/installed/downloadable languages via `checkRecognitionSupport` — check: the list on the phone
- [x] 1.2 Drop-down with state markers, strings in en and ru — check: screenshot on the phone
- [x] 1.3 Fall back to the text field if the engine does not report support (Android < 13 or an error) — check: code review
- [x] 1.4 List logic in pure Java (`Languages`) with unit tests — check: `./build.sh test`

## 2. Default and saved language
- [x] 2.1 Default `en-US` until the user picks a language — check: code review
- [x] 2.2 Only a user pick (touch) is saved; selection events from setting up the list are ignored — check: ru-RU kept after a rebuild on the phone
