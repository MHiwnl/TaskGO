# 44 — Pagamento real com Asaas (sandbox)

Pedido: "sobre o pagamento hoje, vamos resolver" — gateway gratuito escolhido: Asaas.

## Passos
- [x] Cliente HTTP do Asaas (`AsaasCliente`): clientes, cobranças Pix/cartão, QR Pix, estorno, transferência Pix
- [x] `PagamentoService` no modo `asaas`: Pix com QR real, cartão novo na página do Asaas, cartão salvo por token
- [x] Webhook `/api/pagamentos/webhook/asaas` com token + conferência periódica
- [x] Resgate do profissional por chave Pix (`ResgateService`, V17)
- [x] Tela: cartão na página do Asaas, chave Pix e resgate na carteira
- [x] Ligar `TASKGO_PAGAMENTOS_MODO=asaas`, rebuild e testes do backend
- [x] Teste ponta a ponta no sandbox (Pix, cartão, estorno, resgate)
- [x] `npm test`, docs (.env.example, README, PRODUCT.md), limpeza
