package com.taskgo.api;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;
import org.junit.jupiter.api.Test;

class FaixaObraTest {
    private static BigDecimal r(String v) { return new BigDecimal(v); }

    @Test void taxaDecrescentePorFaixa() {
        assertEquals(0, r("0.15").compareTo(FaixaObra.taxa(r("999.99"))));
        assertEquals(0, r("0.12").compareTo(FaixaObra.taxa(r("1000"))));
        assertEquals(0, r("0.10").compareTo(FaixaObra.taxa(r("3000"))));
        assertEquals(0, r("0.08").compareTo(FaixaObra.taxa(r("5000"))));
        assertEquals(0, r("0.06").compareTo(FaixaObra.taxa(r("10000"))));
    }

    @Test void regrasPorFaixa() {
        assertEquals(3, FaixaObra.maximoEtapas(r("2000")));
        assertEquals(2, FaixaObra.minimoEtapas(r("3500")));
        assertEquals(3, FaixaObra.minimoEtapas(r("6000")));
        assertFalse(FaixaObra.contrato(r("4999.99"))); assertTrue(FaixaObra.contrato(r("5000")));
        assertFalse(FaixaObra.garantia(r("2999"))); assertTrue(FaixaObra.garantia(r("3000")));
        assertTrue(FaixaObra.revisaoEquipe(r("10000")));
        assertTrue(FaixaObra.etapaGrandeDemais(r("3001"), r("10000")));
        assertFalse(FaixaObra.etapaGrandeDemais(r("3000"), r("10000")));
    }

    @Test void diasDeTrabalhoPulamFimDeSemana() {
        LocalDate sexta = LocalDate.of(2026, 11, 13);
        var dias = ObraService.diasDeTrabalho(sexta, Set.of(1, 2, 3, 4, 5), 3);
        assertEquals(LocalDate.of(2026, 11, 13), dias.get(0));
        assertEquals(LocalDate.of(2026, 11, 16), dias.get(1));
        assertEquals(LocalDate.of(2026, 11, 17), dias.get(2));
    }

    @Test void horarioValido() {
        assertArrayEquals(new int[]{8, 0, 17, 30}, ObraService.horario("08:00-17:30"));
        assertThrows(RuntimeException.class, () -> ObraService.horario("8h"));
        assertThrows(RuntimeException.class, () -> ObraService.horario("10:00-10:30"));
    }
}
