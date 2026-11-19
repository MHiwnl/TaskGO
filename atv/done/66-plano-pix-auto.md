# 66 — Pix do Plano Pro confirmado sozinho + cronômetro

Pedido: conferir a cada 5 s mesmo com a tela apagada, a aba fechada ou em repouso; na tela de pagamento, contagem regressiva (min:s) do prazo do Pix. Sem texto explicando a conferência.

- [x] 1. Backend: tarefa agendada a cada 5 s confere os Pix pendentes do plano (não vencidos)
- [x] 2. Tela: cronômetro regressivo até expirar; ao voltar à aba, confere na hora
- [x] 3. Testes (backend + navegador visível)

## Resultado (19/11)
- PagamentoService.conferirPixDoPlano: @Scheduled a cada 5 s; Pix pendente do plano (inclui vencidos há até 10 min). Pool do agendador = 4.
- Tela: "Esperando o pagamento · expira em mm:ss" (data-fim = expiraEm, 60:00); ao voltar à aba consulta na hora; espera da tela até 1 h.
- Teste: Pix pago no simulador com a API desligada e a aba fechada (aviso não entregue) → plano ativou sozinho ao religar.
- mvn test 147/0; npm test ok.
- Achado: `docker compose build` local falha no bake; compilar com `docker build -t taskgo-api backend`.
