# Acessibilidade da TaskGO (27/11)

Pedido: "colocar acessibilidade ao meu site para pessoas deficientes poderem usar".
Decisões do usuário: corrigir **público + login/cadastro + cliente + profissional** (equipe/admin fica para depois) e adicionar
**VLibras**, **painel de acessibilidade** e **página /acessibilidade**.
Meta: WCAG 2.1 nível AA (referência da Lei Brasileira de Inclusão, Lei 13.146/2015, art. 63, e do eMAG).

## Ponto de partida (axe-core, 28 rotas × desktop e celular)
| Regra | Gravidade | Ocorrências | Onde |
|---|---|---|---|
| color-contrast | séria | 694 elementos, 29 páginas | quase tudo em `#/servicos` (≈390), depois planos, termos, como-funciona, time: cinzas #4a4b4d–#666 sobre preto (2,4–3,6:1; mínimo 4,5:1) |
| label-content-name-mismatch | séria | 26 | `#contaBotao` (botão da conta na área logada): o texto visível não está no nome acessível |
O axe pega ~1/3 dos problemas: teclado, leitor de tela, zoom, foco e formulários precisam de conferência manual (tabela abaixo).

## Tabela de cenários
| # | Cenário (quem / como) | O que deve acontecer | Como aplicar | Como conferir |
|---|---|---|---|---|
| 1 | Baixa visão: texto cinza sobre preto/branco | contraste ≥ 4,5:1 (texto) e ≥ 3:1 (texto grande, ícones, bordas de campo, foco) | ajustar tokens de cor em `css/base.css`/`cores.css` (não caso a caso); conferir também os estados hover/disabled/placeholder | axe sem `color-contrast` nas 28 rotas, tema claro e escuro |
| 2 | Baixa visão: zoom 200% e 400% (reflow 320 px) | nada corta, nada sobrepõe, sem rolagem lateral | revisar larguras fixas, `nowrap`, alturas fixas (ex.: `.cel{height:640px}`) | navegador em 1280×800 com zoom 200% e 320 px de largura |
| 3 | Baixa visão: painel "aumentar texto" (até +50%) | layout aguenta | tamanhos em rem; painel muda `font-size` da raiz | rotas principais com +50% |
| 4 | Teclado (sem mouse) | tudo alcançável com Tab/Shift+Tab/Enter/Espaço/Esc, ordem lógica | `<button>`/`<a>` de verdade (nada de `div onclick`), `tabindex` só quando preciso | navegar o fluxo completo: cadastro → contratar → pagar → avaliar; profissional: aceitar pedido |
| 5 | Teclado: foco visível | anel de foco claro em todo elemento focável, nos dois temas | `:focus-visible` com contraste ≥ 3:1 | inspeção nas rotas |
| 6 | Teclado: modais e menus | foco entra no modal, fica preso nele, Esc fecha, foco volta ao botão que abriu | revisar `openModal/closeModal` e menus (conta, notificações) | abrir/fechar cada modal só no teclado |
| 7 | Teclado: pular para o conteúdo | link "Pular para o conteúdo" funciona em todas as rotas, inclusive na área logada | conferir o que já existe | Tab na primeira tecla |
| 8 | Leitor de tela: navegação da SPA | ao trocar de rota, o título da aba muda e o foco vai para o título da página (ou é anunciado) | `document.title` por rota + foco no `<h1>`/`main` com `tabindex=-1` em `render()` | NVDA (ou leitura do DOM): cada rota anuncia o título |
| 9 | Leitor de tela: estrutura | um `<h1>` por página, títulos em ordem, regiões (`header`, `nav`, `main`, `footer`) com rótulo | ajustar templates | axe + lista de títulos/landmarks |
| 10 | Leitor de tela: avisos (toast, status do pedido, erros) | anunciados sem roubar o foco | contêiner `aria-live="polite"` (erros: `assertive`/`role=alert`) para `toast()` | disparar toast e conferir região viva |
| 11 | Leitor de tela: ícones e imagens | ícone decorativo `aria-hidden`; botão só-ícone com `aria-label`; foto com `alt` útil (ou vazio se decorativa) | revisar `ic()` e botões só-ícone; fotos da equipe/serviços | axe `image-alt`, `button-name` (já passam) + amostra manual |
| 12 | Leitor de tela / voz: nome acessível = texto visível | quem usa comando de voz consegue dizer o que vê | corrigir `#contaBotao` e similares | axe sem `label-content-name-mismatch` |
| 13 | Formulários | todo campo com `<label>`, erro ligado ao campo (`aria-describedby`, `aria-invalid`), obrigatório anunciado, foco vai para o 1º erro, máscaras não atrapalham digitação | revisar entrar, cadastro, contratar, pagamento, configurações, verificação | enviar vazio/errado só no teclado |
| 14 | Mapa (rastreio) e celular simulado | informação do mapa também em texto (distância/situação); celular da landing navegável | texto equivalente ao lado do mapa | leitura do DOM |
| 15 | Movimento (epilepsia, vestibular) | `prefers-reduced-motion` respeitado; painel "reduzir animações" força; nada pisca > 3×/s | conferir `css/motion.css` e animações em JS | SO com redução ativada + painel |
| 16 | Toque / motora | alvos ≥ 44×44 px no celular, sem gesto obrigatório (arrastar/segurar) sem alternativa | ajustar botões pequenos (ícones, fechar, chips) | inspeção 390 px |
| 17 | Tempo | nenhum limite que expulse sem aviso; o Pix expira com aviso e opção de gerar outro | conferir pagamento | leitura do fluxo |
| 18 | Cognitiva / dislexia | painel com espaçamento de linha/letra maior e links sublinhados; textos já são curtos | painel | ativar cada opção |
| 19 | Idioma | `<html lang="pt-BR">` e trechos em outra língua marcados | conferir | axe `html-has-lang` |
| 20 | Surdos: VLibras | botão do VLibras em todas as páginas, avatar traduz o texto selecionado/da página para Libras | snippet oficial do gov.br em `index.html`; CSP mínima liberando só os domínios que ele usa (descobrir com violações reais) | abrir, traduzir um trecho, nenhum erro de CSP no console |
| 21 | VLibras: segurança e privacidade | só domínios `*.vlibras.gov.br` (e o que for estritamente necessário); nada de `unsafe-eval` geral se der para usar `wasm-unsafe-eval`; documentar na política de privacidade que o texto vai para o serviço do governo | `docker/nginx.conf` e `nginx.prod.conf` | revisor + console |
| 22 | VLibras + painel não se atrapalham | os dois botões fixos não se sobrepõem entre si, nem ao chat/toasts/botões do celular | posição | 390 px e desktop |
| 23 | Painel de acessibilidade | botão fixo acessível (teclado, nome "Acessibilidade"), opções: aumentar/diminuir texto, alto contraste, reduzir animações, sublinhar links, espaçamento maior, restaurar; escolha salva no aparelho (`localStorage`, sem dado pessoal) e aplicada antes da primeira pintura | `js/core/acessibilidade.js` (classes no `<html>`) + CSS; tag em `index.html` com `?v=` | ligar cada opção, recarregar, conferir que manteve |
| 24 | Alto contraste | preto/branco/amarelo puro, bordas visíveis, foco grosso; não quebra mapa/fotos | classe `a11y-contraste` | rotas principais |
| 25 | Modo demonstração e modo real | tudo funciona igual nos dois | — | `?demo=1` e site real |
| 26 | Página /acessibilidade | o que o site oferece, padrão seguido, limitações conhecidas (painel da equipe), contato para relatar barreira, data da última revisão; link no rodapé | `js/pages/acessibilidade.js` + rota + tag no `index.html` | abrir, axe sem violações |
| 27 | Testes automáticos | regressão de acessibilidade no `npm test` | `tests/acessibilidade.test.cjs` com axe-core nas rotas (pula se não houver site/Chrome, como o `navegador.test.cjs`) e teste do painel (classes e persistência) | `npm test` |
| 28 | Fora do escopo agora | painel da equipe/admin (moderação, financeiro, verificação) | anotar como pendência | — |

