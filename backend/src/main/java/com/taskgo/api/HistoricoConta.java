package com.taskgo.api;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/* Um fato com data na vida de uma conta. Alimenta o Log do administrador. */
@Entity
@Table(name = "historico_conta")
public class HistoricoConta {
    @Id private String id;
    @Column(name = "usuario_id", nullable = false) private Long usuarioId;
    @Column(nullable = false, length = 40) private String tipo;
    @Column(nullable = false, length = 500) private String texto;
    @Column(name = "autor_id") private Long autorId;
    @Column(name = "pedido_id") private String pedidoId;
    @Column(name = "criado_em", nullable = false) private long criadoEm;

    protected HistoricoConta() {}
    public HistoricoConta(Long usuarioId, String tipo, String texto, Long autorId, String pedidoId, long criadoEm) {
        this.id = java.util.UUID.randomUUID().toString(); this.usuarioId = usuarioId; this.tipo = tipo;
        this.texto = texto.length() > 500 ? texto.substring(0, 500) : texto; this.autorId = autorId; this.pedidoId = pedidoId; this.criadoEm = criadoEm;
    }
    public String getId() { return id; }
    public Long getUsuarioId() { return usuarioId; }
    public String getTipo() { return tipo; }
    public String getTexto() { return texto; }
    public Long getAutorId() { return autorId; }
    public String getPedidoId() { return pedidoId; }
    public long getCriadoEm() { return criadoEm; }
}
