package com.taskgo.api;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/* Avisos fora do site: cada notificação nova vai também por e-mail e por push do navegador, conforme a preferência da conta.
   Conversa (mensagem) não vai por e-mail, só por push. */
@Service
public class AvisosExternosService {
    private static final Logger log = LoggerFactory.getLogger(AvisosExternosService.class);
    static final String CHAVE_PREF = "avisos";
    /* Servidores de push dos navegadores. Endereço fora desta lista é recusado (o servidor não chama endereços internos). */
    static final java.util.List<String> HOSTS_PUSH = java.util.List.of("fcm.googleapis.com", "updates.push.services.mozilla.com", "web.push.apple.com", ".push.apple.com", ".notify.windows.com");

    private final UsuarioRepository usuarios;
    private final PushInscricaoRepository inscricoes;
    private final ChaveSistemaRepository chaves;
    private final EmailRecuperacaoSenha email;
    private final String siteUrl;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER).build();
    private final ExecutorService fila = Executors.newFixedThreadPool(2, r -> { Thread t = new Thread(r, "avisos-externos"); t.setDaemon(true); return t; });
    private volatile KeyPair vapid;

    public AvisosExternosService(UsuarioRepository usuarios, PushInscricaoRepository inscricoes, ChaveSistemaRepository chaves, EmailRecuperacaoSenha email,
                                 @Value("${taskgo.site.url:}") String siteUrl, @Value("${taskgo.auth.password-reset.frontend-url:}") String resetUrl) {
        this.usuarios = usuarios; this.inscricoes = inscricoes; this.chaves = chaves; this.email = email;
        String base = siteUrl != null && !siteUrl.isBlank() ? siteUrl : resetUrl == null ? "" : resetUrl.replaceAll("#.*$", "");
        this.siteUrl = base.replaceAll("/+$", "");
    }

    /* ---------- preferências ---------- */
    public record Preferencias(boolean email, boolean push, boolean sms) {}
    static Preferencias preferencias(Usuario u) {
        if (u.getExtra().get(CHAVE_PREF) instanceof Map<?, ?> m)
            return new Preferencias(!Boolean.FALSE.equals(m.get("email")), !Boolean.FALSE.equals(m.get("push")), !Boolean.FALSE.equals(m.get("sms")));
        return new Preferencias(true, true, true);
    }
    public boolean emailDisponivel() { return email.canalPronto(); }

    /* SMS: só quando o canal estiver ligado (TASKGO_SMS_MODO). Opcional nos testes que montam o serviço à mão. */
    private SmsService sms;
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    void setSms(SmsService sms) { this.sms = sms; }
    public boolean smsDisponivel() { return sms != null && sms.ligado(); }

    static boolean enderecoPermitido(String endpoint) {
        try {
            URI u = URI.create(endpoint);
            if (!"https".equals(u.getScheme()) || u.getHost() == null || u.getUserInfo() != null || u.getPort() != -1 && u.getPort() != 443) return false;
            String h = u.getHost().toLowerCase(java.util.Locale.ROOT);
            return HOSTS_PUSH.stream().anyMatch(x -> x.startsWith(".") ? h.endsWith(x) : h.equals(x));
        } catch (RuntimeException e) { return false; }
    }

    /* ---------- chaves VAPID: geradas uma vez e guardadas no banco ---------- */
    @Transactional
    public String chavePublica() { return WebPushCripto.B64.encodeToString(WebPushCripto.bruta((ECPublicKey) par().getPublic())); }
    private KeyPair par() {
        KeyPair k = vapid;
        if (k != null) return k;
        synchronized (this) {
            if (vapid != null) return vapid;
            var pub = chaves.findById("vapid_publica").map(ChaveSistema::getValor).orElse(null);
            var priv = chaves.findById("vapid_privada").map(ChaveSistema::getValor).orElse(null);
            if (pub == null || priv == null) {
                KeyPair novo = WebPushCripto.novoPar();
                pub = WebPushCripto.B64.encodeToString(WebPushCripto.bruta((ECPublicKey) novo.getPublic()));
                priv = WebPushCripto.B64.encodeToString(WebPushCripto.bruta((ECPrivateKey) novo.getPrivate()));
                chaves.save(new ChaveSistema("vapid_publica", pub));
                chaves.save(new ChaveSistema("vapid_privada", priv));
            }
            vapid = new KeyPair(WebPushCripto.publica(WebPushCripto.b64(pub)), WebPushCripto.privada(WebPushCripto.b64(priv)));
            return vapid;
        }
    }

    /* ---------- envio ---------- */
    /* Chamado ao criar uma notificação: envia depois que a transação grava, numa fila própria, sem atrasar a resposta. */
    public void aoCriar(Notificacao n) {
        Runnable tarefa = () -> fila.submit(() -> { try { entregar(n); } catch (RuntimeException e) { log.warn("Aviso externo não entregue ({}): {}", n.getTipo(), e.getMessage()); } });
        if (TransactionSynchronizationManager.isSynchronizationActive())
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() { @Override public void afterCommit() { tarefa.run(); } });
        else tarefa.run();
    }

    void entregar(Notificacao n) {
        Usuario u = usuarios.findById(n.getUsuarioId()).orElse(null);
        if (u == null) return;
        Preferencias pref = preferencias(u);
        String link = siteUrl.isBlank() ? "" : siteUrl + "/#/app/" + (n.getPedidoId() != null && !"sistema".equals(n.getTipo()) ? "pedido/" + n.getPedidoId() : "notificacoes");
        boolean porEmail = pref.email() && email.canalPronto() && !"mensagem".equals(n.getTipo()) && (n.getChaveEvento() == null || !n.getChaveEvento().startsWith("conta:foto"));
        if (porEmail) {
            try { email.enviarTexto(u.getEmail(), "TaskGO: " + n.getTitulo(), n.getTitulo() + "\n\n" + n.getConteudo() + (link.isBlank() ? "" : "\n\nAbrir na TaskGO: " + link) + "\n\nPara parar de receber estes e-mails, desligue em Configurações > Avisos."); }
            catch (RuntimeException e) { log.warn("E-mail de aviso não enviado: {}", e.getMessage()); }
        }
        if (pref.push()) {
            Map<String, Object> corpo = new LinkedHashMap<>();
            corpo.put("titulo", n.getTitulo()); corpo.put("texto", n.getConteudo());
            corpo.put("url", "/#/app/" + (n.getPedidoId() != null && !"sistema".equals(n.getTipo()) ? "pedido/" + n.getPedidoId() : "notificacoes"));
            corpo.put("tag", n.getChaveEvento() == null ? n.getId() : n.getChaveEvento());
            enviarPush(u.getId(), json(corpo), "pedido".equals(n.getTipo()) ? "high" : "normal");
        }
        /* SMS custa: só para avisos de pedido (o que pede resposta rápida), com o canal ligado e a preferência da conta. */
        if (pref.sms() && smsDisponivel() && "pedido".equals(n.getTipo()))
            sms.enviar(u.getTelefone(), "TaskGO: " + n.getTitulo() + (link.isBlank() ? "" : " " + link));
    }

    /* Envia o mesmo aviso a todos os navegadores da conta; inscrição que o servidor de push diz não existir mais é apagada. */
    public int enviarPush(Long usuarioId, String json, String urgencia) {
        int entregues = 0;
        for (PushInscricao i : inscricoes.findByUsuarioId(usuarioId)) {
            if (!enderecoPermitido(i.getEndpoint())) { inscricoes.delete(i); continue; }
            try {
                byte[] corpo = WebPushCripto.cifrar(json.getBytes(StandardCharsets.UTF_8), WebPushCripto.b64(i.getP256dh()), WebPushCripto.b64(i.getAuth()));
                URI destino = URI.create(i.getEndpoint());
                String aud = destino.getScheme() + "://" + destino.getHost();
                String jwt = WebPushCripto.vapid(aud, contato(), (ECPrivateKey) par().getPrivate(), System.currentTimeMillis() / 1000 + 12 * 3600);
                HttpRequest req = HttpRequest.newBuilder(destino).timeout(Duration.ofSeconds(10))
                        .header("Authorization", "vapid t=" + jwt + ", k=" + chavePublica())
                        .header("Content-Encoding", "aes128gcm").header("Content-Type", "application/octet-stream")
                        .header("TTL", "86400").header("Urgency", urgencia)
                        .POST(HttpRequest.BodyPublishers.ofByteArray(corpo)).build();
                int s = http.send(req, HttpResponse.BodyHandlers.discarding()).statusCode();
                if (s == 404 || s == 410) inscricoes.delete(i);
                else if (s >= 200 && s < 300) entregues++;
                else log.warn("Push recusado pelo servidor do navegador: HTTP {}", s);
            } catch (IllegalArgumentException e) { inscricoes.delete(i); }
            catch (Exception e) { log.warn("Push não entregue: {}", e.getMessage()); }
        }
        return entregues;
    }

    private String contato() { return siteUrl.startsWith("https://") ? siteUrl : "mailto:taskgo.adm@gmail.com"; }
    private static String json(Map<String, Object> m) {
        StringBuilder b = new StringBuilder("{");
        m.forEach((k, v) -> { if (b.length() > 1) b.append(','); b.append('"').append(k).append("\":\"").append(escapar(String.valueOf(v))).append('"'); });
        return b.append('}').toString();
    }
    static String escapar(String s) {
        StringBuilder b = new StringBuilder();
        for (char c : s.toCharArray()) {
            if (c == '"' || c == '\\') b.append('\\').append(c);
            else if (c < 0x20) b.append(String.format("\\u%04x", (int) c));
            else b.append(c);
        }
        return b.toString();
    }

    /* ---------- inscrições ---------- */
    @Transactional
    public void inscrever(Long usuarioId, String endpoint, String p256dh, String auth) {
        if (!enderecoPermitido(endpoint)) throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "Este navegador usa um serviço de push que não é aceito.");
        try { if (WebPushCripto.b64(p256dh).length != 65 || WebPushCripto.b64(auth).length != 16) throw new IllegalArgumentException(); WebPushCripto.publica(WebPushCripto.b64(p256dh)); }
        catch (RuntimeException e) { throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "Inscrição de push inválida."); }
        inscricoes.findByEndpoint(endpoint).ifPresent(inscricoes::delete);
        inscricoes.flush();
        if (inscricoes.countByUsuarioId(usuarioId) >= 10) inscricoes.findByUsuarioId(usuarioId).stream().findFirst().ifPresent(inscricoes::delete);
        inscricoes.save(new PushInscricao(UUID.randomUUID().toString(), usuarioId, endpoint, p256dh, auth, System.currentTimeMillis()));
    }
    @Transactional
    public void desinscrever(Long usuarioId, String endpoint) {
        inscricoes.findByEndpoint(String.valueOf(endpoint)).filter(i -> i.getUsuarioId().equals(usuarioId)).ifPresent(inscricoes::delete);
    }
    @Transactional(readOnly = true)
    public long quantas(Long usuarioId) { return inscricoes.countByUsuarioId(usuarioId); }
}
