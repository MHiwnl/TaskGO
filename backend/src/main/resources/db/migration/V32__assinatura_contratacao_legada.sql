-- Corrige o preenchimento da V31: o último pagamento podia ser uma renovação e abria a desistência
-- de 7 dias para quem só renovou. O início da contratação passa a ser o primeiro pagamento do plano
-- (conservador: quem voltou a assinar antes desta versão não ganha a janela). Só linhas sem desistência.
UPDATE assinaturas a SET contratada_em = (
  SELECT MIN(c.paga_em) FROM cobrancas c
  WHERE c.pedido_id = 'plano:' || a.id AND c.paga_em IS NOT NULL
) WHERE a.contratada_em IS NOT NULL AND a.desistida_em IS NULL;
