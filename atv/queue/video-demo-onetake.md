# Vídeo demo do TaskGo com onetake

Filme curto (16:9) mostrando o fluxo: cliente pede → profissional aceita → paga com Pix → avalia.
Sem narração, só som sintetizado. UI refeita em HTML a partir do modo demo (`?demo=1`).

## Etapas
- [x] Instalar dependências Python do onetake (playwright, scipy, pillow, matplotlib, fonttools, brotli, opencv)
- [x] Escolher conceito: usuário pediu A (Um ponto azul) e B (Mergulho no bairro), 25 s cada
- [x] Beat sheet (ritmo + o que atravessa cada corte)
- [x] UI refeita a partir dos dados do modo demo (data.js, catalogo.js)
- [x] Composições `video/onetake/a-ponto.html` e `b-bairro.html` (kit.js compartilhado)
- [x] Stills → rascunho 1080p30 → verify_promo (A: PASS; B: renderizando)
- [x] Som (paleta sintetizada, `score.py`)
- [ ] Aprovação → render final
