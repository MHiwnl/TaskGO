package com.taskgo.api;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class LocalizacaoPedidoServiceTest {
    private static final long NOW=1_800_000_000_000L;
    private PedidoContratadoRepository pedidos;
    private LocalizacaoPedidoRepository locais;
    private LocalizacaoPedidoService service;
    private Pedido order;
    private final TaskgoPrincipal pro=new TaskgoPrincipal(2L,"pro@taskgo.com","Pro","profissional","pro-2");
    private final TaskgoPrincipal cliente=new TaskgoPrincipal(1L,"cliente@taskgo.com","Cliente","cliente",null);

    @BeforeEach void setup(){
        pedidos=mock(PedidoContratadoRepository.class); locais=mock(LocalizacaoPedidoRepository.class);
        service=new LocalizacaoPedidoService(pedidos,locais,Clock.fixed(Instant.ofEpochMilli(NOW), ZoneOffset.UTC));
        order=new Pedido();order.setId("pedido-1");order.setProEmail("pro@taskgo.com");order.setClientEmail("cliente@taskgo.com");order.setStatus("a_caminho");
        when(pedidos.findById("pedido-1")).thenReturn(Optional.of(order));
    }
    @Test void profissionalDoPedidoPersisteSomenteUltimaPosicao(){
        var request=new LocalizacaoPedidoService.LocalizacaoRequest(-23.5,-46.6,12.5,NOW);
        when(locais.findById("pedido-1")).thenReturn(Optional.empty());
        when(locais.save(any())).thenAnswer(inv->inv.getArgument(0));
        var result=service.registrar("pedido-1",pro,request);
        assertEquals(-23.5,result.latitude());assertEquals(-46.6,result.longitude());assertEquals(12.5,result.accuracy());assertEquals(NOW,result.receivedAt());
        verify(locais).save(any(LocalizacaoPedido.class));
    }
    @Test void chegadaSoValePertoDoPontoMarcado(){
        order.addExtra("lat",-8.0476);order.addExtra("lng",-34.8770);
        when(locais.findById("pedido-1")).thenReturn(Optional.empty());
        assertEquals(409,assertThrows(ResponseStatusException.class,()->service.exigirPertoDoEndereco(order)).getStatusCode().value());
        /* ~1,1 km de distância */
        when(locais.findById("pedido-1")).thenReturn(Optional.of(new LocalizacaoPedido("pedido-1",-8.0576,-34.8770,10,NOW,NOW)));
        assertTrue(assertThrows(ResponseStatusException.class,()->service.exigirPertoDoEndereco(order)).getReason().contains("1112 m"));
        /* posição velha */
        when(locais.findById("pedido-1")).thenReturn(Optional.of(new LocalizacaoPedido("pedido-1",-8.0476,-34.8770,10,NOW-300_000,NOW-300_000)));
        assertThrows(ResponseStatusException.class,()->service.exigirPertoDoEndereco(order));
        /* imprecisa, mas a margem alcança o endereço: aceita com observação */
        when(locais.findById("pedido-1")).thenReturn(Optional.of(new LocalizacaoPedido("pedido-1",-8.0576,-34.8770,4700,NOW,NOW)));
        assertNotNull(service.exigirPertoDoEndereco(order));
        /* imprecisa e longe demais mesmo com a margem */
        when(locais.findById("pedido-1")).thenReturn(Optional.of(new LocalizacaoPedido("pedido-1",-8.2476,-34.8770,500,NOW,NOW)));
        assertThrows(ResponseStatusException.class,()->service.exigirPertoDoEndereco(order));
        /* a ~50 m, recente e precisa: passa */
        when(locais.findById("pedido-1")).thenReturn(Optional.of(new LocalizacaoPedido("pedido-1",-8.04805,-34.8770,15,NOW,NOW)));
        assertDoesNotThrow(()->service.exigirPertoDoEndereco(order));
    }
    @Test void pedidoSemPontoMarcadoNaoExigeGps(){ assertDoesNotThrow(()->service.exigirPertoDoEndereco(order)); }
    @Test void participantePodeLerMasTerceiroNao(){
        when(locais.findById("pedido-1")).thenReturn(Optional.of(new LocalizacaoPedido("pedido-1",1,2,3,NOW,NOW)));
        assertTrue(service.obter("pedido-1",cliente).isPresent());
        var stranger=new TaskgoPrincipal(3L,"outro@taskgo.com","Outro","cliente",null);
        assertEquals(403,assertThrows(ResponseStatusException.class,()->service.obter("pedido-1",stranger)).getStatusCode().value());
    }
    @Test void rejeitaLocalizacaoInvalidaOuPedidoForaDaEtapaDeRastreamento(){
        assertEquals(400,assertThrows(ResponseStatusException.class,()->service.registrar("pedido-1",pro,new LocalizacaoPedidoService.LocalizacaoRequest(91,2,3,NOW))).getStatusCode().value());
        order.setStatus("chegou");
        assertEquals(409,assertThrows(ResponseStatusException.class,()->service.registrar("pedido-1",pro,new LocalizacaoPedidoService.LocalizacaoRequest(1,2,3,NOW))).getStatusCode().value());
        verify(locais,never()).save(any());
    }
    @Test void outroProfissionalNaoPodeEnviarLocalizacao(){
        var outro=new TaskgoPrincipal(4L,"intruso@taskgo.com","Outro","profissional","pro-4");
        assertEquals(403,assertThrows(ResponseStatusException.class,()->service.registrar("pedido-1",outro,new LocalizacaoPedidoService.LocalizacaoRequest(1,2,3,NOW))).getStatusCode().value());
    }
    @Test void encerraApagandoLocalizacaoDepoisDoStatus(){
        order.setStatus("concluido");service.limparSeEncerrado(order);verify(locais).deleteById("pedido-1");
    }
}
