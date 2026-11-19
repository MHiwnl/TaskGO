-- Pagamento direto: obras e serviço recorrente geram uma taxa a pagar por etapa ou semana do mesmo pedido.
DROP INDEX IF EXISTS uk_pendencias_pedido_tipo;
CREATE INDEX IF NOT EXISTS idx_pendencias_pedido_tipo ON pendencias (pedido_id, tipo);
