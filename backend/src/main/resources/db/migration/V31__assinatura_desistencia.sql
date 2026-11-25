-- Desistência do Plano Pro em 7 dias (CDC art. 49). contratada_em: início da contratação atual (1º pagamento).
-- Quem já tinha plano pago ganha como início o último pagamento; a janela só vale para contratação nova.
ALTER TABLE assinaturas ADD COLUMN IF NOT EXISTS contratada_em BIGINT;
ALTER TABLE assinaturas ADD COLUMN IF NOT EXISTS desistida_em BIGINT;
UPDATE assinaturas a SET contratada_em = (
  SELECT MAX(c.paga_em) FROM cobrancas c
  WHERE c.pedido_id = 'plano:' || a.id AND c.status = 'paga' AND c.paga_em IS NOT NULL
) WHERE a.pago_ate IS NOT NULL AND a.contratada_em IS NULL;
