package com.taskgo.api;

import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Modifying;

public interface PedidoContratadoRepository extends JpaRepository<Pedido, String> {
    @Query("select avg(p.rating), count(p.rating) from Pedido p where lower(p.proEmail)=lower(:email) and lower(p.status)='concluido' and p.rating is not null")
    Object[] resumoAvaliacoes(@Param("email") String email);
    java.util.Optional<Pedido> findByIdAndStatusIgnoreCaseAndProEmailIsNull(String id, String status);
    /* Pedidos abertos recentes: o Chamar Agora procura entre eles os que estão chamando profissionais. */
    @Query("select p from Pedido p where lower(p.status) = 'aguardando' and p.created > :desde order by p.created desc")
    List<Pedido> abertosDesde(@Param("desde") long desde);
    /* Concluídos com o pagamento ainda retido: esperando o cliente confirmar ou o prazo vencer. */
    List<Pedido> findByStatusAndPaid(String status, String paid);
    List<Pedido> findByPaid(String paid);

    /* ---- números do painel do administrador, calculados no banco ---- */
    @Query("select lower(p.status), count(p) from Pedido p group by lower(p.status)")
    List<Object[]> contarPorStatus();
    @Query("select count(p), coalesce(sum(p.price), 0) from Pedido p where lower(p.status) = 'concluido'")
    List<Object[]> concluidosEValor();
    @Query("select count(p.rating), avg(p.rating) from Pedido p where p.rating > 0")
    List<Object[]> notas();
    @Query("select count(p) from Pedido p where p.created >= :desde")
    long criadosDesde(@Param("desde") long desde);
    @Query("select count(p) from Pedido p where p.acceptedAt is not null and p.created is not null and p.acceptedAt >= p.created")
    long comAceite();
    /* Espera até o aceite (ms), em ordem: a mediana é a linha do meio. */
    @Query("select (p.acceptedAt - p.created) from Pedido p where p.acceptedAt is not null and p.created is not null and p.acceptedAt >= p.created order by (p.acceptedAt - p.created)")
    List<Long> esperasAteAceite(org.springframework.data.domain.Pageable pagina);
    @Query("select p.created from Pedido p where p.created >= :desde")
    List<Long> criacoesDesde(@Param("desde") long desde);
    @Query("select p.completedAt from Pedido p where lower(p.status) = 'concluido' and p.completedAt >= :desde")
    List<Long> conclusoesDesde(@Param("desde") long desde);
    @Query("select coalesce(p.service, 'outro'), count(p) from Pedido p group by coalesce(p.service, 'outro')")
    List<Object[]> contarPorCategoria();
    List<Pedido> findByStatusIgnoreCase(String status);

