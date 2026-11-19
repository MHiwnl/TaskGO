-- Histórico com data do que acontece com cada conta: avisos, suspensões, banimentos, recursos, cancelamentos, denúncias, confirmação de e-mail.
create table if not exists historico_conta (
    id varchar(64) primary key,
    usuario_id bigint not null,
    tipo varchar(40) not null,
    texto varchar(500) not null,
    autor_id bigint,
    pedido_id varchar(255),
    criado_em bigint not null
);
create index if not exists idx_historico_usuario on historico_conta (usuario_id, criado_em);
create index if not exists idx_historico_criado on historico_conta (criado_em);
