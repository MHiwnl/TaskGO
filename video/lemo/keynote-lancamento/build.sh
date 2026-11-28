#!/bin/sh
# Monta o filme TaskGO nos dois formatos: sh build.sh
# Entradas: main.js, engine.js, fonts/, vo/ (narração já gerada), mix.py. Saídas: keynote-lancamento-16x9 e -9x16 (.mp4 e .srt).
set -e
HERE=$(cd "$(dirname "$0")" && pwd)
LIB=${LIB:-C:/Users/Mendes/lemo-opuscar}   # biblioteca lemo-opuscar (setup.sh a instala)
export LIB
[ -f "$LIB/core/render/video.mjs" ] || { echo "defina LIB com a pasta da biblioteca"; exit 1; }
PY=$LIB/.venv/Scripts/python.exe
[ -x "$PY" ] || PY=$LIB/.venv/bin/python
mkdir -p "$HERE/out"
cd "$LIB"
for fmt in 16x9 9x16; do
  case $fmt in 16x9) SIZE=1920x1080 ;; 9x16) SIZE=1080x1920 ;; esac
  node core/render/events.mjs "$HERE" --size $SIZE --out "$HERE/out/events-$fmt.json"
  node core/render/video.mjs "$HERE" --fps 24 --size $SIZE --workers 3 --out "$HERE/out/video-$fmt.mp4"
  "$PY" "$HERE/mix.py" "$HERE/out/events-$fmt.json" "$HERE/vo" "$HERE/out/mix-$fmt.wav" "$HERE/out/cues-$fmt.json"
  "$PY" core/render/srt.py "$HERE/out/cues-$fmt.json" "$HERE/keynote-lancamento-$fmt.srt"
  sh core/render/mux.sh "$HERE/out/video-$fmt.mp4" "$HERE/out/mix-$fmt.wav" "$HERE/keynote-lancamento-$fmt.mp4" 24 0
done
