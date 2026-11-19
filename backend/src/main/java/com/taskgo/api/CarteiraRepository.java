package com.taskgo.api;

import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CarteiraRepository extends JpaRepository<Carteira, Long> {
    Optional<Carteira> findByUsuarioId(Long usuarioId);
    /* Dinheiro dos usuários guardado na conta da TaskGO no Asaas (disponível, a liberar e reservado para resgate). */
    @Query("select coalesce(sum(c.saldoDisponivel + c.saldoPendente + c.saldoBloqueado), 0) from Carteira c")
    java.math.BigDecimal totalDosUsuarios();
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Carteira c where c.usuarioId = :usuarioId")
    Optional<Carteira> findByUsuarioIdForUpdate(@Param("usuarioId") Long usuarioId);
}
