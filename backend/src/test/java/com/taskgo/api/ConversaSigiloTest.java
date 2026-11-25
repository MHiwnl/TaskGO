package com.taskgo.api;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.server.ResponseStatusException;

/* Sigilo da conversa (Marco Civil art. 8º; LGPD art. 20): só a moderação, com denúncia ou recurso em análise,
   vê o texto; cada abertura fica na conta de quem conversou; recurso e aviso ligam ao pedido. */
class ConversaSigiloTest {
    private UsuarioRepository usuarios;
    private PedidoContratadoRepository pedidos;
    private MensagemRepository mensagens;
    private DenunciaRepository denuncias;
    private RecursoRepository recursos;
    private HistoricoContaRepository historico;
    private ModeracaoService moderacao;
    private AdminController admin;
    private final UsernamePasswordAuthenticationToken adm = token(9L, "adm@taskgo.com", "cliente");
    private final UsernamePasswordAuthenticationToken moderador = token(7L, "mod@taskgo.com", "moderador");
    private final UsernamePasswordAuthenticationToken suporte = token(6L, "sup@taskgo.com", "suporte");
    private final UsernamePasswordAuthenticationToken financeiro = token(8L, "fin@taskgo.com", "financeiro");
    private final UsernamePasswordAuthenticationToken ana = token(1L, "ana@taskgo.com", "cliente");

    private static TaskgoPrincipal principal(UsernamePasswordAuthenticationToken t) { return (TaskgoPrincipal) t.getPrincipal(); }

    private static UsernamePasswordAuthenticationToken token(Long id, String email, String tipo) {
        return new UsernamePasswordAuthenticationToken(new TaskgoPrincipal(id, email, "Pessoa " + id, tipo, null), null, List.of());
    }

    @BeforeEach void setup() {
        usuarios = mock(UsuarioRepository.class); pedidos = mock(PedidoContratadoRepository.class); mensagens = mock(MensagemRepository.class);
        denuncias = mock(DenunciaRepository.class); recursos = mock(RecursoRepository.class); historico = mock(HistoricoContaRepository.class);
        moderacao = spy(new ModeracaoService(usuarios, "adm@taskgo.com"));
        doNothing().when(moderacao).registrar(anyLong(), anyString(), anyString(), any(), any());
        moderacao.setDenuncias(denuncias); moderacao.setRecursos(recursos); moderacao.setHistorico(historico);
        admin = new AdminController(moderacao, usuarios, pedidos, mock(PropostaRepository.class), recursos, mensagens);
        when(usuarios.findByEmailIgnoreCase("ana@taskgo.com")).thenReturn(Optional.of(usuario(1L, "ana@taskgo.com")));
        when(usuarios.findByEmailIgnoreCase("pro@taskgo.com")).thenReturn(Optional.of(usuario(2L, "pro@taskgo.com")));
        Pedido p = new Pedido(); p.setId("p1"); p.setClientEmail("ana@taskgo.com"); p.setProEmail("pro@taskgo.com");
        when(pedidos.findById("p1")).thenReturn(Optional.of(p));
        when(mensagens.findByPedidoIdOrderByCriadaEmAsc("p1")).thenReturn(List.of(new Mensagem("m1", "p1", 1L, 2L, "c1", "Chego às 9", 1000L)));
    }

    @Test void moderadorSemCasoNaoVeNemRegistra() {
        assertEquals(403, assertThrows(ResponseStatusException.class, () -> admin.conversaDoPedido("p1", moderador)).getStatusCode().value());
        verify(moderacao, never()).registrar(anyLong(), anyString(), anyString(), any(), any());
        verify(mensagens, never()).findByPedidoIdOrderByCriadaEmAsc(anyString());
    }

    @Test void denunciaEmAnaliseAbreRegistraCadaParticipanteELeSemMarcarLida() {
        when(denuncias.existsByPedidoIdAndStatus("p1", "em_analise")).thenReturn(true);
        List<MensagemController.MensagemDto> conversa = admin.conversaDoPedido("p1", moderador);
        assertEquals(1, conversa.size());
        assertEquals("Chego às 9", conversa.get(0).texto());
        assertNull(conversa.get(0).lidaEm());
        verify(mensagens, never()).saveAll(any());
        verify(moderacao).registrar(eq(1L), eq("acesso"), contains("Pessoa 7 (moderador) abriu a conversa do pedido — motivo: denúncia em análise"), eq(7L), eq("p1"));
        verify(moderacao).registrar(eq(2L), eq("acesso"), contains("motivo: denúncia em análise"), eq(7L), eq("p1"));
    }

