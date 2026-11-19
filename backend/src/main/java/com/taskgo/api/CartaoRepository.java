package com.taskgo.api;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CartaoRepository extends JpaRepository<Cartao, String> {
    List<Cartao> findByUsuarioIdOrderByCriadoEmAsc(Long usuarioId);
    Optional<Cartao> findByIdAndUsuarioId(String id, Long usuarioId);
    long countByUsuarioId(Long usuarioId);
}
