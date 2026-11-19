# 57 — Pagamento real sem CNPJ (opção A)

Pedido: pagamento real pelo Asaas no CPF do usuário. O cliente paga direto ao trabalhador (Pix na chave dele ou dinheiro); só a taxa da TaskGO vai para a conta do usuário. "Segurar até confirmar" fica para o futuro.

## Fases
- [x] 1. Modelo `TASKGO_PAGAMENTOS_MODELO=direto` (o retido continua no código, desligado)
  - Pedido comum e ajuda rápida: formas "Pix para o profissional" e "Dinheiro no local"; nada é cobrado ao enviar
  - Chave Pix de quem atende aparece ao cliente depois que ele confirma o profissional; aceitar pedido por Pix exige chave cadastrada
  - Quem atendeu confirma que recebeu; a taxa vira "taxa a pagar", paga por Pix ou cartão pelo Asaas (vai para a conta da TaskGO)
  - Cliente não pagou: paga direto a quem atendeu; quem atendeu marca "o cliente me pagou"
  - Trocar forma: entre Pix direto e dinheiro, uma vez
- [x] 2. Obras: cada etapa e a visita técnica pagas direto ao profissional, que confirma o recebimento; taxa da faixa vira taxa a pagar; sem garantia retida
- [x] 3. Recorrente: cada semana paga direto; o profissional confirma; taxa por semana
- [x] Testes, PRODUCT.md e .env de produção
