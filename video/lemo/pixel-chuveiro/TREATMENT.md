# TREATMENT: Chuveiro (TaskGO · estilo 2.5D Pixel Art, slug hd-2d)

Fase 1: tratamento, roteiro e prova do visual. Sem voz gerada, sem filme completo.
Versões: 16:9 1920×1080, 57,1 s (20 compassos) · 9:16 1080×1920, 31,4 s (11 compassos, corte da mesma história).

## 0. Decisões de briefing (sem perguntas, pelo BRIEF.md e AGENTS.md)

- Língua: português do Brasil em tudo (narração, legendas, falas, cartões). Uma linha só, no lugar da linha de cima do STYLE.md.
- Sem logotipos de terceiros (nem o logo do Pix). Sem pessoas reais: personagens desenhados em código.
- Estilo sem ✦: não precisa de gerador de imagem. Nada de "HD-2D" como título ou marca no filme.
- Narração: a voz é gerada depois pelo diretor (ElevenLabs). Aqui só o texto em `lines.json`.
- Números da tela são ilustrativos (R$ 180,00; código 4719).

## 1. Três estruturas candidatas e a escolhida

1. **Jornada linear**: casa escura → pedido → Carlos atravessa o bairro (travelling lateral) → conserto → pagamento. Simples e clara, mas não mostra o bairro como lugar e não tem a janela como eixo.
2. **Antes e depois numa janela**: uma só janela, apagada e acesa, com foco que passa da sombra para a luz. Forte no começo e no fim, mas fina demais para 57 s: sobra miolo sem assunto.
3. **Mapa de bairro (escolhida)**: a viagem de Carlos vira uma rota num mapa visto de cima, como na tela de acompanhamento do app; depois o mapa desce para a rua em 3/4 e a história segue com a câmera. Usa a gramática de RPG do estilo (cartão de capítulo, mapa, caixa de diálogo), a faixa de nitidez como tira no mapa e o eco de abertura na janela.

**Escolha: a 3.** Ela carrega o produto (o acompanhamento no mapa), dá escala ao bairro e permite o eco final, que é o pedido do briefing.

## 2. Logline e arco

**Logline:** numa noite de chuva no Recife, o chuveiro da dona Socorro queima, e o eletricista Carlos atravessa o bairro molhado para trazer a luz de volta.

**Arco:**
- **Preparação:** a janela da casa acende, uma faísca, tudo apaga. Socorro pede um profissional pelo celular.
- **Virada:** o caminho do mapa, a ponte, a parada no portão. Carlos mostra o código de 4 dígitos (confiança), acha o defeito e religa o disjuntor.
- **Fim:** a luz volta, o vapor sobe do banho quente, ela paga pelo Pix e dá cinco estrelas. A câmera sobe até o mesmo enquadramento da abertura: a mesma janela, agora acesa.

## 3. Benchmark (DIRECTOR.md §2)

- **Octopath Traveler** (só a gramática, já descrita no STYLE.md): câmera alta em 3/4, faixa de nitidez, caixa de diálogo emoldurada. Não levar: personagens, lugares, interface, música.
- **Blade Runner 2049** (cinema): chuva em rua noturna, poucas luzes práticas quentes em interior escuro, travelling lateral longo com primeiro plano borrado. Não levar: a paleta azul-laranja, o néon, o tom de distopia, a trilha.
- **O que aprender:** ritmo das pausas (silêncio antes do pico), o pico de luz como o maior contraste da peça.
- **O que não levar:** melodias de qualquer obra existente. A música é original.

## 4. Lista de planos (16:9, 57,1 s)

