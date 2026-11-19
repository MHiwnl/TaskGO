package com.taskgo.api;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DenunciaRepository extends JpaRepository<Denuncia, String> {
    List<Denuncia> findAllByOrderByCriadaEmDesc();
    List<Denuncia> findByDenuncianteIdOrderByCriadaEmDesc(Long denuncianteId);
    boolean existsByDenuncianteIdAndPedidoIdAndStatus(Long denuncianteId, String pedidoId, String status);
    boolean existsByDenuncianteIdAndPedidoId(Long denuncianteId, String pedidoId);
    long countByDenunciadoId(Long denunciadoId);
    long countByStatus(String status);
    List<Denuncia> findByStatusOrderByCriadaEmAsc(String status, org.springframework.data.domain.Pageable pagina);
    long countByPedidoId(String pedidoId);
    /* Contas com muitas denúncias recentes (alerta). */
    @org.springframework.data.jpa.repository.Query("select d.denunciadoId, count(d) from Denuncia d where d.criadaEm >= :desde group by d.denunciadoId having count(d) >= :minimo")
    List<Object[]> muitasDesde(@org.springframework.data.repository.query.Param("desde") long desde, @org.springframework.data.repository.query.Param("minimo") long minimo);
    void deleteByDenuncianteIdOrDenunciadoId(Long denuncianteId, Long denunciadoId);
}
