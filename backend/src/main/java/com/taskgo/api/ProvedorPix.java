package com.taskgo.api;

import java.math.BigDecimal;

/* Quem gera e confirma as cobranças Pix. Hoje é o simulador da API do Banco Inter; trocar de banco é trocar esta implementação. */
public interface ProvedorPix {
    String nome();
    CobrancaPix criar(String txid, BigDecimal valor, String descricao, int expiracaoSegundos);
    /* Situação da cobrança no provedor: ATIVA, PAGA, CANCELADA... */
    String status(String txid);
    /** true só se o provedor confirmou o cancelamento; false se recusou (pode ter sido paga) ou não respondeu. */
    boolean cancelar(String txid);
    /* Só existe em simuladores. */
    default void simularPagamento(String txid) { throw new UnsupportedOperationException(); }

    record CobrancaPix(String copiaECola, String qrCode) {}
}
