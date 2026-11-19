package com.taskgo.api;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/* Uma sessão aberta (um navegador ou aparelho). Encerrada, o filtro derruba quem ainda estiver com ela. */
@Entity
@Table(name = "sessoes_conta")
public class SessaoConta {
    @Id private String id;
    @Column(name = "usuario_id", nullable = false) private Long usuarioId;
    @Column(length = 120) private String dispositivo;
    @Column(length = 64) private String ip;
    @Column(name = "criada_em", nullable = false) private long criadaEm;
    @Column(name = "vista_em", nullable = false) private long vistaEm;
    @Column(name = "encerrada_em") private Long encerradaEm;

    protected SessaoConta() {}
    public SessaoConta(Long usuarioId, String dispositivo, String ip, long agora) {
        this.id = java.util.UUID.randomUUID().toString(); this.usuarioId = usuarioId; this.dispositivo = dispositivo; this.ip = ip; this.criadaEm = agora; this.vistaEm = agora;
    }
    public String getId() { return id; }
    public Long getUsuarioId() { return usuarioId; }
    public String getDispositivo() { return dispositivo; }
    public String getIp() { return ip; }
    public long getCriadaEm() { return criadaEm; }
    public long getVistaEm() { return vistaEm; }
    public Long getEncerradaEm() { return encerradaEm; }
    public boolean ativa() { return encerradaEm == null; }
    public void ver(long agora) { this.vistaEm = agora; }
    public void encerrar(long agora) { if (encerradaEm == null) encerradaEm = agora; }

    /* Nome legível do aparelho a partir do User-Agent: "Chrome no Windows". */
    public static String dispositivo(String ua) {
        if (ua == null || ua.isBlank()) return "Navegador desconhecido";
        String n = ua.contains("Edg/") ? "Edge" : ua.contains("OPR/") ? "Opera" : ua.contains("Firefox/") ? "Firefox" : ua.contains("Chrome/") ? "Chrome" : ua.contains("Safari/") ? "Safari" : "Navegador";
        String s = ua.contains("Android") ? "Android" : ua.contains("iPhone") || ua.contains("iPad") ? "iPhone/iPad" : ua.contains("Windows") ? "Windows" : ua.contains("Mac OS") ? "Mac" : ua.contains("Linux") ? "Linux" : "outro sistema";
        return n + " no " + s;
    }
}
