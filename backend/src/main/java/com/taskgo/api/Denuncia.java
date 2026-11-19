package com.taskgo.api;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/* Denúncia de uma pessoa contra outra, ligada ao pedido em que as duas estiveram. */
@Entity
@Table(name = "denuncias")
public class Denuncia {
    public static final String EM_ANALISE = "em_analise", PROCEDENTE = "procedente", IMPROCEDENTE = "improcedente";
    @Id private String id;
    @Column(name = "denunciante_id", nullable = false) private Long denuncianteId;
    @Column(name = "denunciado_id", nullable = false) private Long denunciadoId;
    @Column(name = "pedido_id") private String pedidoId;
    @Column(nullable = false, length = 40) private String motivo;
    @Column(columnDefinition = "text") private String texto;
    @Column(nullable = false, length = 20) private String status;
    @Column(name = "criada_em", nullable = false) private long criadaEm;
    @Column(name = "decidida_em") private Long decididaEm;
    @Column(name = "decidida_por") private Long decididaPor;
    @Column(columnDefinition = "text") private String resposta;

    protected Denuncia() {}
    public Denuncia(Long denuncianteId, Long denunciadoId, String pedidoId, String motivo, String texto, long criadaEm) {
        this.id = java.util.UUID.randomUUID().toString(); this.denuncianteId = denuncianteId; this.denunciadoId = denunciadoId;
        this.pedidoId = pedidoId; this.motivo = motivo; this.texto = texto; this.status = EM_ANALISE; this.criadaEm = criadaEm;
    }
    public String getId() { return id; }
    public Long getDenuncianteId() { return denuncianteId; }
    public Long getDenunciadoId() { return denunciadoId; }
    public String getPedidoId() { return pedidoId; }
    public String getMotivo() { return motivo; }
    public String getTexto() { return texto; }
    public String getStatus() { return status; }
    public long getCriadaEm() { return criadaEm; }
    public Long getDecididaEm() { return decididaEm; }
    public Long getDecididaPor() { return decididaPor; }
    public String getResposta() { return resposta; }
    public void decidir(String status, Long por, String resposta, long quando) { this.status = status; this.decididaPor = por; this.resposta = resposta; this.decididaEm = quando; }
}
