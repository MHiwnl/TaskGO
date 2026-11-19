package com.taskgo.api;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ChamarAgoraServiceTest {
    @Test
    void distanciaEMinutos() {
        double km = ChamarAgoraService.distanciaKm(-8.05, -34.90, -8.06, -34.90);
        assertEquals(1.11, km, 0.02);
        assertEquals(3, ChamarAgoraService.minutosAte(km));
        assertEquals(2, ChamarAgoraService.minutosAte(0.1));   /* nunca menos de 2 minutos */
        assertEquals(24, ChamarAgoraService.minutosAte(10));   /* 10 km a 25 km/h */
    }

    @Test
    void convidadoSoQuemFoiChamadoENaoFoiRecusado() {
        Pedido p = new Pedido();
        p.addExtra("modo", "agora");
        p.addExtra("notificados", List.of(1, 2, 3));
        p.addExtra("recusados", List.of(2));
        assertTrue(ChamarAgoraService.modoAgora(p));
        assertTrue(ChamarAgoraService.convidado(p, 1L));
        assertFalse(ChamarAgoraService.convidado(p, 2L));
        assertFalse(ChamarAgoraService.convidado(p, 9L));
        assertEquals(List.of(1L, 2L, 3L), ChamarAgoraService.ids(p, "notificados"));
    }

    @Test
    void posicaoDoProfissionalSaiAproximada() {
        PresencaProfissional x = new PresencaProfissional(1L, -8.051234, -34.901234, 10, 0);
        var m = ChamarAgoraController.pontoAproximado(new ChamarAgoraService.Candidato(new Usuario(), x, 0.234));
        assertEquals(-8.05, m.get("lat"));
        assertEquals(-34.9, m.get("lng"));
        assertEquals(1.0, m.get("km"));
        assertEquals(17.0, ChamarAgoraController.kmAproximado(17.4));
    }

    @Test
    void percentilDaFaixaDePreco() {
        List<Double> v = List.of(100.0, 120.0, 150.0, 200.0, 400.0);
        assertEquals(150.0, PrecoController.percentil(v, .5));
        assertEquals(110.0, PrecoController.percentil(v, .1));
    }
}
