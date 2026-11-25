package com.taskgo.api;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class PlanoDesistenciaTest {
    static final long DIA = PlanoService.DIA, AGORA = System.currentTimeMillis();
    /* Contratação em 10/03/2027 (12h, Recife): o 7º dia termina às 23:59:59 de 17/03. */
    static final long CONTRATA = Instant.parse("2027-03-10T15:00:00Z").toEpochMilli();
    static final long ANTES_PRAZO = Instant.parse("2027-03-18T02:59:59Z").toEpochMilli();
    static final long DEPOIS_PRAZO = Instant.parse("2027-03-18T03:00:00Z").toEpochMilli();
    static final BigDecimal VALOR = new BigDecimal("29.90");

    AssinaturaRepository assinaturas;
    UsuarioRepository usuarios;
    PagamentoService pagamentos;
    CobrancaRepository cobrancas;
    PlanoService svc;

    @BeforeEach void setUp() {
        assinaturas = mock(AssinaturaRepository.class);
        usuarios = mock(UsuarioRepository.class);
        pagamentos = mock(PagamentoService.class);
        cobrancas = mock(CobrancaRepository.class);
        svc = new PlanoService(assinaturas, usuarios, pagamentos, cobrancas, mock(CartaoRepository.class));
        when(usuarios.findById(1L)).thenReturn(Optional.of(prof(1L)));
        when(usuarios.findById(2L)).thenReturn(Optional.of(prof(2L)));
    }

    static Usuario prof(long id) { Usuario u = new Usuario(); u.setId(id); u.setTipo("profissional"); return u; }

    /* Assinatura contratada em `contratadaEm` e paga até `pagoAte` (status ativa). */
    static Assinatura assinatura(long usuarioId, long contratadaEm, long pagoAte) {
        Assinatura a = new Assinatura("a-" + usuarioId, usuarioId, Assinatura.PIX, null, PlanoService.PRECO, contratadaEm - DIA);
        a.contratou(contratadaEm);
        a.pagoAte(pagoAte);
        return a;
    }
    static Cobranca paga(Assinatura a, long criadaEm, long pagaEm) {
        Cobranca c = new Cobranca("c-" + criadaEm, "plano:" + a.getId(), a.getUsuarioId(), "pix", "plano", PlanoService.PRECO, "simulado", criadaEm);
        c.pagar(pagaEm);
        return c;
    }
    void cobrancasDo(Assinatura a, Cobranca... lista) {
        when(cobrancas.findByPedidoIdOrderByCriadaEmAsc("plano:" + a.getId())).thenReturn(List.of(lista));
    }
    static ResponseStatusException erro(Executable e, HttpStatus s) {
        ResponseStatusException r = assertThrows(ResponseStatusException.class, e);
        assertEquals(s, r.getStatusCode());
        return r;
    }

    @Test void prazoAceita2359DoSetimoDiaERecusa0000DoOitavo() {
        long prazo = PlanoService.prazoDesistencia(CONTRATA);
        assertTrue(ANTES_PRAZO < prazo, "23:59:59 do 7º dia ainda vale");
        assertFalse(DEPOIS_PRAZO < prazo, "00:00 do 8º dia já não vale");
    }

    @Test void desistirNoPrazoDevolveIntegralECancela() {
        Assinatura a = assinatura(1L, CONTRATA, CONTRATA + 30 * DIA);
        Cobranca c = paga(a, CONTRATA, CONTRATA);
        when(assinaturas.findByUsuarioId(1L)).thenReturn(Optional.of(a));
        cobrancasDo(a, c);

        svc.desistir(1L, ANTES_PRAZO);

        verify(pagamentos, times(1)).devolverCobranca(c, VALOR);
        assertEquals(Assinatura.CANCELADA, a.getStatus());
        assertFalse(a.vale(ANTES_PRAZO), "os benefícios acabam na hora");
        verify(assinaturas).save(a);
    }

    @Test void desistirNoOitavoDiaRecusaSemDevolver() {
        Assinatura a = assinatura(1L, CONTRATA, CONTRATA + 30 * DIA);
        when(assinaturas.findByUsuarioId(1L)).thenReturn(Optional.of(a));

        erro(() -> svc.desistir(1L, DEPOIS_PRAZO), HttpStatus.BAD_REQUEST);

        verify(pagamentos, never()).devolverCobranca(any(), any());
        verify(assinaturas, never()).save(any());
    }

    @Test void segundaDesistenciaRecusa() {
        Assinatura a = assinatura(1L, CONTRATA, CONTRATA + 30 * DIA);
        a.desistir(CONTRATA + DIA);
        when(assinaturas.findByUsuarioId(1L)).thenReturn(Optional.of(a));

        /* Regressão (revisor): respondia 400 "Não há Plano Pro contratado", que é falso. */
        ResponseStatusException e = erro(() -> svc.desistir(1L, ANTES_PRAZO), HttpStatus.CONFLICT);
        assertTrue(e.getReason().contains("já desistiu"));

        verify(pagamentos, never()).devolverCobranca(any(), any());
    }

    @Test void saveConcorrenteDa409SemDevolver() {
        Assinatura a = assinatura(1L, CONTRATA, CONTRATA + 30 * DIA);
        when(assinaturas.findByUsuarioId(1L)).thenReturn(Optional.of(a));
        when(assinaturas.save(any())).thenThrow(new OptimisticLockingFailureException("versão antiga"));

        erro(() -> svc.desistir(1L, ANTES_PRAZO), HttpStatus.CONFLICT);

        verify(pagamentos, never()).devolverCobranca(any(), any());
    }

    @Test void semPlanoDa404() {
        when(assinaturas.findByUsuarioId(1L)).thenReturn(Optional.empty());
        erro(() -> svc.desistir(1L, ANTES_PRAZO), HttpStatus.NOT_FOUND);
    }

    @Test void desistirConsultaSoAContaDaSessao() {
        when(assinaturas.findByUsuarioId(2L)).thenReturn(Optional.empty());

        erro(() -> svc.desistir(2L, ANTES_PRAZO), HttpStatus.NOT_FOUND);

        verify(assinaturas).findByUsuarioId(2L);
        verify(assinaturas, never()).findByUsuarioId(1L);
    }

    @Test void recontratacaoDepoisDeFicarSemPlanoPodeDesistir() {
        Assinatura a = new Assinatura("a-1", 1L, Assinatura.PIX, null, PlanoService.PRECO, CONTRATA - 400 * DIA);
        a.desistir(CONTRATA - 100 * DIA);
        a.contratou(CONTRATA);
        a.pagoAte(CONTRATA + 30 * DIA);
        Cobranca c = paga(a, CONTRATA, CONTRATA);
        when(assinaturas.findByUsuarioId(1L)).thenReturn(Optional.of(a));
        cobrancasDo(a, c);

        svc.desistir(1L, ANTES_PRAZO);

        verify(pagamentos, times(1)).devolverCobranca(c, VALOR);
    }

    @Test void cobrancaPagaAntesDaContratacaoNaoEDevolvida() {
        Assinatura a = assinatura(1L, CONTRATA, CONTRATA + 30 * DIA);
        Cobranca antiga = paga(a, CONTRATA - 40 * DIA, CONTRATA - 39 * DIA);
        when(assinaturas.findByUsuarioId(1L)).thenReturn(Optional.of(a));
        cobrancasDo(a, antiga);

        svc.desistir(1L, ANTES_PRAZO);

        verify(pagamentos, never()).devolverCobranca(eq(antiga), any());
    }

    @Test void primeiroPagamentoDefineInicioDaContratacao() {
        Assinatura a = new Assinatura("a-1", 1L, Assinatura.PIX, null, PlanoService.PRECO, AGORA - DIA);
        Cobranca c = paga(a, AGORA - DIA, AGORA);
        when(assinaturas.findById(a.getId())).thenReturn(Optional.of(a));

        svc.cobrancaPaga(c);

        assertEquals(AGORA, a.getContratadaEm());
        assertTrue(a.vale(AGORA));
    }

    @Test void renovacaoNaoMudaInicioDaContratacao() {
        Assinatura a = assinatura(1L, AGORA - 60 * DIA, AGORA + 10 * DIA);
        Cobranca c = paga(a, AGORA - DIA, AGORA);
        when(assinaturas.findById(a.getId())).thenReturn(Optional.of(a));

        svc.cobrancaPaga(c);

        assertEquals(AGORA - 60 * DIA, a.getContratadaEm());
        assertTrue(a.getPagoAte() > AGORA + 10 * DIA, "a renovação estende o mês");
    }

    @Test void pagamentoAtrasadoAposDesistenciaNaoEstendeEDevolve() {
        Assinatura a = assinatura(1L, AGORA - 2 * DIA, AGORA + 30 * DIA);
        a.desistir(AGORA - DIA);
        long ateAntes = a.getPagoAte();
        Cobranca atrasada = paga(a, AGORA - 2 * DIA, AGORA);
        when(assinaturas.findById(a.getId())).thenReturn(Optional.of(a));

        svc.cobrancaPaga(atrasada);

        assertEquals(ateAntes, a.getPagoAte(), "não estende o plano desistido");
        verify(pagamentos).devolverCobranca(atrasada, VALOR);
        verify(assinaturas, never()).save(any());
    }

    @Test void estornoAdiadoGravaDesistenciaERepeteNoCuidar() {
        Assinatura a = assinatura(1L, CONTRATA, CONTRATA + 30 * DIA);
        Cobranca c = paga(a, CONTRATA, CONTRATA);
        when(assinaturas.findByUsuarioId(1L)).thenReturn(Optional.of(a));
        cobrancasDo(a, c);
        doThrow(new PagamentoService.EstornoAdiado("banco fora")).when(pagamentos).devolverCobranca(any(), any());

        Map<String, Object> r = svc.desistir(1L, ANTES_PRAZO);

        assertTrue(r.get("aviso").toString().startsWith("Devolução em andamento"));
        assertEquals(Assinatura.CANCELADA, a.getStatus(), "a desistência fica gravada mesmo com a devolução adiada");
        verify(pagamentos, times(1)).devolverCobranca(c, VALOR);

        svc.cuidar(a, ANTES_PRAZO + 5 * 60_000L);
        verify(pagamentos, times(1)).devolverCobranca(c, VALOR);

        svc.cuidar(a, ANTES_PRAZO + 11 * 60_000L);
        verify(pagamentos, times(2)).devolverCobranca(c, VALOR);
    }
    /* Regressão (revisor): a tela mostrava o dia seguinte ao último aceito ("até 08/02" com prazo até 07/02 23:59:59). */
    @Test void telaMostraOUltimoDiaAceito() {
        Assinatura a = assinatura(1L, AGORA, AGORA + 30 * DIA);
        when(assinaturas.findByUsuarioId(1L)).thenReturn(Optional.of(a));
        cobrancasDo(a);
        long ate = ((Number) svc.situacao(prof(1L)).get("desistirAte")).longValue();
        assertEquals(PlanoService.prazoDesistencia(AGORA) - 1, ate);
        java.time.ZoneId recife = java.time.ZoneId.of("America/Recife");
        assertEquals(Instant.ofEpochMilli(AGORA).atZone(recife).toLocalDate().plusDays(7), Instant.ofEpochMilli(ate).atZone(recife).toLocalDate());
    }

    /* Regressão (revisor): erro inesperado no estorno virava 500 com o plano já cancelado e sem aviso. */
    @Test void falhaInesperadaNoEstornoFicaEmAndamento() {
        Assinatura a = assinatura(1L, CONTRATA, CONTRATA + 30 * DIA);
        Cobranca c = paga(a, CONTRATA, CONTRATA);
        when(assinaturas.findByUsuarioId(1L)).thenReturn(Optional.of(a));
        cobrancasDo(a, c);
        doThrow(new IllegalStateException("falha ao gravar")).when(pagamentos).devolverCobranca(any(), any());

        Map<String, Object> r = svc.desistir(1L, ANTES_PRAZO);

        assertTrue(r.get("aviso").toString().startsWith("Devolução em andamento"));
        assertEquals(Assinatura.CANCELADA, a.getStatus());
    }

    /* Regressão (revisor): Pix que o provedor não deixou cancelar continuava aparecendo para pagar. */
    @Test void pixPendenteAnteriorADesistenciaNaoApareceParaPagar() {
        Assinatura a = assinatura(1L, AGORA - DIA, AGORA + 29 * DIA);
        a.desistir(AGORA - 1000);
        Cobranca pendente = new Cobranca("c-pend", "plano:" + a.getId(), 1L, "pix", "plano", PlanoService.PRECO, "simulado", AGORA - 2000);
        when(assinaturas.findByUsuarioId(1L)).thenReturn(Optional.of(a));
        cobrancasDo(a, pendente);

        assertNull(svc.situacao(prof(1L)).get("cobranca"));
        verify(pagamentos, never()).dto(any());
    }
}
