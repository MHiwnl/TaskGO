-- Reserva da devolução no provedor: antes de pedir ao Asaas, a parte fica marcada aqui; só some quando a devolução é confirmada ou recusada.
-- Sem a reserva, uma devolução sem resposta (tempo esgotado) era pedida de novo e o cliente recebia duas vezes.
ALTER TABLE cobrancas ADD COLUMN IF NOT EXISTS valor_estornando NUMERIC(19,2);
ALTER TABLE cobrancas ADD COLUMN IF NOT EXISTS estornando_desde BIGINT;
