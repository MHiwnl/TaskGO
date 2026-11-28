# Pendências da revisão de 25/11 (depois da publicação)

## Etapas
- [x] Devolução sem provedor (cobrança do Asaas sem txid): texto honesto ao cliente, a equipe consegue encerrar o caso, e aviso cai no administrador quando não há usuário de financeiro/suporte
- [x] Moderação: só avisos ligados a um pedido entram na lista de recurso (js/painel/moderacao.js)
- [x] Verificação: data máxima do nascimento no fuso local, não em UTC (js/painel/verificacao.js)
- [x] fetch direto: legal.js passou para apiJSON; entrar.js e entrar-google.js ficam (login com CSRF explícito)
- [x] Texto do prazo do cartão: sem o número
- [x] Testes de regressão, 4 rodadas do revisor, commit, push e publicação
- [ ] Filmes onetake A e B: render final (em espera, pedido do usuário)

## Pendências que tinham ficado para depois (26/11)
- [x] Devolução automática parada avisa a equipe (30 min depois de vista automática, sem reserva; PagamentoService.conferirAutomaticasParadas)
- [x] Aumento do valor de conferência aberta avisa a equipe, com a diferença
- [x] Lista do Financeiro sem N+1 (cobranças, clientes e pendências lidos uma vez)
- [x] Teste com banco real (H2): ConferenciaDevolucaoBancoTest
- [x] Revisor: 2 correções aplicadas (aviso antes da hora; texto com a diferença)
- [ ] Commit e publicação (aguardando o usuário)
