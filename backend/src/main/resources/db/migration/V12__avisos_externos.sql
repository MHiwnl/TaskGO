-- Avisos fora do site: inscrições de push do navegador e chaves geradas pelo próprio sistema (VAPID).
CREATE TABLE IF NOT EXISTS push_inscricoes (
  id VARCHAR(40) PRIMARY KEY,
  usuario_id BIGINT NOT NULL,
  endpoint VARCHAR(1000) NOT NULL,
  p256dh VARCHAR(200) NOT NULL,
  auth VARCHAR(100) NOT NULL,
  criada_em BIGINT NOT NULL
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_push_endpoint ON push_inscricoes (endpoint);
CREATE INDEX IF NOT EXISTS idx_push_usuario ON push_inscricoes (usuario_id);

CREATE TABLE IF NOT EXISTS chaves_sistema (
  nome VARCHAR(60) PRIMARY KEY,
  valor TEXT NOT NULL
);