| # | Tempo (s) | Plano | Câmera | Por quê |
|---|---|---|---|---|
| S01 | 0,00–2,86 | Janela amarela de uma casa na rua, chuva, um só alvo | Muito alto e longe, empurrão lento; banda de nitidez em uma casa | Onde estamos, escala. Gancho: luz e chuva no primeiro quadro |
| S02 | 2,86–5,71 | Inserto da janela: faísca "pá" em 2,86 e a janela apaga | Corte seco, plano fechado do vidro | Gancho dos 3 s. Cartão CAPÍTULO 1 em 3,4–5,6 |
| S03 | 5,71–11,70 | Dona Socorro na porta do banheiro; a tela do celular é a única luz | Médio-alto 3/4, quase parado | A luz é o assunto. Ela espera no escuro |
| S04 | 11,70–14,29 | Tela do celular: cartão do profissional (Carlos Andrade · Eletricista · ★ 4,9 · a caminho) | Próximo, empurrão lento | O pedido pelo app, em tela |
| S05 | 14,29–16,29 | Cartão CAPÍTULO 2 · O CAMINHO sobre fundo escuro | Fixo | Gramática de RPG; separa a casa da viagem |
| S06 | 16,29–19,57 | Mapa do bairro: ruas em pixel, o rio, a rota tracejada da casa até a base de Carlos; o marcador anda | De cima, reto; banda de nitidez como tira | Um plano, uma rota. Forma da candidata 3 |
| S07 | 19,57–21,60 | Descida de cima para 3/4; em 20,00 o marcador vira Carlos (sprite) na rua | Tilt lento | Troca de escala: mapa para rua |
| S08 | 21,60–26,90 | Carlos caminha de capa azul pela chuva; postes acendem sobre ele; ponte sobre o rio | Travelling lateral, câmera um pouco à frente; chuva borrada em primeiro plano | O caminho pelo bairro |
| S09 | 26,90–28,57 | Carlos para no portão | Mergulho baixo e perto | Cuidado; o mundo de brinquedo fica pessoal |
| S10 | 28,57–34,29 | Carlos mostra o celular com o código 4719 a Socorro (troca de pose em 31,43) | Médio 3/4, duas figuras | Confiança: o código é a prova |
| S11 | 34,29–36,50 | Carlos em plano médio; caixa de diálogo "Confere no seu app: 4719." | Parado | Fala na tela, digitada sobre a voz |
| S12 | 36,50–40,30 | Carlos vai ao quadro de luz no corredor; a mão no disjuntor | Travelling curto; rack focus do disjuntor (perto) para a porta do banheiro (longe) | A atenção muda de mão |
| S13 | 40,30–42,86 | Plano largo travado: Carlos e o disjuntor; só chuva | Travado, sem movimento | Silêncio antes do pico |
| S14 | 42,86–47,40 | A lâmpada do banheiro acende em 42,86; as sombras de Socorro e Carlos giram no chão; vapor do chuveiro | Puxada lenta; o foco passa de Carlos para a lâmpada | Pico. Movimento nativo: "sombras giram quando a luz acende" |
| S15 | 46,00–52,14 | Cartão do Pix: "Pagar com Pix · R$ 180,00 · Pago"; depois as cinco estrelas acendem | Próximo, parado | Pagamento e avaliação |
| S16 | 52,20–55,00 | Puxada alta e longe até o enquadramento de S01: a mesma janela, acesa, com vapor | Puxada, a mesma moldura | Eco da abertura |
| S17 | 55,00–57,14 | Cartão de título TaskGO sobre o mesmo quadro escurecido | Fixo | Fecha a marca |

## 5. Roteiro segundo segundo (16:9)

| Tempo | Acontece | Narração / legenda | Som | Música |
|---|---|---|---|---|
| 0,00 | Janela acesa, chuva, plano alto | (sem voz) | Chuva no telhado; gota | Rabeca sozinha, nota longa |
| 2,86 | "pá": faísca, a janela apaga (corte seco) | (sem voz) | Estalo elétrico, pancada seca | Silêncio: a rabeca some na cauda de reverb |
| 3,57 | Uma batida de alfaia, como um coração | (sem voz) | Batida grave | Uma batida só |
| 3,40–5,60 | Cartão CAPÍTULO 1 · A CASA ESCURA | título | Sino curto na entrada do cartão | Silêncio com chuva |
| 5,90–11,70 | Socorro no escuro; a tela do celular ilumina o rosto | l01 | Chuva na janela; passos no piso molhado | Rabeca em nota longa |
| 12,00 | Cartão do profissional no celular | l02 | Sino curto de notificação (UI) | Rabeca |
| 14,29–16,29 | CAPÍTULO 2 · O CAMINHO | título | Chuva | Entra o ritmo de maracatu: alfaia e caixa |
| 16,60–21,60 | Mapa: a rota tracejada; o marcador começa a andar | l03 | Sino fino a cada trecho da rota | Ritmo: caixa e alfaia |
| 20,00 | Marcador vira Carlos na rua | (sem voz) | Pancada de alfaia no tempo forte | Pancada |
| 21,90–26,90 | Carlos atravessa a ponte; poças a cada passo | l04 | Passos em pedra molhada; respingo de poça em 22,86 | Rabeca entra com a melodia A |
| 26,90–28,57 | Portão; Carlos para | (sem voz) | Chuva mais forte; grade | Rabeca |
| 28,90–34,30 | Código mostrado | l05 | Respiração; chuva | Melodia A rarefeita a partir de 34,29 |
| 34,60–36,50 | Caixa de diálogo: "Confere no seu app: 4719." | f01 | Sino do UI na caixa | Só rabeca |
| 36,50–40,30 | Corredor, quadro de luz | l06 | Passos no piso; metal do quadro | Rabeca e pulso leve |
| 40,30–42,86 | Silêncio: a mão no disjuntor | (sem voz) | Só chuva | Nada, só a cauda |
| 42,86 | Clique do disjuntor; a lâmpada acende; sombras giram | (sem voz) | Clique seco; sino (ding) de algo que liga; zumbido de lâmpada | Entra o tema completo (rabeca em oitava acima; alfaia; gonguê) |
| 43,20–47,40 | Vapor sobe do banho quente | l07 | Chiado de vapor; água caindo | Tema |
| 46,00–48,50 | Cartão do Pix: "Pago" | (sem voz) | Sino do UI | Tema |
| 47,70–50,80 | Pagou, deu cinco estrelas | l08 | Cada estrela: um tique seco | Tema |
| 49,29; 50,00; 50,71; 51,43; 52,14 | Cinco estrelas acendem, uma a uma | (sem voz) | Tique por estrela | Cada tique cai numa batida |
| 52,60–54,90 | Puxada até a janela; a mesma moldura de S01 | l09 | Chuva voltando; vapor | Tema recua |
| 55,00 | Cartão TaskGO | título | Acorde final (sino) | Acorde final de rabeca, cauda de reverb |
| 57,14 | Fim | | | |

