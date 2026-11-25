package com.taskgo.api;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/* Recurso contra um aviso, uma suspensão ou um banimento da moderação. */
@Entity
@Table(name = "recursos_moderacao")
public class Recurso {
    public static final String EM_ANALISE = "em_analise", APROVADO = "aprovado", NEGADO = "negado";

    @Id private String id;
    @Column(name = "usuario_id", nullable = false) private Long usuarioId;
    /* Pedido do aviso contestado: a conversa só abre para esse pedido. Nulo nos recursos antigos e nos avisos sem pedido. */
    @Column(name = "pedido_id") private String pedidoId;
    @Column(columnDefinition = "text", nullable = false) private String texto;
    @Column(nullable = false) private String status;
    @Column(name = "criado_em", nullable = false) private long criadoEm;
    @Column(name = "decidido_em") private Long decididoEm;
    @Column(name = "decidido_por") private String decididoPor;
    @Column(columnDefinition = "text") private String resposta;

    protected Recurso() {}
    public Recurso(String id, Long usuarioId, String pedidoId, String texto, long criadoEm) { this.id = id; this.usuarioId = usuarioId; this.pedidoId = pedidoId; this.texto = texto; this.status = EM_ANALISE; this.criadoEm = criadoEm; }
    public String getId() { return id; }
    public Long getUsuarioId() { return usuarioId; }
    public String getPedidoId() { return pedidoId; }
    public String getTexto() { return texto; }
    public String getStatus() { return status; }
    public long getCriadoEm() { return criadoEm; }
    public Long getDecididoEm() { return decididoEm; }
    public String getDecididoPor() { return decididoPor; }
    public String getResposta() { return resposta; }
    public void decidir(String status, String por, String resposta, long quando) { this.status = status; this.decididoPor = por; this.resposta = resposta; this.decididoEm = quando; }
}
