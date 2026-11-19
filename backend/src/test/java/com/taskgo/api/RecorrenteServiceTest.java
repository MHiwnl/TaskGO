package com.taskgo.api;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class RecorrenteServiceTest {
    private static final LocalDate HOJE = LocalDate.of(2026, 11, 13); // sexta

    private static Map<String, Object> bloco(int vezes, List<Integer> dias, String inicio, String hora) {
        return Map.of("vezes", vezes, "dias", dias, "inicio", inicio, "hora", hora);
    }

    @Test
    void geraAsDatasNosDiasEscolhidos() {
        RecorrenteService.Plano p = RecorrenteService.plano(bloco(5, List.of(1, 3, 5), "2026-11-14", "08:00"), "limpeza", HOJE);
        List<LocalDate> d = p.datas();
        assertEquals(List.of(LocalDate.of(2026, 11, 16), LocalDate.of(2026, 11, 18), LocalDate.of(2026, 11, 20), LocalDate.of(2026, 11, 23), LocalDate.of(2026, 11, 25)), d);
        assertEquals(1, RecorrenteService.semana(d.get(0), d.get(2)));
        assertEquals(2, RecorrenteService.semana(d.get(0), d.get(3)));
        Map<String, Object> ex = p.comoExtra(80);
        assertEquals("400.00", ex.get("total").toString());
        assertEquals(2, ex.get("semanas"));
    }

    @Test
    void recusaServicoNaoRecorrenteEForaDosLimites() {
        assertThrows(ResponseStatusException.class, () -> RecorrenteService.plano(bloco(4, List.of(1), "2026-11-14", "08:00"), "pintura", HOJE));
        assertThrows(ResponseStatusException.class, () -> RecorrenteService.plano(bloco(31, List.of(1), "2026-11-14", "08:00"), "limpeza", HOJE));
        assertThrows(ResponseStatusException.class, () -> RecorrenteService.plano(bloco(1, List.of(1), "2026-11-14", "08:00"), "limpeza", HOJE));
        assertThrows(ResponseStatusException.class, () -> RecorrenteService.plano(bloco(4, List.of(), "2026-11-14", "08:00"), "limpeza", HOJE));
        assertThrows(ResponseStatusException.class, () -> RecorrenteService.plano(bloco(4, List.of(1), "2026-11-13", "08:00"), "limpeza", HOJE));
        assertThrows(ResponseStatusException.class, () -> RecorrenteService.plano(bloco(4, List.of(1), "2027-02-01", "08:00"), "limpeza", HOJE));
        assertThrows(ResponseStatusException.class, () -> RecorrenteService.plano(bloco(4, List.of(1), "2026-11-14", "23:00"), "limpeza", HOJE));
        assertDoesNotThrow(() -> RecorrenteService.plano(bloco(30, List.of(1, 2, 3, 4, 5, 6, 7), "2026-11-14", "06:00"), "passeador", HOJE));
    }
}
