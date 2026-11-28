package com.taskgo.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import java.util.concurrent.atomic.AtomicBoolean;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionSynchronizationUtils;

/* Devolução no Asaas e no saldo: reserva antes, confirma depois, conciliação quando a resposta se perdeu. */
class PagamentoEstornoTest {
    private final CobrancaRepository cobrancas = mock(CobrancaRepository.class);
    private final PedidoContratadoRepository pedidos = mock(PedidoContratadoRepository.class);
    private final TransacaoFinanceiraRepository transacoes = mock(TransacaoFinanceiraRepository.class);
    private final CarteiraRepository carteiras = mock(CarteiraRepository.class);
    private final UsuarioRepository usuarios = mock(UsuarioRepository.class);
    private final AsaasCliente asaas = mock(AsaasCliente.class);
    private final NotificacaoService notificacoes = mock(NotificacaoService.class);
    private final ProvedorPix pix = mock(ProvedorPix.class);
    private final ObjectMapper json = new ObjectMapper();
    private final PagamentoService svc = new PagamentoService("asaas", new BigDecimal("0.15"), cobrancas, mock(CartaoRepository.class),
            pedidos, usuarios, carteiras, transacoes, pix, mock(EventosService.class), mock(PublicacaoPedidoService.class));

    PagamentoEstornoTest() {
        svc.setAsaas(asaas);
        svc.setNotificacoes(notificacoes);
    }

    private static Cobranca asaasPaga(String id, String valor) {
        Cobranca c = new Cobranca(id, "p1", 7L, "cartao", "principal", new BigDecimal(valor), "asaas", 0);
        c.externa("asaas-" + id, null);
        c.pagar(1);
        return c;
    }

    private static Cobranca simuladaPaga(String id, String valor) {
        Cobranca c = new Cobranca(id, "p1", 7L, "cartao", "principal", new BigDecimal(valor), "cartao-simulado", 0);
        c.pagar(1);
        return c;
    }

    private static Pedido pedido(String id, String status, String paid) {
        Pedido p = new Pedido();
        ReflectionTestUtils.setField(p, "id", id);
        p.setStatus(status);
        p.setPaid(paid);
        p.setClientId("7");
        return p;
    }

    /* Como ficaria a cobrança no banco depois da reserva (a reserva é feita pelo SQL, que o mock não executa). */
    private static void reservada(Cobranca c, long desde) {
        ReflectionTestUtils.setField(c, "valorEstornando", c.getValor());
        ReflectionTestUtils.setField(c, "estornandoDesde", desde);
    }

    private JsonNode devolucoes(String descricao, String status, String valor) throws Exception {
        return json.readTree("{\"data\":[{\"description\":\"" + descricao + "\",\"status\":\"" + status + "\",\"value\":" + valor + "}],\"hasMore\":false}");
    }

    private JsonNode semDevolucoes() throws Exception { return json.readTree("{\"data\":[],\"hasMore\":false}"); }

    /* Motivo mandado ao Asaas: "Devolução TaskGO <id>:<desde>". Devolve o "desde" para a conciliação achar a mesma marca. */
    private long desdeDoPedidoAoAsaas(ArgumentCaptor<String> motivo) {
        verify(asaas).estornar(eq("asaas-c1"), any(), motivo.capture());
        return Long.parseLong(motivo.getValue().substring(motivo.getValue().lastIndexOf(':') + 1));
    }

    private void conciliacaoVeEssaCobranca(Cobranca c) {
        when(cobrancas.findByValorEstornandoIsNotNullAndEstornandoDesdeLessThan(anyLong())).thenReturn(List.of(c));
        when(cobrancas.findById("c1")).thenReturn(Optional.of(c));
    }

    @Test void asaasAceitouEDepoisFalhaAGravacaoNaoDevolveDeNovo() throws Exception {
        Cobranca c = asaasPaga("c1", "100.00");
        when(cobrancas.reservarEstorno(eq("c1"), any(), anyLong(), anyString())).thenReturn(1, 0);
        when(cobrancas.concluirEstorno(eq("c1"), any(), anyLong(), anyString())).thenReturn(1);
        doThrow(new IllegalStateException("falha ao gravar")).doReturn(null).when(transacoes).save(any());

        assertThrows(IllegalStateException.class, () -> svc.devolverCobranca(c, new BigDecimal("100.00")));

        ArgumentCaptor<String> motivo = ArgumentCaptor.forClass(String.class);
        long desde = desdeDoPedidoAoAsaas(motivo);
        reservada(c, desde);
        /* Segunda devolução com a reserva ainda aberta (ex.: a pendência tentando de novo): não pode pedir ao Asaas. */
        assertThrows(PagamentoService.EstornoAdiado.class, () -> svc.devolverCobranca(c, new BigDecimal("100.00")));
        verify(asaas, times(1)).estornar(any(), any(), any());
        conciliacaoVeEssaCobranca(c);
        when(asaas.listarEstornos("asaas-c1")).thenReturn(devolucoes(motivo.getValue(), "DONE", "100.00"));

        svc.repetirDevolucoes();

        verify(asaas, times(1)).estornar(any(), any(), any());
        verify(cobrancas, times(2)).concluirEstorno(eq("c1"), eq(new BigDecimal("100.00")), anyLong(), eq(Cobranca.ESTORNADA));
    }

    @Test void respostaPerdidaComDevolucaoAceitaNaoPedeDeNovo() throws Exception {
        Cobranca c = asaasPaga("c1", "100.00");
        when(cobrancas.reservarEstorno(eq("c1"), any(), anyLong(), anyString())).thenReturn(1);
        when(cobrancas.concluirEstorno(eq("c1"), any(), anyLong(), anyString())).thenReturn(1);
        doThrow(new AsaasCliente.Erro(503, "O Asaas não respondeu.")).when(asaas).estornar(any(), any(), any());
        assertThrows(PagamentoService.EstornoAdiado.class, () -> svc.devolverCobranca(c, new BigDecimal("100.00")));

        ArgumentCaptor<String> motivo = ArgumentCaptor.forClass(String.class);
        reservada(c, desdeDoPedidoAoAsaas(motivo));
        conciliacaoVeEssaCobranca(c);
        when(asaas.listarEstornos("asaas-c1")).thenReturn(devolucoes(motivo.getValue(), "DONE", "100.00"));

        svc.repetirDevolucoes();

        verify(asaas, times(1)).estornar(any(), any(), any());
        verify(cobrancas, never()).liberarEstorno(anyString(), anyLong());
        verify(cobrancas).concluirEstorno(eq("c1"), eq(new BigDecimal("100.00")), anyLong(), eq(Cobranca.ESTORNADA));
    }

    @Test void devolucaoCanceladaNoAsaasSoltaAReservaEASoProximaRodadaPede() throws Exception {
        Cobranca c = asaasPaga("c1", "100.00");
        when(cobrancas.reservarEstorno(eq("c1"), any(), anyLong(), anyString())).thenReturn(1);
        when(cobrancas.concluirEstorno(eq("c1"), any(), anyLong(), anyString())).thenReturn(1);
        when(cobrancas.liberarEstorno(eq("c1"), anyLong())).thenReturn(1);
        doThrow(new AsaasCliente.Erro(503, "O Asaas não respondeu.")).when(asaas).estornar(any(), any(), any());
        assertThrows(PagamentoService.EstornoAdiado.class, () -> svc.devolverCobranca(c, new BigDecimal("100.00")));

        ArgumentCaptor<String> motivo = ArgumentCaptor.forClass(String.class);
        long desde = desdeDoPedidoAoAsaas(motivo);
        reservada(c, desde);
        conciliacaoVeEssaCobranca(c);
        when(asaas.listarEstornos("asaas-c1")).thenReturn(devolucoes(motivo.getValue(), "CANCELLED", "100.00"));
        svc.repetirDevolucoes();
        verify(cobrancas).liberarEstorno("c1", desde);

        org.mockito.Mockito.reset(asaas);
        svc.devolverCobranca(c, new BigDecimal("100.00"));
        verify(asaas, times(1)).estornar(eq("asaas-c1"), any(), anyString());
    }

