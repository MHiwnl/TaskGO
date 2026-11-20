package com.taskgo.api;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

/* Os contadores de erro (2FA, código de chegada) mudam o extra no lugar: o Hibernate precisa gravar isso. */
@DataJpaTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:taskgo-extra;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false"
})
class ExtraGravaNoBancoIntegrationTest {
    @Autowired private TestEntityManager em;

    @Test void contadorNoExtraSobreviveARecarga() {
        Usuario u = new Usuario(); u.setNome("Ana"); u.setEmail("ana.extra@example.com"); u.setTipo("cliente"); u.setCredentialVersion(0);
        Long id = em.persistAndFlush(u).getId();
        em.clear();

        Usuario lido = em.find(Usuario.class, id);
        LimiteErros.errou(lido.getExtra(), "tfaErros", 5, 900_000L, 900_000L, 1_000L);
        em.flush(); em.clear();

        assertEquals(1, ((Number) em.find(Usuario.class, id).getExtra().get("tfaErros")).intValue());

        Pedido p = new Pedido(); p.setId("o_extra1"); p.setClientEmail("c@x.com"); p.setService("eletrica"); p.setPrice(100.0); p.setStatus("chegou");
        em.persistAndFlush(p); em.clear();
        Pedido pl = em.find(Pedido.class, "o_extra1");
        LimiteErros.errou(pl.getExtra(), "codigoErros", 5, 900_000L, 900_000L, 1_000L);
        em.flush(); em.clear();
        assertEquals(1, ((Number) em.find(Pedido.class, "o_extra1").getExtra().get("codigoErros")).intValue());
    }
}
