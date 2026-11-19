package com.taskgo.api;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VisitaTecnicaRepository extends JpaRepository<VisitaTecnica, String> {
    List<VisitaTecnica> findByPedidoIdOrderByCriadaEmAsc(String pedidoId);
    List<VisitaTecnica> findByStatus(String status);
    Optional<VisitaTecnica> findByCobrancaId(String cobrancaId);
}
