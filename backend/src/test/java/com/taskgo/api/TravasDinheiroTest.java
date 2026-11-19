package com.taskgo.api;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.server.ResponseStatusException;

/* Travas dos caminhos de dinheiro achados na auditoria: taxa em dobro, resgate sem resposta e transferência do admin. */
class TravasDinheiroTest {

    @Test void recebiRepetidoCriaUmaTaxaSo() {
        PendenciaRepository pendencias = mock(PendenciaRepository.class);
        PagamentoService pagamentos = mock(PagamentoService.class);
        DinheiroService d = new DinheiroService(pendencias, mock(PedidoContratadoRepository.class), mock(UsuarioRepository.class), mock(CarteiraRepository.class),
                mock(TransacaoFinanceiraRepository.class), pagamentos, mock(EventosService.class), new BigDecimal("0.20"), BigDecimal.TEN, new BigDecimal("50"));
        when(pendencias.existsById("taxa:etapa:e1")).thenReturn(false, true);
        when(pendencias.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));

        d.registrarTaxa(7L, "p1", "etapa:e1", new BigDecimal("12.00"));
        d.registrarTaxa(7L, "p1", "etapa:e1", new BigDecimal("12.00"));

        verify(pendencias, times(1)).saveAndFlush(argThat(x -> "taxa:etapa:e1".equals(x.getId())));
    }

    @Test void resgateSemRespostaDoAsaasNaoDevolveOSaldo() {
        SolicitacaoSaqueRepository saques = mock(SolicitacaoSaqueRepository.class);
        CarteiraRepository carteiras = mock(CarteiraRepository.class);
        UsuarioRepository usuarios = mock(UsuarioRepository.class);
        PagamentoService pagamentos = mock(PagamentoService.class);
        AsaasCliente asaas = mock(AsaasCliente.class);
        SolicitacaoSaque s = new SolicitacaoSaque("s1", 7L, new BigDecimal("80.00"), "chave-1", 1L);
        Usuario u = new Usuario(); u.setId(7L); u.addExtra("chavePix", Map.of("tipo", "EMAIL", "chave", "pro@exemplo.test"));
        when(saques.findById("s1")).thenReturn(Optional.of(s));
        when(usuarios.findById(7L)).thenReturn(Optional.of(u));
        when(pagamentos.asaasAtivo()).thenReturn(true);
        when(asaas.transferirPix(any(), anyString(), anyString(), anyString())).thenThrow(new AsaasCliente.Erro(503, "O Asaas não respondeu."));

        new ResgateService(saques, carteiras, usuarios, mock(TransacaoFinanceiraRepository.class), pagamentos, asaas, mock(NotificacaoService.class)).enviar("s1");

        assertEquals("VERIFICAR", s.getStatus());
        verify(carteiras, never()).findByUsuarioIdForUpdate(any());
    }

    @Test void transferenciaDoAdminNaoLevaODinheiroDosUsuarios() {
        PagamentoService pagamentos = mock(PagamentoService.class);
        AsaasCliente asaas = mock(AsaasCliente.class);
        ModeracaoService moderacao = mock(ModeracaoService.class);
        CarteiraRepository carteiras = mock(CarteiraRepository.class);
        when(pagamentos.asaasAtivo()).thenReturn(true);
        when(asaas.configurado()).thenReturn(true);
        when(moderacao.administrador("adm@exemplo.test")).thenReturn(true);
        when(asaas.saldo()).thenReturn(new com.fasterxml.jackson.databind.ObjectMapper().createObjectNode().put("balance", "500.00"));
        when(carteiras.totalDosUsuarios()).thenReturn(new BigDecimal("300.00"));
        AdminFinanceiroController c = new AdminFinanceiroController(mock(CobrancaRepository.class), mock(TransacaoFinanceiraRepository.class), mock(UsuarioRepository.class), pagamentos, asaas, moderacao);
        c.setCarteiras(carteiras);
        var auth = new UsernamePasswordAuthenticationToken(new TaskgoPrincipal(1L, "adm@exemplo.test", "Adm", "administrador", null), null, java.util.List.of());

        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> c.transferir(new AdminFinanceiroController.Transferencia(new BigDecimal("250.00"), "EMAIL", "dono@exemplo.test", null), auth));
        assertTrue(e.getReason().contains("200,00"), e.getReason());
        verify(asaas, never()).transferirPix(any(), anyString(), anyString(), anyString());
    }
}
