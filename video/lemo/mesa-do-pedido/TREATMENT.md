# Mesa do pedido: tratamento

Estilo: Tabletop App Flythrough (desktop-flythrough), biblioteca lemo-opuscar. Fase 1: tratamento, roteiro e prova do visual.
Assunto: a jornada de um pedido na TaskGO, contada pelas telas reais do app em modo demonstração (`telas/`).

## Logline

Um pedido de ar-condicionado sai do celular do cliente, é aceito por um profissional, chega à porta com um código de quatro dígitos, é pago e avaliado; tudo numa única tomada sobre a mesa, com cada parada sendo uma tela real do TaskGO.

## 1. Três estruturas candidatas

1. **Um item viaja de cartão em cartão.** O pedido é uma figura de pixel que muda a cada parada: pedir, aceitar, chegar, pagar, avaliar. Cada parada é uma tela real. Cause e efeito em sequência.
2. **Duas metades da mesa.** Lado do cliente (lento, calmo) contra lado do profissional (rápido, agenda cheia), o mesmo pedido nas duas pontas. Bom para o contraste, fraco para o código de segurança, que é o momento de encontro.
3. **Um detalhe que cresce.** Começa no código de quatro dígitos da tela do pedido e cresce até a mesa inteira. Forte no fim, mas esconde o produto no começo e não mostra o caminho.

**Escolhida: 1.** O produto é uma sequência de passagens de mão entre duas pessoas. "Um item viaja" torna a passagem visível (o envelope de aceite, o rastro dos dados do pagamento). A estrutura também usa as telas reais como paradas, e o "Curtain call" final lê a jornada inteira de uma vez. A estrutura 2 fica como possível variação de corte, não usada aqui.

## 2. Benchmark

Referência de gramática: filmes de produto em plano único sobre a mesa, o estilo da própria biblioteca (STYLE.md §6 e §8). Não cito um filme nominal porque não verifiquei nenhuma referência específica offline.

- **Aprender:** o ritmo de um plano só que pousa no tempo; o "rota como lugar" (a câmera lê cada cartão a 55–70°); o cabo de cada passagem (whip de 0,3–0,5 s); o silêncio antes do momento de maior peso.
- **Não copiar:** personagens, layouts, ícones, música ou marca de nenhum filme de referência.

## 3. Estrutura e ritmo

- **Duração 16:9:** 54 s. Dentro de 45–60 s.
- **Tempo:** 120 BPM (compasso de 2 s, grade de 16 avos de 0,125 s), o padrão do `anim.js`.
- **Abertura:** a câmera cai sobre a mesa e pousa no tempo 1 (0,0 s). Uma tenda-título "TASKGO · UM PEDIDO" fica ao lado do cartão real "Contratar". O pedido (figura de pixel) faz o *sticker pop* no tempo 1: o pedido nasce.
- **Gancho:** nos três primeiros segundos já há uma figura pulando e uma frase sobre o pedido. (O gancho forte fica para a versão vertical, ver §9.)
- **Final:** o *curtain call* enquadra os seis cartões de uma vez; cada um pula uma vez, da esquerda para a direita; o cartão-título cai no centro vazio da mesa. A figura faz o *reverse pop* e vira um registro impresso (a avaliação salva), o eco da abertura.

## 4. Lista de planos (16:9, 54 s)

Cada plano tem enquadramento (plano aberto, inteiro, médio, fechado, inserto), ângulo, movimento de câmera, duração e o motivo.

