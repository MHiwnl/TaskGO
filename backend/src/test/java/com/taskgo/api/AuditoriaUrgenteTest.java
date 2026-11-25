package com.taskgo.api;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

/* Falhas achadas na auditoria de 2026-11-19: tomada de conta e vazamento de dados do pedido. */
class AuditoriaUrgenteTest {

    @AfterEach void limpar() { SecurityContextHolder.clearContext(); }

    @Test void confirmarEmailDeContaJaConfirmadaNaoDevolveAConta() {
        UsuarioRepository usuarios = mock(UsuarioRepository.class);
        Usuario vitima = new Usuario(); vitima.setId(1L); vitima.setEmail("vitima@x.com");
        when(usuarios.findByEmailIgnoreCase("vitima@x.com")).thenReturn(Optional.of(vitima));
        ConfirmacaoEmailService s = new ConfirmacaoEmailService(usuarios, mock(EmailRecuperacaoSenha.class));
        assertThrows(ResponseStatusException.class, () -> s.confirmar("vitima@x.com", "000000"));
    }

    @Test void cadastroComIdNoCorpoNaoSobrescreveContaExistente() {
        UsuarioRepository usuarios = mock(UsuarioRepository.class);
        AuthController c = new AuthController(usuarios, mock(PedidoRepository.class), mock(PedidoContratadoRepository.class), mock(MensagemRepository.class),
                mock(NotificacaoRepository.class), new BCryptPasswordEncoder(), new LoginAttemptLimiter());
        Usuario novo = new Usuario(); novo.setId(1L); novo.setNome("Atacante"); novo.setEmail("atk@x.com"); novo.setTipo("cliente"); novo.setSenha("Tarefa#Forte2026"); novo.addExtra("maiorDeIdade", true);
        when(usuarios.save(any(Usuario.class))).thenAnswer(i -> { Usuario u = i.getArgument(0); assertNull(u.getId(), "o id do corpo chegou ao save"); u.setId(50L); return u; });
        assertEquals(50L, c.registrar(novo, new MockHttpServletRequest()).id());
    }

    private static Pedido pedido(String id) {
        Pedido p = new Pedido(); p.setId(id); p.setClientEmail("cliente@x.com"); p.setService("eletrica"); p.setPrice(150.0); p.setStatus("aguardando");
        return p;
    }

    @Test void criarRecusaIdComAspasOuFormula() {
        PedidoContratadoRepository repo = mock(PedidoContratadoRepository.class);
        PedidoService s = new PedidoService(repo);
        for (String id : new String[]{"o_1'onclick", "=HYPERLINK(1)", "a\"b", "-x", ""})
            assertThrows(ResponseStatusException.class, () -> s.criar(pedido(id)), id);
    }

    @Test void notaNaoEntraNaCriacaoNemPeloPut() {
        PedidoContratadoRepository repo = mock(PedidoContratadoRepository.class);
        when(repo.save(any(Pedido.class))).thenAnswer(i -> i.getArgument(0));
        PedidoService s = new PedidoService(repo);
        Pedido novo = pedido("o_abc123"); novo.setRating(5.0);
        assertNull(s.criar(novo).getRating());

        Pedido feito = pedido("o_abc124"); feito.setStatus("concluido");
        when(repo.findById("o_abc124")).thenReturn(Optional.of(feito));
        Pedido put = new Pedido(); put.setRating(1_000_000.0);
        assertNull(s.atualizar("o_abc124", "cliente@x.com", put).getRating());
    }

    @Test void profissionalNaoVeEnderecoNemPontoAntesDaConfirmacao() {
        PedidoRespostaAdvice advice = new PedidoRespostaAdvice(new com.fasterxml.jackson.databind.ObjectMapper());
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new TaskgoPrincipal(2L, "pro@x.com", "Pro", "profissional", null), null, java.util.List.of()));
        Pedido p = pedido("o_abc125"); p.setStatus("a_caminho"); p.setProEmail("pro@x.com"); p.setAddress("Rua A, 10"); p.setCode("1234");
        p.addExtra("lat", -8.05); p.addExtra("lng", -34.9); p.addExtra("confirmacaoCliente", "pendente");
        @SuppressWarnings("unchecked") Map<String, Object> antes = (Map<String, Object>) advice.beforeBodyWrite(p, null, null, null, null, null);
        assertFalse(antes.containsKey("address")); assertFalse(antes.containsKey("lat")); assertFalse(antes.containsKey("code"));

        p.addExtra("confirmacaoCliente", "confirmada");
        @SuppressWarnings("unchecked") Map<String, Object> depois = (Map<String, Object>) advice.beforeBodyWrite(p, null, null, null, null, null);
        assertEquals("Rua A, 10", depois.get("address")); assertFalse(depois.containsKey("code"));
    }
}
