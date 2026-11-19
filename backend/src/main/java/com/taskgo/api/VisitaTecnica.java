package com.taskgo.api;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;

/* Visita técnica paga (R$ 50 a R$ 100) para o profissional medir antes de orçar; abatida se ele for contratado. */
@Entity
@Table(name = "visitas_tecnicas")
public class VisitaTecnica {
    public static final String PROPOSTA = "proposta", PAGA = "paga", REALIZADA = "realizada", LIBERADA = "liberada", RECUSADA = "recusada", CANCELADA = "cancelada";

    @Id private String id;
    @Column(name = "pedido_id", nullable = false) private String pedidoId;
    @Column(name = "profissional_id", nullable = false) private Long profissionalId;
    @Column(name = "profissional_nome") private String profissionalNome;
    @Column(nullable = false, precision = 19, scale = 2) private BigDecimal valor;
    @Column(nullable = false) private long data;
    @Column(nullable = false) private String status = PROPOSTA;
    @Column(name = "cobranca_id") private String cobrancaId;
    @Column(name = "criada_em", nullable = false) private long criadaEm;
    @Column(name = "realizada_em") private Long realizadaEm;
    @Column(name = "liberada_em") private Long liberadaEm;

    protected VisitaTecnica() {}
    public VisitaTecnica(String id, String pedidoId, Usuario pro, BigDecimal valor, long data, long criadaEm) {
        this.id = id; this.pedidoId = pedidoId; this.profissionalId = pro.getId(); this.profissionalNome = pro.getNome(); this.valor = valor; this.data = data; this.criadaEm = criadaEm;
    }
    public String getId() { return id; }
    public String getPedidoId() { return pedidoId; }
    public Long getProfissionalId() { return profissionalId; }
    public String getProfissionalNome() { return profissionalNome; }
    public BigDecimal getValor() { return valor; }
    public long getData() { return data; }
    public String getStatus() { return status; }
    public String getCobrancaId() { return cobrancaId; }
    public long getCriadaEm() { return criadaEm; }
    public Long getRealizadaEm() { return realizadaEm; }
    public Long getLiberadaEm() { return liberadaEm; }
    void cobranca(String id) { this.cobrancaId = id; }
    void situacao(String s) { this.status = s; }
    void realizar(long quando) { this.status = REALIZADA; this.realizadaEm = quando; }
    void liberar(long quando) { this.status = LIBERADA; this.liberadaEm = quando; }
}
