package com.taskgo.api;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;

/* Uma vez de um serviço recorrente. */
@Entity
@Table(name = "visitas_recorrentes")
public class VisitaRecorrente {
    public static final String PENDENTE = "pendente", PAGA = "paga", FEITA = "feita", LIBERADA = "liberada", A_PAGAR = "a_pagar", CONTESTADA = "contestada", FALTOU = "faltou", CANCELADA = "cancelada", DEVOLVIDA = "devolvida";
    @Id private String id;
    @Column(name = "pedido_id", nullable = false) private String pedidoId;
    @Column(nullable = false) private int ordem;
    @Column(nullable = false) private String data;
    @Column(nullable = false) private int semana;
    @Column(nullable = false, precision = 19, scale = 2) private BigDecimal valor;
    @Column(nullable = false) private String status = PENDENTE;
    @Column(name = "cobranca_id") private String cobrancaId;
    @Column(name = "feita_em") private Long feitaEm;
    @Column(name = "liberar_apos") private Long liberarApos;
    @Column(name = "fechada_em") private Long fechadaEm;
    protected VisitaRecorrente() {}
    public VisitaRecorrente(String id, String pedidoId, int ordem, String data, int semana, BigDecimal valor) {
        this.id = id; this.pedidoId = pedidoId; this.ordem = ordem; this.data = data; this.semana = semana; this.valor = valor;
    }
    public String getId() { return id; }
    public String getPedidoId() { return pedidoId; }
    public int getOrdem() { return ordem; }
    public String getData() { return data; }
    public int getSemana() { return semana; }
    public BigDecimal getValor() { return valor; }
    public String getStatus() { return status; }
    public String getCobrancaId() { return cobrancaId; }
    public Long getFeitaEm() { return feitaEm; }
    public Long getLiberarApos() { return liberarApos; }
    public Long getFechadaEm() { return fechadaEm; }
    void pagar(String cobrancaId) { this.cobrancaId = cobrancaId; this.status = PAGA; }
    void cobranca(String id) { this.cobrancaId = id; }
    void feita(long quando, long liberarApos) { this.status = FEITA; this.feitaEm = quando; this.liberarApos = liberarApos; }
    void fechar(String status, long quando) { this.status = status; this.fechadaEm = quando; }
    void aPagar() { this.status = A_PAGAR; }
}
