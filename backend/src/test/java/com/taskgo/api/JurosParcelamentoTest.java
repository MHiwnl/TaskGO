package com.taskgo.api;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class JurosParcelamentoTest {
    @Test void jurosSoDe7a12xEFicamForaDoServico() {
        assertEquals(12, PagamentoService.maxParcelasObra(new BigDecimal("1500")));
        assertEquals(6, PagamentoService.maxParcelas(new BigDecimal("999")));
        Cobranca c = new Cobranca("c1", "p1", 1L, "cartao", "principal", new BigDecimal("1650.00"), "cartao-simulado", 0);
        c.juros(new BigDecimal("150.00"));
        c.pagar(1);
        assertEquals(0, new BigDecimal("1500.00").compareTo(c.base()));
        assertEquals(0, new BigDecimal("1650.00").compareTo(c.liquido()));
    }
}
