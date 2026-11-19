package com.taskgo.api;

import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/* Profissional online ou offline. Offline, não vê oportunidades nem envia propostas. Começa online. */
@RestController
@RequestMapping("/api/profissionais/eu/disponibilidade")
public class DisponibilidadeController {
    static final String CHAVE = "online";
    private final UsuarioRepository usuarios;
    public DisponibilidadeController(UsuarioRepository usuarios) { this.usuarios = usuarios; }

    static boolean online(Usuario u) { return !Boolean.FALSE.equals(u.getExtra().get(CHAVE)); }

    @GetMapping
    @Transactional(readOnly = true)
    public Map<String, Object> obter(Authentication auth) { return Map.of("online", online(profissional(auth))); }

    @PutMapping
    @Transactional
    public Map<String, Object> alterar(@RequestBody Map<String, Object> body, Authentication auth) {
        Usuario u = profissional(auth);
        if (body == null || !(body.get("online") instanceof Boolean on)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe online: true ou false.");
        u.getExtra().put(CHAVE, on);
        usuarios.save(u);
        return Map.of("online", on);
    }

    private Usuario profissional(Authentication a) {
        if (a == null || !(a.getPrincipal() instanceof TaskgoPrincipal p)) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        if (!"profissional".equalsIgnoreCase(p.tipo())) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Só para contas de profissional.");
        return usuarios.findById(p.id()).orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
    }
}
