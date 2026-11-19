-- Pagamento em dinheiro: o que cada conta deve.
-- Cliente: serviço não pago ou taxa de cancelamento (bloqueia novos pedidos até pagar; o valor vai para quem atendeu).
-- Trabalhador: taxa da TaskGO de um serviço recebido em dinheiro (descontada do saldo ou cobrada no balanço semanal).
CREATE TABLE IF NOT EXISTS pendencias (
  id VARCHAR(40) PRIMARY KEY,
  usuario_id BIGINT NOT NULL,
  credor_id BIGINT,
  pedido_id VARCHAR(255) NOT NULL,
  tipo VARCHAR(30) NOT NULL,
  valor NUMERIC(19,2) NOT NULL,
  pago NUMERIC(19,2) NOT NULL DEFAULT 0,
  status VARCHAR(15) NOT NULL,
  cobranca_id VARCHAR(40),
  criada_em BIGINT NOT NULL,
  paga_em BIGINT,
  CONSTRAINT ck_pendencias_valor CHECK (valor > 0 AND pago >= 0 AND pago <= valor)
);
CREATE INDEX IF NOT EXISTS idx_pendencias_usuario ON pendencias (usuario_id, status);
CREATE INDEX IF NOT EXISTS idx_pendencias_status_tipo ON pendencias (status, tipo);
CREATE UNIQUE INDEX IF NOT EXISTS uk_pendencias_pedido_tipo ON pendencias (pedido_id, tipo);
