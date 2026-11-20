package com.taskgo.api;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/* Segurança da conta: sessões abertas (ver e encerrar) e autenticação em duas etapas por aplicativo autenticador. */
@RestController
public class SegurancaContaController {
    static final String SESSAO = "taskgoSessao";
    static final String TOTP = "segurancaTotp", TOTP_PENDENTE = "segurancaTotpPendente", RECUPERACAO = "segurancaRecuperacao";
    private static final SecureRandom RANDOM = new SecureRandom();
    private final SessaoContaRepository sessoes;
    private final UsuarioRepository usuarios;
    private final PasswordEncoder encoder;
    private final ModeracaoService moderacao;

    public SegurancaContaController(SessaoContaRepository sessoes, UsuarioRepository usuarios, PasswordEncoder encoder, ModeracaoService moderacao) {
        this.sessoes = sessoes; this.usuarios = usuarios; this.encoder = encoder; this.moderacao = moderacao;
    }

    /* Chamado ao abrir uma sessão (cadastro, login, confirmação de e-mail, segunda etapa). */
    public void registrarSessao(Usuario u, HttpServletRequest request) {
        HttpSession s = request.getSession(false);
        if (s == null) return;
        String ip = request.getRemoteAddr(); /* já é o IP real (server.forward-headers-strategy) */
        SessaoConta nova = sessoes.save(new SessaoConta(u.getId(), SessaoConta.dispositivo(request.getHeader("User-Agent")), ip, System.currentTimeMillis()));
        s.setAttribute(SESSAO, nova.getId());
    }
    public void encerrarSessaoAtual(HttpServletRequest request) {
        HttpSession s = request.getSession(false);
        Object id = s == null ? null : s.getAttribute(SESSAO);
        if (id != null) sessoes.findById(id.toString()).ifPresent(x -> { x.encerrar(System.currentTimeMillis()); sessoes.save(x); });
    }

    public static boolean doisFatoresAtivo(Usuario u) { return u.getExtra().get(TOTP) instanceof String s && !s.isBlank(); }

    /* Erros seguidos no 2FA (no extra da conta, então novo login não zera): 5 em 15 min bloqueiam por 15 min. */
    static final String TFA_ERROS = "tfaErros";
    static final int MAX_ERROS_2FA = 5;
    static final long JANELA_2FA_MS = 15 * 60_000L;

    @jakarta.persistence.PersistenceContext private jakarta.persistence.EntityManager em;

    /* Confere o código do aplicativo ou um código de recuperação (que só vale uma vez). */
    @Transactional
    public boolean conferirSegundaEtapa(Usuario u, String codigo) {
        if (!doisFatoresAtivo(u) || codigo == null) return false;
        /* Lê o contador de novo com a linha travada: 50 códigos em paralelo contam 50 erros. */
        if (em != null && u.getId() != null) { Usuario travado = em.find(Usuario.class, u.getId()); if (travado != null) { em.refresh(travado, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE); u = travado; } }
        long agora = System.currentTimeMillis();
        long espera = LimiteErros.esperaMs(u.getExtra(), TFA_ERROS, agora);
        if (espera > 0) throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Muitos códigos errados nesta conta. Tente de novo em " + (espera + 59_999) / 60_000 + " min.");
        boolean ok = confere(u, codigo);
        /* Sem exceção aqui: a contagem tem de ser gravada mesmo quando o código está errado. */
        if (ok) LimiteErros.zerar(u.getExtra(), TFA_ERROS);
        else LimiteErros.errou(u.getExtra(), TFA_ERROS, MAX_ERROS_2FA, JANELA_2FA_MS, JANELA_2FA_MS, agora);
        usuarios.save(u);
        return ok;
    }

    private boolean confere(Usuario u, String codigo) {
        if (Totp.confere((String) u.getExtra().get(TOTP), codigo, System.currentTimeMillis())) return true;
        String limpo = codigo.replaceAll("[^A-Za-z0-9]", "").toUpperCase();
        if (limpo.length() != 10) return false;
        Object lista = u.getExtra().get(RECUPERACAO);
        if (!(lista instanceof List<?> l)) return false;
        String h = hash(limpo);
        List<Object> resto = new ArrayList<>(l);
        if (!resto.remove(h)) return false;
        u.getExtra().put(RECUPERACAO, resto);
        moderacao.registrar(u.getId(), "conta", "Entrou com um código de recuperação (restam " + resto.size() + ")", u.getId(), null);
        return true;
    }

