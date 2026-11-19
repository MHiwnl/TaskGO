-- Painel do administrador em escala: buscas exatas, filtros da auditoria, resumo diário e exportações em segundo plano.

-- Buscar: CPF e telefone exatos; nome pelo começo (text_pattern_ops permite LIKE 'ana%').
create index if not exists idx_usuarios_cpf on usuarios using btree (cpf);
create index if not exists idx_usuarios_telefone on usuarios using btree (telefone);
create index if not exists idx_usuarios_nome_prefixo on usuarios using btree (lower(nome) text_pattern_ops);
create index if not exists idx_usuarios_criado on usuarios using btree (criado_em);
create index if not exists idx_usuarios_tipo_lower on usuarios using btree (lower(tipo), criado_em);

-- Atenção e números: pedidos por etapa e tempo; aceite e criação em intervalo.
create index if not exists idx_pedidos_status_aceite on pedidos using btree (lower(status), accepted_at);
create index if not exists idx_pedidos_created on pedidos using btree (created);
create index if not exists idx_pedidos_accepted on pedidos using btree (accepted_at);

-- Auditoria: por tipo, por pedido e por autor, sempre em ordem de data (cursor).
create index if not exists idx_historico_tipo on historico_conta using btree (tipo, criado_em desc, id desc);
create index if not exists idx_historico_pedido on historico_conta using btree (pedido_id, criado_em desc);
create index if not exists idx_historico_autor on historico_conta using btree (autor_id, criado_em desc);
create index if not exists idx_historico_cursor on historico_conta using btree (criado_em desc, id desc);

-- Filas: só o que está em análise, do mais antigo para o mais novo.
create index if not exists idx_denuncias_status on denuncias using btree (status, criada_em);
create index if not exists idx_denuncias_denunciado on denuncias using btree (denunciado_id, criada_em);
create index if not exists idx_recursos_status on recursos_moderacao using btree (status, criado_em);
create index if not exists idx_chamados_status on chamados using btree (status, criado_em);
create index if not exists idx_propostas_criada on propostas using btree (criada_em);

-- Resumo diário (fuso de Recife), calculado à noite; o dia de hoje é calculado na hora.
create table if not exists metricas_diarias (
  dia date primary key,
  pedidos bigint not null default 0,
  concluidos bigint not null default 0,
  cancelados bigint not null default 0,
  valor_concluido numeric(19,2) not null default 0,
  contas_novas bigint not null default 0,
  clientes_novos bigint not null default 0,
  profissionais_novos bigint not null default 0,
  propostas bigint not null default 0,
  atualizado_em bigint not null
);

-- Exportações em CSV geradas em segundo plano (guardadas por 7 dias).
create table if not exists exportacoes (
  id varchar(40) primary key,
  autor_id bigint not null,
  tipo varchar(20) not null,
  de date not null,
  ate date not null,
  status varchar(12) not null,
  linhas bigint not null default 0,
  arquivo bytea,
  erro varchar(300),
  criada_em bigint not null,
  pronta_em bigint
);
create index if not exists idx_exportacoes_autor on exportacoes using btree (autor_id, criada_em desc);
