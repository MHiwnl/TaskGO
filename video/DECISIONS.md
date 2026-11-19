# Decisões do filme (uma linha cada)

## Etapa 1 — ideia
- Ideia em uma frase: na TaskGO você não depende da palavra de ninguém; o pedido chega a quem está perto e disponível, e cada passo do serviço é conferido.
- Resultado para o espectador: depois de 40 s, a banca sabe dizer o que a TaskGO oferece (achar quem está disponível perto e confiar porque cada passo é conferido).
- Pergunta que o filme responde, nas palavras do espectador: "Como eu acho alguém disponível agora, e como sei que posso confiar?"
- Gancho (crença errada): "achar alguém é sorte e confiar é questão de palavra". O filme troca "palavra" por "conferido".
- Fatos obrigatórios: pedido; vai para quem está perto e disponível; identidade verificada; endereço só depois de confirmar; chegada no mapa; código de 4 números; pagar só depois; chegada a mais de 150 m não vale.
- Lista de corte (fica de fora): obras em etapas, serviço recorrente, ajuda rápida, Plano Pro, taxas, conversa filtrada, moderação, agenda, notas e número de avaliações (seria métrica inventada).
- Metáfora: nenhuma. O filme mostra a coisa real (o pedido como ordem de serviço, o mapa, o código, o Pix).
- Caso real da prova: chuveiro queimado, Elétrica, R$ 120. Nome do profissional de exemplo: "Rafael". É exemplo, não cliente real; nenhum número de uso ou nota aparece.
- Nome de cada coisa, usado sempre: "pedido", "profissional", "você" (cliente), "endereço", "código", "chegada".
- Narração: voz do próprio usuário (escolha dele em 18/11/2026), 10 falas em video/ROTEIRO.md. Até a gravação chegar, o tempo usa uma faixa-guia sintética só para montar; ela nunca vai para a entrega.
- Duração: 40 s. O tempo de cada cena se ajusta à duração real de cada fala gravada.

## Etapa 3 — direção
- Vence A, "ordem de serviço em papel" (directions.html): pede o fundo neutro quente sem sair da marca; o pedido em linhas é a interface real e atravessa o filme; o mapa só entra quando a ideia é de lugar. B (preto da marca) perde linhas finas no projetor; C (mapa vivo) faz o mapa disputar atenção; D (celular) deixa a interface pequena demais em 9:16.

## Etapas 4 a 6 — storyboard, animatic e montagem
- Objetos que atravessam o filme: o balão da promessa vira a linha de cima do pedido; um ponto da 1ª onda vira o Rafael (avatar e cartão) e depois o pino do mapa; o pedido junta um ✓ por passo e termina carimbado.
- Rótulos de cor sempre com palavra: azul = "avisados: os 10 mais perto", cheio = disponível, vazado = indisponível; ✓ verde vem com "conferida/vale", ✕ vermelho com "não vale".
- Código da chegada no 9:16 e no 1:1: painéis em cima e a casa embaixo (no 16:9, lado a lado), para nada sair do quadro.
- Papel liso, sem grão: o grão em tela cheia custava mais da metade do tempo de cada quadro no Chrome sem GPU e piorava a compressão; quase não se via.
- Render em 4 Chromes em paralelo, cada um num trecho; os trechos se juntam sem recodificar. Disco com pouco espaço livre: nenhum quadro é gravado em disco, vão direto para o ffmpeg.

## Etapa 8 — verificação (revisão de design por subagente)
- A virada muda uma variável só: na linha "chegada" do pedido, "✕ longe: não vale" vira "✓ 80 m: vale"; o resto do pedido apaga. Antes o pedido dizia "conferida" enquanto o mapa recusava.
- O raio de 150 m ganhou rótulo no mapa da virada: é a régua do 1,1 km contra os 80 m.
- A barra azul do endereço diz "oculto": professor não adivinha o que a tarja significa.
- Um nome para a chegada: "chegada conferida", também no balão do fim.
- Letra mínima a 390 px subiu de 8 px para 11 px (9:16 com fontes maiores; rótulos pequenos de .62em para .7em).
- Cartão do Rafael fica ao lado do avatar se couber; senão, embaixo, com a câmera pondo o Rafael no alto do palco (o botão "confirmar Rafael" ficava escondido no 9:16).
- Mantidos os estados do pedido (procurando → concluído): estão em palavras e mostram o avanço sem a fala.
- O QR é um padrão aleatório com semente, não um BR Code válido: não abre pagamento (SOURCES.md).