    @GetMapping("/api/conta/seguranca")
    @Transactional(readOnly = true)
    public Map<String, Object> situacao(Authentication auth, HttpServletRequest request) {
        Usuario u = usuario(auth);
        HttpSession s = request.getSession(false);
        Object atual = s == null ? null : s.getAttribute(SESSAO);
        List<Map<String, Object>> lista = sessoes.findByUsuarioIdAndEncerradaEmIsNullOrderByVistaEmDesc(u.getId()).stream().map(x -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", x.getId()); m.put("dispositivo", x.getDispositivo()); m.put("ip", x.getIp());
            m.put("criadaEm", x.getCriadaEm()); m.put("vistaEm", x.getVistaEm()); m.put("atual", x.getId().equals(atual));
            return m;
        }).toList();
        Object rec = u.getExtra().get(RECUPERACAO);
        return Map.of("doisFatores", doisFatoresAtivo(u), "codigosRecuperacaoRestantes", rec instanceof List<?> l ? l.size() : 0, "sessoes", lista);
    }

    @DeleteMapping("/api/conta/sessoes/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void encerrar(@PathVariable String id, Authentication auth) {
        Usuario u = usuario(auth);
        SessaoConta x = sessoes.findById(id).filter(s -> u.getId().equals(s.getUsuarioId())).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Sessão não encontrada."));
        x.encerrar(System.currentTimeMillis()); sessoes.save(x);
    }

    @DeleteMapping("/api/conta/sessoes")
    @Transactional
    public Map<String, Object> encerrarOutras(Authentication auth, HttpServletRequest request) {
        Usuario u = usuario(auth);
        HttpSession s = request.getSession(false);
        Object atual = s == null ? null : s.getAttribute(SESSAO);
        long agora = System.currentTimeMillis(); int n = 0;
        for (SessaoConta x : sessoes.findByUsuarioIdAndEncerradaEmIsNullOrderByVistaEmDesc(u.getId()))
            if (!x.getId().equals(atual)) { x.encerrar(agora); sessoes.save(x); n++; }
        if (n > 0) moderacao.registrar(u.getId(), "conta", "Encerrou " + n + (n == 1 ? " sessão" : " sessões") + " em outros aparelhos", u.getId(), null);
        return Map.of("encerradas", n);
    }

    /* ---------- Duas etapas ---------- */
    @PostMapping("/api/conta/2fa/iniciar")
    @Transactional
    public Map<String, String> iniciar(Authentication auth) {
        Usuario u = usuario(auth);
        if (doisFatoresAtivo(u)) throw new ResponseStatusException(HttpStatus.CONFLICT, "A verificação em duas etapas já está ligada.");
        String segredo = Totp.novoSegredo();
        u.getExtra().put(TOTP_PENDENTE, segredo);
        usuarios.save(u);
        return Map.of("segredo", segredo, "uri", Totp.uri(segredo, u.getEmail()));
    }

    public record Codigo(String codigo) {}

    @PostMapping("/api/conta/2fa/ativar")
    @Transactional
    public Map<String, Object> ativar(@RequestBody Codigo body, Authentication auth) {
        Usuario u = usuario(auth);
        Object pendente = u.getExtra().get(TOTP_PENDENTE);
        if (!(pendente instanceof String segredo)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Comece pelo passo de ler o QR code.");
        if (body == null || !Totp.confere(segredo, body.codigo(), System.currentTimeMillis()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Código incorreto. Confira se o relógio do celular está certo e digite o código que aparece agora.");
        List<String> codigos = new ArrayList<>(), hashes = new ArrayList<>();
        String alfabeto = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        for (int i = 0; i < 8; i++) {
            StringBuilder c = new StringBuilder();
            for (int j = 0; j < 10; j++) c.append(alfabeto.charAt(RANDOM.nextInt(alfabeto.length())));
            codigos.add(c.substring(0, 5) + "-" + c.substring(5)); hashes.add(hash(c.toString()));
        }
        u.getExtra().put(TOTP, segredo); u.getExtra().remove(TOTP_PENDENTE); u.getExtra().put(RECUPERACAO, hashes);
        usuarios.save(u);
        moderacao.registrar(u.getId(), "conta", "Ligou a verificação em duas etapas", u.getId(), null);
        return Map.of("codigosRecuperacao", codigos);
    }

    public record Senha(String senha) {}

    @PostMapping("/api/conta/2fa/desativar")
    @Transactional
    public void desativar(@RequestBody Senha body, Authentication auth) {
        Usuario u = usuario(auth);
        if (body == null || body.senha() == null || !encoder.matches(body.senha(), u.getSenha()))
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Senha incorreta.");
        u.getExtra().remove(TOTP); u.getExtra().remove(TOTP_PENDENTE); u.getExtra().remove(RECUPERACAO);
        usuarios.save(u);
        moderacao.registrar(u.getId(), "conta", "Desligou a verificação em duas etapas", u.getId(), null);
    }

    static String hash(String v) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(v.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception e) { throw new IllegalStateException(e); }
    }
    private Usuario usuario(Authentication a) {
        if (a == null || !(a.getPrincipal() instanceof TaskgoPrincipal p)) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        return usuarios.findById(p.id()).orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
    }
}