## 6. Cue map (música: 84 BPM, 4/4; compasso = 2,857 s; tempos em 0,714 s)

Tempos fortes (compassos): 0,00 · 2,86 · 5,71 · 8,57 · 11,43 · 14,29 · 17,14 · 20,00 · 22,86 · 25,71 · 28,57 · 31,43 · 34,29 · 37,14 · 40,00 · 42,86 · 45,71 · 48,57 · 51,43 · 54,29 · 57,14.

**Seções:**
- **A, gancho (0–2,86):** rabeca sozinha, nota longa.
- **B, casa escura (2,86–14,29):** silêncio do "pá", uma batida em 3,57, depois só rabeca e chuva.
- **C, mapa (14,29–21,6):** entra o maracatu (alfaia, caixa); a cada compasso, o pulso sobe.
- **D, caminho (21,6–28,57):** groove cheio; melodia A na rabeca.
- **E, código e corredor (28,57–40,30):** melodia A rarefeita em 34,29; só rabeca em 34,6–36,5.
- **F, silêncio (40,30–42,86):** nada.
- **G, luz (42,86–52,14):** tema completo (melodia B, a melodia A em oitava acima); cinco tiques nas estrelas caem nas batidas.
- **H, eco (52,14–57,14):** a melodia A volta, cadência final em 55,00 (cartão de título), cauda até 57,14.

**Acertos principais (alvo 0 ms):** 2,86 (faísca + corte) · 20,00 (marcador vira Carlos) · 31,43 (troca de pose) · 42,86 (disjuntor + lâmpada + sombras + tema) · 49,29–52,14 (estrelas) · 55,00 (título).

## 7. Tabela de som (por seção)

| Seção | Ambiente | Foley principal | Música |
|---|---|---|---|
| A (0–2,86) | Chuva forte no telhado | gota; faísca "pá" em 2,86 | rabeca, nota longa |
| B (2,86–14,29) | Chuva na janela, geladeira desligada, pingo na pia | passos no piso molhado; tela do celular (tique) | rabeca; alfaia só em 3,57 |
| C (14,29–21,6) | Chuva e trânsito distante de água | sino a cada trecho da rota; passos | maracatu entra |
| D (21,6–28,57) | Rio embaixo da ponte; chuva borrada | passos em pedra; respingo de poça (22,86) | groove cheio; melodia A |
| E (28,57–40,30) | Chuva; grade do portão | fechadura; caixa de diálogo (sino curto) | rarefeita; só rabeca 34,6–36,5 |
| F (40,30–42,86) | Só chuva | nada além de um respiro | silêncio |
| G (42,86–52,14) | Zumbido de lâmpada; chiado de vapor | clique do disjuntor (42,86); sino de ligar; tiques das estrelas | tema completo |
| H (52,14–57,14) | Chuva baixando | sino do título | acorde final |

**Mixagem:** narração comprimida e com RMS igualado; a música abaixa 10 dB (só para baixo) sob a voz; chuva −20 dB sob a voz; −14 LUFS no `mux.sh`; grão 0.

## 8. Legendas e títulos

- **Narração:** legenda em Cormorant Garamond itálico, ~48 px em 1080p, centrada e baixa, sobre faixa escura em gradiente. Uma linha por trecho, aparece 0,35 s antes da voz. Fonte da legenda: `lines.json` (o `.srt` sai dele).
- **Falas na tela (f01):** caixa de diálogo: placa escura, borda fina de metal, cantos ornamentados, nome na borda de cima ("Carlos"), texto digitado em ~90% da fala, cursor piscando, sino na entrada.
- **Cartões de capítulo** (CAPÍTULO 1 · A CASA ESCURA; CAPÍTULO 2 · O CAMINHO): Cinzel, caixa alta, espaçamento largo. Mantidos ≥ 1,8 s (têm 2,0–2,2 s).
- **Título final:** "TaskGO" em Cinzel; "GO" em azul #2b59ff; uma linha de apoio "Serviços locais em Recife" em Cormorant. Mantido ≥ 1,8 s (2,14 s).
- **Texto de interface** (cartão do celular, Pix, estrelas): pixel, em um único metal (ouro) e papel (branco) sobre placa escura.
- **Fontes (OFL):** Cinzel e Cormorant Garamond, baixadas para `fonts/` do projeto (não linkar do Google em tempo de render).

