-- Início real do serviço (quando o profissional confirma o código de chegada), para estimar a duração.
ALTER TABLE pedidos ADD COLUMN IF NOT EXISTS iniciado_em BIGINT;
