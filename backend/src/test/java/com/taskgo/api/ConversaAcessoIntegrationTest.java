package com.taskgo.api;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/* Controller, transação do Spring e banco reais: abrir a conversa precisa GRAVAR um registro "acesso" por participante.
   Regressão (revisor): com @Transactional(readOnly = true) na rota, o Hibernate não gravava e o registro sumia sem erro;
   os testes com mocks não pegavam porque o registrar era simulado. */
@DataJpaTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:taskgo-conversa;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false"
})
@Import({AdminController.class, ModeracaoService.class})
/* Sem transação no teste: a do controller precisa abrir e fazer commit sozinha, como em produção. */
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ConversaAcessoIntegrationTest {
    @Autowired AdminController admin;
    @Autowired UsuarioRepository usuarios;
    @Autowired PedidoContratadoRepository pedidos;
    @Autowired DenunciaRepository denuncias;
    @Autowired MensagemRepository mensagens;
    @Autowired HistoricoContaRepository historico;

    @AfterEach void limpar() {
        historico.deleteAll(); mensagens.deleteAll(); denuncias.deleteAll(); pedidos.deleteAll(); usuarios.deleteAll();
    }

    private Usuario conta(String nome, String email, String tipo) {
        Usuario u = new Usuario(); u.setNome(nome); u.setEmail(email); u.setTipo(tipo); u.setSenha("x"); u.setCredentialVersion(0);
        return usuarios.save(u);
    }

    @Test void abrirConversaComDenunciaGravaUmAcessoPorParticipante() {
        Usuario ana = conta("Ana", "ana@x.com", "cliente");
        Usuario bruno = conta("Bruno", "bruno@x.com", "profissional");
        Usuario mod = conta("Mod", "mod@x.com", "moderador");
        Pedido p = new Pedido(); p.setId("ped-1"); p.setClientEmail(ana.getEmail()); p.setProEmail(bruno.getEmail());
        pedidos.save(p);
        mensagens.save(new Mensagem("m1", "ped-1", ana.getId(), bruno.getId(), "cm-1", "Oi", 1L));
        denuncias.save(new Denuncia(ana.getId(), bruno.getId(), "ped-1", "conduta", "Me ofendeu", 2L));
        var auth = new UsernamePasswordAuthenticationToken(new TaskgoPrincipal(mod.getId(), mod.getEmail(), mod.getNome(), "moderador", null), null, List.of());

        assertEquals(1, admin.conversaDoPedido("ped-1", auth).size());

        for (Usuario participante : List.of(ana, bruno)) {
            List<HistoricoConta> acessos = historico.findByUsuarioIdOrderByCriadoEmDesc(participante.getId()).stream()
                    .filter(h -> "acesso".equals(h.getTipo())).toList();
            assertEquals(1, acessos.size(), "registro de acesso gravado para " + participante.getNome());
            assertEquals(mod.getId(), acessos.get(0).getAutorId());
            assertEquals("ped-1", acessos.get(0).getPedidoId());
        }
    }
}
