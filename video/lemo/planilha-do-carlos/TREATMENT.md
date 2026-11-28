# Planilha do Carlos: tratamento (estilo spreadsheet, slug `spreadsheet`)

Versão 16:9 1920×1080, 45–60 s (alvo 52 s). Corte 9:16 1080×1920, ~30 s, mesma história, outro enquadramento.
Idioma: português do Brasil (narração, legenda, texto na tela). Fórmulas em sintaxe pt-BR de planilha (`;` como separador).
Dados: `dados-planilha.csv` (fictícios, ilustrativos). Narração: `lines.json` (16:9) e `lines-9x16.json` (vertical).

## 1. Três estruturas candidatas

1. **Lista que se vira (escolhida).** A planilha conta os problemas coluna por coluna: vazios em "Pagou?", uma coluna escondida que muda o total, horários que batem. A virada é a TaskGO: pedidos chegam como linhas novas já com bairro, preço e horário, e as linhas se reorganizam pela distância até a casa dele.
2. **Antes e depois.** Duas planilhas lado a lado que não batem até uma fórmula ser corrigida. Descartada: a virada acontece fora da planilha dele, e a TaskGO vira um terceiro lugar. O filme perde a ideia de que é a mesma planilha, só que organizada.
3. **Contagem regressiva.** Um calendário de 31 dias que vai se preenchendo. Descartada: mostra agenda cheia, mas não mostra o problema do caixa (calote, deslocamento) que o briefing põe no centro.

**Escolha:** a estrutura 1. Motivo: o problema é que a planilha esconde o dinheiro. Só a própria planilha pode revelar isso, por fórmula e coluna escondida, e a virada cabe nela mesma.

**Abertura:** pull out a partir de uma célula de fórmula (um resultado "7" pop) e não um logo. Os três primeiros segundos já mostram a conta.
**Final:** merged cell como palco (cartão final "TaskGO") precedido de contagem que sobe de 13 para 21.
**Movimento nativo do filme:** coluna escondida (a virada de informação) com um gag `####` ao reabrir; cells that move (o clímax, com a TaskGO) .

**Regra do briefing (vale para o filme todo):** nada de relógio, cronômetro, timer, contagem de tempo, anel de tempo, mostrador de horas ou "00:14" na tela. A coluna Horário é dado da planilha (08:00, 14:00) e pode aparecer; a passagem de tempo se mostra por ação e luz. A legenda "Linhas novas" e a contagem de células são contagens de linhas e células, não de tempo.

## 2. Logline e arco

**Logline:** Carlos, eletricista em Recife, controla caixa e agenda numa planilha que esconde dois mil e quatrocentos reais parados e um gasto de deslocamento que ele nunca somava; quando os pedidos passam a chegar organizados pela TaskGO, a própria planilha se reorganiza perto de casa.

- **Setup:** a planilha mostra o caos (7 pagamentos em branco, R$ 2.450 a receber, horário duplicado às 14:00 do dia 12/03).
- **Turno:** a coluna escondida "Deslocamento" (R$ 944) reaparece e o total muda de R$ 6.210 para R$ 5.266.
- **Fim:** oito pedidos novos chegam já com local, preço e horário; a planilha ordena por distância e a contagem de notas 5 vai de 13 para 21.

## 3. Benchmark

- **Aprender:** a gramática de células (um hop por célula, a varredura de formatação condicional a partir de uma célula, a contagem na barra de status) e o ritmo de digitação da fórmula com Enter como corte seco. Referência de gênero: videoclipes e campanhas montados dentro de um arquivo de planilha real (gramática só, nada de produto).
- **Não pegar:** canções, plano, cores de marca e fontes de terceiros; a interface é desenhada à mão e genérica (sem verde Excel nem nome de produto).

## 4. Dados da planilha (ilustrativos, coerentes com `dados-planilha.csv`)

Aba "Controle 2026". Colunas A–I: Data, Cliente, Bairro, Serviço, Valor, Pagou?, Deslocamento (**G, oculta**), Horário, Nota.

