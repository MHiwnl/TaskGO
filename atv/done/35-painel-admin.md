# Painel do administrador

**Situação:** concluída
**Pedido do usuário:** o administrador tem acesso à moderação e aos dados do site: uma Visão geral com muitos dados, cards, infográficos, movimentações, usuários etc.

## Tarefas
- [x] Backend: /api/admin/visao-geral com números agregados (usuários, pedidos, propostas, moderação, por dia e por categoria).
- [x] Backend: listas de usuários e de pedidos para o administrador, com busca e filtro.
- [x] Site: área do administrador com abas Visão geral, Usuários, Pedidos e Moderação.
- [x] Visão geral: indicadores, gráficos (pedidos por dia, por status, por categoria, cadastros) e movimentações recentes.
- [x] Testes do backend e conferência no navegador (desktop e celular).

## Fora do escopo
- Dados de pagamento (pagamento fica para depois).

## Feito também
- Corrigido: a proposta aceita ficava gravada como recusada (a atualização do pedido limpava a sessão do JPA). Dados locais corrigidos.
- Data de cadastro das contas (migração V3); contas antigas ficam sem data e fora dos gráficos por dia.
- 85 testes do backend passando; conferido no navegador, escuro, claro e celular.
- Ajuste pedido: o administrador é um tipo próprio de conta (nem cliente nem profissional, sem pedidos). Área só com Visão geral, Usuários, Pedidos e Moderação; sem endereços nem exclusão de conta no perfil.
