package com.taskgo.api;

import java.util.Map;

/* Erros repetidos guardados no extra da conta ou do pedido: ficam no banco, então novo login ou reinício não zera.
   Conta os erros a partir do primeiro; ao chegar ao limite, bloqueia por `bloqueioMs` a partir dali. */
final class LimiteErros {
    private LimiteErros() {}

    /** Milissegundos até liberar; 0 se pode tentar agora. */
    static long esperaMs(Map<String, Object> extra, String chave, long agora) {
        return extra.get(chave + "Ate") instanceof Number ate && ate.longValue() > agora ? ate.longValue() - agora : 0;
    }

    /** Registra um erro e devolve quantas tentativas restam (0 quando bloqueia). */
    static int errou(Map<String, Object> extra, String chave, int max, long janelaMs, long bloqueioMs, long agora) {
        boolean nova = !(extra.get(chave + "Desde") instanceof Number desde) || agora - desde.longValue() >= janelaMs;
        int erros = nova ? 1 : (extra.get(chave) instanceof Number n ? n.intValue() : 0) + 1;
        if (nova) extra.put(chave + "Desde", agora);
        if (erros >= max) {
            zerar(extra, chave);
            extra.put(chave + "Ate", agora + bloqueioMs);
            return 0;
        }
        extra.put(chave, erros);
        return max - erros;
    }

    /** Zera a contagem (acerto ou fim do bloqueio). */
    static void zerar(Map<String, Object> extra, String chave) {
        extra.remove(chave); extra.remove(chave + "Desde"); extra.remove(chave + "Ate");
    }
}
