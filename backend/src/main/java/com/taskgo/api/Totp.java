package com.taskgo.api;

import java.nio.ByteBuffer;
import java.security.SecureRandom;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/* Código de 6 números que muda a cada 30 segundos (RFC 6238), o mesmo do Google Authenticator e afins. */
public final class Totp {
    private static final String BASE32 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
    private static final SecureRandom RANDOM = new SecureRandom();
    private Totp() {}

    public static String novoSegredo() {
        byte[] b = new byte[20]; RANDOM.nextBytes(b);
        StringBuilder s = new StringBuilder();
        int buffer = 0, bits = 0;
        for (byte x : b) { buffer = (buffer << 8) | (x & 0xff); bits += 8; while (bits >= 5) { s.append(BASE32.charAt((buffer >> (bits - 5)) & 31)); bits -= 5; } }
        if (bits > 0) s.append(BASE32.charAt((buffer << (5 - bits)) & 31));
        return s.toString();
    }

    static byte[] decodificar(String segredo) {
        String limpo = segredo.replace("=", "").replace(" ", "").toUpperCase();
        ByteBuffer out = ByteBuffer.allocate(limpo.length() * 5 / 8);
        int buffer = 0, bits = 0;
        for (char c : limpo.toCharArray()) { int v = BASE32.indexOf(c); if (v < 0) continue; buffer = (buffer << 5) | v; bits += 5; if (bits >= 8) { out.put((byte) (buffer >> (bits - 8))); bits -= 8; } }
        return java.util.Arrays.copyOf(out.array(), out.position());
    }

    static String codigo(String segredo, long passo) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(decodificar(segredo), "HmacSHA1"));
            byte[] h = mac.doFinal(ByteBuffer.allocate(8).putLong(passo).array());
            int o = h[h.length - 1] & 0xf;
            int bin = ((h[o] & 0x7f) << 24) | ((h[o + 1] & 0xff) << 16) | ((h[o + 2] & 0xff) << 8) | (h[o + 3] & 0xff);
            return String.format("%06d", bin % 1_000_000);
        } catch (Exception e) { throw new IllegalStateException(e); }
    }

    /* Aceita o código do momento e o de 30 segundos antes ou depois (relógio do celular um pouco fora). */
    public static boolean confere(String segredo, String digitado, long agoraMs) {
        if (segredo == null || digitado == null) return false;
        String d = digitado.replaceAll("[^0-9]", "");
        if (d.length() != 6) return false;
        long passo = agoraMs / 30_000;
        for (long p = passo - 1; p <= passo + 1; p++)
            if (java.security.MessageDigest.isEqual(codigo(segredo, p).getBytes(), d.getBytes())) return true;
        return false;
    }

    public static String uri(String segredo, String email) {
        String rotulo = java.net.URLEncoder.encode("TaskGO:" + email, java.nio.charset.StandardCharsets.UTF_8).replace("+", "%20");
        return "otpauth://totp/" + rotulo + "?secret=" + segredo + "&issuer=TaskGO&digits=6&period=30";
    }
}
