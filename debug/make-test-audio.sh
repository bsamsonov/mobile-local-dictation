#!/usr/bin/env bash
# Synthesizes the test speech for the trace tool and for the keyboard's test-audio hook:
# debug/phrases-ru.txt ("<pause after, s>|<phrase>") -> build/test.pcm (raw 16 kHz, 16-bit, mono).
# Needs piper with a Russian voice (PIPER_VOICE) and sox.
#   adb push build/test.pcm /sdcard/Android/data/com.bvs.localdictation.trace/files/test.pcm   (trace tool)
#   adb push build/test.pcm /sdcard/Android/data/com.bvs.localdictation/files/test.pcm         (keyboard; delete after the test)
set -euo pipefail
cd "$(dirname "$0")/.."
VOICE="${PIPER_VOICE:-$HOME/.local/share/piper/voices/ru_RU-dmitri-medium.onnx}"
TMP="$(mktemp -d)"; trap 'rm -rf "$TMP"' EXIT
mkdir -p build
i=0; parts=()
while IFS='|' read -r pause text; do
    i=$((i + 1))
    echo "$text" | piper -m "$VOICE" -f "$TMP/p$i.wav" >/dev/null 2>&1
    sox "$TMP/p$i.wav" -r 16000 -c 1 -b 16 "$TMP/s$i.wav" pad 0 "$pause" 2>/dev/null
    parts+=("$TMP/s$i.wav")
done < debug/phrases-ru.txt
sox "${parts[@]}" -t raw -r 16000 -c 1 -b 16 -e signed build/test.pcm pad 1.0 0
echo "build/test.pcm: $(($(stat -c %s build/test.pcm) / 32000)) s"
