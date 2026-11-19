package com.taskgo.api;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "presenca_profissional")
public class PresencaProfissional {
    @Id @Column(name = "usuario_id") private Long usuarioId;
    private double latitude;
    private double longitude;
    private double precisao;
    @Column(name = "atualizada_em") private long atualizadaEm;

    protected PresencaProfissional() {}
    public PresencaProfissional(Long usuarioId, double latitude, double longitude, double precisao, long atualizadaEm) {
        this.usuarioId = usuarioId; this.latitude = latitude; this.longitude = longitude; this.precisao = precisao; this.atualizadaEm = atualizadaEm;
    }
    public Long getUsuarioId() { return usuarioId; }
    public double getLatitude() { return latitude; }
    public double getLongitude() { return longitude; }
    public double getPrecisao() { return precisao; }
    public long getAtualizadaEm() { return atualizadaEm; }
}
