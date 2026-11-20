package com.taskgo.api;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

/* Auditoria de 2026-11-19: cobrança em dobro, transferência às cegas e resgate preso. */
class AuditoriaDinheiroTest {
    private static final com.fasterxml.jackson.databind.ObjectMapper JSON = new com.fasterxml.jackson.databind.ObjectMapper();
    private static Cobranca cartao() { return new Cobranca("c1", "p1", 7L, "cartao", "pendencia", new BigDecimal("50.00"), "asaas", 1L); }

    @Test void cartaoEmAnaliseOuSemRespostaNaoViraRecusa() {
        Cobranca analise = cartao();
        PagamentoService.resultadoCartao(analise, JSON.createObjectNode().put("status", "AWAITING_RISK_ANALYSIS"), 2L);
        assertEquals(Cobranca.PENDENTE, analise.getStatus());

        Cobranca semResposta = cartao();
        PagamentoService.semRespostaOuRecusa(semResposta, new AsaasCliente.Erro(503, "O Asaas não respondeu."));
        assertEquals(Cobranca.PENDENTE, semResposta.getStatus());

        Cobranca recusado = cartao();
        PagamentoService.semRespostaOuRecusa(recusado, new AsaasCliente.Erro(400, "Cartão recusado."));
        assertEquals(Cobranca.RECUSADA, recusado.getStatus());

        Cobranca aprovado = cartao();
        PagamentoService.resultadoCartao(aprovado, JSON.createObjectNode().put("status", "CONFIRMED"), 2L);
        assertEquals(Cobranca.PAGA, aprovado.getStatus());
    }

    @Test void transferenciaSemRespostaFicaParaConferirENaoParaRepetir() {
        PagamentoService pagamentos = mock(PagamentoService.class);
        AsaasCliente asaas = mock(AsaasCliente.class);
        ModeracaoService moderacao = mock(ModeracaoService.class);
        CarteiraRepository carteiras = mock(CarteiraRepository.class);
        TransacaoFinanceiraRepository transacoes = mock(TransacaoFinanceiraRepository.class);
        when(pagamentos.asaasAtivo()).thenReturn(true);
        when(asaas.configurado()).thenReturn(true);
        when(moderacao.administrador("adm@exemplo.test")).thenReturn(true);
        when(asaas.saldo()).thenReturn(JSON.createObjectNode().put("balance", "500.00"));
        when(carteiras.totalDosUsuarios()).thenReturn(BigDecimal.ZERO);
        when(asaas.transferirPix(any(), anyString(), anyString(), anyString())).thenThrow(new AsaasCliente.Erro(503, "sem resposta"));
        AdminFinanceiroController c = new AdminFinanceiroController(mock(CobrancaRepository.class), transacoes, mock(UsuarioRepository.class), pagamentos, asaas, moderacao);
        c.setCarteiras(carteiras);
        var auth = new UsernamePasswordAuthenticationToken(new TaskgoPrincipal(1L, "adm@exemplo.test", "Adm", "administrador", null), null, java.util.List.of());

        Map<String, Object> r = c.transferir(new AdminFinanceiroController.Transferencia(new BigDecimal("100.00"), "EMAIL", "dono@exemplo.test", null), auth);
        assertEquals("VERIFICAR", r.get("status"));
        assertNotNull(r.get("aviso"));
        verify(transacoes).save(argThat(t -> "VERIFICAR".equals(t.getStatus())));
    }

    @Test void resgateEmConferenciaPodeSerDevolvidoPelaEquipe() {
        SolicitacaoSaqueRepository saques = mock(SolicitacaoSaqueRepository.class);
        CarteiraRepository carteiras = mock(CarteiraRepository.class);
        SolicitacaoSaque s = new SolicitacaoSaque("s1", 7L, new BigDecimal("80.00"), "k", 1L); s.situacao("VERIFICAR");
        Carteira cart = mock(Carteira.class);
        when(saques.findById("s1")).thenReturn(Optional.of(s));
        when(carteiras.findByUsuarioIdForUpdate(7L)).thenReturn(Optional.of(cart));
        ResgateService r = new ResgateService(saques, carteiras, mock(UsuarioRepository.class), mock(TransacaoFinanceiraRepository.class),
                mock(PagamentoService.class), mock(AsaasCliente.class), mock(NotificacaoService.class));

        r.resolverVerificar("s1", false);
        assertEquals("FALHOU", s.getStatus());
        verify(cart).devolverReserva(new BigDecimal("80.00"));
        assertThrows(org.springframework.web.server.ResponseStatusException.class, () -> r.resolverVerificar("s1", true));
    }
}