| # | Tempo | Enquadramento e ângulo | Movimento | Dur. | Por quê |
|---|---|---|---|---|---|
| P1 | 0,0–4,0 | Fechado sobre a tenda e o cartão Contratar, 20–45° para baixo | Macro glide com órbita lenta; pousa no tempo 1 | 4,0 | Presença e escala pequena; a tenda lê o título sem cobrir nada |
| P2 | 4,0–9,0 | Médio no cartão Contratar, 35° | Whip de 0,4 s até o cartão Oportunidades (no fim: o pedido sobe da figura) | 5,0 | Causa e efeito: o pedido sai do cliente |
| P3 | 9,0–14,0 | Médio no cartão Oportunidades, 40° | A figura espera no cartão (no ritmo); no tempo 10,0 o anel de impacto sai do botão "Aceitar" | 5,0 | Passagem de mão visível |
| P4 | 14,0–19,5 | Inteiro no cartão Pedido (04), 55–65° para baixo | Cavalgada baixa atrás da figura; a câmera sobe até quase o topo | 5,5 | Travessia e a leitura do estado "A CAMINHO" |
| P5 | 19,5–21,5 | Close no cartão Pedido (04), a 60°; silêncio de 0,7 s antes | Empurrão curto, parada (*hold*) com leve tremor | 2,0 | **Assinatura**: o código de quatro dígitos sobe do cartão (*UI rises*) |
| P6 | 21,5–26,0 | Médio no cartão Pedido, 45° | Cavalgada até a figura, que está junto ao cartão de "Agenda" | 4,5 | Chegada do profissional e o código sendo digitado |
| P7 | 26,0–31,5 | Médio em Agenda, 40° | Gaveta (*drawer*): uma lâmina do cartão Agenda desliza; a figura entra e uma janela acende | 5,5 | Organização da agenda do profissional |
| P8 | 31,5–35,5 | Médio em Pedidos (03), 40° | Dados (*data run*) correm do cartão Pedido até Pedidos; o valor R$ 320,00 da lista real aparece | 4,0 | Pagamento sem tela real (ver §8) |
| P9 | 35,5–40,0 | Médio em Avaliações (07), 40° | Whip até o cartão Avaliações; *reverse pop* da figura no registro impresso | 4,5 | Avaliar, e o registro fica salvo |
| P10 | 40,0–47,0 | Plano aberto do anel de cartões | Câmera sobe e recua; cada parada pulsa uma vez | 7,0 | Recapitulação da jornada inteira |
| P11 | 47,0–54,0 | Plano aberto final, mesa inteira | *Curtain call*: a figura e os personagens saltam da esquerda para a direita; o cartão-título cai no centro vazio | 7,0 | Fechamento que ecoa a abertura |

Movimentos usados: macro glide com órbita, whip, cavalgada baixa, subida a quase o topo, empurrão com tremor, gaveta, dados, *reverse pop*, recuo final. São mais de quatro, como pede a regra.

## 5. Beat sheet (segundo a segundo, 54 s)

| Tempo | Compasso | Acontece | Som |
|---|---|---|---|
| 0,0 | 1 | Câmera pousa; figura faz *sticker pop* | Marimba do acorde + varredura para cima |
| 0,5–4,0 | 1–2 | Tenda lê "TASKGO · UM PEDIDO" ao lado de Contratar | Acorde segura; pulso |
| 4,0 | 3 | Whip começa | Assobio do whip, medido pela velocidade |
| 4,5 | 3 | Figura pula até o botão "CHAMAR AGORA" (pílula de palavra-chave) | Tique a cada colcheia |
| 9,0 | 5 | Envelope parte de Contratar em arco | Som de passagem |
| 10,0 | 6 | Toque duplo em "Aceitar R$ 150,00" (tela de Oportunidades) | Dois cliques secos |
| 11,0 | 6 | Anel de impacto sobre o papel; faixa cai no cartão | Baque surdo + anel |
| 14,0 | 8 | Cavalgada até o cartão Pedido; estado "A CAMINHO" | Passos leves |
| 18,8–19,5 | 10 | **Silêncio** (a música recua, nenhuma fala) | Só a sala |
| 19,5 | 10 | Quatro dígitos sobem do cartão em colcheias | Primeiro som após o silêncio: tique de cada dígito |
| 21,5 | 11 | Frase do código | Cama de música volta com o acorde |
| 26,0 | 14 | Gaveta da Agenda abre | Deslize de madeira |
| 31,5 | 16 | Dados correm até Pedidos | Bolinhas claras, em colcheias |
| 35,5 | 18 | Whip para Avaliações; *reverse pop* | Assobio + nota descendente |
| 40,0 | 21 | Subida e recuo sobre a mesa | Acorde final sobe |
| 47,0 | 24 | Cartão-título cai no centro | Baque (*slam*) no tempo 1 do compasso |
| 54,0 | 28 | Fim | Acorde final segura até o último meio segundo |

## 6. Mapa de cues

