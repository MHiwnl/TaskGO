package com.taskgo.api;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/* Um horário em que o profissional não atende. */
@Entity
@Table(name = "agenda_bloqueios")
public class BloqueioAgenda {
    @Id private String id;
    @Column(name = "usuario_id", nullable = false) private Long usuarioId;
    @Column(nullable = false) private long inicio;
    @Column(nullable = false) private long fim;
    private String motivo;
    @Column(name = "criado_em", nullable = false) private long criadoEm;

    protected BloqueioAgenda() {}
    public BloqueioAgenda(String id, Long usuarioId, long inicio, long fim, String motivo, long criadoEm) {
        this.id = id; this.usuarioId = usuarioId; this.inicio = inicio; this.fim = fim; this.motivo = motivo; this.criadoEm = criadoEm;
    }
    public String getId() { return id; }
    public Long getUsuarioId() { return usuarioId; }
    public long getInicio() { return inicio; }
    public long getFim() { return fim; }
    public String getMotivo() { return motivo; }
}
