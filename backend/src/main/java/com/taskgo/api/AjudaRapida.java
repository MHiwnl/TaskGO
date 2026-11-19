package com.taskgo.api;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Ajuda rápida: alguém precisa de uma mão agora (pneu furado, carregar algo, ficou trancado...) e
 * qualquer pessoa com identidade verificada que ligou a ajuda rápida pode aceitar, ir e receber.
 * Sempre no modo "chamar agora", com valor mínimo e taxa própria.
 */
public final class AjudaRapida {
    public static final String SERVICO = "ajuda";
    public static final BigDecimal TAXA = new BigDecimal("0.10");
    public static final double MINIMO = 50;
    static final String CHAVE = "ajudaRapida";

    private AjudaRapida() {}

    public static boolean eh(Pedido p) { return p != null && SERVICO.equals(p.getService()); }

    /** Já ligou a ajuda rápida alguma vez (viu o passo a passo e aceitou as regras). */
    public static boolean conhece(Usuario u) { return u != null && u.getExtra().get(CHAVE) instanceof Map<?, ?>; }

    /** Disponível agora para receber chamados de ajuda. */
    public static boolean ativo(Usuario u) { return conhece(u) && Boolean.TRUE.equals(((Map<?, ?>) u.getExtra().get(CHAVE)).get("ativo")); }

    /** Pode receber e aceitar chamados: verificada, com a ajuda ligada e sem banimento. */
    public static boolean podeAjudar(Usuario u) {
        return u != null && VerificacaoController.verificado(u) && ativo(u) && !Boolean.TRUE.equals(u.getExtra().get(ModeracaoService.BANIDO))
                && java.util.Set.of("cliente", "profissional").contains(String.valueOf(u.getTipo()).toLowerCase(java.util.Locale.ROOT));
    }

    /** Identificador que vai no pedido para quem não é profissional (o pedido sempre guarda um). */
    public static String idAjudante(Usuario u) { return u.getProId() != null && !u.getProId().isBlank() ? u.getProId() : "ajd_" + u.getId(); }
}
