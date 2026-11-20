package com.taskgo.api;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

/* Cliente e prazo automático aprovando a mesma etapa: só o primeiro troca de entregue para aprovada. */
@DataJpaTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:taskgo-etapa;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false"
})
class EtapaObraAprovacaoIntegrationTest {
    @Autowired private EtapaObraRepository etapas;

    @Test void segundaAprovacaoNaoPassa() {
        EtapaObra e = new EtapaObra("e1", "p1", "orc1", 1, "Alvenaria", "Paredes de pé", new BigDecimal("1000.00"), 5, false);
        e.entregar("[]", 1L, 2L);
        etapas.saveAndFlush(e);
        assertEquals(1, etapas.reservarAprovacao("e1", EtapaObra.ENTREGUE, EtapaObra.APROVADA));
        assertEquals(0, etapas.reservarAprovacao("e1", EtapaObra.ENTREGUE, EtapaObra.APROVADA));
    }
}
