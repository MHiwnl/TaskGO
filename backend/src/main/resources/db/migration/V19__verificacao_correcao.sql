-- Verificação recusada: além do motivo, o que a pessoa precisa fazer para corrigir.
ALTER TABLE verificacoes ADD COLUMN IF NOT EXISTS correcao TEXT;
