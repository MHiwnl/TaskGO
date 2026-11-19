-- Chamados de emergência abertos pelo botão do pedido e atendidos pela equipe de suporte em tempo real.
create table if not exists chamados (
    id varchar(64) primary key,
    pedido_id varchar(255),
    autor_id bigint not null,
    tipo varchar(20) not null,
    status varchar(20) not null,
    mensagem text,
    latitude double precision,
    longitude double precision,
    criado_em bigint not null,
    assumido_por bigint,
    assumido_em bigint,
    resolvido_em bigint,
    nota text
);
create index if not exists idx_chamados_status on chamados (status, criado_em);