    @Test void suporteComDenunciaAbreMasAdministradorEFinanceiroNao() {
        when(denuncias.existsByPedidoIdAndStatus("p1", "em_analise")).thenReturn(true);
        assertEquals(1, admin.conversaDoPedido("p1", suporte).size());
        assertEquals(403, assertThrows(ResponseStatusException.class, () -> admin.conversaDoPedido("p1", adm)).getStatusCode().value());
        assertEquals(403, assertThrows(ResponseStatusException.class, () -> admin.conversaDoPedido("p1", financeiro)).getStatusCode().value());
    }

    /* Recursos da conta em um fake: a consulta do serviço acha só o recurso em análise do mesmo pedido. */
    private final List<Recurso> recursosDoTeste = new java.util.ArrayList<>();
    private void recursosFake() {
        when(recursos.existsByUsuarioIdAndStatusAndPedidoId(anyLong(), anyString(), any())).thenAnswer(inv -> recursosDoTeste.stream().anyMatch(r ->
                r.getUsuarioId().equals(inv.getArgument(0)) && r.getStatus().equals(inv.getArgument(1)) && java.util.Objects.equals(r.getPedidoId(), inv.getArgument(2))));
    }

    /* Regra do revisor: Ana tem aviso em p1 e p2 e recorre só do p2. A conversa de p2 abre; a de p1 não. */
    @Test void recursoAbreSoAConversaDoPedidoContestado() {
        Pedido p2 = new Pedido(); p2.setId("p2"); p2.setClientEmail("ana@taskgo.com"); p2.setProEmail("pro@taskgo.com");
        when(pedidos.findById("p2")).thenReturn(Optional.of(p2));
        when(mensagens.findByPedidoIdOrderByCriadaEmAsc("p2")).thenReturn(List.of(new Mensagem("m2", "p2", 1L, 2L, "c1", "Chego às 10", 1000L)));
        recursosDoTeste.add(new Recurso("r1", 1L, "p2", "Contesto o aviso do pedido 2.", 1000L));
        recursosFake();
        assertEquals(1, admin.conversaDoPedido("p2", moderador).size());
        verify(moderacao).registrar(eq(1L), eq("acesso"), contains("motivo: recurso em análise"), eq(7L), eq("p2"));
        assertEquals(403, assertThrows(ResponseStatusException.class, () -> admin.conversaDoPedido("p1", moderador)).getStatusCode().value());
    }

    @Test void recursoJaDecididoNaoAbreAConversa() {
        Recurso r = new Recurso("r1", 1L, "p1", "Contesto o aviso.", 1000L);
        r.decidir(Recurso.NEGADO, "adm@taskgo.com", null, 2000L);
        recursosDoTeste.add(r);
        recursosFake();
        assertEquals(403, assertThrows(ResponseStatusException.class, () -> admin.conversaDoPedido("p1", moderador)).getStatusCode().value());
    }

    /* Recurso antigo (criado antes da coluna pedido_id) não liga a nenhum pedido. */
    @Test void recursoAntigoSemPedidoNaoAbreConversa() {
        Recurso antigo = new Recurso("r0", 1L, null, "Recurso de antes da migração.", 1000L);
        assertNull(antigo.getPedidoId());
        recursosDoTeste.add(antigo);
        recursosFake();
        assertEquals(403, assertThrows(ResponseStatusException.class, () -> admin.conversaDoPedido("p1", moderador)).getStatusCode().value());
    }

    private void anaComAvisos(int avisos) {
        Usuario u = usuario(1L, "ana@taskgo.com"); u.getExtra().put(ModeracaoService.AVISOS, avisos);
        when(usuarios.findById(1L)).thenReturn(Optional.of(u));
    }

