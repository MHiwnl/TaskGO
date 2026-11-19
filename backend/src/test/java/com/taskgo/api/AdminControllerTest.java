package com.taskgo.api;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.server.ResponseStatusException;

/* Fichas e papéis da equipe. O painel novo não lista a base inteira: lê por id, por busca exata ou por página. */
class AdminControllerTest {
    private UsuarioRepository usuarios;
    private PedidoContratadoRepository pedidos;
    private HistoricoContaRepository historico;
    private ModeracaoService moderacao;
    private AdminController controller;
    private PainelAdminController painel;
    private final UsernamePasswordAuthenticationToken adm = token(9L, "adm@taskgo.com", "cliente");
    private final UsernamePasswordAuthenticationToken comum = token(1L, "ana@taskgo.com", "cliente");
    private final UsernamePasswordAuthenticationToken moderador = token(7L, "mod@taskgo.com", "moderador");
    private final UsernamePasswordAuthenticationToken financeiro = token(8L, "fin@taskgo.com", "financeiro");

    private static UsernamePasswordAuthenticationToken token(Long id, String email, String tipo) {
        return new UsernamePasswordAuthenticationToken(new TaskgoPrincipal(id, email, "Pessoa " + id, tipo, null), null, List.of());
    }

    @BeforeEach void setup() {
        usuarios = mock(UsuarioRepository.class); pedidos = mock(PedidoContratadoRepository.class); historico = mock(HistoricoContaRepository.class);
        moderacao = spy(new ModeracaoService(usuarios, "adm@taskgo.com"));
        doNothing().when(moderacao).registrar(anyLong(), anyString(), anyString(), any(), any());
        controller = new AdminController(moderacao, usuarios, pedidos, mock(PropostaRepository.class), mock(RecursoRepository.class), mock(MensagemRepository.class));
        controller.setHistorico(historico);
        painel = new PainelAdminController(moderacao, usuarios, pedidos, mock(DenunciaRepository.class), mock(RecursoRepository.class), mock(VerificacaoRepository.class),
                mock(ChamadoRepository.class), historico, mock(MetricasService.class), mock(ExportacaoService.class), mock(ExportacaoRepository.class));
        Usuario ana = usuario(1L, "Ana Souza", "ana@taskgo.com", "cliente");
        ana.setCpf("12345678901"); ana.setTelefone("(81) 99999-0001");
        when(usuarios.findById(1L)).thenReturn(Optional.of(ana));
        when(usuarios.findByEmailIgnoreCase("ana@taskgo.com")).thenReturn(Optional.of(ana));
        when(usuarios.findByCpfIn(any())).thenReturn(List.of(ana));
        when(usuarios.findByTelefoneIn(any())).thenReturn(List.of(ana));
        when(usuarios.buscarPorNome(eq("ana%"), any())).thenReturn(List.of(ana));
        when(pedidos.resumoAvaliacoes(anyString())).thenReturn(new Object[]{null, 0L});
    }

    @Test void papeisVeemSoASuaParte() {
        assertEquals(403, assertThrows(ResponseStatusException.class, () -> controller.detalheUsuario(1L, comum)).getStatusCode().value());
        assertEquals(403, assertThrows(ResponseStatusException.class, () -> painel.numeros(30, moderador)).getStatusCode().value());
        assertEquals(403, assertThrows(ResponseStatusException.class, () -> painel.auditoria(null, "", "", "", "", "", financeiro)).getStatusCode().value());
        assertEquals(403, assertThrows(ResponseStatusException.class, () -> painel.filaPagamentos(moderador)).getStatusCode().value());
        /* Administrador analisa e administra; quem decide denúncias e pagamentos é o suporte. */
        assertFalse(PapelEquipe.areas("administrador").contains(PapelEquipe.Area.MODERACAO));
        assertFalse(PapelEquipe.areas("administrador").contains(PapelEquipe.Area.PAGAMENTOS));
        assertTrue(PapelEquipe.areas("administrador").containsAll(List.of(PapelEquipe.Area.NUMEROS, PapelEquipe.Area.EQUIPE, PapelEquipe.Area.AVISOS)));
        assertTrue(PapelEquipe.areas("suporte").containsAll(List.of(PapelEquipe.Area.MODERACAO, PapelEquipe.Area.PAGAMENTOS)));
        assertEquals(403, assertThrows(ResponseStatusException.class, () -> painel.filaPagamentos(adm)).getStatusCode().value());
        assertEquals("administrador", painel.eu(adm).get("papel"));
        assertEquals(List.of("HOJE", "MODERACAO", "BUSCAR", "AUDITORIA"), painel.eu(moderador).get("areas"));
    }

    @Test void fichaRegistraQuemAbriuELimitaOsPedidos() {
        when(pedidos.findByClientEmailIgnoreCaseOrProEmailIgnoreCaseOrderByCreatedDesc(eq("ana@taskgo.com"), eq("ana@taskgo.com"), any())).thenReturn(List.of());
        when(pedidos.countByClientEmailIgnoreCaseOrProEmailIgnoreCase(anyString(), anyString())).thenReturn(1234L);
        Map<String, Object> f = controller.detalheUsuario(1L, moderador);
        assertEquals("Ana Souza", f.get("nome"));
        assertEquals(1234L, f.get("totalPedidos"));
        verify(pedidos).findByClientEmailIgnoreCaseOrProEmailIgnoreCaseOrderByCreatedDesc(eq("ana@taskgo.com"), eq("ana@taskgo.com"), argThat(p -> p.getPageSize() == 50));
        verify(moderacao).registrar(eq(1L), eq("acesso"), contains("abriu a ficha"), eq(7L), isNull());
        verify(usuarios, never()).findAll();
    }

    @SuppressWarnings("unchecked")
    @Test void buscaEntendeOQueFoiDigitado() {
        assertEquals("e-mail", painel.buscar("ana@taskgo.com", adm).get("como"));
        assertEquals("CPF ou telefone", painel.buscar("123.456.789-01", adm).get("como"));
        assertEquals("nome (começo)", painel.buscar("Ana", adm).get("como"));
        assertEquals(1, ((List<Object>) painel.buscar("Ana", adm).get("contas")).size());
        assertEquals(400, assertThrows(ResponseStatusException.class, () -> painel.buscar("an", adm)).getStatusCode().value());
        verify(usuarios, never()).findAll();
    }

    private static Usuario usuario(Long id, String nome, String email, String tipo) {
        Usuario u = new Usuario(); u.setId(id); u.setNome(nome); u.setEmail(email); u.setTipo(tipo); return u;
    }
}