## Frases de impacto (pedido do usuário, 18/11/2026)
- Narração e títulos reescritos em frases curtas, com uma ideia que abre e fecha: "achar é sorte / confiar é fé" no começo, "não é sorte, não é fé, é conferido" no fim.
- Cada frase segue com fonte: "dez profissionais" (POR_ONDA = 10), "quatro números", "um quilômetro" (1,1 km do exemplo); "documento e rosto" = verificação com documento e selfie.

## Nova direção de movimento (pedido do usuário: "SUPER animações", referência: showreel de cartas 3D no Pinterest)
- Da referência fica a linguagem (carrossel 3D em perspectiva, letreiro gigante vazado correndo, painel tipo tela de controle com marcas de corte, código de tempo, capítulo "03/07", parâmetros e BPM), não a identidade (laranja, "DEPTH", formas geométricas).
- Cor: o azul da TaskGO em tela cheia com grão, a "Área Azul com Grão" do site, no lugar do papel. A direção A (papel) fica em src/legado/.
- Cada passo é uma carta do carrossel (01 pedido … 07 pago). A carta da vez vira branca e mostra a prova (radar, documento, endereço, mapa, código, Pix). Mantém a regra "um objeto atravessa o filme": é o carrossel.
- Os parâmetros do painel são dados reais com fonte (avisados 10, raio 150 m, código 4 números, 96 BPM da própria trilha).
- O letreiro gigante para no movimento reduzido.

## Vender a TaskGO (pedido do usuário, 18/11/2026)
- Texto reescrito como venda: dor concreta ("chuveiro queimou… torce pra alguém aparecer"), promessa ("um pedido resolve"), quatro provas que respondem às duas crenças do público (achar alguém rápido; confiar em quem chega), a mentira que a TaskGO pega ("aqui, não cola") e chamada para ação ("Peça agora").
- Títulos na tela acompanham: "pediu indicação? agora é torcer." → "confiança na hora."
- Sem legenda gravada na imagem: a legenda fica no `captions.srt` (acessibilidade sem poluir o quadro). `?legenda=1` no film.html volta a mostrar.
- Sem painel de interface (marcas de canto, "TaskGO · filme ’26", código de tempo, capítulo 06/10, BPM, régua e parâmetros): era linguagem da referência, não vendia nada.
- Narração com voz clonada (XTTS-v2, Coqui, licença CPML não comercial, aceita pelo autor para o TCC) de pessoa que autorizou; referência em `narracao/referencia/` (fora do git). "TaskGO" escrito "Tésk Gôu" na síntese para a pronúncia.

## Voz da ElevenLabs em uma tomada (pedido do usuário, 18/11/2026: "tá muito robótica")
- Referência: um reels de explicação em que a narração é contínua (0,7 s de silêncio em 42 s), em tom de conversa. A voz de quem aparece nesse vídeo não é clonada: é uma pessoa real que não autorizou. Só o jeito de falar é copiado.
- Texto reescrito em tom de conversa ("E não é qualquer um, não", "Aqui não cola", "Faz seu pedido agora").
- Narração gerada de uma vez (eleven_multilingual_v2) e cortada nas 10 falas pelos tempos de cada letra que a API devolve: a entonação corre como numa conversa e o filme continua sincronizado com as cenas.
- Sem crédito de voz na tela (decisão do autor). Tomada gerada no site da ElevenLabs, plano grátis, voz "Lucas" da biblioteca.
