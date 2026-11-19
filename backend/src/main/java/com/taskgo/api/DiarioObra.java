package com.taskgo.api;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/* Um dia de trabalho na obra: chegada (com a distância até o endereço), saída, o que foi feito e uma foto. */
@Entity
@Table(name = "diario_obra")
public class DiarioObra {
    @Id private String id;
    @Column(name = "pedido_id", nullable = false) private String pedidoId;
    @Column(name = "autor_id", nullable = false) private Long autorId;
    @Column(nullable = false) private String dia;
    @Column(name = "chegada_em", nullable = false) private long chegadaEm;
    @Column(name = "saida_em") private Long saidaEm;
    @Column(name = "distancia_m") private Integer distanciaM;
    @Column(columnDefinition = "text") private String texto;
    @Column(name = "foto_id") private String fotoId;

    protected DiarioObra() {}
    public DiarioObra(String id, String pedidoId, Long autorId, String dia, long chegadaEm, Integer distanciaM) {
        this.id = id; this.pedidoId = pedidoId; this.autorId = autorId; this.dia = dia; this.chegadaEm = chegadaEm; this.distanciaM = distanciaM;
    }
    public String getId() { return id; }
    public String getPedidoId() { return pedidoId; }
    public Long getAutorId() { return autorId; }
    public String getDia() { return dia; }
    public long getChegadaEm() { return chegadaEm; }
    public Long getSaidaEm() { return saidaEm; }
    public Integer getDistanciaM() { return distanciaM; }
    public String getTexto() { return texto; }
    public String getFotoId() { return fotoId; }
    void sair(long quando, String texto, String fotoId) { this.saidaEm = quando; this.texto = texto; this.fotoId = fotoId; }
}
