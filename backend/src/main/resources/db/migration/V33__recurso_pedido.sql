-- Recurso passa a dizer a qual pedido se refere o aviso contestado; a conversa só abre para esse pedido.
-- Recursos antigos ficam com pedido_id NULL e não liberam conversa nenhuma.
ALTER TABLE recursos_moderacao ADD COLUMN IF NOT EXISTS pedido_id VARCHAR(255);
