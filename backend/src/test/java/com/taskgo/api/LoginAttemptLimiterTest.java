package com.taskgo.api;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class LoginAttemptLimiterTest {
    @Test
    void tresSenhasErradasBloqueiamAContaDeQualquerJeitoDeEscrever() {
        LoginAttemptLimiter l = new LoginAttemptLimiter();
        assertEquals(0, l.contaBloqueadaPor("ana@x.com"));
        l.contaErrou("ana@x.com"); l.contaErrou(" ANA@x.com "); 
        assertEquals(0, l.contaBloqueadaPor("ana@x.com"), "2 erros ainda deixam tentar");
        l.contaErrou("Ana@X.com");
        long espera = l.contaBloqueadaPor("ana@x.com");
        assertTrue(espera > 9 * 60_000 && espera <= 10 * 60_000, "bloqueia por até 10 minutos: " + espera);
        assertEquals(0, l.contaBloqueadaPor("outra@x.com"), "outra conta não é afetada");
    }

    @Test
    void acertarLimpaOsErros() {
        LoginAttemptLimiter l = new LoginAttemptLimiter();
        l.contaErrou("bia@x.com"); l.contaErrou("bia@x.com");
        l.contaAcertou("bia@x.com");
        l.contaErrou("bia@x.com"); l.contaErrou("bia@x.com");
        assertEquals(0, l.contaBloqueadaPor("bia@x.com"));
    }
}
