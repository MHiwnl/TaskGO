-- Um recurso em análise por conta. A checagem no código não basta com dois envios ao mesmo tempo:
-- os dois passavam e cada um abria a conversa de um pedido diferente.
CREATE UNIQUE INDEX IF NOT EXISTS uk_recursos_um_em_analise ON recursos_moderacao (usuario_id) WHERE status = 'em_analise';
