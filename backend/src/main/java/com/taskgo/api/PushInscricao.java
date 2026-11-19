package com.taskgo.api;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/* Um navegador inscrito para receber push de uma conta. */
@Entity
@Table(name = "push_inscricoes")
public class PushInscricao {
    @Id private String id;
    @Column(name = "usuario_id", nullable = false) private Long usuarioId;
    @Column(nullable = false, length = 1000) private String endpoint;
    @Column(nullable = false) private String p256dh;
    @Column(nullable = false) private String auth;
    @Column(name = "criada_em", nullable = false) private long criadaEm;

    protected PushInscricao() {}
    public PushInscricao(String id, Long usuarioId, String endpoint, String p256dh, String auth, long criadaEm) {
        this.id = id; this.usuarioId = usuarioId; this.endpoint = endpoint; this.p256dh = p256dh; this.auth = auth; this.criadaEm = criadaEm;
    }
    public String getId() { return id; }
    public Long getUsuarioId() { return usuarioId; }
    public String getEndpoint() { return endpoint; }
    public String getP256dh() { return p256dh; }
    public String getAuth() { return auth; }
}
