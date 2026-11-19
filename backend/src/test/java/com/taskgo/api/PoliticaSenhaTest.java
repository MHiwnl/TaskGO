package com.taskgo.api;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.*;

class PoliticaSenhaTest {
    @Test
    void aceitaSenhaLongaComTresTipos() {
        assertTrue(PoliticaSenha.problemas("Tarefa#Forte2026", "ana@example.com", "Ana Souza").isEmpty());
        assertTrue(PoliticaSenha.problemas("caminho verde 42 Azul", null, null).isEmpty());
    }

    @Test
    void recusaCurtaSemVariedadeComumOuPessoal() {
        assertFalse(PoliticaSenha.problemas("Ab1#xyz", null, null).isEmpty());              /* curta */
        assertFalse(PoliticaSenha.problemas("somenteletrasminusculas", null, null).isEmpty()); /* 1 tipo */
        assertFalse(PoliticaSenha.problemas("Senha@1234567", null, null).isEmpty());        /* comum */
        assertFalse(PoliticaSenha.problemas("P@ssw0rd!!!!!", null, null).isEmpty());        /* comum com leet */
        assertFalse(PoliticaSenha.problemas("Mendonca#2026x", "x@y.com", "Carla Mendonça").isEmpty()); /* nome */
        assertFalse(PoliticaSenha.problemas("Thiagosilva#26", "thiagosilva@x.com", null).isEmpty());    /* e-mail */
        assertThrows(ResponseStatusException.class, () -> PoliticaSenha.exigirLocal("123456789012", null, null));
    }

    @Test
    void semInternetNaoBloqueia() {
        assertFalse(new PoliticaSenha(false).vazada("qualquer coisa"));
    }
}
