-- Pagamento real pelo Asaas: link da página de pagamento (cartão) e resgate do profissional por Pix.
alter table cobrancas add column if not exists link_pagamento varchar(400);
alter table solicitacoes_saque add column if not exists transferencia_id varchar(60);
alter table solicitacoes_saque add column if not exists chave_pix varchar(140);
