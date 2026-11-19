package com.taskgo.api;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HistoricoContaRepository extends JpaRepository<HistoricoConta, String> {
    List<HistoricoConta> findByUsuarioIdOrderByCriadoEmDesc(Long usuarioId);
    void deleteByUsuarioId(Long usuarioId);

    /* Auditoria: filtros opcionais (-1 / vazio = sem filtro) e cursor (criadoEm, id) para a próxima página. */
    @org.springframework.data.jpa.repository.Query("select h from HistoricoConta h where (:conta = -1 or h.usuarioId = :conta or h.autorId = :conta) and (:pedido = '' or h.pedidoId = :pedido) "
          + "and (:tipo = '' or h.tipo = :tipo) and h.criadoEm >= :de and h.criadoEm < :ate "
          + "and (h.criadoEm < :cursor or (h.criadoEm = :cursor and h.id < :cursorId)) order by h.criadoEm desc, h.id desc")
    List<HistoricoConta> auditoria(@org.springframework.data.repository.query.Param("conta") long conta, @org.springframework.data.repository.query.Param("pedido") String pedido, @org.springframework.data.repository.query.Param("tipo") String tipo,
                                   @org.springframework.data.repository.query.Param("de") long de, @org.springframework.data.repository.query.Param("ate") long ate, @org.springframework.data.repository.query.Param("cursor") long cursor, @org.springframework.data.repository.query.Param("cursorId") String cursorId, org.springframework.data.domain.Pageable pagina);
    @org.springframework.data.jpa.repository.Query("select count(h) from HistoricoConta h where h.tipo = :tipo and h.criadoEm >= :a and h.criadoEm < :b")
    long contarTipoEntre(@org.springframework.data.repository.query.Param("tipo") String tipo, @org.springframework.data.repository.query.Param("a") long a, @org.springframework.data.repository.query.Param("b") long b);
    @org.springframework.data.jpa.repository.Query("select h from HistoricoConta h where h.criadoEm >= :a and h.criadoEm < :b order by h.criadoEm asc, h.id asc")
    List<HistoricoConta> paginaEntre(@org.springframework.data.repository.query.Param("a") long a, @org.springframework.data.repository.query.Param("b") long b, org.springframework.data.domain.Pageable pagina);
    /* Retenção: eventos de rotina somem depois de 12 meses; os de moderação ficam. */
    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query("delete from HistoricoConta h where h.criadoEm < :limite and h.tipo in :tipos")
    int apagarAntigos(@org.springframework.data.repository.query.Param("limite") long limite, @org.springframework.data.repository.query.Param("tipos") java.util.Collection<String> tipos);
}
