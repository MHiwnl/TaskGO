-- Taxa por origem: o id da pendência passa a ser "taxa:<origem>" (ex.: taxa:visita:<uuid>), mais longo que 40.
ALTER TABLE pendencias ALTER COLUMN id TYPE VARCHAR(80);
-- Controle de versão da assinatura: a renovação mensal não sobrescreve um pagamento que caiu no meio dela.
ALTER TABLE assinaturas ADD COLUMN IF NOT EXISTS versao BIGINT NOT NULL DEFAULT 0;
