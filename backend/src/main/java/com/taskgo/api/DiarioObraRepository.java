package com.taskgo.api;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DiarioObraRepository extends JpaRepository<DiarioObra, String> {
    List<DiarioObra> findByPedidoIdOrderByChegadaEmDesc(String pedidoId);
}