    /* ---- painel em escala: tudo por intervalo de tempo, por etapa ou paginado ---- */
    @Query("select count(p) from Pedido p where p.created >= :a and p.created < :b")
    long criadosEntre(@Param("a") long a, @Param("b") long b);
    @Query("select count(p), coalesce(sum(p.price), 0) from Pedido p where lower(p.status) = 'concluido' and p.completedAt >= :a and p.completedAt < :b")
    List<Object[]> concluidosEntre(@Param("a") long a, @Param("b") long b);
    @Query("select count(p) from Pedido p where p.acceptedAt >= :a and p.acceptedAt < :b and p.created is not null and p.acceptedAt >= p.created")
    long aceitosEntre(@Param("a") long a, @Param("b") long b);
    @Query("select (p.acceptedAt - p.created) from Pedido p where p.acceptedAt >= :a and p.acceptedAt < :b and p.created is not null and p.acceptedAt >= p.created order by (p.acceptedAt - p.created)")
    List<Long> esperasEntre(@Param("a") long a, @Param("b") long b, org.springframework.data.domain.Pageable pagina);
    @Query("select coalesce(p.service, 'outro'), count(p) from Pedido p where p.created >= :a and p.created < :b group by coalesce(p.service, 'outro')")
    List<Object[]> categoriasEntre(@Param("a") long a, @Param("b") long b);
    @Query("select p from Pedido p where lower(p.status) = :status and p.created < :antes order by p.created asc")
    List<Pedido> paradosDesde(@Param("status") String status, @Param("antes") long antes, org.springframework.data.domain.Pageable pagina);
    @Query("select count(p) from Pedido p where lower(p.status) = :status and p.created < :antes")
    long contarParadosDesde(@Param("status") String status, @Param("antes") long antes);
    @Query("select p from Pedido p where lower(p.status) = 'a_caminho' and p.acceptedAt < :antes order by p.acceptedAt asc")
    List<Pedido> aCaminhoDesde(@Param("antes") long antes, org.springframework.data.domain.Pageable pagina);
    @Query("select count(p) from Pedido p where lower(p.status) = 'a_caminho' and p.acceptedAt < :antes")
    long contarACaminhoDesde(@Param("antes") long antes);
    @Query("select p from Pedido p where p.created >= :a and p.created < :b order by p.created asc, p.id asc")
    List<Pedido> paginaEntre(@Param("a") long a, @Param("b") long b, org.springframework.data.domain.Pageable pagina);
    List<Pedido> findByClientEmailIgnoreCaseOrProEmailIgnoreCaseOrderByCreatedDesc(String clientEmail, String proEmail, org.springframework.data.domain.Pageable pagina);
    long countByClientEmailIgnoreCaseOrProEmailIgnoreCase(String clientEmail, String proEmail);
    /* Pedidos concluídos de uma categoria, para a faixa de preço praticada no site. */
    @Query("select p.price from Pedido p where lower(p.status) = 'concluido' and lower(p.service) = lower(:servico) and p.created > :desde and p.price is not null")
    List<Double> precosConcluidos(@Param("servico") String servico, @Param("desde") long desde);
    @Query("select count(p) from Pedido p where lower(p.proEmail) = lower(:email) and lower(p.status) = 'concluido'")
    long concluidosDoProfissional(@Param("email") String email);
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @org.springframework.transaction.annotation.Transactional
    @Query("update Pedido p set p.proEmail = null, p.proId = null, p.professionalName = null, p.status = 'aguardando', p.acceptedAt = null where p.id = :id and lower(p.status) = 'a_caminho' and lower(p.proEmail) = lower(:email)")
    int devolverAoMural(@Param("id") String id, @Param("email") String email);
    /* Grava só os dados extras e só se o pedido ainda está aberto: nunca desfaz um aceite que chegou no meio. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @org.springframework.transaction.annotation.Transactional
    @Query(value = "update pedidos set extra = :extra where id = :id and lower(status) = 'aguardando'", nativeQuery = true)
    int gravarExtraSeAberto(@Param("id") String id, @Param("extra") String extra);
    List<Pedido> findByClientEmailIgnoreCaseOrProEmailIgnoreCaseOrderByCreatedDesc(String clientEmail, String proEmail);
    void deleteByClientEmailIgnoreCaseOrProEmailIgnoreCase(String clientEmail, String proEmail);
    @Query("select p from Pedido p where lower(p.status)=lower(:status) and p.proEmail is null")
    Page<Pedido> encontrarDisponiveis(@Param("status") String status, Pageable pageable);
    @Query("select p from Pedido p where lower(p.status)=lower(:status) and p.proEmail is null and lower(p.service)=lower(:service)")
    Page<Pedido> encontrarDisponiveisPorServico(@Param("status") String status, @Param("service") String service, Pageable pageable);
    @Query("select p from Pedido p where lower(p.status)=lower(:status) and p.proEmail is null and p.service in :services")
    Page<Pedido> encontrarDisponiveisPorServicos(@Param("status") String status, @Param("services") java.util.Collection<String> services, Pageable pageable);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Pedido p set p.proEmail = :email, p.proId = :proId, p.professionalName = :name, p.status = 'a_caminho', p.acceptedAt = :acceptedAt, p.progress = 0 where p.id = :id and lower(p.status) = 'aguardando' and (p.proEmail is null or lower(p.proEmail) = lower(:email))")
    int aceitarSeDisponivel(@Param("id") String id, @Param("email") String email, @Param("proId") String proId,
                            @Param("name") String name, @Param("acceptedAt") long acceptedAt);
}
