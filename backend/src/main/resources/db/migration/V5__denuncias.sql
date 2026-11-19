-- Denúncias de usuários, decididas pelo administrador.
create table if not exists denuncias (
    id varchar(64) primary key,
    denunciante_id bigint not null,
    denunciado_id bigint not null,
    pedido_id varchar(255),
    motivo varchar(40) not null,
    texto text,
    status varchar(20) not null,
    criada_em bigint not null,
    decidida_em bigint,
    decidida_por bigint,
    resposta text
);
create index if not exists idx_denuncias_status on denuncias (status, criada_em);
create index if not exists idx_denuncias_denunciado on denuncias (denunciado_id);