    /* Regressão: a conciliação procurava só a marca na descrição. Se o Asaas devolvesse o texto diferente,
       "não achei" soltava a reserva e a rodada seguinte devolvia de novo. Agora confere o total devolvido lá. */
    @Test void devolucaoFeitaSemANossaMarcaNaoSoltaAReservaNemDevolveDeNovo() throws Exception {
        Cobranca c = asaasPaga("c1", "100.00");
        when(cobrancas.reservarEstorno(eq("c1"), any(), anyLong(), anyString())).thenReturn(1);
        doThrow(new AsaasCliente.Erro(503, "O Asaas não respondeu.")).when(asaas).estornar(any(), any(), any());
        assertThrows(PagamentoService.EstornoAdiado.class, () -> svc.devolverCobranca(c, new BigDecimal("100.00")));

        ArgumentCaptor<String> motivo = ArgumentCaptor.forClass(String.class);
        long desde = desdeDoPedidoAoAsaas(motivo);
        reservada(c, desde);
        conciliacaoVeEssaCobranca(c);
        when(asaas.listarEstornos("asaas-c1")).thenReturn(devolucoes("Estorno", "DONE", "100.00"));

        svc.repetirDevolucoes();

        verify(cobrancas, never()).liberarEstorno(anyString(), anyLong());
        verify(cobrancas, never()).concluirEstorno(anyString(), any(), anyLong(), anyString());
        verify(asaas, times(1)).estornar(any(), any(), any());
    }

    @Test void divergenciaDeValorMantemAReservaEAlertaSemPedirDeNovo() throws Exception {
        Cobranca c = asaasPaga("c1", "100.00");
        when(cobrancas.reservarEstorno(eq("c1"), any(), anyLong(), anyString())).thenReturn(1);
        doThrow(new AsaasCliente.Erro(503, "O Asaas não respondeu.")).when(asaas).estornar(any(), any(), any());
        assertThrows(PagamentoService.EstornoAdiado.class, () -> svc.devolverCobranca(c, new BigDecimal("100.00")));

        ArgumentCaptor<String> motivo = ArgumentCaptor.forClass(String.class);
        long desde = desdeDoPedidoAoAsaas(motivo);
        reservada(c, desde);
        conciliacaoVeEssaCobranca(c);
        Usuario financeiro = mock(Usuario.class);
        when(financeiro.getId()).thenReturn(9L);
        when(usuarios.findByTipoIgnoreCaseOrderByNomeAsc("financeiro")).thenReturn(List.of(financeiro));
        when(asaas.listarEstornos("asaas-c1")).thenReturn(devolucoes(motivo.getValue(), "DONE", "90.00"));

        svc.repetirDevolucoes();
        svc.repetirDevolucoes();

        verify(asaas, times(1)).estornar(any(), any(), any());
        verify(cobrancas, never()).liberarEstorno(anyString(), anyLong());
        verify(cobrancas, never()).concluirEstorno(anyString(), any(), anyLong(), anyString());
        String chave = "estorno-conferir:c1:" + desde + ":9";
        verify(notificacoes, times(2)).criar(eq(9L), any(), anyString(), anyString(), anyString(), eq(chave));
    }

    @Test void recusaCertaDoAsaasSoltaAReservaEAProximaTentativaPede() throws Exception {
        Cobranca c = asaasPaga("c1", "100.00");
        when(cobrancas.reservarEstorno(eq("c1"), any(), anyLong(), anyString())).thenReturn(1);
        when(cobrancas.concluirEstorno(eq("c1"), any(), anyLong(), anyString())).thenReturn(1);
        when(cobrancas.liberarEstorno(eq("c1"), anyLong())).thenReturn(1);
        when(asaas.estornar(any(), any(), any())).thenThrow(new AsaasCliente.Erro(400, "Valor inválido")).thenReturn(null);

        assertThrows(PagamentoService.EstornoAdiado.class, () -> svc.devolverCobranca(c, new BigDecimal("100.00")));
        verify(cobrancas).liberarEstorno(eq("c1"), anyLong());

        svc.devolverCobranca(c, new BigDecimal("100.00"));
        verify(asaas, times(2)).estornar(any(), any(), any());
        verify(cobrancas, times(1)).concluirEstorno(eq("c1"), any(), anyLong(), anyString());
    }

    @Test void pedidoComDuasDevolucoesUmaFalhaAOutraFicaEstornada() {
        Pedido pA = pedido("pA", "cancelado", "estornando");
        Pedido pB = pedido("pB", "cancelado", "estornando");
        Cobranca cA = simuladaPaga("cA", "100.00");
        Cobranca cB = simuladaPaga("cB", "50.00");
        when(pedidos.findByPaid("estornando")).thenReturn(List.of(pA, pB));
        when(pedidos.findById("pA")).thenReturn(Optional.of(pA));
        when(pedidos.findById("pB")).thenReturn(Optional.of(pB));
        when(cobrancas.findByPedidoIdOrderByCriadaEmAsc("pA")).thenReturn(List.of(cA));
        when(cobrancas.findByPedidoIdOrderByCriadaEmAsc("pB")).thenReturn(List.of(cB));
        when(cobrancas.reservarEstorno(eq("cA"), any(), anyLong(), anyString())).thenReturn(1);
        when(cobrancas.reservarEstorno(eq("cB"), any(), anyLong(), anyString())).thenThrow(new RuntimeException("banco caiu"));
        when(cobrancas.concluirEstorno(eq("cA"), any(), anyLong(), anyString())).thenReturn(1);

        svc.repetirDevolucoes();

        assertEquals("estornado", pA.getPaid());
        assertEquals("estornando", pB.getPaid());
        verify(transacoes, times(1)).save(any());
    }

    @Test void pedidoEstornandoComCobrancasJaEstornadasFechaOPedido() {
        Pedido p = pedido("p1", "cancelado", "estornando");
        Cobranca c = simuladaPaga("c1", "100.00");
        c.estornar(new BigDecimal("100.00"), 2);
        when(pedidos.findByPaid("estornando")).thenReturn(List.of(p));
        when(pedidos.findById("p1")).thenReturn(Optional.of(p));
        when(cobrancas.findByPedidoIdOrderByCriadaEmAsc("p1")).thenReturn(List.of(c));

        svc.repetirDevolucoes();

        assertEquals("estornado", p.getPaid());
        verify(pedidos).save(p);
    }

    @Test void carteiraCreditaUmaVezMesmoComDuasDevolucoes() {
        Cobranca c = new Cobranca("c1", "p1", 7L, "carteira", "principal", new BigDecimal("100.00"), "carteira", 0);
        c.pagar(1);
        Carteira carteira = new Carteira(7L);
        when(carteiras.findByUsuarioIdForUpdate(7L)).thenReturn(Optional.of(carteira));
        when(cobrancas.reservarEstorno(eq("c1"), any(), anyLong(), anyString())).thenReturn(1, 0);
        when(cobrancas.concluirEstorno(eq("c1"), any(), anyLong(), anyString())).thenReturn(1);

        svc.devolverCobranca(c, new BigDecimal("100.00"));
        assertThrows(PagamentoService.EstornoAdiado.class, () -> svc.devolverCobranca(c, new BigDecimal("100.00")));

        assertEquals(0, new BigDecimal("100").compareTo(carteira.getSaldoDisponivel()));
    }