## Etapas
- [x] Auditoria inicial com axe-core (28 rotas × 2 tamanhos)
- [ ] Fundamentos (cenários 1–19, 25)
- [ ] Painel de acessibilidade (23, 24, 18, 15)
- [ ] VLibras + CSP (20–22)
- [ ] Página /acessibilidade (26)
- [ ] Testes (27) e conferência manual (teclado, zoom, leitor)
- [ ] Revisor (CSP e script externo)
- [ ] Commit/publicação (só com pedido do usuário)

## Status da implementação (27/11, desenvolvedor)
- [x] 1 contraste: causa era `opacity:.35` na animação `traca` (motion.css), não os tokens; tokens mantidos. Auditoria: color-contrast 694 -> 0 nas rotas do escopo exceto `.tag-nova` (corrigido, reconferir)
- [x] 2 reflow 320 px / zoom 200%: CSS de alvos ≥44 px em 560 px; conferência manual PENDENTE
- [x] 3 aumentar texto: px de fonte convertidos para rem nos CSS (auth, base, painel, site); conferência visual com +50% PENDENTE
- [~] 4 teclado: botões reais; Tab completo cadastro->contratar->pagar->avaliar PENDENTE
- [~] 5 foco visível: já existia (`:focus-visible`); contraste do foco nos dois temas OK pelos tokens
- [x] 6 modais: openModal já prendia foco e Esc; painel usa o mesmo modal
- [ ] 7 pular para conteúdo: existe e aponta para `#conteudo` (inclusive na área logada); conferência Tab PENDENTE
- [x] 8 título e foco na troca de rota (router.js trocar())
- [~] 9 estrutura (h1, landmarks): não revisado por página
- [x] 10 toasts em região aria-live (index.html) e role=alert nos erros
- [~] 11 ícones e imagens: ic() já é aria-hidden; amostra manual não feita
- [x] 12 `#contaBotao` com nome igual ao texto visível
- [x] 13 formulários: entrar, contratar (passos), pagamento (cartão), configurações (perfil, senha, exclusão) e verificação ligados a erro com marcarCampo (aria-invalid, aria-describedby, foco no campo); campos obrigatórios já usam required
- [x] 14 mapa do rastreio: distância e tempo em texto (painel) e anúncio em região viva só quando mudam; celular simulado da landing não revisado
- [x] 15 movimento: prefers-reduced-motion já existia; painel "Reduzir animações" aplica `a11y-sem-animacao`
- [x] 16 alvos de toque ≥44 px em 560 px (acessibilidade.css)
- [x] 17 Pix: aviso (role=alert) a 5 min do fim, Pix vencido mostra Gerar outro Pix; relógio fora da região viva. Conferido só por leitura do código (sem pagamento real)
- [x] 18 painel: espaçamento e sublinhado de links
- [x] 19 `<html lang="pt-BR">` já existe
- [x] 20 VLibras: snippet oficial no fim do body; widget abre e traduz sem erro de CSP (conferido)
- [x] 21 CSP: vlibras.gov.br/app/ e prefixo fixo cdn.jsdelivr.net/gh/spbgovbr-vlibras/vlibras-portal@v7.12.3/ (sem unsafe-eval)
- [~] 22 botões fixos: painel à esquerda inferior, VLibras à direita no meio, ajuda à direita inferior; conferir 390 px
- [x] 23 painel de acessibilidade (js/core/acessibilidade.js + css/acessibilidade.css)
- [x] 24 alto contraste (classe a11y-contraste)
- [x] 25 demo e real: não testado nos dois modos ainda (PENDENTE)
- [ ] 26 página /acessibilidade: PENDENTE
- [ ] 27 testes tests/acessibilidade.test.cjs: PENDENTE
- [ ] 28 painel da equipe/admin: fora do escopo, pendência registrada