- **Tempo:** 120 BPM; compasso de 2 s; cada ação na grade de 16 avos.
- **Tempos fortes:** 0,0 (pouso e primeira figura), 10,0 (aceite), 19,5 (código, depois do silêncio), 47,0 (título), 54,0 (fim).
- **Harmonia:** um acorde por compasso. Tom sol maior, progressão I–V–vi–IV.
- **Instrumentação por seção:** abertura (marimba + bateria suave); pedido (marimba + baixo pizzicato); código (só marimba e pulso, seco); pagamento e avaliação (marimba + claps); fechamento (acorde pleno + vibrafone nas notas longas).
- **Silêncio:** 18,8–19,5 (dois momentos de silêncio ao todo, o segundo é o fim).

## 7. Desenho de som

| Seção | Ambiente | Foley principal | Música |
|---|---|---|---|
| Abertura (0–4) | sala de luz de sol, leve | pouso (pancada de feltro), pop | cama, pulso |
| Pedido (4–14) | leve | tique, whip, toques de "Aceitar" | baixo pizzicato, marimba |
| Código (14–21) | leve | tique de dígitos; silêncio antes | recua (silêncio 18,8–19,5) |
| Gaveta e pagamento (21–35) | leve | deslize, dados | cama + claps |
| Avaliação (35–47) | leve | reverse pop (nota descendente) | marimba cheia (sem fala) |
| Fim (47–54) | leve | baque do título | acorde pleno até o fim |

Música abaixa cerca de 16 dB sob a voz. A mistura final é −14 LUFS (core/render/mux.sh).

## 8. Telas reais, mapeadas

Capturadas por `capturar_telas.py` (pasta `telas/`), no modo demonstração, celular 390×844, 2×.

| Arquivo | Tela | Uso no filme | Observação |
|---|---|---|---|
| 01-cliente-inicio.png | Início (cliente), "o profissional está a caminho" | Não usada no anel (já mostra o estado final) | Opcional como reforço |
| 02-cliente-contratar.png | Contratar: "Chamar trabalhador AGORA" | P1–P2 (primeiro cartão) | Real |
| 03-cliente-pedidos.png | Meus pedidos: lista com "R$ 320,00" e "A caminho" | P8 (valor do pagamento) | Real |
| 04-cliente-pedido.png | Pedido: "A caminho", código de segurança 8181 | P4–P6, a assinatura (P5) | Real; ver ressalva do mapa |
| 05-cliente-rastreio.png | Rastreio (rota `chamando`) | **Não usada** | Mostra "O servidor não respondeu como esperado" sobre o mapa: falha da demonstração |
| 06-cliente-pagamento.png | Pagar | **Não usada** | Na demonstração diz só "Na demonstração não há pagamento." Não há tela de pagamento real |
| 07-cliente-avaliacoes.png | Avaliações: "Sua nota", "Que você fez" | P9–P10 | Real |
| 08-profissional-oportunidades.png | Oportunidades: "Aceitar R$ 150,00" | P3 | Real |
| 09-profissional-agenda.png | Agenda: 09:00 e 14:30 | P7 | Real |

Ressalvas:
- **Pagamento:** a tela de pagamento não existe no modo demonstração. O filme não inventa uma. P8 mostra o pagamento pelo valor da lista real (03) e pelos dados que correm. Para ter a tela de pagamento de verdade, é preciso um pedido de demonstração em estado de pagamento ou captura com a API real.
- **Rastreio com erro:** a tela de rastreio da demonstração mostra erro sobre o mapa. Não é usada. Vale registrar como defeito do demo, fora do escopo desta fase.
- **Mapa do pedido (04):** o mapa usa tiles do OpenStreetMap e o rodapé de atribuição (Leaflet, OpenStreetMap). O brief proíbe logotipo de terceiros. Na composição do cartão, o rodapé do mapa é recortado, e o recorte fica registrado no CREDITS. Decisão pendente: aceitar esse recorte ou trocar por outra captura.
- **Regra do usuário: sem relógio nem contagem de tempo na tela.** Nenhum relógio, cronômetro, timer, anel de tempo ou mostrador de horas, em nenhuma versão. A passagem de tempo do filme vem só de ação e montagem. Em `capturar_telas.py` (função `LIMPAR_TEMPO`), o texto de hora ("13:02", "09:00"), "há N min" e "— min", e a caixa de espera com ícone de relógio ("Aguardando a chegada do profissional") ficam ocultos antes de cada captura. O espaço fica vazio nesses pontos. Conferido: nenhum texto de tempo visível restou nas rotas usadas, e a captura 04b foi vista sem o relógio. O anel do aceite (P3) é de impacto, não de tempo. A barra de status não aparece: as capturas são da página do app, sem a barra do celular.
- **Textos da tela:** os nomes (Marcos Vinícius, Ana Beatriz, Juliana Ramos, Rafael Souza) e os valores são fictícios, como pede o brief.

