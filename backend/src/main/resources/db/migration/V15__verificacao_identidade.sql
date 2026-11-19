-- Verificação de identidade: documento (frente e verso) e selfie, revisados pelo administrador.
-- As imagens são apagadas assim que a decisão é tomada; fica só o resultado.
CREATE TABLE IF NOT EXISTS verificacoes (
  id VARCHAR(40) PRIMARY KEY,
  usuario_id BIGINT NOT NULL,
  status VARCHAR(12) NOT NULL,
  doc_frente BYTEA,
  doc_verso BYTEA,
  selfie BYTEA,
  motivo VARCHAR(300),
  enviada_em BIGINT NOT NULL,
  decidida_em BIGINT,
  decidida_por BIGINT
);
CREATE INDEX IF NOT EXISTS idx_verificacoes_usuario ON verificacoes (usuario_id, enviada_em);
CREATE INDEX IF NOT EXISTS idx_verificacoes_status ON verificacoes (status, enviada_em);
