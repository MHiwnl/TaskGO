package com.taskgo.api;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class GoogleLoginTest {
    private static final Base64.Encoder B = Base64.getUrlEncoder().withoutPadding();
    private final KeyPair par = gerar(), outro = gerar();
    private final GoogleLogin google = new GoogleLogin("cliente-123.apps.googleusercontent.com", kid -> "k1".equals(kid) ? par.getPublic() : null);

    private static KeyPair gerar() { try { KeyPairGenerator g = KeyPairGenerator.getInstance("RSA"); g.initialize(2048); return g.generateKeyPair(); } catch (Exception e) { throw new RuntimeException(e); } }
    private String token(String corpo, KeyPair assinante) throws Exception {
        String c = B.encodeToString("{\"alg\":\"RS256\",\"kid\":\"k1\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));
        String p = B.encodeToString(corpo.getBytes(StandardCharsets.UTF_8));
        Signature s = Signature.getInstance("SHA256withRSA"); s.initSign(assinante.getPrivate()); s.update((c + "." + p).getBytes(StandardCharsets.US_ASCII));
        return c + "." + p + "." + B.encodeToString(s.sign());
    }
    private String corpo(String aud, long exp, boolean verificado) {
        return "{\"iss\":\"https://accounts.google.com\",\"aud\":\"" + aud + "\",\"sub\":\"1098\",\"email\":\"Ana@Gmail.com\",\"email_verified\":" + verificado + ",\"name\":\"Ana Lima\",\"exp\":" + exp + "}";
    }

    @Test void aceitaTokenValido() throws Exception {
        var conta = google.verificar(token(corpo("cliente-123.apps.googleusercontent.com", 2000, true), par), 1000);
        assertEquals("ana@gmail.com", conta.email()); assertEquals("1098", conta.sub()); assertEquals("Ana Lima", conta.nome());
    }
    @Test void recusaAssinaturaDeOutraChave() throws Exception {
        assertThrows(IllegalArgumentException.class, () -> google.verificar(token(corpo("cliente-123.apps.googleusercontent.com", 2000, true), outro), 1000));
    }
    @Test void recusaOutroSiteExpiradoOuSemVerificar() throws Exception {
        assertThrows(IllegalArgumentException.class, () -> google.verificar(token(corpo("outro.apps.googleusercontent.com", 2000, true), par), 1000));
        assertThrows(IllegalArgumentException.class, () -> google.verificar(token(corpo("cliente-123.apps.googleusercontent.com", 999, true), par), 1000));
        assertThrows(IllegalArgumentException.class, () -> google.verificar(token(corpo("cliente-123.apps.googleusercontent.com", 2000, false), par), 1000));
        assertThrows(IllegalArgumentException.class, () -> google.verificar("lixo", 1000));
    }
    @Test void desligadoSemClientId() {
        assertFalse(new GoogleLogin("").ativo());
        assertThrows(IllegalArgumentException.class, () -> new GoogleLogin("").verificar("a.b.c", 0));
    }
}
