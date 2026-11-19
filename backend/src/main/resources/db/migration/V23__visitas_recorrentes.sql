-- Serviço recorrente: cada vez do pedido (data, semana, valor). O cliente paga a semana antes; cada vez é liberada na confirmação.
CREATE TABLE IF NOT EXISTS visitas_recorrentes (
  id VARCHAR(40) PRIMARY KEY,
  pedido_id VARCHAR(255) NOT NULL,
  ordem INT NOT NULL,
  data VARCHAR(10) NOT NULL,
  semana INT NOT NULL,
  valor NUMERIC(19,2) NOT NULL,
  status VARCHAR(15) NOT NULL,
  cobranca_id VARCHAR(40),
  feita_em BIGINT,
  liberar_apos BIGINT,
  fechada_em BIGINT
);
CREATE INDEX IF NOT EXISTS idx_visitas_rec_pedido ON visitas_recorrentes (pedido_id, ordem);
CREATE INDEX IF NOT EXISTS idx_visitas_rec_status ON visitas_recorrentes (status);
CREATE INDEX IF NOT EXISTS idx_visitas_rec_cobranca ON visitas_recorrentes (cobranca_id);
