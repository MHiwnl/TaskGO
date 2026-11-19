package com.taskgo.api;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/* Uma categoria que o profissional atende. */
@Entity
@Table(name = "profissional_servicos")
public class ProfissionalServico {
    @Id private String id;
    @Column(name = "usuario_id", nullable = false) private Long usuarioId;
    @Column(nullable = false) private String servico;
    protected ProfissionalServico() {}
    public ProfissionalServico(Long usuarioId, String servico) { this.id = usuarioId + ":" + servico; this.usuarioId = usuarioId; this.servico = servico; }
    public Long getUsuarioId() { return usuarioId; }
    public String getServico() { return servico; }
}
