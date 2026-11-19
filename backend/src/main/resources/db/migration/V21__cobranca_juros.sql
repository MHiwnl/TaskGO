-- Parcelamento de 7x a 12x: a parte de juros fica separada do valor do serviço (é paga pelo cliente e não vai ao profissional).
ALTER TABLE cobrancas ADD COLUMN IF NOT EXISTS juros NUMERIC(19,2) NOT NULL DEFAULT 0;
