package com.taskgo.api;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EtapaObraRepository extends JpaRepository<EtapaObra, String> {
    List<EtapaObra> findByPedidoIdOrderByOrdemAsc(String pedidoId);
    List<EtapaObra> findByStatus(String status);
    Optional<EtapaObra> findByCobrancaId(String cobrancaId);
}
