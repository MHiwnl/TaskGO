package com.taskgo.api;

import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/* Foto de um pedido (contexto "pedido") ou da conversa (contexto "mensagem"). */
@Entity
@Table(name = "fotos_pedido")
public class FotoPedido {
    @Id private String id;
    @Column(name = "pedido_id", nullable = false) private String pedidoId;
    @Column(name = "autor_id", nullable = false) private Long autorId;
    @Column(nullable = false) private String contexto;
    @Column(nullable = false) private String tipo;
    @Column(nullable = false) private int largura;
    @Column(nullable = false) private int altura;
    @Basic(fetch = FetchType.LAZY) @Column(nullable = false, columnDefinition = "bytea") private byte[] dados;
    @Column(name = "criada_em", nullable = false) private long criadaEm;

    protected FotoPedido() {}
    public FotoPedido(String id, String pedidoId, Long autorId, String contexto, String tipo, int largura, int altura, byte[] dados, long criadaEm) {
        this.id = id; this.pedidoId = pedidoId; this.autorId = autorId; this.contexto = contexto; this.tipo = tipo;
        this.largura = largura; this.altura = altura; this.dados = dados; this.criadaEm = criadaEm;
    }
    public String getId() { return id; }
    public String getPedidoId() { return pedidoId; }
    public Long getAutorId() { return autorId; }
    public String getContexto() { return contexto; }
    public String getTipo() { return tipo; }
    public int getLargura() { return largura; }
    public int getAltura() { return altura; }
    public byte[] getDados() { return dados; }
    public long getCriadaEm() { return criadaEm; }
}
