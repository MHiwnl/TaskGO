package com.taskgo.api;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EtapaObraRepository extends JpaRepository<EtapaObra, String> {
    List<EtapaObra> findByPedidoIdOrderByOrdemAsc(String pedidoId);
    List<EtapaObra> findByStatus(String status);
    Optional<EtapaObra> findByCobrancaId(String cobrancaId);
    /* Aprovação atômica: só quem trocar de entregue para aprovada segue (cliente e prazo automático podem chegar juntos). */
    @org.springframework.data.jpa.repository.Modifying(flushAutomatically = true)
    @org.springframework.data.jpa.repository.Query("update EtapaObra e set e.status = :aprovada where e.id = :id and e.status = :entregue")
    int reservarAprovacao(@org.springframework.data.repository.query.Param("id") String id, @org.springframework.data.repository.query.Param("entregue") String entregue,
                          @org.springframework.data.repository.query.Param("aprovada") String aprovada);
}
