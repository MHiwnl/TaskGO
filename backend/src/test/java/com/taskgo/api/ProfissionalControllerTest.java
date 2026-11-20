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
        when(orders.resumoAvaliacoesDe(List.of("joao@privado.test"))).thenReturn(List.of());
        var result=new ProfissionalController(users,ranking(orders)).listar("eletrica");
        assertEquals(1,result.size());assertEquals("pro-real-9",result.get(0).id());assertNull(result.get(0).rating());assertEquals(0,result.get(0).reviews());
        verify(users).profissionaisDoServico("eletrica");
    }
    /* As notas de todos vêm numa consulta só, ligadas pelo e-mail sem diferenciar maiúsculas. */
    @Test void notasVemNumaConsultaSoPeloEmail(){
        UsuarioRepository users=mock(UsuarioRepository.class);PedidoContratadoRepository orders=mock(PedidoContratadoRepository.class);
        Usuario pro=new Usuario();pro.setId(10L);pro.setProId("pro-real-10");pro.setTipo("profissional");pro.setNome("Maria");pro.setEmail("Maria@Privado.test");pro.setService("eletrica");pro.setCidade("Recife");
        when(users.findByTipoIgnoreCaseOrderByNomeAsc("profissional")).thenReturn(List.of(pro));
        when(orders.resumoAvaliacoesDe(List.of("maria@privado.test"))).thenReturn(java.util.Collections.singletonList(new Object[]{"maria@privado.test",4.5d,2L}));
        var result=new ProfissionalController(users,ranking(orders)).listar(null);
        assertEquals(1,result.size());assertEquals(4.5d,result.get(0).rating());assertEquals(2,result.get(0).reviews());
        verify(orders,never()).resumoAvaliacoes(anyString());
    }
    @Test void categoriaVaziaNaoCriaProfissionaisLocais(){
        UsuarioRepository users=mock(UsuarioRepository.class);PedidoContratadoRepository orders=mock(PedidoContratadoRepository.class);
        when(users.profissionaisDoServico("ar")).thenReturn(List.of());
        assertTrue(new ProfissionalController(users,ranking(orders)).listar("ar").isEmpty());verifyNoInteractions(orders);
    }
    /* Pro continua primeiro mesmo cancelando; dentro dos outros, quem cancela desce mesmo com o nome antes. */
    @Test void proFicaPrimeiroEQualidadeOrdenaDentroDoGrupo(){
        UsuarioRepository users=mock(UsuarioRepository.class);PedidoContratadoRepository orders=mock(PedidoContratadoRepository.class);HistoricoContaRepository historico=mock(HistoricoContaRepository.class);
        when(users.profissionaisDoServico("eletrica")).thenReturn(List.of(profissional(11L,"Ana"),profissional(12L,"Zeca"),profissional(13L,"Bia")));
        when(historico.cancelamentosDe(anyCollection(), anyLong())).thenReturn(List.<Object[]>of(new Object[]{11L,5L}, new Object[]{13L,5L}));
        PlanoService planos=mock(PlanoService.class);when(planos.pros(anyCollection())).thenReturn(java.util.Set.of(13L));
        ProfissionalController c=new ProfissionalController(users,new RankingProfissionais(orders,historico));c.setPlanos(planos);
        assertEquals(List.of("pro-real-13","pro-real-12","pro-real-11"), c.listar("eletrica").stream().map(ProfissionalPublicoDto::id).toList());
    }
    static Usuario profissional(long id,String nome){Usuario u=new Usuario();u.setId(id);u.setProId("pro-real-"+id);u.setTipo("profissional");u.setNome(nome);u.setEmail(nome.toLowerCase()+"@privado.test");u.setService("eletrica");u.setCidade("Recife");return u;}
    static RankingProfissionais ranking(PedidoContratadoRepository orders){return new RankingProfissionais(orders,mock(HistoricoContaRepository.class));}
}
