package com.taskgo.api;

import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/* Pedido de verificação de identidade de uma conta. */
@Entity
@Table(name = "verificacoes")
public class Verificacao {
    public static final String PENDENTE = "pendente", APROVADA = "aprovada", RECUSADA = "recusada";
    @Id private String id;
    @Column(name = "usuario_id", nullable = false) private Long usuarioId;
    @Column(nullable = false) private String status;
    @Basic(fetch = FetchType.LAZY) @Column(name = "doc_frente", columnDefinition = "bytea") private byte[] docFrente;
    @Basic(fetch = FetchType.LAZY) @Column(name = "doc_verso", columnDefinition = "bytea") private byte[] docVerso;
    @Basic(fetch = FetchType.LAZY) @Column(columnDefinition = "bytea") private byte[] selfie;
    private String motivo;
    @jakarta.persistence.Column(columnDefinition = "text") private String correcao;
    @Column(name = "enviada_em", nullable = false) private long enviadaEm;
    @Column(name = "decidida_em") private Long decididaEm;
    @Column(name = "decidida_por") private Long decididaPor;

    protected Verificacao() {}
    public Verificacao(String id, Long usuarioId, byte[] frente, byte[] verso, byte[] selfie, long enviadaEm) {
        this.id = id; this.usuarioId = usuarioId; this.docFrente = frente; this.docVerso = verso; this.selfie = selfie; this.enviadaEm = enviadaEm; this.status = PENDENTE;
    }
    public String getId() { return id; }
    public Long getUsuarioId() { return usuarioId; }
    public String getStatus() { return status; }
    public byte[] getDocFrente() { return docFrente; }
    public byte[] getDocVerso() { return docVerso; }
    public byte[] getSelfie() { return selfie; }
    public String getMotivo() { return motivo; }
    public String getCorrecao() { return correcao; }
    public long getEnviadaEm() { return enviadaEm; }
    public Long getDecididaEm() { return decididaEm; }
    /* Decidida: as imagens são apagadas. */
    void decidir(boolean aprovada, String motivo, String correcao, Long admin, long quando) {
        this.status = aprovada ? APROVADA : RECUSADA; this.motivo = motivo; this.correcao = correcao; this.decididaPor = admin; this.decididaEm = quando;
        this.docFrente = null; this.docVerso = null; this.selfie = null;
    }
}