| Conta | Valor | Fonte |
|---|---|---|
| Serviços de março (linhas 2–25) | 24 | CSV |
| "Pagou?" em branco | 7 | `=CONTAR.VAZIO(F2:F25)` |
| A receber (em branco) | R$ 2.450 | `=SOMASE(F2:F25;"";E2:E25)` |
| Bruto de março | R$ 6.210 | `=SOMA(E2:E25)` |
| Deslocamento (oculto) | R$ 944 | `=SOMA(G2:G25)` |
| Sobra depois do deslocamento | R$ 5.266 | `=SOMA(E2:E25)-SOMA(G2:G25)` |
| Conflito de horário | 12/03 às 14:00 (Clínica Vida e Mariana Teles) | âmbar |
| Notas 5 antes dos pedidos novos | 13 | `=CONT.SE(I2:I25;5)` |
| Pedidos novos da TaskGO (linhas 26–33) | 8 | CSV |
| Notas 5 depois | 21 | `=CONT.SE(I2:I33;5)` |

Atenção: os números dos pedidos novos não batem com o total de março porque são de abril. Na tela, o total final é do conjunto (linhas 2–33), e a legenda deve dizer "abril" ao mostrar as linhas novas.

## 5. Plano (beat sheet 16:9, segundo a segundo)

Rascunho de tempos. A linha do tempo que vale é a do `index.html` (`SUBS`, `SEL`, `FORM`, `CAMK`, `SWEEP0`, `LAND0`): a virada entra em 29,8 s, depois da narração longa, e a tabela abaixo ainda não reflete isso. Fase 2 alinha tudo à narração final.

| Tempo | Plano | O que acontece | Por quê |
|---|---|---|---|
| 0,0–1,6 | Close, cell de resumo (Insert) | caret piscando; `=CONTAR.VAZIO(F2:F25)` digitado (~1,2 s), Enter corta; "7" pop +30% | gancho em 3 s: a conta em uma célula |
| 1,6–5,0 | Pull out de célula para planilha inteira | varredura vermelha dos vazios em "Pagou?", a partir da célula de resultado; barra de status conta "7" | a parte vira o todo; o dado vira imagem |
| 5,0–8,0 | Plano largo travado | a planilha inteira com o padrão vermelho; mantém 2,5 s | julgamento visual: são vazios reais |
| 8,0–11,0 | Segue a célula de resultado | `=SOMASE(F2:F25;"";E2:E25)` → R$ 2.450; caixas de referência nas duas colunas (mesmo matiz) | o dinheiro parado é o problema |
| 11,0–14,0 | Empurra para a célula do total | `=SOMA(E2:E25)` → R$ 6.210 | o que ele vê |
| 14,0–17,0 | Cabeçalhos (whip) | letras pulam de F para H, linha dupla marca a dobra; "Mostrar" reabre G: `##`, depois valores | pista de estrutura (coluna escondida) e gag `####` |
| 17,0–20,0 | Segue a célula | `=SOMA(E2:E25)-SOMA(G2:G25)` → R$ 5.266 pop | a virada: o total muda |
| 20,0–22,5 | Trilha ao longo de uma linha | 12/03: as duas células de 14:00 em âmbar | horário batendo |
| 22,5–24,0 | Corte de música (silêncio), fica só o foley | a planilha para | silêncio antes da virada |
| 24,0–30,0 | Virada: linhas chegam de baixo | oito linhas novas (abril) voam para o lugar, cada uma com bairro, preço, horário, "Sim" em Pagou?; blips por célula | TaskGO entra na planilha |
| 30,0–36,0 | Cells that move | ordenação por Deslocamento: as linhas perto de Madalena sobem; barras de células crescem ao pousar | "agenda perto de casa" |
| 36,0–42,0 | Segue a célula de resultado | `=CONT.SE(I2:I33;5)` → 21 pop (antes 13) | avaliações sobem, número legível |
| 42,0–47,0 | Merged cell como palco | cartão final "TaskGO" (GO em azul de marca), linha: "Serviço perto de casa, com preço combinado." | fechamento |
| 47,0–52,0 | Plano largo travado | cartão final sustentado; sem fade | o filme termina na planilha |

Cenas que o corte deve respeitar: a legenda de subtítulo não cobre a fórmula nem o número; o número narrado precisa ter ≥ 26 px na tela.

## 6. Versão 9:16 (~30 s, corte da mesma história)

Enquadra colunas A–I em largura reduzida e linhas em altura; a planilha vertical combina com lista longa.

