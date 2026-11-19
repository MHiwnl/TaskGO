package com.taskgo.api;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;

/* Uma cobrança de um pedido: a principal (ao enviar) ou a complementar (diferença de uma proposta maior). */
@Entity
@Table(name = "cobrancas")
public class Cobranca {
    public static final String PENDENTE = "pendente", PAGA = "paga", RECUSADA = "recusada", CANCELADA = "cancelada", ESTORNADA = "estornada";

    @Id private String id;
    @Column(name = "pedido_id", nullable = false) private String pedidoId;
    @Column(name = "cliente_id", nullable = false) private Long clienteId;
    @Column(nullable = false) private String metodo;
    @Column(nullable = false) private String tipo;
    @Column(nullable = false, precision = 19, scale = 2) private BigDecimal valor;
    @Column(nullable = false) private String status;
    private String motivo;
    @Column(nullable = false) private String provedor;
    private String txid;
    @Column(name = "pix_copia_cola", columnDefinition = "text") private String pixCopiaECola;
    @Column(name = "qr_code", columnDefinition = "text") private String qrCode;
    @Column(name = "expira_em") private Long expiraEm;
    @Column(name = "cartao_id") private String cartaoId;
    @Column(name = "cartao_descricao") private String cartaoDescricao;
    private Integer parcelas;
    @Column(name = "valor_estornado", nullable = false, precision = 19, scale = 2) private BigDecimal valorEstornado = BigDecimal.ZERO.setScale(2);
    @Column(name = "criada_em", nullable = false) private long criadaEm;
    @Column(name = "paga_em") private Long pagaEm;
    @Column(name = "estornada_em") private Long estornadaEm;
    @Column(name = "link_pagamento") private String linkPagamento;
    @Column(nullable = false, precision = 19, scale = 2) private BigDecimal juros = BigDecimal.ZERO.setScale(2);

    protected Cobranca() {}
    public Cobranca(String id, String pedidoId, Long clienteId, String metodo, String tipo, BigDecimal valor, String provedor, long criadaEm) {
        this.id = id; this.pedidoId = pedidoId; this.clienteId = clienteId; this.metodo = metodo; this.tipo = tipo;
        this.valor = valor; this.provedor = provedor; this.criadaEm = criadaEm; this.status = PENDENTE;
    }
    public String getId() { return id; }
    public String getPedidoId() { return pedidoId; }
    public Long getClienteId() { return clienteId; }
    public String getMetodo() { return metodo; }
    public String getTipo() { return tipo; }
    public BigDecimal getValor() { return valor; }
    public String getStatus() { return status; }
    public String getMotivo() { return motivo; }
    public String getProvedor() { return provedor; }
    public String getTxid() { return txid; }
    public String getPixCopiaECola() { return pixCopiaECola; }
    public String getQrCode() { return qrCode; }
    public Long getExpiraEm() { return expiraEm; }
    public String getCartaoId() { return cartaoId; }
    public String getCartaoDescricao() { return cartaoDescricao; }
    public Integer getParcelas() { return parcelas; }
    public BigDecimal getValorEstornado() { return valorEstornado; }
    public long getCriadaEm() { return criadaEm; }
    public Long getPagaEm() { return pagaEm; }
    public Long getEstornadaEm() { return estornadaEm; }
    public String getLinkPagamento() { return linkPagamento; }
    public BigDecimal getJuros() { return juros; }
    void juros(BigDecimal j) { this.juros = j == null ? BigDecimal.ZERO.setScale(2) : j; }
    /** O que foi pago pelo serviço, sem os juros do parcelamento (que ficam com a TaskGO). */
    BigDecimal base() { return liquido().subtract(juros).max(BigDecimal.ZERO); }
    /* Cobrança criada no provedor (Asaas): id dele no txid; cartão novo paga na página dele (link). */
    void externa(String idProvedor, String link) { this.txid = idProvedor; this.linkPagamento = link; }

    void pix(String txid, String copiaECola, String qrCode, long expiraEm) { this.txid = txid; this.pixCopiaECola = copiaECola; this.qrCode = qrCode; this.expiraEm = expiraEm; }
    void cartao(Cartao c, int parcelas) { this.cartaoId = c.getId(); this.cartaoDescricao = c.descricao(); this.parcelas = parcelas; }
    void pagar(long quando) { status = PAGA; pagaEm = quando; motivo = null; }
    void recusar(String motivo) { status = RECUSADA; this.motivo = motivo; }
    void cancelar(String motivo) { status = CANCELADA; this.motivo = motivo; }
    void estornar(BigDecimal quanto, long quando) {
        valorEstornado = valorEstornado.add(quanto);
        estornadaEm = quando;
        if (valorEstornado.compareTo(valor) >= 0) status = ESTORNADA;
    }
    BigDecimal liquido() { return PAGA.equals(status) || ESTORNADA.equals(status) ? valor.subtract(valorEstornado) : BigDecimal.ZERO; }
}
