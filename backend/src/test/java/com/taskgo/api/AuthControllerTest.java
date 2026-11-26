package com.taskgo.api;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Map;
import java.util.List;
import java.util.Optional;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

class AuthControllerTest {
    private UsuarioRepository usuarios;
    private PedidoRepository estimativas;
    private PedidoContratadoRepository pedidos;
    private MensagemRepository mensagens;
    private NotificacaoRepository notificacoes;
    private AuthController controller;
    private final PasswordEncoder encoder = new BCryptPasswordEncoder();

    @BeforeEach void setup() {
        usuarios=mock(UsuarioRepository.class); estimativas=mock(PedidoRepository.class); pedidos=mock(PedidoContratadoRepository.class); mensagens=mock(MensagemRepository.class);notificacoes=mock(NotificacaoRepository.class);
        controller=new AuthController(usuarios,estimativas,pedidos,mensagens,notificacoes,encoder,new LoginAttemptLimiter());
    }

    @AfterEach void limparContextoDeSeguranca(){org.springframework.security.core.context.SecurityContextHolder.clearContext();}

    @Test void cadastroArmazenaSomenteHashEAbreSessaoSemExporHash() {
        Usuario novo=new Usuario(); novo.setNome("Ana"); novo.setEmail(" ANA@TASKGO.COM "); novo.setTipo("cliente"); novo.setSenha("Tarefa#Forte2026"); novo.addExtra("maiorDeIdade", true);
        novo.setService("marido");
        when(usuarios.existsByEmailIgnoreCase("ana@taskgo.com")).thenReturn(false);
        when(usuarios.save(novo)).thenAnswer(inv->{Usuario u=inv.getArgument(0);u.setId(9L);return u;});
        var response=controller.registrar(novo,new MockHttpServletRequest());
        assertEquals(9L,response.id()); assertEquals("ana@taskgo.com",response.email());
        assertFalse(response.toString().contains("senha"));
        assertNotEquals("Tarefa#Forte2026",novo.getSenha()); assertTrue(encoder.matches("Tarefa#Forte2026",novo.getSenha()));
        assertNull(novo.getService());
        assertNotNull(org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication());
    }

    @Test void cadastroIgnoraExtraDoCliente_naoDeixaAutoVerificar() {
        // @JsonAnySetter joga qualquer campo do corpo no extra; o cadastro não pode aceitar verificado/cnpj do cliente.
        Usuario novo=new Usuario(); novo.setNome("Atacante"); novo.setEmail("atk@taskgo.com"); novo.setTipo("profissional"); novo.setService("eletrica"); novo.setSenha("Tarefa#Forte2026"); novo.addExtra("maiorDeIdade", true);
        novo.addExtra("verificado", true); novo.addExtra("cnpj", "11222333000181"); novo.addExtra("saldo", 99999);
        when(usuarios.existsByEmailIgnoreCase("atk@taskgo.com")).thenReturn(false);
        when(usuarios.save(novo)).thenAnswer(inv->{Usuario u=inv.getArgument(0);u.setId(10L);return u;});
        controller.registrar(novo,new MockHttpServletRequest());
        assertFalse(novo.getExtra().containsKey("verificado"), "conta nova não nasce verificada");
        assertFalse(novo.getExtra().containsKey("cnpj"), "cadastro não aceita CNPJ do cliente");
        assertFalse(novo.getExtra().containsKey("saldo"));
    }

