package com.taskgo.api;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CobrancaRepository extends JpaRepository<Cobranca, String> {
    List<Cobranca> findByPedidoIdOrderByCriadaEmAsc(String pedidoId);
    Optional<Cobranca> findByTxid(String txid);
    Optional<Cobranca> findByIdAndClienteId(String id, Long clienteId);
    List<Cobranca> findByTipoAndStatusAndCriadaEmGreaterThanEqualOrderByCriadaEmDesc(String tipo, String status, long desde);
    List<Cobranca> findByTipoAndMetodoAndStatusAndExpiraEmGreaterThan(String tipo, String metodo, String status, long expiraDepoisDe);

    /* Confirma o pagamento só se a cobrança ainda estiver pendente. Devolve 1 para quem confirmou e 0 para quem chegou depois:
       o aviso do provedor e a consulta da tela podem chegar ao mesmo tempo, e só um deles pode aplicar o pagamento. */
    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query("update Cobranca c set c.status = :paga, c.pagaEm = :quando, c.motivo = null where c.id = :id and c.status = :pendente")
    int marcarPaga(@org.springframework.data.repository.query.Param("id") String id, @org.springframework.data.repository.query.Param("pendente") String pendente,
                   @org.springframework.data.repository.query.Param("paga") String paga, @org.springframework.data.repository.query.Param("quando") long quando);
}
