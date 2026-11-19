-- Índices B-Tree (o padrão do Postgres) para tudo que o sistema filtra, busca ou ordena.
-- Os repositórios comparam e-mail, tipo, serviço e status sem diferenciar maiúsculas:
-- o Spring Data gera upper(...) e as consultas escritas à mão usam lower(...),
-- então os índices são de expressão para que o banco consiga usá-los.

-- Login, cadastro e busca por e-mail
create index if not exists idx_usuarios_email_upper on usuarios using btree (upper(email));
create index if not exists idx_usuarios_email_lower on usuarios using btree (lower(email));
-- Lista de profissionais por categoria e filtros do painel do administrador
create index if not exists idx_usuarios_tipo_servico on usuarios using btree (upper(tipo), upper(service), nome);
create index if not exists idx_usuarios_nome on usuarios using btree (lower(nome));

-- Pedidos: meus pedidos (cliente e profissional), mural por status e categoria, relatórios por data
create index if not exists idx_pedidos_cliente on pedidos using btree (upper(client_email), created desc);
create index if not exists idx_pedidos_pro on pedidos using btree (upper(pro_email), created desc);
create index if not exists idx_pedidos_pro_lower on pedidos using btree (lower(pro_email), lower(status));
create index if not exists idx_pedidos_status_servico on pedidos using btree (lower(status), lower(service), created desc);
create index if not exists idx_pedidos_status_created on pedidos using btree (lower(status), created desc);
create index if not exists idx_pedidos_completed on pedidos using btree (completed_at);

-- Mensagens: conversa do pedido em ordem e contagem de não lidas
create index if not exists idx_mensagens_pedido on mensagens using btree (pedido_id, criada_em);
create index if not exists idx_mensagens_nao_lidas on mensagens using btree (destinatario_id, lida_em);
create index if not exists idx_mensagens_criada on mensagens using btree (criada_em);

-- Notificações: lista por data e contagem de não lidas
create index if not exists idx_notificacoes_usuario on notificacoes using btree (usuario_id, criada_em desc);
create index if not exists idx_notificacoes_nao_lidas on notificacoes using btree (usuario_id, lida_em);

-- Carteira, saques e relatórios financeiros por data
create index if not exists idx_transacoes_usuario on transacoes_financeiras using btree (usuario_id, criada_em desc);
create index if not exists idx_saques_usuario on solicitacoes_saque using btree (usuario_id, criada_em desc);

-- Chamados e denúncias por autor
create index if not exists idx_chamados_autor on chamados using btree (autor_id, criado_em desc);
create index if not exists idx_denuncias_denunciante on denuncias using btree (denunciante_id, criada_em desc);

-- Recuperação de senha por usuário e limpeza por validade
create index if not exists idx_tokens_usuario on tokens_recuperacao_senha using btree (usuario_id, expira_em);

-- Estimativas do cliente
create index if not exists idx_estimativa_cliente on pedidos_estimativa using btree (upper(email_cliente), upper(servico));

-- Mensagens do formulário de contato por data
create index if not exists idx_contatos_criada on contatos_site using btree (criada_em desc);
