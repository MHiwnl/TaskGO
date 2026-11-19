package com.taskgo.api;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SessaoContaRepository extends JpaRepository<SessaoConta, String> {
    List<SessaoConta> findByUsuarioIdAndEncerradaEmIsNullOrderByVistaEmDesc(Long usuarioId);
    void deleteByUsuarioId(Long usuarioId);
}
