-- Categorias de cada profissional (a principal continua em usuarios.service; aqui ficam todas, até 10).
CREATE TABLE IF NOT EXISTS profissional_servicos (
  id VARCHAR(80) PRIMARY KEY,
  usuario_id BIGINT NOT NULL,
  servico VARCHAR(40) NOT NULL,
  CONSTRAINT uk_profissional_servico UNIQUE (usuario_id, servico)
);
CREATE INDEX IF NOT EXISTS idx_profissional_servicos_servico ON profissional_servicos (servico);
INSERT INTO profissional_servicos (id, usuario_id, servico)
SELECT id || ':' || service, id, service FROM usuarios WHERE lower(tipo) = 'profissional' AND service IS NOT NULL AND service <> ''
ON CONFLICT DO NOTHING;
