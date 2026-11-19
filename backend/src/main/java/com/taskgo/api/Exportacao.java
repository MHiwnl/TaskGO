package com.taskgo.api;

import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;

/* Uma planilha (CSV) pedida no painel e gerada em segundo plano. */
@Entity
@Table(name = "exportacoes")
public class Exportacao {
    public static final String GERANDO = "gerando", PRONTA = "pronta", ERRO = "erro";
    @Id private String id;
    @Column(name = "autor_id", nullable = false) private Long autorId;
    @Column(nullable = false) private String tipo;
    @Column(nullable = false) private LocalDate de;
    @Column(nullable = false) private LocalDate ate;
    @Column(nullable = false) private String status;
    @Column(nullable = false) private long linhas;
    @Basic(fetch = FetchType.LAZY) @Column(columnDefinition = "bytea") private byte[] arquivo;
    private String erro;
    @Column(name = "criada_em", nullable = false) private long criadaEm;
    @Column(name = "pronta_em") private Long prontaEm;

    protected Exportacao() {}
    public Exportacao(String id, Long autorId, String tipo, LocalDate de, LocalDate ate, long criadaEm) {
        this.id = id; this.autorId = autorId; this.tipo = tipo; this.de = de; this.ate = ate; this.criadaEm = criadaEm; this.status = GERANDO;
    }
    public String getId() { return id; }
    public Long getAutorId() { return autorId; }
    public String getTipo() { return tipo; }
    public LocalDate getDe() { return de; }
    public LocalDate getAte() { return ate; }
    public String getStatus() { return status; }
    public long getLinhas() { return linhas; }
    public byte[] getArquivo() { return arquivo; }
    public String getErro() { return erro; }
    public long getCriadaEm() { return criadaEm; }
    public Long getProntaEm() { return prontaEm; }
    void pronta(byte[] arquivo, long linhas, long quando) { this.arquivo = arquivo; this.linhas = linhas; this.status = PRONTA; this.prontaEm = quando; }
    void falhou(String erro, long quando) { this.erro = erro == null ? null : erro.substring(0, Math.min(300, erro.length())); this.status = ERRO; this.prontaEm = quando; }
}
