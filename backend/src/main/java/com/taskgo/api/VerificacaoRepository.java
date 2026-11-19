package com.taskgo.api;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VerificacaoRepository extends JpaRepository<Verificacao, String> {
    Optional<Verificacao> findFirstByUsuarioIdOrderByEnviadaEmDesc(Long usuarioId);
    List<Verificacao> findByStatusOrderByEnviadaEmAsc(String status);
    long countByStatus(String status);
    void deleteByUsuarioId(Long usuarioId);
}
