# Filmes onetake da TaskGO (25 s cada, 16:9)

Dois filmes curtos do fluxo cliente pede → profissional aceita → Pix → avaliação, feitos com a skill
[onetake](https://github.com/feitangyuan/onetake). Cada quadro é uma função pura do tempo, desenhada em canvas.
Os dados na tela vêm do modo demonstração (`js/core/data.js`, `js/core/catalogo.js`).

| Arquivo | O que é |
|---|---|
| `a-ponto.html` | Filme A, "Um ponto azul" (fundo escuro): um ponto vira o pedido, os profissionais, o ✓, o QR do Pix, as estrelas e o símbolo |
| `b-bairro.html` | Filme B, "Mergulho no bairro" (fundo claro): um plano só, da cidade até o celular e de volta |
| `kit.js` | Canvas 1920×1080, desenho (texto, estrela, ✓, QR, dedo) e o contrato do render (`__seek`, `__meta`, `__events`) |
| `score.py` | Som sintetizado, lido dos `__events()` de cada filme |
| `simbolo.png` | Cópia de `assets/marca/taskgo-simbolo.png` |

## Preparar a pasta (uma vez)

`motion.js` e `font.js` não ficam no Git: o primeiro é da skill onetake (licença PolyForm Noncommercial, uso
não comercial), o segundo embute a Spline Sans (licença OFL). Gere os dois aqui:

```bash
cp ~/.claude/skills/onetake/lib/motion.js video/onetake/
python -c "import base64;b=base64.b64encode(open('app-android/app/src/main/res/font/spline_sans.ttf','rb').read()).decode();open('video/onetake/font.js','w').write(\"(function(){const s=document.createElement('style');s.textContent=\\\"@font-face{font-family:'Spline Sans';font-weight:300 700;src:url(data:font/ttf;base64,\"+b+\") format('truetype')}\\\";document.head.appendChild(s);})();\n\")"
```

Precisa de Python com `playwright` (Chromium), `numpy`, `scipy`, `Pillow`, `opencv-python` e de `ffmpeg`.

## Renderizar

```bash
S=~/.claude/skills/onetake/scripts
cd video/onetake
python score.py a-ponto.html a-som.wav
python $S/render.py "$(pwd)/a-ponto.html" --out a-ponto-rascunho.mp4 --sfx a-som.wav    # 1080p30, ~25 min neste PC
python $S/verify_promo.py a-ponto-rascunho.mp4 --comp "$(pwd)/a-ponto.html"
# versão final, só depois de aprovada: --final (3840×2160, 60 q/s)
```

Troque `a-ponto` por `b-bairro` para o filme B. Prévia ao vivo: abra `a-ponto.html?play` no navegador; um quadro
fixo: `a-ponto.html?t=12.5&hud`. Os MP4, WAV e quadros de conferência gerados ficam fora do Git (`.gitignore`).

O pipeline onetake é gratuito só para uso não comercial: serve para o TCC, não para propaganda paga.
