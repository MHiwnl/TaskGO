package com.taskgo.api;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

/* Só maiores de 18 usam a TaskGO (Código Civil, art. 5º): declaração no cadastro e data conferida na verificação. */
class MaioridadeTest {

    @AfterEach void limpar() { org.springframework.security.core.context.SecurityContextHolder.clearContext(); }

    private AuthController auth(UsuarioRepository usuarios) {
        return new AuthController(usuarios, mock(PedidoRepository.class), mock(PedidoContratadoRepository.class), mock(MensagemRepository.class),
                mock(NotificacaoRepository.class), new BCryptPasswordEncoder(), new LoginAttemptLimiter());
    }

    private Usuario novo() {
        Usuario u = new Usuario(); u.setNome("Ana"); u.setEmail("ana@x.com"); u.setTipo("cliente"); u.setSenha("Tarefa#Forte2026");
        return u;
    }

    @Test void cadastroSemDeclararMaioridadeERecusado() {
        UsuarioRepository usuarios = mock(UsuarioRepository.class);
        ResponseStatusException e = assertThrows(ResponseStatusException.class, () -> auth(usuarios).registrar(novo(), new MockHttpServletRequest()));
        assertEquals(400, e.getStatusCode().value());
        verify(usuarios, never()).save(any());
    }

    @Test void cadastroComDeclaracaoFalsaERecusado() {
        UsuarioRepository usuarios = mock(UsuarioRepository.class);
        Usuario u = novo(); u.addExtra("maiorDeIdade", "true");
        assertThrows(ResponseStatusException.class, () -> auth(usuarios).registrar(u, new MockHttpServletRequest()));
        verify(usuarios, never()).save(any());
    }

    @Test void cadastroComDeclaracaoCriaContaSemGuardarODeclaradoNoExtra() {
        UsuarioRepository usuarios = mock(UsuarioRepository.class);
        when(usuarios.save(any(Usuario.class))).thenAnswer(i -> { Usuario s = i.getArgument(0); s.setId(7L); return s; });
        Usuario u = novo(); u.addExtra("maiorDeIdade", true);
        assertEquals(7L, auth(usuarios).registrar(u, new MockHttpServletRequest()).id());
        assertFalse(u.getExtra().containsKey("maiorDeIdade"));
    }

    private AuthController comGoogle(UsuarioRepository usuarios) {
        GoogleLogin google = mock(GoogleLogin.class);
        when(google.ativo()).thenReturn(true);
        when(google.verificar(any(), anyLong())).thenReturn(new GoogleLogin.Conta("sub-1", "ana@x.com", "Ana"));
        AuthController c = auth(usuarios); c.setGoogle(google);
        return c;
    }

    @Test void contaNovaPeloGoogleSemDeclararMaioridadeERecusada() {
        UsuarioRepository usuarios = mock(UsuarioRepository.class);
        when(usuarios.findByEmailIgnoreCase("ana@x.com")).thenReturn(Optional.empty());
        var body = new AuthController.GoogleRequest("token", "cliente", "81999999999", null, "criar", null);
        ResponseStatusException e = assertThrows(ResponseStatusException.class, () -> comGoogle(usuarios).entrarComGoogle(body, new MockHttpServletRequest()));
        assertEquals(400, e.getStatusCode().value());
        verify(usuarios, never()).save(any());
    }

    /* Regressão: a senha aleatória tinha 73 caracteres e o BCrypt recusa mais de 72 bytes (erro 500 no cadastro pelo Google). */
    @Test void contaNovaPeloGoogleComDeclaracaoEntra() {
        UsuarioRepository usuarios = mock(UsuarioRepository.class);
        when(usuarios.findByEmailIgnoreCase("ana@x.com")).thenReturn(Optional.empty());
        when(usuarios.save(any(Usuario.class))).thenAnswer(i -> { Usuario s = i.getArgument(0); s.setId(8L); return s; });
        var body = new AuthController.GoogleRequest("token", "cliente", "81999999999", null, "criar", true);
        assertEquals(200, comGoogle(usuarios).entrarComGoogle(body, new MockHttpServletRequest()).getStatusCode().value());
    }

    @Test void dezoitoAnosContamNoDiaDoAniversario() {
        LocalDate hoje = LocalDate.of(2026, 11, 22);
        assertTrue(VerificacaoController.maiorDeIdade(LocalDate.of(2008, 11, 22), hoje));
        assertFalse(VerificacaoController.maiorDeIdade(LocalDate.of(2008, 11, 23), hoje));
        // 29/02: em ano sem 29/02, completa 18 em 01/03 (Código Civil, art. 132, §3º)
        assertFalse(VerificacaoController.maiorDeIdade(LocalDate.of(2008, 2, 29), LocalDate.of(2026, 2, 28)));
        assertTrue(VerificacaoController.maiorDeIdade(LocalDate.of(2008, 2, 29), LocalDate.of(2026, 3, 1)));
        assertTrue(VerificacaoController.maiorDeIdade(LocalDate.of(2004, 2, 29), LocalDate.of(2022, 3, 1)));
    }

