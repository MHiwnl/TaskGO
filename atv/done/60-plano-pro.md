# 60 — Plano Pro para o profissional

Pedido: "coloque planos dentro do site" → escolhido: plano pago só para o profissional.

## Decisões do usuário
- Preço: R$ 29,90/mês.
- Cobrança: cartão (renova sozinho) ou Pix (paga todo mês); o profissional escolhe.
- Benefícios:
  - Taxa de 10% nos serviços comuns e no recorrente (no lugar de 15% e 12%). Obras e ajuda rápida mantêm a taxa delas.
  - Destaque nas buscas (aparece primeiro na categoria) e selo "Pro" no perfil.
  - Recebe o "Chamar agora" antes (entre os mais perto, quem é Pro vem antes na onda).
  - Até 20 categorias (em vez de 10).
- Consequência: textos "sem mensalidade" mudam para "sem mensalidade obrigatória" / plano opcional.

## Fases
- [x] 1. Mapear: cálculo da taxa, cobrança Pix/cartão pelo Asaas (pendências), webhook, ondas do Chamar agora, lista de profissionais, limite de categorias.
- [x] 2. Backend: V26 assinaturas, Assinatura/Repository, PlanoService (assinar, pagar mês, cancelar/reativar, trocar forma, cobrancaPaga, renovar a cada 30 min: cartão cobra no último dia e por +3 dias; Pix avisa 3 dias antes; encerra 3 dias depois; corta para 10 categorias), PlanoController (/api/plano), PagamentoService.cobrarPlano (tipo "plano", ref "plano:<id>").
- [x] 3. Benefícios: taxaDe 10% (comum e recorrente), lista com Pro primeiro + campo pro, ordemDaOnda (Pro sobe uma onda), maximo 20 categorias, Financeiro com planosMes/assinantesPro/origem.
- [x] Correção encontrada no teste: confirmação dupla de pagamento (aviso do provedor + consulta da tela ao mesmo tempo) — marcarPaga atômico em conferirPix.
- Testes: unidade 147/0; e2e plano 32/0; direto 36/0; carteira 14/0; ajuda direto ok; financeiro do plano 3/0.
- [x] 4a. Telas (feito): js/painel/plano-pro.js (#/app/plano: faixa azul com preço e situação, benefícios em linhas, assinar por cartão salvo/cartão novo/Pix com QR, pagar o mês, trocar forma, cancelar/voltar a renovar), bloco na Carteira, atalho no Início, selo Pro (Perfil, aceite do pedido, Chamar agora, orçamentos de obra, lista de serviços), limite de categorias vindo do servidor, taxa exibida 10% para o Pro, evento ao vivo "plano". CSS em cores.css (.selo-pro) e painel.css (.plano-*).
  - Achado no teste: `dataCurta` duplicada derrubava o obra.js inteiro; removida a cópia.
  - Teste de tela (Chrome sem janela, site local): 16/16, sem erro de JS, sem rolagem lateral em 1280 e 390. npm test 26/26.
- [x] 4b. (19/11) Feito: página pública Preços (faixa azul do Pro abaixo dos dois planos, sem tríade) e textos "sem mensalidade" (home, entrar, faq, sobre, planos) → "sem mensalidade obrigatória" para quem atende; FAQ "O que é o Plano Pro?".
- [x] 4c. (19/11) Feito: Financeiro do admin mostrar planosMes, assinantesPro e a origem (o backend já manda).
- [x] 5a. PRODUCT.md atualizado (Plano Pro, Financeiro, estado do produto).
- [x] 5b. Publicado em 19/11 (commit 21e1ce3, push, iniciar-producao.ps1; https://taskgohq.com.br).
- E-mail da API religado em 19/11 (`docker compose up -d --no-build api`).

## 19/11/2026
- Preços: faixa azul do Pro abaixo dos dois planos (faixaPro em planos.js, .pro-faixa em site.css); FAQ "O que é o Plano Pro?" (FAQ[8]); "sem mensalidade obrigatória" em planos, entrar, home, profissionais, faq e sobre.
- Financeiro do admin: Recebido hoje/mês (com o valor do Pro), Assinantes Pro, coluna Origem.
- index.html: todos os ?v= = 202611191000.
- Testes: npm test 26/26; node --check ok; Preços conferido em 1280 e 390 px (sem rolagem lateral).
