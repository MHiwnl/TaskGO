package com.taskgo.api;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.server.ResponseStatusException;

/* Orçamento comparável: prazo, garantia e material do profissional, e o combinado gravado no aceite. */
class PropostaComparavelTest {

    private static UsernamePasswordAuthenticationToken logado(long id, String email, String tipo) {
        return new UsernamePasswordAuthenticationToken(new TaskgoPrincipal(id, email, "Nome", tipo, null), null, List.of());
    }

    private static Usuario profissional() {
        Usuario pro = new Usuario(); pro.setId(2L); pro.setNome("Pro"); pro.setEmail("pro@x.com"); pro.setTipo("profissional"); pro.setProId("pro_2"); pro.setService("eletrica");
        return pro;
    }

    private static Pedido pedidoAberto(String id) {
        Pedido p = new Pedido(); p.setId(id); p.setStatus("aguardando"); p.setClientEmail("cli@x.com"); p.setService("eletrica"); p.setPrice(200.0);
        return p;
    }

    private static PropostaController controller(PropostaRepository propostas, PedidoContratadoRepository pedidos, UsuarioRepository usuarios) {
        return new PropostaController(propostas, pedidos, usuarios, mock(NotificacaoService.class), mock(ModeracaoService.class), mock(EventosService.class));
    }

