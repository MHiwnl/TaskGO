-- Sessões abertas de cada conta: a pessoa vê onde está conectada e encerra as que não reconhece.
create table if not exists sessoes_conta (
    id varchar(64) primary key,
    usuario_id bigint not null,
    dispositivo varchar(120),
    ip varchar(64),
    criada_em bigint not null,
    vista_em bigint not null,
    encerrada_em bigint
);
create index if not exists idx_sessoes_usuario on sessoes_conta (usuario_id, encerrada_em);
