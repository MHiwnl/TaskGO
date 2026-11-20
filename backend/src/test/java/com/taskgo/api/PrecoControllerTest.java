package com.taskgo.api;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PrecoControllerTest {
    private final PedidoContratadoRepository pedidos = mock(PedidoContratadoRepository.class);
    private final PrecoController controller = new PrecoController(pedidos);

    @Test void duracaoSoAparecePorTresCincoAmostras() {
        when(pedidos.duracoesConcluidas(anyString(), anyLong())).thenReturn(List.of(40L * 60000, 50L * 60000, 60L * 60000, 45L * 60000));
        Map<String, Object> r = controller.faixa("eletrica");
        assertFalse(r.containsKey("duracaoMedia"));
        assertFalse(r.containsKey("duracaoMin"));
        assertFalse(r.containsKey("duracaoMax"));

        when(pedidos.duracoesConcluidas(anyString(), anyLong())).thenReturn(List.of(30L * 60000, 40L * 60000, 50L * 60000, 60L * 60000, 70L * 60000));
        r = controller.faixa("eletrica");
        assertEquals(50.0, r.get("duracaoMedia"));
        assertEquals(35.0, r.get("duracaoMin"));
        assertEquals(65.0, r.get("duracaoMax"));
    }
}
