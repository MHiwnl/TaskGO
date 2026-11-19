package com.taskgo.api;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/* SMS pronto para ligar a um provedor, mas desligado por padrão (cada SMS custa).
   TASKGO_SMS_MODO: desligado (padrão: não envia nada) | log (simula: só registra no log, para testar) |
   http (envia: POST JSON {"para":"+5581...","texto":"..."} para TASKGO_SMS_URL com "Authorization: Bearer TASKGO_SMS_TOKEN").
   Para outro formato de provedor, troque só o método enviarHttp. */
@Service
public class SmsService {
    private static final Logger log = LoggerFactory.getLogger(SmsService.class);
    static final int MAX_TEXTO = 160;

    private final String modo, url, token;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER).build();

    public SmsService(@Value("${taskgo.sms.modo:desligado}") String modo, @Value("${taskgo.sms.url:}") String url, @Value("${taskgo.sms.token:}") String token) {
        String m = modo == null ? "desligado" : modo.trim().toLowerCase(java.util.Locale.ROOT);
        boolean httpPronto = "http".equals(m) && url != null && url.startsWith("https://") && token != null && !token.isBlank();
        if ("http".equals(m) && !httpPronto) log.warn("SMS em modo http sem TASKGO_SMS_URL (https) ou TASKGO_SMS_TOKEN: SMS desligado.");
        this.modo = "log".equals(m) || httpPronto ? m : "desligado";
        this.url = url; this.token = token;
    }

    public boolean ligado() { return !"desligado".equals(modo); }
    String modo() { return modo; }

    /* Telefone brasileiro em formato internacional (+55 DDD número), ou null se não der para usar. */
    static String telefoneInternacional(String telefone) {
        if (telefone == null) return null;
        String d = telefone.replaceAll("\\D", "");
        if (d.startsWith("55") && (d.length() == 12 || d.length() == 13)) d = d.substring(2);
        if (d.length() != 10 && d.length() != 11) return null;
        if (d.charAt(0) == '0' || d.charAt(1) == '0') return null;
        return "+55" + d;
    }
    static String encurtar(String texto) {
        String t = texto == null ? "" : texto.replaceAll("\\s+", " ").trim();
        return t.length() <= MAX_TEXTO ? t : t.substring(0, MAX_TEXTO - 1) + "…";
    }
    /* No log, só o final do número. */
    static String mascarar(String numero) { return numero == null || numero.length() < 4 ? "***" : "***" + numero.substring(numero.length() - 4); }

    /* Devolve true se o SMS foi enviado (ou registrado, no modo log). Nunca lança: falha de SMS não derruba o aviso. */
    public boolean enviar(String telefone, String texto) {
        if (!ligado()) return false;
        String para = telefoneInternacional(telefone);
        if (para == null) return false;
        String corpo = encurtar(texto);
        if ("log".equals(modo)) { log.info("SMS (simulado) para {}: {}", mascarar(para), corpo); return true; }
        try { return enviarHttp(para, corpo); }
        catch (Exception e) { log.warn("SMS não enviado para {}: {}", mascarar(para), e.getMessage()); return false; }
    }

    private boolean enviarHttp(String para, String texto) throws Exception {
        String json = "{\"para\":\"" + AvisosExternosService.escapar(para) + "\",\"texto\":\"" + AvisosExternosService.escapar(texto) + "\"}";
        HttpRequest req = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(10))
                .header("Authorization", "Bearer " + token).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json)).build();
        int s = http.send(req, HttpResponse.BodyHandlers.discarding()).statusCode();
        if (s >= 200 && s < 300) return true;
        log.warn("Provedor de SMS recusou: HTTP {}", s);
        return false;
    }
}