    @Test void perfilIgnoraExtraDoCliente_naoDeixaAutoVerificar() {
        Usuario atual=new Usuario(); atual.setId(11L); atual.setEmail("u@taskgo.com"); atual.setTipo("profissional"); atual.setNome("User");
        when(usuarios.findById(11L)).thenReturn(java.util.Optional.of(atual));
        when(usuarios.save(atual)).thenReturn(atual);
        var auth=new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
            new TaskgoPrincipal(11L,"u@taskgo.com","User","profissional","pro_x"),null,java.util.List.of());
        Usuario dados=new Usuario(); dados.setNome("User"); dados.addExtra("verificado", true); dados.addExtra("cnpj", "11222333000181");
        controller.atualizarPerfil(dados,auth);
        assertFalse(atual.getExtra().containsKey("verificado"), "perfil não deixa o cliente se auto-verificar");
        assertFalse(atual.getExtra().containsKey("cnpj"));
    }

    /* Regressão (revisão de 25/11): conta verificada trocava a data de nascimento pelo perfil e contornava a trava de 18 anos. */
    @Test void perfilVerificadoNaoTrocaADataDeNascimento() {
        Usuario atual=new Usuario(); atual.setId(12L); atual.setEmail("v@taskgo.com"); atual.setTipo("cliente"); atual.setNome("Vera");
        atual.setBirth("2009-05-10"); atual.addExtra("verificado", true);
        when(usuarios.findById(12L)).thenReturn(java.util.Optional.of(atual));
        when(usuarios.save(atual)).thenReturn(atual);
        var auth=new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
            new TaskgoPrincipal(12L,"v@taskgo.com","Vera","cliente",null),null,java.util.List.of());
        Usuario outra=new Usuario(); outra.setNome("Vera"); outra.setBirth("2000-01-01"); outra.setTelefone("81999990000");
        controller.atualizarPerfil(outra,auth);
        assertEquals("2009-05-10",atual.getBirth(),"a data do documento não muda pelo perfil");
        assertEquals("81999990000",atual.getTelefone(),"o resto do perfil salva mesmo com a data antiga vinda da tela");
        Usuario vazia=new Usuario(); vazia.setNome("Vera"); vazia.setBirth("");
        controller.atualizarPerfil(vazia,auth);
        assertEquals("2009-05-10",atual.getBirth());
    }

    @Test void perfilNaoVerificadoAindaAcertaADataDeNascimento() {
        Usuario atual=new Usuario(); atual.setId(13L); atual.setEmail("n@taskgo.com"); atual.setTipo("cliente"); atual.setNome("Nina");
        when(usuarios.findById(13L)).thenReturn(java.util.Optional.of(atual));
        when(usuarios.save(atual)).thenReturn(atual);
        var auth=new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
            new TaskgoPrincipal(13L,"n@taskgo.com","Nina","cliente",null),null,java.util.List.of());
        Usuario dados=new Usuario(); dados.setNome("Nina"); dados.setBirth("1990-03-02");
        controller.atualizarPerfil(dados,auth);
        assertEquals("1990-03-02",atual.getBirth());
    }

    @Test void emailDeAdministradorViraContaDeAdministrador() {
        controller.setModeracao(new ModeracaoService(usuarios,"adm@taskgo.com"));
        Usuario novo=new Usuario(); novo.setNome("Adm"); novo.setEmail("ADM@taskgo.com"); novo.setTipo("profissional"); novo.setService("eletrica"); novo.setSenha("Tarefa#Forte2026"); novo.addExtra("maiorDeIdade", true);
        when(usuarios.save(novo)).thenAnswer(inv->{Usuario u=inv.getArgument(0);u.setId(5L);return u;});
        controller.registrar(novo,new MockHttpServletRequest());
        assertEquals("administrador",novo.getTipo()); assertNull(novo.getProId()); assertNull(novo.getService());
        /* conta antiga de cliente com o e-mail de administrador vira administrador ao entrar */
        Usuario antigo=new Usuario(); antigo.setId(6L); antigo.setEmail("adm@taskgo.com"); antigo.setNome("Adm"); antigo.setTipo("cliente"); antigo.setSenha(encoder.encode("Tarefa#Forte2026"));
        when(usuarios.findByEmailIgnoreCase("adm@taskgo.com")).thenReturn(Optional.of(antigo));
        Usuario login=new Usuario(); login.setEmail("adm@taskgo.com"); login.setSenha("Tarefa#Forte2026");
        assertEquals(200,controller.login(login,new MockHttpServletRequest()).getStatusCode().value());
        assertEquals("administrador",antigo.getTipo());
    }

    @Test void loginValidaCredencialERejeitaSenhaIncorreta() {
        Usuario existente=new Usuario(); existente.setId(1L); existente.setEmail("a@b.com"); existente.setNome("Ana"); existente.setTipo("cliente"); existente.setSenha(encoder.encode("Tarefa#Forte2026"));
        when(usuarios.findByEmailIgnoreCase("A@B.COM")).thenReturn(Optional.of(existente));
        Usuario login=new Usuario(); login.setEmail("A@B.COM"); login.setSenha("Tarefa#Forte2026");
        var ok=controller.login(login,new MockHttpServletRequest());
        assertEquals(200,ok.getStatusCode().value());
        assertFalse(ok.getBody().toString().contains(existente.getSenha()));
        login.setSenha("incorreta");
        assertEquals(401,controller.login(login,new MockHttpServletRequest()).getStatusCode().value());
    }

    @Test void loginAplicaLimiteDeTentativasPorEndereco() {
        when(usuarios.findByEmailIgnoreCase(org.mockito.ArgumentMatchers.anyString())).thenReturn(Optional.empty());
        for(int i=0;i<10;i++){ Usuario login=new Usuario(); login.setEmail("ausente"+i+"@taskgo.com"); login.setSenha("Tarefa#Forte2026");
            assertEquals(401,controller.login(login,new MockHttpServletRequest()).getStatusCode().value()); }
        Usuario outro=new Usuario(); outro.setEmail("mais-um@taskgo.com"); outro.setSenha("Tarefa#Forte2026");
        assertEquals(429,controller.login(outro,new MockHttpServletRequest()).getStatusCode().value());
    }

    @Test void loginBloqueiaAContaDepoisDeTresSenhasErradas() {
        Usuario login=new Usuario(); login.setEmail("alvo@taskgo.com"); login.setSenha("Errada#2026xx");
        when(usuarios.findByEmailIgnoreCase("alvo@taskgo.com")).thenReturn(Optional.empty());
        for(int i=0;i<3;i++){ MockHttpServletRequest r=new MockHttpServletRequest(); r.setRemoteAddr("10.0.0."+i);
            assertEquals(401,controller.login(login,r).getStatusCode().value()); }
        MockHttpServletRequest deOutroIp=new MockHttpServletRequest(); deOutroIp.setRemoteAddr("10.9.9.9");
        var bloqueado=controller.login(login,deOutroIp);
        assertEquals(429,bloqueado.getStatusCode().value());
        assertTrue(bloqueado.getBody().toString().contains("3 senhas erradas"));
    }

    @Test void perfilEDadosUsamSomenteUsuarioDaSessao() {
        Usuario dono=new Usuario(); dono.setId(7L); dono.setEmail("dono@taskgo.com"); dono.setTipo("cliente");
        when(usuarios.findById(7L)).thenReturn(Optional.of(dono)); when(usuarios.save(dono)).thenReturn(dono);
        var p=new TaskgoPrincipal(7L,dono.getEmail(),"Dono","cliente",null);
        var auth=UsernamePasswordAuthenticationToken.authenticated(p,null,p.getAuthorities());
        Usuario update=new Usuario(); update.setEmail("vitima@taskgo.com"); update.setNome("Nome atualizado"); update.setTipo("administrador"); update.setProId("pro-de-outra-conta"); update.setBirth("1998-04-21");
        controller.atualizarPerfil(update,auth);
        assertEquals("dono@taskgo.com",dono.getEmail()); assertEquals("cliente",dono.getTipo()); assertNull(dono.getProId());
        assertEquals("1998-04-21",dono.getBirth());
        controller.salvarDados(new AuthController.DadosContaRequest(Map.of("notifications",List.of())),auth);
        assertEquals(Map.of(),controller.obterDados(auth));
        verify(usuarios,never()).findByEmailIgnoreCase("vitima@taskgo.com");
    }

    @Test void atualizarSenhaExigeSenhaAtual() {
        Usuario dono=new Usuario(); dono.setId(2L); dono.setEmail("dono@taskgo.com"); dono.setSenha(encoder.encode("antiga123"));
        when(usuarios.findById(2L)).thenReturn(Optional.of(dono)); when(usuarios.save(dono)).thenReturn(dono);
        var p=new TaskgoPrincipal(2L,dono.getEmail(),"Dono","cliente",null);
        var auth=UsernamePasswordAuthenticationToken.authenticated(p,null,p.getAuthorities());
        assertThrows(ResponseStatusException.class,()->controller.atualizarSenha(new AuthController.AlterarSenhaRequest("errada123","NovaChave#2026x"),auth));
        controller.atualizarSenha(new AuthController.AlterarSenhaRequest("antiga123","NovaChave#2026x"),auth);
        assertTrue(encoder.matches("NovaChave#2026x",dono.getSenha()));
        assertEquals(1,dono.getCredentialVersion());
    }

    @Test void excluirContaApagaMensagensDosPedidosAssociados() {
        Usuario dono=new Usuario();dono.setId(3L);dono.setEmail("dono@taskgo.com");dono.setNome("Dono");dono.setTipo("cliente");dono.setSenha(encoder.encode("Tarefa#Forte2026"));
        Pedido pedido=new Pedido();pedido.setId("pedido-1");pedido.setClientEmail(dono.getEmail());
        when(usuarios.findById(3L)).thenReturn(Optional.of(dono));
        when(pedidos.findByClientEmailIgnoreCaseOrProEmailIgnoreCaseOrderByCreatedDesc(dono.getEmail(),dono.getEmail())).thenReturn(List.of(pedido));
        var principal=new TaskgoPrincipal(3L,dono.getEmail(),dono.getNome(),"cliente",null);
        var auth=UsernamePasswordAuthenticationToken.authenticated(principal,null,principal.getAuthorities());
        controller.excluirConta(new AuthController.ExcluirContaRequest("Tarefa#Forte2026"),auth,new MockHttpServletRequest());
        verify(mensagens).deleteByPedidoIdIn(List.of("pedido-1"));
        verify(pedidos).delete(pedido);
        verify(usuarios).delete(dono);
    }

    @Test void excluirContaNaoApagaPedidoDoOutroLado() {
        Usuario dono=new Usuario();dono.setId(4L);dono.setEmail("pro@taskgo.com");dono.setNome("Pro");dono.setTipo("profissional");dono.setSenha(encoder.encode("Tarefa#Forte2026"));
        Pedido pedido=new Pedido();pedido.setId("pedido-2");pedido.setClientEmail("cliente@taskgo.com");pedido.setProEmail(dono.getEmail());pedido.setProfessionalName("Pro");
        when(usuarios.findById(4L)).thenReturn(Optional.of(dono));
        when(pedidos.findByClientEmailIgnoreCaseOrProEmailIgnoreCaseOrderByCreatedDesc(dono.getEmail(),dono.getEmail())).thenReturn(List.of(pedido));
        var principal=new TaskgoPrincipal(4L,dono.getEmail(),dono.getNome(),"profissional",null);
        controller.excluirConta(new AuthController.ExcluirContaRequest("Tarefa#Forte2026"),UsernamePasswordAuthenticationToken.authenticated(principal,null,principal.getAuthorities()),new MockHttpServletRequest());
        verify(pedidos,never()).delete(pedido);
        verify(mensagens,never()).deleteByPedidoIdIn(org.mockito.ArgumentMatchers.anyList());
        assertEquals("Conta excluída",pedido.getProfessionalName());
        assertNotEquals(dono.getEmail(),pedido.getProEmail());
        verify(usuarios).delete(dono);
    }
}
