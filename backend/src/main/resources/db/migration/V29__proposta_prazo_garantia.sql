-- Termos do orçamento comparável: prazo, garantia e material declarados pelo profissional em cada proposta.
-- Nulos nas propostas antigas e nas contrapropostas do cliente (que herdam os termos da última proposta do profissional).
ALTER TABLE propostas ADD COLUMN IF NOT EXISTS prazo_dias INT;
ALTER TABLE propostas ADD COLUMN IF NOT EXISTS garantia_dias INT;
ALTER TABLE propostas ADD COLUMN IF NOT EXISTS material_incluso BOOLEAN;
