package com.taskgo.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigInteger;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.RSAPublicKeySpec;
import java.time.Duration;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/* Entrar com Google: confere o ID token (JWT RS256) que o botão do Google entrega ao navegador.
   Assinatura com as chaves públicas do Google, emissor, público (o ID do cliente), validade e e-mail verificado. */
@Component
public class GoogleLogin {
    static final String CHAVES_URL = "https://www.googleapis.com/oauth2/v3/certs";
    private final String clientId;
    private final ObjectMapper json = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private Function<String, PublicKey> chaves = this::chaveDoGoogle;
    private volatile Map<String, PublicKey> cache = Map.of();
    private volatile long cacheAte = 0;

    @Autowired
    public GoogleLogin(@Value("${taskgo.google.client-id:}") String clientId) { this.clientId = clientId == null ? "" : clientId.trim(); }
    /* Para testes: chaves fixas no lugar das do Google. */
    GoogleLogin(String clientId, Function<String, PublicKey> chaves) { this(clientId); this.chaves = chaves; }

    public boolean ativo() { return !clientId.isBlank(); }
    public String clientId() { return clientId; }

    public record Conta(String sub, String email, String nome) {}

    /* Conta Google do token, ou IllegalArgumentException com o motivo. */
    public Conta verificar(String token, long agoraSegundos) {
        if (!ativo()) throw new IllegalArgumentException("Entrar com Google não está configurado.");
        String[] p = token == null ? new String[0] : token.split("\\.");
        if (p.length != 3) throw new IllegalArgumentException("Token do Google inválido.");
        try {
            JsonNode cab = json.readTree(Base64.getUrlDecoder().decode(p[0]));
            JsonNode c = json.readTree(Base64.getUrlDecoder().decode(p[1]));
            if (!"RS256".equals(cab.path("alg").asText())) throw new IllegalArgumentException("Token do Google inválido.");
            PublicKey chave = chaves.apply(cab.path("kid").asText());
            if (chave == null) throw new IllegalArgumentException("Token do Google inválido.");
            Signature s = Signature.getInstance("SHA256withRSA");
            s.initVerify(chave); s.update((p[0] + "." + p[1]).getBytes(StandardCharsets.US_ASCII));
            if (!s.verify(Base64.getUrlDecoder().decode(p[2]))) throw new IllegalArgumentException("Token do Google inválido.");
            String iss = c.path("iss").asText();
            if (!iss.equals("accounts.google.com") && !iss.equals("https://accounts.google.com")) throw new IllegalArgumentException("Token do Google inválido.");
            if (!clientId.equals(c.path("aud").asText())) throw new IllegalArgumentException("Token emitido para outro site.");
            if (c.path("exp").asLong(0) < agoraSegundos) throw new IllegalArgumentException("O login com Google expirou. Tente de novo.");
            if (!c.path("email_verified").asBoolean(false)) throw new IllegalArgumentException("Esse e-mail do Google não está verificado.");
            String email = c.path("email").asText(""), sub = c.path("sub").asText("");
            if (email.isBlank() || sub.isBlank()) throw new IllegalArgumentException("O Google não informou o e-mail.");
            return new Conta(sub, email.trim().toLowerCase(java.util.Locale.ROOT), c.path("name").asText(""));
        } catch (IllegalArgumentException e) { throw e; }
        catch (Exception e) { throw new IllegalArgumentException("Token do Google inválido."); }
    }

    private PublicKey chaveDoGoogle(String kid) {
        if (System.currentTimeMillis() > cacheAte || !cache.containsKey(kid)) carregar();
        return cache.get(kid);
    }
    private synchronized void carregar() {
        try {
            HttpResponse<String> r = http.send(HttpRequest.newBuilder(URI.create(CHAVES_URL)).timeout(Duration.ofSeconds(8)).GET().build(), HttpResponse.BodyHandlers.ofString());
            if (r.statusCode() != 200) return;
            Map<String, PublicKey> novo = new HashMap<>();
            for (JsonNode k : json.readTree(r.body()).path("keys")) {
                if (!"RSA".equals(k.path("kty").asText())) continue;
                BigInteger n = new BigInteger(1, Base64.getUrlDecoder().decode(k.path("n").asText()));
                BigInteger e = new BigInteger(1, Base64.getUrlDecoder().decode(k.path("e").asText()));
                novo.put(k.path("kid").asText(), KeyFactory.getInstance("RSA").generatePublic(new RSAPublicKeySpec(n, e)));
            }
            cache = novo; cacheAte = System.currentTimeMillis() + 3_600_000;
        } catch (Exception ignorar) { /* sem rede: o login com Google falha e o resto do site segue */ }
    }
}
