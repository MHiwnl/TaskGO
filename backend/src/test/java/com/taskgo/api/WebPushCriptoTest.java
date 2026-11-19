package com.taskgo.api;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.Signature;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class WebPushCriptoTest {
    /* Exemplo da RFC 8291, seção 5. */
    @Test void cifraIgualAoExemploDaRfc8291() {
        byte[] asPriv = WebPushCripto.b64("yfWPiYE-n46HLnH0KqZOF1fJJU3MYrct3AELtAQ-oRw");
        byte[] asPub = WebPushCripto.b64("BP4z9KsN6nGRTbVYI_c7VJSPQTBtkgcy27mlmlMoZIIgDll6e3vCYLocInmYWAmS6TlzAC8wEqKK6PBru3jl7A8");
        byte[] uaPub = WebPushCripto.b64("BCVxsr7N_eNgVRqvHtD0zTZsEc6-VV-JvLexhqUzORcxaOzi6-AYWXvTBHm4bjyPjs7Vd8pZGH6SRpkNtoIAiw4");
        byte[] auth = WebPushCripto.b64("BTBZMqHH6r4Tts7J_aSIgg");
        byte[] sal = WebPushCripto.b64("DGv6ra1nlYgDCS1FRnbzlw");
        byte[] corpo = WebPushCripto.cifrar("When I grow up, I want to be a watermelon".getBytes(StandardCharsets.UTF_8), uaPub, auth,
                WebPushCripto.privada(asPriv), WebPushCripto.publica(asPub), sal);
        assertEquals("DGv6ra1nlYgDCS1FRnbzlwAAEABBBP4z9KsN6nGRTbVYI_c7VJSPQTBtkgcy27mlmlMoZIIgDll6e3vCYLocInmYWAmS6TlzAC8wEqKK6PBru3jl7A_yl95bQpu6cVPTpK4Mqgkf1CXztLVBSt2Ks3oZwbuwXPXLWyouBWLVWGNWQexSgSxsj_Qulcy4a-fN",
                WebPushCripto.B64.encodeToString(corpo));
    }

    @Test void chavesIdaEVolta() {
        KeyPair par = WebPushCripto.novoPar();
        byte[] pub = WebPushCripto.bruta((ECPublicKey) par.getPublic()), priv = WebPushCripto.bruta((ECPrivateKey) par.getPrivate());
        assertEquals(65, pub.length); assertEquals(32, priv.length);
        assertEquals(((ECPublicKey) par.getPublic()).getW(), WebPushCripto.publica(pub).getW());
        assertEquals(((ECPrivateKey) par.getPrivate()).getS(), WebPushCripto.privada(priv).getS());
    }

    @Test void vapidAssinadoConfereComAChavePublica() throws Exception {
        KeyPair par = WebPushCripto.novoPar();
        String jwt = WebPushCripto.vapid("https://fcm.googleapis.com", "mailto:teste@example.com", (ECPrivateKey) par.getPrivate(), 2_000_000_000L);
        String[] p = jwt.split("\\.");
        assertEquals(3, p.length);
        assertTrue(new String(Base64.getUrlDecoder().decode(p[1]), StandardCharsets.UTF_8).contains("\"aud\":\"https://fcm.googleapis.com\""));
        Signature v = Signature.getInstance("SHA256withECDSAinP1363Format");
        v.initVerify(par.getPublic()); v.update((p[0] + "." + p[1]).getBytes(StandardCharsets.US_ASCII));
        assertTrue(v.verify(Base64.getUrlDecoder().decode(p[2])));
    }
}
