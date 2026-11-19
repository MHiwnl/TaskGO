# 43 — Painel do administrador para 1 milhão de contas

**Pedido do usuário:** reformular o painel para fazer sentido em escala (nada de listar tudo). Aprovou a proposta: "Faça".

- [x] Hoje: pendências, 6 números com variação, alertas
- [x] Filas de trabalho: denúncias, recursos, verificações, problemas de pagamento, chamados (paginadas no banco)
- [x] Buscar: e-mail, CPF, telefone, nº do pedido ou nome → ficha (com registro de quem viu)
- [x] Pedidos que precisam de atenção (travados)
- [x] Auditoria: historico_conta com filtros e paginação por cursor; eventos de pedido e ações da equipe registrados; retenção de 12 meses
- [x] Números: resumo diário (metricas_diarias) calculado à noite + hoje ao vivo
- [x] Exportar CSV em segundo plano, com período obrigatório
- [x] Papéis da equipe: administrador, moderador, financeiro, suporte
- [x] Índices para as buscas e filtros

Testado: 117 testes do backend, 23 do site, 29 checagens do painel pela API (papéis, Hoje, Buscar, Atenção, Auditoria, Números, Exportar) e cursor da auditoria com 120 eventos (3 páginas, sem repetir). Partição mensal da auditoria fica para quando o volume pedir.
