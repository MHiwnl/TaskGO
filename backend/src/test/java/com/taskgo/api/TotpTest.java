package com.taskgo.api;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class TotpTest {
    /* Vetor do RFC 6238 (SHA-1): segredo "12345678901234567890" em base32, instante 59 s → 94287082 (8 dígitos) → 287082. */
    @Test void bateComORfc() {
        String segredo = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ";
        assertEquals("287082", Totp.codigo(segredo, 59 / 30));
        assertTrue(Totp.confere(segredo, "287 082", 59_000));
        assertFalse(Totp.confere(segredo, "287083", 59_000));
    }
    @Test void aceitaUmPassoDeDiferencaMasNaoDois() {
        String s = Totp.novoSegredo();
        long agora = 1_800_000_000_000L;
        assertTrue(Totp.confere(s, Totp.codigo(s, agora / 30_000 - 1), agora));
        assertFalse(Totp.confere(s, Totp.codigo(s, agora / 30_000 - 3), agora));
        assertTrue(Totp.uri(s, "ana@x.com").startsWith("otpauth://totp/TaskGO%3Aana%40x.com?secret="));
    }
    @Test void dispositivoLegivel() {
        assertEquals("Chrome no Windows", SessaoConta.dispositivo("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/157.0 Safari/537.36"));
        assertEquals("Safari no iPhone/iPad", SessaoConta.dispositivo("Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) Version/17.0 Mobile Safari/604.1"));
    }
}
