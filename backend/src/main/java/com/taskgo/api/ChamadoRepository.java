package com.taskgo.api;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChamadoRepository extends JpaRepository<Chamado, String> {
    List<Chamado> findAllByOrderByCriadoEmDesc();
    List<Chamado> findByAutorIdOrderByCriadoEmDesc(Long autorId);
    List<Chamado> findByAutorIdAndStatusNot(Long autorId, String status);
    long countByStatusIn(java.util.Collection<String> status);
}
