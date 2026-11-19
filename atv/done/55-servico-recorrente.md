# 55 — Serviço recorrente

Pedido: no "Quanto você oferece", bloco opcional "Serviço recorrente por quantos dias?"; total = valor × vezes. Decisões: cobrança por semana; limite de 30 vezes (explicado); só em serviços cobrados por dia ou visita.

## Passos
- [x] Servidor: visitas do pedido recorrente (V23), datas pela frequência, até 30 vezes, só categorias por dia/visita
- [x] Pagamento por semana (antes de a semana começar), cada vez liberada na confirmação do cliente ou em 24 h; "não veio" devolve a vez
- [x] Aceite agenda todas as datas; cancelar devolve o pago e não feito
- [x] Site: bloco opcional no valor (vezes, frequência, início, horário, total), painel das semanas no pedido, cartão da oportunidade
- [x] Testes e docs