## 9. Versão vertical 9:16 (~30 s, 1080×1920)

É o mesmo filme recortado e reenquadrado: os mesmos cartões, a mesma figura, a mesma gramática, a mesma música. Não é outro filme.

- **Gancho (0–3 s):** o código de quatro dígitos sobe do cartão do pedido, em primeiro plano (a assinatura, P5, vem antes). A voz diz: "Quatro dígitos. Você informa só quando ele chega." O primeiro segundo já é o código.
- **Corpo (3–22 s):** a coluna de cartões é percorrida de cima para baixo: pedido (3–10), aceite (10–12), código e chegada (12–16), gaveta da Agenda (16–19), dados até o pagamento (19–23).
- **Avaliação e fim (22–30 s):** reverse pop no registro impresso, depois o cartão-título cai sobre a coluna. Os planos são os mesmos da versão 16:9, recortados em 9:16.
- **Legenda:** a barra inferior fica a ~64 px da borda, e a câmera mantém o assunto ~110 px acima do centro, para que nada importante fique sob a barra.

## 10. Legendas e títulos

- **Legenda:** uma linha só, em português do Brasil, na barra inferior central (caixa de tinta arredondada a ~72% de opacidade, cerca de 64 px acima da borda). Uma frase por cue, a fala aparece e sai com ela. Mínimo de 1,8 s, e nunca menos que a frase + 0,6 s.
- **Palavras-chave no mundo:** pílulas de mono em caixa alta, sem sombra (`castShadow = false`): "CHAMAR AGORA", "ACEITE", "A CAMINHO", "CÓDIGO", "PIX", "AVALIE". Só palavras-chave, nunca frase.
- **Título:** tenda no início, cartão grande caindo no centro no fim. O título é "TaskGO" (com "GO" em azul, como a marca).
- **Fonte:** só arquivos de fonte do próprio filme. Sans para títulos e legendas (Spline Sans, OFL, em `app-android/app/src/main/res/font/spline_sans.ttf`). Mono para rótulos: a escolher na fase 2 entre uma mono com licença OFL.

## 11. Narração (resumo)

Texto completo em `lines.json`, em português do Brasil. Duração estimada por linha a ~2,6 palavras/s. A narração é gerada na fase 2 (voz não gerada agora). Ver §12 sobre o motor de voz.

## 12. Paleta e tipo (cores da mesa)

Paleta na linha do STYLE.md §3 e da marca.

- **Papel (mesa):** `#e6e9f0`; ponto `#c9cfdc`; cartão `#fcfcfd`; tinta `#14171f`; apagado `#6b7280`; linha `#dfe3ea`.
- **Acento (uma só, o que importa agora):** `#2b59ff` (azul da marca).
- **Apoio do elenco:** damasco `#f39a5b`, céu `#5fb4e8`, limão `#f2c94c`.
- **Sucesso (só para sucesso):** `#10a56a`. **Falha (só para erro):** `#d23a3a`. O filme não usa erro, mas a cor fica reservada.
- **Madeira:** tom quente, sob a luz do sol baixa.

## 13. Checagens de direção

- Dois momentos de silêncio: 18,8–19,5 e 47,0–54,0 (no fim, só o acorde).
- O primeiro som após o silêncio é o tique do código (o som mais importante do filme).
- Ao menos duas transições por som: o envelope (P3) puxa o som do próximo cartão; o reverse pop (P9) deixa o som do cartão anterior se estender.
- Nenhuma parada sem movimento: em cada cartão há uma figura ou um dado em movimento.

## 14. Dúvidas que dependem do usuário (fase 2)

1. Tela de pagamento real: como obter (pedido de demonstração em pagamento, ou captura com API).
2. Motor de voz em português do Brasil: Kokoro (pt-br) ou edge-tts (pt-BR). Ambos exigem checagem de rede ou modelo.
3. Recorte do mapa (atribuição do OpenStreetMap) aceito ou não.
