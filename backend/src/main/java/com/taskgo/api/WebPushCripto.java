package com.taskgo.api;

import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.AlgorithmParameters;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPrivateKeySpec;
import java.security.spec.ECPublicKeySpec;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/* Web Push sem biblioteca externa: criptografia do conteúdo (RFC 8291, aes128gcm) e assinatura VAPID (RFC 8292, ES256). */
final class WebPushCripto {
    private WebPushCripto() {}
    private static final SecureRandom ALEATORIO = new SecureRandom();
    static final Base64.Encoder B64 = Base64.getUrlEncoder().withoutPadding();
    static byte[] b64(String s) { return Base64.getUrlDecoder().decode(s.replace('+', '-').replace('/', '_').replace("=", "")); }

    static ECParameterSpec p256() {
        try { AlgorithmParameters p = AlgorithmParameters.getInstance("EC"); p.init(new ECGenParameterSpec("secp256r1")); return p.getParameterSpec(ECParameterSpec.class); }
        catch (Exception e) { throw new IllegalStateException(e); }
    }
    static KeyPair novoPar() {
        try { KeyPairGenerator g = KeyPairGenerator.getInstance("EC"); g.initialize(new ECGenParameterSpec("secp256r1")); return g.generateKeyPair(); }
        catch (Exception e) { throw new IllegalStateException(e); }
    }
    static ECPublicKey publica(byte[] ponto) {
        try {
            if (ponto.length != 65 || ponto[0] != 4) throw new IllegalArgumentException("Chave pública inválida.");
            ECPoint w = new ECPoint(new BigInteger(1, Arrays.copyOfRange(ponto, 1, 33)), new BigInteger(1, Arrays.copyOfRange(ponto, 33, 65)));
            return (ECPublicKey) KeyFactory.getInstance("EC").generatePublic(new ECPublicKeySpec(w, p256()));
        } catch (IllegalArgumentException e) { throw e; } catch (Exception e) { throw new IllegalArgumentException("Chave pública inválida.", e); }
    }
    static ECPrivateKey privada(byte[] d) {
        try { return (ECPrivateKey) KeyFactory.getInstance("EC").generatePrivate(new ECPrivateKeySpec(new BigInteger(1, d), p256())); }
        catch (Exception e) { throw new IllegalArgumentException("Chave privada inválida.", e); }
    }
    static byte[] bruta(ECPublicKey k) {
        byte[] r = new byte[65]; r[0] = 4;
        copiar32(k.getW().getAffineX(), r, 1); copiar32(k.getW().getAffineY(), r, 33);
        return r;
    }
    static byte[] bruta(ECPrivateKey k) { byte[] r = new byte[32]; copiar32(k.getS(), r, 0); return r; }
    private static void copiar32(BigInteger v, byte[] destino, int pos) {
        byte[] b = v.toByteArray();
        int n = Math.min(b.length, 32);
        System.arraycopy(b, b.length - n, destino, pos + 32 - n, n);
    }

    static byte[] hmac(byte[] chave, byte[]... partes) {
        try { Mac m = Mac.getInstance("HmacSHA256"); m.init(new SecretKeySpec(chave, "HmacSHA256")); for (byte[] p : partes) m.update(p); return m.doFinal(); }
        catch (Exception e) { throw new IllegalStateException(e); }
    }
    private static byte[] junta(byte[]... partes) {
        ByteArrayOutputStream o = new ByteArrayOutputStream(); for (byte[] p : partes) o.writeBytes(p); return o.toByteArray();
    }

    /* Corpo cifrado (aes128gcm) para a inscrição do navegador. */
    static byte[] cifrar(byte[] texto, byte[] uaPublica, byte[] authSecret) {
        KeyPair servidor = novoPar();
        byte[] sal = new byte[16]; ALEATORIO.nextBytes(sal);
        return cifrar(texto, uaPublica, authSecret, (ECPrivateKey) servidor.getPrivate(), (ECPublicKey) servidor.getPublic(), sal);
    }
    static byte[] cifrar(byte[] texto, byte[] uaPublica, byte[] authSecret, ECPrivateKey asPrivada, ECPublicKey asPublica, byte[] sal) {
        try {
            KeyAgreement ka = KeyAgreement.getInstance("ECDH");
            ka.init(asPrivada); ka.doPhase(publica(uaPublica), true);
            byte[] ecdh = ka.generateSecret();
            byte[] asBruta = bruta(asPublica);
            byte[] prkKey = hmac(authSecret, ecdh);
            byte[] ikm = hmac(prkKey, "WebPush: info".getBytes(StandardCharsets.US_ASCII), new byte[]{0}, uaPublica, asBruta, new byte[]{1});
            byte[] prk = hmac(sal, ikm);
            byte[] cek = Arrays.copyOf(hmac(prk, "Content-Encoding: aes128gcm".getBytes(StandardCharsets.US_ASCII), new byte[]{0, 1}), 16);
            byte[] nonce = Arrays.copyOf(hmac(prk, "Content-Encoding: nonce".getBytes(StandardCharsets.US_ASCII), new byte[]{0, 1}), 12);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(cek, "AES"), new GCMParameterSpec(128, nonce));
            byte[] cifrado = c.doFinal(junta(texto, new byte[]{2}));
            ByteBuffer cab = ByteBuffer.allocate(16 + 4 + 1 + asBruta.length);
            cab.put(sal).putInt(4096).put((byte) asBruta.length).put(asBruta);
            return junta(cab.array(), cifrado);
        } catch (IllegalArgumentException e) { throw e; } catch (Exception e) { throw new IllegalStateException("Falha ao cifrar o aviso.", e); }
    }

    /* JWT VAPID (ES256) para o servidor de push de quem recebe. */
    static String vapid(String audiencia, String contato, ECPrivateKey chave, long expiraEmSegundos) {
        try {
            String cab = B64.encodeToString("{\"typ\":\"JWT\",\"alg\":\"ES256\"}".getBytes(StandardCharsets.UTF_8));
            String corpo = B64.encodeToString(("{\"aud\":\"" + audiencia + "\",\"exp\":" + expiraEmSegundos + ",\"sub\":\"" + contato + "\"}").getBytes(StandardCharsets.UTF_8));
            Signature s = Signature.getInstance("SHA256withECDSAinP1363Format");
            s.initSign(chave); s.update((cab + "." + corpo).getBytes(StandardCharsets.US_ASCII));
            return cab + "." + corpo + "." + B64.encodeToString(s.sign());
        } catch (Exception e) { throw new IllegalStateException("Falha ao assinar o aviso.", e); }
    }
}
