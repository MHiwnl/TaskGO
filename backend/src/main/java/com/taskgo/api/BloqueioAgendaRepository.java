package com.taskgo.api;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BloqueioAgendaRepository extends JpaRepository<BloqueioAgenda, String> {
    @Query("select b from BloqueioAgenda b where b.usuarioId = :u and b.fim > :de and b.inicio < :ate order by b.inicio")
    List<BloqueioAgenda> entre(@Param("u") Long usuarioId, @Param("de") long de, @Param("ate") long ate);
    Optional<BloqueioAgenda> findByIdAndUsuarioId(String id, Long usuarioId);
    long countByUsuarioIdAndFimGreaterThan(Long usuarioId, long agora);
    void deleteByUsuarioId(Long usuarioId);
}
