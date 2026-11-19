package com.taskgo.api;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/* Proposta de valor numa negociação entre o cliente dono do pedido e um profissional.
   A última proposta de cada par é a que vale; as anteriores ficam como histórico. */
@Entity
@Table(name = "propostas")
public class Proposta {
    public static final String PENDENTE = "pendente", ACEITA = "aceita", RECUSADA = "recusada", SUBSTITUIDA = "substituida", CANCELADA = "cancelada";

    @Id private String id;
    @Column(name = "pedido_id", nullable = false) private String pedidoId;
    @Column(name = "profissional_id", nullable = false) private Long profissionalId;
    @Column(name = "profissional_email", nullable = false) private String profissionalEmail;
    @Column(name = "profissional_nome") private String profissionalNome;
    /* Quem fez esta proposta: "cliente" ou "profissional". */
    @Column(nullable = false) private String autor;
    @Column(nullable = false) private double valor;
    @Column(nullable = false) private String status;
    @Column(name = "criada_em", nullable = false) private long criadaEm;
    @Column(name = "respondida_em") private Long respondidaEm;

    protected Proposta() {}
    public Proposta(String id, String pedidoId, Usuario profissional, String autor, double valor, long criadaEm) {
        this.id = id; this.pedidoId = pedidoId; this.profissionalId = profissional.getId(); this.profissionalEmail = profissional.getEmail();
        this.profissionalNome = profissional.getNome(); this.autor = autor; this.valor = valor; this.status = PENDENTE; this.criadaEm = criadaEm;
    }
    public String getId() { return id; }
    public String getPedidoId() { return pedidoId; }
    public Long getProfissionalId() { return profissionalId; }
    public String getProfissionalEmail() { return profissionalEmail; }
    public String getProfissionalNome() { return profissionalNome; }
    public String getAutor() { return autor; }
    public double getValor() { return valor; }
    public String getStatus() { return status; }
    public long getCriadaEm() { return criadaEm; }
    public Long getRespondidaEm() { return respondidaEm; }
    public boolean pendente() { return PENDENTE.equals(status); }
    public void encerrar(String novoStatus, long quando) { this.status = novoStatus; this.respondidaEm = quando; }
}
