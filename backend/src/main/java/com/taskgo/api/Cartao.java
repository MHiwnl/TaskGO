package com.taskgo.api;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/* Cartão salvo: só o token do provedor, a bandeira, o final e a validade. */
@Entity
@Table(name = "cartoes")
public class Cartao {
    @Id private String id;
    @Column(name = "usuario_id", nullable = false) private Long usuarioId;
    @Column(nullable = false) private String token;
    @Column(nullable = false) private String bandeira;
    @Column(nullable = false) private String final4;
    @Column(name = "mes_validade", nullable = false) private int mesValidade;
    @Column(name = "ano_validade", nullable = false) private int anoValidade;
    @Column(nullable = false) private String titular;
    @Column(nullable = false) private boolean padrao;
    /* Só no modo simulado: o resultado que o número de teste provoca ao cobrar. */
    @Column(name = "resultado_simulado") private String resultadoSimulado;
    @Column(name = "criado_em", nullable = false) private long criadoEm;

    protected Cartao() {}
    public Cartao(String id, Long usuarioId, String token, String bandeira, String final4, int mes, int ano, String titular, String resultadoSimulado, long criadoEm) {
        this.id = id; this.usuarioId = usuarioId; this.token = token; this.bandeira = bandeira; this.final4 = final4;
        this.mesValidade = mes; this.anoValidade = ano; this.titular = titular; this.resultadoSimulado = resultadoSimulado; this.criadoEm = criadoEm;
    }
    public String getId() { return id; }
    public Long getUsuarioId() { return usuarioId; }
    public String getToken() { return token; }
    public String getBandeira() { return bandeira; }
    public String getFinal4() { return final4; }
    public int getMesValidade() { return mesValidade; }
    public int getAnoValidade() { return anoValidade; }
    public String getTitular() { return titular; }
    public boolean isPadrao() { return padrao; }
    public void setPadrao(boolean padrao) { this.padrao = padrao; }
    public String getResultadoSimulado() { return resultadoSimulado; }
    public long getCriadoEm() { return criadoEm; }
    public String descricao() { return bandeira + " •••• " + final4; }
    public boolean vencido(java.time.YearMonth hoje) { return java.time.YearMonth.of(anoValidade, mesValidade).isBefore(hoje); }
}
