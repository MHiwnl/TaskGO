-- Uma denúncia por pessoa em cada pedido: apaga as repetidas (fica a mais antiga) e trava no banco.
delete from denuncias where id in (
  select d.id from denuncias d where d.pedido_id is not null and exists (
    select 1 from denuncias e where e.denunciante_id = d.denunciante_id and e.pedido_id = d.pedido_id
      and (e.criada_em < d.criada_em or (e.criada_em = d.criada_em and e.id < d.id))));
create unique index if not exists uk_denuncias_denunciante_pedido on denuncias (denunciante_id, pedido_id);
