-- Onde o profissional online está agora (para o Chamar Agora mostrar quem está perto e estimar a chegada).
create table if not exists presenca_profissional (
    usuario_id bigint primary key,
    latitude double precision not null,
    longitude double precision not null,
    precisao double precision not null,
    atualizada_em bigint not null
);
create index if not exists idx_presenca_atualizada on presenca_profissional using btree (atualizada_em);
-- Avisos do sistema enviados pelo administrador (histórico por aviso, não por pessoa).
create index if not exists idx_notificacoes_tipo on notificacoes using btree (tipo, criada_em desc);
