# 67 — Correções da auditoria ponytail

Pedido: "Pode fazer tudo, os urgentes e os depois!"

- [x] 1. Limitadores por IP real (forward-headers) + IP da sessão
- [x] 2. Taxa em dobro: registrarTaxa idempotente por origem + receber com reserva atômica
- [x] 3. Resgate sem resposta do Asaas não devolve saldo (VERIFICAR)
- [x] 4. Transferência do admin limitada ao saldo livre (Asaas − carteiras)
- [x] 5. Meus dados sem findAll
- [x] 6. Prazos (3 h e obras) filtrando no banco
- [x] 7. Assinatura com @Version
- [x] 8. Lista de profissionais com avaliações numa consulta
- [x] 9. Testes dos caminhos de dinheiro
- [x] 10. Acentos quebrados

## Resultado (19/11)
- mvn test 150/0 (3 novos em TravasDinheiroTest); npm test 26/26; node --check ok.
- Postgres local: V27 aplicada; /api/profissionais, /api/conta/meus-dados e login ok; IP da sessão = cliente (172.19.0.1), não o nginx; tarefas de obra sem erro.
- Falta publicar (commit + iniciar-producao.ps1). V27 roda sozinha na subida da produção.
