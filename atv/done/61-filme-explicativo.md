# 61 — Filme explicativo da TaskGO (40 s) para a banca de professores

Pedido: vídeo renderizado com som e legenda + código que renderiza de novo. Claude é o estúdio inteiro; decisões fracas vão para video/DECISIONS.md.
Público: banca de professores. Acreditam que é difícil achar trabalhador disponível e que não dá para confiar na palavra de qualquer um.
Depois de assistir: entendem o que a TaskGO oferece.

## Fluxo (9 etapas)
- [x] 1. Ideia em uma frase e resultado (DECISIONS.md, SOURCES.md)
- [x] 2. Tokens (de ./css)
- [x] 3. directions.html com 3–4 direções; vencedora e porquê
- [x] 4. Storyboard (folha de contato)
- [x] 5. Animatic e passada de tempo
- [x] 6. Montagem completa
- [x] 7. Som e legenda
- [x] 8. Verificação (quadro idêntico duas vezes, quadros a 390 px, sem som / só áudio, revisão por subagente, acessibilidade, antes/depois)
- [x] 9. Exportação: video/out/master_16x9.mp4, cut_9x16.mp4, cut_1x1.mp4, captions.srt, contact.png, código e README só com o que foi testado

Pasta: video/

## Situação (18/11/2026)
- Todas as etapas feitas e verificadas (ver video/README.md).
- PENDENTE do usuário: gravar as 10 falas (video/ROTEIRO.md) em video/narracao/voz/; hoje a narração é faixa-guia sintética. Depois: node render/render.cjs --fmt todos e a versão reduzida.
- Achado no render: Chromes em paralelo podiam sortear a mesma porta e misturar quadros; corrigido (porta 0 + marca de dono por trecho) e checagem de ordem por PSNR criada.

## Encerrado (19/11/2026)
- Narração final pela ElevenLabs (render/elevenlabs.cjs); vídeos de out/ renderizados com ela. video/README.md atualizado.
