package com.taskgo.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

/* "Gerar outro Pix" (POST /pedidos/{id} com metodo pix): o Pix antigo é cancelado no provedor antes de criar o novo. */
class PagamentoPixRenovacaoTest {
    private final CobrancaRepository cobrancas = mock(CobrancaRepository.class);
    private final PedidoContratadoRepository pedidos = mock(PedidoContratadoRepository.class);
    private final ProvedorPix pix = mock(ProvedorPix.class);
    private final PagamentoService svc = new PagamentoService("simulado", new BigDecimal("0.15"), cobrancas, mock(CartaoRepository.class),
            pedidos, mock(UsuarioRepository.class), mock(CarteiraRepository.class), mock(TransacaoFinanceiraRepository.class), pix,
            mock(EventosService.class), mock(PublicacaoPedidoService.class));

    PagamentoPixRenovacaoTest() {
        when(pix.nome()).thenReturn("pix-teste");
        when(pix.criar(anyString(), any(), anyString(), anyInt())).thenReturn(new ProvedorPix.CobrancaPix("copia-nova", "qr-nova"));
        when(cobrancas.save(any())).thenAnswer(i -> i.getArgument(0));
        Pedido p = new Pedido();
        ReflectionTestUtils.setField(p, "id", "p1");
        p.setStatus("aguardando_pagamento");
        p.setClientId("7");
        p.setPrice(100.0);
        when(pedidos.findById("p1")).thenReturn(Optional.of(p));
    }

    /* Pix antigo já vencido, ainda pendente. */
    private static Cobranca pixVencido() {
        Cobranca c = new Cobranca("c-antiga", "p1", 7L, "pix", "principal", new BigDecimal("100.00"), "pix-teste", System.currentTimeMillis() - 7_200_000L);
        c.pix("tx-antiga", "copia-antiga", "qr-antigo", System.currentTimeMillis() - 60_000L);
        return c;
    }

    @Test
    void cancelaOPixVencidoECriaUmSoNovo() {
        Cobranca antiga = pixVencido();
        when(cobrancas.findByPedidoIdOrderByCriadaEmAsc("p1")).thenReturn(List.of(antiga));
        when(pix.cancelar("tx-antiga")).thenReturn(true);

        PagamentoService.CobrancaDto nova = svc.pagar("p1", 7L, "pix", null, null);

        assertEquals("cancelada", antiga.getStatus());
        assertEquals("Substituída por outro pagamento.", antiga.getMotivo());
        assertEquals("pendente", nova.status());
        assertEquals("pix", nova.metodo());
        assertEquals("copia-nova", nova.pixCopiaECola());
        assertNotEquals("c-antiga", nova.id());
        verify(pix, times(1)).criar(anyString(), any(), anyString(), anyInt());
        verify(cobrancas, times(1)).save(any());
    }

    @Test
    void naoCriaOutroPixEnquantoOAntigoNaoForCancelado() {
        Cobranca antiga = pixVencido();
        when(cobrancas.findByPedidoIdOrderByCriadaEmAsc("p1")).thenReturn(List.of(antiga));
        when(pix.cancelar("tx-antiga")).thenReturn(false);
        when(pix.status("tx-antiga")).thenReturn("PENDENTE");

        ResponseStatusException e = assertThrows(ResponseStatusException.class, () -> svc.pagar("p1", 7L, "pix", null, null));

        assertEquals(HttpStatus.CONFLICT, e.getStatusCode());
        assertTrue(e.getReason().startsWith("O pagamento anterior ainda está em conferência"));
        assertEquals("pendente", antiga.getStatus());
        verify(pix, never()).criar(anyString(), any(), anyString(), anyInt());
        verify(cobrancas, never()).save(any());
    }

    @Test
    void pixPagoAgoraViraPagamentoESemNovoPix() {
        Cobranca antiga = pixVencido();
        when(cobrancas.findByPedidoIdOrderByCriadaEmAsc("p1")).thenReturn(List.of(antiga));
        when(pix.cancelar("tx-antiga")).thenReturn(false);
        when(pix.status("tx-antiga")).thenReturn("PAGA");
        when(cobrancas.marcarPaga(eq("c-antiga"), eq(Cobranca.PENDENTE), eq(Cobranca.PAGA), anyLong())).thenReturn(1);

        PagamentoService.CobrancaDto dto = svc.pagar("p1", 7L, "pix", null, null);

        assertEquals("c-antiga", dto.id());
        assertEquals("paga", dto.status());
        verify(pix, never()).criar(anyString(), any(), anyString(), anyInt());
        verify(cobrancas, times(1)).save(antiga);
    }
}
