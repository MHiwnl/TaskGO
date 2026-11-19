-- Fotos do pedido (o problema, o serviço feito) e fotos enviadas na conversa.
-- A imagem é recodificada pelo servidor (sem metadados, como a localização da câmera).
CREATE TABLE IF NOT EXISTS fotos_pedido (
  id VARCHAR(40) PRIMARY KEY,
  pedido_id VARCHAR(255) NOT NULL,
  autor_id BIGINT NOT NULL,
  contexto VARCHAR(10) NOT NULL,
  tipo VARCHAR(20) NOT NULL,
  largura INT NOT NULL,
  altura INT NOT NULL,
  dados BYTEA NOT NULL,
  criada_em BIGINT NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_fotos_pedido ON fotos_pedido (pedido_id, contexto, criada_em);

ALTER TABLE mensagens ADD COLUMN IF NOT EXISTS foto_id VARCHAR(40);
