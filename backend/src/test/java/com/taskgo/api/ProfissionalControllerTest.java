package com.taskgo.api;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;
import org.junit.jupiter.api.Test;

class ProfissionalControllerTest {
    @Test void listaApenasPerfisProfissionaisPersistidosSemInformacaoPrivadaOuInventada(){
        UsuarioRepository users=mock(UsuarioRepository.class);PedidoContratadoRepository orders=mock(PedidoContratadoRepository.class);
        Usuario pro=new Usuario();pro.setId(9L);pro.setProId("pro-real-9");pro.setTipo("profissional");pro.setNome("João");pro.setEmail("joao@privado.test");pro.setService("eletrica");pro.setCidade("Recife");
        when(users.profissionaisDoServico("eletrica")).thenReturn(List.of(pro));
        when(orders.resumoAvaliacoes("joao@privado.test")).thenReturn(new Object[]{null,0L});
        var result=new ProfissionalController(users,orders).listar("eletrica");
        assertEquals(1,result.size());assertEquals("pro-real-9",result.get(0).id());assertNull(result.get(0).rating());assertEquals(0,result.get(0).reviews());
        verify(users).profissionaisDoServico("eletrica");
    }
    /* O Spring Data devolve a consulta de uma linha como [[média, total]]; antes isso dava HTTP 500. */
    @Test void aceitaResumoDeAvaliacoesEmbrulhadoPeloSpringData(){
        UsuarioRepository users=mock(UsuarioRepository.class);PedidoContratadoRepository orders=mock(PedidoContratadoRepository.class);
        Usuario pro=new Usuario();pro.setId(10L);pro.setProId("pro-real-10");pro.setTipo("profissional");pro.setNome("Maria");pro.setEmail("maria@privado.test");pro.setService("eletrica");pro.setCidade("Recife");
        when(users.findByTipoIgnoreCaseOrderByNomeAsc("profissional")).thenReturn(List.of(pro));
        when(orders.resumoAvaliacoes("maria@privado.test")).thenReturn(new Object[]{new Object[]{4.5d,2L}});
        var result=new ProfissionalController(users,orders).listar(null);
        assertEquals(1,result.size());assertEquals(4.5d,result.get(0).rating());assertEquals(2,result.get(0).reviews());
    }
    @Test void categoriaVaziaNaoCriaProfissionaisLocais(){
        UsuarioRepository users=mock(UsuarioRepository.class);PedidoContratadoRepository orders=mock(PedidoContratadoRepository.class);
        when(users.profissionaisDoServico("ar")).thenReturn(List.of());
        assertTrue(new ProfissionalController(users,orders).listar("ar").isEmpty());verifyNoInteractions(orders);
    }
}