    /* ---------- verificação ---------- */

    private record Cenario(VerificacaoController c, UsuarioRepository usuarios, ModeracaoService moderacao, Verificacao v, Usuario u) {}

    private Cenario cenario() {
        VerificacaoRepository verificacoes = mock(VerificacaoRepository.class);
        UsuarioRepository usuarios = mock(UsuarioRepository.class);
        ModeracaoService moderacao = mock(ModeracaoService.class);
        
        Verificacao v = new Verificacao("v1", 5L, new byte[]{1}, null, new byte[]{1}, 1L);
        Usuario u = novo(); u.setId(5L);
        when(verificacoes.findById("v1")).thenReturn(Optional.of(v));
        when(usuarios.findById(5L)).thenReturn(Optional.of(u));
        when(usuarios.existsById(5L)).thenReturn(true);
        return new Cenario(new VerificacaoController(verificacoes, usuarios, mock(NotificacaoService.class), moderacao), usuarios, moderacao, v, u);
    }

    private static UsernamePasswordAuthenticationToken admin() { // verificação é área do moderador
        return new UsernamePasswordAuthenticationToken(new TaskgoPrincipal(1L, "mod@x.com", "Mod", "moderador", null), null, List.of());
    }

    private static String anosAtras(int anos, int diasDepois) {
        return LocalDate.now(VerificacaoController.FUSO).minusYears(anos).plusDays(diasDepois).toString();
    }

    @Test void aprovarSemDataDeNascimentoERecusado() {
        Cenario s = cenario();
        assertEquals(400, assertThrows(ResponseStatusException.class,
                () -> s.c().decidir("v1", "aprovar", new VerificacaoController.Decisao(null, null, null), admin())).getStatusCode().value());
        assertEquals(Verificacao.PENDENTE, s.v().getStatus());
    }

    @Test void aprovarComDataFuturaERecusado() {
        Cenario s = cenario();
        assertThrows(ResponseStatusException.class,
                () -> s.c().decidir("v1", "aprovar", new VerificacaoController.Decisao(null, null, anosAtras(0, 1)), admin()));
        assertEquals(Verificacao.PENDENTE, s.v().getStatus());
    }

    @Test void aprovarMaiorVerificaEGuardaNascimento() {
        Cenario s = cenario();
        Map<String, Object> r = s.c().decidir("v1", "aprovar", new VerificacaoController.Decisao(null, null, anosAtras(18, 0)), admin());
        assertEquals(Verificacao.APROVADA, r.get("status"));
        assertEquals(false, r.get("menorDeIdade"));
        assertEquals(anosAtras(18, 0), s.u().getBirth());
        assertTrue(VerificacaoController.verificado(s.u()));
        verify(s.moderacao(), never()).banir(anyLong(), anyBoolean(), any());
    }

    @Test void aprovarMenorRecusaABanaConta() {
        Cenario s = cenario();
        Map<String, Object> r = s.c().decidir("v1", "aprovar", new VerificacaoController.Decisao(null, null, anosAtras(18, 1)), admin());
        assertEquals(Verificacao.RECUSADA, r.get("status"));
        assertEquals(true, r.get("menorDeIdade"));
        assertEquals("Menor de 18 anos", s.v().getMotivo());
        assertFalse(VerificacaoController.verificado(s.u()));
        verify(s.moderacao()).banir(5L, true, 1L);
    }

    /* Regressão (revisor): com a conta já excluída, banir lançava 404 e a recusa nunca era gravada. */
    @Test void menorComContaJaExcluidaSoRegistraARecusa() {
        Cenario s = cenario();
        when(s.usuarios().findById(5L)).thenReturn(Optional.empty());
        when(s.usuarios().existsById(5L)).thenReturn(false);
        Map<String, Object> r = s.c().decidir("v1", "aprovar", new VerificacaoController.Decisao(null, null, anosAtras(18, 1)), admin());
        assertEquals(Verificacao.RECUSADA, r.get("status"));
        verify(s.moderacao(), never()).banir(anyLong(), anyBoolean(), any());
    }

    @Test void recusarNaoExigeDataDeNascimento() {
        Cenario s = cenario();
        Map<String, Object> r = s.c().decidir("v1", "recusar", new VerificacaoController.Decisao("Documento ilegível", "Fotografe com mais luz.", null), admin());
        assertEquals(Verificacao.RECUSADA, r.get("status"));
    }
}
