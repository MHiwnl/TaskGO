-- Pagamento (simulado por enquanto): cartões salvos e cobranças dos pedidos.
-- Do cartão só ficam o token do provedor, a bandeira, o final e a validade; número completo e CVV nunca são guardados.
CREATE TABLE IF NOT EXISTS cartoes (
  id VARCHAR(40) PRIMARY KEY,
  usuario_id BIGINT NOT NULL,
  token VARCHAR(120) NOT NULL,
  bandeira VARCHAR(20) NOT NULL,
  final4 VARCHAR(4) NOT NULL,
  mes_validade INT NOT NULL,
  ano_validade INT NOT NULL,
  titular VARCHAR(120) NOT NULL,
  padrao BOOLEAN NOT NULL DEFAULT FALSE,
  resultado_simulado VARCHAR(30),
  criado_em BIGINT NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_cartoes_usuario ON cartoes (usuario_id);

CREATE TABLE IF NOT EXISTS cobrancas (
  id VARCHAR(40) PRIMARY KEY,
  pedido_id VARCHAR(255) NOT NULL,
  cliente_id BIGINT NOT NULL,
  metodo VARCHAR(10) NOT NULL,
  tipo VARCHAR(15) NOT NULL,
  valor NUMERIC(19,2) NOT NULL,
  status VARCHAR(15) NOT NULL,
  motivo VARCHAR(200),
  provedor VARCHAR(30) NOT NULL,
  txid VARCHAR(40),
  pix_copia_cola TEXT,
  qr_code TEXT,
  expira_em BIGINT,
  cartao_id VARCHAR(40),
  cartao_descricao VARCHAR(60),
  parcelas INT,
  valor_estornado NUMERIC(19,2) NOT NULL DEFAULT 0,
  criada_em BIGINT NOT NULL,
  paga_em BIGINT,
  estornada_em BIGINT,
  CONSTRAINT ck_cobrancas_valor CHECK (valor > 0 AND valor_estornado >= 0 AND valor_estornado <= valor)
);
CREATE INDEX IF NOT EXISTS idx_cobrancas_pedido ON cobrancas (pedido_id);
CREATE UNIQUE INDEX IF NOT EXISTS uk_cobrancas_txid ON cobrancas (txid) WHERE txid IS NOT NULL;
