# Filtros do administrador e página Log

**Situação:** concluída
**Pedido do usuário:** filtro em Usuários (cadastro, nome, situação e tipo) e em Pedidos. Movimentações sai da Visão geral e vira a página Log, com filtro por usuário. No fim, uma lista nova do que falta.

## Tarefas
- [x] Backend: filtros de usuários (nome/e-mail, tipo, situação, período de cadastro).
- [x] Backend: filtros de pedidos (busca, etapa, categoria, urgente, período).
- [x] Backend: /api/admin/log com eventos de todas as fontes, filtro por usuário, tipo e período.
- [x] Site: filtros em Usuários e Pedidos; aba Log; Movimentações sai da Visão geral.
- [x] Testes e conferência no navegador.
- [x] Lista nova do que falta.

## Feito
- Usuários: nome ou e-mail, tipo, situação (ativa, online, offline, com aviso, suspensa, banida) e período de cadastro; botão Ver log em cada conta.
- Pedidos: busca, etapa, categoria, período de criação e só urgentes.
- Log: aba própria, agrupado por dia, filtros por usuário, tipo de evento e período; clicar no nome filtra pela conta. Mensagens sem o texto.
- 87 testes do backend passando; conferido no navegador, desktop e celular.
