-- E-mail único sem diferenciar maiúsculas: a checagem do código (existsByEmailIgnoreCase) não impede
-- dois cadastros ao mesmo tempo; o banco impede. Conferido antes: nenhum e-mail repetido em produção.
-- O índice comum de lower(email) (V9) fica redundante: o único também serve às buscas.
create unique index if not exists uk_usuarios_email_lower on usuarios (lower(email));
drop index if exists idx_usuarios_email_lower;
