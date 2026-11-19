package com.taskgo.api;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/* Preferências de aviso da conta (e-mail e push) e as inscrições de push dos navegadores. */
@RestController
@RequestMapping("/api/conta/avisos")
public class AvisosController {
    private final AvisosExternosService avisos;
    private final UsuarioRepository usuarios;
    public AvisosController(AvisosExternosService avisos, UsuarioRepository usuarios) { this.avisos = avisos; this.usuarios = usuarios; }

    @GetMapping
    public Map<String, Object> obter(Authentication auth) { return resposta(usuario(auth)); }

    @PutMapping
    @Transactional
    public Map<String, Object> salvar(@RequestBody Map<String, Object> body, Authentication auth) {
        Usuario u = usuario(auth);
        if (body == null || !(body.get("email") instanceof Boolean e) || !(body.get("push") instanceof Boolean p))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe email e push: true ou false.");
        /* SMS é opcional no corpo: sem ele, mantém o que a conta já tinha. */
        boolean s = body.get("sms") instanceof Boolean b ? b : AvisosExternosService.preferencias(u).sms();
        u.getExtra().put(AvisosExternosService.CHAVE_PREF, Map.of("email", e, "push", p, "sms", s));
        usuarios.save(u);
        return resposta(u);
    }

    @PostMapping("/push") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void inscrever(@RequestBody Inscricao body, Authentication auth) {
        if (body == null || body.endpoint() == null || body.keys() == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Inscrição de push inválida.");
        avisos.inscrever(usuario(auth).getId(), body.endpoint(), body.keys().get("p256dh"), body.keys().get("auth"));
    }

    @DeleteMapping("/push") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void desinscrever(@RequestBody(required = false) Inscricao body, Authentication auth) {
        if (body != null && body.endpoint() != null) avisos.desinscrever(usuario(auth).getId(), body.endpoint());
    }

    /* Manda um push de teste para os navegadores desta conta. */
    @PostMapping("/push/teste")
    public Map<String, Object> teste(Authentication auth) {
        int n = avisos.enviarPush(usuario(auth).getId(), "{\"titulo\":\"Avisos ligados\",\"texto\":\"É assim que a TaskGO vai te avisar.\",\"url\":\"/#/app/notificacoes\",\"tag\":\"teste\"}", "normal");
        return Map.of("entregues", n);
    }

    public record Inscricao(String endpoint, Map<String, String> keys) {}

    private Map<String, Object> resposta(Usuario u) {
        var p = AvisosExternosService.preferencias(u);
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("email", p.email()); r.put("push", p.push()); r.put("sms", p.sms());
        r.put("emailDisponivel", avisos.emailDisponivel());
        r.put("smsDisponivel", avisos.smsDisponivel());
        r.put("chavePush", avisos.chavePublica());
        r.put("navegadores", avisos.quantas(u.getId()));
        return r;
    }
    private Usuario usuario(Authentication a) {
        if (a == null || !(a.getPrincipal() instanceof TaskgoPrincipal p)) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        return usuarios.findById(p.id()).orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
    }
}
