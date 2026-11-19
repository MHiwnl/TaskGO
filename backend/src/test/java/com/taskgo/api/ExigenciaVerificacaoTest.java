package com.taskgo.api;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class ExigenciaVerificacaoTest {
    UsuarioRepository usuarios = mock(UsuarioRepository.class);

    private Usuario conta(long id, boolean verificado) {
        Usuario u = new Usuario(); u.setId(id); if (verificado) u.getExtra().put("verificado", true);
        when(usuarios.findById(id)).thenReturn(Optional.of(u)); return u;
    }

    @Test void semVerificacaoNaoPedeNemAtende() {
        ExigenciaVerificacao e = new ExigenciaVerificacao(usuarios, true);
        Usuario u = conta(1L, false);
        assertEquals(403, assertThrows(ResponseStatusException.class, () -> e.exigirParaPedir(1L)).getStatusCode().value());
        assertTrue(assertThrows(ResponseStatusException.class, () -> e.exigirParaAtender(1L)).getReason().contains("oportunidades"));
        assertFalse(e.liberado(u));
    }

    @Test void verificadoPassa() {
        ExigenciaVerificacao e = new ExigenciaVerificacao(usuarios, true);
        Usuario u = conta(2L, true);
        assertDoesNotThrow(() -> e.exigirParaPedir(2L));
        assertDoesNotThrow(() -> e.exigirParaAtender(2L));
        assertTrue(e.liberado(u));
    }

    @Test void desligadaNaoExige() {
        ExigenciaVerificacao e = new ExigenciaVerificacao(usuarios, false);
        Usuario u = conta(3L, false);
        assertDoesNotThrow(() -> e.exigirParaPedir(3L));
        assertTrue(e.liberado(u));
    }
}
