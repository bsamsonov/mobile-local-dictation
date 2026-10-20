# Design: mixed ru/en input

The options are tried in this order on the phone, on the same set of phrases:

1. **Language switching** (API 34): `EXTRA_ENABLE_LANGUAGE_SWITCH` + `EXTRA_LANGUAGE_SWITCH_ALLOWED_LANGUAGES` = [ru-RU, en-US].
   Behavior depends on the engine; on-device support for this pair is not documented. Both models are needed.
   Risk: switching happens per phrase, not per word.
2. **Recognition biasing** (API 33): `EXTRA_BIASING_STRINGS` with the user's term list. Cheap; can only help with
   terms the Russian model is able to write.
3. **User dictionary in post-processing**: Cyrillic spelling to Latin (e.g. the Cyrillic spelling of "Kubernetes"
   becomes "Kubernetes"), editable in the settings. Deterministic fallback; needs a dictionary.

Decision rule: keep every option that measurably helps; option 3 is always added as the fallback.
Source: https://developer.android.com/reference/android/speech/RecognizerIntent
