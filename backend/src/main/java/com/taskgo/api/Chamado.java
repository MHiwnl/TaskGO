package com.taskgo.api;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/* Chamado de emergência: aberto por quem está num atendimento, atendido pela equipe de suporte. */
@Entity
@Table(name = "chamados")
public class Chamado {
    public static final String ABERTO = "aberto", EM_ATENDIMENTO = "em_atendimento", RESOLVIDO = "resolvido";
    @Id private String id;
    @Column(name = "pedido_id") private String pedidoId;
    @Column(name = "autor_id", nullable = false) private Long autorId;
    @Column(nullable = false, length = 20) private String tipo;
    @Column(nullable = false, length = 20) private String status;
    @Column(columnDefinition = "text") private String mensagem;
    private Double latitude;
    private Double longitude;
    @Column(name = "criado_em", nullable = false) private long criadoEm;
    @Column(name = "assumido_por") private Long assumidoPor;
    @Column(name = "assumido_em") private Long assumidoEm;
    @Column(name = "resolvido_em") private Long resolvidoEm;
    @Column(columnDefinition = "text") private String nota;

    protected Chamado() {}
    public Chamado(String pedidoId, Long autorId, String tipo, String mensagem, Double latitude, Double longitude, long criadoEm) {
        this.id = java.util.UUID.randomUUID().toString(); this.pedidoId = pedidoId; this.autorId = autorId; this.tipo = tipo; this.status = ABERTO;
        this.mensagem = mensagem; this.latitude = latitude; this.longitude = longitude; this.criadoEm = criadoEm;
    }
    public String getId() { return id; }
    public String getPedidoId() { return pedidoId; }
    public Long getAutorId() { return autorId; }
    public String getTipo() { return tipo; }
    public String getStatus() { return status; }
    public String getMensagem() { return mensagem; }
    public Double getLatitude() { return latitude; }
    public Double getLongitude() { return longitude; }
    public long getCriadoEm() { return criadoEm; }
    public Long getAssumidoPor() { return assumidoPor; }
    public Long getAssumidoEm() { return assumidoEm; }
    public Long getResolvidoEm() { return resolvidoEm; }
    public String getNota() { return nota; }
    public void assumir(Long por, long quando) { this.status = EM_ATENDIMENTO; this.assumidoPor = por; this.assumidoEm = quando; }
    public void resolver(String nota, long quando) { this.status = RESOLVIDO; this.nota = nota; this.resolvidoEm = quando; }
    public void atualizarPosicao(Double lat, Double lng) { this.latitude = lat; this.longitude = lng; }
}
