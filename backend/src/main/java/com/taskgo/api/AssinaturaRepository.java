package com.taskgo.api;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AssinaturaRepository extends JpaRepository<Assinatura, String> {
    Optional<Assinatura> findByUsuarioId(Long usuarioId);
    List<Assinatura> findByUsuarioIdIn(java.util.Collection<Long> usuarioIds);
    List<Assinatura> findByStatusIn(java.util.Collection<String> status);
    long countByPagoAteGreaterThan(long agora);
}
