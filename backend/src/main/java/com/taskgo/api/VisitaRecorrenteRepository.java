package com.taskgo.api;

import java.util.List;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface VisitaRecorrenteRepository extends JpaRepository<VisitaRecorrente, String> {
    List<VisitaRecorrente> findByPedidoIdOrderByOrdemAsc(String pedidoId);
    List<VisitaRecorrente> findByStatus(String status);
    List<VisitaRecorrente> findByCobrancaId(String cobrancaId);
    /* Quem decide uma vez trava a linha: dois cliques ao mesmo tempo não devolvem a mesma vez duas vezes. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select v from VisitaRecorrente v where v.id = :id")
    Optional<VisitaRecorrente> findByIdForUpdate(@Param("id") String id);
}
