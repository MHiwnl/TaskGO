package com.taskgo.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

/* Reserva da devolução no banco real (H2): uma reserva por vez, a confirmação só soma a parte reservada e a cobrança fica estornada ao chegar ao valor. */
@DataJpaTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:taskgo-estorno;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false"
})
class CobrancaEstornoRepositoryTest {
    @Autowired private CobrancaRepository cobrancas;
    @Autowired private EntityManager em;

    private void paga(String id, String valor) {
        Cobranca c = new Cobranca(id, "p1", 7L, "cartao", "principal", new BigDecimal(valor), "asaas", 0);
        c.pagar(1);
        cobrancas.saveAndFlush(c);
    }

    private Cobranca recarregada(String id) {
        em.clear();
        return cobrancas.findById(id).orElseThrow();
    }

    @Test void segundaReservaEnquantoUmaEstaEmAndamentoNaoPassa() {
        paga("c1", "100.00");
        assertEquals(1, cobrancas.reservarEstorno("c1", new BigDecimal("60.00"), 5L, Cobranca.PAGA));
        assertEquals(0, cobrancas.reservarEstorno("c1", new BigDecimal("40.00"), 6L, Cobranca.PAGA));
    }

    @Test void reservaAcimaDoSaldoNaoPassa() {
        paga("c1", "100.00");
        assertEquals(0, cobrancas.reservarEstorno("c1", new BigDecimal("150.00"), 5L, Cobranca.PAGA));
    }

    @Test void confirmacaoSomaSoAParteReservadaEFechaAoChegarAoValor() {
        paga("c1", "100.00");
        cobrancas.reservarEstorno("c1", new BigDecimal("60.00"), 5L, Cobranca.PAGA);
        assertEquals(0, cobrancas.concluirEstorno("c1", new BigDecimal("50.00"), 6L, Cobranca.ESTORNADA));
        assertEquals(1, cobrancas.concluirEstorno("c1", new BigDecimal("60.00"), 6L, Cobranca.ESTORNADA));
        Cobranca c = recarregada("c1");
        assertEquals(0, new BigDecimal("60.00").compareTo(c.getValorEstornado()));
        assertEquals(Cobranca.PAGA, c.getStatus());
        assertNull(c.getValorEstornando());

        assertEquals(1, cobrancas.reservarEstorno("c1", new BigDecimal("40.00"), 7L, Cobranca.PAGA));
        assertEquals(1, cobrancas.concluirEstorno("c1", new BigDecimal("40.00"), 8L, Cobranca.ESTORNADA));
        c = recarregada("c1");
        assertEquals(Cobranca.ESTORNADA, c.getStatus());
        assertEquals(0, c.liquido().signum());
    }

    @Test void liberaSoAReservaDaMesmaTentativa() {
        paga("c1", "100.00");
        cobrancas.reservarEstorno("c1", new BigDecimal("60.00"), 5L, Cobranca.PAGA);
        assertEquals(0, cobrancas.liberarEstorno("c1", 4L));
        assertEquals(1, cobrancas.liberarEstorno("c1", 5L));
        assertEquals(1, cobrancas.reservarEstorno("c1", new BigDecimal("60.00"), 9L, Cobranca.PAGA));
    }

    @Test void conciliacaoAchaSoAsReservasMaisAntigasQueOLimite() {
        paga("c1", "100.00");
        cobrancas.reservarEstorno("c1", new BigDecimal("60.00"), 5L, Cobranca.PAGA);
        assertEquals(List.of("c1"), cobrancas.findByValorEstornandoIsNotNullAndEstornandoDesdeLessThan(10L).stream().map(Cobranca::getId).toList());
        assertEquals(List.of(), cobrancas.findByValorEstornandoIsNotNullAndEstornandoDesdeLessThan(5L).stream().map(Cobranca::getId).toList());
    }
}
