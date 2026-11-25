package com.taskgo.api;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;

/* Plano Pro de um profissional. Vale (benefícios ligados) enquanto pagoAte estiver no futuro.
   status: pendente (esperando o 1º pagamento), ativa (renova todo mês) ou cancelada (não renova; vale até pagoAte). */
@Entity
@Table(name = "assinaturas")
public class Assinatura {
    public static final String PENDENTE = "pendente", ATIVA = "ativa", CANCELADA = "cancelada";
    public static final String CARTAO = "cartao", PIX = "pix";

    @Id private String id;
    /* Quem salvar uma cópia antiga (a renovação mensal, por exemplo) falha em vez de apagar um pagamento que caiu no meio. */
    @jakarta.persistence.Version private Long versao;
    @Column(name = "usuario_id", nullable = false) private Long usuarioId;
    @Column(nullable = false) private String forma;
    @Column(name = "cartao_id") private String cartaoId;
    @Column(nullable = false) private String status;
    @Column(nullable = false, precision = 19, scale = 2) private BigDecimal valor;
    @Column(name = "pago_ate") private Long pagoAte;
    @Column(name = "criada_em", nullable = false) private long criadaEm;
    @Column(name = "cancelada_em") private Long canceladaEm;
    @Column(name = "ultima_tentativa") private Long ultimaTentativa;
    @Column(name = "aviso_enviado") private Long avisoEnviado;
    /* Início da contratação atual (1º pagamento de quem assinou ou voltou depois de ficar sem plano). Renovação não muda. */
    @Column(name = "contratada_em") private Long contratadaEm;
    @Column(name = "desistida_em") private Long desistidaEm;

    protected Assinatura() {}
    public Assinatura(String id, Long usuarioId, String forma, String cartaoId, BigDecimal valor, long criadaEm) {
        this.id = id; this.usuarioId = usuarioId; this.forma = forma; this.cartaoId = cartaoId; this.valor = valor;
        this.criadaEm = criadaEm; this.status = PENDENTE;
    }

    public String getId() { return id; }
    public Long getUsuarioId() { return usuarioId; }
    public String getForma() { return forma; }
    public String getCartaoId() { return cartaoId; }
    public String getStatus() { return status; }
    public BigDecimal getValor() { return valor; }
    public Long getPagoAte() { return pagoAte; }
    public long getCriadaEm() { return criadaEm; }
    public Long getCanceladaEm() { return canceladaEm; }
    public Long getUltimaTentativa() { return ultimaTentativa; }
    public Long getAvisoEnviado() { return avisoEnviado; }
    public Long getContratadaEm() { return contratadaEm; }
    public Long getDesistidaEm() { return desistidaEm; }

    /** Benefícios ligados agora. */
    public boolean vale(long agora) { return pagoAte != null && pagoAte > agora; }
    public void forma(String forma, String cartaoId) { this.forma = forma; this.cartaoId = cartaoId; }
    public void cartao(String cartaoId) { this.cartaoId = cartaoId; }
    public void pagoAte(long ate) { this.pagoAte = ate; this.status = ATIVA; this.canceladaEm = null; this.ultimaTentativa = null; this.avisoEnviado = null; }
    public void ativar() { this.status = ATIVA; this.canceladaEm = null; }
    public void cancelar(long agora) { this.status = CANCELADA; this.canceladaEm = agora; }
    public void contratou(long quando) { this.contratadaEm = quando; }
    /* Desistência dentro de 7 dias: acaba na hora, sem benefício. */
    public void desistir(long agora) { this.status = CANCELADA; this.canceladaEm = agora; this.desistidaEm = agora; this.pagoAte = agora; }
    public void tentou(long agora) { this.ultimaTentativa = agora; }
    public void avisou(long agora) { this.avisoEnviado = agora; }
}
