package com.taskgo.api;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TransacaoFinanceiraRepository extends JpaRepository<TransacaoFinanceira, String> {
    Page<TransacaoFinanceira> findByUsuarioIdOrderByCriadaEmDesc(Long usuarioId, Pageable pageable);
    java.util.List<TransacaoFinanceira> findByReferenciaAndTipo(String referencia, String tipo);
    java.util.List<TransacaoFinanceira> findByTipoAndStatus(String tipo, String status);
    java.util.List<TransacaoFinanceira> findTop30ByTipoOrderByCriadaEmDesc(String tipo);
}
