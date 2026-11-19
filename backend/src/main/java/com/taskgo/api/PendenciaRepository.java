package com.taskgo.api;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PendenciaRepository extends JpaRepository<Pendencia, String> {
    List<Pendencia> findByUsuarioIdOrderByCriadaEmDesc(Long usuarioId);
    List<Pendencia> findByUsuarioIdAndStatusOrderByCriadaEmAsc(Long usuarioId, String status);
    List<Pendencia> findByStatusAndTipo(String status, String tipo);
    Optional<Pendencia> findByCobrancaId(String cobrancaId);
    Optional<Pendencia> findFirstByPedidoIdAndTipoOrderByCriadaEmDesc(String pedidoId, String tipo);
    default Optional<Pendencia> findByPedidoIdAndTipo(String pedidoId, String tipo) { return findFirstByPedidoIdAndTipoOrderByCriadaEmDesc(pedidoId, tipo); }
}
