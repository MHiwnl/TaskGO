-- Propostas de valor entre cliente e profissional, e recursos da moderação da conversa.
CREATE TABLE IF NOT EXISTS propostas (
  id VARCHAR(255) PRIMARY KEY,
  pedido_id VARCHAR(255) NOT NULL,
  profissional_id BIGINT NOT NULL,
  profissional_email VARCHAR(255) NOT NULL,
  profissional_nome VARCHAR(255),
  autor VARCHAR(32) NOT NULL,
  valor DOUBLE PRECISION NOT NULL,
  status VARCHAR(32) NOT NULL,
  criada_em BIGINT NOT NULL,
  respondida_em BIGINT
);
CREATE INDEX IF NOT EXISTS idx_propostas_pedido ON propostas(pedido_id, criada_em);
CREATE INDEX IF NOT EXISTS idx_propostas_profissional ON propostas(pedido_id, profissional_id);

CREATE TABLE IF NOT EXISTS recursos_moderacao (
  id VARCHAR(255) PRIMARY KEY,
  usuario_id BIGINT NOT NULL,
  texto TEXT NOT NULL,
  status VARCHAR(32) NOT NULL,
  criado_em BIGINT NOT NULL,
  decidido_em BIGINT,
  decidido_por VARCHAR(255),
  resposta TEXT
);
CREATE INDEX IF NOT EXISTS idx_recursos_usuario ON recursos_moderacao(usuario_id, criado_em);
CREATE INDEX IF NOT EXISTS idx_recursos_status ON recursos_moderacao(status, criado_em);