| Tempo | Plano | Obs. |
|---|---|---|
| 0,0–3,0 | Gancho: `=CONTAR.VAZIO(F2:F25)` e "7" | a legenda de gancho aparece em 0,4 s |
| 3,0–8,0 | Pull out + varredura vermelha, "7" ocupa o topo | |
| 8,0–14,0 | Coluna G reaberta, `####` → valores, total 5.266 | |
| 14,0–21,0 | Oito linhas da TaskGO sobem de baixo; ordenação por distância | |
| 21,0–26,0 | `=CONT.SE(I2:I33;5)` → 21 | |
| 26,0–30,0 | Cartão final "TaskGO" | |

## 7. Narração (estimativa 2,6 palavras/s; ver `lines.json`)

Narração 16:9: 11 linhas, 121 palavras, ~46,5 s de fala só pelo critério de 2,6 palavras/s; com pausas entre linhas (~0,4 s cada) ≈ 50–52 s. Linha 1 entra em 0,4 s, depois do corte da primeira fórmula. Os blocos de fala e os planos da tabela se sobrepõem de propósito: a fala acompanha a tela, não o contrário.

## 8. Partitura (cue map)

- **Andamento:** 96 BPM, compasso 4/4 (1 compasso = 2,5 s). Não muda o andamento para acelerar: engrossa o padrão.
- **0,0–22,5 s:** marimba e kit em meio-tempo; piano curto a cada clique de Enter.
- **22,5–24,0 s:** corte de música (silêncio); fica só o foley. A primeira nota depois é a do pedido novo chegando (24,0).
- **24,0–36,0 s:** baixo elétrico e stabs de piano, quatro no chão nos pedidos novos.
- **36,0–42,0 s:** o "ding" da contagem (21) cai no tempo 1 do compasso.
- **42,0–52,0 s:** cartão final, só o último acorde de xilofone, depois silêncio com a última palavra.

## 9. Mesa de som

| Seção | Fundo | Foley principal | Música |
|---|---|---|---|
| 0–1,6 s | nenhum | clique de tecla por caractere; clack + thump no Enter | nenhuma |
| 1,6–17 s | nenhum | blip por célula pintada (tom subindo); tique do hop | marimba e kit, meio-tempo |
| 17–24 s | nenhum | ratchet na coluna reaberta; clique do mouse | marimba |
| 24–36 s | nenhum | blip por célula que pousa (tom pela altura); tique de cada linha | baixo e piano |
| 36–52 s | nenhum | "ding" na contagem; pop no cartão final | xilofone sozinho no fim |

Mix: voz 10 dB acima da música por RMS; música cortada em 300 Hz–4 kHz sob a fala; −14 LUFS; pico real ≤ −1 dB.

## 10. Legendas e títulos

- Uma linha só, em português, no lugar da linha de cima do estilo (o estilo tem chinês em cima; aqui não há). Nota de célula amarelo-pálida, borda de tinta, triângulo vermelho no canto, canto inferior esquerdo, sem quebra de linha.
- Mantém cada legenda por ≥ máx(1,8 s; fala + 0,6 s). Lista de legendas em `planilha-do-carlos.srt` (gerado depois).
- Título final: merged cell com "Task" em tinta e "GO" em azul da marca (#2b59ff). Única exceção de cor fora da paleta de regra; o azul vira a marca só no cartão final.

## 11. Paleta e tipografia

- Vermelho `#e8382d`: "Pagou?" em branco (regra de vazio).
- Índigo `#2a2f6c`: linhas novas da TaskGO.
- Âmbar `#ffb21f`: horário batendo.
- Lima `#b8f25a`: notas 5 (contagem final).
- Tinta `#17120a`; shell `#dcdbd5` / `#f1f0eb`; célula branca.
- Fonte: Inter (OFL) para a interface; subtítulos na mesma fonte.

## 12. Riscos e pendências

- **Pagamento (corrigido na fase 2):** o cliente paga o profissional direto (Pix ou dinheiro) quando o serviço termina; só a taxa da TaskGO passa pela plataforma. O filme não diz que o dinheiro cai pelo app nem que a TaskGO segura o pagamento. "Pagou?" fica preenchido porque o valor já vem combinado. (texto antigo sobre confirmação com o produto removido) ou Pix pelo app). Se não valer, trocar a frase em `lines.json` e na legenda do cartão final.
- **Demo:** o demo é só para técnica (STYLE.md §10). Não copiar história, cenas, objetos nem timeline.
- **Números:** todos fictícios e coerentes com o CSV.
- **Voz:** não gerada nesta fase.
