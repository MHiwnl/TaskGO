# Simulador Pix

Cópia do [inematds/APIPXINTER](https://github.com/inematds/APIPXINTER) (licença ISC, conforme o package.json do projeto), usada pela TaskGO enquanto o pagamento é simulado. Imita a API Pix do Banco Inter: cria cobranças, devolve QR e copia-e-cola, e simula o pagamento.

Mudanças da TaskGO:
- porta 3100;
- o aviso de pagamento é enviado de verdade para `WEBHOOK_URL`, com `X-Webhook-Token: WEBHOOK_TOKEN` (3 tentativas);
- copia-e-cola no formato EMV com CRC16 correto (a chave é o txid, então nenhum banco paga de verdade);
- links fixos para sites de terceiros removidos.

Roda pelo `docker-compose.yml` da raiz (serviço `pix`). Painel do simulador: http://localhost:3100
