-- Agenda do profissional: horários bloqueados (folga, almoço, outro compromisso).
CREATE TABLE IF NOT EXISTS agenda_bloqueios (
  id VARCHAR(40) PRIMARY KEY,
  usuario_id BIGINT NOT NULL,
  inicio BIGINT NOT NULL,
  fim BIGINT NOT NULL,
  motivo VARCHAR(80),
  criado_em BIGINT NOT NULL,
  CONSTRAINT ck_bloqueio_intervalo CHECK (fim > inicio)
);
CREATE INDEX IF NOT EXISTS idx_bloqueios_usuario_fim ON agenda_bloqueios (usuario_id, fim);
