package com.taskgo.api;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RecursoRepository extends JpaRepository<Recurso, String> {
    long countByStatus(String status);
    boolean existsByUsuarioIdAndStatusAndPedidoId(Long usuarioId, String status, String pedidoId);
    java.util.List<Recurso> findByStatusOrderByCriadoEmAsc(String status, org.springframework.data.domain.Pageable pagina);
    List<Recurso> findByUsuarioIdOrderByCriadoEmDesc(Long usuarioId);
    List<Recurso> findByStatusOrderByCriadoEmAsc(String status);
    List<Recurso> findAllByOrderByCriadoEmDesc();
}
