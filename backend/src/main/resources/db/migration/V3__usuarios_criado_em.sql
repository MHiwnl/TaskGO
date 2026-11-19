-- Momento do cadastro, para os números do painel do administrador. Contas antigas ficam sem data.
alter table usuarios add column if not exists criado_em bigint;
create index if not exists idx_usuarios_criado_em on usuarios (criado_em);
create index if not exists idx_pedidos_created on pedidos (created);
