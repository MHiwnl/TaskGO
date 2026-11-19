package com.taskgo.api;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class ModeracaoServiceTest {
    private final UsuarioRepository usuarios = mock(UsuarioRepository.class);
    private final ModeracaoService moderacao = new ModeracaoService(usuarios, "adm@taskgo.com, outro@taskgo.com");

    @Test void bloqueiaNumerosValoresEContatos() {
        assertEquals("números", moderacao.motivoBloqueio("meu numero e 81 99999-0000"));
        assertEquals("valores", moderacao.motivoBloqueio("faço por cem reais"));
        assertEquals("números escritos por extenso", moderacao.motivoBloqueio("nove nove oito sete"));
        assertEquals("contato fora do app", moderacao.motivoBloqueio("me chama no WhatsApp"));
        assertEquals("contato fora do app", moderacao.motivoBloqueio("passa teu Instagram"));
        assertNull(moderacao.motivoBloqueio("Estou a caminho, chego logo."));
        assertNull(moderacao.motivoBloqueio("Pode subir direto, por favor."));
    }

    @Test void avisoSuspensaoEBanimentoNaOrdem() {
        Usuario u = new Usuario(); u.setId(5L); when(usuarios.findById(5L)).thenReturn(Optional.of(u));
        var s1 = moderacao.registrarInfracao(5L); assertEquals(1, s1.avisos()); assertFalse(s1.suspenso()); assertFalse(s1.banido());
        var s2 = moderacao.registrarInfracao(5L); assertTrue(s2.suspenso());
        assertThrows(ResponseStatusException.class, () -> moderacao.exigirLiberado(5L));
        var s3 = moderacao.registrarInfracao(5L); assertTrue(s3.banido());
        var perdoado = moderacao.perdoar(5L); assertEquals(2, perdoado.avisos()); assertFalse(perdoado.banido()); assertFalse(perdoado.suspenso());
        assertDoesNotThrow(() -> moderacao.exigirLiberado(5L));
    }

    @Test void administradoresVemDaConfiguracao() {
        assertTrue(moderacao.administrador("ADM@taskgo.com"));
        assertTrue(moderacao.administrador("outro@taskgo.com"));
        assertFalse(moderacao.administrador("cliente@taskgo.com"));
        assertFalse(new ModeracaoService(usuarios, "").administrador(""));
    }
}