    /* Regressão: a chave da devolução tinha ido para a referência, e o extrato do cliente (carteira-direta.js) passava a
       mostrar "estorno:..." no lugar do pedido. A chave fica no id; a referência continua o pedido. */
    @Test void registroDaDevolucaoMostraOPedidoEGuardaAChaveNoId() {
        Cobranca c = new Cobranca("c1", "p1", 7L, "carteira", "principal", new BigDecimal("100.00"), "carteira", 0);
        c.pagar(1);
        when(carteiras.findByUsuarioIdForUpdate(7L)).thenReturn(Optional.of(new Carteira(7L)));
        when(cobrancas.reservarEstorno(eq("c1"), any(), anyLong(), anyString())).thenReturn(1);
        when(cobrancas.concluirEstorno(eq("c1"), any(), anyLong(), anyString())).thenReturn(1);

        svc.devolverCobranca(c, new BigDecimal("100.00"));

        ArgumentCaptor<TransacaoFinanceira> t = ArgumentCaptor.forClass(TransacaoFinanceira.class);
        verify(transacoes, org.mockito.Mockito.atLeastOnce()).save(t.capture());
        TransacaoFinanceira estorno = t.getAllValues().stream().filter(x -> "ESTORNO".equals(x.getTipo())).findFirst().orElseThrow();
        assertEquals("p1", estorno.getReferencia());
        org.junit.jupiter.api.Assertions.assertTrue(estorno.getId().startsWith("estorno:c1:"));
    }

    @Test void pixQueChegaEmPedidoCanceladoNaoEstornaNaHoraSoNaRodada() {
        Cobranca c = new Cobranca("c1", "p1", 7L, "pix", "principal", new BigDecimal("100.00"), "pix", 0);
        c.pix("txid1", "copia", "qr", Long.MAX_VALUE);
        Pedido p = pedido("p1", "cancelado", null);
        when(cobrancas.findByIdAndClienteId("c1", 7L)).thenReturn(Optional.of(c));
        when(pix.status("txid1")).thenReturn("PAGA");
        when(cobrancas.marcarPaga(eq("c1"), anyString(), anyString(), anyLong())).thenReturn(1);
        when(pedidos.findById("p1")).thenReturn(Optional.of(p));
        when(cobrancas.findByPedidoIdOrderByCriadaEmAsc("p1")).thenReturn(List.of(c));
        when(cobrancas.reservarEstorno(eq("c1"), any(), anyLong(), anyString())).thenReturn(1);
        when(cobrancas.concluirEstorno(eq("c1"), any(), anyLong(), anyString())).thenReturn(1);

        svc.consultar("c1", 7L);
        assertEquals("estornando", p.getPaid());
        verify(cobrancas, never()).reservarEstorno(anyString(), any(), anyLong(), anyString());

        when(pedidos.findByPaid("estornando")).thenReturn(List.of(p));
        svc.repetirDevolucoes();
        verify(cobrancas, times(1)).reservarEstorno(eq("c1"), any(), anyLong(), anyString());
        assertEquals("estornado", p.getPaid());
    }