## Resultado (27/11, desenvolvedor)
- Auditoria axe (28 rotas x desktop e celular, sem admin): antes 694 color-contrast + 26 label-content-name-mismatch; depois 0 violações.
- npm test: 33 de 33 passam (inclui axe em 24 rotas do escopo e teste do painel).
- Teclado: painel abre, foco preso, Esc fecha e devolve o foco; "Pular para o conteúdo" funciona.
- Reflow: sem rolagem lateral em 320, 390 e 640 px nas rotas do escopo (cabeçalho ajustado abaixo de 360 px).
- Botões fixos (painel, VLibras, ajuda) não se sobrepõem em 390 px.
- Pendências: 13 (formulários de contratar, pagamento, configurações e verificação ainda sem erro ligado ao campo); 14 (texto equivalente do mapa); 17 (expiração do Pix sem conferir); 9 e 11 (revisão manual de títulos e imagens por página); 25 (demo e real comparados só nas rotas de teste); 28 (painel da equipe e admin fora do escopo, citado na página /acessibilidade).
- Revisor ainda não passou (etapa de CSP e script externo).

## Fechamento (27/11, segunda rodada)
- Auditoria axe final: 0 violações nas 28 rotas do escopo (desktop e celular).
- npm test: 33 pass, 0 fail.
- Pendências que sobraram: 9 e 11 (revisão manual de títulos e imagens), 25 (comparação demo x real completa), conferência visual de +50% de texto, e 28 (equipe e admin, fora do escopo).

## Correções do revisor (27/11, terceira rodada)
- CSP: pin @v7.12.3 removido; prefixo do repo https://cdn.jsdelivr.net/gh/spbgovbr-vlibras/ em script/img/font/connect. vlibras.gov.br/app/ em script/img/font/connect/frame. Teste com CSP ativa: avatar Unity carregado, 0 violações de CSP, texto selecionado sinalizado (captura em scratchpad/a11y/vlibras-traducao.png).
- Pix: 409 em "Gerar outro" mostra a mensagem de confirmação (role=alert) e mantém o botão; Pix vencido: "expirou; se já pagou, aguarde a confirmação"; contagem reinicia com o Pix novo. Teste de regressão: tests/pagamento-pix.test.cjs (falhava antes: 01:40 em vez de ~60:00).
- Região viva: só por toast (role=alert para erro, role=status para ok); contêiner #toasts sem aria-live.
- Botão da conta: aria-label com o texto visível NÃO passa no axe (label-content-name-mismatch compara com "AB" + "Ana" das iniciais); nome = conteúdo visível, contexto por aria-describedby (texto oculto "Conta de <nome>").
- Página /acessibilidade: textos reescritos para o que foi verificado, com limitações listadas.
- sw.js: só push, sem cache de arquivos do site (nada a corrigir).
- Validações: npm test 36/36; sintaxe OK; auditoria 0 violações.