    @Test void propostaDoProfissionalSemPrazoEhRecusada() {
        PedidoContratadoRepository pedidos = mock(PedidoContratadoRepository.class);
        when(pedidos.findById("o_1")).thenReturn(Optional.of(pedidoAberto("o_1")));
        PropostaController c = controller(mock(PropostaRepository.class), pedidos, mock(UsuarioRepository.class));
        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> c.enviar("o_1", new PropostaController.NovaProposta(150.0, null, null, 30, true), logado(2L, "pro@x.com", "profissional")));
        assertEquals(400, e.getStatusCode().value());
    }

    @Test void garantiaAcimaDeUmAnoEhRecusada() {
        PedidoContratadoRepository pedidos = mock(PedidoContratadoRepository.class);
        when(pedidos.findById("o_1")).thenReturn(Optional.of(pedidoAberto("o_1")));
        PropostaController c = controller(mock(PropostaRepository.class), pedidos, mock(UsuarioRepository.class));
        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> c.enviar("o_1", new PropostaController.NovaProposta(150.0, null, 3, 366, true), logado(2L, "pro@x.com", "profissional")));
        assertEquals(400, e.getStatusCode().value());
        assertEquals("Informe a garantia de 0 a 365 dias.", e.getReason());
    }

    @Test void contrapropostaDoClienteNaoPrecisaDeTermos() {
        PropostaRepository propostas = mock(PropostaRepository.class);
        UsuarioRepository usuarios = mock(UsuarioRepository.class);
        Usuario pro = profissional();
        PedidoContratadoRepository pedidos = mock(PedidoContratadoRepository.class);
        when(pedidos.findById("o_3")).thenReturn(Optional.of(pedidoAberto("o_3")));
        when(propostas.findByPedidoIdAndProfissionalIdOrderByCriadaEmAsc("o_3", 2L))
                .thenReturn(List.of(new Proposta("p0", "o_3", pro, "profissional", 150.0, 1L, 3, 30, true)));
        when(usuarios.findById(2L)).thenReturn(Optional.of(pro));
        when(propostas.save(any(Proposta.class))).thenAnswer(i -> i.getArgument(0));

        PropostaController.PropostaDto dto = controller(propostas, pedidos, usuarios)
                .enviar("o_3", new PropostaController.NovaProposta(140.0, 2L), logado(1L, "cli@x.com", "cliente"));
        assertEquals("cliente", dto.autor());
        assertNull(dto.prazoDias());
        assertNull(dto.garantiaDias());
        assertNull(dto.materialIncluso());
    }

    /* Distância e serviços feitos só para o cliente dono do pedido; o profissional vê só a própria proposta. */
    @Test void distanciaEConcluidosSoParaODonoDoPedido() {
        PropostaRepository propostas = mock(PropostaRepository.class);
        PedidoContratadoRepository pedidos = mock(PedidoContratadoRepository.class);
        UsuarioRepository usuarios = mock(UsuarioRepository.class);
        PresencaProfissionalRepository presencas = mock(PresencaProfissionalRepository.class);
        RankingProfissionais ranking = mock(RankingProfissionais.class);
        Usuario pro = profissional();
        /* A distância sai do ponto de partida (a posição ao vivo da presença é ignorada de propósito). */
        pro.addExtra(AreaAtendimentoController.CHAVE, Map.of("raioKm", 20, "baseLat", -8.06, "baseLng", -34.9));
        Pedido pedido = pedidoAberto("o_5"); pedido.addExtra("lat", -8.05); pedido.addExtra("lng", -34.9);
        Proposta x = new Proposta("p5", "o_5", pro, "profissional", 180.0, 1L, 5, 90, true);
        when(pedidos.findById("o_5")).thenReturn(Optional.of(pedido));
        when(propostas.findByPedidoIdOrderByCriadaEmAsc("o_5")).thenReturn(List.of(x));
        when(propostas.findByPedidoIdAndProfissionalIdOrderByCriadaEmAsc("o_5", 2L)).thenReturn(List.of(x));
        when(usuarios.findAllById(any())).thenReturn(List.of(pro));
        when(presencas.findById(2L)).thenReturn(Optional.of(new PresencaProfissional(2L, -8.06, -34.9, 10, System.currentTimeMillis())));
        when(ranking.metricas(any())).thenReturn(Map.of(2L, new RankingProfissionais.Metricas(7, 0, null, 0.9, 0, null)));
        PropostaController c = controller(propostas, pedidos, usuarios);
        c.setRanking(ranking);
        c.setPresencas(presencas);

        PropostaController.PropostaDto doDono = c.listar("o_5", logado(1L, "cli@x.com", "cliente")).get(0);
        assertEquals(1.0, doDono.km().doubleValue(), 0.001);
        assertEquals(7, doDono.concluidos());
        verify(ranking, times(1)).metricas(any());

        PropostaController.PropostaDto doPro = c.listar("o_5", logado(2L, "pro@x.com", "profissional")).get(0);
        assertNull(doPro.km());
        assertNull(doPro.concluidos());
        verify(ranking, times(1)).metricas(any());
    }

    @Test void aceiteGravaOCombinadoComOsTermosDaUltimaPropostaDoProfissional() {
        PropostaRepository propostas = mock(PropostaRepository.class);
        PedidoContratadoRepository pedidos = mock(PedidoContratadoRepository.class);
        UsuarioRepository usuarios = mock(UsuarioRepository.class);
        Usuario pro = profissional();
        Proposta x = new Proposta("p1", "o_4", pro, "profissional", 180.0, 1L, 5, 90, true);
        Pedido aberto = pedidoAberto("o_4");
        when(propostas.findById("p1")).thenReturn(Optional.of(x));
        when(propostas.findByPedidoIdAndProfissionalIdOrderByCriadaEmAsc("o_4", 2L)).thenReturn(List.of(x));
        when(propostas.findByPedidoIdAndStatus("o_4", Proposta.PENDENTE)).thenReturn(List.of());
        when(pedidos.findById("o_4")).thenReturn(Optional.of(aberto));
        when(pedidos.aceitarSeDisponivel(eq("o_4"), anyString(), any(), any(), anyLong())).thenReturn(1);
        when(usuarios.findById(2L)).thenReturn(Optional.of(pro));

        Pedido fechado = controller(propostas, pedidos, usuarios).aceitar("o_4", "p1", logado(1L, "cli@x.com", "cliente"));

        assertEquals(java.util.Map.of("prazoDias", 5, "garantiaDias", 90, "materialIncluso", true), fechado.getExtra().get("combinado"));
        verify(pedidos).save(aberto);
    }
}