    /* Simula a transação de quem chamou (ObraService, DinheiroService...): só a sincronização do Spring, sem banco. */
    private static void abrirTransacao() {
        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);
    }
    /* Commit simulado: roda os afterCommit e fecha a transação. */
    private static void commitSimulado() {
        try { TransactionSynchronizationUtils.invokeAfterCommit(TransactionSynchronizationManager.getSynchronizations()); }
        finally { fecharTransacao(); }
    }
    private static void fecharTransacao() {
        TransactionSynchronizationManager.clear();
        TransactionSynchronizationManager.setActualTransactionActive(false);
    }
    /* Reserva como ficou no banco (o SQL não roda no mock): valor e hora da reserva. */
    private static void reservadaPor(Cobranca c, String parte, long desde) {
        ReflectionTestUtils.setField(c, "valorEstornando", new BigDecimal(parte));
        ReflectionTestUtils.setField(c, "estornandoDesde", desde);
    }

    /* Depois da conciliação confirmar: a reserva sai e a soma entra na cobrança (o que o SQL faria). */
    private static void concluidaNoBanco(Cobranca c, String parte) {
        ReflectionTestUtils.setField(c, "valorEstornando", null);
        ReflectionTestUtils.setField(c, "estornandoDesde", null);
        c.estornar(new BigDecimal(parte), 1L);
    }

    /* Devolução ambígua dentro de uma transação (obra): o Asaas recebe o pedido depois do commit, e a resposta se perde.
       Devolve a referência da pendência. */
    private String ambiguaNaObra(Cobranca c, String parte) {
        when(cobrancas.reservarEstorno(eq("c1"), any(), anyLong(), anyString())).thenReturn(1);
        when(cobrancas.concluirEstorno(eq("c1"), any(), anyLong(), anyString())).thenReturn(1);
        doThrow(new AsaasCliente.Erro(503, "O Asaas não respondeu.")).when(asaas).estornar(any(), any(), any());
        abrirTransacao();
        try {
            PagamentoService.EstornoAdiado adiado = assertThrows(PagamentoService.EstornoAdiado.class, () -> svc.devolverCobranca(c, new BigDecimal(parte)));
            verify(asaas, never()).estornar(any(), any(), any());
            String ref = adiado.referencia(c);
            commitSimulado();
            return ref;
        } finally { fecharTransacao(); }
    }

    /* Regressão (obra): devolução parcial de 30 de 100 com resposta perdida. A pendência espera a reserva, a conciliação confirma
       e a pendência fica resolvida sem novo POST. Antes: a pendência repetia a devolução e o cliente recebia 2 vezes. */
    @Test void devolucaoParcialAmbiguaNaObraNaoRepeteAposConciliacao() throws Exception {
        Cobranca c = asaasPaga("c1", "100.00");
        String ref = ambiguaNaObra(c, "30.00");
        long desde = Long.parseLong(ref.substring(ref.lastIndexOf(':') + 1));
        verify(asaas, times(1)).estornar(eq("asaas-c1"), eq(new BigDecimal("30.00")), anyString());

        reservadaPor(c, "30.00", desde);
        conciliacaoVeEssaCobranca(c);
        /* Reserva aberta: a pendência espera a conciliação, sem POST. */
        assertEquals(ref, svc.retomarDevolucao(ref, new BigDecimal("30.00")));
        when(asaas.listarEstornos("asaas-c1")).thenReturn(devolucoes("Devolução TaskGO c1:" + desde, "DONE", "30.00"));
        svc.repetirDevolucoes();
        concluidaNoBanco(c, "30.00");
        when(transacoes.existsById(ref)).thenReturn(true);

        assertNull(svc.retomarDevolucao(ref, new BigDecimal("30.00")));
        verify(asaas, times(1)).estornar(any(), any(), any());
    }

    /* Regressão (1b): devolução total ambígua, já concluída. A pendência não pode ficar presa para sempre. */
    @Test void devolucaoTotalAmbiguaConcluidaFechaAPendencia() throws Exception {
        Cobranca c = asaasPaga("c1", "100.00");
        String ref = ambiguaNaObra(c, "100.00");
        long desde = Long.parseLong(ref.substring(ref.lastIndexOf(':') + 1));
        reservadaPor(c, "100.00", desde);
        conciliacaoVeEssaCobranca(c);
        when(asaas.listarEstornos("asaas-c1")).thenReturn(devolucoes("Devolução TaskGO c1:" + desde, "DONE", "100.00"));
        svc.repetirDevolucoes();
        concluidaNoBanco(c, "100.00");

        assertNull(svc.retomarDevolucao(ref, new BigDecimal("100.00")));
        verify(asaas, times(1)).estornar(any(), any(), any());
    }

    /* Regressão (3): o Asaas só recebe o pedido depois do commit de quem chamou, nunca com a transação aberta. */
    @Test void devolucaoDentroDeTransacaoSoChamaOAsaasDepoisDoCommit() throws Exception {
        Cobranca c = asaasPaga("c1", "100.00");
        when(cobrancas.reservarEstorno(eq("c1"), any(), anyLong(), anyString())).thenReturn(1);
        when(cobrancas.concluirEstorno(eq("c1"), any(), anyLong(), anyString())).thenReturn(1);
        abrirTransacao();
        try {
            assertThrows(PagamentoService.EstornoAdiado.class, () -> svc.devolverCobranca(c, new BigDecimal("30.00")));
            verify(asaas, never()).estornar(any(), any(), any());
            commitSimulado();
        } finally { fecharTransacao(); }

        verify(asaas, times(1)).estornar(eq("asaas-c1"), eq(new BigDecimal("30.00")), anyString());
        verify(cobrancas).concluirEstorno(eq("c1"), eq(new BigDecimal("30.00")), anyLong(), eq(Cobranca.ESTORNADA));
    }

    /* Regressão (revisão de 25/11): com o executor do Spring, o POST ao Asaas sai em outra thread depois do commit.
       Antes, saía dentro do afterCommit, e a requisição de quem chamou esperava o Asaas segurando a conexão do banco. */
    @Test void devolucaoDepoisDoCommitVaiParaOExecutorSemEsperarOAsaas() throws Exception {
        Cobranca c = asaasPaga("c1", "100.00");
        when(cobrancas.reservarEstorno(eq("c1"), any(), anyLong(), anyString())).thenReturn(1);
        when(cobrancas.concluirEstorno(eq("c1"), any(), anyLong(), anyString())).thenReturn(1);
        List<Runnable> fila = new java.util.ArrayList<>();
        svc.setExecutor(fila::add);
        abrirTransacao();
        try {
            assertThrows(PagamentoService.EstornoAdiado.class, () -> svc.devolverCobranca(c, new BigDecimal("30.00")));
            commitSimulado();
        } finally { fecharTransacao(); }

        verify(asaas, never()).estornar(any(), any(), any());
        assertEquals(1, fila.size());
        fila.get(0).run();
        verify(asaas, times(1)).estornar(eq("asaas-c1"), eq(new BigDecimal("30.00")), anyString());
    }

    /* Regressão (revisão de 25/11): cobrança do Asaas sem identificador não pode virar "devolvida" sem o dinheiro sair.
       Antes, caía no caminho local: gravava ESTORNADA e o registro de estorno, e nada era pedido ao Asaas. */
    @Test void cobrancaDoAsaasSemTxidNaoEConcluidaAquiEAvisaAEquipe() {
        Cobranca c = new Cobranca("c1", "p1", 7L, "cartao", "principal", new BigDecimal("100.00"), "asaas", 0);
        c.pagar(1);
        when(cobrancas.reservarEstorno(eq("c1"), any(), anyLong(), anyString())).thenReturn(1);
        when(cobrancas.concluirEstorno(eq("c1"), any(), anyLong(), anyString())).thenReturn(1);
        Usuario financeiro = mock(Usuario.class);
        when(financeiro.getId()).thenReturn(9L);
        when(usuarios.findByTipoIgnoreCaseOrderByNomeAsc("financeiro")).thenReturn(List.of(financeiro));

        assertThrows(PagamentoService.EstornoAdiado.class, () -> svc.devolverCobranca(c, new BigDecimal("30.00")));

        verify(cobrancas, never()).concluirEstorno(anyString(), any(), anyLong(), anyString());
        verify(transacoes, never()).save(org.mockito.ArgumentMatchers.argThat(t -> t != null && "ESTORNO".equals(t.getTipo())));
        verify(asaas, never()).estornar(any(), any(), any());
        verify(notificacoes).criar(eq(9L), any(), anyString(), anyString(), anyString(), org.mockito.ArgumentMatchers.startsWith("estorno-conferir:c1:"));
    }

    private static Cobranca asaasSemTxid() {
        Cobranca c = new Cobranca("c1", "p1", 7L, "cartao", "principal", new BigDecimal("100.00"), "asaas", 0);
        c.pagar(1);
        return c;
    }

    /* Regressão (pendências de 26/11): sem txid, o pedido cancelado não promete "assim que o banco liberar" (depende da equipe)
       e a cobrança entra uma vez na lista de conferência do Financeiro. */
    @Test void pedidoCanceladoSemTxidAvisaQueAEquipeConfereERegistraAConferencia() {
        Cobranca c = asaasSemTxid();
        Pedido p = pedido("p1", "cancelado", "estornando");
        when(pedidos.findById("p1")).thenReturn(Optional.of(p));
        when(cobrancas.findByPedidoIdOrderByCriadaEmAsc("p1")).thenReturn(List.of(c));

        svc.processarEstorno("p1");

        verify(transacoes).save(org.mockito.ArgumentMatchers.argThat(t -> t != null && PagamentoService.DEVOLUCAO_CONFERIR.equals(t.getTipo()) && t.getId().startsWith("conferir:c1:") && "VERIFICAR".equals(t.getStatus())));
        verify(notificacoes).criar(eq(7L), eq("p1"), anyString(), eq("Devolução em andamento"), org.mockito.ArgumentMatchers.contains("equipe da TaskGO está conferindo"), anyString());
        verify(notificacoes, never()).criar(eq(7L), any(), anyString(), anyString(), org.mockito.ArgumentMatchers.contains("banco liberar"), anyString());
        assertEquals("estornando", p.getPaid());
    }

    /* Regressão (pendências de 26/11): a equipe devolveu pelo painel do Asaas e marca; a devolução é registrada uma vez,
       a conferência fecha e o pedido termina como estornado, com aviso ao cliente. */
    @Test void equipeMarcaDevolvidaNoAsaasFechaConferenciaEPedido() {
        Cobranca c = asaasSemTxid();
        Pedido p = pedido("p1", "cancelado", "estornando");
        TransacaoFinanceira conferencia = new TransacaoFinanceira("conferir:c1", 7L, PagamentoService.DEVOLUCAO_CONFERIR, new BigDecimal("100.00"), "VERIFICAR", "c1", 1L);
        when(transacoes.findById("conferir:c1")).thenReturn(Optional.of(conferencia));
        when(cobrancas.findById("c1")).thenReturn(Optional.of(c));
        when(pedidos.findById("p1")).thenReturn(Optional.of(p));
        when(cobrancas.findByPedidoIdOrderByCriadaEmAsc("p1")).thenReturn(List.of(c));
        when(cobrancas.reservarEstorno(eq("c1"), any(), anyLong(), anyString())).thenReturn(1);
        when(cobrancas.concluirEstorno(eq("c1"), any(), anyLong(), anyString())).thenAnswer(inv -> { c.estornar(inv.getArgument(1), 1L); return 1; });
        TransacaoFinanceira daCobranca = new TransacaoFinanceira("pend1", 7L, "DEVOLUCAO_PENDENTE", new BigDecimal("100.00"), "PENDENTE", "c1", 1L);
        TransacaoFinanceira deOutra = new TransacaoFinanceira("pend2", 8L, "DEVOLUCAO_PENDENTE", new BigDecimal("40.00"), "PENDENTE", "c9", 1L);
        when(transacoes.findByTipoAndStatus("DEVOLUCAO_PENDENTE", "PENDENTE")).thenReturn(List.of(daCobranca, deOutra));

        assertEquals(new BigDecimal("100.00"), svc.devolvidaNaConferencia("conferir:c1", 1L));

        assertEquals("DONE", conferencia.getStatus());
        assertEquals("CONCLUIDA", daCobranca.getStatus(), "a pendência da cobrança fecha: não repete sobre o que sobrou");
        assertEquals("PENDENTE", deOutra.getStatus());
        assertEquals("estornado", p.getPaid());
        verify(transacoes).save(org.mockito.ArgumentMatchers.argThat(t -> t != null && "ESTORNO".equals(t.getTipo())));
        verify(asaas, never()).estornar(any(), any(), any());
        verify(notificacoes).criar(eq(7L), eq("p1"), anyString(), eq("Devolução enviada"), anyString(), anyString());
        assertThrows(org.springframework.web.server.ResponseStatusException.class, () -> svc.devolvidaNaConferencia("conferir:c1", 1L), "não marca duas vezes");
    }

    /* Regressão (revisão de 26/11): a conferência guarda a parte pedida, não o líquido inteiro. Recorrente devolve 200 − 50 da semana
       retida: a equipe devolve 150, e não 200 (o cliente levaria os 50 do profissional). */
    @Test void conferenciaGuardaAParteEAEquipeDevolveSoEla() {
        Cobranca c = new Cobranca("c1", "p1", 7L, "cartao", "principal", new BigDecimal("200.00"), "asaas", 0);
        c.pagar(1);
        assertThrows(PagamentoService.EstornoAdiado.class, () -> svc.devolverCobranca(c, new BigDecimal("150.00")));
        ArgumentCaptor<TransacaoFinanceira> salva = ArgumentCaptor.forClass(TransacaoFinanceira.class);
        verify(transacoes).save(salva.capture());
        assertEquals(new BigDecimal("150.00"), salva.getValue().getValor());

        when(transacoes.findById("conferir:c1")).thenReturn(Optional.of(salva.getValue()));
        when(cobrancas.findById("c1")).thenReturn(Optional.of(c));
        when(cobrancas.reservarEstorno(eq("c1"), any(), anyLong(), anyString())).thenReturn(1);
        when(cobrancas.concluirEstorno(eq("c1"), any(), anyLong(), anyString())).thenReturn(1);
        assertEquals(new BigDecimal("150.00"), svc.devolvidaNaConferencia("conferir:c1", 1L));
        verify(cobrancas).reservarEstorno(eq("c1"), eq(new BigDecimal("150.00")), anyLong(), anyString());
    }

    /* Regressão (revisão de 26/11): a cobrança voltou a ter identificador com o Asaas ligado. A devolução sai sozinha; marcar a
       conferência só a fecha, sem registrar outra devolução (senão a equipe devolveria de novo pelo painel). */
    @Test void conferenciaDeCobrancaQueVoltouAoAsaasFechaSemDevolverDeNovo() {
        Cobranca c = asaasPaga("c1", "100.00");
        TransacaoFinanceira conferencia = new TransacaoFinanceira("conferir:c1", 7L, PagamentoService.DEVOLUCAO_CONFERIR, new BigDecimal("100.00"), "VERIFICAR", "c1", 1L);
        when(transacoes.findById("conferir:c1")).thenReturn(Optional.of(conferencia));
        when(cobrancas.findById("c1")).thenReturn(Optional.of(c));

        assertFalse(svc.conferenciaManual("c1"));
        assertNull(svc.devolvidaNaConferencia("conferir:c1", 1L));
        assertEquals(PagamentoService.CONFERENCIA_AUTOMATICA, conferencia.getStatus());
        verify(cobrancas, never()).reservarEstorno(anyString(), any(), anyLong(), anyString());
        verify(cobrancas, never()).concluirEstorno(anyString(), any(), anyLong(), anyString());
    }

    /* Regressão (revisão de 26/11): Obra e Recorrente usam a mesma pendência; na conferência o texto não promete o banco. */
    @Test void pendenciaDeObraERecorrenteUsaOTextoDaConferencia() {
        Cobranca c = asaasSemTxid();
        svc.pendencia(c, new BigDecimal("30.00"), new PagamentoService.EstornoAdiado("x", null, true), true);
        svc.pendencia(c, new BigDecimal("20.00"), new PagamentoService.EstornoAdiado("y"), false);
        verify(notificacoes).criar(eq(7L), eq("p1"), anyString(), eq("Devolução em andamento"), org.mockito.ArgumentMatchers.contains("equipe da TaskGO está conferindo"), org.mockito.ArgumentMatchers.startsWith("devolucao-pendente:c1:30.00:"));
        verify(notificacoes).criar(eq(7L), eq("p1"), anyString(), eq("Devolução em andamento"), org.mockito.ArgumentMatchers.contains("assim que o banco liberar"), eq("devolucao-pendente:c1:20.00"));
    }

    /* Regressão (segunda revisão de 26/11): com uma conferência ainda aberta, repetir o pedido não abre outra nem avisa de novo;
       uma parte maior atualiza a aberta. Depois de fechada, um pedido novo abre outra conferência (antes, o id fixo travava). */
    @Test void umaConferenciaAbertaPorCobrancaEDepoisDeFechadaAbreOutra() {
        Cobranca c = asaasSemTxid();
        TransacaoFinanceira aberta = new TransacaoFinanceira("conferir:c1:1", 7L, PagamentoService.DEVOLUCAO_CONFERIR, new BigDecimal("50.00"), "VERIFICAR", "c1", 1L);
        when(transacoes.findByTipoAndStatus(PagamentoService.DEVOLUCAO_CONFERIR, "VERIFICAR")).thenReturn(List.of(aberta));
        assertThrows(PagamentoService.EstornoAdiado.class, () -> svc.devolverCobranca(c, new BigDecimal("50.00")));
        verify(transacoes, never()).save(any());
        assertThrows(PagamentoService.EstornoAdiado.class, () -> svc.devolverCobranca(c, new BigDecimal("80.00")));
        verify(transacoes).save(org.mockito.ArgumentMatchers.argThat(t -> "conferir:c1:1".equals(t.getId()) && new BigDecimal("80.00").equals(t.getValor())));
        verify(notificacoes, never()).criar(any(), any(), anyString(), anyString(), anyString(), org.mockito.ArgumentMatchers.startsWith("estorno-conferir:"));

        when(transacoes.findByTipoAndStatus(PagamentoService.DEVOLUCAO_CONFERIR, "VERIFICAR")).thenReturn(List.of());
        assertThrows(PagamentoService.EstornoAdiado.class, () -> svc.devolverCobranca(c, new BigDecimal("30.00")));
        verify(transacoes).save(org.mockito.ArgumentMatchers.argThat(t -> t.getId().startsWith("conferir:c1:") && !"conferir:c1:1".equals(t.getId()) && new BigDecimal("30.00").equals(t.getValor())));
    }

    /* Regressão (terceira revisão de 26/11): recorrente com duas vezes de R$ 50 canceladas na mesma cobrança sem txid. A conferência
       guardava 50 e marcar fechava as duas pendências: o cliente recebia só metade. Agora a equipe devolve a soma, R$ 100. */
    @Test void duasPendenciasNaMesmaCobrancaSomamNaConferencia() {
        Cobranca c = new Cobranca("c1", "p1", 7L, "cartao", "principal", new BigDecimal("200.00"), "asaas", 0);
        c.pagar(1);
        TransacaoFinanceira conferencia = new TransacaoFinanceira("conferir:c1:1", 7L, PagamentoService.DEVOLUCAO_CONFERIR, new BigDecimal("50.00"), "VERIFICAR", "c1", 1L);
        when(transacoes.findById("conferir:c1:1")).thenReturn(Optional.of(conferencia));
        when(cobrancas.findById("c1")).thenReturn(Optional.of(c));
        when(transacoes.findByTipoAndStatus("DEVOLUCAO_PENDENTE", "PENDENTE")).thenReturn(List.of(
                new TransacaoFinanceira("v1", 7L, "DEVOLUCAO_PENDENTE", new BigDecimal("50.00"), "PENDENTE", "c1", 1L),
                new TransacaoFinanceira("v2", 7L, "DEVOLUCAO_PENDENTE", new BigDecimal("50.00"), "PENDENTE", "c1", 2L)));
        when(cobrancas.reservarEstorno(eq("c1"), any(), anyLong(), anyString())).thenReturn(1);
        when(cobrancas.concluirEstorno(eq("c1"), any(), anyLong(), anyString())).thenReturn(1);

        assertEquals(new BigDecimal("100.00"), svc.valorDaConferencia(conferencia));
        assertEquals(new BigDecimal("100.00"), svc.devolvidaNaConferencia("conferir:c1:1", 1L));
        verify(cobrancas).reservarEstorno(eq("c1"), eq(new BigDecimal("100.00")), anyLong(), anyString());
    }

    /* Regressão (terceira revisão de 26/11): duas linhas abertas da mesma cobrança (corrida). Marcar uma fecha a outra, para a equipe
       não devolver duas vezes pelo painel do Asaas. */
    @Test void marcarUmaConferenciaFechaAsIrmasDaMesmaCobranca() {
        Cobranca c = asaasSemTxid();
        TransacaoFinanceira a = new TransacaoFinanceira("conferir:c1:1", 7L, PagamentoService.DEVOLUCAO_CONFERIR, new BigDecimal("100.00"), "VERIFICAR", "c1", 1L);
        TransacaoFinanceira b = new TransacaoFinanceira("conferir:c1:2", 7L, PagamentoService.DEVOLUCAO_CONFERIR, new BigDecimal("100.00"), "VERIFICAR", "c1", 2L);
        TransacaoFinanceira outra = new TransacaoFinanceira("conferir:c9:1", 8L, PagamentoService.DEVOLUCAO_CONFERIR, new BigDecimal("40.00"), "VERIFICAR", "c9", 1L);
        when(transacoes.findById("conferir:c1:1")).thenReturn(Optional.of(a));
        when(cobrancas.findById("c1")).thenReturn(Optional.of(c));
        when(transacoes.findByTipoAndStatus(PagamentoService.DEVOLUCAO_CONFERIR, "VERIFICAR")).thenReturn(List.of(a, b, outra));
        when(cobrancas.reservarEstorno(eq("c1"), any(), anyLong(), anyString())).thenReturn(1);
        when(cobrancas.concluirEstorno(eq("c1"), any(), anyLong(), anyString())).thenReturn(1);

        svc.devolvidaNaConferencia("conferir:c1:1", 1L);

        assertEquals("DONE", a.getStatus());
        assertEquals("DONE", b.getStatus());
        assertEquals("VERIFICAR", outra.getStatus());
    }

    /* Regressão (segunda revisão de 26/11): devolução automática concluída fecha a conferência aberta da cobrança. */
    @Test void devolucaoAutomaticaConcluidaFechaAConferenciaAberta() {
        Cobranca c = asaasPaga("c1", "100.00");
        TransacaoFinanceira aberta = new TransacaoFinanceira("conferir:c1:1", 7L, PagamentoService.DEVOLUCAO_CONFERIR, new BigDecimal("100.00"), "VERIFICAR", "c1", 1L);
        when(transacoes.findByTipoAndStatus(PagamentoService.DEVOLUCAO_CONFERIR, "VERIFICAR")).thenReturn(List.of(aberta));
        when(cobrancas.reservarEstorno(eq("c1"), any(), anyLong(), anyString())).thenReturn(1);
        when(cobrancas.concluirEstorno(eq("c1"), any(), anyLong(), anyString())).thenReturn(1);

        svc.devolverCobranca(c, new BigDecimal("100.00"));

        assertEquals(PagamentoService.CONFERENCIA_AUTOMATICA, aberta.getStatus());
    }

    /* Regressão (pendências de 26/11): sem ninguém de financeiro ou suporte cadastrado, o aviso da conferência vai para o administrador. */
    @Test void semEquipeOAvisoDaConferenciaVaiParaOAdministrador() {
        Cobranca c = asaasSemTxid();
        Usuario adm = mock(Usuario.class);
        when(adm.getId()).thenReturn(5L);
        when(usuarios.findByEmailIgnoreCase("adm@taskgo.com")).thenReturn(Optional.of(adm));
        svc.setModeracao(new ModeracaoService(usuarios, "adm@taskgo.com"));

        assertThrows(PagamentoService.EstornoAdiado.class, () -> svc.devolverCobranca(c, new BigDecimal("30.00")));

        verify(notificacoes).criar(eq(5L), any(), anyString(), anyString(), anyString(), org.mockito.ArgumentMatchers.argThat(k -> k.startsWith("estorno-conferir:c1:") && k.endsWith(":5")));
    }

    private Usuario equipeFinanceira() {
        Usuario f = mock(Usuario.class);
        when(f.getId()).thenReturn(9L);
        when(usuarios.findByTipoIgnoreCaseOrderByNomeAsc("financeiro")).thenReturn(List.of(f));
        return f;
    }

    /* Regressão (pendências de 26/11): outra semana cancelada na mesma cobrança sem txid aumenta o devido. A conferência passa a
       guardar o novo total e a equipe é avisada uma vez; repetir o mesmo pedido não avisa de novo. */
    @Test void aumentoDoValorDaConferenciaAvisaAEquipeUmaVez() {
        Cobranca c = new Cobranca("c1", "p1", 7L, "cartao", "principal", new BigDecimal("200.00"), "asaas", 0);
        c.pagar(1);
        equipeFinanceira();
        TransacaoFinanceira aberta = new TransacaoFinanceira("conferir:c1:1", 7L, PagamentoService.DEVOLUCAO_CONFERIR, new BigDecimal("50.00"), "VERIFICAR", "c1", 1L);
        when(transacoes.findByTipoAndStatus(PagamentoService.DEVOLUCAO_CONFERIR, "VERIFICAR")).thenReturn(List.of(aberta));
        when(transacoes.findByTipoAndStatus("DEVOLUCAO_PENDENTE", "PENDENTE")).thenReturn(List.of(
                new TransacaoFinanceira("v1", 7L, "DEVOLUCAO_PENDENTE", new BigDecimal("50.00"), "PENDENTE", "c1", 1L),
                new TransacaoFinanceira("v2", 7L, "DEVOLUCAO_PENDENTE", new BigDecimal("50.00"), "PENDENTE", "c1", 2L)));
        assertThrows(PagamentoService.EstornoAdiado.class, () -> svc.devolverCobranca(c, new BigDecimal("50.00")));

        ArgumentCaptor<TransacaoFinanceira> salva = ArgumentCaptor.forClass(TransacaoFinanceira.class);
        verify(transacoes).save(salva.capture());
        assertEquals(new BigDecimal("100.00"), salva.getValue().getValor());
        verify(notificacoes).criar(eq(9L), eq("p1"), anyString(), eq("Devolução para conferir mudou"), org.mockito.ArgumentMatchers.contains("subiu R$ 50,00: o total agora é R$ 100,00"), eq("conferir-valor:conferir:c1:1:100.00:9"));

        when(transacoes.findByTipoAndStatus(PagamentoService.DEVOLUCAO_CONFERIR, "VERIFICAR")).thenReturn(List.of(salva.getValue()));
        assertThrows(PagamentoService.EstornoAdiado.class, () -> svc.devolverCobranca(c, new BigDecimal("50.00")));
        verify(notificacoes, times(1)).criar(any(), any(), anyString(), eq("Devolução para conferir mudou"), anyString(), anyString());
    }

    /* Regressão (pendências de 26/11): a cobrança voltou a ter como devolver pelo Asaas, mas a devolução automática não saiu.
       Antes ficava só na lista; agora, passados 30 minutos sem reserva em andamento, a equipe é avisada. */
    @Test void devolucaoAutomaticaParadaAvisaAEquipe() {
        Cobranca c = asaasPaga("c1", "100.00");
        equipeFinanceira();
        long agora = 100_000_000L;
        TransacaoFinanceira velha = new TransacaoFinanceira("conferir:c1:1", 7L, PagamentoService.DEVOLUCAO_CONFERIR, new BigDecimal("100.00"), "VERIFICAR", "c1", agora - 31 * 60_000L);
        when(transacoes.findByTipoAndStatus(PagamentoService.DEVOLUCAO_CONFERIR, "VERIFICAR")).thenReturn(List.of(velha));
        when(cobrancas.findById("c1")).thenReturn(Optional.of(c));

        /* Revisão de 26/11: a conferência é antiga, mas a cobrança acabou de voltar a ser automática; a pendência ainda vai rodar.
           Avisar agora levaria a equipe a devolver em dobro. */
        svc.conferirAutomaticasParadas(agora);
        verify(notificacoes, never()).criar(any(), any(), anyString(), eq("Devolução automática parada"), anyString(), anyString());
        svc.conferirAutomaticasParadas(agora + 31 * 60_000L);
        verify(notificacoes).criar(eq(9L), eq("p1"), anyString(), eq("Devolução automática parada"), org.mockito.ArgumentMatchers.contains("R$ 100,00"), eq("conferir-automatica:conferir:c1:1:9"));
        agora += 62 * 60_000L;

        /* Vista há pouco, com reserva em andamento (a conciliação cuida) ou ainda manual: não avisa nem depois de 31 min. */
        TransacaoFinanceira recente = new TransacaoFinanceira("conferir:c2:1", 7L, PagamentoService.DEVOLUCAO_CONFERIR, new BigDecimal("100.00"), "VERIFICAR", "c2", agora - 60_000L);
        Cobranca reservadaAgora = asaasPaga("c3", "100.00");
        reservada(reservadaAgora, agora - 60_000L);
        TransacaoFinanceira comReserva = new TransacaoFinanceira("conferir:c3:1", 7L, PagamentoService.DEVOLUCAO_CONFERIR, new BigDecimal("100.00"), "VERIFICAR", "c3", agora - 31 * 60_000L);
        TransacaoFinanceira manual = new TransacaoFinanceira("conferir:c4:1", 7L, PagamentoService.DEVOLUCAO_CONFERIR, new BigDecimal("100.00"), "VERIFICAR", "c4", agora - 31 * 60_000L);
        Cobranca semTxid = new Cobranca("c4", "p1", 7L, "cartao", "principal", new BigDecimal("100.00"), "asaas", 0);
        semTxid.pagar(1);
        when(transacoes.findByTipoAndStatus(PagamentoService.DEVOLUCAO_CONFERIR, "VERIFICAR")).thenReturn(List.of(recente, comReserva, manual));
        when(cobrancas.findById("c2")).thenReturn(Optional.of(asaasPaga("c2", "100.00")));
        when(cobrancas.findById("c3")).thenReturn(Optional.of(reservadaAgora));
        when(cobrancas.findById("c4")).thenReturn(Optional.of(semTxid));
        svc.conferirAutomaticasParadas(agora);
        svc.conferirAutomaticasParadas(agora + 20 * 60_000L);
        verify(notificacoes, times(1)).criar(any(), any(), anyString(), eq("Devolução automática parada"), anyString(), anyString());
    }

    /* Regressão (revisão de 27/11): conferência antiga sem referência fazia findById(null) lançar (como no Spring Data)
       e a rodada parava ali, sem avaliar as outras conferências. */
    @Test void conferenciaSemReferenciaNaoParaARodada() {
        Cobranca c = asaasPaga("c1", "100.00");
        equipeFinanceira();
        long agora = 100_000_000L;
        TransacaoFinanceira legado = new TransacaoFinanceira("conferir:legado", 7L, PagamentoService.DEVOLUCAO_CONFERIR, new BigDecimal("10.00"), "VERIFICAR", null, agora - 90 * 60_000L);
        TransacaoFinanceira velha = new TransacaoFinanceira("conferir:c1:1", 7L, PagamentoService.DEVOLUCAO_CONFERIR, new BigDecimal("100.00"), "VERIFICAR", "c1", agora - 31 * 60_000L);
        when(transacoes.findByTipoAndStatus(PagamentoService.DEVOLUCAO_CONFERIR, "VERIFICAR")).thenReturn(List.of(legado, velha));
        when(cobrancas.findById(org.mockito.ArgumentMatchers.<String>isNull())).thenThrow(new IllegalArgumentException("The given id must not be null"));
        when(cobrancas.findById("c1")).thenReturn(Optional.of(c));
        svc.conferirAutomaticasParadas(agora);
        svc.conferirAutomaticasParadas(agora + 31 * 60_000L);
        verify(notificacoes, times(1)).criar(any(), any(), anyString(), eq("Devolução automática parada"), anyString(), anyString());
    }

    /* Regressão (3): fora de transação, o POST ao Asaas acontece com a transação do Spring já encerrada. */
    @Test void chamadaAoAsaasSemTransacaoAberta() throws Exception {
        Cobranca c = asaasPaga("c1", "100.00");
        when(cobrancas.reservarEstorno(eq("c1"), any(), anyLong(), anyString())).thenReturn(1);
        when(cobrancas.concluirEstorno(eq("c1"), any(), anyLong(), anyString())).thenReturn(1);
        AtomicBoolean ativa = new AtomicBoolean(true);
        when(asaas.estornar(any(), any(), any())).thenAnswer(inv -> { ativa.set(TransactionSynchronizationManager.isActualTransactionActive()); return null; });

        svc.devolverCobranca(c, new BigDecimal("100.00"));

        assertFalse(ativa.get());
    }

    /* Regressão (2): pedido com uma cobrança do Asaas e outra na carteira. A carteira falha na primeira rodada:
       o pedido segue "estornando", o Asaas não é chamado de novo e a carteira é devolvida na rodada seguinte. */
    @Test void carteiraQueFalhaNaoFazRepetirOAsaasENaoFechaOPedido() throws Exception {
        Pedido p = pedido("p1", "cancelado", "estornando");
        Cobranca a = asaasPaga("c1", "100.00");
        Cobranca b = new Cobranca("c2", "p1", 7L, "carteira", "principal", new BigDecimal("50.00"), "carteira", 0);
        b.pagar(1);
        Carteira carteira = new Carteira(7L);
        when(pedidos.findById("p1")).thenReturn(Optional.of(p));
        when(cobrancas.findByPedidoIdOrderByCriadaEmAsc("p1")).thenReturn(List.of(a, b));
        when(cobrancas.reservarEstorno(eq("c1"), any(), anyLong(), anyString())).thenReturn(1);
        when(cobrancas.concluirEstorno(eq("c1"), any(), anyLong(), anyString())).thenReturn(1);
        when(cobrancas.reservarEstorno(eq("c2"), any(), anyLong(), anyString())).thenThrow(new RuntimeException("banco caiu")).thenReturn(1);
        when(cobrancas.concluirEstorno(eq("c2"), any(), anyLong(), anyString())).thenReturn(1);
        when(carteiras.findByUsuarioIdForUpdate(7L)).thenReturn(Optional.of(carteira));

        svc.processarEstorno("p1");
        assertEquals("estornando", p.getPaid());
        a.estornar(new BigDecimal("100.00"), 2L);

        svc.processarEstorno("p1");

        verify(asaas, times(1)).estornar(any(), any(), any());
        assertEquals("estornado", p.getPaid());
        assertEquals(0, new BigDecimal("50").compareTo(carteira.getSaldoDisponivel()));
    }
    /* Regressão (revisor): o cliente cancela (o cancelamento já foi gravado) e a devolução falha no meio. Antes a marca
       "estornando" só era gravada no meio do processamento; com o erro antes dela, o pedido ficava "retido" e o job nunca
       devolvia. Agora a marca é gravada primeiro, e o erro não sobe para o cliente. */
    @Test void cancelamentoForaDeTransacaoMarcaEstornandoAntesDeDevolver() {
        Pedido p = pedido("p1", "cancelado", "retido");
        Cobranca c = new Cobranca("c1", "p1", 7L, "carteira", "principal", new BigDecimal("50.00"), "carteira", 0);
        c.pagar(1);
        when(pedidos.findById("p1")).thenReturn(Optional.of(p));
        /* A 2ª leitura das cobranças falha: no código antigo isso acontecia antes de gravar a marca. */
        when(cobrancas.findByPedidoIdOrderByCriadaEmAsc("p1")).thenReturn(List.of(c)).thenThrow(new RuntimeException("banco caiu")).thenReturn(List.of(c));

        org.junit.jupiter.api.Assertions.assertDoesNotThrow(() -> svc.estornar(p));

        assertEquals("estornando", p.getPaid());
    }
    /* Regressão (revisor): se até a marca "estornando" falhasse, o pedido ficava "cancelado" + "retido" e o job (que só
       olhava "estornando") nunca devolvia. A rede do job pega esse pedido e devolve. */
    @Test void redeDoJobPegaPedidoCanceladoComPagamentoRetido() {
        Pedido p = pedido("p1", "cancelado", "retido");
        Cobranca c = new Cobranca("c1", "p1", 7L, "carteira", "principal", new BigDecimal("50.00"), "carteira", 0);
        c.pagar(1);
        Carteira carteira = new Carteira(7L);
        when(pedidos.findById("p1")).thenReturn(Optional.of(p));
        when(pedidos.findByStatusAndPaid("cancelado", "retido")).thenReturn(List.of(p));
        when(pedidos.findByPaid("estornando")).thenAnswer(inv -> "estornando".equals(p.getPaid()) ? List.of(p) : List.of());
        when(cobrancas.findByPedidoIdOrderByCriadaEmAsc("p1")).thenReturn(List.of(c));
        when(cobrancas.reservarEstorno(eq("c1"), any(), anyLong(), anyString())).thenReturn(1);
        when(cobrancas.concluirEstorno(eq("c1"), any(), anyLong(), anyString())).thenAnswer(inv -> { c.estornar(new BigDecimal("50.00"), 2L); return 1; });
        when(carteiras.findByUsuarioIdForUpdate(7L)).thenReturn(Optional.of(carteira));

        svc.repetirDevolucoes();

        assertEquals(0, new BigDecimal("50").compareTo(carteira.getSaldoDisponivel()));
    }

    /* A rede não pode mexer em obra/recorrente: etapas e semanas têm devolução própria. */
    @Test void redeDoJobNaoTocaPedidoDeObra() {
        Pedido p = pedido("p2", "cancelado", "retido");
        Cobranca etapa = new Cobranca("c9", "p2", 7L, "carteira", "etapa", new BigDecimal("300.00"), "carteira", 0);
        etapa.pagar(1);
        when(pedidos.findById("p2")).thenReturn(Optional.of(p));
        when(pedidos.findByStatusAndPaid("cancelado", "retido")).thenReturn(List.of(p));
        when(cobrancas.findByPedidoIdOrderByCriadaEmAsc("p2")).thenReturn(List.of(etapa));

        svc.repetirDevolucoes();

        assertEquals("retido", p.getPaid());
        verify(cobrancas, never()).reservarEstorno(anyString(), any(), anyLong(), anyString());
    }

    /* Regressão (revisor): dentro de transação, a devolução rodava no afterCommit na thread de quem chamou, segurando a
       conexão durante o POST ao Asaas. Agora a marca vai na transação de quem chama e o afterCommit só entrega ao executor. */
    @Test void dentroDeTransacaoMarcaEEntregaAoExecutorSemChamarOAsaasNaThreadDeQuemChamou() throws Exception {
        Pedido p = pedido("p1", "cancelado", "retido");
        Cobranca c = asaasPaga("c1", "100.00");
        when(pedidos.findById("p1")).thenReturn(Optional.of(p));
        when(cobrancas.findByPedidoIdOrderByCriadaEmAsc("p1")).thenReturn(List.of(c));
        when(cobrancas.reservarEstorno(eq("c1"), any(), anyLong(), anyString())).thenReturn(1);
        when(cobrancas.concluirEstorno(eq("c1"), any(), anyLong(), anyString())).thenReturn(1);
        List<Runnable> entregues = new java.util.ArrayList<>();
        svc.setExecutor(entregues::add);
        var tsm = org.springframework.transaction.support.TransactionSynchronizationManager.class;
        org.springframework.transaction.support.TransactionSynchronizationManager.setActualTransactionActive(true);
        org.springframework.transaction.support.TransactionSynchronizationManager.initSynchronization();
        try {
            svc.estornar(p);
            assertEquals("estornando", p.getPaid(), "a marca vai na transação de quem chamou");
            verify(asaas, never()).estornar(any(), any(), any());
            for (var s : org.springframework.transaction.support.TransactionSynchronizationManager.getSynchronizations()) s.afterCommit();
        } finally {
            org.springframework.transaction.support.TransactionSynchronizationManager.clearSynchronization();
            org.springframework.transaction.support.TransactionSynchronizationManager.setActualTransactionActive(false);
        }
        verify(asaas, never()).estornar(any(), any(), any());
        assertEquals(1, entregues.size(), "o afterCommit só entrega ao executor");
        entregues.get(0).run();
        verify(asaas, times(1)).estornar(eq("asaas-c1"), any(), anyString());
    }
}
