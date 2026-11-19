package com.taskgo.api;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;

/* Uma etapa da obra. O cliente paga antes de começar; a TaskGO segura e libera quando ele aprova (ou em 24 h). */
@Entity
@Table(name = "etapas_obra")
public class EtapaObra {
    public static final String PENDENTE = "pendente", PAGA = "paga", ANDAMENTO = "andamento", ENTREGUE = "entregue", APROVADA = "aprovada",
            PROBLEMA = "problema", MEDIACAO = "mediacao", DEVOLVIDA = "devolvida", CANCELADA = "cancelada";

    @Id private String id;
    @Column(name = "pedido_id", nullable = false) private String pedidoId;
    @Column(name = "orcamento_id", nullable = false) private String orcamentoId;
    @Column(nullable = false) private int ordem;
    @Column(nullable = false) private String nome;
    @Column(columnDefinition = "text") private String entrega;
    @Column(nullable = false, precision = 19, scale = 2) private BigDecimal valor;
    @Column(nullable = false, precision = 19, scale = 2) private BigDecimal desconto = BigDecimal.ZERO.setScale(2);
    @Column(nullable = false) private int dias;
    @Column(nullable = false) private String status = PENDENTE;
    @Column(name = "cobranca_id") private String cobrancaId;
    @Column(nullable = false, precision = 19, scale = 2) private BigDecimal pago = BigDecimal.ZERO.setScale(2);
    @Column(nullable = false, precision = 19, scale = 2) private BigDecimal liberado = BigDecimal.ZERO.setScale(2);
    @Column(nullable = false, precision = 19, scale = 2) private BigDecimal devolvido = BigDecimal.ZERO.setScale(2);
    @Column(name = "material_liberado", nullable = false, precision = 19, scale = 2) private BigDecimal materialLiberado = BigDecimal.ZERO.setScale(2);
    @Column(nullable = false) private boolean aditivo;
    @Column(columnDefinition = "text") private String fotos;
    @Column(columnDefinition = "text") private String problema;
    @Column(name = "paga_em") private Long pagaEm;
    @Column(name = "iniciada_em") private Long iniciadaEm;
    @Column(name = "entregue_em") private Long entregueEm;
    @Column(name = "liberar_apos") private Long liberarApos;
    @Column(name = "aprovada_em") private Long aprovadaEm;

    protected EtapaObra() {}
    public EtapaObra(String id, String pedidoId, String orcamentoId, int ordem, String nome, String entrega, BigDecimal valor, int dias, boolean aditivo) {
        this.id = id; this.pedidoId = pedidoId; this.orcamentoId = orcamentoId; this.ordem = ordem; this.nome = nome; this.entrega = entrega;
        this.valor = valor; this.dias = dias; this.aditivo = aditivo;
    }
    public String getId() { return id; }
    public String getPedidoId() { return pedidoId; }
    public String getOrcamentoId() { return orcamentoId; }
    public int getOrdem() { return ordem; }
    public String getNome() { return nome; }
    public String getEntrega() { return entrega; }
    public BigDecimal getValor() { return valor; }
    public BigDecimal getDesconto() { return desconto; }
    public int getDias() { return dias; }
    public String getStatus() { return status; }
    public String getCobrancaId() { return cobrancaId; }
    public BigDecimal getPago() { return pago; }
    public BigDecimal getLiberado() { return liberado; }
    public BigDecimal getDevolvido() { return devolvido; }
    public BigDecimal getMaterialLiberado() { return materialLiberado; }
    public boolean isAditivo() { return aditivo; }
    public String getFotos() { return fotos; }
    public String getProblema() { return problema; }
    public Long getPagaEm() { return pagaEm; }
    public Long getIniciadaEm() { return iniciadaEm; }
    public Long getEntregueEm() { return entregueEm; }
    public Long getLiberarApos() { return liberarApos; }
    public Long getAprovadaEm() { return aprovadaEm; }
    /** O que o cliente paga nesta etapa (valor menos o abatimento da visita técnica). */
    public BigDecimal aPagar() { return valor.subtract(desconto).max(BigDecimal.ZERO); }
    /** Pago e ainda com a TaskGO. */
    public BigDecimal retido() { return pago.subtract(liberado).subtract(devolvido).max(BigDecimal.ZERO); }
    void abater(BigDecimal d) { this.desconto = d; }
    void cobranca(String id) { this.cobrancaId = id; }
    void pagar(BigDecimal quanto, long quando) { this.pago = quanto; this.pagaEm = quando; this.status = PAGA; }
    void iniciar(long quando) { this.status = ANDAMENTO; this.iniciadaEm = quando; }
    void entregar(String fotos, long quando, long liberarApos) { this.status = ENTREGUE; this.fotos = fotos; this.entregueEm = quando; this.liberarApos = liberarApos; }
    void liberarMaterial(BigDecimal v) { this.materialLiberado = this.materialLiberado.add(v); this.liberado = this.liberado.add(v); }
    void liberar(BigDecimal v) { this.liberado = this.liberado.add(v); }
    void devolver(BigDecimal v) { this.devolvido = this.devolvido.add(v); }
    void aprovar(long quando) { this.status = APROVADA; this.aprovadaEm = quando; }
    /* Pagamento direto: o cliente pagou a etapa ao profissional, que confirmou. */
    void recebidoDireto(BigDecimal quanto, long quando) { this.pago = quanto; this.liberado = quanto; this.pagaEm = quando; }
    void problema(String texto) { this.status = PROBLEMA; this.problema = texto; }
    void situacao(String s) { this.status = s; }
}
