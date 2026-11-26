# Pendências da revisão de 25/11 (depois da publicação)

## Etapas
- [x] Devolução sem provedor (cobrança do Asaas sem txid): texto honesto ao cliente, a equipe consegue encerrar o caso, e aviso cai no administrador quando não há usuário de financeiro/suporte
- [x] Moderação: só avisos ligados a um pedido entram na lista de recurso (js/painel/moderacao.js)
- [x] Verificação: data máxima do nascimento no fuso local, não em UTC (js/painel/verificacao.js)
- [x] fetch direto: legal.js passou para apiJSON; entrar.js e entrar-google.js ficam (login com CSRF explícito)
- [x] Texto do prazo do cartão: sem o número
- [x] Testes de regressão, 4 rodadas do revisor, commit, push e publicação
- [ ] Filmes onetake A e B: render final 4K60 (em andamento)

## Ficou para depois (revisor, não bloqueia)
- Devolução automática travada fica só na lista, sem aviso à equipe.
- Aumento de valor de conferência aberta não avisa a equipe.
- valorDaConferencia consulta as pendências por linha da lista (N+1).
- Sem teste de integração com transação real para a conferência.
