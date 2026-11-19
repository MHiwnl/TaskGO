# 40 — Pagamento simulado (Pix + cartão)

- [x] Tirar a faixa de preço do Contratar: o cliente digita qualquer valor
- [x] Simulador Pix (APIPXINTER) em `pagamentos/pix-simulador`, rodando no Docker na porta 3100
  - [x] Webhook de verdade para a API, com token
  - [x] CRC16 correto no copia-e-cola, sem links de terceiros
- [x] Backend: cartões salvos (só token, bandeira, final e validade), cobranças, simulador de cartão
- [x] Pedido nasce "aguardando pagamento"; só vai para o mural / ondas depois de pago
  - Cartão salvo: cobrado na hora do envio
  - Pix: paga antes de procurar profissional
- [x] TaskGO segura o valor; ao concluir, libera 85% na carteira do profissional (taxa 15%)
- [x] Cancelou: devolve o que foi pago
- [x] Proposta com valor maior: cobra a diferença antes do profissional chegar; menor: devolve a sobra ao concluir
- [x] Telas: escolha do pagamento no Contratar, tela Pagar (QR, copia-e-cola, simular), cartões em Configurações, resumo do pagamento no pedido
- [x] Textos públicos que dizem que pagamento não existe → modo de teste
- [x] Testes (backend + navegador)

Fica para depois: resgate do profissional (saque), Pix expirado não cancela o pedido sozinho, troca para um gateway real.
