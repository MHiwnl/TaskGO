#!/bin/sh
set -e
cd "C:/Users/Mendes/lemo-opuscar"
node core/render/video.mjs "C:/Users/Mendes/Documents/Inter-atualizado/video/lemo/mesa-do-pedido" --fps 30 --workers 3 --q 'n=8' --out "C:/Users/Mendes/Documents/Inter-atualizado/video/lemo/mesa-do-pedido/out/video16.mp4"
sh core/render/mux.sh "C:/Users/Mendes/Documents/Inter-atualizado/video/lemo/mesa-do-pedido/out/video16.mp4" "C:/Users/Mendes/Documents/Inter-atualizado/video/lemo/mesa-do-pedido/out/mix.wav" "C:/Users/Mendes/Documents/Inter-atualizado/video/lemo/mesa-do-pedido/mesa-do-pedido-16x9.mp4" 30
node core/render/video.mjs "C:/Users/Mendes/Documents/Inter-atualizado/video/lemo/mesa-do-pedido" --fps 30 --workers 3 --q 'n=8' --size 1080x1920 --out "C:/Users/Mendes/Documents/Inter-atualizado/video/lemo/mesa-do-pedido/out/video9.mp4"
sh core/render/mux.sh "C:/Users/Mendes/Documents/Inter-atualizado/video/lemo/mesa-do-pedido/out/video9.mp4" "C:/Users/Mendes/Documents/Inter-atualizado/video/lemo/mesa-do-pedido/out/mix.wav" "C:/Users/Mendes/Documents/Inter-atualizado/video/lemo/mesa-do-pedido/mesa-do-pedido-9x16.mp4" 30
echo FIM