## 9. Personagens e paleta (para a folha de personagens)

- **Dona Socorro:** ~60 anos, cabelo grisalho em coque baixo, robe de algodão rosa-empoeirado (cor pouco saturada, não usada nos cenários), chinelo. Altura do sprite ~44 px no canvas.
- **Carlos Andrade:** ~35 anos, eletricista. Capa de chuva azul #2b59ff: única cor saturada de figura; nenhum cenário usa azul saturado (a névoa é cinza-verde). Calça escura, boné, caixa de ferramentas de metal. Altura ~48 px.
- **Poses:** Carlos: caminhar (4 quadros), parado, mostrando o celular, mão no disjuntor. Socorro: parada com toalha, sorrindo (vapor).
- **Expressões (3):** neutra; preocupada (sobrancelha); aliviada (olhos fechados).
- **Cenários:**
  - Rua: névoa cinza-verde #2d3d42, luz-chave fria pálida, práticos âmbar (postes, janelas).
  - Casa: luz-chave pálida e baixa; prático: lâmpada do banheiro quente, e depois âmbar dominante.
  - Ponte: água escura com colunas de luz dos postes.
  - Mapa: superfície plana de tiles, mesma paleta da rua.
- **Sombra quente:** a tinta mais escura é tinta quente, nunca preto puro.

## 10. Corte 9:16 (1080×1920, 31,4 s, 11 compassos)

A mesma história, enquadrada na vertical (rua em altura, ponte como faixa vertical, legenda na faixa inferior). Não há cartão de capítulo nem mapa; a viagem é contada com travelling vertical. Gancho: a faísca em 2,86 s (dentro dos 3 s).

| # | Tempo | Acontece | Texto |
|---|---|---|---|
| V01 | 0,00–2,86 | Janela acesa, chuva | (sem voz) |
| V02 | 2,86–3,30 | "pá", corte seco | (sem voz) |
| V03 | 3,30–5,60 | Cartão do profissional no celular | l02 |
| V04 | 5,60–11,40 | Carlos sai, rua acima; ponte; respingos em 8,57 e 11,43 | l04 (versão curta: "Atravessou a ponte e a chuva não deu trégua.") |
| V05 | 11,40–14,30 | Portão; mergulho baixo em 12,50 | f01 em 12,90–14,80 |
| V06 | 14,30–18,40 | Corredor, quadro de luz | l06 em 14,60–18,40 |
| V07 | 18,40–20,00 | Silêncio; mão no disjuntor | (sem voz) |
| V08 | 20,00–22,86 | Lâmpada acende em 20,00; sombras giram; vapor | (sem voz) |
| V09 | 22,86–26,50 | Cartão do Pix em 23,00; estrelas em 23,57; 24,29; 25,00; 25,71; 26,43 | l08 em 23,40–26,50 |
| V10 | 27,00–29,30 | Puxada até a janela (eco de V01) | l09 em 27,00–29,30 |
| V11 | 29,29–31,43 | Cartão TaskGO | título (acorde em 29,29) |

**Por que a vertical não tem l01, l03, l05 e l07:** cabem no tempo de 31 s só as falas que levam a história sem a parte do mapa. A parte "fez o código" continua na caixa de diálogo.

## 11. Textos de fala e narração (ver `lines.json`)

Narração em pt-BR, frases curtas, sem tom de propaganda. Voz para a fase 2: narrador de livro ilustrado, lento (velocidade ~0,82–0,90). Nomes inventados (Socorro, Carlos, Casa Amarela) entram no campo `asr` da TTS.

## 12. Riscos e o que ainda falta (fase 2)

- **Voz:** ElevenLabs (diretor). Não gerada aqui.
- **Música:** partitura original (rabeca, alfaia, caixa, gonguê). Não há amostras nesta fase.
- **Fontes:** baixar Cinzel e Cormorant Garamond (ver fase 1, passo 4).
- **Animação:** caminhada em 4 quadros a 8 fps; sprites passo a passo; câmera e luz suaves a 60 fps.
- **Ponte e água:** shader de água com colunas de luz; é o trecho mais caro em GPU.
- **Vertical:** a versão 9:16 precisa de reenquadramento próprio (não é só um corte).
- **Não validado ainda:** a legibilidade (readcheck) e o pico de luz (contraste).
