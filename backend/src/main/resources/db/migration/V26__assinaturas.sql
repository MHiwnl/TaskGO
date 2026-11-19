-- Plano Pro do profissional: R$ 29,90 por mês, por cartão (renova sozinho) ou Pix (paga a cada mês).
-- Os benefícios valem enquanto pago_ate estiver no futuro; cancelar só para a renovação.
CREATE TABLE IF NOT EXISTS assinaturas (
  id VARCHAR(40) PRIMARY KEY,
  usuario_id BIGINT NOT NULL,
  forma VARCHAR(10) NOT NULL,
  cartao_id VARCHAR(40),
  status VARCHAR(12) NOT NULL,
  valor NUMERIC(19,2) NOT NULL,
  pago_ate BIGINT,
  criada_em BIGINT NOT NULL,
  cancelada_em BIGINT,
  ultima_tentativa BIGINT,
  aviso_enviado BIGINT
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_assinaturas_usuario ON assinaturas (usuario_id);
CREATE INDEX IF NOT EXISTS idx_assinaturas_status ON assinaturas (status);
