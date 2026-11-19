package com.taskgo.api;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PushInscricaoRepository extends JpaRepository<PushInscricao, String> {
    List<PushInscricao> findByUsuarioId(Long usuarioId);
    Optional<PushInscricao> findByEndpoint(String endpoint);
    long countByUsuarioId(Long usuarioId);
    void deleteByUsuarioId(Long usuarioId);
}
