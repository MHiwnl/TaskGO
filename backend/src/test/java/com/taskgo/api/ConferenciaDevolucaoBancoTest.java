package com.taskgo.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

/* Conferência de devolução com o banco de verdade (H2): os UPDATEs condicionais da reserva, as consultas por tipo e situação
   e o que fica gravado. Os testes de PagamentoEstornoTest simulam o banco. */
@DataJpaTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:taskgo-conferencia;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false"
})
class ConferenciaDevolucaoBancoTest {
    @Autowired private CobrancaRepository cobrancas;
    @Autowired private PedidoContratadoRepository pedidos;
    @Autowired private TransacaoFinanceiraRepository transacoes;
    @Autowired private UsuarioRepository usuarios;
    @Autowired private CarteiraRepository carteiras;
    @Autowired private CartaoRepository cartoes;
    @Autowired private EntityManager em;

    private PagamentoService servico() {
        PagamentoService s = new PagamentoService("asaas", new BigDecimal("0.15"), cobrancas, cartoes, pedidos, usuarios, carteiras, transacoes,
                mock(ProvedorPix.class), mock(EventosService.class), mock(PublicacaoPedidoService.class));
        ReflectionTestUtils.setField(s, "em", em);
        return s;
    }

    /* Recorrente: duas semanas de R$ 50 canceladas numa cobrança do Asaas sem txid. A conferência soma as duas, a equipe marca
       e tudo fecha no banco: cobrança devolvida, pendências concluídas, conferência feita, pedido estornado. */
    @Test void duasSemanasNaConferenciaFechamNoBanco() {
        Pedido p = new Pedido();
        ReflectionTestUtils.setField(p, "id", "p1");
        p.setStatus("cancelado"); p.setPaid("estornando"); p.setClientId("7");
        pedidos.saveAndFlush(p);
        Cobranca c = new Cobranca("c1", "p1", 7L, "cartao", "principal", new BigDecimal("100.00"), "asaas", 0);
        c.pagar(1);
        cobrancas.saveAndFlush(c);
        PagamentoService svc = servico();

        for (int semana = 0; semana < 2; semana++) {
            PagamentoService.EstornoAdiado adiado = assertThrows(PagamentoService.EstornoAdiado.class, () -> svc.devolverCobranca(c, new BigDecimal("50.00")));
            svc.pendencia(c, new BigDecimal("50.00"), adiado, true);
        }
        /* A pendência repete: agora a conferência passa a valer a soma. */
        assertThrows(PagamentoService.EstornoAdiado.class, () -> svc.devolverCobranca(c, new BigDecimal("50.00")));
        em.flush(); em.clear();

        List<TransacaoFinanceira> abertas = transacoes.findByTipoAndStatus(PagamentoService.DEVOLUCAO_CONFERIR, "VERIFICAR");
        assertEquals(1, abertas.size(), "uma conferência aberta por cobrança");
        assertEquals(0, new BigDecimal("100.00").compareTo(abertas.get(0).getValor()));
        assertEquals(0, new BigDecimal("100.00").compareTo(svc.pendentesPorCobranca().get("c1")));

        assertEquals(0, new BigDecimal("100.00").compareTo(svc.devolvidaNaConferencia(abertas.get(0).getId(), 1L)));
        em.flush(); em.clear();

        Cobranca depois = cobrancas.findById("c1").orElseThrow();
        assertEquals(Cobranca.ESTORNADA, depois.getStatus());
        assertEquals(0, depois.liquido().signum());
        assertEquals("DONE", transacoes.findById(abertas.get(0).getId()).orElseThrow().getStatus());
        assertEquals(List.of(), transacoes.findByTipoAndStatus("DEVOLUCAO_PENDENTE", "PENDENTE"));
        assertEquals(1, transacoes.findByTipoAndStatus("ESTORNO", "CONCLUIDA").size());
        assertEquals("estornado", pedidos.findById("p1").orElseThrow().getPaid());
        assertThrows(ResponseStatusException.class, () -> svc.devolvidaNaConferencia(abertas.get(0).getId(), 1L), "não marca duas vezes");
    }
}
