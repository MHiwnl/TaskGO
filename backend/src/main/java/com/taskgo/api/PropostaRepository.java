package com.taskgo.api;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PropostaRepository extends JpaRepository<Proposta, String> {
    /* Números do painel do administrador: por situação e por autor. */
    @org.springframework.data.jpa.repository.Query("select x.status, x.autor, count(x) from Proposta x group by x.status, x.autor")
    java.util.List<Object[]> contarPorStatusEAutor();
    @org.springframework.data.jpa.repository.Query("select x.pedidoId, count(x) from Proposta x group by x.pedidoId")
    java.util.List<Object[]> contarPorPedido();
    @org.springframework.data.jpa.repository.Query("select count(x) from Proposta x where x.criadaEm >= :a and x.criadaEm < :b")
    long criadasEntre(@org.springframework.data.repository.query.Param("a") long a, @org.springframework.data.repository.query.Param("b") long b);
    List<Proposta> findByPedidoIdOrderByCriadaEmAsc(String pedidoId);
    List<Proposta> findByPedidoIdIn(java.util.Collection<String> pedidoIds);
    void deleteByProfissionalId(Long profissionalId);
    List<Proposta> findByPedidoIdAndProfissionalIdOrderByCriadaEmAsc(String pedidoId, Long profissionalId);
    List<Proposta> findByPedidoIdAndStatus(String pedidoId, String status);
}
