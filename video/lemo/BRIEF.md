# Briefing comum dos 5 filmes da TaskGO (biblioteca lemo-opuscar)

## O produto
TaskGO é um marketplace de serviços locais em Recife (PE). O cliente descreve o que precisa (elétrica, hidráulica,
limpeza, montagem, pintura, ar-condicionado, TI), recebe profissionais perto dele com nota e preço, chama um, acompanha
a chegada no mapa, mostra um código de segurança de 4 dígitos quando o profissional chega, paga por Pix (ou cartão) e
avalia com estrelas. O profissional recebe o pedido no celular, aceita, organiza a agenda e vê os ganhos do mês.
Marca: "Task" + "GO" (GO em azul #2b59ff). Símbolo: `assets/marca/taskgo-simbolo.png` (escudo azul com ✓).
Interface real do site: fundo claro (#fff / #f3f3f5) ou escuro (#000), azul #2b59ff, verde #10a56a/#35d08f, fonte Spline Sans
(`app-android/app/src/main/res/font/spline_sans.ttf`, licença OFL). Telas reais: http://localhost:3000/index.html?demo=1&role=cliente#/app/inicio
(e role=profissional), servidas localmente agora.
Dados de demonstração (fictícios): Carlos Andrade (eletricista, ★4,9, Recife), Ana Beatriz Souza, Juliana Ramos (limpeza ★5,0),
Rafael Souza (hidráulica ★4,7), Roberto Farias; preços R$ 120–620; bairros: Boa Viagem, Casa Amarela, Pina, Graças, Madalena, Olinda.

## Regras de todos os filmes
- Língua: português do Brasil (texto na tela, legendas, narração). Uma linha só onde o STYLE.md empilha chinês e inglês.
- Narração: ElevenLabs (plano grátis, vozes masculinas padrão; ver video/lemo/vozes.json), gerada com `python video/lemo/tts_eleven.py <lines.json> <projeto>/vo <voice_id>` (mesma saída do core/tts/tts.py: <id>.wav + dur.json). Créditos são limitados: gere só a versão final do roteiro, uma vez. Escreva o roteiro em
  `lines.json` no formato da biblioteca ([{"id","text"}...]) e uma estimativa de duração por linha (~2,6 palavras/s).
- Duas versões: 16:9 1920×1080 (45–60 s) e 9:16 1080×1920 (~30 s, gancho nos 3 primeiros segundos). Planeje as duas no
  tratamento (a vertical é um corte/reenquadramento da mesma história, não outro filme).
- Nada de "cara de IA": nada de brilho azul genérico, partículas por enfeite, glassmorphism, cards SaaS genéricos. Siga o
  STYLE.md à risca; o estilo é a direção de arte.
- Sem logotipos de terceiros, sem pessoas reais. Números são ilustrativos.
- PROIBIDO (pedido do usuário): relógio, cronômetro, timer, contagem de tempo, anel de tempo, mostrador de horas ou
  "00:14" na tela, em nenhum filme e em nenhum formato. Passagem de tempo se mostra por ação e luz, não por relógio.
  Também não use "chega em N min" ou outra contagem de minutos.
- DETALHADO (regra do usuário): todo cenário tem conteúdo (móveis, objetos, fundo em camadas) e toda ação narrada tem
  consequência visível ("a luz voltou" → os cômodos acendem e o interior aparece). Liste cena a cena em CENAS.md e
  confira cada item nos quadros finais. Nada de cena vazia nem objeto que seja só uma caixa.
- COERÊNCIA FÍSICA (NUNCA pode falhar): toda construção/objeto tem volume real de todos os lados que a câmera vê
  (laterais, fundo, telhado, espessura); nada de fachada "de papel". Trajetos (pessoa, carro, rota no mapa) seguem
  as ruas, dobram nas esquinas e chegam a lugar acessível; nunca atravessam casa, quarteirão ou rio fora da ponte.
  Conferir renderizando de um ângulo extra e sobrepondo a rota ao mapa.
- Pagamento: o cliente paga o profissional direto (Pix ou dinheiro) quando o serviço termina; só a taxa da TaskGO passa
  pela plataforma. Não diga que o dinheiro do serviço "cai pelo app" nem que a TaskGO segura o pagamento.
- Pasta do projeto: `video/lemo/<nome>/`. Ferramentas da biblioteca rodam de `$LIB` = `C:/Users/Mendes/lemo-opuscar`
  (`cd "$LIB" && node core/render/still.mjs "<caminho absoluto do projeto>" 1.5 3`). Nunca edite `core/` nem `styles/`.
- Modo skill: arquivos da biblioteca nas páginas por URL absoluta (`/core/lib.js`); o projeto é servido em `/@film/`.
