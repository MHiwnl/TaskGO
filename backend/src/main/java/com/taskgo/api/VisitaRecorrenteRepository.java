package com.taskgo.api;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VisitaRecorrenteRepository extends JpaRepository<VisitaRecorrente, String> {
    List<VisitaRecorrente> findByPedidoIdOrderByOrdemAsc(String pedidoId);
    List<VisitaRecorrente> findByStatus(String status);
    List<VisitaRecorrente> findByCobrancaId(String cobrancaId);
}
