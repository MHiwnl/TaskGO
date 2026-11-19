package com.taskgo.api;

/* Regras por faixa de valor das obras. */
import java.math.BigDecimal;

final class FaixaObra {
    private FaixaObra() {}
    static final BigDecimal MIL = new BigDecimal("1000"), TRES = new BigDecimal("3000"), CINCO = new BigDecimal("5000"), DEZ = new BigDecimal("10000");
    static final BigDecimal GARANTIA = new BigDecimal("0.08");
    static final BigDecimal MATERIAL_MAX = new BigDecimal("0.30");

    /** Taxa da TaskGO pelo valor total: 15% até R$ 999, 12% até 2.999, 10% até 4.999, 8% até 9.999, 6% a partir de 10.000. */
    static BigDecimal taxa(BigDecimal total) {
        if (total.compareTo(MIL) < 0) return new BigDecimal("0.15");
        if (total.compareTo(TRES) < 0) return new BigDecimal("0.12");
        if (total.compareTo(CINCO) < 0) return new BigDecimal("0.10");
        if (total.compareTo(DEZ) < 0) return new BigDecimal("0.08");
        return new BigDecimal("0.06");
    }
    static int minimoEtapas(BigDecimal total) {
        if (total.compareTo(DEZ) >= 0) return 4;
        if (total.compareTo(CINCO) >= 0) return 3;
        if (total.compareTo(TRES) >= 0) return 2;
        return 1;
    }
    static int maximoEtapas(BigDecimal total) { return total.compareTo(TRES) < 0 && total.compareTo(MIL) >= 0 ? 3 : 12; }
    /** A partir de R$ 10.000, nenhuma etapa passa de 30% do total. */
    static boolean etapaGrandeDemais(BigDecimal etapa, BigDecimal total) { return total.compareTo(DEZ) >= 0 && etapa.compareTo(total.multiply(MATERIAL_MAX)) > 0; }
    static boolean contrato(BigDecimal total) { return total.compareTo(CINCO) >= 0; }
    static boolean revisaoEquipe(BigDecimal total) { return total.compareTo(DEZ) >= 0; }
    static boolean garantia(BigDecimal total) { return total.compareTo(TRES) >= 0; }
}
