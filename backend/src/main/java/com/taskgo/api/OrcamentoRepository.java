package com.taskgo.api;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrcamentoRepository extends JpaRepository<Orcamento, String> {
    List<Orcamento> findByPedidoIdOrderByCriadoEmAsc(String pedidoId);
    List<Orcamento> findByPedidoIdAndStatus(String pedidoId, String status);
}
