#!/bin/sh
# Palito: como a TaskGO funciona. Gera os dois filmes (16:9 e 9:16) a partir do roteiro, da página e dos sons.
# Uso: sh build.sh   (LIB = pasta da biblioteca lemo-opuscar; padrão abaixo)
set -e
HERE=$(cd "$(dirname "$0")" && pwd)
LIB=${LIB:-C:/Users/Mendes/lemo-opuscar}
export LIB
cd "$LIB"
[ -f "$LIB/core/render/video.mjs" ] || { echo "defina LIB com a pasta da biblioteca"; exit 1; }
PY="$LIB/.venv/Scripts/python.exe"; [ -x "$PY" ] || PY="$LIB/.venv/bin/python"
mkdir -p "$HERE/out"

for f in h v; do
  if [ "$f" = h ]; then SIZE=1920x1080; NAME=16x9; LINES=lines.json; else SIZE=1080x1920; NAME=9x16; LINES=lines_9x16.json; fi
  node core/render/events.mjs "$HERE" --size $SIZE --out "$HERE/events-$f.json"
  "$PY" "$HERE/mix.py" $f "$HERE" "$LIB"
  node core/render/video.mjs "$HERE" --fps 24 --workers 3 --size $SIZE --out "$HERE/out/video-$f.mp4"
  sh core/render/mux.sh "$HERE/out/video-$f.mp4" "$HERE/out/mix-$f.wav" "$HERE/palito-como-funciona-$NAME.mp4" 24 0
  node "$HERE/cues.cjs" "$HERE/$LINES" "$HERE/out/cues-$f.json"
  "$PY" core/render/srt.py "$HERE/out/cues-$f.json" "$HERE/subtitles_pt-$NAME.srt"
done
echo "pronto"