    /* Pedido informado no recurso tem que ser um aviso da própria conta (identidade vem da sessão). */
    @Test void recorrerComPedidoSemAvisoDaPessoaDaDe400() {
        anaComAvisos(1);
        ModeracaoController c = controller(moderacao);
        when(historico.existsByUsuarioIdAndTipoAndPedidoId(1L, "aviso", "p9")).thenReturn(false);
        assertEquals(400, assertThrows(ResponseStatusException.class, () -> c.recorrer(Map.of("texto", "Contesto o aviso.", "pedidoId", "p9"), ana)).getStatusCode().value());
        verify(recursos, never()).save(any());
    }

    @Test void recorrerComPedidoDeOutraPessoaDa400() {
        anaComAvisos(1);
        when(historico.existsByUsuarioIdAndTipoAndPedidoId(2L, "aviso", "p3")).thenReturn(true);
        ModeracaoController c = controller(moderacao);
        assertEquals(400, assertThrows(ResponseStatusException.class, () -> c.recorrer(Map.of("texto", "Contesto o aviso.", "pedidoId", "p3"), ana)).getStatusCode().value());
        verify(historico).existsByUsuarioIdAndTipoAndPedidoId(eq(1L), eq("aviso"), eq("p3"));
        verify(recursos, never()).save(any());
    }

    @Test void recorrerGravaOPedidoDoAvisoEscolhido() {
        anaComAvisos(2);
        when(historico.existsByUsuarioIdAndTipoAndPedidoId(1L, "aviso", "p2")).thenReturn(true);
        when(recursos.saveAndFlush(any(Recurso.class))).thenAnswer(inv -> inv.getArgument(0));
        ModeracaoController c = controller(moderacao);
        ModeracaoController.RecursoDto enviado = c.recorrer(Map.of("texto", "Contesto o aviso do pedido 2.", "pedidoId", "p2"), ana);
        assertEquals("p2", enviado.pedidoId());
        ArgumentCaptor<Recurso> salvo = ArgumentCaptor.forClass(Recurso.class);
        verify(recursos).saveAndFlush(salvo.capture());
        assertEquals("p2", salvo.getValue().getPedidoId());
    }

