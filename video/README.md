# Filme explicativo da TaskGO (40 s)

Para a banca de professores. Pergunta que o filme responde: "Como eu acho alguém disponível agora, e como sei que posso confiar?"

## Entrega

| Arquivo | O que é |
|---|---|
| `out/master_16x9.mp4` | Principal, 1920×1080, 60 q/s, 40,04 s, H.264 (crf 16, yuv420p) + AAC 48 kHz |
| `out/cut_9x16.mp4` | Recomposto para celular, 1080×1920 |
| `out/cut_1x1.mp4` | Recomposto quadrado, 1080×1080 |
| `out/master_16x9_movimento_reduzido.mp4` | Mesma sequência de ideias, sem deslocamentos (só aparece/some suave) |
| `captions.srt` (e `out/captions.srt`) | Legenda das 10 falas, em arquivo separado (os vídeos saem sem legenda gravada; `?legenda=1` no film.html mostra) |
| `contact.png` | Um quadro de cada cena, tirado do master |
| `directions.html` | As 4 direções visuais e a escolhida |
| `DECISIONS.md`, `SOURCES.md`, `ROTEIRO.md` | Decisões, fonte de cada número, roteiro de gravação |

> **Versão final:** narração pela ElevenLabs (`render/elevenlabs.cjs`, uma tomada só cortada nas 10 falas). Os vídeos em `out/` são os da banca.

## Trocar a narração e renderizar de novo

1. Gere as falas em `narracao/voz/` (`01`…`10`): pela ElevenLabs com `node render/elevenlabs.cjs` (chave e voz em `video/.env.elevenlabs`, fora do git; `--amostra` testa só a primeira fala), ou gravando as falas de `ROTEIRO.md` (`.m4a`, `.mp3`, `.wav`, `.ogg`…).
2. Na pasta `video/`:
   ```
   node render/render.cjs --fmt todos
   node render/render.cjs --fmt 16x9 --reduzido --sem-som
   ```
   O tempo de cada cena, a legenda e a trilha se ajustam à duração de cada fala. Leva uns 25 minutos.
3. Confira: `node render/checar-ordem.cjs master_16x9.mp4 16x9` e `node render/checar-audio.cjs`.

Precisa de Node 20+, Google Chrome e ffmpeg (`winget install Gyan.FFmpeg`).

## Como funciona

- `src/timeline.js`: o objeto TIMELINE (cenas, falas, efeitos), calculado a partir da duração das falas.
- `src/film.html` + `src/film.js`: `window.seek(t)` pinta o quadro no tempo t; cada quadro é função pura de t, e a aleatoriedade usa semente.
- `src/audio.js`: trilha procedural e efeitos (Web Audio, fora do tempo real), com o motivo de três notas nas verificações.
- `render/render.cjs`: narração → legenda → mixagem (duas passadas de loudnorm) → 4 Chromes sem janela, cada um num trecho, avançando t = n/60 e mandando PNG direto ao ffmpeg → junção sem recodificar → áudio.

## O que foi testado (e o resultado)

| Checagem | Resultado | Registro |
|---|---|---|
| Mesmo quadro renderizado duas vezes, em dois Chromes e em ordem inversa | 9 de 9 idênticos byte a byte | `verificacao/determinismo.txt` |
| Cada vídeo mostra o quadro certo em cada batida (PSNR contra render à parte) | 10 de 10 nos 4 vídeos (PSNR 32–42 dB) | `verificacao/ordem.txt` |
| Texto a 390 px de largura (corte 9:16), em cada batida | Menor texto 11 px; nada cortado | `verificacao/390.txt`, `verificacao/390/` |
| Volume do mix | -16,0 LUFS integrado; pico real -1,5 dBTP (medido também no MP4 entregue) | `verificacao/audio.txt`, `verificacao/volume-master.txt` |
| Trilha abaixa sob a voz | Desce 6,5 dB | `verificacao/audio.txt`, `verificacao/forma-de-onda.png` |
| Piscadas (mais de 25% da tela mudando de uma vez) | 0 nos 4 vídeos | `verificacao/piscadas.txt` |
| Contraste das cores do filme | Todos os pares de texto ≥ 4,5:1 (tinta 15,9:1; branco sobre azul 5,3:1) | `render/contraste.cjs` |
| Erros de JavaScript durante o render | Nenhum (o render para se houver) | — |
| Revisão de design por subagente (perguntas de pra quem é, o que comunica, nome ou invisível) | 6 correções aplicadas, com antes e depois | `DECISIONS.md` (etapa 8), `verificacao/antes-depois-*.png` |

## O que não foi testado

- Ninguém assistiu ao filme com som de ponta a ponta: "sem som" foi conferido pelos quadros (folhas de contato) e "só o áudio" por medição e forma de onda, não por escuta.
- `verificacao/volume-master.txt`, `piscadas.txt` e `determinismo.txt` são da faixa-guia; `audio.txt`, `ordem.txt` e `390.txt` foram refeitos com a narração da ElevenLabs.
- Leitores de tela e players específicos (projetor da sala, WhatsApp, Instagram) não foram testados.
