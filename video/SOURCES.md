# Fontes de cada número e afirmação do filme

Todo número na tela ou na fala tem uma linha aqui. Se não tem, sai do filme.

| No filme | Valor | Fonte |
|---|---|---|
| Profissionais avisados por vez | 10 por onda | `backend/src/main/java/com/taskgo/api/ChamarAgoraService.java` (`POR_ONDA = 10`) |
| Chegada só vale perto do endereço | até 150 m | `backend/src/main/java/com/taskgo/api/LocalizacaoPedidoService.java` (`RAIO_CHEGADA_M=150`) |
| Código de chegada | 4 números | `backend/src/main/java/com/taskgo/api/PedidoService.java` (`String.format("%04d", ...)`) |
| Identidade verificada (documento e selfie) | — | `PRODUCT.md`, "Verificação de identidade"; `VerificacaoController.java` |
| Endereço só depois de confirmar quem vem | — | `PRODUCT.md`, "Confirmação do cliente" e linha 28 |
| Pagar só depois, direto ao profissional | — | `PRODUCT.md`, "Pagamento direto"; o pedido mostra "Não pague antes" |
| Serviços no catálogo (só se aparecer) | 105 | `js/core/catalogo.js` (105 itens) e `PRODUCT.md`, "Catálogo de serviços" |
| Valor do exemplo | R$ 120 | Exemplo do roteiro, marcado como exemplo; não é dado de uso |
| "Na hora": aviso sai no momento do pedido | — | `ChamarAgoraService.java` (primeira onda disparada ao criar o pedido "Chamar agora") |
| "Ao vivo, no mapa" | — | `LocalizacaoPedidoController.java` / `LocalizacaoPedidoService.java` (posição do profissional a caminho, atualizada durante o trajeto) |
| "Só paga quando o serviço estiver pronto" | — | Mesma fonte de "Pagar só depois": `PRODUCT.md`, "Pagamento direto" ("Não pague antes") |
| "Um quilômetro" na fala | ~1 km | Arredondamento do exemplo de 1,1 km abaixo |
| Distância "longe" na virada | 1,1 km | Exemplo; mesmo caso testado contra o servidor real (1112 m recusado), `atv/done/34-backend-fases.md` |
| Chegada perto que vale, na virada | 80 m | Exemplo; dentro do raio de 150 m de `LocalizacaoPedidoService.java` (`RAIO_CHEGADA_M=150`) |
| QR do Pix na tela | — | Padrão aleatório com semente, só para ilustrar: não é um BR Code válido e não abre pagamento |
