# Correções da crítica do site todo (08/11/2026)

**Situação:** concluído (sem a nova crítica, que fica para quando o usuário pedir)
**Pedido do usuário:** corrigir os 5 problemas prioritários da crítica, começando por profissional e honestidade. O celular simulado fica; só abre com pedido aberto, convida ao toque e ganha texto maior.
**Usar:** a skill `impeccable`. Crítica salva em `.impeccable/critique/2026-11-08T18-41-26Z__js-pages.md`.

## Fazer
- [x] P0: Oportunidades do profissional com pedidos fictícios no modo demonstração; erros em português com causa e saída.
- [x] P1: dados de demonstração honestos (sem previsão de chegada, sem pagamento confirmado; Termos no condicional).
- [x] P1: celular abre com pedido aberto, convida ao toque e ganha texto maior.
- [x] P2: uma tabela de estado só, com links nas outras páginas; Início sem trecho repetido.
- [x] P2: Contratar com categorias agrupadas e busca; abas com indicação de rolagem; botão "?" sem cobrir conteúdo no celular.
- [x] Polimento (iniciais do avatar, avaliação média igual no Início e em Desempenho).
- [ ] Nova crítica para medir a nota.

## Feito
- `js/painel/oportunidades.js`: pedidos fictícios no modo demonstração; erros em português (sessão, conexão, servidor).
- `js/core/data.js`, `js/painel/inicio.js`: sem previsão de chegada nem pagamento confirmado.
- `js/pages/legal.js`: termos e políticas no condicional; sem data falsa de atualização.
- `js/pages/celular.js`, `css/site.css`: celular abre no pedido, convite no Aceitar, texto maior.
- `js/pages/profissionais.js`, `js/pages/entrar.js`, `js/pages/home.js`, `js/core/layout.js`: sem tabela de estado repetida; selo "Pronto, sem pedidos ainda"; Início sem Desafio/Proposta; login com título neutro.
- `js/pages/contratar.js`, `js/painel/area.js`, `css/painel.css`: 6 categorias mais pedidas + busca + ver todas; abas com degradê quando há mais; botão de ajuda vai para o menu no celular.

## Não feito
- Dar a cada página pública um recurso de estrutura próprio (a fórmula título + parágrafo + seta continua nas páginas internas).
