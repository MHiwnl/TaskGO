package com.taskgo.api;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PagamentoSimuladoTest {
    @Test
    void validaNumeroEBandeira() {
        assertTrue(CartaoSimulado.luhn("4111111111111111"));
        assertFalse(CartaoSimulado.luhn("4111111111111112"));
        assertFalse(CartaoSimulado.luhn("4111"));
        assertEquals("Visa", CartaoSimulado.bandeira("4111111111111111"));
        assertEquals("Mastercard", CartaoSimulado.bandeira("5555555555554444"));
        assertEquals("Mastercard", CartaoSimulado.bandeira("2223000048400011"));
        assertEquals("Amex", CartaoSimulado.bandeira("378282246310005"));
        assertEquals("Elo", CartaoSimulado.bandeira("6362970000457013"));
        assertNull(CartaoSimulado.bandeira("9999999999999995"));
        assertEquals("4111111111111111", CartaoSimulado.somenteDigitos("4111 1111-1111 1111"));
    }

    @Test
    void numerosDeTesteDecidemOResultado() {
        assertEquals("aprovado", CartaoSimulado.resultado("4111111111111111"));
        assertEquals("recusado", CartaoSimulado.resultado("4000000000000002"));
        assertEquals("sem_saldo", CartaoSimulado.resultado("4000000000009995"));
        Cartao recusa = new Cartao("c", 1L, "t", "Visa", "0002", 12, 2099, "TESTE", "recusado", 0);
        assertFalse(CartaoSimulado.cobrar(recusa).aprovado());
        assertNotNull(CartaoSimulado.cobrar(recusa).motivo());
        assertTrue(CartaoSimulado.cobrar(new Cartao("c", 1L, "t", "Visa", "1111", 12, 2099, "TESTE", "aprovado", 0)).aprovado());
    }

    @Test
    void parcelasSemJurosDeAte6xComMinimoDe50() {
        assertEquals(1, PagamentoService.maxParcelas(new BigDecimal("80")));
        assertEquals(3, PagamentoService.maxParcelas(new BigDecimal("150")));
        assertEquals(6, PagamentoService.maxParcelas(new BigDecimal("2000")));
    }

    @Test
    void estornoParcialMantemOLiquido() {
        Cobranca c = new Cobranca("x", "p", 1L, "cartao", "principal", new BigDecimal("200.00"), "cartao-simulado", 0);
        assertEquals(BigDecimal.ZERO, c.liquido());
        c.pagar(1);
        c.estornar(new BigDecimal("50.00"), 2);
        assertEquals(new BigDecimal("150.00"), c.liquido());
        assertEquals(Cobranca.PAGA, c.getStatus());
        c.estornar(new BigDecimal("150.00"), 3);
        assertEquals(Cobranca.ESTORNADA, c.getStatus());
        assertEquals(0, c.liquido().signum());
    }

    @Test
    void avisoPixSoLeTxids() {
        assertEquals(List.of("taskgoabc"), PagamentoService.txidsDoAviso(Map.of("pix", List.of(Map.of("txid", "taskgoabc", "valor", "10.00")))));
        assertEquals(List.of(), PagamentoService.txidsDoAviso("lixo"));
    }
}
