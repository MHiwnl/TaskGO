package com.taskgo.api;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CobrancaRepository extends JpaRepository<Cobranca, String> {
    List<Cobranca> findByPedidoIdOrderByCriadaEmAsc(String pedidoId);
    Optional<Cobranca> findByTxid(String txid);
    /* Pagamento que ainda pode acontecer: pendente e sem prazo (cartão em conferência) ou com o Pix ainda no prazo. */
    @org.springframework.data.jpa.repository.Query("select count(c) > 0 from Cobranca c where c.clienteId = :cliente and c.status = :status and (c.expiraEm is null or c.expiraEm > :agora)")
    boolean temEmAberto(@org.springframework.data.repository.query.Param("cliente") Long clienteId, @org.springframework.data.repository.query.Param("status") String status,
                        @org.springframework.data.repository.query.Param("agora") long agora);
    Optional<Cobranca> findByIdAndClienteId(String id, Long clienteId);
    List<Cobranca> findByTipoAndStatusAndCriadaEmGreaterThanEqualOrderByCriadaEmDesc(String tipo, String status, long desde);
    List<Cobranca> findByTipoAndMetodoAndStatusAndExpiraEmGreaterThan(String tipo, String metodo, String status, long expiraDepoisDe);
    List<Cobranca> findByMetodoAndStatusAndProvedorAndCriadaEmGreaterThan(String metodo, String status, String provedor, long criadaDepoisDe);
    /* Devoluções reservadas há mais tempo que o limite: a conciliação confere no provedor se elas chegaram a ser aceitas. */
    List<Cobranca> findByValorEstornandoIsNotNullAndEstornandoDesdeLessThan(long reservadaAntesDe);

    /* Confirma o pagamento só se a cobrança ainda estiver pendente. Devolve 1 para quem confirmou e 0 para quem chegou depois:
       o aviso do provedor e a consulta da tela podem chegar ao mesmo tempo, e só um deles pode aplicar o pagamento. */
    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query("update Cobranca c set c.status = :paga, c.pagaEm = :quando, c.motivo = null where c.id = :id and c.status = :pendente")
    int marcarPaga(@org.springframework.data.repository.query.Param("id") String id, @org.springframework.data.repository.query.Param("pendente") String pendente,
                   @org.springframework.data.repository.query.Param("paga") String paga, @org.springframework.data.repository.query.Param("quando") long quando);

    /* Reserva a devolução só se a cobrança estiver paga, sem outra devolução em andamento e com saldo para a parte. Devolve 0 para quem chegou depois. */
    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query("update Cobranca c set c.valorEstornando = :parte, c.estornandoDesde = :desde where c.id = :id and c.status = :paga and c.valorEstornando is null and c.valor - c.valorEstornado >= :parte")
    int reservarEstorno(@org.springframework.data.repository.query.Param("id") String id, @org.springframework.data.repository.query.Param("parte") java.math.BigDecimal parte,
                        @org.springframework.data.repository.query.Param("desde") long desde, @org.springframework.data.repository.query.Param("paga") String paga);

    /* Confirma a devolução reservada: soma na cobrança, limpa a reserva e marca estornada quando chegar ao valor. */
    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query("update Cobranca c set c.valorEstornado = c.valorEstornado + :parte, c.valorEstornando = null, c.estornandoDesde = null, c.estornadaEm = :quando, c.status = case when c.valorEstornado + :parte >= c.valor then :estornada else c.status end where c.id = :id and c.valorEstornando = :parte")
    int concluirEstorno(@org.springframework.data.repository.query.Param("id") String id, @org.springframework.data.repository.query.Param("parte") java.math.BigDecimal parte,
                        @org.springframework.data.repository.query.Param("quando") long quando, @org.springframework.data.repository.query.Param("estornada") String estornada);

    /* Solta a reserva de uma devolução que o provedor recusou com certeza. Só a reserva com esse "desde" é solta. */
    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query("update Cobranca c set c.valorEstornando = null, c.estornandoDesde = null where c.id = :id and c.estornandoDesde = :desde")
    int liberarEstorno(@org.springframework.data.repository.query.Param("id") String id, @org.springframework.data.repository.query.Param("desde") long desde);
}
