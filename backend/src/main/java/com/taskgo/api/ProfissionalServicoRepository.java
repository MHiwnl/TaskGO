package com.taskgo.api;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProfissionalServicoRepository extends JpaRepository<ProfissionalServico, String> {
    List<ProfissionalServico> findByUsuarioId(Long usuarioId);
    boolean existsByUsuarioIdAndServico(Long usuarioId, String servico);
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from ProfissionalServico x where x.usuarioId = :u")
    void apagarDoUsuario(@Param("u") Long usuarioId);
}
