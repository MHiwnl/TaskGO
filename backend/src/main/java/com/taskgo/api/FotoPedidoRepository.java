package com.taskgo.api;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FotoPedidoRepository extends JpaRepository<FotoPedido, String> {
    /* Só os dados leves (sem a imagem) para montar listas. */
    @Query("select new com.taskgo.api.FotoPedidoRepository$Resumo(f.id, f.autorId, f.largura, f.altura, f.criadaEm) from FotoPedido f where f.pedidoId = :pedidoId and f.contexto = :contexto order by f.criadaEm asc")
    List<Resumo> resumos(@Param("pedidoId") String pedidoId, @Param("contexto") String contexto);
    long countByPedidoIdAndContexto(String pedidoId, String contexto);
    void deleteByPedidoIdIn(java.util.Collection<String> pedidoIds);

    record Resumo(String id, Long autorId, int largura, int altura, long criadaEm) {}
}
