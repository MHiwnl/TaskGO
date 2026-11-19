-- Obras: serviços de vários dias e de alto valor, contratados por orçamento com etapas.
CREATE TABLE IF NOT EXISTS orcamentos (
  id VARCHAR(40) PRIMARY KEY,
  pedido_id VARCHAR(255) NOT NULL,
  profissional_id BIGINT NOT NULL,
  profissional_email VARCHAR(255) NOT NULL,
  profissional_nome VARCHAR(255),
  etapas TEXT NOT NULL,
  total NUMERIC(19,2) NOT NULL,
  material VARCHAR(15) NOT NULL,
  lista_material TEXT,
  inicio BIGINT NOT NULL,
  dias_trabalho VARCHAR(20) NOT NULL,
  horario VARCHAR(20) NOT NULL,
  previsao_fim BIGINT NOT NULL,
  observacao TEXT,
  status VARCHAR(15) NOT NULL,
  contrato_pro_em BIGINT,
  contrato_cliente_em BIGINT,
  criado_em BIGINT NOT NULL,
  respondido_em BIGINT,
  CONSTRAINT ck_orcamentos_total CHECK (total > 0)
);
CREATE INDEX IF NOT EXISTS idx_orcamentos_pedido ON orcamentos (pedido_id, status);
CREATE INDEX IF NOT EXISTS idx_orcamentos_pro ON orcamentos (profissional_id, status);

CREATE TABLE IF NOT EXISTS visitas_tecnicas (
  id VARCHAR(40) PRIMARY KEY,
  pedido_id VARCHAR(255) NOT NULL,
  profissional_id BIGINT NOT NULL,
  profissional_nome VARCHAR(255),
  valor NUMERIC(19,2) NOT NULL,
  data BIGINT NOT NULL,
  status VARCHAR(15) NOT NULL,
  cobranca_id VARCHAR(40),
  criada_em BIGINT NOT NULL,
  realizada_em BIGINT,
  liberada_em BIGINT
);
CREATE INDEX IF NOT EXISTS idx_visitas_pedido ON visitas_tecnicas (pedido_id);
CREATE INDEX IF NOT EXISTS idx_visitas_status ON visitas_tecnicas (status);

CREATE TABLE IF NOT EXISTS etapas_obra (
  id VARCHAR(40) PRIMARY KEY,
  pedido_id VARCHAR(255) NOT NULL,
  orcamento_id VARCHAR(40) NOT NULL,
  ordem INT NOT NULL,
  nome VARCHAR(120) NOT NULL,
  entrega TEXT,
  valor NUMERIC(19,2) NOT NULL,
  desconto NUMERIC(19,2) NOT NULL DEFAULT 0,
  dias INT NOT NULL,
  status VARCHAR(15) NOT NULL,
  cobranca_id VARCHAR(40),
  pago NUMERIC(19,2) NOT NULL DEFAULT 0,
  liberado NUMERIC(19,2) NOT NULL DEFAULT 0,
  devolvido NUMERIC(19,2) NOT NULL DEFAULT 0,
  material_liberado NUMERIC(19,2) NOT NULL DEFAULT 0,
  aditivo BOOLEAN NOT NULL DEFAULT FALSE,
  fotos TEXT,
  problema TEXT,
  paga_em BIGINT,
  iniciada_em BIGINT,
  entregue_em BIGINT,
  liberar_apos BIGINT,
  aprovada_em BIGINT
);
CREATE INDEX IF NOT EXISTS idx_etapas_pedido ON etapas_obra (pedido_id, ordem);
CREATE INDEX IF NOT EXISTS idx_etapas_status ON etapas_obra (status);
CREATE INDEX IF NOT EXISTS idx_etapas_cobranca ON etapas_obra (cobranca_id);

CREATE TABLE IF NOT EXISTS diario_obra (
  id VARCHAR(40) PRIMARY KEY,
  pedido_id VARCHAR(255) NOT NULL,
  autor_id BIGINT NOT NULL,
  dia VARCHAR(10) NOT NULL,
  chegada_em BIGINT NOT NULL,
  saida_em BIGINT,
  distancia_m INT,
  texto TEXT,
  foto_id VARCHAR(40)
);
CREATE INDEX IF NOT EXISTS idx_diario_pedido ON diario_obra (pedido_id, chegada_em);
