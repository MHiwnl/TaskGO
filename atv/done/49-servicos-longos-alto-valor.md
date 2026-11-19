# 49 — Plano: serviços de vários dias e de alto valor

Pedido: "Faça um plano para serviços onde você precisa do profissional por vários dias, como pedreiro, e serviços acima de 1000 reais, 2000, 3000, 4000, 5000, 10000."

Estado: plano para aprovação (nada implementado).

## Ideia central
Serviço grande vira **obra em etapas**: o profissional manda um orçamento com etapas (valor, dias, o que entrega), o cliente paga cada etapa antes de ela começar, a TaskGO segura e libera quando o cliente aprova a etapa (com fotos). Ninguém arrisca o valor inteiro de uma vez.

## Faixas de valor
| Faixa | Regras |
|---|---|
| Até R$ 999 | Como hoje |
| R$ 1.000 a 2.999 | Orçamento obrigatório; 1 a 3 etapas |
| R$ 3.000 a 4.999 | Pelo menos 2 etapas; visita técnica recomendada; profissional com 3+ serviços e nota 4+ |
| R$ 5.000 a 9.999 | Pelo menos 3 etapas; contrato com aceite registrado; 10+ serviços e nota 4,5+ (ou MEI/CNPJ) |
| R$ 10.000+ | Etapas de no máximo 30%; equipe (financeiro) confere antes de liberar a 1ª etapa; garantia retida |

## Fases de implementação
1. Orçamento com etapas e visita técnica
2. Pagamento por etapa (Pix, cartão até 12x, boleto) e liberação por aprovação
3. Vários dias: agenda de vários dias, diário da obra com check-in/out e fotos
4. Faixas de valor, exigências de reputação, contrato, revisão da equipe e garantia retida
5. Problemas por etapa, cancelamento no meio e mediação

## Decisões do usuário
- Taxa por faixa: 15% / 12% / 10% / 8% / 6% (aceita)
- Visita técnica: R$ 50 a R$ 100, abatida se contratar (aceita)
- Juros do parcelamento acima de 6x: pagos pelo cliente
- Dinheiro em obras: não (sem pagamento em dinheiro acima de R$ 1.000)
- Garantia retida: 8% do total, por 7 dias depois da última etapa, em obras a partir de R$ 3.000

## Passos
- [x] Fase 1: orçamento com etapas e visita técnica
- [x] Fase 2: pagamento por etapa (Pix, cartão até 12x com juros do cliente, boleto) e liberação por aprovação
- [x] Fase 3: agenda de vários dias e diário da obra
- [x] Fase 4: faixas (taxa 15/12/10/8/6%, exigências de reputação, contrato, revisão da equipe, garantia de 8%, sem dinheiro acima de R$ 1.000)
- [x] Fase 5: problemas por etapa, cancelamento no meio e mediação
