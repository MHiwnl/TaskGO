package com.taskgo.api;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;
import org.junit.jupiter.api.Test;

class RankingProfissionaisTest {
    static final double DELTA = 1e-9;
    static RankingProfissionais.Metricas m(long concluidos, long cancelamentos, Double minutos, long avaliacoes, Double media) {
        return new RankingProfissionais.Metricas(concluidos, cancelamentos, minutos, RankingProfissionais.nota(media == null ? 0 : media, avaliacoes), avaliacoes, media);
    }

    /* Sem histórico, a nota e a conclusão começam no prior (0.9) e a resposta em 0.5. */
    @Test void novoComecaNoPrior() {
        assertEquals(0.9, RankingProfissionais.nota(0, 0), DELTA);
        assertEquals(0.9, RankingProfissionais.conclusao(0, 0), DELTA);
        assertEquals(0.5, RankingProfissionais.resposta(null), DELTA);
    }

    @Test void quemCancelaFicaAbaixo() {
        assertTrue(RankingProfissionais.pontuacaoLista(m(5, 0, null, 0, null)) > RankingProfissionais.pontuacaoLista(m(5, 5, null, 0, null)));
        assertTrue(RankingProfissionais.pontuacaoOnda(5, 40, m(5, 0, null, 0, null)) > RankingProfissionais.pontuacaoOnda(5, 40, m(5, 5, null, 0, null)));
    }

    @Test void avaliacoesBoasSobemERuinsDescem() {
        assertTrue(RankingProfissionais.nota(5, 10) > RankingProfissionais.nota(5, 0));
        assertTrue(RankingProfissionais.nota(2, 10) < RankingProfissionais.nota(5, 0));
    }

    @Test void respostaLentaDesceEViraTetoDe24h() {
        assertTrue(RankingProfissionais.resposta(10.0) > RankingProfissionais.resposta(120.0));
        assertEquals(0.5, RankingProfissionais.resposta(30.0), DELTA);
        assertEquals(RankingProfissionais.resposta(1440.0), RankingProfissionais.resposta(5000.0), DELTA);
    }

    @Test void distanciaZeraNoRaio() {
        assertEquals(1, RankingProfissionais.distancia(0, 40), DELTA);
        assertEquals(0.5, RankingProfissionais.distancia(20, 40), DELTA);
        assertEquals(0, RankingProfissionais.distancia(50, 40), DELTA);
    }

    /* Consultas agrupadas viram uma entrada por profissional, com minutos e cancelamentos do banco. */
    @Test void metricasVemDasConsultasAgrupadas() {
        PedidoContratadoRepository pedidos = mock(PedidoContratadoRepository.class);
        HistoricoContaRepository historico = mock(HistoricoContaRepository.class);
        Usuario u = new Usuario(); u.setId(7L); u.setEmail("Ana@Privado.test");
        when(pedidos.metricasDe(eq(List.of("ana@privado.test")), anyLong())).thenReturn(List.<Object[]>of(new Object[]{"ana@privado.test", 2L, 60_000d}));
        when(pedidos.resumoAvaliacoesDe(List.of("ana@privado.test"))).thenReturn(List.<Object[]>of(new Object[]{"ana@privado.test", 4.0d, 2L}));
        when(historico.cancelamentosDe(anyCollection(), anyLong())).thenReturn(List.<Object[]>of(new Object[]{7L, 1L}));
        RankingProfissionais.Metricas r = new RankingProfissionais(pedidos, historico).metricas(List.of(u)).get(7L);
        assertEquals(2L, r.concluidos());
        assertEquals(1L, r.cancelamentos());
        assertEquals(1.0, r.mediaRespostaMin().doubleValue(), DELTA);
        assertEquals(2L, r.avaliacoes());
        assertEquals(4.0, r.media().doubleValue(), DELTA);
    }
}