    /* Regressão (revisor): dois envios ao mesmo tempo passavam pela checagem e abriam dois pedidos. O banco barra o
       segundo (uk_recursos_um_em_analise, V34); a resposta vira 409 e o envio não fica registrado no histórico. */
    @Test void segundoRecursoSimultaneoRecebe409() {
        anaComAvisos(2);
        when(historico.existsByUsuarioIdAndTipoAndPedidoId(1L, "aviso", "p1")).thenReturn(true);
        when(recursos.saveAndFlush(any(Recurso.class))).thenThrow(new org.springframework.dao.DataIntegrityViolationException("uk_recursos_um_em_analise"));
        ModeracaoController c = controller(moderacao);
        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> c.recorrer(Map.of("texto", "Contesto o aviso do pedido 1.", "pedidoId", "p1"), ana));
        assertEquals(409, e.getStatusCode().value());
        verify(moderacao, never()).registrar(eq(1L), eq("recurso"), anyString(), any(), any());
    }

    @Test void denunciaProcedenteNaoAbreAConversa() {
        when(denuncias.existsByPedidoIdAndStatus("p1", "procedente")).thenReturn(true);
        assertEquals(403, assertThrows(ResponseStatusException.class, () -> admin.conversaDoPedido("p1", moderador)).getStatusCode().value());
    }

    @Test void fotoDaConversaSegueOMesmoCriterio() {
        FotoPedidoRepository fotos = mock(FotoPedidoRepository.class);
        when(fotos.findById("f1")).thenReturn(Optional.of(new FotoPedido("f1", "p1", 1L, "mensagem", "image/jpeg", 10, 10, new byte[]{1}, 1000L)));
        FotoPedidoService svc = new FotoPedidoService(fotos, pedidos, usuarios);
        svc.setModeracao(moderacao);
        assertEquals(404, assertThrows(ResponseStatusException.class, () -> svc.abrir("f1", principal(financeiro))).getStatusCode().value());
        assertEquals(404, assertThrows(ResponseStatusException.class, () -> svc.abrir("f1", principal(moderador))).getStatusCode().value());
        when(denuncias.existsByPedidoIdAndStatus("p1", "em_analise")).thenReturn(true);
        assertNotNull(svc.abrir("f1", principal(moderador)));
        assertNotNull(svc.abrir("f1", principal(ana)));
        assertEquals(404, assertThrows(ResponseStatusException.class, () -> svc.abrir("f1", principal(adm))).getStatusCode().value());
    }

    @Test void filaDeRecursosTrazOsAvisosComOPedido() {
        when(usuarios.findById(5L)).thenReturn(Optional.of(usuario(5L, "pessoa5@taskgo.com")));
        when(recursos.findByStatusOrderByCriadoEmAsc(eq("em_analise"), any())).thenReturn(List.of(new Recurso("r1", 5L, "p1", "Foi um número, não telefone.", 1000L)));
        when(historico.findByUsuarioIdOrderByCriadoEmDesc(5L)).thenReturn(List.of(new HistoricoConta(5L, "aviso", "Recebeu o aviso 1 de 3 (mensagem com números)", null, "p1", 900L)));
        ModeracaoController c = controller(moderacao);
        Map<String, Object> aviso = c.recursosAdmin(moderador).get(0).avisos().get(0);
        assertEquals("p1", aviso.get("pedidoId"));
        assertEquals("p1", c.recursosAdmin(moderador).get(0).pedidoId());
        assertEquals("Recebeu o aviso 1 de 3 (mensagem com números)", aviso.get("texto"));
    }

    @Test void tresAvisosBanemRecursoAprovadoPeloModeradorDesbaneEFicaRegistrado() {
        ModeracaoService real = new ModeracaoService(usuarios, "adm@taskgo.com");
        real.setHistorico(historico); real.setRecursos(recursos); real.setDenuncias(denuncias);
        Usuario u = usuario(5L, "pessoa5@taskgo.com");
        when(usuarios.findById(5L)).thenReturn(Optional.of(u));
        real.registrarInfracao(5L, "números", "p1"); real.registrarInfracao(5L, "números", "p1"); real.registrarInfracao(5L, "números", "p1");
        assertTrue(real.situacao(u).banido());
        ModeracaoController c = controller(real);
        var sua = token(5L, "pessoa5@taskgo.com", "cliente");
        when(recursos.saveAndFlush(any(Recurso.class))).thenAnswer(inv -> inv.getArgument(0));
        ModeracaoController.RecursoDto enviado = c.recorrer(Map.of("texto", "Escrevi o número do apartamento, não um telefone."), sua);
        assertEquals("em_analise", enviado.status());
        ArgumentCaptor<Recurso> salvo = ArgumentCaptor.forClass(Recurso.class);
        verify(recursos).saveAndFlush(salvo.capture());
        when(recursos.findById(enviado.id())).thenReturn(Optional.of(salvo.getValue()));
        c.decidir(enviado.id(), "aprovar", Map.of(), moderador);
        assertFalse(real.situacao(u).banido());
        assertEquals(2, real.situacao(u).avisos());
        ArgumentCaptor<HistoricoConta> hist = ArgumentCaptor.forClass(HistoricoConta.class);
        verify(historico, atLeastOnce()).save(hist.capture());
        assertTrue(hist.getAllValues().stream().anyMatch(h -> "recurso".equals(h.getTipo()) && h.getTexto().startsWith("Recurso aprovado na revisão por pessoa da equipe")
                && Long.valueOf(7L).equals(h.getAutorId())));
    }

    private ModeracaoController controller(ModeracaoService m) {
        ModeracaoController c = new ModeracaoController(m, recursos, usuarios, mock(NotificacaoService.class), mock(EventosService.class));
        c.setHistorico(historico);
        return c;
    }

    private static Usuario usuario(Long id, String email) {
        Usuario u = new Usuario(); u.setId(id); u.setEmail(email); u.setNome("Pessoa " + id); u.setTipo("cliente"); return u;
    }
    /* Regressão (revisor): a rota era @Transactional(readOnly = true); o Hibernate não grava em transação só de leitura
       (o id do histórico é gerado no código, então o INSERT fica para o fim), e o registro "acesso" sumia sem erro. */
    @Test void aberturaDaConversaRodaEmTransacaoQueGrava() throws Exception {
        var m = AdminController.class.getMethod("conversaDoPedido", String.class, org.springframework.security.core.Authentication.class);
        var t = m.getAnnotation(org.springframework.transaction.annotation.Transactional.class);
        assertNotNull(t, "precisa de transação para gravar o registro de acesso junto com a leitura");
        assertFalse(t.readOnly(), "readOnly descarta o registro de acesso");
    }
}
