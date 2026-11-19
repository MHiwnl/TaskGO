package com.taskgo.api;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;

/* O que uma conta deve: serviço não pago ou taxa de cancelamento (cliente) e taxa de serviço recebido em dinheiro (trabalhador). */
@Entity
@Table(name = "pendencias")
public class Pendencia {
    public static final String SERVICO_NAO_PAGO = "servico_nao_pago", TAXA_CANCELAMENTO = "taxa_cancelamento", TAXA_DINHEIRO = "taxa_dinheiro";
    public static final String ABERTA = "aberta", PAGA = "paga", CANCELADA = "cancelada";

    @Id private String id;
    @Column(name = "usuario_id", nullable = false) private Long usuarioId;
    @Column(name = "credor_id") private Long credorId;
    @Column(name = "pedido_id", nullable = false) private String pedidoId;
    @Column(nullable = false) private String tipo;
    @Column(nullable = false, precision = 19, scale = 2) private BigDecimal valor;
    @Column(nullable = false, precision = 19, scale = 2) private BigDecimal pago = BigDecimal.ZERO.setScale(2);
    @Column(nullable = false) private String status = ABERTA;
    @Column(name = "cobranca_id") private String cobrancaId;
    @Column(name = "criada_em", nullable = false) private long criadaEm;
    @Column(name = "paga_em") private Long pagaEm;

    protected Pendencia() {}
    public Pendencia(String id, Long usuarioId, Long credorId, String pedidoId, String tipo, BigDecimal valor, long criadaEm) {
        this.id = id; this.usuarioId = usuarioId; this.credorId = credorId; this.pedidoId = pedidoId; this.tipo = tipo; this.valor = valor; this.criadaEm = criadaEm;
    }
    public String getId() { return id; }
    public Long getUsuarioId() { return usuarioId; }
    public Long getCredorId() { return credorId; }
    public String getPedidoId() { return pedidoId; }
    public String getTipo() { return tipo; }
    public BigDecimal getValor() { return valor; }
    public BigDecimal getPago() { return pago; }
    public String getStatus() { return status; }
    public String getCobrancaId() { return cobrancaId; }
    public long getCriadaEm() { return criadaEm; }
    public Long getPagaEm() { return pagaEm; }
    public BigDecimal restante() { return valor.subtract(pago); }
    public boolean aberta() { return ABERTA.equals(status); }
    void tentativa(String cobrancaId) { this.cobrancaId = cobrancaId; }
    /* Abate uma parte (ou tudo). Quitada, passa a paga. */
    void abater(BigDecimal parte, long quando) {
        pago = pago.add(parte).min(valor);
        if (pago.compareTo(valor) >= 0) { status = PAGA; pagaEm = quando; }
    }
    void cancelar() { status = CANCELADA; }
}
